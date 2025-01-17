// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

package it.univr.di.cstnu.algorithms;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import it.unimi.dsi.fastutil.chars.CharAVLTreeSet;
import it.unimi.dsi.fastutil.chars.CharSortedSet;
import it.unimi.dsi.fastutil.objects.*;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.univr.di.Debug;
import it.univr.di.cstnu.graph.*;
import it.univr.di.cstnu.visualization.CSTNUStaticLayout;
import it.univr.di.labeledvalue.*;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static it.univr.di.cstnu.graph.Edge.ConstraintType.derived;

/**
 * Represents a Simple Temporal Network with Uncertainty and Oracles (OSTNU).
 *
 * <p>This class implementation considers instantaneous reactions and checks the agile controllability assuming
 * that the network can be executed adopting an early execution strategy.
 * <p>
 * At 2024-02-14, the agile controllability is checked propagating a modified version of Morris' rules:
 *     <ol>
 *         <li>No case rule</li>
 *         <li>new upper case rule</li>
 *         <li>new lower case rule</li>
 *         <li>cross case rule</li>
 *         <li>label removal rule</li>
 *         <li>oracle rule</li>
 *     </ol>
 * <p>
 *     Each oracle is associated to a specific contingent node.
 *     So, if there is a contingent node 'C' and an oracle 'OC' for it, node OC ha in its observation field the value 'C'.
 *     <br>
 *     New upper/lower case rules and oracle rule determines new rules labeled by the fact the oracle is involved or not.
 *     <br>
 *     In particular, each value determined by oracle rule involving an oracle 'OC' having observation field = 'C'
 *     is labeled by 'C'. Each value determined by new lower/upper rule that involve contingent node 'C' is labeled by '¬C'.
 *      <br>
 *     In other words, label 'C' and label '¬C' represent two possible values generated considering contingent node 'C'
 *     that cannot be combined because they are alternative. 'C' and '¬C' can be considered literals of a boolean
 *     proposition 'C'.
 *      <br>
 *     Other rules, consider and propagate only values having label that are consistent, i.e., labels that not present
 *     opposite literals.
 * </p>
 *
 * <br>Constraint values (represented as edge values) are integers.
 *
 * @author Roberto Posenato
 * @version $Rev: 840 $
 */
public class OSTNU extends AbstractCSTN<OSTNUEdgePluggable> {
	/**
	 * Simple class to represent the status of the checking algorithm during an execution.<br>
	 * {@code controllability = super.consistency}.
	 *
	 * @author Roberto Posenato
	 */
	public static class OSTNUCheckStatus extends CSTNCheckStatus {

		// controllability = super.consistency!
		/**
		 * Scenario containing negative cycle. I use LabeledIntTreeMap for maintaining the set minimal w.r.t. the length
		 * of label but without the simplification on (¬a, 0)(a,1)-->(empty,0)(a,1).
		 * <br>
		 * Don't access to this map directly, but use methods {@link #addNegativeScenario(Label)},
		 * {@link #getNegativeScenarios()}, {@link #isInNegativeScenarios(Label)}.
		 */
		private final LabeledIntMap negativeScenarios =
			(new LabeledIntMapSupplier<>(LabeledIntMapSupplier.DEFAULT_LABELEDINTMAP_CLASS)).get(false);
		/**
		 * Counters about the # of application of different rules.
		 */
		public int crossCaseRuleCalls;
		/**
		 * Counters about the # of application of different rules.
		 */
		public int letterRemovalRuleCalls;
		/**
		 * Counters about the # of application of different rules.
		 */
		public int lowerCaseRuleCalls;
		/**
		 * Counters about the # of application of different rules.
		 */
		public int oracleRuleCalls;
		/**
		 * Map (Ctg,Node)-->proposition
		 */
		public Object2ObjectMap<LabeledNode, Object2CharMap<LabeledNode>> propositionOfPair =
			new Object2ObjectLinkedOpenHashMap<>();
		/**
		 * Counters about the # of application of different rules.
		 */
		public int upperCaseRuleCalls;
		/**
		 * First free proposition
		 */
		char firstProposition = 'a';

		/**
		 * Adds to negative scenarios maintain minimal the representation of the set.
		 *
		 * @param label label to add
		 */
		public void addNegativeScenario(@Nonnull Label label) {
			this.negativeScenarios.put(label, 0);
		}

		/**
		 * @return the negative scenarios
		 */
		public ObjectSet<Label> getNegativeScenarios() {
			return negativeScenarios.keySet();
		}

		/**
		 * @return the value of controllability
		 */
		public boolean isControllable() {
			return consistency;
		}

		@Override
		public void reset() {
			super.reset();
			oracleRuleCalls = 0;
			lowerCaseRuleCalls = 0;
			crossCaseRuleCalls = 0;
			letterRemovalRuleCalls = 0;
			upperCaseRuleCalls = 0;
			negativeScenarios.clear();
			propositionOfPair.clear();
			firstProposition = 'a';
		}

		/**
		 * Set the controllability value!
		 *
		 * @param controllability true if it is controllable
		 */
		public void setControllability(boolean controllability) {
			consistency = controllability;
		}

		@Override
		public String toString() {
			final StringBuilder msg = new StringBuilder(200);
			msg.append("The check is %s finished after %d cycle(s)%n".formatted((finished ? "" : " NOT"), cycles));
			if (finished) {
				msg.append("The controllability check has determined that given network is")
					.append(consistency ? " " : " NOT ")
					.append("agilely controllable.\n");
			}
			if (!consistency && negativeLoopNode != null) {
				msg.append("The negative loop is on node ").append(negativeLoopNode).append("\n");
			}
			msg.append("Some statistics:\n");
			msg.append("Rule no case propagation has been applied ").append(propagationCalls).append(" times.\n");
			msg.append("Rule Oracle has been applied ").append(oracleRuleCalls).append(" times.\n");
			msg.append("Rule Labeled Upper Case has been applied ").append(upperCaseRuleCalls).append(" times.\n");
			msg.append("Rule Labeled Lower Case has been applied ").append(lowerCaseRuleCalls).append(" times.\n");
			msg.append("Rule Labeled Cross-Lower Case has been applied ").append(crossCaseRuleCalls)
				.append(" times.\n");
			msg.append("Rule Labeled Letter Removal has been applied ").append(letterRemovalRuleCalls)
				.append(" times.\n");
			msg.append("Scenarios containing negative loops: ").append(this.getNegativeScenarios()).append("\n");
			msg.append("Propositions map: ").append(propositionOfPair).append("\n");
			msg.append("The global execution time has been ").append(executionTimeNS).append(" ns (~")
				.append(executionTimeNS / 1E9).append(" s.)");
			return msg.toString();
		}

