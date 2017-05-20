package smarthand.ui_explorer;

import org.json.JSONObject;
import smarthand.ui_explorer.util.Util;

import java.util.HashSet;
import java.util.LinkedList;

/**
 * Created by wtchoi on 6/5/16.
 */
public class EventInfo {
    public int indexInCluster;
    public LinkedList<Integer> accessPath; // an access path from the root ui component to the outer most ui component of the event.
    public HashSet<Integer> cluster;
    public HashSet<Integer> brothers;

    public static JSONObject dumpToJson(EventInfo info) {
        JSONObject result = new JSONObject();
        if (info.cluster != null) {
            result.put("cluster", Util.makeIntSetToJson(info.cluster));
            result.put("indexInCluster", info.indexInCluster);
        }

        if (info.brothers != null) {
            result.put("brothers", Util.makeIntSetToJson(info.brothers));
        }
        return result;
    }

    public static EventInfo loadJson(JSONObject info) {
        EventInfo result = new EventInfo();
        if (info.has("cluster")) {
            result.indexInCluster = info.getInt("indexInCluster");
            result.cluster = Util.makeJsonToIntSet(info.getJSONArray("cluster"));
        }

        if (info.has("brothers")) {
            result.brothers = Util.makeJsonToIntSet(info.getJSONArray("brothers"));
        }
        return result;
    }
}
