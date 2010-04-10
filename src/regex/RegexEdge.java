package regex;

public class RegexEdge implements Comparable {
    public RegexEdge(String pattern, RegexNode node) {
        this.pattern = pattern;
        this.node = node;
    }
    public RegexEdge(String pattern, RegexNode node, EdgeType type) {
        this.pattern = pattern;
        this.node = node;
        setEdgeType(type);
    }

    private String pattern = null;
    private RegexNode node = null;
    private EdgeType type = EdgeType.GENERIC;

    enum EdgeType {
        GENERIC,
        PAREN_GROUP, // [] or ()
        BRACKET_GROUP,
        STAR, // *
        PLUS, // +
        REPEAT,
        QUESTION_MARK; // ?
    }

    public void setEdgeType(EdgeType type) {
        this.type = type;
    }
    public EdgeType getEdgeType() {
        return type;
    }

    public boolean matches(String key, int index) {
        //System.out.println(key + " " + pattern + " " + index + " " + getNode());
        if(key.length() < index + getEdgeLength()) {
        //if (key.length() < index + pattern.length()) {
            //System.out.println("\t failed");
            return false;
        }
        else {
            for (int i = index, patternIndex = 0; i < key.length() && patternIndex < pattern.length(); ++i, ++patternIndex) {
                char patChar = pattern.charAt(patternIndex);
                char keyChar = key.charAt(i);
                switch (pattern.charAt(patternIndex)) {
                    case '.':
                        continue;
                    case '$':
                        // match this as a variable
                        continue;
                    case '\\':
                        ++patternIndex;
                        switch (pattern.charAt(patternIndex)) {
                            case 's':
                                if (!Character.isWhitespace(keyChar))
                                    return false;
                                break;
                            case 'S':
                                if (Character.isWhitespace(keyChar))
                                    return false;
                                break;
                            case 'd':
                                if (!Character.isDigit(keyChar))
                                    return false;
                                break;
                            case 'D':
                                if (Character.isDigit(keyChar))
                                    return false;
                                break;
                            default:
                                break;
                        }
                        break;
                    default:
                        if (patChar != keyChar) {
                            return false;
                        }
                        break;
                }
            }

            return true;
            // */
        }
    }

    /**
     *Returns the length of the partial match from the start of both Strings
     * @param key
     * @return
     */
    public int partMatches(String key, EdgeType type) {
        //if(!type.equals(this.type))
        if (!this.type.equals(type.GENERIC)) {
            return 0;
        }
        if (pattern.length() == 0) {
            System.err.println("pattern has length zero");
        }

        int i = 0;
        for (; i < key.length() && i < pattern.length(); ++i) {
            if (key.charAt(i) != pattern.charAt(i)) {
                if (type == EdgeType.GENERIC) // for a generic edge, it is OK to cut this edge into two and insert a new node
                    break;
                else // non-generic edges cannot be cut without breaking something, so return false
                    return 0;
            }
        }
        return i;
    }

    /**
     *Returns the length of the pattern.
     * @return
     */
    public int getEdgeLength() {
        return pattern.length() - (pattern.split("\\\\").length - 1);
    }

    public String getPattern() {
        return pattern;
    }

    public RegexNode getNode() {
        return node;
    }

    /**
     *Returns true if the pattern starts with key.
     * @param key
     * @return
     */
    public boolean startsWith(String key) {
        return pattern.startsWith(key);
    }

    public int compareTo(Object o) {
        if (o instanceof RegexEdge) {
            RegexEdge other = (RegexEdge)o;
            if (type.equals(other.type)) {
                int temp = pattern.compareTo(other.pattern);
                if(temp != 0)
                    return temp;
                else {
                    return (hashCode() - other.hashCode()); // compareTo must NOT return 0
                }
            }
            else {
                return type.compareTo(other.type);
            }
        }
        else {
            return -1;
        }
    }
    
    public static class ParenRegexEdge extends RegexEdge{
        public ParenRegexEdge(String pattern, RegexNode node, RegexNode endsAt) {
            super(pattern, node, EdgeType.PAREN_GROUP);
            this.endsAt = endsAt;
        }
        
        RegexNode endsAt = null;
        
        public RegexNode getEndNode(){
            return endsAt;
        }
    }
}
