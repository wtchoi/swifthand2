package smarthand.ui_explorer.strategy;

import smarthand.ui_explorer.CoverageManager;
import smarthand.ui_explorer.DeviceInfo;
import smarthand.ui_explorer.Strategy;
import smarthand.ui_explorer.trace.Coverage;

import java.util.LinkedList;
import java.util.Scanner;

/**
 * Created by wtchoi on 6/4/16.
 */
public class SemiManualStrategy extends Strategy {

    LinkedList<String> currentEvents;
    int previous_coverage = 0;

    @Override
    public void reportExecution(DeviceInfo deviceInfo, Coverage coverage, boolean escaped, boolean blocked) {
        currentEvents = deviceInfo.filteredEvents;
        log("Filtered Events");
        int eventCounter = 0;
        for (String s: currentEvents) {
            log(eventCounter + ". "  + s);
            eventCounter++;
        }
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
                System.out.println(">>> Enter a number :");
                n = reader.nextInt(); // Scans the next token of the input as an int.
                if (n >= 0 && n < currentEvents.size()) return "event:" + n;
            } catch (java.util.InputMismatchException e) { }
            System.out.println("Wrong input: " + reader.next());
        }
    }

    public void intermediateDump(int id) { }
    public void finalDump() { }

    public String getName() {
        return "semi manual";
    }
    public String getDetailedExplanation() { return getName(); }

    public void setRandomSeed(int randomSeed) { }
}
