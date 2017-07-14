package smarthand.ui_explorer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

/**
 * Created by wtchoi on 11/22/15.
 */

public class ForwardGraphPrinter<Node, FGV extends ForwardGraphPrintVisitor<Node>> {

  class NodeInfo {
    Node n;
    int id;

    NodeInfo(Node n) {
      this.n = n;
      this.id = idCount++;
    }
  }

  private FGV visitor;
  private int idCount;

  public ForwardGraphPrinter(FGV visitor) {
    this.visitor = visitor;
    this.idCount = 0;
  }

  public void print() {
    HashMap<Node, NodeInfo> nodeInfoMap = new HashMap();
    LinkedList<Node> nodesToHandle = new LinkedList();
    HashSet<Node> observedNodes = new HashSet();

    Node root = visitor.getRoot();
    nodeInfoMap.put(root, new NodeInfo(root));
    nodesToHandle.add(root);
    observedNodes.add(root);

    // print start
    System.out.println("digraph G {");

    // print all edges using BFS.
    while (!nodesToHandle.isEmpty()) {
      Node node = nodesToHandle.get(0);
      nodesToHandle.remove(0);

      for (Node successor : visitor.getSuccessors(node)) {
        printEdge(nodeInfoMap, node, successor);

        if (!observedNodes.contains(successor)) {
          nodeInfoMap.put(successor, new NodeInfo(successor));
          nodesToHandle.add(successor);
          observedNodes.add(successor);
        }
      }
    }

    // print nodes
    for (Node node : observedNodes) {
      printNode(nodeInfoMap, node);
    }

    // print end
    System.out.println("}");
  }

  private void printEdge(HashMap<Node, NodeInfo> nodeInfoMap, Node src, Node dst) {
    NodeInfo srcInfo = nodeInfoMap.get(src);
    NodeInfo dstInfo = nodeInfoMap.get(dst);
    if (visitor.isLabeled()) {
      String label = visitor.getTransitionLabel(src, dst);
      System.out.println(srcInfo.id + " --> " + dstInfo.id + "[label=" + label + "];");
    }
    else {
      System.out.println(srcInfo.id + " --> " + dstInfo.id + ";");
    }
  }

  private void printNode(HashMap<Node, NodeInfo> nodeInfoMap, Node node) {
    NodeInfo nodeInfo = nodeInfoMap.get(node);
    System.out.println(nodeInfo.id + ";");
  }
}
