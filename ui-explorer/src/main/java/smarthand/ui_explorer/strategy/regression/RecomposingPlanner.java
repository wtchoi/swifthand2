package smarthand.ui_explorer.strategy.regression;

import smarthand.ui_explorer.DeviceInfo;
import smarthand.ui_explorer.HistoryManager;
import smarthand.ui_explorer.Options;
import smarthand.ui_explorer.strategy.C;
import smarthand.ui_explorer.trace.*;
import smarthand.ui_explorer.util.Util;

import java.util.*;


public class RecomposingPlanner extends TraceBasedPlanner {

    private final int MAX_FRAGMENT;

    Coverage currentCoverage = new Coverage();
    Coverage confirmedCoverage = new Coverage();
    Trace confirmedTrace = new Trace();

    Coverage expectedFinalCoverage;

    PlanSet<PlanInfo> plans;
    RecompositionGraph graph;

    int trialCount = 0;
    int selectedSequenceCount = 0;
    int selectedEventCount = 0;

    int skippedCandidateCountFP = 0;
    int skippedCandidateEventCountFP = 0;
    int skippedCandidateCountCV = 0;
    int skippedCandidateEventCountCV = 0;

    class SkipCounter implements PlanSet.Observer<PlanInfo> {
        @Override
        public void onBeginFilterOut(String tag, Object opt) {
            //nop
        }

        @Override
        public void onFilterOutItem(LinkedList<Integer> plan, PlanInfo p, int tag, Object opt) {
            if (tag == PlanSet.Observer.FP) {
                skippedCandidateCountFP++;
                skippedCandidateEventCountFP += ((plan.size() - 1) / 2);
            }
            else {
                skippedCandidateCountCV++;
                skippedCandidateEventCountCV += ((plan.size() - 1) / 2);
            }
        }
    }

    public RecomposingPlanner(String inputTracePath, int stabilizationTh, int bound) {
        super(inputTracePath, stabilizationTh);
        MAX_FRAGMENT = bound;

        plans = new PlanSet();
        plans.addObserver(new SkipCounter());

        graph = new RecompositionGraph();
    }

    @Override
    public void reportIntermediateStep(int eventIndex, DeviceInfo deviceInfo, AbstractUI abstractUI, Coverage coverage, boolean escaped, boolean blocked) {
        currentCoverage.add(coverage.methodCoverage, coverage.branchCoverage, abstractUI.id());
    }

    @Override
    public void reportExecutionFailureImpl(LinkedList<Integer> plan, LinkedList<Integer> failingPrefix, Trace resultingTrace, Coverage expectedCoverage) {
        //plans.removePossibleFailures(failingPrefix);
        //no need to sort, because the order is preserved.

        plans.removePlanInfo(plan, "reportExecutionFailureImpl");
        currentCoverage = new Coverage(confirmedCoverage);
        expectedFinalCoverage = Coverage.add(confirmedCoverage, plans.getExpectedCoverageSum());
    }



    @Override
    public void reportExecutionSuccessImplExact(LinkedList<Integer> plan, Trace resultingTrace, Coverage expectedCoverage) {
        Trace t = Trace.trim(resultingTrace, expectedCoverage, true);
        confirmedTrace.append(t);
        confirmedCoverage.add(t.computeCoverage(false));

        selectedSequenceCount++;
        selectedEventCount += (t.size() - 1);

        plans.removePlanInfo(plan, "reportExecutionSuccessImplExact");
        plans.filterPlansByCoverage(confirmedCoverage);
        plans.sortPlans(confirmedCoverage);

        currentCoverage = new Coverage(confirmedCoverage);
        expectedFinalCoverage = Coverage.add(confirmedCoverage, plans.getExpectedCoverageSum());
    }

    @Override
    public void reportExecutionSuccessImplFlaky(LinkedList<Integer> plan, Trace resultingTrace, Coverage expectedCoverage) {
        PlanInfo p = plans.getPlanInfo(plan);
        if (p.justSynthesized) {
            p.c = Coverage.intersect(inputTraceCoverage, resultingTrace.computeCoverage(true));
            p.justSynthesized = false;
        }
        else {
            p.c = Coverage.intersect(expectedCoverage, resultingTrace.computeCoverage(true));
        }

        if (p.c.size() > 0) {
            plans.registerPlan(plan, p);
            plans.sortPlans(confirmedCoverage);
        }
        else {
            plans.removePlanInfo(plan, "reportExecutionSuccessImplFlaky");
        }

        currentCoverage = new Coverage(confirmedCoverage);
        expectedFinalCoverage = Coverage.add(confirmedCoverage, plans.getExpectedCoverageSum());
    }

