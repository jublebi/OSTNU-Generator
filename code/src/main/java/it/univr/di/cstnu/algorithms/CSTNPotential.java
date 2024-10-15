// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

package it.univr.di.cstnu.algorithms;

import it.unimi.dsi.fastutil.chars.CharOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.univr.di.Debug;
import it.univr.di.cstnu.graph.CSTNEdge;
import it.univr.di.cstnu.graph.Edge.ConstraintType;
import it.univr.di.cstnu.graph.LabeledNode;
import it.univr.di.cstnu.graph.TNGraph;
import it.univr.di.labeledvalue.*;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents a Conditional Simple Temporal Network (CSTN) and it contains a method to check the dynamic consistency of
 * the instance.
 * <p>
 * Edge weights are signed integer.
 * <p>
 * The dynamic consistency check (DC check) is done assuming the instantaneous reaction semantics (cf. ICAPS 2016 paper,
 * table 1). and that the instance is streamlined (cf TIME 2018 paper). In this class the DC checking is solved using
 * the Single-Sink Shortest-Paths algorithm (SSSP), and R0-R3 rules. This DC checking algorithm was presented at ICAPS
 * 2020.
 *
 * @author Roberto Posenato
 * @version $Rev: 840 $
 */
public class CSTNPotential extends CSTNIR {

	/**
	 * Version of the class
	 */
	// static final public String VERSIONandDATE = "Version 0.1 - February, 20 2019";
	// static final public String VERSIONandDATE = "Version 0.2 - March, 31 2019";// It extends CSTNIR. I proved that
	// pseudo-polynomiality cannot be avoided.
	// static final public String VERSIONandDATE = "Version 2.0 - April, 26 2019";// During the init, all nodes in the negative subpath of a negative q-loop
	// will be identified by putting -∞ in their potential.
	// static final public String VERSIONandDATE = "Version 3.0 - May, 01 2019";// It apply SPFA approach directly on instance adjusting the SPFA.
	// static final public String VERSIONandDATE = "Version 3.1 - June, 09 2019";// Edge refactoring
	// static final public String VERSIONandDATE = "Version 3.5 - November, 07 2019";// Version working with CSTN 9R v. 6.5 (SVN 363)
	// static final public String VERSIONandDATE = "Version 3.6 - November, 25 2019";// Renamed
	// static final public String VERSIONandDATE = "Version 4.0 - January, 31 2020";// Implements HP_20 algorithm presented at ICAPS 2020
	static final public String VERSIONandDATE = "Version 4.1 - December, 31 2021";
	// Improved HP_20 algorithm: added upper bound determination

	/**
	 * logger
	 */
	static private final Logger LOG = Logger.getLogger(CSTNPotential.class.getName());

	/**
	 * True if DC checking algorithm must determine also the maximum execution time for each time-point.
	 */
	boolean isUpperBoundRequested;

	/**
	 * TNGraph&lt;CSTNEdge&gt; order
	 */
	int numberOfNodes;

	/**
	 * Constructor for CSTNPotential.
	 *
	 * @param graph TNGraph to check
	 */
	public CSTNPotential(TNGraph<CSTNEdge> graph) {
		super(graph);
		// this.withNodeLabels = false;
		isUpperBoundRequested = false;
	}

	/**
	 * Constructor for CSTNPotential.
	 *
	 * @param graph        TNGraph to check
	 * @param givenTimeOut timeout for the check
	 */
	public CSTNPotential(TNGraph<CSTNEdge> graph, int givenTimeOut) {
		super(graph, givenTimeOut);
		// this.withNodeLabels = false;
		isUpperBoundRequested = false;
	}

	/**
	 * Default constructor.
	 */
	CSTNPotential() {
		// this.withNodeLabels = false;
		isUpperBoundRequested = false;
	}

	/**
	 * Just for using this class also from a terminal.
	 *
	 * @param args an array of {@link java.lang.String} objects.
	 *
	 * @throws java.io.IOException                            if any.
	 * @throws javax.xml.parsers.ParserConfigurationException if any.
	 * @throws org.xml.sax.SAXException                       if any.
	 */
	@SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
	public static void main(final String[] args) throws IOException, ParserConfigurationException, SAXException {
		defaultMain(args, new CSTNPotential(), "Potential DC");
	}

