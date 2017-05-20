package smarthand.ui_explorer.visualize;

import java.util.HashMap;
import java.util.Set;

/**
 * Created by wtchoi on 12/2/15.
 */
public class ForwardLabeledGraphStat<Node> extends ForwardLabeledGraphTraversal.Visitor<Node> {
  private int edgeCount = 0;
  private int edgeCountByGroup[];

  private int nodeCount = 0;

  private int realizedTransitionCount = 0;
  private int realizedTransitionCountByGroup[];

  private int expectedTransitionCount = 0;
  private HashMap<String, Integer> labelCount = new HashMap();

  private ForwardLabeledGraphTrait<Node> helper;

  public ForwardLabeledGraphStat(ForwardLabeledGraphTrait<Node> helper) {
    this.helper = helper;
    realizedTransitionCountByGroup = new int[helper.getTransitionGroupCount()];
    edgeCountByGroup = new int[helper.getTransitionGroupCount()];

    for (int i =0;i<helper.getTransitionGroupCount(); i++) {
      realizedTransitionCountByGroup[i] = 0;
      edgeCountByGroup[i] = 0;
    }
  }

  public void beginGraph() { }
  public void visitPre(Node node) {
    expectedTransitionCount += helper.countOutgoingLabels(node);
    nodeCount++;
  }

  public void visitPost(Node node) { }
  public void endGraph() { }

  public void visit(Node src, Node dst, String label) {
    int transitionGroup = helper.getTransitionGroupID(src, dst, label);

    edgeCount++;
    edgeCountByGroup[transitionGroup]++;

    String key = src + ":" + label;
    if (!labelCount.containsKey(key)) {
      labelCount.put(key, 1);
      realizedTransitionCount++;
      realizedTransitionCountByGroup[transitionGroup]++;
    }
    else{
      labelCount.put(key, labelCount.get(key) + 1);
    }

  }

  public int countNode() {
    return nodeCount;
  }

  public int countEdge() {
    return edgeCount;
  }

  public int countEdgeByGroup(int group) { return edgeCountByGroup[group]; }

  public int countRealizedTransition() { return realizedTransitionCount; }

  public int countRealizedTransitionByGroup(int group) {
    return realizedTransitionCountByGroup[group];
  }

  public int countNonDeterministicTransition() {
    int count = 0;
    Set<String> keys = labelCount.keySet();
    for (String key : keys) {
      if (labelCount.get(key) != 1) count++;
    }
    return count;
  }

  public int countNonDeterministicEdge() {
    int count = 0;
    Set<String> keys = labelCount.keySet();
    for (String key : keys) {
      int lCount = labelCount.get(key);
      if (lCount != 1) count += lCount;
    }
    return count;
  }

  public int countUnrealizedTransition() {
    return expectedTransitionCount - realizedTransitionCount;
  }

  public static <Node, FGT extends ForwardLabeledGraphTrait<Node>> ForwardLabeledGraphStat
  compute(ForwardLabeledGraphTrait<Node> helper) {
    ForwardLabeledGraphStat<Node> stat = new ForwardLabeledGraphStat(helper);
    ForwardLabeledGraphTraversal<Node, FGT> traversal = new ForwardLabeledGraphTraversal(helper);
    traversal.accept(stat);
    return stat;
  }
}