    @Override
    public Plan getNextPlanImpl(AbstractUI currAbs) {
        LinkedList<Integer> pp = plans.fetchNextPlan();

        if (pp != null) {
            PlanInfo pinfo = plans.getPlanInfo(pp);
            trialCount++;
            pinfo.trial++;
            Plan p = new Plan();
            p.plan = pp;
            p.expectedCoverage = pinfo.c;
            Coverage gain = Coverage.minus(pinfo.c, confirmedCoverage);
            printIntSet(pp, "Plan to execute (" + pinfo.c.size() + ", " + pinfo.fragmentCount + ", " + gain.size() + ")", this);
            return p;
        }
        else {
            return null;
        }
    }

    @Override
    public void initializeImpl() {
        // build a recomposition graph from the input trace set
        graph.init(inputTraceSet);

        // build a set of recomposition paths

        int initialState = 1;
        SearchStep emptyPath = new SearchStep();
        emptyPath.fragmentCount = 1;
        emptyPath.accumulatedCoverage = new Coverage();

        LinkedList<SearchStep> paths = new LinkedList<>();
        paths.add(emptyPath);

        long begin = System.currentTimeMillis();
        log("Generating plans");
        prepareTransitionGroupTable();
        buildPaths(initialState, paths);


        for (int i=0; i<inputTraceSet.size(); i++) {
            Trace inputTrace = inputTraceSet.get(i);
            LinkedList<Integer> plan = PlanUtil.traceToPlan(inputTrace);

            PlanInfo pi = new PlanInfo();
            pi.c = inputTrace.computeCoverage(true);
            pi.fragmentCount = 1;

            if (plans.contains(plan)) {
                plans.updatePlanInfo(plan, pi);
            }
            else {
                plans.registerPlan(plan, pi);
            }
        }

        long end = System.currentTimeMillis();
        log(String.format("Generation take %d msec.", end-begin));

        log("Sorting plans: " + plans.remainingPlanCount());
        plans.sortPlans(new Coverage());
        expectedFinalCoverage = plans.getExpectedCoverageSum();
    }

    @Override
    public Coverage getAlreadyCovered() {
        return new Coverage(confirmedCoverage);
    }

    private ConcreteUI.CheckOption option = new ConcreteUI.CheckOption(true, true, true);

    class SearchStep {
        RecompositionGraph.Transition tr;
        SearchStep prev;

        Coverage accumulatedCoverage;
        SearchStep lastChunkEnd;
        int fragmentCount;

        boolean updatedSinceLastChunk() {
            if (lastChunkEnd == null) {
                return accumulatedCoverage.size() != 0;
            }
            else {
                return accumulatedCoverage.isGreaterThan(lastChunkEnd.accumulatedCoverage);
            }
        }

        public LinkedList<Integer> toPlan() {
            LinkedList<RecompositionGraph.Transition> result = new LinkedList<>();

            SearchStep cur = this;
            while (cur.tr != null) {
                result.addFirst(cur.tr);
                cur = cur.prev;
            }

            return RecompositionGraph.pathToPlan(result);
        }
    }

    HashMap<Integer, Collection<LinkedList<RecompositionGraph.Transition>>> trGroupTbl = new HashMap<>();

    private void prepareTransitionGroupTable() {
        for (int id=0; id<AbstractUI.count(); id++) {
            LinkedList<RecompositionGraph.Transition> trs = graph.transitionInfo.get(id);
            TreeMap<LinkedList<Integer>, LinkedList<RecompositionGraph.Transition>> trGroups = new TreeMap(PlanUtil.planListComparator);

            for (int i = 0; i < trs.size(); i++) {
                RecompositionGraph.Transition tr = trs.get(i);
                if (tr.dst.prevAction.isStart() || tr.dst.prevAction.isReset()) continue;

                LinkedList<RecompositionGraph.Transition> tempPath = new LinkedList<>();
                tempPath.addLast(tr);
                LinkedList<Integer> plan = RecompositionGraph.pathToPlan(tempPath);

                if (!trGroups.containsKey(plan)) {
                    trGroups.put(plan, new LinkedList<>());
                }
                trGroups.get(plan).add(tr);
            }

            trGroupTbl.put(id, trGroups.values());
        }
    }

