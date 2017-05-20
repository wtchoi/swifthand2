package smarthand.ui_explorer.strategy.ksen;

import org.json.JSONObject;
import smarthand.ui_explorer.*;
import smarthand.ui_explorer.Strategy;
import smarthand.ui_explorer.trace.AbstractUI;
import smarthand.ui_explorer.trace.Coverage;
import smarthand.ui_explorer.visualize.ForwardLabeledGraphPrinter;
import smarthand.ui_explorer.visualize.ForwardLabeledGraphStat;
import smarthand.ui_explorer.visualize.PrinterHelper;

import java.util.*;

/**
 * Created by wtchoi on 11/23/15.
 */
public class NondeterministicModelBasedStrategy extends Strategy {
  State root;
  State current;
  State prev;

  ModelTraversalHelper modelPrintHelper = new ModelTraversalHelper(this);

  Stack<Integer> nextSeq = null;
  int index = -1;
  int lastTid;

  LinkedList<String> elist;

  int deviationCount = 0;
  int deviationLoss = 0;
  int crashCount = 0;

  int randomWalkCount = 0;
  int latestFailureStreak = 0;

  int iteration = 0;
  final public static int MAX_SEQUENCE_LENGTH = 10;
  final public static int MAX_SEQUENCE_TRIALS = 50000;
  final public static int DEVIATION_TOLERANCE = 5;
  final public static int MAX_TRACE_LENGTH = 100;

  private int getUniqueAppStateID(String activityName, boolean keyboardOn, JSONObject appGui, LinkedList<String> elist) {
    log("Constructing Abstract State");
    AbstractUI state = AbstractUI.getState(activityName, keyboardOn, elist);
    return state.id();
  }

  @Override
  public String getName() {
    return "ksen.ND";
  }

  @Override
  public String getDetailedExplanation() { return getName(); }

  @Override
  public void setRandomSeed(int seed) {
    State.setRandomSeed(seed);
  }

  @Override
  public void reportExecution(DeviceInfo deviceInfo, Coverage coverage, boolean escaped, boolean blocked) {
    log("Received state:");
    log("- Activity Stack:");
    for (String s : deviceInfo.activityStack) {
      log(s);
    }
    log("- Events:");

    elist = deviceInfo.filteredEvents;
    for (String s : elist) {
      log(s);
    }

    String currentActivity = "null";
    if (deviceInfo.activityStack.size() > 0) currentActivity = deviceInfo.activityStack.getLast();
    int id = getUniqueAppStateID(currentActivity, deviceInfo.isKeyboardShown, deviceInfo.appGuiTree, elist);
    int nTransitions = elist.size();

    log("Compute Strategy");
    if (root == null) {
      current = root = new State(id, nTransitions);
      index = 2;
      nextSeq = current.getNextSequence(MAX_SEQUENCE_LENGTH, MAX_SEQUENCE_TRIALS, null);
      log("Next sequence " + nextSeq);
    } else {
      prev = current;
      boolean knownTransition = current.isKnownTransition(lastTid, id, nTransitions);
      current = current.addTransition(lastTid, id, nTransitions);
      if (index == -1 || index == nextSeq.size() || nextSeq.get(index) != current.id || escaped) {
        if (index != -1 && index < nextSeq.size() && nextSeq.get(index) != current.id || escaped) {
          //originally, System.err
          log("***********************************************************************************************************");
          log("Failed to follow sequence = " + nextSeq + ", index = " + index + ", current = " + current.id + ", lastTid = " + lastTid);
          log("***********************************************************************************************************");

          warning("Failed to follow sequence = " + nextSeq + ", index = " + index + ", current = " + current.id + ", lastTid = " + lastTid);

          if (!escaped && knownTransition) {
            latestFailureStreak++;
            deviationCount++;
            deviationLoss += (index / 2);
          } else {
            latestFailureStreak = 0;
          }
          nextSeq = current.getNextSequence(MAX_SEQUENCE_LENGTH, MAX_SEQUENCE_TRIALS, nextSeq);
        } else {
          latestFailureStreak = 0;
          nextSeq = current.getNextSequence(MAX_SEQUENCE_LENGTH, MAX_SEQUENCE_TRIALS, null);
        }
        index = 2;
      } else {
        index += 2;
      }

      if (escaped) {
        crashCount++;
      }
    }

    if (nextSeq == null || latestFailureStreak >= DEVIATION_TOLERANCE) {
      log("Pick random sequence");
      latestFailureStreak = 0;
      nextSeq = current.getRandomSequence();
      randomWalkCount++;
    } else {
      log("Pick strategic sequence");
    }

    log("Printing Stats");
    ForwardLabeledGraphStat<State> modelStat = ForwardLabeledGraphStat.<State, ModelTraversalHelper>compute(modelPrintHelper);
    stat(modelStat);
  }

  public String getNextAction() {
    iteration++;

    if (nextSeq == null) {
      log("Model exploration finished.  All states have been explored.");
      return null;
    }

    int i = nextSeq.get(index - 1);
    lastTid = i;
    log("Picked i = " + i);
    log("Sequence = " + nextSeq + ", index = " + index + ", current state = " + current.id + ", nextTid = " + lastTid);
    return "event:" + i;
  }

  public void stat(ForwardLabeledGraphStat modelStat) {
    HistoryManager hm = HistoryManager.instance();
    hm.periodStat("Strategy: Model: #Node", AbstractUI.count());
    hm.periodStat("Strategy: Model: #Edge", modelStat.countEdge());
    hm.periodStat("Strategy: Model: #Tran. (realized)", modelStat.countRealizedTransition());
    hm.periodStat("Strategy: Model: #Tran. (remaining)", modelStat.countUnrealizedTransition());
    hm.periodStat("Strategy: Model: #ND Tran.", modelStat.countNonDeterministicTransition());
    hm.periodStat("Strategy: Model: #ND Edge ", modelStat.countNonDeterministicEdge());
    hm.periodStat("Strategy: Stat: #ND Seq. failure", deviationCount);
    hm.periodStat("Strategy: Stat: #ND Seq. wasted", deviationLoss);
    hm.periodStat("Strategy: Stat: #Crash", crashCount);
    hm.periodStat("Strategy: Stat: #RW", randomWalkCount);
    hm.periodStat("Strategy: Stat: #C. ND Seq. failure", latestFailureStreak);
  }

  public void intermediateDump(int id) {
    // graph without clustering
    dumpModel(id, "a", false);

    // graph with clustering
    //dumpModel(id, "b", true);
  }

  public void finalDump() { }

  public void dumpModel(int id, String postfix, boolean cluster) {
    // Generate dotty representation of the graph
    ForwardLabeledGraphPrinter<State, ModelTraversalHelper> printer =
            new ForwardLabeledGraphPrinter(modelPrintHelper, cluster);
    PrinterHelper.dumpForwardLabeledGraphToDot(id, "model", postfix, printer);
  }
}