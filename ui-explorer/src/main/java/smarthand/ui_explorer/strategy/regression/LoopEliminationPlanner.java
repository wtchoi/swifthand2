package smarthand.ui_explorer.strategy.regression;

import smarthand.ui_explorer.DeviceInfo;
import smarthand.ui_explorer.HistoryManager;
import smarthand.ui_explorer.Options;
import smarthand.ui_explorer.strategy.C;
import smarthand.ui_explorer.trace.*;
import smarthand.ui_explorer.util.Util;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Created by wtchoi on 3/13/17.
 */
public class LoopEliminationPlanner extends TraceBasedPlanner {

    boolean checkText = true;
    boolean checkProperties = true;
    boolean checkCoordinate = true;

    Trace confirmedTrace = new Trace();
    Coverage confirmedCoverage = new Coverage();
    Coverage currentCoverage = new Coverage();

    LinkedList<LinkedList<Integer>> currentPlans = new LinkedList<>();
    Map<LinkedList<Integer>, Expectation> expectations = new HashMap<>();

    int nextInputTrace = 0;

    int trueDeviationCount = 0;

    int truncatedEventCount = 0;

    int skippedCandidateCount = 0;
    int skippedCandidateEventCount = 0;

    int droppedSequenceCount = 0;
    int droppedEventCount = 0;
    int dropCountNoGain = 0;
    int dropCountWrongInit = 0;
    int dropCountPossibleND = 0;

    int selectedSequenceCount = 0;
    int selectedEventCount = 0;

    int trialCount = 0;
    int triedCandidateCount = 0;

    class Expectation {
        Coverage c;
        boolean original;
        LinkedList<Integer> originalPlan;
    }

    public LoopEliminationPlanner(String inputTracePath, int stabilizationTh) {
        super(inputTracePath, stabilizationTh);
    }

    @Override
    public void reportIntermediateStep(int eventIndex, DeviceInfo deviceInfo, AbstractUI abstractUI, Coverage coverage, boolean escaped, boolean blocked) {
        currentCoverage.add(coverage.methodCoverage, coverage.methodCoverage, coverage.screenCoverage);
    }

    @Override
    public void reportExecutionFailureImpl(LinkedList<Integer> plan, LinkedList<Integer> failingPrefix, Trace resultingTrace, Coverage expectedCoverage) {
        currentCoverage = new Coverage(confirmedCoverage);
        removePossibleFailures(failingPrefix, currentPlans);
        expectations.remove(plan);

        addAlternative(failingPrefix, resultingTrace);
    }

    @Override
    public Coverage getAlreadyCovered() {
        return new Coverage(confirmedCoverage);
    }

    private void addAlternative(LinkedList<Integer> plan, Trace counterExample) {
        if (plan.size() > 3) {
            Trace prefixTrace = counterExample.getSubtrace(0, (counterExample.size() - 1));
            Coverage prefixCoverage = prefixTrace.computeCoverage(true);

            LinkedList<Integer> commonPrefix  = PlanUtil.getCommonPrefix(plan, PlanUtil.traceToPlan(counterExample));
            LinkedList<Integer> p = PlanUtil.finalizePlan(commonPrefix);

            printIntSet(p, "common prefix", this);

            Expectation e = new Expectation();
            e.originalPlan = plan;
            e.c = prefixCoverage;
            e.original = true;

            currentPlans.addLast(p);
            expectations.put(p, e);
            sortPlans(currentPlans);
        }
    }

    @Override
    public void reportExecutionSuccessImplExact(LinkedList<Integer> plan, Trace resultingTrace, Coverage expectedCoverage) {
        Trace t = Trace.trim(resultingTrace, expectedCoverage, true);
        confirmedTrace.append(t);
        confirmedCoverage.add(t.computeCoverage(false));
        //Set ignoreStart to false in order to reflect starting coverage to the final coverage

        selectedSequenceCount++;
        selectedEventCount += resultingTrace.size();
        truncatedEventCount += ((expectations.get(plan).originalPlan.size() -  plan.size()) / 2);

        currentCoverage = new Coverage(confirmedCoverage);

        for (LinkedList<Integer> p: currentPlans) {
            expectations.remove(p);
            skippedCandidateCount++;
            skippedCandidateEventCount += (p.size() - 1) / 2;
        }
        currentPlans.clear();
        expectations.remove(plan);

        printIntSet(plan, "confirming plan", this);
        printIntSet(PlanUtil.traceToPlan(resultingTrace), "confirming trace", this);
    }

