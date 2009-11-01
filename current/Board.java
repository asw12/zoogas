import java.lang.*;
import java.util.*;
import java.text.*;
import java.awt.*;
import java.awt.image.*;
import java.net.*;
import java.io.*;

public class Board extends MooreTopology {
    public int size = 0;  // size of board in cells

    // main board data
    private Cell[][] cell = null;

    // cellular automata rule/particle generator
    private PatternSet patternSet = new PatternSet(this);

    // random number generator
    private Random rnd = null;

    // particle name registry
    protected Map<String,Particle> nameToParticle = new HashMap<String,Particle>();  // updated by Particle constructor

    // off-board connections
    private HashMap<Point,RemoteCellCoord> remoteCell = null;  // map of connections from off-board Point's to RemoteCellCoord's

    // networking
    private UpdateServer updateServer = null;  // UpdateServer fields UDP requests for cross-border interactions
    private ConnectionServer connectServer = null;   // ConnectionServer runs over TCP (otherwise requests to establish connections can get lost amongst the flood of UDP traffic)
    private int boardServerPort = 4444;
    private String localhost = null;

    // fast quad tree
    QuadTree quad = null;

    // constructor
    public Board (int size) {
	this.size = size;
	rnd = new Random();
	cell = new Cell[size][size];
	for (int x = 0; x < size; ++x)
	    for (int y = 0; y < size; ++y)
		cell[x][y] = new Cell();

	// quad tree
	quad = new QuadTree(size);

	// net init
	remoteCell = new HashMap<Point,RemoteCellCoord>();
	try {
	    localhost = InetAddress.getLocalHost().getHostAddress();
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    public final void initClient(int port,ZooGas gas) {

	this.boardServerPort = port;

	try {
	    updateServer = new UpdateServer (this, boardServerPort, gas);
	    updateServer.start();

	    connectServer = new ConnectionServer (this, boardServerPort);
	    connectServer.start();

	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    public final void initServer (InetSocketAddress remote) {
	connectBorder (new Point(0,0), new Point(-1,0), new Point(0,1), 128, new Point(-size,0), remote);  // west
	connectBorder (new Point(127,0), new Point(128,0), new Point(0,1), 128, new Point(+size,0), remote);  // east
	connectBorder (new Point(0,0), new Point(0,-1), new Point(1,0), 128, new Point(0,-size), remote);  // north
	connectBorder (new Point(0,127), new Point(0,128), new Point(1,0), 128, new Point(0,+size), remote);  // south
    }

    // read/write methods for cells
    public final int getCellWriteCount (Point p) {
	return cell[p.x][p.y].writeCount;
    }

    public final Particle readCell (Point p) {
	return cell[p.x][p.y].particle;
    }

    public final void writeCell (Point p, Particle pc) {
	writeCell (p, pc, readCell(p));
	quad.updateQuadTree (p, pc.normalizedTotalTransformRate());
    }

    private final void writeCell (Point p, Particle pc, Particle old_pc) {
	if (old_pc != pc) {
	    cell[p.x][p.y].particle = pc;
	    ++cell[p.x][p.y].writeCount;
	    if (old_pc != null)
		old_pc.decReferenceCount();
	    pc.incReferenceCount();
	}
    }

    // bond accessors
    public Map<String,Integer> incoming (Point p) {
	return cell[p.x][p.y].incoming;
    }

    public Map<String,Integer> outgoing (Point p) {
	return cell[p.x][p.y].outgoing;
    }

    public Point incoming (Point p, String bond) {
	if (cell[p.x][p.y].incoming.containsKey(bond)) {
	    Point q = new Point();
	    getNeighbor(p,q,cell[p.x][p.y].incoming.get(bond));
	    return q;
	}
	return null;
    }

    public Point outgoing (Point p, String bond) {
	if (cell[p.x][p.y].outgoing.containsKey(bond)) {
	    Point q = new Point();
	    getNeighbor(p,q,cell[p.x][p.y].outgoing.get(bond));
	    return q;
	}
	return null;
    }


    public void removeBonds (Point p) {
	Map<String,Integer> in = incoming(p), out = outgoing(p);
	if (in.size() > 0 || out.size() > 0) {
	    Point q = new Point();
	    for (Iterator<Map.Entry<String,Integer>> iter = in.entrySet().iterator(); iter.hasNext(); ) {
		Map.Entry<String,Integer> kv = iter.next();
		getNeighbor(p,q,kv.getValue().intValue());
		if (onBoard(q))
		    outgoing(q).remove(kv.getKey());
	    }
	    in.clear();
	    for (Iterator<Map.Entry<String,Integer>> iter = out.entrySet().iterator(); iter.hasNext(); ) {
		Map.Entry<String,Integer> kv = iter.next();
		getNeighbor(p,q,kv.getValue().intValue());
		if (onBoard(q))
		    incoming(q).remove(kv.getKey());
	    }
	    out.clear();
	}
    }

    public void addBond (Point p, Point q, String bond) {
	int ns = neighborhoodSize();
	int dir = getNeighborDirection(p,q);
	int rev = reverseDir(dir);
	outgoing(p).put(bond,dir);
	incoming(q).put(bond,rev);
    }

    public void addIncoming (Point p, Map<String,Integer> bondDir) {
	if (bondDir != null && bondDir.size() > 0) {
	    Point q = new Point();
	    for (Iterator<Map.Entry<String,Integer>> iter = bondDir.entrySet().iterator(); iter.hasNext(); ) {
		Map.Entry<String,Integer> kv = iter.next();
		getNeighbor(p,q,kv.getValue().intValue());
		if (onBoard(q))
		    addBond(p,q,kv.getKey());
	    }
	}
    }

    public void addOutgoing (Point p, Map<String,Integer> bondDir) {
	if (bondDir != null && bondDir.size() > 0) {
	    Point q = new Point();
	    for (Iterator<Map.Entry<String,Integer>> iter = bondDir.entrySet().iterator(); iter.hasNext(); ) {
		Map.Entry<String,Integer> kv = iter.next();
		getNeighbor(p,q,reverseDir(kv.getValue().intValue()));
		if (onBoard(q))
		    addBond(q,p,kv.getKey());
	    }
	}
    }

    // fill/init method
    public final void fill(Particle particle) {
	Point p = new Point();
	for (p.x = 0; p.x < size; ++p.x)
	    for (p.y = 0; p.y < size; ++p.y)
		writeCell(p,particle);
    }

    // helper to test if a cell is on board
    public final boolean onBoard (Point p) { return p.x >= 0 && p.x < size && p.y >= 0 && p.y < size; }

    // helper to get direction (quick implementation; reimplement in superclass for performance optimization)
    public int getNeighborDirection(Point p,Point q) {
	Point n = new Point();
	int ns = neighborhoodSize();
	for (int dir = 0; dir < ns; ++dir) {
	    getNeighbor(p,n,dir);
	    if (n.x == q.x && n.y == q.y)
		return dir;
	}
	return -1;
    }

    // helper to reverse direction
    public int reverseDir(int dir) {
	int ns = neighborhoodSize();
	return (dir + (ns >> 1)) % ns;
    }

    // update methods
    // getRandomPair places coordinates of a random pair in (p,n) and returns direction from p to n
    public final int getRandomPair(Point p,Point n) {
	quad.sampleQuadLeaf(p,rnd);
	int dir = readCell(p).sampleDir(rnd);
	getNeighbor(p,n,dir);
	return dir;
    }

    // update()
    public final void update(double boardUpdates,BoardRenderer renderer) {
	int updatedCells = 0;
	Point p = new Point(), n = new Point();
	double maxUpdates = boardUpdates * quad.topQuadRate();
	for (; updatedCells < maxUpdates; ++updatedCells) {

	    int dir = getRandomPair(p,n);
	    Particle oldSource = readCell(p);
	    Particle oldTarget = onBoard(n) ? readCell(n) : null;
	    UpdateEvent newPair = evolvePair(p,n,dir);
	    if (newPair != null) {
		Particle newSource = newPair.source;
		Particle newTarget = newPair.target;
	    
		if (newSource != oldSource)
		    renderer.drawCell(p);

		if (onBoard(n) && newTarget != oldTarget)
		    renderer.drawCell(n);

		if (newPair.verb != null)
		    renderer.showVerb(p,n,oldSource,oldTarget,newPair);
	    }
	}
    }

    // evolvePair(sourceCoords,targetCoords,dir) : delegate to appropriate evolve* method.
    // in what follows, one cell is designated the "source", and its neighbor is the "target".
    // "dir" is the direction from source to target.
    // returns a UpdateEvent describing the new state and verb (may be null).
    private final UpdateEvent evolvePair (Point sourceCoords, Point targetCoords, int dir)
    {
	UpdateEvent pp = null;
	if (onBoard (targetCoords)) {
	    pp = evolveLocalSourceAndLocalTarget (sourceCoords, targetCoords, dir);
	} else {
	    // request remote evolveLocalTargetForRemoteSource
	    RemoteCellCoord remoteCoords = (RemoteCellCoord) remoteCell.get (targetCoords);
	    if (remoteCoords != null)
		evolveLocalSourceAndRemoteTarget (sourceCoords, remoteCoords, dir);
	}
	return pp;
    }

    // evolveLocalSourceAndRemoteTarget: send an EVOLVE datagram to the network address of a remote cell.
    protected final void evolveLocalSourceAndRemoteTarget (Point sourceCoords, RemoteCellCoord remoteCoords, int dir) {
	Particle oldSourceState = readCell(sourceCoords);
	if (oldSourceState.isActive(dir)) {

	    if (oldSourceState.name.equals("_")) {
		System.err.println("_ is active");
		Set<String> actives = oldSourceState.transform.get(dir).keySet();
		for (Iterator<String> a = actives.iterator(); a.hasNext(); )
		    System.err.println("_ " + a.next());
	    }

	    double energyBarrier = -neighborhoodEnergy(sourceCoords);
	    BoardServer.sendEvolveDatagram (remoteCoords.addr, remoteCoords.port, remoteCoords.p, oldSourceState, sourceCoords, dir, energyBarrier, localhost, boardServerPort, getCellWriteCount(sourceCoords));
	}
    }

    // SYNCHRONIZED : this is one of two synchronized methods in this class
    // evolveLocalSourceAndLocalTarget : handle entirely local updates. Strictly in the family, folks
    // returns a UpdateEvent
    synchronized public final UpdateEvent evolveLocalSourceAndLocalTarget (Point sourceCoords, Point targetCoords, int dir)
    {
	UpdateEvent newCellPair = evolveTargetForSource(sourceCoords,targetCoords,readCell(sourceCoords),dir,0);
	if (newCellPair != null)
	    writeCell (sourceCoords, newCellPair.source);
	return newCellPair;
    }

    // SYNCHRONIZED : this is one of two synchronized methods in this class
    // evolveLocalTargetForRemoteSource : handle a remote request for update.
    // Return the new source state (the caller of this method will send this returned state back over the network as a RETURN datagram).
    synchronized public final Particle evolveLocalTargetForRemoteSource (Point targetCoords, Particle oldSourceState, int dir, double energyBarrier)
    {
	UpdateEvent pp = evolveTargetForSource(null,targetCoords,oldSourceState,dir,energyBarrier);
	return pp == null ? oldSourceState : pp.source;
    }

    // evolveTargetForSource : given a source state, and the co-ords of a target cell,
    // sample the new (source,target) state configuration, accept/reject based on energy difference,
    // write the updated target, and return the updated (source,target) pair.
    // The source cell coords are provided, but may be null if the source cell is off-board.
    public final UpdateEvent evolveTargetForSource (Point sourceCoords, Point targetCoords, Particle oldSourceState, int dir, double energyBarrier)
    {
	// get old state-pair
	Particle oldTargetState = readCell (targetCoords);

	// sample new state-pair
	UpdateEvent newCellPair = oldSourceState.samplePair (dir, oldTargetState, rnd, sourceCoords, targetCoords, this);

	if (newCellPair != null) {
	    Particle newSourceState = newCellPair.source;
	    Particle newTargetState = newCellPair.target;
	    // test for null
	    if (newSourceState == null || newTargetState == null) {
		throw new RuntimeException ("Null outcome of rule: " + oldSourceState.name + " " + oldTargetState.name + " -> " + (newSourceState == null ? "[null]" : newSourceState.name) + " " + (newTargetState == null ? "[null]" : newTargetState.name));
	    } else {
		// test energy difference and write, or reject
		// TODO: replace this with a test based on old & new bond energies
		if (energyDeltaAcceptable(sourceCoords,targetCoords,dir,oldSourceState,oldTargetState,newSourceState,newTargetState,energyBarrier)) {
		    //		    System.err.println ("Firing rule: " + oldSourceState.name + " " + oldTargetState.name + " -> " + newSourceState.name + " " + newTargetState.name + " " + newCellPair.verb);
		    removeBonds(targetCoords);
		    if (onBoard(sourceCoords))
			removeBonds(sourceCoords);
		    writeCell (targetCoords, newTargetState);
		    addIncoming(sourceCoords,newCellPair.sIncoming);
		    addOutgoing(sourceCoords,newCellPair.sOutgoing);
		    addIncoming(targetCoords,newCellPair.tIncoming);
		    addOutgoing(targetCoords,newCellPair.tOutgoing);
		    // TODO: add new bonds
		} else {
		    newCellPair = null;
		}
	    }
	}

	// return
	return newCellPair;
    }

    // methods to test if a move is energetically acceptable
    public final boolean energyDeltaAcceptable (Point coords, Particle newState, double energyBarrier) {
	return energyDeltaAcceptable (null, coords, -1, null, readCell(coords), null, newState, energyBarrier);
    }
    public final boolean energyDeltaAcceptable (Point sourceCoords, Point targetCoords, int dir, Particle oldSourceState, Particle oldTargetState, Particle newSourceState, Particle newTargetState) {
	return energyDeltaAcceptable (sourceCoords, targetCoords, dir, oldSourceState, oldTargetState, newSourceState, newTargetState, 0);
    }
    public final boolean energyDeltaAcceptable (Point sourceCoords, Point targetCoords, int dir, Particle oldSourceState, Particle oldTargetState, Particle newSourceState, Particle newTargetState, double energyBarrier) {

	double energyDelta = energyBarrier +
	    (sourceCoords == null
	     ? neighborhoodEnergyDelta(targetCoords,oldTargetState,newTargetState)
	     : neighborhoodEnergyDelta(sourceCoords,targetCoords,dir,oldSourceState,oldTargetState,newSourceState,newTargetState));

	//	if (energyDelta < 0)
	//	    System.err.println("Gain in energy (" + -energyDelta + "): " + oldSourceState.name + " " + oldTargetState.name + " -> " + newSourceState.name + " " + newTargetState.name);

	return
	    energyDelta > 0
	    ? true
	    : rnd.nextDouble() < Math.pow(10,energyDelta);
    }

    // method to calculate the absolute interaction energy of a cell with its neighbors.
    public final double neighborhoodEnergy (Point p) {
	return neighborhoodEnergyDelta (p, null, readCell(p), null);
    }

    // methods to calculate the energy of a cell neighborhood, if the cell is in a particular state.
    // a single neighbor can optionally be excluded from the sum (this aids in pair-cell neighborhood calculations).
    public final double neighborhoodEnergyDelta (Point p, Particle oldState, Particle newState) {
	return oldState == newState ? 0 : neighborhoodEnergyDelta (p, oldState, newState, null);
    }
    public final double neighborhoodEnergyDelta (Point p, Particle oldState, Particle newState, Point exclude) {
	double delta = 0;
	if (oldState != newState) {
	    int N = neighborhoodSize();
	    Point q = new Point();
	    for (int d = 0; d < N; ++d) {
		getNeighbor(p,q,d);
		if (q != exclude && onBoard(q)) {
		    Particle nbrState = readCell(q);
		    delta += newState.symmetricPairEnergy(nbrState,d);
		    if (oldState != null)
			delta -= oldState.symmetricPairEnergy(nbrState,d);
		}
	    }
	}
	return delta;
    }

    // method to calculate the change in energy of a joint neighborhood around a given pair of cells in a particular pair-state.
    // ("joint neighborhood" means the union of the neighborhoods of the two cells.)
    public final double neighborhoodEnergyDelta (Point sourceCoords, Point targetCoords, int dir, Particle oldSourceState, Particle oldTargetState, Particle newSourceState, Particle newTargetState) {
	return
	    neighborhoodEnergyDelta(sourceCoords,oldSourceState,newSourceState,targetCoords)
	    + neighborhoodEnergyDelta(targetCoords,oldTargetState,newTargetState,sourceCoords)
	    + newSourceState.symmetricPairEnergy(newTargetState,dir) - oldSourceState.symmetricPairEnergy(oldTargetState,dir);
    }

    // method returning a description of a cell neighborhood (including incoming & outgoing bonds) as a String
    private final String singleNeighborhoodDescription(Point p,boolean includeSelf) {
	StringBuffer sb = new StringBuffer();
	if (includeSelf)
	    sb.append(readCell(p).name+" ");
	for (Iterator<Map.Entry<String,Integer>> iter = incoming(p).entrySet().iterator(); iter.hasNext(); ) {
	    Map.Entry<String,Integer> kv = iter.next();
	    sb.append("<"+dirString(kv.getValue().intValue())+":"+kv.getKey());
	}
	for (Iterator<Map.Entry<String,Integer>> iter = outgoing(p).entrySet().iterator(); iter.hasNext(); ) {
	    Map.Entry<String,Integer> kv = iter.next();
	    sb.append(">"+dirString(kv.getValue().intValue())+":"+kv.getKey());
	}
	return sb.toString();
    }

    // method returning a description of a two-cell neighborhood (including incoming & outgoing bonds) as a String
    public final String pairNeighborhoodDescription(Point p,Point q) {
	return singleNeighborhoodDescription(p,false) + "+" + singleNeighborhoodDescription(q,true);
    }

    // method to send requests to establish two-way network connections between cells
    // (called in the client during initialization)
    private final void connectBorder (Point sourceStart, Point targetStart, Point lineVector, int lineLength, Point remoteOrigin, InetSocketAddress remoteBoard) {
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

    protected final void addRemoteCellCoord (Point p, InetSocketAddress remoteBoard, Point pRemote) {
	System.err.println("Connecting (" + p.x + "," + p.y + ") to (" + pRemote.x + "," + pRemote.y + ") on " + remoteBoard);
	remoteCell.put (new Point(p), new RemoteCellCoord (remoteBoard, pRemote));
    }


    // Particle name-indexing methods
    protected final void registerParticle (Particle p) {
	nameToParticle.put (p.name, p);
    }

    protected final void deregisterParticle (Particle p) {
	nameToParticle.remove (p.name);
	//	System.err.println("Deregistering " + p.name);
    }

    public final Particle getParticleByName (String name) {
	return (Particle) nameToParticle.get (name);
    }

    protected final Particle getOrCreateParticle (String name) {
	return patternSet.getOrCreateParticle (name, this);
    }

    protected Collection<Particle> knownParticles() {
	return nameToParticle.values();
    }

    // flush particle cache, and flush all particles' transformation rule & energy caches
    public void flushCaches() {
	Collection<Particle> particles = knownParticles();
	LinkedList<Particle> particlesToForget = new LinkedList<Particle>();
	for (Iterator<Particle> iter = particles.iterator(); iter.hasNext(); ) {
	    Particle p = iter.next();
	    p.flushCaches();
	    if (p.getReferenceCount() <= 0)
		particlesToForget.add(p);
	}
	for (Iterator<Particle> iter = particlesToForget.iterator(); iter.hasNext(); )
	    deregisterParticle(iter.next());
    }

    // method to init PatternSet from file
    public final void loadPatternSetFromFile(String filename) {
	patternSet = PatternSet.fromFile(filename,this);
    }

    // network helpers
    public final boolean online() { return updateServer != null; }
    public final boolean connected() { return remoteCell.size() > 0; }

    // read from image
    protected final void initFromImage (BufferedImage img, ParticleSet particleSet) {
	Set<Particle> ps = particleSet.getParticles(this);

	for (int x = 0; x < size; ++x)
	    for (int y = 0; y < size; ++y) {
		int c = img.getRGB(x,y);
		int red = (c & 0x00ff0000) >> 16;
		int green = (c & 0x0000ff00) >> 8;
		int blue = c & 0x000000ff;

		// find state with closest color
		int dmin = 0;
		Particle s = null;
		for (Iterator<Particle> e = ps.iterator(); e.hasNext() ;) {
		    Particle pt = e.next();
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
		writeCell(new Point(x,y), s);
	    }
    }

    // debug
    String debugDumpStats() {
	int energyRules = 0, transRules = 0, outcomes = 0;
	for (Iterator<Particle> iter = nameToParticle.values().iterator(); iter.hasNext(); ) {
	    Particle p = iter.next();
	    energyRules += p.energyRules();
	    transRules += p.transformationRules();
	    outcomes += p.outcomes();
	}
	return nameToParticle.size() + " states, " + energyRules + " energies, " + transRules + " rules, " + outcomes + " outcomes";
    }
}

