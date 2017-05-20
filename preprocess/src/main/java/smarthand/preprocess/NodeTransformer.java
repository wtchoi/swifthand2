package smarthand.preprocess;

import pxb.android.axml.AxmlVisitor;
import pxb.android.axml.NodeVisitor;

/**
 * Created by wtchoi on 12/1/15.
 */
class NodeTransformer extends NodeVisitor {
  private AxmlTransformer root;
  private NodeTransformer parent;
  private String namespace;
  private String name;
  private Object aux;

  public static final String androidNS = AxmlTransformer.androidNS;

  private boolean hasActionMainAttr = false;
  private boolean hasCategoryLauncherAttr = false;

  public NodeTransformer(String namespace, String name, NodeTransformer parent, AxmlTransformer root, NodeVisitor nv) {
    super(nv);
    this.namespace = namespace;
    this.name = name;
    this.parent = parent;
    this.root = root;
  }

  @Override
  public NodeVisitor visitChild(String namespace, String name) {
    NodeVisitor nv = super.visitChild(namespace, name);
    return new NodeTransformer(namespace, name, this, root, nv);
  }

  @Override
  public void visitContentAttr(String namespace, String name, int resourceId, int type, Object obj) {
    super.visitContentAttr(namespace, name, resourceId, type, obj);

    //check internet permission
    if(this.name.equals("uses-permission")
        && namespace.equals(androidNS)
        && name.equals("name")
        && ((String)obj).equals("android.permission.INTERNET")) {
      root.permissionHandled = true;
    }

    //check application
    if(this.name.equals("application")
        && namespace.equals(androidNS)
        && name.equals("name")){
      root.appClassDefined = true;
      root.appClassName = (String)obj;
    }

    //check entry points
    if(this.parent != null && this.parent.name.equals("intent-filter")) {
      if(this.name.equals("action")
          && namespace.equals(androidNS)
          && name.equals("name")
          && ((String) obj).equals("android.intent.action.MAIN")) {
        parent.parent.hasActionMainAttr = true;
      }
      if(this.name.equals("category")
          && namespace.equals(androidNS)
          && name.equals("name")
          && ((String) obj).equals("android.intent.category.LAUNCHER")) {
        parent.parent.hasCategoryLauncherAttr = true;
      }
    }

    // check main activity class
    if(this.name.equals("activity")
        && namespace.equals(androidNS)
        && name.equals("name")){
      aux = obj;
    }

    // check package name
    if(this.name.equals("manifest")
        && name.equals("package")) {
      root.pkgName = (String)obj;
    }
  }

  @Override
  public void visitEnd(){
    // Add entry to manifest
    if (!root.noInstrumentation) {
      if (name.equals("manifest")) {
        if (!root.permissionHandled) {
          root.permissionHandled = true;
          NodeVisitor nv = visitChild(null, "uses-permission");
          if (nv != null) {
            nv.visitBegin();
            nv.visitContentAttr(androidNS, "name", 0x01010003, AxmlVisitor.TYPE_STRING, "android.permission.INTERNET");
            nv.visitContentEnd();
            nv.visitEnd();
          }
        }
      }
    }

    super.visitEnd();

    //check
    if(this.name.equals("activity") && hasActionMainAttr && hasCategoryLauncherAttr){
      root.mainActivityClassName = (String)this.aux;
    }
  }
}
