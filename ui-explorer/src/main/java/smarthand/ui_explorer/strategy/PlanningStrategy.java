package smarthand.ui_explorer.strategy;

import smarthand.ui_explorer.*;
import smarthand.ui_explorer.trace.*;
import smarthand.ui_explorer.util.Util;

import java.util.LinkedList;

/**
 * Created by wtchoi on 3/9/17.
 */
public class PlanningStrategy extends Strategy {

    private final Planner planner;

    public PlanningStrategy(Planner str) {
        this.planner = str;
        currentCoverage = new Coverage();
        currentMiniCoverage = new Coverage();
    }

    // misc. options
    private boolean takeEveryScreenshot = false;

    private int consecutiveReset = 0;
    private LinkedList<Integer> plan;
    private int planIndex;

    private boolean deviationFlag = false;
    private Integer lastAction;

    private AbstractUI currAbs;
    private AbstractUI prevAbs;

    private Coverage currentCoverage;
    private Coverage currentMiniCoverage;
    private int deviationCount = 0;

    private Tracer miniTracer = new Tracer();   // to record mini trace
    private Tracer tracer = new Tracer();       // to record whole trace


    //Implementing Strategy Interface
    @Override
    public void reportExecution(DeviceInfo deviceInfo, Coverage coverage, boolean escaped, boolean blocked) {
        prevAbs = currAbs;

        String currentActivity = (deviceInfo.activityStack.size() == 0)
                ? "null"
                : deviceInfo.activityStack.getLast();

        currAbs = (escaped || blocked)
                ? AbstractUI.getFailState()
                : AbstractUI.getState(currentActivity, deviceInfo.isKeyboardShown, deviceInfo.filteredEvents, deviceInfo.eventInfo);

        if (takeEveryScreenshot && HistoryManager.instance().getCurrentPeriod() != currAbs.getSnapshotID()) {
            HistoryManager.instance().takeSnapshot();
        }

        log(String.format("Abstract State ID: %s", currAbs.id()));

        Coverage coverageInfo = new Coverage(coverage.methodCoverage, coverage.branchCoverage, currAbs.id());
        log(String.format("Coverage m:%d b:%d", coverage.methodCoverage.size(), coverage.branchCoverage.size()));
        currentCoverage.add(coverageInfo);
        currentMiniCoverage.add(coverageInfo);

        boolean justInitialized = false;
        if (lastAction == null) {
            lastAction = C.CLOSE;
            planner.initialize();
            justInitialized = true;
        }

        this.planner.reportIntermediateStep(lastAction, deviceInfo, currAbs, coverageInfo, escaped, blocked);

        ConcreteUI currentCui = ConcreteUI.getFromRawInfo(deviceInfo.appGuiTree, currAbs);
        Action tty;

        if (lastAction.equals(C.START)) {
            tty = Action.getStart();
        }
        else if (lastAction.equals(C.CLOSE)) {
            tty = Action.getClose();
        }
        else {
            tty = Action.getEvent(lastAction);
        }

        if (!justInitialized) {
            tracer.on(tty, currentActivity, deviceInfo.isKeyboardShown, currentCui, currAbs, coverage.branchCoverage, coverage.methodCoverage, null);
            miniTracer.on(tty, currentActivity, deviceInfo.isKeyboardShown, currentCui, currAbs, coverage.branchCoverage, coverage.methodCoverage, null);
            log("trace size:" + miniTracer.getTrace().size());
        }

        if (plan != null) {
            int nextAbsIndex = (planIndex+1) * 2;
            int expectedAbsUid = plan.get(nextAbsIndex);

            if ((expectedAbsUid == C.WILDCARD || expectedAbsUid == currAbs.id()) && (nextAbsIndex+1 < plan.size())) {
                // progress plan
                planIndex++;
            } else {
                // need to stop execution
                if (expectedAbsUid != C.WILDCARD && expectedAbsUid != currAbs.id()) {
                    // execution deviated
                    log("Deviation!");
                    log("Expected key: " + AbstractUI.getStateById(expectedAbsUid).getKey());
                    log("Expected activity: " + AbstractUI.getStateById(expectedAbsUid).getActivityName());
                    log("Available key: " + currAbs.getKey());
                    log("Available activity: " + currAbs.getKey());

                    deviationFlag = true;
                    deviationCount++;
                    consecutiveReset = 0;
                    planner.reportExecutionFailure(plan, miniTracer.getTrace());
                }
                else if (lastAction.equals(C.START) && escaped) {
                    // application escaped
                    consecutiveReset++;
                    if (consecutiveReset > 3) {
                        throw new RuntimeException("Something is wrong");
                    }
                    deviationFlag = true;
                }
                else {
                    consecutiveReset = 0;
                    if (!(plan.size() == 3 && (lastAction.equals(C.START) || lastAction.equals(C.CLOSE)))) {
                        planner.reportExecutionSuccess(plan, miniTracer.getTrace());
                    }
                }

                plan = null;
                planIndex = 0;

                if (lastAction.equals(C.CLOSE)) {
                    miniTracer.clear();
                    currentMiniCoverage = new Coverage();
                }
            }
        }
    }

