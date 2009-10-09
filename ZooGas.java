import java.lang.*;
import java.util.*;
import java.text.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.net.*;
import java.io.*;
import javax.swing.JFrame;
import javax.imageio.ImageIO;

public class ZooGas extends JFrame implements MouseListener, KeyListener {

    // simulation particle params
    int size = 128;  // size of board in cells
    int species = 18;  // number of species
    int aversion = 1;  // number of species that species will consider too similar to prey on
    int omnivorousness = 11;  // number of species that each species can prey on
    double lifeRate = .1;  // probability of moving, preying, choking or spawning
    double forageEfficiency = .8;  // probability that predation leads successfully to breeding
    double chokeRate = .05;  // probability of dying due to overcrowding
    double birthRate = .02;  // probability of breeding
    double guestMoveRate = .01;  // move rate of zoo guests

    // tool particle params
    int wallDecayStates = 5;
    double playDecayRate = .006;  // speed of decay events that drive gameplay
    double buriedWallDecayRate = .18, exposedWallDecayRate = .22;  // probability of wall decay when buried/exposed
    double cementSetRate = .2, cementStickRate = .9;  // probability of cement setting into wall (or sticking to existing wall)
    double gasDispersalRate = .1;  // probability that a gas particle will disappear
    double gasMultiplyRate = .2;  // probability that a surviving fecundity gas particle will multiply (gives illusion of pressure)
    double lavaSeedRate = .01;  // probability that lava will stick to a wall particle (it always sticks to basalt)
    double lavaFlowRate = .3;  // probability that lava will take a random step
    int mutateRange = 2;  // range of species change due to contact w/mutator gas

    // initial conditions
    String initImageFilename = "TheZoo.bmp";  // if non-null, initialization loads a seed image from this filename
    // String initImageFilename = null;
    double initialDensity = .1;  // initial density of species-containing cells
    int initialDiversity = 3;  // initial number of species (can be increased with mutator gas)

    // view
    int pixelsPerCell = 4;  // width & height of each cell in pixels
    int boardSize;  // width & height of board in pixels
    int popChartHeight = 100, popBarHeight = 4, entropyBarHeight = 20, statusBarHeight;  // size in pixels of various parts of the status bar (below the board)
    int toolKeyWidth = 16, toolReserveBarWidth = 100, toolHeight = 30, toolBarWidth;  // size in pixels of various parts of the tool bar (right of the board)

    // tools
    int sprayDiameter, sprayPower;  // diameter & power of spraypaint tool
    Map sprayRefillRate = new IdentityHashMap(), sprayReserve = new IdentityHashMap(), sprayMax = new IdentityHashMap();

    // cheat c0d3z
    String cheatString = "boosh";
    int cheatStringPos = 0;
    boolean cheating() { return cheatStringPos == cheatString.length(); }

    // commentator code ("well done"-type messages)
    int boardUpdateCount = 0;
    int[] timeFirstTrue = new int[100];   // indexed by row: tracks the first time when various conditions are true, so that the messages flash at first

    // networking
    BoardServer boardServer = null;  // board servers field UDP requests for cross-border interactions
    ConnectionServer connectServer = null;   // connectionServer runs over TCP (otherwise requests to establish connections can get lost amongst the flood of UDP traffic)
    int boardServerPort = 4444;
    String localhost = null;

    // cellular automata state dictionary
    Map nameToParticle = new HashMap();
    Vector particleVec = new Vector();

    // constant helper vars
    Particle spaceParticle, cementParticle, acidParticle, fecundityParticle, mutatorParticle, lavaParticle, basaltParticle, tripwireParticle, guestParticle;
    Particle[] wallParticle, speciesParticle;
    int patternMatchesPerRefresh;

    // main board data
    Cell[][] cell;
    HashMap remoteCell;  // map of connections from off-board Point's to RemoteCellCoord's

    // random number generator
    Random rnd;

    // Swing
    Insets insets;
    BufferStrategy bufferStrategy;
    Graphics bfGraphics;
    Cursor boardCursor, normalCursor;

    // helper objects
    Point cursorPos;  // co-ordinates of cell beneath current mouse position
    boolean mouseDown;  // true if mouse is currently down
    boolean randomPressed;  // true if 'randomize' button was pressed (randomize the model once only)
    boolean mixPressed;  // true if 'mix' button was pressed (model as a perfectly-mixed gas, i.e. with no spatial fluctuations, using Gillespie algorithm)
    Particle sprayParticle;  // current spray particle

    int histXPos = 0;  // current x-position of sweeping population graph

    double entropy;  // current entropy score (defined in terms of *relative* species populations)
    double bestEntropy;  // best entropy so far
    double maxEntropy;  // max possible entropy
    double minEntropyOverCycle;  // lowest entropy over a complete "cycle" of the population histogram
    double bestMinEntropyOverCycle;  // best "lowest entropy" score

    // main()
    public static void main(String[] args) {
	// create ZooGas
	ZooGas gas = null;
	switch (args.length)
	    {
	    case 0:
		gas = new ZooGas();  // standalone
		break;
	    case 1:
		gas = new ZooGas(new Integer(args[0]).intValue());  // server on specified port
		break;
	    case 3:
		gas = new ZooGas(new Integer(args[0]).intValue(), new InetSocketAddress (args[1], new Integer(args[2]).intValue()));  // client, connecting to server at specified address/port
		break;
	    default:
		System.err.println ("Usage: <progname> [<port> [<remote address> <remote port>]]");
		break;
	    }

	// run it
	gas.gameLoop();
    }

    // networked constructor (client)
    public ZooGas (int port, InetSocketAddress remote) {
	this(port);

	connectBorder (new Point(0,0), new Point(-1,0), new Point(0,1), 128, new Point(-size,0), remote);  // west
	connectBorder (new Point(127,0), new Point(128,0), new Point(0,1), 128, new Point(+size,0), remote);  // east
	connectBorder (new Point(0,0), new Point(0,-1), new Point(1,0), 128, new Point(0,-size), remote);  // north
	connectBorder (new Point(0,127), new Point(0,128), new Point(1,0), 128, new Point(0,+size), remote);  // south
    }

