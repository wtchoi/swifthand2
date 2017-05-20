package smarthand.ui_explorer.strategy;

import smarthand.ui_explorer.DeviceInfo;
import smarthand.ui_explorer.HistoryManager;
import smarthand.ui_explorer.Logger;
import smarthand.ui_explorer.trace.AbstractUI;
import smarthand.ui_explorer.trace.Coverage;
import smarthand.ui_explorer.trace.Trace;
import smarthand.ui_explorer.util.Util;

import java.util.LinkedList;

/**
 * Created by wtchoi on 3/13/17.
 */


public abstract class Planner implements Logger {
    public abstract void initialize();

    public abstract LinkedList<Integer> getNextPlan(AbstractUI currAbs);
    public abstract void reportExecutionSuccess(LinkedList<Integer> plan, Trace trace);
    public abstract void reportExecutionFailure(LinkedList<Integer> plan, Trace trace);

    // Recommend to use this to collect debugging information.
    public abstract void reportIntermediateStep(int eventIndex, DeviceInfo deviceInfo, AbstractUI abstractUI, Coverage coverage, boolean escaped, boolean blocked);

    public abstract String getName();
    public abstract void setRandomSeed(int randomSeed);

    public abstract void intermediateDump(int id);
    public abstract void finalDump();

    public void log(String s) {
        System.out.println(s);
        HistoryManager.instance().log("Planner", s);
    }

    public void log(String s, int indent) {
        String result = "";
        for (int i=0; i<indent; i++) {
            result += "\t";
        }
        result += s;
        log(result);
    }

    public final void warning(String s) {
        HistoryManager.instance().warning("Planner", s);
    }

    //helper
    public static void printIntSet(Iterable<Integer> set, String tag, Logger logger) {
        StringBuilder builder = new StringBuilder(tag + ": [");
        Util.makeIntSetToString(set, ", ", builder);
        builder.append("]");
        logger.log(builder.toString());
    }
}