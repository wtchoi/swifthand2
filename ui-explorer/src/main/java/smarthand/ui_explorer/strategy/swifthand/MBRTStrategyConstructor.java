package smarthand.ui_explorer.strategy.swifthand;

import java.util.HashSet;

/**
 * Created by wtchoi on 7/21/16.
 */
public class MBRTStrategyConstructor {
    String modelPath;
    MBRTStrategy.ExplorationStrategy explorationStrategy = MBRTStrategy.ExplorationStrategy.StateCover;
    MBRTStrategy.ExploitationStrategy exploitationStrategy = MBRTStrategy.ExploitationStrategy.EdgeGreedyBlockGoal;
    HashSet<MBRTStrategy.StrategyOption> options = new HashSet<>();

    public MBRTStrategy create(String path) {
        MBRTStrategy s = new MBRTStrategy(path, exploitationStrategy);
        //TODO: set exploration strategy

        for (MBRTStrategy.StrategyOption opt: options) {
            s.addOption(opt);
        }
        return s;
    }

    public MBRTStrategyConstructor setExplorationStrategy (MBRTStrategy.ExplorationStrategy s) {
        explorationStrategy = s;
        return this;
    }

    public MBRTStrategyConstructor setExploitationStrategy (MBRTStrategy.ExploitationStrategy s) {
        exploitationStrategy = s;
        return this;
    }

    public MBRTStrategyConstructor addOption(MBRTStrategy.StrategyOption opt) {
        options.add(opt);
        return this;
    }
}
