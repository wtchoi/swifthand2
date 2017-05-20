package smarthand.ui_explorer.visualize;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.*;

/**
 * Created by wtchoi on 11/22/15.
 */
public class ForwardLabeledGraphPrinter<Node, FGT extends ForwardLabeledGraphPrintTrait<Node>> {

  class NodeInfo {
    Node n;
    int id;

    NodeInfo(Node n) {
      this.n = n;
      this.id = idCount++;
    }
  }

  private FGT graph;
  private int idCount;
  private boolean cluster;

  public ForwardLabeledGraphPrinter(FGT graph, boolean cluster) {
    this.graph = graph;
    this.idCount = 0;
    this.cluster = cluster;
  }

  public void print(OutputStream os) {
    HashMap<Node, NodeInfo> nodeInfoMap = new HashMap();
    LinkedList<Node> nodesToHandle = new LinkedList();

    HashSet<Node> observedNodes = new HashSet();
    HashMap<Node, HashMap<Node, HashSet<String>>> observedTransitions = new HashMap();
    HashMap<String, HashSet<Node>> groups = new HashMap();

    PrintWriter pw = new PrintWriter(os);

    Iterable<Node> roots = graph.getRoots();
    for (Node root: roots) {
      nodeInfoMap.put(root, new NodeInfo(root));
      nodesToHandle.add(root);
      observedNodes.add(root);
    }

    // print start
    pw.println("digraph G {");
    if (graph.isGrouped() && cluster) pw.println("compound=true;");
    pw.println("node [shape=box];");

    // collect all edge in BFS order.
    while (!nodesToHandle.isEmpty()) {
      Node node = nodesToHandle.get(0);
      nodesToHandle.remove(0);

      if (graph.isGrouped() && cluster) {
        String nodeGroup = graph.getGroupID(node);
        if (!groups.containsKey(nodeGroup)) groups.put(nodeGroup, new HashSet<Node>());
        groups.get(nodeGroup).add(node);
      }

      for (Transition<Node> transition : graph.getTransitions(node)) {
        Node successor = transition.successor;
        String label = transition.label;

        if (!observedNodes.contains(successor)) {
          nodeInfoMap.put(successor, new NodeInfo(successor));
          nodesToHandle.add(successor);
          observedNodes.add(successor);
        }

        // Register transition label
        if (!observedTransitions.containsKey(node)) observedTransitions.put(node, new HashMap());
        HashMap<Node, HashSet<String>> secondMap = observedTransitions.get(node);

        if (!secondMap.containsKey(successor)) secondMap.put(successor, new HashSet<String>());
        HashSet<String> labels = secondMap.get(successor);
        labels.add(label);
      }
    }

    if (graph.isGrouped() && cluster) {
      int clusterCount = 0;
      for (String group : groups.keySet()) {
        String activity = group;
        if (group.split("/").length == 2) activity = group.split("/")[1];
        pw.println("subgraph cluster" + (clusterCount++) + " {");
        pw.println("label=\"" +  activity + "\";");
        pw.println("style=dashed;");
        pw.println("color=gray;");
        for (Node node : groups.get(group)) {
          printNode(nodeInfoMap, node, pw);
        }
        pw.println("}");
        pw.println();
      }
    }
    else {
      for (Node node : observedNodes) {
        printNode(nodeInfoMap, node, pw);
      }
    }

    // print edges
    for (Map.Entry<Node, HashMap<Node,HashSet<String>>> entry : observedTransitions.entrySet()) {
      Node src = entry.getKey();
      HashMap<Node, HashSet<String>> secondMap = entry.getValue();
      for (Map.Entry<Node, HashSet<String>> secondEntry: secondMap.entrySet()) {
        Node dst = secondEntry.getKey();
        HashSet<String> labels = secondEntry.getValue();
        printEdge(nodeInfoMap, src, dst, labels, pw);
      }
    }

    // print end
    pw.println("}");
    pw.flush();
  }

