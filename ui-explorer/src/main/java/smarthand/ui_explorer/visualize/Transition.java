package smarthand.ui_explorer.visualize;

/**
 * Created by wtchoi on 3/7/16.
 */
public class Transition<Node> {
    public Transition(Node successor, String label) {
        this.successor = successor;
        this.label = label;
    }

    public String label;
    public Node successor;
}
