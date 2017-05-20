package smarthand.ui_explorer.strategy.ksen;

import smarthand.ui_explorer.HistoryManager;
import smarthand.ui_explorer.Options;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Stack;

/**
 * Created by wtchoi on 11/23/15.
 */
class State {
  int id;
  int nTransitions;
  int level = -1;
  LinkedHashSet<State> transitions[];

  int seqGenTrialCount = 0;

  private static ArrayList<State> states = new ArrayList<State>();
  private static Random rand = new Random();
  private static PTANode banned = new PTANode();

  public static void setRandomSeed(int seed) {
    rand = new Random(seed);
  }

  State(int id, int nTransitions) {
    this.id = id;
    this.nTransitions = nTransitions;
    this.transitions = new LinkedHashSet[nTransitions];
    states.add(this);
  }

  private boolean addTransition(int tid, State q) {
    assert tid >=0 && tid <nTransitions;
    LinkedHashSet<State> next = transitions[tid];
    if (next == null) {
      next = transitions[tid] = new LinkedHashSet<State>();
    }
    if (next.contains(q)) {
      return false;
    } else {
      next.add(q);
      return true;
    }
  }

  public State addTransition(int tid, int sid, int nTransitions) {
    State tmp;
    if (sid < states.size()) {
      addTransition(tid, tmp = states.get(sid));
    } else {
      assert sid == states.size();
      tmp = new State(sid, nTransitions);
      addTransition(tid, tmp);
    }
    return tmp;
  }

  public boolean isKnownTransition(int tid, int sid, int nTransitions) {
    assert sid <= states.size();
    if (sid == states.size()) return false;

    assert tid >=0 && tid < nTransitions;
    LinkedHashSet<State> next = transitions[tid];
    if (next == null) return false;
    return next.contains(states.get(sid));
  }

  public void setLevel(int l) {
    this.level = l;
  }

  public boolean hasLevel() {
    return level >= 0;
  }

  public int getLevel() {
    return level;
  }

  public static void print() {
    log("Model");
    log("-----");

    StringBuffer buffer = new StringBuffer();
    for (State s: states) {
      buffer.append(s.id+"("+s.nTransitions+")");
      buffer.append(" -> ");
      for(int i=0; i<s.nTransitions;i++) {
        if (s.transitions[i] != null) {
          if (s.transitions[i].size()>1) {
            buffer.append("**");
          }
          for (State t : s.transitions[i]) {
            buffer.append("(" + i + "," + t.id + ")");
          }
        }
      }
      log(buffer.toString());
    }
  }

  @Override
  public int hashCode() {
    return id;
  }

  @Override
  public boolean equals(Object o) {
    return this == o;
  }

  public static int[] generateRandomPermutation (int n){
    int[] array = new int[n];
    for(int i = 0; i < array.length; i++)
      array[i] = i;

    for(int i = 0; i < n; i++){
      int ran = i + rand.nextInt (n-i);
      int temp = array[i];
      array[i] = array[ran];
      array[ran] = temp;
    }
    return array;
  }


  private boolean getSequence(int i, int k, Stack<Integer> trace, int maxTrials) {
    if (i < k) {
      if (Options.get(Options.Keys.BACK_FIRST).equals("True")) {
        if (transitions[0] == null) {
          trace.push(0);
          return true;
        }
      }

      int[] randArr = generateRandomPermutation(nTransitions);
      for(int l=0; l<nTransitions; l++) {
        int j = randArr[l];
        if (transitions[j] == null) {
          trace.push(j);
          if (banned.hasPrefix(trace)) {
            //originally, it was System.err
            log("************************************************");
            log("Avoiding sequence " + trace);
            log("************************************************");
            trace.pop();

            seqGenTrialCount++;
            return false;
          } else {
            return true;
          }
        } else {
          int length = transitions[j].size();
          if (length > 1) {
            int[] randArr2 = generateRandomPermutation(length);
            Object[] states = transitions[j].toArray();
            for (int m=0; m < length; m++) {
              State q = (State)states[randArr2[m]];
              trace.push(j);
              trace.push(q.id);
              if (q.getSequence(i + 1, k, trace, maxTrials)) {
                return true;
              } else {
                trace.pop();
                trace.pop();
              }
            }
          } else {
            for (State q:transitions[j]) {
              trace.push(j);
              trace.push(q.id);
              if (q.getSequence(i + 1, k, trace, maxTrials)) {
                return true;
              } else {
                trace.pop();
                trace.pop();
              }
            }
          }
        }

        if(seqGenTrialCount >= maxTrials) {
          return false;
        }
      }
      seqGenTrialCount++;
      return false;
    } else {
      seqGenTrialCount++;
      return false;
    }
  }

  public Stack<Integer> getNextSequence(int maxLength, int maxTrials, Stack<Integer> bad) {
    if (bad != null) {
      banned.addTrace(bad);
      banned.print();
    }
    Stack<Integer> tmp = new Stack<Integer>();
    seqGenTrialCount = 0;

    for (int i = 1; i <= maxLength; i++) {
      tmp.clear();
      tmp.push(this.id);
      if (getSequence(0, i, tmp, maxTrials)) {
        HistoryManager.instance().log("State", "#Trials:" + seqGenTrialCount);
        return tmp;
      }

      if (seqGenTrialCount >= maxTrials) {
        HistoryManager.instance().log("State", "Out of budget!");
        return null;
      }
    }

    HistoryManager.instance().log("State", "Length limit reached!");
    return null;
  }

  public Stack<Integer> getRandomSequence() {
    Stack<Integer> tmp = new Stack();
    tmp.push(this.id);
    tmp.push(rand.nextInt(nTransitions));
    return tmp;
  }

  public static void log(String s) {
    System.out.println(s);
    HistoryManager.instance().log("State", s);
  }

  private class OutOfBudget extends Exception {}
}