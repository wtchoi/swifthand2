package smarthand.ui_driver;

/**
 * Created by ksen on 3/10/15.
 */

import android.os.RemoteException;

// Import the uiautomator libraries
import android.os.SystemClock;
import com.android.uiautomator.core.*;
import com.android.uiautomator.testrunner.UiAutomatorTestCase;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;

public class SwiftHand extends UiAutomatorTestCase {
  private Random rand = new Random();
  private LogServer logServer = new LogServer();

  public static final int TIMEOUT = 10000;
  public static final int WAIT = 200;

  private StopWatch watch = new StopWatch();
  private boolean close_requested = false;

  private void miniwait() {
    SystemClock.sleep(WAIT);
  }

  private void waitForIdle() {
    try {
      System.out.println("Wait for idle (BEGIN)");
      watch.begin("waitForIdle");

      miniwait();
      getUiDevice().waitForIdle(TIMEOUT);

      watch.end("waitForIdle");
      System.out.println("Wait for idle (END)");
    }
    catch(Exception e) {
      throw new RuntimeException(e);
    }
  }

  private ActionPair[] actions = new ActionPair[]{
      new ActionPair("back", new Action() {
        public void action() {
          System.out.println("back");
          getUiDevice().pressBack();
          waitForIdle();
        }
      }),
      new ActionPair("menu", new Action() {
        public void action() {
          System.out.println("menu");
          getUiDevice().pressMenu();
          waitForIdle();
        }
      })
  };

  boolean clickButton(String description) throws UiObjectNotFoundException {
    UiCollection root = getRootSet();
    int count;

    UiSelector selector = new UiSelector().className("android.widget.Button").text(description);
    count = root.getChildCount(selector);
    if (count > 0) {
      UiObject uiobj;
      uiobj = new UiObject(new UiSelector().className("android.widget.Button").text(description));
      uiobj.click();
      return true;
    }
    return false;
  }

  private void handle_crash() throws UiObjectNotFoundException {
    if (clickButton("OK")) return;
    if (clickButton("Cancel")) return;
  }

  private void closeApp_pm(String packageName) {
    try {
      close_requested = true;
      System.out.println("close app");
      String cmd = "pm clear " + packageName;
      Process pr = Runtime.getRuntime().exec(cmd);
      pr.waitFor();

      System.out.println("Closing " + packageName);
      waitForIdle();
    } catch (Exception e) {
      e.printStackTrace(System.out);
    }
  }

  private void closeApp_am(String packageName) {
    try {
      close_requested = true;
      System.out.println("close app");
      String cmd = "am kill " + packageName;
      Process pr = Runtime.getRuntime().exec(cmd);
      pr.waitFor();

      System.out.println("Closing " + packageName);
      waitForIdle();
    } catch (Exception e) {
      e.printStackTrace(System.out);
    }
  }

  private void closeApp() throws UiObjectNotFoundException, RemoteException {
    close_requested = true;
    //logServer.kill();

    System.out.println("close app");
    getUiDevice().pressRecentApps();
    waitForIdle();

    UiCollection root = getRootSet();
    int count = 0;

    while (count == 0) {
      count = root.getChildCount(new UiSelector()
          .resourceId("com.android.systemui:id/task_view_bar"));
      waitForIdle();
    }
    UiObject uiobj;
    uiobj = new UiObject(new UiSelector()
        .resourceId("com.android.systemui:id/task_view_bar")
        .instance(0));
    String cname = uiobj.getClassName();
    System.out.println("Closing frame of type  "
        + cname
        + " at "
        + uiobj.getBounds().flattenToString());
    uiobj.swipeRight(100);
    waitForIdle();
  }

  // intent-based launch using package name
  private void launchApp_pkg(String packageName) throws UiObjectNotFoundException {
    getUiDevice().pressHome();
    waitForIdle();
    handle_crash();

    try {
      String cmd = "monkey -p " + packageName + " -c android.intent.category.LAUNCHER 1";
      System.out.println("Executing: " + cmd);
      Process pr = Runtime.getRuntime().exec(cmd);
      pr.waitFor();

      System.out.println("Launching " + packageName);
      waitForIdle();
    } catch (Exception e) {
      e.printStackTrace(System.out);
    }
  }

