package smarthand.instrument;

import org.json.JSONArray;
import org.json.JSONObject;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

/**
 * Created by wtchoi on 10/20/15.
 */
public class LibraryInjector {
  private JSONArray classes;

  LibraryInjector(String specFileName){
    try {
      InputStream is = getClass().getResourceAsStream(specFileName);
      InputStreamReader isr = new InputStreamReader(is);
      BufferedReader br = new BufferedReader(isr);
      StringBuilder builder = new StringBuilder();
      String line;
      while ((line = br.readLine()) != null) {
        builder.append(line);
      }
      br.close();
      isr.close();
      is.close();

      classes = new JSONArray(builder.toString());

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected void prepare() {
    for (int i=0; i<classes.length(); i++) {
      String className = classes.getString(i);
      Scene.v().addBasicClass(className, SootClass.BODIES);
    }
  }

  protected void inject() {
    for (int i=0; i<classes.length(); i++) {
      String className = classes.getString(i);
      Scene.v().getSootClass(className).setApplicationClass();
    }
  }
}
