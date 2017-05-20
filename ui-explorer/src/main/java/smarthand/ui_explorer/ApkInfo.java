package smarthand.ui_explorer;

/**
 * Created by wtchoi on 10/18/16.
 */
public class ApkInfo {
    public String apkPath;
    public String appPackage;
    public String mainActivity;

    public ApkInfo(String path, String pkg, String activity) {
        apkPath = path;
        appPackage = pkg;
        mainActivity = activity;
    }
}