  private String getCurrentAppPackageName()  {
    return UiDevice.getInstance().getCurrentPackageName();
  }

  private void addEditables(UiCollection root, LinkedList sb, HashMap<Integer, String> hashToPath) throws UiObjectNotFoundException {
    int count = root.getChildCount(new UiSelector()
        .classNameMatches("android.widget.EditText")
        .clickable(true)
        .checkable(false)
        .enabled(true));

    for (int i = 0; i < count; i++) {
      UiObject uiobj = new UiObject(new UiSelector()
          .classNameMatches("android.widget.EditText")
          .clickable(true)
          .checkable(false)
          .enabled(true)
          .instance(i));

      AccessibilityNodeInfo info = UiUtil.getExtraInfo(uiobj);
      int hash = info.hashCode();
      info.recycle();

      String path = hashToPath.get(hash);
      if (path == null) path ="0";

      sb.addLast("edit"
          + ":"
          + uiobj.getClassName()
          + ":"
          + hash
          + ":"
          + path
          + ":"
          + i);
    }
  }

  private void addClickables(UiCollection root, LinkedList sb, HashMap<Integer, String> hashToPath) throws UiObjectNotFoundException {
    int count = root.getChildCount(new UiSelector()
        .clickable(true)
        .checkable(false)
        .enabled(true));

    for (int i = 0; i < count; i++) {
      UiObject uiobj = new UiObject(new UiSelector()
          .clickable(true)
          .checkable(false)
          .enabled(true)
          .instance(i));

      AccessibilityNodeInfo info = UiUtil.getExtraInfo(uiobj);
      int hash = info.hashCode();
      info.recycle();

      String path = hashToPath.get(hash);
      if (path == null) path ="0";

      sb.addLast("click"
          + ":"
          + uiobj.getClassName()
          + ":"
          + hash
          + ":"
          + path
          + ":"
          + i);
    }
  }

  private void addLongClickables(UiCollection root, LinkedList sb, HashMap<Integer, String> hashToPath) throws UiObjectNotFoundException {
    int count = root.getChildCount(new UiSelector()
        .longClickable(true)
        .checkable(false)
        .enabled(true));

    for (int i = 0; i < count; i++) {
      UiObject uiobj = new UiObject(new UiSelector()
          .longClickable(true)
          .checkable(false)
          .enabled(true)
          .instance(i));

      AccessibilityNodeInfo info = UiUtil.getExtraInfo(uiobj);
      int hash = info.hashCode();
      info.recycle();

      String path = hashToPath.get(hash);
      if (path == null) path ="0";

      sb.addLast("long"
          + ":"
          + uiobj.getClassName()
          + ":"
          + hash
          + ":"
          + path
          + ":"
          + i);
    }

  }

  private void addCheckables(UiCollection root, LinkedList sb, HashMap<Integer, String> hashToPath) throws UiObjectNotFoundException {
    int count = root.getChildCount(new UiSelector()
        .checkable(true)
        .enabled(true));

    for (int i = 0; i < count; i++) {
      UiObject uiobj = new UiObject(new UiSelector()
          .checkable(true)
          .enabled(true)
          .instance(i));

      AccessibilityNodeInfo info = UiUtil.getExtraInfo(uiobj);
      int hash = info.hashCode();
      info.recycle();

      String path = hashToPath.get(hash);
      if (path == null) path ="0";

      sb.addLast("check"
          + ":"
          + uiobj.getClassName()
          + ":"
          + hash
          + ":"
          + path
          + ":"
          + i
          + ":"
          + uiobj.isChecked());
    }
  }

