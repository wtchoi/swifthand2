package smarthand.dynamic.coverage;

/**
 * Created by wtchoi on 10/8/15.
 */

import android.content.ComponentName;
import android.content.Intent;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Coverage {
  private static CoverageProxy coverageProxy;

  /**
   *
   * @param id Method identifier
   */
  public static void reportMethod(int id) {
    tryInitialize();
    coverageProxy.reportMethod(id);
  }

  public static void reportBranch(int bid) {
    tryInitialize();
    coverageProxy.reportBranch(bid);
  }

  public static void hello() {
    logcat("Hello Hello");
  }

  private static void tryInitialize() {
    if (coverageProxy == null) {
      coverageProxy = new CoverageProxy();
      coverageProxy.start();
    }
  }

  private static void logcat(String m) {
    System.out.println("wtchoi:" + m);
  }

  // To prevent the instantiation of the class.
  private Coverage() {
    throw new AssertionError();
  }
}


