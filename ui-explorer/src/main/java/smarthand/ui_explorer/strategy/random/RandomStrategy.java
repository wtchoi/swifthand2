package smarthand.ui_explorer.strategy.random;

import smarthand.ui_explorer.trace.AbstractUI;
import smarthand.ui_explorer.DeviceInfo;
import smarthand.ui_explorer.HistoryManager;
import smarthand.ui_explorer.Strategy;
import smarthand.ui_explorer.trace.Coverage;

import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.Random;

/**
 * Created by wtchoi on 11/23/15.
 */
public class RandomStrategy extends Strategy {
  private static long time = System.currentTimeMillis();
  boolean useStat;
  int randomSeed=100;

  static {
    //System.out.println("Seed "+ time);
  }

  private Random rand;
  private AbstractUI current;

  private LinkedList<Integer> executionTrace = new LinkedList<>();
  private static Integer executionTraceThreshold = 50;

  public RandomStrategy(boolean useStat) {
    this.useStat = useStat;
    this.rand = new Random(this.randomSeed);
  }

  @Override
  public String getName() {
    return "random";
  }

  @Override
  public String getDetailedExplanation() { return getName(); }


  @Override
  public void setRandomSeed(int seed) {
    randomSeed = seed;
    rand = new Random(seed);
  }

  @Override
  public void reportExecution(DeviceInfo deviceInfo, Coverage coverage, boolean escaped, boolean blocked) {
    String currentActivity = "null";
    if (deviceInfo.activityStack.size() > 0) currentActivity = deviceInfo.activityStack.getLast();
    current = AbstractUI.getState(currentActivity, deviceInfo.isKeyboardShown, deviceInfo.filteredEvents);

    if (escaped) executionTrace.clear();
    executionTrace.push(current.id());

    HistoryManager hm = HistoryManager.instance();
    hm.periodStat("#Screen", AbstractUI.count());
  }

  @Override
  public String getNextAction() {
    if (executionTrace.size() > executionTraceThreshold) {
      log("Restart");
      executionTrace.clear();
      return "reset";
    }

    //String evt;
    //int i = rand.nextInt(current.getEventCount());
    //while ((evt = (String) current.getAction(i)).startsWith("text")) {
    //  i = rand.nextInt(i);
    //}
    //System.out.println("Picked i = "+i);

    int decision;
    State state = State.getState(current);

    if (useStat) {
      decision = -1;
      double val = rand.nextDouble();
      for (int i = 0; i < current.getEventCount(); i++) {
        val -= state.eventProbability[i];
        if (val < 0) {
          decision = i;
          break;
        }
      }

      if (decision == -1) {
        decision = rand.nextInt(current.getEventCount());
      }
    }
    else {
      decision = rand.nextInt(state.abstractUi.getEventCount());
    }

    DecimalFormat df = new DecimalFormat("#.00");
    log("Current AbstractUI");
    for (int i = 0; i<current.getEventCount(); i++) {
      String prob = df.format(state.eventProbability[i]*100);
      log(current.getEvent(i) + " : " + + state.eventCounter[i] + ": " + prob + "%");
    }
    log("Picked i = "+decision);

    executionTrace.push(decision);
    state.incrEventCounter(decision);
    return "event:" + decision;
  }

  @Override
  public void intermediateDump(int id) { }
  public void finalDump() { }
}