package smarthand.ui_explorer.strategy.swifthand;

import smarthand.ui_explorer.*;
import smarthand.ui_explorer.Strategy;
import smarthand.ui_explorer.trace.*;
import smarthand.ui_explorer.util.Util;
import smarthand.ui_explorer.visualize.ForwardLabeledGraphPrinter;
import smarthand.ui_explorer.visualize.ForwardLabeledGraphStat;
import smarthand.ui_explorer.visualize.PrinterHelper;

import java.text.DecimalFormat;
import java.util.*;

/**
 * Created by wtchoi on 2/28/16.
 *
 * SwiftHand implementation (without restart)
 */

//TODO: refactoring

/*
 * STML stands for Simultaneous Testing and Model Learning (a.k.a. swifthand)
 */
public class STMLStrategy extends Strategy {
    public enum ExtrapolationOptions {
        Non, Strict;
    }

    private enum Phase {
        Explore, Monkey;
    }

    boolean initialized = false;

    PTA pta;
    PTA.PTAState ptaCurrent = null;
    PTA.PTAState ptaPrev = null;

    Model model;
    Model.ModelState current = null;
    Model.ModelState prev = null;

    LinkedList<Integer> currentExecutionTrace = new LinkedList<>();

    Coverage currentCoverage = new Coverage();
    int lastGainIndex = 0;

    Integer[] executionPlan = null;
    Integer executionCursor = 0;
    Integer lastTid;

    boolean isMonkeyPlan = false;

    Integer executionTraceThreshold = 30;

    HashSet<Model.ModelState> frontiers = new HashSet<>();

    Extrapolation extrapolation = null;
    int eDgreeMax = 0;

    HashSet<Model.ModelState> monkeyFrontiers = new HashSet<>();
    HashSet<PTA.PTAState> monkeyCovered = new HashSet<>();

    boolean restartAfterEveryGoal = true;
    boolean conservativeMerging = false;
    HashMap<Integer, TreeSet<LinkedList<Integer>>> observationRequirementTable = new HashMap<>();
    HashSet<Model.ModelState> identifiedStates = new HashSet<>();

    boolean useHybridMode = false;
    Phase testingPhase = Phase.Explore;
    long explorationStartTime = 0;
    long explorationBudget = 3600000;//120000; //milliseconds
    long monkeyBudget = 10000;//60000; //milliseconds


    // stats
    int DPlanCount = 0;
    int NDPlanCount = 0;
    int NDFailure = 0;
    int RPlanCount = 0;
    int reconstructionCount = 0;
    int crashCount = 0;
    int resetCount = 0;
    int monkeyCount = 0;

    long previousTick = 0;
    long timeSpentOnReset = 0;

    // constants
    static Integer CLOSE = 0;
    static Integer MONKEY = 222222;
    static Integer START = 333333;
    static Integer WILDCARD = 99999999;

    // helpers
    STMLModelTraversalHelper helper = new STMLModelTraversalHelper(this);
    Tracer tracer = new Tracer();
    Random rand = new Random(0);
    int randomSeed = 0;


    @Override
    public void setRandomSeed(int seed) {
        randomSeed = seed;
        rand = new Random(seed);
    }

    public STMLStrategy(ExtrapolationOptions opt, boolean restartAfterEveryGoal, boolean conservativeMerging) {
        if (opt == ExtrapolationOptions.Strict) {
            extrapolation = new StrictExtrapolation(this);
        }
        if (extrapolation != null) {
            eDgreeMax = extrapolation.degree();
        }

        this.restartAfterEveryGoal = restartAfterEveryGoal;
        this.conservativeMerging = conservativeMerging;
    }

    @Override
    public String getName() {
        String base = "swifthand";
        String suffix = "";

        if (extrapolation != null) {
            return base + "-" + extrapolation.name();
        }
        else{
            return base;
        }
    }

    @Override
    public String getDetailedExplanation() {
        return getName() + " with seed=" + randomSeed;
    }

