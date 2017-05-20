package smarthand.instrument;

import soot.*;
import soot.baf.Inst;
import soot.jimple.Expr;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.StringConstant;

import java.util.*;

/**
 * Created by wtchoi on 10/14/15.
 */
public class InstMethodLogHelper {
  private static final Map<String, SootMethod> loggerMethodTbl = new HashMap<>();
  private static LibraryInjector libraryInjector = null;
  private static String libraryClassName = null;
  private static boolean initialized = false;
  private static SootClass libraryClass = null;

  public static void init() {
    libraryInjector = new LibraryInjector("/library_inject.json");
    libraryClassName = "smarthand.logger.Logger";
  }

  public static void insertInvoke0(String methodName, PatchingChain<Unit> units, Unit u) {
    InstHelper.insertInvoke0(loggerMethodTbl.get(methodName), units, u);
  }

  public static void insertInvoke1(String methodName, Value argExp, PatchingChain<Unit> units, Unit u) {
    InstHelper.insertInvoke1(loggerMethodTbl.get(methodName), argExp, units, u);
  }

  public static void insertInvoke2(String methodName, Value arg1, Value arg2, PatchingChain<Unit> units, Unit u) {
    InstHelper.insertInvoke2(loggerMethodTbl.get(methodName), arg1, arg2, units, u);
  }

  public static void instInvokeLog(InvokeExpr invokeExpr, List<String> pattern, Body body, PatchingChain<Unit> units, Unit u) {
    insertInvoke0("prepareInvoke", units, u);
    for(int i =0 ; i < invokeExpr.getArgCount() ; i++) {
      String paramType = pattern.get(i);
      Value argExp = invokeExpr.getArg(i);

      switch (paramType) {
        case "Intent" :
          insertInvoke1("pushArgIntent", argExp, units, u);
          break;
        case "_" :
          insertInvoke0("pushArgDummy", units, u);
          break;
        default:
          throw new RuntimeException("ERROR!");
      }
    }

    Local tmpStrVar = InstHelper.addTmpString(body);

    String methodName = invokeExpr.getMethod().getName();
    InstHelper.insertAssignValue(tmpStrVar, StringConstant.v(methodName), units, u);

    insertInvoke1("handleInvoke", tmpStrVar, units, u);
  }

  public static void instMethodExitLog(SootMethod method, Body body, PatchingChain<Unit> units, Unit u) {
    Local tmpStrVar = InstHelper.addTmpString(body);
    String methodName = method.getName();

    String declaringClass = method.getDeclaringClass().getName();;

    insertInvoke0("prepareInvoke", units, u);
    InstHelper.insertAssignValue(tmpStrVar, StringConstant.v(declaringClass), units, u);
    insertInvoke1("pushArgStr", tmpStrVar, units, u);
    InstHelper.insertAssignValue(tmpStrVar, StringConstant.v(methodName), units, u);
    insertInvoke1("handleMethodExit", tmpStrVar, units, u);
  }

  public static void instMethodEntryLog(SootMethod method, Body body, PatchingChain<Unit> units, Unit u) {
    Local tmpStrVar = InstHelper.addTmpString(body);
    String methodName = method.getName();

    String declaringClass = method.getDeclaringClass().getName();;

    insertInvoke0("prepareInvoke", units, u);
    InstHelper.insertAssignValue(tmpStrVar, StringConstant.v(declaringClass), units, u);
    insertInvoke1("pushArgStr", tmpStrVar, units, u);
    InstHelper.insertAssignValue(tmpStrVar, StringConstant.v(methodName), units, u);
    insertInvoke1("handleMethodEntry", tmpStrVar, units, u);
  }


  public static void tryInit() {
    if (initialized) return;
    initialized = false;
    libraryInjector.prepare();
    libraryInjector.inject();

    libraryClass = Scene.v().getSootClass(libraryClassName);

    {
      final String[] init = {
          // list of pairs ("method name", "Soot style method signature")
          "prepareInvoke", "void prepareInvoke()",
          "pushArgIntent", "void pushArgIntent(android.content.Intent)",
          "pushArgStr",    "void pushArgStr(java.lang.String)",
          "pushArgDummy",  "void pushArgDummy()",
          "handleInvoke",  "void handleInvoke(java.lang.String)",
          "handleMethodExit", "void handleMethodExit(java.lang.String)",
          "handleMethodEntry", "void handleMethodEntry(java.lang.String)",
          "hello", "void hello()"
      };

      for (int i = 0 ; i < init.length / 2 ; i++) {
        loggerMethodTbl.put(init[i * 2], libraryClass.getMethod(init[i * 2 + 1]));
      }
    }
  }

  public static SootClass getLibraryClass() {
    return libraryClass;
  }
}
