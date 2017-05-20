package smarthand.ui_explorer;

import smarthand.ui_explorer.util.SubProcess;

/**
 * A singleton total manager for UI Driver.
 *
 * @author  Wenyu Wang
 * @version 1.0
 * @since   2016-05-26
 */
public class UIDriverManager {

    private static SubProcess mProcess = null;
    private static String mAdbCommand = "adb";
    private static String mDeviceName = "";
    private static final String CMD_UIAUTOMATOR = "uiautomator";

    public static boolean isRunning() {
        return mProcess != null && mProcess.isAlive();
    }

    public static void start(final String sAdbCommand,
                             final String sDeviceName,
                             final String sDriverFilename,
                             final String sOutputFilename,
                             final boolean appendLog) {
        if (isRunning())
            return;

        String cmd;

        // Kill UIAutomator if it is running.
        SubProcess.forceKill(sAdbCommand, sDeviceName, CMD_UIAUTOMATOR);

        // Launch UI Driver.
        cmd = String.format("%s -s %s shell %s runtest \"%s\" -c \"smarthand.ui_driver.SwiftHand\"",
                sAdbCommand, sDeviceName, CMD_UIAUTOMATOR, sDriverFilename);
        mProcess = SubProcess.execCommand(cmd, sOutputFilename, appendLog);

        mAdbCommand = sAdbCommand;
        mDeviceName = sDeviceName;
    }

    public static void stop() {
        if (!isRunning())
            return;

        // Stop UI Driver right now.
        mProcess.kill();
        mProcess = null;

        // Make sure it stops.
        SubProcess.forceKill(mAdbCommand, mDeviceName, CMD_UIAUTOMATOR);
    }

}
