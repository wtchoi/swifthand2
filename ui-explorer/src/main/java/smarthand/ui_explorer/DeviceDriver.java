package smarthand.ui_explorer;

import smarthand.ui_explorer.util.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.util.LinkedList;

/**
 * Created by wtchoi on 10/18/16.
 */
public class DeviceDriver {
    String deviceID;
    Logger logger;

    public DeviceDriver(String deviceID) {
        this.deviceID = deviceID;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    private void log(String msg) {
        if (logger != null) {
            logger.log(msg);
        }
    }

    public void wakeUpDevice() {
        LinkedList<String> buffer = new LinkedList<>();
        String cmd = "adb -s " + deviceID + " shell input keyevent KEYCODE_WAKEUP";
        Util.executeShellCommand(cmd, buffer);
        buffer.forEach(x -> log(x));
    }

    public void installApp(ApkInfo apkInfo) {
        LinkedList<String> buffer = new LinkedList<>();
        String cmd = String.format("adb -s %s install %s", deviceID, apkInfo.apkPath);
        Util.executeShellCommand(cmd, buffer);
        buffer.forEach(x -> log(x));
    }

    public void uninstallApp(ApkInfo apkInfo) {
        LinkedList<String> buffer = new LinkedList<>();
        String cmd = String.format("adb -s %s uninstall %s", deviceID, apkInfo.appPackage);
        Util.executeShellCommand(cmd, buffer);
        buffer.forEach(x -> log(x));
    }

    // check is software keyboard is dispalyed on the device
    public boolean isKeyboardOn() throws InterruptedException, IOException {
        String androidHome = System.getenv("ANDROID_HOME");
        String cmd = androidHome + "/platform-tools/adb -s " + deviceID + " shell dumpsys input_method";
        Runtime rt = Runtime.getRuntime();
        Process pr = rt.exec(cmd);

        BufferedReader dumpsysIn = new BufferedReader(new InputStreamReader(pr.getInputStream()));
        String line;

        while((line=dumpsysIn.readLine()) != null) {
            if (line.contains("mInputShown=true")) return true;
        }
        pr.waitFor();
        return false;
    }

    // get the activity stack of the target application
    public LinkedList<String> getActivityStack(ApkInfo apkInfo) throws InterruptedException, IOException {
        String androidHome = System.getenv("ANDROID_HOME");
        String cmd = androidHome + "/platform-tools/adb -s " + deviceID+ " shell dumpsys activity package " + apkInfo.appPackage;
        Runtime rt = Runtime.getRuntime();
        Process pr = rt.exec(cmd);

        BufferedReader dumpsysIn = new BufferedReader(new InputStreamReader(pr.getInputStream()));
        LinkedList<String> list = new LinkedList<>();
        String line;

        while((line=dumpsysIn.readLine()) != null) {
            if (line.contains("Hist #")) {
                String activity = line.split("\\s+")[5];
                list.addFirst(activity);
            }
        }
        pr.waitFor();
        return list;
    }

    // get the currently focused activity (may or may not belong to the target app)
    public String getFocusedActivity() throws InterruptedException, IOException {
        String androidHome = System.getenv("ANDROID_HOME");
        String cmd = androidHome + "/platform-tools/adb -s " + deviceID+ " shell dumpsys activity";
        Runtime rt = Runtime.getRuntime();
        Process pr = rt.exec(cmd);

        BufferedReader dumpsysIn = new BufferedReader(new InputStreamReader(pr.getInputStream()));
        String result = null;
        String line;

        while((line=dumpsysIn.readLine()) != null) {
            if (line.contains("mFocusedActivity")) {
                String[] activity = line.split("\\s+");
                result = activity[4];
                break;
            }
        }
        pr.waitFor();
        return result;
    }

    // clear sd card contents
    public void clearSdCard() throws IOException, InterruptedException {
        String androidHome = System.getenv("ANDROID_HOME");

        {
            String cmd = androidHome + "/platform-tools/adb -s " + deviceID + " shell rm -rf /sdcard/*";
            log("Executing cmd: " + cmd);
            Runtime rt = Runtime.getRuntime();
            Process pr = rt.exec(cmd);
            pr.waitFor();
        }

        {
            String cmd = androidHome + "/platform-tools/adb -s " + deviceID + " shell rm /sdcard/.*";
            log("Executing cmd: " + cmd);
            Runtime rt = Runtime.getRuntime();
            Process pr = rt.exec(cmd);
            pr.waitFor();
        }
    }

    // clear app data
    public void clearAppData(ApkInfo targetApkInfo) throws IOException, InterruptedException {
        String androidHome = System.getenv("ANDROID_HOME");
        {
            String cmd = androidHome + "/platform-tools/adb -s " + deviceID + " shell pm clear " + targetApkInfo.appPackage;
            log("Executing cmd: " + cmd);
            Runtime rt = Runtime.getRuntime();
            Process pr = rt.exec(cmd);
            pr.waitFor();
        }
    }

    public void prepareSdCardForApp(ApkInfo targetApkInfo) throws IOException, InterruptedException {
        // prepare SD card contents depending on the apk and the type of phone
        String androidHome = System.getenv("ANDROID_HOME");
        {
            if (targetApkInfo.appPackage.equals("jp.gr.java_conf.hatalab.mnv")) {
                String cmd = androidHome + "/platform-tools/adb -s " + deviceID + " shell mkdir /sdcard/alt_autocycle";
                log("Executing cmd: " + cmd);
                Runtime rt = Runtime.getRuntime();
                Process pr = rt.exec(cmd);
                pr.waitFor();

                cmd = androidHome + "/platform-tools/adb -s " + deviceID + " shell mkdir /sdcard/android";
                log("Executing cmd: " + cmd);
                rt = Runtime.getRuntime();
                pr = rt.exec(cmd);
                pr.waitFor();
            }
        }
    }

    // force stop app
    public void forceStopApp(ApkInfo targetApkInfo) throws  IOException, InterruptedException {
        String androidHome = System.getenv("ANDROID_HOME");
        {
            String cmd = androidHome + "/platform-tools/adb -s " + deviceID + " shell am force-stop " + targetApkInfo.appPackage;
            log("Executing cmd: " + cmd);
            Runtime rt = Runtime.getRuntime();
            Process pr = rt.exec(cmd);
            pr.waitFor();
        }
    }
}
