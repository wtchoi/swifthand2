package smarthand.instrument;

import soot.*;
import soot.jimple.Expr;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.StringConstant;

import java.util.List;

/**
 * Created by wtchoi on 4/11/16.
 */
public class InstHelper {
    public static void insertInvoke0(SootMethod toCall, PatchingChain<Unit> units, Unit u) {
        Expr expr = Jimple.v().newStaticInvokeExpr(toCall.makeRef());
        units.insertBefore(Jimple.v().newInvokeStmt(expr), u);
    }

    public static void insertInvoke1(SootMethod toCall, Value argExp, PatchingChain<Unit> units, Unit u) {
        Expr expr = Jimple.v().newStaticInvokeExpr(toCall.makeRef(), argExp);
        units.insertBefore(Jimple.v().newInvokeStmt(expr), u);
    }

    public static void insertInvoke2(SootMethod toCall, Value arg1, Value arg2, PatchingChain<Unit> units, Unit u) {
        Expr expr = Jimple.v().newStaticInvokeExpr(toCall.makeRef(), arg1, arg2);
        units.insertBefore(Jimple.v().newInvokeStmt(expr), u);
    }

    public static void insertAssignValue(Local var, Value value, PatchingChain<Unit> units, Unit u) {
        units.insertBefore(Jimple.v().newAssignStmt(var, value), u);
    }

    public static Local addTmpRef(Body body) {
        Local tmpRef = Jimple.v().newLocal("tmpRef", RefType.v("java.io.PrintStream"));
        body.getLocals().add(tmpRef);
        return tmpRef;
    }

    public static Local addTmpString(Body body) {
        Local tmpString = Jimple.v().newLocal("tmpString", RefType.v("java.lang.String"));
        body.getLocals().add(tmpString);
        return tmpString;
    }

    public static Local addTmpInt(Body body) {
        Local tmpString = Jimple.v().newLocal("tmpInt", RefType.v("int"));
        body.getLocals().add(tmpString);
        return tmpString;
    }
}