    @Override
    public void reportExecution(DeviceInfo deviceInfo, Coverage coverage, boolean escaped, boolean blocked) {
        log("Filtered Events");
        int eventCounter = 0;
        for (String s: deviceInfo.filteredEvents) {
            log(eventCounter + ". "  + s);
            eventCounter++;
        }

        //String currentActivity = (deviceInfo.activityStack.size() == 0)
        //        ? "null"
        //        : deviceInfo.activityStack.getLast();
        String currentActivity = deviceInfo.focusedActivity;
        if (currentActivity == null) currentActivity = "null";

        Boolean isKeyboardShown = deviceInfo.isKeyboardShown;

        AbstractUI uistate = (escaped || blocked)
                ? AbstractUI.getFailState()
                : AbstractUI.getState(currentActivity, deviceInfo.isKeyboardShown, deviceInfo.filteredEvents, deviceInfo.eventInfo);

        //if (HistoryManager.instance().getCurrentPeriod() != uistate.getSnapshotID()) {
        //    HistoryManager.instance().takeSnapshot();
        //}

        Coverage receivedCoverage = new Coverage(coverage.methodCoverage, coverage.branchCoverage, uistate.id());
        Coverage coverageGain = Coverage.minus(receivedCoverage, currentCoverage);
        if (coverageGain.size() > 0) {
            lastGainIndex = HistoryManager.instance().getCurrentPeriod();
        }

        currentCoverage.add(coverageGain);

        // If it is the first report, initialize and return.
        if (!initialized) {
            pta = new PTA();
            model = new Model(pta);

            initialized = true;
            ptaCurrent = pta.populateRoot(uistate);
            ptaCurrent.updateCoverage(coverage.methodCoverage, coverage.branchCoverage);
            current = getRoot(ptaCurrent);
            currentExecutionTrace.addLast(uistate.id());
            updateExtrapolation();
            lastTid = CLOSE;
            buildStartPlan();

            previousTick = System.currentTimeMillis();
        }
        else {
            // Update trace, pta, model, and execution plan (must follow this order).
            updateStat(uistate, escaped, blocked);
            updateTrace(uistate, escaped, blocked);

            if (!lastTid.equals(MONKEY)) {
                updatePTAForest(uistate, escaped, blocked);
                ptaCurrent.updateCoverage(coverage.methodCoverage, coverage.branchCoverage);

                updateModel(ptaCurrent, escaped, blocked);
                updateExtrapolation();
            }
            else {
                monkeyCovered.add(ptaCurrent);
                monkeyFrontiers.remove(current);
            }

            updateExecutionPlan(uistate, escaped, blocked);

            if (lastTid == CLOSE || lastTid == START) {
                timeSpentOnReset += (System.currentTimeMillis() - previousTick);
            }
            previousTick = System.currentTimeMillis();
        }

        ConcreteUI currCui = ConcreteUI.getFromRawInfo(deviceInfo.appGuiTree, uistate);
        Action tty;

        if (lastTid.equals(CLOSE)) {
            tty = Action.getClose();
        }
        else if (lastTid.equals(START)) {
            tty = Action.getStart();
        }
        else {
            tty = Action.getEvent(lastTid);
        }

        tracer.on(tty, currentActivity, isKeyboardShown, currCui, uistate, coverage.branchCoverage, coverage.methodCoverage, null);
    }

    @Override
    public String getNextAction() {
        if (executionPlan == null) {
            generateExecutionPlan();
            if (isMonkeyPlan) log("Driving Monkey!");
        }

        Integer eventIndex = executionPlan[executionCursor + 1];
        if (eventIndex.equals(START)) {
            lastTid = START;
            return "start";
        }
        if (eventIndex.equals(CLOSE)) {
            resetCount++;
            lastTid = CLOSE;
            return "close";
        }
        else if (eventIndex.equals(MONKEY)) {
            if (isMonkeyPlan) log("Unleash Monkey!");
            lastTid = MONKEY;
            return "monkey:" + monkeyBudget;

        } else {
            lastTid = eventIndex;
            AbstractUI currentAbstractUi = AbstractUI.getStateById(executionPlan[executionCursor]);
            return "event:" + eventIndex;
        }
    }

    @Override
    public void finalDump() {
        String output_dir = Options.get(Options.Keys.OUTPUT_DIR);

        String pta_path = output_dir + "/pta.json";
        Util.writeJsonFile(pta_path, pta.exportToJson());

        String trace_path = output_dir + "/trace.json";
        Util.writeJsonFile(trace_path, tracer.getTrace().toJson());
    }

