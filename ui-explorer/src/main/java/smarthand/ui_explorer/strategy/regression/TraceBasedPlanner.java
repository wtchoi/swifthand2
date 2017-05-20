package smarthand.ui_explorer.strategy.regression;

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

    class Plan {
        LinkedList<Integer> plan;
        Coverage expectedCoverage;
    }

    public abstract void initializeImpl();
    public abstract void reportExecutionFailureImpl(LinkedList<Integer> plan, LinkedList<Integer> failingPrefix, Trace resultingTrace, Coverage expectedCoverage);
    public abstract void reportExecutionSuccessImplExact(LinkedList<Integer> plan, Trace resultingTrace, Coverage expectedCoverage);
    public abstract void reportExecutionSuccessImplFlaky(LinkedList<Integer> plan, Trace resultingTrace, Coverage expectedCoverage);
    public abstract Plan getNextPlanImpl(AbstractUI currAbs);

    protected Trace inputTrace;
    protected SequenceSelector.Profiler inputTraceProfiler;

    protected Coverage inputTraceCoverage = new Coverage(); //without starting coverage

    protected LinkedList<Trace> inputTraceSet;

    protected LinkedList<LinkedList<Integer>> failingPrefixes = new LinkedList<>();

    public TraceBasedPlanner(String inputTracePath, int stabilizationTh) {
        this.stabilizationThreshold = stabilizationTh;
        inputTrace = Trace.fromJson(Util.readJsonFile(inputTracePath));
        inputTraceProfiler = new SequenceSelector.Profiler();
        SequenceSelector sequenceSelector = new SequenceSelector(false, false, this);
        sequenceSelector.addObserver(inputTraceProfiler);
        sequenceSelector.load(inputTrace);

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

        LinkedList<Integer> failingPrefix = PlanUtil.getFailingPrefix(resultingTrace, plan);
        failingPrefixes.addLast(failingPrefix);

        printIntSet(plan, "intended plan", this);
        printIntSet(PlanUtil.traceToPlan(resultingTrace), "resulting trace", this);
        printIntSet(failingPrefix, "failing prefix", this);

        this.reportExecutionFailureImpl(plan, failingPrefix, resultingTrace, currentPlan.expectedCoverage);

        this.currentPlan = null;
        this.repeatCount = 0;
    }

    @Override
    public final void reportExecutionSuccess(LinkedList<Integer> plan, Trace resultingTrace) {
        if (plan != currentPlan.plan) {
            throw new RuntimeException("something is wrong");
        }

        Coverage resultCoverage = resultingTrace.computeCoverage(true);
        log("Expected:" + currentPlan.expectedCoverage.size() + ", Actual:" + resultCoverage.size() + ", Missing: "
                + Coverage.minus(currentPlan.expectedCoverage, resultCoverage).size() +  ", New: " + Coverage.minus(resultCoverage, currentPlan.expectedCoverage).size());

        if (resultCoverage.isGreaterOrEqual(currentPlan.expectedCoverage)){
            this.repeatCount++;
            log("Plan executed successfully");
            log("Repetition count:" + repeatCount);

            if (repeatCount >= stabilizationThreshold) {
                log("Plan confirmed");
                this.reportExecutionSuccessImplExact(plan, resultingTrace, currentPlan.expectedCoverage);
                this.currentPlan = null;
                this.repeatCount = 0;
            }
        }
        else {
            log("Plan has flaky test coverage. Will be tried latter with a lower expectation");
            this.reportExecutionSuccessImplFlaky(plan, resultingTrace, currentPlan.expectedCoverage);
            this.currentPlan = null;
            this.repeatCount = 0;
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
}


