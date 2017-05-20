package smarthand.instrument;

import soot.*;
import soot.jimple.*;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by wtchoi on 4/5/16.
 */
public class InstCoverageInjector extends BodyTransformer {

    private Spec spec;
    private SootClass libraryClass;
    private String libraryClassName;
    private LibraryInjector libraryInjector;
    private HashSet<String> packages = new HashSet<>();
    AtomicBoolean injected = new AtomicBoolean(false);

    // need to be accessed with "synchronous" keyword
    private int nextMethodId = 0;
    private int nextBranchId = 0;
    private LinkedList<String> methodNames = new LinkedList<>();
    private LinkedList<Integer> methodLines = new LinkedList<>();
    private LinkedList<BranchInfo> branchTable = new LinkedList<>();

    private static final int branchTypes = 4;

    enum BranchTag {
        True(0), False(1), TrapHandler(2), Entry(3);

        private final int id;

        private BranchTag(int id) {
            this.id = id;
        }

        public int id() {
            return this.id;
        }
    }


    class BranchInfo {
        public int methodId;
        public int lineNumber;
        public BranchTag tag;

        BranchInfo(int methodId, int lineNumber, BranchTag tag) {
            this.methodId = methodId;
            this.lineNumber = lineNumber;
            this.tag = tag;
        }
    }

    public InstCoverageInjector(String specFileName) {
        try {
            spec = new Spec(specFileName);
            libraryInjector = new LibraryInjector("/library_inject.json");
            libraryClassName = "smarthand.dynamic.coverage.Coverage";

            libraryInjector.prepare();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private void tryInject() {
        boolean localInjected;
        synchronized (injected) {
            localInjected = injected.get();
            if (!localInjected) {
                injected.set(true);
                libraryInjector.inject();
                libraryClass = Scene.v().getSootClass(libraryClassName);
            }
        }
    }

    @Override
    protected void internalTransform(
            final Body body, String phaseName,
            @SuppressWarnings("rawtypes") Map options) {

        tryInject();

        final PatchingChain<Unit> units = body.getUnits();

        UnitGraph unitGraph = new ExceptionalUnitGraph(body);

        SootMethod method = body.getMethod();
        SootClass cls = body.getMethod().getDeclaringClass();

        if (!spec.match(cls, method.getName(), method.getParameterTypes())) return;

        String pkg = method.getDeclaringClass().getPackageName();
        LinkedList<String> seq = new LinkedList<>();

        String methodKey = buildMethodKey(method);

        int methodId = 0;
        synchronized (methodNames) {
            methodNames.add(methodKey);
            methodLines.add(method.getJavaSourceStartLineNumber());
            methodId = nextMethodId++;
        }

        // Inject function call at method head
        for (Unit u : unitGraph.getHeads()) {
            seq.clear();
            if (isEntry(method, unitGraph, u)) {
                u = findMethodCoverageAnchor(method, unitGraph, u, seq);
                if (u == null) continue;

                injectMethodCoverageReport(methodId, units, u);

                synchronized (packages) {
                    packages.add(pkg);
                }
                break;
            }
        }

        // Inject function call at successors of branches
        HashSet<Unit> ifSet = new HashSet<>();
        HashSet<Unit> switchSet = new HashSet<>();
        HashSet<Unit> tableSwitchSet = new HashSet<>();
        HashSet<Unit> trapHandlerSet = new HashSet<>();
        for (Unit u : unitGraph) {
            if (u instanceof IfStmt) {
                ifSet.add(u);
            }
            else if (u instanceof LookupSwitchStmt) {
                switchSet.add(u);
            }
            else if (u instanceof TableSwitchStmt) {
                tableSwitchSet.add(u);
            }
            else if (isTrapHandlerEntry(u)) {
                trapHandlerSet.add(u);
            }
        }

        for (Unit u : ifSet) injectBranchCoverageReportIf(methodId, units, u);
        for (Unit u : switchSet) injectBranchCoverageReportSwitch(methodId, units, u);
        for (Unit u : tableSwitchSet) injectBranchCoverageReportTableSwitch(methodId, units, u);
        for (Unit u : trapHandlerSet) injectBranchCoverageReportTrapHandler(methodId, units, u);

        try {
            body.validate();
        }
        catch (Exception e) {
            StringBuilder str = new StringBuilder();
            for(String s: seq) {
                str.append(s + "\n");
            }
            //System.out.println(str.toString());
            System.out.println(body.toString());
            throw e;
        }
    }

    private Unit findBranchCoverageAnchor(SootMethod method, UnitGraph ug, Unit u) {
        if (isTrapHandlerEntry(u)) return null; //ignore trap handler
        return u;
    }

    private Unit findMethodCoverageAnchor(SootMethod method, UnitGraph ug, Unit u, LinkedList<String> seq) {
        if (isTrapHandlerEntry(u)) return null;

        seq.add(u.toString() + " " + isParameterAssignment(u) + " " + isTrapHandlerEntry(u) + " " + isEntry(method, ug, u));
        if (isParameterAssignment(u)) {
            List<Unit> succs = ug.getSuccsOf(u);
            for (Unit succ: succs) {
                Unit target = findMethodCoverageAnchor(method, ug, succ, seq);
                if (target == null) continue;
                else return target;
            }
            seq.removeLast();
            return null;
        }
        else {
            return u;
        }
    }

    private boolean isEntry(SootMethod method, UnitGraph ug, Unit u) {
        boolean isStatic = method.isStatic();
        int numParam = method.getParameterCount();

        if (!isStatic || numParam > 0) {
            return isParameterAssignment(u);
        }
        return !isTrapHandlerEntry(u);
    }

    private boolean isParameterAssignment(Unit u) {
        String str = u.toString();
        if (str.matches("\\$\\p{Lower}\\d+ := @((parameter\\d+)|this):.+")) return true;
        return false;
    }

    private boolean isTrapHandlerEntry(Unit u) {
        return u.toString().matches("\\$\\p{Lower}\\d+ := @caughtexception");
    }

    private int getFreshBranchId(int methodId, int line, BranchTag tag) {
        int id =0;
        BranchInfo bl = new BranchInfo(methodId, line, tag);
        synchronized (branchTable) {
            id = nextBranchId++;
            branchTable.add(bl);
        }

        return id * branchTypes + tag.id();
    }

    private void injectBranchCoverageReportIf(int methodId, PatchingChain<Unit> units, Unit u) {
        int line = u.getJavaSourceStartLineNumber();
        int bidTrue = getFreshBranchId(methodId, line, BranchTag.True);
        int bidFalse = getFreshBranchId(methodId, line, BranchTag.False);

        IfStmt ifStmt = (IfStmt) u;
        SootMethodRef mr;

        mr = libraryClass.getMethod("void reportBranch(int)").makeRef();
        Stmt pos = Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(mr, IntConstant.v(bidTrue)));
        IfStmt is = Jimple.v().newIfStmt(ifStmt.getCondition(), pos);
        units.insertBefore(is, ifStmt);

        mr = libraryClass.getMethod("void reportBranch(int)").makeRef();
        Stmt neg = Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(mr, IntConstant.v(bidFalse)));
        units.insertBefore(neg, ifStmt);

        NopStmt ns = Jimple.v().newNopStmt();
        units.insertBefore(Jimple.v().newGotoStmt(ns), ifStmt);
        units.insertBefore(pos, ifStmt);
        units.insertBefore(ns, ifStmt);
    }

