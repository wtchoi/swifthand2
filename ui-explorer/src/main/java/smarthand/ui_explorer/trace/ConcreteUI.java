package smarthand.ui_explorer.trace;

import org.json.JSONArray;
import org.json.JSONObject;
import smarthand.ui_explorer.*;
import smarthand.ui_explorer.util.HashConsed;
import smarthand.ui_explorer.util.HashConsingFactory;
import smarthand.ui_explorer.util.Util;

import java.util.LinkedList;
import java.util.concurrent.Exchanger;

/**
 * Created by wtchoi on 8/15/16.
 */ // Immutable, Hash-consed, Factory, Own hashcode and equals
public class ConcreteUI extends HashConsed {
    public final String type;
    public final boolean actionable;
    //public final boolean checkable;
    //public final boolean clickable;
    //public final boolean scrollable;
    public final boolean enabled;
    public final boolean focused;
    public final boolean checked;
    public final String text;
    public final String bound;
    public final ConcreteUI[] children;

    public final AbstractUI abstractUI; // only top level ConcreteUI object has abstractUI field.
    public int snapshotID; //optional.

    private static ConcreteUI blockstate = null; //a special state to indicate that UI information is not available.
    private static HashConsingFactory<ConcreteUI> factory =
        new HashConsingFactory<ConcreteUI>(
            ConcreteUI::checkEqual,
            new HashConsingFactory.InstanceDumper<ConcreteUI>(Options.get(Options.Keys.IMAGE_OUTPUT_DIR), "concreteUI") {
                @Override
                public boolean isImportant(ConcreteUI e) { return e.abstractUI != null; }
            });

    private ConcreteUI(String ty, ConcreteUI[] ch, boolean actionable, boolean enabled, boolean focused, boolean checked, String text, String bound, AbstractUI abstractUI) {
        super(Util.chainHash(0, ty, Util.arrayChainHash(0, ch), actionable, enabled, focused, checked, text, bound));

        this.type = ty;
        this.children = ch;
        this.actionable = actionable;
        //checkable = false;
        //clickable = false;
        //scrollable = false;
        this.enabled = enabled;
        this.focused = focused;
        this.checked = checked;
        this.text = text;
        this.bound = bound;
        this.abstractUI = abstractUI;
    }

    public static ConcreteUI getBlockState() {
        if (blockstate == null) {
            blockstate = factory.getInstance(new ConcreteUI(null, null, false, false, false, false, null, null, AbstractUI.getFailState()));
        }
        return blockstate;
    }

    // get a concrete-ui correspond to the provided raw info
    public static ConcreteUI getFromRawInfo(JSONObject uiTree, AbstractUI abstractUI) {
        //TODO: check if the current state is blockstate
        if ((abstractUI != null && abstractUI.getEventCount() == 2) || uiTree.length() == 0) {
            return getBlockState();
        }

        try {
            ConcreteUI[] rChildren;
            String rTy = uiTree.getString("class");
            boolean rActionable = uiTree.has("actionable") && uiTree.getBoolean("actionable");
            boolean rFocused = uiTree.has("focused") && uiTree.getBoolean("focused");
            boolean rEnabled = uiTree.has("enabled") && uiTree.getBoolean("enabled");
            boolean rChecked = uiTree.has("checked") && uiTree.getBoolean("checked");
            String rText = uiTree.has("text") ? uiTree.getString("text") : null;
            String bound = uiTree.optString("bound");

            if (uiTree.has("children")) {
                JSONArray children = uiTree.getJSONArray("children");
                rChildren = new ConcreteUI[children.length()];
                for (int i = 0; i < children.length(); i++) {
                    rChildren[i] = getFromRawInfo(children.getJSONObject(i), null);
                }
            } else {
                rChildren = new ConcreteUI[0];
            }

            ConcreteUI obj = new ConcreteUI(rTy, rChildren, rActionable, rEnabled, rFocused, rChecked, rText, bound, abstractUI);
            return factory.getInstance(obj);
        }
        catch (Exception e) {
            System.out.println(uiTree.toString());
            throw new RuntimeException(e);
        }
    }

    public JSONObject toJson() {
        JSONObject uiObj = new JSONObject();

        // set properties
        uiObj.put("class", type);
        uiObj.put("bound", bound);
        if (checked) uiObj.put("checked", true);
        if (actionable) uiObj.put("actionable", true);
        if (enabled) uiObj.put("enabled", true);
        if (focused) uiObj.put("focused", true);
        if (text != null) uiObj.put("text", text);

        // get children
        if (this.children != null && this.children.length != 0) {
            JSONArray childrenObj = new JSONArray();
            uiObj.put("children", childrenObj);
            for (ConcreteUI child: this.children) {
                childrenObj.put(child.toJson());
            }
        }

        return uiObj;
    }

