package regex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RegexMap implements Map<String, Object> {
    public RegexMap() {
        super();
        root = new RegexNode();
    }

    RegexNode root;

    public int size() {
        return 0;
    }
    public boolean isEmpty() {
        return false;
    }
    public boolean containsKey(Object key) {
        return false;
    }
    public boolean containsValue(Object value) {
        return false;
    }
    public Set<Object> get(Object key) {
        if (!(key instanceof String))
            return null;
        return root.get((String)key, 0);
    }
    public ArrayList<RegexNode.Tuple<Object, String[]>> getWithGrouping(Object key) {
        if (!(key instanceof String))
            return null;
        return root.getWithGrouping((String)key);
    }
    public Object put(String key, Object value) {
        root.put(key, value);
        return value;
    }
    public Object remove(Object key) {
        return null;
    }
    public void putAll(Map m) {
    }
    public void clear() {
    }
    public Set keySet() {
        return Collections.emptySet();
    }
    public Collection values() {
        return Collections.emptySet();
    }
    public Set entrySet() {
        return Collections.emptySet();
    }

    public void printDebug() {
        root.printDebug();
    }
}
