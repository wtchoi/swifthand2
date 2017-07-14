package smarthand.ui_explorer;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.LinkedList;

/**
 * Created by wtchoi on 11/23/15.
 */
public class DeviceInfo {
  public String appPackageName = null;

  public String appGuiTreeString = null;
  public JSONObject appGuiTree;
  public boolean isKeyboardShown = false;

  public LinkedList<String> activityStack = new LinkedList();
  public String focusedActivity = null;

  LinkedList<String> events = new LinkedList();                     // raw event set from the device
  public LinkedList<String> filteredRawEvents = new LinkedList<>(); // filtered (contains less event)
  public LinkedList<String> filteredEvents = new LinkedList();      // filtered and simplified (event have less info)
  public LinkedList<EventInfo> eventInfo;

  public HashSet<Integer> coveredMethods = new HashSet<>();
  public HashSet<Integer> coveredBranches = new HashSet<>();

  public LinkedList<String> logcat;
  public int logcatHash;
}
