package smarthand.ui_explorer;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

/**
 * Created by wtchoi on 3/7/16.
 */
public class UIAbstractor {

    static int listLimit = 0;

    public static void setListLimit(int limit) {
        if (limit < 0) throw new RuntimeException("list limit should be a positive number");
        limit = listLimit;
    }

    public static String simplifyRawEvent(String event, boolean useCheckbox, boolean usePath) {
        String[] lst = event.split(":");
        String se = "";

        for (int i=0;i<lst.length;i++) {
            if (i == 2) continue; // skipping hashcode

            if (!usePath) {
                if (i == 3) continue; //skipping ui-object path info
                if (lst[0].equals("scroll") || lst[0].equals("edit")) {
                    if (i == 4) continue; //skipping ui-object index info
                }
            }

            if (lst[0].equals("dclick") || lst[0].equals("dlong") || lst[0].equals("dcheck")) {
                if (i == 4 || i == 5) continue; //skipping coordinate
            }

            if (lst[0].equals("dcheck")) {
                if (i == 6 && !useCheckbox) continue;
                //skipping checkbox
            }

            if (i != 0) se += ":";
            se += lst[i];
        }
        return se;
    }

    public static String simplifySimpilfiedEvent(String event, boolean checkboxUsed, boolean pathUsed) {
        String[] lst = event.split(":");
        String se = "";

        for (int i=0;i<lst.length;i++) {
            if (pathUsed) {
                if (i == 2) continue;
                if (lst[0].equals("scroll") || lst[0].equals("edit")) {
                    if (i == 3) continue;
                }
            }

            if (lst[0].equals("dcheck") && checkboxUsed) {
                if (pathUsed && i==3) continue;
                if (!pathUsed && i==2) continue;
            }

            if (i != 0) se += ":";
            se += lst[i];
        }
        return se;
    }

    // Get raw event list, and return simplified event list
    public static LinkedList<String> simplifyRawEvents(LinkedList<String> elist) {
        LinkedList<String> selist = new LinkedList<>();
        elist.forEach((x) -> selist.addLast(simplifyRawEvent(x, false, true)));
        return selist;
    }

    public static LinkedList<String> simplifySimplifiedEvents(LinkedList<String> elist) {
        LinkedList<String> selist = new LinkedList<>();
        elist.forEach((x) -> selist.addLast(simplifySimpilfiedEvent(x, false, true)));
        return selist;
    }

    // Get  event list and activity name, and compute simplified event list, key, and tooltip of the abstract state;
    public static void computeAbstraction(String activityName, Boolean isKeyboardShown, LinkedList<String> elist, StringBuilder key, StringBuilder tooltip) {
        // compute abstraction
        key.append(activityName + "," + isKeyboardShown + ",");
        boolean first = true;
        for (String next : elist) {
            String[] strs = next.split(":");
            if (first) first = false;
            else {
                key.append(",");
                tooltip.append(",");
            }

            String[] lst = next.split(":");
            String cleanDescPP = "";
            for (int i = 0 ; i<lst.length; i++) {
                if (i != 0) cleanDescPP += ":";

                if (i == 1) {
                    String[] chunks = lst[i].split("\\.");
                    cleanDescPP += chunks[chunks.length-1];
                }
                else{
                    cleanDescPP += lst[i];
                }
            }
            key.append(next);
            tooltip.append(cleanDescPP);
        }
    }

    // event filter function
    public static LinkedList<String> filter(JSONObject root, LinkedList<String> elist) {
        LinkedList<String> filteredList = new LinkedList<>();

        for (String e : elist) {
            String[] components = e.split(":");

            if (components[0].equals("dlong")) continue;
            if (components.length > 1) {
                if (components[1].equals("android.webkit.WebView")) continue;
                String pathStr = components[3];
                String[] path = pathStr.split("\\.");

                JSONObject curObj = root;
                for (int i = 1; i < path.length; i++) {
                    int index = Integer.parseInt(path[i]);
                    curObj = curObj.getJSONArray("children").getJSONObject(index);

                    // filters out web-view related events (they are unstable).
                    if (curObj.getString("class").equals("android.webkit.WebView")) {
                        curObj = null;
                        break;
                    }

                    // only return the first five elements of a list (abstraction)
                    // TODO: provide an option to turn this on and off

                    if (listLimit > 0) {
                        String className = curObj.getString("class");
                        if (className.equals("android.widget.ListView") || className.equals("android.widget.ExpandableListView")) {
                            if (i + 1 < path.length) {
                                int nextIndex = Integer.parseInt(path[i + 1]);
                                if (nextIndex > listLimit) {
                                    curObj = null;
                                    break;
                                }
                            }
                        }
                    }
                }
                if (curObj == null) continue;
            }
            filteredList.addLast(e);
        }

        return filteredList;
    }

    public static LinkedList<EventInfo> analyzeEvents(JSONObject root, LinkedList<String> elist) {
        HashMap<String, HashSet<Integer>> uiToEvents = new HashMap<>();
        HashMap<String, HashSet<Integer>> clusters = new HashMap<>();
        LinkedList<EventInfo> result = new LinkedList<>();


        for (int eventIndex=0;eventIndex<elist.size();eventIndex++) {
            String e = elist.get(eventIndex);

            EventInfo info = new EventInfo();
            result.addLast(info);

            String components[] = e.split(":");
            if (components.length > 1) {
                String cmd = components[0];
                String id = components[2];

                String[] path = components[3].split("\\.");

                info.accessPath = new LinkedList<>();
                for (String i: path) info.accessPath.addLast(Integer.parseInt(i));

                if (!uiToEvents.containsKey(id)) {
                    uiToEvents.put(id, new HashSet<Integer>());
                }
                info.brothers = uiToEvents.get(id);
                info.brothers.add(eventIndex);

                JSONObject curObj = root;
                String prefix = null;
                for (int pathIndex = 1; pathIndex < path.length; pathIndex++) {
                    int position = Integer.parseInt(path[pathIndex]);
                    if (prefix == null) prefix = path[pathIndex];
                    else prefix = prefix + ":"  +  path[pathIndex];

                    curObj = curObj.getJSONArray("children").getJSONObject(position);

                    String className = curObj.getString("class");
                    if (className.equals("android.widget.ListView") || className.equals("android.widget.ExpandableListView")) {
                        if (pathIndex + 1 < path.length) {
                            if (!clusters.containsKey(prefix)) {
                                clusters.put(cmd + ":" + prefix, new HashSet<>());
                            }
                            info.indexInCluster = position;
                            info.cluster = clusters.get(cmd + ":"  + prefix);
                            info.cluster.add(eventIndex);
                        }
                    }
                }
            }
        }

        return result;
    }
}
