package smarthand.ui_explorer.strategy.ksen;

import smarthand.ui_explorer.HistoryManager;

import java.util.HashMap;
import java.util.Stack;

public class PTANode {
  HashMap<Integer, PTANode> children;
  boolean finalState;

  public PTANode() {
    children = new HashMap<Integer, PTANode>();
    finalState = false;
  }

  private void addTrace(Stack<Integer> trace, int i) {
    if (finalState) {
      return;
    }
    else {
      int j;
      PTANode next = children.get(j = trace.get(i));

      if (next == null) {
        next = new PTANode();
        children.put(j, next);
      }

      if (i == trace.size() - 1) {
        next.finalState = true;
        next.children.clear();
      }
      else {
        next.addTrace(trace, i + 1);
      }
    }
  }

  public void addTrace(Stack<Integer> trace) {
    addTrace(trace, 0);
  }

  private boolean hasPrefix(Stack<Integer> trace, int i) {
    if (finalState) {
      return true;
    }
    else {
      int j;
      PTANode next = children.get(j = trace.get(i));

      if (next == null) {
        return false;
      }
      return next.hasPrefix(trace, i + 1);
    }
  }

  public boolean hasPrefix(Stack<Integer> trace) {
    return hasPrefix(trace, 0);
  }


  private void print(String prefix) {
    for (Integer i : children.keySet()) {
      log(prefix + i);
      children.get(i).print(prefix + "    ");
    }
  }

  public void print() {
    print("");
  }

  public static void main(String[] args) {
    PTANode root = new PTANode();
    Stack<Integer> tmp = new Stack<Integer>();
    tmp.push(3);
    tmp.push(4);
    root.addTrace(tmp);

    tmp.clear();
    tmp.push(3);
    tmp.push(4);
    tmp.push(1);
    tmp.push(2);
    root.addTrace(tmp);

    tmp.clear();
    tmp.push(5);
    tmp.push(4);
    tmp.push(1);
    tmp.push(2);
    root.addTrace(tmp);

    tmp.clear();
    tmp.push(5);
    tmp.push(4);
    tmp.push(2);
    tmp.push(2);
    root.addTrace(tmp);


    tmp.clear();
    tmp.push(5);
    tmp.push(4);
    root.addTrace(tmp);

    tmp.clear();
    tmp.push(7);
    tmp.push(4);
    tmp.push(1);
    tmp.push(2);
    root.addTrace(tmp);

    tmp.clear();
    tmp.push(7);
    tmp.push(4);
    tmp.push(9);
    tmp.push(2);
    root.addTrace(tmp);

    tmp.clear();
    tmp.push(3);
    tmp.push(4);
    System.out.println(root.hasPrefix(tmp));

    tmp.clear();
    tmp.push(5);
    tmp.push(4);
    tmp.push(7);
    tmp.push(7);
    System.out.println(root.hasPrefix(tmp));

    tmp.clear();
    tmp.push(1);
    tmp.push(1);
    tmp.push(7);
    tmp.push(7);
    System.out.println(root.hasPrefix(tmp));

    tmp.clear();
    tmp.push(7);
    tmp.push(4);
    tmp.push(1);
    tmp.push(2);
    System.out.println(root.hasPrefix(tmp));

    tmp.clear();
    tmp.push(7);
    tmp.push(4);
    tmp.push(1);
    tmp.push(2);
    tmp.push(1);
    tmp.push(2);
    System.out.println(root.hasPrefix(tmp));

    tmp.clear();
    tmp.push(7);
    tmp.push(4);
    tmp.push(2);
    tmp.push(2);
    System.out.println(root.hasPrefix(tmp));


    root.print();
  }

  public static void log(String s) {
    System.out.println(s);
    HistoryManager.instance().log("PTANode", s);
  }
}


