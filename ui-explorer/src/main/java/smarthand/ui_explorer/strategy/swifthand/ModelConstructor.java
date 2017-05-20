package smarthand.ui_explorer.strategy.swifthand;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import smarthand.ui_explorer.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

/**
 * Created by wtchoi on 6/21/16.
 */
public class ModelConstructor {
    HashSet<Model.ModelState> redSet;
    HashSet<Model.ModelState> blueSet;
    Logger logger;
    Model model;
    boolean ignoreOldTrace = false;

    public ModelConstructor(Logger logger, boolean ignoreOldTrace) {
        this.logger = logger;
        this.ignoreOldTrace = ignoreOldTrace;
    }

    private void log(String msg) {
        logger.log(msg);
    }

    public void reconstruct(Model model) {
        this.model = model;

        model.nextStateId = 1;
        log("RECONSTRUCT: PTA CLONING");
        model.copyPTAForest(model.roots, model.pta, ignoreOldTrace);

        redSet = new HashSet<>();
        blueSet = new HashSet<>(model.roots.values());

        log("RECONSTRUCT: START BLUE FRINGE");
        for (Model.ModelState root: model.roots.values()) {
            promoteBlueToRed(root, redSet, blueSet);
        }

        boolean verbose = false;
        HashMap<Integer, HashSet<Integer>> blacklist = new HashMap<>();

        while (!blueSet.isEmpty()) {
            Model.ModelState blueCandidate = null;
            Model.ModelState redCandidate = null;
            Integer maxScore = 0;

            for (Model.ModelState blue: blueSet) {
                Integer blueMaxScore = 0;
                if (blueCandidate == null) blueCandidate = blue;

                boolean unique = true;
                for (Model.ModelState red: redSet) {
                    if (red.abstractUi == blue.abstractUi) {
                        unique = false;

                        Integer score = computeScoreBF(red, blue, false, blacklist);
                        blueMaxScore = (score > blueMaxScore) ? score : blueMaxScore;

                        if (score > maxScore) {
                            maxScore = score;
                            blueCandidate = blue;
                            redCandidate = red;
                        }
                    }
                }

                if (unique) {
                    blueCandidate = blue;
                    redCandidate = null;
                    break;
                }
            }

            if (redCandidate == null) {
                if(verbose) log("RECONSTRUCT: promote");
                promoteBlueToRed(blueCandidate, redSet, blueSet);
            }
            else {
                if(verbose) log("RECONSTRUCT: merge");
                HashSet<Model.ModelState> merged = new HashSet<>();
                redCandidate.recursiveMerge(blueCandidate, merged);

                for (Model.ModelState m: merged) {
                    if (blueSet.contains(m)) blueSet.remove(m);
                }
                for (Model.ModelState s : redSet) {
                    expandBlueFringe(s, redSet, blueSet);
                }
            }
        }

        // reconstruct uiToState map
        log("RECONSTRUCT: ui to state map");
        for (HashSet<Model.ModelState> states: model.uiToStates.values()) {
            states.clear();
        }
        Model.ModelState failState = model.getFailState();
        model.uiToStates.get(failState.abstractUi.id()).add(failState);

        for (Model.ModelState state: redSet) {
            Integer stateUI = state.abstractUi.id();
            if (!model.uiToStates.containsKey(stateUI)) {
                if(verbose) log("RECONSTRUCT: !!! " + stateUI);
            }
            model.uiToStates.get(stateUI).add(state);
        }
    }

    private static class Disagree extends Exception {}

    private static class OverlayGraph {
        HashMap<Model.ModelState, HashMap<Integer,HashSet<Model.ModelState>>> overlayGraph = new HashMap<>();

        HashSet<Model.ModelState> getChildren(Model.ModelState m, Integer eventIndex) {
            if (!overlayGraph.containsKey(m)) {
                overlayGraph.put(m, new HashMap());
            }

            HashMap<Integer, HashSet<Model.ModelState>> overlayTransitions = overlayGraph.get(m);
            if (!overlayTransitions.containsKey(eventIndex)) {
                HashSet<Model.ModelState> toPut = (m.outTransitions[eventIndex] != null)
                        ? new HashSet(m.outTransitions[eventIndex]) : new HashSet();
                overlayTransitions.put(eventIndex, toPut);
            }
            return overlayGraph.get(m).get(eventIndex);
        }

        void addChildren(Model.ModelState s, Integer eventIndex, HashSet<Model.ModelState> toAdd) {
            HashSet<Model.ModelState> current = this.getChildren(s, eventIndex);
            if (current.containsAll(toAdd)) return;

            HashSet<Model.ModelState> update = new HashSet(current);
            update.addAll(toAdd);
            overlayGraph.get(s).put(eventIndex, update);
        }

