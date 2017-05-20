package smarthand.ui_explorer.visualize;

import java.util.Iterator;
import java.util.Set;

/**
 * Created by wtchoi on 3/7/16.
 */
public interface ForwardLabeledGraphPrintTrait<Node> extends ForwardLabeledGraphTrait<Node> {
    @Override
    Iterable<Node> getRoots();

    @Override
    String getNodeName(Node node); // should be unique

    @Override
    Iterable<Node> getSuccessors(Node node);

    @Override
    Iterable<Transition<Node>> getTransitions(Node node);

    @Override
    int countTransition(Node node);

    @Override
    int countOutgoingLabels(Node node);

    boolean skipNode(Node node);

    String getNodeDetail(Node node);
    boolean isBoldNode(Node node);
    boolean isColoredNode(Node node);
    String getNodeColor(Node node);
    boolean hasImage(Node node);
    String getImageURI(Node node);
    boolean hasURL(Node node);
    String getURL(Node node);

    boolean isImportantTransition(Node from, Node to, String label);
    boolean isColoredTransition(Node from, Node to, String label);
    boolean isBoldTransition(Node from, Node to, String label);
    boolean isDottedTransition(Node from, Node to, String label);
    boolean hasTransitionTooltip(Node from, Node to, String label);
    String getTransitionTooltip(Node from, Node to, String label);
    String getTransitionColor(Node from, Node to, String label);

    @Override
    int getTransitionGroupID(Node from, Node to, String label);

    @Override
    int getTransitionGroupCount();

    boolean isColoredTransitionGroup(Node from, Node to, int groupID);
    boolean isBoldTransitionGroup(Node from, Node to, int groupID);
    boolean isDottedTransitionGroup(Node from, Node to, int groupID);
    String getTransitionGroupColor(Node from, Node to, int groupID);

    String compactLabels(Set<String> labels);

    boolean isGrouped();
    String getGroupID(Node node);
}