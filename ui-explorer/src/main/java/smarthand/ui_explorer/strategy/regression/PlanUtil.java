package smarthand.ui_explorer.strategy.regression;

import smarthand.ui_explorer.strategy.C;
import smarthand.ui_explorer.trace.Action;
import smarthand.ui_explorer.trace.Trace;

import java.util.Comparator;
import java.util.LinkedList;

/**
 * Created by wtchoi on 3/16/17.
 */
public class PlanUtil {
    public static LinkedList<Integer> finalizePlan(LinkedList<Integer> planPrefix) {
        LinkedList<Integer> result = new LinkedList<>(planPrefix);
        if (!result.get(result.size() - 2).equals(C.CLOSE)) {
            result.addLast(C.CLOSE);
            result.addLast(C.WILDCARD);
        }
        return result;
    }

    public static LinkedList<Integer> traceToPlan(Trace sequence) {
        // build a plan based on the identified prefix of the sequence
        LinkedList<Integer> plan = new LinkedList<>();
        Integer lastAction = null;

        for (int cursor = 0; cursor < sequence.size(); cursor++) {
            Trace.Snapshot s = sequence.get(cursor);

            // handling close with special care
            lastAction = s.prevAction.kind == Action.Kind.Event ? s.prevAction.actionIndex : C.CLOSE;
            if (cursor != 0) plan.addLast(lastAction);
            plan.addLast(s.abstractUI.id());
        }

        if (!lastAction.equals(C.CLOSE)) {
            plan.addLast(C.CLOSE); //always restart after execution the sequence.
            plan.addLast(C.WILDCARD); //everything is possible
        }
        return plan;
    }

    public static LinkedList<Integer> getFailingPrefix(Trace resultingTrace, LinkedList<Integer> plan) {
        if (resultingTrace.get(0).prevAction.isClose()) {
            resultingTrace = resultingTrace.getSubtrace(1, resultingTrace.size() - 1);
        }

        LinkedList<Integer> failingPrefix = new LinkedList<>();

        failingPrefix.addLast(plan.get(0));
        // Note: a trace only containing an initial state has size = 1

        for(int i=0; i<resultingTrace.size()-1; i++) {
            if (resultingTrace.get(i).abstractUI.id() != plan.get(i*2)) {
                break;
            }
            failingPrefix.addLast(plan.get(i*2+1));
            failingPrefix.addLast(plan.get(i*2+2));
        }
        return failingPrefix;
    }

    public static LinkedList<Integer> getCommonPrefix(LinkedList<Integer> a, LinkedList<Integer> b) {
        int size = Math.min(a.size(), b.size());

        LinkedList<Integer> result = new LinkedList<>();
        for (int i=0; i<size; i++) {
            if (a.get(i).equals(b.get(i))) {
                result.addLast(a.get(i));
            }
            else {
                break;
            }
        }

        if (result.size() % 2 == 0) {
            result.removeLast();
        }

        return result;
    }

    public static boolean equalsExceptLast(LinkedList<Integer> p1, LinkedList<Integer> p2) {
        if (p1.size() != p2.size()) return false;

        for (int i=0; i<p1.size()-1; i++) {
            int x1 = p1.get(i);
            int x2 = p2.get(i);

            if (x1 == x2) continue;
            else return false;
        }
        return true;
    }

    public static boolean planEquals(LinkedList<Integer> a, LinkedList<Integer> b) {
        if (a.size() != b.size()) return false;
        for (int i=0; i<a.size(); i++) {
            if (!a.get(i).equals(b.get(i))) return false;
        }
        return true;
    }

    //returns whether B is a prefix of A
    public static boolean prefixCheck(LinkedList<Integer> plan, LinkedList<Integer> prefix) {
        for (int i = 0; i < prefix.size(); i++) {
            if (!plan.get(i).equals(prefix.get(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean prefixCheck(int[] plan, LinkedList<Integer> prefix) {
        for (int i = 0; i < prefix.size(); i++) {
            if (!prefix.get(i).equals(plan[i])) {
                return false;
            }
        }
        return true;
    }

    public static final Comparator<LinkedList<Integer>> planListComparator = new Comparator<LinkedList<Integer>>() {
        @Override
        public int compare(LinkedList<Integer> o1, LinkedList<Integer> o2) {
            if (o1.size() > o2.size()) return 1;
            if (o1.size() < o2.size()) return -1;
            for (int i = 0; i < o1.size(); i++) {
                if (o1.get(i) > o2.get(i)) return 1;
                if (o1.get(i) < o2.get(i)) return -1;
            }
            return 0;
        }
    };

    public static final Comparator<int[]> planArrayComparator = new Comparator<int[]>() {
        @Override
        public int compare(int[] o1, int[] o2) {
            if (o1.length > o2.length) return 1;
            if (o1.length < o2.length) return -1;
            for (int i = 0; i < o1.length; i++) {
                if (o1[i] > o2[i]) return 1;
                if (o1[i] < o2[i]) return -1;
            }
            return 0;
        }
    };
}
