package smarthand.preprocess;

import pxb.android.axml.AxmlVisitor;
import pxb.android.axml.NodeVisitor;

/**
 * Created by wtchoi on 10/30/15.
 */
class AxmlTransformer extends AxmlVisitor {
  public static final String androidNS = "http://schemas.android.com/apk/res/android";

  boolean noInstrumentation = false;

  boolean permissionHandled = false;
  boolean appClassDefined = false;
  String appClassName;
  String mainActivityClassName;
  String pkgName;

  public AxmlTransformer(AxmlVisitor visitor) {
    super(visitor);
  }
  public AxmlTransformer() {
    super();
  }

  @Override
  public NodeVisitor visitFirst(String namespace, String name) {
    NodeVisitor nv = super.visitFirst(namespace, name);
    return new NodeTransformer(namespace, name, null, this, nv);
  }

  public void setNoInstrumentation(boolean flag) {
    this.noInstrumentation = flag;
  }
}

