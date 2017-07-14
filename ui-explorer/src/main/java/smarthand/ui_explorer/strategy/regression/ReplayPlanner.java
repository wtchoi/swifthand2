package smarthand.ui_explorer.strategy.regression;

import smarthand.ui_explorer.DeviceInfo;
import smarthand.ui_explorer.HistoryManager;
import smarthand.ui_explorer.Options;
import smarthand.ui_explorer.strategy.C;
import smarthand.ui_explorer.trace.AbstractUI;
import smarthand.ui_explorer.trace.Coverage;
import smarthand.ui_explorer.trace.Trace;
import smarthand.ui_explorer.util.Util;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Created by wtchoi on 3/13/17.
 */
public class ReplayPlanner extends TraceBasedPlanner {

    private int nextTraceIndex = 0;
    private LinkedList<LinkedList<Integer>> nextPlans = new LinkedList<>();
    private Map<LinkedList<Integer>, PlanInfo> planInfo = new HashMap<>();

    private Trace confirmedTrace = new Trace();
    private Coverage confirmedCoverage = new Coverage();
    private Coverage currentCoverage = new Coverage();

    private int trialCount = 0;
    private int triedCandidateCount= 0;
    private int trueDeviationCount = 0;
    private int selectedSequenceCount = 0;
    private int selectedEventCount = 0;

    private int truncatedEventCount = 0;
    private int droppedEventCount = 0;
    private int droppedSequenceCount = 0;
    private int consideredAlternativeCount = 0;

    private boolean stabilizing = true;

    static class PlanInfo {
        Coverage c;
        LinkedList<Integer> originalPlan;
        int trialCount = 0;
    }

    public ReplayPlanner(String inputTracePath, int stabilizationTh, boolean stabilizing) {
        super(inputTracePath, stabilizationTh);
        this.stabilizing = stabilizing;
    }

    @Override
    public void reportIntermediateStep(int eventIndex, DeviceInfo deviceInfo, AbstractUI abstractUI, Coverage coverage, boolean escaped, boolean blocked) {
        if (eventIndex != C.START) {
            currentCoverage.add(coverage);
        }
    }

    @Override
    public void reportExecutionFailureImpl(LinkedList<Integer> plan, LinkedList<Integer> failingPrefix, Trace resultingTrace, Coverage expectedCoverage) {
        if (stabilizing) {
            if (failingPrefix.size() > 3) {
                Coverage prefixCoverage = resultingTrace.getSubtrace(0, resultingTrace.size() - 1).computeCoverage(true);
                LinkedList<Integer> alternative = PlanUtil.finalizePlan(PlanUtil.getCommonPrefix(PlanUtil.traceToPlan(resultingTrace), failingPrefix));

                printIntSet(plan, "failing prefix", this);
                printIntSet(PlanUtil.traceToPlan(resultingTrace), "resulting trace", this);
                printIntSet(alternative, "common prefix", this);

                PlanInfo pInfo = new PlanInfo();
                pInfo.c = prefixCoverage;
                pInfo.originalPlan = planInfo.get(plan).originalPlan;

                planInfo.put(alternative, pInfo);
                nextPlans.addFirst(alternative);

                printIntSet(alternative, "Adding alternative (" + prefixCoverage.size() + ")", this);

                planInfo.remove(plan);
                currentCoverage = new Coverage(confirmedCoverage);

                consideredAlternativeCount++;
            } else {
                droppedSequenceCount++;
            }
        }
        else {
            droppedSequenceCount++;
        }

        trueDeviationCount++;
    }

    @Override
    public void reportExecutionSuccessImplExact(LinkedList<Integer> plan, Trace resultingTrace, Coverage expectedCoverage) {
        Trace t = Trace.trim(resultingTrace, expectedCoverage, true);
        confirmedTrace.append(t);
        confirmedCoverage.add(t.computeCoverage(false));

        log(String.format("Expected:%d, Confirmded:", expectedCoverage.size(), t.computeCoverage(true).size()));
        log(String.format("Confirmed with start: %d", t.computeCoverage(false).size()));

        selectedSequenceCount++;
        selectedEventCount += (t.size() - 1);

        PlanInfo pInfo = planInfo.get(plan);
        if (plan != pInfo.originalPlan) {
            truncatedEventCount += (pInfo.originalPlan.size() - plan.size()) / 2;
        }

        planInfo.remove(plan);
        currentCoverage = new Coverage(confirmedCoverage);
    }

