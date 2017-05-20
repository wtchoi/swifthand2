package smarthand.ui_driver;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Created by wtchoi on 12/15/15.
 */
class SummarizingVisitor implements UIVisitor {
  private JSONObject summary = new JSONObject();
  private JSONArray children;
  private SummarizingVisitor currentChild;

  UIVisitor chain;

  public void visitBegin(AccessibilityNodeInfo node) {
    try {
      summary.put("class", node.getClassName());
      summary.put("hash", node.hashCode());

      summary.put("enabled", String.valueOf(node.isEnabled()));
      if (node.isCheckable()) summary.put("checked", String.valueOf(node.isChecked()));
      if (node.isSelected()) summary.put("selected", true);
      if (node.isFocusable()) summary.put("focused", true);
      if (node.isAccessibilityFocused()) summary.put("afocused", true);
      if (node.isEnabled() && (node.isClickable() || node.isCheckable() || node.isScrollable()))
        summary.put("actionable", true);

      CharSequence text = node.getText();
      if (text != null) summary.put("text", text.toString());


      Rect rect = new Rect();
      node.getBoundsInScreen(rect);
      summary.put("bound", rect.left + ":" + rect.top + "-" + rect.right + ":" + rect.bottom);
    }
    catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  public void visitChildrenBegin() {
    children = new JSONArray();
  }

  public UIVisitor visitChildBegin() {
    currentChild = new SummarizingVisitor();
    return currentChild;
  }

  public void visitChildEnd() {
    children.put(((SummarizingVisitor)currentChild).summary);
    currentChild = null;
  }

  public void visitChildrenEnd() {
    try {
      summary.put("children", children);
      children = null;
    }
    catch (Exception e){
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  public void visitEnd() { /*NOP*/ }

  public JSONObject getSummary() {
    return summary;
  }
}
