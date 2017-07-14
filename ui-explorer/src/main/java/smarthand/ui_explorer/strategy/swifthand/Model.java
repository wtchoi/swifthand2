package smarthand.ui_explorer.strategy.swifthand;

import com.google.common.collect.Multimap;
import smarthand.ui_explorer.trace.AbstractUI;
import smarthand.ui_explorer.util.Util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

/**
 * Created by wtchoi on 6/21/16.
 */
public class Model {
    int nextStateId = 0;
    ModelState failState;
    PTA pta;

    HashMap<Integer, ModelState> roots = new HashMap<>();
    HashMap<Integer, HashSet<ModelState>> uiToStates = new HashMap<>();

    public Model(PTA pta) {
        this.pta = pta;
        failState = getFailState();
    }

    public ModelState getRoot(PTA.PTAState ptaState) {
        int uid = ptaState.uistate.id();
        if (roots.containsKey(uid)) {
            return roots.get(uid);
        }
        else return null;
    }

    public void makeRoot(ModelState state) {
        roots.put(state.abstractUi.id(), state);
    }

    public boolean isObservedUI(AbstractUI uistate) {
        return uiToStates.containsKey(uistate.id());
    }

    public ModelState pickState(PTA.PTAState ptaState) {
        Integer uid = ptaState.uistate.id();

        Model.ModelState state = null;
        if (!uiToStates.containsKey(uid)) {
            HashSet<Model.ModelState> stateSet = new HashSet<>();
            state = ptaState.isFailState() ? getFailState() : new Model.ModelState(ptaState);

            uiToStates.put(uid, stateSet);
            stateSet.add(state);
        }
        else {
            HashSet<Model.ModelState> stateSet = uiToStates.get(uid);
            for (Model.ModelState s: stateSet) {
                state = s;
                break;
            }
            state.ptaStates.add(ptaState);
        }

        assert state != null;
        state.ptaStates.add(ptaState);
        return state;
    }

    public ModelState createState(PTA.PTAState ptaState) {
        Integer uid = ptaState.uistate.id();
        Model.ModelState state = ptaState.isFailState() ? getFailState() : new Model.ModelState(ptaState);

        if (!uiToStates.containsKey(uid)) {
            uiToStates.put(uid, new HashSet<>());
        }
        HashSet<Model.ModelState> stateSet = uiToStates.get(uid);
        stateSet.add(state);

        assert state != null;
        return state;
    }

    // only to be invoked after removing PTA state.
    public void purge() {
        HashSet<Integer> keySet = new HashSet<>(uiToStates.keySet());
        for (Integer uid: keySet) {
            if (uiToStates.get(uid).isEmpty()) uiToStates.remove(uid);
        }
    }


    public ModelState getFailState() {
        if (failState == null) {
            failState = new ModelState(pta.getDummyFailState());
            uiToStates.put(failState.abstractUi.id(), new HashSet<Model.ModelState>());
            uiToStates.get(failState.abstractUi.id()).add(failState);

        }
        return failState;
    }

    public void copyPTAForest(HashMap<Integer, Model.ModelState> roots, PTA pta, boolean ignoreOldTrace) {
        roots.clear();
        ModelState failState = getFailState();
        failState.parents.clear();

        for (PTA.PTAState ptaRoot : pta.roots.values()) {
            if (ignoreOldTrace && !ptaRoot.isRealized) continue;
            roots.put(ptaRoot.uistate.id(), copyPTA(ptaRoot, ignoreOldTrace));
        }
    }

    private ModelState copyPTA(PTA.PTAState ptaState, boolean ignoreOldTrace) {
        Model.ModelState state = getFailState();
        if (!ptaState.isFailState()) {
            state = new ModelState(ptaState);
            state.isRealized = ptaState.isRealized;
        }

        if (!uiToStates.containsKey(ptaState.uistate.id())) {
            uiToStates.put(ptaState.uistate.id(), new HashSet<ModelState>());
        }

        for (int i =0;i<ptaState.transition.length;i++) {
            if (ptaState.transition[i] == null) continue;
            for (PTA.PTAState child: ptaState.transition[i]) {
                if (ignoreOldTrace && !child.isRealized) continue;
                if (state.mayBranches[i] == null) {
                    state.mayBranches[i] = new HashSet<>();
                    state.mayMethods[i] = new HashSet<>();
                }

                state.addChild(copyPTA(child, ignoreOldTrace), i);
                state.mayBranches[i].addAll(child.coveredBranches);
                state.mayMethods[i].addAll(child.coveredMethods);
            }
        }
        return state;
    }

