package regex;

import java.lang.reflect.Array;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class RegexNode {
    public RegexNode() {
        this(null);
    }
    public RegexNode(Object value) {
        this.value = value;

        edges = new TreeSet<RegexEdge>();
    }

    Object value = null;
    SortedSet<RegexEdge> edges = null;
    int numValues = 0;

    /**
     *Adds a value into the set with this node as root using a pattern string, key.
     * @param key
     * @param value
     * @return
     */
    public boolean put(String key, Object value) {
        if (value == null) {
            System.err.println("Cannot add null value; null is reserved in this implementation.");
            return false;
        }
        Tuple<RegexNode, Integer> values = getNextNode(key, 0);
        RegexNode node = values.car();

        if (node.value == null) {
            node.value = value;
            return true;
        }
        else {
            node.value = value;
            return false;
        }
    }

    /**
     *Returns the newKey and type
     * @param key
     * @param index
     * @return
     */
    private final Tuple<String, Tuple<RegexEdge.EdgeType, Integer>> getNextEdge(String key, int index) {
        //System.out.println("getNextEdge called: key " + key + " index " + index);
        RegexEdge.EdgeType type = RegexEdge.EdgeType.GENERIC;
        String newKey = "";

        // Greedily increase newKey's length
        int i = index;
        boolean fastfail = false;
        for (; i < key.length() && !fastfail; ++i) {
            char c = key.charAt(i);
            // handle prefix
            if (isPreFix(c)) {
                // don't handle a prefix unless it is the first character in newKey
                if (i == index) {
                    switch (c) {
                        case '[':
                            type = type.BRACKET_GROUP;
                            newKey = key.substring(index + 1, getClosingBrace(key, index));
                            i += newKey.length() + 2; // for the two parenthesis
                            fastfail = true;
                            --i;
                            continue;
                        case '(':
                            type = type.PAREN_GROUP;
                            newKey = key.substring(index + 1, getClosingBrace(key, index));
                            i += newKey.length() + 2; // for the two parenthesis
                            fastfail = true;
                            --i;
                            continue;
                        case '\\':
                            newKey += c;
                            break;
                        default:
                            System.err.println("Unhandled prefix");
                            break;
                    }
                }
                else
                    break;
            }
            // handle generic
            else if (!isPostFix(key.charAt(i))) {
                // always add last character
                if (i == key.length() - 1) {
                    newKey += c;
                    ++i;
                    break;
                }
                // not first character and next character is a postfix
                else if (isPostFix(key.charAt(i + 1)) && i != index && !(i == index + 1 && key.charAt(index) == '\\')) {
                    break;
                }
                else {
                    newKey += c;
                }
            }
            else {
                break;
            }
        }

        // return these
        return new Tuple<String, Tuple<RegexEdge.EdgeType, Integer>>(newKey, new Tuple<RegexEdge.EdgeType, Integer>(type, i));
    }

    /**
     *Gets the closing brace which matches the open brace at index
     * @param
     * @return
     */
    private static final int getClosingBrace(String key, int index) {
        Stack<Character> braces = new Stack<Character>();
        braces.push(key.charAt(index));

        while (braces.size() > 0) {
            ++index;
            if (index >= key.length()) {
                throw new PatternSyntaxException("Closing brace not found", key, index);
            }
            char c = key.charAt(index);
            if (isEnclosureChar(c)) {
                switch (c) {
                    case '(':
                    case '{':
                    case '[':
                        if (braces.peek() == '[' || braces.peek() == '{')
                            throw new PatternSyntaxException("Open brace not allowed here", key, index);
                        braces.push(c);
                        break;
                    case ')':
                    case '}':
                    case ']':
                        if (braces.peek() == getOpposingBrace(c)) {
                            braces.pop();
                        }
                        else
                            throw new PatternSyntaxException("Closing brace not found", key, index);
                        break;
                    default:
                        break;

                }
            }
        }

        return index;
    }

    /**
     *Gets the next node for putting values in
     * @param key
     * @param index
     * @return Returns <node, index> Tuple
     */
    private Tuple<RegexNode, Integer> getNextNode(final String key, final int index) {
        // since this is for putting values in, increment numValues
        ++numValues;

        if (index >= key.length()) {
            return new Tuple<RegexNode, Integer>(this, index);
        }

        RegexEdge.EdgeType type = null;
        String newKey = null;

        // Find out what the edge should look like

        // set edge
        Tuple<String, Tuple<RegexEdge.EdgeType, Integer>> edgeVals = getNextEdge(key, index);
        newKey = edgeVals.car();
        type = edgeVals.cdr().car();
        int newIndex = edgeVals.cdr().cdr();
        //System.out.println("getNextEdge returned: " + newKey + " " + type + " Indices (old, new): (" + index + "," + newIndex + ")");
        // getNextEdge did not find a valid point at which to add a new edge
        if (newIndex == index) {
            throw new PatternSyntaxException("Invalid syntax? newKey is " + newKey, key, index);
        }

        RegexNode node = null;
        RegexNode returnedNode = null;
        RegexEdge edge = null;
        //System.out.println(newKey);

        // handle enclosures and post fixes
        // enclosures
        boolean handleEnclosure = false;
        if (type != RegexEdge.EdgeType.GENERIC) {
            // parse the new key to see if this edge can be simplified
            String newKeyParsed = getNextEdge(newKey, 0).car();
            if (newKeyParsed.length() != newKey.length()) {
                node = new RegexNode();
                returnedNode = node.getNextNode(newKey, 0).car();
                newKey = "";
                handleEnclosure = true;
            }
        }

        // peek for postfix
        if (newIndex < key.length() && isPostFix(key.charAt(newIndex))) {
            switch (key.charAt(newIndex)) {
                case '*':
                    type = RegexEdge.EdgeType.STAR;
                    break;
                case '+':
                    type = RegexEdge.EdgeType.PLUS;
                    break;
                case '?':
                    type = RegexEdge.EdgeType.QUESTION_MARK;
                    break;
                default:
                    System.err.println("Unhandled postfix operator " + key.charAt(newIndex));
                    throw new PatternSyntaxException("", key, newIndex);
            }
            ++newIndex;
        }

        // Now get or create this node

        // Find an edge that already exists
        boolean addNewEdge = true;
        for (RegexEdge e : edges) {
            if (e.getPattern().length() == 0)
                continue;

            int partMatch = e.partMatches(newKey, type);
            if (partMatch == e.getEdgeLength()) {
                node = e.getNode();
                newIndex = partMatch;
                break;
            }
        }

        // Create an edge if one has not been found
        if (addNewEdge) {
            if (node == null)
                node = new RegexNode();
            switch (type) {
                case GENERIC:
                    edge = new RegexEdge(newKey, node, type);
                    break;
                case STAR:
                    if (!handleEnclosure) {
                        node.edges.add(new RegexEdge(newKey, node, type));
                        edge = new RegexEdge("", node, type);
                    }
                    else {
                        returnedNode.edges.add(new RegexEdge("", node, type));
                        edge = new RegexEdge("", returnedNode, type);
                    }
                    break;
                case PLUS:
                    if (!handleEnclosure) {
                        node.edges.add(new RegexEdge(newKey, node, type));
                        edge = new RegexEdge(newKey, node, type);
                    }
                    else {
                        returnedNode.edges.add(new RegexEdge("", node, type));
                        edge = new RegexEdge("", node, type);
                    }

                    break;
                case QUESTION_MARK:
                    // add two edges to the new node
                    edges.add(new RegexEdge(newKey, node, type));
                    edge = new RegexEdge("", handleEnclosure ? returnedNode : node, type);
                    break;
                case PAREN_GROUP:
                    //edge = new RegexEdge(newKey, node, type);
                    // TODO: hacky?
                    edge = new RegexEdge.ParenRegexEdge(newKey, node, handleEnclosure ? returnedNode : node);
                    break;
                default:
                    System.err.println("Unhandled edge type " + type);
                    edge = new RegexEdge(newKey, node, type);
                    break;
            }

            edges.add(edge);
        }

        if (returnedNode == null) {
            return node.getNextNode(key, newIndex);
        }
        else {
            return returnedNode.getNextNode(key, newIndex);
        }
    }

    /**
     *Get
     * @param key
     * @param index
     * @return
     */
    public Set<Object> get(String key, int index) {
        Set<Object> set = null;
        if (index == key.length()) {
            if (value != null) {
                set = new HashSet<Object>();
                set.add(value);
            }
        }

        for (RegexEdge edge : edges) {
            if (edge.matches(key, index)) {
                Set<Object> value = edge.getNode().get(key, index + edge.getEdgeLength());
                if (value != null) {
                    if (set == null)
                        set = value;
                    else {
                        set.addAll(value);
                    }

                    if (set.size() >= numValues)
                        break;
                }
            }
        }

        return set;
    }

    public ArrayList<Tuple<Object, String[]>> getWithGrouping(String key) {
        return getWithGrouping(key, 0, new HashMap<RegexNode, List<Group>>(), 0);
    }

    /**
     *
     * @param key
     * @param index
     * @param groups
     * @return a Tuple<ArrayList<Object>, HashMap<, >>
     *  Values returned and groupings should be separate
     */
    private ArrayList<Tuple<Object, String[]>> getWithGrouping(String key, int index, Map<RegexNode, List<Group>> grouping, int groupNumber) {
        // handle groupings at close paren
        if(grouping.containsKey(this)) {
            List<Group> list = grouping.get(this);
            for(Group group : list) {
                group.setEndIndex(index);
            }
        }
        
        ArrayList<Tuple<Object, String[]>> returnList = null;
        if (index == key.length()) {
            if (value != null) {
                returnList = new ArrayList<Tuple<Object, String[]>>();
                
                // Process prepared groups
                String[] groupStrings = new String[groupNumber];
                for(List<Group> list : grouping.values()) {
                    for(Group group : list) {
                        try{
                            groupStrings[group.groupNumber] = group.getStringFromKey(key);
                        }
                        catch(IndexOutOfBoundsException e) {
                            System.err.println(groupNumber + " " + group.groupNumber);
                            e.printStackTrace();
                        }
                    }
                }
                returnList.add(new Tuple<Object, String[]>(value, groupStrings));
            }
        }

        for (RegexEdge edge : edges) {
            if (edge.matches(key, index)) {
                boolean incGroupNumber = false;
                Map<RegexNode, List<Group>> groupingClone = cloneGrouping(grouping);
                // handle grouping at open paren
                if (edge instanceof RegexEdge.ParenRegexEdge) {
                    incGroupNumber = true;

                    RegexNode nodeAtCloseParen = ((RegexEdge.ParenRegexEdge)edge).getEndNode();
                    if(groupingClone.containsKey(nodeAtCloseParen)) {
                        groupingClone.get(nodeAtCloseParen).add(new Group(groupNumber, index));
                    }
                    else {
                        groupingClone.put(nodeAtCloseParen, new ArrayList<Group>(Collections.singletonList(new Group(groupNumber, index))));
                    }
                }

                ArrayList<Tuple<Object, String[]>> value = edge.getNode().getWithGrouping(key, index + edge.getEdgeLength(), groupingClone, groupNumber + (incGroupNumber ? 1 : 0));
                if (value != null) {
                    if (returnList == null)
                        returnList = value;
                    else {
                        returnList.addAll(value);
                        
                        /*System.out.println(this);

                        for(Tuple<Object, String[]> tuple : returnList) {
                            System.out.println(" " + tuple.car());
                            for(String s : tuple.cdr()) {
                                System.out.println("  " + s);
                            }
                        }*/
                    }

                    if (returnList.size() >= numValues)
                        break;
                }
            }
        }

        return returnList;
    }

    public boolean contains(String key, int index) {
        if (index == key.length()) {
            if (value != null) {
                return true;
            }
        }

        for (RegexEdge edge : edges) {
            if (edge.matches(key, index)) {
                if (edge.getNode().contains(key, index + edge.getEdgeLength()))
                    return true;
            }
        }

        return true;
    }

    public void printDebug() {
        HashSet<RegexNode> visited = new HashSet<RegexNode>();
        visited.add(this);
        printDebug(0, visited);
    }
    protected void printDebug(int level, HashSet<RegexNode> visited) {
        char[] spacers = new char[level];
        Arrays.fill(spacers, ' ');
        for (int i = 0; i < spacers.length; i += 2) {
            spacers[i] = '|';
        }
        String indentation = new String(spacers);

        System.out.println(indentation + "VERTEX " + this + " numValues " + numValues);
        if (value != null) {
            System.out.println(indentation + "VALUE " + value);
        }
        for (RegexEdge edge : edges) {
            RegexNode vertex = edge.getNode();
            if (!visited.contains(vertex)) {
                System.out.println(indentation + "*EDGE: PATTERN '" + edge.getPattern() + "' TYPE '" + edge.getEdgeType() + "' TO " + vertex + (edge instanceof RegexEdge.ParenRegexEdge ? " (close group at " + ((RegexEdge.ParenRegexEdge)edge).getEndNode() + ")" : ""));
                visited.add(vertex);
                vertex.printDebug(level + 2, visited);
            }
            else {
                System.out.println(indentation + "*EDGE: PATTERN '" + edge.getPattern() + "' TYPE '" + edge.getEdgeType() + "' TO " + (vertex == this ? "SELF" : vertex + " (see above)"));
            }
        }
    }

    /**
     *
     * @param c
     * @return
     * @Deprecated
     */
    public static final boolean isPreFix(char c) {
        switch (c) {
            case '(':
            case '[':
            case '\\':
                return true;
            default:
                return false;
        }
    }

    public static final boolean isPostFix(char c) {
        switch (c) {
            case '?':
            case '*':
            case '+':
            case ')':
                //case '}':
                //case ']':
                return true;
            default:
                return false;
        }
    }
    public static final boolean isEnclosureChar(char c) {
        switch (c) {
            case '(':
            case '{':
            case '[':
            case ')':
            case '}':
            case ']':
                return true;
            default:
                return false;
        }
    }
    public static final char getOpposingBrace(char c) {
        switch (c) {
            case '(':
                return ')';
            case '{':
                return '}';
            case '[':
                return ']';
            case ')':
                return '(';
            case '}':
                return '{';
            case ']':
                return '[';
            default:
                return 0;
        }
    }

    protected void finalize() throws Throwable {
        super.finalize();
    }

    // TODO: find a way to make this private
    public final class Tuple<CAR, CDR> {
        public Tuple(CAR car, CDR cdr) {
            this.car = car;
            this.cdr = cdr;
        }

        private CAR car = null;
        private CDR cdr = null;

        public CAR car() {
            return car;
        }

        public CDR cdr() {
            return cdr;
        }
    }
    
    private Map<RegexNode, List<Group>> cloneGrouping(Map<RegexNode, List<Group>> grouping) {
        HashMap<RegexNode, List<Group>> clone = new HashMap<RegexNode, List<Group>>();
        for(RegexNode rn : grouping.keySet()) {
            List<Group> list = grouping.get(rn);
            List<Group> clonelist = new ArrayList<Group>(list.size());
            for(Group g : list) {
                clonelist.add(g.clone());
            }
            clone.put(rn, clonelist);
        }
        return clone;
    }
    
    private final class Group {
        public Group(int groupNumber, int startIndex) {
            this.groupNumber = groupNumber;
            this.startIndex = startIndex;
        }
        
        int groupNumber = 0;
        int startIndex = -1;
        int endIndex = -1;
        
        public void setEndIndex(int endIndex) {
            this.endIndex = endIndex;
        }

        public String getStringFromKey(String key) {
            return key.substring(startIndex, endIndex);
        }
        
        public Group clone() {
            Group clone = new Group(groupNumber, startIndex);
            clone.endIndex = endIndex;
            return clone;
        }
    }
}
