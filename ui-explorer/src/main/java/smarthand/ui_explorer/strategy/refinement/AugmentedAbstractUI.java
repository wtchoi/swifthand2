package smarthand.ui_explorer.strategy.refinement;

import smarthand.ui_explorer.*;
import smarthand.ui_explorer.trace.AbstractUI;
import smarthand.ui_explorer.trace.ConcreteUI;
import smarthand.ui_explorer.util.HashConsed;
import smarthand.ui_explorer.util.HashConsingFactory;
import smarthand.ui_explorer.util.Util;

/**
 * Created by wtchoi on 8/15/16.
 */ // Immutable, Hash-consed, Factory, Own hashCode and equals
class AugmentedAbstractUI extends HashConsed {
    public final AbstractUI abstractUi;
    public final Boolean[] aug;

    private static AugmentedAbstractUI blockstate = null;
    private static HashConsingFactory<AugmentedAbstractUI> factory
        = new HashConsingFactory<>(
            AugmentedAbstractUI::checkEqual,
            new HashConsingFactory.InstanceDumper<AugmentedAbstractUI>(
                Options.get(Options.Keys.IMAGE_OUTPUT_DIR),
                "augmentedUI"));

    public static Boolean checkEqual(Object o1, Object o2) {
        if (!(o1 instanceof AugmentedAbstractUI) || !(o2 instanceof AugmentedAbstractUI)) {
            return o1.equals(o2);
        }
        else {
            AugmentedAbstractUI a1 = (AugmentedAbstractUI) o1;
            AugmentedAbstractUI a2 = (AugmentedAbstractUI) o2;
            return a1.abstractUi == a2.abstractUi
                    && (a1.aug == null ? a2.aug == null : checkConsensus(a1.aug, a2.aug));
        }
    }

    private static boolean checkConsensus(Boolean[] aug1, Boolean[] aug2) {
        if (aug1.length != aug2.length) return false;
        for (int i=0; i<aug1.length; i++) {
            if (!aug1[i].equals(aug2[i])) return false;
        }
        return true;
    }

    public String augToString() {
        if (aug == null) return "[]";

        StringBuilder builder = new StringBuilder();
        boolean flag = false;
        for(boolean val: aug) {
            if (flag) builder.append(",");
            else (flag) = true;
            builder.append(val ? "1" : "0");
        }
        return "[" + builder.toString() + "]";
    }

    private AugmentedAbstractUI(AbstractUI abstractUi, Boolean[] aug) {
        super(Util.chainHash(Util.arrayChainHash(0, aug), abstractUi));
        this.abstractUi = abstractUi;
        this.aug = aug;
    }

    public static AugmentedAbstractUI get(ConcreteUI concreteUI, AbstractUI abstractUi, Augmenter augmentor) {
        //TODO: check input is block state
        Boolean[] aug = augmentor.augment(concreteUI, abstractUi);
        return factory.getInstance(new AugmentedAbstractUI(abstractUi, aug));
    }

    public static AugmentedAbstractUI getBlockState() {
        if (blockstate == null) {
            blockstate = factory.getInstance(new AugmentedAbstractUI(AbstractUI.getFailState(), null));
        }
        return blockstate;
    }

    public static AugmentedAbstractUI getById(int id) {
        return factory.getById(id);
    }

    public static int compare(AugmentedAbstractUI a1, AugmentedAbstractUI a2) {
        Integer id1 = a1.id();
        Integer id2 = a2.id();
        return id1.compareTo(id2);
    }

    @Override
    public String toString() {
        return id() + " (" + abstractUi.id() + "," + augToString() + ")";
    }
}