    @Override
    public void intermediateDump(int id) {
        log("Current:" + current.id);
        printTrace();
        printRoots();
        printFrontiers();
        printObservedUI();
        printMonkeyFrontiers();

        if (extrapolation != null){
            extrapolation.dump();
        }

        ForwardLabeledGraphPrinter<Model.ModelState, STMLModelTraversalHelper> printer =
                new ForwardLabeledGraphPrinter<>(helper, false);

        PrinterHelper.dumpForwardLabeledGraphToDot(id, "model", "a", printer);

        log("Printing Stats");
        ForwardLabeledGraphStat<Model.ModelState> modelStat =
                ForwardLabeledGraphStat.<Model.ModelState, STMLModelTraversalHelper>compute(helper);

        HistoryManager hm = HistoryManager.instance();
        hm.periodStat("#Screen", AbstractUI.count());
        hm.periodStat("Strategy:LastGainIndex", lastGainIndex);
        hm.periodStat("Strategy:getName", getName());
        hm.periodStat("Strategy:Model:#Node", modelStat.countNode());
        hm.periodStat("Strategy:Model:#Edge", modelStat.countEdge());
        hm.periodStat("Strategy:PTA:#Node", pta.countNode());
        if (extrapolation != null) {
            hm.periodStat("Strategy:Model:#Edge (real)", modelStat.countEdgeByGroup(0));
            hm.periodStat("Strategy:Model:#Edge (extrapolated)", modelStat.countEdgeByGroup(1));
        }
        dumpCoverage("Strategy:Coverage", currentCoverage);

        hm.periodStat("Strategy:Model:#Tran. (realized)", modelStat.countRealizedTransition());
        hm.periodStat("Strategy:Model:#Tran. (remaining)", modelStat.countUnrealizedTransition());
        hm.periodStat("Strategy:Model:#ND Tran.", modelStat.countNonDeterministicTransition());
        hm.periodStat("Strategy:Model:#ND Edge", modelStat.countNonDeterministicEdge());
        hm.periodStat("Strategy:Model:PTA Size", pta.nextStateId);
        hm.periodStat("Strategy:Stat:#Recon.", reconstructionCount);
        hm.periodStat("Strategy:Stat:#Crash", crashCount);
        hm.periodStat("Strategy:Stat:#Reset", resetCount);
        hm.periodStat("Strategy:Stat:#Monkey", monkeyCount);
        hm.periodStat("Strategy:Stat:Plan R", RPlanCount);
        hm.periodStat("Strategy:Stat:Plan D", DPlanCount);
        hm.periodStat("Strategy:Stat:Plan ND", NDPlanCount);
        hm.periodStat("Strategy:Stat:ND Failure:", NDFailure);
        hm.periodStat("Strategy:Stat:TimeSpentOnReset:", timeSpentOnReset);
    }

    private void updateExtrapolation() {
        if (extrapolation != null)
            extrapolation.update(this);
    }

    private void updateStat(AbstractUI state, boolean escaped, boolean blocked) {
        if (escaped){
            crashCount++;
        }
    }

    void updateTrace(AbstractUI abstractUi, boolean escaped, boolean blocked) {
        Integer executedEventIndex = executionPlan[executionCursor+1];

        if (executedEventIndex.equals(START)) {
            currentExecutionTrace.clear();
            currentExecutionTrace.addLast(abstractUi.id());
        }
        else {
            currentExecutionTrace.addLast(executedEventIndex);
            currentExecutionTrace.addLast(abstractUi.id());
        }
    }

    void updatePTAForest(AbstractUI uistate, boolean escaped, boolean blocked) {
        Integer executedEventIndex = executionPlan[executionCursor+1];

        if (executedEventIndex.equals(START)) {
            // The starting point was observed previously.
            ptaCurrent = pta.populateRoot(uistate);
            ptaPrev = null;
        }
        else {
            ptaPrev = ptaCurrent;
            ptaCurrent = ptaCurrent.populateChild(uistate, executedEventIndex);
        }
    }

    boolean isExecutionFinished() {
        if (executionCursor+2 == executionPlan.length) {
            log("EXECUTION PLAN FINISHED");
            return true;
        }
        return false;
    }

    boolean isExpectedExecution(AbstractUI uistate, boolean escaped, boolean blocked) {
        if (isExecutionFinished()) return true;
        Integer expectedUIStateId = executionPlan[executionCursor+2];
        if ((expectedUIStateId == WILDCARD && !blocked && !escaped)|| uistate.id() == expectedUIStateId) {
            return true;
        }
        else{
            log("Current:" + uistate.id());
            log("Expected:" + expectedUIStateId);
            log("UNEXPECTED");
            return false;
        }
    }

