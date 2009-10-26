import java.lang.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;
import java.awt.*;
import java.net.*;
import java.io.*;


// Patterns are to be transmitted in a "Particle definition" packet with the following structure:
// ParticlePatterns (one per line, format "NOUN NameRegex R G B", describing appearances of Particles to which this definition packet applies)
// RulePatterns (one per line, format "VERB A B C D P V" - see RuleMatch.java for semantics)

public class PatternSet {
    // data
    private Vector<EnergyRulePattern> energyRulePattern = new Vector<EnergyRulePattern>();
    private Vector<TransformRulePattern> transformRulePattern = new Vector<TransformRulePattern>();
    private Vector<ParticlePattern> particlePattern = new Vector<ParticlePattern>();

    // method to lay down a template for a Particle
    void addParticlePattern (String patternString, Color col) {
	particlePattern.add (new ParticlePattern(patternString,col));
    }

    // method to lay down a template for a rule
    void addRulePattern (String patternString) {
	transformRulePattern.add (TransformRulePattern.fromString(patternString));
    }

    // method to get a Particle from the Board object or create and add one
    Particle getOrCreateParticle (String particleName, Board board) {
	// look for existing particle
	Particle p = board.getParticleByName (particleName);
	// if no such particle, look for a pattern that matches this particle
	for (int n = 0; p == null && n < particlePattern.size(); ++n) {
	    ParticlePattern pp = (ParticlePattern) particlePattern.get(n);
	    p = pp.makeParticle(particleName,board,this);  // returns null if fails to match
	}
	// if still no such particle, create a bright white default with this PatternSet 
	if (p == null)
	    p = new Particle (particleName, Color.white, board, this);
	// return
	return p;
    }

    // method to compile transformation rules for a new target Particle
    RandomVariable<ParticlePair> compileTransformRules (int dir, Particle source, Particle target, Board board) {
	//	System.err.println ("Compiling transformation rules for " + source.name + " " + target.name);
	RandomVariable<ParticlePair> rv = new RandomVariable<ParticlePair>();
	if (source.patternTemplate[dir] == null)
	    source.patternTemplate[dir] = getSourceTransformRules (source.name, board, dir);
	for (int n = 0; n < source.patternTemplate[dir].length; ++n) {
	    TransformRuleMatch rm = source.patternTemplate[dir][n];
	    //	    System.err.println ("Pair " + source.name + " " + target.name + " : trying to match " + source.patternTemplate[dir][n].pattern);
	    if (rm.bindTarget(target.name)) {
		Particle
		    newSource = board.getOrCreateParticle (rm.C()),
		    newTarget = board.getOrCreateParticle (rm.D());
		//		System.err.println ("Pair " + source.name + " " + target.name + " : matched " + source.patternTemplate[dir][n].pattern);
		if (newSource == null || newTarget == null) {
		    System.err.println ("Null outcome of rule '" + rm.pattern + "': "
					+ source.name + " " + target.name + " -> "
					+ (newSource == null ? ("[null: " + rm.C() + "]") : newSource.name) + " "
					+ (newTarget == null ? ("[null: " + rm.D() + "]") : newTarget.name));
		} else {
		    ParticlePair pp = new ParticlePair (newSource, newTarget);
		    rv.add (pp, rm.P());
		}
	    }
	    rm.unbindTarget();
	}
	rv.close (new ParticlePair (source, target));
	return rv;
    }

    // helper to get a set of transformation rules with the source bound to a given Particle
    protected TransformRuleMatch[] getSourceTransformRules (String particleName, Board board, int dir) {
	//	System.err.println ("Trying to match particle " + particleName + " to transformation rule generators");
	Vector<TransformRuleMatch> v = new Vector<TransformRuleMatch>();
	for (int n = 0; n < transformRulePattern.size(); ++n) {
	    //	    System.err.println ("Trying to match particle " + particleName + " to transformation rule " + (RulePattern) transformRulePattern.get(n));
	    TransformRuleMatch rm = new TransformRuleMatch (transformRulePattern.get(n), board, dir, particleName);
	    if (rm.matches())
		v.add (rm);
	}
	TransformRuleMatch[] rm = new TransformRuleMatch[v.size()];
	for (int n = 0; n < v.size(); ++n)
	    rm[n] = v.get(n);
	return rm;
    }

