package smarthand.ui_explorer.strategy;

import smarthand.ui_explorer.DeviceInfo;
import smarthand.ui_explorer.Strategy;
import smarthand.ui_explorer.trace.AbstractUI;
import smarthand.ui_explorer.trace.Coverage;

/**
 * Created by wtchoi on 10/18/16.
 * Adapter starategy which automatically restart app when it detect the app is not running
 */
public class AutoRestartAdapter extends Strategy {
    private Strategy s;

    public AutoRestartAdapter(Strategy s) {
        this.s = s;
    }

    boolean escaped;
    boolean blocked;
    int blockCount;

    @Override
    public void reportExecution(DeviceInfo deviceInfo, Coverage coverage, boolean escaped, boolean blocked) {
        this.escaped = escaped;
        this.blocked = blocked;

        if (escaped) return;
        if (blocked) return;
        s.reportExecution(deviceInfo, coverage, escaped, blocked);
    }

    public String getNextAction() {
        if (escaped) return "reset";
        if (blocked) return "reset";
        return s.getNextAction();
    }

    public void intermediateDump(int id) {
        s.intermediateDump(id);
    }

    public void finalDump() {
        s.finalDump();
    }

    public String getName() {
        return s.getName();
    }
    public String getDetailedExplanation() { return s.getDetailedExplanation(); }
    public void setRandomSeed(int randomSeed) { s.setRandomSeed(randomSeed); }
}
