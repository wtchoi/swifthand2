package smarthand.ui_explorer;

import org.json.JSONArray;
import org.json.JSONObject;
import smarthand.ui_explorer.strategy.*;
import smarthand.ui_explorer.trace.Coverage;
import smarthand.ui_explorer.util.Util;

import java.io.*;
import java.util.LinkedList;

public class Client implements Logger {
  enum ResetMethod {
    UNINSTALL, FORCE_STOP, CLEAR_DATA;
  }

  Strategy strategy;
  int blockingCount = 0;

  CoverageManager coverageManager;
  UiDriverBridge uiBridge;

  ApkInfo targetApkInfo;
  DeviceDriver deviceDriver;

  ResetMethod resetMethod = ResetMethod.FORCE_STOP;

  Coverage coverageSinceLastReport = new Coverage();

  public void setTarget(ApkInfo info) {
    this.targetApkInfo = info;
  }

  public void setDeviceDriver(DeviceDriver dd) {
    this.deviceDriver = dd;
    dd.setLogger(this);
  }

  private boolean hasEmptyWebview(JSONObject obj) {
    if (obj.getString("class").equals("android.webkit.WebView")){
      return !obj.has("children");
    }

    if (obj.has("children")) {
      JSONArray children = obj.getJSONArray("children");
      for (int i= 0; i < children.length(); i++) {
        if(hasEmptyWebview(children.getJSONObject(i))) return true;
      }
    }
    return false;
  }

  private void getTimingFromDevice() throws IOException {
    LinkedList<String> data = uiBridge.getTimingFromDevice();
    for (String entry: data) {
      int lastIndex = entry.lastIndexOf(":");
      String value = entry.substring(lastIndex + 1, entry.length());
      String key = entry.substring(0, lastIndex);
      log("Time: " + entry);
      HistoryManager.instance().collectValue("Time:" + key, Long.valueOf(value));
    }
  }

