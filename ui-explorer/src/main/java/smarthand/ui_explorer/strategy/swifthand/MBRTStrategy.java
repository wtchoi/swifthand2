package smarthand.ui_explorer.strategy.swifthand;

import com.google.common.base.Function;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.json.JSONObject;
import smarthand.ui_explorer.*;
import smarthand.ui_explorer.Strategy;
import smarthand.ui_explorer.trace.*;
import smarthand.ui_explorer.util.Util;
import smarthand.ui_explorer.visualize.*;

import java.io.IOException;
import java.util.*;

/**
 * Created by wtchoi on 6/22/16.
 *
 * MBRT stands for Model Based Rapid Testing
 */
public class MBRTStrategy extends Strategy {
    private enum PlanType {
        Explore, Replay, Reset, Stop, Start, Refinement, ExactReplay;
    }

    public enum ExploitationStrategy {
        UiGreedyBlockGoal,              //  TODO: explain
        EdgeGreedyBlockGoal,            //  TODO: explain
        EdgeGreedyBlockTransition,      //  TODO: explain
        UiBoundedPath,
        EdgeBoundedPath,
    }

    public enum ExplorationStrategy {
        IgnoreOldTrace, StateCover
    }

    public enum StrategyOption {
        SkipUICheck,        // Only check the existence of events instead of checking the entire UI
    }

    ExploitationStrategy exploitationStrategy;
    ExplorationStrategy explorationStrategy = ExplorationStrategy.StateCover;
    HashSet<StrategyOption> options = new HashSet<>();

    boolean initialized = false;

    PTA pta;
    PTA.PTAState ptaCurrent = null;
    PTA.PTAState ptaPrev = null;

    Model model;
    Model.ModelState current = null;
    Model.ModelState prev = null;

    LinkedList<Integer> currentExecutionTrace = new LinkedList<>();

    PlanType planType;
    Integer[] executionPlan = null;
    Integer executionCursor = 0;
    Integer lastTid;

    HashSet<Model.ModelState> frontiers = new HashSet<>();

    HashSet<Integer> previouslyCoveredMethods = new HashSet<>();
    HashSet<Integer> previouslyCoveredBranches = new HashSet<>();

    HashSet<Integer> coveredUI = new HashSet<>();
    HashSet<Integer> coveredBranches = new HashSet<>();
    HashSet<Integer> coveredMethods = new HashSet<>();

    HashMap<Integer, Integer> uiTrialCount = new HashMap<>();
    HashMap<Integer, Integer[]> transitionTrialCount = new HashMap<>();

    LinkedList<Integer> goalTransitions = new LinkedList<>();

    HashMap<Integer, Integer[]> failedTransitionCount = new HashMap();

    // controlling the rate of exploration and exploitation
    LinkedList<PlanType> recentPlans = new LinkedList<>();
    static int PLAN_HISTORY_LIMIT = 10;
    static double PLAN_EXPLORATION_RATE_THRESHOLD = 0.7;

    boolean exploitationFinished = false;
    boolean exploitationJustFinished = false;
    boolean exactTraceFinished = false;

    boolean ignoreOldTrace = false;

    // constants events
    static Integer RESET = 111111;
    static Integer MONKEY = 222222;
    static Integer WILDCARD = 333333;
    static Integer DUMMY = 444444;

    // replay algorithm parameter
    static int executionTraceThreshold = 30;    // control reset frequency
    static int REPLAY_TRIAL_LIMIT = 5;          // control black listing
    static int PATH_LENGTH_LIMIT= 5;            // control plan length (for bounded path construction)

    // stats
    int DPlanCount = 0;
    int NDPlanCount = 0;
    int NDFailure = 0;
    int RPlanCount = 0;
    int ReplayCount = 0;
    int ExploreCount = 0;
    int ExactReplayCount = 0;
    int ReplayFail = 0;
    int ExactReplayFail = 0;
    int reconstructionCount = 0;
    int crashCount = 0;
    int resetCount = 0;
    boolean newPlan = false;

    // tracer
    Tracer tracer = new Tracer();

    // helpers
    MBRTModelTraversalHelper helper = new MBRTModelTraversalHelper(this);
    Random rand = new Random(0);

    @Override
    public void setRandomSeed(int seed) {
        rand = new Random(seed);
    }

    public MBRTStrategy(String path, ExploitationStrategy exploitationStrategy) {
        JSONObject obj = Util.readJsonFile(path);
        AbstractUI.loadAbstractionsFromJson(obj.getJSONArray("ui"));
        pta = PTA.loadJson(obj.getJSONArray("pta"));
        model = new Model(pta);
        this.exploitationStrategy = exploitationStrategy;

        String output_dir = Options.get(Options.Keys.OUTPUT_DIR);
        String pta_path = output_dir + "/pta_out.json";
        Util.writeJsonFile(pta_path, pta.exportToJson());
    }

    private void analyzePTA() {
        PTA.traverse(pta, new PTA.PTAVisitor() {
            @Override
            public void visitPre(PTA.PTAState state) {
                if (state.coveredBranches != null) {
                    previouslyCoveredBranches.addAll(state.coveredBranches);
                }
                if (state.coveredMethods != null) {
                    previouslyCoveredMethods.addAll(state.coveredMethods);
                }
            }
        });
    }