    void updateExecutionPlan(AbstractUI uistate, boolean escaped, boolean blocked) {
        if (!escaped && !blocked && !isExecutionFinished() && isExpectedExecution(uistate, escaped, blocked)) {
            executionCursor = executionCursor + 2;
            printExecutionPlan();
            return;
        }

        // Either the execution is finished or didn't went well.
        if (!escaped && !blocked && !isExpectedExecution(uistate, escaped, blocked)) {
            HashSet<Model.ModelState> children = prev.outTransitions[executionPlan[executionCursor+1]];
            if (children != null && children.size() > 1) {
                NDFailure++;
            }
        }

        executionPlan = null;
        executionCursor = 0;
    }

    void updateModel(PTA.PTAState ptaCurrent, boolean escaped, boolean blocked) {
        // Retrieve failure information
        PTA.PTAState ptaNext = null;
        int executedEventIndex = executionPlan[executionCursor+1];

        if (executedEventIndex != START) {
            HashSet<Model.ModelState> knownTargetStates = current.outTransitions[executedEventIndex];
            if (knownTargetStates != null) {
                log("UpdateModel: checking existing observation");
                for (Model.ModelState state : knownTargetStates) {
                    if (state.abstractUi == ptaCurrent.uistate) {
                        state.ptaStates.add(ptaCurrent);
                        prev = current;
                        current = state;
                        log("UpdateModel: the resulting screen conforms to one of expected states");
                        return;
                    }
                }

                // Case 2: The transition is deterministic, and the observation does not match.
                //         Conflict detected.
                if (knownTargetStates.size() == 1){
                    if (ptaPrev.transition[executedEventIndex].size() == 1) {
                        if (!model.uiToStates.containsKey(ptaCurrent.uistate.id())) {
                            model.uiToStates.put(ptaCurrent.uistate.id(), new HashSet<Model.ModelState>());
                        }
                        reconstructModelBF();
                        log("UpdateModel: non-determinism detected. reconstructing the model");
                        return;
                    }
                }
            }

            // Case 3
            // Case 3-1: the transition is non-deterministic
            // Case 3-2: the transition is not observed before
            log("UpdateModel: new observation");
            if (!conservativeMerging) {
                Model.ModelState targetState = pickState(ptaCurrent);
                current.addChild(targetState, executedEventIndex);
                if (current.isClosed()) {
                    frontiers.remove(current);
                }

                prev = current;
                current = targetState;
            }
            else {
                Model.ModelState targetState = model.createState(ptaCurrent);
                current.addChild(targetState, executedEventIndex);

                if (current.isClosed() && !identifiedStates.contains(current)) {
                    boolean merged = false;
                    frontiers.remove(current);

                    log ("Try to merge model state " + current.id);
                    for (Model.ModelState similarState: model.uiToStates.get(current.abstractUi.id())) {
                        if (similarState == current) continue;
                        if (!similarState.isClosed()) continue;
                        log ("Candidate state: " + similarState.id);

                        if (observationallyEqual(similarState, current)) {
                            log ("State " + current.id  + " is merged to state: " + similarState.id);
                            merged = true;
                            Model.ModelState toRemove = current;

                            for (Model.ModelState parent: current.parents) {
                                for (int i=0;i<parent.abstractUi.getEventCount(); i++) {
                                    if (parent.outTransitions[i] != null && parent.outTransitions[i].contains(current)) {
                                        parent.outTransitions[i].remove(current);
                                        parent.outTransitions[i].add(similarState);
                                    }
                                }
                            }

                            for (Model.ModelState child: similarState.outTransitions[executedEventIndex]) {
                                if (child.abstractUi == ptaCurrent.uistate) {
                                    prev = similarState;
                                    current = child;
                                    break;
                                }
                            }
                            current.ptaStates.add(ptaCurrent);
                            model.uiToStates.get(toRemove.abstractUi.id()).remove(toRemove);
                            break;
                        }
                    }

                    if (!merged) {
                        identifiedStates.add(current);
                        for (int i=0;i<current.outTransitions.length;i++) {
                            if (current.outTransitions[i] != null && current.outTransitions[i].size() == 1) {
                                for (Model.ModelState child: current.outTransitions[i]) {
                                    if (child.isFailState()) continue;
                                    log ("add to frontier (merging): " + child.id);
                                    frontiers.add(child);
                                    break;
                                }
                            }
                        }
                        prev = current;
                        current = targetState;
                    }
                }
                else {
                    prev = current;
                    current = targetState;
                }
            }
            log("UpdateModel: update model");
        }
        else {
            prev = null;
            current = getRoot(ptaCurrent);
            return;
        }
    }

