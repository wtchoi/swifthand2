package smarthand.ui_explorer.strategy.regression;

import smarthand.ui_explorer.HistoryManager;
import smarthand.ui_explorer.strategy.Planner;
import smarthand.ui_explorer.trace.*;
import smarthand.ui_explorer.util.Util;

import java.util.LinkedList;

/**
 * Created by wtchoi on 2/23/17.
 */


public abstract class TraceBasedPlanner extends Planner {
    final int stabilizationThreshold;

    private Plan currentPlan;
    private int repeatCount = 0;
    private int editTextFailureCount = 0;
    private int editTextFailureRecovered = 0;
    private int scrollFailureCount = 0;
    private int scrollFailureRecovered = 0;
    private int ignoredEventCount = 0;
    private int ignoredEventRecovered = 0;
    private int eventFailureCount = 0;
    private int frequentFlakyCoverageCount = 0;
    private int frequentFlakyCoverageRecovered = 0;
    private int flakyCoverageCount = 0;
    private int flakyCoverageTolerance = 2;

    private boolean tollerateFlakyCoverage = false;

    LinkedList<FlakyCoverageInfo> flakyCoverageInfo = new LinkedList();

    protected int[] ndHistogram = null;
    protected int[] screenNDHistogram = null;

    class Plan {
        LinkedList<Integer> plan = null;
        Coverage expectedCoverage = null;
    }

    class FlakyCoverageInfo{
        Coverage c = null;
        int occurrence = 0;

        FlakyCoverageInfo(Coverage c, int o) {
            this.c = c;
            this.occurrence = o;
        }
    }

    public abstract void initializeImpl();
    public abstract void reportExecutionFailureImpl(LinkedList<Integer> plan, LinkedList<Integer> failingPrefix, Trace resultingTrace, Coverage expectedCoverage);
    public abstract void reportExecutionSuccessImplExact(LinkedList<Integer> plan, Trace resultingTrace, Coverage expectedCoverage);
    public abstract void reportExecutionSuccessImplFlaky(LinkedList<Integer> plan, Trace resultingTrace, Coverage expectedCoverage);
    public abstract Plan getNextPlanImpl(AbstractUI currAbs);

    protected abstract Coverage getAlreadyCovered();

    protected Trace inputTrace;
    protected SequenceSelector.Profiler inputTraceProfiler;

    protected Coverage inputTraceCoverage = new Coverage(); //without starting coverage

    protected LinkedList<Trace> inputTraceSet;

    protected LinkedList<LinkedList<Integer>> failingPrefixes = new LinkedList<>();

    public TraceBasedPlanner(String inputTracePath, int stabilizationTh) {
        this.stabilizationThreshold = stabilizationTh;

        if ( stabilizationTh < 4) {
            this.flakyCoverageTolerance = 1;
        }

        inputTrace = Trace.fromJson(Util.readJsonFile(inputTracePath));
        inputTraceProfiler = new SequenceSelector.Profiler();
        SequenceSelector sequenceSelector = new SequenceSelector(false, false, this);
        sequenceSelector.addObserver(inputTraceProfiler);
        sequenceSelector.load(inputTrace);

        ndHistogram = new int[stabilizationTh];
        screenNDHistogram = new int[stabilizationTh];
        for (int i=0; i<stabilizationTh; i++) {
            ndHistogram[i] = 0;
            screenNDHistogram[i] = 0;
        }

        inputTraceSet = new LinkedList();
        while(true) {
            Trace miniTrace = sequenceSelector.getNextSequence();
            if (miniTrace == null) break;
            inputTraceSet.addLast(miniTrace);
            inputTraceCoverage.add(miniTrace.computeCoverage(false));
        }
    }

    //Implementing Planner Interface
    @Override
    public final void initialize() {
        this.initializeImpl();
    }

    @Override
    public final void reportExecutionFailure(LinkedList<Integer> plan, Trace resultingTrace) {
        log("Plan deviates!");

        boolean scrollFail = false;
        boolean editFail = false;
        boolean eventIgnored = false;
        if (resultingTrace.size() > 1) {
            Trace.Snapshot lastSnapshot = resultingTrace.get(resultingTrace.size() - 1);
            Trace.Snapshot prevSnapshot = resultingTrace.get(resultingTrace.size() - 2);
            Action lastAction = lastSnapshot.prevAction;

            scrollFail = lastAction.isEvent() && prevSnapshot.abstractUI.getEvent(lastAction.actionIndex).contains("scroll");
            if (scrollFail) {
                this.scrollFailureCount++;
                this.eventFailureCount++;
            }

            editFail = lastAction.isEvent() && prevSnapshot.abstractUI.getEvent(lastAction.actionIndex).contains("edit");
            if (editFail) {
                this.editTextFailureCount++;
                this.eventFailureCount++;
            }

            eventIgnored = lastSnapshot.abstractUI.id() == prevSnapshot.abstractUI.id();
            if (eventIgnored) {
                this.ignoredEventCount++;
                this.eventFailureCount++;
            }
        }

        if ((!editFail && !scrollFail) || eventFailureCount > Math.max(2, (this.stabilizationThreshold  / 3))){
            LinkedList<Integer> failingPrefix = PlanUtil.getFailingPrefix(resultingTrace, plan);
            failingPrefixes.addLast(failingPrefix);

            printIntSet(plan, "intended plan", this);
            printIntSet(PlanUtil.traceToPlan(resultingTrace), "resulting trace", this);
            printIntSet(failingPrefix, "failing prefix", this);

            this.reportExecutionFailureImpl(plan, failingPrefix, resultingTrace, currentPlan.expectedCoverage);

            this.ndHistogram[repeatCount]++;
            this.screenNDHistogram[repeatCount]++;
            this.currentPlan = null;
            this.repeatCount = 0;
            this.scrollFailureCount = 0;
            this.editTextFailureCount = 0;
            this.ignoredEventCount = 0;
            this.eventFailureCount = 0;
            this.flakyCoverageCount = 0;
        }
    }