    @Override
    public void reportExecution(DeviceInfo deviceInfo, Coverage coverage, boolean escaped, boolean blocked) {
        //log("Filtered Events");
        //int eventCounter = 0;
        //for (String s: deviceInfo.filteredEvents) {
        //    log(eventCounter + ". "  + s);
        //    eventCounter++;
        //}

        //String currentActivity = (deviceInfo.activityStack.size() == 0)
        //        ? "null"
        //        : deviceInfo.activityStack.getLast();
        String currentActivity = deviceInfo.focusedActivity;
        if (currentActivity == null) currentActivity = "null";

        Boolean isKeyboardShown = deviceInfo.isKeyboardShown;

        AbstractUI uistate = blocked || escaped
                ? AbstractUI.getFailState()
                : AbstractUI.getState(currentActivity, isKeyboardShown, deviceInfo.filteredEvents, deviceInfo.eventInfo);

        int uid = uistate.id();

        coveredUI.add(uistate.id());
        coveredMethods.addAll(deviceInfo.coveredMethods);
        coveredBranches.addAll(deviceInfo.coveredBranches);


        ConcreteUI currentCui = ConcreteUI.getFromRawInfo(deviceInfo.appGuiTree, uistate);
        Action tty;

        if (!initialized) {
            initialized = true;

            reconstructModelBF();
            if (model.roots.containsKey(uid)) {
                analyzePTA();
                ptaCurrent = pta.roots.get(uid);
                current = model.roots.get(uid);
            }
            else {
                ptaCurrent = pta.populateRoot(uistate);
                current = getRoot(ptaCurrent);
            }
            currentExecutionTrace.addLast(uid);
            ptaCurrent.updateCoverage(coverage.methodCoverage, coverage.branchCoverage);
            ptaCurrent.isRealized = true;

            tty = Action.getStart();
        }
        else {
            updateStat(uistate, escaped, blocked);
            updateTrace(uistate, escaped, blocked);
            updatePTAForest(uistate, deviceInfo, escaped, blocked);
            updateModel(ptaCurrent, deviceInfo, escaped, blocked);

            updateExecutionPlan(uistate, escaped, blocked);
            updatePlanHistory();
            if (exploitationFinished && exploitationJustFinished) {
                exploitationJustFinished = false;
                switchPhase();
            }

            // record trace
            tty = lastTid == RESET ? Action.getReset() : Action.getEvent(lastTid);
        }

        tracer.on(tty, currentActivity, isKeyboardShown, currentCui, uistate, coverage.branchCoverage, coverage.methodCoverage, null);
    }

    private void switchPhase() {
        //turn off unrealized node in PTA and reconstruct
        log("SWITCHING THE PHASE!");
        switch (explorationStrategy) {
            case IgnoreOldTrace: {
                ignoreOldTrace = true;
                reconstructModelBF();
                model.purge();
                break;
            }
            case StateCover: {
                uiTrialCount.clear();
                break;
            }
        }
    }

    private void updateStat(AbstractUI state, boolean escaped, boolean blocked) {
        if (escaped){
            crashCount++;
        }
    }

    void updateTrace(AbstractUI abstractUi, boolean escaped, boolean blocked) {
        Integer executedEventIndex = executionPlan[executionCursor+1];
        if (executedEventIndex.equals(RESET)) {
            currentExecutionTrace.clear();
            currentExecutionTrace.addLast(abstractUi.id());
        }
        else {
            currentExecutionTrace.addLast(executedEventIndex);
            currentExecutionTrace.addLast(abstractUi.id());
        }
    }

    void updatePTAForest(AbstractUI uistate, DeviceInfo deviceInfo, boolean escaped, boolean blocked) {
        Integer executedEventIndex = executionPlan[executionCursor+1];

        if (executedEventIndex.equals(RESET)) {
            // The starting point was observed previously.
            ptaCurrent = pta.populateRoot(uistate);
            ptaPrev = null;
        }
        else {
            log("ui state: " + uistate.id());
            ptaPrev = ptaCurrent;
            ptaCurrent = ptaCurrent.populateChild(uistate, executedEventIndex);
            ptaCurrent.isRealized = true;
        }

        ptaCurrent.updateCoverage(deviceInfo.coveredMethods, deviceInfo.coveredBranches);
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
        if (escaped || blocked) return false;

        Integer expectedUIStateId = executionPlan[executionCursor+2];
        if (expectedUIStateId == WILDCARD || uistate.id() == expectedUIStateId) {
            return true;
        }

        if(options.contains(StrategyOption.SkipUICheck)) {
            AbstractUI expectedState = AbstractUI.getStateById(expectedUIStateId);
            Integer nextEvent = executionPlan[executionCursor+3];
            if (nextEvent.equals(DUMMY)) return true;
            if (uistate.getEventCount() > nextEvent && expectedState.getEvent(nextEvent).equals(uistate.getEvent(nextEvent))) {
                return true;
            }
        }

        log("Current:" + uistate.id());
        log("Expected:" + expectedUIStateId);
        log("UNEXPECTED");
        return false;
    }

    void updateExecutionPlan(AbstractUI uistate, boolean escaped, boolean blocked) {
        if (!escaped && !blocked && !isExecutionFinished() && isExpectedExecution(uistate, escaped, blocked)) {
            executionCursor = executionCursor + 2;
            if (executionPlan[executionCursor+1].equals(DUMMY)) {
                executionPlan = null;
                executionCursor = 0;
            }
            return;
        }

        // Either the execution is finished or didn't went well.
        if (!escaped && !blocked && !isExpectedExecution(uistate, escaped, blocked)) {
            HashSet<Model.ModelState> children = prev.outTransitions[executionPlan[executionCursor+1]];
            if (children != null && children.size() > 1) {
                NDFailure++;
            }

            if (planType == PlanType.Replay) {
                ReplayFail++;

                //count transition failure
                int previousUiStateId = executionPlan[executionCursor];
                AbstractUI previousAbstractUi = AbstractUI.getStateById(previousUiStateId);
                int executedEventId = executionPlan[executionCursor+1];
                Integer[] counter;
                if (!failedTransitionCount.containsKey(previousUiStateId)) {
                    counter = new Integer[previousAbstractUi.getEventCount()];
                    failedTransitionCount.put(previousUiStateId, counter);
                }
                else {
                    counter = failedTransitionCount.get(previousUiStateId);
                }

                if (counter[executedEventId] == null) {
                    counter[executedEventId] = 0;
                }
                else {
                    counter[executedEventId]++;
                }
            }
        }

        // count goal transition trials
        if (!goalTransitions.isEmpty()) {
            transitionTrialCount.get(goalTransitions.get(0))[goalTransitions.get((1))]++;
            log("========== COUNT: " + goalTransitions.get(0) + " -" + goalTransitions.get(1) + "->");
            goalTransitions.clear();
        }

        executionPlan = null;
        executionCursor = 0;
    }