    //assume m1.uistate == m2.uistate
    private boolean observationallyEqual(Model.ModelState m1, Model.ModelState m2) {
        AbstractUI abstractUI = m1.abstractUi;
        for (int i=1;i<abstractUI.getEventCount(); i++) { //skip close event
            if (m1.outTransitions[i] == null || m2.outTransitions[i] == null) continue;
            if (m1.outTransitions[i].size() == 1 &&  m2.outTransitions[i].size() == 1) {
                if (getElement(m1.outTransitions[i]).abstractUi != getElement(m2.outTransitions[i]).abstractUi) {
                    log ("equality check failed : [" + i + "]");
                    return false;
                }
            }
        }

        if (observationRequirementTable.containsKey(abstractUI.id())) {
            TreeSet<LinkedList<Integer>> inputSequences = observationRequirementTable.get(abstractUI.id());
            for (LinkedList<Integer> inputSequence : inputSequences) {
                if (!checkEqualityOnInputSequence(m1, m2, inputSequence)) {
                    log("equality check failed : " + Util.makeIntSetToString(inputSequence, ",", null).toString());
                    return false;
                }
            }
        }
        return true;
    }

    private boolean checkEqualityOnInputSequence(Model.ModelState m1, Model.ModelState m2, LinkedList<Integer> inputSequence) {
        for (Integer action: inputSequence) {
            if (m1.outTransitions[action] == null || m2.outTransitions[action] == null) return true;
            if (m1.outTransitions[action].size() == 1 && m2.outTransitions[action].size() == 1) {
                m1 = getElement(m1.outTransitions[action]);
                m2 = getElement(m2.outTransitions[action]);
                if (m1.abstractUi != m2.abstractUi) return false;
            }
        }
        return true;
    }

    private Model.ModelState getRoot(PTA.PTAState ptaState) {
        if (model.roots.containsKey(ptaState.uistate.id())) {
            return model.getRoot(ptaState);
        }
        else {
            Model.ModelState state = pickState(ptaState);
            model.makeRoot(state);
            return state;
        }
    }

    private Model.ModelState pickState(PTA.PTAState ptaState) {
        boolean isNewUI = !model.isObservedUI(ptaState.uistate);
        Model.ModelState state = model.pickState(ptaState);

        if (isNewUI && !state.isClosed()) {
            frontiers.add(state);
            log ("add to frontier (pickState): " + state.id);
            if (!state.isFailState()) {
                monkeyFrontiers.add(state);
            }
        }
        return state;
    }


    void reconstructModelBF() {
        reconstructionCount++;

        log("RECONSTRUCTION BEGIN!");
        ModelConstructor learner = new ModelConstructor(this, false, conservativeMerging);
        learner.reconstruct(model);

        //get merge counter example collected during the merge
        for (Map.Entry<Integer, TreeSet<LinkedList<Integer>>> entry: learner.counterExamples.entrySet()) {
            Integer uid = entry.getKey();
            if (!observationRequirementTable.containsKey(uid)) {
                observationRequirementTable.put(uid, new TreeSet<>(Util::compareIntegerList));
            }
            observationRequirementTable.get(uid).addAll(entry.getValue());
        }

        // recompute prev and current
        log("RECONSTRUCT: recompute prev and current");
        Model.ModelState old = current;

        current = model.projectPTAStateToModel(ptaCurrent);
        prev = (ptaPrev != null) ? model.projectPTAStateToModel(ptaPrev) : null;

        log("" + current.abstractUi.id());
        log("" + ptaCurrent.uistate.id());
        log("" + current.id);

        // reconstruct frontier set
        log("RECONSTRUCT: frontier");
        frontiers.clear();
        identifiedStates.clear();

        if (!conservativeMerging) {
            for (Model.ModelState state : learner.redSet) {
                if (!state.isClosed()) {
                    log ("add to frontier (reconstruct): " + state.id);
                    frontiers.add(state);
                }
            }
        }
        else {
            for (Model.ModelState state : learner.redSet) {
                if (state.isClosed()) {
                    identifiedStates.add(state);
                    for (Set<Model.ModelState> children: state.outTransitions) {
                        if (children != null && children.size() == 1 && !getElement(children).isClosed()) {
                            frontiers.add(getElement(children));
                        }
                    }
                }
            }
        }

        // recompute monkey frontiers
        monkeyFrontiers.clear();
        for (Model.ModelState state: learner.redSet) {
            if (state.isFailState()) continue;
            monkeyFrontiers.add(state);
        }

        for (PTA.PTAState ptaState: monkeyCovered) {
            Model.ModelState modelState = model.projectPTAStateToModel(ptaState);
            monkeyFrontiers.remove(modelState);
        }
    }

