package smarthand.ui_explorer.strategy.refinement;

import com.google.common.collect.Multimap;
import org.json.JSONArray;
import org.json.JSONObject;
import smarthand.ui_explorer.trace.AbstractUI;
import smarthand.ui_explorer.trace.ConcreteUI;

import java.util.LinkedList;

/**
 * Created by wtchoi on 8/26/16.
 */
public class VRFStrategyLogger {
    private JSONArray arr = new JSONArray();

    public void onStart() {
        arr.put(new JSONArray().put("start"));
    }

    public void onEvent(int index) {
        arr.put(new JSONArray().put("event").put(index));
    }

    public void onReport(ConcreteUI cui, AugmentedAbstractUI aaui, AbstractUI aui, boolean restarted) {
        JSONObject report = new JSONObject()
                .put("concrete", cui.id())
                .put("augmented", aaui.id())
                .put("abstract", aui.id())
                .put("restarted", restarted);

        arr.put(new JSONArray().put("report").put(report));
    }

    public void onPlan(ExecutionPlan plan) {
        JSONArray p = new JSONArray(plan.getPlanEncoding());
        arr.put(new JSONArray().put("plan").put(p));
    }

    public void onExpect(AugmentedAbstractUI aui) {
        arr.put(new JSONArray().put("expect").put(aui.id()));
    }


    public void onRefinement(AbstractUI aui, Multimap<AugmentedAbstractUI, ConcreteUI> map, LinkedList<String> predicateDescs) {
        JSONObject j = new JSONObject().put("abstract", aui.id());

        JSONArray augToCon = new JSONArray();
        j.put("partition", augToCon);
        map.keySet().forEach(aug -> {
            JSONArray cons = new JSONArray();
            augToCon.put(aug.id(), cons);
            map.get(aug).forEach(con -> cons.put(con.id()));
        });

        arr.put(new JSONArray().put("refinement").put(j).put(new JSONArray(predicateDescs)));
    }


    public String getLog() {
        return arr.toString();
    }

    public void clear() {
        arr = new JSONArray();
    }
}