    @Override
    public void reportExecutionSuccessImplFlaky(LinkedList<Integer> plan, Trace resultingTrace, Coverage expectedCoverage) {
        Expectation e = expectations.get(plan);
        e.c = Coverage.intersect(resultingTrace.computeCoverage(true), e.c);

        if (e.c.size() > 0) {
            currentPlans.addLast(plan);
            sortPlans(currentPlans);

            log("Candidates");
            for (LinkedList<Integer> p : currentPlans) {
                 Coverage gain = Coverage.minus(expectations.get(p).c, confirmedCoverage);
                printIntSet(p, "p(" + expectations.get(p).c.size() + ", " + gain.size() + ")", LoopEliminationPlanner.this);
            }
        }
        else {
            expectations.remove(plan);
        }

        currentCoverage = new Coverage(confirmedCoverage);
    }

    @Override
    public Plan getNextPlanImpl(AbstractUI currAbs) {
        log("Getting next plan");

        if (currentPlans.isEmpty()) {
            log("Plan queue is empty. Fill the queue by consuming a trace.");

            while (nextInputTrace < inputTraceSet.size()) {
                Trace next = inputTraceSet.get(nextInputTrace++);
                trialCount++;

                if (next.computeGain(confirmedCoverage, true).size() == 0) {
                    log("Skipping trace (no gain)");
                    dropCountNoGain++;
                    droppedSequenceCount++;
                    droppedEventCount += (next.size() - 1);
                    continue;
                }
                if (next.get(0).abstractUI != currAbs) {
                    log("Skipping trace (wrong init state");
                    dropCountWrongInit++;
                    droppedSequenceCount++;
                    droppedEventCount += (next.size() - 1);
                    continue;
                }

                log("Found a candidate trace");
                LinkedList<LinkedList<Integer>> candidatePlans = (new PlanBuilder()).buildPlans(next);

                if (candidatePlans.isEmpty()) {
                    // Everything dropped because of non-determinism
                    log("Skipping trace (non-determinisim)");
                    dropCountPossibleND++;
                    droppedSequenceCount++;
                    droppedEventCount += next.size();
                    continue;
                }

                currentPlans = candidatePlans;
                log("Candidates");
                for (LinkedList<Integer> p : currentPlans) {
                    printIntSet(p, "p(" + expectations.get(p).c.size() + ")", LoopEliminationPlanner.this);
                }
                break;
            }

            // plan is not updated
            if (currentPlans.isEmpty()) {
                // Nothing left to do
                log("No trace left in the original trace list");
                log("Finishing the loop elimination algorithm");
                return null;
            }
        }

        triedCandidateCount++;
        Plan p = new Plan();
        p.plan = currentPlans.removeFirst();
        p.expectedCoverage = expectations.get(p.plan).c;

        if (p.plan.getFirst() != currAbs.id()) {
            AbstractUI expected = AbstractUI.getStateById(p.plan.getFirst());
            log("Expected key: " + expected.getKey());
            log("Expected activity: " + expected.getActivityName());
            log("Available key: " + currAbs.getKey());
            log("Available activity: " + currAbs.getKey());
            throw new RuntimeException("Initial state missmatch!");
        }

        return p;
    }

    void sortPlans(LinkedList<LinkedList<Integer>> planSet) {
        Collections.sort(planSet, (LinkedList<Integer> a, LinkedList<Integer> b) -> {
            int scoreA = Coverage.minus(expectations.get(a).c, confirmedCoverage).size();
            int scoreB = Coverage.minus(expectations.get(b).c, confirmedCoverage).size();
            if (scoreA > scoreB) return -1;
            if (scoreA < scoreB) return 1;
            if (a.size() > b.size()) return 1;
            if (a.size() < b.size()) return -1;
            return 0;
        });
    }

    void removePossibleFailures(LinkedList<Integer> prefix, LinkedList<LinkedList<Integer>> plans) {
        printIntSet(prefix, "filter out plan using prefix", this);
        LinkedList<LinkedList<Integer>> newPlans = new LinkedList<>();
        for (LinkedList<Integer> plan : plans) {
            if (!PlanUtil.prefixCheck(plan, prefix)) {
                newPlans.addLast(plan);
            } else {
                skippedCandidateCount++;
                skippedCandidateEventCount += (plan.size() - 1) / 2;
                printIntSet(plan, "filter out", this);

                if (expectations.get(plan).original) {
                    trueDeviationCount++;
                    log("Original plan removed!");
                }
                expectations.remove(plan);
            }
        }

        plans.clear();
        if (!newPlans.isEmpty()) {
            plans.addAll(newPlans);
        }
    }


    class PlanBuilder {
        Coverage expectedGain = null;
        LinkedList<LinkedList<Integer>> plans = null;
        LinkedList<Integer> originalPlan = null;
        LinkedList<Coverage> coverages = new LinkedList<>();