    void updateModel(PTA.PTAState ptaCurrent, DeviceInfo deviceInfo, boolean escaped, boolean blocked) {
        // Retrieve failure information
        PTA.PTAState ptaNext = null;
        int executedEventIndex = executionPlan[executionCursor+1];

        if (executedEventIndex != RESET) {
            HashSet<Model.ModelState> knownTargetStates = current.outTransitions[executedEventIndex];
            if (knownTargetStates != null) {
                // Case 1: The resulting screen conforms to one of expected states
                for (Model.ModelState state : knownTargetStates) {
                    if (state.abstractUi == ptaCurrent.uistate) {
                        state.ptaStates.add(ptaCurrent);
                        prev = current;
                        current = state;
                        log("UpdateModel: the resulting screen conforms to one of expected states");
                        updateModelEdge(prev, current, executedEventIndex, deviceInfo);
                        return;
                    }
                }

                // Case 2: The transition is deterministic, and the observation does not match.
                //         Conflict detected.
                if (knownTargetStates.size() == 1){
                    if (ptaPrev.transition[executedEventIndex].size() == 1) {
                        if (!model.uiToStates.containsKey(ptaCurrent.uistate.id())) {
                            model.uiToStates.put(ptaCurrent.uistate.id(), new HashSet());
                        }
                        reconstructModelBF();
                        log("UpdateModel: non-determinism detected. reconstructing the model");
                        updateModelEdge(prev, current, executedEventIndex, deviceInfo);
                        return;
                    }
                }
            }

            // Case 3
            // Case 3-1: the transition is non-deterministic
            // Case 3-2: the transition is not observed before
            Model.ModelState targetState = pickState(ptaCurrent);
            current.addChild(targetState, executedEventIndex);
            if (current.isClosed()) {
                frontiers.remove(current);
            }

            prev = current;
            current = targetState;
            log("UpdateModel: update model");
        }

        // the event was restart or the execution was failed.
        if (executedEventIndex == RESET) {
            prev = null;
            current = getRoot(ptaCurrent);
        }
        updateModelEdge(prev, current, executedEventIndex, deviceInfo);

    }

    private void updateModelEdge(Model.ModelState from, Model.ModelState to, int eventIndex, DeviceInfo deviceInfo) {
        if (from == null) return;

        if (from.mayMethods[eventIndex] == null) {
            from.mayMethods[eventIndex] = new HashSet<>();
        }
        from.mayMethods[eventIndex].addAll(deviceInfo.coveredMethods);

        if (from.mayBranches[eventIndex] == null) {
            from.mayBranches[eventIndex] = new HashSet<>();
        }
        from.mayBranches[eventIndex].addAll(deviceInfo.coveredBranches);
    }

    private Model.ModelState getRoot(PTA.PTAState ptaState) {
        if (model.roots.containsKey(ptaState.uistate)) {
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
        }
        return state;
    }

    void reconstructModelBF() {
        reconstructionCount++;

        log("RECONSTRUCTION BEGIN!");
        ModelConstructor learner = new ModelConstructor(this, ignoreOldTrace, false);
        learner.reconstruct(model);

        // recompute prev and current
        log("RECONSTRUCT: recompute prev and current");
        current = ptaCurrent != null ? model.projectPTAStateToModel(ptaCurrent) : null;
        prev = ptaPrev != null ? model.projectPTAStateToModel(ptaPrev) : null;

        // reconstruct frontier set
        log("RECONSTRUCT: frontier");
        frontiers.clear();
        for (Model.ModelState state: learner.redSet) {
            if (!state.isClosed()) {
                frontiers.add(state);
            }
        }
    }

    @Override
    public String getNextAction() {
        if (executionPlan == null) {
            generateExecutionPlan();
            printExecutionPlan();
            newPlan = true;
        }
        else {
            newPlan = false;
        }

        Integer eventIndex = executionPlan[executionCursor + 1];
        if (eventIndex.equals(RESET)) {
            resetCount++;
            lastTid = RESET;
            return "reset";
        }
        else {
            lastTid = eventIndex;
            AbstractUI currentAbstractUi = current.abstractUi;
            log("uid: " + currentAbstractUi.id());
            log("event count:" + currentAbstractUi.getEventCount());
            log("event to execute:" + eventIndex);

            if (!goalTransitions.isEmpty() &&
                  goalTransitions.get(0).equals(currentAbstractUi.id()) &&
                  goalTransitions.get(1).equals(eventIndex)) {
                goalTransitions.removeFirst();
                goalTransitions.removeFirst();
                transitionTrialCount.get(currentAbstractUi.id())[eventIndex]++;
                log("========== COUNT: " + currentAbstractUi.id() + " -" + eventIndex + "->");
            }

            if (eventIndex.equals(DUMMY)) {
                log("Something is wrong : " + newPlan);
            }

            return "event:" + eventIndex;
        }
    }

    private void updatePlanHistory() {
        if (planType == null) return;

        if (recentPlans.size() == PLAN_HISTORY_LIMIT) {
            recentPlans.removeFirst();
        }
        recentPlans.addLast(planType);

        if (!exploitationFinished) {
            int explorationCount = 0;
            for (PlanType p : recentPlans) {
                if (p != PlanType.Replay) {
                    explorationCount++;
                }
            }

            long explorationRate = ((long) explorationCount) / ((long) recentPlans.size());
            if (explorationRate >= PLAN_EXPLORATION_RATE_THRESHOLD) {
                exploitationFinished = true;
                exploitationJustFinished = true;
            }
        }
    }

