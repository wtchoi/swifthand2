package smarthand.ui_explorer.strategy.random;

import smarthand.ui_explorer.trace.AbstractUI;

import java.util.HashMap;

/**
 * Created by wtchoi on 3/7/16.
 */

public class State {
    public int eventCounter[];
    public int eventCounterSum = 0;
    public double eventProbability[];
    public AbstractUI abstractUi;

    private static HashMap<AbstractUI, State> states = new HashMap<>();

    private State(AbstractUI abstractUi) {
        this.abstractUi = abstractUi;
        this.eventCounter = new int[abstractUi.getEventCount()];
        this.eventProbability = new double[abstractUi.getEventCount()];

        for (int i =0; i<eventCounter.length ;i++) {
            eventCounter[i] = 0;
        }

        this.updateEventProbability();
    }

    public void incrEventCounter(int i) {
        eventCounterSum++;
        eventCounter[i]++;
        updateEventProbability();
    }

    private void updateEventProbability() {
        double score[] = new double[abstractUi.getEventCount()];
        double scoreSum = 0;
        for(int i = 0; i< abstractUi.getEventCount(); i++) {
            score[i] = ((double)(eventCounterSum*2 + abstractUi.getEventCount())) / ((double) eventCounter[i]*4 + 1);
            scoreSum += score[i];
        }
        for(int i = 0; i< abstractUi.getEventCount(); i++) {
            eventProbability[i] = score[i] / scoreSum;
        }
    }

    public static State getState(AbstractUI uistate) {
        if (states.containsKey(uistate)) {
            return states.get(uistate);
        }
        else {
            State state = new State(uistate);
            states.put(uistate, state);
            return state;
        }
    }
}
