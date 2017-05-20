package smarthand.ui_explorer.strategy;

import smarthand.ui_explorer.CoverageManager;
import smarthand.ui_explorer.DeviceInfo;
import smarthand.ui_explorer.Strategy;
import smarthand.ui_explorer.trace.Coverage;

import java.util.LinkedList;
import java.util.Scanner;

/**
 * Created by wtchoi on 4/19/16.
 */
public class ManualStrategy extends Strategy {

    LinkedList<String> currentEvents;
    int previous_coverage = 0;

    @Override
    public void reportExecution(DeviceInfo deviceInfo, Coverage coverage, boolean escaped, boolean blocked) {
        currentEvents = deviceInfo.filteredEvents;
    }

    public String getNextAction() {
        // print coverage info
        int coverage = CoverageManager.getInstance().getMBCoverage();
        int diff = coverage - previous_coverage;
        if (diff > 0) {
            System.out.println("Current MB Coverage: " + coverage + " (new " + diff + ")");
            previous_coverage = coverage;
        }
        else {
            System.out.println("Current MB Coverage: " + coverage);
        }

        //getFromRawInfo next command (coverage update or finish)
        Scanner reader = new Scanner(System.in);  // Reading from System.in
        int n;
        while (true) {
            try {
                System.out.println(">>> Enter a number (1 for nop, anything else for finish) :");
                n = reader.nextInt(); // Scans the next token of the input as an int.
                break;
            } catch (java.util.InputMismatchException e) {
                System.out.println("Wrong input: " + reader.next());
            }
        }

        if (n == 1) return "nop";
        if (n == 2) return "cmonkey:touch down 487 233\n" +
                "sleep 100\n" +
                "touch up 482 217\n" +
                "sleep 100\n" +
                "touch down 313 669\n" +
                "sleep 100\n" +
                "touch up 303 689\n" +
                "sleep 100\n" +
                "touch down 194 623\n" +
                "sleep 100\n" +
                "touch up 195 688\n" +
                "sleep 100\n" +
                "touch down 100 1165\n" +
                "sleep 100\n" +
                "touch up 91 1164\n" +
                "sleep 100\n" +
                "touch down 644 48\n" +
                "sleep 100\n" +
                "touch up 630 19\n" +
                "sleep 100\n" +
                "touch down 567 600\n" +
                "sleep 100\n" +
                "touch up 629 542\n" +
                "sleep 100\n" +
                "touch down 330 810\n" +
                "sleep 100\n" +
                "touch up 303 806\n" +
                "sleep 100\n" +
                "trackball 1 1\n" +
                "sleep 100\n" +
                "touch down 190 247\n" +
                "sleep 100\n" +
                "touch up 136 180\n" +
                "sleep 100\n" +
                "touch down 366 550\n" +
                "sleep 100\n" +
                "touch up 368 549\n" +
                "sleep 100\n" +
                "touch down 211 1116\n" +
                "sleep 100\n" +
                "touch up 197 1110\n" +
                "sleep 100\n" +
                "touch down 35 773\n" +
                "sleep 100\n" +
                "touch up 27 775\n" +
                "sleep 100\n" +
                "trackball -3 2\n" +
                "sleep 100\n" +
                "press 23\n" +
                "sleep 100\n" +
                "trackball -5 -5\n" +
                "sleep 100\n" +
                "touch down 51 639\n" +
                "sleep 100\n" +
                "touch up 0 706\n" +
                "sleep 100\n" +
                "touch down 576 550\n" +
                "sleep 100\n" +
                "touch up 580 562\n" +
                "sleep 100\n" +
                "touch down 250 1151\n" +
                "sleep 100\n" +
                "touch up 213 1097\n" +
                "sleep 100\n" +
                "touch down 86 372\n" +
                "sleep 100\n" +
                "touch up 102 375\n" +
                "sleep 100\n" +
                "touch down 159 313";
        return "finish";
    }

    public void intermediateDump(int id) { }
    public void finalDump() { }

    public String getName() {
        return "manual";
    }
    public String getDetailedExplanation() { return getName(); }

    public void setRandomSeed(int randomSeed) { }
}
