package smarthand.ui_explorer.strategy.refinement;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import smarthand.ui_explorer.trace.ConcreteUI;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Created by wtchoi on 8/15/16.
 */
class PredicateFactory {
    private static Table<LinkedList<Integer>, String, Predicate<ConcreteUI>> typeTesters = HashBasedTable.create();
    private static Table<LinkedList<Integer>, String, Predicate<ConcreteUI>> textTester = HashBasedTable.create();
    private static Table<LinkedList<Integer>, String, Predicate<ConcreteUI>> boundTester = HashBasedTable.create();
    private static Table<LinkedList<Integer>, Attr, Predicate<ConcreteUI>> attributeTesters = HashBasedTable.create();
    private static Map<LinkedList<Integer>, Predicate<ConcreteUI>> existenceTesters = new HashMap();

    private static Map<Predicate<ConcreteUI>, String> desc = new HashMap<>();

    public enum Attr {
        Checked, Actionable
    }

    public static Predicate<ConcreteUI> getExistenceTester(final LinkedList<Integer> path) {
        if (!existenceTesters.containsKey(path)) {
            Predicate<ConcreteUI> p = ui -> ui.checkExistence(path);
            existenceTesters.put(path, p);
            desc.put(p, "exists(" + path + ")");
        }
        return existenceTesters.get(path);
    }

    public static Predicate<ConcreteUI> getTypeTester(final LinkedList<Integer> path, final String typeName) {
        if (!typeTesters.contains(path, typeName)) {
            Predicate<ConcreteUI> p = ui -> typeName.equals(ui.getSubComponent(path).type);
            typeTesters.put(path, typeName, p);
            desc.put(p, "typeEq(" + path + ", " + typeName + ")");
        }
        return typeTesters.get(path, typeName);
    }

    public static Predicate<ConcreteUI> getTextTester(final LinkedList<Integer> path, final String text) {
        if (textTester.contains(path, text)) {
            Predicate<ConcreteUI> p = ui -> text.equals(ui.getSubComponent(path).text);
            textTester.put(path, text, p);
            desc.put(p, "textEq(" + path + ", " + text + ")");
        }
        return textTester.get(path, text);
    }

    public static Predicate<ConcreteUI> getBoundTester(final LinkedList<Integer> path, final String bound) {
        if (!boundTester.contains(path, bound)) {
            Predicate<ConcreteUI> p = ui -> {
                if (bound == null) System.out.println("bound is null");
                if (ui.getSubComponent(path) == null) System.out.println("cannot find subcomponent");
                return bound.equals(ui.getSubComponent(path).bound);
            };
            boundTester.put(path, bound, p);
            desc.put(p, "boundEq(" + path + ", " + bound + ")");
        }
        return boundTester.get(path, bound);
    }

    public static Predicate<ConcreteUI> getBooleanAttrTester(final  LinkedList<Integer> path, final Attr attr) {
        Predicate<ConcreteUI> p;
        if (!attributeTesters.contains(path, attr)) {
            switch (attr) {
                case Checked:
                    p = ui -> ui.getSubComponent(path).checked;
                    attributeTesters.put(path, attr, p);
                    desc.put(p, "checked(" + path + ")");
                    break;
                case Actionable:
                    p = ui -> ui.getSubComponent(path).actionable;
                    attributeTesters.put(path, attr, p);
                    desc.put(p, "actionable(" + path + ")");
                    break;
            }
        }
        return attributeTesters.get(path, attr);
    }

    public static String getDesc(Predicate<ConcreteUI> p) {
        return desc.get(p);
    }
}
