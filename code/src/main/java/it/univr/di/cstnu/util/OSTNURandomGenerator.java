// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.util;

import com.google.common.io.Files;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.univr.di.Debug;
import it.univr.di.cstnu.algorithms.OSTNU;
import it.univr.di.cstnu.graph.Edge.ConstraintType;
import it.univr.di.cstnu.graph.*;
import it.univr.di.labeledvalue.Constants;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionHandlerFilter;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Allows one to build random OSTNU instances specifying:
 *
 * <ul>
 * <li>number of wanted DC/NOT DC instances</li>
 * </ul>
 * And the following parameters that characterize each generated instance:
 * <ul>
 * <li> number nodes
 * <li> number of contingent nodes
 * <li> number of oracles</li>
 * <li> max weight for each edge
 * <li> max weight for each contingent link (upper value)
 * <li> max in-degree for each node
 * <li> max out-degree for each node
 * <li> probability to have an edge between any pair of nodes
 * </ul>
 * <p>
 * The class generates the wanted instances, building each one randomly and, then, DC checking it for stating its DC property.
 *
 * @author posenato
 * @version 1.0
 */
public class OSTNURandomGenerator {

	/**
	 * Version of the class
	 */
	// static public final String VERSIONandDATE = "Version 2.0 - April, 20 2020";
	static public final String VERSIONandDATE = "Version 1.0 - April, 06 2024";// switch to Rul2020 checking algorithm

	/*
	 * Base name for generated files
	 */
	static final String OSTNU_SUFFIX = ".ostnu";
	/**
	 * Name of the root directory
	 */
	static final String BASE_DIR_NAME = "Instances";
	/**
	 * Name of sub dir containing DC instances
	 */
	static final String DC_SUB_DIR_NAME = "Controllable";
	/**
	 * Default edge name prefix
	 */
	static final String EDGE_NAME_PREFIX = "e";

	/**
	 * Default edge probability for each pair of nodes
	 */
	static final double EDGE_PROBABILITY = .2d;

	/**
	 * Default lane number
	 */
	static final int LANES = 5;

	/**
	 * logger
	 */
	static final Logger LOG = Logger.getLogger(OSTNURandomGenerator.class.getName());

	/**
	 * Maximum checks for a network
	 */
	static final int MAX_CHECKS = 50;

	/**
	 * Max distance between lower and upper value in a contingent link
	 */
	static final int MAX_CONTINGENT_RANGE = 100;

	/**
	 * Max weight value
	 */
	static final int MAX_CONTINGENT_WEIGHT = 100;

	/**
	 * Min max weight value
	 */
	static final int MIN_MAX_WEIGHT = 150;

	/**
	 * Min number of nodes
	 */
	static final int MIN_NODES = 4;

	/**
	 * Default node name prefix
	 */
	static final String NODE_NAME_PREFIX = "N";

	/**
	 * Name of sub dir containing NOT DC instances
	 */
	static final String NOT_DC_SUB_DIR_NAME = "NotControllable";

	/**
	 * Default son probability
	 */
	static final double SON_PROBABILITY = .8d;

	/**
	 * Default factor for modifying an edge
	 */
	static final double WEIGHT_MODIFICATION_FACTOR = .04d;

	/**
	 * Default x shift for node position
	 */
	static final double X_SHIFT = 150d;

	/**
	 * Default y shift for node position
	 */
	static final double Y_SHIFT = 150d;

	/**
	 * @param args input args
	 *
	 * @throws FileNotFoundException if any.
	 * @throws IOException           if any.
	 */
	public static void main(String[] args) throws FileNotFoundException, IOException {
		final OSTNURandomGenerator generator = new OSTNURandomGenerator();
		System.out.println(generator.getVersionAndCopyright());
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Start...");
			}
		}
		if (!generator.manageParameters(args)) {
			return;
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Parameters ok!");
			}
		}
		System.out.println("Starting execution...");

		final String fileNamePrefix = generator.createFolders();
		generator.createReadmeFiles();

		final String numberFormat = makeNumberFormat(generator.dcInstances);

		final TNGraphMLWriter stnuWriter = new TNGraphMLWriter(null);
		ObjectPair<TNGraph<OSTNUEdgePluggable>> instances;

		int notDCinstancesDone = 0;

		for (int dcInstancesDone = 0; dcInstancesDone < generator.dcInstances; dcInstancesDone++) {

			do {
				instances = generator.buildAPairRndTNInstances(notDCinstancesDone < generator.notDCInstances);
			} while (instances.getFirst() == null);

			// save the dc instance
			final String indexNumber = String.format(numberFormat, Integer.valueOf(dcInstancesDone + generator.startingIndex));
			String fileName = "dc" + fileNamePrefix + "_" + indexNumber + OSTNU_SUFFIX;
			File outputFile = getNewFile(generator.dcSubDir, fileName);
			stnuWriter.save(instances.getFirst(), outputFile);
			System.out.println("DC instance " + fileName + " saved.");
			// plain format
			fileName = "dc" + fileNamePrefix + "_" + indexNumber + ".plainOStnu";
			outputFile = getNewFile(generator.dcSubDir, fileName);
			stnuPlainWriter(instances.getFirst(), outputFile);

			if (generator.dense) {
				// make the dense version
				final TNGraph<OSTNUEdgePluggable> denseInstance = generator.makeDenseInstance(instances.getFirst());
				fileName = "dc" + fileNamePrefix + "_dense_" + indexNumber + OSTNU_SUFFIX;
				outputFile = getNewFile(generator.dcSubDir, fileName);
				assert denseInstance != null;
				stnuWriter.save(denseInstance, outputFile);
				System.out.println("DC instance " + fileName + " saved.");
				fileName = "dc" + fileNamePrefix + "_dense_" + indexNumber + ".plainStnu";
				outputFile = getNewFile(generator.dcSubDir, fileName);
				stnuPlainWriter(denseInstance, outputFile);

			}

			if (notDCinstancesDone < generator.notDCInstances) {
				// save the NOT DC instance
				fileName = "notDC" + fileNamePrefix + "_" + indexNumber + OSTNU_SUFFIX;
				outputFile = getNewFile(generator.notDCSubDir, fileName);
				stnuWriter.save(instances.getSecond(), outputFile);
				System.out.println("NOT DC instance " + fileName + " saved.");
				notDCinstancesDone++;
				fileName = "notDC" + fileNamePrefix + "_" + indexNumber + ".plainStnu";
				outputFile = getNewFile(generator.notDCSubDir, fileName);
				stnuPlainWriter(instances.getSecond(), outputFile);

				if (generator.dense) {
					// make the dense version
					final TNGraph<OSTNUEdgePluggable> denseInstance = generator.makeDenseInstance(instances.getSecond());
					fileName = "notDC" + fileNamePrefix + "_dense_" + indexNumber + OSTNU_SUFFIX;
					outputFile = getNewFile(generator.notDCSubDir, fileName);
					assert denseInstance != null;
					stnuWriter.save(denseInstance, outputFile);
					System.out.println("NOT DC instance " + fileName + " saved.");
					fileName = "notDC" + fileNamePrefix + "_dense_" + indexNumber + ".plainStnu";
					outputFile = getNewFile(generator.notDCSubDir, fileName);
					stnuPlainWriter(denseInstance, outputFile);
				}
			}
		}
		System.out.println("Execution finished.");
	}

	/**
	 * Adds 'edgesForNode' redundant edges having source 'node'. For determining the destination node, it makes 'hop' hops. If all the edges cannot be added
	 * because already present, or it is not possible making them after 'hop' hops, it tries to add the remaining making fewer hops.
	 *
	 * @param source        the source node
	 * @param dest          since the procedure is recursive, this parameter is for finding the destination node. In the main call, specify 'null'.
	 * @param weight        the edge weight
	 * @param requiredEdges number of required edges
	 * @param hop           number of required hops.
	 * @param denseDc       the input network
	 *
	 * @return the number of edges added if it was possible. If there is no possibility to add edges because there not |paths| = hop, returns
	 *    {@link Constants#INT_NULL}.
	 */
	private static int addRedundantNewEdges(
		LabeledNode source, LabeledNode dest, int weight, int hop, int requiredEdges, TNGraph<OSTNUEdgePluggable> denseDc) {
		if (requiredEdges <= 0) {
			return 0;
		}
		if (hop == 0) {
			final OSTNUEdgePluggable e = denseDc.findEdge(source, dest);
			if (e == null) {
				final OSTNUEdgePluggable newEdge = denseDc.getEdgeFactory().get(source.getName() + "-" + dest.getName());
				newEdge.setValue(weight + 10);
				denseDc.addEdge(newEdge, source, dest);
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Added edge " + newEdge);
				}
				return 1;
			}
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Cannot add an edge because it is already present: " + e);
			}
			return 0;
		}
		// I have to make a hop
		int added = Constants.INT_NULL;
		int w;
		if (dest == null) {
			dest = source;
		}
		if (denseDc.outDegree(dest) == 0) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Node " + dest.getName() + " has no neighbours.");
			}
			return added;
		}
		int rAdd;
		for (final OSTNUEdgePluggable edge : denseDc.getOutEdges(dest)) {
			if (added != Constants.INT_NULL && requiredEdges - added <= 0) {
				LOG.finer("No more edges are necessary!");
				break;
			}
			w = edge.getValue();
			if (w == Constants.INT_NULL) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Edge is contingent, ignore. Details: " + edge);
				}
				continue;// contingent link
			}
			final LabeledNode nextNode = denseDc.getDest(edge);
			final int stillRequiredEdges = (added == Constants.INT_NULL) ? requiredEdges : requiredEdges - added;
			rAdd = addRedundantNewEdges(source, nextNode, weight + w, hop - 1, stillRequiredEdges, denseDc);
			if (rAdd != Constants.INT_NULL) {
				if (added == Constants.INT_NULL) {
					added = rAdd;
				} else {
					added += rAdd;
				}
			}
		}
		if (added > 0) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Added " + added + " edges.Return.");
			}
			return added;
		}
		// since it was no possible to add at desired hop, I try to add here
		if (source != dest) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("No edges were added. Try to add with present node " + dest);
			}
			if (denseDc.findEdge(source, dest) == null) {
				final OSTNUEdgePluggable newEdge = denseDc.getEdgeFactory().get(source.getName() + "-" + dest.getName());
				newEdge.setValue(weight + 10);
				denseDc.addEdge(newEdge, source, dest);
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Added edge " + newEdge);
				}
				return 1;
			}
		}
		return added;
	}

	/**
	 * Creates a file using child as name. If child is already present, the already present child is renamed as child+"~" (recursively).
	 *
	 * @param parent the parent abstract name
	 * @param child  the child pathname string
	 *
	 * @return a File that is not present with name child[~]*
	 */
