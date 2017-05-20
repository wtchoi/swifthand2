package smarthand.ui_explorer.strategy.refinement;

import com.google.common.collect.*;
import org.json.JSONObject;
import smarthand.ui_explorer.*;
import smarthand.ui_explorer.trace.AbstractUI;
import smarthand.ui_explorer.trace.ConcreteUI;
import smarthand.ui_explorer.trace.Coverage;
import smarthand.ui_explorer.util.Util;
import smarthand.ui_explorer.visualize.*;

import java.util.*;
import java.util.function.Predicate;

/**
 * Created by wtchoi on 8/10/16.
 */
public class VRFStrategy extends Strategy {
    private static final int RESET_THRESHOLD = 10;

    private Augmenter augmenter = new Augmenter();
    private Multimap<AbstractUI, ConcreteUI> abstractToConcrete = HashMultimap.create();
    private Multimap<AbstractUI, AugmentedAbstractUI> abstractToAugmented = HashMultimap.create();
    private Multimap<AugmentedAbstractUI, ConcreteUI> augmentedToConcrete = HashMultimap.create();

    private Multimap<AugmentedAbstractUI, TransitionRecord> transitionRecordTbl = HashMultimap.create();
    private Multimap<AugmentedAbstractUI, TransitionRecord> reverseTransitionRecordTbl = HashMultimap.create();
    private Table<AugmentedAbstractUI, Integer, SortedSet<AugmentedAbstractUI>> transitionTbl = HashBasedTable.create();

    private TransitionRecord prevTransition = null;
    private TransitionRecord currTransition = null;
    private Multimap<TransitionRecord, TransitionRecord> transitionPredecessor = HashMultimap.create();

    // The odd elements of the trace are id of ConcreteUIs.
    // The even elements of the trace are "eventIndex*2 + failed".
    private LinkedList<Integer> currentTrace = new LinkedList<>();

    private ConcreteUI prevConcreteUi = null;
    private ConcreteUI currConcreteUi = null;
    private AugmentedAbstractUI prevAugmentedUi = null;
    private AugmentedAbstractUI currAugmentedUi = null;
    private AbstractUI prevAbstractUi = null;
    private AbstractUI currAbstractUi = null;
    JSONObject currUiTree = null;

    private int eventExecuted = 0;

    private ExecutionPlan executionPlan = null;

    private int currentPeriod = 0;
    private StringBuilder logStringBuilder = new StringBuilder();
    private VRFStrategyLogger sLogger = new VRFStrategyLogger();

    private Table<AugmentedAbstractUI, Integer, Integer> ndScoreTbl = HashBasedTable.create();

    private Random rand = new Random(0);
    private VRFModelPrintHelper printHelper = new VRFModelPrintHelper(this);
    private VRFModelTraversalHelper traversalHelper = new VRFModelTraversalHelper(this);

    private void addTransition(AugmentedAbstractUI from, int eventIndex, AugmentedAbstractUI to) {
        if (!transitionTbl.contains(from, eventIndex)) {
            transitionTbl.put(from, eventIndex, new TreeSet<>(AugmentedAbstractUI::compare));
        }
        transitionTbl.get(from, eventIndex).add(to);
    }

    private void removeTransition(AugmentedAbstractUI from, int eventIndex, AugmentedAbstractUI to) {
        if (!transitionTbl.contains(from, eventIndex)) return;

        Set<AugmentedAbstractUI> set = transitionTbl.get(from, eventIndex);
        set.remove(to);
        if (set.isEmpty()) transitionTbl.remove(from, eventIndex);
    }

    private int getNDScore(AugmentedAbstractUI node, int eventExecuted) {
        return ndScoreTbl.contains(node, eventExecuted)
                ? ndScoreTbl.get(node, eventExecuted)
                : 0;
    }

    private void increaseNDScore(AugmentedAbstractUI node, int eventExecuted) {
        ndScoreTbl.put(node, eventExecuted, getNDScore(node, eventExecuted) + 1);
    }

