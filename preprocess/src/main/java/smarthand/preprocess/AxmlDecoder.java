package smarthand.preprocess;

import pxb.android.axml.AxmlVisitor;
import pxb.android.axml.NodeVisitor;

/**
 * Created by wtchoi on 10/30/15.
 */
class AxmlDecoder extends AxmlVisitor {
  private static String androidNS = "http://schemas.android.com/apk/res/android";

  public AxmlDecoder(AxmlVisitor visitor) {
    super(visitor);
  }

  public AxmlDecoder() {
    super();
  }

  boolean appClassDefined = false;
  String appClassName;
  String mainActivityClassName;
  String pkgName;

  public static class NodeDecoder extends NodeVisitor {
    private AxmlDecoder root;
    private NodeDecoder parent;
    private String namespace;
    private String name;
    private Object aux;

    private static boolean permissionHandled = false;

    private boolean hasActionMainAttr = false;
    private boolean hasCategoryLauncherAttr = false;

    public NodeDecoder(String namespace, String name, NodeDecoder parent, AxmlDecoder root, NodeVisitor nv) {
      super(nv);
      this.namespace = namespace;
      this.name = name;
      this.parent = parent;
      this.root = root;
    }

    @Override
    public NodeVisitor visitChild(String namespace, String name) {
      NodeVisitor nv = super.visitChild(namespace, name);
      return new NodeDecoder(namespace, name, this, root, nv);
    }

    @Override
    public void visitContentAttr(String namespace, String name, int resourceId, int type, Object obj) {
      //check internet permission
      super.visitContentAttr(namespace, name, resourceId, type, obj);
      if(this.name.compareTo("uses-permission") == 0
          && namespace.compareTo(androidNS) == 0
          && name.compareTo("name") == 0
          && ((String)obj).compareTo("android.permission.INTERNET") == 0) {
        permissionHandled = true;
      }

      //check application
      if(this.name.compareTo("application") == 0
          && namespace.compareTo(androidNS) == 0
          && name.compareTo("name") == 0){
        root.appClassDefined = true;
        root.appClassName = (String)obj;
      }

      //check main activity
      if(this.parent != null && this.parent.name.compareTo("intent-filter") == 0) {
        if(this.name.compareTo("action") == 0
            && namespace.compareTo(androidNS) == 0
            && name.compareTo("name") == 0
            && ((String) obj).compareTo("android.intent.action.MAIN") == 0) {
          parent.parent.hasActionMainAttr = true;
        }
        if(this.name.compareTo("category") == 0
            && namespace.compareTo(androidNS) == 0
            && name.compareTo("name") == 0
            && ((String) obj).compareTo("android.intent.category.LAUNCHER") == 0) {
          parent.parent.hasCategoryLauncherAttr = true;
        }
      }

      if(this.name.compareTo("activity") == 0
          && namespace.compareTo(androidNS) == 0
          && name.compareTo("name") == 0){
        aux = obj;
      }

      if(this.name.compareTo("manifest") == 0
          && name.compareTo("package") == 0) {
        root.pkgName = (String)obj;
      }
    }

    @Override
    public void visitEnd(){
      if(name.compareTo("manifest") == 0 ) {
        if(!permissionHandled){
          permissionHandled = true;
          NodeVisitor nv = visitChild(null, "uses-permission");
          if(nv != null){
            nv.visitBegin();
            nv.visitContentAttr(androidNS,"name", 0x01010003, AxmlVisitor.TYPE_STRING, "android.permission.INTERNET");
            nv.visitContentEnd();
            nv.visitEnd();
          }
        }
      }
      super.visitEnd();

      //check
      if(this.name.compareTo("activity") == 0 && hasActionMainAttr && hasCategoryLauncherAttr){
        root.mainActivityClassName = (String)this.aux;
      }
    }
  }

  @Override
  public NodeVisitor visitFirst(String namespace, String name) {
    NodeVisitor nv = super.visitFirst(namespace, name);
    return new NodeDecoder(namespace, name, null, this, nv);
  }
}