    void generateExecutionPlan() {
        isMonkeyPlan = false;

        if (lastTid.equals(CLOSE)) {
            buildStartPlan();
            return;
        }

        if (current.abstractUi.getEventCount() == 1) {
            buildResetPlan();
            return;
        }

        if (testingPhase == Phase.Explore) {
            if (!explorationTimedOut()) {
                log("Generating exploration plan");
                if (currentExecutionTrace.size() > executionTraceThreshold) {
                    buildResetPlan();
                    return;
                }

                buildExplorationPlan();
                return;
            }

            // exploration timeout
            log("Switch to Monkey mode");
            testingPhase = Phase.Monkey;
        }

        if (testingPhase == Phase.Monkey) {
            if (!monkeyFrontiers.isEmpty()) {
                log("Generating monkey driving plan");
                if (buildPlanWithTrials(current, testingPhase)){
                    isMonkeyPlan = true;
                    return;
                }

                log("Temporarily switch to Explore mode");
                buildExplorationPlan();
                return;
            }

            if (!frontiers.isEmpty()){
                log("Switch to Explore mode");
                explorationStartTime = System.currentTimeMillis();
                testingPhase = Phase.Explore;
                generateExecutionPlan();
                return;
            }
        }

        RPlanCount++;
        buildRefinementPlan(current);
    }

    void buildExplorationPlan() {
        if (!frontiers.isEmpty()) {
            if (buildPlanWithTrials(current, Phase.Explore)) return;
            if (!model.roots.values().contains(current)) return;
        }
        RPlanCount++;
        buildRefinementPlan(current);
    }

    boolean explorationTimedOut() {
        if (!useHybridMode) return false;

        long elapsedTime;
        if (explorationStartTime == 0) {
            elapsedTime = HistoryManager.instance().getElapsedTime();
        }
        else {
            elapsedTime = System.currentTimeMillis() - explorationStartTime;
        }

        System.out.println(elapsedTime);
        System.out.println(explorationBudget);
        return elapsedTime > explorationBudget;
    }

    void buildResetPlan() {
        executionPlan = new Integer[2];
        executionPlan[0] = current.abstractUi.id();
        executionPlan[1] = CLOSE;
        executionCursor = 0;
    }

    void buildStartPlan() {
        executionPlan = new Integer[2];
        executionPlan[0] = current.abstractUi.id();
        executionPlan[1] = START;
        executionCursor = 0;
    }

    // return false if the constructed plan is the reset plan.
    boolean buildPlanWithTrials (Model.ModelState current, Phase phase) {
        for (int i = eDgreeMax; i >= 0; i--) {
            if (buildPlan(current, false, i, phase)) {
                DPlanCount++;
                return true;
            }
        }
        for (int i = eDgreeMax; i >= 0; i--) {
            if (buildPlan(current, true, i, phase)) {
                NDPlanCount++;
                return true;
            }
        }

        buildResetPlan();
        return false;
    }

    private boolean doubt() {
        double threshold = 0.9;
        return rand.nextDouble() > threshold;
    }