		/**
		 * Checks if the label belongs to the negativeScenarios.
		 *
		 * @param label1 label to check
		 *
		 * @return true if the label1 belongs to the negative scenarios.
		 */
		boolean isInNegativeScenarios(@Nonnull final Label label1) {
			for (final Label negativeScenario : this.negativeScenarios.keySet()) {
				if (label1.subsumes(negativeScenario)) {
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							LOG.log(Level.FINER,
							        "Label " + label1 + " contains the negative scenario " + negativeScenario);
						}
					}
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * Version of the class
	 */
	static final public String VERSIONandDATE = "Version alpha-February, 14 2024";

	/**
	 * logger
	 */
	static private final Logger LOG = Logger.getLogger(OSTNU.class.getName());

	/*
	 * Static initializer
	 */
	static {
		FILE_NAME_SUFFIX = ".ostnu";// Override suffix
	}

	/**
	 * @param value    value
	 * @param nodeName node name to put as lower case label
	 * @param label    label to represent
	 *
	 * @return the conventional representation of a labeled value
	 */
	static String lowerCaseValueAsString(ALabel nodeName, int value, Label label) {
		return LabeledLowerCaseValue.entryAsString(nodeName, value, label, true);
	}

	/**
	 * Reads a CSTNU file and checks it.
	 *
	 * @param args an array of {@link String} objects.
	 *
	 * @throws IOException                  if any.
	 * @throws ParserConfigurationException if any.
	 * @throws SAXException                 if any.
	 */
	public static void main(final String[] args) throws IOException, ParserConfigurationException, SAXException {
		final OSTNU ostnu = new OSTNU();
		System.out.println(ostnu.getVersionAndCopyright());
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "Start...");
			}
		}
		if (!ostnu.manageParameters(args)) {
			return;
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "Parameters ok!");
			}
		}
		if (ostnu.versionReq) {
			System.out.println("OSTNU " + VERSIONandDATE + ". Academic and non-commercial use only.\n" +
			                   "Copyright © 2024 Roberto Posenato");
			return;
		}

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "Loading graph...");
			}
		}
		final TNGraphMLReader<OSTNUEdgePluggable> graphMLReader = new TNGraphMLReader<>();

		ostnu.setG(graphMLReader.readGraph(ostnu.fInput, EdgeSupplier.DEFAULT_OSTNU_EDGE_CLASS));
		ostnu.g.setInputFile(ostnu.fInput);

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "TNGraph loaded!\nDC Checking...");
			}
		}
		System.out.println("Checking started...");
		final OSTNUCheckStatus status;
		try {
			status = ostnu.agileControllabilityCheck();
		} catch (final WellDefinitionException e) {
			System.out.print("An error has been occurred during the checking: " + e.getMessage());
			return;
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "TNGraph minimized!");
			}
		}
		if (status.finished) {
			System.out.println("Checking finished!");
			if (status.consistency) {
				System.out.println("The given network is agilely controllable!");
			} else {
				System.out.println("The given network is NOT agilely controllable!");
			}
			System.out.println("Checked graph saved as " + ostnu.fOutput.getCanonicalPath());

			System.out.println("Details: " + status);
		} else {
			System.out.println("Checking has not been finished!");
			System.out.println("Details: " + status);
		}

		if (ostnu.fOutput != null) {
			final TNGraphMLWriter graphWriter = new TNGraphMLWriter(new CSTNUStaticLayout<>(ostnu.g));
			graphWriter.save(ostnu.getGChecked(), ostnu.fOutput);
		}
	}

	/**
	 * @param value    value
	 * @param nodeName node name to put as upper case label
	 * @param label    label to represent
	 *
	 * @return the conventional representation of a labeled value
	 */
	static String upperCaseValueAsString(ALabel nodeName, int value, Label label) {
		return LabeledALabelIntTreeMap.entryAsString(label, value, nodeName);
	}

	/**
	 * Removes the labeled value identified by label in the given map.
	 *
	 * @param label label of the value to remove
	 * @param aleph possible ALabel if the value is an upper-case value
	 * @param edge  edge where the labeled value is present.
	 */
	private static void removeLabeledValueBecauseInNegativeScenario(@Nonnull Label label, @Nullable ALabel aleph,
	                                                                OSTNUEdgePluggable edge) {
		if (aleph == null || aleph.equals(ALabel.emptyLabel)) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("Removing labeled value identified by label " + label + " from edge " + edge +
					           " because it belongs to a negative scenario.");
				}
			}
			edge.removeLabeledValue(label);
		} else {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest(
						"Remove upper case value associated to " + aleph + " and label " + label + " from edge " +
						edge +
						" because it belongs to a negative scenario.");
				}
			}
			edge.removeUpperCaseValue(label, aleph);
		}
	}

	/**
	 * Agile status
	 */
	@SuppressWarnings("FieldNameHidesFieldInSuperclass")
	OSTNUCheckStatus checkStatus;
	/**
	 * Utility map that returns the activation time point (node) associated to a contingent link given the contingent
	 * time point, i.e., contingent link A===&gt;C determines the entry (C,A) in this map.
	 */
	Object2ObjectMap<LabeledNode, LabeledNode> activationNode;
	/**
	 * Utility map that returns the oracle node O_C associated to a contingent node C. The oracle node contain the
	 * contingent node name in its 'proposition' field. The map is (C, O_C).
	 */
	Object2ObjectMap<LabeledNode, LabeledNode> oracleNode;
	/**
	 * Represent contingent links also as ordinary constraints.
	 */
	boolean contingentAlsoAsOrdinary;
	/**
	 * Utility map that return the edge containing the lower case constraint of a contingent link given the contingent
	 * time point.
	 */
	Object2ObjectMap<LabeledNode, OSTNUEdgePluggable> lowerContingentEdge;

	/**
	 * Helper constructor for CSTNU.
	 * <p>
	 * This constructor is useful for making easier the use of this class in environment like Node.js-Java
	 *
	 * @param graphXML the TNGraph to check in GraphML format
	 *
	 * @throws IOException                  if any error occurs during the graphXML reading
	 * @throws ParserConfigurationException if graphXML contains character that cannot be parsed
	 * @throws SAXException                 if graphXML is not valid
	 */
	public OSTNU(String graphXML) throws IOException, ParserConfigurationException, SAXException {
		this();
		setG((new TNGraphMLReader<OSTNUEdgePluggable>()).readGraph(graphXML, EdgeSupplier.DEFAULT_OSTNU_EDGE_CLASS));
	}

	/**
	 * Constructor for CSTNU
	 *
	 * @param graph       TNGraph to check
	 * @param giveTimeOut timeout for the check in seconds
	 */
	public OSTNU(TNGraph<OSTNUEdgePluggable> graph, int giveTimeOut) {
		this(graph);
		timeOut = giveTimeOut;
	}

	/**
	 * Constructor for CSTNU
	 *
	 * @param graph TNGraph to check
	 */
	public OSTNU(TNGraph<OSTNUEdgePluggable> graph) {
		this();
		setG(graph);
	}

	/**
	 * Default constructor, package use only!
	 */
	OSTNU() {
		checkStatus = new OSTNUCheckStatus();
		activationNode = new Object2ObjectOpenHashMap<>();
		oracleNode = new Object2ObjectOpenHashMap<>();
		lowerContingentEdge = new Object2ObjectOpenHashMap<>();
		propagationOnlyToZ = false;
		contingentAlsoAsOrdinary = true;
		reactionTime = 0;// IR semantics
		cleanCheckedInstance = true;
	}

	/**
	 * Checks the agile controllability (AC) of the given network (see {@link #OSTNU(TNGraph)} or
	 * {@link #setG(TNGraph)}).<br> If the network is AC, it determines all the minimal ranges for the constraints. <br>
	 * During the execution of this method, the given network is modified. <br> If the check is successful, all
	 * constraints to node Z in the network are minimized; otherwise, the network contains a negative loop at least.
	 * <br>
	 * After a check, {@link #getGChecked()} returns the graph resulting after the check and {@link #getCheckStatus()}
	 * the result of the checking action with some statistics and the node with the negative loop is the network is NOT
	 * DC.<br> In any case, before returning, this method call {@link #saveGraphToFile()} for saving the computed
	 * graph.
	 *
	 * @return an {@link OSTNUCheckStatus} object containing the final status and some statistics about the executed
	 * 	checking.
	 *
	 * @throws WellDefinitionException if any.
	 */
	public OSTNUCheckStatus agileControllabilityCheck() throws WellDefinitionException {
		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO, "\nStarting checking OSTNU agile controllability...\n");
			}
		}

		try {
			initAndCheck();
		} catch (final IllegalArgumentException e) {
			throw new IllegalArgumentException(
				"The graph has a problem, and it cannot be initialized: " + e.getMessage());
		}

		final EdgesToCheck<OSTNUEdgePluggable> edgesToCheck = new EdgesToCheck<>(g.getEdges());

		final int n = g.getVertexCount();
		int k = g.getContingentNodeCount();
		if (k == 0) {
			k = 1;
		}
		int p = g.getObserverCount();
		if (p == 0) {
			p = 1;
		}
		// horizon * |T|^2 3^|P| 2^|L|
		int maxCycles = horizon * n * n * p * p * p * k * k;
		if (maxCycles <= 0) {
			maxCycles = Integer.MAX_VALUE;
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.info("The maximum number of possible cycles is " + maxCycles);
			}
		}

		int i;
		checkStatus.finished = false;
		final Instant startInstant = Instant.now();
		final Instant timeoutInstant = startInstant.plusSeconds(timeOut);
		for (i = 1; i <= maxCycles && checkStatus.consistency && !checkStatus.finished; i++) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.log(Level.INFO, "*** Start Main Cycle " + i + "/" + maxCycles + " ***");
				}
			}

			checkStatus = oneStepAgileControllability(edgesToCheck, timeoutInstant);

			if (!checkStatus.finished) {
				if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
					if (Debug.ON) {
						final String msg =
							"During the check # " + i + " time out of " + timeOut + " seconds occurred. ";
						if (LOG.isLoggable(Level.INFO)) {
							LOG.log(Level.INFO, msg);
						}
					}
					checkStatus.executionTimeNS = Duration.between(startInstant, Instant.now()).toNanos();
					saveGraphToFile();
					return checkStatus;
				}
			}
			if (checkStatus.consistency) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						final StringBuilder log =
							new StringBuilder("During the check n. " + i + ", " + edgesToCheck.size() +
							                  " edges have been added/modified. Check has to continue.\nDetails of only modified edges having values:\n");
						for (final OSTNUEdgePluggable e : edgesToCheck) {
							log.append("Edge ").append(e).append("\n");
						}
						LOG.log(Level.FINER, log.toString());
					}
				}
			} else {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.INFO)) {
						LOG.log(Level.INFO,
						        "During the check n. " + i +
						        ", it has been stated that the network is not Agilely Controllable." + "\nStatus: " +
						        checkStatus);
					}
				}
				return checkStatus;
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.log(Level.FINE, "*** End Main Cycle " + i + "/" + maxCycles + " ***\n\n");
				}
			}
		}
		if (i > maxCycles && !checkStatus.finished) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.log(Level.INFO, "The maximum number of cycle (+" + maxCycles + ") has been reached!\nStatus: " +
					                    checkStatus);
				}
			}
			checkStatus.consistency = checkStatus.finished;
//			saveGraphToFile();
			return checkStatus;
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "Check consistency of all max projection.");
			}
		}
		final TNGraph<OSTNUEdgePluggable> allMax = this.makeAllMaxProjection();
		checkStatus.consistency = isAllMaxMinimalGraphConsistent(allMax);
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "All max projection check done.");
			}
		}
		final Instant endInstant = Instant.now();
		checkStatus.executionTimeNS = Duration.between(startInstant, endInstant).toNanos();

		if (!checkStatus.consistency) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.log(Level.INFO,
					        "After " + (i - 1) +
					        " cycle, it has been stated that the network is not Agilely Controllable." + "\nStatus: " +
					        checkStatus);
				}
			}
			saveGraphToFile();
			return checkStatus;
		}

		// controllable && finished
		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO,
				        "Stable state reached. The network is Agilely Controllable.\nNumber of cycles: " + (i - 1) +
				        " over the maximum allowed " + maxCycles +
				        ".\nStatus: " + checkStatus);
				LOG.log(Level.INFO, "Removing all labeled values associated to negative scenarios: " +
				                    checkStatus.negativeScenarios);
			}
		}

		removeLabeledValuesBelongingToNegativeScenarios();
		if (cleanCheckedInstance) {
			gCheckedCleaned = new TNGraph<>(g.getName(), g.getEdgeImplClass());
			gCheckedCleaned.copyCleaningRedundantLabels(g);
		}
		saveGraphToFile();
		return checkStatus;
	}

	/**
	 * {@inheritDoc} Wrapper method for {@link #agileControllabilityCheck()}
	 */
	@Override
	public OSTNUCheckStatus dynamicConsistencyCheck() throws WellDefinitionException {
		return agileControllabilityCheck();
	}

	/**
	 * {@inheritDoc} Calls  and, then, check all contingent links. This method works only with streamlined instances!
	 *
	 * @throws WellDefinitionException if the initial graph is not well-defined. We preferred to throw an exception
	 *                                 instead of returning a negative status to stress that any further operation
	 *                                 cannot be made on this instance.
	 */
	@Override
	public void initAndCheck() throws WellDefinitionException {
		if (checkStatus.initialized) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.WARNING)) {
					LOG.log(Level.WARNING,
					        "Initialization of a OSTNU can be done only one time! Reload the graph if a new init is necessary!");
				}
			}
			return;
		}

		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO, "Starting checking graph as OSTNU well-defined instance...");
			}
		}

		//core
		g.clearCache();
		gCheckedCleaned = null;

		LabeledNode Z = g.getZ();

		// Checks the presence of Z node!
		if (Z == null) {
			Z = g.getNode(AbstractCSTN.ZERO_NODE_NAME);
			if (Z == null) {
				// We add by authority!
				Z = LabeledNodeSupplier.get(AbstractCSTN.ZERO_NODE_NAME);
				Z.setX(10);
				Z.setY(10);
				g.addVertex(Z);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.WARNING)) {
						LOG.log(Level.WARNING, "No " + AbstractCSTN.ZERO_NODE_NAME + " node found: added!");
					}
				}
			}
			g.setZ(Z);
		} else {
			if (!Z.getLabel().isEmpty()) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.WARNING)) {
						LOG.log(Level.WARNING, "In the graph, Z node has not empty label. Label removed!");
					}
				}
				Z.setLabel(Label.emptyLabel);
			}
		}
		// Checks well definiteness of edges and determine maxWeight
		int minNegWeightFound = 0, maxWeightFound = 0;
		for (final OSTNUEdgePluggable e : g.getEdges()) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Initial Checking edge e: " + e);
				}
			}
			// Determines the absolute max weight value
			for (final Object2IntMap.Entry<Label> entry : e.getLabeledValueSet()) {
				final int v = entry.getIntValue();
				if (entry.getKey().containsUnknown()) {
					continue;// the graph contains labeled values of a checking.
				}
				if (v != Constants.INT_NEG_INFINITE && v < minNegWeightFound) {
					// if the distance is -∞, then the node must not be executed.
					// Such a constraints cannot contribute to find the minimum span of the network.
					minNegWeightFound = v;
				} else {
					if (v != Constants.INT_POS_INFINITE && v > maxWeightFound) {
						maxWeightFound = v;
					}
				}
			}

			final LabeledNode s = g.getSource(e);
			final LabeledNode d = g.getDest(e);

			if (s == d) {
				// loop are not admissible
				g.removeEdge(e);
				continue;
			}
			if (e.isEmpty()) {
				// The merge removed labels...
				g.removeEdge(e);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.WARNING)) {
						LOG.log(Level.WARNING,
						        "Labels fixing on edge " + e + " removed all labels. Edge " + e + " has been removed.");
					}
				}
				//				continue;
			}
		}
		// manage maxWeight value
		// don't remove this code because it is fundamental for determining the right upper bounds!
		maxWeight = Math.max(-minNegWeightFound, maxWeightFound);
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE,
				        "Propagating everywhere, so masWeight is the max between the opposite of the most negative one and the max positive : " +
				        (-minNegWeightFound) + ", " + maxWeightFound);
			}
		}

		// Determine horizon value
		long product = ((long) maxWeight) * (g.getVertexCount() - 1);// Z doesn't count!
		if (product >= Constants.INT_POS_INFINITE) {
			throw new ArithmeticException(
				"Horizon value is not representable by an integer. maxWeight = " + maxWeight + ", #vertices = " +
				g.getVertexCount());
		}
		horizon = (int) product;
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "The horizon value is " + String.format("%6d", product));
			}
		}


		//check contingent links. It is separated from the previous cycle for clarity reasons.
		int maxWeightContingent = Constants.INT_POS_INFINITE;
		for (final OSTNUEdgePluggable e : g.getEdges()) {
			if (!e.isContingentEdge()) {
				continue;
			}
			final LabeledNode s = g.getSource(e);
			final LabeledNode d = g.getDest(e);
			/*
			 * Manage contingent link.
			 * A contingent link can have, alternatively:
			 * 1. only one labeled value
			 * 2. a labeled value and a lower case one
			 * 3. a labeled value and an upper case one.
			 */
			final Entry<Label> minLabeledValue =
				e.getMinLabeledValue(); // among possible labeled values, we get the minimum one as a possible.

			int initialValue = minLabeledValue.getIntValue();
			final Label conjunctedLabel = minLabeledValue.getKey();

			if (initialValue == Constants.INT_NULL) {
				if (e.lowerCaseValueSize() == 0 && e.upperCaseValueSize() == 0) {
					throw new IllegalArgumentException(
						"Contingent edge " + e +
						" cannot be initialized because it hasn't an initial value neither a lower/upper case value.");
				}
			}
			if (initialValue == 0) {
				assert d != null;
				if (d.isObserver() && e.upperCaseValueSize() > 0) {
					e.removeLabeledValue(conjunctedLabel);
					initialValue = Constants.INT_NULL;
				} else {
					throw new IllegalArgumentException(
						"Contingent edge " + e +
						" cannot have a bound equals to 0. The two bounds [x,y] have to be 0 < x < y < ∞.");
				}
			}

			final OSTNUEdgePluggable eInverted = g.findEdge(d, s);
			if (eInverted == null) {
				assert d != null;
				assert s != null;
				throw new IllegalArgumentException(
					"Contingent edge " + e + " is alone. The companion contingent edge between " + d.getName() +
					" and " + s.getName() +
					" does not exist while it must exist!");
			}
			if (!eInverted.isContingentEdge()) {
				throw new IllegalArgumentException(
					"Edge " + e + " is contingent while the companion edge " + eInverted +
					" is not contingent!\nIt must be!");
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Edge " + e + " is contingent. Found its companion: " + eInverted);
				}
			}
			/*
			 * Memo.
			 * If current initialValue is negative, current edge is the lower bound C---&gt;A. The lower case labeled value has to be put in the inverted edge.
			 * If the lower case labeled value is already present, it must be equal.
			 * If current initialValue is positive, current edge is the upper bound A---&gt;C. The upper case labeled value has to be put in the inverted edge.
			 * If the upper case labeled value is already present, it must be equal.
			 * if current initialValue is undefined, then we assume that the contingent link is already set.
			 */
			if (initialValue != Constants.INT_NULL) {
				final int eInvertedInitialValue;
				int lowerCaseValue;
				int upperCaseValue;
				eInvertedInitialValue = eInverted.getValue(conjunctedLabel);

				if (initialValue < 0) {
					// current edge is the lower bound.
					assert s != null;
					final ALabel sourceALabel = new ALabel(s.getName(), g.getALabelAlphabet());
					lowerCaseValue = eInverted.getLowerCaseValue().getValue();
					if (lowerCaseValue != Constants.INT_NULL && -initialValue != lowerCaseValue) {
						throw new IllegalArgumentException(
							"Edge " + e + " is contingent with a negative value and the inverted " + eInverted +
							" already contains a ***different*** lower case value: " +
							lowerCaseValueAsString(sourceALabel, lowerCaseValue, conjunctedLabel) + ".");
					}
					if (lowerCaseValue == Constants.INT_NULL &&
					    (eInvertedInitialValue <= 0)) {//eInvertedInitialValue == Constants.INT_NULL is redundant
						throw new IllegalArgumentException(
							"Edge " + e + " is contingent with a negative value but the inverted " + eInverted +
							" does not contain a lower case value neither a proper initial value. ");
					}

					if (lowerCaseValue == Constants.INT_NULL) {
						lowerCaseValue = -initialValue;
						eInverted.setLowerCaseValue(conjunctedLabel, sourceALabel, lowerCaseValue);
						if (contingentAlsoAsOrdinary) {
							e.mergeLabeledValue(conjunctedLabel, initialValue);
						}
						/*
						 * History for lower bound.
						 * 2017-10-11 initialValue = minLabeledValue.getIntValue() is not necessary for the check, but only for AllMax. AllMax building
						 * method cares of it.
						 * 2017-12-22 If activation t.p. is Z, then removing initial value the contingent t.p. has not a right lower bound w.r.t. Z!
						 * 2018-02-21 initialValue = minLabeledValue.getIntValue() allows the reduction of # propagations.
						 */
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								LOG.log(Level.FINER,
								        "Inserted the lower label value: " +
								        lowerCaseValueAsString(sourceALabel, lowerCaseValue, conjunctedLabel) +
								        " to edge " + eInverted);
							}
						}
						upperCaseValue = -eInvertedInitialValue;
						e.mergeUpperCaseValue(conjunctedLabel, sourceALabel, upperCaseValue);
						if (contingentAlsoAsOrdinary) {
							eInverted.mergeLabeledValue(conjunctedLabel, eInvertedInitialValue);
						}
						/*
						 * History for upper bound.
						 * 2017-10-11 such value is not necessary for the check, but only for AllMax. AllMax building method cares of it.
						 * 2017-12-22 If activation t.p. is Z, then removing initial value the contingent t.p. has not a right upper bound w.r.t. Z!
						 * 2018-02-21 Upper bound are not necessary for the completeness, we ignore it.
						 */
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								LOG.log(Level.FINER,
								        "Inserted the upper label value: " +
								        upperCaseValueAsString(sourceALabel, upperCaseValue, conjunctedLabel) +
								        " to edge " + e);
							}
						}
					}
					// In order to speed up the checking, prepare some auxiliary data structure