    @Override
    public final void reportExecutionSuccess(LinkedList<Integer> plan, Trace resultingTrace) {
        if (plan != currentPlan.plan) {
            throw new RuntimeException("something is wrong");
        }

        Coverage resultCoverage = resultingTrace.computeCoverage(true);
        Coverage missingCoverage = Coverage.minus(currentPlan.expectedCoverage, resultCoverage);
        log("Expected:" + currentPlan.expectedCoverage.size() + ", Actual:" + resultCoverage.size() + ", Missing: "
                + missingCoverage.size() +  ", New: " + Coverage.minus(resultCoverage, currentPlan.expectedCoverage).size());

        boolean frequentlyMissed = false;
        boolean coverageMissed = !resultCoverage.isGreaterOrEqual(currentPlan.expectedCoverage);
        boolean importantCoverageMissed = !this.getAlreadyCovered().isGreaterOrEqual(missingCoverage);

        //if (coverageMissed){
        if (importantCoverageMissed) {
            printIntSet(missingCoverage.branchCoverage, "missing branches", this);
            printIntSet(missingCoverage.methodCoverage, "missing methods", this);

            flakyCoverageCount++;
            if (!tollerateFlakyCoverage || flakyCoverageCount > flakyCoverageTolerance) {
                this.ndHistogram[repeatCount]++;
                log("Plan has flaky test coverage.");
                log("Will be tried latter with a lower expectation");
                this.reportExecutionSuccessImplFlaky(plan, resultingTrace, currentPlan.expectedCoverage);
                this.currentPlan = null;
                this.repeatCount = 0;
                this.scrollFailureCount = 0;
                this.editTextFailureCount = 0;
                this.ignoredEventCount = 0;
                this.eventFailureCount = 0;
                this.flakyCoverageCount = 0;
                return;
            }
        }
        else {
            this.repeatCount++;
            log("Plan executed successfully");
            log("Repetition count:" + repeatCount);

            if (repeatCount >= stabilizationThreshold) {
                if (scrollFailureCount > 0) {
                    scrollFailureRecovered++;
                }
                if (editTextFailureCount > 0) {
                    editTextFailureRecovered++;
                }
                if (ignoredEventCount > 0) {
                    ignoredEventRecovered++;
                }
                if (flakyCoverageCount > 0) {
                    frequentFlakyCoverageRecovered++;
                }

                log("Plan confirmed");
                this.reportExecutionSuccessImplExact(plan, resultingTrace, currentPlan.expectedCoverage);
                this.currentPlan = null;
                this.repeatCount = 0;
                this.scrollFailureCount = 0;
                this.editTextFailureCount = 0;
                this.ignoredEventCount = 0;
                this.eventFailureCount = 0;
                this.flakyCoverageCount = 0;
            }
        }
    }

    @Override
    public final LinkedList<Integer> getNextPlan(AbstractUI currAbs) {
        if (currentPlan == null) {
            currentPlan = getNextPlanImpl(currAbs);
        }

        if (currentPlan == null) {
            log("No more plan");
            return null;
        }
        else {
            printIntSet(currentPlan.plan, "Plan to execute (" + currentPlan.expectedCoverage.size() + ")", this);
            return currentPlan.plan;
        }
    }

    @Override
    public void intermediateDump(int id) {
        HistoryManager hm = HistoryManager.instance();
        for (int i=0; i<stabilizationThreshold; i++) {
            hm.periodStat("Replay:ND[" + (i+1) + "]", this.ndHistogram[i]);
            hm.periodStat("Replay:NDS[" + (i+1) + "]", this.screenNDHistogram[i]);
        }

        hm.periodStat("Planner:ScrollFailurRecovered", scrollFailureRecovered);
        hm.periodStat("Planner:EditFailureRecovered", editTextFailureRecovered);
        hm.periodStat("Planner:IgnoredEventRecored", ignoredEventRecovered);
        hm.periodStat("Planner:FlakyCoverageRecovered", frequentFlakyCoverageRecovered);
        hm.periodStat("Planner:FlakyCoverageSetCount", frequentFlakyCoverageCount);
    }
}


