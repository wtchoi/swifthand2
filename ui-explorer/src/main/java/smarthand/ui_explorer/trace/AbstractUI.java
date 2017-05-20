package smarthand.ui_explorer.trace;

import com.sun.org.apache.xpath.internal.operations.Bool;
import org.json.JSONArray;
import org.json.JSONObject;
import smarthand.ui_explorer.*;
import smarthand.ui_explorer.util.HashConsed;
import smarthand.ui_explorer.util.HashConsingFactory;
import smarthand.ui_explorer.util.Util;

import java.util.*;

/**
 * Created by wtchoi on 3/7/16.
 * Hash-consed. Can be compared with equality check.
 */
public class AbstractUI extends HashConsed implements Comparable<AbstractUI>  {
    private static HashConsingFactory<AbstractUI> factory = new HashConsingFactory<>(
        AbstractUI::checkEqual,
        new HashConsingFactory.InstanceDumper<AbstractUI>(Options.get(Options.Keys.IMAGE_OUTPUT_DIR), "abstractUI") {
            @Override
            protected String getString(AbstractUI e) {
                return e.key == null ? "blocked" : e.key;
            }
        });

    private static AbstractUI failState = getState(null, null, null);

    static boolean takeScreenshot = true;

    final List<String> elist;
    final String activityName;
    final Boolean isKeyboardShown;

    final String key;
    final String tooltip;

    final List<EventInfo> eInfo;

    Integer snapshotID = -1;

    private AbstractUI(LinkedList<String> elist, Boolean isKeyboardShown, List<EventInfo> eInfo, String activityName, String key, String tooltip) {
        super(Util.chainHash(0, key));
        this.elist = (elist != null) ? elist : new LinkedList();
        this.eInfo = eInfo;
        this.key = key;
        this.tooltip = tooltip;
        this.activityName = activityName;
        this.isKeyboardShown = isKeyboardShown;
    }

    private static boolean checkEqual(Object s1, Object s2) {
        if (!(s1 instanceof AbstractUI) || !(s1 instanceof AbstractUI)) {
            return s1.equals(s2);
        }
        AbstractUI u1 = (AbstractUI) s1;
        AbstractUI u2 = (AbstractUI) s2;
        if (!u1.key.equals(u2.key)) return false;
        if (!Util.equalsNullable(u1.activityName, u2.activityName)) return false;
        return true;
    }

    public String getKey() { return key; }

    public Collection<String> getEvents() {
        return elist;
    }

    public List<EventInfo> getEventsInfo() { return eInfo; }

    public String getEvent(int index) {
        return elist.get(index);
    }

    public Integer getEventIndex(String targetEv) {
        int index = 0;
        for (String ev: elist) {
            if (ev.equals(targetEv)) return index;
            index++;
        }
        return -1; // error return
    }

    public Integer getEventCount() { return elist.size(); }

    public String getTooltip() { return tooltip; }

    public String getActivityName() { return activityName; }

    public Integer getSnapshotID() { return snapshotID; }

    public boolean isFailState() {
        return this == failState;
    }

    public static AbstractUI getStateById(Integer id) {
        AbstractUI state = factory.getById(id);
        if (state == null) {
            throw new RuntimeException("Cannot find UI state " + id);
        }
        return state;
    }

    public static AbstractUI getState(String activityName, Boolean isKeyboardShown, LinkedList<String> elist) {
        return getState(activityName, isKeyboardShown, elist, null);
    }

    public static AbstractUI getState(String activityName, Boolean isKeyboardShown,  LinkedList<String> elist, LinkedList<EventInfo> info) {
        if (elist == null) {
            LinkedList<String> failed = new LinkedList<>();
            failed.addLast("close");
            return factory.getInstance(new AbstractUI(failed, null, null, null, null, null));
        }

        StringBuilder keyBuilder = new StringBuilder();
        StringBuilder tooltipBuilder = new StringBuilder();

        LinkedList<String> sselist = UIAbstractor.simplifySimplifiedEvents(elist);
        UIAbstractor.computeAbstraction(activityName, isKeyboardShown, sselist, keyBuilder, tooltipBuilder);
        String key = keyBuilder.toString();
        String tooltip = tooltipBuilder.toString();

        AbstractUI state = factory.getInstance(new AbstractUI(elist, isKeyboardShown, info, activityName, key, tooltip));

        if (state.snapshotID == -1) {
            if (takeScreenshot) {
                HistoryManager.instance().takeSnapshot();
                state.snapshotID = HistoryManager.instance().getCurrentPeriod();
            }
        }
        return state;
    }