  private void addScrollables(UiCollection root, LinkedList sb, HashMap<Integer, String> hashToPath) throws UiObjectNotFoundException {
    int count = root.getChildCount(new UiSelector()
        .scrollable(true)
        .enabled(true));

    for (int i = 0; i < count; i++) {
      UiObject uiobj = new UiObject(new UiSelector()
          .scrollable(true)
          .enabled(true)
          .instance(i));

      if (uiobj.getClassName().equals("android.widget.Spinner")) continue;

      AccessibilityNodeInfo info = UiUtil.getExtraInfo(uiobj);
      int hash = info.hashCode();
      info.recycle();

      String path = hashToPath.get(hash);
      if (path == null) path ="0";

      sb.addLast("scroll"
          + ":"
          + uiobj.getClassName()
          + ":"
          + hash
          + ":"
          + path
          + ":"
          + i
          + ":"
          + 0);

      sb.addLast("scroll"
          + ":"
          + uiobj.getClassName()
          + ":"
          + hash
          + ":"
          + path
          + ":"
          + i
          + ":"
          + 1);
    }
  }

  private String encode(String s) {
    return s.replaceAll(":","&#58;");
  }

  private void getAbstractUIState(LinkedList<String> elist, boolean hasEditText, boolean hasScrollables, HashMap<Integer, String> hashToPath) throws UiObjectNotFoundException {
    if (!hasEditText && !hasScrollables) return;

    LinkedList<String> elistNew = new LinkedList<String>();

    int attempts = 1;
    while (true) {
      try {
        UiCollection root = getRootSet();
        if (hasEditText) addEditables(root, elistNew, hashToPath);
        if (hasScrollables) addScrollables(root, elistNew, hashToPath);
        elist.addAll(elistNew);
        return;

      } catch (Exception e) {
        attempts++;
        elistNew.clear();
        System.out.println("-----------------------");
        if (attempts > 2) {
          System.out.println("!!!!!!!!!!!!! Needs attention !!!!!!!!!");
          e.printStackTrace(System.out);
        }
        System.out.println("----------------------- Trying to retrieve UI State again: attempt number " + attempts);
        waitForIdle();
      }
    }
  }

  private void clickAction(String instance) throws UiObjectNotFoundException {
    int i = Integer.parseInt(instance);
    UiObject uiobj = new UiObject(new UiSelector()
        .clickable(true)
        .checkable(false)
        .enabled(true)
        .instance(i));

    String cname = uiobj.getClassName();
    System.out.println("Clicking "
        + cname
        + " at "
        + uiobj.getBounds().flattenToString());
    uiobj.click();
    waitForIdle();
  }

  private void editAction(String instance) throws UiObjectNotFoundException {
    int i = Integer.parseInt(instance);
    UiObject uiobj = new UiObject(new UiSelector()
            .classNameMatches("android.widget.EditText")
            .clickable(true)
            .checkable(false)
            .enabled(true)
            .instance(i));

    String cname = uiobj.getClassName();
    System.out.println("Clicking "
            + cname
            + " at "
            + uiobj.getBounds().flattenToString());
    // uiobj.click();
    // waitForIdle();

    uiobj.setText("random text");
    waitForIdle();
  }

  private void dclickAction(String xs, String ys, String cname) {
    int x = Integer.valueOf(xs);
    int y = Integer.valueOf(ys);

    System.out.println("Clicking " + cname + " at " + x + ":" + y);
    UiDevice.getInstance().click(x,y);
    waitForIdle();
  }

  private void dlongAction(String xs, String ys, String cname) {
    int x = Integer.valueOf(xs);
    int y = Integer.valueOf(ys);

    System.out.println("Long Clicking " + cname + " at " + x + ":" + y);
    UiDevice.getInstance().swipe(x,y,x,y,100);
    waitForIdle();
  }

  private void dcheckAction(String xs, String ys, String cname) {
    int x = Integer.valueOf(xs);
    int y = Integer.valueOf(ys);

    System.out.println("Checking " + cname + " at " + x + ":" + y);
    UiDevice.getInstance().click(x,y);
    waitForIdle();
  }

  private void longClickAction(String instance) throws UiObjectNotFoundException {
    int i = Integer.parseInt(instance);
    UiObject uiobj = new UiObject(new UiSelector()
        .longClickable(true)
        .checkable(false)
        .enabled(true)
        .instance(i));

    System.out.println("Long clicking "
        + uiobj.getClassName()
        + " at "
        + uiobj.getBounds().flattenToString());
    uiobj.longClick();
    waitForIdle();
  }

