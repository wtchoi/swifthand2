package smarthand.ui_explorer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import smarthand.ui_driver.Constants;
import smarthand.ui_explorer.util.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.LinkedList;

/**
 * Created by wtchoi on 6/7/16.
 *
 * An interface class to control the application running on device from PC
 */
public class UiDriverBridge {

    private static UiDriverBridge inst;
    private static Logger logger;

    private BufferedReader in;
    private PrintWriter out;

    private static final int RETRY_INTERVAL = 500;

    private UiDriverBridge() { }

    public static UiDriverBridge getInstance() {
        if (inst == null) {
            inst = new UiDriverBridge();
        }
        return inst;
    }

    public static void setLogger(Logger logger) {
        UiDriverBridge.logger = logger;
    }

    // start driver
    public void start(long timeout, int port) {
        if (UIDriverManager.isRunning()) {
            throw new RuntimeException("ui driver is already running");
        }

        // System.out.printf("\t\tUIDriver is%s running.\n", UIDriverManager.isRunning() ? "" : " NOT");
        System.out.println("Starting UI Driver...");
        UIDriverManager.start("adb", Options.get(Options.Keys.DEVICE_NAME), "ui-driver-1.0-SNAPSHOT.dex.jar", Options.get(Options.Keys.OUTPUT_DIR) + "/log/driver.log", true);

        int trialMax = (int) timeout / RETRY_INTERVAL;
        if (timeout % RETRY_INTERVAL != 0) trialMax++;

        for (int i=0; i<trialMax; i++) {
            Util.sleep(RETRY_INTERVAL);
            // System.out.printf("\t\tUIDriver is%s running.\n", UIDriverManager.isRunning() ? "" : " NOT");
            if (UIDriverManager.isRunning()) break;
            System.out.println("UI Driver did not start. Retry.");
        }

        if (!UIDriverManager.isRunning()) {
            throw new RuntimeException("UIDriver did not start correctly.");
        }

        String ret;
        Socket kkSocket = null; //Constants.CONTROL_SERVER_PORT);

        for (int i=0; i<trialMax; i++) {
            Util.sleep(RETRY_INTERVAL);

            try {
                kkSocket = new Socket(Constants.hostName, port);
                out = new PrintWriter(kkSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(kkSocket.getInputStream()));

                out.println("ping");
                ret = in.readLine();
                if (ret == null || !ret.equals("pong"))
                    throw new IOException();

                // isRunning = true;
                System.out.println("UI Driver started and connected.");
                return;
            } catch (IOException e) {
                // e.printStackTrace();
                if (kkSocket != null) {
                    try {
                        kkSocket.close();
                    } catch (IOException e1) {
                        // e1.printStackTrace();
                    }
                    kkSocket = null;
                }
                System.out.println("Cannot connect to UI Driver. Retry.");
            }
        }

        throw new RuntimeException("Cannot connect to UI Driver before timeout.");
    }

    public void stop() {
        if (UIDriverManager.isRunning()) {
            out.println("shutdown");
            out.flush();
            Util.sleep(10);
            UIDriverManager.stop();
            Util.sleep(100);
            // isRunning = false;
        }
        else {
            throw new RuntimeException("driver is not running. cannot stop.");
        }
    }

    public LinkedList<String> getTimingFromDevice() throws IOException {
        LinkedList result = new LinkedList();
        String fromServer;
        out.println("timing");
        while (true) {
            fromServer = in.readLine();
            if (fromServer == null) throw new RuntimeException("Something is wrong!");
            if (fromServer.equals("end")) break;
            result.add(fromServer);
        }
        return result;
    }