    public void generateExecutionPlan() {
        if (current.abstractUi.getEventCount() == 0) {
            buildResetPlan();
            return;
        }

        if (currentExecutionTrace.size() > executionTraceThreshold) {
            buildResetPlan();
            return;
        }

        buildExplorationPlan();
        return;
    }

    void buildResetPlan() {
        planType = PlanType.Reset;
        executionPlan = new Integer[2];
        executionPlan[0] = current.abstractUi.id();
        executionPlan[1] = RESET;
        executionCursor = 0;
    }

    int consecutiveExploreCount = 0;

    void buildExplorationPlan() {
        if (!exploitationFinished || (exactTraceFinished && explorationStrategy != ExplorationStrategy.IgnoreOldTrace)) {
            if (buildPlanWithTrials(current, PlanType.Replay)) {
                consecutiveExploreCount = 0;
                ReplayCount++;
                planType = PlanType.Replay;
                log("Use replay plan");
                return;
            }

            consecutiveExploreCount++;
            if (consecutiveExploreCount % 3 == 0) {
                buildResetPlan();
                log("Use reset");
                return;
            }
        }

        if (!exactTraceFinished) {
            //boolean flag = buildPlanUsingPTA(ptaCurrent, x -> findTargetUsingPTA(x, true))
            //        || buildPlanUsingPTA(ptaCurrent, x -> findTargetUsingPTA(x, false));
            boolean flag = buildPlanUsingPTA(ptaCurrent, x -> findBestTargetUsingPTA(x));
            if (flag) {
                ExactReplayCount++;
                planType = PlanType.ExactReplay;
                log ("Use exact replay");
                return;
            }
            else {
                if (pta.roots.containsValue(ptaCurrent)) {
                    exactTraceFinished = true;
                    buildExplorationPlan();
                    return;
                }
                else {
                    buildResetPlan();
                    return;
                }
            }
        }

        if (!frontiers.isEmpty()) {
            if (buildPlanWithTrials(current, PlanType.Explore)){
                planType = PlanType.Explore;
                ExploreCount++;
                log("Use explore plan");
                return;
            }
            if (!model.roots.values().contains(current)) {
                planType = PlanType.Explore;
                ExploreCount++;
                log("Use reset plan");
                return;
            }
        }
        buildRefinementPlan(current);
        planType = PlanType.Refinement;
        RPlanCount++;
    }

    PTA.PTAState findTargetUsingPTA(PTA.PTAState ptaCurrent, boolean useMethodCoverage) {
        LinkedList<PTA.PTAState> queue = new LinkedList<>();
        queue.addLast(ptaCurrent);
        PTA.PTAState target = null;
        while (!queue.isEmpty()) {
            PTA.PTAState cur = queue.removeFirst();
            if (cur.isFailState()) continue;
            if (cur != ptaCurrent && isTargetPTAState(cur, useMethodCoverage)) {
                target = cur;
                break;
            }

            int p[] = Util.permutation(cur.uistate.getEventCount(), rand);
            for (int i=0; i<cur.uistate.getEventCount();i++) {
                Set<PTA.PTAState> children = cur.transition[p[i]];
                if (children == null) continue;
                if (children.isEmpty()) continue;
                if (children.size() > 1) continue;
                if (p[i] == 0 && pta.roots.values().contains(cur)) continue;
                children.forEach(child -> queue.addLast(child));
            }
        }
        return target;
    }

    PTA.PTAState findBestTargetUsingPTA(PTA.PTAState ptaCurrent) {
        Multimap<PTA.PTAState, Integer> methodCoverages = HashMultimap.create();
        Multimap<PTA.PTAState, Integer> branchCoverages = HashMultimap.create();
        HashMap<PTA.PTAState, Integer> distances = new HashMap();

        LinkedList<PTA.PTAState> terminals = new LinkedList<>();
        LinkedList<PTA.PTAState> queue = new LinkedList<>();

        queue.addLast(ptaCurrent);
        distances.put(ptaCurrent, 0);

        while(!queue.isEmpty()) {
            PTA.PTAState cur = queue.removeFirst();
            if (cur.isFailState()) continue;

            if (cur != ptaCurrent) {
                int improvement;

                HashSet<Integer> mSet = new HashSet<>();
                cur.coveredMethods.forEach(m -> {
                    if (!coveredMethods.contains(m)) mSet.add(m);
                });
                improvement = mSet.size();
                if (cur.parent != null && methodCoverages.containsKey(cur.parent)) {
                    mSet.addAll(methodCoverages.get(cur.parent));
                }
                methodCoverages.putAll(cur, mSet);

                HashSet<Integer> bSet = new HashSet<>();
                cur.coveredBranches.forEach(b -> {
                    if (!coveredBranches.contains(b)) bSet.add(b);
                });
                improvement = improvement + bSet.size();
                if (cur.parent != null && branchCoverages.containsKey(cur.parent)) {
                    bSet.addAll(branchCoverages.get(cur.parent));
                }
                branchCoverages.putAll(cur, bSet);

                if (improvement > 0) terminals.add(cur);
            }

            for (int i=0;i<cur.uistate.getEventCount(); i++) {
                Set<PTA.PTAState> children = cur.transition[i];
                if (children == null) continue;
                if (children.isEmpty()) continue;
                if (children.size() > 1) continue;
                if (i == 0 && pta.roots.values().contains(cur)) continue;
                children.forEach(child -> {
                    queue.addLast(child);
                    distances.put(child, distances.get(cur)+1);
                });
            }
        }

        PTA.PTAState target = null;
        double score = 0;
        for (PTA.PTAState candidate: terminals) {
            int distance = distances.get(candidate);
            double candidateScore = methodCoverages.get(candidate).size() + branchCoverages.get(candidate).size();
            candidateScore = candidateScore / ((double) (distance + 7));

            if (target == null || candidateScore > score) {
                target = candidate;
                score = candidateScore;
            }
        }
        return target;
    }