    // create an abstract-ui using activity name and simplified event vector
    public static AbstractUI fromJson(JSONObject obj) {
        StringBuilder keyBuilder = new StringBuilder();
        StringBuilder tooltipBuilder = new StringBuilder();

        LinkedList<String> elist = new LinkedList<>();
        LinkedList<EventInfo> info = new LinkedList<>(); //TODO

        JSONArray events = obj.getJSONArray("enabledEvents");
        String activityName = obj.optString("activity", null);
        Boolean isKeyboardShown = obj.has("isKeyboardShown") ? obj.getBoolean("isKeyboardShown") : null;

        for (int i=0; i<events.length(); i++) {
            elist.addLast(events.getString(i));
        }

        if (events.length() == 1 && isKeyboardShown == null && activityName == null) {
            return AbstractUI.getFailState();
        }

        LinkedList<String> sselist = UIAbstractor.simplifySimplifiedEvents(elist);
        UIAbstractor.computeAbstraction(activityName, isKeyboardShown, sselist, keyBuilder, tooltipBuilder);
        String key = keyBuilder.toString();
        String tooltip = tooltipBuilder.toString();

        AbstractUI state = factory.getInstance(new AbstractUI(elist, isKeyboardShown, info, activityName, key, tooltip));

        return state;
    }

    // Dump event vector of the abstract ui to json file.
    public JSONObject exportEventsToJson() {
        JSONObject uiObj = new JSONObject();
        JSONArray eventsObj = new JSONArray();
        getEvents().forEach((evStr) -> { eventsObj.put(evStr); });

        uiObj.put("id", this.id());
        uiObj.put("activity", this.activityName);
        uiObj.put("isKeyboardShown", this.isKeyboardShown);
        uiObj.put("enabledEvents", eventsObj);
        return uiObj;
    }

    // Return the number of distinct ui object created so far.
    public static int count() {
        return factory.count();
    }

    @Override
    public int compareTo(AbstractUI state) {
        assert state != null;
        return Integer.compare(id(), state.id());
    }

    public static AbstractUI getFailState() {
        if (failState == null) {
            failState = getState(null, null, null, null);
        }
        return failState;
    }

    public static JSONArray dumpAbstractionsToJson() {
        JSONArray result = new JSONArray();

        for (AbstractUI state: factory.getInstances()) {
            JSONObject stateInfo = new JSONObject();
            JSONArray eventArr = new JSONArray();
            JSONArray eventInfoArr = new JSONArray();
            for (int i=0; i<state.elist.size(); i++) {
                eventArr.put(state.elist.get(i));
                if (!state.isFailState()) {
                    eventInfoArr.put(EventInfo.dumpToJson(state.eInfo.get(i)));
                }
            }
            stateInfo.put("elist", eventArr);
            stateInfo.put("einfo", eventInfoArr);
            stateInfo.put("activity", state.activityName);
            stateInfo.put("isKeyboardShown", state.isKeyboardShown);
            result.put(stateInfo);
        }

        return result;
    }

    /*
     * Assume that AbstractUI is currently empty
     */
    public static void loadAbstractionsFromJson(JSONArray arr) {
        boolean takeScreenshotOld = takeScreenshot;
        takeScreenshot = false;

        for (int i=0; i<arr.length(); i++) {
            JSONObject stateInfo = arr.getJSONObject(i);
            String activity = stateInfo.optString("activity");
            Boolean isKeyboardShown = stateInfo.has("isKeyboardShown") ? stateInfo.optBoolean("isKeyboardShown"): null;

            JSONArray eventArr = stateInfo.getJSONArray("elist");
            LinkedList<String> eList = new LinkedList<>();
            for (int j=0; j<eventArr.length(); j++) {
                eList.add(eventArr.getString(j));
            }

            if (eList.size() == 1) continue; //skip fail state

            JSONArray eventInfoArr = stateInfo.getJSONArray("einfo");
            LinkedList<EventInfo> einfoList = new LinkedList<>();
            for (int j=0; j<eventInfoArr.length(); j++) {
                einfoList.add(EventInfo.loadJson(eventInfoArr.getJSONObject(j)));
            }

            AbstractUI state = AbstractUI.getState(activity, isKeyboardShown, eList, einfoList);
        }

        takeScreenshot = takeScreenshotOld;
    }
}
