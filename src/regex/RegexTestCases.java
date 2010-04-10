package regex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class RegexTestCases {
    public static void main(String[] args) {
        //testBacktracking();
        //testMemoryUsage();
        //testSpeed();
        //testCorrectness();
        //testMap();
        testGrouping();
        //System.gc();
    }

    public static void testBacktracking() {
        int n = 14;
        String pattern = "";
        String stringToMatch = "";
        for(int i = 0; i < n; ++i) {
            pattern += "a*";
            stringToMatch += "a";
        }
        pattern += stringToMatch;
        RegexSet set = null;
        System.gc();
        long time = System.currentTimeMillis();
        set = new RegexSet();
        set.add(pattern);
        set.printDebug();
        System.out.println("RegexTrie contains " + set.contains(stringToMatch));
        System.out.println("Pattern matches " + Pattern.compile(pattern).matcher(stringToMatch).matches());
    }

    public static void testMemoryUsage() {
        long membefore = 0xFFFFFFFF;
        long memafter = 0xFFFFFFFF;
        Runtime rt = Runtime.getRuntime();
        Set set;

        System.gc();
        membefore = rt.maxMemory() - rt.getRuntime().freeMemory();

        set = new RegexSet();

        System.gc();
        System.gc();
        System.gc();
        System.gc();
        memafter = rt.maxMemory() - rt.getRuntime().freeMemory();

        System.out.println(memafter - membefore);
    }

    public static void testSpeed() {
        
    }

    public static void testCorrectness() {
        RegexSet set;

        // sanity check?
        set = new RegexSet();
        set.add("abc");
        assert set.contains("abc");
        set.add("a*a*a*aaa");
        assert !set.contains("aa");
        assert set.contains("aaaa");

        // check foo versus foo* versus (foo)*
        set = new RegexSet();
        set.add("foo");
        assert set.contains("foo");
        assert !set.contains("fooo");
        set = new RegexSet();
        set.add("foo*");
        assert set.contains("fo");
        assert set.contains("fooooooo");
        set = new RegexSet();
        set.add("(foo)*");
        assert set.contains("foofoo");
        assert set.contains("");
        assert !set.contains("fo");

        // check for weird nested postfixes
        set = new RegexSet();
        set.add("(((foo(b(a)+r)*)+(baz)?)*)");
        assert set.contains("");
        assert set.contains("foobaaaaaaaaaaarbazfoobaz");
        assert !set.contains("fo");
        
        // Special chars
        set = new RegexSet();
        set.add("\\d");
        set.printDebug();
        assert set.contains("1");
    }

    private static void testMap() {
        RegexMap map = new RegexMap();
        //map.put("\\D*", "bar");
        //map.put("\\S*", "baz");
        map.put("((.*))", "foo");
        map.printDebug();
        //System.out.println(map.get(""));
    }
    
    public static void testGrouping(){
        RegexMap map = new RegexMap();
        map.put("(\\D*)(\\d*)", "foo");
        map.put("(.*)", "bar");
        map.printDebug();
        
        printValuesAndGroups(map.getWithGrouping("abcd0123"));
        
    }
    
    public static void printValuesAndGroups(ArrayList<RegexNode.Tuple<Object, String[]>> match) {
        for(int i = 1; i <= match.size(); ++i) {
            System.out.println(i + (i%10==1 ? "st" : (i%10==2 ? "nd" : (i%10==3 ? "rd" : "th"))) + " value returned:" + match.get(i-1).car());
            System.out.println(" Groups:");
            for(int j = 0; j < match.get(i-1).cdr().length; ++j){
                System.out.println("  " + j + " " + match.get(i-1).cdr()[j]);
            }
        }
    }
}