    // networked constructor (server)
    public ZooGas (int port) {
	this();
	this.boardServerPort = port;

	try {
	    boardServer = new BoardServer (this, boardServerPort);
	    boardServer.start();

	    connectServer = new ConnectionServer (this, boardServerPort);
	    connectServer.start();

	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    // default constructor
    public ZooGas() {

	// set helpers, etc.
	rnd = new Random();
	cell = new Cell[size][size];
	for (int x = 0; x < size; ++x)
	    for (int y = 0; y < size; ++y)
		cell[x][y] = new Cell();
	remoteCell = new HashMap();
	boardSize = size * pixelsPerCell;

	patternMatchesPerRefresh = (int) (size * size);

	// init particles
	spaceParticle = newCellType ("/", Color.black);  // empty space
	speciesParticle = new Particle[species];
	for (int s = 0; s < species; ++s)
	    speciesParticle[s] = newCellType ("critter/" + (s+1), Color.getHSBColor ((float) s / (float) (species+1), 1, 1));

	wallParticle = new Particle[wallDecayStates];
	for (int w = 1; w <= wallDecayStates; ++w) {
	    float gray = (float) w / (float) (wallDecayStates + 1);
	    wallParticle[w-1] = newCellType ("wall/" + w, new Color (gray, gray, gray));  // walls (in various sequential states of decay)
	}
	cementParticle = newCellType ("cement", Color.white);  // cement (drifts; sets into wall)

	float gasHue = (float) species / (float) (species+1);
	acidParticle = newCellType ("acid", Color.darkGray);  // acid (destroys most things; dissolves basalt into lava)
	fecundityParticle = newCellType ("perfume", Color.getHSBColor (gasHue, (float) .5, (float) .5));  // fecundity gas (multiplies; makes animals breed)
	mutatorParticle = newCellType ("mutator", Color.getHSBColor (gasHue, (float) .5, (float) 1));  // mutator gas (converts animals into nearby species)
	lavaParticle = newCellType ("lava", Color.lightGray);  // lava (drifts; sets into basalt)
	basaltParticle = newCellType ("wall/basalt", Color.orange);  // basalt
	tripwireParticle = newCellType ("/tripwire", new Color(1,1,1));  // tripwire (an invisible, static particle that animals will eat; use as a subtle test of whether animals have escaped)
	guestParticle = newCellType ("zoo_guest", new Color(254,254,254));  // guest (a visible, mobile particle that animals will eat; use as a test of whether animals have escaped)

	// call method to add probabilistic pattern-matching replacement rules
	addPatterns();

	// "close" all patterns, adding a do-nothing rule for patterns whose RHS probabilities sum to <1
	for (int c = 0; c < particleTypes(); ++c) {
	    Particle source = getParticleByNumber(c);
	    Iterator iter = source.pattern.entrySet().iterator();
	    while (iter.hasNext()) {
		Map.Entry keyval = (Map.Entry)iter.next();
		Particle target = (Particle) keyval.getKey();
		//		System.err.println ("Closing pattern " + source.name + " " + target.name + " -> ...");
		RandomVariable rv =  (RandomVariable) keyval.getValue();
		if (rv != null)
		    rv.close(new ParticlePair (source, target));
	    }
	}

	// init board
	boolean boardInitialized = false;
	if (initImageFilename != null) {

	    try {
		BufferedImage img = ImageIO.read(new File(initImageFilename));
		initBoard (img);
		boardInitialized = true;

	    } catch (IOException e) {
		e.printStackTrace();
	    }
	}

	if (!boardInitialized)  // fallback: randomize board
	    for (int x = 0; x < size; ++x)
		for (int y = 0; y < size; ++y)
		    if (rnd.nextDouble() < initialDensity) {
			int s = rnd.nextInt(initialDiversity) * (species/initialDiversity);
			Particle p = speciesParticle[s];
			cell[x][y].particle = p;
		    } else
			cell[x][y].particle = spaceParticle;

	// init cell counts
	for (int x = 0; x < size; ++x)
	    for (int y = 0; y < size; ++y) {
		++cell[x][y].particle.count;
	    }

	// init spray tools
	initSprayTools();

	// init view
	statusBarHeight = popChartHeight + popBarHeight * (species + 1) + entropyBarHeight;
	toolBarWidth = toolKeyWidth + toolReserveBarWidth;

	entropy = log2(initialDiversity);
	bestEntropy = minEntropyOverCycle = bestMinEntropyOverCycle = entropy;
	maxEntropy = log2(species);

	// init JFrame
	setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	setResizable(false);
	setVisible(true);

	// set size
	insets = getInsets();
	setSize(boardSize + toolBarWidth + insets.left + insets.right,boardSize + statusBarHeight + insets.top + insets.bottom);

	// init double buffering
	createBufferStrategy(2);
	bufferStrategy = getBufferStrategy();
	bfGraphics = bufferStrategy.getDrawGraphics();
	bfGraphics.translate (insets.left, insets.top);

	// create cursors
	boardCursor = new Cursor(Cursor.HAND_CURSOR);
	normalCursor = new Cursor(Cursor.DEFAULT_CURSOR);

	// register for mouse & keyboard events
	cursorPos = new Point();
	mouseDown = false;
	randomPressed = false;
	mixPressed = false;

        addMouseListener(this);
        addKeyListener(this);

	// net init
	try {
	    localhost = InetAddress.getLocalHost().getHostAddress();
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    private void initBoard (BufferedImage img) {
	for (int x = 0; x < size; ++x)
	    for (int y = 0; y < size; ++y) {
		int c = img.getRGB(x,y);
		int red = (c & 0x00ff0000) >> 16;
		int green = (c & 0x0000ff00) >> 8;
		int blue = c & 0x000000ff;

		// find state with closest color
		int dmin = 0;
		Particle s = null;
		for (int t = 0; t < particleTypes(); ++t) {
		    Particle pt = getParticleByNumber(t);
		    Color ct = pt.color;
		    int rdist = red - ct.getRed(), gdist = green - ct.getGreen(), bdist = blue - ct.getBlue();
		    int dist = rdist*rdist + gdist*gdist + bdist*bdist;
		    if (s == null || dist < dmin) {
			s = pt;
			dmin = dist;
			if (dist == 0)
			    break;
		    }
		}
		cell[x][y].particle = s;
	    }
    }

    // builder method for cell types
    private Particle newCellType (String name, Color color) {
	Particle p = new Particle (name, color);
	nameToParticle.put (name, p);
	particleVec.add (p);
	return p;
    }

    public Particle getParticleByName (String name) {
	return (Particle) nameToParticle.get (name);
    }

    private Particle getParticleByNumber (int n) {
	return (Particle) particleVec.get (n);
    }

    private int particleTypes() {
	return particleVec.size();
    }

    // builder method for patterns
    private void addPatterns() {

	// For now, we add all production rules manually.
	// Here is a hypothetical syntax for regexp-based pattern generators:
	//  A B C D E
	// where
	//  A is a regexp that must match the old source state
	//  B is a regexp that must match the old target state
	//  C is a string that will expand to the new source state
	//  D is a string that will expand to the new target state
	//  E is a numeric constant that is the probability of the rule

	// The following "special variables" will be expanded in {A,B,C,D} as appropriate:
	//  $1,$2,$3... => groups in A and B regexps
	//  $S,$T => full names for old source,target states
	//  $F,$L,$R,$B => directions relative to neighbor direction ($F=forward, $L=left, $R=right, $B=back)

	// A matching rule should overwrite any previously matched rules, allowing us to create exceptions
	// (e.g. "destroy any particle EXCEPT basalt").


	// Now here comes the actual model.
	// the cyclic ecology
	for (int ns = 0; ns < species; ++ns)
	    {
		Particle s = speciesParticle[ns];
		// adjacent to emptiness
		addPattern (s, spaceParticle, s, s, lifeRate*birthRate);  // spontaneous birth
		addPattern (s, spaceParticle, spaceParticle, s, lifeRate*(1-birthRate));  // no birth, so take a random walk step

		// adjacent to self
		addPattern (s, s, spaceParticle, s, lifeRate*chokeRate);  // spontaneous death due to overcrowding

		// adjacent to wall
		for (int w = 0; w < wallDecayStates; ++w) {
		    addPattern (s, wallParticle[w], spaceParticle, wallParticle[w], lifeRate*chokeRate);  // spontaneous death due to being muthafuckin BURIED ALIVE or CRUSHED AGAINST A BRICK WALL
		}

		// adjacent to prey
		for (int t = aversion; t < aversion + omnivorousness; ++t) {
		    int prey = (ns + t) % species;
		    Particle pp = speciesParticle[prey];
		    addPattern (s, pp, s, s, lifeRate*forageEfficiency);  // eat + breed (i.e. convert)
		    addPattern (s, pp, s, spaceParticle, lifeRate*(1 - forageEfficiency));  // eat + don't breed
		}

		// adjacent to other species
		for (int t = 1; t < species; ++t)
		    if (t < aversion || t >= aversion + omnivorousness) {
			int other = (ns + t) % species;
			Particle po = speciesParticle[other];
			addPattern (s, po, spaceParticle, po, lifeRate*chokeRate);  // spontaneous death due to overcrowding
		    }

		// adjacent to guest or tripwire: eat'em!
		addPattern (s, guestParticle, spaceParticle, s, 1);
		addPattern (s, tripwireParticle, spaceParticle, s, 1);
	    }

	// decaying walls
	for (int w = 0; w < wallDecayStates; ++w) {
	    Particle pw = wallParticle[w];
	    for (int c = 0; c < particleTypes(); ++c)
		{
		    Particle pc = getParticleByNumber(c);
		    boolean isWall = false;
		    for (int w2 = 0; w2 < wallDecayStates; ++w2)
			if (pc == wallParticle[w2]) {
			    isWall = true;
			    break;
			}

		    double decayRate = playDecayRate * (isWall ? buriedWallDecayRate : (pc == acidParticle ? 1 : exposedWallDecayRate));
		    addPattern (pw, pc, (w == 0) ? spaceParticle : wallParticle[w-1], pc, decayRate);  // wall decays
		}
	}

	// drifting & setting cement
	for (int c = 1; c < particleTypes(); ++c) {
	    Particle pc = getParticleByNumber(c);
	    boolean isWall = false;
	    for (int w2 = 0; w2 < wallDecayStates; ++w2)
		if (pc == wallParticle[w2]) {
		    isWall = true;
		    break;
		}

	    double setRate = (isWall ? cementStickRate : cementSetRate);
	    addPattern (cementParticle, pc, wallParticle[wallDecayStates - 1], pc, setRate);  // cement sets into wall
	}
	addPattern (cementParticle, spaceParticle, spaceParticle, cementParticle, 1);  // liquid cement always does random walk step

	// death gas
	for (int c = 1; c < particleTypes(); ++c) {
	    Particle pc = getParticleByNumber(c);
	    // exceptions to "acid melts everything" rule:
	    //  - tripwire (can only be destroyed by escaping animals)
	    //  - basalt (not destroyed; melts back into lava instead)
	    if (pc != tripwireParticle && pc != basaltParticle) {
		addPattern (acidParticle, pc, acidParticle, spaceParticle, gasMultiplyRate);  // acid lives
		addPattern (acidParticle, pc, spaceParticle, spaceParticle, 1 - gasMultiplyRate);  // acid dies
	    }
	}
	addPattern (acidParticle, spaceParticle, spaceParticle, acidParticle, 1);  // acid always does random walk step, doesn't disperse

	// fecundity gas
	for (int c = 0; c < species; ++c) {
	    Particle pc = speciesParticle[c];
	    addPattern (fecundityParticle, pc, pc, pc, 1);  // fecundity particle makes species BREED
	    addPattern (pc, fecundityParticle, pc, pc, 1);
	}
	addPattern (fecundityParticle, spaceParticle, fecundityParticle, fecundityParticle, lifeRate*gasMultiplyRate);  // gas breeds (!? gives illusion of pressure, I guess)
	addPattern (fecundityParticle, spaceParticle, spaceParticle, fecundityParticle, gasDispersalRate * (1 - lifeRate*gasMultiplyRate));  // gas disperses
	addPattern (fecundityParticle, spaceParticle, spaceParticle, fecundityParticle, (1 - gasDispersalRate) * (1 - lifeRate*gasMultiplyRate));  // gas does random walk step

	// mutator gas
	for (int c = 0; c < species; ++c) {
	    Particle pc = speciesParticle[c];
	    for (int t = -mutateRange; t <= mutateRange; ++t)
		if (t != 0)
		    {
			int mutant = (c - 1 + t + species) % species;
			Particle pm = speciesParticle[mutant];
			double mutProb = Math.pow (gasMultiplyRate, Math.abs(t));
			addPattern (mutatorParticle, pc, spaceParticle, pm, mutProb);  // fecundity particle makes species mutate into random other species
		    }
	}
	addPattern (mutatorParticle, spaceParticle, spaceParticle, spaceParticle, gasDispersalRate);  // gas disperses
	addPattern (mutatorParticle, spaceParticle, spaceParticle, mutatorParticle, 1 - gasDispersalRate);  // gas does random walk step
	addPattern (mutatorParticle, fecundityParticle, mutatorParticle, mutatorParticle, 1);  // fecundity gas reacts with mutator to produce MORE mutator

	// flowing & setting lava
	for (int c = 1; c < particleTypes(); ++c) {
	    Particle pc = getParticleByNumber(c);
	    boolean isWall = false;
	    for (int w2 = 0; w2 < wallDecayStates; ++w2)
		if (pc == wallParticle[w2]) {
		    isWall = true;
		    break;
		}

	    if (pc == basaltParticle || isWall) {
		double setRate = (pc == basaltParticle ? 1 : lavaSeedRate);
		addPattern (lavaParticle, pc, basaltParticle, pc, setRate);  // lava sets into basalt
	    }
	}
	addPattern (lavaParticle, spaceParticle, spaceParticle, lavaParticle, lavaFlowRate);  // lava does random walk step

	// basalt
	addPattern (basaltParticle, acidParticle, spaceParticle, lavaParticle, lavaFlowRate);  // acid melts basalt

	// guests
	addPattern (guestParticle, spaceParticle, spaceParticle, guestParticle, guestMoveRate);
    }

    // helper to add a pattern
    private void addPattern (Particle pc_old, Particle nc_old, Particle pc_new, Particle nc_new, double prob) {
	RandomVariable rv = (RandomVariable) pc_old.pattern.get (nc_old);
	if (rv == null)
	    pc_old.pattern.put (nc_old, rv = new RandomVariable());
	rv.add (new ParticlePair (pc_new, nc_new), prob);
	// uncomment to print the production rule to stderr
	// System.err.println ("P(" + pc_old.name + " " + nc_old.name + " -> " + pc_new.name + " " + nc_new.name + ") = " + prob);
    }

    // init tools method
    private void initSprayTools() {
	sprayDiameter = 2;
	sprayPower = 15;

	for (int c = 0; c < particleTypes(); ++c) {
	    sprayReserve.put (getParticleByNumber(c), new Double(0));
	    sprayMax.put (getParticleByNumber(c), new Double(0));
	    sprayRefillRate.put (getParticleByNumber(c), new Double(0));
	}

	double baseRefillRate = 0.25 * (double) sprayPower;

	sprayRefillRate.put (cementParticle, new Double (.35 * baseRefillRate));
	sprayMax.put (cementParticle, new Double (300));

	sprayRefillRate.put (acidParticle, new Double (.75 * baseRefillRate));
	sprayMax.put (acidParticle, new Double (200));

	sprayRefillRate.put (fecundityParticle, new Double (.5 * baseRefillRate));
	sprayMax.put (fecundityParticle, new Double (80));

	sprayRefillRate.put (mutatorParticle, new Double (.03 * baseRefillRate));
	sprayMax.put (mutatorParticle, new Double (40));

	sprayRefillRate.put (lavaParticle, new Double (.5 * baseRefillRate));
	sprayMax.put (lavaParticle, new Double (400));

	sprayParticle = cementParticle;

	cheatStringPos = 0;
    }

    // main game loop
    private void gameLoop() {
	// Game logic goes here.

	drawEverything();

	while (true)
	    {
		evolveStuff();
		useTools();

		plotCounts();
		refreshBuffer();
	    }
    }

    // getCursorPos() returns true if cursor is over board, and places cell coords in cursorPos
    private boolean getCursorPos() {
	Point mousePos = getMousePosition();
	if (mousePos != null) {
	    cursorPos.x = (int) ((mousePos.x - insets.left) / pixelsPerCell);
	    cursorPos.y = (int) ((mousePos.y - insets.top) / pixelsPerCell);

	    return onBoard(cursorPos);
	}
	return false;
    }

    private void useTools() {
	// randomize
	if (randomPressed) {
	    randomizeBoard();
	    redrawBoard();
	    refreshBuffer();
	    randomPressed = false;
	}

	boolean cursorOnBoard = getCursorPos();
	setCursor(cursorOnBoard ? boardCursor : normalCursor);

	// do spray
	if (mouseDown) {
	    if (cursorOnBoard)
		for (int i = 0; i < sprayPower; ++i) {
		    if (((Double) sprayReserve.get(sprayParticle)).doubleValue() > 0) {

			Point sprayCell = new Point();

			sprayCell.x = cursorPos.x + rnd.nextInt(sprayDiameter) - sprayDiameter / 2;
			sprayCell.y = cursorPos.y + rnd.nextInt(sprayDiameter) - sprayDiameter / 2;

			if (onBoard(sprayCell)) {
			    Particle oldCell = readCell (sprayCell);
			    if (oldCell == spaceParticle) {
				writeCell (sprayCell, sprayParticle, oldCell);
				sprayReserve.put (sprayParticle, ((Double) sprayReserve.get(sprayParticle)).doubleValue() - 1);
			    }
			}
		    }
		}

	} else {  // if (mouseDown) ...

	    // not spraying, so refresh spray reserves
	    for (int c = 0; c < particleTypes(); ++c) {
		Particle p = getParticleByNumber(c);
		double refillRate = ((Double) sprayRefillRate.get(p)).doubleValue() * (entropy + 1) / (maxEntropy + 1);
		double oldReserve = ((Double) sprayReserve.get(p)).doubleValue();
		if (refillRate > 0. && oldReserve < ((Double) sprayMax.get(p)).doubleValue())
		    sprayReserve.put (p, new Double (oldReserve + refillRate));
	    }
	}
    }

    protected void refreshBuffer() {
	// draw border around board
	bfGraphics.setColor(Color.white);
	bfGraphics.drawLine(0,0,boardSize,0);
	bfGraphics.drawLine(0,0,0,boardSize);
	bfGraphics.drawLine(0,boardSize,boardSize,boardSize);
	bfGraphics.drawLine(boardSize,0,boardSize,boardSize);

	// update buffer
	bufferStrategy.show();
	Toolkit.getDefaultToolkit().sync();	
    }

    private void evolveStuff() {
	Point p = new Point(), n = new Point();
	for (int u = 0; u < patternMatchesPerRefresh; ++u)
	    {
		getRandomPoint(p);
		getRandomNeighbor(p,n);
		evolvePair(p,n);
	    }
	++boardUpdateCount;
    }

    private boolean onBoard (Point p) { return p.x >= 0 && p.x < size && p.y >= 0 && p.y < size; }

    private void evolvePair (Point sourceCoords, Point targetCoords)
    {
	if (onBoard (targetCoords)) {
	    evolveLocalSourceAndLocalTarget (sourceCoords, targetCoords);
	} else {
	    // request remote evolveLocalTargetForRemoteSource
	    RemoteCellCoord remoteCoords = (RemoteCellCoord) remoteCell.get (targetCoords);
	    if (remoteCoords != null)
		evolveLocalSourceAndRemoteTarget (sourceCoords, remoteCoords);
	}
    }

    protected void evolveLocalSourceAndRemoteTarget (Point sourceCoords, RemoteCellCoord remoteCoords) {
	Particle oldSourceState = readCell(sourceCoords);
	if (oldSourceState.pattern.size() > 0)
	    BoardServer.sendEvolveDatagram (remoteCoords.addr, remoteCoords.port, remoteCoords.p, oldSourceState, sourceCoords, localhost, boardServerPort, getCellWriteCount(sourceCoords));
    }

    synchronized void evolveLocalSourceAndLocalTarget (Point sourceCoords, Point targetCoords)
    {
	writeCell (sourceCoords, evolveTargetForSource (targetCoords, readCell(sourceCoords)));
    }

    synchronized Particle evolveLocalTargetForRemoteSource (Point targetCoords, Particle oldSourceState)
    {
	return evolveTargetForSource (targetCoords, oldSourceState);
    }

    Particle evolveTargetForSource (Point targetCoords, Particle oldSourceState)
    {
	// get old state-pair
	Particle oldTargetState = readCell (targetCoords);

	// sample new state-pair
	Particle newSourceState = oldSourceState;
	RandomVariable rv = (RandomVariable) oldSourceState.pattern.get (oldTargetState);
	if (rv != null) {
	    ParticlePair newCellPair = (ParticlePair) rv.sample(rnd);
	
	    newSourceState = newCellPair.source;
	    Particle newTargetState = newCellPair.target;
	
	    // write
	    writeCell (targetCoords, newTargetState);
	}

	// return
	return newSourceState;
    }

    private void connectBorder (Point sourceStart, Point targetStart, Point lineVector, int lineLength, Point remoteOrigin, InetSocketAddress remoteBoard) {
	String[] connectRequests = new String [lineLength];
	Point source = new Point (sourceStart);
	Point target = new Point (targetStart);
	for (int i = 0; i < lineLength; ++i) {
	    Point remoteSource = new Point (source.x - remoteOrigin.x, source.y - remoteOrigin.y);
	    Point remoteTarget = new Point (target.x - remoteOrigin.x, target.y - remoteOrigin.y);

	    addRemoteCellCoord (target, remoteBoard, remoteTarget);
	    connectRequests[i] = BoardServer.connectString (remoteSource, source, localhost, boardServerPort);

	    source.x += lineVector.x;
	    source.y += lineVector.y;

	    target.x += lineVector.x;
	    target.y += lineVector.y;
	}

	BoardServer.sendTCPPacket (remoteBoard.getAddress(), remoteBoard.getPort(), connectRequests);
    }

    protected void addRemoteCellCoord (Point p, InetSocketAddress remoteBoard, Point pRemote) {
	System.err.println("Connecting (" + p.x + "," + p.y + ") to (" + pRemote.x + "," + pRemote.y + ") on " + remoteBoard);
	remoteCell.put (new Point(p), new RemoteCellCoord (remoteBoard, pRemote));
    }

    private void getRandomPoint (Point p) {
	p.x = rnd.nextInt(size);
	p.y = rnd.nextInt(size);
    }

    private void getRandomNeighbor (Point p, Point n) {
	int ni = rnd.nextInt(4);
	n.x = p.x;
	n.y = p.y;
	int delta = (ni & 1) == 0 ? -1 : +1;
	if ((ni & 2) == 0) { n.x += delta; } else { n.y += delta; }
	// Replace previous two lines with the following for periodic boundary conditions:
	/*
	  int delta = (ni & 1) == 0 ? size-1 : +1;
	  if ((ni & 2) == 0) { n.x = (n.x + delta) % size; } else { n.y = (n.y + delta) % size; }
	*/
    }
    private int neighborhoodSize() { return 4; }

    private static double log2(double x) { return Math.log(x) / Math.log(2); }

    protected int getCellWriteCount (Point p) {
	return cell[p.x][p.y].writeCount;
    }

    private Particle readCell (Point p) {
	if (mixPressed) {
	    int x = rnd.nextInt (size * size);
	    int rv = 0;
	    while (rv < particleTypes() - 1) {
		x -= getParticleByNumber(rv).count;
		if (x <= 0)
		    break;
		++rv;
	    }
	    if (getParticleByNumber(rv).count == 0) {
		for (int k = 0; k < (int) particleTypes(); ++k)
		    System.err.println (getParticleByNumber(k).name + " " + getParticleByNumber(k).count);
		System.err.println ("Sampled: " + rv + " " + getParticleByNumber(rv).count);
		throw new RuntimeException (new String ("Returned a zero-probability cell type"));
	    }
	    return getParticleByNumber(rv);
	}
	return cell[p.x][p.y].particle;
    }

    protected void writeCell (Point p, Particle pc) {
	writeCell (p, pc, readCell(p));
    }

    private void writeCell (Point p, Particle pc, Particle old_pc) {
	if (old_pc != pc) {
	    if (!mixPressed) {
		cell[p.x][p.y].particle = pc;
		++cell[p.x][p.y].writeCount;
		drawCell(p);
	    }
	    --old_pc.count;
	    ++pc.count;
	}
    }

    private void randomizeBoard() {
	// randomize board without changing total cell counts
	// uses a Fisher-Yates shuffle

	Point p = new Point();
	for (p.x = 0; p.x < size; ++p.x)
	    for (p.y = 0; p.y < size; ++p.y)
		cell[p.x][p.y].particle = spaceParticle;

	int i = 0;
	for (int c = 1; c < particleTypes(); ++c)
	    for (int k = 0; k < getParticleByNumber(c).count; ++k)
		{
		    cell[i % size][i / size].particle = getParticleByNumber(c);
		    ++i;
		}

	for (int k = 0; k < i; ++k)
	    {
		int kSwap = k + rnd.nextInt (size*size - k);
		Particle tmp = cell[kSwap % size][kSwap / size].particle;
		cell[kSwap % size][kSwap / size].particle = cell[k % size][k / size].particle;
		cell[k % size][k / size].particle = tmp;
	    }
    }

    private void drawCell (Point p) {
	bfGraphics.setColor(cell[p.x][p.y].particle.color);
	bfGraphics.fillRect(p.x*pixelsPerCell,p.y*pixelsPerCell,pixelsPerCell,pixelsPerCell);
    }

    private void drawEverything() {
	bfGraphics.setColor(Color.black);
	bfGraphics.fillRect(0,0,boardSize+toolBarWidth,boardSize+statusBarHeight);

	Point p = new Point();
	for (p.x = 0; p.x < size; ++p.x)
	    for (p.y = 0; p.y < size; ++p.y)
		drawCell (p);

	refreshBuffer();
    }

    protected void redrawBoard() {
	bfGraphics.setColor(Color.black);
	bfGraphics.fillRect(0,0,boardSize,boardSize);

	Point p = new Point();
	for (p.x = 0; p.x < size; ++p.x)
	    for (p.y = 0; p.y < size; ++p.y)
		drawCell (p);
    }

    // status & tool bars
    protected void plotCounts() {
	int h = 0;
	int cellsOnBoard = size * size;
	entropy = 0;

	int maxCount = 0, totalCount = 0;
	for (int c = 0; c < species; ++c) {
	    int cc = speciesParticle[c].count;
	    if (cc > maxCount)
		maxCount = cc;
	    totalCount += cc;
	}

	if (maxCount > 0)
	    for (int c = 0; c < species; ++c) {
		int cc = speciesParticle[c].count;
		double p = ((double) cc) / (double) totalCount;
		if (p > 0 && p <= 1)
		    entropy -= p * log2(p);

		bfGraphics.setColor(speciesParticle[c].color);
		int b = popChartHeight * cc / totalCount;
		int w = boardSize * cc / maxCount;
		if (b < 1 && cc > 0)
		    b = 1;
		if (b + h > popChartHeight)
		    b = popChartHeight - h;
		bfGraphics.fillRect(histXPos,boardSize + popChartHeight - h - b,1,b);
		h += b;

		bfGraphics.fillRect(0,boardSize + popChartHeight + popBarHeight * c,w,popBarHeight);
		bfGraphics.setColor(Color.black);
		bfGraphics.fillRect(w+1,boardSize + popChartHeight + popBarHeight * c,boardSize-w-1,popBarHeight);
	    }
	bfGraphics.setColor(Color.black);
	bfGraphics.fillRect(histXPos,boardSize,1,popChartHeight - h);

	histXPos = (histXPos + 1) % (boardSize);

	bfGraphics.setColor(Color.white);
	bfGraphics.fillRect(histXPos,boardSize,1,popChartHeight);

	// calculate entropy scores
	if (histXPos == 0) {  // just completed a cycle?

	    if (minEntropyOverCycle > bestMinEntropyOverCycle)
		bestMinEntropyOverCycle = minEntropyOverCycle;

	    minEntropyOverCycle = entropy;
	}

	if (entropy < minEntropyOverCycle)
	    minEntropyOverCycle = entropy;

	if (entropy > bestEntropy)
	    bestEntropy = entropy;

	// entropy bar
	int entropyBarBase = boardSize + popChartHeight + popBarHeight * (species + 1);
	float entropyBarLevel = (float) (entropy / maxEntropy);
	if (entropyBarLevel < 0 || entropyBarLevel > 1) {
	    System.err.println ("entropyBarLevel: " + entropyBarLevel);
	    throw new RuntimeException (new String ("Entropy outside permitted range"));
	}
	int entropyBarWidth = (int) (entropyBarLevel * (float) boardSize);

	double minGray = .1, redThreshold = 2*minGray;
	float entropyGrayLevel = (float) Math.max (entropyBarLevel, minGray);
	float entropyRedLevel = (float) Math.max (entropyGrayLevel, (redThreshold - entropyGrayLevel) / redThreshold);
	Color entropyColor = new Color (entropyRedLevel, entropyGrayLevel, entropyGrayLevel);
	bfGraphics.setColor(entropyColor);
	bfGraphics.fillRect(0,entropyBarBase,entropyBarWidth,entropyBarHeight);

	bfGraphics.setColor(Color.black);
	bfGraphics.fillRect(entropyBarWidth,entropyBarBase,boardSize - entropyBarWidth,entropyBarHeight);

	bfGraphics.setColor(Color.pink);
	bfGraphics.fillRect((int) ((bestMinEntropyOverCycle / maxEntropy) * (double) boardSize),entropyBarBase,1,entropyBarHeight);

	bfGraphics.setColor(Color.white);
	bfGraphics.fillRect((int) ((bestEntropy / maxEntropy) * (double) boardSize),entropyBarBase,1,entropyBarHeight);

	// entropy scores
	DecimalFormat myFormatter = new DecimalFormat("###.000");
	String entropyString = myFormatter.format(Math.pow(2,entropy));
	String bestEntropyString = myFormatter.format(Math.pow(2,bestEntropy));
	String minEntropyString = myFormatter.format(Math.pow(2,minEntropyOverCycle));
	String bestMinEntropyString = myFormatter.format(Math.pow(2,bestMinEntropyOverCycle));

	FontMetrics fm = bfGraphics.getFontMetrics();
	int ch = fm.getHeight();
	int sw = fm.stringWidth(entropyString);
	int bsw = fm.stringWidth(bestEntropyString);

	bfGraphics.setColor(Color.black);
	bfGraphics.fillRect(boardSize,boardSize + statusBarHeight - 5*ch,toolBarWidth,5*ch);

	bfGraphics.setColor(entropyColor);
	bfGraphics.drawString (entropyString, boardSize + toolBarWidth - sw, boardSize + statusBarHeight);

	bfGraphics.setColor(Color.white);
	bfGraphics.drawString (bestEntropyString, boardSize + toolBarWidth - bsw, boardSize + statusBarHeight - ch);

	// spray levels
	double m = 0;
	for (int c = 0; c < particleTypes(); ++c) {
	    double cm = ((Double) sprayMax.get(getParticleByNumber(c))).doubleValue();
	    if (cm > m)
		m = cm;
	}

	plotReserve ('S', 0, cementParticle, 1);
	plotReserve ('D', 1, acidParticle, .8);
	plotReserve ('F', 2, fecundityParticle, .5);
	plotReserve ('G', 3, mutatorParticle, .2);

	// lava spray (only available in cheat mode)
	plotOrHide ('B', 4, lavaParticle, .4, cheating());

	// name of the game
	flashOrHide ("Z00 GAS", 5, true, 0, 400, true, Color.white);

	// cheat mode
	printOrHide (cheatString, 6, cheating(), Color.getHSBColor ((float) rnd.nextDouble(), 1, 1));

	// entropy: positive feedback
	double dScore = Math.pow(2,entropy);
	flashOrHide ("Nice balance!", 7, dScore > 3.6, 100, -1, true, Color.pink);
	flashOrHide ("AWESOME ZOO!", 8, dScore > 5, 100, -1, true, Color.yellow);

	// entropy: negative feedback
	flashOrHide ("Uh-oh, imbalance", 9, dScore < 2.5 && dScore > 1.8, 10, 400, false, Color.red);
	flashOrHide ("Diversity low", 10, dScore < 2, 20, 500, false, Color.red);

	// number of species
	int liveSpecies = 0;
	for (int s = 0; s < species; ++s)
	    if (speciesParticle[s].count > 0)
		++liveSpecies;

	Color darkRed = Color.getHSBColor(0,(float).5,(float).5);
	flashOrHide ("EXTINCTION", 11, liveSpecies < 3, 25, 600, true, darkRed);
	flashOrHide ("ECO CRASH", 12, liveSpecies < 2, 30, -1, true, Color.white);

	flashOrHide (liveSpecies + " species!", 13, liveSpecies > 3, 100, -1, false, Color.orange);
	flashOrHide ("GR00VY", 14, liveSpecies > 5, 500, -1, false, Color.orange);

	flashOrHide ("V0ID space", 16, liveSpecies < 1, 0, 1000, false, Color.cyan);

	flashOrHide ("Guests eaten", 15, guestParticle.count==0, 0, -1, false, Color.red);
	flashOrHide ("ZOO BREAK!", 17, tripwireParticle.count==0, 0, -1, false, Color.red);


	// networking
	flashOrHide ("Online", 18, boardServer != null, 0, -1, false, Color.blue);
	flashOrHide ("Connected", 19, remoteCell.size() > 0, 0, -1, false, Color.cyan);

	// identify particle that cursor is currently over
	boolean cursorOnBoard = getCursorPos();
	Particle cursorParticle = cursorOnBoard ? cell[cursorPos.x][cursorPos.y].particle : null;
	printOrHide (cursorOnBoard ? cursorParticle.visibleName() : "", 20, cursorOnBoard, cursorOnBoard ? cursorParticle.color : Color.white);

    }

    private int toolYCenter (int row) { return (2 * row + 1) * toolHeight / 2; }

    private void flashOrHide (String text, int row, boolean show, int minTime, int maxTime, boolean onceOnly, Color color) {
	int flashPeriod = 10, flashes = 10;
	boolean reallyShow = false;
	boolean currentlyShown = timeFirstTrue[row] > 0;
	if (show) {
	    if (!currentlyShown)
		timeFirstTrue[row] = boardUpdateCount;
	    else {
		int timeSinceFirstTrue = boardUpdateCount - timeFirstTrue[row];
		int flashesSinceFirstTrue = (timeSinceFirstTrue - minTime) / flashPeriod;
		reallyShow = timeSinceFirstTrue >= minTime && (maxTime < 0 || timeSinceFirstTrue <= maxTime) && ((flashesSinceFirstTrue > 2*flashes) || (flashesSinceFirstTrue % 2 == 0));
	    }
	} else if (!onceOnly)
	    timeFirstTrue[row] = 0;

	if (reallyShow || currentlyShown)
	    printOrHide (text, row, reallyShow, color);
    }

    private void printOrHide (String text, int row, boolean show, Color color) {
	FontMetrics fm = bfGraphics.getFontMetrics();
	int xSize = fm.stringWidth(text), xPos = boardSize + toolBarWidth - xSize;
	int ch = fm.getHeight(), yPos = toolYCenter(row) + ch / 2;

	bfGraphics.setColor (Color.black);
	bfGraphics.fillRect (boardSize, yPos - ch + 1, toolBarWidth, ch + 2);

	if (show) {
	    bfGraphics.setColor (color);
	    bfGraphics.drawString (text, xPos, yPos);
	}
    }

    private void plotOrHide (char c, int row, Particle particle, double scale, boolean show) {
	if (show)
	    plotReserve (c, row, particle, scale);
	else
	    {
		bfGraphics.setColor (Color.black);
		bfGraphics.fillRect (boardSize, toolYCenter(row) - toolHeight/2, toolBarWidth, toolHeight);
	    }
    }

    private void plotReserve (char c, int row, Particle particle, double scale) {
	char[] ca = new char[1];
	ca[0] = c;
	int center = toolYCenter(row);
	int count = (int) ((Double) sprayReserve.get(particle)).doubleValue();
	int max = (int) ((Double) sprayMax.get(particle)).doubleValue();

	FontMetrics fm = bfGraphics.getFontMetrics();
	int cw = fm.charWidth(c);
	int ch = fm.getHeight();

	bfGraphics.setColor (particle.color);
	bfGraphics.drawChars (ca, 0, 1, boardSize + toolReserveBarWidth + toolKeyWidth/2 - cw/2, center + ch/2);

	int w = (int) (scale * (double) (toolReserveBarWidth * count / max));
	if (w > toolReserveBarWidth)
	    w = toolReserveBarWidth;

	int bh = toolHeight * 3 / 4;
	bfGraphics.fillRect (boardSize + toolReserveBarWidth - w, center - bh/2, w, bh);
	bfGraphics.setColor (Color.black);
	bfGraphics.fillRect (boardSize, center - bh/2, toolReserveBarWidth - w, bh);
    }

    // mouse events
    public void mousePressed(MouseEvent e) {
	mouseDown = true;
    }

    public void mouseReleased(MouseEvent e) {
	mouseDown = false;
    }

    public void mouseEntered(MouseEvent e) {
	mouseDown = false;
    }

    public void mouseExited(MouseEvent e) {
	mouseDown = false;
    }

    public void mouseClicked(MouseEvent e) {
	mouseDown = false;
    }

    // key events
    public void keyTyped(KeyEvent e) {
    }

    public void keyPressed(KeyEvent e) {
	char c = e.getKeyChar();
	if (c == 's' || c == 'S') {
	    mouseDown = true;
	    sprayParticle = cementParticle;
	} else if (c == 'd' || c == 'D') {
	    mouseDown = true;
	    sprayParticle = acidParticle;
	} else if (c == 'f' || c == 'F') {
	    mouseDown = true;
	    sprayParticle = fecundityParticle;
	} else if (c == 'g' || c == 'G') {
	    mouseDown = true;
	    sprayParticle = mutatorParticle;
	} else if (cheating())
	    {
		// cheats
		if (c == 'k' || c == 'K') {
		    randomPressed = true;
		} else if (c == 'l' || c == 'L') {
		    mixPressed = true;
		} else if (c == '0') {
		    initSprayTools();
		} else if (c == '9') {
		    mouseDown = true;
		    sprayParticle = speciesParticle[rnd.nextInt(species)];
		    sprayReserve.put (sprayParticle, new Double (123456789));
		} else if (c == '8') {
		    sprayReserve.put (sprayParticle, new Double (123456789));
		} else if (c == '7') {
		    sprayDiameter *= 1.5;
		    sprayPower *= 2.25;
		} else if (c == '6') {
		    sprayPower *= 1.5;
		} else if (c == 'b' || c == 'B') {
		    mouseDown = true;
		    sprayParticle = lavaParticle;
		}
	    }
    }

    public void keyReleased(KeyEvent e) {
	char c = e.getKeyChar();
	if (cheatStringPos < cheatString.length())
	    {
		if (c == cheatString.charAt (cheatStringPos))
		    ++cheatStringPos;
	    }
	else {  // cheating() == true
	    if (c == 'l' || c == 'L')
		randomPressed = true;
	}
	mouseDown = false;
	mixPressed = false;
    }
}
