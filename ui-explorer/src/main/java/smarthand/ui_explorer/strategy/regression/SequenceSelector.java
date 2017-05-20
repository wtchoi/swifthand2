package smarthand.ui_explorer.strategy.regression;

import smarthand.ui_explorer.Logger;
import smarthand.ui_explorer.trace.AbstractUI;
import smarthand.ui_explorer.trace.Action;
import smarthand.ui_explorer.trace.Coverage;
import smarthand.ui_explorer.trace.Trace;
import smarthand.ui_explorer.util.Util;

import java.util.HashSet;
import java.util.LinkedList;

/**
 * Created by wtchoi on 11/1/16.
 */

public class SequenceSelector {
    private Logger logger;

    private int nextKeyPositionIndex = 0;
    private LinkedList<Integer> keyPositions = new LinkedList<>();
    private Trace inputTrace;

    private final boolean skipSequence;
    private final boolean cutTail;
    
    LinkedList<Observer> observers = new LinkedList<>();
    
    public interface Observer {
        void onSnapshot(Trace.Snapshot s);
        void onTraceDrop(int size);
        void onTailDrop(int count);
        void onRestart();
    }
    
    public static class Profiler implements Observer {
        public int droppedTailEvents = 0;
        public int droppedTraceEvents = 0;
        public int droppedTraces = 0;
        public int events = 0;
        public int traces = 0;
        public Coverage coverage = new Coverage();
        
        public void onSnapshot(Trace.Snapshot s) {
            events++;
            coverage.add(s.methodCoverage, s.branchCoverage, s.abstractUI.id());
        }
        
        public void onTraceDrop(int size) {
            droppedTraceEvents += size;
            droppedTraces++;
        }
        
        public void onTailDrop(int count) {
            droppedTailEvents += count;
        }
        
        public void onRestart() {
            traces++;
        }
    }
    
    public SequenceSelector(boolean skipSequence, boolean cutTail, Logger logger) {
        this.logger = logger;
        this.skipSequence = skipSequence;
        this.cutTail = cutTail;
    }
    
    public void addObserver(Observer o) {
        this.observers.add(o);
    }
    
    public void load(Trace it) {
        this.inputTrace = it;
        for (int i = 0; i< inputTrace.size(); i++) {
            Trace.Snapshot s = inputTrace.get(i);
            observers.forEach(x->x.onSnapshot(s));
            
            if (s.prevAction.kind == Action.Kind.Start || s.prevAction.kind == Action.Kind.Restart) {
                keyPositions.addLast(i);
                observers.forEach(x->x.onRestart());
            }
        }
    }

    public Trace getNextSequence() {
        return getNextSequence(new Coverage(), new Coverage(), null);
    }
    
    public Trace getNextSequence(Coverage currentCoverage, Coverage deltaToReturn, AbstractUI currAbs) {
        int begin, end, cutPoint;

        // find next sequence to execute
        while (true) {
            if (keyPositions.size() == nextKeyPositionIndex) {
                //Replay is finished. Nothing left to replay.
                return null;
            }

            cutPoint = 0;
            begin = keyPositions.get(nextKeyPositionIndex++);
            end = (nextKeyPositionIndex == keyPositions.size())
                    ? inputTrace.size() - 1
                    : keyPositions.get(nextKeyPositionIndex) - 1;



            // if the current stat and the expected initial state of the sequence if different, skip the current sequence
            if (currAbs  != null && inputTrace.get(begin).abstractUI != currAbs) {
                log(String.format("Skip a sequence: initial state doesn't match [%s, %s]", begin, end));
                log(inputTrace.get(begin).abstractUI.id() + " : " + inputTrace.get(begin).abstractUI.getTooltip());
                log("vs");
                log(currAbs.id() + " : " + currAbs.getTooltip());

                final int size = end - begin;
                observers.forEach(x->x.onTraceDrop(size));
                continue;
            }

            int cursor = begin+1;
            HashSet<Integer> newBranches = new HashSet();
            HashSet<Integer> newMethods = new HashSet();
            HashSet<Integer> newScreens = new HashSet<>();

            // find the prefix of the sequence that maximizes the benefit
            while (cursor <= end) {
                Trace.Snapshot s = inputTrace.get(cursor);
                HashSet branchDelta = Util.setMinus(Util.setMinus(s.branchCoverage, currentCoverage.branchCoverage), newBranches);
                HashSet methodDelta = Util.setMinus(Util.setMinus(s.methodCoverage, currentCoverage.methodCoverage), newMethods);

                if (!branchDelta.isEmpty() || !methodDelta.isEmpty()
                        || (!currentCoverage.screenCoverage.contains(s.abstractUI.id()) && !newScreens.contains(s.abstractUI.id()))) {
                    cutPoint = cursor;
                    newBranches.addAll(branchDelta);
                    newMethods.addAll(methodDelta);
                    newScreens.add(s.abstractUI.id());
                    log("update at index: " + (cursor - begin));
                }
                cursor++;
            }

            // the current sequence does not give new coverage. try next one.
            if (skipSequence && cutPoint == 0) {
                log("Skip a sequence: nothing to gain");
                final int diff = end - begin;
                observers.forEach(x->x.onTraceDrop(diff));
                continue;
            }

            if (!cutTail) {
                cutPoint = end;
            }

            final int dropCount = end - cutPoint;
            observers.forEach(x->x.onTailDrop(dropCount));
            
            log(String.format("sequence selected : [%s, %s]", begin, cutPoint));
            log(String.format("expected gain: %s %s %s", newMethods.size(), newBranches.size(), newScreens.size()));
            deltaToReturn.add(newMethods, newBranches, newScreens);
            return inputTrace.getSubtrace(begin, cutPoint);
        }
    }

    void log(String message) {
        logger.log(message);
    }
}
