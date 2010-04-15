package zoogas.core.rules;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import zoogas.core.Thunk;

public class TransformRulePattern extends RulePattern {
    // data
    String dir = null, C = null, D = null, V = null;
    private double probability = 0;
    Vector<BondPattern> optionalLhsBond = null, requiredLhsBond = null, excludedLhsBond = null, rhsBond = null;

    public ArrayList<String> cThunkChunks = null;
    public ArrayList<Thunk<TransformRuleMatch.TransformRuleMatchResult, String>> cThunkVars = null;
    public ArrayList<String> dThunkChunks = null;
    public ArrayList<Thunk<TransformRuleMatch.TransformRuleMatchResult, String>> dThunkVars = null;

    // constructor
    // w: subject prefix
    // dir: direction
    // a, b: LHS regexes to be matched (source & target patterns)
    // c, d: RHS back-referenced substitutions to be generated (source & target updates)
    // p, v: probability & verb attributes

    public TransformRulePattern(String w, String dir, String a, String b, String c, String d, double p, String v) {
        super(w, a, b);
        if (dir != null && dir.length() > 0)
            this.dir = dir;
        C = c;
        D = d;
        probability = p;
        V = v;

        cThunkChunks = new ArrayList<String>();
        cThunkVars = new ArrayList<Thunk<TransformRuleMatch.TransformRuleMatchResult, String>>();
        dThunkChunks = new ArrayList<String>();
        dThunkVars = new ArrayList<Thunk<TransformRuleMatch.TransformRuleMatchResult, String>>();

        prepareVariables();
    }

    public double getProbability() {
        return probability;
    }

    // wrappers to add bonds

    public void addRequiredLhsBonds(String[] b) {
        requiredLhsBond = addBonds(requiredLhsBond, b);
    }

    public void addOptionalLhsBonds(String[] b) {
        optionalLhsBond = addBonds(optionalLhsBond, b);
    }

    public void addExcludedLhsBonds(String[] b) {
        excludedLhsBond = addBonds(excludedLhsBond, b);
    }

    public void addRhsBonds(String[] b) {
        rhsBond = addBonds(rhsBond, b);
    }

    private Vector<BondPattern> addBonds(Vector<BondPattern> bondVec, String[] b) {
        if (bondVec == null)
            bondVec = new Vector<BondPattern>(b.length);
        for (int n = 0; n < b.length; ++n)
            bondVec.add(BondPattern.fromString(b[n]));
        return bondVec;
    }

    protected final String prepareVariables() {
        StringBuffer sb = new StringBuffer();
        try {
            Pattern macroPattern = Pattern.compile("\\$(S|T|\\d+|[\\+\\-]\\d*\\.?\\d+|%\\d+\\+\\d*\\.?\\d+)");
            Matcher m = macroPattern.matcher(C);
            int index = 0;
            while (m.find()) {
                String g = m.group(1);
                cThunkChunks.add(C.substring(index, m.start()));
                cThunkVars.add(createThunk(g));
                index = m.end();
            }
            cThunkChunks.add(C.substring(index));

            m = macroPattern.matcher(D);
            index = 0;
            while (m.find()) {
                String g = m.group(1);
                dThunkChunks.add(D.substring(index, m.start()));
                dThunkVars.add(createThunk(g));
                index = m.end();
            }
            dThunkChunks.add(D.substring(index));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    private Thunk<TransformRuleMatch.TransformRuleMatchResult, String> createThunk(String str) {
        switch(str.charAt(0)) {
            case 'S':
                return new Thunk<TransformRuleMatch.TransformRuleMatchResult, String>() {
                    public String process(TransformRuleMatch.TransformRuleMatchResult input) {
                        return input.A;
                    }
                };
            case 'T':
                return new Thunk<TransformRuleMatch.TransformRuleMatchResult, String>() {
                    public String process(TransformRuleMatch.TransformRuleMatchResult input) {
                        return input.B;
                    }
                };
            case '+':
                return createIncThunk(str);
            case '-':
                return createDecThunk(str);
            case '%':
                return createModThunk(str);
            default:
                if(Character.isDigit(str.charAt(0))) {
                    final int index = Integer.valueOf(str);
                    return new Thunk<TransformRuleMatch.TransformRuleMatchResult, String>() {
                        public String process(TransformRuleMatch.TransformRuleMatchResult input) {
                            return input.getGroup(index);
                        }
                    };
                }
        }
        return new Thunk<TransformRuleMatch.TransformRuleMatchResult, String>() {
            public String process(TransformRuleMatch.TransformRuleMatchResult input) {
                return "";
            }
        };
    }

    private Thunk<TransformRuleMatch.TransformRuleMatchResult, String> createIncThunk(String str) {
        Matcher m = Pattern.compile("\\+(\\d*)\\.?([1-9]\\d*)").matcher(str);
        if(m.find())
            try {
                final int inc = m.group(1).length() > 0 ? Integer.valueOf(m.group(1)) : 1;
                final int g = Integer.valueOf(m.group(2));
                
                return new Thunk<TransformRuleMatch.TransformRuleMatchResult, String>() {
                    public String process(TransformRuleMatch.TransformRuleMatchResult input) {
                        int n = Integer.valueOf(input.getGroup(g));
                        return String.valueOf(n - inc);
                    }
                };
            }
            catch(Exception e) {
                System.err.println(str + " " + m.matches() + " " + m.groupCount());
                e.printStackTrace();
                return null;
            }
        return null;
    }

    private Thunk<TransformRuleMatch.TransformRuleMatchResult, String> createDecThunk(String str) {
        Matcher m = Pattern.compile("\\-(\\d*)\\.?([1-9]\\d*)").matcher(str);
        if(m.find())
            try {
                final int inc = m.group(1).length() > 0 ? Integer.valueOf(m.group(1)) : 1;
                final int g = Integer.valueOf(m.group(2));
                
                return new Thunk<TransformRuleMatch.TransformRuleMatchResult, String>() {
                    public String process(TransformRuleMatch.TransformRuleMatchResult input) {
                        int n = Integer.valueOf(input.getGroup(g));
                        if (n >= inc)
                            return String.valueOf(n - inc);
                        return "";
                    }
                };
            }
            catch(Exception e) {
                System.err.println(str + " " + m.matches() + " " + m.groupCount());
                e.printStackTrace();
                return null;
            }
        return null;
    }
    
    private Thunk<TransformRuleMatch.TransformRuleMatchResult, String> createModThunk(String str) {
        Matcher m = Pattern.compile("%([1-9]\\d*)\\+(\\d*)\\.?([1-9]\\d*)").matcher(str);
        if(m.find())
            try {
                final int mod = Integer.valueOf(m.group(1));
                final int delta = m.group(1).length() > 0 ? Integer.valueOf(m.group(2)) : 0;
                final int g = Integer.valueOf(m.group(3));
                
                return new Thunk<TransformRuleMatch.TransformRuleMatchResult, String>() {
                    public String process(TransformRuleMatch.TransformRuleMatchResult input) {
                        int n = Integer.valueOf(input.getGroup(g));
                        return String.valueOf((n + delta) % mod);
                    }
                };
            }
            catch(Exception e) {
                System.err.println(str + " " + m.matches() + " " + m.groupCount());
                e.printStackTrace();
                return null;
            }
        return null;
    }
}
