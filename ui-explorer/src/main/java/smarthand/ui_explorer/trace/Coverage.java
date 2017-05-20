package smarthand.ui_explorer.trace;

import smarthand.ui_explorer.util.Util;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by wtchoi on 11/4/16.
 */
public class Coverage {
    public HashSet<Integer> methodCoverage;
    public HashSet<Integer> branchCoverage;
    public HashSet<Integer> screenCoverage;

    public Coverage() {
        this.methodCoverage = new HashSet<>();
        this.branchCoverage = new HashSet<>();
        this.screenCoverage = new HashSet<>();
    }

    public Coverage(Coverage c) {
        this.methodCoverage = new HashSet<>();
        this.branchCoverage = new HashSet<>();
        this.screenCoverage = new HashSet<>();
        this.add(c);
    }

    public Coverage(Set<Integer> methodCoverage, Set<Integer> branchCoverage, int absUiId) {
        this.methodCoverage = new HashSet<>();
        this.branchCoverage = new HashSet<>();
        this.screenCoverage = new HashSet<>();
        this.add(methodCoverage, branchCoverage, absUiId);
    }

    public Coverage(Set<Integer> methodCoverage, Set<Integer> branchCoverage, HashSet<Integer> screenCoverage) {
        this.methodCoverage = new HashSet<>();
        this.branchCoverage = new HashSet<>();
        this.screenCoverage = new HashSet<>();
        this.add(methodCoverage, branchCoverage, screenCoverage);
    }

    public int size() {
        return this.methodCoverage.size() + this.branchCoverage.size() + screenCoverage.size();
    }

    public void add(Set<Integer> m, Set<Integer> b) {
        this.methodCoverage.addAll(m);
        this.branchCoverage.addAll(b);
    }

    public void add(Set<Integer> m, Set<Integer> b, int absUiId) {
        this.methodCoverage.addAll(m);
        this.branchCoverage.addAll(b);
        this.screenCoverage.add(absUiId);
    }

    public void add(Set<Integer> m, Set<Integer> b, Set<Integer> s) {
        this.methodCoverage.addAll(m);
        this.branchCoverage.addAll(b);
        this.screenCoverage.addAll(s);
    }

    public void add(Coverage c) {
        this.methodCoverage.addAll(c.methodCoverage);
        this.branchCoverage.addAll(c.branchCoverage);
        this.screenCoverage.addAll(c.screenCoverage);
    }

    public boolean isGreaterThan(Coverage c) {
        return this.isGreaterOrEqual(c) && this.size() > c.size();
    }

    public boolean isGreaterOrEqual(Coverage c) {
        return this.methodCoverage.containsAll(c.methodCoverage)
                && this.branchCoverage.containsAll(c.branchCoverage)
                && this.screenCoverage.containsAll(c.screenCoverage);
    }

    public boolean isEqualTo(Coverage c) {
        return this.isGreaterOrEqual(c) && c.isGreaterOrEqual(this);
    }

    public static Coverage minus(Coverage a, Coverage b) {
        HashSet<Integer> mm = Util.setMinus(a.methodCoverage, b.methodCoverage);
        HashSet<Integer> mb = Util.setMinus(a.branchCoverage, b.branchCoverage);
        HashSet<Integer> ms = Util.setMinus(a.screenCoverage, b.screenCoverage);
        return new Coverage(mm, mb, ms);
    }

    public static Coverage add(Coverage a, Coverage b) {
        Coverage res = new Coverage(a);
        res.add(b);
        return res;
    }

    public static Coverage intersect(Coverage a, Coverage b) {
        HashSet<Integer> mm = Util.setIntersect(a.methodCoverage, b.methodCoverage);
        HashSet<Integer> mb = Util.setIntersect(a.branchCoverage, b.branchCoverage);
        HashSet<Integer> ms = Util.setIntersect(a.screenCoverage, b.screenCoverage);
        return new Coverage(mm, mb, ms);
    }
}