    boolean buildPlanUsingPTA(PTA.PTAState ptaCurrent, java.util.function.Function<PTA.PTAState, PTA.PTAState> selector) {
        PTA.PTAState target = selector.apply(ptaCurrent);
        if (target == null) return false;

        LinkedList<Integer> backTrace = new LinkedList<>();
        backTrace.push(DUMMY);

        PTA.PTAState cursor = target;
        backTrace.push(cursor.uistate.id());
        while (cursor != ptaCurrent) {
            backTrace.push(cursor.incomingEventIndex);
            cursor = cursor.parent;
            backTrace.push(cursor.uistate.id());
        }

        executionPlan = new Integer[backTrace.size()];
        executionCursor = 0;

        for (int i = 0; i < executionPlan.length; i++) {
            executionPlan[i] = backTrace.pop();
        }
        return true;
    }

    boolean isTargetPTAState(PTA.PTAState state, boolean useMethodCoverage) {
        if (useMethodCoverage) {
            if (!coveredMethods.containsAll(state.coveredMethods)) {
                return true;
            }
        }
        else {
            if (!coveredBranches.containsAll(state.coveredBranches)) {
                return true;
            }
        }

        return false;
    }

    // return false if the constructed plan is the reset plan.
    boolean buildPlanWithTrials (Model.ModelState current, PlanType planType) {
        if (buildPlan(current, false, 0, planType)) {
            DPlanCount++;
            return true;
        }

        if (buildPlan(current, true, 0, planType)) {
            NDPlanCount++;
            return true;
        }

        buildResetPlan();
        return false;
    }

    boolean buildPlan(Model.ModelState current, boolean nd, int eDgree, PlanType planType) {
        if (nd){
            log("Try ND plan");
        }
        else {
            log("Try D plan");
        }

        boolean isGreedy =
            planType == PlanType.Explore
                || (!exploitationFinished &&
                    (exploitationStrategy == ExploitationStrategy.UiGreedyBlockGoal
                        || exploitationStrategy == ExploitationStrategy.EdgeGreedyBlockGoal
                        || exploitationStrategy == ExploitationStrategy.EdgeGreedyBlockTransition))
                || (exploitationFinished);

        if (isGreedy) {
            HashSet<Model.ModelState> visitedStates = new HashSet<>();
            LinkedList<Model.ModelState> queue = new LinkedList<>();
            HashSet<Model.ModelState> queuedStates = new HashSet<>();

            HashMap<Model.ModelState, Model.ModelState> parentMapBFS = new HashMap<>();
            HashMap<Model.ModelState, Integer> transitionMapBFS = new HashMap<>();

            queue.addLast(current);
            queuedStates.add(current);

            Model.ModelState target = null;
            while (!queue.isEmpty()) {
                Model.ModelState cur = queue.removeFirst();
                queuedStates.remove(cur);
                visitedStates.add(cur);

                if (cur.isFailState()) continue;
                if (isTargetState(cur, eDgree, planType)) {
                    target = cur;
                    break;
                }

                int p[] = Util.permutation(cur.abstractUi.getEventCount(), rand);
                for (int i = 0; i < cur.abstractUi.getEventCount(); i++) {
                    HashSet<Model.ModelState> children = getChildren(cur, p[i], eDgree);
                    if (children == null) continue;
                    if (isSuppressedEvent(cur, p[i], eDgree, children)) continue;
                    if (!nd && children.size() > 1) continue;

                    //TODO: statistical reasoning
                    if (children.contains(model.getFailState())) continue;

                    if (planType == PlanType.Replay && !exploitationFinished && explorationStrategy.equals(ExploitationStrategy.EdgeGreedyBlockTransition)) {
                        if (failedTransitionCount.containsKey(cur.id)) {
                            Integer[] counter = failedTransitionCount.get(cur.id);
                            if (counter[i] != null && counter[i] > REPLAY_TRIAL_LIMIT) continue;
                        }
                    }

                    for (Model.ModelState successor : children) {
                        if (visitedStates.contains(successor)) continue;
                        if (queuedStates.contains(successor)) continue;

                        queue.addLast(successor);
                        queuedStates.add(successor);

                        parentMapBFS.put(successor, cur);
                        transitionMapBFS.put(successor, p[i]);
                    }
                }
            }

            if (target == null) {
                return false;
            }

            LinkedList<Integer> backTrace = new LinkedList<>();
            int eventIndex = DUMMY;

            switch (planType) {
                case Explore:
                    int p[] = Util.permutation(target.abstractUi.getEventCount(), rand);
                    for (int i = 0; i < target.abstractUi.getEventCount(); i++) {
                        HashSet<Model.ModelState> children = getChildren(target, p[i], eDgree);
                        if (children != null) continue;
                        if (isSuppressedEvent(target, p[i], eDgree, children)) continue;
                        eventIndex = p[i];
                        break;
                    }
                    break;
                case Replay:
                    if (!exploitationFinished) {
                        switch (exploitationStrategy) {
                            case EdgeGreedyBlockGoal:
                            case EdgeGreedyBlockTransition:
                                int candidateIndex = eventIndex;
                                int candidateScore = 0;

                                AbstractUI targetUI = target.abstractUi;
                                Integer[] trialCounts =
                                        exploitationStrategy == ExploitationStrategy.EdgeGreedyBlockGoal
                                                ? transitionTrialCount.get(targetUI.id())
                                                : failedTransitionCount.get(targetUI.id());

                                if (trialCounts == null && exploitationStrategy == ExploitationStrategy.EdgeGreedyBlockTransition) {
                                    trialCounts = new Integer[targetUI.getEventCount()];
                                    failedTransitionCount.put(targetUI.id(), trialCounts);
                                }

                                for (int i = 0; i < targetUI.getEventCount(); i++) {
                                    if (target.outTransitions[i] == null) continue;
                                    if (target.mayMethods[i] == null) continue;


                                    if (trialCounts[i] == null) {
                                        trialCounts[i] = 0;
                                    }

                                    if (trialCounts[i] < REPLAY_TRIAL_LIMIT) {
                                        int newMethodCount = 0;
                                        for (Integer m : target.mayMethods[i]) {
                                            if (!coveredMethods.contains(m)) newMethodCount++;
                                        }
                                        if (newMethodCount == 0) continue;

                                        //log("candidate: " + i);
                                        //log("candidate score1:" + newMethodCount);
                                        //log("candidate score2:" + target.mayMethods[i].size());
                                        //log("covered methods:" + coveredMethods.size());

                                        if (newMethodCount > candidateScore) {
                                            candidateIndex = i;
                                            candidateScore = newMethodCount;
                                        }
                                    }
                                }
                                eventIndex = candidateIndex;
                                if (eventIndex == DUMMY) {
                                    throw new RuntimeException("Something is wrong!");
                                }
                                break;
                            default:
                                // No need to anything for EdgeGreedyBlockTransition
                                break;
                        }
                    }
                    else {
                        switch (explorationStrategy) {
                            case StateCover:
                                eventIndex = DUMMY;
                                break;
                            case IgnoreOldTrace:
                                throw new RuntimeException("Unreachable!");
                        }
                    }
                    break;
            }

            backTrace.push(eventIndex);
            assert eventIndex != -1;

            Model.ModelState cursor = target;
            backTrace.push(cursor.abstractUi.id());
            while (parentMapBFS.containsKey(cursor)) {
                backTrace.push(transitionMapBFS.get(cursor));
                cursor = parentMapBFS.get(cursor);
                backTrace.push(cursor.abstractUi.id());
            }

            executionPlan = new Integer[backTrace.size()];
            executionCursor = 0;

            for (int i = 0; i < executionPlan.length; i++) {
                executionPlan[i] = backTrace.pop();
            }

            // update replay statistics
            if (planType == PlanType.Replay) {
                updateReplayTrialStatistics(target, eventIndex);
            }

            return true;
        }
        else {
            if (exploitationStrategy == ExploitationStrategy.UiBoundedPath || exploitationStrategy == ExploitationStrategy.EdgeBoundedPath) {
                int budget = PATH_LENGTH_LIMIT;
                BoundedOptimalPathSearcher searcher = new BoundedOptimalPathSearcher(exploitationStrategy);
                searcher.search(current, budget);
                if (searcher.bestScore == 0) return false;

                executionPlan = new Integer[searcher.bestPlan.size()];
                for (int i = 0; i < searcher.bestPlan.size(); i++) {
                    executionPlan[i] = searcher.bestPlan.get(i);
                }
                goalTransitions = searcher.bestGoals;

                StringBuilder builder = new StringBuilder();
                Util.makeIntArrToString(executionPlan, ",", builder);
                log("constructed plan: [" + builder.toString() + "]");
                return true;
            }
            else {
                throw new RuntimeException("Not implemented");
            }
        }
    }

