import java.lang.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;
import java.net.*;
import java.io.*;

public class RulePattern {
    // data
    private String prefix = null, A = null, B = null;
    
    // constructors
    public RulePattern (String w, String a, String b) {
	prefix = w;
	A = a;
	B = b;
    }
    
    public String getSourceName() {
        return A + prefix;
    }
    
    public String getTargetName() {
        return B;
    }
}
