package smarthand.ui_explorer.strategy.swifthand;

import java.util.HashSet;

/**
 * Created by wtchoi on 3/11/16.
 */
public interface Extrapolation {
    HashSet<Model.ModelState> extrapolate(Model.ModelState state, Integer eventIndex, int degree);
    boolean isExtrapolation(Model.ModelState from, Model.ModelState to, Integer eventIndex);
    boolean isClosed(Model.ModelState state, int degree);
    void update(STMLStrategy s);

    int degree();
    void dump();
    String name();
}