    class BoundedOptimalPathSearcher {
        ExploitationStrategy strategy;
        LinkedList<Integer> bestPlan = new LinkedList<>();
        LinkedList<Integer> bestGoals = new LinkedList<>();
        HashSet<Integer> bestCoverage = new HashSet<>();
        long bestScore = 0;

        public BoundedOptimalPathSearcher(ExploitationStrategy strategy) {
            this.strategy = strategy;
        }

        public void search(Model.ModelState state, int search_budget) {
            LinkedList<Integer> plan = new LinkedList<>();
            LinkedList<Integer> goals = new LinkedList<>();
            HashSet<Integer> coverage = new HashSet<>();
            HashSet<Model.ModelState> visitedStates = new HashSet<>();

            log("---- search start ----");
            log("state: " + state.id);
            log("uid: " + state.abstractUi.id());
            searchImpl(state, search_budget, plan, goals, coverage, 0, visitedStates);
        }

        private void searchImpl(Model.ModelState state,
                                int budget,
                                LinkedList<Integer> currentPlan,
                                LinkedList<Integer> currentGoals,
                                HashSet<Integer> currentCoverage,
                                long currentScore,
                                HashSet<Model.ModelState> visitedStates) {

            if (budget < 0) throw new RuntimeException("Something is wrong");

            if (currentScore > bestScore) {
                bestCoverage = new HashSet<>(currentCoverage);
                bestPlan = new LinkedList<>(currentPlan);
                bestGoals = new LinkedList<>(currentGoals);
                bestScore = currentScore;
            }

            if (budget == 0) return;
            currentPlan.addLast(state.abstractUi.id());
            visitedStates.add(state);

            if (!transitionTrialCount.containsKey(state.abstractUi.id())) {
                transitionTrialCount.put(state.abstractUi.id(), new Integer[state.abstractUi.getEventCount()]);
            }
            Integer[] trialCounts = transitionTrialCount.get(state.abstractUi.id());


            for (int i=0; i<state.outTransitions.length; i++) {
                HashSet<Model.ModelState> children = state.outTransitions[i];
                if (children == null) continue;
                if (children.contains(model.getFailState())) continue;
                if (trialCounts[i] == null) trialCounts[i] = 0;

                currentPlan.addLast(i);

                Model.ModelState nextState = null;
                HashSet<Integer> coverageDelta = new HashSet<>();
                long scoreDelta = 0;

                if (strategy == ExploitationStrategy.UiBoundedPath) {
                    for (Model.ModelState child : children) {
                        if (!coveredUI.contains(child.abstractUi.id())) {
                            coverageDelta.add(child.abstractUi.id());
                        }
                    }
                    scoreDelta = (coverageDelta.size() / children.size());

                    if (children.size() == 1) {
                        for (Model.ModelState child: children) {
                            if (!visitedStates.contains(child)) {
                                nextState = child;
                            }
                            else {
                                scoreDelta = -1; // indicate that we don't want to continue;
                            }
                        }
                    }
                }
                else if (strategy == ExploitationStrategy.EdgeBoundedPath) {
                    if (state.mayMethods[i] == null) scoreDelta = -1;
                    else {
                        for (Integer m : state.mayMethods[i]) {
                            if (!coveredMethods.contains(m)) {
                                coverageDelta.add(m);
                            }
                        }
                        scoreDelta = (coverageDelta.size() / children.size());

                        if (children.size() == 1) {
                            for (Model.ModelState child : children) {
                                if (!visitedStates.contains(child) || scoreDelta != 0) {
                                    nextState = child;
                                } else {
                                    coverageDelta.clear();
                                    scoreDelta = -1; // indicate that we don't want to continue;
                                }
                            }
                        }
                    }
                }

                if (scoreDelta != -1) {
                    int nextBudget = (nextState == null) ? 0 : budget - 1;
                    long updatedScore = scoreDelta + currentScore;
                    HashSet updatedCoverage = currentCoverage;
                    if (scoreDelta != 0) {
                        updatedCoverage = new HashSet(currentCoverage);
                        updatedCoverage.addAll(coverageDelta);
                    }

                    if (scoreDelta == 0 || trialCounts[i] < REPLAY_TRIAL_LIMIT) {
                        if (scoreDelta != 0){
                            currentGoals.addLast(state.abstractUi.id());
                            currentGoals.addLast(i);
                        }
                        searchImpl(nextState, nextBudget, currentPlan, currentGoals, updatedCoverage, updatedScore, visitedStates);
                        if (scoreDelta != 0) {
                            currentGoals.removeLast();
                            currentGoals.removeLast();
                        }
                    }
                }
                currentPlan.removeLast();
            }

            visitedStates.remove(state);
            currentPlan.removeLast();
        }

    }

