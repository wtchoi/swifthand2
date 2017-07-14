package smarthand.ui_explorer;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Created by wtchoi on 11/23/15.
 */

// a data-structure providing both list and set interface
public class Chain<Elt> {
  private List<Elt> _list = new LinkedList();
  private Set<Elt> _set = new HashSet();

  public boolean contains(Elt elt){
    return _set.contains(elt);
  }

  public boolean isEmpty() {
    return _set.isEmpty();
  }

  public void add(Elt elt) {
    if (_set.contains(elt)) return;

    _set.add(elt);
    _list.add(elt);
  }

  public int size() {
    return _set.size();
  }


}
