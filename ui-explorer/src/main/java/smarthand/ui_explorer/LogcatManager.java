package smarthand.ui_explorer;

import org.json.JSONArray;
import org.json.JSONObject;
import smarthand.ui_explorer.util.SubProcess;
import smarthand.ui_explorer.util.Util;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Created by wtchoi on 6/19/17.
 */
public class LogcatManager {

    private static SubProcess mProcess = null;
    private static String mAdbCommand = "adb";
    private static String mDeviceName = "";
    private static String CMD_LOGCAT = "logcat";

    private static LinkedList<String> logs = new LinkedList<>();
    private static boolean closed = false;

    public static boolean isRunning() {
        return mProcess != null && mProcess.isAlive();
    }

    public static void start(final String sDeviceName) {
        start("adb",sDeviceName);
    }

    public static void start(final String sAdbCommand, final String sDeviceName) {
        if (isRunning())
            return;

        String cmd;

        // Kill UIAutomator if it is running.
        SubProcess.forceKill(sAdbCommand, sDeviceName, CMD_LOGCAT);

        // Launch UI Driver.
        cmd = String.format("%s -s %s logcat *:E", sAdbCommand, sDeviceName);

        mProcess = SubProcess.execCommand(cmd, new SubProcess.OutputAdopter() {
            @Override
            public void flush() { }

            @Override
            public void println(String s) {
                synchronized (mProcess) {
                    if (!closed) {
                        logs.addLast(s);
                    }
                }
            }

            @Override
            public void close() {
                synchronized (mProcess) {
                    closed = true;
                }
            }
        });

        mAdbCommand = sAdbCommand;
        mDeviceName = sDeviceName;
    }

    public static LinkedList<String> getLogs() {
        LinkedList<String> temp = new LinkedList<>();
        LinkedList<String> oldLog = null;

        synchronized (mProcess) {
            oldLog = logs;
            logs = temp;
        }

        return oldLog;
    }

    public static LinkedList<String> extractExceptionLogs(LinkedList<String> log) {
        LinkedList<String> filteredLog = new LinkedList<>();

        if (log.size() != 0) {
            for (String s: log) {
                if (s.contains("AndroidRuntime")) {
                    String tail = s.split("AndroidRuntime:")[1];
                    if (tail.contains("Process") && tail.contains("PID")) {
                        filteredLog.addLast(tail.split("PID:")[0]);
                    } else {
                        filteredLog.addLast(tail);
                    }
                }
            }
        }
        return filteredLog;
    }

    private static HashMap<String, Integer> exceptionDB = new HashMap<>();

    public static int registerExceptionLog(LinkedList<String> logs) {
        if (logs.size() == 0) { return 0; }

        StringBuffer buffer = new StringBuffer();
        for (String s: logs) {
            buffer.append(s);
        }

        String message = buffer.toString();
        if (!exceptionDB.containsKey(message)) {
            exceptionDB.put(message, message.hashCode());
        }
        return exceptionDB.get(message);
    }

    // report registered exception strings (this is not necessarily the list of exceptions raised while testing an app)
    public static HashMap<String, Integer> getExceptionTbl() {
        return new HashMap<>(exceptionDB);
    }


    public static void stop() {
        if (!isRunning())
            return;

        // Stop UI Driver right now.
        mProcess.kill();
        mProcess = null;

        // Make sure it stops.
        SubProcess.forceKill(mAdbCommand, mDeviceName, CMD_LOGCAT);
    }

    public static void dumpExceptionInfo(String filename) {
        JSONObject exceptionInfo = new JSONObject();
        JSONArray exceptionArr = new JSONArray();

        HashMap<String, Integer> exceptionDB = LogcatManager.getExceptionTbl();
        for (Map.Entry<String,Integer> e: exceptionDB.entrySet()) {
            JSONObject exception = new JSONObject();
            exception.put("exception", e.getKey());
            exception.put("hash", e.getValue());
            exceptionArr.put(exception);
        }

        exceptionInfo.put("exceptions", exceptionArr);
        Util.writeJsonFile(filename, exceptionInfo);
    }

}