package smarthand.ui_explorer.strategy.ksen;

import smarthand.ui_explorer.trace.AbstractUI;
import smarthand.ui_explorer.visualize.ForwardLabeledGraphPrintTrait;
import smarthand.ui_explorer.visualize.PrinterHelper;
import smarthand.ui_explorer.visualize.Transition;

import java.util.LinkedList;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Created by wtchoi on 3/7/16.
 */
class ModelTraversalHelper implements ForwardLabeledGraphPrintTrait<State> {
    NondeterministicModelBasedStrategy strategy;

    ModelTraversalHelper(NondeterministicModelBasedStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public Iterable<State> getRoots() {
        LinkedList<State> lst = new LinkedList<>();
        lst.push(strategy.root);
        return lst;
    }

    @Override
    public Iterable<State> getSuccessors(State node) {
        LinkedList<State> states = new LinkedList();
        for (int i = 0; i < node.nTransitions; i++) {
            for (State successor : node.transitions[i]) {
                states.add(successor);
            }
        }
        return states;
    }

    @Override
    public Iterable<Transition<State>> getTransitions(State node) {
        LinkedList<Transition<State>> transitions = new LinkedList();
        for (int i = 0; i < node.nTransitions; i++) {
            if (node.transitions[i] == null) continue;
            for (State successor : node.transitions[i]) {
                transitions.add(new Transition<State>(successor, String.valueOf(i + 1)));
            }
        }
        return transitions;
    }

    @Override
    public int countTransition(State node) {
        int count = 0;
        for (int i = 0; i < node.nTransitions; i++) {
            if (node.transitions[i] == null) continue;
            count += node.transitions[i].size();
        }
        return count;
    }

    @Override
    public int countOutgoingLabels(State node) {
        return node.transitions.length;
    }

    @Override
    public boolean isColoredTransitionGroup(State from, State to, int groupID) {
        return false;
    }

    @Override
    public boolean isBoldTransitionGroup(State from, State to, int groupID) {
        return false;
    }

    @Override
    public boolean isDottedTransitionGroup(State f, State t, int groupID) { return false; }

    @Override
    public String getTransitionGroupColor(State from, State to, int groupID) {
        assert (false);
        return null;
    }

    @Override
    public int getTransitionGroupCount() {
        return 1;
    }

    @Override
    public int getTransitionGroupID(State from, State to, String label) {
        return 0;
    }

    private boolean isPrevAction(State from, State to, String label) {
        return (from.id == strategy.prev.id) && (to.id == strategy.current.id) && label.equals(String.valueOf(strategy.lastTid + 1));
    }

    @Override
    public boolean isImportantTransition(State from, State to, String label) {
        return isPrevAction(from, to, label);
    }

    @Override
    public boolean isColoredTransition(State from, State to, String label) {
        return isPrevAction(from, to, label);
    }

    @Override
    public boolean isBoldTransition(State from, State to, String label) {
        return isPrevAction(from, to, label);
    }

    @Override
    public boolean isDottedTransition(State from, State to, String label) {
        return false;
    }

    @Override
    public String getTransitionColor(State from, State to, String label) {
        assert (isColoredTransition(from, to, label));
        if (isPrevAction(from, to, label)) return "blue";
        return null;
    }

    @Override
    public String getNodeName(State node) {
        return String.valueOf(node.id) + " [" + node.nTransitions + "]";
    }

    @Override
    public String getNodeDetail(State node) {
        return AbstractUI.getStateById(node.id).getTooltip();
    }

    @Override
    public boolean hasImage(State node) {
        return true;
    }

    @Override
    public String getImageURI(State node) {
        return "./screen" + AbstractUI.getStateById(node.id).getSnapshotID() + ".png";
    }

    @Override
    public boolean isColoredNode(State node) {
        if ((node == strategy.current) || (node == strategy.prev)) return true;

        for (int i = 0; i < node.nTransitions; i++) {
            if (node.transitions[i] == null) continue;
            if (node.transitions[i].size() > 1) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isBoldNode(State node) {
        if ((node == strategy.current) || (node == strategy.prev)) return true;

        for (int i = 0; i < node.nTransitions; i++) {
            if (node.transitions[i] == null) continue;
            if (node.transitions[i].size() > 1) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getNodeColor(State node) {
        assert (isColoredNode(node));
        if (node == strategy.current) return "red";
        if (node == strategy.prev) return "blue";

        for (int i = 0; i < node.nTransitions; i++) {
            if (node.transitions[i] == null) continue;
            if (node.transitions[i].size() > 1) {
                return "green";
            }
        }

        return null;
    }

    @Override
    public boolean hasURL(State node) {
        return false;
        // TODO
    }

    @Override
    public String getURL(State node) {
        return null;
        // TODO
    }

    @Override
    public String getTransitionTooltip(State node1, State node2, String label) {
        return null;
    }

    @Override
    public boolean hasTransitionTooltip(State node1, State node2, String label) {
        return false;
    }

    @Override
    public boolean isGrouped() {
        return true;
    }

    @Override
    public String getGroupID(State node) {
        return AbstractUI.getStateById(node.id).getActivityName();
    }

    @Override
    public String compactLabels(Set<String> labels) {
        SortedSet<Integer> sortedLabels = new TreeSet();
        for (String label : labels) {
            sortedLabels.add(Integer.valueOf(label));
        }

        return PrinterHelper.buildIntervalString(sortedLabels);
    }

    @Override
    public boolean skipNode(State node){
        return false;
    }
    // -- END --
}
