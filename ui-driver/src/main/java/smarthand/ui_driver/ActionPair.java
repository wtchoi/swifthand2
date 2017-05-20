package smarthand.ui_driver;

/**
 * Created by wtchoi on 10/10/15.
 */
class ActionPair {
  String actionName;
  Action action;

  ActionPair(String actionName, Action action) {
    this.actionName = actionName;
    this.action = action;
  }
}