    public DeviceInfo getDataFromDevice(DeviceDriver driver, ApkInfo target) throws IOException {
        String fromServer;
        DeviceInfo ret = new DeviceInfo();

        int uiComponentCounter = -1;
        int logCounter = 0;

        out.println("data");

        fromServer = in.readLine();
        ret.appPackageName = fromServer;
        log("Package : " + fromServer);

        try {
            fromServer = decode(in.readLine());
            ret.appGuiTreeString = fromServer;

            if (fromServer != null) {
                ret.appGuiTree = new JSONObject(fromServer);
                logGUITree(ret.appGuiTree, 0);
            }
        }
        catch(Exception e){
            log(fromServer);
            e.printStackTrace();
            throw new RuntimeException(e);
        }


        // Get enabled events
        log("Received events:");
        ret.events.addLast("close");
        while (true) {
            uiComponentCounter++;
            fromServer = in.readLine();
            if (fromServer == null) throw new RuntimeException("Something is wrong!");
            if (fromServer.equals("end")) {
                log(fromServer);
                break;
            }

            ret.events.addLast(fromServer);
            log(uiComponentCounter + ". " + fromServer);
        }

        // Get additional information from driver
        while (true) {
            fromServer = in.readLine();
            if (fromServer == null) throw new RuntimeException("Something is wrong!");
            logCounter++;

            if (fromServer.equals("end")) {
                log(fromServer);
                break;
            }
            else {
                String[] components = fromServer.split(":");
                if (components[0].equals("MethodCoverage")) {
                    String[] methodIdList = components[1].split(",");
                    for (String id : methodIdList) {
                        ret.coveredMethods.add(Integer.parseInt(id));
                    }
                }
                else if (components[0].equals("BranchCoverage")) {
                    String[] branchIdList = components[1].split(",");
                    for (String id : branchIdList) {
                        ret.coveredBranches.add(Integer.parseInt(id));
                    }
                }
            }
        }

        // Print activity stack
        try {
            ret.activityStack = driver.getActivityStack(target);
            log("Activity Stacks:");
            for(String s: ret.activityStack) { log(s); }
        }
        catch (InterruptedException e) {
            log("*****************************");
            log("Cannot get application info:");
            log(e.toString());
            log("*****************************");
        }

        // check whether a software keyboard is shown or not
        try {
            ret.isKeyboardShown = driver.isKeyboardOn();
            log("Keyboard is shown:" + ret.isKeyboardShown);
        }
        catch (InterruptedException e) {
            log("*****************************");
            log("Cannot get application info:");
            log(e.toString());
            log("*****************************");
        }


        // filter enabled events
        ret.filteredRawEvents = UIAbstractor.filter(ret.appGuiTree, ret.events);
        ret.filteredEvents = UIAbstractor.simplifyRawEvents(ret.filteredRawEvents);
        ret.eventInfo = UIAbstractor.analyzeEvents(ret.appGuiTree, ret.events);

        return ret;
    }

    private void logGUITree(JSONObject node, int indent) throws JSONException {
        if (node.length() == 0){
            log("no GUI tree summary");
            return;
        }

        String prefix = "";
        for(int i = 0; i < indent; i++) {
            prefix += "|\t";
        }

        String attributes = "#" + node.getInt("hash") + ", " +  node.getString("bound");
        if (node.has("checked") && node.getBoolean("checked")) attributes += ", checked";
        if (node.has("selected") && node.getBoolean("selected")) attributes += ", selected";
        if (node.has("focused") && node.getBoolean("focused")) attributes += ", focused";
        if (node.has("afocused") && node.getBoolean("afocused")) attributes += ", afocused";
        if (node.has("actionable") && node.getBoolean("actionable")) attributes += ", actionable";

        String message = prefix + node.getString("class") + "(" + attributes + ")";

        if (node.has("text")){
            String text = node.getString("text");
            if (text.length() > 10) text = text.substring(0, 9) + "...";
            message = message + "[" + text + "]";
        }
        log(message);

        if (node.has("children")) {
            JSONArray children = node.getJSONArray("children");
            for (int i=0; i<children.length(); i++) {
                logGUITree(children.getJSONObject(i), indent+1);
            }
        }
    }

    private String decode(String s) {
        if (s == null) return null;
        return s.replaceAll("&#58;", ":");
    }

    public void sendEvent(String command) {
        log("Sending:" + command);
        out.println("event");
        out.println(command);
    }

    public void sendHeartBeat(int iter) {
        out.println("heartbeat");
        out.println("client iter = " + iter);
    }

    private void log(String cmd) {
        if (logger == null) {
            System.out.println(cmd);
        }
        else {
            logger.log(cmd);
        }
    }
}