    private void updateReplayTrialStatistics(Model.ModelState target, Integer eventIndex) {
        int uid = target.abstractUi.id();
        if (!exploitationFinished) {
            switch (exploitationStrategy) {
                case UiGreedyBlockGoal:
                    uiTrialCount.put(uid, uiTrialCount.get(uid) + 1);
                    break;
                case EdgeGreedyBlockGoal:
                    try {
                        transitionTrialCount.get(uid)[eventIndex]++;
                    } catch (Exception e) {
                        System.out.println(uid);
                        System.out.println(eventIndex);
                        System.out.println(target.abstractUi.getEventCount());
                        System.out.println(transitionTrialCount.get(uid).length);
                        System.out.println("state id:" + target.id);
                        throw new RuntimeException(e);
                    }
                    break;
                case EdgeGreedyBlockTransition:
                    // Nothing to do
                    break;
            }
        }
        else {
            switch (explorationStrategy) {
                case IgnoreOldTrace:
                    break;
                case StateCover:
                    uiTrialCount.put(uid, uiTrialCount.get(uid) + 1);
                    break;
            }
        }
    }

    HashSet<Model.ModelState> getChildren(Model.ModelState state, int index, int eDegree) {
        HashSet<Model.ModelState> children = state.outTransitions[index];
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

    boolean isTargetState(Model.ModelState state, int eDegree, PlanType planType) {
        int uid = state.abstractUi.id();
        int eventCount = state.abstractUi.getEventCount();

        switch (planType) {
            case Explore:
                return frontiers.contains(state);
            case Replay:
                if (!exploitationFinished) {
                    switch (exploitationStrategy) {
                        case UiGreedyBlockGoal:
                            if (!uiTrialCount.containsKey(uid)) uiTrialCount.put(uid, 0);
                            return !coveredUI.contains(uid) && uiTrialCount.get(uid) < REPLAY_TRIAL_LIMIT;
                        case EdgeGreedyBlockGoal:
                            if (!transitionTrialCount.containsKey(uid))
                                transitionTrialCount.put(uid, new Integer[eventCount]);
                            Integer[] trialCounts = transitionTrialCount.get(uid);

                            for (int i = 0; i < eventCount; i++) {
                                if (state.outTransitions[i] == null) continue;
                                if (trialCounts[i] == null) trialCounts[i] = 0;
                                if (state.mayMethods[i] == null || coveredMethods.containsAll(state.mayMethods[i]))
                                    continue;
                                if (trialCounts[i] < REPLAY_TRIAL_LIMIT) return true;
                            }
                            return false;
                        case EdgeGreedyBlockTransition:
                            for (int i = 0; i < eventCount; i++) {
                                if (state.outTransitions[i] == null) continue;
                                if (state.mayMethods[i] == null || coveredMethods.containsAll(state.mayMethods[i]))
                                    continue;
                                return true;
                            }
                            return false;
                    }
                }
                else {
                    switch (explorationStrategy) {
                        case StateCover:
                            if (state.isRealized) return false;
                            if (!uiTrialCount.containsKey(uid)) uiTrialCount.put(uid, 0);
                            return !coveredUI.contains(uid) && uiTrialCount.get(uid) < REPLAY_TRIAL_LIMIT;
                        case IgnoreOldTrace:
                            throw new RuntimeException("Unreachable!");
                    }
                }
        }
        throw new RuntimeException("Unreachable!");
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

            if (!current.outTransitions[eventIndex].contains(model.getFailState())) {
                blackList.add(eventIndex);
                break;
            }
        }

        executionPlan[0] = baseUI;
        executionPlan[1] = eventIndex;
        executionCursor = 0;
        return true;
    }

    @Override
    public String getName() {
        return "replay" + "-" + exploitationStrategy.name();
    }

