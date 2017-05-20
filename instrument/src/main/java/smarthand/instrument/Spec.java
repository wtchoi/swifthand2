package smarthand.instrument;

import org.json.JSONArray;
import org.json.JSONObject;
import soot.Scene;
import soot.SootClass;
import soot.Type;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Created by wtchoi on 10/20/15.
 */

// Spec with spec description file.
public class Spec {
  private JSONArray specs;
  private JSONObject types;

  Spec(String specFileName) {
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

      JSONObject obj = new JSONObject(builder.toString());
      specs = obj.getJSONArray("specs");
      types = obj.getJSONObject("types");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public List<String> getStringListOf(JSONArray pattern) {
    List<String> result = new LinkedList<>();
    for (int i=0; i<pattern.length(); i++) {
      result.add(pattern.optString(i));
    }
    return result;
  }

  public List<String> getSigPatternString(SootClass cls, String mName, List<Type> sig) {
    for (int i =0;i < specs.length(); i++) {
      JSONObject spec = specs.optJSONObject(i);
      String specType = spec.getString("spec-type");

      if (specType.equals("include")) {
        if (matchSpec(spec, cls, mName, sig)) {
          return getSigPatternStringFromSpec(spec, cls, mName, sig);
        }
      }
      else if (specType.equals("exclude")) {
        if (matchSpec(spec, cls, mName, sig)) return null;
      }
    }
    return null;
  }

  private List<String> getSigPatternStringFromSpec(JSONObject spec, SootClass cls, String mName, List<Type> sig) {
    if (!spec.has("all-class")) {
      String targetClassName = spec.optString("class");
      SootClass targetClass = Scene.v().getSootClass(targetClassName);

      if (!cls.equals(targetClass)) {
        if (spec.has("exact-class")) return null;
        if (!checkSubclass(cls, targetClass)) return null;
      }
    }

    if (spec.has("all-method")) return getPatternStringOf(sig);
    if (spec.has("methods")) {
      JSONArray sigPatterns = getSigPatterns(spec, mName);
      if (sigPatterns == null) return null;

      JSONArray sigPattern = getSigPattern(sigPatterns, sig);
      if (sigPattern == null) return null;

      return getStringListOf(sigPattern);
    }
    return null;
  }

  private List<String> getPatternStringOf(List<Type> sig) {
    List<String> pattern = new LinkedList();
    Set<String> keys = types.keySet();

    for (Type t: sig) {
      String p = null;
      for (String key: keys) {
        if (types.optString(key).equals(t)) {
          p = key;
          break;
        }
      }
      pattern.add(p==null ? "_" : p);
    }

    return pattern;
  }

  public boolean match(SootClass cls, String methodName, List<Type> sig) {
    for (int i =0;i < specs.length(); i++) {
      JSONObject spec = specs.optJSONObject(i);
      String specType = spec.getString("spec-type");

      if (specType.equals("include")) {
        if (matchSpec(spec, cls, methodName, sig)) return true;
      }
      else if (specType.equals("exclude")) {
        if (matchSpec(spec, cls, methodName, sig)) return false;
      }
    }
    return false;
  }

  private boolean matchSpec(JSONObject spec, SootClass cls, String mName, List<Type> sig) {
    if (spec.has("packages")) {
      JSONArray packages = spec.getJSONArray("packages");
      boolean packageMatches = false;
      for (int i=0; i< packages.length(); i++) {
        if (cls.getPackageName().startsWith(packages.getString(i))) {
          packageMatches = true;
          break;
        }
      }
      if (!packageMatches) return false;
    }

    if (!spec.has("all-class")) {
      String targetClassName = spec.optString("class");
      SootClass targetClass = Scene.v().getSootClass(targetClassName);

      if (!cls.equals(targetClass)) {
        if (spec.has("exact-class")) return false;
        if (!checkSubclass(cls, targetClass)) return false;
      }
    }

    if (spec.has("all-method")) return true;
    if (spec.has("methods")) {
      JSONArray sigPatterns = getSigPatterns(spec, mName);
      if (sigPatterns == null) return false;

      JSONArray sigPattern = getSigPattern(sigPatterns, sig);
      if (sigPattern == null) return false;
      return true;
    }
    return false;
  }

  public JSONObject getClassSpec(SootClass receiver) {
    for (int i = 0; i < specs.length(); i++) {
      JSONObject spec = specs.optJSONObject(i);
      SootClass targetClass = Scene.v().getSootClass(spec.optString("class"));

      if (!receiver.equals(targetClass)) {
        if (spec.has("exact-class")) return null;
        if (checkSubclass(receiver, targetClass)) return spec;
      }
    }
    return null;
  }

  public JSONArray getSigPatterns(JSONObject spec, String methodName) {
    JSONArray methodSpecs = spec.optJSONArray("methods");
    for (int j=0; j<methodSpecs.length(); j++) {
      JSONObject methodSpec = methodSpecs.optJSONObject(j);
      if (methodSpec.optString("name").equals(methodName))
        return methodSpec.optJSONArray("signatures");
    }
    return null;
  }

  public JSONArray getSigPattern(JSONArray sigPatterns, List<Type> sig) {
    for (int k=0; k<sigPatterns.length(); k++) {
      JSONArray pattern = sigPatterns.optJSONArray(k);
      if (checkSignatureMatches(pattern, sig)) return pattern;
    }
    return null;
  }

  //check a <: b
  private boolean checkSubclass(SootClass a, SootClass b) {
    while (true) {
      if (a.equals(b)) return true;
      if (!a.hasSuperclass()) break;
      a = a.getSuperclass();
    }
    return false;
  }

  //check type-pattern and type mathces
  private boolean checkSignatureMatches(JSONArray sigPattern, List<Type> actualSig) {
    if (sigPattern.length() != actualSig.size()) return false;
    for (int i=0; i<sigPattern.length(); i++) {
      String pattern = sigPattern.optString(i);
      if (pattern.equals("_")) continue;

      pattern = types.optString(pattern);
      String type = actualSig.get(i).toString();
      if (!pattern.equals(type)) return false;
    }
    return true;
  }
}
