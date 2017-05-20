package smarthand.ui_explorer.util;

/**
 * Created by wtchoi on 8/22/16.
 *
 * The base class for hash-consed objects.
 * This is designed to be used with HashConsingFactory class.
 */
public abstract class HashConsed {
    public final int hashcode;
    private int id;

    public HashConsed(int hashcode) {
        this.hashcode = hashcode;
    }

    @Override
    public final boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public final int hashCode() {
        return hashcode;
    }
    public final int id() { return id; }

    final void setId(int id) {
        this.id = id;
    }
}
