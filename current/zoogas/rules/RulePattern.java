package zoogas.rules;

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
}
