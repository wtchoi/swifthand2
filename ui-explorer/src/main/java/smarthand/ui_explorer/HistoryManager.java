package smarthand.ui_explorer;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.text.DecimalFormat;
import java.util.*;

/**
 * Created by wtchoi on 11/24/15.
 */
public class HistoryManager {
  private static HistoryManager manager;

  public static HistoryManager instance(){
    if(manager == null) {
      manager = new HistoryManager();
    }
    return manager;
  }

  private static class LogEntry {
    String type;
    String log;

    public LogEntry(String type, String log) {
      this.type = type;
      this.log = log;
    }
  }

  private int periodCount = 0;
  private LinkedList<LogEntry> logs = new LinkedList();
  private LinkedList<LogEntry> warnings = new LinkedList();
  private HashMap<String, String> stats = new HashMap();
  private HashMap<String, String> colorMap = new HashMap();

  private HashMap<String, LinkedList<Long>> valueSeqTable = new HashMap();
  private HashMap<String, Double> valueSeqAverage = new HashMap();

  private String[] colorTable = {"red", "blue", "green", "indigo", "cyan", "maroon"};
  private int nextColor = 0;

  private long startTime = 0;
  private long previousPeriodEndTime = 0;
  private long previousActionEndTime = 0;
  private long previousInfoGatherEndTime = 0;
  private long previousDecisionTime = 0;

  public void begin() {
    startTime = System.currentTimeMillis();
    previousPeriodEndTime = startTime;
  }

  public void end() {}

  public void actionPerformed(){
    previousActionEndTime = System.currentTimeMillis();
  }

  public void informationGathered() {
    previousInfoGatherEndTime = System.currentTimeMillis();
  }

  public void setDecision(String decision) {
    previousDecisionTime = System.currentTimeMillis();
  }

  public void startNextPeriod(){
    periodCount++;
    logs.clear();
    warnings.clear();
    stats.clear();
  }

