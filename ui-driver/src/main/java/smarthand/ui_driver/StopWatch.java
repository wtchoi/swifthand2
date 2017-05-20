package smarthand.ui_driver;

import java.util.HashMap;
import java.util.Set;

/**
 * Created by wtchoi on 12/9/15.
 */
public class StopWatch {
  private HashMap<String, Long> elapsedTimes = new HashMap<String, Long>();
  private HashMap<String, Long> currentStartTime = new HashMap<String, Long>();

  public void begin(String key){
    if (!elapsedTimes.containsKey(key)) {
      elapsedTimes.put(key, Long.valueOf(0));
    }
    currentStartTime.put(key, System.currentTimeMillis());
  }

  public void end(String key) {
    long currentElapsedTime = System.currentTimeMillis() - currentStartTime.get(key);
    elapsedTimes.put(key, elapsedTimes.get(key) + currentElapsedTime);
  }

  public long getTime(String key) {
    return elapsedTimes.get(key);
  }

  public Set<String> getKeySet() {
    return elapsedTimes.keySet();
  }
}
