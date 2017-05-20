package smarthand.ui_explorer.util;

import java.util.*;
import java.util.function.BiFunction;

/**
 * Created by wtchoi on 8/16/16.
 */

public class HashConsingFactory<T extends HashConsed> {
    private final BiFunction<Object,Object,Boolean> checkEqual;
    private final Observer<T> observer;

    private ArrayList<T> instances = new ArrayList<>();
    private HashMap<Handle<T>, T> table = new HashMap<>();


    // methods
    public HashConsingFactory(BiFunction<Object,Object,Boolean> checkEqual) {
        this.checkEqual = checkEqual;
        this.observer = null;
    }

    public HashConsingFactory(BiFunction<Object,Object,Boolean> checkEqual, Observer<T> observer) {
        this.checkEqual = checkEqual;
        this.observer = observer;
    }

    public T getInstance(T t) {
        Handle<T> handle = new Handle(t, this);
        if (table.containsKey(handle)) {
            return table.get(handle);
        }

        int id = instances.size();
        t.setId(id);
        instances.add(t);
        table.put(handle, t);

        if (observer != null) {
            observer.onRegister(t);
        }
        return t;
    }

    public T getById(int id) {
        return instances.get(id);
    }

    public int count() {
        return instances.size();
    }

    public Collection<T> getInstances() {
        return new LinkedList(instances);
    }


    // inner classes
    public interface Observer<T extends HashConsed> {
        void onRegister(T instance);
    }

    public static class InstanceDumper<T extends HashConsed> implements Observer<T> {
        private final String path;
        private final String prefix;

        public InstanceDumper(String path, String prefix) {
            this.path = path;
            this.prefix = prefix;
        }

        @Override
        public void onRegister(T aaui) {
            if (isImportant(aaui)) {
                String contents = getString(aaui);
                Util.dumpStringToFile(path, prefix + aaui.id(), contents);
            }
        }

        protected boolean isImportant(T aaui) {
            return true;
        }

        protected String getString(T aaui) {
            return aaui.toString();
        }
    }

    private static class Handle<T extends HashConsed> {
        private final T val;
        private final HashConsingFactory<T> factory;

        public Handle(T val, HashConsingFactory<T> f) {
            this.val = val;
            this.factory = f;
        }

        @Override
        public int hashCode() {
            return val.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof  Handle)) return false;
            return factory.checkEqual.apply(val, ((Handle)obj).val);
        }
    }
}
