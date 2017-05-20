package smarthand.instrument;

import soot.*;
import soot.jimple.*;

import java.util.*;

/**
 * Created by wtchoi on 10/9/15.
 */

public class InstInvokeLog extends BodyTransformer {

  private Spec spec;
  public InstInvokeLog(String specFileName) {
    try {
      spec = new Spec(specFileName);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void internalTransform(
      final Body body, String phaseName,
      @SuppressWarnings("rawtypes") Map options) {
    final PatchingChain<Unit> units = body.getUnits();
    InstMethodLogHelper.tryInit();

    //important to use snapshotIterator here
    for (Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext(); ) {
      final Unit u = iter.next();

	  u.apply(
        new AbstractStmtSwitch() {
          public void caseInvokeStmt(InvokeStmt stmt) {
            InvokeExpr invokeExpr = stmt.getInvokeExpr();
            SootMethod method = invokeExpr.getMethod();

            SootClass receiverClass = invokeExpr.getMethod().getDeclaringClass();
            List<Type> signature = method.getParameterTypes();
            String methodName = method.getName();

            List<String> pattern = spec.getSigPatternString(receiverClass, methodName, signature);
            if (pattern == null) return;

            InstMethodLogHelper.instInvokeLog(invokeExpr, pattern, body, units, u);
            body.validate();
          }
        });
    }
  }
}