    public ModelState projectPTAStateToModel(PTA.PTAState ptaState) {
        LinkedList<Integer> path = new LinkedList<>();
        while(ptaState.parent != null) {
            path.addFirst(ptaState.uistate.id());
            path.addFirst(ptaState.incomingEventIndex);
            ptaState = ptaState.parent;
        }
        Model.ModelState current = roots.get(ptaState.uistate.id());
        int index = 0;
        while (index != path.size()) {
            int expectedUid = path.get(index+1);
            for (Model.ModelState child: current.outTransitions[path.get(index)]) {
                if (child.abstractUi.id() == expectedUid) {
                    current = child;
                    break;
                }
            }
            if (current == null) throw new RuntimeException();
            index = index + 2;
        }

        System.out.println("projection path: " + Util.makeIntSetToString(path, ",", null).toString());

        return current;
    }

    /**
     * Created by wtchoi on 3/7/16.
     */
    public class ModelState {
        ModelState(PTA.PTAState seed) {
            this.id = nextStateId++;
            this.abstractUi = seed.uistate;

            ptaStates = new HashSet<>();
            ptaStates.add(seed);

            outTransitions = new HashSet[abstractUi.getEventCount()];
            parents = new HashSet<>();

            mayBranches = new HashSet[abstractUi.getEventCount()];
            mayMethods = new HashSet[abstractUi.getEventCount()];
        }

        void addChild(ModelState s, Integer eventIndex) {
            if (outTransitions[eventIndex] == null) {
                outTransitions[eventIndex] = new HashSet<>();
            }
            HashSet<ModelState> children = outTransitions[eventIndex];

            for (ModelState child: children) {
                if (child.equals(s)) return;
            }

            s.parents.add(this);
            children.add(s);
        }

        void replaceChild(ModelState from, ModelState to) {
            for (int i = 0; i< abstractUi.getEventCount(); i++) {
                HashSet<ModelState> children = outTransitions[i];
                if (children == null) continue;
                if (children.contains(from)) {
                    children.remove(from);
                    children.add(to);
                }
            }
        }

        void recursiveMerge(ModelState s, HashSet<ModelState> mergedNodes) {
            assert s.abstractUi == abstractUi;

            // special treatment for the fail state, since they are always been merged
            if (this.isFailState() && s.isFailState()) return;

            for (ModelState s_parent: s.parents) {
                s_parent.replaceChild(s, this);
                this.parents.add(s_parent);
            }

            for (int i = 0; i < abstractUi.getEventCount(); i++) {
                HashSet<ModelState> s_children = s.outTransitions[i];
                if (s_children == null) continue;
                HashSet<ModelState> s_children_clone = new HashSet<>(s_children);
                for (ModelState s_child : s_children_clone) {
                    if (!s_children.contains(s_child)) continue;
                    if (outTransitions[i] == null) {
                        outTransitions[i] = new HashSet<>();
                    }

                    boolean foundMerge = false;
                    for (ModelState my_child: outTransitions[i]) {
                        if (my_child.abstractUi == s_child.abstractUi) {
                            my_child.recursiveMerge(s_child, mergedNodes);
                            foundMerge = true;
                            break;
                        }
                    }
                    if (!foundMerge) {
                        s_child.parents.remove(s);
                        s_child.parents.add(this);
                        this.addChild(s_child, i);
                    }
                }
            }
            mergedNodes.add(s);

            this.isRealized = this.isRealized || s.isRealized;
        }

        public boolean isClosed() {
            if (isFailState()) return true;
            for (int i=1; i<outTransitions.length; i++) { //skip CLOSE event
                if (outTransitions[i] == null || outTransitions[i].size() == 0) {
                    return false;
                }
            }
            return true;
        }

        public boolean isFailState() {
            return this == failState;
        }

        int id;
        AbstractUI abstractUi;
        HashSet<PTA.PTAState> ptaStates;
        HashSet<ModelState>[] outTransitions;
        HashSet<ModelState> parents;

        boolean isRealized = true;

        HashSet<Integer> mayBranches[]; // a set of branches that may be covered by specific event
        HashSet<Integer> mayMethods[]; // a set of branches that may covered by specific event
    }
}