    boolean buildPlan(Model.ModelState current, boolean nd, int eDgree, Phase phase) {
        if (nd){
            if (eDgree > 0) log("Try ND-EF plan");
            else log("Try ND plan");
        }
        else {
            if (eDgree > 0) log("Try D-EF plan");
            else log("Try D plan");
        }

        HashSet<Model.ModelState> visitedStates = new HashSet<>();
        LinkedList<Model.ModelState> queue = new LinkedList<>();
        HashSet<Model.ModelState> queuedStates = new HashSet<>();

        HashMap<Model.ModelState, Model.ModelState> parentMapBFS =  new HashMap<>();
        HashMap<Model.ModelState, Integer> transitionMapBFS = new HashMap<>();

        queue.addLast(current);
        queuedStates.add(current);

        Model.ModelState target = null;
        while(!queue.isEmpty()) {
            Model.ModelState cur = queue.removeFirst();
            queuedStates.remove(cur);
            visitedStates.add(cur);

            if (cur.isFailState()) continue;
            if (isFrontierState(cur, eDgree, phase)) {
                target = cur;
                break;
            }
            if (!conservativeMerging && doubt()) {
                boolean flag = false;
                for (int i = 0; i < cur.abstractUi.getEventCount(); i++) {
                    if (CLOSE.equals(i)) continue;
                    HashSet<Model.ModelState> children = getChildren(cur, i, eDgree);
                    if (children != null && children.contains(model.getFailState())) continue;
                    flag = true;
                    break;
                }
                if (flag) {
                    target = cur;
                    break;
                }
            }

            int p[] = Util.permutation(cur.abstractUi.getEventCount(), rand);
            for (int i = 0; i<cur.abstractUi.getEventCount(); i++) {
                if (p[i] == CLOSE) continue;

                HashSet<Model.ModelState> children = getChildren(cur, p[i], eDgree);
                if (children == null) continue;
                if (isSuppressedEvent(cur, p[i], eDgree, children)) continue;
                if (!nd && children.size() > 1) continue;

                //TODO: statistical reasoning
                if (children.contains(model.getFailState())) continue;

                for (Model.ModelState successor: children) {
                    if (visitedStates.contains(successor)) continue;
                    if (queuedStates.contains(successor)) continue;

                    queue.addLast(successor);
                    queuedStates.add(successor);

                    parentMapBFS.put(successor, cur);
                    transitionMapBFS.put(successor, p[i]);
                }
            }
        }

        if (target == null){
            return false;
        }

        LinkedList<Integer> backTrace = new LinkedList<>();
        if (phase == Phase.Explore)
        {
            int eventIndex = -1;
            int p[] = Util.permutation(target.abstractUi.getEventCount(), rand);

            if (isFrontierState(target, eDgree, phase)) {
                for (int i = 0; i < target.abstractUi.getEventCount(); i++) {
                    if (CLOSE.equals(p[i])) continue;

                    HashSet<Model.ModelState> children = getChildren(target, p[i], eDgree);
                    if (children != null) continue;
                    if (isSuppressedEvent(target, p[i], eDgree, children)) continue;
                    eventIndex = p[i];
                    break;
                }
            }
            else {
                for (int i = 0; i < target.abstractUi.getEventCount(); i++) {
                    if (CLOSE.equals(p[i])) continue;

                    HashSet<Model.ModelState> children = getChildren(target, p[i], eDgree);
                    if (children != null && children.contains(model.getFailState())) continue;
                    eventIndex = p[i];
                    break;
                }
            }

            if (eventIndex == -1) {
                System.out.println(isFrontierState(target, eDgree, phase));
                System.out.println(target.isFailState());
                System.out.println(target.id);
                System.out.println(target.abstractUi.getEventCount());
                throw new RuntimeException();
            }

            if (restartAfterEveryGoal) {
                backTrace.push(CLOSE);
                backTrace.push(WILDCARD);
            }
            backTrace.push(eventIndex);
        }
        else { //phase == Phase.MONKEY
            backTrace.push(CLOSE);
            backTrace.push(WILDCARD);
            backTrace.push(MONKEY);
        }

        backTrace.push(target.abstractUi.id());

        while (parentMapBFS.containsKey(target)) {
            backTrace.push(transitionMapBFS.get(target));
            target = parentMapBFS.get(target);
            backTrace.push(target.abstractUi.id());
        }

        executionPlan = new Integer[backTrace.size()];
        executionCursor = 0;

        for (int i=0; i<executionPlan.length; i++) {
            executionPlan[i] = backTrace.pop();
        }

        printExecutionPlan();
        return true;
    }

    HashSet<Model.ModelState> getChildren(Model.ModelState state, int index, int eDegree) {
        HashSet<Model.ModelState> children = state.outTransitions[index];

        if (children == null && extrapolation != null && eDegree > 0)
            children = extrapolation.extrapolate(state, index, eDegree);

        return children;
    }

