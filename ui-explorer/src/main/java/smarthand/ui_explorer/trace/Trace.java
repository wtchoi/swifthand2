package smarthand.ui_explorer.trace;

import org.json.JSONArray;
import org.json.JSONObject;
import smarthand.ui_explorer.util.Util;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static smarthand.ui_explorer.trace.Action.Kind.Event;

/**
 * Created by wtchoi on 10/4/16.
 */
public class Trace {
    // -- subclasses --
    public static class Prefix {
        Trace trace;
        int cutPoint; //indicating the index of the last snapshot of the prefix

        public Snapshot getSnapshotFromLast(int index) {
            if (index > cutPoint) throw new RuntimeException("Something is wrong");
            return trace.snapshots.get(cutPoint - index);
        }

        public int length() {
            return cutPoint + 1;
        }
    }

    public static class Snapshot {
        public Action prevAction;
        public String activityName;
        public Boolean isKeyboardShown;
        public ConcreteUI concreteUI;
        public AbstractUI abstractUI;
        public Set<Integer> branchCoverage;
        public Set<Integer> methodCoverage;
        public JSONObject aux;          // auxiliary information

        public Snapshot(Action action, String activity, Boolean isKeyboardShown, ConcreteUI cui, AbstractUI aui, Set<Integer> bC, Set<Integer> mC, JSONObject aux) {
            prevAction = action;
            activityName = activity;
            this.isKeyboardShown = isKeyboardShown;
            concreteUI = cui;
            abstractUI = aui;
            branchCoverage = new HashSet(bC);
            methodCoverage = new HashSet(mC);
            this.aux = aux;
        }

        public static boolean similarNullable(Snapshot s1, Snapshot s2, boolean verbose) {
            if (s1 == null && s2 == null) {
                if (verbose) System.out.println("similarNullable:1");
                return false;
            }
            if (s1 == null && s2 != null) {
                if (verbose) System.out.println("similarNullable:2");
                return false;
            }
            if (s1 != null && s2 == null) {
                if (verbose) System.out.println("similarNullable:3");
                return false;
            }

            if (s1.prevAction != s2.prevAction) {
                if (verbose) {
                    System.out.println("similarNullable:4");
                    System.out.println(s1.prevAction.toString());
                    System.out.println(s2.prevAction.toString());
                }
                return false;
            }
            if (!Util.equalsNullable(s1.activityName, s2.activityName)) {
                if (verbose) {
                    System.out.println(s1.activityName);
                    System.out.println(s2.activityName);
                    System.out.println("similarNullable:5");
                }
                return false;
            }
            if (!Util.equalsNullable(s1.isKeyboardShown, s2.isKeyboardShown)) {
                if (verbose) System.out.println("similarNullable:6");
                return false;
            }
//            if (s1.concreteUI != s2.concreteUI) {
//                if (verbose) System.out.println("similarNullable:7");
//                return false;
//            }
            if (s1.abstractUI != s2.abstractUI) {
                if (verbose) {
                    System.out.println("similarNullable:8");
                    System.out.println(s1.abstractUI.getKey());
                    System.out.println(s2.abstractUI.getKey());
                }
                return false;
            }
            return true;
        }
    }



    // -- properties --
    public LinkedList<Snapshot> snapshots;

    public Trace() {
        this.snapshots = new LinkedList<>();
    }

    public Trace(Trace t) {
        this.snapshots = new LinkedList(t.snapshots);
    }


    // -- methods --

    // add transition. aux can be null.
    public void addTransition(Action event, String activityName, Boolean isKeyboardShown, ConcreteUI cui, AbstractUI aui, Set<Integer> bc, Set<Integer> mc, JSONObject aux) {
        snapshots.addLast(new Snapshot(event, activityName, isKeyboardShown, cui, aui, bc, mc, aux));
    }

    public void forEachSnapshot(Consumer<Snapshot> f) { snapshots.forEach(f); }

    public void forEachSnapshot(BiConsumer<Prefix, Snapshot> f) {
        throw new RuntimeException("Not implemented");
    }

    public Coverage computeGain(Coverage base, boolean ignoreStartClose) {
        Coverage c = new Coverage();
        snapshots.forEach(s -> {
            if (ignoreStartClose && (s.prevAction.isReset()) || s.prevAction.isStart()) return;
            Coverage sc = new Coverage(s.methodCoverage, s.branchCoverage, s.abstractUI.id());
            c.add(Coverage.minus(sc, base));
        });
        return c;
    }

    public Coverage computeCoverage(boolean ignoreStart) {
        return computeGain(new Coverage(), ignoreStart);
    }

    public Snapshot get(int index) {
        return this.snapshots.get(index);
    }

    public int size() {
        return this.snapshots.size();
    }

    // include both begin and end
    public Trace getSubtrace(int begin, int end) {
        Trace result = new Trace();
        result.snapshots = new LinkedList(this.snapshots.subList(begin, end+1));
        return result;
    }

    public void addSnapshot(Snapshot s) { this.snapshots.addLast(s); }

    public void append(Trace t) {
        for (Snapshot s: t.snapshots) { this.snapshots.addLast(s); }
    }