  private void printEdge(HashMap<Node, NodeInfo> nodeInfoMap, Node src, Node dst, HashSet<String> labels, PrintWriter pw) {
    assert (src != null);
    assert (dst != null);
    NodeInfo srcInfo = nodeInfoMap.get(src);
    NodeInfo dstInfo = nodeInfoMap.get(dst);

    int groupCount = graph.getTransitionGroupCount();
    Set<String> toCompact[] = new Set[groupCount];
    for(int i =0;i<groupCount;i++) {
      toCompact[i] = new HashSet<>();
    }

    for (String label : labels) {
      if (graph.isImportantTransition(src, dst, label)) {
        boolean isBold = graph.isBoldTransition(src, dst, label);
        boolean isDotted = graph.isDottedTransition(src, dst, label);
        String color = graph.isColoredTransition(src, dst, label)
            ? graph.getTransitionColor(src, dst, label) : null;
        String tooltip = graph.hasTransitionTooltip(src, dst, label)
            ? graph.getTransitionTooltip(src, dst, label) : null;
        printSingleEdge(srcInfo.id, dstInfo.id, label, isBold, isDotted, color, tooltip, pw);
      } else {
        int groupID = graph.getTransitionGroupID(src, dst, label);
        toCompact[groupID].add(label);
      }
    }

    for (int i=0; i<groupCount; i++) {
      if (!toCompact[i].isEmpty()) {
        String label = graph.compactLabels(toCompact[i]);
        boolean isBold = graph.isBoldTransitionGroup(src, dst,i);
        boolean isDotted = graph.isDottedTransitionGroup(src, dst,i);
        String color = graph.isColoredTransitionGroup(src, dst,i)
                ? graph.getTransitionGroupColor(src, dst,i) : null;
        printSingleEdge(srcInfo.id, dstInfo.id, label, isBold, isDotted, color, null, pw);
      }
    }
  }

  private void printSingleEdge(Integer srcId, Integer dstId, String label, boolean isBold, boolean isDotted, String color, String tooltip, PrintWriter pw) {
    String deco = "";
    if (isBold) deco = "penwidth=3";
    if (isDotted) deco += " style=dotted";
    if (color != null) deco += " color="+color;
    if (tooltip != null) deco += " tooltip=\"" + tooltip + "\"";
    String opt = "[label=\"" + label + "\" " + deco + "]";
    pw.println(srcId + " -> " + dstId + " " + opt + ";");
  }

  private void printNode(HashMap<Node, NodeInfo> nodeInfoMap, Node node, PrintWriter pw) {
    assert(node != null);

    NodeInfo nodeInfo = nodeInfoMap.get(node);

    String nodeID = graph.getNodeName(node);
    String nodeString = graph.getNodeDetail(node);
    if (nodeString == null) nodeString = "N/A";

    String[] eventList = nodeString.split(",");
    StringBuilder readableEventList = new StringBuilder();
    readableEventList.append(eventList[0]).append("&#10;");
    for (int i =1; i<eventList.length; i++) {
      readableEventList
          .append(String.valueOf(i))
          .append(" ")
          .append(eventList[i])
          .append("&#10;");
      // putting newline to html tool tip:
      // http://stackoverflow.com/questions/358874/how-can-i-use-a-carriage-return-in-a-html-tooltip
    }

    String label = nodeID;

    String imageURI = graph.getImageURI(node);
    StringBuilder imageBoxBuilder = new StringBuilder()
        .append("<<TABLE border=\"0\" cellborder=\"0\" cellpadding=\"0\" cellspacing=\"0\">")
        .append("<TR><TD width=\"60\" height=\"100\" fixedsize=\"true\">");

    if (graph.hasImage(node)) {
      imageBoxBuilder.append("<IMG SRC=\"" + imageURI + "\" scale=\"true\"/>");
    }

    imageBoxBuilder.append("</TD></TR>")
        .append("<TR><TD><font point-size=\"10\">" + nodeID + "</font></TD></TR>")
        .append("</TABLE>>");

    label = imageBoxBuilder.toString();

    String deco = "";
    if (graph.isBoldNode(node)) deco = "penwidth=3";
    if (graph.isColoredNode(node)) deco += " color="+ graph.getNodeColor(node);
    if (graph.hasURL(node)) deco += " url=" + graph.getURL(node);
    pw.println(nodeInfo.id + " [label=" + label + " " + deco + " tooltip=\"" + readableEventList + "\"];");
  }
}