	/**
	 * {@inheritDoc} Calls {@link CSTN#initAndCheck()} and, if everything is ok, it determined all possible -∞
	 * potentials in negative q-loops.
	 */
	@Override
	public void initAndCheck() throws WellDefinitionException {
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "Starting CSTN potential checking. Init data structures...");
			}
		}
		// determines the strict horizon
		// it is sufficient to put
		propagationOnlyToZ = true;
		// before calling
		super.initAndCheck();

		checkStatus.reset();
		checkStatus.initialized = true;

		// qLoopFinder can detect a negative cycle!
		qLoopFinder();

		for (final LabeledNode node : g.getVertices()) {
			node.clearPotential();
		}

		if (isUpperBoundRequested) {
			propagationOnlyToZ = false;// now it is important to guarantee that all propagation can be done!
			for (final LabeledNode node : g.getVerticesArray()) {
				node.putLabeledUpperPotential(node.getLabel(), horizon);
			}
			final LabeledNode Z = g.getZ();
			assert Z != null;
			Z.putLabeledUpperPotential(Label.emptyLabel, 0);
		}

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "Init done!");
			}
		}
	}

	/**
	 * This version is a restricted version of
	 * {@link CSTN#labelPropagation(LabeledNode, LabeledNode, LabeledNode, CSTNEdge, CSTNEdge, CSTNEdge)}. This method
	 * is used by {@link #initAndCheck()} for determining all negative qloops!
	 */
	@Override
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "DLS_DEAD_LOCAL_STORE", justification = "I know!")
	boolean labelPropagation(final LabeledNode nA, final LabeledNode nB, final LabeledNode nC, final CSTNEdge eAB,
	                         final CSTNEdge eBC, CSTNEdge eAC) {
		// * Be careful, in order to propagate correctly possibly -∞ self-loop, it is necessary call this method also for triple like with nodes A == B or B==C!
		// Visibility is package because there is Junit Class test that checks this method.

		boolean ruleApplied = false;
		Label nAnCLabel = null;
		if (withNodeLabels) {
			nAnCLabel = nA.getLabel().conjunction(nC.getLabel());
			if (nAnCLabel == null) {
				return false;
			}
		}

		final boolean nAisAnObserver = nA.isObserver();
		final char proposition = nA.getPropositionObserved();
		final boolean nCisAnObserver = nC.isObserver();
		final Literal unkPropositionC = Literal.valueOf(nC.getPropositionObserved(), Literal.UNKNOWN);

		final ObjectSet<Object2IntMap.Entry<Label>> setToReuse = new ObjectArraySet<>();
		String firstLog =
			"Potential Labeled Propagation Rule considers edges " + eAB.getName() + ", " + eBC.getName() + " for "
			+ eAC.getName();
		for (final Object2IntMap.Entry<Label> ABEntry : eAB.getLabeledValueSet()) {
			final Label labelAB = ABEntry.getKey();
			final int u = ABEntry.getIntValue();
			for (final Object2IntMap.Entry<Label> BCEntry : eBC.getLabeledValueSet(setToReuse)) {
				final int v = BCEntry.getIntValue();
				int sum = Constants.sumWithOverflowCheck(u, v);
				if (sum > 0) {
					continue;
				}
				final Label labelBC = BCEntry.getKey();
				final boolean qLabel;
				Label newLabelAC;
				if (lpMustRestricted2ConsistentLabel(u, v)) {
					// Even if we published that when nC == Z, the label must be consistent, we
					// also showed (but not published) that if u<0, then label can contain unknown even when nC==Z.
					newLabelAC = labelAB.conjunction(labelBC);
					if (newLabelAC == null) {
						continue;
					}
				} else {
					newLabelAC = labelAB.conjunctionExtended(labelBC);
					qLabel = newLabelAC.containsUnknown();
					if (qLabel && withNodeLabels) {
						newLabelAC = removeChildrenOfUnknown(newLabelAC);
						//						qLabel = newLabelAC.containsUnknown();
					}
				}
				if (withNodeLabels) {
					if (!newLabelAC.subsumes(nAnCLabel)) {
						if (Debug.ON) {
							LOG.log(Level.FINEST,
							        "New alphaBeta label " + newLabelAC + " does not subsume node labels " + nAnCLabel
							        + ". New value cannot be added.");
						}
						continue;
					}
				}

				final int oldValue = eAC.getValue(newLabelAC);
				if (oldValue != Constants.INT_NULL && sum >= oldValue) {
					continue;
				}

				// Prepare the log in advance in order to avoid repetition
				StringBuilder log = new StringBuilder(80);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						log = new StringBuilder(firstLog);
						log.append("\nsource: ").append(nA.getName()).append(" ---").append(pairAsString(labelAB, u))
							.append("⟶ ").append(nB.getName()).append(" ---").append(pairAsString(labelBC, v))
							.append("⟶ ").append(nC.getName()).append("\n");
					}
				}
				if (nA == nC) {
					if (sum == 0) {
						continue;
					}
					sum = Constants.INT_NEG_INFINITE;
					if (updatePotential(nA, newLabelAC, sum, false, log.toString())) {
						// The labeled value is negative and label is in Q*.
						// The -∞ value is now stored on node A (==C) as potential value if label is in Q*/P*, otherwise, a negative loop has been found!
						ruleApplied = true;
						checkStatus.labeledValuePropagationCalls++;
						if (!checkStatus.consistency) {
							return true;
						}
					}
					continue;
				} // end if nA==nC

				// here sum has to be added!
				if (eAC.mergeLabeledValue(newLabelAC, sum)) {
					ruleApplied = true;
					checkStatus.labeledValuePropagationCalls++;
					// // R0qR0 rule
					if (nAisAnObserver && newLabelAC.contains(proposition)) {
						final Label newLabelAC1 = labelModificationR0qR0Core(nA, nC, newLabelAC, sum);
						if (!newLabelAC1.equals(newLabelAC)) {
							newLabelAC = newLabelAC1;
							eAC.mergeLabeledValue(newLabelAC, sum);
						}
					}
					// The following is equivalent to R3qR33 when nD==Obs and newLabel contains the unknown literal of obs prop.
					if (nCisAnObserver && newLabelAC.contains(unkPropositionC)) {
						final Label newLabelAC1 = newLabelAC.remove(unkPropositionC);
						if (!newLabelAC1.equals(newLabelAC)) {
							newLabelAC = newLabelAC1;
							sum = 0;
							eAC.mergeLabeledValue(newLabelAC, sum);
						}
					}
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							log.append("result: ").append(nA.getName()).append(" ---")
								.append(pairAsString(newLabelAC, sum)).append("⟶ ").append(nC.getName())
								.append("; old value: ").append(Constants.formatInt(oldValue)).append("\n");
							LOG.log(Level.FINER, log.toString());
							firstLog = "";
						}
					}
				}
			}
		}
		return ruleApplied;
	}

	/**
	 * Completes the graph adding only temporary negative edges for n round. The scope is to find possible negative
	 * q-loops. Once possibly negative q-loops have been found (and store as (-∞, qLabel) in node potentials), the added
	 * temporary edges are removed from the graph.
	 */
	@SuppressWarnings("null")
	private void qLoopFinder() {
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "Starting completion of graph with edges having negative weights...");
			}
		}

		numberOfNodes = g.getVertexCount();
		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO, "Number of nodes: " + numberOfNodes);
			}
		}

		ObjectArrayList<CSTNEdge> edgesToCheck = new ObjectArrayList<>(g.getEdges());
		ObjectArrayList<CSTNEdge> newEdgesToCheck = new ObjectArrayList<>();
		final ObjectArrayList<CSTNEdge> edgesToRemove = new ObjectArrayList<>();

		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO, "Number of edges: " + edgesToCheck.size());
			}
		}

		boolean noFirstRound = false;
		int negInfinityPotentialCount = 0;
		int n = numberOfNodes;

		//noinspection MethodCallInLoopCondition
		while (!edgesToCheck.isEmpty() && n-- > 0) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "***Cycle countdown: " + n);
				}
			}
			for (final CSTNEdge edgeAB : edgesToCheck) {
				final LabeledNode A = g.getSource(edgeAB);
				final LabeledNode B = g.getDest(edgeAB);
				assert A != null;
				assert B != null;
				boolean newEdge;
				for (final CSTNEdge edgeBC : g.getOutEdges(B)) {
					final LabeledNode C = g.getDest(edgeBC);
					assert C != null;
					CSTNEdge edgeAC = g.findEdge(A, C);

					// Propagate only to new edges.
					// At first round, even an already defined edge has to be considered new.
					newEdge = edgeAC == null;
					if (newEdge) {
						edgeAC = makeNewEdge(A.getName() + C.getName() + "∞", CSTNEdge.ConstraintType.qloopFinder);
					} else {
						if (noFirstRound) {
							continue;
						}
					}
					assert edgeAC != null : "No way!";

					final boolean newValue = labelPropagation(A, B, C, edgeAB, edgeBC, edgeAC);
					if (newValue) {
						if (!checkStatus.consistency) {
							checkStatus.initialized = true;
							checkStatus.finished = true;
							return;
						}

						if (A != C) {
							newEdgesToCheck.add(edgeAC);
							if (newEdge && edgeAC.getConstraintType() == ConstraintType.qloopFinder) {
								g.addEdge(edgeAC, A, C);
								edgesToRemove.add(edgeAC);
							}
						}
						if (Debug.ON) {
							if (A == C) {
								negInfinityPotentialCount++;
							}
						}
					}
				}
			}
			edgesToCheck = newEdgesToCheck;
			newEdgesToCheck = new ObjectArrayList<>();
			noFirstRound = true;
		}
		for (final CSTNEdge e : edgesToRemove) {
			g.removeEdge(e);
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO, "All possible -∞ potentials found. They are " + negInfinityPotentialCount + "."
				                    + "\nAll added edges for such a checking removed." +
				                    "\nTo be sure, number of edges: "
				                    + g.getEdgeCount());
			}
		}
	}

	/**
	 * Adds the labeled value {@code (value, label)} in the potential of {@code node}.<br> If {@code node} is an
	 * observation node, then {@code label} is cleaned removing any literals related to the observation node.<br> When
	 * {@code (value, label)} has value &lt; 0, label without unknown literals, and the node is Z, then a negative
	 * circuit is present: this method sets {@link #getCheckStatus()}.consistency to false and
	 * {@link #getCheckStatus()}.finished to true.
	 *
	 * @param node     the considered node
	 * @param newLabel the new label
	 * @param newValue it is assumed that it is != {@link Constants#INT_NULL}
	 * @param fromR3   true if the value comes from R3 rule
	 * @param log      log message to complete.
	 *
	 * @return true if the value was added.
	 */
	@SuppressWarnings("ParameterCanBeLocal")
	private boolean updatePotential(LabeledNode node, Label newLabel, int newValue, boolean fromR3, String log) {

		if (newValue >= 0) {
			throw new IllegalArgumentException(
				"Potential value cannot be non-negative: " + node + ", new potential to add "
				+ AbstractLabeledIntMap.entryAsString(newLabel, newValue));
		}

		if (node.isObserver()) {
			newLabel = newLabel.remove(node.getPropositionObserved());
		}

		final int currentValue = node.getLabeledPotential(newLabel);
		if (node.putLabeledPotential(newLabel, newValue)) {
			// The value was added
			/*
			 * Theoretically, if a new value with the same label is added for n+1 times, it means that there is a negative cycle.
			 * In such a case, the value is updated to Constants.INT_NEG_INFINITE.
			 * If #potentialR3 modifies the label of a value, it is necessary to restart again for such a label.
			 * Moreover, if the old value for label is NULL, it means that either it is the first time that it is added, or it has been removed
			 * in a previous cycle by R3. In any case, the counter must start from 1.
			 * Therefore, if the (label,value) comes from potentialR3 or the old value is NULL, reset the counter
			 * See 088_1negQloop1posQloop1Shared.cstn as test case where the update of each node has to be done
			 * following the update of each obs node and each obs node is updated by 1 at each cycle.
			 */
			final int count = updatePotentialCount(node, newLabel, fromR3 || currentValue == Constants.INT_NULL);
			if (count > numberOfNodes) {
				newValue = Constants.INT_NEG_INFINITE;
				node.putLabeledPotential(newLabel, newValue);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.log(Level.FINER,
						        "###Update potential has been called more than " + numberOfNodes + " times on "
						        + node.getName() + "\nSet the associated value to -∞.");
					}
				}
			}
			if (!newLabel.containsUnknown() && ((newValue == Constants.INT_NEG_INFINITE) || (node == g.getZ()))) {
				// found a negative cycle!
				checkStatus.consistency = false;
				checkStatus.finished = true;
				checkStatus.negativeLoopNode = node;
			}
			if (Debug.ON) {
				if (fromR3) {
					log += "R3 ";
				}
				log += "Update potential on " + node.getName() + ": " + pairAsString(newLabel, currentValue)
				       + " replaced by " + pairAsString(newLabel, newValue) + "\n";// + ". Update #" + count;
				if (!checkStatus.consistency) {
					log += "***\nFound a negative loop in node " + node + "\n***\n\n";
				}
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, log);
				}
			}
			checkStatus.potentialUpdate++;
			return true;
		}
		return false;
	}

	/**
	 * Updates the count associated to the pair (node, label) adding 1 unless reset is true. If reset is true or there
	 * was no count for the pair, the count is set to 1.<br> If #potentialR3 modifies the label of a value, it is
	 * necessary to restart again for such a label. Moreover, if the old value for label is NULL, it means that either
	 * it is the first time that it is added, or it has been removed in a previous cycle by R3. In any case, the counter
	 * must start from 1. Therefore, if the (label,value) comes from potentialR3 or the old value is NULL, reset the
	 * counter See 088_1negQloop1posQloop1Shared.cstn as test case where the update of each node has to be done
	 * following the update of each obs node and each obs node is updated by 1 at each cycle.<br>
	 * <br>
	 * See 088_1negQloop1posQloop1Shared.cstn as test case where the update of each node has to be done following the
	 * update of each obs node and each obs node is updated by 1 at each cycle.
	 *
	 * @param node  the node to which the labeled value has been added
	 * @param l     a label
	 * @param reset true if the count has to be reset to 1.
	 *
	 * @return the value associate to label l after the update. If the label does not exist or contains unknown, returns
	 * 	0. In case of reset, returns 1.
	 */
	public static int updatePotentialCount(LabeledNode node, Label l, boolean reset) {
		if (l == null || node == null) {
			return 0;
		}
		int i = node.getLabeledPotentialCount(l);
		i = (i == Constants.INT_NULL || reset) ? 1 : i + 1;
		node.setLabeledPotentialCount(l, i);
		return i;
	}

	/**
	 * @return the isUpperBoundRequested
	 */
	@SuppressWarnings("FinalMethod")
	public final boolean isUpperBoundRequested() {
		return isUpperBoundRequested;
	}

	@Deprecated
	public CSTNCheckStatus oneStepDynamicConsistencyByNode() {
		throw new UnsupportedOperationException("Not applicable.");
	}

	/**
	 * @param isUpperBoundRequested1 the isUpperBoundRequested to set
	 */
	@SuppressWarnings("FinalMethod")
	public final void setUpperBoundRequested(boolean isUpperBoundRequested1) {
		isUpperBoundRequested = isUpperBoundRequested1;
		if (isUpperBoundRequested1) {
			// if also UpperBounds are request, then propagationOnlyToZ must be false
			// and horizon must be calculated accordingly (coreCheckAndInit manages this).
			propagationOnlyToZ = false;
		}
	}

	@Override
	CSTNCheckStatus dynamicConsistencyCheckWOInit() {
		if (!checkStatus.initialized) {
			throw new IllegalStateException(
				"TNGraph<CSTNEdge> has not been initialized! Please, consider dynamicConsistencyCheck() method!");
		}

		final LabeledNode[] allNodes = g.getVerticesArray();

		NodesToCheck nodesToCheck = new NodesToCheck();
		final LabeledNode Z = g.getZ();
		assert Z != null;
		nodesToCheck.enqueue(Z);
		Z.putLabeledPotential(Label.emptyLabel, 0);

		NodesToCheck obsNodesInvolved;

		int i = 1;
		final Instant startInstant = Instant.now();
		final Instant timeoutInstant = startInstant.plusSeconds(timeOut);

		// First of all, checks the DC controllability considering only distances to Z
		while (checkStatus.consistency && !checkStatus.finished && !checkStatus.timeout) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.log(Level.FINE, "*** Start Main Cycle " + i + " ***");
				}
			}
			checkStatus.cycles++;

			// backward propagates all minimal distances to Z.
			obsNodesInvolved = singleSinkShortestPaths(nodesToCheck, timeoutInstant);
			// ASSERTIONS
			// nodesToCheck is empty
			// obsNodesToCheck may be not empty

			if (!checkStatus.consistency || checkStatus.timeout || obsNodesInvolved == null) {
				break;
			}

			if (obsNodesInvolved.isEmpty()) {
				checkStatus.finished = true;
				break;
			}

			// if there are one or more modified observation node, then ALL nodes distances must be checked by rule R3
			// for possibly simplifying the labels of potentials.
			nodesToCheck = potentialR3(allNodes, obsNodesInvolved, timeoutInstant);

			if (!checkStatus.consistency || checkStatus.timeout || nodesToCheck == null) {
				break;
			}

			if (nodesToCheck.isEmpty()) {
				checkStatus.finished = true;
				break;
			}

			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.log(Level.FINE, "*** End Main Cycle " + i + " ***\n\n");
				}
			}
			i++;
		} // end checking

		if (checkStatus.timeout) {
			if (Debug.ON) {
				final String msg = "During the check # " + i + ", " + timeOut + " seconds timeout occurred. ";
				if (LOG.isLoggable(Level.INFO)) {
					LOG.log(Level.INFO, msg);
				}
			}
			saveGraphToFile();
			checkStatus.executionTimeNS = ChronoUnit.NANOS.between(startInstant, Instant.now());
			return checkStatus;
		}

		if (isUpperBoundRequested) {
			// Then determine upper bounds for all time-points
			// LabeledUpperBound has been initialized in #initAndCheck

			checkStatus.finished = false;
			nodesToCheck =
				new NodesToCheck(g.getVertices());// all nodes (but Z) have already upper potential = horizon.
			// Z has 0. It is not possible to start only from Z as the algorithm required, because it is possible
			// that no nodes is update (for example, all nodes has reachable from Z with edge having horizon value).
			// Z.putLabeledUpperPotential(Label.emptyLabel, 0); already done in init!
			// nodesToCheck.enqueue(Z);

			obsNodesInvolved = new NodesToCheck();
			i = 0;
			do {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.log(Level.FINE, "*** Start upper potential update cycle " + i + " ***");
					}
				}
				checkStatus.cycles++;

				// propagates distances from Z simplifying label if destination node is an observation one.
				nodesToCheck = singleSourceShortestPaths(nodesToCheck, obsNodesInvolved, timeoutInstant);

				// simplifies label in new labeled distances
				nodesToCheck = upperPotentialR3(nodesToCheck, obsNodesInvolved, timeoutInstant);

				checkStatus.finished = nodesToCheck.isEmpty();

				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.log(Level.FINE, "*** End upper potential update cycle " + i + " ***\n\n");
					}
				}
				i++;
				obsNodesInvolved.clear();
			} while (!checkStatus.finished);
		}

		final Instant endInstant = Instant.now();
		checkStatus.executionTimeNS = Duration.between(startInstant, endInstant).toNanos();

		if (!checkStatus.consistency) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.log(Level.INFO,
					        "After " + (i - 1) + " cycle, found an inconsistency.\nStatus: " + checkStatus);
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.log(Level.FINEST, "Final inconsistent graph: " + g);
					}
				}
			}
			saveGraphToFile();
			return checkStatus;
		}

		// consistent && finished
		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO,
				        "Stable state reached. Number of cycles: " + (i - 1) + ".\nStatus: " + checkStatus);
			}
		}
		gCheckedCleaned = new TNGraph<>(g.getName(), g.getEdgeImplClass());
		if (cleanCheckedInstance) {
			gCheckedCleaned.copyCleaningRedundantLabels(g);
		}
		saveGraphToFile();
		return checkStatus;
	}

	@Deprecated
	boolean labelModificationR3qR3(final LabeledNode nS, final LabeledNode nD,
	                               final CSTNEdge eSD) {
		throw new UnsupportedOperationException("labelModificationR3qR3");
	}

	@Override
	@Deprecated
	CSTNCheckStatus oneStepDynamicConsistencyByEdges(final EdgesToCheck<CSTNEdge> edgesToCheck,
	                                                 Instant timeoutInstant) {
		throw new UnsupportedOperationException("oneStepDynamicConsistencyByEdges");
	}

	// * If (u+v) is the n+1 update of labeled value associated to γ=αβ, then a negative circuit is present and the check is over.
	// * If (u+v) is the n+1 update of labeled value associated to γ=α*β, then a negative q-loop is present and the
	// value (u+v) is set to -∞.

	@Override
	@Deprecated
	CSTNCheckStatus oneStepDynamicConsistencyByEdgesLimitedToZ(
		EdgesToCheck<CSTNEdge> edgesToCheck, Instant timeoutInstant) {
		throw new UnsupportedOperationException("oneStepDynamicConsistencyByEdgesLimitedToZ");
	}

	/**
	 * Apply rule R3 to potentials of each node in nodesToCheck.
	 *
	 * <pre>
	 * if P?_[u,α]  and X_[v,βp]
	 * then adds
	 * X_[max(u,v), α*β]
	 * A^[...] represents the upper labeled potential, while A_[...] represents the (lower) labeled potential.
	 * </pre>
	 *
	 * @param nodesToCheck    the set of nodes that has to be checked w.r.t. the obs node in obsNodesToCheck. It must be
	 *                        not null.
	 * @param obsNodesToCheck the input set of observation time point to consider. It must be not null.
	 * @param timeoutInstant  time out instant to not overlap. It must be not null.
	 *
	 * @return the set of nodes whose at least one labeled distance was modified, null if an inconsistency or a timeout
	 * 	occurred.
	 */
	@Nullable
	NodesToCheck potentialR3(final LabeledNode[] nodesToCheck, final NodesToCheck obsNodesToCheck,
	                         Instant timeoutInstant) {
		final NodesToCheck newNodesToCheck;

		// First of all, check all obs node among them in order to have the minimum common potential among obs t.p.
		newNodesToCheck = potentialR3internalCycle(obsNodesToCheck.toArray(), new NodesToCheck(obsNodesToCheck), true,
		                                           timeoutInstant);

		if (!checkStatus.consistency || checkStatus.timeout || newNodesToCheck == null) {
			return null;
		}

		obsNodesToCheck.addAll(newNodesToCheck);
		// Secondly, check the nodesToCheck w.r.t. obs nodes
		final NodesToCheck newNodesToCheckII =
			potentialR3internalCycle(nodesToCheck, obsNodesToCheck, false, timeoutInstant);

		if (!checkStatus.consistency || checkStatus.timeout || newNodesToCheckII == null) {
			return null;
		}

		newNodesToCheck.addAll(newNodesToCheckII);
		return newNodesToCheck;
	}

	/**
	 * Executes single-sink BellmanFord algorithm propagating from each in {@code nodesToCheck} until no new distances
	 * are determined. The relaxation step is:
	 *
	 * <pre>
	 * A_[u,α] &lt;---(v,β)---B
	 * adds
	 * B_[(u+v),γ]
	 * where γ=α*β if v&lt;0, γ=αβ otherwise and (u+v) &lt; possibly previous value.
	 * Instantaneous reaction semantics is assumed.
	 * A^[...] represents the upper labeled potential, while A_[...] represents the (lower) labeled potential.
	 * </pre>
	 * <p>
	 * This method modified {@code this.checkStatus}: check it for verifying if a negative cycle or a timeout occurred.
	 *
	 * @param nodesToCheck   input set of nodes
	 * @param timeoutInstant time instant limit allowed to the computation.
	 *
	 * @return the set of observation nodes that have proposition in at least one labeled potential modified, null if a
	 * 	negative cycle or a timeout occurred.
	 */
	@Nullable
	NodesToCheck singleSinkShortestPaths(final NodesToCheck nodesToCheck, Instant timeoutInstant) {
		LabeledNode B;
		final NodesToCheck obsNodesToCheck = new NodesToCheck();
		/*
		 * When a new labeled value is added in a potential, then it has to be checked w.r.t. all observation potentials for verifying whether
		 * it can be simplified. obsModified maintains footprint of all proposition involved in the new added labels.
		 */
		Label obsInvolved = Label.emptyLabel;
		while (!nodesToCheck.isEmpty()) {
			final LabeledNode A = nodesToCheck.dequeue();
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.log(Level.FINE, "\nConsidering node " + A.getName());
				}
			}
			// cache
			final LabeledIntMap APotential = A.getLabeledPotential();
			final ObjectSet<Label> APotentialLabel = APotential.keySet();

			for (final CSTNEdge AB : g.getInEdges(A)) {
				B = g.getSource(AB);
				final ObjectSet<Entry<Label>> ABEntrySet = AB.getLabeledValueSet();
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						assert B != null;
						LOG.log(Level.FINEST,
						        "*** Considering " + A.getName() + " <--" + ABEntrySet + "-- " + B.getName());
					}
				}
				boolean isBModified = false;

				for (final Entry<Label> ABEntry : ABEntrySet) {
					final int v = ABEntry.getIntValue();
					final Label beta = ABEntry.getKey();
					for (final Label alpha : APotentialLabel) {
						final int u = APotential.get(alpha);
						final int newValue = Constants.sumWithOverflowCheck(u, v);
						if (newValue >= 0 || (newValue == Constants.INT_NEG_INFINITE && v > 0)) {
							continue;// only non-positive values are interesting and -∞ through positive edges cannot be propagated
						}
						final Label newLabel =
							(v < 0) ? alpha.conjunctionExtended(beta) : alpha.conjunction(beta);// IR assumed.
						final String log;
						if (newLabel != null) {
							assert B != null;
							log = A.getName() + "_[" + pairAsString(alpha, u) + "]<--" + pairAsString(beta, v) + "--"
							      + B.getName();
							if (updatePotential(B, newLabel, newValue, false, log + "\n")) {
								isBModified = true;
								obsInvolved = obsInvolved.conjunctionExtended(newLabel);
								checkStatus.labeledValuePropagationCalls++;
								if (!checkStatus.consistency) {
									return null;
								}
							}
						}
					}
				}
				if (isBModified) {
					nodesToCheck.enqueue(B);
					if (B.isObserver()) {
						obsNodesToCheck.enqueue(B);
					}
				}
				if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
					return null;
				}
			} // end for edges outgoing from A
		}

		// add all obs node that have to be checked.
		for (final char p : obsInvolved.getPropositions()) {
			obsNodesToCheck.enqueue(g.getObserver(p));
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "Observation nodes involved by new labeled values: " + obsNodesToCheck);
			}
		}
		return obsNodesToCheck;
	}

	/**
	 * Executes the single-source BellmanFord algorithm for each node in {@code nodesToCheck}. The relax operation is
	 * defined as:
	 *
	 * <pre>
	 * A^[u,α] ---(v,β)---&gt; B
	 * adds
	 * B^[(u+v),γ]
	 * where γ=αβ.
	 *
	 * The DC semantics is IR (instantaneous reaction).
	 * A^[...] represents the upper labeled potential, while A_[...] represents the (lower) labeled potential.
	 * </pre>
	 * <p>
	 * Moreover, this method updates the statistics in {@code this.checkStatus}.
	 *
	 * @param nodesToCheck    input set of nodes to check.
	 * @param obsNodesToCheck at the end of computation, it contains the observation nodes that have their propositions
	 *                        in new labeled values.
	 * @param timeoutInstant  time instant limit allowed to the computation.
	 *
	 * @return the set of node whose at least one distance has been modified.
	 */
	NodesToCheck singleSourceShortestPaths(final NodesToCheck nodesToCheck, final NodesToCheck obsNodesToCheck,
	                                       Instant timeoutInstant) {
		LabeledNode B;
		/*
		 * When a new labeled value is added in the upper potential, then it has to checked w.r.t. all observation potentials for verifying whether
		 * it can be simplified. obsModified maintains footprint of observation t.p. involved in the new added values.
		 */
		final CharOpenHashSet obsInvolved = new CharOpenHashSet();

		final NodesToCheck modifiedNodes = new NodesToCheck();

		while (!nodesToCheck.isEmpty()) {
			final LabeledNode A = nodesToCheck.dequeue();
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.log(Level.FINE, "Considering node " + A.getName());
				}
			}
			// cache
			final LabeledIntMap APotential = A.getLabeledUpperPotential();
			final ObjectSet<Label> APotentialLabel = APotential.keySet();

			for (final CSTNEdge AB : g.getOutEdges(A)) {// only outgoing edges!
				B = g.getDest(AB);
				final ObjectSet<Entry<Label>> ABEntrySet = AB.getLabeledValueSet();
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						assert B != null;
						LOG.log(Level.FINEST,
						        "*** Considering " + A.getName() + "---" + ABEntrySet + "-->" + B.getName());
					}
				}
				boolean isBModified = false;

				for (final Entry<Label> ABEntry : ABEntrySet) {
					final int v = ABEntry.getIntValue();
					final Label beta = ABEntry.getKey();
					for (final Label alpha : APotentialLabel) {
						final int u = APotential.get(alpha);
						final int newValue = Constants.sumWithOverflowCheck(u, v);
						if (newValue >= horizon) {
							continue;// only below horizon values are interesting
						}
						final Label newLabel = alpha.conjunction(beta);// IR assumed.

						final String log;
						if (newLabel != null) {
							assert B != null;
							log = A.getName() + "^[" + pairAsString(alpha, u) + "]--" + pairAsString(beta, v) + "-->"
							      + B.getName() + "\n";
							if (updateUpperPotential(B, newLabel, newValue, false, obsInvolved, log)) {
								isBModified = true;
								checkStatus.labeledValuePropagationCalls++;
							}
						}
					}
				}
				if (isBModified) {
					nodesToCheck.enqueue(B);
					modifiedNodes.enqueue(B);
				}
				if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
					return modifiedNodes;
				}
			} // end for edges outgoing from A
		}

		// add all obs node that have to be checked.
		for (final char p : obsInvolved) {
			obsNodesToCheck.enqueue(g.getObserver(p));
		}
		return modifiedNodes;
	}

	/**
	 * Apply R3 to the upper potential of each node in nodesToCheck.
	 *
	 * <pre>
	 * if P?_[u,α] and X^[v,βp]
	 * then adds
	 * X^[v, αβ] if (v &lt; -u)
	 *
	 * A^[...] represents the upper labeled potential, while A_[...] represents the (lower) labeled potential.
	 * </pre>
	 *
	 * @param nodesToCheck     the set of nodes that has to be checked w.r.t. the obs node in obsNodesToCheck.
	 * @param obsNodesInvolved the input set of observation time point to consider.
	 * @param timeoutInstant   time out instant to not overlap
	 *
	 * @return nodes whose distance-labels were modified by this method.
	 */
	NodesToCheck upperPotentialR3(final NodesToCheck nodesToCheck, final NodesToCheck obsNodesInvolved,
	                              Instant timeoutInstant) {
		final NodesToCheck newNodesToCheck = new NodesToCheck();
		String log = "";
		// Considers one observation node at cycle and adjusts all labeled values in nodesToCheck w.r.t. the distance
		// of the current observation node.
		while (!obsNodesInvolved.isEmpty()) {
			final LabeledNode obs = obsNodesInvolved.dequeue();
			final char p = obs.getPropositionObserved();
			final ObjectSet<Entry<Label>> obsDistanceEntrySet =
				obs.getLabeledPotential().entrySet();// for obs node, minimal potential must be considered!

			// Scans all nodes to check
			for (final LabeledNode node : nodesToCheck) {
				if (node == obs) {
					continue;
				}
				// Checks and fixes labeled potentials
				for (final Label betap : node.getLabeledUpperPotential().keySet()) {
					final int nodeDistanceValue = node.getLabeledUpperPotential(betap);
					if (nodeDistanceValue == Constants.INT_NULL || !betap.contains(p))
					// v can be int_null if it has been removed by a merge labeled value in a previous cycle
					{
						continue;
					}
					final Label beta = betap.remove(p);
					// now check the current (v, beta) of the node w.r.t. all consistent labeled distances in observation node.
					for (final Entry<Label> obsDistanceEntry : obsDistanceEntrySet) {
						final Label alpha = obsDistanceEntry.getKey();
						final Label alphaBeta = alpha.conjunction(beta);
						if (alphaBeta == null) {
							continue;
						}
						final int obsDistanceValue = obsDistanceEntry.getIntValue();
						if (nodeDistanceValue > -obsDistanceValue) {
							continue;
						}

						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								log = "Potential R3 applied to " + obs.getName() + " and " + node.getName()
								      + ":\nsource: " + obs.getName() + "_[" + pairAsString(alpha, obsDistanceValue)
								      + "] " + node.getName() + "^[" + pairAsString(betap, nodeDistanceValue) + "]\n";
							}
						}
						if (updateUpperPotential(node, alphaBeta, nodeDistanceValue, true, null, log)) {
							checkStatus.r3calls++;
							newNodesToCheck.enqueue(
								node);// in any case a modified obsNode has to be rechecked using getDistanceBellmanFord method.
						}
					}

				}
			}
			if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
				return newNodesToCheck;
			}
		}
		return newNodesToCheck;
	}

	/**
	 * Checks the potential of each node X in {@code nodesToCheck} w.r.t. each node in {@code obsNodes}.<br> If the
	 * potential of X is modified, X is added to {@code newNodesToCheck}.<br> If {@code obsAlignment} is true, then it
	 * assumed that {@code nodesToCheck} contains obs nodes only, and such nodes has to be aligned among them.
	 * Therefore, the rule is applied considering only obs nodes until no potential is modified.
	 *
	 * @param nodesToCheck   the set of nodes to check
	 * @param obsNodes       the observation node to which all nodes have to be checked
	 * @param obsAlignment   if true, then it assumed that {@code nodesToCheck} contains obs nodes only and such nodes
	 *                       has to be aligned among them.
	 * @param timeoutInstant maximum allowed time for the check
	 *
	 * @return the set of nodes whose at least one label in their labeled potentials has been modified, null if a
	 * 	negative cycle or a timeout occurred.
	 */
	@Nullable
	private NodesToCheck potentialR3internalCycle(final LabeledNode[] nodesToCheck, final NodesToCheck obsNodes,
	                                              boolean obsAlignment, Instant timeoutInstant) {
		String log = "";
		final NodesToCheck newNodesToCheck = new NodesToCheck();
		// Considers one observation node at cycle and adjusts all labeled values in nodesToCheck w.r.t. the distance
		// of the current observation node.
		while (!obsNodes.isEmpty()) {
			final LabeledNode obs = obsNodes.dequeue();
			final char p = obs.getPropositionObserved();
			final ObjectSet<Entry<Label>> obsDistanceEntrySet = obs.getLabeledPotential().entrySet();

			// Scans all nodes to check
			for (final LabeledNode node : nodesToCheck) {
				if (node == obs || (!obsAlignment && node.isObserver())) {
					continue;
				}
				int minNodeValue = node.getLabeledPotential(Label.emptyLabel);
				if (minNodeValue == Constants.INT_NULL) {
					minNodeValue = Constants.INT_POS_INFINITE;
				}
				// Checks and fixes labeled potentials
				for (final Label betap : node.getLabeledPotential().keySet()) {
					final int nodeDistanceValue = node.getLabeledPotential(betap);
					if (nodeDistanceValue == Constants.INT_NULL || !betap.contains(p))
					// v can be int_null if it has been removed by a merge labeled value in a previous cycle
					{
						continue;
					}
					final Label beta = betap.remove(p);
					// now check the current (v, beta) of the node w.r.t. all consistent labeled distances in observation node.
					for (final Entry<Label> obsDistanceEntry : obsDistanceEntrySet) {
						final Label alpha = obsDistanceEntry.getKey();
						final int obsDistanceValue = obsDistanceEntry.getIntValue();
						if (obsDistanceValue >= minNodeValue)
						// minNodeValue is always > nodeDistanceValue
						{
							continue;
						}
						final int max = newValueInR3qR3(nodeDistanceValue, obsDistanceValue);
						final Label alphaBeta = alpha.conjunctionExtended(beta);

						// if (this.withNodeLabels) {
						// alphaBeta = removeChildrenOfUnknown(alphaBeta);
						// }
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								log = "Potential R3 applied to " + obs.getName() + " and " + node.getName()
								      + ":\nsource: " + obs.getName() + "[" + pairAsString(alpha, obsDistanceValue)
								      + "] " + node.getName() + "[" + pairAsString(betap, nodeDistanceValue) + "]\n";
							}
						}
						if (updatePotential(node, alphaBeta, max, true, log)) {
							checkStatus.r3calls++;
							if (obsAlignment)// if an alignment among observation node is required, then re-enqueue the current obs node to the queue.
							{
								obsNodes.enqueue(node);
							}
							newNodesToCheck.enqueue(
								node);// in any case a modified obsNode has to be rechecked using getDistanceBellmanFord method.
							if (!checkStatus.consistency) {
								return null;
							}
						}
					}
				}
			}
		}
		if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
			return null;
		}
		return newNodesToCheck;
	}

	/**
	 * Adds the labeled value {@code (value, label)} in the upper potential of {@code node}.<br> If {@code node} is an
	 * observation t.p., then {@code label} is cleaned removing a possible literal related to the observation t.p..<br>
	 *
	 * @param node     the considered node
	 * @param newLabel the new label
	 * @param newValue it is assumed that it is != {@link Constants#INT_NULL}
	 * @param fromR3   true if the value comes from R3 rule
	 * @param log      the log message to complete.
	 *
	 * @return true if the value was added.
	 */
	@SuppressWarnings("ParameterCanBeLocal")
	private boolean updateUpperPotential(LabeledNode node, Label newLabel, int newValue, boolean fromR3,
	                                     CharOpenHashSet obsInvolved, String log) {

		if (newValue >= horizon) {
			return false;
		}

		if (node.isObserver()) {
			newLabel = newLabel.remove(node.getPropositionObserved());
		}

		final int currentValue = node.getLabeledUpperPotential(newLabel);

		if (node.putLabeledUpperPotential(newLabel, newValue)) {
			if (Debug.ON) {
				if (fromR3) {
					log += "R3 ";
				}
				log += "Update upper potential on " + node.getName() + ": " + pairAsString(newLabel, currentValue)
				       + " replaced by " + pairAsString(newLabel, newValue) + "\n";// + ". Update #" + count;
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, log);
				}
			}
			if (currentValue == Constants.INT_NULL && obsInvolved != null) {
				// the new label is not present, then, it must be considered for simplification by #upperPotentialR3
				for (final char l : newLabel.getPropositions()) {
					obsInvolved.add(l);
				}
			}
			checkStatus.potentialUpdate++;
			return true;
		}
		return false;
	}

}