  private void checkAction(String instance) throws UiObjectNotFoundException {
    int i = Integer.parseInt(instance);
    UiObject uiobj = new UiObject(new UiSelector()
        //.classNameMatches(".*")
        .checkable(true)
        .enabled(true)
        .instance(i));
    System.out.println("Checking "
        + uiobj.getClassName()
        + " at "
        + uiobj.getBounds().flattenToString());
    uiobj.click();
    waitForIdle();
  }

  private void scrollAction(String instance, String upOrDown) throws UiObjectNotFoundException {
    int i = Integer.parseInt(instance);
    int ud = Integer.parseInt(upOrDown);
    UiScrollable uiobj = new UiScrollable(new UiSelector()
        //.classNameMatches(".*")
        .scrollable(true)
        .enabled(true)
        .instance(i));
    System.out.println("Scrolling "
        + uiobj.getClassName()
        + " at "
        + uiobj.getBounds().flattenToString());
    if (ud == 0) {
      uiobj.scrollForward();
    } else {
      uiobj.scrollBackward();
    }
    waitForIdle();
  }

  private void triggerEvent(String event) throws UiObjectNotFoundException {
    try {
      for (int i = 0; i < actions.length; i++) {
        ActionPair action = actions[i];
        if (event.equals(action.actionName)) {
          action.action.action(); // I know that this looks funny and annoying
        }
      }

      String[] components = event.split(":");
      if (components[0].equals("edit")) {
        editAction(components[4]);
      } else if (components[0].equals("long")) {
        longClickAction(components[4]);
      } else if (components[0].equals("check")) {
        checkAction(components[4]);
      } else if (components[0].equals("scroll")) {
        scrollAction(components[4], components[5]);
      } else if (components[0].equals("launch") && components[1].equals("pkg")) {
        launchApp_pkg(components[2]);
      } else if (components[0].equals("closeapp") && components[1].equals("pm")) {
        closeApp_pm(components[2]);
      } else if (components[0].equals("closeapp") && components[1].equals("am")) {
        closeApp_am(components[2]);
        // Do not kill everything.
        // Need to run "adb shell pm force-stop" from outside of the machine
      } else if (components[0].equals("wait")) {
        System.out.println("wait");
        waitForIdle();
      } else if (components[0].equals("dclick")) {
        dclickAction(components[4], components[5], components[1]);
      } else if (components[0].equals("dlong")) {
        dlongAction(components[4], components[5], components[1]);
      } else if (components[0].equals("dcheck")) {
        dcheckAction(components[4], components[5], components[1]);
      } else if (components[0].equals("nop")) {
        int x = 1; // nop
      }
    } catch (Exception e) {
      System.out.flush();
      System.out.println("----------- Ignore the following exception! ------------");
      e.printStackTrace(System.out);
      System.out.println("-----------------------");
      System.out.flush();
    }
  }

  private UiCollection getRootSet() {
    UiCollection collection;
    collection = new UiCollection(new UiSelector().classNameMatches(".*"));
    return collection;
  }

  private AccessibilityNodeInfo getRootNode( ){
    try {
      Class cls = getUiDevice().getClass();
      Method m = cls.getDeclaredMethod("findObject", UiSelector.class);
      m.setAccessible(true);

      UiObject result = (UiObject) m.invoke(getUiDevice(), new UiSelector().classNameMatches(".*"));
      return UiUtil.getExtraInfo(result);
    }
    catch (Exception e) {
      e.printStackTrace(System.out);
      throw new RuntimeException(e);
    }
  }

  private void sendTiming(PrintWriter out) {
    for (String key: watch.getKeySet()) {
      out.println(key + ":" + watch.getTime(key));
    }
    out.println("end");
  }

