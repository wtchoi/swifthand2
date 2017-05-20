package smarthand.ui_explorer.trace;

import org.json.JSONObject;
import smarthand.ui_explorer.trace.AbstractUI;
import smarthand.ui_explorer.trace.Action;
import smarthand.ui_explorer.trace.ConcreteUI;
import smarthand.ui_explorer.trace.Trace;

import java.util.Collection;
import java.util.Set;

/**
 * Created by wtchoi on 10/6/16.
 */
// class to build trace
public class Tracer {
    private Trace trace = new Trace();

    //aux could be null
    public void onBegin(String activityName, Boolean isKeyboardShown, ConcreteUI uiTree, AbstractUI abstractUI, Set<Integer> bc, Set<Integer> mc, JSONObject aux) {
        trace.addTransition(Action.getStart(), activityName, isKeyboardShown, uiTree, abstractUI, bc, mc, aux);
    }

    //aux could be null
    public void onReset(String activityName, Boolean isKeyboardShown, ConcreteUI uiTree, AbstractUI abstractUI, Set<Integer> bc, Set<Integer> mc, JSONObject aux) {
        trace.addTransition(Action.getReset(), activityName, isKeyboardShown, uiTree, abstractUI, bc, mc, aux);
    }

    //aux could be null
    public void onTransition(int eventIndex, String activityName, Boolean isKeyboardShown, ConcreteUI uiTree, AbstractUI abstractUI, Set<Integer> bc, Set<Integer> mc, JSONObject aux) {
        trace.addTransition(Action.getEvent(eventIndex), activityName, isKeyboardShown, uiTree, abstractUI, bc, mc, aux);
    }

    //aux could be null
    public void onClose(String activityName, Boolean isKeyboardShown, ConcreteUI uiTree, AbstractUI abstractUI, Set<Integer> bc, Set<Integer> mc, JSONObject aux) {
        trace.addTransition(Action.getClose(), activityName, isKeyboardShown, uiTree, abstractUI, bc, mc, aux);
    }

    //general purpose function
    public void on(Action tty, String activityName, Boolean isKeyboardShown, ConcreteUI uiTree, AbstractUI abstractUI, Set<Integer> bc, Set<Integer> mc, JSONObject aux) {
        switch (tty.kind) {
            case Start:
                trace.addTransition(Action.getStart(), activityName, isKeyboardShown, uiTree, abstractUI, bc, mc, aux);
                break;
            case Close:
                trace.addTransition(Action.getClose(), activityName, isKeyboardShown, uiTree, abstractUI, bc, mc, aux);
                break;
            case Restart:
                trace.addTransition(Action.getReset(), activityName, isKeyboardShown, uiTree, abstractUI, bc, mc, aux);
                break;
            case Event:
                trace.addTransition(Action.getEvent(tty.actionIndex), activityName, isKeyboardShown, uiTree, abstractUI, bc, mc, aux);
                break;
        }
    }

    public void clear() {
        trace.snapshots.clear();
    }

    public Trace getTrace() {
        return new Trace(trace);
    }
}
