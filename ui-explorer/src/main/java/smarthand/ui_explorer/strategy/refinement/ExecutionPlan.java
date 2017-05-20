package smarthand.ui_explorer.strategy.refinement;

import smarthand.ui_explorer.util.Util;

import java.util.LinkedList;
import java.util.Random;

/**
 * Created by wtchoi on 8/15/16.
 */
class ExecutionPlan {
    // planEncoding is an integer list with an odd length.
    // Even elements are augmented-ui identifiers and odd elements
    // are event index. For example, list [10, 1, 11, 2, 12] indicates
    // an execution plan starts from an app state with the augmented-ui with
    // identifier 10, executes the first enabled event of the state, moves
    // to another app state with the augmented-ui with identifier 11, and so on.
    private LinkedList<Integer> planEncoding = null;
    private int executionIndex = 0;
    private boolean deviated = false;
    private boolean finished = false;
    private boolean isResetPlan = false;
    private boolean isNDPlan = false;

    private static final int SPECIAL_EVENT_BEGIN = 111111;
    private static final int RESET_EVENT = 888888;

    private ExecutionPlan(LinkedList<Integer> planEncoding) {
        this.planEncoding = planEncoding;
    }

    public static ExecutionPlan getExecutionPlan(LinkedList<Integer> encoding) {
        return new ExecutionPlan(encoding);
    }

    public static ExecutionPlan getResetPlan(AugmentedAbstractUI augmentedUi) {
        LinkedList<Integer> encoding = new LinkedList<>();
        encoding.addLast(augmentedUi.id());
        encoding.addLast(RESET_EVENT);
        ExecutionPlan plan =  new ExecutionPlan(encoding);
        plan.isResetPlan = true;
        return plan;
    }

    public static ExecutionPlan getRandomPlan(AugmentedAbstractUI augmentedAbstractUI, Random rand) {
        LinkedList<Integer> encoding = new LinkedList<>();
        encoding.addLast(augmentedAbstractUI.id());
        encoding.addLast(rand.nextInt(augmentedAbstractUI.abstractUi.getEventCount()));
        return new ExecutionPlan(encoding);
    }

    public boolean checkAndProgress(AugmentedAbstractUI observation) {
        if (hasNextEvent()) {
            if (!observation.equals(getExpectedAugmentedUI())) {
                deviated = true;
                return false;
            }
            executionIndex += 2;
        }
        else {
            finished = true;
        }
        return true;
    }

    public boolean hasNextEvent() {
        return executionIndex + 2 < planEncoding.size();
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean isDeviated() {
        return deviated;
    }

    public boolean isResetPlan() { return isResetPlan; }

    public AugmentedAbstractUI getBaseAugmentedUI() {
        return AugmentedAbstractUI.getById(planEncoding.get(executionIndex));
    }

    public int getEventIndexToExecute() {
        return planEncoding.get(executionIndex+1);
    }

    public static String getAction(AugmentedAbstractUI state, int eventIndex) {
        if (eventIndex > SPECIAL_EVENT_BEGIN) {
            switch (eventIndex) {
                case RESET_EVENT: return "reset";
                default:
                    throw new RuntimeException("not implemented yet!");
            }
        }
        return "event:" + eventIndex;
    }

    public String getActionToExecute() {
        int eventIndex = planEncoding.get(executionIndex + 1);
        AugmentedAbstractUI state = getBaseAugmentedUI();
        return getAction(state, eventIndex);
    }

    public AugmentedAbstractUI getExpectedAugmentedUI() {
        return AugmentedAbstractUI.getById(planEncoding.get(executionIndex+2));
    }

    public LinkedList<Integer> getPlanEncoding() {
        return new LinkedList<>(planEncoding);
    }

    public void setNDFlag() {
        isNDPlan = true;
    }

    public boolean isNDPlan() {
        return isNDPlan;
    }

    @Override
    public String toString() {
        if (isResetPlan) return "RESET plan";
        return Util.makeIntSetToString(planEncoding, ", ", null).toString();
    }
}