        public LinkedList<LinkedList<Integer>> buildPlans(Trace sequence) {
            originalPlan = PlanUtil.traceToPlan(sequence);
            expectedGain = sequence.computeGain(confirmedCoverage, true);

            printIntSet(originalPlan, "originalPlan", LoopEliminationPlanner.this);
            log("expectedGain : " + expectedGain.size() );

            plans = new LinkedList<>();

            // register original plan
            Expectation e = new Expectation();
            e.c = Coverage.add(expectedGain, confirmedCoverage);
            e.original = true;
            e.originalPlan = originalPlan;
            plans.add(originalPlan);
            expectations.put(originalPlan, e);
            coverages.addLast(e.c);

            LinkedList plan = new LinkedList();
            plan.addLast(sequence.get(0).abstractUI.id());

            buildPlans(sequence, 0, new Coverage(confirmedCoverage), plan, false);
            sortPlans(plans);
            return plans;
        }

        private void buildPlans(Trace sequence, int index, Coverage assumedCoverage, LinkedList<Integer> planPrefix, boolean justSkipCycle) {
            if (index == sequence.size()) {
                Coverage gain = Coverage.minus(assumedCoverage, confirmedCoverage);
                if (gain.size() > 0 && gain.size() == expectedGain.size()) {
                    LinkedList<Integer> result = PlanUtil.finalizePlan(planPrefix);
                    for (LinkedList<Integer> p : plans) {
                        if (PlanUtil.planEquals(p, result)) {
                            printIntSet(result, "redundant", LoopEliminationPlanner.this);
                            return;
                        }
                        if (PlanUtil.equalsExceptLast(p, result)) {
                            if (p.getLast() == C.WILDCARD && result.getLast() == 0 || p.getLast() == 0 && result.getLast() == C.WILDCARD) {
                                printIntSet(result, "redundant", LoopEliminationPlanner.this);
                                return;
                            } else {
                                throw new RuntimeException("Something is wrong!");
                            }
                        }
                    }

                    boolean possiblyNondeterministic = false;
                    for (LinkedList<Integer> p: failingPrefixes) {
                        if (PlanUtil.prefixCheck(result, p)) {
                            printIntSet(result, "discard (possibly non-deterministic)", LoopEliminationPlanner.this);
                            possiblyNondeterministic = true;
                            break;
                        }
                    }

                    if (!possiblyNondeterministic) {
                        printIntSet(result, "register", LoopEliminationPlanner.this);

                        Expectation e = new Expectation();
                        for (Coverage pc: coverages) {
                            if (pc.equals(assumedCoverage)) {
                                e.c = pc;
                                break;
                            }
                        }
                        if (e.c == null) {
                            e.c = new Coverage(assumedCoverage);
                            coverages.addLast(e.c);
                        }

                        plans.add(result);
                        e.original = PlanUtil.equalsExceptLast(result, originalPlan);
                        e.originalPlan = originalPlan;
                        expectations.put(result, e);
                    }
                } else {
                    printIntSet(planPrefix, "discard:(" + Coverage.minus(assumedCoverage, confirmedCoverage).size() +  " vs " + expectedGain.size() + ")" , LoopEliminationPlanner.this);
                    Coverage diff = Coverage.minus(expectedGain, (Coverage.minus(assumedCoverage, confirmedCoverage)));
                    log(String.format("missing screen:%d, branch:%d, method:%d", diff.screenCoverage.size(), diff.branchCoverage.size(), diff.methodCoverage.size()));
                    printIntSet(diff.screenCoverage, "missing screen set", LoopEliminationPlanner.this);
                }
                return;
            }

            Trace.Snapshot s = sequence.get(index);
            LinkedList<Integer> updatedPlan = new LinkedList<>(planPrefix);
            if (index != 0) {
                updatedPlan.addLast(s.prevAction.kind == Action.Kind.Close ? C.CLOSE : s.prevAction.actionIndex);
                updatedPlan.addLast(s.abstractUI.id());
            }

            Coverage updatedCoverage = new Coverage(assumedCoverage);
            if (index != 0) {
                updatedCoverage.add(s.methodCoverage, s.branchCoverage, s.abstractUI.id());
            }

            boolean mustSkip = false;
            //Handling cycle
            {
                int cycleEnd = index;
                boolean gain = false;
                for (int i = index + 1; i < sequence.size(); i++) {
                    Trace.Snapshot cur = sequence.get(i);
                    Coverage snapshotCoverage = new Coverage(cur.methodCoverage, cur.branchCoverage, cur.abstractUI.id());

                    if (!assumedCoverage.isGreaterOrEqual(snapshotCoverage)) {
                        gain = true;
                    }

                    if (cur.abstractUI.id() == s.abstractUI.id()) {
                        ConcreteUI.CheckOption option = new ConcreteUI.CheckOption(checkProperties, checkText, checkCoordinate);
                        option.verbose = false;

                        if (ConcreteUI.checkUiDetail(cur.concreteUI, s.concreteUI, option, LoopEliminationPlanner.this)) {
                            cycleEnd = i;
                            break;
                        } else {
                            log("------ Cycle Detection: Not a cycle ------");
                            //log(cur.concreteUI.toString());
                            //log(s.concreteUI.toString());
                        }
                    }
                }

                if (!gain) {
                    Coverage nextCoverage = justSkipCycle ? assumedCoverage : updatedCoverage;
                    LinkedList<Integer> nextPlan = justSkipCycle ? planPrefix : updatedPlan;
                    if (cycleEnd > index) {
                        //skip cycle
                        buildPlans(sequence, cycleEnd, nextCoverage, nextPlan, true);
                        if (cycleEnd - index == 1) {
                            mustSkip = true;
                        }
                    } else {
                        //skip tail
                        // plan without tail
                        LinkedList<Integer> anotherPlan = new LinkedList<>(nextPlan);
                        if (anotherPlan.size() == 1 || !anotherPlan.get(anotherPlan.size() - 2).equals(C.CLOSE)) {
                            anotherPlan.addLast(C.CLOSE);
                            anotherPlan.addLast(C.WILDCARD);
                        }
                        buildPlans(sequence, sequence.size(), nextCoverage, anotherPlan, false);

                        // plan with tail
                        LinkedList<Integer> yetAnotherPlan = new LinkedList<>(nextPlan);
                        Coverage yetAnotherCoverage = new Coverage(nextCoverage);
                        for (int j=index+1; j<sequence.size(); j++) {
                           Trace.Snapshot ss = sequence.get(j);
                            yetAnotherPlan.addLast(ss.prevAction.isClose() ? C.CLOSE : ss.prevAction.actionIndex);
                            yetAnotherPlan.addLast(ss.abstractUI.id());
                            yetAnotherCoverage.add(ss.methodCoverage, ss.branchCoverage, ss.abstractUI.id());
                        }
                        buildPlans(sequence, sequence.size(), yetAnotherCoverage, yetAnotherPlan, false);
                        return;
                    }
                }
            }

            if (!mustSkip) {
                if (justSkipCycle) {
                    buildPlans(sequence, index + 1, assumedCoverage, planPrefix, false);
                } else {
                    buildPlans(sequence, index + 1, updatedCoverage, updatedPlan, false);
                }
            }
        }
    }

