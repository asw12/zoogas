package zoogas.core.rules;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import zoogas.core.topology.Topology;

public class RulePattern {
    // data
    private String prefix = null;
    protected String A = null;
    protected String B = null;

    // constructors
    public RulePattern(String w, String a, String b) {
        prefix = w;
        A = a;
        B = b;
    }

    public final String getSourceName() {
        return A;
    }

    public final String getTargetName() {
        return B;
    }
    
    // expansion of direction macros: $F, $B, $L, $R, $+L, etc.
    final Pattern dirPattern = Pattern.compile("\\$(F|B|L|R|\\+L|\\+\\+L|\\+R|\\+\\+R)");
    protected String expandDir(String s, Topology topology, int dir) {
        Matcher m = dirPattern.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String var = m.group(1);
            int nbrs = topology.neighborhoodSize();
            if (var.equals("F"))
                m.appendReplacement(sb, topology.dirString(dir));
            else if (var.equals("B"))
                m.appendReplacement(sb, topology.dirString((dir + nbrs / 2) % nbrs));
            else if (var.equals("L"))
                m.appendReplacement(sb, topology.dirString((dir + nbrs - 1) % nbrs));
            else if (var.equals("+L"))
                m.appendReplacement(sb, topology.dirString((dir + nbrs - 2) % nbrs));
            else if (var.equals("++L"))
                m.appendReplacement(sb, topology.dirString((dir + nbrs - 3) % nbrs));
            else if (var.equals("R"))
                m.appendReplacement(sb, topology.dirString((dir + 1) % nbrs));
            else if (var.equals("+R"))
                m.appendReplacement(sb, topology.dirString((dir + 2) % nbrs));
            else if (var.equals("++R"))
                m.appendReplacement(sb, topology.dirString((dir + 3) % nbrs));
        }
        m.appendTail(sb);
        return sb.toString();
    }
    
    public final String regexA(Topology topology, int dir) {
        return expandDir(getSourceName(), topology, dir);
    }

    public final String regexB(Topology topology, int dir) {
        return expandDir(getTargetName(), topology, dir);
    }
}