    boolean isSuppressedEvent(Model.ModelState state, int index, int eDgree, HashSet<Model.ModelState> children) {
        EventInfo info = state.abstractUi.getEventsInfo().get(index);
        String event = state.abstractUi.getEvent(index);

        // Suppress long click if it is identical to click
        if (event.contains("dlong") && info.brothers != null && info.brothers.size() > 1) {
            for (Integer i : info.brothers) {
                if (i == index) continue;
                String brother = state.abstractUi.getEvent(i);
                if (brother.contains("dclick")) {
                    HashSet<Model.ModelState> bChildren = getChildren(state, i, eDgree);
                    if (bChildren == null) return true; //prioritizing click
                    if (isSuppressedEvent(state, i, eDgree, bChildren)) return true; //suppress long click if click is suppressed
                    if (bChildren != null && children != null) {
                        for (Model.ModelState bChild: bChildren) {
                            if (!children.contains(bChild)) break;
                        }
                        return true;
                    }
                }
            }
        }

        // Suppress a cluster item if there are three or more observed items in the cluster
        // and at least two of them are identical.
        if (info.cluster != null && info.cluster.size() > 3) {
            LinkedList<HashSet<Model.ModelState>> observedEvents = new LinkedList<>();
            for (Integer i: info.cluster) {
                HashSet<Model.ModelState> cChildren = getChildren(state, i, eDgree);
                if (cChildren != null) observedEvents.add(cChildren);
            }

            if (observedEvents.size() >= 3) {
                HashSet<Model.ModelState> c1 = observedEvents.get(0);
                HashSet<Model.ModelState> c2 = observedEvents.get(1);
                HashSet<Model.ModelState> c3 = observedEvents.get(2);
                if (!c1.equals(c2) && !c2.equals(c3) && !c3.equals(c3)) {
                    return false;
                }
            }

            if (info.indexInCluster >= 3) {
                log("Suppressing list item!: " +  info.indexInCluster);
                return true;
            }
        }

        return false;
    }

    boolean isFrontierState(Model.ModelState state, int eDegree, Phase phase) {
        if (phase == Phase.Explore) {
            if (extrapolation != null && eDegree > 0) {
                return !extrapolation.isClosed(state, eDegree);
            } else {
                return frontiers.contains(state);
            }
        }
        else { //phase == Monkey
            return monkeyFrontiers.contains(state);
        }
    }

    boolean buildRefinementPlan(Model.ModelState current) {
        log("Try R plan");
        executionPlan = new Integer[2];
        int baseUI = current.abstractUi.id();

        int eventIndex;
        HashSet<Integer> blackList = new HashSet();

        while (true) {
            eventIndex = Util.getRandomEvent(current.abstractUi.getEventCount(), blackList, rand);
            if (eventIndex == -1) {
                buildResetPlan();
                return true;
            }

            Model.ModelState failState = model.getFailState();
            HashSet<Model.ModelState> children = current.outTransitions[eventIndex];
            if (children == null || !children.contains(failState)) {
                blackList.add(eventIndex);
                break;
            }
        }

        executionPlan[0] = baseUI;
        executionPlan[1] = eventIndex;
        executionCursor = 0;
        return true;
    }

    private void printTrace() {
        printIntSet(currentExecutionTrace, "Trace", this);
    }

    private void printRoots() {
        printIntSet(model.roots.keySet(), "Roots (uid)", this);
        printStateSet(model.roots.values(), "Roots (id)");
    }

    private void printFrontiers() {
        printStateSet(frontiers, "Frontiers");
        if (conservativeMerging) {
            printStateSet(identifiedStates, "Identified");
        }
    }

    private void printMonkeyFrontiers() {
        printStateSet(monkeyFrontiers, "MonkeyFrontiers");
    }

    private void printExecutionPlan() {
        printIntSet(Arrays.asList(executionPlan), "Execution plan", this);
        log("Execution cursor:" + executionCursor);
    }

    void printObservedUI() {
        DecimalFormat df = new DecimalFormat("#.00");
        StringBuilder builder = new StringBuilder("Observed UI : [");

        boolean flag = false;
        for(Integer id: model.uiToStates.keySet()) {
            AbstractUI uistate = AbstractUI.getStateById(id);
            if (uistate.isFailState()) continue;

            if (flag) builder.append(", ");
            else flag = true;

            builder.append(id);
            builder.append("(" + model.uiToStates.get(id).size() + ")");
        }
        log(builder.toString() + "]");
    }

    void printStateSet(Collection<Model.ModelState> set, String tag) {
        StringBuilder builder = new StringBuilder(tag + ": [");
        boolean flag = false;
        for(Model.ModelState state: set) {
            if (flag) builder.append(", ");
            else flag = true;
            builder.append(state.id);
        }
        log(builder.toString() + "]");
    }

    @Override
    public boolean requiresAutoRestart() {
        return false;
    }

    static private <T> T getElement(Collection<T> set) {
        if (set.isEmpty()) return null;
        T elt = null;
        for (T t: set) {
            elt = t;
            break;
        }
        return elt;
    }
}