    @Override
    public void reportExecutionSuccessImplFlaky(LinkedList<Integer> plan, Trace resultingTrace, Coverage expectedCoverage) {
        if (stabilizing) {
            nextPlans.addFirst(plan);
            PlanInfo pInfo = planInfo.get(plan);
            pInfo.c = Coverage.intersect(expectedCoverage, resultingTrace.computeCoverage(true));
            currentCoverage = new Coverage(confirmedCoverage);
        }
        else {
            reportExecutionSuccessImplExact(plan, resultingTrace, expectedCoverage);
        }
    }

    @Override
    public Plan getNextPlanImpl(AbstractUI currAbs) {
        if (nextPlans.isEmpty()) {
            while (nextTraceIndex < inputTraceSet.size()) {
                log(String.format("Getting %d out of %d", nextTraceIndex, inputTraceSet.size()));
                Trace nextTrace = inputTraceSet.get(nextTraceIndex);
                nextTraceIndex++;

                if (nextTrace.get(0).abstractUI != currAbs) {
                    droppedSequenceCount++;
                    droppedEventCount += (nextTrace.size() - 1);
                    log(String.format("skipping trace (initial state miss match) %d vs %d", currAbs.id(), nextTrace.get(0).abstractUI.id()));
                    log("Available key: " + currAbs.getKey());
                    log("Available activity: " + currAbs.getActivityName());
                    log("Expected key: " + nextTrace.get(0).abstractUI.getKey());
                    log("Expected activity: " + nextTrace.get(0).abstractUI.getActivityName());
                    continue;
                }

                if (nextTrace.size() <= 2) {
                    droppedSequenceCount++;
                    droppedEventCount += (nextTrace.size() - 1);
                    log(String.format("skipping trace (too short)"));
                    continue;
                }

                LinkedList<Integer> p = PlanUtil.traceToPlan(nextTrace);

                PlanInfo pInfo = new PlanInfo();
                pInfo.c = nextTrace.computeCoverage(true);
                pInfo.originalPlan = p;

                planInfo.put(p, pInfo);
                nextPlans.addLast(p);
                trialCount++;
                break;
            }
        }

        if (!nextPlans.isEmpty()) {
            Plan p = new Plan();
            LinkedList<Integer> pp = nextPlans.removeFirst();
            PlanInfo ppInfo = planInfo.get(pp);

            p.expectedCoverage = ppInfo.c;
            p.plan = pp;
            ppInfo.trialCount++;

            if (ppInfo.trialCount == 1) {
                triedCandidateCount++;
            }
            return p;
        }
        else {
            return null;
        }
    }

    @Override
    public Coverage getAlreadyCovered() {
        return new Coverage();
    }


    @Override
    public void initializeImpl() {
        //nop
    }

    @Override
    public void intermediateDump(int id) {
        super.intermediateDump(id);

        HistoryManager hm = HistoryManager.instance();
        Coverage trialGain = Coverage.minus(currentCoverage, confirmedCoverage);

        dumpCoverage("Replay", currentCoverage);
        dumpCoverage("Replay:Confirmed", confirmedCoverage);
        dumpCoverage("Replay:Plan:Mini:Trial", trialGain);

        dumpCoverage("Trace", inputTraceCoverage);

        hm.periodStat("Replay:#TrueDeviation", trueDeviationCount);
        hm.periodStat("Replay:Plan:Mini:#Tried Candidates", triedCandidateCount);
        hm.periodStat("Replay:Plan:#Selected Seq.", selectedSequenceCount);
        hm.periodStat("Replay:Plan:#Selected Event", selectedEventCount);

        hm.periodStat("Replay:Plan:#Trials.", trialCount);
        hm.periodStat("Replay:Plan:#Alternatives", consideredAlternativeCount);
        hm.periodStat("Replay:Plan:#Truncated Event", truncatedEventCount);
        hm.periodStat("Replay:Plan:#Dropped Event", inputTraceProfiler.droppedTraceEvents + inputTraceProfiler.droppedTailEvents + droppedEventCount);
        hm.periodStat("Replay:Plan:#Dropped Seq.", inputTraceProfiler.droppedTraces + droppedSequenceCount);

        hm.periodStat("Trace:#Event", inputTraceProfiler.events);
        hm.periodStat("Trace:#Seq.", inputTraceProfiler.traces);
        hm.periodStat("Trace", inputTraceProfiler.events);
    }

    @Override
    public void finalDump() {
        String output_dir = Options.get(Options.Keys.OUTPUT_DIR);
        Util.writeJsonFile(output_dir + "/minimized_trace.json", confirmedTrace.toJson());
    }

    @Override
    public void setRandomSeed(int seed) {
        //TODO
    }

    @Override
    public String getName() {
        return null;//TODO
    }
}