    private boolean buildPaths(int currentState, LinkedList<SearchStep> prevSteps) {
        SearchStep samplePreviousStep = prevSteps.getFirst();
        boolean terminatingStep = false;
        boolean terminatingChild = false;
        boolean updateInChild = false;

        if (samplePreviousStep.tr != null) {
            terminatingStep = (samplePreviousStep.tr.dst.prevAction.isClose());
        }

        if (!terminatingStep) {
            Collection<LinkedList<RecompositionGraph.Transition>> trGroups = trGroupTbl.get(currentState);
            for (LinkedList<RecompositionGraph.Transition> trGroup : trGroups) {
                RecompositionGraph.Transition sampleNextTr = trGroup.getFirst();

                if (sampleNextTr.dst.prevAction.isClose()) {
                    terminatingChild = true;
                }

                if (samplePreviousStep.tr != null) {
                    // concrete ui should match
                    if (!ConcreteUI.checkUiDetail(samplePreviousStep.tr.dst.concreteUI, sampleNextTr.src.concreteUI, option, null)) {
                        continue;
                    }
                }

                LinkedList<SearchStep> nextSteps = new LinkedList<>();
                for (SearchStep prevStep : prevSteps) {
                    for (RecompositionGraph.Transition tr : trGroup) {
                        boolean switching = false;

                        if (prevStep.tr != null) {
                            if (!RecompositionGraph.Transition.checkContinuity(prevStep.tr, tr)) {
                                if (prevStep.fragmentCount == MAX_FRAGMENT || !prevStep.updatedSinceLastChunk()) {
                                    continue;
                                }
                                switching = true;
                            }
                        }

                        SearchStep nextStep = new SearchStep();
                        if (switching) {
                            nextStep.fragmentCount = prevStep.fragmentCount + 1;
                            nextStep.lastChunkEnd = prevStep;
                        }
                        else {
                            nextStep.fragmentCount = prevStep.fragmentCount;
                            nextStep.lastChunkEnd = prevStep.lastChunkEnd;
                        }

                        Coverage trCoverage = tr.getCoverage();
                        nextStep.accumulatedCoverage = prevStep.accumulatedCoverage.isGreaterThan(trCoverage)
                                ? prevStep.accumulatedCoverage
                                : Coverage.add(tr.getCoverage(), prevStep.accumulatedCoverage);

                        nextStep.tr = tr;
                        nextStep.prev = prevStep;
                        nextSteps.add(nextStep);

                        for (SearchStep n: nextSteps) {
                            if (nextStep.tr == n.tr && nextStep.fragmentCount == n.fragmentCount) {
                                n.accumulatedCoverage.add(nextStep.accumulatedCoverage);
                                nextStep = null;
                                break;
                            }
                        }
                        if (nextStep != null) {
                            nextSteps.add(nextStep);
                        }
                    }
                }

                if (!nextSteps.isEmpty()) {
                    updateInChild = buildPaths(sampleNextTr.dst.abstractUI.id(), nextSteps) || updateInChild;
                }
            }
        }

        if (!terminatingChild && samplePreviousStep.tr != null) {
            Coverage cc = new Coverage();
            int maxFragment = 0;
            for (SearchStep prevstep : prevSteps) {
                if (prevstep.updatedSinceLastChunk()) {
                    cc.add(prevstep.accumulatedCoverage);
                    maxFragment = Math.max(maxFragment, prevstep.fragmentCount);
                }
            }
            if (cc.size() != 0) {
                LinkedList<Integer> plan = samplePreviousStep.toPlan();
                PlanInfo planInfo = new PlanInfo();
                planInfo.c = cc;
                planInfo.fragmentCount = maxFragment;
                plans.registerPlan(plan, planInfo);
                return true;
            }
        }
        return updateInChild;
    }


    @Override
    public void intermediateDump(int id) {
        super.intermediateDump(id);

        HistoryManager hm = HistoryManager.instance();
        Coverage trialGain = Coverage.minus(currentCoverage, confirmedCoverage);

        dumpCoverage("Replay", currentCoverage);
        dumpCoverage("Replay:Confirmed", confirmedCoverage);
        dumpCoverage("Replay:Expected", expectedFinalCoverage);
        dumpCoverage("Replay:Plan:Mini:Trial", trialGain);
        dumpCoverage("Trace", inputTraceCoverage);

        hm.periodStat("Replay:Plan:Mini:#Trials.", trialCount);
        hm.periodStat("Replay:Plan:Mini:#Skipped Candidates FP", skippedCandidateCountFP);
        hm.periodStat("Replay:Plan:Mini:#Skipped Events FP", skippedCandidateEventCountFP);
        hm.periodStat("Replay:Plan:Mini:#Skipped Candidates CV", skippedCandidateCountCV);
        hm.periodStat("Replay:Plan:Mini:#Skipped Events CV", skippedCandidateEventCountCV);
        hm.periodStat("Replay:Plan:#Selected Seq.", selectedSequenceCount);
        hm.periodStat("Replay:Plan:#Selected Event", selectedEventCount);

        hm.periodStat("Trace:#Event", inputTraceProfiler.events);
        hm.periodStat("Trace:#Seq.", inputTraceProfiler.traces);    }


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