    @Override
    public String getDetailedExplanation() {
        switch (exploitationStrategy) {
            case UiGreedyBlockGoal:
            case EdgeGreedyBlockGoal:
            case EdgeGreedyBlockTransition:
                return getName() + " with tl=" + REPLAY_TRIAL_LIMIT;
            case EdgeBoundedPath:
            case UiBoundedPath:
                return getName() + " with tl=" + REPLAY_TRIAL_LIMIT + ", pl=" + PATH_LENGTH_LIMIT;
            default:
                throw new RuntimeException();
        }
    }


    @Override
    public void intermediateDump(int id) {
        ForwardLabeledGraphPrinter<Model.ModelState, MBRTModelTraversalHelper> printer =
                new ForwardLabeledGraphPrinter<>(helper, false);

        PrinterHelper.dumpForwardLabeledGraphToDot(id, "model", "a", printer);

        log("Printing Stats");
        ForwardLabeledGraphStat<Model.ModelState> modelStat =
                ForwardLabeledGraphStat.<Model.ModelState, STMLModelTraversalHelper>compute(helper);

        HistoryManager hm = HistoryManager.instance();
        hm.periodStat("#Screen", AbstractUI.count());
        hm.periodStat("Strategy:getName", getName());
        hm.periodStat("Strategy:Model:#Node", modelStat.countNode());
        hm.periodStat("Strategy:Model:#Edge", modelStat.countEdge());
        hm.periodStat("Strategy:Model:#Tran. (realized)", modelStat.countRealizedTransition());
        hm.periodStat("Strategy:Model:#Tran. (remaining)", modelStat.countUnrealizedTransition());
        hm.periodStat("Strategy:Model:#ND Tran.", modelStat.countNonDeterministicTransition());
        hm.periodStat("Strategy:Model:#ND Edge", modelStat.countNonDeterministicEdge());
        hm.periodStat("Strategy:Stat:#Recon.", reconstructionCount);
        hm.periodStat("Strategy:Stat:#Crash", crashCount);
        hm.periodStat("Strategy:Stat:#Reset", resetCount);
        hm.periodStat("Strategy:Stat:Plan R", RPlanCount);
        hm.periodStat("Strategy:Stat:Plan D", DPlanCount);
        hm.periodStat("Strategy:Stat:Plan ND", NDPlanCount);
        hm.periodStat("Strategy:Stat:ND Failure", NDFailure);
        hm.periodStat("Strategy:Stat:Replay Failure", ReplayFail);
        hm.periodStat("Strategy:Stat:Replay Plan", ReplayCount);
        hm.periodStat("Strategy:Stat:Replay Finish", exploitationFinished ? 1 : 0);
        hm.periodStat("Strategy:Stat:Exact Trace Finish", exactTraceFinished ? 1 : 0);
        hm.periodStat("Strategy:Stat:Covered UI", coveredUI.size());

        int blockedCount = 0;
        for (Integer uid: uiTrialCount.keySet()) {
            if (uiTrialCount.get(uid).equals(REPLAY_TRIAL_LIMIT)) blockedCount++;
        }
        hm.periodStat("Strategy:Stat:Blocked UI count", blockedCount);

        int blockedTransitionsCount = 0;
        for (Integer[] counts: transitionTrialCount.values()) {
            for (int i=0; i<counts.length; i++) {
                if (counts[i] == null) continue;
                if (counts[i].equals(REPLAY_TRIAL_LIMIT)) {
                    blockedTransitionsCount++;
                }
            }
        }
        hm.periodStat("Strategy:Stat:Blocked Transition count", blockedTransitionsCount);

        ForwardLabeledGraphTraversal<Model.ModelState, MBRTModelTraversalHelper> traversal =
                new ForwardLabeledGraphTraversal(new MBRTModelTraversalHelper(this));

        ForwardLabeledGraphTraversal.VisitorChain<Model.ModelState> visitorChain =
                new ForwardLabeledGraphTraversal.VisitorChain<>();

        if (exploitationStrategy == ExploitationStrategy.EdgeGreedyBlockGoal || exploitationStrategy == ExploitationStrategy.EdgeBoundedPath) {
            visitorChain.addVisitor(
                new ForwardLabeledGraphTraversal.Visitor<Model.ModelState>() {
                    @Override
                    public void visit(Model.ModelState src, Model.ModelState dst, String label) {
                        if (src.mayMethods == null) return;
                        if (src.mayMethods[Integer.parseInt(label)] == null) return;

                        int newMethodsCount = 0;
                        for (Integer m : src.mayMethods[Integer.parseInt(label)]) {
                            if (!coveredMethods.contains(m)) newMethodsCount++;
                        }

                        //if (newMethodsCount != 0) {
                        //    log(src.id + " -[" + label + "]-> " + dst.id + "\tcovers " + newMethodsCount);
                        //}
                    }
                });
        }


        visitorChain.addVisitor(
            new ForwardLabeledGraphTraversal.Visitor<Model.ModelState>() {
                private int realizedNodeCount = 0;

                @Override
                public void visitPre(Model.ModelState node) {
                    if(node.isRealized) realizedNodeCount++;
                }

                @Override
                public void endGraph() {
                    HistoryManager.instance().periodStat("Strategy:Stat:RealizedNode", realizedNodeCount);
                }
            });

        traversal.accept(visitorChain);


        if (newPlan) {
            hm.periodStat("Strategy:Stat:NewPlan:", planType.name());
        }
    }

    @Override
    public void finalDump() {
        String output_dir = Options.get(Options.Keys.OUTPUT_DIR);
        String trace_path = output_dir + "/trace.json";
        Util.writeJsonFile(trace_path, tracer.getTrace().toJson());
    }

    @Override
    public boolean requiresAutoRestart() {
        return false;
    }

    public void addOption(StrategyOption opt) {
        options.add(opt);
    }

    private void printExecutionPlan() {
        printIntSet(Arrays.asList(executionPlan), "Execution plan", this);
        log("Execution cursor:" + executionCursor);
    }
}