    @Override
    public void initializeImpl() {
        //nop
    }

    @Override
    public void intermediateDump(int id) {
        super.intermediateDump(id);

        HistoryManager hm = HistoryManager.instance();

        hm.periodStat("Replay:#TrueDeviation", trueDeviationCount);

        dumpCoverage("Replay", currentCoverage);

        Coverage trialGain = Coverage.minus(currentCoverage, confirmedCoverage);
        dumpCoverage("Replay:Plan:Mini:Trial", trialGain);
        dumpCoverage("Replay:Confirmed", confirmedCoverage);

        hm.periodStat("Replay:Plan:Mini:#Trials.", trialCount);
        hm.periodStat("Replay:Plan:Mini:#Skipped Candidates", skippedCandidateCount);
        hm.periodStat("Replay:Plan:Mini:#Skipped Events", skippedCandidateEventCount);
        hm.periodStat("Replay:Plan:Mini:#Tried Candidates", triedCandidateCount);
        hm.periodStat("Replay:Plan:#Selected Seq.", selectedSequenceCount);
        hm.periodStat("Replay:Plan:#Selected Event", selectedEventCount);
        hm.periodStat("Replay:Plan:#Truncated Event", truncatedEventCount);
        hm.periodStat("Replay:Plan:#Dropped Event", inputTraceProfiler.droppedTraceEvents + inputTraceProfiler.droppedTailEvents + droppedEventCount);
        hm.periodStat("Replay:Plan:#Dropped Seq.", inputTraceProfiler.droppedTraces + droppedSequenceCount);
        hm.periodStat("Replay:Plan:#Dropped Seq.:No Gain", dropCountNoGain);
        hm.periodStat("Replay:Plan:#Dropped Seq.:Wrong Init", dropCountWrongInit);
        hm.periodStat("Replay:Plan:#Dropped Seq.:Possible ND", dropCountPossibleND);

        dumpCoverage("Trace", inputTraceCoverage);
        hm.periodStat("Trace:#Event", inputTraceProfiler.events);
        hm.periodStat("Trace:#Seq.", inputTraceProfiler.traces);
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