    public void reportExecution(DeviceInfo deviceInfo, Coverage coverage, boolean escaped, boolean blocked) {
        String currentActivity = (deviceInfo.activityStack.size() == 0)
                ? "null"
                : deviceInfo.activityStack.getLast();

        Boolean isKeyboardShown = deviceInfo.isKeyboardShown;

        prevAbstractUi = currAbstractUi;
        prevConcreteUi = currConcreteUi;
        prevAugmentedUi = currAugmentedUi;

        currUiTree = deviceInfo.appGuiTree;
        if (!blocked && !escaped && deviceInfo.appGuiTree.has("class")) {
            // usual case
            currAbstractUi = AbstractUI.getState(currentActivity, isKeyboardShown, deviceInfo.filteredEvents, deviceInfo.eventInfo);
            currConcreteUi = ConcreteUI.getFromRawInfo(deviceInfo.appGuiTree, currAbstractUi);
            currAugmentedUi = AugmentedAbstractUI.get(currConcreteUi, currAbstractUi, augmenter);
        }
        else {
            // ui information if not available
            currAbstractUi = AbstractUI.getFailState();
            currConcreteUi = ConcreteUI.getBlockState();
            currAugmentedUi = AugmentedAbstractUI.getBlockState();
        }

        if (HistoryManager.instance().getCurrentPeriod() != currAbstractUi.getSnapshotID()) {
            currConcreteUi.takeSnapshot();
        }
        else {
            currConcreteUi.snapshotID = currAbstractUi.getSnapshotID();
        }

        // update mapping
        abstractToAugmented.put(currAbstractUi, currAugmentedUi);
        abstractToConcrete.put(currAbstractUi, currConcreteUi);
        augmentedToConcrete.put(currAugmentedUi, currConcreteUi);

        // update trace
        if (prevConcreteUi == null) {
            currentTrace.addLast(currConcreteUi.id());
            sLogger.onStart();
        }
        else {
            int eventEncoding = executionPlan.getEventIndexToExecute()*2;
            if (executionPlan.isResetPlan()) currentTrace.clear();
            else currentTrace.addLast(eventEncoding);
            currentTrace.addLast(currConcreteUi.id());
            sLogger.onEvent(eventExecuted);
        }
        Util.dumpStringToFile(Options.get(Options.Keys.IMAGE_OUTPUT_DIR), "ui" + HistoryManager.instance().getCurrentPeriod(), currUiTree.toString());
        sLogger.onReport(currConcreteUi, currAugmentedUi, currAbstractUi, false);

        // update model
        boolean refined = false;
        if (prevConcreteUi != null) {
            log("UPDATE!");
            prevTransition = currTransition;
            currTransition = TransitionRecord.get(prevConcreteUi, currConcreteUi, prevAbstractUi, currAbstractUi, eventExecuted);
            transitionRecordTbl.put(prevAugmentedUi, currTransition);
            reverseTransitionRecordTbl.put(currAugmentedUi, currTransition);

            // sanity check
            assertSanity();
            if (executionPlan.hasNextEvent()) {
                AugmentedAbstractUI expectedAugmentedUi = executionPlan.getExpectedAugmentedUI();
                sLogger.onExpect(expectedAugmentedUi);
                if (!expectedAugmentedUi.equals(currAugmentedUi)) {
                    log("REFINEMENT!");
                    refined = refineAugmentation(prevAugmentedUi, eventExecuted, currAugmentedUi, expectedAugmentedUi);
                    if (!refined) log("REFINEMENT failed");
                    else {
                        prevAugmentedUi = AugmentedAbstractUI.get(prevConcreteUi, prevAbstractUi, augmenter);
                        currAugmentedUi = AugmentedAbstractUI.get(currConcreteUi, currAbstractUi, augmenter);

                        Multimap<AugmentedAbstractUI, ConcreteUI> mapping = HashMultimap.create();
                        LinkedList<String> predicateDescs = new LinkedList();
                        abstractToAugmented.get(prevAbstractUi).forEach(x -> mapping.putAll(x, augmentedToConcrete.get(x)));
                        augmenter.getPredicates(prevAbstractUi).forEach(p -> predicateDescs.addLast(PredicateFactory.getDesc(p)));
                        sLogger.onRefinement(prevAbstractUi, mapping, predicateDescs);
                    }
                }
            }
            addTransition(prevAugmentedUi, eventExecuted, currAugmentedUi);

            // update transitionPredecessor
            if (prevTransition != null) {
                transitionPredecessor.put(currTransition, prevTransition);
            }
        }

        // update execution plan
        {
            if (executionPlan != null) {
                log("PROGRESS!!");
                executionPlan.checkAndProgress(currAugmentedUi);
            }

            if (refined || executionPlan == null || executionPlan.isDeviated() || executionPlan.isFinished()) {
                ExecutionPlan prev = executionPlan;

                log("BUILD PLAN!");
                if (constructNormalPlan(false)) {
                    log("Deterministic plan");
                }
                else if (constructNormalPlan(true)) {
                    log("Non-deterministic plan");
                    if (prev != null && !refined && prev.isNDPlan()) {
                        increaseNDScore(prevAugmentedUi, eventExecuted);
                    }
                }
                else {
                    if (currentTrace.size() > RESET_THRESHOLD) {
                        constructResetPlan();
                        log("Reset plan");
                    }
                    else {
                        constructRandomPlan();
                        log("Random plan");
                    }
                }
                log(executionPlan.toString());
            }
        }
    }