//	@SuppressWarnings("UnstableApiUsage")
	static private File getNewFile(File parent, String child) {
		final File newFile = new File(parent, child);
		if (newFile.isFile()) {
			final File bakFile = getNewFile(parent, child + "~");
			try {
				Files.move(newFile, bakFile);
			} catch (IOException e) {
				throw new IllegalStateException(
					"Cannot rename file " + child + " to " + child + "~: " + e.getMessage());
			}
		}
		return newFile;
	}

	/**
	 * @param n the input integer
	 *
	 * @return a string format "%0&lt;i&gt;d" where {@code i} is the max between 3 and the digit number of {@code n}.
	 */
	private static String makeNumberFormat(int n) {
		int nDigits = (int) Math.floor(Math.log10(n)) + 1;
		if (nDigits < 3) {
			nDigits = 3;
		}
		return "%%0%dd".formatted(nDigits);
	}

	/**
	 * Saves the given stnu in file filename in the Hunsberger format.<br> Hunsberger format example:
	 *
	 * <pre>
	 * # KIND OF NETWORK
	 * STNU
	 * # Num Time-Points
	 * 5
	 * # Num Ordinary Edges
	 * 4
	 * # Num Contingent Links
	 * 2
	 * # Time-Point Names
	 * A0 C0 A1 C1 X
	 * # Ordinary Edges
	 * X 12 C0
	 * C1 11 C0
	 * C0 -7 X
	 * C0 -1 C1
	 * # Contingent Links
	 * A0 1 3 C0
	 * A1 1 10 C1
	 * ...
	 * </pre>
	 *
	 * @param graph      the input graph
	 * @param outputFile the output file
	 */
	static private void stnuPlainWriter(TNGraph<OSTNUEdgePluggable> graph, File outputFile) {
		if (graph == null || outputFile == null) {
			return;
		}
		LOG.finest("Start to save the instance in a plain format");

		try (final PrintWriter writer = new PrintWriter(outputFile, StandardCharsets.UTF_8)) {
			writer.println("# Nodes and contingent links saved in random order.");
			writer.println("# KIND OF NETWORK\nSTNU");
			writer.println("# Num Time-Points\n" + graph.getVertexCount());
			writer.println("# Num Ordinary Edges\n" + (graph.getEdgeCount() - 2 * graph.getContingentNodeCount()));
			writer.println("# Num Contingent Links\n" + graph.getContingentNodeCount());
			writer.println("# Time-Point Names");
			// Luke asked to save nodes in random order :/
			final ObjectArrayList<LabeledNode> randomVertexList = new ObjectArrayList<>(graph.getVertices());
//			Collections.shuffle(randomVertexList);
			final ObjectArrayList<LabeledNode> oracles = new ObjectArrayList<>();
			for (final LabeledNode node : randomVertexList) {
				writer.print("'" + node.getName() + "' ");
				if (node.getPropositionObserved() != Constants.UNKNOWN) {
					oracles.add(node);
				}
			}
			writer.println();
			writer.println("# Ordinary Edges");
			final ObjectArrayList<OSTNUEdgePluggable> randomContingentList = new ObjectArrayList<>();
			for (final OSTNUEdgePluggable edge : graph.getEdges()) {
				if (edge.isContingentEdge()) {
					if (edge.getValue() > 0) {
						randomContingentList.add(edge);
					}
					continue;
				}
				writer.println("'" + Objects.requireNonNull(graph.getSource(edge)).getName() + "' " +
				               Constants.formatInt(edge.getValue()) + " '" +
				               Objects.requireNonNull(graph.getDest(edge)).getName() + "'");
			}
			writer.println("# Contingent Links");
//			Collections.shuffle(randomContingentList);
			for (final OSTNUEdgePluggable e : randomContingentList) {
				final LabeledNode activation = graph.getSource(e);
				final LabeledNode contingent = graph.getDest(e);
				final OSTNUEdgePluggable lower = graph.findEdge(contingent, activation);
				assert lower != null;
				assert activation != null;
				assert contingent != null;
				writer.println("'" + activation.getName() + "' " + (-lower.getValue()) + " " + (e.getValue()) + " '" +
				               contingent.getName() + "'");
			}
			if (oracles.size() > 0) {
				writer.println("# Oracles");
				for (final LabeledNode oracle : oracles) {
					writer.println("'" + oracle.getName() + "' --> '" + oracle.getPropositionObserved() + "'");
				}
			}
		} catch (IOException e) {
			LOG.severe(e.getMessage());
			throw new IllegalStateException(e.toString());
		}
	}

	/**
	 * Random generator used in the building of labels.
	 */
	private final SecureRandom rnd = new SecureRandom();
	/**
	 * Local temporary network
	 */
	private final File tmpNetwork;
	/**
	 * Base directory for saving the random instances.
	 */
	@Option(name = "--baseOutputDir", usage = "Root directory where to create the subdirs containing the DC/notDC instance.")
	String baseDirName = BASE_DIR_NAME;
	/**
	 * Number of wanted DC random STNU instances.
	 */
	@Option(required = true, name = "--dcInstances", usage = "Number of wanted DC random STNU instances.")
	private int dcInstances = 10;
	/**
	 * Subdir containing DC instances
	 */
	private File dcSubDir;
	/**
	 * The edge probability between any two nodes.
	 */
	@Option(name = "--edgeProb", usage =
		"The edge probability between any two nodes in case of general random network.\n" +
		"In case of tree random network, it is the probability of edge between nodes of the same level. This is equivalent to the density of the graph.")
	private double edgeProb = EDGE_PROBABILITY;
	/**
	 * The node in-degree
	 */
	@Option(name = "--inDegree", usage = "The maximal node in-degree. If a node has such in-degree, no incoming random edge can be added.")
	private int inDegree = 10;
	/**
	 * Max contingent edge weight value
	 */
	@Option(name = "--maxContingentWeightValue", usage = "Max contingent weight value.")
	private int maxContingentWeight = MAX_CONTINGENT_WEIGHT;
	/**
	 * Max contingent edge weight value
	 */
	@Option(name = "--maxContingentRange", usage = "Max contingent range between random upper and lower values.")
	private int maxContingentRange = MAX_CONTINGENT_RANGE;
	/**
	 * Max edge weight value (If x is the max weight value, the range for each STNU ordinary link may be [-x, x])
	 */
	@Option(name = "--maxWeightValue", usage = "Max edge weight value (If x is the max weight value, the range for each STNU ordinary link may be [-x, x]).")
	private int maxWeight = MIN_MAX_WEIGHT;
	/**
	 * Number of contingent nodes.
	 */
	@Option(required = true, name = "--ctgNodes", usage = "Number of contingent node in each STNU instance.")
	private int nCtgNodes = 1;
	/**
	 * Number of nodes in each random CSTN instance.
	 */
	@Option(required = true, name = "--nodes", usage = "Number of nodes in each STNU instance.")
	private int nNodes = MIN_NODES;
	/**
	 * Number of wanted NOT DC random CSTN instances.
	 */
	@Option(required = true, name = "--notDCInstances", usage = "Number of wanted NOT DC random CSTN instances.")
	private int notDCInstances = 10;
	/**
	 * Sub directory containing not DC instances
	 */
	private File notDCSubDir;
	/**
	 * Son number
	 */
	@Option(depends = "--treeNetwork", name = "--sons", usage = "The maximum number of sons. It must be a value in [1, #nodes].")
	private int nSons = 2;
	/**
	 * The node out-degree
	 */
	@Option(name = "--outDegree", usage = "The maximal node out-degree. If a node has such out-degree, no outgoing random edge can be added.")
	private int outDegree = 10;

	/**
	 * Simple contingent name
	 */
	@Option(name = "--oracles", usage = "Add oracles to the network associating them to the first contingents. The network must have contingent names made by a single letter. --ctgNodes must be less than 26.")
	private int nOracles;

	/**
	 * Tree random network
	 */
	@Option(name = "--treeNetwork", usage = "The random network must be a tree having Z as root.")
	private boolean randomTree;
	/**
	 * Lane random network
	 */
	@Option(forbids = "--treeNetwork", name = "--laneNetwork", usage = "The random network must be a graph like swimming lanes made by sequences of nodes connected by some random edges. It is incompatible with --treeNetwork.")
	private boolean randomLane;
	/**
	 * Dense random network
	 */
	@Option(name = "--dense", usage = "Complete the network making it dense. --density parameter says how much.")
	private boolean dense;
	/**
	 * The density index
	 */
	@Option(depends = "--dense", name = "--density", usage = "The density of the network. The network is made dense adding edges to it that does not change its DC property until #edges > #nodes*(#nodes-1)*density.")
	private double density = 0.5d;
	/**
	 * Min ## edges
	 */
	private int minNEdges;
	/**
	 * Pool random network
	 */
	@Option(depends = "--laneNetwork", forbids = "--treeNetwork", name = "--lanes", usage = "The number of swimming lanes.")
	private int lanes = LANES;
	/**
	 * Son probability
	 */
	@Option(depends = "--treeNetwork", name = "--sonProbability", usage = "The probability that in parent node can have a son.")
	private double sonProb = SON_PROBABILITY;
	/**
	 *
	 */
	@Option(name = "--startingIndex", usage = "Index of the first generated instance.")
	private int startingIndex;
	/**
	 * Timeout in seconds for the check.
	 */
	@Option(name = "-t", aliases = "--timeOut", usage = "Timeout in seconds for the check", metaVar = "seconds")
	private int timeOut = 60 * 15;
	/**
	 * weight adjustment. This value is determined in the constructor.
	 */
	private int weightAdjustment;

	/**
	 * @param givenDcInstances         the wanted dc instances
	 * @param givenNotDCInstances      the wanted not-dc instances
	 * @param nodes                    the number of nodes
	 * @param nCtgNodes1               the number of contingent nodes
	 * @param givenMaxContingentWeight max contingent duration
	 * @param edgeProbability          the probability of an edge between two nodes
	 * @param givenMaxWeight           the maximum edge weight
	 *
	 * @throws IllegalArgumentException if one or more parameters has/have not valid value/s.
	 * @throws IllegalArgumentException if any.
	 */
	public OSTNURandomGenerator(
		int givenDcInstances, int givenNotDCInstances, int nodes, int nCtgNodes1, double edgeProbability,
		int givenMaxWeight, int givenMaxContingentWeight) {
		this();
		dcInstances = givenDcInstances;
		notDCInstances = givenNotDCInstances;
		nNodes = nodes;
		nCtgNodes = nCtgNodes1;
		edgeProb = edgeProbability;
		maxWeight = givenMaxWeight;
		maxContingentWeight = givenMaxContingentWeight;
		checkParameters();// it is necessary!
	}

	/**
	 * It cannot be used outside.
	 */
	private OSTNURandomGenerator() {
		try {
			tmpNetwork = File.createTempFile("currentNetwork", "xml");
		} catch (IOException e) {
			LOG.severe(e.getMessage());
			throw new IllegalStateException(e.toString());
		}
	}

	/**
	 * <p>
	 * getDcInstanceNumber.
	 * </p>
	 *
	 * @return the dcInstances
	 */
	public int getDcInstanceNumber() {
		return dcInstances;
	}

	/**
	 * <p>
	 * Getter for the field {@code edgeProb}.
	 * </p>
	 *
	 * @return the edgeProb
	 */
	public double getEdgeProb() {
		return edgeProb;
	}

	/**
	 * <p>
	 * Getter for the field {@code maxWeight}.
	 * </p>
	 *
	 * @return the maxWeight
	 */
	public int getMaxWeight() {
		return maxWeight;
	}

	/**
	 * <p>
	 * getNodeNumber.
	 * </p>
	 *
	 * @return the nNodes
	 */
	public int getNodeNumber() {
		return nNodes;
	}

	/**
	 * <p>
	 * getNotDCInstanceNumber.
	 * </p>
	 *
	 * @return the notDCInstances
	 */
	public int getNotDCInstanceNumber() {
		return notDCInstances;
	}

	/**
	 * <p>
	 * Getter for the field {@code notDCSubDir}.
	 * </p>
	 *
	 * @return the notDCSubDir
	 */
	public File getNotDCSubDir() {
		return notDCSubDir;
	}

	/**
	 * <p>
	 * getObsQLoopNumber.
	 * </p>
	 *
	 * @return the nObsQLoop
	 */
	public int getObsQLoopNumber() {
		return nCtgNodes;
	}

	/**
	 * @return version and copyright string
	 */
	public String getVersionAndCopyright() {
		// I use a non-static method for having a general method that prints the right name for each derived class.
		String s = "\nSPDX-License-Identifier: LGPL-3.0-or-later, Roberto Posenato.\n";
		try {
			s = getClass().getName() + " " + getClass().getDeclaredField("VERSIONandDATE").get(this) + s;
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			//
		}
		return s;
	}

	/**
	 * Adds randomly a forward or a backward edge between node given by addedNodes[firstNodeIndex] and node addedNodes[secondNodeIndex]. The probability of the
	 * adding an edge is {@link #edgeProb}. The edge value is chosen randomly considering {@link #maxWeight}.
	 *
	 * @param firstNodeIndex  first node
	 * @param secondNodeIndex second node
	 * @param g               must be not null
	 * @param addedNodes      must be not null
	 * @param edgeFactory     factory to create a new edge for 'g'.
	 * @param addedEdges      If null, the generated edges are not stored.
	 */
	private void addForwardOrBackwardEdge(
		int firstNodeIndex, int secondNodeIndex, TNGraph<OSTNUEdgePluggable> g, EdgeSupplier<OSTNUEdgePluggable> edgeFactory,
		LabeledNode[] addedNodes, ObjectList<OSTNUEdgePluggable> addedEdges) {

		final LabeledNode firstNode = addedNodes[firstNodeIndex];
		final LabeledNode secondNode = addedNodes[secondNodeIndex];
		final OSTNUEdgePluggable forwardEdge;
		final OSTNUEdgePluggable backEdge;
		int forwardWeight, backwardWeight;

		/*
		 * At first sight, one can argue that a negative forward edge is a backward edge.
		 * This implementation does not exploit such property because it has to consider also the in-Degree and the
		 * outDegree of each node.
		 */
		boolean isEdgeToAdd = rnd.nextDouble() <= edgeProb && (g.findEdge(firstNode, secondNode) == null) &&
		                      g.outDegree(firstNode) < outDegree && g.inDegree(secondNode) < inDegree;
		// forward edge weight
		if (isEdgeToAdd) {
			forwardWeight = ((rnd.nextBoolean()) ? -1 : 1) * rnd.nextInt(maxWeight);
			if (forwardWeight < 0) {
				//noinspection lossy-conversions
				forwardWeight /=
					1.5;//If negative, we make it less negative otherwise it is diffucult to have DC property
			}
			forwardEdge = edgeFactory.get(firstNode.getName() + "-" + secondNode.getName());
			forwardEdge.setValue(forwardWeight);
			g.addEdge(forwardEdge, firstNode, secondNode);
			if (addedEdges != null) {
				addedEdges.add(forwardEdge);
			}
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Added forward edge: " + forwardEdge);
			}
			return;// we try to put a backward edge ONLY if a forward was not added.
			// because we have experimented that it is quite difficult to create DC instanced adding loop
		}

		// we consider the possibility to add a backardEdge
		isEdgeToAdd = rnd.nextDouble() <= edgeProb && (g.findEdge(secondNode, firstNode) == null) &&
		              g.inDegree(firstNode) < inDegree && g.outDegree(secondNode) < outDegree;
		if (isEdgeToAdd) {
			backwardWeight = ((rnd.nextBoolean()) ? -1 : 1) * rnd.nextInt(maxWeight);
			if (backwardWeight < 0) {
				//noinspection lossy-conversions
				backwardWeight /=
					1.5;//If negative, we make it less negative otherwise it is diffucult to have DC property
			}
			backEdge = edgeFactory.get(secondNode.getName() + "-" + firstNode.getName());
			backEdge.setValue(backwardWeight);
			g.addEdge(backEdge, secondNode, firstNode);
			if (addedEdges != null) {
				addedEdges.add(backEdge);
			}
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Added backward edge: " + backEdge);
			}
		}
	}

	/**
	 * Adds a pair of edges between node given by addedNodes[firstNodeIndex] and node addedNodes[secondNodeIndex] such that addedNodes[firstNodeIndex] precedes
	 * addedNodes[secondNodeIndex]. The edges values are chosen randomly.
	 *
	 * @param firstNodeIndex  first node
	 * @param secondNodeIndex second node
	 * @param g               must be not null
	 * @param addedNodes      must be not null
	 * @param edgeFactory     factory of edges
	 */
	private void addPrecedenceEdges(
		int firstNodeIndex, int secondNodeIndex, TNGraph<OSTNUEdgePluggable> g, EdgeSupplier<OSTNUEdgePluggable> edgeFactory,
		LabeledNode[] addedNodes) {
		final LabeledNode firstNode = addedNodes[firstNodeIndex];
		final LabeledNode secondNode = addedNodes[secondNodeIndex];

		// forward edge weight
		int positiveWeight = rnd.nextInt(maxWeight);

		// backward edge weight
		int negativeWeight = -rnd.nextInt(maxWeight);
		int sum = positiveWeight + negativeWeight;
		while (sum < 0) {
			positiveWeight = rnd.nextInt(maxWeight);
			negativeWeight = -rnd.nextInt(maxWeight);
			sum = positiveWeight + negativeWeight;
		}
		OSTNUEdgePluggable e = edgeFactory.get(firstNode.getName() + "-" + secondNode.getName());
		e.setValue(positiveWeight);
		g.addEdge(e, firstNode, secondNode);
//		if (addedEdges != null) {
//			addedEdges.add(e);
//		}
		if (LOG.isLoggable(Level.FINER)) {
			LOG.finer("Added edge: " + e);
		}

		e = edgeFactory.get(secondNode.getName() + "-" + firstNode.getName());
		e.setValue(negativeWeight);
		g.addEdge(e, secondNode, firstNode);
//		if (addedEdges != null) {
//			addedEdges.add(e);
//		}
		if (LOG.isLoggable(Level.FINER)) {
			LOG.finer("Added edge: " + e);
		}

	}

	/**
	 * Decides randomly if an edge with a random value has to be added to {@code g}. If yes, it adds it as edge between node given by addedNodes[firstNodeIndex]
	 * and node addedNodes[secondNodeIndex].
	 *
	 * @param firstNodeIndex  index of source node
	 * @param secondNodeIndex index of the destination node
	 * @param g               the input network
	 * @param addedNodes      the array containing the added nodes, used to find the nodes specified by their indexes.
	 * @param edgeFactory     the edge factory
	 * @param addedEdges      the array of the added edges. It will contain the possible new edge made by this method.
	 *
	 * @return the added edge, if added; null otherwise.
	 */
	@Nullable
	private OSTNUEdgePluggable addRndEdge(
		int firstNodeIndex, int secondNodeIndex, TNGraph<OSTNUEdgePluggable> g, EdgeSupplier<OSTNUEdgePluggable> edgeFactory,
		LabeledNode[] addedNodes, ObjectList<OSTNUEdgePluggable> addedEdges) {
		final boolean isEdgeToAdd = rnd.nextDouble() <= edgeProb;
		if (!isEdgeToAdd) {
			return null;
		}
		final LabeledNode firstNode = addedNodes[firstNodeIndex];
		final LabeledNode secondNode = addedNodes[secondNodeIndex];
		if (g.outDegree(firstNode) > outDegree || g.inDegree(secondNode) > inDegree) {
			LOG.finer("Edge cannot be added because inDegree or outDegree bound would be violated.");
			return null;
		}

		final int weight = rnd.nextInt(maxWeight);
		final OSTNUEdgePluggable e = edgeFactory.get(firstNode.getName() + "-" + secondNode.getName());
		e.setValue((rnd.nextBoolean() ? -weight / 4
		                              : weight)); // A quarter of the weight when it must be negative: to make more probable DC instances
		// with negative
		// values.
		g.addEdge(e, firstNode, secondNode);
		addedEdges.add(e);
		if (LOG.isLoggable(Level.FINER)) {
			LOG.finer("Added edge: " + e);
		}
		return e;
	}

	/**
	 * Adjusts the weight of all edges (increase/decrease) considering {@link #weightAdjustment}.
	 *
	 * @param addedEdges the array of added edges
	 * @param increase   true if the weight values must be increase. The increment is given by {@link  #weightAdjustment}.
	 */
	private void adjustEdgeWeights(OSTNUEdgePluggable[] addedEdges, boolean increase) {
		/*
		 * Don't implement the following actions:
		 * 1. Limit the adjustment to <= this.maxWeight because, otherwise, many values are squeezed to this.maxWeight.
		 * 2. Adjust only positive values when increase and negative value when !increase because it never reaches a DC instance.
		 */
		final int sign = (increase) ? +1 : -1;
		for (final OSTNUEdgePluggable e : addedEdges) {
			final int oldV = e.getValue();
			int adjustment = sign * weightAdjustment;
			if (increase && oldV < 0) {
				adjustment /= 2;
			}

			e.setValue(oldV + adjustment);
		}
	}

	/**
	 * Builds a pair of DC and not DC of CSTN instances using the building parameters. The not DC instance is build adding one or more constraints to the
	 * previous generated DC instance.
	 *
	 * @param alsoNotDcInstance false if the not DC instances is required. If false, the returned not DC instance is an empty tNGraph.
	 *
	 * @return a pair of DC and not DC of CSTN instances. If the first member is null, it means that a generic error in the building has occurred. If
	 * 	alsoNotDcInstance is false, the returned not DC instance is null.
	 */
	private ObjectPair<TNGraph<OSTNUEdgePluggable>> buildAPairRndTNInstances(boolean alsoNotDcInstance) {

		LOG.info("Start building a new random instance");
		final TNGraph<OSTNUEdgePluggable> randomGraph = new TNGraph<>("", EdgeSupplier.DEFAULT_OSTNU_EDGE_CLASS);
		TNGraph<OSTNUEdgePluggable> notDCGraph = null;
//		LabeledNodeSupplier nodeFactory = randomGraph.getNodeFactory();
		final EdgeSupplier<OSTNUEdgePluggable> edgeFactory = randomGraph.getEdgeFactory();

		LOG.info("Adding " + nNodes + " nodes of which " + nCtgNodes + " will be contingents.");
		final LabeledNode[] addedNodes = generateNodesAndContingentLinks(randomGraph, edgeFactory);

		// Adding edges.
		final OSTNUEdgePluggable[] addedEdges;
		if (randomTree) {
			addedEdges = generateRandomTree(randomGraph, addedNodes, edgeFactory);
		} else {
			if (randomLane) {
				addedEdges = generateRandomLanes(randomGraph, addedNodes, edgeFactory);
			} else {
				addedEdges = generateRandomEdges(randomGraph, addedNodes, edgeFactory);
			}
		}

		TNGraph<OSTNUEdgePluggable> lastDC = randomGraph;

		final TNGraphMLWriter cstnWriter = new TNGraphMLWriter(null);

		int checkN = 0;// number of checks
		boolean nonDCfound = false, DCfound = false;
		final OSTNU ostnu = new OSTNU(randomGraph, timeOut);

		OSTNU.OSTNUCheckStatus status;

		while (true) {
			ostnu.reset();
			ostnu.setG(new TNGraph<>(randomGraph, EdgeSupplier.DEFAULT_OSTNU_EDGE_CLASS));
			if (LOG.isLoggable(Level.FINER)) {
				try {
					cstnWriter.save(ostnu.getG(), tmpNetwork);
				} catch (IOException e) {
					System.err.println(
						"It is not possible to save the result. File " + tmpNetwork + " cannot be created: " +
						e.getMessage() + ". Computation continues.");
				}
				LOG.finer("Current cstn saved as 'current.stnu' before checking.");
			}
			try {
				LOG.fine("DC Check started.");

				status = ostnu.agileControllabilityCheck();

				checkN++;
				LOG.fine("DC Check finished.");
			} catch (Exception ex) {
				final String fileName = "error%d.stnu".formatted(System.currentTimeMillis());
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("DC Check interrupted for the following reason: " + ex.getMessage() +
					          ". Instance is saved as " + fileName + ".");
				}
				final File d = getNewFile(dcSubDir.getParentFile(), fileName);
				try {
					Files.move(tmpNetwork, d);
				} catch (IOException e1) {
					LOG.finer(
						"Problem to save 'current.stnu' as non-valid instance for logging. Program continues anyway.");
				}
				return new ObjectPair<>(null, null);
			}
			if (status.timeout) {
				final String fileName = "timeOut%d.stnu".formatted(System.currentTimeMillis());
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("DC Check finished for timeout. Instance is saved as " + fileName + ".");
				}
				final File d = getNewFile(dcSubDir.getParentFile(), fileName);
				try {
					Files.move(tmpNetwork, d);
				} catch (IOException ex) {
					LOG.finer("Problem to save 'current.cstn' as time out instance. Program continues anyway.");
				}
				return new ObjectPair<>(null, null);
			}
			if (status.isControllable()) {
				LOG.finer("Random instance is DC.");
				lastDC = new TNGraph<>(randomGraph, EdgeSupplier.DEFAULT_OSTNU_EDGE_CLASS);
				DCfound = true;
				if (!nonDCfound && alsoNotDcInstance) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("Now, a not DC instance must be generated. Tentative #" + checkN);
					}
					// we lower the edge value
					adjustEdgeWeights(addedEdges, false);
				} else {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("The pair has been found after " + checkN + " iterations.");
					}
					return new ObjectPair<>(lastDC, notDCGraph);
				}
			} else {
				LOG.finer("Random instance is not DC.");
				notDCGraph = new TNGraph<>(randomGraph, EdgeSupplier.DEFAULT_OSTNU_EDGE_CLASS);
				nonDCfound = true;
				if (!DCfound) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("Now, a DC instance must be generated. Tentative #" + checkN);
					}
					adjustEdgeWeights(addedEdges, true);
				} else {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("The pair has been found after " + checkN + " iterations.");
					}
					return new ObjectPair<>(lastDC, notDCGraph);
				}
			}
			if (checkN > MAX_CHECKS) {
				LOG.finer("This network was checked more than " + MAX_CHECKS +
				          " times without finding the wanted pair. Program continues with another network.");
				return new ObjectPair<>(null, null);
			}
		}
	}

	/**
	 * @throws IllegalArgumentException if a parameter is not valid.
	 */
	private void checkParameters() {
		if (nNodes < MIN_NODES || nNodes < (nCtgNodes << 1) + 1) {
			throw new IllegalArgumentException(
				"The number of nodes is not valid. Value has to be greater than the double of the number of contingent nodes. Value " +
				MIN_NODES + " is the minimum.");
		}

		if (nCtgNodes < 0) {
			throw new IllegalArgumentException(
				"The number of contingent nodes is not valid. The value must be equal to or greater than 0.");
		}

		if (nOracles < 0) {
			LOG.warning("The number of oracle cannot be negative. Reset to 0.");
			nOracles = 0;
		}

		if (nOracles != 0 && nCtgNodes > 25) {
			throw new IllegalArgumentException(
				"Since it is required to add oracles, the number of contingent must be less than 26.");
		}

		if (nOracles != 0 && nCtgNodes < nOracles) {
			throw new IllegalArgumentException(
				"The number of oracles cannot be greater than the number of contingent nodes.");
		}

		if (maxWeight < MIN_MAX_WEIGHT) {
			throw new IllegalArgumentException(
				"The maximum edge weight value is not valid. Valid range = [" + MIN_MAX_WEIGHT + ", " +
				Integer.MAX_VALUE + "].");
		}

		if (((long) maxWeight * (nNodes)) >= Constants.INT_POS_INFINITE) {
			throw new IllegalArgumentException(
				"The maximum edge weight value combined with the number of nodes is not valid. maxWeight * #nodes must be < " +
				Constants.INT_POS_INFINITE);
		}

		if (maxContingentWeight <= 0) {
			throw new IllegalArgumentException(
				"The maximum contingent edge weight value is not valid. Valid range = [1, " + Integer.MAX_VALUE + "].");
		}

		if (maxContingentRange < 1 || maxContingentRange >= maxContingentWeight) {
			throw new IllegalArgumentException(
				"The maximum contingent edge range is not valid. Valid values in [1, " + maxContingentWeight + "].");
		}

		if (((long) maxContingentWeight * (nNodes)) >= Constants.INT_POS_INFINITE) {
			throw new IllegalArgumentException(
				"The maximum contingent edge weight value combined with the number of nodes is not valid. maxContingentWeight * #nodes must be < " +
				Constants.INT_POS_INFINITE);
		}

		if (edgeProb < 0 || edgeProb > 1.0) {
			throw new IllegalArgumentException("The edge probability is not valid. Valid range = [0.0, 1.0].");
		}

		if (dcInstances < 0 || dcInstances < notDCInstances) {
			throw new IllegalArgumentException(
				"The number of wanted DC instances is not valid. It must be positive and at least as notDCInstances value.");
		}

		if (notDCInstances < 0) {
			throw new IllegalArgumentException(
				"The number of wanted not DC instances is not valid. It must be positive and at most as dcInstances value.");
		}

		weightAdjustment = (int) (maxWeight * WEIGHT_MODIFICATION_FACTOR);

		if (randomTree) {
			if (nSons < 1 || nSons > nNodes) {
				throw new IllegalArgumentException(
					"The number of wanted sons is not valid. It must be in [1, " + nNodes + "].");
			}
			if (sonProb <= 0 || sonProb > 1) {
				throw new IllegalArgumentException("The son probability must be a value (0, 1].");
			}
		}
		if (randomLane) {
			if (lanes < 1 || lanes > nNodes) {
				throw new IllegalArgumentException(
					"The number of wanted lanes is not valid. It must be in [1, " + nNodes + "].");
			}
		}
		if (dense) {
			if (density < 0 || density > 1) {
				throw new IllegalArgumentException("The density is not valid. It must be in [0, 1].");
			}
			minNEdges = (int) (nNodes * (nNodes - 1) * density);
			if (nNodes * (inDegree + outDegree) < minNEdges) {
				throw new IllegalArgumentException(
					"The density is not compatible with the inDegree and outDegree values. Lower the density or increase inDegree and/or outDegree.");
			}
		}
	}

	/**
	 * Creates the main directory and the two sub dirs that will contain the random instances.
	 *
	 * @return prefix to use for creating file names
	 *
	 * @throws IOException if any directory cannot be created or moved.
	 */
	private String createFolders() throws IOException {
		final File baseDir = new File(baseDirName);

		if (!baseDir.exists()) {
			if (!baseDir.mkdirs()) {
				final String m = "Directory " + baseDir.getAbsolutePath() + " cannot be created!";
				LOG.severe(m);
				throw new IllegalStateException(m);
			}
		}
		String suffix = "_%snodes_%sctgs_%dmaxWeight_%dmaxCtgWeight_".formatted(
			makeNumberFormat(nNodes).formatted(nNodes),
			makeNumberFormat(nCtgNodes).formatted(nCtgNodes),
			maxWeight,
			maxContingentWeight);
		if (randomTree) {
			suffix += "%daryTree_%ssonProb".formatted(nSons, sonProb);
		} else {
			if (randomLane) {
				suffix += "%dlanes_".formatted(lanes);
			} else {
				suffix += "%dinDegree_%doutDegree".formatted(inDegree, outDegree);
			}
		}

		dcSubDir = new File(baseDir, DC_SUB_DIR_NAME + suffix);
		if (!dcSubDir.exists()) {
			if (!dcSubDir.mkdir()) {
				final String m = "Directory " + dcSubDir.getAbsolutePath() + " cannot be created!";
				LOG.severe(m);
				throw new IllegalStateException(m);
			}
		}

		notDCSubDir = new File(baseDir, NOT_DC_SUB_DIR_NAME + suffix);
		if (!notDCSubDir.exists()) {
			if (!notDCSubDir.mkdir()) {
				final String m = "Directory " + notDCSubDir.getAbsolutePath() + " cannot be created!";
				LOG.severe(m);
				throw new IllegalStateException(m);
			}
		}

		final String log = "Main directory where generated instances are saved: " + baseDir.getCanonicalPath() +
		                   "\nSub dir for DC instances:\t\t" + dcSubDir.getPath() +
		                   "\nSub dir for NOT DC instances:\t" + notDCSubDir.getPath();
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine(log);
		}
		System.out.println(log);

		return suffix;

	}

	/**
	 * Creates the two README files that describe the content of the two sub dir this.DCSubDir and this.notDCSubDir.
	 *
	 * @throws IOException if any file cannot be created.
	 */
	private void createReadmeFiles(OSTNURandomGenerator this) throws IOException {
		if (dcSubDir == null || notDCSubDir == null) {
			return;
		}

		String readmeText = "(O)STNU RANDOM INSTANCE BENCHMARK\n" + "==============================\n\n" +
		                    "This directory contains %04d %s (O)STNU random instances.\n" + // generator.dcInstances +
		                    "Each instance is built by a random algorithm tuned by the following parameters:\n" +
		                    "#nodes:\t\t\t\t" + String.format("%4d%n", Integer.valueOf(nNodes)) +
		                    "#contingent:\t\t" + String.format("%4d%n", Integer.valueOf(nCtgNodes)) +
		                    "max Outdegree:\t\t" + String.format("%4d%n", Integer.valueOf(outDegree)) +
		                    "max IDegree:\t\t" + String.format("%4d%n", Integer.valueOf(inDegree)) +
		                    "edgeProbability:\t" + String.format("%4.2f%n", Double.valueOf(edgeProb)) +
		                    "#max weight:\t\t" + String.format("%4d%n", Integer.valueOf(maxWeight)) +
		                    "#max contingent weight:\t" + String.format("%4d%n", Integer.valueOf(maxContingentWeight)) +
		                    "#max contingent range:\t" + String.format("%4d%n", Integer.valueOf(maxContingentRange)) +
		                    ((nOracles > 0) ? "#oracles:\t" + String.format("%4d%n", Integer.valueOf(nOracles)) : "");
		if (randomTree) {
			readmeText +=
				"#treeArity:\t\t" + String.format("%4d", Integer.valueOf(nSons)) + "\n" + "#sonProbability:\t" +
				String.format("%4.2f", Double.valueOf(sonProb)) + "\n";
		}
		if (randomLane) {
			readmeText += "#lanes:\t\t" + String.format("%4d", Integer.valueOf(lanes)) + "\n";
		}
		readmeText += "\n";

		final String readmeStr = "README.txt";
		try (final PrintWriter writer = new PrintWriter(getNewFile(dcSubDir, readmeStr), StandardCharsets.UTF_8)) {
			writer.format(readmeText, Integer.valueOf(dcInstances), "dynamic controllable (DC)");
		}
		try (final PrintWriter writer = new PrintWriter(getNewFile(notDCSubDir, readmeStr), StandardCharsets.UTF_8)) {
			writer.format(readmeText, Integer.valueOf(notDCInstances), "NOT dynamic controllable (NOTDC)");
		}
	}

	/**
	 * Generates the network nodes adding to the graph randomGraph and returns them as an array.<br> For contingent nodes, it also generates the contingent link
	 * (the two edges representing the link).
	 *
	 * @param randomGraph the graph where to add nodes and contingent links.
	 * @param edgeFactory factory for edges
	 *
	 * @return the array of random nodes generated.
	 */
	private LabeledNode[] generateNodesAndContingentLinks(
		TNGraph<OSTNUEdgePluggable> randomGraph, EdgeSupplier<OSTNUEdgePluggable> edgeFactory) {
		LOG.finer("The generation of nodes is started.");

		final LabeledNode[] addedNodes = new LabeledNode[nNodes + 1];// Z is not considered in the total number of nodes.
		int indexLastAddedNode = 0, indexLastAddedCtg = 0, indexAddedNodesArray = 0, indexLastAddedOracle = 0;
		// each slot (but last one) is composed by int(nNodes4Slot) nodes + a contingent link
		// each slot will be then manipulated by other methods to decide how to lay out nodes and contingent link.
		final int nSlots = nCtgNodes + 1;
		final double nNodes4Slot = (nNodes - 2.0 * nCtgNodes) / nSlots;

		// add nodes and ctg link interleaving nodes and contingent ones (with their activation nodes).
		double x, y = -Y_SHIFT + 30;
		for (int slot = 1; slot <= nSlots; slot++) {
			// each slot (but last one) is composed by int(nNodes4Slot) nodes + a contingent link
			// edges among ordinary nodes and contingent ones are added after by another method.

			// add ordinary nodes
			x = X_SHIFT;
			y += Y_SHIFT;
			while (++indexLastAddedNode <= (nNodes4Slot * slot)) {
				// LOG.finer("indexLastAddedNode: " + indexLastAddedNode + " <= " + nNodes4Slot * slot);
				final LabeledNode node = LabeledNodeSupplier.get("%s%d".formatted(NODE_NAME_PREFIX, indexLastAddedNode));
				node.setX(x);
				node.setY(y);
				randomGraph.addVertex(node);
				addedNodes[++indexAddedNodesArray] = node;
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Node added: " + node.getName());
				}
				x += X_SHIFT;
			}
			indexLastAddedNode--;
			if (slot == nSlots) {
				continue;// after last slot, no contingent link
			}

			// added a contingent pair
			indexLastAddedCtg++;
			final LabeledNode actNode = LabeledNodeSupplier.get("A%d".formatted(indexLastAddedCtg));
			actNode.setX(x);
			actNode.setY(y);
			randomGraph.addVertex(actNode);
			addedNodes[++indexAddedNodesArray] = actNode;
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Activation node added: " + actNode);
			}
			x += X_SHIFT;

			final LabeledNode ctgNode = LabeledNodeSupplier.get((nOracles != 0)
			                                                    ? String.valueOf((char) ('@' + indexLastAddedCtg)) //'@'+1='A'
			                                                    : "C%d".formatted(indexLastAddedCtg)
			                                                   );
			ctgNode.setContingent(true);
			ctgNode.setX(x);
			ctgNode.setY(y);
			randomGraph.addVertex(ctgNode);
			addedNodes[++indexAddedNodesArray] = ctgNode;
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Contingent node added: " + ctgNode);
			}

			if (indexLastAddedOracle < nOracles) {
				indexLastAddedOracle++;
				final LabeledNode oracleNode = LabeledNodeSupplier.get("O_" + ctgNode.getName());
				oracleNode.setX(x);
				oracleNode.setY(0);
				oracleNode.setObservable(ctgNode.getName().charAt(0));
				randomGraph.addVertex(oracleNode);
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Oracle added: " + oracleNode.getName());
				}
			}
			// add contingent edges
			int upperValue = 2 + rnd.nextInt(maxContingentWeight);
			if (upperValue > maxContingentWeight) {
				upperValue = maxContingentWeight;
			}
			int lowerValue = upperValue;
			while (lowerValue >= upperValue) {
				lowerValue = upperValue - (rnd.nextInt(maxContingentRange));
				if (lowerValue <= 0) {
					lowerValue = 1;
				}
			}

			OSTNUEdgePluggable e = edgeFactory.get(EDGE_NAME_PREFIX + actNode.getName() + "-" + ctgNode.getName());
			e.setConstraintType(ConstraintType.contingent);
			e.setValue(upperValue);
			randomGraph.addEdge(e, actNode, ctgNode);
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Upper link added: " + e);
			}

			e = edgeFactory.get(EDGE_NAME_PREFIX + ctgNode.getName() + "-" + actNode.getName());
			e.setConstraintType(ConstraintType.contingent);
			e.setValue(-lowerValue);
			randomGraph.addEdge(e, ctgNode, actNode);
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Lower link added: " + e);
			}
		}

		assert (indexLastAddedCtg == nCtgNodes);
		assert (indexAddedNodesArray == nNodes);

		final LabeledNode Z = LabeledNodeSupplier.get("Z");
		Z.setX(0);
		Z.setY(0);
		randomGraph.addVertex(Z);
		addedNodes[0] = Z;
		LOG.finer("Node Z added.");
		LOG.finer("The generation of nodes is finished.");

		return addedNodes;
	}

	/**
	 * Generates random edges considering only the edge probability. For each ordered pair on nodes, a random edge is added with probability {@link #edgeProb}.
	 *
	 * @param randomGraph the input network
	 * @param addedNodes  the array of added nodes so far
	 * @param edgeFactory the edge factory
	 *
	 * @return the array of random edges added.
	 */
	private OSTNUEdgePluggable[] generateRandomEdges(
		TNGraph<OSTNUEdgePluggable> randomGraph, LabeledNode[] addedNodes, EdgeSupplier<OSTNUEdgePluggable> edgeFactory) {
		LOG.finer("The generation of edges is started.");
		final ObjectList<OSTNUEdgePluggable> addedEdges = new ObjectArrayList<>();
		OSTNUEdgePluggable e, eI;
		for (int i = 1; i <= nNodes; i++) {// We do not put upper bound to Z.
			final int k = i + (isAnActivationTimepoint(addedNodes[i]) ? 2 : 1);// between activation and contingent nodes no random edges.
			for (int j = k; j <= nNodes; j++) {
				// avoid directed edge between activation and contingent link
//				final String source = addedNodes[i].getName().substring(0, 1);
//				final String dest = addedNodes[j].getName().substring(0, 1);
				e = addRndEdge(i, j, randomGraph, edgeFactory, addedNodes, addedEdges);
				eI = addRndEdge(j, i, randomGraph, edgeFactory, addedNodes, addedEdges);

				// avoid negative 2-edge loops
				if (e == null || eI == null) {
					continue;
				}
				final int s = e.getValue() + eI.getValue();
				if (s >= 0) {
					continue;
				}
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Negative loop " + e + " <-> " + eI + " Sum: " + s);
				}
				if (e.getValue() < 0) {
					e.setValue(-s);
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("Changed " + e);
					}
				} else {
					eI.setValue(-s);
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("Changed " + eI);
					}
				}
			}
		}
		LOG.finer("The generation of edges is finished.");
		return addedEdges.toArray(new OSTNUEdgePluggable[0]);
	}

	/**
	 * Generates {@link #lanes} lanes of tasks and adding constraints between a pair of tasks in different lanes.
	 *
	 * @param randomGraph the input network
	 * @param addedNodes  the array of added nodes so far
	 * @param edgeFactory the edge factory
	 *
	 * @return the array of random edges added.
	 */
	private OSTNUEdgePluggable[] generateRandomLanes(
		TNGraph<OSTNUEdgePluggable> randomGraph, LabeledNode[] addedNodes, EdgeSupplier<OSTNUEdgePluggable> edgeFactory) {
		LOG.finer("The generation of random edges in lanes started.");
		final ObjectList<OSTNUEdgePluggable> addedEdges = new ObjectArrayList<>();
		final int N = nNodes;// Z is counted
		final int L = lanes;

		/*
		 * laneBoundary is significative only for index =[1, L]
		 * laneBoundary[i-1]+1 is the index of the first node in lane i.
		 * laneBoundary[i] is the index of the last node in lane i.
		 */
		final int[] laneBoundary = new int[L + 1];
		laneBoundary[0] = 0;//it is just to satisfy the above rule. Moreover, Z is not in the lanes!
		laneBoundary[L] = nNodes;

		final double nodesPerLane = ((double) N) / L;
		double k = nodesPerLane;
		int bound;
		for (int i = 1; i < L; i++) {
			bound = (int) Math.round(k);
			if (isAnActivationTimepoint(addedNodes[bound])) {
				//the last node in the lane would be an activation timepoint. It is better to put it as the first node
				//of the next lane.
				bound--;
				k = bound;
			} else {
				if (addedNodes[bound].isContingent()) {
					bound -= 2; //the last node is contingent, It is better to put its activation time point as the first node
					//of the next lane.
					k = bound;
				}
			}
			laneBoundary[i] = bound;
			k += nodesPerLane;
		}

		double x = 0.0, y;
		for (int l = 1; l <= L; l++) {
			y = 0;
			x += X_SHIFT;
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Creating lane " + l + " using nodes in range [" + (laneBoundary[l - 1] + 1) + ", " +
				          laneBoundary[l] + "].");
			}
			for (int currentNode = laneBoundary[l - 1] + 1, relativeI = 0; currentNode <= laneBoundary[l]; currentNode++, y += Y_SHIFT, relativeI++) {
				addedNodes[currentNode].setX(x);
				addedNodes[currentNode].setY(y);

				if (currentNode == laneBoundary[l]) {
					continue;// for the last node in the lane, there is no other thing to do
				}
				if (isAnActivationTimepoint(addedNodes[currentNode])) {
					continue;// for the pair (A,C) edges are already set as contingent ones.
				}
				addPrecedenceEdges(currentNode, currentNode + 1, randomGraph, edgeFactory, addedNodes);
				//FIXME
//				//Alter the distance of the node following the contingent node 'A'
				if (nOracles > 0 && "A".equals(addedNodes[currentNode - 1].getName())) {
					OSTNUEdgePluggable e = randomGraph.findEdge(addedNodes[currentNode-1], addedNodes[currentNode]);
					if (e == null) {
						throw new IllegalStateException("Missing edge between " + addedNodes[currentNode-1] + " and " + addedNodes[currentNode]);
					}
					e.clear();
					e.setValue(-1);
					e = randomGraph.findEdge(addedNodes[currentNode], addedNodes[currentNode-1]);
					if (e == null) {
						throw new IllegalStateException("Missing edge between " + addedNodes[currentNode] + " and " + addedNodes[currentNode-1]);
					}
					e.clear();
					e.setValue(10);
				}
				// put some edges among siblings.
				for (int secondLane = l + 1; secondLane <= L; secondLane++) {
					final int secondLaneLB = laneBoundary[secondLane - 1] + 1;
					int secondIndex = secondLaneLB + relativeI + ((rnd.nextBoolean()) ? -1 : 1) * rnd.nextInt(3);
					if (secondIndex < secondLaneLB) {
						secondIndex = secondLaneLB;
					}
					if (secondIndex > laneBoundary[secondLane]) {
						secondIndex = laneBoundary[secondLane];
					}
					addForwardOrBackwardEdge(currentNode, secondIndex, randomGraph, edgeFactory, addedNodes,
					                         addedEdges);
				}
			}
		}

		LOG.finer("The generation of lane edges is finished.");
		return addedEdges.toArray(new OSTNUEdgePluggable[0]);

	}

	/**
	 * Generates a random tree having Z as root.
	 *
	 * @param randomGraph the input network
	 * @param addedNodes  the array of added nodes so far
	 * @param edgeFactory the edge factory
	 *
	 * @return the array of random edges added.
	 */
	@SuppressWarnings("AssignmentToForLoopParameter")
	private OSTNUEdgePluggable[] generateRandomTree(
		TNGraph<OSTNUEdgePluggable> randomGraph, LabeledNode[] addedNodes, EdgeSupplier<OSTNUEdgePluggable> edgeFactory) {
		LOG.finer("The generation of random tree edges is started.");
		final ObjectList<OSTNUEdgePluggable> addedEdges = new ObjectArrayList<>();
		final int N = nNodes + 1;// Z is counted
		final int K = nSons;

		final IntList parents = new IntArrayList();
		parents.add(0);
		final IntList sons = new IntArrayList();
		// for each height h, connect a parent node at height h with its sons
		int h = 0;
		double x, y = 0.0;
		for (int absoluteI = 1; absoluteI < N; h++) {
			y += Y_SHIFT;
			x = -X_SHIFT;
			for (final int parent : parents) {
				boolean add = false;
				int i = 0;
				x += X_SHIFT;
				for (; i < K; i++) {
					int son = (rnd.nextDouble() <= sonProb || (!add && i == K - 1)) ? absoluteI++ : 0;// 0 is not used
					if (son >= N)// if Z is counted put =
					{
						break;
					}
					if (son == 0) {
						continue;
					}
					sons.add(son);
					add = true;
					addedNodes[son].setX(x);
					x += X_SHIFT;
					addedNodes[son].setY(y);
					addPrecedenceEdges(parent, son, randomGraph, edgeFactory, addedNodes);

					if (isAnActivationTimepoint(addedNodes[son])) {
						// the contingent node is put as brother even if it is the last
						son = absoluteI++;
						sons.add(son);
						addedNodes[son].setX(x);
						x += X_SHIFT;
						addedNodes[son].setY(y);
						i++;
					}
				}
				if (absoluteI >= N) {
					break;
				}
			}
			final int nSon = sons.size();
			if (nSon > 5) {
				// put some edges among siblings.
				for (int i = 0; i < nSon; i++) {
					for (int j = i + 1; j < nSon; j++) {
						addForwardOrBackwardEdge(sons.getInt(i), sons.getInt(j), randomGraph, edgeFactory, addedNodes,
						                         addedEdges);
					}
				}
			}
			parents.clear();
			parents.addAll(sons);
			sons.clear();
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Tree height " + h + " completed.");
			}
		}

		LOG.finer("The generation of tree edges is finished.");
		return addedEdges.toArray(new OSTNUEdgePluggable[0]);
	}

	/**
	 * Given an instance, determines a dense version of it adding redundant edges according to {@link #density} and {@link #minNEdges} parameters.
	 *
	 * @param instance the input network
	 *
	 * @return the dense version
	 */
	@Nullable
	private TNGraph<OSTNUEdgePluggable> makeDenseInstance(TNGraph<OSTNUEdgePluggable> instance) {
		if (instance == null) {
			return null;
		}
		if (LOG.isLoggable(Level.FINER)) {
			LOG.finer("Making a dense instance. Required density: " + density + ". Required edges: " + minNEdges);
		}

		final TNGraph<OSTNUEdgePluggable> denseI = new TNGraph<>(instance, instance.getEdgeImplClass());
		final int nCurrentEdges = denseI.getEdgeCount();
		int missingEdges = minNEdges - nCurrentEdges;
		if (LOG.isLoggable(Level.FINER)) {
			LOG.finer("Current #edges: " + nCurrentEdges + ". Missing edges: " + missingEdges);
		}

		int nNodeWithOutEdges = 0;
		for (final LabeledNode node : denseI.getVertices()) {
			if (denseI.outDegree(node) > 0) {
				nNodeWithOutEdges++;
			}
		}
		if (missingEdges > 0) {
			int hop = 4;
			final int edgesForNode = missingEdges / nNodeWithOutEdges;
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("#edges for node: " + edgesForNode);
			}
			while (missingEdges > 0) {
				boolean stillPossibleToAdd = false;
				for (final LabeledNode node : denseI.getVertices()) {
					final int add = addRedundantNewEdges(node, null, 0, hop, edgesForNode, denseI);
					if (add != Constants.INT_NULL) {
						missingEdges -= add;
						stillPossibleToAdd = true;
						if (LOG.isLoggable(Level.FINER)) {
							LOG.finer("Added #edges: " + add + ". Missing edges: " + missingEdges);
						}
					}
					if (missingEdges <= 0) {
						break;
					}
				}
				if (!stillPossibleToAdd) {
					LOG.warning("It is not possible to add more edges! Giving up!");
					break;
				}
				hop++;
			}
		}
		return denseI;
	}

	/**
	 * Simple method to manage command line parameters using args4j library.
	 *
	 * @param args the arguments from the command line.
	 *
	 * @return false if a parameter is missing, or it is wrong. True if every parameter is given in a right format.
	 */
	private boolean manageParameters(final String[] args) {
		final CmdLineParser parser = new CmdLineParser(this);
		try {
			parser.parseArgument(args); // parse the arguments.

			checkParameters(); // check the parameters!

		} catch (final CmdLineException | IllegalArgumentException e) {
			System.err.println(e.getMessage());
			System.err.println("Usage: java " + getClass().getName() + " [options...]");
			// print the list of available options
			parser.printUsage(System.err);
			System.err.println();

			// print option sample. This is useful some time
			System.err.println("Required options: java -jar CSTNU-*.*.*-SNAPSHOT.jar " + getClass().getName() + " " +
			                   parser.printExample(OptionHandlerFilter.REQUIRED));
			return false;
		}
		return true;
	}

	private static boolean isAnActivationTimepoint(LabeledNode node) {
		final String name = node.getName();
		return (name.length() > 1 && name.charAt(0) == 'A');//no activation name can be just 'A'
	}
}
