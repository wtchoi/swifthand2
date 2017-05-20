package smarthand.ui_explorer.strategy;

import smarthand.ui_explorer.DeviceInfo;
import smarthand.ui_explorer.Strategy;
import smarthand.ui_explorer.trace.AbstractUI;
import smarthand.ui_explorer.trace.Coverage;

/**
 * Created by wtchoi on 5/18/16.
 */
public class MonkeyStrategy extends Strategy {

    private int monkeyCount = 0;
    private int monkeyMax = 50;

    private AbstractUI previousUI;
    private AbstractUI currentUI;

    @Override
    public void reportExecution(DeviceInfo deviceInfo, Coverage coverage, boolean escaped, boolean blocked) {
        String currentActivity = "null";
        if (deviceInfo.activityStack.size() > 0) currentActivity = deviceInfo.activityStack.getLast();
        AbstractUI uistate = AbstractUI.getState(currentActivity, deviceInfo.isKeyboardShown, deviceInfo.filteredEvents);

        previousUI = currentUI;
        currentUI = uistate;
    }

    public String getNextAction() {
        monkeyCount++;
        if (monkeyCount == monkeyMax) {
            monkeyCount = 0;
            return "reset";
        }
        return "monkey:5000"; //run monkey for 5 sec.s
    }

    public void intermediateDump(int id) { }
    public void finalDump() { }

    public String getName() {
        return "monkey";
    }
    public String getDetailedExplanation() { return getName(); }

    public void setRandomSeed(int randomSeed) { }
}