    private void constructResetPlan() {
        executionPlan = ExecutionPlan.getResetPlan(currAugmentedUi);
    }
    private void constructRandomPlan() { executionPlan = ExecutionPlan.getRandomPlan(currAugmentedUi, rand); }

    // nd threshold
    private static final double ND_SEARCH_THRESHOLD = 0.5;
    private static final int ND_START_DEGREE = 1;
    private static int ND_FAILURE_THRESHOLD = 4;

    // this plan do not visit the same state twice
    private LinkedList<Integer> constructNormalPlanImpl(AugmentedAbstractUI startNode, int degreeND, Set<AugmentedAbstractUI> visitedNodes) {
        Map<AugmentedAbstractUI, Integer> incomingEdges = new HashMap<>();
        Map<AugmentedAbstractUI, AugmentedAbstractUI> predecessor = new HashMap<>();
        LinkedList<AugmentedAbstractUI> nodeToVisit = new LinkedList<>();
        visitedNodes = new HashSet<>(visitedNodes);

        nodeToVisit.add(startNode);
        AugmentedAbstractUI target = null;
        int targetEdge = 0;

        while (!nodeToVisit.isEmpty() && target == null) {
            AugmentedAbstractUI node = nodeToVisit.removeFirst();
            if (visitedNodes.contains(node)) continue;
            visitedNodes.add(node);

            int[] perm = Util.permutation(node.abstractUi.getEventCount(), rand);
            for (int i = 0; i < node.abstractUi.getEventCount(); i++) {
                int index = perm[i];
                if (!transitionTbl.contains(node, index)) {
                    target = node;
                    targetEdge = index;
                    break;
                } else {
                    SortedSet<AugmentedAbstractUI> children = transitionTbl.get(node, index);
                    if (children.size() == 1) {
                        AugmentedAbstractUI child = children.first();
                        if (visitedNodes.contains(child)) continue;
                        nodeToVisit.addLast(child);
                        incomingEdges.put(child, index);
                        predecessor.put(child, node);
                    }
                    else if (degreeND != 0) {
                        if (getNDScore(node, index) > ND_FAILURE_THRESHOLD) {
                            log("Transition (" + node.id() + ", " + index + ") is blocked");
                            continue; //ignore black listed ND-transition
                        } else {
                            int successCount = 0;
                            for (AugmentedAbstractUI child : children) {
                                LinkedList<Integer> plan = constructNormalPlanImpl(child, degreeND - 1, visitedNodes);
                                if (plan != null) successCount++;
                            }
                            if ((double) successCount >= children.size() * ND_SEARCH_THRESHOLD) {
                                target = node;
                                targetEdge = index;
                                break;
                            }
                        }
                    }
                    else continue; // ignore non-deterministic choice
                }
            }
        }

        if (target == null) return null;

        LinkedList<Integer> planEncoding = new LinkedList<>();

        planEncoding.addFirst(targetEdge);
        planEncoding.addFirst(target.id());
        while (incomingEdges.containsKey(target)) {
            targetEdge = incomingEdges.get(target);
            target = predecessor.get(target);
            planEncoding.addFirst(targetEdge);
            planEncoding.addFirst(target.id());
        }
        return planEncoding;
    }

    private boolean constructNormalPlan(boolean nd) {
        int degree = nd ? ND_START_DEGREE : 0;
        LinkedList<Integer> planEncoding = constructNormalPlanImpl(currAugmentedUi, degree, new HashSet<>());

        if (planEncoding == null) return false;
        executionPlan = ExecutionPlan.getExecutionPlan(planEncoding);
        if (nd) executionPlan.setNDFlag();
        return true;
    }

