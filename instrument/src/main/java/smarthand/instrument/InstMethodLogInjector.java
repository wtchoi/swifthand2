package smarthand.instrument;

import soot.*;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;

import java.util.List;
import java.util.Map;

/**
 * Created by wtchoi on 10/9/15.
 */

public class InstMethodLogInjector extends BodyTransformer {

  private Spec spec;
  public boolean logEntry = true;
  public boolean logExit = true;

  public InstMethodLogInjector(String specFileName) {
    try {
      spec = new Spec(specFileName);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void setLogEntryFlag(boolean f) {
    this.logEntry = f;
  }

  public void setLogExitFlag(boolean f) {
    this.logExit = f;
  }

  @Override
  protected void internalTransform(
      final Body body, String phaseName,
      @SuppressWarnings("rawtypes") Map options) {
    final PatchingChain<Unit> units = body.getUnits();
    InstMethodLogHelper.tryInit();

    SootMethod method = body.getMethod();
    SootClass receiver = method.getDeclaringClass();
    List<Type> signature = method.getParameterTypes();
    String methodName = method.getName();
    if (!spec.match(receiver, methodName, signature)) return;

    UnitGraph unitGraph = new ExceptionalUnitGraph(body);

    if (logEntry) {
      for (Unit u : unitGraph.getHeads()) {
        while (mustSkip(u)) {
          List<Unit> next = unitGraph.getSuccsOf(u);

          // Currently, only consider the first successor.
          // TODO: check whether this implementation is correct
          u = next.get(0);
        }
        InstMethodLogHelper.instMethodEntryLog(method, body, units, u);
      }
    }

    if (logExit) {
      for (Unit u : unitGraph.getTails()) {
        InstMethodLogHelper.instMethodExitLog(method, body, units, u);
      }
    }

    try {
      body.validate();
    }
    catch (Exception e) {
      System.out.println(body.toString());
      throw e;
    }
  }

  private boolean mustSkip(Unit u) {
	String str = u.toString(); 
	if (str.startsWith("$r0 := @this:")) return true;
    if (str.startsWith("$r1 := @parameter0:")) return true;
    if (str.startsWith("$r2 := @parameter1:")) return true;
    if (str.startsWith("$r3 := @parameter2:")) return true;
    if (str.startsWith("$r4 := @parameter3:")) return true;
    if (str.startsWith("$r5 := @parameter4:")) return true;
    return false;
  }
}
