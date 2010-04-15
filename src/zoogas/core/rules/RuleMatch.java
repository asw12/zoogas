package zoogas.core.rules;

import java.util.regex.*;

import zoogas.core.topology.Topology;

// RuleMatch - a partially- or fully-bound RulePattern.
public class RuleMatch {
    // data
    protected RulePattern pattern = null;
    protected Topology topology = null;
    private int dir = -1;
    protected Pattern aPattern = null;
    protected Pattern bPattern = null;

    // classes

    class AlreadyBoundException extends RuntimeException {
        AlreadyBoundException() {
            super("Attempt to bind already-bound rule");
        }
    }

    // constructors

    public RuleMatch(RulePattern p) {
        pattern = p;
    }

    public RuleMatch(RulePattern p, Topology topology, int dir) {
        this(p);
        bindDir(topology, dir);
    }

    // lhs methods

    public boolean bindDir(Topology topology, int d) {
        if (!dirBound()) {
            this.topology = topology;
            dir = d;
            aPattern = Pattern.compile(pattern.regexA(topology, d));
            bPattern = Pattern.compile(pattern.regexB(topology, d));
            return true;
        }
        throw new AlreadyBoundException();
    }

    // versions of matches() that bind temporarily, then unbind

    public final boolean matchesSource(String a) {
        return aPattern.matcher(a).matches();
    }

    // methods to test if the rule is fully or partly bound

    public final boolean dirBound() {
        return dir >= 0;
    }

    // expanded pattern methods
    // keep regexB() independent of regexA() as it greatly simplifies optimization
    //  (although it was nice to have backreference expressive capability for AB...)

    public RuleMatchResult match(String source, String target) {
        return new RuleMatchResult(source, target);
    }

    final Pattern modGroupPattern = Pattern.compile("%([1-9]\\d*)\\+(\\d*)\\.?([1-9]\\d*)");
    final Pattern decGroupPattern = Pattern.compile("\\-(\\d*)\\.?([1-9]\\d*)");
    final Pattern incGroupPattern = Pattern.compile("\\+(\\d*)\\.?([1-9]\\d*)");

    // TODO: move this to another file

    public class RuleMatchResult {
        public RuleMatchResult(String source, String target) {
            if (bindSource(source)) {
                bindTarget(target);
            }
        }

        protected String A = null, B = null;
        protected Matcher am = null, bm = null;
        private boolean aMatched = false, abMatched = false;

        private final boolean bindSource(String a) {
            A = a;
            am = aPattern.matcher(A);
            aMatched = am.matches();
            return aMatched;
        }

        private final boolean bindTarget(String b) {
            if (aMatched) {
                B = b;
                bm = bPattern.matcher(B);
                abMatched = bm.matches();
                return matches();
            }
            return false;
        }

        // matches() returns true if the rule has matched *so far*

        public boolean matches() {
            return abMatched;
        }

        // helper method to get a group ($1,$2,...) from AB
        String getGroup(String group) {
            try {
                int n = new Integer(group).intValue();
                if (n <= am.groupCount())
                    return am.group(n);
                else if (n <= am.groupCount() + bm.groupCount())
                    return bm.group(n - am.groupCount());
                else {
                    throw new Exception("While trying to get group $" + group + " matching " + A + " " + B + " to " + aPattern.pattern() + " " + bPattern.pattern());
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                return "";
            }
        }

        
    }
}