    private boolean refineAugmentation(AugmentedAbstractUI augmentedFrom, int eventExecuted, AugmentedAbstractUI augmentedTo1, AugmentedAbstractUI augmentedTo2) {
        AbstractUI abstractFrom = augmentedFrom.abstractUi;
        Set<TransitionRecord> transitionRecords = new HashSet<>();
        transitionRecords.addAll(transitionRecordTbl.get(augmentedFrom));
        Set<ConcreteUI> category1 = new HashSet<>();
        Set<ConcreteUI> category2 = new HashSet<>();

        for (TransitionRecord tr : transitionRecords) {
            if (tr.eventIndex == eventExecuted) {
                AugmentedAbstractUI augTo = AugmentedAbstractUI.get(tr.to, tr.abstractTo, augmenter);
                AugmentedAbstractUI augFrom = AugmentedAbstractUI.get(tr.from, tr.abstractFrom, augmenter);
                if (augTo.equals(augmentedTo1)) category1.add(tr.from);
                else if (augTo.equals(augmentedTo2)) category2.add(tr.from);
                else {
                    log("event: " + eventExecuted);
                    log("event: " + tr.eventIndex);
                    log("AugFrom in TR:" + augFrom.toString());
                    log("Aug1 : " + augmentedTo1.toString());
                    log("Aug2 : " + augmentedTo2.toString());
                    log("Aug  : " + augTo.toString());
                    log("Aug1 == Aug2: " + augmentedTo1.equals(augmentedTo2));
                    log("Aug1 == Aug : " + augmentedTo1.equals(augTo));
                    log("Aug2 == Aug : " + augTo.equals(augmentedTo2));
                    log(transitionTbl.row(augmentedFrom).toString());
                    throw new RuntimeException("Something is wrong");
                }
            }
        }

        // sanity check
        if (category1.size() == 0 || category2.size() == 0) {
            throw new RuntimeException("Something is wrong!");
        }

        // getFromRawInfo all relevant transitions to refine
        abstractToAugmented.get(abstractFrom).forEach(aug -> {
            log("Aug to refine: " + aug.toString());
            transitionRecords.addAll(transitionRecordTbl.get(aug));
            transitionRecords.addAll(reverseTransitionRecordTbl.get(aug));});

        // remove entries to be refined.
        for (TransitionRecord tr : transitionRecords) {
            AugmentedAbstractUI augFrom = AugmentedAbstractUI.get(tr.from, tr.abstractFrom, augmenter);
            AugmentedAbstractUI augTo = AugmentedAbstractUI.get(tr.to, tr.abstractTo, augmenter);
            transitionRecordTbl.remove(augFrom, tr);
            reverseTransitionRecordTbl.remove(augTo, tr);
            removeTransition(augFrom, tr.eventIndex, augTo);
        }
        // sanity check
        assertSanity();

        // refine augmentation
        boolean success = refine(category1, category2, abstractFrom, eventExecuted);

        //sanity check
        assertSanity();

        // refine table entries
        for (TransitionRecord tr : transitionRecords) {
            AugmentedAbstractUI augFrom = AugmentedAbstractUI.get(tr.from, tr.abstractFrom, augmenter);
            AugmentedAbstractUI augTo = AugmentedAbstractUI.get(tr.to, tr.abstractTo, augmenter);

            transitionRecordTbl.put(augFrom, tr);
            reverseTransitionRecordTbl.put(augTo, tr);
            addTransition(augFrom, tr.eventIndex, augTo);

            try {
                assertSanity();
            }
            catch (RuntimeException e) {
                log("Something is wrong");
                log("AugFrom : " + augmentedFrom.toString());
                log("AugTo1  : " + augmentedTo1.toString());
                log("AugTo2  : " + augmentedTo2.toString());
                log("Event   : " + eventExecuted);
                log("TR From : " + augFrom.toString());
                log("TR To   : " + augTo.toString());
                log("TR Event: " + tr.eventIndex);
                throw e;
            }
        }

        // refine mappings
        if (success) {
            abstractToAugmented.get(abstractFrom).forEach(aug -> augmentedToConcrete.removeAll(aug));
            abstractToAugmented.removeAll(abstractFrom);
            abstractToConcrete.get(abstractFrom).forEach(con -> {
                AugmentedAbstractUI aug = AugmentedAbstractUI.get(con, abstractFrom, augmenter);
                abstractToAugmented.put(abstractFrom, aug);
                augmentedToConcrete.put(aug, con);
            });
        }

        // sanity check
        assertSanity();
        return success;
    }

