package smarthand.ui_explorer.strategy.regression;

import smarthand.ui_explorer.DeviceInfo;
import smarthand.ui_explorer.HistoryManager;
import smarthand.ui_explorer.Options;
import smarthand.ui_explorer.strategy.C;
import smarthand.ui_explorer.trace.AbstractUI;
import smarthand.ui_explorer.trace.ConcreteUI;
import smarthand.ui_explorer.trace.Coverage;
import smarthand.ui_explorer.trace.Trace;
import smarthand.ui_explorer.util.Util;

import java.security.SecureRandom;
import java.util.*;

/**
 * Created by wtchoi on 4/6/17.
 */
public class SamplingPlanner extends TraceBasedPlanner {

    private boolean strictSearch = true;
    private boolean justSampling = false;

    private int SEARCH_DELTA = 10;
    private int OVERSAMPLING= 10;
    private int MIN_LENGTH = 7;
    private int MAX_LENGTH = 20;


    private final int MAX_FRAGMENT;
    private final int SAMPLE;

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

    int droppedSequenceCount = 0;

    int[] fragmentFrequency = new int[10000];

    SecureRandom rand = new SecureRandom();

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

    public SamplingPlanner(String inputTracePath, int stabilizationTh, int bound, int sampleCount) {
        super(inputTracePath, stabilizationTh);
        MAX_FRAGMENT = bound;
        SAMPLE = sampleCount;

        rand.setSeed(0);

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
        //plans.removePlanInfo(plan);
        currentCoverage = new Coverage(confirmedCoverage);
        expectedFinalCoverage = Coverage.add(confirmedCoverage, plans.getExpectedCoverageSum());
        droppedSequenceCount++;
    }



    @Override
    public void reportExecutionSuccessImplExact(LinkedList<Integer> plan, Trace resultingTrace, Coverage expectedCoverage) {
        Trace t = Trace.trim(resultingTrace, expectedCoverage, true);
        confirmedTrace.append(t);
        confirmedCoverage.add(t.computeCoverage(false));

        selectedSequenceCount++;
        selectedEventCount += (t.size() - 1);

        //plans.removePlanInfo(plan);
        currentCoverage = new Coverage(confirmedCoverage);
        expectedFinalCoverage = Coverage.add(confirmedCoverage, plans.getExpectedCoverageSum());
    }

    @Override
    public void reportExecutionSuccessImplFlaky(LinkedList<Integer> plan, Trace resultingTrace, Coverage expectedCoverage) {
        PlanInfo p = plans.getPlanInfo(plan);
        p.c = Coverage.intersect(expectedCoverage, resultingTrace.computeCoverage(true));

        if (p.c.size() > 0) {
            plans.registerPlan(plan, p);
        }
//        else {
//            plans.removePlanInfo(plan);
//        }

        currentCoverage = new Coverage(confirmedCoverage);
        expectedFinalCoverage = Coverage.add(confirmedCoverage, plans.getExpectedCoverageSum());
    }