    // import trace from json object
    public static Trace fromJson(JSONObject traceFileObject) {
        Trace trace = new Trace();
        JSONArray traceObject = traceFileObject.getJSONArray("trace");

        for (int i=0; i<traceObject.length(); i++) {
            JSONObject snapshotObj = traceObject.getJSONObject(i);
            JSONObject cuiObj = snapshotObj.getJSONObject("ui");
            JSONObject auiObj = snapshotObj.getJSONObject("abstractState");
            String activityName = snapshotObj.getString("activity");
            Boolean isKeyboardShown = snapshotObj.getBoolean("isKeyboardShown");
            Set<Integer> bc = new HashSet(constructIntList(snapshotObj.getJSONArray("branchCoverage")));
            Set<Integer> mc = new HashSet(constructIntList(snapshotObj.getJSONArray("methodCoverage")));
            String event = snapshotObj.getString("action");
            Integer actionIndex = snapshotObj.has("actionIndex") ? snapshotObj.getInt("actionIndex") : null;
            JSONObject aux = snapshotObj.has("aux") ? snapshotObj.getJSONObject("aux") : null;


            AbstractUI aui = AbstractUI.fromJson(auiObj);
            ConcreteUI cui = ConcreteUI.getFromRawInfo(cuiObj, aui);
            Action evn = Action.getAction(Action.Kind.get(event), actionIndex);
            trace.addTransition(evn, activityName, isKeyboardShown, cui, aui, bc, mc, aux);
        }

        return trace;
    }

    // Generating a new trace by interesting the coverage of otherwise identical two traces.
    public static Trace intersect(Trace a, Trace b) {
        //Trace a and Trace b should be identical except test coverage. The method will raise an exception, otherwise.

        if (a.size() != b.size()) {
            System.out.println(a.size());
            System.out.println(b.size());
            throw new RuntimeException("Something is wrong");
        }

        LinkedList<Snapshot> ls1 = a.snapshots;
        LinkedList<Snapshot> ls2 = b.snapshots;

        Tracer tr = new Tracer();
        for (int i =0; i<ls1.size(); i++) {
            Snapshot s1 = ls1.get(i);
            Snapshot s2 = ls2.get(i);

            if (!Snapshot.similarNullable(s1, s2, false)) {
                Snapshot.similarNullable(s1, s2, true);
                throw new RuntimeException("Something is wrong");
            }

            Set<Integer> bc = Util.setIntersect(s1.branchCoverage, s2.branchCoverage);
            Set<Integer> mc = Util.setIntersect(s1.methodCoverage, s2.methodCoverage);
            tr.on(s1.prevAction, s1.activityName, s1.isKeyboardShown, s1.concreteUI, s1.abstractUI, bc, mc, s1.aux);
        }

        return tr.getTrace();
    }

    // Trim the branch and method coverage of the input trace (by taking intersection with Coverage c).
    public static Trace trim(Trace tt, Coverage c, boolean ignoreStart) {
        Trace t = new Trace(tt);
        for (int i=0; i<tt.size(); i++) {
            Trace.Snapshot s = t.get(i);
            if (ignoreStart && (s.prevAction.isStart() && s.prevAction.isReset())) continue;
            s.methodCoverage = Util.setIntersect(s.methodCoverage, c.methodCoverage);
            s.branchCoverage = Util.setIntersect(s.branchCoverage, c.branchCoverage);
        }
        return t;
    }

    private static Collection<Integer> constructIntList(JSONArray arr) {
        LinkedList<Integer> lst = new LinkedList<>();
        for (int i=0; i<arr.length(); i++) {
            //lst.add(Integer.parseInt(arr.getString(i)));
            lst.add(arr.getInt(i));
        }
        return lst;
    }

    private static JSONArray constructJsonIntList(Collection<Integer> lst) {
        JSONArray arr = new JSONArray();
        //lst.forEach(x -> arr.put(Integer.toString(x)));
        lst.forEach(x -> arr.put(x));
        return arr;
    }

    // export trace to json object
    public JSONObject toJson() {
        JSONObject traceFileObj = new JSONObject();
        JSONArray traceObj = new JSONArray();

        int id=0;
        for (Snapshot snapshot: snapshots) {
            JSONObject snapshotObj = new JSONObject();
            snapshotObj.put("id", id++);
            snapshotObj.put("action", snapshot.prevAction.kind);
            if (snapshot.prevAction.kind == Event) {
                snapshotObj.put("actionIndex", snapshot.prevAction.actionIndex);
            }
            snapshotObj.put("activity", snapshot.activityName);
            snapshotObj.put("isKeyboardShown", snapshot.isKeyboardShown);
            snapshotObj.put("ui", snapshot.concreteUI.toJson());
            snapshotObj.put("abstractState", snapshot.abstractUI.exportEventsToJson());

            snapshotObj.put("branchCoverage", constructJsonIntList(snapshot.branchCoverage));
            snapshotObj.put("methodCoverage", constructJsonIntList(snapshot.methodCoverage));
            if (snapshot.aux != null) snapshotObj.put("aux", snapshot.aux);

            traceObj.put(snapshotObj);
        }

        traceFileObj.put("trace", traceObj);
        return traceFileObj;
    }
}
