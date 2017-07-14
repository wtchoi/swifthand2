package smarthand.ui_explorer.strategy.random;

import smarthand.ui_explorer.*;
import smarthand.ui_explorer.strategy.C;
import smarthand.ui_explorer.strategy.swifthand.Model;
import smarthand.ui_explorer.strategy.swifthand.PTA;
import smarthand.ui_explorer.trace.*;
import smarthand.ui_explorer.util.Util;

import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;

/**
 * Created by wtchoi on 11/23/15.
 */
public class RandomStrategy extends Strategy {
  private static long time = System.currentTimeMillis();
  boolean useStat;
  boolean usePTA;

  int randomSeed=100;

  int resetCount = 0;
  long timeSpentOnReset = 0;
  long previousTick = 0;


  static {
    //System.out.println("Seed "+ time);
  }

  private Random rand;
  private AbstractUI current;

  private boolean initialized = false;
  PTA.PTAState ptaCurrent;
  PTA.PTAState ptaPrev;
  PTA pta;

  LinkedList<Integer> path;

  int lastTid;

  private LinkedList<Integer> executionTrace = new LinkedList<>();
  private static Integer executionTraceThreshold = 50;

  private static Tracer tracer = new Tracer();

  public RandomStrategy(boolean useStat, boolean usePTA) {
    this.useStat = useStat;
    this.usePTA = usePTA;
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
    log("Filtered Events");
    int eventCounter = 0;
    for (String s: deviceInfo.filteredEvents) {
      log(eventCounter + ". "  + s);
      eventCounter++;
    }

    //String currentActivity = (deviceInfo.activityStack.size() == 0)
    //        ? "null"
    //        : deviceInfo.activityStack.getLast();
    String currentActivity = deviceInfo.focusedActivity;
    if (currentActivity == null) currentActivity = "null";

    current = (escaped || blocked)
            ? AbstractUI.getFailState()
            : AbstractUI.getState(currentActivity, deviceInfo.isKeyboardShown, deviceInfo.filteredEvents, deviceInfo.eventInfo);


    if (lastTid == C.CLOSE) {
      executionTrace.clear();
    }
    executionTrace.push(current.id());

    if (!initialized) {
      pta = new PTA();
      initialized = true;
      ptaCurrent = pta.populateRoot(current);
      ptaCurrent.updateCoverage(coverage.methodCoverage, coverage.branchCoverage);
      lastTid = C.CLOSE;
      previousTick = System.currentTimeMillis();
    }
    else {
      updatePTAForest(current, escaped, blocked);
      ptaCurrent.updateCoverage(coverage.methodCoverage, coverage.branchCoverage);

      if (lastTid == C.CLOSE || lastTid == C.START) {
        timeSpentOnReset += (System.currentTimeMillis() - previousTick);
      }
      previousTick = System.currentTimeMillis();
    }

    HistoryManager hm = HistoryManager.instance();
    hm.periodStat("#Screen", AbstractUI.count());

    Action tty;
    if (lastTid == C.CLOSE) {
      tty = Action.getClose();
    }
    else if (lastTid == C.START) {
      tty = Action.getStart();
    }
    else {
      tty = Action.getEvent(lastTid);
    }

    ConcreteUI currCui = ConcreteUI.getFromRawInfo(deviceInfo.appGuiTree, current);
    tracer.on(tty, currentActivity, deviceInfo.isKeyboardShown, currCui, current, coverage.branchCoverage, coverage.methodCoverage, null);
  }

  void updatePTAForest(AbstractUI uistate, boolean escaped, boolean blocked) {
    if (lastTid == C.START) {
      // The starting point was observed previously.
      ptaPrev = null;
      ptaCurrent = pta.populateRoot(uistate);
    }
    else {
      ptaPrev = ptaCurrent;
      ptaCurrent = ptaCurrent.populateChild(uistate, lastTid == C.CLOSE ? 0 : lastTid);
    }
  }

  private boolean isClosed(PTA.PTAState state) {
    for (Set<PTA.PTAState> children: state.transition) {
      if (children == null) return false;
    }
    return true;
  }


  @Override
  public String getNextAction() {
    if (lastTid == C.CLOSE) {
      log("Start");
      lastTid = C.START;
      return "start";
    }

    if (executionTrace.size() > executionTraceThreshold || ptaCurrent.isFailState() || rand.nextDouble() < 0.05) {
      resetCount++;
      log("Close");
      lastTid = C.CLOSE;
      return "close";
    }

    int decision = -1;
    State state = State.getState(current);

    if (useStat) {
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

      DecimalFormat df = new DecimalFormat("#.00");
      log("Current AbstractUI");
      for (int i = 0; i<current.getEventCount(); i++) {
        String prob = df.format(state.eventProbability[i]*100);
        log(current.getEvent(i) + " : " + + state.eventCounter[i] + ": " + prob + "%");
      }
      log("Picked i = "+decision);

      executionTrace.push(decision);
      state.incrEventCounter(decision);
      lastTid = decision;
      return "event:" + decision;
    }

    if (usePTA) {
      if (decision == -1){
        outer:
        for (int i=0; i<10; i++) {
          decision = rand.nextInt(state.abstractUi.getEventCount()-1) + 1; //avoid close event
          if (ptaCurrent.transition[decision] != null) {
            for (PTA.PTAState child: ptaCurrent.transition[decision]) {
              if (child.isFailState()) continue outer; // avoid closing transition
              //if (child.uistate == ptaCurrent.uistate) continue outer; //avoid self loop
            }
          }
          break;
        }
      }
    }
    else {
      decision = rand.nextInt(state.abstractUi.getEventCount()-1) + 1; //avoid close event
    }

    executionTrace.push(decision);
    lastTid = decision;
    return "event:" + decision;
  }

  private HashSet<PTA.PTAState> computerFrontiers(AbstractUI root, int max) {
    HashSet<PTA.PTAState> frontiers = new HashSet<>();
    LinkedList<PTA.PTAState> worklist = new LinkedList<>();

    worklist.add(pta.populateRoot(root));
    while (!worklist.isEmpty() && frontiers.size() < max) {
      PTA.PTAState s = worklist.removeFirst();
      if (!isClosed(s)) {
        if (rand.nextDouble() < 0.4) {
          frontiers.add(s);
        }
      }

      outer:
      for (Set<PTA.PTAState> children: s.transition) {
        if (children == null) continue;
        if (children.size() != 1) continue;
        for (PTA.PTAState child:children) {
          if (child.isFailState()) continue outer;
        }
        worklist.addAll(children);
      }
    }
    return frontiers;
  }

  @Override
  public void intermediateDump(int id) {
    HistoryManager hm = HistoryManager.instance();
    hm.periodStat("Strategy:Stat:#Reset", resetCount);
    hm.periodStat("Strategy:Stat:TimeSpentOnReset:", timeSpentOnReset);
    hm.periodStat("Strategy:PTA:#Node", pta.countNode());
  }

  @Override
  public void finalDump() {
    String output_dir = Options.get(Options.Keys.OUTPUT_DIR);

    String pta_path = output_dir + "/pta.json";
    Util.writeJsonFile(pta_path, pta.exportToJson());

    String trace_path = output_dir + "/trace.json";
    Util.writeJsonFile(trace_path, tracer.getTrace().toJson());
  }

  @Override
  public boolean requiresAutoRestart() {
    return false;
  }
}