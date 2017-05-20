package smarthand.ui_explorer.strategy.swifthand;

import org.json.JSONArray;
import org.json.JSONObject;
import smarthand.ui_explorer.trace.AbstractUI;
import smarthand.ui_explorer.trace.Action;
import smarthand.ui_explorer.trace.Trace;
import smarthand.ui_explorer.util.Util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by wtchoi on 6/21/16.
 */
public class PTA {
    HashMap<Integer, PTAState> roots = new HashMap<>();

    int nextStateId = 0;
    PTAState dummayFailState;

    public PTAState populateRoot(AbstractUI uistate) {
        int uid = uistate.id();
        if (roots.containsKey(uid)) {
            return roots.get(uid);
        }
        else {
            PTAState state = uistate.isFailState() ? getDummyFailState() : new PTAState(uistate);
            roots.put(uid, state);
            return state;
        }
    }

    public PTAState getDummyFailState() {
        if (dummayFailState == null)
            dummayFailState = new PTAState(AbstractUI.getFailState());

        return dummayFailState;
    }

    //Generate PTA datastructure from trace
    public static PTA loadTrace(Trace trace) {
        PTA pta = new PTA();

        PTA.PTAState curState = null; // null indicate that the app is restarted, and the current abstract UI must be a FailedState.
        PTA.PTAState prevState = null;
        int id = 0;
        for (Trace.Snapshot s: trace.snapshots) {
            if (s.prevAction.kind == Action.Kind.Start || s.prevAction.kind == Action.Kind.Restart) {
                prevState = null;
                curState = pta.populateRoot(s.abstractUI);
            }
            else if (s.prevAction.kind == Action.Kind.Close) {
                if (!s.abstractUI.isFailState()) {
                    System.out.println(s.activityName);
                    System.out.println(s.isKeyboardShown);
                    throw new RuntimeException("Something is wrong: " + id);
                }
                prevState = curState;
                if (prevState == null) continue;
                curState = prevState.populateFailedChild(0); //special treatment for close
            }
            else {
                prevState = curState;
                curState = s.abstractUI.isFailState()
                        ? prevState.populateFailedChild(s.prevAction.actionIndex)
                        : prevState.populateChild(s.abstractUI, s.prevAction.actionIndex);
            }
            curState.updateCoverage(s.branchCoverage, s.methodCoverage);

            if (curState.isFailState()) {
                curState.isRealized = false;
            }

            id++;
        }

        return pta;
    }

    //We assume that AbstractUI is properly initialized
    public static JSONArray dumpToJson(PTA pta) {
        final JSONArray arr = new JSONArray();
        traverse(pta, new PTAVisitor() {
            @Override
            public void visitPre(PTAState state) {
                JSONObject stateInfo = new JSONObject();
                stateInfo.put("sid", state.sid);
                stateInfo.put("uid", state.uistate.id());
                if (state.parent != null) {
                    stateInfo.put("parent", state.parent.sid);
                    stateInfo.put("incomingEvent", state.incomingEventIndex);
                    if (state.coveredMethods != null && !state.coveredBranches.isEmpty()) {
                        stateInfo.put("coveredMethods", Util.makeIntSetToJson(state.coveredMethods));
                    }
                    if (state.coveredBranches != null && !state.coveredBranches.isEmpty()) {
                        stateInfo.put("coveredBranches", Util.makeIntSetToJson(state.coveredBranches));
                    }
                }
                arr.put(stateInfo);
            }
        });
        return arr;
    }


