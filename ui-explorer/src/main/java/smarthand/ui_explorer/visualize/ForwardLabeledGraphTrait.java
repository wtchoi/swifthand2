package smarthand.ui_explorer.visualize;

import java.util.Set;

/**
 * Created by wtchoi on 12/2/15.
 */
public interface ForwardLabeledGraphTrait<Node> {
  Iterable<Node> getRoots();
  String getNodeName(Node node); // should be unique
  Iterable<Node> getSuccessors(Node node);
  Iterable<Transition<Node>> getTransitions(Node node);

  int getTransitionGroupID(Node from, Node to, String label);
  int getTransitionGroupCount();

  int countTransition(Node node);
  int countOutgoingLabels(Node node);
}