package smarthand.ui_explorer.strategy.swifthand;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import java.util.HashSet;

/**
 * Created by wtchoi on 3/11/16.
 */
public class StrictExtrapolation implements Extrapolation {

    STMLStrategy strategy;
    Table<Model.ModelState, Integer, Boolean> cached = HashBasedTable.create();
    Table<Model.ModelState, Integer, HashSet<Model.ModelState>> cache = HashBasedTable.create();

    public StrictExtrapolation(STMLStrategy s) {
        strategy = s;
        cache.clear();
    }

    @Override
    public void update(STMLStrategy s) {
        cached.clear();
        cache.clear();
    }

    @Override
    public HashSet<Model.ModelState> extrapolate(Model.ModelState state, Integer eventIndex, int degree) {
        if (eventIndex == 1) {
            //never extrapolate back button
            return null;
        }

        if (cached.contains(state, eventIndex)){
            if (cache.contains(state, eventIndex)) {
                return cache.get(state, eventIndex);
            }
            else {
                return null;
            }
        }
        cached.put(state, eventIndex, true);

        HashSet<Model.ModelState> friends = strategy.model.uiToStates.get(state.abstractUi.id());
        if (friends.size() == 1) return null;

        int maxSize = 0;
        HashSet<Model.ModelState> largestCandidate = null;
        Model.ModelState largestFriend = null;

        for (Model.ModelState friend : friends) {
            HashSet<Model.ModelState> children = friend.outTransitions[eventIndex];
            if (children == null) continue;

            if (children.size() > maxSize) {
                largestFriend = friend;
                largestCandidate = children;
                maxSize = children.size();
            }
        }
        if (maxSize == 0) return null;

        HashSet<Model.ModelState> result = new HashSet<>(largestCandidate);
        if (result.contains(largestFriend)) {
            // detect self loop
            result.remove(largestFriend);
            result.add(state);
        }

        cache.put(state, eventIndex, result);
        return result;
    }

    @Override
    public boolean isExtrapolation(Model.ModelState f, Model.ModelState to, Integer event) {
        HashSet<Model.ModelState> children = f.outTransitions[event];
        if (children == null) return true;
        return !children.contains(to);
    }

    @Override
    public boolean isClosed(Model.ModelState state, int degree) {
        if (state.isFailState()) return true;
        for (int i = 1; i<state.abstractUi.getEventCount(); i++) { //skip CLOSE event
            if (state.outTransitions[i] != null) continue;
            if (extrapolate(state, i, degree) == null) return false;
        }
        return true;
    }

    @Override
    public int degree() {
        return 1;
    }

    @Override
    public void dump() {

    }

    @Override
    public String name() {
        return "strict";
    }
}