    /*
     * The function assumes that child states always appear after their parent
     * states in input JSONArray arr.
     */
    public static PTA loadJson(JSONArray arr) {
        PTA pta = new PTA();
        HashMap<Integer, PTAState> oldMap = new HashMap<>();

        for (int i=0; i<arr.length(); i++) {
            JSONObject stateInfo = arr.getJSONObject(i);
            int sid = stateInfo.getInt("sid");
            int uid = stateInfo.getInt("uid");
            AbstractUI uistate = AbstractUI.getStateById(uid);

            PTAState state;
            if (stateInfo.has("parent")) {
                int parentSid = stateInfo.getInt("parent");
                PTAState parent = oldMap.get(parentSid);
                int incomingEvent = stateInfo.getInt("incomingEvent");

                System.out.println("create node (sid = " + sid + ", parent = " +  parent.sid + ") [" + uistate.id() + "]");

                state = uistate.isFailState()
                        ? parent.populateFailedChild(incomingEvent)
                        : parent.populateChild(uistate, incomingEvent);
            }
            else {
                System.out.println("create root (sid = " + sid + ") [" + uistate.id() + "]");
                state = pta.populateRoot(uistate);
            }

            if (stateInfo.has("coveredMethods")) {
                state.coveredMethods = Util.makeJsonToIntSet(stateInfo.getJSONArray("coveredMethods"));
            }
            else {
                state.coveredMethods = new HashSet<>();
            }

            if (stateInfo.has("coveredBranches")) {
                state.coveredBranches = Util.makeJsonToIntSet(stateInfo.getJSONArray("coveredBranches"));
            }
            else {
                state.coveredBranches = new HashSet<>();
            }

            if (!state.isFailState())
                state.isRealized = false;

            oldMap.put(sid, state);
        }
        return pta;
    }


    // Hash-consed. Can be compared with equality check.
    class PTAState implements Comparable<PTAState> {
        PTAState(AbstractUI uistate) {
            int eventCount = uistate.getEventCount();

            this.sid = nextStateId++;
            this.uistate = uistate;
            this.transition = new HashSet[eventCount];
            this.coveredBranches = new HashSet<>();
            this.coveredMethods = new HashSet<>();
        }

        PTAState populateChild(AbstractUI uistate, Integer eventIndex) {
            if (transition[eventIndex] == null)
                transition[eventIndex] = new HashSet<>();

            Set<PTAState> children = transition[eventIndex];

            for (PTAState child: children) {
                if (child.uistate == uistate) return child;
            }

            PTAState child = new PTAState(uistate);
            child.setParent(this, eventIndex);
            children.add(child);
            return child;
        }

        PTAState populateFailedChild(Integer eventIndex) {
            return populateChild(AbstractUI.getFailState(), eventIndex);
        }

        public boolean isFailState() {
            return this.uistate.isFailState();
        }

        public int compareTo(PTAState state) {
            assert state != null;
            return Integer.compare(sid, state.sid);
        }

        private void setParent(PTAState parent, Integer incomingEventIndex) {
            this.parent = parent;
            this.incomingEventIndex = incomingEventIndex;
        }

        public void updateCoverage(Set<Integer> cm, Set<Integer> cb) {
            coveredMethods.addAll(cm);
            coveredBranches.addAll(cb);
        }

        int sid;
        AbstractUI uistate;

        PTAState parent;
        Integer incomingEventIndex;
        Set<Integer> coveredMethods;
        Set<Integer> coveredBranches;

        Set<PTAState>[] transition;

        boolean isRealized = true;
    }

    static class PTAVisitor {
        public  void visitPre(PTAState state) { }
        public  void visitPost(PTAState state) { }
        public  void visitPreChildren(PTAState state) { }
        public  void visitPostChildren(PTAState state) { }
        public  void visitPreChild(PTAState state) { }
        public  void visitPostChild(PTAState state) { }
    }

    public static void traverse(PTA pta, PTAVisitor visitor) {
        for (PTAState root: pta.roots.values()) {
            traverseNode(root, visitor);
        }
    }

    private static void traverseNode(PTAState state, PTAVisitor visitor) {
        visitor.visitPre(state);
        visitor.visitPreChildren(state);
        for (Set<PTAState> children: state.transition) {
            if (children == null) continue;
            for (PTAState child: children) {
                visitor.visitPreChild(state);
                traverseNode(child, visitor);
                visitor.visitPostChild(state);
            }
        }
        visitor.visitPostChildren(state);
        visitor.visitPost(state);
    }

    public JSONObject exportToJson() {
        JSONObject result = new JSONObject();
        result.put("ui", AbstractUI.dumpAbstractionsToJson());
        result.put("pta", PTA.dumpToJson(this));
        return result;
    }
}