    @Override
    public String getNextAction() {
        // There is no plan. Create a new one.
        if (plan == null) {
            log("Create new plan");
            if (deviationFlag) {
                plan = new LinkedList<>();
                plan.addLast(C.WILDCARD);
                plan.addLast(C.CLOSE);
                plan.addLast(C.WILDCARD);
                deviationFlag = false;
            }
            else if (lastAction == C.CLOSE) {
                plan = new LinkedList<>();
                plan.addLast(C.WILDCARD);
                plan.addLast(C.START);
                plan.addLast(C.WILDCARD);
            }
            else {
                plan = planner.getNextPlan(currAbs);
                if (plan == null) {
                    return null;
                }
            }
            log("new plan");
            printIntSet(plan, "Plan", this);
        }

        // Now, return the planned action
        int action = plan.get(planIndex*2+1);
        lastAction = action;

        if (action == C.CLOSE) {
            return "close";
        }
        else if (action == C.START) {
            return "start";
        }
        else {
            return "event:" + action;
        }
    }

    @Override
    public void intermediateDump(int id) {
        HistoryManager hm = HistoryManager.instance();
        hm.periodStat("PlanningStrategy:#Deviation", deviationCount);
        hm.periodStat("PlanningStrategy:#Screen", currentCoverage.screenCoverage.size());
        hm.periodStat("PlanningStrategy:#Branch", currentCoverage.branchCoverage.size());
        hm.periodStat("PlanningStrategy:#Method", currentCoverage.methodCoverage.size());

        hm.periodStat("PlanningStrategy:Mini:#Screen", currentMiniCoverage.screenCoverage.size());
        hm.periodStat("PlanningStrategy:Mini.#Branch", currentMiniCoverage.branchCoverage.size());
        hm.periodStat("PlanningStrategy:Mini.#Method", currentMiniCoverage.methodCoverage.size());
        planner.intermediateDump(id);
    }

    @Override
    public void finalDump() {
        String output_dir = Options.get(Options.Keys.OUTPUT_DIR);
        Util.writeJsonFile(output_dir + "/trace.json", tracer.getTrace().toJson());
        planner.finalDump();
    }

    @Override
    public String getName() {
        return planner.getName();
    }

    @Override
    public String getDetailedExplanation() {
        return getName();
    }

    @Override
    public void setRandomSeed(int randomSeed) {
        planner.setRandomSeed(randomSeed);
    }

    // Indicate whether the strategy requires the underlying client to automatically
    // restart the target application when the app is getting stuck.
    @Override
    public boolean requiresAutoRestart() {
        return false;
    }
}