  public void testDemo() throws UiObjectNotFoundException, IOException {
    ServerSocket driverServerSocket = new ServerSocket(Constants.CONTROL_SERVER_PORT);
    logServer.start();

    boolean loop = true;
    while (loop) {
      // each iteration represents a client
      Socket driverClientSocket = driverServerSocket.accept();
      PrintWriter out =
          new PrintWriter(driverClientSocket.getOutputStream(), true);
      BufferedReader in = new BufferedReader(
          new InputStreamReader(driverClientSocket.getInputStream()));

      while (true) {
        String cmd = in.readLine();
        System.out.println("-------------- cmd: " + cmd + " --------------");
        if (cmd.equals("ping"))
          out.println("pong");
        else if (cmd.equals("heartbeat")) {
          System.out.println("============= Heart Beat =============");
          String heartbeat = in.readLine();                   // <-- RECEIVE
          System.out.println("heart beat:" + heartbeat);
        }
        else if (cmd.equals("timing")) {
          // send timing info
          sendTiming(out);
          watch = new StopWatch();
        }
        else if (cmd.equals("data")) {
          String packageName;
          String guiSummaryString;
          LinkedList<String> elist;

          // collect app state
          {
            watch.begin("collect");
            elist = new LinkedList();

            System.out.println("Collecting information");
            packageName = getCurrentAppPackageName();
            System.out.println("package:" + packageName);

            //add default events
            for (int i = 0; i < actions.length; i++) {
              ActionPair action = actions[i];
              elist.addLast(action.actionName);
            }

            watch.begin("collect:summary");
            //SummarizingVisitor summarizingVisitor = new SummarizingVisitor();
            //EventCollectingVisitor eventCollectingVisitor = new EventCollectingVisitor(elist, "0");

            HashMap<Integer, String> hashToPath = new HashMap<Integer, String>();
            LinkedList<UIVisitor> chain = new LinkedList<UIVisitor>();
            SummarizingVisitor sv = new SummarizingVisitor();
            EventCollectingVisitor ev = new EventCollectingVisitor(elist, "0", hashToPath);
            chain.add(sv);
            chain.add(ev);
            UIVisitorChain visitor = new UIVisitorChain(chain);
            UiTraverse.traverseUI(getRootSet(), visitor);

            JSONObject guiSummary = sv.getSummary();
            guiSummaryString = (guiSummary == null) ? "{}" : guiSummary.toString();
            watch.end("collect:summary");

            // rest of ui state
            watch.begin("collect:elist");
            getAbstractUIState(elist, ev.hasEditText, ev.hasScrollable, hashToPath);
            watch.end("collect:elist");
            watch.end("collect");
          }
          // send ui state
          {
            watch.begin("send state");
            out.println(packageName);                           // <-- SEND
            out.println(encode(guiSummaryString));              // <-- SEND

            for (Object line : elist) {
              out.println(line);                                // <-- SEND
            }
            out.println("end");                                 // <-- SEND

            // send log messages
            LinkedList<String> messages = logServer.getMessages();
            if (!messages.isEmpty()) {
              miniwait();
              LinkedList<String> furtherMessages = logServer.getMessages();
              if (!furtherMessages.isEmpty()) {
                messages.addAll(furtherMessages);
              }
            }

            if (close_requested) {
              logServer.kill();
              close_requested = false;
            }

            for (String s : messages) {
              System.out.println(s);
              out.println(s);                                   // <-- SEND
            }
            out.println("end");                                 // <-- SEND
            watch.end("send state");
          }
        }
        else if (cmd.equals("event")) {
          // perform an action
          {
            watch.begin("action");

            // get event
            watch.begin("action:receive");
            String event = in.readLine();                       // <-- RECEIVE
            watch.end("action:receive");

            watch.begin("action:execution");
            if (event == null || event.equals("end")) {
              logServer.kill();
              System.out.println("break:" + event);
              break;
            }
            // trigger event
            System.out.println(event);
            triggerEvent(event);
            watch.end("action:execution");
            watch.end("action");
          }
        }
        else if (cmd.equals("shutdown")) {
          logServer.shutdown();
          loop = false;
          break;
        }
        else {
          System.out.println("undefined cmd!");
        }
      }
      driverClientSocket.close();
    }
    driverServerSocket.close();
  }
}

