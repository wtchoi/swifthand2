package smarthand.ui_explorer;

import smarthand.ui_explorer.trace.Coverage;
import smarthand.ui_explorer.util.Util;

/**
 * Created by wtchoi on 11/23/15.
 */
public abstract class Strategy implements Logger {
  public abstract void reportExecution(DeviceInfo deviceInfo, Coverage coverage, boolean escaped, boolean blocked);
  public abstract String getNextAction();
  public abstract void intermediateDump(int id);
  public abstract void finalDump();
  public abstract String getName();
  public abstract String getDetailedExplanation();
  public abstract void setRandomSeed(int randomSeed);

  // Indicate whether the strategy requires the underlying client to automatically
  // restart the target application when the app is getting stuck.
  public boolean requiresAutoRestart() {
    return true;
  }

  public void log(String s) {
    System.out.println(s);
    HistoryManager.instance().log("Strategy", s);
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
    HistoryManager.instance().warning("Strategy", s);
  }

  //helper
  public static void printIntSet(Iterable<Integer> set, String tag, Logger logger) {
    StringBuilder builder = new StringBuilder(tag + ": [");
    Util.makeIntSetToString(set, ", ", builder);
    builder.append("]");
    logger.log(builder.toString());
  }

  public static void dumpCoverage(String tag, Coverage c) {
    HistoryManager hm = HistoryManager.instance();
    hm.periodStat(tag + ":#Screen", c.screenCoverage.size());
    hm.periodStat(tag + ":#Branch", c.getBranchCount());
    hm.periodStat(tag + ":#Exception", c.getExceptionCount());
    hm.periodStat(tag + ":#Method", c.methodCoverage.size());
  }
}