    private void assertSanity() {
        for (AugmentedAbstractUI from: transitionRecordTbl.keySet()) {
            for(TransitionRecord tr: transitionRecordTbl.get(from)) {
                AugmentedAbstractUI augFrom = AugmentedAbstractUI.get(tr.from, tr.abstractFrom, augmenter);
                if (!augFrom.equals(from)) {
                    log("assert sanity. From Key: " + from.toString());
                    log("assert sanity. TR From : " + augFrom.toString());
                    throw new RuntimeException("Something is wrong!");
                }
            }
        }

        for (AugmentedAbstractUI to: reverseTransitionRecordTbl.keySet()) {
            for(TransitionRecord tr: reverseTransitionRecordTbl.get(to)) {
                AugmentedAbstractUI augTo = AugmentedAbstractUI.get(tr.to, tr.abstractTo, augmenter);
                if (!augTo.equals(to)) {
                    log("assert sanity. To Key: " + to.toString());
                    log("assert sanity. TR To : " + augTo.toString());
                    throw new RuntimeException("Something is wrong!");
                }
            }
        }
    }

    private boolean refine(Set<ConcreteUI> category1, Set<ConcreteUI> category2, AbstractUI abstractUI, int eventExecuted) {
        boolean refined = false;
        LinkedList<Predicate<ConcreteUI>> blacklist = new LinkedList<>();

        ConcreteUI cui1 = Util.getRandomElement(category1, rand);
        ConcreteUI cui2 = Util.getRandomElement(category2, rand);

        String refinementDesc = null;
        while (!refined) {
            Predicate<ConcreteUI> tester = findSeparator(cui1, cui2, abstractUI, eventExecuted, blacklist);
            if (tester != null) {
                boolean c1Consensus = checkConsensus(tester, category1);
                boolean c2Consensus = checkConsensus(tester, category2);
                if (c1Consensus && c2Consensus) {
                    augmenter.addTester(abstractUI, tester);
                    refinementDesc = PredicateFactory.getDesc(tester);
                    refined = true;
                } else {
                    blacklist.add(tester);
                    continue;
                }
            } else {
                //cannot distinguish cui1 and cui2
                dumpComparison(cui1, cui2, abstractUI, eventExecuted, refinementDesc);
                return false;
            }
        }
        log("REFINEMENT: " +  refinementDesc);
        dumpComparison(cui1, cui2, abstractUI, eventExecuted, refinementDesc);
        return true;
    }

    private void dumpComparison(ConcreteUI c1, ConcreteUI c2, AbstractUI abstractUi, int eventExecuted, String refinementDesc) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head></head><body>");
        sb.append("<table><tr><td>");
        sb.append("<img src=\"screen" + c1.snapshotID + ".png\" alt=\"C1\" style=\"width:200px;height:300px;\">");
        sb.append("</td><td>");
        sb.append("<img src=\"screen" + c2.snapshotID + ".png\" alt=\"C2\" style=\"width:200px;height:300px;\">");
        sb.append("</td></tr>");
        sb.append("<tr><td>");
        sb.append("<p>");
        sb.append("C1:</br>");
        sb.append(c1.toString().replace("\n","</br>"));
        sb.append("</p></td>");
        sb.append("<td><p>");
        sb.append("C2:</br>");
        sb.append(c2.toString().replace("\n","</br>"));
        sb.append("</p></td></tr>");
        sb.append("<tr><td>");
        int i = 0;
        for (String event: abstractUi.getEvents()) {
            sb.append((i++) + "." + event + "</br>");
        }
        sb.append("</td></tr></table>");
        sb.append("<p>event: " + eventExecuted + "</p>");
        sb.append("<p>refinement: " + refinementDesc);
        sb.append("</body></html>");

