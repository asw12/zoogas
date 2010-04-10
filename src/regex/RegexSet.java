package regex;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;

public class RegexSet implements Set<String> {
    public RegexSet() {
        root = new RegexNode();
    }

    RegexNode root = null;

    public int size() {
        return 0;
    }
    public boolean isEmpty() {
        return false;
    }
    public boolean contains(Object o) {
        if (!(o instanceof String)) {
            throw new ClassCastException(o + " is not an instance of String");
        }

        //return root.contains((String)o, 0);
        return root.get((String)o, 0) != null;
    }
    public Iterator<String> iterator() {
        return null;
    }
    public Object[] toArray() {
        return new Object[0];
    }
    public Object[] toArray(Object[] a) {
        return new Object[0];
    }
    public boolean add(String e) {
        //return root.put(e, true);
        root.put(e, true);
        return true;
    }
    public boolean remove(Object o) {
        return false;
    }
    public boolean containsAll(Collection<?> c) {
        return false;
    }
    public boolean addAll(Collection<? extends String> c) {
        boolean changed = false;

        for (String str : c) {
            if (add(str))
                changed = true;
        }

        return changed;
    }
    public boolean retainAll(Collection<?> c) {
        return false;
    }
    public boolean removeAll(Collection<?> c) {
        return false;
    }
    public void clear() {
    }

    public void printDebug() {
        root.printDebug();
    }
}
