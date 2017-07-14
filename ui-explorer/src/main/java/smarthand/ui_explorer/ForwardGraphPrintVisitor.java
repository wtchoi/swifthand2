package smarthand.ui_explorer;

/**
 * Created by wtchoi on 11/22/15.
 */
public interface ForwardGraphPrintVisitor<Node> {
  public Node getRoot();
  public Iterable<Node> getSuccessors(Node node);
  public boolean isLabeled();
  public String getTransitionLabel(Node src, Node dst);
}