        String image_dir = Options.get(Options.Keys.IMAGE_OUTPUT_DIR);
        int periodCount = HistoryManager.instance().getCurrentPeriod();
        String filename = "compare" + periodCount + ".html";
        String contents = sb.toString();
        Util.dumpStringToFile(image_dir, filename, contents);
    }

    private Predicate<ConcreteUI> findSeparator(ConcreteUI c1, ConcreteUI c2, AbstractUI abstractUI, int eventExecuted, LinkedList<Predicate<ConcreteUI>> blacklist) {
        LinkedList<Integer> accessPath;

        // special treatment for back and menu button, since they don't have corresponding UI component
        if (eventExecuted == 0 || eventExecuted == 1) {
            accessPath = new LinkedList<>();
        }
        else {
            EventInfo eventInfo = abstractUI.getEventsInfo().get(eventExecuted);
            accessPath = new LinkedList<>(eventInfo.accessPath);
            accessPath.removeFirst();
        }

        ConcreteUI ec1 = c1.getSubComponent(accessPath);
        ConcreteUI ec2 = c2.getSubComponent(accessPath);

        SortedSet<LinkedList<Integer>> postfixes = new TreeSet<>(Util::compareIntegerList);
        postfixes.addAll(generatePostfixes(ec1));
        postfixes.addAll(generatePostfixes(ec2));

        HashSet<LinkedList<Integer>> paths = new HashSet<>();

        Predicate<ConcreteUI> tester = null;

        for (LinkedList<Integer> postfix : postfixes) {
            LinkedList<Integer> path = new LinkedList<>(accessPath);
            path.addAll(postfix);

            boolean existsSub1 = ec1.checkExistence(postfix);
            boolean existsSub2 = ec2.checkExistence(postfix);
            if (existsSub1 != existsSub2) {
                tester = filterBlacklist(PredicateFactory.getExistenceTester(path), blacklist);
                if (tester != null) return tester;
            }

            if (existsSub1 && existsSub2) {
                paths.add(path);
            }
        }

        for (LinkedList<Integer> path:paths) {
            ConcreteUI sub1 = c1.getSubComponent(path);
            ConcreteUI sub2 = c2.getSubComponent(path);

            if (!sub1.type.equals(sub2.type)) {
                tester = filterBlacklist(PredicateFactory.getTypeTester(path, sub1.type), blacklist);
                if (tester != null) return tester;
            }
        }

        for (LinkedList<Integer> path: paths) {
            ConcreteUI sub1 = c1.getSubComponent(path);
            ConcreteUI sub2 = c2.getSubComponent(path);

            if (sub1.checked != sub2.checked) {
                tester = filterBlacklist(PredicateFactory.getBooleanAttrTester(path, PredicateFactory.Attr.Checked), blacklist);
                if (tester != null) return tester;
            }

            if (sub1.actionable != sub2.actionable) {
                tester = filterBlacklist(PredicateFactory.getBooleanAttrTester(path, PredicateFactory.Attr.Actionable), blacklist);
                if (tester != null) return tester;
            }

            if (sub1.text != null && sub2.text != null && !sub1.text.equals(sub2.text)) {
                tester = filterBlacklist(PredicateFactory.getTextTester(path, sub1.text), blacklist);
                if (tester != null) return tester;
            }
        }

        for (LinkedList<Integer> path: paths) {
            ConcreteUI sub1 = c1.getSubComponent(path);
            ConcreteUI sub2 = c2.getSubComponent(path);

            if (!sub1.bound.equals(sub2.bound)) {
                tester = filterBlacklist(PredicateFactory.getBoundTester(path, sub1.bound), blacklist);
                if (tester != null) return tester;
            }
        }
        return null;
    }

    private Predicate<ConcreteUI> filterBlacklist(Predicate<ConcreteUI> p, LinkedList<Predicate<ConcreteUI>> blacklist) {
        if (blacklist.contains(p)) return null;
        return p;
    }

    private Set<LinkedList<Integer>> generatePostfixes(ConcreteUI concreteUI) {
        Set<LinkedList<Integer>> result = new HashSet<>();
        result.add(new LinkedList<>());
        for (int i = 0; i < concreteUI.children.length; i++) {
            Set<LinkedList<Integer>> childResult = generatePostfixes(concreteUI.children[i]);
            for (LinkedList<Integer> childPostfix : childResult) childPostfix.addFirst(i);
            result.addAll(childResult);
        }
        return result;
    }

    private boolean checkConsensus(Predicate<ConcreteUI> tester, Set<ConcreteUI> concreteUIs) {
        boolean flag = false;
        boolean init = false;
        try {
            for (ConcreteUI c : concreteUIs) {
                if (!init) {
                    init = true;
                    flag = tester.test(c);
                } else if (flag != tester.test(c)) {
                    return false;
                }
            }
        }
        catch(NullPointerException e) {
            return false;
        }
        return true;
    }

    @Override
    public String getNextAction() {
        eventExecuted = executionPlan.getEventIndexToExecute();
        return executionPlan.getActionToExecute();
    }

    @Override
    public boolean requiresAutoRestart() {
        return false;
    }

    @Override
    public void intermediateDump(int id) {
        ForwardLabeledGraphPrinter<AugmentedAbstractUI, VRFModelPrintHelper> printer =
            new ForwardLabeledGraphPrinter<>(printHelper, false);

        ForwardLabeledGraphStat<AugmentedAbstractUI> modelStat =
                ForwardLabeledGraphStat.compute(traversalHelper);

        HistoryManager hm = HistoryManager.instance().instance();
        hm.periodStat("#Screen", AbstractUI.count());
        hm.periodStat("Strategy:getName", getName());
        hm.periodStat("Strategy:Model:#Node", modelStat.countNode());
        hm.periodStat("Strategy:Model:#Edge", modelStat.countEdge());
        hm.periodStat("Strategy:Model:#Tran. (realized)", modelStat.countRealizedTransition());
        hm.periodStat("Strategy:Model:#Tran. (remaining)", modelStat.countUnrealizedTransition());
        hm.periodStat("Strategy:Model:#ND Tran.", modelStat.countNonDeterministicTransition());
        hm.periodStat("Strategy:Model:#ND Edge", modelStat.countNonDeterministicEdge());
        hm.periodStat("Strategy:Execution", sLogger.getLog());
        sLogger.clear();

        PrinterHelper.dumpForwardLabeledGraphToDot(id, "model", "a", printer);
        log(augmenter.toString());
    }

    @Override
    public void finalDump() {
        //TODO
    }

    @Override
    public String getName() {
        return "VRFStrategy"; //TODO
    }

    @Override
    public String getDetailedExplanation() {
        return "VRFStrategy"; //TODO
    }

    @Override
    public void setRandomSeed(int randomSeed) { }

    @Override
    public void log(String message) {
        super.log(message);

        if (HistoryManager.instance().getCurrentPeriod() != currentPeriod) {
            currentPeriod = HistoryManager.instance().getCurrentPeriod();
            logStringBuilder = new StringBuilder();
        }
        else {
            logStringBuilder.append("\n");
        }
        logStringBuilder.append(message);
    }

    private class VRFModelTraversalHelper implements ForwardLabeledGraphTrait<AugmentedAbstractUI> {
        protected VRFStrategy model;

        VRFModelTraversalHelper(VRFStrategy model) { this.model = model; }

        public Iterable<AugmentedAbstractUI> getRoots() {
            return model.transitionTbl.rowKeySet();
        }

        public String getNodeName(AugmentedAbstractUI node) {
            String result = String.valueOf(node.id());
            return result;
        }

        public Iterable<AugmentedAbstractUI> getSuccessors(AugmentedAbstractUI node) {
            Collection<? extends Set<AugmentedAbstractUI>> childrenSets = transitionTbl.row(node).values();
            Set<AugmentedAbstractUI> successors = new HashSet<>();
            childrenSets.forEach(successors::addAll);
            return successors;
        }

        public Iterable<Transition<AugmentedAbstractUI>> getTransitions(AugmentedAbstractUI node) {
            LinkedList<Transition<AugmentedAbstractUI>> transitions = new LinkedList<>();
            for (Map.Entry<Integer, ? extends Set<AugmentedAbstractUI>> entry: transitionTbl.row(node).entrySet()) {
                for (AugmentedAbstractUI child: entry.getValue()) {
                    transitions.add(new Transition<>(child, entry.getKey().toString()));
                }
            }
            return transitions;
        }

        @Override
        public int countTransition(AugmentedAbstractUI node) {
            return transitionTbl.row(node).size();
        }

        @Override
        public int countOutgoingLabels(AugmentedAbstractUI node) {
            return node.abstractUi.getEventCount();
        }

        @Override
        public int getTransitionGroupID(AugmentedAbstractUI from, AugmentedAbstractUI to, String label) {
            return 0;
        }

        @Override
        public int getTransitionGroupCount() {
            return 1;
        }
    }

    private class VRFModelPrintHelper extends VRFModelTraversalHelper implements ForwardLabeledGraphPrintTrait<AugmentedAbstractUI> {
        VRFModelPrintHelper(VRFStrategy model) {
            super(model);
        }

        @Override
        public String getNodeName(AugmentedAbstractUI node) {
            String result = String.valueOf(node.id());
            if (node.aug != null && node.aug.length > 0) {
                result +=  ": " + node.abstractUi.id() + node.augToString();
            }
            return result;
        }

        @Override
        public String getNodeDetail(AugmentedAbstractUI node) {
            String part1 = node.abstractUi.getTooltip();
            return part1 + "\n" + node.augToString();
        }

        @Override
        public boolean isColoredNode(AugmentedAbstractUI node) {
            return (node.equals(currAugmentedUi) || node.equals(prevAugmentedUi));
        }

        @Override
        public boolean isBoldNode(AugmentedAbstractUI node) {
            return (node.equals(currAugmentedUi) || node.equals(prevAugmentedUi));
        }

        @Override
        public String getNodeColor(AugmentedAbstractUI node) {
            if (node.equals(currAugmentedUi)) return "red";
            if (node.equals(prevAugmentedUi)) return "blue";
            return "black";
        }

        @Override
        public boolean isColoredTransitionGroup(AugmentedAbstractUI from, AugmentedAbstractUI to, int groupID) {
            return false;
        }

        @Override
        public boolean isBoldTransitionGroup(AugmentedAbstractUI from, AugmentedAbstractUI to, int groupID) {
            return false;
        }

        @Override
        public boolean isDottedTransitionGroup(AugmentedAbstractUI from, AugmentedAbstractUI to, int groupID) {
            return false;
        }

        @Override
        public String getTransitionGroupColor(AugmentedAbstractUI from, AugmentedAbstractUI to, int groupID) {
            return null;
        }

        @Override
        public boolean isImportantTransition(AugmentedAbstractUI from, AugmentedAbstractUI to, String label) {
            if (currAugmentedUi != null && prevAugmentedUi != null) {
                if (currAugmentedUi.equals(to) && prevAugmentedUi.equals(from)) {
                    if (String.valueOf(eventExecuted).equals(label)) return true;
                }
            }
            return false;
        }

        @Override
        public boolean isColoredTransition(AugmentedAbstractUI from, AugmentedAbstractUI to, String label) {
            if (currAugmentedUi != null && prevAugmentedUi != null) {
                if (currAugmentedUi.equals(to) && prevAugmentedUi.equals(from)) {
                    if (String.valueOf(eventExecuted).equals(label)) return true;
                }
            }
            return false;
        }

        @Override
        public boolean isBoldTransition(AugmentedAbstractUI from, AugmentedAbstractUI to, String label){
            if (currAugmentedUi != null && prevAugmentedUi != null) {
                if (currAugmentedUi.equals(to) && prevAugmentedUi.equals(from)) {
                    if (String.valueOf(eventExecuted).equals(label)) return true;
                }
            }
            return false;
        }

        @Override
        public boolean isDottedTransition(AugmentedAbstractUI from, AugmentedAbstractUI to, String label){
            return false;
        }

        @Override
        public boolean hasTransitionTooltip(AugmentedAbstractUI from, AugmentedAbstractUI to, String label) {
            return false;
            //TODO
        }

        @Override
        public String getTransitionTooltip(AugmentedAbstractUI from, AugmentedAbstractUI to, String label) {
            return null;
            //TODO
        }

        @Override
        public String getTransitionColor(AugmentedAbstractUI from, AugmentedAbstractUI to, String label) {
            if (currAugmentedUi != null && prevAugmentedUi != null) {
                if (currAugmentedUi.equals(to) && prevAugmentedUi.equals(from)) return "red";
            }
            return "black";
        }

        @Override
        public boolean hasImage(AugmentedAbstractUI node) {
            if (node == AugmentedAbstractUI.getBlockState()) return false;
            return true;
        }

        @Override
        public String getImageURI(AugmentedAbstractUI node) {
            return "./screen" + node.abstractUi.getSnapshotID() + ".png";
        }

        @Override
        public boolean hasURL(AugmentedAbstractUI node) {
            return false;
        }

        @Override
        public String getURL(AugmentedAbstractUI node) {
            return null;
        }

        @Override
        public String compactLabels(Set<String> labels) {
            SortedSet<Integer> sortedLabels = new TreeSet<>();
            for (String label : labels) {
                sortedLabels.add(Integer.valueOf(label));
            }
            return PrinterHelper.buildIntervalString(sortedLabels);
        }

        @Override
        public boolean isGrouped() {
            return false;
        }

        @Override
        public String getGroupID(AugmentedAbstractUI node) {
            return null;
        }

        @Override
        public boolean skipNode(AugmentedAbstractUI node){
            return false;
        }
    }
}

