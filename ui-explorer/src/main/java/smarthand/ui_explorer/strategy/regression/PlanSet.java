package smarthand.ui_explorer.strategy.regression;

import smarthand.ui_explorer.trace.Coverage;

import java.util.*;

/**
 * Created by wtchoi on 4/8/17.
 */
class PlanSet<Info extends PlanInfo> {
    public interface Observer<Info> {
        int FP = 0;
        int CV = 1;
        void onBeginFilterOut(String tag, Object opt);
        void onFilterOutItem(LinkedList<Integer> plan, Info p, int type, Object opt);
    }

    LinkedList<LinkedList<Integer>> plans = new LinkedList<>();
    private HashMap<LinkedList<Integer>, Info> planInfo = new HashMap();
    private HashSet<LinkedList<Integer>> planSet = new HashSet<>();

    private LinkedList<Observer> observers = new LinkedList<>();

    public PlanSet() {

    }

    public int remainingPlanCount() {
        return plans.size();
    }

    public void removePossibleFailures(LinkedList<Integer> failingPrefix) {
        observers.forEach(x->x.onBeginFilterOut("filter out plans with a failing prefix.", failingPrefix));
        LinkedList<LinkedList<Integer>> newPlans = new LinkedList<>();
        for (LinkedList<Integer> plan : plans) {
            if (!PlanUtil.prefixCheck(plan, failingPrefix)) {
                newPlans.addLast(plan);
            } else {
                observers.forEach(x->x.onFilterOutItem(plan, planInfo.get(plan), Observer.FP, null));
                planInfo.remove(plan);
                planSet.remove(plan);
            }
        }
        plans = newPlans;
    }

    public void filterPlansByCoverage(Coverage baseCoverage) {
        observers.forEach(x->x.onBeginFilterOut("filter out plans without a gain.", null));
        LinkedList<LinkedList<Integer>> survived = new LinkedList<>();
        for (LinkedList<Integer> p:plans) {
            if (planInfo.containsKey(p)) {
                if (!baseCoverage.isGreaterOrEqual(planInfo.get(p).c)) {
                    survived.add(p);
                } else {
                    observers.forEach(x -> x.onFilterOutItem(p, planInfo.get(p), Observer.CV, null));
                    removePlanInfo(p);
                }
            }
        }

        plans = survived;
    }

    public void sortPlans(Coverage baseCoverage) {
        for (PlanInfo pi: planInfo.values()) {
            pi.gain = Coverage.minus(pi.c, baseCoverage);
            pi.gainSize = pi.gain.size();
        }

        Comparator<LinkedList<Integer>> comparator = new Comparator<LinkedList<Integer>>() {
            public int compare(LinkedList<Integer> a, LinkedList<Integer> b) {
                int scoreA = planInfo.get(a).gainSize;
                int scoreB = planInfo.get(b).gainSize;
                if (scoreA > scoreB) return -1;
                if (scoreA < scoreB) return 1;
                if (a.size() > b.size()) return 1;
                if (a.size() < b.size()) return -1;
                return 0;
            }
        };

        plans.sort(comparator);
    }

    public LinkedList<Integer> fetchNextPlan() {
        return plans.isEmpty() ? null : plans.removeFirst();
    }

    public void removePlanInfo(LinkedList<Integer> plan) {
        planInfo.remove(plan);
        planSet.remove(plan);
    }

    public Info getPlanInfo(LinkedList<Integer> plan) {
        return planInfo.get(plan);
    }

    public void registerPlan(LinkedList<Integer> plan, Info info) {
        plans.add(plan);
        planInfo.put(plan, info);
    }

    public boolean contains(LinkedList<Integer> p) {
        return planSet.contains(p);
    }

    public void updatePlanInfo(LinkedList<Integer> plan, Info info) {
        planInfo.put(plan, info);
    }

    public Coverage getExpectedCoverageSum() {
        Coverage cv = new Coverage();
        for (PlanInfo pi: planInfo.values()) {
            cv.add(pi.c);
        }
        return cv;
    }

    public void addObserver(Observer observer){
        observers.addLast(observer);
    }


    //sample N plans, and remove the rest
    public void sample(int N, Random rand) {
        if (N > plans.size()) return;

        Collections.shuffle(plans, rand);
        LinkedList<LinkedList<Integer>> sampledPlans = new LinkedList<>();
        for (int i=0; i<N; i++) {
            sampledPlans.add(plans.get(i));
        }

        for (LinkedList<Integer> plan: plans) {
            if (!sampledPlans.contains(plan)) {
                removePlanInfo(plan);
            }
        }

        plans = sampledPlans;
    }
}
