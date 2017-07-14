package smarthand.ui_explorer.strategy.regression;

import smarthand.ui_explorer.trace.Coverage;

/**
 * Created by wtchoi on 3/13/17.
 */

class PlanInfo {
    Coverage c;
    int trial = 0;
    int fragmentCount;
    boolean justSynthesized = true;

    //used by sorting function
    Coverage gain;
    int gainSize;
}