        void addChild(Model.ModelState s, Integer eventIndex, Model.ModelState toAdd) {
            HashSet<Model.ModelState> current = this.getChildren(s, eventIndex);
            if (current.contains(toAdd)) return;

            HashSet<Model.ModelState> update = new HashSet(current);
            update.add(toAdd);
            overlayGraph.get(s).put(eventIndex, update);
        }
    }

    private boolean blacklisted(Model.ModelState red, Model.ModelState blue, HashMap<Integer, HashSet<Integer>> blacklist) {
        if (red.id < blue.id) {
            if (!blacklist.containsKey(red.id)) return false;
            return blacklist.get(red.id).contains(blue.id);
        }
        else return blacklisted(blue, red, blacklist);
    }

    private void addToBlacklist(Model.ModelState red, Model.ModelState blue, HashMap<Integer, HashSet<Integer>> blacklist) {
        if (red.id < blue.id) {
            if (!blacklist.containsKey(red.id)) blacklist.put(red.id, new HashSet<Integer>());
            blacklist.get(red.id).add(blue.id);
        }
        else {
            addToBlacklist(blue, red, blacklist);
        }
    }

    private Integer computeScoreBF(Model.ModelState red, Model.ModelState blue, boolean verbose, HashMap<Integer, HashSet<Integer>> blacklist) {
        try {
            if (blacklisted(red, blue, blacklist)) return 0;
            return computeScoreBody(red, blue, new OverlayGraph(), HashMultimap.create(), verbose);
        }
        catch (Disagree e) {
            addToBlacklist(red, blue, blacklist);
            return 0;
        }
    }

    private Integer computeScoreBody(Model.ModelState red, Model.ModelState blue, OverlayGraph og, Multimap<Integer, Integer> equalityAssumptions, boolean verbose) throws Disagree{
        if (red.abstractUi != blue.abstractUi){
            if (verbose){
                log("Comparison Failed: " + red.abstractUi.id() + " vs " + blue.abstractUi.id());
                log("red: " + red.abstractUi.getTooltip());
                log("blue: " + blue.abstractUi.getTooltip());
            }
            throw new Disagree();
        }

        // special treatment for the fail state, since they are always been merged
        if (blue.isFailState() && red.isFailState()) return 0;

        Integer score = 0;
        for (int i = 0; i<red.abstractUi.getEventCount(); i++) {
            if (blue.outTransitions[i] == null) continue;
            HashSet<Model.ModelState> blueChildren = blue.outTransitions[i];
            HashSet<Model.ModelState> redChildren = og.getChildren(red, i);

            if (redChildren.isEmpty()) {
                og.addChildren(red, i, blueChildren);
            }
            else if (redChildren.size() == 1 && blueChildren.size() == 1) {
                Model.ModelState redChild = redChildren.iterator().next();
                Model.ModelState blueChild = blueChildren.iterator().next();
                try {
                    score += computeScoreBody(redChild, blueChild, og, equalityAssumptions, verbose);
                }
                catch (Disagree e) {
                    if (verbose) log(i + "th child (1)");
                    throw e;
                }
            }
            else {
                for (Model.ModelState blueChild: blueChildren) {
                    boolean newChild = true;
                    for (Model.ModelState redChild:redChildren) {
                        if (redChild.abstractUi == blueChild.abstractUi) {
                            try {
                                score += computeScoreBody(redChild, blueChild, og, equalityAssumptions, verbose);
                            }
                            catch(Disagree e) {
                                if (verbose) log(i + "th child (2)");
                                throw e;
                            }
                            newChild = false;
                            break;
                        }
                    }
                    if (newChild) {
                        og.addChild(red, i, blueChild);
                    }
                }
            }
        }

        return score+1;
    }

    private void promoteBlueToRed(Model.ModelState state, HashSet<Model.ModelState> redSet, HashSet<Model.ModelState> blueSet) {
        redSet.add(state);
        blueSet.remove(state);
        expandBlueFringe(state, redSet, blueSet);
    }

    private void expandBlueFringe(Model.ModelState state, HashSet<Model.ModelState> redSet, HashSet<Model.ModelState> blueSet) {
        for (int i =0;i < state.outTransitions.length; i++) {
            if (i==0) continue; //skip CLOSE event
            HashSet<Model.ModelState> children = state.outTransitions[i];
            if (children == null) continue;
            for (Model.ModelState child : children) {
                if (blueSet.contains(child) || redSet.contains(child)) continue;
                blueSet.add(child);
            }
        }
    }
}
