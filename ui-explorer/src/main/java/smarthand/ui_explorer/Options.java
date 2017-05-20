package smarthand.ui_explorer;

import org.json.JSONException;
import org.json.JSONObject;
import org.omg.CORBA.TIMEOUT;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by wtchoi on 11/23/15.
 */
public class Options {
  static HashMap<String, String> options = new HashMap();

  public enum Keys {
    ANDROID_HOME("ANDROID_HOME"),
    BACK_FIRST("BACK_FIRST"),                    // always try back button first, if it is not tried before.
    COMMAND_LIMIT("COMMAND_LIMIT"),              // maximum number of commands to use
    DEVICE_NAME("DEVICE_NAME"),
    IMAGE_OUTPUT_DIR("IMAGE_OUTPUT_DIR"),
    OUTPUT_DIR("OUTPUT_DIR"),
    PORT("PORT"),
    APK_DIR("APK_DIR"),                           // the directory containing the target apk file and the information file
    TIMEOUT("TIMEOUT"),
    STRATEGY("STRATEGY"),
    RANDOM_SEED("RANDOM_SEED");
    //TARGET_APK_NAME("TARGET_APK_NAME");

    private final String text;

    private Keys (final String text) {
      this.text = text;
    }

    @Override
    public String toString() {
      return text;
    }
  }

  public static boolean exists(Keys key) {
    return options.containsKey(key);
  }

  public static String get(Keys key) {
    assert(options.containsKey(key.toString()));
    return options.get(key.toString());
  }

  public static void put(Keys key, String value) {
    options.put(key.toString(), value);
  }

  public static void loadJson(JSONObject json) throws JSONException{
    Iterator<String> iter = json.keys();

    while(iter.hasNext()) {
      String key = iter.next();
      String value = json.getString(key);
      options.put(key, value);
    }
  }

  public static JSONObject toJson() throws JSONException{
    JSONObject obj = new JSONObject();
    for (Map.Entry<String, String> entry: options.entrySet()) {
      obj.put(entry.getKey(), entry.getValue());
    }
    return obj;
  }

  public static void assertInitialized() {
    for (Keys key : Keys.values()) {
      assert exists(key);
    }
  }
}
