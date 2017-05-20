package smarthand.ui_explorer.strategy.refinement;

import smarthand.ui_explorer.trace.AbstractUI;
import smarthand.ui_explorer.trace.ConcreteUI;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * Created by wtchoi on 8/15/16.
 */
class Augmenter {
    HashMap<AbstractUI, LinkedList<Predicate<ConcreteUI>>> table = new HashMap<>();

    public void addTester(AbstractUI abstractUi, Predicate<ConcreteUI> tester) {
        if (!table.containsKey(abstractUi)) {
            table.put(abstractUi, new LinkedList());
        }
        table.get(abstractUi).addLast(tester);
    }

    public Boolean[] augment(ConcreteUI concreteUI, AbstractUI abstractUi) {
        if (!table.containsKey(abstractUi)) {
            return null;
        }
        else {
            LinkedList<Predicate<ConcreteUI>> testerList = table.get(abstractUi);
            Boolean[] result = new Boolean[testerList.size()];
            for (int i=0 ; i<testerList.size() ; i++) {
                result[i] = testerList.get(i).test(concreteUI);
            }
            return result;
        }
    }

    public LinkedList<Predicate<ConcreteUI>> getPredicates(AbstractUI aui) {
        if (!table.containsKey(aui)) return null;
        return new LinkedList(table.get(aui));
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        table.forEach((ui, predicates) -> {
            builder.append(ui.id()).append("{");
            final AtomicBoolean flag = new AtomicBoolean(true);

            predicates.forEach(p -> {
                if (flag.get()) flag.set(false);
                else builder.append(", ");
                builder.append(PredicateFactory.getDesc(p));
            });

            builder.append("}\n");
        });

        return builder.toString();
    }
}