    // retrieve a sub-component given a path
    public ConcreteUI getSubComponent(LinkedList<Integer> path) {
        WalkObserver<ConcreteUI> getter = new WalkObserver<ConcreteUI>() {
            private ConcreteUI result;

            @Override
            public void fail() { }

            @Override
            public void success(ConcreteUI goal) { result = goal; }

            @Override
            public ConcreteUI getResult() { return result; }
        };
        this.walk(path, getter);
        return getter.getResult();
    }

    public boolean checkExistence(LinkedList<Integer> path) {
        WalkObserver<Boolean> checker = new WalkObserver<Boolean>() {
            private boolean result;

            @Override
            public void fail() { result = false;}

            @Override
            public void success(ConcreteUI goal) { result = true;}

            @Override
            public Boolean getResult() { return result;}
        };
        this.walk(path, checker);
        return checker.getResult();
    }

    private interface WalkObserver<T> {
        void fail();
        void success(ConcreteUI goal);
        T getResult();
    }

    private void walk(LinkedList<Integer> path, WalkObserver observer) {
        LinkedList<Integer> copy = new LinkedList<>(path);
        walkImpl(copy, observer);
    }

    // Only walk method should use this method.
    private void walkImpl(LinkedList<Integer> path, WalkObserver observer) {
        if (path.isEmpty()){
            observer.success(this);
            return;
        }

        Integer next = path.removeFirst();
        if (this.children.length <= next){
            observer.fail();
        }
        else{
            this.children[next].walkImpl(path, observer);
        }
    }

    public static boolean checkEqual(Object o1, Object o2) {
        if (!(o1 instanceof ConcreteUI) || !(o2 instanceof  ConcreteUI)){
            return o1.equals(o2);
        }
        else {
            ConcreteUI c1 = (ConcreteUI) o1;
            ConcreteUI c2 = (ConcreteUI) o2;

            if (!Util.equalsNullable(c1.type, c2.type)) return false;

            if (c1.children == null) {
                if (c2.children != null) return false;
            }
            else {
                if (c2.children == null) return false;
                if (c1.children.length != c2.children.length) return false;
                for (int i=0;i<c1.children.length;i++) {
                    // children should be already hash consed.
                    if (!checkEqual(c1.children[i],c2.children[i])) return false;
                }
            }

            return c1.actionable == c2.actionable
                    && c1.checked == c2.checked
                    && Util.equalsNullable(c1.text, c2.text)
                    && Util.equalsNullable(c1.bound, c2.bound);
        }
    }

    public static boolean checkEqualV(Object o1, Object o2, Logger logger) {
        if (!(o1 instanceof ConcreteUI) || !(o2 instanceof  ConcreteUI)){
            return o1.equals(o2);
        }
        else {
            ConcreteUI c1 = (ConcreteUI) o1;
            ConcreteUI c2 = (ConcreteUI) o2;

            if (!(c1.type == null ? c2.type == null : c1.type.equals(c2.type))) {
                logger.log("Type mismatch");
                return false;
            }

            if (c1.children == null) {
                if (c2.children != null){
                    logger.log("Children 1");
                    return false;
                }
            }
            else {
                if (c2.children == null) {
                    logger.log("Children 2");
                    return false;
                }
                if (c1.children.length != c2.children.length) {
                    logger.log("Children 3");
                    return false;
                }
                for (int i=0;i<c1.children.length;i++) {
                    // children should be already hash consed.
                    if (!checkEqualV(c1.children[i],c2.children[i], logger)) {
                        logger.log("Children 4:" + i);
                        return false;
                    }
                }
            }

            if (c1.actionable != c2.actionable) {
                logger.log("actionable!");
                return false;
            }

            if (c1.checked != c2.checked) {
                logger.log("checkable!");
                return false;
            }

            if (!(c1.text == null ? c2.text == null : c1.text.equals(c2.text))) {
                if (c1.text == null && c2.text != null) {
                    logger.log("text1!");
                }
                else if (c1.text != null && c2.text == null) {
                    logger.log("text2!");
                }
                else if (!c1.text.equals(c2.text)) {
                    logger.log("text3!");
                    logger.log(c1.text);
                    logger.log(c2.text);
                }
                return false;
            }

            if(!(c1.bound == null ? c2.bound == null : c1.bound.equals(c2.bound))) {
                if (c1.bound == null && c2.bound != null) {
                    logger.log("bound1!");
                }
                else if (c1.bound != null && c2.bound == null) {
                    logger.log("bound2!");
                }
                else if (!c1.bound.equals(c2.text)) {
                    logger.log("bound3!");
                    logger.log(c1.bound);
                    logger.log(c2.bound);
                }
                return false;
            }

            return true;
        }
    }

