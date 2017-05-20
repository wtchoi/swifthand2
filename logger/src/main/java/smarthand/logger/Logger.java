package smarthand.logger;

/**
 * Created by wtchoi on 10/8/15.
 */

import android.content.ComponentName;
import android.content.Intent;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import smarthand.ui_driver.Constants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.LinkedList;

public class Logger {
  private static JSONArray args;
  private static LogProxy logProxy;

  public class C {
    // keywords
    public static final String action = "action";
    public static final String argument = "argument";
    public static final String arguments = "arguments";
    public static final String cls = "class";
    public static final String category = "category";
    public static final String categories = "categories";
    public static final String component = "component";
    public static final String data = "data";
    public static final String dummy = "dummy";
    public static final String entry = "entry";
    public static final String exit = "exit";
    public static final String invoke = "invoke";
    public static final String method = "method";
    public static final String name = "name";
    public static final String pkg = "package";
    public static final String type = "type";

    // method names
    public static final String StartActivity = "StartActivity";
    public static final String StartActivities = "StartActivities";
    public static final String StartActivityForResult = "StartActivityForResult";
    public static final String StartActivityIfNeeded = "StartActivityIfNeeded";
    public static final String StartIntentSender = "StartIntentSender";
    public static final String StartIntentSenderForResult = "StartIntentSenderForResult";
    public static final String StartNextMatchingActivity = "StartNextMatchingActivity";
    public static final String Finish = "Finish";
    public static final String FinishActivity = "FinishActivity";
    public static final String FinishAffinity = "FinishAffinity";
    public static final String FinishAndRemoveAfterTask = "FinishAndRemoveAfterTask";

    // To prevent the instantiation.
    private C() {
      throw new AssertionError();
    }
  }

  public static void prepareInvoke() {
    tryInitialize();
    args = new JSONArray();
  }

  /**
   *
   * @param intent The Intent object passed to the method being invoked.
   */

  public static void pushArgIntent(Intent intent) {
    try {
      // note: check Intent.resolveActivity
      JSONObject arg = new JSONObject();
      ComponentName cname = intent.getComponent();
      if (cname != null) {
        // handle explicit intent
        arg.put(C.component,
            (new JSONObject())
                .put(C.pkg, cname.getPackageName())
                .put(C.cls, cname.getClassName()));
      } else {
        // handle implicit intent
        arg.put(C.action, intent.getAction())
            .put(C.type, intent.getType())
            .put(C.data, intent.getData())
            .put(C.categories, intent.getCategories())
            .put(C.pkg, intent.getPackage());
      }
      args.put(new JSONArray().put("Intent").put(arg));
    } catch (JSONException je) {
      args.put("Cannot Log Intent!\n" + je);
    }
  }

  public static void pushArgStr(String str) {
    args.put(new JSONArray().put("String").put(str));
  }

  public static void pushArgDummy() {
    args.put(new JSONArray().put(C.dummy));
  }

  /**
   *
   * @param methodName indicates the method being invoked.
   */
  public static void handleInvoke(String methodName) {
    try {
      String message = new JSONObject()
          .put(C.type, C.invoke)
          .put(C.method, methodName)
          .put(C.arguments, args)
          .toString();

      logcat(message);
      logProxy.request(message);
    } catch (JSONException je) {
      logcat("error");
    }
    args = null;
  }

  public static void handleMethodExit(String methodName) {
    try {
      String message = new JSONObject()
          .put(C.type, C.exit)
          .put(C.method, methodName)
          .put(C.arguments, args)
          .toString();

      logcat(message);
      logProxy.request(message);
    } catch (JSONException je) {
      logcat("error");
    }
  }

  public static void handleMethodEntry(String methodName) {
    try {
      String message = new JSONObject()
          .put(C.type, C.entry)
          .put(C.method, methodName)
          .put(C.arguments, args)
          .toString();

      logcat(message);
      logProxy.request(message);
    } catch (JSONException je) {
      logcat("error");
    }
  }

  public static void hello() {
    logcat("Hello Hello");
  }

  private static void tryInitialize() {
    if (logProxy == null) {
      logProxy = new LogProxy();
      logProxy.start();
    }
  }

  private static void logcat(String m) {
    System.out.println("wtchoi:" + m);
  }

  // To prevent the instantiation of the class.
  private Logger() {
    throw new AssertionError();
  }
}


