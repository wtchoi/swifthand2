package smarthand.ui_explorer.strategy;

import smarthand.ui_explorer.DeviceInfo;
import smarthand.ui_explorer.Strategy;
import smarthand.ui_explorer.trace.Coverage;

/**
 * Created by wtchoi on 5/18/16.
 */
public class BranchCoverageTestingStrategy extends Strategy {
    @Override
    public void reportExecution(DeviceInfo deviceInfo, Coverage coverage, boolean escaped, boolean blocked) {
        System.out.println(deviceInfo.filteredEvents);
    }

    public String getNextAction() {
        return "finish";
    }

    public void intermediateDump(int id) { }
    public void finalDump() { }

    public String getName() {
        return "branch-coverage-testing";
    }
    public String getDetailedExplanation() { return getName(); }

    public void setRandomSeed(int randomSeed) { }
}
