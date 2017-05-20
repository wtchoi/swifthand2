package smarthand.ui_explorer.strategy.regression;

import smarthand.ui_explorer.strategy.C;
import smarthand.ui_explorer.trace.Action;
import smarthand.ui_explorer.trace.Coverage;
import smarthand.ui_explorer.trace.Trace;

import java.util.HashMap;
import java.util.LinkedList;

/**
 * Created by wtchoi on 4/6/17.
 */
class RecompositionGraph {
    public static class Transition {
        int traceId;
        int transitionIndexInTrace;
        Trace.Snapshot src;
        Trace.Snapshot dst;

        private Transition(int i, int j, Trace.Snapshot s, Trace.Snapshot d) {
            traceId = i;
            transitionIndexInTrace = j;
            src = s;
            dst = d;
        }

        public Action getAction() {
            return dst.prevAction;
        }

        private Coverage coverage;
        public Coverage getCoverage() {
            if (coverage == null) {
                coverage = new Coverage(dst.methodCoverage, dst.branchCoverage, dst.abstractUI.id());
            }
            return coverage;
        }

        static boolean checkContinuity(Transition t1, Transition t2) {
            if (t1.traceId == t2.traceId) {
                if (t1.transitionIndexInTrace + 1 == t2.transitionIndexInTrace) {
                    return true;
                }
            }
            return false;
        }
    }

    HashMap<Integer, LinkedList<Transition>> transitionInfo = new HashMap<>();

    public RecompositionGraph() {}

    public void addTransition(int traceId, int indexInTrace, Trace.Snapshot src, Trace.Snapshot dst) {
        if (!transitionInfo.containsKey(src.abstractUI.id())) {
            transitionInfo.put(src.abstractUI.id(), new LinkedList<>());
        }

        Transition tr = new Transition(traceId, indexInTrace, src, dst);
        transitionInfo.get(src.abstractUI.id()).addLast(tr);
    }

    public void init(LinkedList<Trace> inputTraceSet) {
        for (int i = 0; i< inputTraceSet.size(); i++) {
            Trace inputTrace = inputTraceSet.get(i);
            for(int j=1; j<inputTrace.size(); j++) {
                Trace.Snapshot src = inputTrace.get(j-1);
                Trace.Snapshot dst = inputTrace.get(j);
                this.addTransition(i, j, src, dst);
            }
        }
    }

    public static LinkedList<Integer> pathToPlan(LinkedList<Transition> path) {
        LinkedList<Integer> plan = new LinkedList<Integer>();
        for(Transition tr: path) {
            plan.addLast(tr.src.abstractUI.id());

            if (tr.dst.prevAction.isClose()) {
                plan.addLast(C.CLOSE);
            }
            else if (tr.dst.prevAction.isStart() || tr.dst.prevAction.isReset()) {
                plan.addLast(C.START);
            }
            else if (tr.dst.prevAction.isEvent()) {
                plan.addLast(tr.dst.prevAction.actionIndex);
            }
        }

        plan.addLast(path.getLast().dst.abstractUI.id());
        return PlanUtil.finalizePlan(plan);
    }

    public static Coverage pathCoverage(LinkedList<Transition> path) {
        Coverage c = new Coverage();
        for (Transition tr: path) {
            if (tr.coverage == null) {
                continue;
            }
            c.add(tr.coverage);
        }
        return c;
    }
}