  public void finishCurrentPeriod() {
    // update stats
    long currentPeriodEndTime = System.currentTimeMillis();
    long elapsedTime = currentPeriodEndTime - startTime;
    long currentDecisionTime = previousDecisionTime - previousPeriodEndTime;
    long currentActionTime = previousActionEndTime - previousDecisionTime;
    long currentInfoTime = previousInfoGatherEndTime - previousActionEndTime;

    periodStat("Time:Elapsed", elapsedTime);
    collectValue("Time:Iteration",  currentPeriodEndTime - previousPeriodEndTime);
    collectValue("Time:Strategy", currentDecisionTime);
    collectValue("Time:Event", currentActionTime);
    collectValue("Time:Info", currentInfoTime);

    previousPeriodEndTime = System.currentTimeMillis();

    // Output logs and stats
    try {
      String image_dir = Options.get(Options.Keys.IMAGE_OUTPUT_DIR);
      File image_output_dir = new File(image_dir);
      if (!image_output_dir.exists()) image_output_dir.mkdir();

      {
        // handle logs
        String log_file_path = image_dir + "/log" + periodCount + ".html";
        FileWriter fw = new FileWriter(log_file_path);
        fw.write(logToHtml());
        fw.flush();
        fw.close();;

        // handle stats
        String stat_file_path = image_dir + "/stat" + periodCount + ".html";
        FileWriter sfw = new FileWriter(stat_file_path);
        sfw.write(statToHtml());
        sfw.flush();
        sfw.close();

        String stat_json_file_path = image_dir + "/stat" +  periodCount + ".json";
        FileWriter sjfw = new FileWriter(stat_json_file_path);
        sjfw.write(statToJson());
        sjfw.flush();
        sjfw.close();
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void log(String source, String log) {
    logs.add(new LogEntry(source, log));
  }
  public void warning(String source, String log) { warnings.add(new LogEntry(source, log)); }

  public void periodStat(String key, long value) {
    stats.put(key, String.valueOf(value));
  }
  public void periodStat(String key, String value) { stats.put(key, value); }
  //public void periodStat(String key, JSONArray arr) { stats.put(key, arr); } //TODO
  //public void periodSTat(String key, JSONObject obj) { stats.put(key, obj); } //TODO

  public void collectValue(String key, long value) {
    if (!valueSeqTable.containsKey(key)) {
      valueSeqTable.put(key, new LinkedList());
      valueSeqAverage.put(key, 0.0);
    }

    LinkedList<Long> valueSeq = valueSeqTable.get(key);
    int count = valueSeq.size();
    double prevAvg = valueSeqAverage.get(key);

    valueSeq.add(value);
    valueSeqAverage.put(key, prevAvg + ((value - prevAvg) / (count + 1.0)));
  }


  public void takeSnapshot() {
    String imageFilePath = Options.get(Options.Keys.IMAGE_OUTPUT_DIR) + "/screen" + periodCount + ".png";

    String cmd[] = {
            "/bin/sh",
            "-c",
            String.format("%s -s %s shell screencap -p | perl -pe 's/\\x0D\\x0A/\\x0A/g' > '%s'",
                    Options.get(Options.Keys.ANDROID_HOME) + "/platform-tools/adb",
                    Options.get(Options.Keys.DEVICE_NAME),
                    imageFilePath)
    };

    try {
      Runtime.getRuntime().exec(cmd).waitFor();
    } catch (Exception e) {
      // e.printStackTrace();
    }

    // String command = "screenshot2 -s " + Options.getFromRawInfo(Options.Keys.DEVICE_NAME) + " " + imageFilePath;
    // Util.executeShellCommand(command);
  }


  private String getColor(String source) {
    if (!colorMap.containsKey(source)) {
      assert (nextColor != colorTable.length);
      colorMap.put(source, colorTable[nextColor++]);
    }
    return colorMap.get(source);
  }


  public String logToHtml() {
    StringBuilder sb = new StringBuilder();
    sb.append("<html><body>");

    getColor("warning");
    if (warnings.size() != 0) {
      String color = getColor("warning");
      String style = "\"margin:1px; padding:1px; font-size:11px; color:" + color + ";\"";
      sb.append("<p style=" + style + ">------- Warning -------</p>");
      for (LogEntry l : warnings) {
        String encoded = l.log.replaceAll("\\s", "&nbsp;").replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;");
        String entry = "<p style=" + style + ">[" + l.type + "]\t" + encoded + "</p>";
        sb.append(entry);
      }
    }

    sb.append("<p style=\"margin:1px; padding:1px; font-size:11px;\">------- Logs -------</p>");
    for (LogEntry l : logs) {
      String color = getColor(l.type);
      String style = "\"margin:1px; padding:1px; font-size:11px; color:" + color + ";\"";
      String encoded = l.log.replaceAll("\\s", "&nbsp;").replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;");
      String entry = "<p style=" + style + ">[" + l.type + "]\t" + encoded + "</p>";
      sb.append(entry);
    }

    sb.append("</html></body>");
    return sb.toString();
  }


  public String statToHtml() {
    StringBuilder sb = new StringBuilder();
    sb.append("<html><body style=\"font-size:11px;\"><table>");
    SortedSet<String> keys = new TreeSet<String>(stats.keySet());
    for (String key: keys) {
      String keyParagraph   = "<p style=\"font-size:11px;\">" + key + "</p>";
      String valueParagraph = "<p style=\"font-size:11px;\">" + stats.get(key) + "</p>";
      sb.append("<tr><td>" + keyParagraph + "</td><td>" + valueParagraph + "</td></tr>");
    }

    DecimalFormat df = new DecimalFormat();
    df.setMaximumFractionDigits(0);
    SortedSet<String> sortedKeys = new TreeSet<String>(valueSeqTable.keySet());
    for (String s : sortedKeys) {
      String val = String.valueOf(valueSeqTable.get(s).getLast());
      String avg = df.format(valueSeqAverage.get(s)).replaceAll(",","");

      String keyParagraph   = "<p style=\"font-size:11px;\">" + s + "</p>";
      String valueParagraph = "<p style=\"font-size:11px;\">" + val + "</p>";
      String avgParagraph = "<p style=\"font-size:11px;\">" + avg + "</p>";
      sb.append("<tr><td>" + keyParagraph + "</td><td>" + avgParagraph + "</td><td>" + valueParagraph + "</td></tr>");
    }

    sb.append("</table></html></body>");
    return sb.toString();
  }


  public String statToJson() {
    JSONObject jobj = new JSONObject();
    SortedSet<String> keys = new TreeSet<String>(stats.keySet());
    for (String key: keys) {
      jobj.put(key.replaceAll(":","_").replaceAll(" ","_").replaceAll("#","Num"), stats.get(key));
    }
    DecimalFormat df = new DecimalFormat();
    df.setMaximumFractionDigits(0);

    SortedSet<String> sortedKeys = new TreeSet<String>(valueSeqTable.keySet());
    for (String s: sortedKeys) {
      String val = String.valueOf(valueSeqTable.get(s).getLast());
      String avg = df.format(valueSeqAverage.get(s)).replaceAll(",","");

      JSONObject sub = new JSONObject();
      sub.put("value", val);
      sub.put("mean", avg);
      jobj.put(s.replaceAll(":","_").replaceAll(" ","_").replaceAll("#","Num"), sub);
    }
    return jobj.toString();
  }


  public int getCurrentPeriod() {
    return periodCount;
  }

  public long getElapsedTime() { return System.currentTimeMillis() - startTime; }
}
