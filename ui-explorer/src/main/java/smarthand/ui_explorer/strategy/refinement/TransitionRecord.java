package smarthand.ui_explorer.strategy.refinement;

import smarthand.ui_explorer.trace.AbstractUI;
import smarthand.ui_explorer.trace.ConcreteUI;
import smarthand.ui_explorer.util.HashConsed;
import smarthand.ui_explorer.util.HashConsingFactory;
import smarthand.ui_explorer.util.Util;

/**
 * Created by wtchoi on 8/15/16.
 */ // Immutable, Hash-consed, Factory, Own hashcode and equals
class TransitionRecord extends HashConsed {
    public final ConcreteUI from;
    public final ConcreteUI to;
    public final AbstractUI abstractFrom;
    public final AbstractUI abstractTo;
    public final int eventIndex;

    private static HashConsingFactory<TransitionRecord> factory =
            new HashConsingFactory<>(TransitionRecord::checkEquals);

    private TransitionRecord(ConcreteUI from, ConcreteUI to, AbstractUI fromA, AbstractUI toA, int eventIndex) {
        super(Util.chainHash(1, from, to, eventIndex));
        this.from = from;
        this.to = to;
        this.abstractFrom = fromA;
        this.abstractTo = toA;
        this.eventIndex = eventIndex;
    }

    public static TransitionRecord get(ConcreteUI from, ConcreteUI to, AbstractUI fromA, AbstractUI toA, int eventIndex) {
        TransitionRecord t = new TransitionRecord(from, to, fromA, toA, eventIndex);
        return factory.getInstance(t);
    }

    public static boolean checkEquals(Object o1, Object o2) {
        if (!(o1 instanceof TransitionRecord) || !(o2 instanceof TransitionRecord)) {
            return o1.equals(o2);
        }
        else {
            TransitionRecord tr1 = (TransitionRecord) o1;
            TransitionRecord tr2 = (TransitionRecord) o2;
            return (tr1.from == null ? tr2.from == null : tr1.from.equals(tr2.from))
                    && (tr1.to == null ? tr2.to == null : tr1.to.equals(tr2.to))
                    &&  tr1.eventIndex == tr2.eventIndex;
        }
    }
}