  private void resetApp(ApkInfo targetApkInfo) throws IOException {
    try {
      if (resetMethod == ResetMethod.UNINSTALL) {
        deviceDriver.uninstallApp(targetApkInfo);
        Util.sleep(300);
        deviceDriver.clearSdCard();
        deviceDriver.prepareSdCardForApp(targetApkInfo);
        deviceDriver.installApp(targetApkInfo);
        Util.sleep(300);
      } else if (resetMethod == ResetMethod.FORCE_STOP) {
        deviceDriver.forceStopApp(targetApkInfo);
        deviceDriver.clearAppData(targetApkInfo);
        deviceDriver.clearSdCard();
        deviceDriver.prepareSdCardForApp(targetApkInfo);
      } else {
        deviceDriver.clearAppData(targetApkInfo);
        deviceDriver.clearSdCard();
        deviceDriver.prepareSdCardForApp(targetApkInfo);
      }
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public void infiniteTest(int N, String launchMode) throws IOException {
    if (strategy.requiresAutoRestart()){
      strategy = new AutoRestartAdapter(strategy);
    }

    int port = Integer.parseInt(Options.get(Options.Keys.PORT));
    uiBridge.start(15000, port);
    int waitCounter = 0;

    boolean explicitCloseRequested = false;
    boolean escaped = false;
    int counterAfterLastReset = 0;

    HistoryManager.instance().begin();
    HistoryManager.instance().startNextPeriod();
    HistoryManager.instance().setDecision("initiate testing");

    long timeout = Long.parseLong(Options.get(Options.Keys.TIMEOUT)) * 1000;

    deviceDriver.wakeUpDevice();

    for (int i=0; i<N; i++, counterAfterLastReset++) {
      log("============================");
      log("iter = " + i);

      int commandLimit = Integer.parseInt(Options.get(Options.Keys.COMMAND_LIMIT));
      if (commandLimit != 0 && HistoryManager.instance().getCurrentPeriod() >= commandLimit) {
        log("Terminate: experiment finished (command limit reached)");
        break;
      }
      System.out.println("Timeout:" + timeout);
      System.out.println("Elapsed:" + HistoryManager.instance().getElapsedTime());

      if (HistoryManager.instance().getElapsedTime() >= timeout) {
        log("Terminate: experiment finished (timeout)");
        break;
      }

      // send heartbeat
      uiBridge.sendHeartBeat(i);

      // get ui data
      DeviceInfo data = uiBridge.getDataFromDevice(deviceDriver, targetApkInfo);

      // get profiling info
      getTimingFromDevice();

      // get coverage info
      coverageManager.addCoverage(data.coveredMethods, data.coveredBranches);
      coverageSinceLastReport.add(data.coveredMethods, data.coveredBranches);
      if (coverageManager.existsLatestBranchDelta()) {
        String bDelta = Util.makeIntSetToString(coverageManager.getLatestBranchDelta(), ",", null).toString();
        HistoryManager.instance().periodStat("Client:BranchCoverageDelta", bDelta);
      }
      if (coverageManager.existsLatestMethodDelta()) {
        String mDelta = Util.makeIntSetToString(coverageManager.getLatestMethodDelta(), ",", null).toString();
        HistoryManager.instance().periodStat("Client:MethodCoverageDelta", mDelta);
      }

      // You received an empty event set. Something is wrong with event collection.
      // By default, the event set should contain at least two default events.
      if (data.events.isEmpty()) {
        log("ERROR: Received empty event list.");
        break;
      }

      // initial launch
      if (i == 0) {
        resetApp(targetApkInfo);
        explicitCloseRequested = true;
      }

      escaped = !targetApkInfo.appPackage.equals(data.appPackageName);
      if (escaped && data.appPackageName.equals("com.google.android.packageinstaller")) {
          if (data.filteredEvents.size() == 5 || data.filteredEvents.size() == 4) {
              //This is the permission screen.
              escaped = false;
          }
      }
      if (escaped && data.appPackageName.equals("null")) {
        escaped = false;
      }


      boolean blocked = false;

      // a different app is showing
      if(escaped) {
        waitCounter = 0;
        log("Escaped!");
        log("Current Package:" + data.appPackageName);
        log("" + data.filteredEvents.size());

        if (counterAfterLastReset < 1 && !explicitCloseRequested) {
          log("Launch Target Package :" + launchMode + ":" + targetApkInfo.appPackage);
          uiBridge.sendEvent("launch:" + launchMode + ":" + targetApkInfo.appPackage);
          Util.sleep(1000);
          getTimingFromDevice();
          continue;
        }
        else if (explicitCloseRequested) {
          explicitCloseRequested = false;
        }
      }
      else {
        //blocked = (data.events.size() == 3 && data.appGuiTree.length() != 0);
        blocked = (data.events.size() == 3);

        if (blocked && waitCounter < 20) {
          // handling a possibly blocking state
          waitCounter++;
          log("Blocking State: wait a while : " + (waitCounter + 1));
          Util.sleep(1000);
          uiBridge.sendEvent("wait");
          getTimingFromDevice();
          continue;
        } else {
          if (blocked) {
            blockingCount++;
            log("Blocking State: accept blocking state");
          } else if (waitCounter != 0) {
            log("Blocking State: escaped");
          }
        }
      }

      waitCounter = 0;
      HistoryManager.instance().actionPerformed();

      strategy.reportExecution(data, coverageSinceLastReport, escaped, blocked);
      coverageSinceLastReport = new Coverage();

      strategy.intermediateDump(HistoryManager.instance().getCurrentPeriod());
      HistoryManager.instance().periodStat("Client:#Block", blockingCount);
      HistoryManager.instance().periodStat("Client:MethodCoverage", coverageManager.getMethodCoverage());
      HistoryManager.instance().periodStat("Client:BranchCoverage", coverageManager.getBranchCoverage());
      HistoryManager.instance().periodStat("Client:MBCoverage", coverageManager.getMBCoverage());
      HistoryManager.instance().informationGathered();
      HistoryManager.instance().finishCurrentPeriod();
      HistoryManager.instance().startNextPeriod();

      String action = strategy.getNextAction();
      log("action returned: " + action);

      HistoryManager.instance().setDecision(action);

      if (action == null) {
        log("Terminate: received null command from the strategy.");
        break;
      }

      if (action.equals("finish")) {
        log("Terminate: experiment finished (requested)");
        break;
      }
      else if (action.equals("reset")) {
        log("Reset:Closing the app");
        log("Current Package:" + data.appPackageName);
        log("Close Target Package :" + launchMode + ":" + targetApkInfo.appPackage);
        uiBridge.sendEvent("closeapp:pm:" + targetApkInfo.appPackage);
        Util.sleep(500);
        resetApp(targetApkInfo);
        uiBridge.sendEvent("launch:" + launchMode + ":" + targetApkInfo.appPackage);
        Util.sleep(15000);
        getTimingFromDevice();
        counterAfterLastReset = 0;
      }
      else if (action.equals("start")) {
        log("Start:Starting the app");
        uiBridge.sendEvent("launch:" + launchMode + ":" + targetApkInfo.appPackage);
        Util.sleep(2000);
        getTimingFromDevice();
        counterAfterLastReset = 0;
      }
      else if (action.equals("close")) {
        log("Close:Closing the app");
        log("Current Package:" + data.appPackageName);
        log("Close Target Package :" + launchMode + ":" + targetApkInfo.appPackage);
        uiBridge.sendEvent("closeapp:pm:" + targetApkInfo.appPackage);
        Util.sleep(500);
        resetApp(targetApkInfo);
        Util.sleep(500);
        getTimingFromDevice();
        explicitCloseRequested = true;
      }
      else if (action.startsWith("monkey")) {
        log("Monkey");
        int monkeyTimeout = Integer.parseInt(action.split(":")[1]);
        int period = HistoryManager.instance().getCurrentPeriod();
        String monkeyFileName = Options.get(Options.Keys.OUTPUT_DIR) + "/image/monkey" + period;
        MonkeyManager.runRandomMonkey(monkeyTimeout, targetApkInfo.appPackage, monkeyFileName);
        uiBridge.sendEvent("nop");
        getTimingFromDevice();
      }
      else if (action.startsWith("cmonkey")) {
        log("Commanding Monkey");
        int period = HistoryManager.instance().getCurrentPeriod();
        String monkeyFileName = Options.get(Options.Keys.OUTPUT_DIR) + "/image/monkeyC" + period;

        uiBridge.stop();

        MonkeyManager.start("adb", Options.get(Options.Keys.DEVICE_NAME), targetApkInfo.appPackage, monkeyFileName, port + 1);
        MonkeyManager.sendBatchCommandsToMonkeyAndWaitToStop(action.substring(8)); // Skip 'cmonkey:'

        uiBridge.start(2000, port);
        deviceDriver.wakeUpDevice();
      }
      else if (action.startsWith("event")){
        String[] components = action.split(":");
        uiBridge.sendEvent(data.filteredRawEvents.get(Integer.parseInt(components[1])));
        getTimingFromDevice();
      }
      else {
        throw new RuntimeException("Something is wrong");
      }
    }
    uiBridge.sendEvent("end");
    HistoryManager.instance().actionPerformed();
    HistoryManager.instance().informationGathered();
    HistoryManager.instance().finishCurrentPeriod();
    HistoryManager.instance().end();

    try {
      deviceDriver.clearSdCard();
    }
    catch(InterruptedException e) {
      throw new RuntimeException(e);
    }

    coverageManager.dump();
    strategy.finalDump();
  }

  // update meta.txt with "note" field
  public void updateMetaData() {
    String filename = Options.get(Options.Keys.OUTPUT_DIR) + "/meta.txt";
    JSONObject meta = Util.readJsonFile(filename);
    String note = strategy.getDetailedExplanation();
    meta.put("note", note);
    Util.writeJsonFile(filename, meta);
  }

  public void log(String s) {
    System.out.println(s);
    HistoryManager.instance().log("Client", s);
  }

  public void log(String s, int depth) {
    String indent = "";
    for(int i=0;i<depth;i++) {
      indent += "\t";
    }

    System.out.println(indent + s);
    HistoryManager.instance().log("Client", indent + s);
  }
}