    @Override
    public String toString() {
        return toStringImpl(0, new StringBuilder()).toString();
    }

    private StringBuilder toStringImpl(int indent, StringBuilder builder) {
        for (int i=0;i<indent;i++) {
            builder.append("|\t");
        }

        builder.append(type);

        boolean flag = false;
        String option = "";

        if (actionable) {
            option += "actionable";
            flag = true;
        }

        if (checked) {
            if (flag) option += ", ";
            option += "checked";
            flag = true;
        }

        if (text != null) {
            if (flag) option += ", ";
            option += "[" + text + "]";
        }

        if (flag) {
            builder.append(" (")
                    .append(option)
                    .append(")");
        }

        builder.append(" " + bound);
        builder.append("\n");

        if (children != null) {
            for (ConcreteUI child : children) {
                child.toStringImpl(indent + 1, builder);
            }
        }

        return builder;
    }

    public void takeSnapshot() {
        HistoryManager.instance().takeSnapshot();
        snapshotID = HistoryManager.instance().getCurrentPeriod();
    }

    public static class CheckOption {
        public boolean checkProperties = false;
        public boolean checkText = false;
        public boolean checkCoordinate = false;
        public boolean verbose = false;

        public CheckOption (boolean checkProperties, boolean checkText, boolean checkCoordinate) {
            this.checkProperties = checkProperties;
            this.checkText = checkText;
            this.checkCoordinate =checkCoordinate;
        }
    }

    public static boolean checkUiDetail(ConcreteUI a, ConcreteUI b, CheckOption option, Logger logger) {
        LinkedList<Integer> path = new LinkedList<>();
        path.add(0);
        return checkUiDetailImpl(a,b,path, option, logger);
    }

    private static void printIntSet(Iterable<Integer> set, String tag, Logger logger) {
        StringBuilder builder = new StringBuilder(tag + ": [");
        Util.makeIntSetToString(set, ", ", builder);
        builder.append("]");
        logger.log(builder.toString());
    }

    public boolean isCrashScreen() {

        boolean isCrashMessage = false;
        if (this.type != null && this.type.equals("TextView") && this.text.startsWith("Unfortunately,") && this.text.endsWith("has stopped.")) {
            isCrashMessage = true;
        }
        if (isCrashMessage) return true;

        if (this.children != null) {
            for (ConcreteUI child:children) {
                if (child.isCrashScreen()) return true;
            }
        }
        return false;
    }

    private static boolean checkUiDetailImpl(ConcreteUI a, ConcreteUI b, LinkedList<Integer> path, CheckOption option, Logger logger) {
        //Let's first try structural equality
        if (!Util.equalsNullable(a,b)) {
            if (logger != null && option.verbose) {
                logger.log("Type mismatch");
                printIntSet(path, "Deviating path: ", logger);
            }
            return false;
        }

        if (option.checkProperties) {
            if (a.checked != b.checked || a.enabled != b.enabled || a.actionable != b.actionable || a.focused != b.focused) {
                if (logger != null && option.verbose) {
                    logger.log("Property difference");
                    printIntSet(path, "Deviating path: ", logger);
                }
                return false;
            }
        }

        if (option.checkText) {
            if (!Util.equalsNullable(a.text, b.text)) {
                if (logger != null && option.verbose) {
                    logger.log(String.format("Text difference: %s %s", a.text, b.text));
                    printIntSet(path, "Deviating path: ", logger);
                }
                return false;
            }
        }

        if (option.checkCoordinate) {
            if (!Util.equalsNullable(a.bound, b.bound)){
                if (logger != null && option.verbose) {
                    logger.log("Coordinate difference");
                }
                return false;
            }
        }

        if (a.children == null && b.children != null){
            if (logger != null && option.verbose) {
                logger.log("children difference 1");
                printIntSet(path, "Deviating path: ", logger);
            }
            return false;
        }

        if (a.children != null && b.children == null){
            if (logger != null && option.verbose) {
                logger.log("children difference 2");
                printIntSet(path, "Deviating path: ", logger);
            }
            return false;
        }

        if (a.children != null && b.children != null) {
            if (a.children.length != b.children.length) {
                if (logger != null && option.verbose) {
                    logger.log("children difference 3");
                    printIntSet(path, "Deviating path: ", logger);
                }
                return false;
            }
            for (int i=0; i<a.children.length; i++) {
                LinkedList<Integer> childPath = new LinkedList<>(path);
                childPath.add(i);
                if (!checkUiDetailImpl(a.children[i], b.children[i], childPath, option, logger)) {
                    return false;
                }
            }
        }

        return true;
    }
}
