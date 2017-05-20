package smarthand.ui_driver;

import android.view.accessibility.AccessibilityNodeInfo;
import com.android.uiautomator.core.UiCollection;
import com.android.uiautomator.core.UiObject;
import com.android.uiautomator.core.UiSelector;
import org.json.JSONArray;

import java.lang.reflect.Method;
import java.util.LinkedList;

/**
 * Created by wtchoi on 12/15/15.
 */

class UiUtil {
  public static AccessibilityNodeInfo getExtraInfo(UiObject uiobj) {
    try {
      Class cls = uiobj.getClass();
      Method m = cls.getDeclaredMethod("findAccessibilityNodeInfo", long.class);
      m.setAccessible(true);

      Object result = m.invoke(uiobj, 10 * 1000);
      return (AccessibilityNodeInfo) result;
    }
    catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }


}

public class UiTraverse {
  public static void traverseUI(UiCollection root, UIVisitor visitor) {
    try {
      AccessibilityNodeInfo nodeInfo = null;
      UiObject uiobj = null;

      int bound = root.getChildCount(new UiSelector().classNameMatches(".*"));
      int index = 0;
      try {
        do {
          uiobj = root.getChild(new UiSelector().classNameMatches(".*").instance(index++));
          nodeInfo = UiUtil.getExtraInfo(uiobj);
        } while (nodeInfo == null && index < bound);
      }
      catch (Exception e) {
        System.out.println(String.format("bound:%s  error index:%s", bound, index));
        System.out.println(e.toString());
        nodeInfo = null;
      }


      if (nodeInfo == null) {
        System.out.println("cannot find AccessibilityNodeInfo of " + uiobj);
        System.out.println(bound + " uiobjects are tried");
        return;
      }

      // step 1: get root node
      AccessibilityNodeInfo parent = nodeInfo.getParent();
      while (parent != null) {
        nodeInfo.recycle();
        nodeInfo = parent;
        parent = nodeInfo.getParent();
      }

      // step 2: start traversal from the root (json object)
      traverse(nodeInfo, visitor);
      nodeInfo.recycle();
    }
    catch(Exception e){
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  private static void traverse(AccessibilityNodeInfo node, UIVisitor visitor) {
    visitor.visitBegin(node);

    int childCount = node.getChildCount();
    if (childCount > 0) {
      visitor.visitChildrenBegin();

      JSONArray childrenSummary = new JSONArray();
      for (int i = 0; i < childCount; i++) {
        AccessibilityNodeInfo child = node.getChild(i);
        if (child == null) continue;
        if (child.getClassName() != null){
          UIVisitor childVisitor = visitor.visitChildBegin();
          traverse(child, childVisitor);
          visitor.visitChildEnd();
        }
        child.recycle();
      }
      visitor.visitChildrenEnd();
    }
    visitor.visitEnd();
  }
}

interface UIVisitor {
  // valid execution sequence ::= begin [childrenBegin [(childBegin childEnd)+] childrenEnd] end
  void visitBegin(AccessibilityNodeInfo node);
  void visitChildrenBegin();
  UIVisitor visitChildBegin();
  void visitChildEnd();
  void visitChildrenEnd();
  void visitEnd();
}

class UIVisitorChain implements UIVisitor {
  public LinkedList<UIVisitor> visitors;

  public UIVisitorChain(LinkedList<UIVisitor> visitors) {
    this.visitors = visitors;
  }

  public void visitBegin(AccessibilityNodeInfo node) {
    for (UIVisitor v: visitors) {
      v.visitBegin(node);
    }
  }

  public void visitChildrenBegin() {
    for (UIVisitor v: visitors) {
      v.visitChildrenBegin();
    }
  }

  public UIVisitor visitChildBegin() {
    LinkedList<UIVisitor> children = new LinkedList<UIVisitor>();
    for (UIVisitor v:visitors) {
      children.add(v.visitChildBegin());
    }
    return new UIVisitorChain(children);
  }

  public void visitChildEnd() {
    for (UIVisitor v:visitors) {
      v.visitChildEnd();
    }
  }

  public void visitChildrenEnd() {
    for (UIVisitor v:visitors) {
      v.visitChildrenEnd();
    }
  }

  public void visitEnd() {
    for (UIVisitor v:visitors) {
      v.visitEnd();
    }
  }
}