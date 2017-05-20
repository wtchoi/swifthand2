package smarthand.ui_explorer;

import smarthand.ui_explorer.util.Util;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashSet;

/**
 * Created by wtchoi on 5/27/16.
 */
public class CoverageManager {
    private HashSet<Integer> coveredMethods = new HashSet<>();
    private HashSet<Integer> coveredBranches = new HashSet<>();

    private HashSet<Integer> latestMethodDelta = new HashSet<>();
    private HashSet<Integer> latestBrancheDelta = new HashSet<>();

    private String path;

    private static boolean dumpEveryUpdate = false;
    private static CoverageManager instance = null;

    public static CoverageManager getInstance() {
        if (instance == null) {
            instance = new CoverageManager();
        }
        return instance;
    }

    public static void setDumpEveryUpdate(boolean flag) {
        CoverageManager.dumpEveryUpdate = flag;
    }

    private CoverageManager() {
        path = Options.get(Options.Keys.OUTPUT_DIR) + "/log";
    }

    public void addMethods(Collection<Integer> s) {
        latestMethodDelta.clear();
        if (coveredMethods.containsAll(s)) return;

        for (Integer mid: s) {
            if (coveredMethods.contains(mid)) continue;
            latestMethodDelta.add(mid);
            coveredMethods.add(mid);
        }

        if (!dumpEveryUpdate) return;
        dumpMethodCoverage();
    }

    public void addBranches(Collection<Integer> s) {
        latestBrancheDelta.clear();
        if (coveredBranches.containsAll(s)) return;

        for (Integer bid: s) {
            if (coveredBranches.contains(bid)) continue;
            latestBrancheDelta.add(bid);
            coveredBranches.add(bid);
        }

        if (!dumpEveryUpdate) return;
        dumpBranchCoverage();
    }

    public void addCoverage(Collection ms, Collection bs) {
        addMethods(ms);
        addBranches(bs);
    }

    public int getMBCoverage() {
        return coveredBranches.size() + coveredMethods.size();
    }

    public int getMethodCoverage() {
        return coveredMethods.size();
    }

    public int getBranchCoverage() {
        return coveredBranches.size();
    }

    public boolean existsLatestMethodDelta() {
        return !latestMethodDelta.isEmpty();
    }

    public boolean existsLatestBranchDelta() {
        return !latestBrancheDelta.isEmpty();
    }

    public HashSet<Integer> getLatestMethodDelta() {
        return latestMethodDelta;
    }

    public HashSet<Integer> getLatestBranchDelta() {
        return latestBrancheDelta;
    }

    public void dump() {
        dumpBranchCoverage();
        dumpMethodCoverage();
    }

    private void dumpBranchCoverage() {
        try {
            File bCoverage = new File(path + "/covered_branches");
            StringBuilder bBuilder = Util.makeIntSetToString(coveredBranches, "\n", null);
            PrintWriter bWriter = new PrintWriter(new FileWriter(bCoverage));
            bWriter.print(bBuilder.toString());
            bWriter.flush();
            bWriter.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void dumpMethodCoverage() {
        try {
            File mCoverage = new File(path + "/covered_methods");
            StringBuilder mBuilder = Util.makeIntSetToString(coveredMethods, "\n", null);
            PrintWriter mWriter = new PrintWriter(new FileWriter(mCoverage));
            mWriter.print(mBuilder.toString());
            mWriter.flush();
            mWriter.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