//					if (s.getName().length() != 1 || !Literal.check(s.getName().charAt(0))) {
//						final String msg =
//							"In this beta version of the program, a contingent node name must be sigle character in the range " +
//							Literal.PROPOSITION_RANGE +
//							" Current name is " + s.getName();
//						if (LOG.isLoggable(Level.SEVERE)) {
//							LOG.log(Level.SEVERE, msg);
//						}
//						throw new WellDefinitionException(msg);
//					}

					s.setALabel(sourceALabel);// s is the contingent node.
					assert d != null;
					STNU.CHECK_ACTIVATION_UNIQUENESS(d, s, activationNode);
					activationNode.put(s, d);
					lowerContingentEdge.put(s, eInverted);
				} else {
					// e : A--->C
					// eInverted : C--->A
					assert d != null;
					// In order to speed up the checking, prepare some auxiliary data structure
//					if (d.getName().length() != 1 || !Literal.check(d.getName().charAt(0))) {
//						final String msg =
//							"In this beta version of the program, a contingent node name must be sigle character in the range " +
//							Literal.PROPOSITION_RANGE +
//							" Current name is " + d.getName();
//						if (LOG.isLoggable(Level.SEVERE)) {
//							LOG.log(Level.SEVERE, msg);
//						}
//						throw new WellDefinitionException(msg);
//					}

					final ALabel destALabel = new ALabel(d.getName(), g.getALabelAlphabet());
					if (!destALabel.equals(d.getALabel())) {
						d.setALabel(destALabel);// to speed up DC checking!
					}
					upperCaseValue = eInverted.getUpperCaseValue(conjunctedLabel, destALabel);
					if (upperCaseValue != Constants.INT_NULL && -initialValue != upperCaseValue) {
						throw new IllegalArgumentException(
							"Edge " + e + " is contingent with a positive value and the inverted " + eInverted +
							" already contains a ***different*** upper case value: " +
							upperCaseValueAsString(destALabel, upperCaseValue, conjunctedLabel) + ".");
					}
					if (upperCaseValue == Constants.INT_NULL &&
					    (eInvertedInitialValue == Constants.INT_NULL || eInvertedInitialValue >= 0)) {
						throw new IllegalArgumentException(
							"Edge " + e + " is contingent with a positive value but the inverted " + eInverted +
							" does not contain a upper case value neither a proper initial value. ");
					}
					if (upperCaseValue == Constants.INT_NULL) {
						upperCaseValue = -initialValue;
						eInverted.mergeUpperCaseValue(conjunctedLabel, destALabel, upperCaseValue);
						if (contingentAlsoAsOrdinary) {
							e.mergeLabeledValue(conjunctedLabel, initialValue);
						}
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								LOG.log(Level.FINER,
								        "Inserted the upper label value: " +
								        upperCaseValueAsString(destALabel, upperCaseValue, conjunctedLabel) +
								        " to edge " +
								        eInverted);
							}
						}

						/*
						 * @see comment "History for upper bound." above.
						 */
						lowerCaseValue = -eInvertedInitialValue;
						e.setLowerCaseValue(conjunctedLabel, destALabel, lowerCaseValue);
						if (contingentAlsoAsOrdinary) {
							eInverted.mergeLabeledValue(conjunctedLabel, eInvertedInitialValue);
						}
						// In order to speed up the checking, prepare some auxiliary data structure
						assert s != null;
						STNU.CHECK_ACTIVATION_UNIQUENESS(s, d, activationNode);
						activationNode.put(d, s);
						lowerContingentEdge.put(d, e);
						/*
						 * @see comment "History for lower bound." above.
						 */
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								LOG.log(Level.FINER,
								        "Inserted the lower label value: " +
								        lowerCaseValueAsString(destALabel, lowerCaseValue, conjunctedLabel) +
								        " to edge " +
								        e);
							}
						}
					}
				}
			} else {
				// here initialValue is indefinite... UC and LC values are already present.
				if (!e.getLowerCaseValue().isEmpty()) {
					assert s != null;
					assert d != null;
					STNU.CHECK_ACTIVATION_UNIQUENESS(s, d, activationNode);
					activationNode.put(d, s);
					lowerContingentEdge.put(d, e);
					if (contingentAlsoAsOrdinary) {
						eInverted.mergeLabeledValue(conjunctedLabel, -e.getLowerCaseValue().getValue());
					}
				}
				if (e.upperCaseValueSize() > 0) {
					assert s != null;
					final ALabel sourceALabel = new ALabel(s.getName(), g.getALabelAlphabet());
					if (!sourceALabel.equals(s.getALabel())) {
						s.setALabel(sourceALabel);// to speed up DC checking!
					}
					if (contingentAlsoAsOrdinary) {
						e.mergeLabeledValue(conjunctedLabel,
						                    -eInverted.getUpperCaseValue(Label.emptyLabel, sourceALabel));
					}
				}
				if (!eInverted.getLowerCaseValue().isEmpty()) {
					assert d != null;
					assert s != null;
					STNU.CHECK_ACTIVATION_UNIQUENESS(d, s, activationNode);
					activationNode.put(s, d);
					lowerContingentEdge.put(s, eInverted);
					if (contingentAlsoAsOrdinary) {
						eInverted.mergeLabeledValue(conjunctedLabel, -e.getLowerCaseValue().getValue());
					}
				}
				if (eInverted.upperCaseValueSize() > 0) {
					assert d != null;
					final ALabel destALabel = new ALabel(d.getName(), g.getALabelAlphabet());
					if (!destALabel.equals(d.getALabel())) {
						d.setALabel(destALabel);// to speed up DC checking!
					}
					if (contingentAlsoAsOrdinary) {
						e.mergeLabeledValue(conjunctedLabel,
						                    -eInverted.getUpperCaseValue(Label.emptyLabel, destALabel));
					}
				}
			}
			if (!contingentAlsoAsOrdinary) {
				e.removeLabeledValue(conjunctedLabel);
				eInverted.removeLabeledValue(conjunctedLabel);
			}


			// it is necessary to check max value
			int m = e.getMinUpperCaseValue().getValue().getIntValue();
			// LOG.warning("m value: " + m);
			if (m != Constants.INT_NULL && m < maxWeightContingent) {
				maxWeightContingent = m;
			}
			m = eInverted.getMinUpperCaseValue().getValue().getIntValue();
			if (m != Constants.INT_NULL && m < maxWeightContingent) {
				maxWeightContingent = m;
			}
		} // end contingent edges cycle

		maxWeightContingent = -maxWeightContingent;
		// LOG.warning("maxWeightContingent value: " + maxWeightContingent);
		// LOG.warning("this.maxWeight value: " + this.maxWeight);
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("maxWeightContingent value found: " + maxWeightContingent + ". MaxWeight not contingent: " +
				          maxWeight);
			}
		}
		if (maxWeightContingent > maxWeight) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.WARNING)) {
					LOG.warning(
						maxWeightContingent + " is the greatest unsigned value found in contingent " + "while " +
						maxWeight +
						" is the greater unsigned value found in normal constraint.");
				}
			}
			// it is necessary to recalculate horizon
			maxWeight = maxWeightContingent;
			// Determine horizon value
			product = ((long) maxWeight) * (g.getVertexCount() - 1);// Z doesn't count!
			if (product >= Constants.INT_POS_INFINITE) {
				throw new ArithmeticException("Horizon value is not representable by an integer.");
			}
			horizon = (int) product;
		}
		//addUpperBounds();

		// init CSTNU structures.
		g.getLowerLabeledEdges();


		//to manage oracles
		final CharSortedSet verifiedContingentName = new CharAVLTreeSet();
		for (final LabeledNode ctg : activationNode.keySet()) {
			verifiedContingentName.add(ctg.getName().charAt(0));
		}

		/*
		 * Checks well definiteness of nodes and oracles
		 */
		final Collection<LabeledNode> nodeSet = g.getVertices();
		for (final LabeledNode node : nodeSet) {
			node.clearPotential();
			if (node == Z) {
				continue;
			}
			// LOWER BOUND FROM Z
			OSTNUEdgePluggable edge = g.findEdge(node, Z);
			if (edge == null) {
				edge = makeNewEdge(node.getName() + "-" + Z.getName(), Edge.ConstraintType.internal);
				g.addEdge(edge, node, Z);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.WARNING)) {
						LOG.log(Level.WARNING,
						        "It is necessary to add a constraint to guarantee that '" + node.getName() +
						        "' occurs after '" + AbstractCSTN.ZERO_NODE_NAME +
						        "'.");
					}
				}
			}
			final Label nodeLabel;
			nodeLabel = Label.emptyLabel;
			node.setLabel(nodeLabel);

			boolean added = edge.mergeLabeledValue(nodeLabel, 0);// in any case, all nodes must be after Z!
			if (Debug.ON) {
				if (added) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.log(Level.FINER,
						        "Added " + edge.getName() + ": " + node.getName() + "--" + pairAsString(nodeLabel, 0) +
						        "-->" + Z.getName());
					}
				}
			}


			final char proposition = node.getPropositionObserved();
			if (proposition != Constants.UNKNOWN) {
				//it is an oracle
				if (!verifiedContingentName.contains(proposition)) {
					final String msg =
						"The oracle " + node + " is related to contingent node " + proposition + " that does not exit.";
					if (LOG.isLoggable(Level.SEVERE)) {
						LOG.log(Level.SEVERE, msg);
					}
					throw new WellDefinitionException(msg);
				}
				final LabeledNode ctg = this.g.getNode(String.valueOf(proposition));
				if (ctg == null) {
					throw new WellDefinitionException(
						"Oracle " + node + " is associated to contingent node with name " + proposition +
						", but there is no contingent node with this name.");
				}
				this.oracleNode.put(ctg, node);

				edge = g.findEdge(ctg, node);
				if (edge == null) {
					edge = makeNewEdge(ctg.getName() + "-" + node.getName(), Edge.ConstraintType.internal);
					g.addEdge(edge, ctg, node);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.WARNING)) {
							LOG.log(Level.WARNING,
							        "It is necessary to add a constraint to guarantee that oracle '" + node.getName() +
							        "' occurs before contingent '" +
							        ctg.getName() + "'.");
						}
					}
				}
				node.setLabel(ctg.getLabel());

				added = edge.mergeLabeledValue(ctg.getLabel(), 0);// contingent node must be after its oracle.
				if (Debug.ON) {
					if (added) {
						if (LOG.isLoggable(Level.FINER)) {
							LOG.log(Level.FINER,
							        "Added " + edge.getName() + ": " + ctg.getName() + "--" +
							        pairAsString(ctg.getLabel(), 0) + "-->" + node.getName());
						}
					}
				}
			}
		}
		checkStatus.initialized = true;

		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO, "Checking graph as CSTNU well-defined instance finished!\n");
			}
		}
	}

	/**
	 * Executes one step of the agile controllability check.<br> Before the first execution of this method, it is
	 * necessary to execute {@link #initAndCheck()}.
	 *
	 * @param edgesToCheck   set of edges that have to be checked.
	 * @param timeoutInstant time instant limit allowed to the computation.
	 *
	 * @return the update status (for convenience. It is not necessary because return the same parameter status).
	 */
	public OSTNUCheckStatus oneStepAgileControllability(final EdgesToCheck<OSTNUEdgePluggable> edgesToCheck,
	                                                    Instant timeoutInstant) {

		LabeledNode A, B, C;
		OSTNUEdgePluggable AC, CB, edgeCopy;

		checkStatus.cycles++;
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "Start application labeled propagation rules.");
			}
		}

		final EdgesToCheck<OSTNUEdgePluggable> newEdgesToCheck = new EdgesToCheck<>();
		int i = 1;
		// int maxNumberOfValueInAnEdge = 0, maxNumberOfUpperCaseValuesInAnEdge = 0;
		// OSTNUEdgePluggable fatEdgeInLabeledValues = null, fatEdgeInUpperCaseValues = null;// for sure they will be initialized!
		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO, "Number of edges to analyze: " + edgesToCheck.size());
			}
		}
		final LabeledNode Z = g.getZ();
		for (final OSTNUEdgePluggable AB : edgesToCheck) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Considering edge " + (i++) + "/" + edgesToCheck.size() + ": " + AB + "\n");
				}
			}
			A = g.getSource(AB);
			B = g.getDest(AB);
			assert A != null;
			assert B != null;
			// initAndCheck does not resolve completely a possible qStar. So, it is necessary to check here the edge before to consider the second edge.
			// The check has to be done in case B==Z, and it consists in applying R0, R3 and zLabeledLetterRemovalRule!
			edgeCopy = g.getEdgeFactory().get(AB);

			labeledLetterRemovalRule(A, B, AB);

			if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
				return checkStatus;
			}

			if (!AB.hasSameValues(edgeCopy)) {
				newEdgesToCheck.add(AB, A, B, Z, g, propagationOnlyToZ);
			}

			if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
				return checkStatus;
			}

			/*
			 * Step 1/2: Make all propagation considering edge AB as first edge.<br>
			 * A-->B-->C
			 */
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Rules, phase 1/2: edge " + AB.getName() + " as first component.");
				}
			}
			for (final OSTNUEdgePluggable BC : g.getOutEdges(B)) {
				C = g.getDest(BC);
				assert C != null;

				AC = g.findEdge(A, C);
				// I need to preserve the old edge to compare below
				if (AC != null) {
					edgeCopy = g.getEdgeFactory().get(AC);
				} else {
					AC = makeNewEdge(A.getName() + "-" + C.getName(), derived);
					edgeCopy = null;
				}

				labeledLetterRemovalRule(B, C, BC);
				labeledPropagationRule(A, B, C, AB, BC, AC);

				if (!BC.getUpperCaseValueMap().isEmpty()) {
					labeledUpperCaseRule(A, B, C, AB, BC, AC);
				}
				labeledOracleRule(A, B, C, AB, BC, AC);

				//it is necessary to manage also the case when oracle rule is necessary when the edge X---v--≥Ctg
				//is modified. Oracle rule required the pattern Act===≥Ctg----≥X
				//Using the A,B,C names, A=X ,B=Ctg ,C=Act
				if (B.isContingent() && this.activationNode.get(B) == C) {
					final OSTNUEdgePluggable BA = this.g.findEdge(B, A);
					CB = this.g.findEdge(C, B);
					final OSTNUEdgePluggable CA = this.g.findEdge(C, A);
					if (BA != null && CB != null && CA != null) {
						labeledOracleRule(C, B, A, CB, BA, CA);
					}
				}
				if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
					return checkStatus;
				}

				/*
				 * The following rule are called if there are condition (avoid to call for nothing)
				 */
				if (!AB.getLowerCaseValue().isEmpty()) {
					labeledLowerCaseRule(A, B, C, AB, BC, AC);
					labeledCrossCaseRule(A, B, C, AB, BC, AC);
				}

				boolean add = false;
				if (edgeCopy == null && !AC.isEmpty()) {
					// the new CB has to be added to the graph!
					g.addEdge(AC, A, C);
					add = true;
				} else if (edgeCopy != null && !edgeCopy.hasSameValues(AC)) {
					// CB was already present and it has been changed!
					add = true;
				}
				if (add) {
					newEdgesToCheck.add(AC, A, C, Z, g, false);
				}

				if (!checkStatus.consistency) {
					checkStatus.finished = true;
					return checkStatus;
				}

			}

			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Rules, phase 1/2 done.");
				}
			}

			if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
				return checkStatus;
			}

			/*
			 * Step 2/2: Make all propagation considering edge AB as second edge.<br>
			 * C-->A-->B
			 */
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Rules, phase 2/2: edge " + AB.getName() + " as second component.");
				}
			}
			for (final OSTNUEdgePluggable CA : g.getInEdges(A)) {
				C = g.getSource(CA);
				assert C != null;

				CB = g.findEdge(C, B);
				// I need to preserve the old edge to compare below
				if (CB != null) {
					edgeCopy = g.getEdgeFactory().get(CB);
				} else {
					CB = makeNewEdge(C.getName() + "-" + B.getName(), derived);
					edgeCopy = null;
				}

				labeledLetterRemovalRule(C, A, CA);
				labeledPropagationRule(C, A, B, CA, AB, CB);

				if (!AB.getUpperCaseValueMap().isEmpty()) {
					labeledUpperCaseRule(C, A, B, CA, AB, CB);
				}

				if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
					return checkStatus;
				}

				if (!CA.getLowerCaseValue().isEmpty()) {
					labeledLowerCaseRule(C, A, B, CA, AB, CB);
					labeledCrossCaseRule(C, A, B, CA, AB, CB);
				}
				labeledOracleRule(C, A, B, CA, AB, CB);

				boolean add = false;
				if (edgeCopy == null && !CB.isEmpty()) {
					// the new CB has to be added to the graph!
					g.addEdge(CB, C, B);
					add = true;
				} else if (edgeCopy != null && !edgeCopy.hasSameValues(CB)) {
					// CB was already present and it has been changed!
					add = true;
				}
				if (add) {
					newEdgesToCheck.add(CB, C, B, Z, g, false);
				}

				if (!checkStatus.consistency) {
					checkStatus.finished = true;
					return checkStatus;
				}
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Rules phase 2/2 done.\n");
				}
			}

			if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
				return checkStatus;
			}

		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "End application all rules.");
			}
		}
		edgesToCheck.clear();// in any case, this set has been elaborated. It is better to clear it out.
		checkStatus.finished = newEdgesToCheck.size() == 0;
		if (!checkStatus.finished) {
			edgesToCheck.takeIn(newEdgesToCheck);
		}
		return checkStatus;
	}

	/**
	 * Resets all internal structures
	 */
	@Override
	public void reset() {
		super.reset();
		if (activationNode == null) {
			activationNode = new Object2ObjectOpenHashMap<>();
			lowerContingentEdge = new Object2ObjectOpenHashMap<>();
			oracleNode = new Object2ObjectOpenHashMap<>();
			return;
		}
		activationNode.clear();
		lowerContingentEdge.clear();
		oracleNode.clear();
	}

	/**
	 * Checks if a new labeled value is negative and represents a negative cycle. In such a case, update the status
	 * adding the new scenario in the list of negative scenarios.
	 *
	 * @param value       value
	 * @param label       label of the value
	 * @param source      source node
	 * @param destination destination node
	 * @param newEdge     new edge
	 *
	 * @return true if a negative loop has been found in the given scenario and that there is no alternative scenario to
	 * 	it (i.e., there are two scenario that present negative loop w.r.t. the value generated by a contingent time
	 * 	point, one using oracle, one not using oracle).
	 */
	boolean checkAndManageIfNewLabeledValueIsANegativeLoop(final int value, @Nonnull final Label label,
	                                                       @Nonnull final LabeledNode source,
	                                                       @Nonnull final LabeledNode destination,
	                                                       @Nonnull final OSTNUEdgePluggable newEdge) {
		if (source == destination && value < 0) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.log(Level.INFO, "Check a negative loop in the edge " + newEdge + " and scenario " + label);
				}
			}
			//if label is empty, then for sure there is a negative cycle
			if (label.isEmpty()) {
				this.checkStatus.consistency = false;
				this.checkStatus.finished = true;
				this.checkStatus.negativeLoopNode = source;
				this.checkStatus.addNegativeScenario(label);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.INFO)) {
						LOG.log(Level.INFO, "It makes the network inconsistent; Stop.");
					}
				}
				return true;
			}
			this.checkStatus.addNegativeScenario(label);
			if (checkStatus.negativeScenarios.get(Label.emptyLabel) == 0) {
				this.checkStatus.consistency = false;
				this.checkStatus.finished = true;
				this.checkStatus.negativeLoopNode = source;
				if (Debug.ON) {
					if (LOG.isLoggable(Level.INFO)) {
						LOG.log(Level.INFO, "It makes the network inconsistent; Stop.");
					}
				}
				return true;
			}
		}
		return false;
	}

	/**
	 * @param contingent contingent node
	 * @param node       other node for which it is necessary to determine the label for constraint involving contingent
	 *                   and node.
	 * @param straight   true if the label is relative to value determined using oracle rule, false otherwise.
	 *
	 * @return the label used by lower case/upper case/oracle rules to label values involving contingent node.
	 */
	@SuppressFBWarnings("NP_NONNULL_RETURN_VIOLATION")//false positive
	@Nonnull
	Label getLabel4ValueInvolvingContingent(@Nonnull LabeledNode contingent, @Nonnull LabeledNode node, boolean straight) {
		final LabeledNode oracle = this.oracleNode.get(contingent);
		if (oracle != null && node != oracle && node != this.g.getZ() && node != this.activationNode.get(contingent)) {
			final char state = (straight) ? Literal.STRAIGHT : Literal.NEGATED;
			Object2CharMap<LabeledNode> firstMap = this.checkStatus.propositionOfPair.get(contingent);
			char proposition;
			if (firstMap == null) {
				firstMap = new Object2CharLinkedOpenHashMap<>();
				firstMap.defaultReturnValue(Constants.UNKNOWN);
				this.checkStatus.propositionOfPair.put(contingent, firstMap);
				proposition = Constants.UNKNOWN;
			} else {
				proposition = firstMap.getChar(node);
			}
			if (proposition == Constants.UNKNOWN) {
				if (!((('a' <= this.checkStatus.firstProposition) && (this.checkStatus.firstProposition <= 'z')) ||
				      (('A' <= this.checkStatus.firstProposition) && (this.checkStatus.firstProposition <= 'F')))) {
					throw new IllegalStateException("Too much propositions. The program cannot check this network.");
				}
				proposition = this.checkStatus.firstProposition++;
				firstMap.put(node, proposition);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer(
							"Pair (" + contingent + ", " + node + ") is associated to proposition " + proposition);
					}
				}
			}
			return Label.valueOf(proposition, state);
		}
		return Label.emptyLabel;
	}

	/**
	 * Determines the minimal distance between all pairs of vertexes of the given graph if the graph is consistent,
	 * i.e., it does not contain any negative cycles.
	 *
	 * @param graph the graph. It will be modified
	 *
	 * @return true if the input graph is consistent, i.e., it does not contain any negative cycle; false otherwise.
	 */
	boolean isAllMaxMinimalGraphConsistent(@Nonnull final TNGraph<OSTNUEdgePluggable> graph) {
		final LabeledIntMapSupplier<? extends LabeledIntMap> labeledIntMapSupplier =
			new LabeledIntMapSupplier<>(LabeledIntMapSupplier.SIMPLE_LABELEDINTMAP_CLASS);
		final int n = graph.getVertexCount();
		final LabeledNode[] node = graph.getVerticesArray();
		LabeledNode iV, jV, kV;
		OSTNUEdgePluggable ikE, kjE, ijE;
		int v;
		Label label;
		LabeledIntMap ijMap;
		boolean negativeLoop;
		for (int k = 0; k < n; k++) {
			kV = node[k];
			for (int i = 0; i < n; i++) {
				iV = node[i];
				for (int j = 0; j < n; j++) {
					if ((k == i) && (i == j)) {
						continue;
					}
					jV = node[j];
					final Label nodeLabelConjunction = iV.getLabel().conjunction(jV.getLabel());
					if (nodeLabelConjunction == null) {
						continue;
					}
					ikE = graph.findEdge(iV, kV);
					kjE = graph.findEdge(kV, jV);
					if ((ikE == null) || (kjE == null)) {
						continue;
					}
					ijE = graph.findEdge(iV, jV);

					final Set<Object2IntMap.Entry<Label>> ikMap = ikE.getLabeledValueSet();
					final Set<Object2IntMap.Entry<Label>> kjMap = kjE.getLabeledValueSet();
					if ((k == i) || (k == j)) {
						// this is necessary to avoid concurrent access to the same map by the iterator.
						assert ijE != null;
						ijMap = labeledIntMapSupplier.get(ijE.getLabeledValueMap());
					} else {
						ijMap = null;
					}
					for (final Object2IntMap.Entry<Label> ikL : ikMap) {
						for (final Object2IntMap.Entry<Label> kjL : kjMap) {
							label = ikL.getKey().conjunction(kjL.getKey());
							if (label == null) {
								continue;
							}
							label = label.conjunction(
								nodeLabelConjunction);// It is necessary to propagate with node labels!
							if (label == null) {
								continue;
							}
							if (ijE == null) {
								ijE = graph.makeNewEdge(node[i].getName() + "-" + node[j].getName(), derived);
								graph.addEdge(ijE, iV, jV);
							}
							v = Constants.sumWithOverflowCheck(ikL.getIntValue(), kjL.getIntValue());
							if (ijMap != null) {
								ijMap.put(label, v);
								negativeLoop =
									this.checkAndManageIfNewLabeledValueIsANegativeLoop(v, label, iV, jV, ijE);
							} else {
								ijE.mergeLabeledValue(label, v);
								final Entry<Label> entry = ijE.getMinLabeledValue();
								negativeLoop = this.checkAndManageIfNewLabeledValueIsANegativeLoop(entry.getIntValue(),
								                                                                   entry.getKey(), iV,
								                                                                   jV, ijE);
							}
							if (negativeLoop) {
								ijE.setLabeledValueMap(ijMap);
								if (Debug.ON) {
									if (LOG.isLoggable(Level.FINE)) {
										LOG.fine(
											"Found a negative loop in All-Max network: " + ikE + "--" + kjE + ": " +
											ijE);
									}
								}
								return false;
							}
						}
					}
					if (ijMap != null) {
						ijE.setLabeledValueMap(ijMap);
					}
				}
			}
		}
		return true;
	}

	/**
	 * <b>Labeled Cross Case Rule</b>
	 *
	 * <pre>
	 * A ---(u,c,α)--→ C ---(v,D,β)--→ X
	 * adds
	 * A ---(u+v,D,αβ)--→ X
	 *
	 * if αβ∈P, C != D, and v ≤ 0.
	 * </pre>
	 * Since it is assumed that L(C)=L(A)=α, there is only ONE lower-case labeled value u,c,α!
	 *
	 * @param nA  node
	 * @param nC  node
	 * @param nX  node
	 * @param eAC CANNOT BE NULL
	 * @param eCX CANNOT BE NULL
	 * @param eAX CANNOT BE NULL
	 */
	void labeledCrossCaseRule(@Nonnull final LabeledNode nA, @Nonnull final LabeledNode nC,
	                          @Nonnull final LabeledNode nX,
	                          @Nonnull final OSTNUEdgePluggable eAC, @Nonnull final OSTNUEdgePluggable eCX,
	                          @Nonnull final OSTNUEdgePluggable eAX) {

		final LabeledLowerCaseValue lowerCaseValue = eAC.getLowerCaseValue();
		if (lowerCaseValue.isEmpty()) {
			return;
		}
		// Since it is assumed that L(C)=L(A)=α, there is only ONE lower-case labeled value u,c,α!
		final ALabel cALabel = lowerCaseValue.getNodeName();
		final Label alpha = lowerCaseValue.getLabel();
		final int u = lowerCaseValue.getValue();

		final LabeledALabelIntTreeMap CXValueMap = eCX.getUpperCaseValueMap();
		if (CXValueMap.isEmpty()) {
			return;
		}
		for (final ALabel aleph : CXValueMap.keySet()) {
			if (aleph.isEmpty()) {
				//lower case rule must be considered.
				continue;
			}
			final LabeledIntMap valuesMap = CXValueMap.get(aleph);
			if (valuesMap == null) {
				continue;
			}
			// Rule condition: upper case label cannot be equal or contain c name
			if (aleph.contains(cALabel)) {
				continue;// rule condition
			}
			for (final Entry<Label> entryCX : valuesMap.entrySet()) {// entrySet read-only
				final int v = entryCX.getIntValue();
				if (v > 0) {
					// this rule is not applicable because we are considering instantaneous reaction: ||
					// (v == 0 && nX == nC))
					continue; // Rule condition!
				}
				final Label beta = entryCX.getKey();
				if (beta == null) {
					continue;
				}
				if (checkStatus.isInNegativeScenarios(beta)) {
					removeLabeledValueBecauseInNegativeScenario(beta, null, eCX);
					continue;
				}
				final Label alphaBeta = beta.conjunction(alpha);
				if (alphaBeta == null || checkStatus.isInNegativeScenarios(alphaBeta)) {
					continue;
				}
				final int sum = Constants.sumWithOverflowCheck(v, u);

				if (sum > 0) {
					// && aleph.isEmpty()) // (sum > 0) works well for big DC instances! (sum > 0 && aleph.isEmpty()) works well for not-DC ones!
					continue;
				}

				final int oldValue = eAX.getUpperCaseValue(alphaBeta, aleph);

				if (oldValue != Constants.INT_NULL && oldValue <= sum) {
					continue;
				}
				String logMsg = null;
				if (Debug.ON) {
					final String oldAX = eAX.toString();
					logMsg =
						"Cross case rule applied to edge " + oldAX + ":\nDetail: " + nX.getName() + " <---" +
						upperCaseValueAsString(aleph, v, beta) + "--- " +
						nC.getName() + " <---" + lowerCaseValueAsString(cALabel, u, alpha) + "--- " + nA.getName() +
						"\nresult: " + nX.getName() + " <---" +
						upperCaseValueAsString(aleph, sum, alphaBeta) + "--- " + nA.getName() + "; oldValue: " +
						Constants.formatInt(oldValue);
				}

				final boolean localApp = eAX.mergeUpperCaseValue(alphaBeta, aleph, sum);

				if (localApp) {
					checkStatus.crossCaseRuleCalls++;
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							LOG.log(Level.FINER, logMsg);
						}
					}
				}

				final boolean isNegativeLoop =
					checkAndManageIfNewLabeledValueIsANegativeLoop(sum, alphaBeta, nA, nX, eAX);
				if (isNegativeLoop) {
					if (Debug.ON) {
						LOG.info("Found a negative loop.");
					}
					return;
				}
			}
		}
	}

	/**
	 * <b>Labeled Letter Removal</b>
	 *
	 * <pre>
	 * X ---(v,ℵ,β)--→ A ---(x,c,α)--→ C
	 * adds
	 * X ---(m,ℵ',β)---→ A
	 *
	 * if C ∈ ℵ, m = max(v, −x), β entails α.
	 * ℵ'=ℵ'/C
	 * </pre>
	 *
	 * @param nX  node
	 * @param nA  node
	 * @param eXA edge
	 */
	void labeledLetterRemovalRule(@Nonnull final LabeledNode nX, @Nonnull final LabeledNode nA,
	                              @Nonnull final OSTNUEdgePluggable eXA) {

		if (!activationNode.containsValue(nA) || eXA.getUpperCaseValueMap().isEmpty()) {
			return;
		}
		for (final OSTNUEdgePluggable eAC : g.getOutEdges(nA)) {
			if (eAC.getLowerCaseValue().isEmpty()) {
				continue;
			}
			// found the lower-case contingent constraint A ---(x,c,α)--→ C
			final LabeledNode nC = g.getDest(eAC);
			assert nC != null;
			if (nX == nC) {
				continue;
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.log(Level.FINEST, "Labeled removal rule has found the contingent link " + eAC);
				}
			}
			final LabeledLowerCaseValue ACLowerCaseValueObj = eAC.getLowerCaseValue();
			final Label alpha = ACLowerCaseValueObj.getLabel();
			final int x = ACLowerCaseValueObj.getValue();
			for (final ALabel aleph : eXA.getUpperCaseValueMap().keySet()) {
				if (!aleph.contains(nC.getALabel())) {
					continue;
				}
				final LabeledIntMap eXAValueMap = eXA.getUpperCaseValueMap().get(aleph);
				if (eXAValueMap == null) {
					continue;
				}
				for (final Label beta : eXAValueMap.keySet()) {
					if (checkStatus.isInNegativeScenarios(beta)) {
						removeLabeledValueBecauseInNegativeScenario(beta, aleph, eXA);
						continue;
					}
					if (!beta.subsumes(alpha)) {
						continue;
					}
					final int v = eXA.getUpperCaseValue(beta, aleph);
					if (v == Constants.INT_NULL) {
						continue;
					}
					final int newV = Math.max(v, -x);
					final int oldZ = (Debug.ON) ? eXA.getUpperCaseValue(beta, aleph) : -1;
					final String oldXA = (Debug.ON) ? eXA.toString() : "";

					final ALabel aleph1 = ALabel.clone(aleph);
					aleph1.remove(nC.getALabel());

					final boolean mergeStatus = (aleph1.isEmpty()) ? eXA.mergeLabeledValue(beta, newV)
					                                               : eXA.mergeUpperCaseValue(beta, aleph1, newV);
					if (mergeStatus) {
						checkStatus.letterRemovalRuleCalls++;
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								LOG.log(Level.FINER,
								        "Labeled removal rule applied to edge " + oldXA + ":\n" + "Detail: " + nC +
								        " <---" +
								        lowerCaseValueAsString(ACLowerCaseValueObj.getNodeName(), x, alpha) + "--- " +
								        nA.getName() + " <---" +
								        upperCaseValueAsString(aleph, v, beta) + "--- " + nX.getName() + "\nresult: " +
								        nA.getName() + " <---" +
								        upperCaseValueAsString(aleph1, newV, beta) + "--- " + nX.getName() +
								        "; oldValue: " +
								        Constants.formatInt(oldZ));
							}
						}
					}
				}
			}
		}
	}

	/**
	 * <b>Labeled Lower Case Rule</b>
	 * <pre>
	 * X ←--(-u,◇,β)--- C ←--(x,c,α)----- A
	 *   ---(v,◇,β')---→   --(-y,◇,-----→
	 * adds
	 * X ←-----(x-u,◇,¬cαββ')------------ A
	 *
	 * if ¬cαββ'∈P, -u≤0, and (Oracle O_C does not exist or v-u≥y-x).
	 * </pre>
	 * <p>
	 * Since it is assumed that L(C)=L(A), there is only ONE lower-case labeled value u,c,α!
	 *
	 * @param nA  node
	 * @param nC  node
	 * @param nX  node
	 * @param eAC CANNOT BE NULL
	 * @param eCX CANNOT BE NULL
	 * @param eAX CANNOT BE NULL
	 */
	void labeledLowerCaseRule(@Nonnull final LabeledNode nA, @Nonnull final LabeledNode nC,
	                          @Nonnull final LabeledNode nX,
	                          @Nonnull final OSTNUEdgePluggable eAC, @Nonnull final OSTNUEdgePluggable eCX,
	                          @Nonnull final OSTNUEdgePluggable eAX) {

		if (!nC.isContingent() || activationNode.get(nC) != nA) {
			return;
		}

		final LabeledLowerCaseValue lowerCaseValueEntry = eAC.getLowerCaseValue();
		if (lowerCaseValueEntry.isEmpty()) {
			return;
		}

		final LabeledIntMap CXValueMap = eCX.getLabeledValueMap();
		if (CXValueMap.isEmpty()) {
			return;
		}
		// Since it is assumed that L(C)=L(A)=α, there is only ONE lower-case labeled value u,c,α!
		final ALabel ctgALabel = lowerCaseValueEntry.getNodeName();
		final Label alpha = lowerCaseValueEntry.getLabel();

		final int x = lowerCaseValueEntry.getValue();
		final OSTNUEdgePluggable upperCaseEdge = this.g.findEdge(nC, nA);
		assert upperCaseEdge != null;
		final int y = -upperCaseEdge.getUpperCaseValue(alpha, ctgALabel);
		if (y == Constants.INT_NULL) {
			throw new IllegalStateException("Edge " + upperCaseEdge + " is not an upper case edge");
		}
		final int contingentSpan = y - x;
		final OSTNUEdgePluggable eXC = this.g.findEdge(nX, nC);
		final Set<Entry<Label>> XCLabeledValueEntrySet =
			(eXC == null) ? ObjectSets.emptySet() : eXC.getLabeledValueSet();

		final boolean oraclePresent = oracleNode.get(nC) != null;

		Label alphaBeta;
		for (final Object2IntMap.Entry<Label> entry : CXValueMap.entrySet()) {
			final int u = entry.getIntValue();
			if (u > 0) {
				//rule condition
				continue;
			}
			final Label beta = entry.getKey();
			if (checkStatus.isInNegativeScenarios(beta)) {
				removeLabeledValueBecauseInNegativeScenario(beta, null, eCX);
				continue;
			}


			final int sum = Constants.sumWithOverflowCheck(x, u);

			if (!oraclePresent) {
				//nLC has to be applied without labeling the values.
				alphaBeta = alpha.conjunction(beta);
				if (alphaBeta == null || checkStatus.isInNegativeScenarios(alphaBeta)) {
					continue;
				}
				boolean applied =
					labeledLowerCaseRuleHelper(x, u, sum, beta, alpha, alphaBeta, nA, nC, nX, eAX, ctgALabel);
				if (applied) {
					applied = checkAndManageIfNewLabeledValueIsANegativeLoop(sum, alphaBeta, nA, nX, eAX);
					if (applied) {
						if (Debug.ON) {
							LOG.info("Found a negative loop.");
						}
					}
					return;
				}
				continue;
			}
			//oracle exists.
			//nLC can be applied only if there is no limited range for the distance between C and X.

			if (XCLabeledValueEntrySet.isEmpty() || nX == nA) {
				//nX == nA ->lower case rule must be applied on the contingent link itself to verify the bounds have not changed.
				//I invoke getLabel4ValueInvolvingContingent here because only here it is certain that it is necessary
				alphaBeta = Objects.requireNonNull(getLabel4ValueInvolvingContingent(nC, nX, false).conjunction(alpha))
					.conjunction(beta);
				if (alphaBeta == null || checkStatus.isInNegativeScenarios(alphaBeta)) {
					continue;
				}
				boolean applied =
					labeledLowerCaseRuleHelper(x, u, sum, beta, alpha, alphaBeta, nA, nC, nX, eAX, ctgALabel);
				if (applied) {
					applied = checkAndManageIfNewLabeledValueIsANegativeLoop(sum, alphaBeta, nA, nX, eAX);
					if (applied) {
						if (Debug.ON) {
							LOG.info("Found a negative loop.");
						}
					}
					return;
				}
				continue;
			}

			//oracle is present and XC contains values.
			//check if there is value u such that v-u≥y-x
			alphaBeta = null;
			for (final Entry<Label> entryXC : XCLabeledValueEntrySet) {
				final Label beta1 = entryXC.getKey();
				if (checkStatus.isInNegativeScenarios(beta1)) {
					removeLabeledValueBecauseInNegativeScenario(beta1, null, eXC);
					continue;
				}
				if (!beta.isConsistentWith(beta1)) {
					continue;
				}
				final int v = entryXC.getIntValue();
				if (Constants.sumWithOverflowCheck(v, u) < contingentSpan) {
					//oracle is necessary. This rule cannot be applied.
					continue;
				}

				//I invoke getLabel4ValueInvolvingContingent here because only here it is certain that it is necessary
				if (alphaBeta == null) {
					alphaBeta =
						Objects.requireNonNull(getLabel4ValueInvolvingContingent(nC, nX, false).conjunction(alpha))
							.conjunction(beta);
					if (alphaBeta == null || checkStatus.isInNegativeScenarios(alphaBeta)) {
						continue;
					}
				}
				final Label alpha1BetaBeta1 = alphaBeta.conjunction(beta1);
				if (alpha1BetaBeta1 == null || checkStatus.isInNegativeScenarios(alpha1BetaBeta1)) {
					continue;
				}
				final boolean newValue =
					labeledLowerCaseRuleHelper(x, u, Constants.sumWithOverflowCheck(x, u), beta, alpha, alpha1BetaBeta1,
					                           nA, nC, nX, eAX, ctgALabel);
				if (newValue) {
					final boolean isNegativeLoop =
						checkAndManageIfNewLabeledValueIsANegativeLoop(sum, alpha1BetaBeta1, nA, nX, eAX);
					if (isNegativeLoop) {
						if (Debug.ON) {
							LOG.info("Found a negative loop.");
						}
						return;
					}
				}
			}
		}
	}

	/**
	 * <b>Oracle rule.</b>
	 * <pre>
	 * X ---(v,◇,β)-→ C ---(-y,C,α)-→ A
	 *  ←--(-u,◇,β')--|  ←--(x,c,α)---
	 *                |
	 *                (0)
	 *                |
	 *                ↓
	 *               O_C
	 * adds
	 * X ---(v-x,◇,αcβ)--------------→ A
	 *  ←--(y-u,◇,αcβ')-----------------|
	 *  |              C              |
	 *  |              |              |
	 *  |             (-u,◇,αcββ')     |
	 *  |             |               |
	 *  |             ↓               |
	 *  |-(0,◇,αcββ')→ O_C ←(x-u,◇,αcββ')
	 *
	 * when X is not contingent,
	 * αcββ' is consistent, and it is not subsumed by a negative scenario,
	 * and v-u &lt; y-x
	 * </pre>
	 *
	 * @param nX  node
	 * @param nC  node
	 * @param nA  node
	 * @param eAC CANNOT BE NULL
	 * @param eCX CANNOT BE NULL
	 * @param eAX CANNOT BE NULL
	 */
	void labeledOracleRule(@Nonnull final LabeledNode nA, @Nonnull final LabeledNode nC, @Nonnull final LabeledNode nX,
	                       @Nonnull final OSTNUEdgePluggable eAC,
	                       @Nonnull final OSTNUEdgePluggable eCX, @Nonnull final OSTNUEdgePluggable eAX) {

		if (nX == nC || nX == nA || nX.isContingent() || !nC.isContingent() || activationNode.get(nC) != nA) {
			//this rule cannot be applied.
			return;
		}
		final LabeledNode oracle = oracleNode.get(nC);
		if (oracle == null || nX == oracle) {
			//this rule cannot be applied.
			return;
		}

		final Set<Entry<Label>> CXLabeledValueEntrySet = eCX.getLabeledValueSet();
		if (CXLabeledValueEntrySet.isEmpty()) {
			return;
		}
		final OSTNUEdgePluggable eXC = this.g.findEdge(nX, nC);
		if (eXC == null) {
			return;
		}
		final Set<Entry<Label>> XCLabeledValueMap = eXC.getLabeledValueSet();

		final LabeledLowerCaseValue lowerCaseEntry = eAC.getLowerCaseValue();
		final int x = lowerCaseEntry.getValue();
		final Label alpha = lowerCaseEntry.getLabel();
		if (alpha == null || x < 0) {
			throw new IllegalStateException("Edge " + eAC + " is not a lower case contingent constraint.");
		}
		//contingent link has only two values with alpha label
		final OSTNUEdgePluggable eCA = this.g.findEdge(nC, nA);
		assert eCA != null;
		final int y = -eCA.getUpperCaseValue(alpha, nC.getALabel());
		if (y <= 0 || y <= x) {
			throw new IllegalStateException("Edge " + eCA + " is not an upper case contingent constraint.");
		}
		final int contingentSpan = y - x;

		boolean merged;
		for (final Entry<Label> entryCX : CXLabeledValueEntrySet) {
			boolean applied = false;
			final int u = entryCX.getIntValue();
			if (u > 0) {
				continue;
			}
			final Label beta1 = entryCX.getKey();
			if (checkStatus.isInNegativeScenarios(beta1)) {
				removeLabeledValueBecauseInNegativeScenario(beta1, null, eCX);
				continue;
			}

			//Apply the rule only when oracle is necessary, so c must be activated only from this point.
			final Label alpha1 = alpha.conjunction(getLabel4ValueInvolvingContingent(nC, nX, true));
			assert alpha1 != null;
			final Label alpha1Beta = alpha1.conjunction(beta1);

			if (alpha1Beta == null || checkStatus.isInNegativeScenarios(alpha1Beta)) {
				continue;
			}
			for (final Entry<Label> entryXC : XCLabeledValueMap) {
				final int v = entryXC.getIntValue();
				if (Constants.sumWithOverflowCheck(v, u) >= contingentSpan) {
					//Distance between C and X can compensate contingent span.
					continue;
				}
				final Label beta = entryXC.getKey();
				if (checkStatus.isInNegativeScenarios(beta)) {
					continue;
				}
				final Label alpha1BetaBeta1 = alpha1Beta.conjunction(beta);
				if (alpha1BetaBeta1 == null || checkStatus.isInNegativeScenarios(alpha1BetaBeta1)) {
					continue;
				}

				// X--->oracle
				OSTNUEdgePluggable edge = this.g.findEdge(nX, oracle);
				if (edge == null) {
					edge = this.g.makeNewEdge(nX.getName() + "-" + oracle.getName(), derived);
					this.g.addEdge(edge, nX, oracle);
				}
				merged = edge.mergeLabeledValue(alpha1BetaBeta1, 0);
				applied |= merged;
				if (merged && Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("Added/update edge " + edge);
					}
				}
				//Oracle -->C
				edge = this.g.findEdge(nC, oracle);
				assert edge != null;
				merged = edge.mergeLabeledValue(alpha1BetaBeta1, u);
				applied |= merged;
				if (merged && Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("Added/update edge " + edge);
					}
				}
				// A--->oracle
				edge = this.g.findEdge(nA, oracle);
				if (edge == null) {
					edge = this.g.makeNewEdge(nA.getName() + "-" + oracle.getName(), derived);
					this.g.addEdge(edge, nA, oracle);
				}
				merged = edge.mergeLabeledValue(alpha1BetaBeta1, Constants.sumWithOverflowCheck(x, u));
				applied |= merged;
				if (merged && Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("Added/update edge " + edge);
					}
				}
				//X --->A
				edge = this.g.findEdge(nX, nA);
				if (edge == null) {
					edge = this.g.makeNewEdge(nX.getName() + "-" + nA.getName(), derived);
					this.g.addEdge(edge, nX, nA);
				}
				merged = edge.mergeLabeledValue(alpha1.conjunction(beta), Constants.sumWithOverflowCheck(v, -x));
				applied |= merged;
				if (merged && Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("Added/update edge " + edge);
					}
				}
				//A-->X
				edge = eAX;
				merged = edge.mergeLabeledValue(alpha1.conjunction(beta1), Constants.sumWithOverflowCheck(y, u));
				applied |= merged;
				if (merged && Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("Added/update edge " + edge);
					}
				}
			}
			if (applied) {
				checkStatus.oracleRuleCalls++;
			}
		}
	}

	/**
	 * Applies 'labeled no case' or 'labeled upper case propagation' rules.<br>
	 *
	 * <pre>
	 * 1) CASE labeled no case, labeled upper case propagation
	 * X ----(u,◇,α)---→ Y ----(v,ℵ,β)-----→ W
	 * adds
	 * X -----------(u+v,ℵ,αβ)------------→  W
	 * ℵ can be empty. αβ must be consistent and not in negative scenarios
	 * </pre>
	 *
	 * @param nX  node
	 * @param nY  node
	 * @param nW  node
	 * @param eXY CANNOT BE NULL
	 * @param eYW CANNOT BE NULL
	 * @param eXW CANNOT BE NULL
	 */
	void labeledPropagationRule(@Nonnull final LabeledNode nX, @Nonnull final LabeledNode nY,
	                            @Nonnull final LabeledNode nW,
	                            @Nonnull final OSTNUEdgePluggable eXY, @Nonnull final OSTNUEdgePluggable eYW,
	                            @Nonnull final OSTNUEdgePluggable eXW) {

		if (nY.isContingent() && (activationNode.get(nY) == nW || activationNode.get(nY) == nX)) {
			//upper case rule or lower case rule must be applied or oracle rule
			return;
		}
		final LabeledALabelIntTreeMap YWAllLabeledValueMap = eYW.getAllUpperCaseAndLabeledValuesMaps();
		if (YWAllLabeledValueMap.isEmpty()) {
			return;
		}
		final Set<Entry<Label>> XYLabeledValueMap = eXY.getLabeledValueSet();
		// 1) CASE labeled no case, labeled upper case propagation
		for (final Entry<Label> entryXY : XYLabeledValueMap) {
			final Label alpha = entryXY.getKey();
			if (checkStatus.isInNegativeScenarios(alpha)) {
				removeLabeledValueBecauseInNegativeScenario(alpha, null, eXY);
				continue;
			}
			final int u = entryXY.getIntValue();

			for (ALabel aleph : YWAllLabeledValueMap.keySet()) {
				for (final Entry<Label> entryYW : YWAllLabeledValueMap.get(aleph).entrySet()) {// entrySet read-only
					final Label beta = entryYW.getKey();
					if (checkStatus.isInNegativeScenarios(beta)) {
						removeLabeledValueBecauseInNegativeScenario(beta, aleph, eYW);
						continue;
					}
					final Label alphaBeta;
					alphaBeta = alpha.conjunction(beta);
					if (alphaBeta == null || checkStatus.isInNegativeScenarios(alphaBeta)) {
						continue;
					}

					final int v = entryYW.getIntValue();
					final int sum = Constants.sumWithOverflowCheck(u, v);

					if (sum >= 0) {
						if (nX == nW) {
							// it would be a redundant edge
							continue;
						}
						aleph = ALabel.emptyLabel;
					}

					final int oldValue =
						(aleph.isEmpty()) ? eXW.getValue(alphaBeta) : eXW.getUpperCaseValue(alphaBeta, aleph);

					if ((oldValue != Constants.INT_NULL) && (sum >= oldValue)) {
						// value is stored only if it is more negative than the current one.
						continue;
					}

					String logMsg = null;
					if (Debug.ON) {
						final String oldXW = eXW.toString();
						logMsg =
							"labeled propagation rule applied to edge " + oldXW + ":\n" + "Detail: " + nW.getName() +
							" <---" +
							upperCaseValueAsString(aleph, v, beta) + "--- " + nY.getName() + " <---" +
							upperCaseValueAsString(ALabel.emptyLabel, u, alpha) + "--- " + nX.getName() + "\nresult: " +
							nW.getName() + " <---" +
							upperCaseValueAsString(aleph, sum, alphaBeta) + "--- " + nX.getName() + "; old value: " +
							Constants.formatInt(oldValue);
					}

					final boolean mergeStatus = (aleph.isEmpty()) ? eXW.mergeLabeledValue(alphaBeta, sum)
					                                              : eXW.mergeUpperCaseValue(alphaBeta, aleph, sum);

					if (mergeStatus) {
						if (aleph.isEmpty()) {
							checkStatus.labeledValuePropagationCalls++;
						} else {
							checkStatus.upperCaseRuleCalls++;
						}
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								LOG.log(Level.FINER, logMsg);
							}
						}
						final boolean isNegativeLoop =
							checkAndManageIfNewLabeledValueIsANegativeLoop(sum, alphaBeta, nX, nW, eXW);
						if (isNegativeLoop) {
							if (Debug.ON) {
								LOG.info("Found a negative loop.");
							}
							return;
						}
					}
				}
			}
		}
	}

	/**
	 * Applies 'labeled upper case' rules.<br>
	 *
	 * <pre>
	 * 2) labeled upper case
	 * A ←--(-y,C,𝛂)---- C ←-(v,◇,β)-------- X
	 *   ---(x,C,𝛂)----→   --(-u,◇,β')-----→
	 * adds
	 * A ←---(y+v,C,¬c𝛂ββ')----------------- X
	 * when ¬c𝛂ββ' is consistent and it is not subsumed by a negative scenario.
	 * Oracle for Y does not exist or v-u ≥ y-x.
	 * </pre>
	 *
	 * @param nX  node
	 * @param nC  node
	 * @param nA  node
	 * @param eXC CANNOT BE NULL
	 * @param eCA CANNOT BE NULL
	 * @param eXA CANNOT BE NULL
	 */
	void labeledUpperCaseRule(@Nonnull final LabeledNode nX, @Nonnull final LabeledNode nC,
	                          @Nonnull final LabeledNode nA,
	                          @Nonnull final OSTNUEdgePluggable eXC, @Nonnull final OSTNUEdgePluggable eCA,
	                          @Nonnull final OSTNUEdgePluggable eXA) {

		if (!nC.isContingent() || activationNode.get(nC) != nA) {
			//labeledPropagationRule must applied
			return;
		}
		final ALabel ctgALabel = nC.getALabel();

		final LabeledIntMap upperCaseMap = eCA.getUpperCaseValueMap().get(ctgALabel);
		if (upperCaseMap == null || upperCaseMap.size() != 1) {
			throw new IllegalStateException("Edge " + eCA + " is not an upper case contingent constraint.");
		}
		//contingent link has only two values with empty label
		final Entry<Label> upperCaseEntry = upperCaseMap.getMinLabeledValue();
		final Label alpha = upperCaseEntry.getKey();
		final int y = -upperCaseEntry.getIntValue();
		if (alpha == null || y <= 0) {
			throw new IllegalStateException("Edge " + eCA + " is not an upper case contingent constraint.");
		}

		final OSTNUEdgePluggable eAC = this.g.findEdge(nA, nC);
		if (eAC == null || eAC.getLowerCaseValue() == null) {
			throw new IllegalStateException("Edge " + eAC + " is not a lower case contingent constraint.");
		}
		final int x = eAC.getLowerCaseValue().getValue();
		if (x < 0) {
			throw new IllegalStateException("Edge " + eCA + " is not an upper case contingent constraint.");
		}
		final int contingentSpan = y - x;

		final Set<Entry<Label>> XCLabeledValueEntrySet = eXC.getLabeledValueSet();
		final OSTNUEdgePluggable eCX = this.g.findEdge(nC, nX);
		final Set<Entry<Label>> CXLabeledValueEntrySet =
			(eCX == null) ? ObjectSets.emptySet() : eCX.getLabeledValueSet();

		final boolean oraclePresent = this.oracleNode.get(nC) != null;
		for (final Entry<Label> entryXC : XCLabeledValueEntrySet) {
			final Label beta = entryXC.getKey();
			if (checkStatus.isInNegativeScenarios(beta)) {
				removeLabeledValueBecauseInNegativeScenario(beta, null, eXC);
				continue;
			}
			final int v = entryXC.getIntValue();
			if (v < 0) {
				//X is after C, upper case rule is useless.
				continue;
			}
			final int sum = Constants.sumWithOverflowCheck(-y, v);

			if (!oraclePresent) {
				//Standard contingent link. Apply original UC rule (without labeling)
				final Label alphaBeta = alpha.conjunction(beta);
				if (alphaBeta == null || checkStatus.isInNegativeScenarios(alphaBeta)) {
					continue;
				}
				final boolean isNewValue =
					labeledUpperCaseRuleHelper(y, v, sum, beta, alphaBeta, nX, nC, nA, eXA, ctgALabel);
				if (isNewValue) {
					final boolean isNegativeLoop =
						checkAndManageIfNewLabeledValueIsANegativeLoop(sum, alphaBeta, nX, nA, eXA);
					if (isNegativeLoop) {
						if (Debug.ON) {
							LOG.info("Found a negative loop.");
						}
						return;
					}
				}
				continue;
			}

			//oracle is present
			//we apply the rule only when oracle is not necessary

			if (CXLabeledValueEntrySet.isEmpty() || nX == nA) {
				//nX == nA ->lower case rule must be applied on the contingent link itself to verify the bounds have not changed.
				final Label alpha1 = alpha.conjunction(
					getLabel4ValueInvolvingContingent(nC, nX, false));//we ask a new label only when v >= 0
				final Label alpha1Beta = beta.conjunction(alpha1);
				if (alpha1Beta == null || checkStatus.isInNegativeScenarios(alpha1Beta)) {
					continue;
				}
				final boolean isNewValue =
					labeledUpperCaseRuleHelper(y, v, sum, beta, alpha1Beta, nX, nC, nA, eXA, ctgALabel);
				if (isNewValue) {
					final boolean isNegativeLoop =
						checkAndManageIfNewLabeledValueIsANegativeLoop(sum, alpha1Beta, nX, nA, eXA);
					if (isNegativeLoop) {
						if (Debug.ON) {
							LOG.info("Found a negative loop.");
						}
						return;
					}
				}
				continue;
			}
			//Oracle is present AND CX have some values
			//Check if there is value -u such that v-u≥y-x
			Label alpha1Beta = null;
			for (final Entry<Label> entryCX : CXLabeledValueEntrySet) {
				final Label beta1 = entryCX.getKey();
				if (checkStatus.isInNegativeScenarios(beta1)) {
					assert eCX != null;
					removeLabeledValueBecauseInNegativeScenario(beta1, null, eCX);
					continue;
				}
				if (!beta.isConsistentWith(beta1)) {
					continue;
				}
				final int u = entryCX.getIntValue();
				if (Constants.sumWithOverflowCheck(v, u) < contingentSpan) {
					//oracle is necessary. This rule cannot be applied.
					continue;
				}

				//Ask a new label only when v Constants.sumWithOverflowCheck(v, u) >= contingentSpan
				if (alpha1Beta == null) {
					final Label alpha1 = alpha.conjunction(getLabel4ValueInvolvingContingent(nC, nX, false));
					alpha1Beta = beta.conjunction(alpha1);
					if (alpha1Beta == null || checkStatus.isInNegativeScenarios(alpha1Beta)) {
						continue;
					}
				}
				final Label alpha1BetaBeta1 = alpha1Beta.conjunction(beta1);
				if (alpha1BetaBeta1 == null || checkStatus.isInNegativeScenarios(alpha1BetaBeta1)) {
					continue;
				}
				final boolean newValue =
					labeledUpperCaseRuleHelper(y, v, sum, beta, alpha1BetaBeta1, nX, nC, nA, eXA, ctgALabel);
				if (newValue) {
					final boolean isNegativeLoop =
						checkAndManageIfNewLabeledValueIsANegativeLoop(sum, alpha1BetaBeta1, nX, nA, eXA);
					if (isNegativeLoop) {
						if (Debug.ON) {
							LOG.info("Found a negative loop.");
						}
						return;
					}
				}
			}
		}
	}

	/*
	 * Create a copy of this.g merging, for each edge of g, all ordinary and upper case values.
	 * Moreover, for each edge representing lower bound of a contingent, sets its ordinary value to the maximum of the contingent.
	 *
	 * @return the all-max projection of the graph g (CSTN graph).
	 */
	TNGraph<OSTNUEdgePluggable> makeAllMaxProjection() {
		final TNGraph<OSTNUEdgePluggable> allMax = new TNGraph<>("allMaxProjection", this.g.getEdgeImplClass());
		// clone all nodes
		LabeledNode vNew;
		for (final LabeledNode v : this.g.getVertices()) {
			vNew = new LabeledNode(v);
			allMax.addVertex(vNew);
		}
		assert this.g.getZ() != null;
		allMax.setZ(allMax.getNode(this.g.getZ().getName()));

		// clone all edges giving the right new endpoints corresponding the old ones.
		// we do not add edges connecting nodes in not consistent scenarios (such edges have only unknown labels).
		OSTNUEdgePluggable eNew;
		final EdgeSupplier<OSTNUEdgePluggable> edgeFactory = allMax.getEdgeFactory();
		for (final OSTNUEdgePluggable e : this.g.getEdges()) {
			final boolean toAdd;
			final LabeledNode s = this.g.getSource(e);
			final LabeledNode d = this.g.getDest(e);
			assert s != null;
			final String sName = s.getName();
			assert d != null;
			final String dName = d.getName();
			final Label sdLabel = s.getLabel().conjunction(d.getLabel());
			if (sdLabel == null) {
				continue;
			}
			eNew = allMax.findEdge(sName, dName);
			toAdd = (eNew == null);
			if (toAdd) {
				eNew = edgeFactory.get(e); // to preserve the name
			}
			eNew.setConstraintType(Edge.ConstraintType.requirement);
			final LabeledALabelIntTreeMap allValueMapE = e.getAllUpperCaseAndLabeledValuesMaps();
			//merge all labeled values and upper-case labeled values as labeled values.
			for (final ALabel alabel : allValueMapE.keySet()) {
				eNew.mergeLabeledValue(allValueMapE.get(alabel));
			}
			if (e.isContingentEdge()) {
				// if e is the upper-case constraint C--(C:-y)-->A of a contingent link (A,C),
				// then, it must set C--(-10)-->A  and A--(10)-->C
				final LabeledALabelIntTreeMap map = e.getUpperCaseValueMap();
				if (map != null && map.size() > 0) {
					OSTNUEdgePluggable eNewInverted = allMax.findEdge(dName, sName);
					if (eNewInverted == null) {
						// this is the lower bound
						eNewInverted = edgeFactory.get(this.g.findEdge(d, s));
						allMax.addEdge(eNewInverted, dName, sName);
					}
					final int ub = map.getValue(sdLabel, s.getALabel());
					if (ub != Constants.INT_NULL) {
						eNewInverted.mergeLabeledValue(sdLabel, -ub);
					}
				}
			}
			if (toAdd) {
				allMax.addEdge(eNew, sName, dName);
			}
		}
		return allMax;
	}

	/**
	 * Removes all labeled values belonging to negative scenarios.
	 */
	void removeLabeledValuesBelongingToNegativeScenarios() {
		for (final OSTNUEdgePluggable edge : this.g.getEdges()) {
			//copy the labeled values because it is not possible to navigate and remove
			ObjectSet<Entry<Label>> labeledValues = edge.getLabeledValueSet();
			for (final Entry<Label> entry : labeledValues) {
				final Label label = entry.getKey();
				if (checkStatus.isInNegativeScenarios(label)) {
					removeLabeledValueBecauseInNegativeScenario(label, null, edge);
				}
			}
			final LabeledALabelIntTreeMap upperCaseValues = edge.getUpperCaseValueMap();
			for (final ALabel aLabel : upperCaseValues.keySet()) {
				labeledValues = upperCaseValues.get(aLabel).entrySet();
				for (final Entry<Label> entry : labeledValues) {
					final Label label = entry.getKey();
					if (checkStatus.isInNegativeScenarios(label)) {
						removeLabeledValueBecauseInNegativeScenario(label, aLabel, edge);
					}
				}
			}
			if (edge.isEmpty()) {
				this.g.removeEdge(edge);
			}
		}
	}

	/**
	 * Helper methods for apply the lower case rule for a pair of values (x,u).
	 *
	 * @param x         lower bound of contingent AC
	 * @param u         value of edge CX
	 * @param beta      label of value u
	 * @param alpha     label of value x
	 * @param alphaBeta label of final value x+u
	 * @param nA        activation node
	 * @param nC        contingent node
	 * @param nX        external node
	 * @param eAX       edge where x+u will be stored
	 * @param ctgALabel ALabel associated to nC
	 *
	 * @return true if the value was stored, false otherwise
	 */
	private boolean labeledLowerCaseRuleHelper(int x, int u, int sum, @Nonnull Label beta, @Nonnull Label alpha,
	                                           @Nullable Label alphaBeta,
	                                           @Nonnull LabeledNode nA, @Nonnull LabeledNode nC,
	                                           @Nonnull LabeledNode nX, @Nonnull OSTNUEdgePluggable eAX,
	                                           @Nonnull ALabel ctgALabel) {

		if (alphaBeta == null || checkStatus.isInNegativeScenarios(alphaBeta)) {
			return false;
		}
		if ((sum >= 0 && nA == nX)) {
			// it would be a redundant edge
			return false;
		}
		final int oldValue = eAX.getUpperCaseValue(alphaBeta, ctgALabel);
		if ((oldValue != Constants.INT_NULL && oldValue <= sum)) {
			return false;
		}
		String logMsg = null;
		if (Debug.ON) {
			final String oldAX = eAX.toString();
			logMsg =
				"labeledLowerCaseRule applied to edge " + oldAX + ":\nDetail: " + nX.getName() + " <---" +
				upperCaseValueAsString(ALabel.emptyLabel, u, beta) +
				"--- " + nC.getName() + " <---" + lowerCaseValueAsString(ctgALabel, x, alpha) + "--- " + nA.getName() +
				"\nresult: " + nX.getName() + " <---" +
				upperCaseValueAsString(ALabel.emptyLabel, sum, alphaBeta) + "--- " + nA.getName() + "; oldValue: " +
				Constants.formatInt(oldValue);
		}
		final boolean localApp = eAX.mergeLabeledValue(alphaBeta, sum);
		if (localApp) {
			checkStatus.lowerCaseRuleCalls++;
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, logMsg);
				}
			}
			return true;
		}
		return false;
	}

	private boolean labeledUpperCaseRuleHelper(int y, int v, int sum, @Nonnull Label beta, @Nullable Label alphaBeta,
	                                           @Nonnull LabeledNode nX,
	                                           @Nonnull LabeledNode nC, @Nonnull LabeledNode nA,
	                                           @Nonnull OSTNUEdgePluggable eXA, @Nonnull ALabel ctgALabel) {
		if (alphaBeta == null || checkStatus.isInNegativeScenarios(alphaBeta)) {
			return false;
		}
		if (sum >= 0) {
			// it would be a redundant edge
			if (nX == nA) {
				return false;
			}
			ctgALabel = ALabel.emptyLabel;
		}
		final int oldValue =
			(ctgALabel.isEmpty()) ? eXA.getValue(alphaBeta) : eXA.getUpperCaseValue(alphaBeta, ctgALabel);
		if ((oldValue != Constants.INT_NULL) && (sum >= oldValue)) {
			// value is stored only if it is more negative than the current one.
			return false;
		}
		String logMsg = null;
		if (Debug.ON) {
			final String oldXW = eXA.toString();
			logMsg = "Labeled upper case propagation applied to edge " + oldXW + ":\n" + "Detail: " + nA.getName() +
			         " ←---" +
			         upperCaseValueAsString(nC.getALabel(), -y, Label.emptyLabel) + "--- " + nC.getName() + " ←---" +
			         upperCaseValueAsString(ALabel.emptyLabel, v, beta) + "--- " + nX.getName() + "\nresult: " +
			         nA.getName() + " ←---" +
			         upperCaseValueAsString(ctgALabel, sum, alphaBeta) + "--- " + nX.getName() + "; old value: " +
			         Constants.formatInt(oldValue);
		}

		final boolean mergeStatus = (ctgALabel.isEmpty()) ? eXA.mergeLabeledValue(alphaBeta, sum)
		                                                  : eXA.mergeUpperCaseValue(alphaBeta, ctgALabel, sum);

		if (mergeStatus) {
			if (ctgALabel.isEmpty()) {
				checkStatus.labeledValuePropagationCalls++;
			} else {
				checkStatus.upperCaseRuleCalls++;
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, logMsg);
				}
			}
			return true;
		}
		return false;
	}
}