    // method to compile energy rules for a new target Particle
    double compileEnergyRules (Particle source, Particle target) {
	//	System.err.println ("Compiling energy rules for " + source.name + " " + target.name);
	double E = 0;
	if (source.energyTemplate == null)
	    source.energyTemplate = getSourceEnergyRules (source.name);
	for (int n = 0; n < source.energyTemplate.length; ++n) {
	    EnergyRuleMatch rm = source.energyTemplate[n];
	    //		    System.err.println ("Particle " + source.name + ": trying to match " + source.energyTemplate[n]);
	    if (rm.bindTarget(target.name))
		E += rm.E();
	    rm.unbindTarget();
	}
	return E;
    }

    // helper to get a set of energy rules with the source bound to a given Particle
    protected EnergyRuleMatch[] getSourceEnergyRules (String particleName) {
	//	System.err.println ("Trying to match particle " + particleName + " to energy rule generators");
	Vector<EnergyRuleMatch> v = new Vector<EnergyRuleMatch>();
	for (int n = 0; n < energyRulePattern.size(); ++n) {
	    //	    System.err.println ("Trying to match particle " + particleName + " to energy rule " + (RulePattern) energyRulePattern.get(n));
	    EnergyRuleMatch rm = new EnergyRuleMatch (energyRulePattern.get(n), particleName);
	    if (rm.matches())
		v.add (rm);
	}
	EnergyRuleMatch[] rm = new EnergyRuleMatch[v.size()];
	for (int n = 0; n < v.size(); ++n)
	    rm[n] = v.get(n);
	return rm;
    }

    // i/o
    void toStream (OutputStream out) {
	PrintStream print = new PrintStream(out);
	for (Enumeration e = particlePattern.elements(); e.hasMoreElements() ;)
	    print.println ("NOUN " + (e.nextElement()).toString());
	for (Enumeration e = transformRulePattern.elements(); e.hasMoreElements() ;)
	    print.println ("VERB " + (e.nextElement()).toString());
	for (Enumeration e = energyRulePattern.elements(); e.hasMoreElements() ;)
	    print.println ("ENERGY " + (e.nextElement()).toString());
	print.println ("END");
    }


    void toFile(String filename) {
	try {
	    FileOutputStream fos = new FileOutputStream(filename);
	    toStream (fos);
	    fos.close();
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    static PatternSet fromStream (InputStream in) {
	PatternSet ps = new PatternSet();
	InputStreamReader read = new InputStreamReader(in);
	BufferedReader buff = new BufferedReader(read);
	Pattern nounRegex = Pattern.compile("NOUN (.*)");
	Pattern verbRegex = Pattern.compile("VERB (.*)");
	Pattern energyRegex = Pattern.compile("ENERGY (.*)");
	Pattern endRegex = Pattern.compile("END.*");
	Pattern commentRegex = Pattern.compile(" *#.*");
	try {
	    while (buff.ready()) {
		String s = buff.readLine();
		Matcher m = null;
		if (commentRegex.matcher(s).matches()) {
		    continue;
		} else if ((m = nounRegex.matcher(s)).matches()) {
		    ps.particlePattern.add (new ParticlePattern(m.group(1)));
		} else if ((m = verbRegex.matcher(s)).matches()) {
		    ps.transformRulePattern.add (TransformRulePattern.fromString(m.group(1)));
		} else if ((m = energyRegex.matcher(s)).matches()) {
		    ps.energyRulePattern.add (EnergyRulePattern.fromString(m.group(1)));
		} else if (endRegex.matcher(s).matches()) {
		    break;
		}
	    }
	} catch (IOException e) {
	    e.printStackTrace();
	}
	return ps;
    }

    static PatternSet fromFile (String filename) {
	try {
	    FileInputStream fis = new FileInputStream(filename);
	    return fromStream(fis);
	} catch (Exception e) {
	    e.printStackTrace();
	}
	return null;
    }
}