    private void injectBranchCoverageReportSwitch(int methodId, PatchingChain<Unit> units, Unit u) {
        LookupSwitchStmt lookupSwitchStmt = (LookupSwitchStmt) u;
        int sz = lookupSwitchStmt.getTargetCount();
        NopStmt ns = Jimple.v().newNopStmt();
        LinkedList linkedList = new LinkedList();
        int line = u.getJavaSourceStartLineNumber();

        SootMethodRef mr;
        int bid;

        for (int i = 0; i < sz; i++) {
            EqExpr eq = Jimple.v().newEqExpr(lookupSwitchStmt.getKey(), IntConstant.v(lookupSwitchStmt.getLookupValue(i)));

            mr = libraryClass.getMethod("void reportBranch(int)").makeRef();
            bid = getFreshBranchId(methodId, line, BranchTag.True);
            Stmt pos = Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(mr, IntConstant.v(bid)));

            mr = libraryClass.getMethod("void reportBranch(int)").makeRef();
            bid = getFreshBranchId(methodId, line, BranchTag.False);
            Stmt neg = Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(mr, IntConstant.v(bid)));

            //visitBinopExpr(sm, units, lookupSwitchStmt, eq, IfContextImpl.getInstance()); ??
            IfStmt ifStmt = Jimple.v().newIfStmt(eq, pos);
            units.insertBefore(ifStmt, lookupSwitchStmt);
            units.insertBefore(neg, lookupSwitchStmt);
            linkedList.addLast(pos);
        }
        units.insertBefore(Jimple.v().newGotoStmt(ns), lookupSwitchStmt);
        for (Object pos1 : linkedList) {
            Stmt stmt = (Stmt) pos1;
            units.insertBefore(stmt, lookupSwitchStmt);
            units.insertBefore(Jimple.v().newGotoStmt(ns), lookupSwitchStmt);
        }
        units.insertBefore(ns, lookupSwitchStmt);
    }

    private void injectBranchCoverageReportTableSwitch(int methodId, PatchingChain<Unit> units, Unit u) {
        TableSwitchStmt tableSwitchStmt = (TableSwitchStmt) u;
        int sz = tableSwitchStmt.getHighIndex();
        NopStmt ns = Jimple.v().newNopStmt();
        LinkedList linkedList = new LinkedList();
        int line = u.getJavaSourceStartLineNumber();

        SootMethodRef mr;
        int bid;

        for (int i = tableSwitchStmt.getLowIndex(); i < sz; i++) {
            EqExpr eq = Jimple.v().newEqExpr(tableSwitchStmt.getKey(), IntConstant.v(i));

            mr = libraryClass.getMethod("void reportBranch(int)").makeRef();
            bid = getFreshBranchId(methodId, line, BranchTag.True);
            Stmt pos = Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(mr, IntConstant.v(bid)));

            mr = libraryClass.getMethod("void reportBranch(int)").makeRef();
            bid = getFreshBranchId(methodId, line, BranchTag.False);
            Stmt neg = Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(mr, IntConstant.v(bid)));

            //visitBinopExpr(sm, units, lookupSwitchStmt, eq, IfContextImpl.getInstance()); ??
            IfStmt ifStmt = Jimple.v().newIfStmt(eq, pos);
            units.insertBefore(ifStmt, tableSwitchStmt);
            units.insertBefore(neg, tableSwitchStmt);
            linkedList.addLast(pos);
        }
        units.insertBefore(Jimple.v().newGotoStmt(ns), tableSwitchStmt);
        for (Object pos1 : linkedList) {
            Stmt stmt = (Stmt) pos1;
            units.insertBefore(stmt, tableSwitchStmt);
            units.insertBefore(Jimple.v().newGotoStmt(ns), tableSwitchStmt);
        }
        units.insertBefore(ns, tableSwitchStmt);
    }

    private void injectBranchCoverageReportTrapHandler(int methodId,  PatchingChain<Unit> units, Unit u) {
        int line = u.getJavaSourceStartLineNumber();
        SootMethodRef mr = libraryClass.getMethod("void reportBranch(int)").makeRef();
        int bid = getFreshBranchId(methodId, line, BranchTag.TrapHandler);
        Stmt stmt = Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(mr, IntConstant.v(bid)));
        units.insertAfter(stmt, u);
    }


    private void injectMethodCoverageReport(int methodId, PatchingChain<Unit> units, Unit u) {
        SootMethod sm = libraryClass.getMethod("void reportMethod(int)");
        InstHelper.insertInvoke1(sm, IntConstant.v(methodId), units, u);
    }

    private String buildMethodKey(SootMethod method) {
        String pkgName = method.getDeclaringClass().getPackageName();
        String declaringClass = method.getDeclaringClass().getName();
        String methodName = method.getName();

        List<Type> methodSig = method.getParameterTypes();

        boolean isFirst = true;
        StringBuilder sig  = new StringBuilder();
        for (Type t : methodSig) {
            if (isFirst) isFirst = false;
            else sig.append(",");
            sig.append(t.toString());
        }

        String key = pkgName + ":" + declaringClass + ":" + methodName + ":" + methodSig;
        return key;
    }

    public void dump(String directory) throws IOException {
        {
            File file = new File(directory + "/" + "method_table");
            PrintWriter writer = new PrintWriter(file);
            for (int i =0;i<methodNames.size();i++) {
                String s = methodNames.get(i);
                Integer l = methodLines.get(i);
                writer.println(s + ":" + l);
            }
            writer.flush();
            writer.close();
        }

        {
            File file = new File(directory + "/" + "branch_table");
            PrintWriter writer = new PrintWriter(file);
            int index = 0;
            for (BranchInfo b : branchTable) {
                writer.print((index++)*branchTypes + b.tag.id);
                writer.print(", ");
                writer.print(methodNames.get(b.methodId));
                writer.print(", ");
                writer.print(b.lineNumber);
                writer.print(", ");
                writer.println(b.tag.name());
            }
            writer.flush();
            writer.close();
        }

        for (String s: packages) {
            System.out.println("pkg:" + s);
        }
        System.out.flush();
    }
}
