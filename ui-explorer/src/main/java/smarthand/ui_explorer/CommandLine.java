package smarthand.ui_explorer;

import org.json.JSONObject;
import smarthand.ui_explorer.strategy.*;
import smarthand.ui_explorer.strategy.random.RandomStrategy;
import smarthand.ui_explorer.strategy.regression.LoopEliminationPlanner;
import smarthand.ui_explorer.strategy.regression.RecomposingPlanner;
import smarthand.ui_explorer.strategy.regression.ReplayPlanner;
import smarthand.ui_explorer.strategy.regression.SamplingPlanner;
import smarthand.ui_explorer.strategy.swifthand.MBRTStrategy;
import smarthand.ui_explorer.strategy.swifthand.MBRTStrategyConstructor;
import smarthand.ui_explorer.strategy.swifthand.STMLStrategy;
import smarthand.ui_explorer.util.Util;

import java.io.File;
import java.io.IOException;

/**
 * Created by wtchoi on 10/18/16.
 * Commandline interface class
 */
public class CommandLine {
    public static void main(String[] args) throws IOException {

        // 1. Read command line arguments:
        // The program expect following four command line arguments.
        //  - input directory containing apk file and json file
        //  - output directory
        //  - emulator (-e) / real device flag (-d)
        //  - device getName
        Client c = new Client();

        if (args.length < 8) {
            c.log("At least eight arguments should be provided (currently " + args.length + ").");
            return;
        }

        final String launchMode = "pkg"; // gui or am or pkg

        Options.put(Options.Keys.APK_DIR, args[0]);
        String apkPath = args[0] + "/instrumented.apk";
        String infoFilePath = args[0] + "/info.json";
        JSONObject info = Util.readJsonFile(infoFilePath);
        String mainActivity = info.getString("mainActivity");
        String appPackage = info.getString("package");
        c.setTarget(new ApkInfo(apkPath, appPackage, mainActivity));

        String outputDir = args[1];
        File outputDirFile = new File(outputDir);
        Options.put(Options.Keys.OUTPUT_DIR, outputDirFile.getCanonicalPath());
        Options.put(Options.Keys.IMAGE_OUTPUT_DIR, outputDirFile.getCanonicalPath() + "/image/");

        //if (args[2].equals("-e")) Options.put(Options.Keys.DEVICE_TYPE, "emulator");
        //else if (args[2].equals("-d")) Options.put(Options.Keys.DEVICE_TYPE, "device");
        //else assert false;

        String deviceName = args[3];
        Options.put(Options.Keys.DEVICE_NAME, deviceName);
        c.setDeviceDriver(new DeviceDriver(deviceName));

        String port = args[4];
        Options.put(Options.Keys.PORT, port);

        String timeout = args[5];
        Options.put(Options.Keys.TIMEOUT, timeout);

        String strategy = args[6];

        Options.put(Options.Keys.STRATEGY, strategy);

        int randomSeed = Integer.parseInt(args[7]);
        Options.put(Options.Keys.RANDOM_SEED, String.valueOf(randomSeed));


        // 2. Read environment variables:
        // The program also assumes that ANDROID_HOME environment variable is configured.
        String androidHome = System.getenv("ANDROID_HOME");
        File androidHomeDir = new File(androidHome);
        Options.put(Options.Keys.ANDROID_HOME, androidHomeDir.getAbsolutePath());

        // 3. Initialize the rest of options with a magic number
        Options.put(Options.Keys.COMMAND_LIMIT, Integer.toString(0));
        Options.put(Options.Keys.BACK_FIRST, "True");

        // 4. Before starting the test, assert all options are initialized properly.
        Options.assertInitialized();

        // 5. Set strategy
        String path;
        switch (strategy) {
            case "random":
                c.strategy = new RandomStrategy(false, false);
                break;
            case "smart-random":
                c.strategy = new RandomStrategy(false, true);
                break;
            case "sh":
                c.strategy = new STMLStrategy(STMLStrategy.ExtrapolationOptions.Non, false, false);
                break;
            case "sh2":
                c.strategy = new STMLStrategy(STMLStrategy.ExtrapolationOptions.Strict, false, false);
                break;
            case "sh-r":
                c.strategy = new STMLStrategy(STMLStrategy.ExtrapolationOptions.Non, true, false);
                break;
            case "sh2-r":
                c.strategy = new STMLStrategy(STMLStrategy.ExtrapolationOptions.Strict, true, false);
                break;
            case "lstar":
                c.strategy = new STMLStrategy(STMLStrategy.ExtrapolationOptions.Non, true, true);
                break;
            case "replay-ui":
                c.strategy = (new MBRTStrategyConstructor())
                        .setExploitationStrategy(MBRTStrategy.ExploitationStrategy.UiGreedyBlockGoal)
                        //.addOption(MBRTStrategy.StrategyOption.SkipUICheck)
                        .create(args[8]);
                break;
            case "replay-edge":
                c.strategy = (new MBRTStrategyConstructor())
                        .setExploitationStrategy(MBRTStrategy.ExploitationStrategy.EdgeGreedyBlockGoal)
                        //.addOption(MBRTStrategy.StrategyOption.SkipUICheck)
                        .create(args[8]);
                break;
            case "replay-edge-tr":
                c.strategy = (new MBRTStrategyConstructor())
                        .setExploitationStrategy(MBRTStrategy.ExploitationStrategy.EdgeGreedyBlockTransition)
                        //.addOption(MBRTStrategy.StrategyOption.SkipUICheck)
                        .create(args[8]);
                break;
            case "replay-ui-search":
                c.strategy = (new MBRTStrategyConstructor())
                        .setExploitationStrategy(MBRTStrategy.ExploitationStrategy.UiBoundedPath)
                        //.addOption(MBRTStrategy.StrategyOption.SkipUICheck)
                        .create(args[8]);
                break;
            case "replay-edge-search":
                c.strategy = (new MBRTStrategyConstructor())
                        .setExploitationStrategy(MBRTStrategy.ExploitationStrategy.EdgeBoundedPath)
                        //.addOption(MBRTStrategy.StrategyOption.SkipUICheck)
                        .create(args[8]);
                break;
            case "sequence-replay":
                c.strategy = new PlanningStrategy(new ReplayPlanner(args[8], 1, false));
                break;
            case "sequence-stabilize":
                c.strategy = new PlanningStrategy(new ReplayPlanner(args[8], Integer.parseInt(args[9]), true));
                break;
            case "eliminate-loop":
                c.strategy = new PlanningStrategy(new LoopEliminationPlanner(args[8], Integer.parseInt(args[9])));
                break;
            case "splicing":
                c.strategy = new PlanningStrategy(new RecomposingPlanner(args[8], Integer.parseInt(args[9]), Integer.parseInt(args[10])));
                break;
            case "sampling":
                c.strategy = new PlanningStrategy(new SamplingPlanner(args[8], 3, Integer.parseInt(args[9]), Integer.parseInt(args[10])));
                break;
            case "manual":
                c.strategy = new ManualStrategy();
                break;
            case "branch-coverage-testing":
                c.strategy = new BranchCoverageTestingStrategy();
                break;
            case "monkey":
                c.strategy = new MonkeyStrategy();
                break;
            case "semi":
                c.strategy = new SemiManualStrategy();
                break;
            default:
                throw new RuntimeException("Wrong strategy");
        }
        c.strategy.setRandomSeed(randomSeed);

        CoverageManager.setDumpEveryUpdate(true);
        c.coverageManager = CoverageManager.getInstance();

        c.uiBridge = UiDriverBridge.getInstance();
        UiDriverBridge.setLogger(c);

        // update meta data
        c.updateMetaData();

        // Start testing
        c.infiniteTest(Integer.MAX_VALUE, launchMode);
        System.out.println("Halting!");
        System.exit(0);
    }
}
