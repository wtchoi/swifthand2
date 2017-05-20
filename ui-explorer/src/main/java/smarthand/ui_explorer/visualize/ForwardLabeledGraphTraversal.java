package smarthand.ui_explorer.visualize;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

/**
 * Created by wtchoi on 3/7/16.
 */
public class ForwardLabeledGraphTraversal<Node, FGT extends ForwardLabeledGraphTrait<Node>> {

    public static abstract class Visitor<Node> {
        public void beginGraph() {}
        public void endGraph() {}
        public void visitPre(Node node) {}
        public void visit(Node src, Node dst, String label) {}
        public void visitPost(Node node) {}
    }

    public static class VisitorChain<Node> extends Visitor<Node> {
        private LinkedList<Visitor<Node>> visitorList = new LinkedList<>();

        public void addVisitor(Visitor<Node> v) {
            visitorList.addLast(v);
        }

        public void beginGraph() {
            for (Visitor<Node> v : visitorList) {
                v.beginGraph();
            }
        }

        public void endGraph() {
            for (Visitor<Node> v : visitorList) {
                v.endGraph();
            }
        }

        public void visitPre(Node node) {
            for (Visitor<Node> v : visitorList) {
                v.visitPre(node);
            }
        }

        public void visit(Node src, Node dst, String label) {
            for (Visitor<Node> v : visitorList) {
                v.visit(src, dst, label);
            }
        }

        public void visitPost(Node node) {
            for (Visitor<Node> v : visitorList) {
                v.visitPost(node);
            }
        }
    }

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

    public ForwardLabeledGraphTraversal(FGT graph) {
        this.graph = graph;
        this.idCount = 0;
    }

    public void accept(Visitor<Node> visitor) {
        visitor.beginGraph();

        HashMap<Node, NodeInfo> nodeInfoMap = new HashMap();
        LinkedList<Node> nodesToHandle = new LinkedList();

        HashSet<Node> observedNodes = new HashSet();
        HashMap<Node, HashMap<Node, HashSet<String>>> observedTransition = new HashMap();

        Iterable<Node> roots = graph.getRoots();
        for (Node root: roots) {
            nodeInfoMap.put(root, new NodeInfo(root));
            nodesToHandle.add(root);
            observedNodes.add(root);
        }

        // collect all edge in BFS order.
        while (!nodesToHandle.isEmpty()) {
            Node node = nodesToHandle.get(0);
            nodesToHandle.remove(0);

            visitor.visitPre(node);

            for (Transition<Node> transition : graph.getTransitions(node)) {
                Node successor = transition.successor;
                String label = transition.label;

                if (!observedNodes.contains(successor)) {
                    nodeInfoMap.put(successor, new NodeInfo(successor));
                    nodesToHandle.add(successor);
                    observedNodes.add(successor);
                }

                // Register transition label
                if (!observedTransition.containsKey(node)) observedTransition.put(node, new HashMap());
                HashMap<Node, HashSet<String>> secondMap = observedTransition.get(node);

                if (!secondMap.containsKey(successor)) secondMap.put(successor, new HashSet<String>());
                HashSet<String> labels = secondMap.get(successor);

                if (!labels.contains(label)) {
                    visitor.visit(node, successor, label);
                    labels.add(label);
                }
            }

            visitor.visitPost(node);
        }

        visitor.endGraph();
    }
}