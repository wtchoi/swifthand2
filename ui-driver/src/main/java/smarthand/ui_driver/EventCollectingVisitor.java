package smarthand.ui_driver;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import com.android.uiautomator.core.UiDevice;

import java.rmi.server.UID;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * Created by wtchoi on 12/15/15.
 */
public class EventCollectingVisitor implements UIVisitor{
  private LinkedList<String> elist;
  private int childIndex;
  private EventCollectingVisitor currentChild;

  HashMap<Integer, String> hashToPath;

  String path;
  boolean hasEditText = false;
  boolean hasScrollable = false;

  EventCollectingVisitor(LinkedList<String> elist, String path, HashMap<Integer, String> hashToPath) {
    this.elist = elist;
    this.path = path;
    this.hashToPath = hashToPath;
  }

  private boolean visible(Rect bound, int width, int height) {
    if (bound.right < 0
        || bound.bottom < 0
        || bound.left >= width
        || bound.top >= height) {
      return false;
    }
    return true;
  }

  private boolean isTiny(int l, int r, int u, int d) {
    if (r == l) return true;
    if (u == d) return true;
    return false;
  }

  public void visitBegin(AccessibilityNodeInfo node) {
    int hash = node.hashCode();
    hashToPath.put(hash, path);

    if (node.isEnabled()) {
      Rect bound = new Rect();
      node.getBoundsInScreen(bound);

      int width = UiDevice.getInstance().getDisplayWidth();
      int height = UiDevice.getInstance().getDisplayHeight();

      if (!visible(bound, width, height)) return;

      int l = (bound.left < 0) ? 0 : bound.left;
      int r = (bound.right >= width) ? (width-1) : bound.right;
      int u = (bound.top < 0) ? 0 : bound.top;
      int d = (bound.bottom >= height) ? (height-1) : bound.bottom;
      if (isTiny(l,r,u,d)) return;
      if (l > r) return;
      if (u > d) return;

      int x = (l + r) / 2;
      int y = (u + d) / 2;

      int index = elist.size() + 1;
      String className = node.getClassName().toString();

      if (node.isCheckable()) {
        boolean checked = node.isChecked();
        elist.addLast("dcheck" + ":" + className + ":" + hash + ":" + path + ":" + x + ":" + y + ":" + checked);
      } else if (node.isClickable()) {
        if (className.equals("android.widget.EditText") ) {
          hasEditText = true;
        }
        else if(!className.equals("android.webkit.WebView") && !className.equals("android.widget.ListView")) {
          elist.addLast("dclick" + ":" + className + ":" + hash + ":" + path + ":" + x + ":" + y);
          elist.addLast("dlong" + ":" + className + ":" + hash + ":" + path + ":" + x + ":" + y);
        }
      }

      if (node.isScrollable() && !className.equals("android.widget.Spinner")) {
        hasScrollable = true;
      }
    }
  }

  public void visitChildrenBegin() {
    childIndex = 0;
  }

  public UIVisitor visitChildBegin() {
    currentChild = new EventCollectingVisitor(elist, path + "." + (childIndex++), hashToPath);
    return currentChild;
  }

  public void visitChildEnd() {
    hasEditText = hasEditText || currentChild.hasEditText;
    hasScrollable = hasScrollable || currentChild.hasScrollable;
    currentChild = null;
  }

  public void visitChildrenEnd() {}
  public void visitEnd() {}
}