    @Override
    public Plan getNextPlanImpl(AbstractUI currAbs) {
        LinkedList<Integer> pp = plans.fetchNextPlan();

        if (pp != null) {
            trialCount++;
            PlanInfo pinfo = plans.getPlanInfo(pp);
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
        emptyPath.depth = 0;

        LinkedList<SearchStep> paths = new LinkedList<>();
        paths.add(emptyPath);

        long begin = System.currentTimeMillis();
        log("Generating plans (over sampling)");
        prepareTransitionGroupTable();

        if (MAX_FRAGMENT > 1) {
            int prevCount = 0;
            while (plans.remainingPlanCount() <= SAMPLE * OVERSAMPLING) {
                buildPaths(initialState, paths, prevCount, MIN_LENGTH, MAX_LENGTH);
                prevCount = plans.remainingPlanCount();
                log ("Progress: " + prevCount);
            }
        }

        if (MAX_FRAGMENT == 1 || !strictSearch) {
            for (int i = 0; i < inputTraceSet.size(); i++) {
                Trace inputTrace = inputTraceSet.get(i);
                LinkedList<Integer> plan = PlanUtil.traceToPlan(inputTrace);

                PlanInfo pi = new PlanInfo();
                pi.c = inputTrace.computeCoverage(true);
                pi.fragmentCount = 1;

                if (plans.contains(plan)) {
                    plans.updatePlanInfo(plan, pi);
                } else {
                    plans.registerPlan(plan, pi);
                }
            }
        }

        long end = System.currentTimeMillis();
        log(String.format("Generation %d plans, taking %d msec.", plans.remainingPlanCount(), end-begin));

        log ("Sampling");
        plans.sample(SAMPLE, rand);
        log ("Sampling Finished");

        for (int i=0;i<plans.plans.size(); i++) {
            PlanInfo planInfo = plans.getPlanInfo(plans.plans.get(i));
            fragmentFrequency[planInfo.fragmentCount]++;
        }

        log ("Frequency info");
        for (int i=0; i<=MAX_FRAGMENT; i++) {
            log ("frequecy (" + i + "):" + fragmentFrequency[i]);
        }

        expectedFinalCoverage = plans.getExpectedCoverageSum();

        if (justSampling) {
            System.exit(0);
        }
    }

    private ConcreteUI.CheckOption option = new ConcreteUI.CheckOption(true, true, true);

    class SearchStep {
        RecompositionGraph.Transition tr;
        SearchStep prev;
        int depth;

        int fragmentCount;

        public LinkedList<Integer> toPlan() {
            LinkedList<RecompositionGraph.Transition> result = new LinkedList<>();

            SearchStep cur = this;
            while (cur.tr != null) {
                result.addFirst(cur.tr);
                cur = cur.prev;
            }

            return RecompositionGraph.pathToPlan(result);
        }

        public Coverage computeCoverage() {
            Coverage result = new Coverage();

            SearchStep cur = this;
            while (cur.tr != null) {
                result.add(cur.tr.getCoverage());
                cur = cur.prev;
            }
            return result;
        }
    }

    HashMap<Integer, LinkedList<LinkedList<RecompositionGraph.Transition>>> trGroupTbl = new HashMap<>();

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

            trGroupTbl.put(id, new LinkedList(trGroups.values()));
        }
    }

    private boolean buildPaths(int currentState, LinkedList<SearchStep> prevSteps, int prevPlanCount, int minLength, int maxLength) {
        if (plans.remainingPlanCount() - prevPlanCount >= SEARCH_DELTA) return false;

        SearchStep samplePreviousStep = prevSteps.getFirst();
        if (samplePreviousStep.depth >= maxLength) {
            return false;
        }

        boolean terminatingStep = false;
        boolean updateInChild = false;
        boolean storeCurrentPlan = false;

        if (samplePreviousStep.tr != null) {
            terminatingStep = (samplePreviousStep.tr.dst.prevAction.isClose());
        }

        if (!terminatingStep) {
            boolean terminatingChild = false;
            LinkedList<LinkedList<RecompositionGraph.Transition>> trGroups = trGroupTbl.get(currentState);
            for (LinkedList<RecompositionGraph.Transition> trGroup: trGroups) {
                RecompositionGraph.Transition sampleNextTr = trGroup.getFirst();
                if (sampleNextTr.dst.prevAction.isClose()) {
                    terminatingChild = true;
                    break;
                }
            }

            if (!terminatingChild && samplePreviousStep.tr != null) {
                LinkedList<Integer> plan = samplePreviousStep.toPlan();
                if (plan.size() >= minLength) {
                    int fragment = MAX_FRAGMENT;
                    for (SearchStep pv: prevSteps) {
                        fragment = Math.min(pv.fragmentCount, fragment);
                    }

                    if (fragment == MAX_FRAGMENT || (!strictSearch && fragment < MAX_FRAGMENT)) {
                        PlanInfo planInfo = new PlanInfo();
                        planInfo.fragmentCount = samplePreviousStep.fragmentCount;
                        planInfo.c = samplePreviousStep.computeCoverage();
                        plans.registerPlan(plan, planInfo);
                        storeCurrentPlan = true;
                        if (plans.remainingPlanCount() - prevPlanCount >= SEARCH_DELTA) return true;
                    }
                }
            }
        }

        if (!terminatingStep) {
            LinkedList<LinkedList<RecompositionGraph.Transition>> trGroups = trGroupTbl.get(currentState);
            int[] permutation1 = Util.permutation(trGroups.size(), rand);

            for (int indexTrGroup: permutation1) {
                LinkedList<RecompositionGraph.Transition> trGroup = trGroups.get(indexTrGroup);
                RecompositionGraph.Transition sampleNextTr = trGroup.getFirst();

                if (samplePreviousStep.tr != null) {
                    // concrete ui should match
                    if (!ConcreteUI.checkUiDetail(samplePreviousStep.tr.dst.concreteUI, sampleNextTr.src.concreteUI, option, null)) {
                        continue;
                    }
                }

                LinkedList<SearchStep> nextSteps = new LinkedList<>();
                int[] permutation2 = Util.permutation(prevSteps.size(), rand);
                int[] permutation3 = Util.permutation(trGroup.size(), rand);

                for (int indexPrevStep: permutation2) {
                    SearchStep prevStep = prevSteps.get(indexPrevStep);

                    for (int indexTr: permutation3) {
                        RecompositionGraph.Transition tr = trGroup.get(indexTr);
                        boolean switching = false;

                        if (prevStep.tr != null) {
                            if (!RecompositionGraph.Transition.checkContinuity(prevStep.tr, tr)) {
                                if (prevStep.fragmentCount == MAX_FRAGMENT) { // || !prevStep.updatedSinceLastChunk()) {
                                    continue;
                                }
                                switching = true;
                            }
                        }

                        SearchStep nextStep = new SearchStep();
                        if (switching) {
                            nextStep.fragmentCount = prevStep.fragmentCount + 1;
                        }
                        else {
                            nextStep.fragmentCount = prevStep.fragmentCount;
                        }

                        nextStep.depth = prevStep.depth+1;
                        nextStep.tr = tr;
                        nextStep.prev = prevStep;

                        for (SearchStep n: nextSteps) {
                            if (nextStep.tr == n.tr && nextStep.fragmentCount == n.fragmentCount) {
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
                    updateInChild = buildPaths(sampleNextTr.dst.abstractUI.id(), nextSteps, prevPlanCount, minLength, maxLength) || updateInChild;
                    if (plans.remainingPlanCount() - prevPlanCount >= SEARCH_DELTA) return updateInChild;
                }
            }
        }

        return storeCurrentPlan || updateInChild;
    }


    @Override
    public void intermediateDump(int id) {
        HistoryManager hm = HistoryManager.instance();

        hm.periodStat("Replay:#Screen", currentCoverage.screenCoverage.size());
        hm.periodStat("Replay:#Branch", currentCoverage.branchCoverage.size());
        hm.periodStat("Replay:#Method", currentCoverage.methodCoverage.size());

        Coverage trialGain = Coverage.minus(currentCoverage, confirmedCoverage);
        hm.periodStat("Replay:Plan:Mini:Trial:#Method", trialGain.methodCoverage.size());
        hm.periodStat("Replay:Plan:Mini:Trial:#Branch", trialGain.branchCoverage.size());

        hm.periodStat("Replay:Confirmed:#Screen", confirmedCoverage.screenCoverage.size());
        hm.periodStat("Replay:Confirmed:#Branch", confirmedCoverage.branchCoverage.size());
        hm.periodStat("Replay:Confirmed:#Method", confirmedCoverage.methodCoverage.size());

        hm.periodStat("Replay:Plan:Mini:#Trials.", trialCount);
        hm.periodStat("Replay:Plan:Mini:#Skipped Candidates FP", skippedCandidateCountFP);
        hm.periodStat("Replay:Plan:Mini:#Skipped Events FP", skippedCandidateEventCountFP);
        hm.periodStat("Replay:Plan:Mini:#Skipped Candidates CV", skippedCandidateCountCV);
        hm.periodStat("Replay:Plan:Mini:#Skipped Events CV", skippedCandidateEventCountCV);
        hm.periodStat("Replay:Plan:#Selected Seq.", selectedSequenceCount);
        hm.periodStat("Replay:Plan:#Selected Event", selectedEventCount);

        hm.periodStat("Replay:Plan:#Dropped Seq.", droppedSequenceCount);

        hm.periodStat("Replay:Expected:#Screen", expectedFinalCoverage.screenCoverage.size());
        hm.periodStat("Replay:Expected:#Branch", expectedFinalCoverage.branchCoverage.size());
        hm.periodStat("Replay:Expected:#Method", expectedFinalCoverage.methodCoverage.size());

        hm.periodStat("Trace:#Screen", inputTraceCoverage.screenCoverage.size());
        hm.periodStat("Trace:#Branch", inputTraceCoverage.branchCoverage.size());
        hm.periodStat("Trace:#Method", inputTraceCoverage.methodCoverage.size());
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
