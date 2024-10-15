// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

package it.univr.di.cstnu.algorithms;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.AbstractObject2IntMap.BasicEntry;
import it.unimi.dsi.fastutil.objects.*;
import it.univr.di.Debug;
import it.univr.di.cstnu.algorithms.STN.STNCheckStatus;
import it.univr.di.cstnu.graph.Edge.ConstraintType;
import it.univr.di.cstnu.graph.*;
import it.univr.di.cstnu.graph.STNUEdge.CaseLabel;
import it.univr.di.cstnu.util.ExtendedPriorityQueue;
import it.univr.di.cstnu.util.ExtendedPriorityQueue.Status;
import it.univr.di.cstnu.util.ObjectPair;
import it.univr.di.cstnu.visualization.CSTNUStaticLayout;
import it.univr.di.labeledvalue.ALabelAlphabet.ALetter;
import it.univr.di.labeledvalue.Constants;
import it.univr.di.labeledvalue.Label;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.args4j.*;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Simple class to represent and consistency-check Simple Temporal Network with Uncertainty (STNU), where the edge weight are signed integer.
 *
 * @author Roberto Posenato
 * @version $Rev: 896 $
 */
public class STNU {

	/**
	 * Possible DC checking algorithm
	 */
	public enum CheckAlgorithm {
		/**
		 * Original FD_STNU improved by removing waits with values greater than the maximum duration of the relative contingent link.
		 */
		FD_STNU_IMPROVED,
		/**
		 * RUL2021 + dispatchable phase
		 */
		FD_STNU,
		/**
		 * Morris cubic algorithm
		 */
		Morris2014Dispatchable,
		/**
		 * Morris cubic algorithm
		 */
		Morris2014,
		/**
		 * Cairo, Rizzi and Hunsberger RUL^- algorithm
		 */
		RUL2018,
		/**
		 * Luke's version of RUL^- algorithm
		 */
		RUL2021,
		/**
		 * NegCycleSTNU is the RUL2021 algorithm that also returns the negative cycle when the instance is not DC.
		 */
		SRNCycleFinder
	}

	/**
	 * Status for 1) negative nodes in Morris 2014 algorithm or 2) contingent edges in RUL2020.
	 *
	 * @author posenato
	 */
	private enum ElementStatus {
		/**
		 * un-started MUST be the first element of this enum!
		 */
		unStarted, halfFinished, finished, started
	}

	/**
	 * Simple class to represent the status of the checking algorithm during an execution.
	 *
	 * @author Roberto Posenato
	 */
	@SuppressFBWarnings("SE_TRANSIENT_FIELD_NOT_RESTORED")
	public static class STNUCheckStatus extends STNCheckStatus {
		/**
		 * Indicates which kind of edges determine a Semi Reducible Negative Cycle (SRNC)
		 */
		public enum SRNCEdges {
			/**
			 * only ordinary edges
			 */
			ordinary,
			/**
			 * ordinary and lower case edges
			 */
			ordinaryLC,
			/**
			 * ordinary lower- and upper-case
			 */
			all
		}
		// controllability == super.consistency!

		/**
		 * Kinds of negative cycles
		 */
		public enum SRNCKind {
			/**
			 * Negative cycle was found in the LO-Graph. Potential function cannot be found.
			 */
			loGraphPotFailure,
			/**
			 * Negative cycle was found solving a CC loop because LC can be bypassed.
			 */
			ccLoop,
			/**
			 * Negative cycle was found because the propagation interruption sequence went back to a previous contingent node.
			 */
			interruptionCycle
		}

		/**
		 * Represents some statistics about a Semi Reducible Negative Cycle (SRNC)
		 *
		 * @param srnc              the negative semi-reducible cycle (SRNC).
		 * @param value             the value of the SRNC.
		 * @param length            the number of edges forming the expanded SRNC.
		 * @param edgeType          kinds of edges forming the SRNC:
		 * @param simple            true if the SRNC does NOT contain any derived edge.
		 * @param srnExpanded       the SRNC expanded. This version of SRNC is made only by original edges of the network. Therefore, tt is possible that some
		 *                          edges are present more times.
		 * @param maxEdgeRepetition in case of an expanded SRNC, the maximum number of presence of the same edge.
		 * @param lowerCaseCount    hash map that given a contingent node, returns how many times its lower case edge is present in the expanded SRNC.
		 * @param upperCaseCount    hash map that given a contingent node, returns how many times its upper case edge is present in the expanded SRNC.
		 */
		@SuppressFBWarnings("EI_EXPOSE_REP2")
		public record SRNCInfo(ObjectImmutableList<STNUEdge> srnc, int value, int length, SRNCKind srncKind, SRNCEdges edgeType, boolean simple,
		                       ObjectImmutableList<STNUEdge> srnExpanded, int maxEdgeRepetition,
		                       Object2IntMap<LabeledNode> lowerCaseCount, Object2IntMap<LabeledNode> upperCaseCount) {}

		/**
		 *
		 */
		static public final long serialVersionUID = 1L;

		/**
		 * Which algorithm was used to check the network last time.
		 */
		CheckAlgorithm checkAlgorithm;

		/**
		 * maxMin constraint added;
		 */
		int maxMinConstraint;
		/**
		 * Negative STNU cycle. This field may represent a simple negative cycle made of ordinary constraints or a semi-reducible negative cycle, i.e., a
		 * negative cycle also containing lower-case or upper-case edges.
		 */
		STNUPath negativeSTNUCycle;


		/**
		 * Kind of SRNC
		 */
		SRNCKind srncKind;

		/**
		 * It maintains, for each edge (key), the STNUPath that determined the edge.
		 */
		transient Object2ObjectOpenHashMap<STNUEdge, STNUPath> edgePathAnnotation;

		/**
		 * Default constructor
		 */
		public STNUCheckStatus() {
			super();
			this.checkAlgorithm = null;
			this.maxMinConstraint = 0;
			this.negativeSTNUCycle = null;
			this.edgePathAnnotation = new Object2ObjectOpenHashMap<>();
			srncKind = null;
		}

		/**
		 * copy constructor only used in benchmarks
		 */
		STNUCheckStatus(STNUCheckStatus in) {
			super(in);
			if (in == null) {
				return;
			}
			this.checkAlgorithm = in.checkAlgorithm;
			this.maxMinConstraint = 0;
			this.negativeSTNUCycle = in.negativeSTNUCycle;
			this.edgePathAnnotation = in.edgePathAnnotation;
			srncKind = in.srncKind;
		}

		/**
		 * @return the name of the algorithm used for the checking.
		 */
		public CheckAlgorithm getCheckAlgorithm() {
			return checkAlgorithm;
		}

		/**
		 * @param checkAlg the name of the algorithm used for the checking.
		 */
		public void setCheckAlgorithm(CheckAlgorithm checkAlg) {
			this.checkAlgorithm = checkAlg;
		}

		/**
		 * @return the sequence of edges that determine the given edge.
		 */
		public final Object2ObjectMap<STNUEdge, ObjectList<STNUEdge>> getEdgePathAnnotation() {
			final Object2ObjectMap<STNUEdge, ObjectList<STNUEdge>> newMap = new Object2ObjectOpenHashMap<>();
			for (final Entry<STNUEdge, STNUPath> entry : this.edgePathAnnotation.entrySet()) {
				final STNUEdge e = entry.getKey();
				final STNUPath path = entry.getValue();
				if (path.size() != 0) {
					newMap.put(e, new ObjectArrayList<>(path.get()));
				}
			}
			return newMap;
		}

		/**
		 * @return number of max min constraints added by applyMinDispatchESTNU.
		 */
		public final int getMaxMinConstraint() {
			return maxMinConstraint;
		}

		/**
		 * @param expanded true if the all derived edge must be expanded in order to have the negative cycle that is made only by original edges. If true, the
		 *                 expanded SRNC is stored in {@link SRNCInfo#srnExpanded}.
		 * @param g        the network. If null, the two hash maps that return the number of times that lowercase and upper case edges are present will be not
		 *                 determined.
		 *
		 * @return the negative cycle if it exists, null otherwise
		 */
		public SRNCInfo getNegativeSTNUCycleInfo(boolean expanded, @Nullable TNGraph<STNUEdge> g) {
			if (this.negativeSTNUCycle == null) {
				return null;
			}

			int pathValue = 0, length = 0, maxEdgeRepetition = 0, expandedPathValue = 0;
			SRNCEdges type = SRNCEdges.ordinary;
			boolean simple = true;
			final ObjectArrayList<STNUEdge> expandedPath = new ObjectArrayList<>();
			final Object2IntMap<STNUEdge> repetition = new Object2IntOpenHashMap<>();
			repetition.defaultReturnValue(0);

			final Object2IntMap<LabeledNode> lowerCaseCount = new Object2IntOpenHashMap<>();
			lowerCaseCount.defaultReturnValue(0);
			final Object2IntMap<LabeledNode> upperCaseCount = new Object2IntOpenHashMap<>();
			upperCaseCount.defaultReturnValue(0);

			for (final STNUEdge edge : this.negativeSTNUCycle.get()) {
				length++;
				final int edgeValue;
				final boolean lowerCase;

				if ((lowerCase = edge.isLowerCase()) || edge.isUpperCase()) {
					edgeValue = edge.getLabeledValue();
					if (g != null) {
						final LabeledNode ctg = (lowerCase) ? g.getDest(edge) : g.getSource(edge);
						assert ctg != null;
						if (lowerCase) {
							lowerCaseCount.put(ctg, lowerCaseCount.getInt(ctg) + 1);
						} else {
							upperCaseCount.put(ctg, upperCaseCount.getInt(ctg) + 1);
						}
					}
					if (type != SRNCEdges.all) {
						if (lowerCase) {
							type = SRNCEdges.ordinaryLC;
						} else {
							type = SRNCEdges.all;
						}
					}
				} else {
					if (edge.isWait()) {
						edgeValue = edge.getLabeledValue();
					} else {
						edgeValue = edge.getValue();
					}
				}

				pathValue = Constants.sumWithOverflowCheck(pathValue, edgeValue);
				int repetitionValue = repetition.getInt(edge);
				repetitionValue++;
				if (repetitionValue > maxEdgeRepetition) {
					maxEdgeRepetition = repetitionValue;
				}
				repetition.put(edge, repetitionValue);
				final ObjectList<STNUEdge> derivation = this.getEdgePathAnnotation().get(edge);
				if (simple && derivation != null) {
					simple = false;
				}

				if (expanded) {
					if (derivation == null) {
						//the edge has been already evaluated.
						expandedPath.add(edge);
						expandedPathValue = Constants.sumWithOverflowCheck(expandedPathValue, edgeValue);
						continue;
					}
					final ObjectList<STNUEdge> subPath = resolveEdgeDerivation(edge);
					for (final STNUEdge e1 : subPath) {
						final int e1Value;
						final boolean lowerCase1;

						if ((lowerCase1 = e1.isLowerCase()) || e1.isUpperCase()) {
							e1Value = e1.getLabeledValue();
							if (g != null) {
								final LabeledNode ctg = (lowerCase1) ? g.getDest(e1) : g.getSource(e1);
								assert ctg != null;
								if (lowerCase1) {
									lowerCaseCount.put(ctg, lowerCaseCount.getInt(ctg) + 1);
								} else {
									upperCaseCount.put(ctg, upperCaseCount.getInt(ctg) + 1);
								}
							}
							if (type != SRNCEdges.all) {
								if (lowerCase) {
									type = SRNCEdges.ordinaryLC;
								} else {
									type = SRNCEdges.all;
								}
							}
						} else {
							if (e1.isWait()) {
								e1Value = e1.getLabeledValue();
							} else {
								e1Value = e1.getValue();
							}
						}

						expandedPathValue = Constants.sumWithOverflowCheck(expandedPathValue, e1Value);
						repetitionValue = repetition.getInt(e1);
						repetitionValue++;
						if (repetitionValue > maxEdgeRepetition) {
							maxEdgeRepetition = repetitionValue;
						}
						repetition.put(e1, repetitionValue);
						expandedPath.add(e1);
					}
					if (pathValue != expandedPathValue) {
						throw new IllegalStateException(
							"Value of the semi-reducible negative cycle " + pathValue + " is different from the value of its expansion " + expandedPathValue);
					}
				}
			}
			return new SRNCInfo(new ObjectImmutableList<>(this.negativeSTNUCycle.get())
				, pathValue
				, length
				, srncKind
				, type
				, simple
				, new ObjectImmutableList<>(expandedPath)
				, maxEdgeRepetition
				, lowerCaseCount
				, upperCaseCount
			);
		}

		/**
		 * @return the SRNC type
		 */
		public SRNCKind getSrncKind() {
			return srncKind;
		}

		/**
		 * @param srncKind the new kind of SRNC
		 */
		public void setSrncKind(SRNCKind srncKind) {
			this.srncKind = srncKind;
		}

		/**
		 * @return true if the controllability status is true
		 */
		public final boolean isControllable() {
			return consistency;
		}

		/**
		 * Reset all indexes.
		 */
		@Override
		public final void reset() {
			super.reset();
			checkAlgorithm = null;
			maxMinConstraint = 0;
			this.negativeSTNUCycle = null;
			this.edgePathAnnotation.clear();
		}

		/**
		 * @param e the edge to resolve.
		 *
		 * @return the path of edges that determined 'e' if 'e' is derived, 'e' otherwise.
		 */
		public final ObjectList<STNUEdge> resolveEdgeDerivation(STNUEdge e) {
			final ObjectList<STNUEdge> path = new ObjectArrayList<>();
			final ObjectList<STNUEdge> derivation = this.getEdgePathAnnotation().get(e);
			if (derivation != null) {
				for (final STNUEdge derivationEdge : derivation) {
					path.addAll(resolveEdgeDerivation(derivationEdge));
				}
			} else {
				path.add(e);
			}
			return path;
		}

		/**
		 * Sets the controllability value
		 *
		 * @param state new controllability value
		 */
		public final void setControllability(boolean state) {
			consistency = state;
		}

		@Override
		public final String toString() {
			final StringBuilder sb = new StringBuilder(160);
			sb.append("The check is");
			if (!finished) {
				sb.append(" NOT");
			}
			sb.append(" finished");
			if (cycles > 0) {
				sb.append(" after ").append(cycles).append(" cycle(s).\n");
			} else {
				sb.append(".\n");
			}
			if (finished) {
				sb.append("The consistency check has determined that given network is ");
				if (!consistency) {
					sb.append("NOT ");
				}
				sb.append("controllable.\n");

				if (!consistency && negativeSTNUCycle != null) {
					final SRNCInfo SRNCInfo = getNegativeSTNUCycleInfo(true, null);
					assert SRNCInfo != null;
					sb.append("Negative cycle ha value ").append(SRNCInfo.value).append(" and it is: ").append(SRNCInfo.srnc).append("\n");
					if (!SRNCInfo.simple) {
						sb.append("Expanded negative cycle is: ").append(SRNCInfo.srnExpanded).append("\n");
					}
				}
			}
			if (timeout) {
				sb.append("Checking has been interrupted because execution time exceeds the given time limit.\n");
			}

			if (executionTimeNS != Constants.INT_NULL) {
				sb.append("The global execution time has been ").append(executionTimeNS).append(" ns (~").append((executionTimeNS / 1E9)).append(" s.)");
			}
			return sb.toString();
		}
	}

	/**
	 * Represents a path of STNU edge
	 */
	private static class STNUPath implements Serializable {
		static public final long serialVersionUID = 1L;
		/**
		 * Internal representation of the path
		 */
		final ObjectArrayList<STNUEdge> path = new ObjectArrayList<>();

		public final ObjectArrayList<STNUEdge> get() {
			return path;
		}

		/**
		 * @return string representation
		 */
		public final String toString() {
			return path.toString();
		}

		/**
		 * Adds to the end of this path the edge
		 *
		 * @param edge if it is null, it does nothing
		 *
		 * @return this
		 */
		@SuppressWarnings("UnusedReturnValue")
		final STNUPath add(STNUEdge edge) {
			if (edge != null) {
				this.path.add(edge);
			}
			return this;
		}

		/**
		 * Adds to the end of this path the given path.
		 *
		 * @param path if it is null, it does nothing
		 *
		 * @return this
		 */
		final STNUPath add(STNUPath path) {
			if (path != null) {
				this.path.addAll(path.get());
			}
			return this;
		}

		/**
		 * Adds at the position the given path
		 */
		@SuppressWarnings({"SameParameterValue", "UnusedReturnValue"})
		final STNUPath add(int position, STNUPath path) {
			if (path == null) {
				return this;
			}
			this.path.addAll(position, path.get());
			return this;
		}

		/**
		 * Clear current path.
		 */
		final void clear() {
			path.clear();
		}

		/**
		 * Sets the path using the input
		 *
		 * @param edge the edge
		 *
		 * @return this
		 */
		final STNUPath set(STNUEdge edge) {
			this.clear();
			if (edge != null) {
				this.path.add(edge);
			}
			return this;
		}

		/**
		 * @return the number of edge in the path.
		 */
		final int size() {
			return path.size();
		}

		/**
		 * @return the value of the given path.
		 */
		final int value() {
			int sum = 0;
			for (final STNUEdge e : path) {
				sum += (e.isOrdinaryEdge()) ? e.getValue() : e.getLabeledValue();
			}
			return sum;
		}
	}

	/**
	 * Data structure for RUL algorithm.
	 *
	 * @author posenato
	 */
	private static class RULGlobalInfo {
		/**
		 * Map (contingent-node, localInfo) used by rul2021Dispatchable for adding wait constraints after a successful check
		 */
		final Object2ObjectMap<LabeledNode, RULLocalInfo> localInfoOfContingentNodes;

		/**
		 * Potential of each node.
		 */
		Object2IntMap<LabeledNode> nodePotential;

		/**
		 * upperCaseEdgeFromActivation for rul2020OneStepBackProp
		 */
		Object2ObjectMap<LabeledNode, STNUEdge> upperCaseEdgeFromActivation;

		/**
		 * Status of each upperCase edge
		 */
		final Object2ObjectMap<STNUEdge, ElementStatus> upperCaseEdgeStatus;

		/**
		 * InterruptBy maintains, for each UC edge E, the possibile pair (UCEdge E', STNUPath P') where E' is the UC edge that interrupted the bypass procedure
		 * for E ({@link #SRNCycleFinderBackPropagation(STNUEdge, RULGlobalInfo)}. P' is the path used to bypass E'.
		 */
		final Object2ObjectMap<STNUEdge, Pair<STNUEdge, STNUPath>> interruptBy;


		/**
		 * Default constructor
		 *
		 * @param potential       potential determined
		 * @param nUpperCaseEdges number of upper case edges present in the network.
		 */
		RULGlobalInfo(Object2IntMap<LabeledNode> potential, int nUpperCaseEdges) {
			nodePotential = potential;
			upperCaseEdgeStatus = new Object2ObjectOpenHashMap<>(nUpperCaseEdges);
			upperCaseEdgeStatus.defaultReturnValue(ElementStatus.unStarted);
			upperCaseEdgeFromActivation = null;
			localInfoOfContingentNodes = new Object2ObjectOpenHashMap<>(nUpperCaseEdges);
			interruptBy = new Object2ObjectOpenHashMap<>(nUpperCaseEdges);
		}
	}

	/**
	 * Data structure for RUL algorithm.
	 *
	 * @author posenato
	 */
	private static class RULLocalInfo {
		/**
		 * CCLoop is true if the loop 'C' to 'C' has length < Delta_C
		 */
		boolean ccLoop;

		/**
		 * Distance from a node to the contingent node associated to this local info.
		 */
		final Object2IntMap<LabeledNode> distanceFromNodeToContingent;

		/**
		 * Edges to check
		 */
		Object2ObjectMap<LabeledNode, STNUEdge> unStartedUCEdges;

		/**
		 * path maintains, for each node, the path to reach the considered UC edge.
		 */
		final Object2ObjectMap<LabeledNode, STNUPath> path;


		/**
		 * Default constructor
		 *
		 * @param defaultDistance default distance when map does not contain the key.
		 */
		@SuppressWarnings("SameParameterValue")
		RULLocalInfo(int defaultDistance) {
			distanceFromNodeToContingent = new Object2IntOpenHashMap<>();
			distanceFromNodeToContingent.defaultReturnValue(defaultDistance);
			ccLoop = false;
			unStartedUCEdges = null;
			path = new Object2ObjectOpenHashMap<>();
		}
	}

	/**
	 * Represents the immutable triple (sourceNode, weight, destinationNode) necessary for generating a STNUEdge.
	 *
	 * @author posenato
	 */
	private static class EdgeData {
		final LabeledNode source;
		final LabeledNode destination;
		final int weight;

		/**
		 * Constructor for an immutable edgeData object.
		 *
		 * @param s the source node
		 * @param d the destination node
		 * @param w the weight of the edge
		 */
		EdgeData(LabeledNode s, LabeledNode d, int w) {
			source = s;
			destination = d;
			weight = w;
		}
	}

	/**
	 * Suffix for file name
	 */
	public static final String FILE_NAME_SUFFIX = ".stnu";
	/**
	 * The name for the initial node.
	 */
	public static final String ZERO_NODE_NAME = "Z";
	/**
	 * Version of the class
	 */
	// static final String VERSIONandDATE = "Version 1.0 - April, 08 2020";
	// static final String VERSIONandDATE = "Version 1.1 - January 19, 2020";// fixed only the nome for the logger.
	// static final String VERSIONandDATE = "Version 1.1.1 - October 13, 2021";// Fixed ExtendedPriorityQueue made generics
	// static final String VERSIONandDATE = "Version 1.2 - January 13, 2022";// added fastSTNUDispatchability method. Changed the name RUL2020 in RUL2021.
	// static final String VERSIONandDATE = "Version 1.2.1 - January 16, 2023";//tweaking
	// static final String VERSIONandDATE = "Version 2.0.1 - December 27, 2023";//Added minDispatchESTNU
	static final String VERSIONandDATE = "Version 2.1.0 - April 12, 2024";//Added NegCycleSTNU algorithm

	/**
	 * Logger of the class.
	 */
	private static final Logger LOG = Logger.getLogger(STNU.class.getName());
	/**
	 * Patter for the file name suffix.
	 */
	private static final Pattern COMPILE = Pattern.compile(FILE_NAME_SUFFIX + "$");

	/**
	 * Checks if the activation node is already an activation node for another contingent node. In such a case, a WellDefinitionException is thrown because each
	 * activation node must be associated only to a contingent node.
	 *
	 * @param activation        the activation node
	 * @param contingent        the contingent node
	 * @param activationNodeMap the map of already known activation nodes.
	 *
	 * @throws WellDefinitionException if the activation node is already associated to another contingent node.
	 */
	static void CHECK_ACTIVATION_UNIQUENESS(@Nonnull LabeledNode activation, @Nonnull LabeledNode contingent,
	                                        @Nonnull Object2ObjectMap<LabeledNode, LabeledNode> activationNodeMap) throws WellDefinitionException {
		final LabeledNode actNode = activationNodeMap.get(contingent);
		if (actNode != null) {
			LabeledNode otherCtg = null;
			for (final Entry<LabeledNode, LabeledNode> entry : activationNodeMap.entrySet()) {
				if (entry.getValue().equalsByName(activation)) {
					otherCtg = entry.getKey();
					break;
				}
			}
			if (otherCtg == contingent) {
				return;
			}
			throw new WellDefinitionException(
				"Contingent node " + contingent + " has as activation node " + activation + ", that is the activation node of another contingent node " +
				otherCtg + ". This is forbidden. Transform the activation node in a pair of nodes at distance 0 and associate each to a distinct contingent.");
		}
	}

	/**
	 * For each rigid components with two elements at least, determines:
	 * <ol>
	 *  <li>the node that should precede the others as representative, and
	 *  <li>the distances from  the representative and the other nodes of the rigid component.
	 * </ol>
	 * Such information are stored and returned as two maps: one saying the representative for each node,
	 * the other the distance from the representative for each node.
	 * <p>Activation timepoint are preferred in case that there is 0 distance.</p>
	 *
	 * @param rigidComponents   the list of rigid components. Each rigid components is a list of nodes.
	 * @param solution          distances from a source. It used for deciding the representative node in each rigid components: it is the nearest node to the
	 *                          source.
	 * @param Z                 the Z node. If a component contain Z, then Z is set as representative.
	 * @param activationNodeSet the collection of activation timepoints
	 *
	 * @return two maps:
	 * 	<ul>
	 * 	    <li>left map of the pair maps each node to its representative in the rigid component.</li>
	 * 	    <li>right map maps each node to the value of its distance from the representative.
	 * 	    In other words, if R is representative and X is the node, the entry is {@code solution(X)-solution(R)}.
	 * 	    If solution was determined using a fake source, solution(R)&le;solution(X). So, the distance is always non-negative.
	 * 	</ul>
	 * 	Nodes are represented by their name.
	 */
	static Pair<Object2ObjectMap<LabeledNode, LabeledNode>, Object2IntMap<LabeledNode>> GET_REPRESENTATIVE_RIGID_COMPONENTS(
		ObjectList<ObjectList<LabeledNode>> rigidComponents, @Nonnull Object2IntMap<LabeledNode> solution, @Nonnull LabeledNode Z,
		@Nonnull ObjectCollection<LabeledNode> activationNodeSet) {

		final Object2ObjectMap<LabeledNode, LabeledNode> rep = new Object2ObjectArrayMap<>();
		final Object2IntMap<LabeledNode> distances = new Object2IntOpenHashMap<>();
		solution.defaultReturnValue(Constants.INT_POS_INFINITE);

		for (final ObjectList<LabeledNode> rc : rigidComponents) {
			if (rc == null || rc.size() <= 1) {
				continue;
			}
			// The representative is the node nearest to the source. Solution contains the distances from the source.
			LabeledNode representative = rc.getFirst();
			int representativeDistance = Constants.INT_POS_INFINITE;
			final ObjectSortedSet<LabeledNode> activationInRC = new ObjectAVLTreeSet<>();
			for (final LabeledNode node : rc) {
				final int v = solution.getInt(node);
				if (Debug.ON && v == Constants.INT_POS_INFINITE) {
					throw new IllegalStateException("Node " + node + " has no a distance in solution map.");
				}
				if (node.equalsByName(Z)) {
					// Z is the preferred representative
					representative = node;
					representativeDistance = v;
					break;
				}
				if (activationNodeSet.contains(node) && v <= representativeDistance) {
					activationInRC.add(node);
				}
				if (v == representativeDistance) {
					if (node.getName().compareTo(representative.getName()) < 0) {
						representative = node;
					}
				}
				if (v < representativeDistance) {
					representative = node;
					representativeDistance = v;
				}
			}
			//now... if there is activation time point with distance == representativeDistance, prefer the first one
			if (!activationInRC.isEmpty()) {
				for (final LabeledNode node : activationInRC) {
					if (representative == node) {
						break;
					}
					if (solution.getInt(node) == representativeDistance) {
						representative = node;
						break;
					}
				}
			}
			// fill the result maps
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("For rigid component " + rc + ", the representative is " + representative);
				}
			}
			for (final LabeledNode node : rc) {
				rep.put(node, representative);
				distances.put(node, solution.getInt(node) - representativeDistance);
			}
		}
		return new ObjectObjectImmutablePair<>(rep, distances);
	}

	/**
	 * Determines the minimal distance from a virtual source node (added by the method) and each other node (single-source-shortest-path (SSSP)) using the
	 * BellmanFord algorithm.<br> The minimal distance {@code h} is determined considering the ordinary and the lower case values of the input STNU.
	 * <p>
	 * The minimal distance is returned as map (node, value). If the graph contains a negative cycle, it returns null.
	 * <p>
	 * This method implements Algorithm 8 in the Technical Appendix of the AAAI22 paper.
	 * <p>
	 * The Technical Appendix is published at <a href="https://hdl.handle.net/11562/1045707">https://hdl.handle.net/11562/1045707</a>.</p>
	 *
	 * @return the minimal potential if the network is consistent, null otherwise.
	 */
	@Nullable
	static Object2IntMap<LabeledNode> GET_SSSP_BellmanFordOL(TNGraph<STNUEdge> graph, STNUCheckStatus checkStatus1) {
		final Collection<LabeledNode> nodes = graph.getVertices();
		final int n = nodes.size();
		final Collection<STNUEdge> edges = graph.getEdges();
		int v;
		final Object2IntOpenHashMap<LabeledNode> distanceFromSource = new Object2IntOpenHashMap<>(n);
		distanceFromSource.defaultReturnValue(0);
		graph.getVertices().forEach((node) -> distanceFromSource.put(node, 0)); //It must be added to guarantee that
		//if one list such a map, he can meet all the nodes.

		LabeledNode s, d;
		int w;
		for (int i = 1; i < n; i++) {// n-1 rounds
			boolean update = false;
			for (final STNUEdge e : edges) {
				w = STNU.GET_MIN_VALUE_BETWEEN_ORDINARY_AND_LOWERCASE(e);
				if (w == Constants.INT_NULL) {// it is an upper edge
					continue;
				}
				s = graph.getSource(e);
				d = graph.getDest(e);
				/*
				 * We update the potential of source instead of destination because we like positive values instead of negative ones. :-)
				 * If (X,-3,Y) is the edge and X and Y potentials are both 0,
				 * using the standard approach: Y:= X+v = X-3 = 0-3 = -3
				 * using the equivalent inverted update: X:= Y-v = 0+3 = 3
				 */
				v = Constants.sumWithOverflowCheck(distanceFromSource.getInt(d), -w);// -w because we sum using destination potential
				if (distanceFromSource.getInt(s) < v) {
					if (Debug.ON) {
						if (STNU.LOG.isLoggable(Level.FINEST)) {
							assert s != null;
							STNU.LOG.finest("Potential on node " + s.getName() + " from " + Constants.formatInt(distanceFromSource.getInt(s)) + " to " +
							                Constants.formatInt(v));
						}
					}
					distanceFromSource.put(s, v);
					update = true;
				}
			}
			if (!update) {
				return distanceFromSource;
			}
		}
		// check if a negative cycle is present
		for (final STNUEdge e : edges) {
			w = STNU.GET_MIN_VALUE_BETWEEN_ORDINARY_AND_LOWERCASE(e);// OK
			if (w == Constants.INT_NULL) {// it is an upper edge
				continue;
			}
			s = graph.getSource(e);
			d = graph.getDest(e);
			v = Constants.sumWithOverflowCheck(distanceFromSource.getInt(d), -w);
			if (distanceFromSource.getInt(s) < v) {
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINE)) {
						assert s != null;
						STNU.LOG.fine("BF inconsistency:" + s.getName() + " value from " + Constants.formatInt(distanceFromSource.getInt(s)) + " to " +
						              Constants.formatInt(v));
					}
				}
				checkStatus1.setControllability(false);
				checkStatus1.finished = true;
				return null;
			}
		}
		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINEST)) {
				STNU.LOG.finest("BF: potential determined: " + distanceFromSource);
			}
		}
		return distanceFromSource;
	}

	/**
	 * @return the version
	 */
	public static String getVersionAndDate() {
		return VERSIONandDATE;
	}

	/**
	 * @param args an array of {@link String} objects.
	 */
	public static void main(String[] args) {
		final STNU stnu = new STNU();
		System.out.println(stnu.getVersionAndCopyright());
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.info("Start...");
			}
		}
		if (!stnu.manageParameters(args)) {
			return;
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.info("Parameters ok!");
			}
		}
		System.out.println("Starting execution...");
		if (stnu.versionReq) {
			return;
		}

		if (Debug.ON) {
			LOG.info("Loading graph...");
		}
		final TNGraphMLReader<STNUEdge> graphMLReader = new TNGraphMLReader<>();
		try {
			stnu.setG(graphMLReader.readGraph(stnu.fInput, STNUEdgeInt.class));
		} catch (IOException | ParserConfigurationException | SAXException e) {
			throw new RuntimeException(e);
		}
		final float initialEdgeN = stnu.g.getEdgeCount();
		if (Debug.ON) {
			LOG.info("STNU Graph loaded!\nNow, it is time to check it...");
		}

		final STNUCheckStatus status;
		try {
			status = stnu.dynamicControllabilityCheck(stnu.defaultControllabilityCheckAlg);
		} catch (WellDefinitionException e) {
			throw new RuntimeException(e);
		}
//		stnu.applyMinDispatchableESTNU();
		if (status.finished) {
			System.out.println("Checking finished!");
			if (status.isControllable()) {
				System.out.println("The given STNU is dynamic controllable!");
			} else {
				System.out.println("The given STNU is NOT dynamic controllable!");
			}
			System.out.println("Details: " + status);
			// System.out.println("Graph checked: " + stnu.getGChecked());
			System.out.printf("The percentage of added edges: %5.2f%%%n", stnu.g.getEdgeCount() / initialEdgeN * 100);
		} else {
			System.out.println("Checking has not been finished!");
			System.out.println("Details: " + status);
		}
		if (stnu.fOutput != null) {
			System.out.println("Saving the result in file " + stnu.fOutput.getName());
			final TNGraphMLWriter writer = new TNGraphMLWriter(null);
			try {
				writer.save(stnu.g, stnu.fOutput);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	/**
	 * @param edge must be not null
	 *
	 * @return the min value between the ordinary value and the lower-case one. If the edge has only an upper-case value, it returns {@link Constants#INT_NULL}.
	 */
	static private int GET_MIN_VALUE_BETWEEN_ORDINARY_AND_LOWERCASE(@Nonnull STNUEdge edge) {
		// this method is used by BellmanFord LO alg.
		final int lv = (edge.isLowerCase()) ? edge.getLabeledValue() : Constants.INT_NULL;
		final int v = edge.getValue();
		if (v == Constants.INT_NULL) {
			return lv;
		}
		if (lv == Constants.INT_NULL) {
			return v;
		}
		return Math.min(lv, v);
	}

	/**
	 * @param edge must be not null. It is assumed that the network is normalized.
	 *
	 * @return the upper-case value if the edge is an upper-case edge; the ordinary value, otherwise.
	 * 	<p>
	 * 	If the edge has only a lower-case value, it returns {@link Constants#INT_POS_INFINITE}.
	 */
	static private int GET_UPPER_OR_ORDINARY_VALUE(@Nonnull STNUEdge edge) {
		if (edge.isUpperCase()) {
			return edge.getLabeledValue();
		}
		final int v = edge.getValue();
		if (v == Constants.INT_NULL) {
			return Constants.INT_POS_INFINITE;
		}
		return v;
	}

	/**
	 * Updates the distance values between node (V) preceding the source (U) of a negative edge and the destination node (X) of the negative edge.
	 *
	 * @param V        first node
	 * @param valueVU  Assumed to be >= 0.
	 * @param distU    distance from U
	 * @param queue    the queue of node
	 * @param distance the distance map
	 */
	static private void MORRIS2014_UPDATE_DISTANCE(@Nonnull LabeledNode V, int valueVU, int distU, @Nonnull ExtendedPriorityQueue<LabeledNode> queue,
	                                               @Nonnull Object2IntOpenHashMap<LabeledNode> distance) {
		final int newValue = Constants.sumWithOverflowCheck(distU, valueVU);
		if (newValue < distance.getInt(V)) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("Update distance of " + V + " using edge value " + Constants.formatInt(valueVU) + " and distance " + Constants.formatInt(distU) +
					           " of the predecessor of " + V + ". Old value: " + Constants.formatInt(distance.getInt(V)) + " new: " +
					           Constants.formatInt(newValue));
				}
			}
			distance.put(V, newValue);
			assert distance.getInt(V) == newValue;
			queue.insertOrUpdate(V, newValue);
			assert queue.getPriority(V) == newValue;
		}
	}

	/**
	 * @param globalInfo    the global info about the execution of the algorithm. The map interruptBy is the important source of info here.
	 * @param upperCaseEdge the upper case edge bypassing which a negative cycle was discovered.
	 *
	 * @return the negative cycle occurred bypassing the UC edge upperCaseEdge.
	 */
	private static STNUPath SRNCycleFinderBuildNegCycle(STNU.RULGlobalInfo globalInfo, STNUEdge upperCaseEdge) {
		final STNUPath negCycle = new STNUPath();
		if (upperCaseEdge == null) {
			return negCycle;
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Found a negative cycle bypassing " + upperCaseEdge);
			}
		}
		Pair<STNUEdge, STNUPath> entry = globalInfo.interruptBy.get(upperCaseEdge);
		while (!(entry.left().equalsByName(upperCaseEdge))) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("Adding previous path: " + entry.right() + ", that determines edge " + entry.left());
				}
			}
			negCycle.add(0, entry.right());
			entry = globalInfo.interruptBy.get(entry.left());
		}
		negCycle.add(0, entry.right());
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Final negative cycle: " + negCycle);
			}
		}
		return negCycle;
	}

	/**
	 * NegCycleSTNU companion procedure
	 *
	 * @return $NegCycleVar$, if $NegCycleVar$ is empty, $newH$ was updated to satisfy $(U,\delta, V)$ without detecting a negative loop; otherwise
	 * 	$NegCycleVar$ is a negative cycle.
	 */
	private static STNUPath SRNCycleFinderUpdateVal(final STNUEdge eUV, final LabeledNode U, LabeledNode V, int delta, final Object2IntMap<LabeledNode> h,
	                                                final Object2IntMap<LabeledNode> newH, final ExtendedPriorityQueue<LabeledNode> Q,
	                                                Object2ObjectMap<LabeledNode, STNUPath> path) {

		final int possibleNewUDistance = Constants.sumWithOverflowCheck(newH.getInt(V), -delta);
		final int currentUDistance = newH.getInt(U);
		final STNUPath negCycle = new STNUPath();
		if (currentUDistance < possibleNewUDistance) {
			newH.put(U, possibleNewUDistance);
			if (Q.getStatus(U) == Status.wasPresent) {
				negCycle.set(eUV).add(path.get(V));
				return negCycle;
			}
			final int updatedKey4U = Constants.sumWithOverflowCheck(h.getInt(U), -possibleNewUDistance);
			Q.insertOrUpdate(U, updatedKey4U);
			STNUPath newSourcePath = path.get(U);
			if (newSourcePath == null) {
				newSourcePath = new STNUPath();
				path.put(U, newSourcePath);
			}
			newSourcePath.set(eUV).add(path.get(V));
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {LOG.finest("Update generating path for " + U + ": " + newSourcePath);}
			}
		}
		return null;
	}


	/**
	 * Check status.
	 */
	private STNUCheckStatus checkStatus;//it cannot be final because I introduced the copy constructor.
	/**
	 * Map (contingentNode, activationNode) that is built during an {@link #initAndCheck()}
	 */
	private Object2ObjectMap<LabeledNode, LabeledNode> activationNode;
	/**
	 *
	 */
	@Option(name = "-clean", usage = "Output a cleaned result. A cleaned graph does not contain empty edges.")
	private boolean cleanCheckedInstance;
	/**
	 * Represent contingent links also as ordinary constraints.
	 */
	@Option(name = "-contingentAlsoAsOrdinary", usage = "Represent contingent links also as ordinary links.")
	private boolean contingentAlsoAsOrdinary;
	/**
	 * Which algorithm to use for consistency check. Default is Morris2014.
	 */
	@Option(name = "-a", aliases = "--alg", usage = "Which DC checking algorithm to use.")
	private CheckAlgorithm defaultControllabilityCheckAlg;
	/**
	 * The input file containing the STN graph in GraphML format.
	 */
	@Argument(usage = "file_name must be the input STNU graph in GraphML format.", metaVar = "file_name")
	private File fInput;
	/**
	 * Output file where to write the XML representing the minimal STN graph.
	 */
	@Option(name = "-o", aliases = "--output", usage = "Output to this file. If file is already present, it is overwritten. If this parameter is not present, then the output is sent to the std output.", metaVar = "output_file_name")
	private File fOutput;
	/**
	 * Input TNGraph.
	 */
	private TNGraph<STNUEdge> g;
	/**
	 * TNGraph on which to operate.
	 */
	private TNGraph<STNUEdge> gCheckedCleaned;
	/**
	 * Horizon value. A node that has to be executed after such time means that it has not to be executed!
	 */
	private int horizon;
	/**
	 * Utility map that returns the edge containing the lower case constraint of a contingent link given the contingent time point.
	 * <p>
	 * In other words, if there is a contingent link {@code (A, 1, 3, C)}, `lowerContingentEdge` contains {@code C --> (A, c(1), C)}.
	 */
	private Object2ObjectMap<LabeledNode, STNUEdge> lowerContingentEdge;
	/**
	 * Absolute value of the max negative weight determined during initialization phase.
	 */
	private int maxWeight;
	/**
	 *
	 */
	@Option(name = "-save", usage = "Save the checked instance.")
	private boolean save;
	/**
	 * Timeout in seconds for the check.
	 */
	@Option(name = "-t", aliases = "--timeOut", usage = "Timeout in seconds for the check", metaVar = "seconds")
	private int timeOut;
	/**
	 * Utility map that return the edge containing the upper-case value of a contingent link given the contingent timepoint.
	 * <p>
	 * In other words, if there is a contingent link {@code (A, 1, 3, C)}, `upperContingentEdge` contains {@code C --> (C, C:-3, A)}.
	 */
	private Object2ObjectMap<LabeledNode, STNUEdge> upperContingentEdge;
	/**
	 * Software Version.
	 */
	@Option(name = "-v", aliases = "--version", usage = "Version")
	private boolean versionReq;

	/**
	 * @param graph       TNGraph to check. Does not make a copy of graph!
	 * @param giveTimeOut timeout for the check
	 */
	public STNU(TNGraph<STNUEdge> graph, int giveTimeOut) {
		this(graph);
		timeOut = giveTimeOut;
	}

	/**
	 * Does not make a copy of graph!
	 *
	 * @param graph TNGraph to check
	 */
	public STNU(TNGraph<STNUEdge> graph) {
		this();
		setG(graph);// sets also checkStatus!
	}

	/**
	 * Copy constructor, used only for executing benchmarks. Does make a copy of the graph!
	 *
	 * @param in STNU to copy
	 */
	public STNU(STNU in) {
		if (in == null) {
			return;
		}
		this.activationNode = (in.activationNode != null) ? new Object2ObjectOpenHashMap<>(in.activationNode) : null;
		this.checkStatus = new STNUCheckStatus(in.checkStatus);
		this.cleanCheckedInstance = in.cleanCheckedInstance;
		this.contingentAlsoAsOrdinary = in.contingentAlsoAsOrdinary;
		this.defaultControllabilityCheckAlg = in.defaultControllabilityCheckAlg;
		this.fInput = in.fInput;
		this.fOutput = in.fOutput;
		this.g = (in.g != null) ? new TNGraph<>(in.g, in.g.getEdgeImplClass()) : null;
		this.gCheckedCleaned = (in.gCheckedCleaned != null) ? new TNGraph<>(in.gCheckedCleaned, in.gCheckedCleaned.getEdgeImplClass()) : null;
		this.horizon = in.horizon;
		this.lowerContingentEdge = (in.lowerContingentEdge != null) ? new Object2ObjectOpenHashMap<>(in.lowerContingentEdge) : null;
		this.maxWeight = in.maxWeight;
		this.save = in.save;
		this.timeOut = in.timeOut;
		this.upperContingentEdge = (in.upperContingentEdge != null) ? new Object2ObjectOpenHashMap<>(in.upperContingentEdge) : null;
		this.versionReq = in.versionReq;
	}

	/**
	 * Default constructor.
	 */
	STNU() {
		this.activationNode = null;
		this.checkStatus = new STNUCheckStatus();
		this.g = gCheckedCleaned = null;
		this.fInput = fOutput = null;
		this.horizon = maxWeight = Constants.INT_NULL;
		contingentAlsoAsOrdinary = save = false;
		lowerContingentEdge = upperContingentEdge = null;
		defaultControllabilityCheckAlg = CheckAlgorithm.RUL2021;
		timeOut = 2700;// seconds
	}

	/**
	 * Makes the current DC network dispatchable removing <b>all</b> the dominated edges (ordinaries or waits). The current network must be already
	 * dispatchable. Therefore, it must be already successfully checked by either {@link #applyMorris2014Dispatchable()} or
	 * {@link #applyFastDispatchSTNU(boolean)} method.<br> Unlike {@link #applyFastDispatchSTNU(boolean)}, this procedure removes all dominated edges that could
	 * be explicit or implicit ordinary constraints and all redundant waits.
	 * <p>
	 * It also manages possible improper waits removing them, and possible rigid components collapsing them.
	 * </p>
	 *
	 * @return true if the minimization action was successful; false if any error occurred.
	 *
	 * @throws IllegalArgumentException if the network is not DC or was not checked by either {@link CheckAlgorithm#Morris2014Dispatchable} or
	 *                                  {@link CheckAlgorithm#FD_STNU}.
	 */
	public final boolean applyMinDispatchableESTNU() throws IllegalArgumentException {
		if (Debug.ON) {
			LOG.info("applyMinDispatchableESTNU started.\n");
		}
		if (!this.checkStatus.isControllable()) {
			throw new IllegalArgumentException("The network is not DC.");
		}
		if (this.checkStatus.checkAlgorithm != CheckAlgorithm.Morris2014Dispatchable && this.checkStatus.checkAlgorithm != CheckAlgorithm.FD_STNU &&
		    this.checkStatus.checkAlgorithm != CheckAlgorithm.FD_STNU_IMPROVED) {
			throw new IllegalArgumentException("The network has not been checked by Morris2014Dispatchable or FD_STNU. " +
			                                   "It is not possible to determine the minimal dispatchability version.");
		}
		final Instant startInstant = Instant.now();
		final Instant timeoutInstant = startInstant.plusSeconds(this.timeOut);

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("Initial number of edges: " + this.g.getEdgeCount());
			}
		}
		final Collection<LabeledNode> allNodes = this.g.getVertices();
		final ObjectList<STNUEdge> waitEdges = new ObjectArrayList<>();
		final ObjectSet<LabeledNode> contingentNodes = new ObjectOpenHashSet<>();
		//it is necessary to rebuild activationNode, lowerContingentEdge and upperContingentEdge.
		//Then, it is necessary to build a wait-edge list
		activationNode.clear();
		lowerContingentEdge.clear();
		upperContingentEdge.clear();
		for (final STNUEdge edge : this.g.getEdges()) {
			if (edge.isContingentEdge()) {
				if (edge.isLowerCase()) {
					final LabeledNode A = this.g.getSource(edge);
					final LabeledNode C = this.g.getDest(edge);
					lowerContingentEdge.put(C, edge);
					activationNode.put(C, A);
					contingentNodes.add(C);
					continue;
				}
				//is an upper case
				final LabeledNode C = this.g.getSource(edge);
				upperContingentEdge.put(C, edge);
				continue;
			}
			if (edge.isWait()) {
				waitEdges.add(edge);
			}
		}

		final int k = contingentNodes.size();
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Found " + k + " contingent links.");
				LOG.finer("Found " + waitEdges.size() + " wait edges.");
			}
		}

		final ObjectSet<STNUEdge> weakOrdinaryConstraints = addWeakOrdinaryConstraints(waitEdges);

		if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
			return false;
		}

		//k main cycles to propagate max min implicit constraint through a contingent link
		final STNCheckStatus stnCheckStatus = new STNCheckStatus();
		TNGraph<STNUEdge> apsp = null;
		boolean addedMaxMinConstraint = true;// Stop the following check if no new max min constraints have been added
		for (int i = 0; i < k && addedMaxMinConstraint; i++) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("Cycle " + i + "/" + k + "\nDetermine APSP_FloydWarshall by Johnson algorithm");
				}
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Number of ordinary edges: " + this.g.getEdgeCount());
				}
			}
			addedMaxMinConstraint = false;
			apsp = STN.GET_APSP_Johnson(this.g, stnCheckStatus);
			if (Debug.ON && apsp == null || !stnCheckStatus.consistency) {
				throw new IllegalArgumentException("The implicit APSP is not consistent: " + stnCheckStatus);
			}
			assert apsp != null : "The implicit APSP is not consistent.";
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("APSP:\n" + apsp);
				}
			}
			if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
				return false;
			}
			//check all possible pairs of nodes with respect to each contingent link
			assert this.getLowerCaseEdgesMap() != null;
			for (final STNUEdge lowerCaseEdge : this.getLowerCaseEdgesMap().values()) {
				final LabeledNode A = this.g.getSource(lowerCaseEdge);
				final LabeledNode C = this.g.getDest(lowerCaseEdge);
				assert A != null;
				assert C != null;
				final int x = lowerCaseEdge.getLabeledValue();
				if (Debug.ON && x == Constants.INT_NULL) {
					throw new IllegalArgumentException("Expected lower-case value in edge " + lowerCaseEdge);
				}
				final int y = -this.upperContingentEdge.get(C).getLabeledValue();
				if (Debug.ON && y == Constants.INT_NULL) {
					throw new IllegalArgumentException("Expected upper-case value in edge " + this.upperContingentEdge.get(C));
				}
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("Considering contingent link (" + A + ", " + Constants.formatInt(x) + ", " + Constants.formatInt(y) + ", " + C + ")");
					}
				}

				//find a possible V, source of the wait (V, C:-q, A)
				LabeledNode V;
				STNUEdge Vwait;
				int q;
				for (final STNUEdge edge : this.g.getInEdges(A)) {
					if (edge.isContingentEdge() || !(edge.isWait() && (C.hasNameEquals(edge.getCaseLabel().getName())))) {
						continue;
					}
					V = this.g.getSource(edge);
					assert V != null;
					Vwait = edge;
					q = Vwait.getLabeledValue();
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest("Wait " + V + " and " + A + ": " + Vwait);
						}
					}
					for (final LabeledNode W : allNodes) {
						if (W == A || W == C || W == V) {
							continue;
						}
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINEST)) {
								LOG.finest("A: " + A + ", C: " + C + ", V: " + V + ", W: " + W);
							}
						}
						final int gamma = Objects.requireNonNull(apsp.findEdge(C, W)).getValue();
						if (gamma == Constants.INT_POS_INFINITE) {
							continue;
						}
						final int delta = Objects.requireNonNull(apsp.findEdge(A, W)).getValue();
						if (delta == Constants.INT_POS_INFINITE) {
							continue;
						}
						//now all the data is ready
						final int omega = Constants.sumWithOverflowCheck(delta, -gamma);
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINEST)) {
								LOG.finest("Distance " + C + "->" + W + "(ɣ): " + Constants.formatInt(gamma) + "\tDistance " + A + "->" + W + "(δ): " +
								           Constants.formatInt(delta) + "\tOmega (ω):" + Constants.formatInt(omega));
							}
						}
						if (omega <= x || omega >= y) {
							if (Debug.ON) {
								LOG.finest("Omega is smaller or greater than the contingent bounds. Next!");
							}
							continue;
						}

						final int sigma = Constants.sumWithOverflowCheck(Math.max(-omega, q), delta);
						final int VWDistance = Objects.requireNonNull(apsp.findEdge(V, W)).getValue();
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINEST)) {
								LOG.finest(
									"From wait between " + V + " and " + A + ": " + Vwait + ", value q is " + q + ". Sigma: " + Constants.formatInt(sigma) +
									". Distance " + V + "-" + W + ": " + Constants.formatInt(VWDistance));
							}
						}
						if (sigma <= VWDistance) {
							STNUEdge maxMinWeakEdge = this.g.findEdge(V, W);
							if (maxMinWeakEdge == null) {
								maxMinWeakEdge = this.g.makeNewEdge(V.getName() + "-" + W.getName(), ConstraintType.internal);
								this.g.addEdge(maxMinWeakEdge, V, W);
								maxMinWeakEdge.setValue(sigma);
								weakOrdinaryConstraints.add(maxMinWeakEdge);
								if (Debug.ON) {
									if (LOG.isLoggable(Level.FINE)) {
										LOG.fine("A max min constraint found and added as dominated to the network: " + maxMinWeakEdge);
									}
								}
								addedMaxMinConstraint = true;
								checkStatus.maxMinConstraint++;
							} else {
								final int oldValue = maxMinWeakEdge.getValue();
								if (oldValue > sigma) {
									if (Debug.ON) {
										if (LOG.isLoggable(Level.FINE)) {
											LOG.fine(
												"A constraint already present where a max min must be added. So, it was updated from " + oldValue + " to " +
												sigma);
										}
									}
									maxMinWeakEdge.setValue(sigma);
									weakOrdinaryConstraints.add(maxMinWeakEdge);
									addedMaxMinConstraint = true;
									checkStatus.maxMinConstraint++;
								}
							}
						}
					}
				}
			}
			if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
				return false;
			}
		}//end k cycles

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("After the max min constraints addition, number of edges: " + this.g.getEdgeCount());
			}
		}
		//redetermine all minimal distances
		if (addedMaxMinConstraint) {
			//in case that also last cycle added a last constraint.
			stnCheckStatus.reset();
			apsp = STN.GET_APSP_Johnson(this.g, stnCheckStatus);
			if (Debug.ON && apsp == null || !stnCheckStatus.consistency) {
				throw new IllegalArgumentException("The network is not consistent. Something gone wrong!");
			}
		}
		if (Debug.ON) {
			LOG.fine("Determining all undominated edges and removing the dominated ones.");
		}
		if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
			return false;
		}

		//remove all dominated ordinary constraints
		makeOrdinaryConstraintMinimalDispatchable(weakOrdinaryConstraints, waitEdges);
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("After the makeOrdinaryConstraintMinimalDispatchable action, number of edges: " + this.g.getEdgeCount());
			}
		}

		//remove all weak ordinary constraints
		if (Debug.ON) {
			LOG.fine("Removing all weak ordinary constraints.");
		}
		removeWeakOrdinaryConstraints(weakOrdinaryConstraints);

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("After the removeWeakOrdinaryConstraints action, number of edges: " + this.g.getEdgeCount());
				LOG.fine("Starting removing all waits that are not necessary.");
			}
		}

		/*
		 * Mark all wait must be removed
		 */
		final ObjectSet<STNUEdge> waitToDelete = new ObjectOpenHashSet<>();
		for (final STNUEdge Vwait : waitEdges) {
			if (waitToDelete.contains(Vwait)) {
				continue;
			}
			final LabeledNode V = this.g.getSource(Vwait);
			final LabeledNode A = this.g.getDest(Vwait);
			if (A == null || V == null) {
				LOG.warning("Wait edge " + Vwait + " is not present in the network.");
				continue;
			}

			/*
			 * Mark negative wait edges outgoing from contingent TPs because they cannot be removed by makeOrdinaryConstraintMinimalDispatchable
			 */
			if (A.isContingent()) {
				waitToDelete.add(Vwait);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("Mark wait " + Vwait + " because it is outgoing from a contingent timepoint " + V);
					}
				}
				continue;
			}

			final int VwaitValue = Vwait.getLabeledValue();
			final ALetter C = Vwait.getCaseLabel().getName();
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("Considering wait " + Vwait + " between " + V + " and " + A + " having upper-case " + C + " and (negated) value " +
					           Constants.formatInt(VwaitValue));
				}
			}
			assert apsp != null;
			final int VADistance = Objects.requireNonNull(apsp.findEdge(V, A)).getValue();
			final int VCDistance = Objects.requireNonNull(apsp.findEdge(V.getName(), C.toString())).getValue();
			/*
			 * Mark wait edges dominated by ordinary edges
			 */
			if (VADistance <= VwaitValue || VCDistance < 0) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("Mark wait " + Vwait + " because the minimum distance from " + V + " to " + A + " is " + Constants.formatInt(VADistance) +
						         " and it is smaller than the wait " + Constants.formatInt(VwaitValue) + " or because the minimum distance " +
						         Constants.formatInt(VADistance) + " from " + V + " to " + C + " " + " is negative.");
					}
				}
				if (Vwait.getValue() >= VADistance) {
					// if the wait has a weak ordinary value that is weak also w.r.t. the distance, reset it.
					Vwait.setValue(Constants.INT_NULL);
				}
				waitToDelete.add(Vwait);
				if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
					return false;
				}
				continue;
			}
			/*
			 * Mark wait edges dominated by other waits.
			 */
			for (final STNUEdge UWait : this.g.getInEdges(A)) {
				if (UWait == Vwait || !UWait.isWait() || !UWait.getCaseLabel().getName().equals(C)) {
					continue;
				}
				final LabeledNode U = this.g.getSource(UWait);
				final int VUdistance = Objects.requireNonNull(apsp.findEdge(V, U)).getValue();
				if (VUdistance >= 0) {//if the distance is 0, then it is better to maintain the two waits.
					continue;
				}
				final int u = UWait.getLabeledValue();
				final int VUDinstancePlusWait = Constants.sumWithOverflowCheck(VUdistance, u);
				if (VUDinstancePlusWait <= VwaitValue) {
					//Luke replaced Math.max(upperBoundContingentLinkACNegated, VwaitValue) by VwaitValue on 2024-02-27
					if (Vwait.getValue() >= VUDinstancePlusWait) {
						// if the wait has a weak ordinary value that is weak also w.r.t. the distance, reset it.
						Vwait.setValue(Constants.INT_NULL);
					}
					if (Debug.ON) {
						LOG.info("Mark wait " + Vwait + " because the distance " + Constants.formatInt(VUdistance) + " between " + V + " and " + U +
						         " plus the wait " + UWait + " is smaller or equal than the wait " + Vwait);
					}
					waitToDelete.add(Vwait);
				}
			}
			if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
				return false;
			}
		}
		/*
		 * Remove all marked wait.
		 */
		for (final STNUEdge wait : waitToDelete) {
			if (wait.getValue() != Constants.INT_NULL && wait.getValue() > wait.getLabeledValue()) {
				//the condition wait.getValue() < wait.getLabeledValue() should be impossible. Here, we removed it in case it occurs.
				if (Debug.ON) {
					LOG.info("Removed negative wait value in " + wait);
				}
				wait.resetLabeledValue();
			} else {
				if (Debug.ON) {
					LOG.info("Removed wait " + wait);
				}
				this.g.removeEdge(wait);
			}
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("Final number of edges: " + this.g.getEdgeCount());
			}
		}

		if (Debug.ON) {
			LOG.info("applyMinDispatchableESTNU finished.");
		}
		final Instant endInstant = Instant.now();
		checkStatus.finished = true;
		checkStatus.executionTimeNS = Duration.between(startInstant, endInstant).toNanos();

		if (Debug.ON) {
			LOG.info("applyMinDispatchableESTNU finished.\n");
		}

		return true;
	}

	/**
	 * The equivalent normal-form of the distance graph associated to a STNU. In a normal-form distance-graph, each contingent link has a 0 lower bound.
	 *
	 * @return true if the current graph was made in normal form, false otherwise
	 */
	@SuppressWarnings("SameReturnValue")
	public final boolean applyNormalForm() {
		if (!checkStatus.initialized) {
			try {
				initAndCheck();
			} catch (WellDefinitionException e) {
				throw new IllegalArgumentException("The STNU graph has a problem, and it cannot be initialized: " + e.getMessage());
			}
		}
		if (Debug.ON) {
			STNU.LOG.fine("Normalization of contingent links started.");
		}

		/*
		 * MAKING LOWER CASE VALUE == 0
		 */
		for (final Entry<LabeledNode, STNUEdge> entry : lowerContingentEdge.entrySet()) {
			final STNUEdge lowerEdge = entry.getValue();
			final int lowerValue = lowerEdge.getLabeledValue();
			if (lowerValue == 0) {
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINEST)) {
						STNU.LOG.finest("Lower-case constraint " + lowerEdge + " does not require normalization.");
					}
				}
				continue;
			}

			final LabeledNode cntgNode = entry.getKey();
			final LabeledNode localActivationNode = this.activationNode.get(cntgNode);
			final STNUEdge upperEdge = this.upperContingentEdge.get(cntgNode);

			// Adds the support node
			final LabeledNode aNClone = new LabeledNode(localActivationNode);
			aNClone.setName("aux_" + localActivationNode.getName());
			aNClone.setX(localActivationNode.getX() - 100);
			if (!g.addVertex(aNClone)) {
				throw new IllegalArgumentException("Cannot add node " + aNClone);
			}
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINEST)) {
					STNU.LOG.finest("Added the support node " + aNClone);
				}
			}

			// Move all edges incident to activation node but the contingents ones to support node.
			for (final STNUEdge edgeGoing2ActNode : g.getInEdges(localActivationNode)) {
				if (edgeGoing2ActNode.equalsByName(upperEdge)) {
					continue;
				}
				final LabeledNode s = g.getSource(edgeGoing2ActNode);
				g.removeEdge(edgeGoing2ActNode);
				assert s != null;
				edgeGoing2ActNode.setName(g.getUniqueEdgeName(s.getName() + "-" + aNClone.getName()));
				g.addEdge(edgeGoing2ActNode, s, aNClone);
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINEST)) {
						STNU.LOG.finest("Moved the edge " + edgeGoing2ActNode);
					}
				}
			}
			for (final Pair<STNUEdge, LabeledNode> entryEdgeNode : g.getOutEdgesAndNodes(localActivationNode)) {
				final STNUEdge edgeOutGoingFromActNode = entryEdgeNode.left();
				if (edgeOutGoingFromActNode.equalsByName(lowerEdge)) {
					continue;
				}
				final LabeledNode d = entryEdgeNode.right();
				g.removeEdge(edgeOutGoingFromActNode);
				edgeOutGoingFromActNode.setName(aNClone.getName() + "-" + d.getName());
				g.addEdge(edgeOutGoingFromActNode, aNClone, d);
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINEST)) {
						STNU.LOG.finest("Moved the edge " + edgeOutGoingFromActNode);
					}
				}
			}
			final ALetter aletter = new ALetter(cntgNode.getName());
			lowerEdge.setLabeledValue(aletter, 0, false);
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINEST)) {
					STNU.LOG.finest("Updated the lower-case edge to " + lowerEdge);
				}
			}
			final int newUpperValue = upperEdge.getLabeledValue() + lowerValue;
			upperEdge.setLabeledValue(aletter, newUpperValue, true);
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINEST)) {
					STNU.LOG.finest("Updated the upper-case edge to " + upperEdge);
				}
			}

			// connect the clone node to the activation node
			STNUEdge e1 = g.makeNewEdge(aNClone.getName() + "-" + localActivationNode.getName(), ConstraintType.derived);
			e1.setValue(lowerValue);
			g.addEdge(e1, aNClone, localActivationNode);
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINEST)) {
					STNU.LOG.finest("Added the edge to " + e1);
				}
			}
			e1 = g.makeNewEdge(localActivationNode.getName() + "-" + aNClone.getName(), ConstraintType.derived);
			e1.setValue(-lowerValue);
			g.addEdge(e1, localActivationNode, aNClone);
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINEST)) {
					STNU.LOG.finest("Added the edge to " + e1);
				}
			}

		}
		return true;
	}

	/**
	 * Checks the consistency of an STN instance within timeout seconds. During the execution of this method, the given graph is modified.
	 * <p>
	 * If the check is successful, all constraints to node Z in g are minimized; otherwise, g contains a negative cycle at least.
	 * <p>
	 * After a check, {@link #getGChecked} returns the graph resulting after the check.
	 *
	 * @return the final status of the checking with some statistics.
	 *
	 * @throws WellDefinitionException if any.
	 */
	public final STNUCheckStatus dynamicControllabilityCheck() throws WellDefinitionException {
		return dynamicControllabilityCheck(defaultControllabilityCheckAlg);
	}

	/**
	 * Executes the dynamic controllability check using the specified algorithm, and returns the status of the check.
	 *
	 * @param alg the algorithm to use for the checking.
	 *
	 * @return the final status of the check
	 *
	 * @throws WellDefinitionException if any structural error in the current network.
	 */
	public final STNUCheckStatus dynamicControllabilityCheck(CheckAlgorithm alg) throws WellDefinitionException {
		try {
			initAndCheck();
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("The STNU graph has a problem, and it cannot be initialized: " + e.getMessage());
		}
		if (!checkStatus.initialized) {
			throw new IllegalStateException("The STNU has not been initialized! Please, consider dynamicConsistencyCheck() method!");
		}
		final Instant startInstant = Instant.now();

		switch (alg) {
			case SRNCycleFinder:
				applySRNCycleFinder();
				break;
			case FD_STNU_IMPROVED:
				applyFastDispatchSTNU(true);
				break;
			case FD_STNU:
				applyFastDispatchSTNU(false);
				break;
			case Morris2014:
				applyMorris2014();
				break;
			case Morris2014Dispatchable:
				applyMorris2014Dispatchable();
				break;
			case RUL2018:
				applyRul2018();
				break;
			case RUL2021:
			default:
				applyRul2021();
				break;
		}

		final Instant endInstant = Instant.now();
//		checkStatus.finished = true;
		checkStatus.executionTimeNS = Duration.between(startInstant, endInstant).toNanos();

		if (!checkStatus.isControllable()) {
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.INFO)) {
					STNU.LOG.log(Level.INFO, "Found an inconsistency.\nStatus: " + checkStatus);
					if (STNU.LOG.isLoggable(Level.FINEST)) {
						STNU.LOG.log(Level.FINEST, "Final uncontrollable graph: " + g);
					}
				}
			}
			if (save) {
				saveGraphToFile();
			}
			return checkStatus;
		}
		// consistent && finished
		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.INFO)) {
				STNU.LOG.log(Level.INFO, "Stable state reached. Status: " + checkStatus);
			}
		}
		if (cleanCheckedInstance) {
			// for STNU g is always cleaned.
			gCheckedCleaned = g;
		}
		if (save) {
			saveGraphToFile();
		}
		return checkStatus;
	}

	/**
	 * @return the activationNode map if the network has been {@link #initAndCheck()}, null otherwise.
	 * 	<p>
	 * 	The map is (contingentNode, activationNode).
	 */
	@Nullable
	public final Object2ObjectMap<LabeledNode, LabeledNode> getActivationNodeMap() {
		if (!this.checkStatus.initialized) {
			return null;
		}
		return activationNode;
	}

	/**
	 * @return the checkStatus
	 */
	public final STNUCheckStatus getCheckStatus() {
		return checkStatus;
	}

	/**
	 * @return the cleaned graph after a check if {@link #isCleanedOutputRequired()} is true; null otherwise. A cleaned graph does not contain empty edges.
	 */
	public TNGraph<STNUEdge> getCleanedOutput() {
		return gCheckedCleaned;
	}

	/**
	 * @return the defaultConsistencyCheckAlg
	 */
	public CheckAlgorithm getDefaultControllabilityCheckAlg() {
		return defaultControllabilityCheckAlg;
	}

	/**
	 * @return the g
	 */
	public final TNGraph<STNUEdge> getG() {
		return g;
	}

	/**
	 * Considers the given graph as the graph to check (graph will be modified). Clear all internal parameter.
	 *
	 * @param graph set internal TNGraph to g. It cannot be null.
	 */
	@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "For efficiency reason, it includes an external mutable object.")
	public final void setG(TNGraph<STNUEdge> graph) {
		if (graph == null) {
			throw new IllegalArgumentException("Input graph is null!");
		}
		reset();
		this.g = graph;
	}

	/**
	 * @return the resulting graph of a check. It is up to the called to be sure the returned graph is the result of a check. It can be used also by subclasses
	 * 	with a proper cast.
	 *
	 * @see #setOutputCleaned(boolean)
	 */
	public final TNGraph<STNUEdge> getGChecked() {
		if (cleanCheckedInstance && checkStatus.finished && checkStatus.isControllable()) {
			return gCheckedCleaned;
		}
		return g;
	}

	/**
	 * Returns a map containing the lower-case edges associated to the contingent link of the network if the network was initialized ({@link #initAndCheck()}
	 * executed successfully); null otherwise. In particular, if the network contains the contingent link {@code (A, 1, 3, C)}, the returned map contains the
	 * pair {@code C --> (A, c(1), C)}.
	 *
	 * @return the lowerContingentEdge map
	 */
	@Nullable
	public final Object2ObjectMap<LabeledNode, STNUEdge> getLowerCaseEdgesMap() {
		if (!this.checkStatus.initialized) {
			return null;
		}
		return lowerContingentEdge;
	}

	/**
	 * @return the horizon of the network
	 */
	public int getHorizon() {
		return horizon;
	}

	/**
	 * @return version and copyright string
	 */
	public final String getVersionAndCopyright() {
		// I use a non-static method for having a general method that prints the right name for each derived class.
		try {
			return getClass().getName() + " " + getClass().getDeclaredField("VERSIONandDATE").get(this) +
			       "\nSPDX-License-Identifier: LGPL-3.0-or-later, Roberto Posenato.\n";

		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			throw new IllegalStateException("Not possible exception I think :-)");
		}
	}

	/**
	 * @return the absolute max weight of the network
	 */
	public int getMaxWeight() {
		return maxWeight;
	}

	/**
	 * @return the time-out in seconds for the DC checking method.
	 */
	public int getTimeOut() {
		return timeOut;
	}

	/**
	 * Returns a map containing the upper-case edges associated to the contingent link of the network if the network was initialized ({@link #initAndCheck()}
	 * executed successfully); null otherwise. In particular, if the network contains the contingent link {@code (A, 1, 3, C)}, the returned map contains
	 * {@code C --> (C, C:-3, A)}.
	 *
	 * @return the upper-case constraints map
	 */
	@Nullable
	public Object2ObjectMap<LabeledNode, STNUEdge> getUpperCaseEdgesMap() {
		if (!this.checkStatus.initialized) {
			return null;
		}
		return upperContingentEdge;
	}

	/**
	 * Makes the STNU check and initialization. The STNU instance is represented by graph g. If some constraints of the network does not observe well-definition
	 * properties AND they can be adjusted, then the method fixes them and logs such fixes in log system at WARNING level. If the method cannot fix such
	 * not-well-defined constraints, it raises a {@link WellDefinitionException}.
	 *
	 * @throws WellDefinitionException if any error about the format of the network occurs
	 */
	public final void initAndCheck() throws WellDefinitionException {
		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINE)) {
				STNU.LOG.log(Level.FINE, "Starting initial well definition check.");
			}
		}
		g.clearCache();
		gCheckedCleaned = null;
		LabeledNode Z = g.getZ();
		activationNode = new Object2ObjectOpenHashMap<>();
		lowerContingentEdge = new Object2ObjectOpenHashMap<>();
		upperContingentEdge = new Object2ObjectOpenHashMap<>();

		// Checks the presence of Z node!
		// Z = this.g.getZ(); already done in setG()
		if (Z == null) {
			Z = g.getNode(STNU.ZERO_NODE_NAME);
			if (Z == null) {
				// We add by authority!
				Z = LabeledNodeSupplier.get(STNU.ZERO_NODE_NAME);
				Z.setX(10);
				Z.setY(10);
				g.addVertex(Z);
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.WARNING)) {
						STNU.LOG.log(Level.WARNING, "No " + STNU.ZERO_NODE_NAME + " node found: added!");
					}
				}
			}
			g.setZ(Z);
		} else {
			if (!Z.getLabel().isEmpty()) {
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.WARNING)) {
						STNU.LOG.log(Level.WARNING, "In the graph, Z node has not empty label. Label removed!");
					}
				}
				Z.setLabel(Label.emptyLabel);
			}
		}

		// Checks well definiteness of edges and determine maxWeight
		this.maxWeight = 0;
		for (final STNUEdge e : g.getEdges()) {
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINEST)) {
					STNU.LOG.log(Level.FINEST, "Initial Checking edge e: " + e);
				}
			}
			final LabeledNode s = g.getSource(e);
			final LabeledNode d = g.getDest(e);
			assert s != null;
			assert d != null;

			if (s == d) {
				// loop are not admissible
				g.removeEdge(e);
				continue;
			}
			if (e.isEmpty()) {
				e.isEmpty();
				g.removeEdge(e);
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.WARNING)) {
						STNU.LOG.log(Level.WARNING, "Empty edge " + e + " has been removed.");
					}
				}
				continue;
			}
			final int initialValue = e.getValue();
			if (initialValue != Constants.INT_NULL) {
				final int absValue = Math.abs(initialValue);
				if (absValue > maxWeight) {
					maxWeight = absValue;
				}
			}

			if (!e.isContingentEdge()) {
				continue;
			}
			// Check contingent properties!
			final int labeledValue = e.getLabeledValue();

			if (initialValue == Constants.INT_NULL && labeledValue == Constants.INT_NULL) {
				throw new WellDefinitionException(
					"Contingent edge " + e + " cannot be initialized because it hasn't an initial value neither a lower/upper case value.");
			}

			final STNUEdge eInverted = g.findEdge(d, s);
			if (eInverted == null) {
				throw new WellDefinitionException(
					"Contingent edge " + e + " is alone. The companion contingent edge between " + d.getName() + " and " + s.getName() +
					" does not exist while it must exist!");
			}
			if (!eInverted.isContingentEdge()) {
				throw new WellDefinitionException("Edge " + e + " is contingent while the companion edge " + eInverted + " is not contingent!\nIt must be!");
			}
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINEST)) {
					STNU.LOG.log(Level.FINEST, "Edge " + e + " is contingent. Found its companion: " + eInverted);
				}
			}
			/*
			 * Memo.
			 * If current initialValue is negative, current edge is the lower bound C--->A. The lower case labeled value has to be put in the inverted edge.
			 * If the lower case labeled value is already present, it must be equal.
			 * If current initialValue is positive, current edge is the upper bound A--->C. The upper case labeled value has to be put in the inverted edge.
			 * If the upper case labeled value is already present, it must be equal.
			 * if current initialValue is undefined, then we assume that the contingent link is already set.
			 */
			if (initialValue != Constants.INT_NULL) {
				final int eInvertedInitialValue;
				int lowerCaseValue;
				int upperCaseValue;
				eInvertedInitialValue = eInverted.getValue();

				if (initialValue < 0) {
					// e : A<---C
					// d s
					// eInverted : A--->C
					// d s
					// current edge 'e' is the lower bound.
					lowerCaseValue = eInverted.getLabeledValue();
					final ALetter contingentALetter = new ALetter(s.getName());

					if (lowerCaseValue != Constants.INT_NULL && -initialValue != lowerCaseValue) {
						throw new WellDefinitionException("Edge " + e + " is contingent with a negative value and the inverted " + eInverted +
						                                  " already contains a ***different*** lower case value: " + eInverted.getLabeledValueFormatted() +
						                                  ".");
					}
					if (lowerCaseValue == Constants.INT_NULL && (eInvertedInitialValue <= 0)) {
						//|| eInvertedInitialValue == Constants.INT_NULL is subsumed by <= 0
						throw new WellDefinitionException("Edge " + e + " is contingent with a negative value but the inverted " + eInverted +
						                                  " does not contain a lower case value neither a proper initial value. ");
					}

					if (lowerCaseValue == Constants.INT_NULL) {
						lowerCaseValue = -initialValue;
						eInverted.setLabeledValue(contingentALetter, lowerCaseValue, false);

						if (!contingentAlsoAsOrdinary) {
							e.setValue(Constants.INT_NULL);
						}

						upperCaseValue = -eInvertedInitialValue;
						e.setLabeledValue(contingentALetter, upperCaseValue, true);

						if (!contingentAlsoAsOrdinary) {
							eInverted.setValue(Constants.INT_NULL);
						}

						if (Debug.ON) {
							if (STNU.LOG.isLoggable(Level.FINEST)) {
								STNU.LOG.log(Level.FINEST, "Inserted the upper label value: " + e.getLabeledValueFormatted() + " to edge " + e);
							}
						}
						if (Debug.ON) {
							if (STNU.LOG.isLoggable(Level.FINEST)) {
								STNU.LOG.log(Level.FINEST, "Inserted the lower label value: " + eInverted.getLabeledValueFormatted() + " to edge " + eInverted);
							}
						}
						if (lowerCaseValue >= -upperCaseValue) {
							throw new WellDefinitionException(
								"Edge " + eInverted + " is a lower-case edge but its value equal or greater than upper-case value "+ (-upperCaseValue));
						}
					}
					// In order to speed up the checking, prepare some auxiliary data structure
					lowerContingentEdge.put(s, eInverted);
					upperContingentEdge.put(s, e);
					STNU.CHECK_ACTIVATION_UNIQUENESS(d, s, activationNode);
					activationNode.put(s, d);
					s.setContingent(true);

				} else {
					// e : A--->C
					// eInverted : C--->A
					final ALetter contingentALetter = new ALetter(d.getName());
					upperCaseValue = eInverted.getLabeledValue();

					if (upperCaseValue != Constants.INT_NULL && -initialValue != upperCaseValue) {
						throw new WellDefinitionException("Edge " + e + " is contingent with a positive value and the inverted " + eInverted +
						                                  " already contains a ***different*** upper case value: " + eInverted.getLabeledValueFormatted() +
						                                  ".");
					}
					if (upperCaseValue == Constants.INT_NULL && (eInvertedInitialValue == Constants.INT_NULL || eInvertedInitialValue >= 0)) {
						throw new WellDefinitionException("Edge " + e + " is contingent with a positive value but the inverted " + eInverted +
						                                  " does not contain a upper case value neither a proper initial value. ");
					}

					if (upperCaseValue == Constants.INT_NULL) {
						upperCaseValue = -initialValue;
						eInverted.setLabeledValue(contingentALetter, upperCaseValue, true);

						if (!contingentAlsoAsOrdinary) {
							e.setValue(Constants.INT_NULL);
						}

						lowerCaseValue = -eInvertedInitialValue;
						e.setLabeledValue(contingentALetter, lowerCaseValue, false);

						if (!contingentAlsoAsOrdinary) {
							eInverted.setValue(Constants.INT_NULL);
						}

						if (Debug.ON) {
							if (STNU.LOG.isLoggable(Level.FINEST)) {
								STNU.LOG.log(Level.FINEST, "Inserted the lower label value: " + e.getLabeledValueFormatted() + " to edge " + e);
							}
						}
						if (Debug.ON) {
							if (STNU.LOG.isLoggable(Level.FINEST)) {
								STNU.LOG.log(Level.FINEST, "Inserted the upper label value: " + eInverted.getLabeledValueFormatted() + " to edge " + eInverted);
							}
						}
						if (lowerCaseValue >= -upperCaseValue) {
							throw new WellDefinitionException(
								"Edge " + e + " is a lower-case edge but its value equal or greater than upper-case value " + (-upperCaseValue));
						}
					}
					// In order to speed up the checking, prepare some auxiliary data structure
					lowerContingentEdge.put(d, e);
					upperContingentEdge.put(d, eInverted);
					STNU.CHECK_ACTIVATION_UNIQUENESS(s, d, activationNode);
					activationNode.put(d, s);
					d.setContingent(true);
				}
			} else {
				// here initialValue is null.
				// UC and LC values are already present.
				final CaseLabel pair = e.getCaseLabel();
				if (pair != null) {
					final ALetter ctg = pair.left();
					if (pair.rightBoolean()) {// it is an upper case value
						// check that the node name is correct
						if (!ctg.toString().equals(s.getName())) {
							throw new WellDefinitionException(
								"Edge " + e + " is an upper-case edge but the name of node is not the name of contingent node: " +
								"\n upper case label: " + ctg + "\n ctg node: " + s);
						}
						final int lowerCaseValue = eInverted.getLabeledValue();
						if (lowerCaseValue != Constants.INT_NULL && lowerCaseValue > -e.getLabeledValue()) {
							throw new WellDefinitionException(
								"Edge " + eInverted + " is a lower-case edge but its value equal or greater than upper-case value " + (-e.getLabeledValue()));
						}
						STNU.CHECK_ACTIVATION_UNIQUENESS(d, s, activationNode);
						activationNode.put(s, d);
						upperContingentEdge.put(s, e);
						s.setContingent(true);
					} else {// it is a lower case value
						if (!ctg.toString().equals(d.getName())) {
							throw new WellDefinitionException(
								"Edge " + e + " is a lower-case edge but the name of node is not the name of contingent node: " +
								"\n upper case label: " + ctg + "\n ctg node: " + d);
						}
						final int upperCaseValue = eInverted.getLabeledValue();
						if (upperCaseValue != Constants.INT_NULL && e.getLabeledValue() > -upperCaseValue) {
							throw new WellDefinitionException(
								"Edge " + e + " is a lower-case edge but its value equal or greater than upper-case value " + (-upperCaseValue));
						}
						lowerContingentEdge.put(d, e);
						STNU.CHECK_ACTIVATION_UNIQUENESS(s, d, activationNode);
						activationNode.put(d, s);
						d.setContingent(true);
					}
				}
			}
			// it is necessary to check max value
			int m = e.getLabeledValue();
			// LOG.warning("m value: " + m);
			if (m != Constants.INT_NULL) {
				final int absValue = Math.abs(m);
				if (absValue > maxWeight) {
					maxWeight = absValue;
				}
			}
			m = eInverted.getLabeledValue();
			if (m != Constants.INT_NULL) {
				final int absValue = Math.abs(m);
				if (absValue > maxWeight) {
					maxWeight = absValue;
				}
			}
		} // end contingent edges cycle

		// Determine horizon value
		final long product = ((long) maxWeight) * (g.getVertexCount() - 1);// Z doesn't count!
		if (product >= Constants.INT_POS_INFINITE) {
			throw new ArithmeticException("Horizon value is not representable by an integer. maxWeight = " + maxWeight + ", #vertices = " + g.getVertexCount());
		}
		horizon = (int) product;
		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINER)) {
				STNU.LOG.log(Level.FINER, "The horizon value is " + Constants.formatInt(horizon));
			}
		}

		/*
		 * Checks well definiteness of nodes.
		 * The following part is no more necessary because
		 * BellmanFord algorithm with null source adds a symbolic source node that reaches every other node
		 * in order to determine a potential value for each node even though are not reachable from Z.
		 * Adding the following edges that forces all nodes after Z, alters the solution.
		 */
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "Adding edges to force all nodes to be at or after zero node " + Z);
			}
		}
		final Collection<LabeledNode> nodeSet = this.g.getVertices();
		for (final LabeledNode node : nodeSet) {
			// 3. Checks that each node different from Z has an edge to Z
			if (node == Z) {
				continue;
			}
			boolean added = false;
			STNUEdge edge = this.g.findEdge(node, Z);
			if (edge == null) {
				edge = this.g.makeNewEdge(node.getName() + "_" + Z.getName(), ConstraintType.derived);
				this.g.addEdge(edge, node, Z);
				edge.setValue(0);
				added = true;
			}
			if (edge.getValue() == Constants.INT_NULL || edge.getValue() > 0) {
				edge.setValue(0);
				added = true;
			}
			if (Debug.ON) {
				if (added) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.log(Level.FINEST, "Added " + edge.getName() + ": " + node.getName() + "--(0)-->" + Z.getName());
					}
				}
			}
		}

		checkStatus.reset();
		checkStatus.initialized = true;
		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINE)) {
				STNU.LOG.log(Level.FINE, "Initial well definition check done!");
			}
		}
	}

	/**
	 * @return the fInput
	 */
	@SuppressWarnings("SpellCheckingInspection")
	public File getfInput() {
		return fInput;
	}

	/**
	 * @return the fOutput
	 */
	@SuppressWarnings("SpellCheckingInspection")
	public File getfOutput() {
		return fOutput;
	}

	/**
	 * Remove ordinary weak constraints from the network that were added by {@link #addWeakOrdinaryConstraints(ObjectList)} The set of weak constraints to
	 * remove are given by weakOrdinaryConstraints. If a weak constraint is a wait or a contingent, it removes only the ordinary value. If a weak constraint is
	 * ordinary, it removes the constraint from the network.
	 *
	 * @param weakOrdinaryConstraints a set of weak constraints.
	 */
	public final void removeWeakOrdinaryConstraints(@Nonnull final ObjectSet<STNUEdge> weakOrdinaryConstraints) {
		for (final STNUEdge edge : weakOrdinaryConstraints) {
			if (!this.g.containsEdge(edge)) {
				continue;
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("Considering weak edge " + edge);
				}
			}
//			LabeledNode s = this.g.getSource(edge);
//			LabeledNode d = this.g.getDest(edge);
			if (edge.isContingentEdge() || edge.isWait()) {
				edge.setValue(Constants.INT_NULL);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("Edge " + edge + " is contingent or wait. Removed only ordinary value.");
					}
				}
				continue;
			}
			this.g.removeEdge(edge);
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("Edge " + edge + " is a simple weak constraint. Removed.");
				}
			}
		}
	}

	/**
	 * Resets all internal structures.
	 */
	public final void reset() {
		g = null;
		maxWeight = 0;
		horizon = 0;
		checkStatus.reset();
		activationNode = null;
		lowerContingentEdge = null;
	}

	/**
	 * @return true if it was required a cleaned output.
	 */
	public boolean isCleanedOutputRequired() {
		return cleanCheckedInstance;
	}

	/**
	 * @return the contingentAlsoAsOrdinary
	 */
	public boolean isContingentAlsoAsOrdinary() {
		return contingentAlsoAsOrdinary;
	}

	/**
	 * @param contingentAlsoAsOrdinary1 the contingentAlsoAsOrdinary to set
	 */
	public void setContingentAlsoAsOrdinary(boolean contingentAlsoAsOrdinary1) {
		contingentAlsoAsOrdinary = contingentAlsoAsOrdinary1;
	}

	/**
	 * @return the save
	 */
	public boolean isSave() {
		return save;
	}

	/**
	 * Stores the graph after a check to the file.
	 *
	 * @see #getGChecked()
	 */
	public final void saveGraphToFile() {
		if (fOutput == null) {
			if (fInput == null) {
				STNU.LOG.info("Input file and output file are null. It is not possible to save the result in automatic way.");
				return;
			}
			String outputName;
			try {
				outputName = COMPILE.matcher(fInput.getCanonicalPath()).replaceFirst("");
			} catch (IOException e) {
				System.err.println(
					"It is not possible to save the result. Field fOutput is null and no the standard output file can be created: " + e.getMessage());
				return;
			}
			if (!checkStatus.finished) {
				outputName += "_notFinishedCheck";
				if (checkStatus.timeout) {
					outputName += "_timeout_" + timeOut;
				}
			} else {
				outputName += "_checked_" + ((checkStatus.isControllable() ? "DC" : "NOTDC"));
			}
			outputName += STNU.FILE_NAME_SUFFIX;
			fOutput = new File(outputName);
			STNU.LOG.info("Output file name is " + fOutput.getAbsolutePath());
		}

		final TNGraph<STNUEdge> g1 = getGChecked();
		g1.setInputFile(fOutput);
		g1.setName(fOutput.getName());
		g1.removeEmptyEdges();

		final CSTNUStaticLayout<STNUEdge> layout = new CSTNUStaticLayout<>(g1);
		final TNGraphMLWriter graphWriter = new TNGraphMLWriter(layout);
		try {
			graphWriter.save(g1, fOutput);
		} catch (IOException e) {
			System.err.println("It is not possible to save the result. File " + fOutput + " cannot be created: " + e.getMessage());
			return;
		}
		STNU.LOG.info("Checked instance saved in file " + fOutput.getAbsolutePath());
	}

	/**
	 * @return the versionReq
	 */
	public boolean isVersionReq() {
		return versionReq;
	}

	/**
	 * @param defaultControllabilityCheckAlg1 the defaultControllabilityCheckAlg to set
	 */
	public final void setDefaultControllabilityCheckAlg(CheckAlgorithm defaultControllabilityCheckAlg1) {
		defaultControllabilityCheckAlg = defaultControllabilityCheckAlg1;
	}

	/**
	 * Sets to true for having the result graph cleaned of empty edges. It must be set before an instance check.
	 *
	 * @param clean the resulting graph
	 */
	public final void setOutputCleaned(boolean clean) {
		cleanCheckedInstance = clean;
	}

	/**
	 * @param s the save to set
	 */
	public final void setSave(boolean s) {
		save = s;
	}

	/**
	 * @param fileOutput the file where to save the result.
	 */
	@SuppressWarnings("SpellCheckingInspection")
	public final void setfOutput(File fileOutput) {
		fOutput = fileOutput;
	}

	/**
	 * It makes sanity check fixing each wait {@code (V, C:-v, A)} if v&le;x or v&gt;y. Then, it adds to the current network the set of the following new
	 * ordinary edges or <b>values (if the edge is already present)</b>. Such edges/values are called
	 * <b>stand-in (weak) ordinary constraints</b>:
	 * <ul>
	 *     <li>weak ordinary constraints representing the bounds of contingent links: for each contingent link {@code (A,x,y,C)},
	 *     the edges {@code (A,y,C)} and {@code (C,-x,A)}.</li>
	 *     <li>weak version of wait constraints: for each wait {@code (V,C:-v,A)} , the edges {@code (V,-x,A)}
	 *     and {@code (V,y-v,C)}.</li>
	 * </ul>
	 * The added weak edges will be returned as a set.
	 * <p>
	 * This method implements the method genStandIns presented in the paper 'Converting Simple Temporal Networks with Uncertainty into Minimal Equivalent Dispatchable Networks'
	 * presented at ICAPS 2024.
	 *
	 * @param waitEdges list of waits in the network
	 */
	final ObjectSet<STNUEdge> addWeakOrdinaryConstraints(ObjectList<STNUEdge> waitEdges) {

		final ObjectSet<STNUEdge> weakAdded = new ObjectOpenHashSet<>();
		//upper case contingent edges (C, C:-v, A)
		for (final Entry<LabeledNode, STNUEdge> entry : this.upperContingentEdge.entrySet()) {
			final STNUEdge upperCaseEdge = entry.getValue();
			if (!upperCaseEdge.isUpperCase()) {
				throw new IllegalStateException("Edge " + upperCaseEdge + " should be an upper-case edge.");
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("Considering " + upperCaseEdge);
				}
			}
			final LabeledNode C = entry.getKey();
			final STNUEdge lowerCaseEdge = this.lowerContingentEdge.get(C);
			//add the corresponding weak ordinary constraint
			lowerCaseEdge.setValue(-upperCaseEdge.getLabeledValue());
			weakAdded.add(lowerCaseEdge);
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("Contingent constraint " + upperCaseEdge + " represented as a weak " + lowerCaseEdge);
				}
			}
		}
		//lower case contingent edges (A, c:-v, C)
		for (final Entry<LabeledNode, STNUEdge> entry : this.lowerContingentEdge.entrySet()) {
			final STNUEdge lowerCaseEdge = entry.getValue();
			if (!lowerCaseEdge.isLowerCase()) {
				throw new IllegalStateException("Edge " + lowerCaseEdge + " should be a lower-case edge.");
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Considering " + lowerCaseEdge);
				}
			}
			final LabeledNode C = entry.getKey();
			final STNUEdge upperCaseEdge = this.upperContingentEdge.get(C);
			//add the corresponding weak ordinary constraint
			if (upperCaseEdge == null || !upperCaseEdge.isUpperCase()) {
				throw new IllegalStateException("Edge " + upperCaseEdge + " should be an upper-case edge.");
			}
			upperCaseEdge.setValue(-lowerCaseEdge.getLabeledValue());
			weakAdded.add(upperCaseEdge);
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("Contingent constraint " + lowerCaseEdge + " represented as a weak " + upperCaseEdge);
				}
			}
		}

		final Iterable<STNUEdge> waitEdgesToClean = new ArrayList<>(waitEdges);
		for (final STNUEdge wait : waitEdgesToClean) {
			// wait (V, C:-v, A)
			if (!wait.isWait()) {
				throw new IllegalStateException("Edge " + wait + " should be a wait.");
			}
			final LabeledNode V = this.g.getSource(wait);
			assert V != null;
			final ALetter waitALetter = wait.getCaseLabel().getName();
			final LabeledNode C = this.g.getNode(waitALetter.toString());
			assert C != null;
			final STNUEdge lowerCaseContingent = this.lowerContingentEdge.get(C);
			if (lowerCaseContingent == null || !lowerCaseContingent.isContingentEdge()) {
				throw new IllegalStateException("Edge " + lowerCaseContingent + " should be a lower-case edge.");
			}
			final STNUEdge upperCaseContingent = this.upperContingentEdge.get(C);
			if (!upperCaseContingent.isContingentEdge()) {
				throw new IllegalStateException("Edge " + upperCaseContingent + " should be an upper-case edge.");
			}
			// -x
			final int contingentLowerBoundNegated = -lowerCaseContingent.getLabeledValue();
			// -y
			final int contingentUpperBoundNegated = upperCaseContingent.getLabeledValue();
			// -v
			int waitLabeledValue = wait.getLabeledValue();

			//check for ``weak wait to fix''
			if (waitLabeledValue >= contingentLowerBoundNegated) {
				//wait (V, C:-v, C) is improper because v <= x
				wait.resetLabeledValue();
				wait.updateValue(waitLabeledValue);
				waitEdges.remove(wait);
				continue;
			}
			//check for improper wait to fix
			if (waitLabeledValue < contingentUpperBoundNegated) {
				//wait (V, C:-v, C) is improper because v > y
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("Wait " + wait + " is improper because more negative than UC " + upperCaseContingent + ". Fixing it to wait value " +
						           contingentUpperBoundNegated);
					}
				}
				//fix the wait to the maximum contingent duration.
				waitLabeledValue = contingentUpperBoundNegated;
				wait.resetLabeledValue();
				wait.updateWait(waitLabeledValue, waitALetter);
			}

			//wait is proper (V, C:-v, C) with x<v<=y.
			//It is necessary to add (V, -x, A) where x is the lower bound of contingent link (A, C)
			//add also the companion weak ordinary edge {@code (v, y-v, ctg)} where y is the upper bound of contingent link (C, d)
			STNUEdge VCedge = this.g.findEdge(V, C);
			final int V2CdistanceViaWait = Constants.sumWithOverflowCheck(waitLabeledValue, -contingentUpperBoundNegated);
			final int waitOrdinaryValue = wait.getValue();
			if (waitOrdinaryValue == Constants.INT_NULL || waitOrdinaryValue >= contingentLowerBoundNegated) {
				wait.setValue(contingentLowerBoundNegated);
				weakAdded.add(wait);//it is weak because its ordinary value is weak.
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("Wait edge " + wait + " represented as weak " + wait);
					}
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("Wait edge " + wait + " saved in the list of weak edges.");
					}
				}
				//add also the companion weak ordinary edge {@code (v, y-v, ctg)} where y is the upper bound of contingent link (C, d)
				if (VCedge == null) {
					//input ESTNU does not contain the companion
					VCedge = this.g.makeNewEdge(V.getName() + "-" + C.getName(), ConstraintType.internal);
					this.g.addEdge(VCedge, V, C);
					VCedge.setValue(V2CdistanceViaWait);
					weakAdded.add(VCedge);//they must be removed at the end
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("Added weak wait-companion edge " + VCedge + " (the contingent upper-bound is " + contingentUpperBoundNegated + ")");
						}
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest("Edge " + VCedge + " saved in the list of weak edges.");
						}
					}
					continue;
				}
				//input ESTNU contains the companion edge
				final int oldVCValue = VCedge.getValue();
				if (oldVCValue == Constants.INT_NULL || oldVCValue >= V2CdistanceViaWait) {
					//here we manage only the case that the VCedge value is a less restrictive constraint
					//than it should be w.r.t. the induced wait.
					//In this case, the VCedge should be set with a
					//more restrictive value and stored as a weak because, in any case, it is redundant w.r.t. the wait.
					//In case that VCedge is stronger, then this scope of instruction is not executed
					//and VCtgE will be managed as an ordinary constraint.
					VCedge.setValue(V2CdistanceViaWait);
					weakAdded.add(VCedge);//they must be removed at the end
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("Added weak wait-companion edge " + VCedge + " (the contingent upper-bound is " + contingentUpperBoundNegated + ")");
						}
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest("Edge " + VCedge + " saved in the list of weak edges.");
						}
					}
				}
			}
		}
		return weakAdded;
	}

	/**
	 * FD_STNU algorithm for checking the DC of the network and making it dispatchable (if DC).
	 * <br>
	 * Its theoretical time complexity is <code>O(mn + kn<sup>2</sup> + n<sup>2</sup> log n)</code>.
	 * <p>
	 * It updates `checkStatus` field with the status of computation.<br> The status of the computation is stored in {@link #checkStatus}.
	 * <p>
	 * The paper presenting FD_STNU is
	 * <pre>L. Hunsberger and R. Posenato, “A Faster Algorithm for Converting Simple Temporal Networks with Uncertainty into Dispatchable Form,”
	 *     Information and Computation, vol. 293, p. 105063, Jun. 2023, doi: 10.1016/j.ic.2023.105063.</pre>
	 * <p>This implementation is able to remove few more redundant edges with respect to the algorithm presented in the
	 * paper without modifying the time complexity.</p>
	 * <p>Regarding waits, it adds waits {@code (V, C:-v, A)} with {@code x &lt; v ≤ y)}, where {@code x, y} are are
	 * the bounds of the relative contingent link {@code (A, x, y, C)}.</p>
	 *
	 * @param improved true if the improved version of the FastDispatchSTNU must be applied, false if the original version of the algorithm must be used.
	 */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "DLS", justification = "It is for isUpdate that is not used when debug is off.")
	final void applyFastDispatchSTNU(final boolean improved) {
		contingentAlsoAsOrdinary = false; // RUL consider only labeled value in contingent links.
		checkStatus.checkAlgorithm = CheckAlgorithm.FD_STNU;
		final int k;

		if (g == null) {
			checkStatus.consistency = false;
			checkStatus.finished = true;
			return;
		}
		k = g.getContingentNodeCount();
		if (Debug.ON) {
			STNU.LOG.info("applyFastDispatchSTNU started.");
			if (STNU.LOG.isLoggable(Level.FINER)) {
				STNU.LOG.finer("Initial number of edges: " + g.getEdgeCount() + "\nInitial number of contingent nodes: " + k);
			}
			STNU.LOG.fine("Preliminary check by BellmanFord on G_LO side!");
		}
		final RULGlobalInfo globalInfo = new RULGlobalInfo(GET_SSSP_BellmanFordOL(this.g, this.checkStatus), k);
		if (globalInfo.nodePotential == null) {
			if (Debug.ON) {
				STNU.LOG.info("Found an inconsistency in G_LO graph. Giving up!");
			}
			checkStatus.consistency = false;
			checkStatus.finished = true;
			return;
		}
		if (k == 0) {
			// it is an STN
			if (Debug.ON) {
				STNU.LOG.info("The STNU ha no contingent time point! Finished!");
			}
			checkStatus.consistency = true;
			checkStatus.finished = true;
			return;
		}

		if (Debug.ON) {
			STNU.LOG.fine("Building auxiliary data structure...");
		}
		// Prepare some auxiliary data structure necessary for rul2020OneStepBackProp
		globalInfo.upperCaseEdgeFromActivation = new Object2ObjectOpenHashMap<>();

		final Instant startInstant = Instant.now();
		final Instant timeoutInstant = startInstant.plusSeconds(this.timeOut);

		for (final Entry<LabeledNode, STNUEdge> entry : upperContingentEdge.entrySet()) {
			final STNUEdge upperCaseEdge = entry.getValue();
			final LabeledNode actNode = activationNode.get(entry.getKey());
			globalInfo.upperCaseEdgeFromActivation.put(actNode, upperCaseEdge);
		}

		if (Debug.ON) {
			assert k == globalInfo.upperCaseEdgeFromActivation.size() : "Number of contingents is not equal to the number of upper case edges: " + k + "," +
			                                                            globalInfo.upperCaseEdgeFromActivation.size();
			STNU.LOG.fine("Done!\nStarting RUL2021 checking...");
		}

		for (final STNUEdge upperCaseEdge : upperContingentEdge.values()) {
			// RUL2021
			if (!rul2021BackPropagation(upperCaseEdge, globalInfo)) {
				checkStatus.consistency = false;
				checkStatus.finished = true;
				if (Debug.ON) {
					STNU.LOG.fine("Found an inconsistency!");
				}
				return;
			}

			if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
				return;
			}
		}
		if (Debug.ON) {
			STNU.LOG.fine("Done!");
			if (STNU.LOG.isLoggable(Level.FINER)) {
				STNU.LOG.finer("After RUL2021, number of edges: " + this.g.getEdgeCount());
			}
			STNU.LOG.fine("Starting adding all waits not added in the RUL2021 check...");
		}

		/*
		 * From this point this method adds some edges to the network for making it dispatchable.
		 * This is what differentiates this method from applyRul2021().
		 * 1. Add all UC edges (waits) not added by RUL2021:
		 */
		for (final STNUEdge currentUpperCaseEdge : upperContingentEdge.values()) {
			final LabeledNode C = g.getSource(currentUpperCaseEdge);
			final LabeledNode A = g.getDest(currentUpperCaseEdge);
			final int y = -currentUpperCaseEdge.getLabeledValue();
			final STNUEdge currentLowerCaseEdge = this.lowerContingentEdge.get(C);
			final int x = currentLowerCaseEdge.getLabeledValue();
			final int DeltaC = y - x;

			for (final LabeledNode otherNode : g.getVertices()) {
				if (otherNode == C) {
					continue;
				}
				final int deltaXC = globalInfo.localInfoOfContingentNodes.get(C).distanceFromNodeToContingent.getInt(otherNode);
				if (deltaXC < DeltaC) {
					final int w = deltaXC - y;
					if (improved) {
						if (w < -y) {
							// The formal proof adds such edges, although it is not necessary.
							// If w < -y, it means that the minimal distance of X from C will be always equal or greater than the duration
							// of contingent link.
							// So, a wait is useless.
							//2021-12-27 Be careful: if w==-y, then it could be that there is (V, 0, C) explicit;
							//We add the wait (V, C:-w, A) because we want to guarantee the shortest vee-path from V to A.
							// In minDispatchESTNU the companion (V, 0, C) is declared weak, and, therefore removed,
							// but (V, C:-w, A) must not be removed (we have to remove only VC distance < 0)
							if (Debug.ON) {
								if (LOG.isLoggable(Level.FINEST)) {
									LOG.finest("The wait value " + w + " is longer the maximum duration of contingent span " + y + ". Wait not added.");
								}
							}
							continue;
						}
					}
					assert A != null;
					STNUEdge e = g.findEdge(otherNode.getName(), A.getName());
					if (e == null) {
						e = this.g.makeNewEdge(otherNode.getName() + "-" + A.getName(), ConstraintType.derived);
						g.addEdge(e, otherNode, A);
					}
					final boolean isUpdated;
					if (w >= -x) {//it is a wait that can be simplified to an ordinary constraint
						isUpdated = e.updateValue(w);
					} else {
						assert C != null;
						isUpdated = e.updateWait(w, new ALetter(C.getName()));
					}
					if (Debug.ON) {
						if (isUpdated) {
							if (STNU.LOG.isLoggable(Level.FINE)) {
								STNU.LOG.fine("Added wait (or a simplified wait) in " + e);
							}
						} else {
							if (STNU.LOG.isLoggable(Level.FINER)) {
								STNU.LOG.finer("Edge " + e + " contains already a stronger value that wait " + w + ": Wait not added");
							}
						}
					}
				}
			}
		}
		if (Debug.ON) {
			STNU.LOG.fine("Done!");
			if (STNU.LOG.isLoggable(Level.FINER)) {
				STNU.LOG.finer("After adding of all wait constraints, number of edges: " + this.g.getEdgeCount());
			}
		}

		/*
		 * 2. Add all ordinary edges that represent the bypass of lower-case edges
		 */
		/*
		 * accumulatedMoatEdges is a list of (sourceNode, bypassEdge destination node, path value) object
		 */
		final ObjectArrayList<EdgeData> accumulatedBypassEdgeData = new ObjectArrayList<>();

		if (Debug.ON) {
			STNU.LOG.info("Starting accumulating all ordinary edges representing bypass of lower-case edges...");
		}
		for (final STNUEdge currentLowerEdge : lowerContingentEdge.values()) {
			accumulatedBypassEdgeData.addAll(fastDispatchSTNULowerCaseForwardPropagation(currentLowerEdge, globalInfo.nodePotential));
		}
		if (Debug.ON) {
			STNU.LOG.info("Done!\nStarting adding all accumulated ordinary edges...");
		}
		// create and add all edges derived from accumulatedBypassEdgeData
		for (final EdgeData entry : accumulatedBypassEdgeData) {
			final LabeledNode source = entry.source;
			final LabeledNode destination = entry.destination;
			final int value = entry.weight;
			STNUEdge eInG = g.findEdge(source, destination);
			if (eInG == null) {
				eInG = this.g.makeNewEdge(source.getName() + "-" + destination.getName(), ConstraintType.derived);
				eInG.setValue(value);
				g.addEdge(eInG, source, destination);
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINE)) {
						STNU.LOG.fine("Added the moat edge " + eInG);
					}
				}
			} else {
				final boolean isLowered = eInG.updateValue(value);
				if (Debug.ON) {
					if (isLowered) {
						if (STNU.LOG.isLoggable(Level.FINE)) {
							STNU.LOG.fine("Lowering value of edge " + eInG + " to " + value);
						}
					}
				}
			}
		}
		if (Debug.ON) {
			STNU.LOG.info("Done!");
			if (STNU.LOG.isLoggable(Level.FINE)) {
				STNU.LOG.fine("After adding all bypass lower-case edges, number of edges: " + this.g.getEdgeCount());
			}
			STNU.LOG.info("Starting remove dominated edges...");
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Current graph: " + this.g);
			}
		}
		assert getActivationNodeMap() != null;

		final ObjectSet<STNUEdge> weakEdges = new ObjectOpenHashSet<>();//not used
		final ObjectList<STNUEdge> waitList = new ObjectArrayList<>();//not used

		makeOrdinaryConstraintMinimalDispatchable(weakEdges, waitList);

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("Final number of edges : " + this.g.getEdgeCount());
			}
		}
		checkStatus.consistency = true;
		checkStatus.finished = true;
		if (Debug.ON) {
			LOG.info("applyFastDispatchSTNU finished.\n");
		}
	}

	/**
	 * Morris' 2014 algorithm to check the dynamic controllability of an STNU.
	 * <p>
	 * It analyses negative node, one at time. A node is considered negative when it has negative incoming edges. For each negative node, it tries to by-pass
	 * its negative edges back-propagating along non-negative edges. If during such a process meets twice a negative node not yet analysed, then a negative
	 * cycle is found.
	 * <p>
	 * The algorithm transforms the network in normal form (equivalent form where contingent links have 0 lower bound).
	 * </p>
	 * The backpropagation is realized by the recursive method {@link #morris2014DCBackpropagation(LabeledNode, Object2ObjectMap)} Moreover, it uses a global
	 * vector to keep track of status of each negative node: *not-yet-encountered*, *already-started*, *successfully-completed*.
	 * <p>
	 * The algorithm was presented in the article <a href="https://www.doi.org/10.1007/978-3-319-07046-9_33">Dynamic controllability and dispatchability
	 * relationships</a> at CPAIOR 2014 by Paul Morris.
	 * <p>
	 * Its theoretical time complexity is <code>O(n&#x1E3F; + n log n)}</code>, where &#x1E3F; is the number of edges at the end of computation.
	 * </p>
	 *
	 * @return true if the graph is dynamic controllable (DC), false otherwise.
	 */
	final boolean applyMorris2014() {
		contingentAlsoAsOrdinary = false; // Morris consider only labeled value for contingent links.
		checkStatus.checkAlgorithm = CheckAlgorithm.Morris2014;
		if (!applyNormalForm()) {
			checkStatus.consistency = false;
			checkStatus.finished = true;
			return false;
		}
		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINER)) {
				STNU.LOG.finer("Initial number of edges: " + this.g.getEdgeCount());
			}
		}
		// determine negative nodes, i.e., nodes target of negative ordinary edges or upper-case edge.
		final Object2ObjectMap<LabeledNode, ElementStatus> negativeNodeStatusMap = new Object2ObjectOpenHashMap<>();
		for (final LabeledNode node : g.getVerticesArray()) {
			for (final STNUEdge inEdge : g.getInEdges(node)) {
				if (STNU.GET_UPPER_OR_ORDINARY_VALUE(inEdge) < 0) {
					negativeNodeStatusMap.put(node, ElementStatus.unStarted);
					break;
				}
			}
		}
		// main applyMorris2014 cycle
		for (final LabeledNode X : negativeNodeStatusMap.keySet()) {
			final boolean consistent = morris2014DCBackpropagation(X, negativeNodeStatusMap);
			if (!consistent) {
				checkStatus.consistency = false;
				checkStatus.finished = true;
				return false;
			}
		}
		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINER)) {
				STNU.LOG.finer("Final number of edges: " + this.g.getEdgeCount());
			}
		}
		checkStatus.consistency = true;
		checkStatus.finished = true;
		return true;
	}

	/**
	 * Morris' 2014 algorithm to check the dynamic controllability (DC) of an STNU. In case that the network is DC, the network is modified adding all ordinary
	 * and wait constraints to make the network dispatchable.
	 * <p>Regarding waits, it adds waits {@code (V, C:-v, A)} with {@code x &lt; v ≤ y)}, where {@code x, y} are are
	 * the bounds of the relative contingent link {@code (A, x, y, C)}.</p> It analyses negative node, one at time. A node is considered negative when it has
	 * negative incoming edges. For each negative node, it tries to by-pass its negative edges back-propagating along non-negative edges. If during such a
	 * process meets twice a negative node not yet analysed, then a negative cycle is found.
	 * <p>
	 * Thi version of the algorithm <b>does not</b> transform the network in normal form to avoid the introduction of rigid components that cannot be removed
	 * and make the dispatchability phase not correct.
	 * </p>
	 * The backpropagation is realized by the recursive method
	 * {@link #morris2014DispatchableDCBackpropagation(LabeledNode, boolean, Object2ObjectMap, Object2ObjectMap)} Moreover, it uses a global vector to keep
	 * track of status of each negative node:
	 * <i>not-yet-encountered</i>, <i>already-started</i>, <i>successfully-completed</i>.
	 * <p>
	 * The algorithm was presented in the article <a href="https://www.doi.org/10.1007/978-3-319-07046-9_33">Dynamic controllability and dispatchability
	 * relationships</a> at CPAIOR 2014 by Paul Morris.
	 * <p>
	 * Its theoretical time complexity is <code>O(n ṁ + n log n)}</code>, where ṁ is the number of edges at the end of computation.
	 * </p>
	 */
	final void applyMorris2014Dispatchable() {
		contingentAlsoAsOrdinary = false; // Morris consider only labeled value for contingent links.
		checkStatus.checkAlgorithm = CheckAlgorithm.Morris2014Dispatchable;
		//no normal form
		if (Debug.ON) {
			STNU.LOG.info("Morris2014Dispatchable started...");
			if (STNU.LOG.isLoggable(Level.FINE)) {
				STNU.LOG.fine("Initial number of edges: " + this.g.getEdgeCount());
			}
		}
		// determine negative nodes, i.e., nodes target of negative ordinary edges or upper-case edge.
		final Object2ObjectMap<LabeledNode, ElementStatus> negativeNodeStatusMap = new Object2ObjectOpenHashMap<>();
		negativeNodeStatusMap.defaultReturnValue(null);
		final Object2ObjectMap<LabeledNode, STNUEdge> activationNodesMap = new Object2ObjectOpenHashMap<>();
		activationNodesMap.defaultReturnValue(null);
		final Instant startInstant = Instant.now();
		final Instant timeoutInstant = startInstant.plusSeconds(this.timeOut);
		for (final LabeledNode node : g.getVerticesArray()) {
			for (final STNUEdge inEdge : g.getInEdges(node)) {
				if (inEdge.getConstraintType() == ConstraintType.contingent && inEdge.isUpperCase()) {
					//node is an activation time point
					activationNodesMap.put(node, inEdge);
					negativeNodeStatusMap.put(node, ElementStatus.unStarted);
					if (Debug.ON) {
						if (STNU.LOG.isLoggable(Level.FINEST)) {
							STNU.LOG.finest("Added the negative activation node " + node);
						}
					}
					break;
				}
				if (negativeNodeStatusMap.containsKey(node)) {
					continue;//we cannot break because we check all incoming edges to check if there is an upper-case edge
				}
				final int v = inEdge.getValue();
				if (v != Constants.INT_NULL && v < 0) {
					negativeNodeStatusMap.put(node, ElementStatus.unStarted);
					if (Debug.ON) {
						if (STNU.LOG.isLoggable(Level.FINEST)) {
							STNU.LOG.finest("Added the negative node " + node);
						}
					}
				}
			}
			if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
				return;
			}
		}

		// main applyMorris2014 cycle
		for (final LabeledNode X : negativeNodeStatusMap.keySet()) {
			boolean success;
			if (activationNodesMap.containsKey(X)) {
				success = morris2014DispatchableDCBackpropagation(X, true, activationNodesMap, negativeNodeStatusMap);
				if (!success) {
					checkStatus.consistency = false;
					checkStatus.finished = true;
					return;
				}
			}
			success = morris2014DispatchableDCBackpropagation(X, false, activationNodesMap, negativeNodeStatusMap);
			if (!success) {
				checkStatus.consistency = false;
				checkStatus.finished = true;
				return;
			}
		}
		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINE)) {
				STNU.LOG.fine("Final number of edges: " + this.g.getEdgeCount());
			}
		}

		checkStatus.consistency = true;
		checkStatus.finished = true;
		if (Debug.ON) {
			STNU.LOG.info("Morris2014Dispatchable finished.");
		}
	}

	/**
	 * RUL^- algorithm by Cairo, Hunsberger, and Rizzi, presented in
	 * <a href="https://www.doi.org/10.4230/LIPIcs.TIME.2018.8">Faster dynamic controllability checking for simple
	 * temporal networks with uncertainty</a> at TIME 2018.
	 * <p>
	 * Its theoretical time complexity is <code>O(mn + k<sup>2</sup>n + kn log n)</code>, where {@code m} is the number of edges, {@code n} the number of nodes,
	 * and {@code k} the number of contingent links.
	 * <p>
	 * It updates `checkStatus` field with the status of computation.
	 */
	final void applyRul2018() {
		contingentAlsoAsOrdinary = false; // RUL consider only labeled value in contingent links.
		checkStatus.checkAlgorithm = CheckAlgorithm.RUL2018;
		final int k;

		if (g == null) {
			checkStatus.consistency = false;
			checkStatus.finished = true;

			return;
		}

		k = g.getContingentNodeCount();

		Object2IntMap<LabeledNode> h = GET_SSSP_BellmanFordOL(this.g, this.checkStatus);
		if (h == null) {
			if (Debug.ON) {
				STNU.LOG.info("Found an inconsistency in G_LO graph. Giving up!");
			}
			checkStatus.consistency = false;
			checkStatus.finished = true;
			return;
		}
		if (k == 0) {
			// it is an STN
			if (Debug.ON) {
				STNU.LOG.info("The STNU ha no contingent time point! Finished!");
			}
			checkStatus.consistency = true;
			checkStatus.finished = true;

			return;
		}
		if (Debug.ON) {
			STNU.LOG.info("RUL2018 started");
		}
		final ObjectArrayList<LabeledNode> contingentNodes = new ObjectArrayList<>(upperContingentEdge.keySet());
		assert contingentNodes.size() == k : "Number of contingents node is not equal to the number of upper case edges: " + k + "," + contingentNodes.size();
		final ObjectArrayList<LabeledNode> S = new ObjectArrayList<>();
		S.push(contingentNodes.top());// push arbitrary element of U onto S keeping in U
		assert contingentNodes.size() == k : "U.top() removed a node";

		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINEST)) {
				STNU.LOG.finest("Stack U (size: " + contingentNodes.size() + "): " + contingentNodes + "\nStack S: " + S);
			}
		}
		//noinspection MethodCallInLoopCondition
		while (!S.isEmpty()) {
			checkStatus.cycles++;// Counts how many times this procedure was called;

			final LabeledNode C = S.top();
			assert getActivationNodeMap() != null;
			final LabeledNode A = getActivationNodeMap().get(C);
			final int x = lowerContingentEdge.get(C).getLabeledValue();
			final int y = -upperContingentEdge.get(C).getLabeledValue();
			final int DeltaC = y - x;
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINEST)) {
					STNU.LOG.finest("Considering contingent link (" + A + ", " + x + ", " + y + ", " + C.getName() + ")");
				}
			}
			rul2018CloseRelaxLower(h, C, DeltaC);
			rul2018ApplyUpper(A, x, y, C);
			h = rul2018UpdatePotential(h, A);
			if (h == null) {
				if (Debug.ON) {
					STNU.LOG.info("Found an inconsistency in G_LO graph. Giving up!");
				}
				checkStatus.consistency = false;
				checkStatus.finished = true;

				return;
			}
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINEST)) {
					STNU.LOG.finest("Considering other contingent t.p. w.r.t. contingent link (" + A + ", " + x + ", " + y + ", " + C.getName() + ")");
				}
			}
			LabeledNode C1 = null;
			int eValue;
			for (final LabeledNode C1i : contingentNodes) {
				if (C1i == C) {
					continue;
				}
				final LabeledNode A1 = getActivationNodeMap().get(C1i);
				assert A1 != null : "A1 must be an activation time-point.";
				final STNUEdge edgeFromAnotherActivationToC = g.findEdge(A1, C);
				if (edgeFromAnotherActivationToC == null) {
					continue;
				}
				eValue = edgeFromAnotherActivationToC.getValue();
				if (eValue == Constants.INT_NULL) {
					continue;
				}
				if (eValue < DeltaC) {
					C1 = C1i;
					break;
				}
			}
			if (C1 != null) {
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINEST)) {
						STNU.LOG.finest("Found contingent " + C1 + " w.r.t. contingent link (" + A + ", " + x + ", " + y + ", " + C + ")");
					}
				}

				if (S.contains(C1)) {
					if (Debug.ON) {
						STNU.LOG.info("Found a negative cycle involving " + C1 + " and " + C + ". Giving up!");
					}
					checkStatus.consistency = false;
					checkStatus.finished = true;

					return;
				}
				S.push(C1);
			} else {
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINEST)) {
						STNU.LOG.finest(
							"Found no other contingent t.p. w.r.t. contingent link (" + A.getName() + ", " + x + ", " + y + ", " + C.getName() + ")");
					}
				}
				contingentNodes.remove(C);
				S.pop();
				if (!contingentNodes.isEmpty() && S.isEmpty()) {
					S.push(contingentNodes.top());
				}
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINEST)) {
						STNU.LOG.finest("Stack U (size: " + contingentNodes.size() + "): " + contingentNodes);
						STNU.LOG.finest("Stack S: " + S);
					}
				}
			}
		}
		checkStatus.consistency = true;
		checkStatus.finished = true;

	}

	/**
	 * Luke Hunsberger version of RUL<sup>-</sup> algorithm. The algorithm was presented at AAAI22 conference: "Speeding up the RUL<sup>-</sup>
	 * Dynamic-Controllability-Checking Algorithm for Simple Temporal Networks with Uncertainty".
	 * <p>
	 * Its theoretical time complexity is <code>O(mn + k<sup>2</sup>n + kn log n)</code>, where {@code m} is the number of edges, {@code n} the number of nodes,
	 * and {@code k} the number of contingent links.
	 * <p>
	 * It updates `checkStatus` field with the status of computation.
	 */
	final void applyRul2021() {
		contingentAlsoAsOrdinary = false; // RUL consider only labeled value in contingent links.
		checkStatus.checkAlgorithm = CheckAlgorithm.RUL2021;
		final int k;

		if (g == null) {
			checkStatus.consistency = false;
			checkStatus.finished = true;

			return;
		}
		k = g.getContingentNodeCount();

		final RULGlobalInfo globalInfo = new RULGlobalInfo(GET_SSSP_BellmanFordOL(this.g, this.checkStatus), k);
		if (globalInfo.nodePotential == null) {
			if (Debug.ON) {
				STNU.LOG.info("Found an inconsistency in G_LO graph. Giving up!");
			}
			checkStatus.consistency = false;
			checkStatus.finished = true;

			return;
		}
		if (k == 0) {
			// it is an STN
			if (Debug.ON) {
				STNU.LOG.info("The STNU ha no contingent time point! Finished!");
			}
			checkStatus.consistency = true;
			checkStatus.finished = true;

			return;
		}

		// Prepare some auxiliary data structure necessary for rul2020OneStepBackProp
		globalInfo.upperCaseEdgeFromActivation = new Object2ObjectOpenHashMap<>();

		for (final Entry<LabeledNode, STNUEdge> entry : upperContingentEdge.entrySet()) {
			final STNUEdge upperCaseEdge = entry.getValue();
			final LabeledNode actNode = activationNode.get(entry.getKey());
			globalInfo.upperCaseEdgeFromActivation.put(actNode, upperCaseEdge);
		}

		assert k == globalInfo.upperCaseEdgeFromActivation.size() : "Number of contingents is not equal to the number of upper case edges: " + k + "," +
		                                                            globalInfo.upperCaseEdgeFromActivation.size();

		if (Debug.ON) {
			STNU.LOG.finer("Starting to bypass each upper-case edge.");
		}
		for (final STNUEdge upperCaseEdge : upperContingentEdge.values()) {
			if (!rul2021BackPropagation(upperCaseEdge, globalInfo)) {
				checkStatus.consistency = false;
				checkStatus.finished = true;

				return;
			}
		}
		if (Debug.ON) {
			STNU.LOG.info("RUL2021 found network controllable! Finished!");
		}
		checkStatus.consistency = true;
		checkStatus.finished = true;

	}

	/**
	 * RUL2021 algorithms that also returns the negative cycle in case that the instance is not DC. The name SRNCycleFinder stands for Semi Reducible Negative
	 * Cycle.<br> If the instance is not DC, the negative cycle is in {@link STNUCheckStatus#getNegativeSTNUCycleInfo(boolean, TNGraph)} and for each edge in
	 * the SRNC, the {@link STNUCheckStatus#getEdgePathAnnotation()} represents the path of edges that generates the considered edge.
	 * <p>
	 * Its theoretical time complexity is <code>O(mn + k<sup>2</sup>n + kn log n)</code>, where {@code m} is the number of edges, {@code n} the number of nodes,
	 * and {@code k} the number of contingent links.
	 */
	final void applySRNCycleFinder() {
		contingentAlsoAsOrdinary = false; // RUL consider only labeled value in contingent links.
		checkStatus.checkAlgorithm = CheckAlgorithm.SRNCycleFinder;
		final int k;

		if (g == null) {
			checkStatus.consistency = false;
			checkStatus.finished = true;
			return;
		}
		k = g.getContingentNodeCount();

		final RULGlobalInfo globalInfo = new RULGlobalInfo(GET_SSSP_BellmanFordOL(this.g, this.checkStatus), k);
		if (globalInfo.nodePotential == null) {
			if (Debug.ON) {
				STNU.LOG.info("Found an inconsistency in G_LO graph.");
			}
			final STN.EdgeValue edgeValue = (e) -> GET_MIN_VALUE_BETWEEN_ORDINARY_AND_LOWERCASE((STNUEdge) e);
			assert this.g.getZ() != null;
			STN.SSSP_BFCT(this.g, this.g.getZ(), edgeValue, this.horizon, checkStatus);
			checkStatus.consistency = false;
			checkStatus.finished = true;
			final int negCycleSize;
			if (checkStatus.negativeCycle == null || (negCycleSize = checkStatus.negativeCycle.size()) == 0) {
				throw new IllegalStateException("BFCT was unable to find the negative cycle");
			}
			final STNUPath negCycle = new STNUPath();
			LabeledNode prev = checkStatus.negativeCycle.getFirst();
			for (int i = 1; i < negCycleSize; i++) {
				final LabeledNode node = checkStatus.negativeCycle.get(i);
				final STNUEdge e = this.g.findEdge(prev, node);
				negCycle.add(e);
				prev = node;
			}
			checkStatus.srncKind = STNUCheckStatus.SRNCKind.loGraphPotFailure;
			checkStatus.negativeSTNUCycle = negCycle;
			return;
		}
		if (k == 0) {
			// it is an STN
			if (Debug.ON) {
				STNU.LOG.info("The STNU ha no contingent time point! Finished!");
			}
			checkStatus.consistency = true;
			checkStatus.finished = true;
			return;
		}

		// Prepare some auxiliary data structure necessary for OneStepBackProp
		globalInfo.upperCaseEdgeFromActivation = new Object2ObjectOpenHashMap<>();

		for (final Entry<LabeledNode, STNUEdge> entry : upperContingentEdge.entrySet()) {
			final STNUEdge upperCaseEdge = entry.getValue();
			final LabeledNode actNode = activationNode.get(entry.getKey());
			globalInfo.upperCaseEdgeFromActivation.put(actNode, upperCaseEdge);
		}

		assert k == globalInfo.upperCaseEdgeFromActivation.size() : "Number of contingents is not equal to the number of upper case edges: " + k + "," +
		                                                            globalInfo.upperCaseEdgeFromActivation.size();

		if (Debug.ON) {
			STNU.LOG.finer("Starting to bypass each upper-case edge.");
		}
		for (final STNUEdge upperCaseEdge : upperContingentEdge.values()) {
			if (!SRNCycleFinderBackPropagation(upperCaseEdge, globalInfo)) {
				//the negative cycle is already in checkStatus
				checkStatus.consistency = false;
				checkStatus.finished = true;
				return;
			}
		}
		if (Debug.ON) {
			STNU.LOG.info("applySRNCycleFinder found network controllable! Finished!");
		}
		checkStatus.consistency = true;
		checkStatus.finished = true;
	}

	/**
	 * Collapses given rigid components (RC) rerouting, in each RC, all edges to/from nodes in RC to the representative node of RC.
	 * <p>Nodes belonging to an RC and different from the RC representative are removed from the graph.</p>
	 *
	 * @param <E>       the kind of edges
	 * @param rep       map giving the RC representative of the node. This map can be determined by
	 *                  {@link #GET_REPRESENTATIVE_RIGID_COMPONENTS(ObjectList, Object2IntMap, LabeledNode, ObjectCollection)}.
	 * @param distance  map giving the distance from the RC representative. This map can be determined by
	 *                  {@link #GET_REPRESENTATIVE_RIGID_COMPONENTS(ObjectList, Object2IntMap, LabeledNode, ObjectCollection)}.
	 * @param weakEdges set of edges already dominated. If this procedure remove an edge from the network, it updates this set removing the edge if it is into
	 *                  the set.
	 * @param waitEdges set of waits of the network. If this procedure remove a wait from the network, it updates this set removing the edge if it is into the
	 *                  set.
	 *
	 * @throws IllegalStateException when the edge has no a standard weight.
	 */
	@SuppressWarnings("unchecked")
	final <E extends STNUEdge> void collapseRigidComponents(@Nonnull Object2ObjectMap<LabeledNode, LabeledNode> rep,
	                                                        @Nonnull Object2IntMap<LabeledNode> distance,
	                                                        @Nonnull ObjectSet<E> weakEdges, @Nonnull ObjectList<E> waitEdges) {

		// make sure that the distance of node not in an RC is 0
		distance.defaultReturnValue(0);

		// consider each node in rep and reroute its in/out edges to its RC representative node
		for (final LabeledNode nodeInRC : rep.keySet()) {
			final LabeledNode nodeInRCrep = rep.get(nodeInRC);
			if (nodeInRC == nodeInRCrep) {// it is a representative
				continue;
			}
			final int nodeInRCOffset = distance.getInt(nodeInRC);
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("Considering node " + nodeInRC + ". Its representative is " + nodeInRCrep + " at distance " + nodeInRCOffset);
				}
			}
			/*
			 * The general rule for re-routing an edge (nodeInRC,v,Y) is
			 * nodeInRCrep=rep[nodeInRC]
			 * Yr=rep[Y]
			 * if (nodeInRCrep!=Yr) add (nodeInRCrep, distance[nodeInRC]+v-distance[Y], Yr)
			 * Here we consider (nodeInRCrep,v,Y) (outgoing) and (Y,v,nodeInRCrep) (incoming)
			 */
			// all outgoing edges
			for (final STNUEdge outEdge : this.g.getOutEdges(nodeInRC)) {
				final LabeledNode Y = this.g.getDest(outEdge);
				LabeledNode Yr = rep.get(Y);
				if (Yr == null) {
					Yr = Y;// it is not in a rigid component.
				}
				E newOutEdge = null;
				boolean updateOrdinaryValue = false;
				boolean updateLabeledValue = false;
				if (Yr != nodeInRCrep) {
					//Be careful because we have to re-route all values: ordinary and UC or LC if present.
					final int outEdgeValue = outEdge.getValue();
					final int labeledOutEdgeValue = outEdge.getLabeledValue();
					final CaseLabel caseOutEdge = outEdge.getCaseLabel();
					// if Y does not belong to any RC, its offset is 0 (see above defaultReturnValue)
					final int valueShift = Constants.sumWithOverflowCheck(nodeInRCOffset, -distance.getInt(Y));
					final int newOutEdgeValue = (outEdgeValue != Constants.INT_NULL)
					                            ? Constants.sumWithOverflowCheck(outEdgeValue, valueShift)
					                            : Constants.INT_NULL;
					final int newOutEdgeLabeledValue = (labeledOutEdgeValue != Constants.INT_NULL)
					                                   ? Constants.sumWithOverflowCheck(labeledOutEdgeValue, valueShift)
					                                   : Constants.INT_NULL;

					newOutEdge = (E) this.g.findEdge(nodeInRCrep, Yr);
					//manage ordinary value
					if (newOutEdge == null) {
						assert Yr != null;
						newOutEdge = (E) this.g.makeNewEdge(nodeInRCrep.getName() + "-" + Yr.getName(),
						                                    outEdge.getConstraintType() == ConstraintType.contingent ? ConstraintType.contingent
						                                                                                             : ConstraintType.derived);
						this.g.addEdge(newOutEdge, nodeInRCrep, Yr);
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("Added edge " + newOutEdge);
							}
						}
					}
					if (newOutEdgeValue != Constants.INT_NULL) {
						if (Debug.ON && updateOrdinaryValue) {
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("Ordinary value in " + newOutEdge + " updated to " + newOutEdgeValue);
							}
						}
						updateOrdinaryValue = newOutEdge.updateValue(newOutEdgeValue);
					}
					if (newOutEdgeLabeledValue != Constants.INT_NULL) {
						if (outEdge.isContingentEdge()) {
							if (Debug.ON) {
								if (LOG.isLoggable(Level.FINE)) {
									LOG.fine("Labeled value in " + newOutEdge + " updated to " + newOutEdgeLabeledValue);
								}
							}
							newOutEdge.setLabeledValue(caseOutEdge.getName(), newOutEdgeLabeledValue, caseOutEdge.isUpper());
							newOutEdge.setConstraintType(ConstraintType.contingent);//because the e1 could already present
							updateLabeledValue = true;
						}
						if (outEdge.isWait()) {
							if (newOutEdgeLabeledValue < 0) {
								updateLabeledValue = newOutEdge.updateWait(newOutEdgeLabeledValue, caseOutEdge.getName());
								if (Debug.ON && updateLabeledValue) {
									if (LOG.isLoggable(Level.FINE)) {
										LOG.fine("Wait value in " + newOutEdge + " updated to " + newOutEdgeLabeledValue);
									}
								}
							} else {
								updateOrdinaryValue = newOutEdge.updateValue(newOutEdgeLabeledValue);
								newOutEdge.resetLabeledValue();
								if (Debug.ON && updateOrdinaryValue) {
									if (LOG.isLoggable(Level.FINE)) {
										LOG.fine(
											"The new value for the wait is positive, update ordinary value in " + newOutEdge + " to " + newOutEdgeLabeledValue);
									}
								}
							}
						}
					}
					if (Debug.ON && (updateLabeledValue || updateOrdinaryValue)) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("Added or updated edge " + newOutEdge);
						}
					}
				}
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("Removed edge " + outEdge);
					}
				}
				this.g.removeEdge(outEdge);
				//if outEdge is contingent... adjust auxiliary data structures
				if (outEdge.isContingentEdge()) {
					if (newOutEdge == null || !newOutEdge.isContingentEdge()) {
						throw new IllegalStateException("New edge " + newOutEdge + " replacing contingent " + outEdge + " cannot be null or not contingent");
					}
					final LabeledNode A;
					final LabeledNode C;
					if (outEdge.isUpperCase()) {
						A = Yr;
						C = nodeInRCrep;
						upperContingentEdge.put(C, newOutEdge);
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("Added edge " + newOutEdge + " to upperContingentEdge map as (" + C + ", " + newOutEdge + ").");
							}
						}
					} else {
						A = nodeInRCrep;
						C = Yr;
						lowerContingentEdge.put(C, newOutEdge);
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("Added edge " + newOutEdge + " to lowerContingentEdge map as (" + C + ", " + newOutEdge + ").");
							}
						}
					}
					activationNode.put(C, A);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("Added node " + A + " to activationNode map as (" + C + ", " + A + ").");
						}
					}
				}
				if (weakEdges.contains(outEdge) || weakEdges.contains(newOutEdge)) {
					weakEdges.remove(outEdge);
					if (newOutEdge != null) {
						//If newOutEdge is contingent or wait, then newOutEdge is weak
						if (newOutEdge.isContingentEdge() || newOutEdge.isWait()) {
							weakEdges.add(newOutEdge);//ordinary value must be removed at the end
						} else {
							// e1 is ordinary. if it was update, then its weakness is lost.
							// if e1 comes from a wait and e1 results to be NOT wait because the value becomes positive, e1 is not weak.
							if (updateOrdinaryValue || (outEdge.isWait() && !newOutEdge.isWait())) {
								weakEdges.remove(newOutEdge);
							} else {
								weakEdges.add(newOutEdge);
							}
						}
					}
				}
				if (outEdge.isWait()) {
					waitEdges.remove(outEdge);
				}
				if (newOutEdge != null) {
					waitEdges.remove(newOutEdge);//this because some updateValue can transform a wait in an ordinary, and it is hard to catch
					if (newOutEdge.isWait()) {
						waitEdges.add(newOutEdge);
					}
				}
			}

			// all ingoing edges
			// Remember: invert the role!
			for (final STNUEdge inEdge : this.g.getInEdges(nodeInRC)) {
				final LabeledNode Y = this.g.getSource(inEdge);
				LabeledNode Yr = rep.get(Y);
				if (Yr == null) {
					Yr = Y;
				}
				E newInEdge = null;
				boolean updateOrdinaryValue = false;
				boolean updateLabeledValue = false;
				if (Yr == null) {
					throw new IllegalStateException("A very strange error: Yr cannot be null");
				}
				if (Yr != nodeInRCrep) {
					final int inEdgeValue = inEdge.getValue();
					final int labeledInEdgeValue = inEdge.getLabeledValue();
					final CaseLabel caseInEdge = inEdge.getCaseLabel();
					// if Y does not belong to any RC, its offset is 0 (see above defaultReturnValue)
					final int valueShift = Constants.sumWithOverflowCheck(-nodeInRCOffset, distance.getInt(Y));
					final int newInEdgeValue =
						(inEdgeValue != Constants.INT_NULL) ? Constants.sumWithOverflowCheck(inEdgeValue, valueShift) : Constants.INT_NULL;
					final int newInEdgeLabeledValue =
						(labeledInEdgeValue != Constants.INT_NULL) ? Constants.sumWithOverflowCheck(labeledInEdgeValue, valueShift) : Constants.INT_NULL;

					newInEdge = (E) this.g.findEdge(Yr, nodeInRCrep);
					if (newInEdge == null) {
						newInEdge = (E) this.g.makeNewEdge(Yr.getName() + "-" + nodeInRCrep.getName(),
						                                   inEdge.getConstraintType() == ConstraintType.contingent ? ConstraintType.contingent
						                                                                                           : ConstraintType.derived);
						this.g.addEdge(newInEdge, Yr, nodeInRCrep);
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("Added edge " + newInEdge);
							}
						}
					}
					if (newInEdgeValue != Constants.INT_NULL) {
						updateOrdinaryValue = newInEdge.updateValue(newInEdgeValue);
						if (Debug.ON && updateOrdinaryValue) {
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("Ordinary value in " + newInEdge + " updated to " + newInEdgeValue);
							}
						}
					}
					if (newInEdgeLabeledValue != Constants.INT_NULL) {
						if (inEdge.isContingentEdge()) {
							if (Debug.ON) {
								if (LOG.isLoggable(Level.FINE)) {
									LOG.fine("Collapsing edge: " + inEdge + " from  " + Y + " to " + nodeInRC + ". Representative are, respectively, " + Yr
									         + " and " + nodeInRCrep + ". Labeled value in the new edge " + newInEdge + " updated to " + newInEdgeLabeledValue);
								}
							}
							newInEdge.setLabeledValue(caseInEdge.getName(), newInEdgeLabeledValue, caseInEdge.isUpper());
							newInEdge.setConstraintType(ConstraintType.contingent);
							updateLabeledValue = true;

							//FIXME it tries to manage possible log-normal distribution parameters associated to the contingent link
							if (caseInEdge.isUpper() && Yr.getLogNormalDistribution() != null) {
								if (Debug.ON) {
									if (LOG.isLoggable(Level.FINEST)) {
										LOG.info(
											"Upper case to change: " + inEdge + ". Source node: " + Y + ". Its representative: " + Yr + ". Dest node: " +
											nodeInRC
											+ ". Its representative: " + nodeInRCrep + ". Distance of dest node to representative: " + nodeInRCOffset
											+ ". Labeled value in the new edge " + newInEdge + " updated to " + newInEdgeLabeledValue);
										STNUEdge lowerCase = this.g.findEdge(nodeInRC, Y);
										if (lowerCase != null) {
											LOG.info("The lower case value must be updated to " + (lowerCase.getLabeledValue() + nodeInRCOffset));
										} else {
											lowerCase = this.g.findEdge(nodeInRCrep, Y);
											assert lowerCase != null;
											LOG.info("The lower case value is " + lowerCase.getLabeledValue());
										}
									}
								}
								if (Debug.ON) {
									if (LOG.isLoggable(Level.FINEST)) {
										LOG.finest(
											"Log-normal parameter " + Yr.getLogNormalDistribution() + " of ctg node " + Y + " adjusted by a shift of " +
											nodeInRCOffset);
									}
								}
								Yr.getLogNormalDistribution().setShift(nodeInRCOffset);
							}
						}
						if (newInEdgeLabeledValue < 0) {
							updateLabeledValue = newInEdge.updateWait(newInEdgeLabeledValue, caseInEdge.getName());
							if (Debug.ON && updateLabeledValue) {
								if (LOG.isLoggable(Level.FINE)) {
									LOG.fine("Wait value in " + newInEdge + " updated to " + newInEdgeLabeledValue);
								}
							}
						} else {
							updateOrdinaryValue = newInEdge.updateValue(newInEdgeLabeledValue);
							newInEdge.resetLabeledValue();
							if (Debug.ON && updateOrdinaryValue) {
								if (LOG.isLoggable(Level.FINE)) {
									LOG.fine("The new value for the shifted wait is positive, ordinary value in " + newInEdge + " updated to " +
									         newInEdgeLabeledValue);
								}
							}
						}
					}
					if (Debug.ON && (updateOrdinaryValue || updateLabeledValue)) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("Added or updated edge " + newInEdge);
						}
					}
				}
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("Removed edge " + inEdge);
					}
				}
				this.g.removeEdge(inEdge);
				//if inEdge is contingent... adjust auxiliary data structures
				if (inEdge.isContingentEdge()) {
					if (newInEdge == null || !newInEdge.isContingentEdge()) {
						throw new IllegalStateException("New edge " + newInEdge + " replacing contingent " + inEdge + " cannot be null or not contingent");
					}
					final LabeledNode A;
					final LabeledNode C;
					if (inEdge.isUpperCase()) {
						C = Yr;
						A = nodeInRCrep;
						upperContingentEdge.put(C, newInEdge);
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("Added edge " + newInEdge + " to upperContingentEdge map as (" + C + ", " + newInEdge + ").");
							}
						}
					} else {
						C = nodeInRCrep;
						A = Yr;
						lowerContingentEdge.put(C, newInEdge);
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("Added edge " + newInEdge + " to lowerContingentEdge map as (" + C + ", " + newInEdge + ").");
							}
						}
					}
					activationNode.put(C, A);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("Added node " + A + " to activationNode map as (" + C + ", " + A + ").");
						}
					}
				}
				if (weakEdges.contains(inEdge) || weakEdges.contains(newInEdge)) {
					weakEdges.remove(inEdge);
					if (newInEdge != null) {
						//If e1 is contingent or wait, then e1 is weak
						if (newInEdge.isContingentEdge() || newInEdge.isWait()) {
							weakEdges.add(newInEdge);//ordinary value must be removed at the end
						} else {
							// e1 is ordinary. if it was update, then its weakness is lost.
							// if e1 comes from a wait and e1 results to be NOT wait because the value becomes positive, e1 is not weak.
							if (updateOrdinaryValue || (inEdge.isWait() && !newInEdge.isWait())) {
								weakEdges.remove(newInEdge);
							} else {
								weakEdges.add(newInEdge);
							}
						}
					}
				}
				if (inEdge.isWait()) {
					waitEdges.remove(inEdge);
				}
				if (newInEdge != null) {
					waitEdges.remove(newInEdge);//this because some updateValue can transform a wait in an ordinary, and it is hard to catch
					if (newInEdge.isWait()) {
						waitEdges.add(newInEdge);
					}
				}

			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("Removed vertex " + nodeInRC);
				}
			}
			this.g.removeVertex(nodeInRC);
		}
	}

	/**
	 * Adds to the given graph the undominated edges and return such added/modified edges as a map {@code ((sourceName, destinationName), edge)}. It also
	 * removes all ordinary dominated edges.<br> This method does:
	 * <ol>
	 *     <li>not initialize the network. So, it is up to the user to call {@link #initAndCheck()} before this method.</li>
	 *     <li>manage rigid components, removing them before the minimization and restoring them before returning the final network.
	 *     Moreover, the edge added for restoring the rigid components are also returned as undominated edges.</li>
	 * </ol>
	 * <p>
	 * This method works also with edges that are specialization of STNUEdge.
	 * </p>
	 *
	 * @param <E>                   the specific kind of edge
	 * @param alreadyDominatedEdges set of edges already dominated. If this procedure remove an edge from the network, it updates this set removing the edge if it is into the set.
	 * @param waitEdges             set of waits of the network. If this procedure remove a wait from the network, it updates this set removing it.
	 *
	 * @return the map {@code ((sourceName, destinationName), edge)} of undominated edges of the network if the network was made dispatchable, null if any error
	 * 	occurred.
	 */
	@Nullable
	@SuppressWarnings({"unchecked", "UnusedReturnValue"})
	final <E extends STNUEdge> Object2ObjectMap<ObjectPair<LabeledNode>, E> makeOrdinaryConstraintMinimalDispatchable(
		@Nonnull ObjectSet<E> alreadyDominatedEdges,
		@Nonnull ObjectList<E> waitEdges) {
		//The rigid components are determined finding the strongly connected components in a predecessor graph that reaches all node.
		//Since we cannot assume that Z reaches all node, we use a fake source, we add edges from fase source to all nodes with 0 weight
		//and, at the end, we remove such fake edges.
		final LabeledNode fakeSource = new LabeledNode("_FAKE_" + System.currentTimeMillis());
		this.g.addVertex(fakeSource);
		//noinspection StringConcatenationMissingWhitespace
		STN.MAKE_NODES_REACHABLE_BY(this.g, fakeSource, 0, "P" + System.currentTimeMillis());
		//
		// 1) Determine a solution that will be used by the following phases.
		//
		final Object2IntMap<LabeledNode> nodePotential = STN.GET_SSSP_BellmanFord(this.g, fakeSource, null);
		if (nodePotential == null) {
			if (Debug.ON) {
				LOG.info("The network is not consistent. Giving up!");
			}
			return null;
		}

		//
		// 2) Using the solution and the predecessor graph of fakeSource, remove all possible rigid components.
		//
		final Object2IntMap<LabeledNode> distanceFromSource = new Object2IntOpenHashMap<>();
		if (Debug.ON) {
			LOG.fine("Finding FakeSource predecessor...");
		}

		final TNGraph<E> fakeSourcePredecessorGraph =
			(TNGraph<E>) STN.GET_STN_PREDECESSOR_SUBGRAPH(this.g, fakeSource, nodePotential, distanceFromSource, null);

		if (fakeSourcePredecessorGraph == null) {
			throw new IllegalStateException("Fake source predecessor graph is null while it shouldn't.");
		}
		if (Debug.ON) {
			LOG.fine("Fake source predecessor done!\nFinding rigid components...");
		}

		final ObjectList<ObjectList<LabeledNode>> rigidComponents = STN.GET_STRONG_CONNECTED_COMPONENTS(fakeSourcePredecessorGraph, fakeSource);

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("Rigid components: " + rigidComponents);
			}
		}

		Object2ObjectMap<LabeledNode, LabeledNode> nodeRepMap = Object2ObjectMaps.emptyMap();//(node->representative) map
		Object2IntMap<LabeledNode> nodeDistanceFromRep = Object2IntMaps.emptyMap(); //(node->int) distance from representative

		if (rigidComponents.size() > 0) {
			if (Debug.ON) {
				LOG.fine("Finding representatives for each rigid component...");
			}
			assert this.g.getZ() != null;
			final Pair<Object2ObjectMap<LabeledNode, LabeledNode>, Object2IntMap<LabeledNode>>
				pairRepDistance = GET_REPRESENTATIVE_RIGID_COMPONENTS(rigidComponents, nodePotential, this.g.getZ(), this.activationNode.values());
			nodeRepMap = pairRepDistance.first();
			nodeDistanceFromRep = pairRepDistance.second();
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("Representative map: " + nodeRepMap);
				}
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("Rappresentative done!\n" + "Found " + rigidComponents.size() + " rigid components.");
				}
			}

			if (Debug.ON) {
				LOG.fine("Collapsing them...");
			}
			collapseRigidComponents(nodeRepMap, nodeDistanceFromRep, alreadyDominatedEdges, waitEdges);

			if (Debug.ON) {
				LOG.fine("Collapse done!");
			}
		}

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("Removing fake node " + fakeSource + " and all its incident edges.");
			}
		}
		this.g.removeVertex(fakeSource);
		nodePotential.removeInt(fakeSource);

		//
		// 3) In each predecessor graph, find the undominated edges.
		//
		if (Debug.ON) {
			LOG.fine("Removed all fake structure done!\nFinding undominated edges...");
		}

		// the format of globalUndominatedEdges is ((sourceName, destinationName), edge)
		final Object2ObjectMap<ObjectPair<LabeledNode>, E> globalUndominatedEdges = new Object2ObjectOpenHashMap<>();

		final TNPredecessorGraph<E> graphToReuse = new TNPredecessorGraph<>();

		for (final LabeledNode node : this.g.getVertices()) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("Finding " + node.getName() + " predecessor and its undominated edges...");
				}
			}
			final TNPredecessorGraph<E> nodePredecessor =
				STN.GET_STN_PRECEDESSOR_SUBGRAPH_OPTIMIZED((TNGraph<E>) this.g, node, nodePotential, distanceFromSource, graphToReuse);
			if (nodePredecessor == null) {
				throw new IllegalStateException("Predecessor graph for node " + node + " is null. ");
			}
			final ObjectList<Pair<LabeledNode, E>> undominatedEdges =
				STN.GET_UNDOMINATED_EDGES(node, nodePredecessor, distanceFromSource, (EdgeSupplier<E>) g.getEdgeFactory());
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("Undominated edges found in " + node + " predecessor graph are: " + undominatedEdges);
				}
			}
			undominatedEdges.forEach((pair) -> {
				final ObjectPair<LabeledNode> s_d = new ObjectPair<>(node, pair.left());
				final E e = globalUndominatedEdges.get(s_d);
				if (e != null) {
					if (e.getValue() != pair.right().getValue()) {
						throw new IllegalStateException(
							"Found a previous dominant edge " + e + " with a different value: old " + e.getValue() + ", new " + pair.right().getValue());
					}
					return;
				}
				globalUndominatedEdges.put(new ObjectPair<>(node, pair.left()), pair.right());
			});
		}
		if (Debug.ON) {
			LOG.fine("Add all found undominated edges: ");
		}
		// add all undominated edges
		for (final ObjectPair<LabeledNode> s_d : globalUndominatedEdges.keySet()) {
			final LabeledNode s = s_d.getFirst();
			final LabeledNode d = s_d.getSecond();
			final E undominatedToAdd = globalUndominatedEdges.get(s_d);
			final E edgeInGToUpdate = (E) this.g.findEdge(s, d);
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("Considering to add undominated edge  " + undominatedToAdd + " to " + edgeInGToUpdate);
				}
			}
			if (edgeInGToUpdate != null) {
				final int undominatedValue = undominatedToAdd.getValue();
				final int edgeInGValue = edgeInGToUpdate.getValue();
				if (edgeInGToUpdate.isContingentEdge()) {
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine(
								"Edge " + edgeInGToUpdate + " is contingent. Its ordinary value is not modified. Possible update would be: " +
								undominatedValue);
						}
					}
					continue;
				}
				if (edgeInGValue == Constants.INT_NULL || edgeInGValue > undominatedValue) {
					//If it is a wait, remove the wait
					if (edgeInGToUpdate.isWait() && undominatedValue < edgeInGToUpdate.getLabeledValue()) {
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("The wait " + edgeInGToUpdate + " is removed by the undominated edge " + undominatedToAdd);
							}
						}
						edgeInGToUpdate.resetLabeledValue();
						waitEdges.remove(edgeInGToUpdate);
					}
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine(
								"Ordinary value " + Constants.formatInt(edgeInGValue) + " in  " + edgeInGToUpdate + " is updated by the undominated value " +
								undominatedValue);
						}
					}
					edgeInGToUpdate.setValue(undominatedValue);
				}
				continue;
			}
			undominatedToAdd.setName(g.getUniqueEdgeName(undominatedToAdd.getName()));
			this.g.addEdge(undominatedToAdd, s, d);
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("Added edge " + undominatedToAdd);
				}
			}
		}

		if (Debug.ON) {
			LOG.fine("Remove all dominated edges...");
		}
		// 4) remove dominated edges
		for (final STNUEdge e : this.g.getEdges()) {
			final int eValue = e.getValue();
			if (eValue == Constants.INT_NULL) {
				// we do not consider it as an ordinary edge. We must preserve.
				continue;
			}
			final LabeledNode s = this.g.getSource(e);
			final LabeledNode d = this.g.getDest(e);
			if (globalUndominatedEdges.get(new ObjectPair<>(s, d)) != null) {
				continue;
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Removing dominated ordinary value in " + e);
				}
			}
			if (e.isContingentEdge() || e.isWait()) {
				e.setValue(Constants.INT_NULL);
			} else {
				this.g.removeEdge(e);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("Removed edge " + e);
					}
				}
			}
			alreadyDominatedEdges.remove(e);
		}

		if (rigidComponents.size() > 0) {
			if (Debug.ON) {
				LOG.fine("Restore rigid components...");
			}
			// 5) Restore the rigid components and add the edge also to the `undominated edges`
			for (final ObjectList<LabeledNode> rc : rigidComponents) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("Rigid components " + rc);
					}
				}

				LabeledNode rep = null;
				//find the representative
				for (final LabeledNode node : rc) {
					if (nodeRepMap.get(node) == node) {
						//found representative
						rep = node;
						break;
					}
				}
				if (rep == null) {
					throw new IllegalStateException("Rigid component " + rc + " has not a representative in the map " + nodeRepMap);
				}
				for (final LabeledNode node : rc) {
					if (node == rep) {
						continue;
					}
					final int nodeDistance = nodeDistanceFromRep.getInt(node);
					//FROM REP TO NODE.
					E e = (E) this.g.makeNewEdge(rep.getName() + "-" + node.getName(), ConstraintType.derived);
					e.setValue(nodeDistance);
					this.g.addEdge(e, rep, node);
					alreadyDominatedEdges.remove(e);
					globalUndominatedEdges.put(new ObjectPair<>(rep, node), e);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("Added edge " + e + " from " + rep + " to " + node);
						}
					}
					//FROM NODE TO REP
					e = (E) this.g.makeNewEdge(node.getName() + "-" + rep.getName(), ConstraintType.derived);
					e.setValue(-nodeDistance);
					this.g.addEdge(e, node, rep);
					globalUndominatedEdges.put(new ObjectPair<>(node, rep), e);
					alreadyDominatedEdges.remove(e);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("Added edge " + e + " from " + node + " to " + rep);
						}
					}
				}
			}
		}
		if (Debug.ON) {
			LOG.fine("Done!");
		}
		return globalUndominatedEdges;
	}

	/**
	 * Auxiliary procedure for NegCycleSTNU algorithm.<br> Applies RELAX^- and LOWER^- rules to the edges going to V.
	 *
	 * @param V       the considered node
	 * @param DeltaC  the deltaC of the contingent link
	 * @param deltaVC the delta between V and C
	 *
	 * @return a list of pair (STNUEdge, w) obtained applying RELAX^- and LOWER^- rules. The STNUEdge is (W, v, V) and w is v+deltaVC.
	 */
	private ObjectList<BasicEntry<STNUEdge>> SRNCycleFinderApplyRL(LabeledNode V, int DeltaC, int deltaVC) {
		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINEST)) {
				STNU.LOG.finest("SRNCycleFinderApplyRL parameters: node V: " + V + ", DeltaC: " + DeltaC + ", deltaVC: " + deltaVC);
			}
		}
		final ObjectList<BasicEntry<STNUEdge>> edges = new ObjectArrayList<>();
		if (deltaVC >= DeltaC) {
			if (Debug.ON) {
				STNU.LOG.finest("deltaVC >= DeltaC, returns no new edges.");
			}
			return edges; // The RELAX^- and LOWER^- rules don't apply
		}
		final STNUEdge lowerCaseEdge = lowerContingentEdge.get(V);
		if (lowerCaseEdge != null) {// == V is a contingent node
			// Apply LOWER^-
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINEST)) {
					STNU.LOG.finest("Apply LOWER considering: " + lowerCaseEdge);
				}
			}
			edges.add(new BasicEntry<>(lowerCaseEdge, Constants.sumWithOverflowCheck(lowerCaseEdge.getLabeledValue(), deltaVC)));
		} else {
			for (final Pair<STNUEdge, LabeledNode> edgeAndSource : g.getInEdgesAndNodes(V)) {
				final STNUEdge e = edgeAndSource.left();
				if (!e.isOrdinaryEdge()) {
					continue;
				}
//				final LabeledNode W = edgeAndSource.right();
				// Apply RELAX^-
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINEST)) {
						STNU.LOG.finest("Apply `RELAX` considering: " + e);
					}
				}
				edges.add(new BasicEntry<>(e, Constants.sumWithOverflowCheck(e.getValue(), deltaVC)));
			}
		}
		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINEST)) {
				STNU.LOG.finest("newApplyRelaxLower. Resulting data for edges to add (W->deltaWC): " + edges);
			}
		}
		return edges;
	}

	/**
	 * Back propagation algorithm to bypass UC edge used by SRNCycleFinder algorithm.
	 *
	 * @param upperCaseEdgeCAToByPass an upper-case edge (C,C:-y,A).
	 * @param globalInfo              global data structure for the algorithm
	 *
	 * @return true if no negative circuit was found (graph is still DC), false otherwise.
	 */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	private boolean SRNCycleFinderBackPropagation(STNUEdge upperCaseEdgeCAToByPass, RULGlobalInfo globalInfo) {
		final ElementStatus statusCurrentUEdge = globalInfo.upperCaseEdgeStatus.get(upperCaseEdgeCAToByPass);
		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINER)) {
				STNU.LOG.finer("Checking upper-case edge: " + upperCaseEdgeCAToByPass + ". Status: " + statusCurrentUEdge);
			}
		}
		if (statusCurrentUEdge == ElementStatus.started) {
			checkStatus.negativeSTNUCycle = STNU.SRNCycleFinderBuildNegCycle(globalInfo, upperCaseEdgeCAToByPass);
			checkStatus.srncKind = STNUCheckStatus.SRNCKind.interruptionCycle;
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINER)) {
					STNU.LOG.finer("Found a started UC edge. Negative cycle: " + checkStatus.negativeSTNUCycle);
				}
			}
			return false;
		}
		if (statusCurrentUEdge == ElementStatus.finished) {
			return true;
		}
		checkStatus.cycles++;// Counts how many times this procedure was called;

		globalInfo.upperCaseEdgeStatus.put(upperCaseEdgeCAToByPass, ElementStatus.started);
		final LabeledNode C = g.getSource(upperCaseEdgeCAToByPass);
		final LabeledNode A = g.getDest(upperCaseEdgeCAToByPass);
		assert C != null;
		assert A != null;
		final int y = -upperCaseEdgeCAToByPass.getLabeledValue();
		final STNUEdge lowerCaseEdgeAC = g.findEdge(A, C);
		assert lowerCaseEdgeAC != null;
		final int x = lowerCaseEdgeAC.getLabeledValue();
		final int DeltaC = y - x;
		if (Debug.ON) {
			assert (DeltaC > 0);
			if (STNU.LOG.isLoggable(Level.FINEST)) {
				STNU.LOG.finest("DeltaC is about contingent link " + A.getName() + C.getName() + ": " + DeltaC);
			}
		}
		final RULLocalInfo localInfo = new RULLocalInfo(Constants.INT_POS_INFINITE);
		// save localInfo in globalInfo for the rul2021Dispatchable version of the algorithm
//		globalInfo.localInfoOfContingentNodes.put(C, localInfo);

		// queue contains the adjusted distance from X to C
		final ExtendedPriorityQueue<LabeledNode> queue = new ExtendedPriorityQueue<>();

		for (final Pair<STNUEdge, LabeledNode> edgeAndSource : g.getInEdgesAndNodes(C)) {
			//consider only ordinary constraints
			final STNUEdge e = edgeAndSource.left();
			if (!e.isOrdinaryEdge()) {
				continue;
			}
			final LabeledNode X = edgeAndSource.right();
			queue.insertOrUpdate(X, Constants.sumWithOverflowCheck(globalInfo.nodePotential.getInt(X), e.getValue()));
			//Save the path to C
			final STNUPath XPath = new STNUPath();
			XPath.set(e).add(upperCaseEdgeCAToByPass);
			localInfo.path.put(X, XPath);
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {LOG.finest("Update generating path for " + X + ": " + XPath);}
			}
		}
		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINEST)) {
				STNU.LOG.finest("Initial queue of nodes to consider for contingent node " + C + ": " + queue);
			}
		}
		boolean continuePropagation = true;
		while (continuePropagation) {
			if (!SRNCycleFinderTryBackProp(upperCaseEdgeCAToByPass, C, DeltaC, queue, globalInfo, localInfo)) {
				//negative cycle is already in STNUStatus
				return false;
			}
			if (!localInfo.unStartedUCEdges.isEmpty()) {
				for (final var entry : localInfo.unStartedUCEdges.entrySet()) {
					final STNUEdge unStartedUCEdge = entry.getValue();
					final LabeledNode X = entry.getKey();
					if (Debug.ON) {
						if (STNU.LOG.isLoggable(Level.FINEST)) {
							STNU.LOG.finest("Considering unStarted edge " + unStartedUCEdge);
						}
					}
					globalInfo.interruptBy.put(upperCaseEdgeCAToByPass, new ObjectObjectImmutablePair<>(unStartedUCEdge, localInfo.path.get(X)));
					if (!SRNCycleFinderBackPropagation(unStartedUCEdge, globalInfo)) {
						//negative cycle is in STNUStatus
						return false;
					}
				}
				globalInfo.interruptBy.remove(upperCaseEdgeCAToByPass);
				queue.clear();
				for (final LabeledNode X : localInfo.unStartedUCEdges.keySet()) {
					if (Debug.ON) {
						if (STNU.LOG.isLoggable(Level.FINEST)) {
							STNU.LOG.finest("From unStarted upper-case edge, update node " + X + " in the queue.");
						}
					}
					final int newKey = Constants.sumWithOverflowCheck(localInfo.distanceFromNodeToContingent.getInt(X), globalInfo.nodePotential.getInt(X));
					queue.insertOrUpdate(X, newKey);
//					localInfo.distanceFromNodeToContingent.put(X, Constants.INT_POS_INFINITE); no more necessary
				}
			} else {
				continuePropagation = false;
			}
		}
		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINEST)) {
				STNU.LOG.finest("Updated localInfo.distanceFrom: " + localInfo.distanceFromNodeToContingent);
			}
		}
		if (localInfo.ccLoop) {
			final Pair<LabeledNode, STNUPath> nodeAndPath = SRNCycleFinderFwdPropNotDC(C, DeltaC, localInfo, globalInfo.nodePotential);
			if (nodeAndPath != null) {
				final STNUPath negCycle = new STNUPath();
				negCycle.set(lowerCaseEdgeAC).add(nodeAndPath.right()).add(localInfo.path.get(nodeAndPath.left()));
				checkStatus.negativeSTNUCycle = negCycle;
				checkStatus.srncKind = STNUCheckStatus.SRNCKind.ccLoop;
				return false;
			}
		}

		boolean addedEdge = false;
		for (final LabeledNode X : localInfo.distanceFromNodeToContingent.keySet()) {
			if (X == C) {
				continue;
			}
			final int deltaXC = localInfo.distanceFromNodeToContingent.getInt(X);
			if (deltaXC == Constants.INT_POS_INFINITE || deltaXC < DeltaC) {
				continue;
			}
			final int newValueXA = deltaXC - y;
			STNUEdge eXA = g.findEdge(X, A);
			if (eXA == null) {
				eXA = this.g.makeNewEdge(X.getName() + "-" + A.getName(), ConstraintType.derived);
				eXA.setValue(newValueXA);
				g.addEdge(eXA, X, A);
				checkStatus.edgePathAnnotation.put(eXA, localInfo.path.get(X));
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINE)) {
						STNU.LOG.fine("Added edge: " + eXA);
						STNU.LOG.finer("Added generation path: " + localInfo.path.get(X));
					}
					//Check if the edge value is equal to the value of the generating path
					final int sum = localInfo.path.get(X).value();
					if (sum != newValueXA) {
						throw new IllegalArgumentException(
							"Edge value of " + eXA + " not equal to " + sum + " for generation path " + localInfo.path.get(X));
					}
				}
				addedEdge = true;
			} else {
				final int oldValue = eXA.getValue();
				final boolean isUpdated = eXA.updateValue(newValueXA);
				if (isUpdated) {
					checkStatus.edgePathAnnotation.put(eXA, localInfo.path.get(X));
					addedEdge = true;
					if (Debug.ON) {
						if (STNU.LOG.isLoggable(Level.FINE)) {
							STNU.LOG.fine("Update edge: " + eXA.getName() + " from " + oldValue + " to " + newValueXA);
							STNU.LOG.finer("Added generation path: " + localInfo.path.get(X));
						}
						//Check if the edge value is equal to the value of the generating path
						final int sum = localInfo.path.get(X).value();
						if (sum != newValueXA) {
							throw new IllegalArgumentException(
								"Edge value of " + eXA + " not equal to " + sum + " for generation path " + localInfo.path.get(X));
						}
					}
				}
			}
		}
		if (addedEdge) {
			final Pair<Object2IntMap<LabeledNode>, STNUPath> pair = SRNCycleFinderUpdatePotential(globalInfo, A);
			globalInfo.nodePotential = pair.left();
			if (globalInfo.nodePotential == null) {
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINER)) {
						STNU.LOG.finer("The determination of new potential from " + A + " found a negative cycle: " + pair.right());
					}
				}
				checkStatus.negativeSTNUCycle = pair.right();
				checkStatus.srncKind = STNUCheckStatus.SRNCKind.loGraphPotFailure;
				return false;
			}
		}
		globalInfo.upperCaseEdgeStatus.put(upperCaseEdgeCAToByPass, ElementStatus.finished);
		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINER)) {
				STNU.LOG.finer("Checking upper-case edge: " + upperCaseEdgeCAToByPass + ". Status: " + statusCurrentUEdge);
			}
		}
		return true;
	}

	/**
	 * Propagates forward from {@code C} along LO-edges checking whether there is a negative-length LO-path from {@code C} to some {@code X} that can be used to
	 * bypass the LC edge {@code (A,c:x,C)}. It accumulates path information in a vector called {@code FwdPath}. A priority queue uses the potential function
	 * {@code globalPotential} to effectively re-weight the LO-edges. As each time-point {@code X} is popped from the queue, the distance from {@code X}  to
	 * {@code C}  that was determined during back-propagation and stored in {@code localInfo.distanceFromNodeToContingent} is compared to {@code Delta_C}. It
	 * was proved that generating an edge to bypass the LC edge using the path from {@code C} to {@code X} will only generate an SRNC  if
	 * {@code localInfo.distanceFromNodeToContingent(X) Delta_C}. If {@code localInfo.distanceFromNodeToContingent(X) Delta_C} and {@code d(C,X) < 0} (i.e., the
	 * forward path from {@code C} to {@code X} has negative length), then an SRNC  has been found, so the algorithm terminates, returning
	 * {@code (X,FwdPath[X])}. Otherwise, forward propagation continues from {@code X} while also accumulating relevant path information. If the queue is
	 * exhausted without finding a way to bypass the LC edge, it returns null.
	 *
	 * @param C               a contingent node
	 * @param DeltaC          the difference y-x of contingent link associated to C
	 * @param localInfo       local info used for field 'distanceFromNodeToContingent' and 'path'
	 * @param globalPotential global potential
	 *
	 * @return (X, P_X) if the path P_X from C to X determines a SRNC ; null otherwise.
	 */
	private Pair<LabeledNode, STNUPath> SRNCycleFinderFwdPropNotDC(LabeledNode C, int DeltaC, RULLocalInfo localInfo,
	                                                               Object2IntMap<LabeledNode> globalPotential) {

		final Object2ObjectMap<LabeledNode, STNUPath> fwdPath = new Object2ObjectOpenHashMap<>();
		final ExtendedPriorityQueue<LabeledNode> localQueue = new ExtendedPriorityQueue<>();

		localQueue.insertOrUpdate(C, -globalPotential.getInt(C));
		fwdPath.put(C, new STNUPath());

		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINEST)) {
				STNU.LOG.finest("Node C: " + C + ", DeltaC: " + DeltaC);
			}
		}
		while (!localQueue.isEmpty()) {
			final BasicEntry<LabeledNode> minEntry = localQueue.extractFirstEntry();
			final LabeledNode X = minEntry.getKey();
			final int Xkey = minEntry.getIntValue();
			final int deltaCX = Xkey + globalPotential.getInt(X);
			final int localDistXC = localInfo.distanceFromNodeToContingent.getInt(X);
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINEST)) {
					STNU.LOG.finest(
						"Node X: " + X + ", Xkey: " + Xkey + ", deltaCX: " + deltaCX + ", distanceFrom(" + C + "): " + localDistXC);
				}
			}
			if (localDistXC < DeltaC) {
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINEST)) {
						STNU.LOG.finest("Node X: " + X + ", distanceFrom: " + localDistXC + " is less than " + DeltaC);
					}
				}
				if (deltaCX < 0) {
					if (Debug.ON) {
						if (STNU.LOG.isLoggable(Level.FINEST)) {
							STNU.LOG.finest("The path CX can reduce-away the LC-edge: Return (" + X + ", " + fwdPath.get(X));
						}
					}
					return new ObjectObjectImmutablePair<>(X, fwdPath.get(X));
				}
			}
			//Else iterate over LO-edges emanating from X
			for (final Pair<STNUEdge, LabeledNode> entry : g.getOutEdgesAndNodes(X)) {
				final STNUEdge e = entry.left();
				final int eValue = STNU.GET_MIN_VALUE_BETWEEN_ORDINARY_AND_LOWERCASE(e);
				if (eValue == Constants.INT_NULL) {// is an upper edge
					continue;
				}
				final LabeledNode Y = entry.right();
				final int deltaCY = Constants.sumWithOverflowCheck(deltaCX, eValue);
				final int newKey = Constants.sumWithOverflowCheck(deltaCY, -globalPotential.getInt(Y));// lower case o no-case value

				if (newKey < localQueue.getPriority(Y)) {
					localQueue.insertOrUpdate(Y, newKey);
					final STNUPath newPath = new STNUPath();
					newPath.add(fwdPath.get(X)).add(e);
					fwdPath.put(Y, newPath);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {LOG.finest("Update generating path for " + Y + ": " + fwdPath);}
					}
				}
			}
		}
		return null;
	}

	/**
	 * Auxiliary procedure for SRNCycleFinder algorithm.
	 *
	 * @param upperCaseEdgeToByPass upper case edge to by-pass
	 * @param C                     contingent time-point
	 * @param DeltaC                the difference y-x of upperCaseEdgeToByPass.
	 * @param Q                     a priority queue containing all incoming ordinary edges of C with priority adjusted with the potential of source node.
	 * @param globalInfo            the global checking data structure
	 * @param localInfo             the local checking data structure
	 *
	 * @return false iff back-propagation from C reveals STNU to be non-DC. If false, {@link STNUCheckStatus#negativeSTNUCycle} contains the negative cycle.
	 */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	private boolean SRNCycleFinderTryBackProp(@Nonnull STNUEdge upperCaseEdgeToByPass, @NotNull LabeledNode C, int DeltaC,
	                                          @Nonnull ExtendedPriorityQueue<LabeledNode> Q, @Nonnull RULGlobalInfo globalInfo,
	                                          @Nonnull RULLocalInfo localInfo) {

		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINEST)) {
				STNU.LOG.finest(
					"SRNCycleFinderTryBackProp parameters: Upper case edge to bypass: " + upperCaseEdgeToByPass + ", Node C: " + C + ", DeltaC: " + DeltaC +
					", Queue: " + Q);
			}
		}
		localInfo.unStartedUCEdges = new Object2ObjectOpenHashMap<>();
		while (!Q.isEmpty()) {
			final BasicEntry<LabeledNode> minQueueEntry = Q.extractFirstEntry();
			final LabeledNode X = minQueueEntry.getKey();
			final int Xkey = minQueueEntry.getIntValue();
			final int deltaXC = Xkey - globalInfo.nodePotential.getInt(X);
			final STNUEdge upperCaseEdgeFromX = globalInfo.upperCaseEdgeFromActivation.get(X);
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINEST)) {
					STNU.LOG.finest(
						"Considering node " + X + ", Xkey: " + Xkey + ", deltaXC: " + deltaXC + ", possible upper-case edge with " + X + " as activation: " +
						upperCaseEdgeFromX + ", distance of " + X + " from C: " + Constants.formatInt(localInfo.distanceFromNodeToContingent.getInt(X)));
				}
			}
			localInfo.distanceFromNodeToContingent.put(X, deltaXC);
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINEST)) {
					STNU.LOG.finest("New distance of " + X + ": " + deltaXC);
				}
			}
			if (deltaXC >= DeltaC) {
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINEST)) {
						STNU.LOG.finest("New distance of " + X + " is greater than DeltaC " + DeltaC);
					}
				}
				continue;
			}
			if (X == C) {
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINEST)) {
						STNU.LOG.finest("Node X == C: " + X + "==" + C);
					}
				}

//				*** Case 1: CC loop of length δxc < ∆C ***
//              The following if is comment out because if true, it would be a neg cycle in LO-graph, which update potential would have found.
//				I maintain the code till the end of tests.
//				if (weightEdgeXC < 0) {
//					if (Debug.ON) {
//						if (STNU.LOG.isLoggable(Level.FINEST)) {
//							STNU.LOG.finest("OneStepBackProp. Node X == C and deltaXC < 0. Negative cycle. Giving up! deltaC: " + weightEdgeXC);
//						}
//					}
//					return false;
//				}
				localInfo.ccLoop = true;
				if (Debug.ON) {
					STNU.LOG.finest("OneStepBackProp. ccLoop true");
				}
				continue;
			}

			if (upperCaseEdgeFromX != null) {
				final ElementStatus ucFromXStatus = globalInfo.upperCaseEdgeStatus.get(upperCaseEdgeFromX);
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINEST)) {
						STNU.LOG.finest("Status of upper-case edge having " + X + " as activation. Edge: " + upperCaseEdgeFromX + ". Status: " + ucFromXStatus);
					}
				}
				if (ucFromXStatus == ElementStatus.unStarted) {
//					*** Case 2: upperCaseEdgeFromX is an unStarted UC-edge ***
					localInfo.unStartedUCEdges.put(X, upperCaseEdgeFromX);
					continue;
				}
				if (ucFromXStatus == ElementStatus.started) {
//					*** Case 3: Cycle of interruptions: not DC ***
					globalInfo.interruptBy.put(upperCaseEdgeToByPass, new ObjectObjectImmutablePair<>(upperCaseEdgeFromX, localInfo.path.get(X)));
					checkStatus.negativeSTNUCycle = STNU.SRNCycleFinderBuildNegCycle(globalInfo, upperCaseEdgeFromX);
					checkStatus.srncKind = STNUCheckStatus.SRNCKind.interruptionCycle;
					if (Debug.ON) {
						if (STNU.LOG.isLoggable(Level.FINER)) {
							STNU.LOG.finer("Found case 3. Negative cycle: " + checkStatus.negativeSTNUCycle);
						}
					}
					return false;
				}
			}
//			*** Case 4:  Back-prop. along LO-edges ***
			for (final BasicEntry<STNUEdge> entry : SRNCycleFinderApplyRL(X, DeltaC, deltaXC)) {
				final STNUEdge edgeWC = entry.getKey();
				final int deltaWC = entry.getIntValue();
				final LabeledNode W = this.g.getSource(edgeWC);
				assert W != null;
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINEST)) {
						STNU.LOG.finest("Considering (e, deltaWC): " + entry + ". The current edge WC: " + edgeWC + ". Node W: " + W);
					}
				}
//				STNUEdge wc = g.findEdge(W, C);
//				int wcValue = Constants.INT_NULL;
//				if (wc != null && (wcValue = wc.getValue()) == Constants.INT_NULL) {// it does not represent an ordinary edge, so ignore it!
//					wc = null;
//				}
//				//Luke: I deleted the check against ord edge wcValue since back prop would eventually find any shorter ord edge.
//				if (wc == null || deltaWC < wcValue) {
				final int newKey = Constants.sumWithOverflowCheck(deltaWC, globalInfo.nodePotential.getInt(W));
				if (Debug.ON) {
					final Status Wstatus = Q.getStatus(W);
					if (STNU.LOG.isLoggable(Level.FINEST)) {
						STNU.LOG.finest(
							"CurrentDistance(" + W + "): " + localInfo.distanceFromNodeToContingent.getInt(W)
							+ "; new deltaWC: " + deltaWC
							+ "; Potential(" + W + "): " + globalInfo.nodePotential.getInt(W)
							+ "; newKey(" + W + "): " + newKey + "; queue status(" + W + "): " + Wstatus);
					}
				}

				/*
				 * Since the queue associated with a contingent TP is erased coming back from the management
				 * of another contingent TP, the only way to check if a node was already managed
				 * is to check if localInfo.distanceFromNodeToContingent.getInt(W) is different from ∞.
				 * In case localInfo.distanceFromNodeToContingent.getInt(W) is finite and <= the current deltaWC,
				 * then the node can be ignored because its distance and path are already correct.
				 *
				 * Otherwise, the node is added to the queue, and this will determine an SRNC  (it will be caught later).
				 */
				if (localInfo.distanceFromNodeToContingent.getInt(W) <= deltaWC) {
					if (Debug.ON) {
						final Status Wstatus = Q.getStatus(W);
						if (STNU.LOG.isLoggable(Level.FINEST)) {
							STNU.LOG.finest(
								"Potential(" + W + "): " + globalInfo.nodePotential.getInt(W)
								+ ", deltaWC: " + deltaWC + ", newKey of " + W + ": " + newKey + ", queue status of " + W +
								": " + Wstatus);
						}
					}
					continue; //the current distance is already minimal
				}
				if (newKey < Q.getPriority(W)) {//this is necessary to manage new added nodes
					Q.insertOrUpdate(W, newKey);
					final STNUPath newPath = new STNUPath();
					newPath.set(edgeWC).add(localInfo.path.get(X));
					localInfo.path.put(W, newPath);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {LOG.finest("Update generating path for " + W + ": " + newPath);}
					}
				}
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINEST)) {
						STNU.LOG.finest("Queue after adding " + W + ": " + Q);
					}
				}
//					}
			}

		}
		return true;
	}

	/**
	 * @param globalInfo it contains out-of-date potential and the node path info
	 * @param A          an activation time point
	 *
	 * @return the pair (update potential, negCycle). If update potential is not null, it is the new one. Otherwise, a negative cycle was determined (second
	 * 	term).
	 */
	@Nonnull
	private Pair<Object2IntMap<LabeledNode>, STNUPath> SRNCycleFinderUpdatePotential(RULGlobalInfo globalInfo, LabeledNode A) {
		final Object2IntMap<LabeledNode> h = globalInfo.nodePotential;
		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINEST)) {
				STNU.LOG.finest("SRNCycleFinderUpdatePotential parameters with h: " + h + "\n and A: " + A);
			}
		}
		final Object2IntMap<LabeledNode> newH = new Object2IntOpenHashMap<>(h);
		final ExtendedPriorityQueue<LabeledNode> localQueue = new ExtendedPriorityQueue<>();
		final Object2ObjectMap<LabeledNode, STNUPath> path = new Object2ObjectOpenHashMap<>();

		localQueue.insertOrUpdate(A, 0);
		LabeledNode U;
		int delta;
		STNUPath negCycle;
		while (!localQueue.isEmpty()) {
			final BasicEntry<LabeledNode> entry = localQueue.extractFirstEntry();
			final LabeledNode V = entry.getKey();
			//Back-prop along ordinary edges ending at V
			for (final Pair<STNUEdge, LabeledNode> edgeAndSource : g.getInEdgesAndNodes(V)) {
				final STNUEdge eUV = edgeAndSource.left();
				if (!eUV.isOrdinaryEdge()) {
					continue;
				}
				U = edgeAndSource.right();
				delta = eUV.getValue();
				negCycle = SRNCycleFinderUpdateVal(eUV, U, V, delta, h, newH, localQueue, path);
				if (negCycle != null) {
					return new ObjectObjectImmutablePair<>(null, negCycle);
				}
			}
			if (V.isContingent()) {
				final STNUEdge lowerCaseEdgeAV = lowerContingentEdge.get(V);
				final LabeledNode activation = activationNode.get(V);
				final int x = lowerCaseEdgeAV.getLabeledValue();
				negCycle = SRNCycleFinderUpdateVal(lowerCaseEdgeAV, activation, V, x, h, newH, localQueue, path);
				if (negCycle != null) {
					return new ObjectObjectImmutablePair<>(null, negCycle);
				}
			}
		}
		return new ObjectObjectImmutablePair<>(newH, null);
	}

	/**
	 * Determines the moat edges for the lower-case edge associated to lower-case edge {@code (actNode, contNode:lowerCaseValue, contNode)}, using Dijkstra
	 * technique based on {@code nodePotential}. Then, returns the bypass edges obtained combining the lower-case edge and the found moat edges.
	 *
	 * @param currentLowerEdge the considered lower-case edge. No check is done.
	 * @param nodePotential    a solution of the ordinary-lower-case graph. No check is done.
	 *
	 * @return list of bypass edge data as a list of #EdgeData object
	 */
	private ObjectList<EdgeData> fastDispatchSTNULowerCaseForwardPropagation(@Nonnull STNUEdge currentLowerEdge,
	                                                                         @Nonnull Object2IntMap<LabeledNode> nodePotential) {

		if (!currentLowerEdge.isLowerCase()) {
			throw new IllegalStateException("Edge " + currentLowerEdge + " is not a lower-case edge");
		}
		final LabeledNode A = g.getSource(currentLowerEdge);
		final LabeledNode C = g.getDest(currentLowerEdge);
		assert A != null;
		assert C != null;
		final int x = currentLowerEdge.getLabeledValue();

		final ObjectList<EdgeData> bypassEdgeData = new ObjectArrayList<>();
		final ExtendedPriorityQueue<LabeledNode> nodeQueue = new ExtendedPriorityQueue<>();
		nodeQueue.insertOrUpdate(C, -nodePotential.getInt(C));

		while (!nodeQueue.isEmpty()) {
			final BasicEntry<LabeledNode> entry = nodeQueue.extractFirstEntry();
			final LabeledNode node = entry.getKey();
			final int nodeKey = entry.getIntValue();

			final int distFromC = Constants.sumWithOverflowCheck(nodeKey, nodePotential.getInt(node));
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINER)) {
					STNU.LOG.finer("Considering node " + node + " having distance from " + C + ": " + Constants.formatInt(distFromC));
				}
			}
			if (distFromC < 0) {
				// found a moat edge
				// add data for the bypass edge
				final int byPassValue = Constants.sumWithOverflowCheck(x, distFromC);
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINER)) {
						STNU.LOG.finer("Found a moat edge. Bypass edge data: source node " + A + ", destination node " + node + " edge value: " +
						               Constants.formatInt(byPassValue));
					}
				}
				bypassEdgeData.add(new EdgeData(A, node, byPassValue));
				continue;
			}
			for (final Pair<STNUEdge, LabeledNode> pairEdgeDest : g.getOutEdgesAndNodes(node)) {
				final STNUEdge e = pairEdgeDest.left();
				final LabeledNode adjNode = pairEdgeDest.right();
				if (adjNode == A || adjNode == C) {
					continue;// don't go back to the activation time point!
				}
				final int eValue = STNU.GET_MIN_VALUE_BETWEEN_ORDINARY_AND_LOWERCASE(e);
				if (eValue == Constants.INT_NULL) {
					continue;// it is not a lower-case or ordinary edge
				}
				final int adjNodeNewKey = Constants.sumWithOverflowCheck(Constants.sumWithOverflowCheck(distFromC, eValue), -nodePotential.getInt(adjNode));
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINEST)) {
						STNU.LOG.finest("Insert or update '" + adjNode.getName() + "' node in the queue with priority " + Constants.formatInt(adjNodeNewKey));
					}
				}
				nodeQueue.insertOrUpdate(adjNode, adjNodeNewKey);
			}
		}
		return bypassEdgeData;
	}

	/**
	 * Simple method to manage command line parameters using args4j library.
	 *
	 * @param args the input args
	 *
	 * @return false if a parameter is missing, or it is wrong. True if every parameter is given in a right format.
	 */
	@SuppressWarnings("deprecation")
	private boolean manageParameters(String[] args) {
		final CmdLineParser parser = new CmdLineParser(this);
		try {
			// parse the arguments.
			parser.parseArgument(args);

			if (fInput == null) {
				try (final Scanner consoleScanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
					System.out.print("Insert STNU file name (absolute file name): ");
					final String fileName = consoleScanner.next();
					fInput = new File(fileName);
				}
			}
			if (!fInput.exists()) {
				throw new CmdLineException(parser, "Input file does not exist.");
			}

			if (fOutput != null) {
				if (fOutput.isDirectory()) {
					throw new CmdLineException(parser, "Output file is a directory.");
				}
				if (!fOutput.getName().endsWith(".stnu")) {
					if (!fOutput.renameTo(new File(fOutput.getAbsolutePath() + ".stnu"))) {
						final String m = "File " + fOutput.getAbsolutePath() + " cannot be renamed.";
						STNU.LOG.severe(m);
						throw new IllegalStateException(m);
					}
				}
				if (fOutput.exists()) {
					if (!fOutput.delete()) {
						final String m = "File " + fOutput.getAbsolutePath() + " cannot be deleted.";
						STNU.LOG.severe(m);
						throw new IllegalStateException(m);
					}
				}
			}
		} catch (CmdLineException e) {
			// if there's a problem in the command line, you'll get this exception. this will report an error message.
			System.err.println(e.getMessage());
			System.err.println("java " + getClass().getName() + " [options...] arguments...");
			// print the list of available options
			parser.printUsage(System.err);
			System.err.println();

			// print option sample. This is useful some time
			System.err.println(
				"Example: java -jar CSTNU-*.jar " + getClass().getName() + " " + parser.printExample(OptionHandlerFilter.REQUIRED) + " file_name");
			return false;
		}
		return true;
	}

	/**
	 * Recursive procedure for {@link #applyMorris2014()} method. Calculates distance backwards from potential moat edges.
	 *
	 * @param X                  must not be null
	 * @param negativeNodeStatus a map (node, nodeStatus) of the negative node status. It must contain {@code X}.
	 *
	 * @return true, if relevant negative edges coming into {@code X} can be reduced away (the network is still consistent), false otherwise.
	 */
	private boolean morris2014DCBackpropagation(@Nonnull LabeledNode X, @Nonnull Object2ObjectMap<LabeledNode, ElementStatus> negativeNodeStatus) {

		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINER)) {
				STNU.LOG.finer("Node " + X + " analysis started.");
			}
		}
		final ElementStatus sourceStatus = negativeNodeStatus.get(X);
		if (sourceStatus == null) {
			throw new IllegalStateException("Source status of " + X + " cannot be null.");
		}
		if (sourceStatus == ElementStatus.started) {
			if (Debug.ON) {
				STNU.LOG.info("Found a semi-reducible negative cycle since source node " + X + " was already met.");
			}
			return false;
		}
		if (sourceStatus == ElementStatus.finished) {
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINEST)) {
					STNU.LOG.finest("Source node  " + X + " was already analyzed.");
				}
			}
			return true;
		}
		checkStatus.cycles++;// Counts how many times this procedure was called;
		negativeNodeStatus.put(X, ElementStatus.started);

		final Object2IntOpenHashMap<LabeledNode> distance = new Object2IntOpenHashMap<>(g.getVertexCount());
		distance.defaultReturnValue(Constants.INT_POS_INFINITE);
		g.getVertices().forEach((node) -> distance.put(node, Constants.INT_POS_INFINITE));//to guarantee that
		//if one list the distance map, he can find all nodes.
		distance.put(X, 0);

		final ExtendedPriorityQueue<LabeledNode> queue = new ExtendedPriorityQueue<>();
		for (final STNUEdge e2Source : g.getInEdges(X)) {
			final int v = STNU.GET_UPPER_OR_ORDINARY_VALUE(e2Source);
			if (v >= 0) {
				continue;
			}
			final LabeledNode s = g.getSource(e2Source);
			assert s != null;
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINEST)) {
					STNU.LOG.finest("Queue.add(" + s.getName() + ", " + v + ")");
				}
			}
			distance.put(s, v);
			queue.insertOrUpdate(s, v);
		}
		while (!queue.isEmpty()) {
			final LabeledNode U = queue.extractFirst();
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINEST)) {
					STNU.LOG.finest("Analyze X node = " + X + " and U node = " + U);
				}
			}
			final int distU = distance.getInt(U);
			if (distU >= 0) {
				STNUEdge newE = g.findEdge(U, X);
				if (newE == null) {
					newE = this.g.makeNewEdge(U.getName() + "-" + X.getName(), ConstraintType.derived);
					newE.setValue(distU);
					g.addEdge(newE, U, X);
					if (Debug.ON) {
						if (STNU.LOG.isLoggable(Level.FINER)) {
							STNU.LOG.finer("Added " + newE + ". Continue with the next node.");
						}
					}
				} else {
					if (distU < newE.getValue() || newE.getValue() == Constants.INT_NULL) {
						newE.setValue(distU);
						if (Debug.ON) {
							if (STNU.LOG.isLoggable(Level.FINER)) {
								STNU.LOG.finer("Adjusted " + newE + ". Continue with the next node.");
							}
						}
					}
				}
				continue;
			}
			if (negativeNodeStatus.containsKey(U)) {
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINER)) {
						STNU.LOG.finer("Node " + U + " is negative. Analyze it recursively.");
					}
				}
				if (!morris2014DCBackpropagation(U, negativeNodeStatus)) {
					return false;
				}
			}

			for (final STNUEdge eVU : g.getInEdges(U)) {
				// it is possible that there exist edges like A---(c:4),5--->C
				final LabeledNode V = g.getSource(eVU);
				if (eVU.isLowerCase()) {
					if (X != V) {
						assert V != null;
						STNU.MORRIS2014_UPDATE_DISTANCE(V, eVU.getLabeledValue(), distU, queue, distance);
					} else {
						if (Debug.ON) {
							if (STNU.LOG.isLoggable(Level.FINER)) {
								STNU.LOG.finer("Found an unsuitable edge " + eVU);
							}
						}
					}
				}
				final int valueVU = eVU.getValue();
				if (valueVU < 0) {// || valueVU == Constants.INT_NULL is subsumed by < 0
					continue;
				}
				assert V != null;
				STNU.MORRIS2014_UPDATE_DISTANCE(V, valueVU, distU, queue, distance);
			}
		}
		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINER)) {
				STNU.LOG.finer("Node " + X + " analysis completed.");
			}
		}
		negativeNodeStatus.put(X, ElementStatus.finished);
		return true;
	}

	/**
	 * Recursive procedure for {@link #applyMorris2014Dispatchable()} method. Calculates distance backwards from potential moat edges and adds intermediate
	 * constraints.
	 * <p>Regarding waits, it adds waits
	 * <math xmlns = "http://www.w3.org/1998/Math/MathML"><mrow><mo>(</mo><mi>V</mi><mo>,</mo>
	 * <mi>C</mi><mo>:</mo><mo>-</mo><mi>v</mi><mo>,</mo><mi>A</mi><mo>)</mo></mrow></math>
	 * with
	 * <math xmlns="http://www.w3.org/1998/Math/MathML">
	 * <mrow>
	 * <mi>x</mi>
	 * <mo>&lt;</mo>
	 * <mi>v</mi>
	 * <mo>≤</mo>
	 * <mi>y</mi>
	 * </mrow>
	 * </math>
	 * where
	 * <math xmlns="http://www.w3.org/1998/Math/MathML">
	 * <mrow>
	 * <mi>x</mi><mo>,</mo><mi>y</mi>
	 * </mrow>
	 * </math>
	 * are the bounds of the relative contingent link
	 * <math xmlns="http://www.w3.org/1998/Math/MathML">
	 * <mrow><mo>(</mo><mi>A</mi><mo>,</mo><mi>x</mi><mo>,</mo><mi>y</mi><mo>,</mo><mi>C</mi><mo>)</mo></mrow></math>
	 *
	 * @param X                     must not be null
	 * @param generateUC            true if the procedure has to generate only waits
	 * @param negativeNodeStatusMap a map of (node, nodeStatus) of the negative nodes. It must contain {@code X}.
	 *
	 * @return true, if relevant negative edges coming into {@code X} can be reduced away (the network is still consistent), false otherwise.
	 */
	private boolean morris2014DispatchableDCBackpropagation(@Nonnull final LabeledNode X, final boolean generateUC,
	                                                        @Nonnull Object2ObjectMap<LabeledNode, STNUEdge> activationNodeMap,
	                                                        @Nonnull Object2ObjectMap<LabeledNode, ElementStatus> negativeNodeStatusMap) {

		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINER)) {
				STNU.LOG.finer("Source node " + X + " starts with 'generateUC' " + generateUC);
			}
		}
		final ElementStatus sourceStatus = negativeNodeStatusMap.get(X);
		if (sourceStatus == null) {
			throw new IllegalStateException("Source status of " + X + " cannot be null.");
		}
		if (sourceStatus == ElementStatus.started) {
			if (Debug.ON) {
				STNU.LOG.info("Found a semi-reducible negative cycle since source node " + X + " was already met.");
			}
			return false;
		}
		if (sourceStatus == ElementStatus.finished) {
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINER)) {
					STNU.LOG.finer("Source node " + X + " was already analyzed. Next!");
				}
			}
			return true;
		}

		int contingentLowerBoundNegated = Constants.INT_NULL;
		int contingentUpperBoundNegated = Constants.INT_NULL;
		ALetter contingentNodeCase = null;
		LabeledNode contingentNode = null;

		if (sourceStatus == ElementStatus.halfFinished) {
			if (generateUC) {
				throw new IllegalStateException("Node " + X + " was already evaluated as activation timepoint!");
			}
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINER)) {
					STNU.LOG.finer("Source node " + X + " was half-analyzed. Now, it will be completed.");
				}
			}
			final STNUEdge upperCaseEdge = activationNodeMap.get(X);
			if (upperCaseEdge == null) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("Source node " + X + " is not an activation timepoint but the parameter generateUC is true. Ignore");
					}
				}
				return true;//it o never execute
			}
			contingentNode = g.getSource(upperCaseEdge);
		}

		checkStatus.cycles++;// Counts how many times this procedure was called;
		negativeNodeStatusMap.put(X, ElementStatus.started);

		final Object2IntOpenHashMap<LabeledNode> distance = new Object2IntOpenHashMap<>(g.getVertexCount());
		distance.defaultReturnValue(Constants.INT_POS_INFINITE);
		g.getVertices().forEach((node) -> distance.put(node, Constants.INT_POS_INFINITE));//to guarantee that
		//if one list the distance map, he can find all nodes.

		distance.put(X, 0);

		final ExtendedPriorityQueue<LabeledNode> queue = new ExtendedPriorityQueue<>();

		//If generateUC is true, X must be an activation timepoint
		if (generateUC) {
			final STNUEdge upperCaseEdge = activationNodeMap.get(X);
			if (upperCaseEdge == null) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("Source node " + X + " is not an activation timepoint but the parameter generateUC is true. Ignore");
					}
				}
				return true;//it should never execute
			}
			contingentNode = g.getSource(upperCaseEdge);
			contingentUpperBoundNegated = upperCaseEdge.getLabeledValue();
			distance.put(contingentNode, contingentUpperBoundNegated);
			assert contingentNode != null;
			queue.insertOrUpdate(contingentNode, contingentUpperBoundNegated);
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINER)) {
					STNU.LOG.finer(X + " is the source node of a contingent link. " + "So, only the contingent timepoint is added to the queue: (" +
					               contingentNode.getName() + ", " + contingentUpperBoundNegated + ")");
				}
			}
			final STNUEdge lowerCaseEdge = g.findEdge(X, contingentNode);
			assert lowerCaseEdge != null;
			contingentLowerBoundNegated = -lowerCaseEdge.getLabeledValue();
			contingentNodeCase = new ALetter(contingentNode.getName());
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINEST)) {
					STNU.LOG.finest("The negated lower bound of the contingent link is: " + contingentLowerBoundNegated);
				}
			}
		} else {
			//otherwise, all incoming negative edges must be inserted
			for (final ObjectObjectImmutablePair<STNUEdge, LabeledNode> inEdgeSource : g.getInEdgesAndNodes(X)) {
				final STNUEdge inEdge = inEdgeSource.left();
				final int v = inEdge.getValue();
				if (v == Constants.INT_NULL || v >= 0) {//v==Constants.INT_NULL in case of contingent upper-case
					continue;
				}
				final LabeledNode s = inEdgeSource.right();
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINEST)) {
						STNU.LOG.finest("Add (" + s.getName() + ", " + v + ") to the queue.");
					}
				}
				distance.put(s, v);
				queue.insertOrUpdate(s, v);
			}
		}
		STNUEdge newE;
		while (!queue.isEmpty()) {
			final LabeledNode U = queue.extractFirst();
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINEST)) {
					STNU.LOG.finest("Analyze node " + X + " and its backward node " + U);
				}
			}
			final int distU = distance.getInt(U);
			if (distU >= 0) {
				//insert or update ordinary edge
				newE = g.findEdge(U, X);
				if (newE == null) {
					newE = this.g.makeNewEdge(U.getName() + "-" + X.getName(), ConstraintType.derived);
					newE.setValue(distU);
					g.addEdge(newE, U, X);
					if (Debug.ON) {
						if (STNU.LOG.isLoggable(Level.FINER)) {
							STNU.LOG.finer("Added " + newE + ". Continue with the next node.");
						}
					}
				} else {
					if (newE.isContingentEdge()) {
						//Consider the case that A is activation and  generateUC is false
						//and there is A<--C:-5--C---4--->X
						//              <-------(-3)-----
						//Then, C is in the queue with value 1 and the edge A<--C:-5--C would be
						//updated as A<--(1),(C:-5)--C
						continue;
					}
					//noinspection unused
					final boolean status = newE.updateValue(distU);
					if (Debug.ON) {
						if (status) {
							if (STNU.LOG.isLoggable(Level.FINER)) {
								STNU.LOG.finer("Adjusted " + newE + ". Continue with the next node.");
							}
						}
					}
				}
				continue;
			}
			//distU is negative
			if (negativeNodeStatusMap.containsKey(U)) {
				//U is a negative node, Analyze it recursively
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINER)) {
						STNU.LOG.finer("Node " + U + " is negative. Analyze it recursively.");
					}
				}
				boolean status;
				if (activationNodeMap.containsKey(U)) {
					status = morris2014DispatchableDCBackpropagation(U, true, activationNodeMap, negativeNodeStatusMap);
					if (!status) {
						return false;
					}
				}
				status = morris2014DispatchableDCBackpropagation(U, false, activationNodeMap, negativeNodeStatusMap);
				if (!status) {
					return false;
				}
			}
			//distU is negative and U is not a negative node, or it has been analysed.
			if (generateUC && U != contingentNode && distU >= contingentUpperBoundNegated) {
				//it is necessary to add intermediate constraints that can be a wait or an ord. constraint derived by a
				//simplified wait
				newE = g.findEdge(U, X);
				if (newE == null) {
					newE = this.g.makeNewEdge(U.getName() + "-" + X.getName(), ConstraintType.derived);
					g.addEdge(newE, U, X);
				}
				if (distU >= contingentLowerBoundNegated) {
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest(
								"The wait value " + distU + " is smaller than the minimum duration of contingent span " + (-contingentUpperBoundNegated) +
								". Wait transformed as ordinary constraint.");
						}
					}
					newE.updateValue(distU);
					continue;
				}
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINEST)) {
						STNU.LOG.finest("It is necessary to insert/update the intermediate wait edge between " + U + " and " + X + " with value " + distU);
					}
				}
				final boolean updated = newE.updateWait(distU, contingentNodeCase);
				if (updated) {
					if (Debug.ON) {
						if (STNU.LOG.isLoggable(Level.FINE)) {
							STNU.LOG.fine("Added intermediate wait " + newE + ".");
						}
					}
					if (newE.getValue() >= contingentLowerBoundNegated) {
						newE.setValue(Constants.INT_NULL);
					}
				}
			} else {
				if (!generateUC && U != contingentNode) {
					newE = g.findEdge(U, X);
					if (newE == null) {
						newE = this.g.makeNewEdge(U.getName() + "-" + X.getName(), ConstraintType.derived);
						g.addEdge(newE, U, X);
						newE.setValue(distU);
						if (Debug.ON) {
							if (STNU.LOG.isLoggable(Level.FINE)) {
								STNU.LOG.fine("Added intermediate ordinary " + newE + ".");
							}
						}
					} else {
						final boolean status = newE.updateValue(distU);
						if (Debug.ON) {
							if (status) {
								if (STNU.LOG.isLoggable(Level.FINE)) {
									STNU.LOG.fine("Updated " + newE + " as intermediate ordinary constraint.");
								}
							}
						}
					}
				}
			}
			for (final ObjectObjectImmutablePair<STNUEdge, LabeledNode> VUpair : g.getInEdgesAndNodes(U)) {
				final STNUEdge eVU = VUpair.left();
				if (eVU.isUpperCase()) {
					continue;
				}
				final LabeledNode V = VUpair.right();
				//If X is activation tp for a contingent link, then cannot back-prop along LC-edge for that contingent link
				if (!generateUC || !eVU.isLowerCase() || V != X) {
					final int valueVU = (eVU.isLowerCase()) ? eVU.getLabeledValue() : eVU.getValue();
					if (valueVU < 0) {
						continue;
					}
					STNU.MORRIS2014_UPDATE_DISTANCE(V, valueVU, distU, queue, distance);
				}
			}
		}
		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINER)) {
				STNU.LOG.finer("Source node " + X + " completed.");
			}
		}
		if (generateUC) {
			negativeNodeStatusMap.put(X, ElementStatus.halfFinished);
		} else {
			negativeNodeStatusMap.put(X, ElementStatus.finished);
		}
		return true;
	}

	/**
	 * RUL<sup>-</sup> algorithm: applyRelaxLower
	 *
	 * @param W      a node
	 * @param C      a contingent time-point
	 * @param DeltaC the y-x value of the contingent link associated to C
	 *
	 * @return The set of all edges (V,v,C) obtained by applying RELAX^- or LOWER^- to ordinary or lower-case edge (V,u,W) and ordinary edge (W,l,C).
	 */
	@SuppressWarnings("StringConcatenationMissingWhitespace")
	private Object2IntMap<LabeledNode> rul2018ApplyRelaxLower(LabeledNode W, LabeledNode C, int DeltaC) {
		final Object2IntMap<LabeledNode> newEdges = new Object2IntOpenHashMap<>();
		final STNUEdge eWC = g.findEdge(W, C);
		final int deltaWC = (eWC == null || !eWC.isOrdinaryEdge()) ? Constants.INT_POS_INFINITE : eWC.getValue();
		if (deltaWC >= DeltaC) {
			return newEdges;
		}
		if (W.isContingent()) {
			final LabeledNode Aw = activationNode.get(W);
			assert Aw != null;
			assert Aw != C;
			final STNUEdge lowerEdgeAW = lowerContingentEdge.get(W);
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINEST)) {
					STNU.LOG.finest("W is contingent: " + W.getName() + ". Activation node is " + Aw + ". x: " + lowerEdgeAW.getLabeledValue() + " Added: (" +
					                Aw.getName() + ", " + lowerEdgeAW.getLabeledValue() + deltaWC + ")");
				}
			}
			newEdges.put(Aw, Constants.sumWithOverflowCheck(lowerEdgeAW.getLabeledValue(), deltaWC));
			return newEdges;
		}

		for (final Pair<STNUEdge, LabeledNode> entry : g.getInEdgesAndNodes(W)) {
			final STNUEdge eVW = entry.left();
			assert !Debug.ON || !eVW.isLowerCase();
			final LabeledNode V = entry.right();
			if (V == C)// faster than equalsByName
			{
				continue;
			}
			final int eVWvalue = eVW.getValue();
			if (eVWvalue == Constants.INT_NULL) // it is an upper-case
			{
				continue;
			}
			newEdges.put(V, Constants.sumWithOverflowCheck(eVWvalue, deltaWC));
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINEST)) {
					STNU.LOG.finest(
						"W is not contingent: " + W.getName() + ". V:" + V.getName() + ". delta_vw: " + eVWvalue + " Added: (" + V.getName() + ", " + eVWvalue +
						deltaWC + ")");
				}
			}
		}
		return newEdges;
	}

	/**
	 * RUL<sup>-</sup> algorithm: applyUpper procedure.
	 * <p>
	 * Adds to the graph all new edges (V,w,A) obtained by applying UPPER<sup>-</sup> to any ordinary edge (V,v,C) and the UC-edge (C,C:-y,A).
	 *
	 * @param C a contingent time point
	 * @param A the activation time point of contingent link (A,C)
	 * @param x the lower bound of contingent link (A,C)
	 * @param y the upper bound of contingent link (A,C)
	 */
	private void rul2018ApplyUpper(LabeledNode A, int x, int y, LabeledNode C) {
		final int DeltaC = y - x;
		for (final Pair<STNUEdge, LabeledNode> entry : g.getInEdgesAndNodes(C)) {
			final STNUEdge eVC = entry.left();
			final int v = eVC.getValue();
			if (v == Constants.INT_NULL) {
				continue;
			}
			final LabeledNode V = entry.right();
			STNUEdge eVA = g.findEdge(V, A);
			final int deltaVA = (eVA == null || !eVA.isOrdinaryEdge()) ? Constants.INT_POS_INFINITE : eVA.getValue();
			final int newDeltaVA = (v < DeltaC) ? Math.min(deltaVA, -x) : Math.min(deltaVA, v - y);
			if (eVA == null) {
				eVA = this.g.makeNewEdge(V.getName() + "-" + A.getName(), ConstraintType.derived);
				eVA.setValue(newDeltaVA);
				g.addEdge(eVA, V, A);
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINE)) {
						STNU.LOG.fine("rul2018ApplyUpper. Added edge: " + eVA);
					}
				}
			} else {
				eVA.setValue(newDeltaVA);
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINE)) {
						STNU.LOG.fine("rul2018ApplyUpper. Adjusted edge: " + eVA);
					}
				}
			}
		}
	}

	/**
	 * RUL<sup>-</sup> algorithm: cloneRelaxLower procedure
	 * <p>
	 * Adds to this.g all ordinary edges (V,v,C) that can be generated by applications of the RELAX<sup>-</sup> and LOWER<sup>-</sup> rules.
	 *
	 * @param h      the potential to update
	 * @param C      a contingent time point
	 * @param DeltaC the y-c value of contingent link associated to C.
	 */
	private void rul2018CloseRelaxLower(Object2IntMap<LabeledNode> h, LabeledNode C, int DeltaC) {
		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINER)) {
				STNU.LOG.finer("rul2018CloseRelaxLower with " + C.getName() + ", Delta: " + DeltaC);
			}
		}
		final ExtendedPriorityQueue<LabeledNode> Q = new ExtendedPriorityQueue<>();
		LabeledNode W;
		for (final Pair<STNUEdge, LabeledNode> entry : g.getInEdgesAndNodes(C)) {
			final STNUEdge e = entry.left();
			if (!e.isOrdinaryEdge()) {
				continue;
			}
			W = entry.right();
			Q.insertOrUpdate(W, Constants.sumWithOverflowCheck(h.getInt(W), e.getValue()));
		}
		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINEST)) {
				STNU.LOG.finest("rul2018CloseRelaxLower Q: " + Q);
			}
		}

		while (!Q.isEmpty()) {
			W = Q.extractFirst();
			final Object2IntMap<LabeledNode> edges2C = rul2018ApplyRelaxLower(W, C, DeltaC);
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINEST)) {
					STNU.LOG.finest("rul2018CloseRelaxLower Node W: " + W + ". edges2C: " + edges2C);
				}
			}
			for (final Object2IntMap.Entry<LabeledNode> entry : edges2C.object2IntEntrySet()) {
				final LabeledNode V = entry.getKey();
				final int v = entry.getIntValue();
				final STNUEdge currVC = g.findEdge(V, C);
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINEST)) {
						STNU.LOG.finest("rul2018CloseRelaxLower currVC: (" + V.getName() + ", " + v + ", " + C.getName() + ")=" + currVC);
					}
				}
				int min;
				if (currVC == null) {
					final STNUEdge newE = this.g.makeNewEdge(V.getName() + "-" + C.getName(), ConstraintType.derived);
					newE.setValue(v);
					g.addEdge(newE, V, C);
					if (Debug.ON) {
						if (STNU.LOG.isLoggable(Level.FINE)) {
							STNU.LOG.fine("rul2018CloseRelaxLower Added edge: " + newE);
						}
					}
					min = v;
				} else {
					min = currVC.getValue();
					if (min == Constants.INT_NULL || v < min) {
						currVC.setValue(v);
						min = v;
						if (Debug.ON) {
							if (STNU.LOG.isLoggable(Level.FINE)) {
								STNU.LOG.fine("rul2018CloseRelaxLower Adjusted edge: " + currVC);
							}
						}
					}
				}
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINER)) {
						STNU.LOG.finer("rul2018CloseRelaxLower V pot: " + h.getInt(V) + ". min " + min);
					}
				}
				final int newKey = Constants.sumWithOverflowCheck(h.getInt(V), min);
				Q.insertOrUpdate(V, newKey);
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINER)) {
						STNU.LOG.finer("rul2018CloseRelaxLower Fixed: " + V.getName() + " at " + newKey + " in Q.");
					}
				}
			}
		}
	}

	/**
	 * RUL<sup>-</sup> algorithm: updatePotential procedure.
	 *
	 * @param h out-of-date potential
	 * @param A an activation time point
	 *
	 * @return the update potential considering edges terminating at A.
	 */
	@Nullable
	private Object2IntMap<LabeledNode> rul2018UpdatePotential(Object2IntMap<LabeledNode> h, LabeledNode A) {

		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINEST)) {
				STNU.LOG.finest("rul2018UpdatePotential started with h: " + h + "\n and A: " + A);
			}
		}
		final Object2IntMap<LabeledNode> newH = new Object2IntOpenHashMap<>(h);
		final ExtendedPriorityQueue<LabeledNode> newQ = new ExtendedPriorityQueue<>();

		//Priority of each time-point = amount its lower-bound changes
		newQ.insertOrUpdate(A, 0);
		LabeledNode V;
		int w;
		while (!newQ.isEmpty()) {
			final BasicEntry<LabeledNode> entry = newQ.extractFirstEntry();
			final LabeledNode W = entry.getKey();
			for (final Pair<STNUEdge, LabeledNode> edgeAndSource : g.getInEdgesAndNodes(W)) {
				final STNUEdge eVW = edgeAndSource.left();
				V = edgeAndSource.right();
				w = STNU.GET_MIN_VALUE_BETWEEN_ORDINARY_AND_LOWERCASE(eVW);
				/* edge is (V, w, W)*/
				if (w == Constants.INT_NULL) {// it is an UpperCase
					continue;
				}
				final int Vpot = newH.getInt(V);
				final int newVpot = Constants.sumWithOverflowCheck(newH.getInt(W), -w);
				if (Vpot < newVpot) {//i.e., d(V) > d(W) + w
					newH.put(V, newVpot);
					final int newKey = Constants.sumWithOverflowCheck(h.getInt(V), -newVpot);
					if (!newQ.insertOrUpdate(V, newKey)) {
						return null;// there is a negative loop!
					}
				}
			}
		}
		return newH;
	}

	/**
	 * Luke Hunsberger version of RUL<sup>-</sup> back propagation algorithm.
	 * <p>
	 * This is the Algorithm 11 in the Tech. Appendix of the AAAI22 paper.
	 * <p>
	 * The Tech. Appendix is published at <a href="http://hdl.handle.net/11562/1045707">http://hdl.handle.net/11562/1045707</a>.
	 * <p>
	 * Side effects: Modifies contents of graph and globalInfo.
	 * </p>
	 *
	 * @param currentUEdge an upper-case edge (C,C:-y,A).
	 * @param globalInfo   global data structure for the algorithm
	 *
	 * @return true if no negative circuit was found (graph is still DC), false otherwise.
	 */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	private boolean rul2021BackPropagation(STNUEdge currentUEdge, RULGlobalInfo globalInfo) {
		final ElementStatus statusCurrentUEdge = globalInfo.upperCaseEdgeStatus.get(currentUEdge);
		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINER)) {
				STNU.LOG.finer("Checking upper-case edge: " + currentUEdge + ". Status: " + statusCurrentUEdge);
			}
		}
		if (statusCurrentUEdge == ElementStatus.started) {
			return false;
		}
		if (statusCurrentUEdge == ElementStatus.finished) {
			return true;
		}
		checkStatus.cycles++;// Counts how many times this procedure was called;

		globalInfo.upperCaseEdgeStatus.put(currentUEdge, ElementStatus.started);
		final LabeledNode C = g.getSource(currentUEdge);
		final LabeledNode A = g.getDest(currentUEdge);
		assert C != null;
		assert A != null;
		final int y = -currentUEdge.getLabeledValue();
		final STNUEdge currentEdgeInverted = g.findEdge(A, C);
		assert currentEdgeInverted != null;
		final int x = currentEdgeInverted.getLabeledValue();
		final int DeltaC = y - x;
		if (Debug.ON) {
			assert (DeltaC > 0);
			if (STNU.LOG.isLoggable(Level.FINEST)) {
				STNU.LOG.finest("DeltaC is about contingent link " + A.getName() + C.getName() + ": " + DeltaC);
			}
		}
		final RULLocalInfo localInfo = new RULLocalInfo(Constants.INT_POS_INFINITE);
		// save localInfo in globalInfo for the rul2021Dispatchable version of the algorithm
		globalInfo.localInfoOfContingentNodes.put(C, localInfo);

		// queue contains the adjusted distance from X to C
		final ExtendedPriorityQueue<LabeledNode> queue = new ExtendedPriorityQueue<>();

		for (final Pair<STNUEdge, LabeledNode> edgeAndSource : g.getInEdgesAndNodes(C)) {
			//consider only ordinary constraints
			final STNUEdge e = edgeAndSource.left();
			if (!e.isOrdinaryEdge()) {
				continue;
			}
			final LabeledNode X = edgeAndSource.right();
			queue.insertOrUpdate(X, Constants.sumWithOverflowCheck(globalInfo.nodePotential.getInt(X), e.getValue()));
		}
		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINEST)) {
				STNU.LOG.finest("Initial queue of nodes to consider for contingent node " + C + ": " + queue);
			}
		}
		boolean cycle = true;
		do {
			if (!rul2021OneStepBackProp(C, DeltaC, queue, globalInfo, localInfo)) {
				return false;
			}
			if (!localInfo.unStartedUCEdges.isEmpty()) {
				for (final STNUEdge e : localInfo.unStartedUCEdges.values()) {
					if (Debug.ON) {
						if (STNU.LOG.isLoggable(Level.FINEST)) {
							STNU.LOG.finest("Considering unStarted edge " + e);
						}
					}
					if (!rul2021BackPropagation(e, globalInfo)) {
						return false;
					}
				}
				queue.clear();
				for (final LabeledNode X : localInfo.unStartedUCEdges.keySet()) {
					if (Debug.ON) {
						if (STNU.LOG.isLoggable(Level.FINEST)) {
							STNU.LOG.finest("From unStarted upper-case edge, update node " + X + " in the queue.");
						}
					}
					queue.insertOrUpdate(X,
					                     Constants.sumWithOverflowCheck(localInfo.distanceFromNodeToContingent.getInt(X), globalInfo.nodePotential.getInt(X)));
					localInfo.distanceFromNodeToContingent.put(X, Constants.INT_POS_INFINITE);
				}
			} else {
				cycle = false;
			}
		} while (cycle);
		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINEST)) {
				STNU.LOG.finest("Updated localInfo.distanceFrom: " + localInfo.distanceFromNodeToContingent);
			}
		}
		if (localInfo.ccLoop && rul2021FwdPropNotDC(C, DeltaC, localInfo.distanceFromNodeToContingent, globalInfo.nodePotential)) {
			return false;
		}

		boolean addedEdge = false;
		for (final LabeledNode X : g.getVertices()) {
			if (X == C) {
				continue;
			}
			final int deltaXC = localInfo.distanceFromNodeToContingent.getInt(X);
			if (deltaXC == Constants.INT_POS_INFINITE || deltaXC < DeltaC) {
				continue;
			}
			final int newValueXA = deltaXC - y;
			STNUEdge eXA = g.findEdge(X, A);
			if (eXA == null) {
				eXA = this.g.makeNewEdge(X.getName() + "-" + A.getName(), ConstraintType.derived);
				eXA.setValue(newValueXA);
				g.addEdge(eXA, X, A);
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINE)) {
						STNU.LOG.fine("Added edge: " + eXA);
					}
				}
				addedEdge = true;
			} else {
				final boolean isUpdated = eXA.updateValue(newValueXA);
				if (isUpdated) {
					addedEdge = true;
					if (Debug.ON) {
						if (STNU.LOG.isLoggable(Level.FINE)) {
							STNU.LOG.fine("Update edge: " + eXA + " to value " + newValueXA);
						}
					}
				}
			}
		}
		if (addedEdge) {
			globalInfo.nodePotential = rul2018UpdatePotential(globalInfo.nodePotential, A);
		}
		if (globalInfo.nodePotential == null) {
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINER)) {
					STNU.LOG.finer("The determination of new potential from " + A + " found an inconsistency. Giving up!");
				}
			}
			return false;
		}
		globalInfo.upperCaseEdgeStatus.put(currentUEdge, ElementStatus.finished);
		return true;
	}

	/**
	 * Auxiliary procedure for the Luke Hunsberger implementation of RUL<sup>-</sup> algorithm.
	 * <p>
	 * This is the Algorithm 13 in the Tech. Appendix of the AAAI22 paper.
	 * <p>
	 * The Tech. Appendix is published at
	 * <a href="http://hdl.handle.net/11562/1045707">http://hdl.handle.net/11562/1045707</a>.
	 *
	 * @param C               a contingent node
	 * @param DeltaC          the difference y-x of contingent link associated to C
	 * @param distanceFromC   distances of X from C computed during back-propagation.
	 * @param globalPotential global potential
	 *
	 * @return true iff forward propagation discovered a negative loop. Otherwise, the lower case from A to C is reduced away.
	 */
	private boolean rul2021FwdPropNotDC(LabeledNode C, int DeltaC, Object2IntMap<LabeledNode> distanceFromC, Object2IntMap<LabeledNode> globalPotential) {

		final ExtendedPriorityQueue<LabeledNode> queue = new ExtendedPriorityQueue<>();
		queue.insertOrUpdate(C, -globalPotential.getInt(C));

		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINEST)) {
				STNU.LOG.finest("rul2020fwdPropNotDC: Node C: " + C + ", DeltaC: " + DeltaC);
			}
		}
		while (!queue.isEmpty()) {
			final BasicEntry<LabeledNode> minEntry = queue.extractFirstEntry();
			final LabeledNode X = minEntry.getKey();
			final int Xkey = minEntry.getIntValue();
			final int deltaCX = Xkey + globalPotential.getInt(X);
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINEST)) {
					STNU.LOG.finest(
						"rul2020fwdPropNotDC: Node X: " + X + ", Xkey: " + Xkey + ", deltaCX: " + deltaCX + ", distanceFrom: " + distanceFromC.getInt(X));
				}
			}

			if (distanceFromC.getInt(X) >= DeltaC) {
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINEST)) {
						STNU.LOG.finest(
							"rul2020fwdPropNotDC: Node X: " + X + ", distanceFrom: " + distanceFromC.getInt(X) + " is greater than " + DeltaC + ". Ignore!");
					}
				}
				continue;
			}
			if (deltaCX < 0) {
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINEST)) {
						STNU.LOG.finest("rul2020fwdPropNotDC: Node X: " + X + ", deltaCX: " + deltaCX + " is less than " + DeltaC + ". Return true!");
					}
				}
				return true;
			}
			for (final Pair<STNUEdge, LabeledNode> entry : g.getOutEdgesAndNodes(X)) {
				final STNUEdge e = entry.left();
				final int eValue = STNU.GET_MIN_VALUE_BETWEEN_ORDINARY_AND_LOWERCASE(e);
				if (eValue == Constants.INT_NULL) {// is an upper edge
					continue;
				}
				final LabeledNode Y = entry.right();
				int newKey = Constants.sumWithOverflowCheck(deltaCX, eValue);
				newKey = Constants.sumWithOverflowCheck(newKey, -globalPotential.getInt(Y));// lower case o no-case value
				queue.insertOrUpdate(Y, newKey);
			}
		}
		return false;
	}

	/**
	 * Auxiliary procedure for the Luke Hunsberger implementation of RUL<sup>-</sup> algorithm.
	 * <p>
	 * This is the Algorithm 14 in the Tech. Appendix of the AAAI22 paper.
	 * <p>
	 * The Tech. Appendix is published at <a href="https://hdl.handle.net/11562/1045707">https://hdl.handle.net/11562/1045707</a>.
	 *
	 * @param V       the considered node
	 * @param DeltaC  the deltaC of the contingent link
	 * @param deltaVC the delta between V and C
	 *
	 * @return a list of pair (node, int) obtained applying RELAX^- and LOWER^- rules to all LO-edges incoming to V, together with the edge (V,deltaVC,C).
	 */
	private ObjectList<BasicEntry<LabeledNode>> rul2021NewApplyRelaxLower(LabeledNode V, int DeltaC, int deltaVC) {
		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINEST)) {
				STNU.LOG.finest("newApplyRelaxLower started with parameters: node V: " + V + ", DeltaC: " + DeltaC + ", deltaVC: " + deltaVC);
			}
		}

		final ObjectArrayList<BasicEntry<LabeledNode>> edges = new ObjectArrayList<>();
		if (deltaVC >= DeltaC) {
			if (Debug.ON) {
				STNU.LOG.finest("newApplyRelaxLower. deltaVC >= DeltaC, returns no new edges.");
			}
			return edges; // The RELAX^- and LOWER^- rules don't apply
		}
		final STNUEdge lowerCaseEdge = lowerContingentEdge.get(V);
		if (lowerCaseEdge != null) {// == V is a contingent node
			// Apply LOWER^-
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINEST)) {
					STNU.LOG.finest("newApplyRelaxLower. Apply LOWER considering: " + lowerCaseEdge);
				}
			}
			final BasicEntry<LabeledNode> localEntry =
				new BasicEntry<>(activationNode.get(V), Constants.sumWithOverflowCheck(lowerCaseEdge.getLabeledValue(), deltaVC));
			edges.add(localEntry);
		} else {
			for (final Pair<STNUEdge, LabeledNode> edgeAndSource : g.getInEdgesAndNodes(V)) {
				final STNUEdge e = edgeAndSource.left();
				if (!e.isOrdinaryEdge()) {
					continue;
				}
				final LabeledNode W = edgeAndSource.right();
				// Apply RELAX^-
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINEST)) {
						STNU.LOG.finest("newApplyRelaxLower. Apply `RELAX` considering: " + e);
					}
				}
				edges.add(new BasicEntry<>(W, Constants.sumWithOverflowCheck(e.getValue(), deltaVC)));
			}
		}
		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINEST)) {
				STNU.LOG.finest("newApplyRelaxLower. Resulting data for edges to add (W->deltaWC): " + edges);
			}
		}
		return edges;
	}

	/**
	 * Auxiliary procedure for the Luke Hunsberger version of RUL<sup>-</sup>.
	 * <p>
	 * This is the Algorithm 12 in the Tech. Appendix of the AAAI22 paper.
	 * <p>
	 * The Tech. Appendix is published at <a href="https://hdl.handle.net/11562/1045707">http://hdl.handle.net/11562/1045707</a>.
	 * <p>
	 * Side effects: Modifies contents of localInfo.
	 * </p>
	 *
	 * @param C          contingent time-point
	 * @param DeltaC     the difference y-x of contingent link associated to C.
	 * @param Q          a priority queue containing all incoming ordinary edges of C with priority adjusted with the potential of source node.
	 * @param globalInfo the global checking data structure
	 * @param localInfo  the local checking data structure
	 *
	 * @return false iff back-propagation from C reveals STNU to be non-DC.
	 */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	private boolean rul2021OneStepBackProp(@Nonnull LabeledNode C, int DeltaC, @Nonnull ExtendedPriorityQueue<LabeledNode> Q, @Nonnull RULGlobalInfo globalInfo,
	                                       @Nonnull RULLocalInfo localInfo) {

		localInfo.unStartedUCEdges = new Object2ObjectOpenHashMap<>();
		if (Debug.ON) {
			if (STNU.LOG.isLoggable(Level.FINEST)) {
				STNU.LOG.finest("OneStepBackProp started with parameters: Node C: " + C + ", DeltaC: " + DeltaC + ", Queue: " + Q);
			}
		}
		while (!Q.isEmpty()) {
			final BasicEntry<LabeledNode> minEntry = Q.extractFirstEntry();
			final LabeledNode X = minEntry.getKey();
			final int XKey = minEntry.getIntValue();
			final int weightEdgeXC = XKey - globalInfo.nodePotential.getInt(X);
			final STNUEdge upperCaseEdgeFromX = globalInfo.upperCaseEdgeFromActivation.get(X);

			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINEST)) {
					STNU.LOG.finest(
						"OneStepBackProp. Considering node " + X + ", XKey: " + XKey + ", deltaXC: " + weightEdgeXC + ", possible upper-case edge with " + X +
						" as activation: " + upperCaseEdgeFromX + ", distance of " + X + " from C: " +
						Constants.formatInt(localInfo.distanceFromNodeToContingent.getInt(X)));
				}
			}

			if (weightEdgeXC >= localInfo.distanceFromNodeToContingent.getInt(X)) {
				continue;
			}

			localInfo.distanceFromNodeToContingent.put(X, weightEdgeXC);
			if (Debug.ON) {
				if (STNU.LOG.isLoggable(Level.FINEST)) {
					STNU.LOG.finest("OneStepBackProp. New distance of " + X + ": " + weightEdgeXC);
				}
			}

			if (weightEdgeXC >= DeltaC) {
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINEST)) {
						STNU.LOG.finest("OneStepBackProp. New distance of " + X + " is greater than DeltaC " + DeltaC);
					}
				}
				continue;
			}

			if (X == C) {
				if (Debug.ON) {
					if (STNU.LOG.isLoggable(Level.FINEST)) {
						STNU.LOG.finest("OneStepBackProp. Node X == C: " + X + "==" + C);
					}
				}
				// Case 1: CC loop of length δxc < ∆C
				if (weightEdgeXC < 0) {
					if (Debug.ON) {
						if (STNU.LOG.isLoggable(Level.FINEST)) {
							STNU.LOG.finest("OneStepBackProp. Node X == C and deltaXC < 0. Negative cycle. Giving up! deltaC: " + weightEdgeXC);
						}
					}
					return false;
				}
				localInfo.ccLoop = true;
				if (Debug.ON) {
					STNU.LOG.finest("OneStepBackProp. ccLoop true");
				}
			} else {
				final ElementStatus ucFromXStatus = globalInfo.upperCaseEdgeStatus.get(upperCaseEdgeFromX);
				if (upperCaseEdgeFromX != null) {
					if (Debug.ON) {
						if (STNU.LOG.isLoggable(Level.FINEST)) {
							STNU.LOG.finest(
								"OneStepBackProp. Status of upper-case edge having " + X + " as activation. Edge: " + upperCaseEdgeFromX + ". Status: " +
								ucFromXStatus);
						}
					}
					if (ucFromXStatus == ElementStatus.unStarted) {
						// Case 2: upperCaseEdgeFromX is an unStarted UC-edge
						localInfo.unStartedUCEdges.put(X, upperCaseEdgeFromX);
						continue;
					}
					if (ucFromXStatus == ElementStatus.started) {
						// Case 3: Cycle of interruptions: not DC
						if (Debug.ON) {
							STNU.LOG.finer("Found case 3. Giving up!");
						}
						return false;
					}
				}
				// case 4
				for (final BasicEntry<LabeledNode> entry : rul2021NewApplyRelaxLower(X, DeltaC, weightEdgeXC)) {
					final LabeledNode W = entry.getKey();
					final int deltaWC = entry.getIntValue();
					STNUEdge wc = g.findEdge(W, C);
					int wcValue = Constants.INT_NULL;
					if (wc != null && (wcValue = wc.getValue()) == Constants.INT_NULL) {// it does not represent an ordinary edge, so ignore it!
						wc = null;
					}
					if (Debug.ON) {
						if (STNU.LOG.isLoggable(Level.FINEST)) {
							STNU.LOG.finest("OneStepBackProp. Considering (W->deltaWC): " + entry + ". The current edge WC: " + wc);
						}
					}
					if (wc == null || deltaWC < wcValue) {
						final int newKey = Constants.sumWithOverflowCheck(deltaWC, globalInfo.nodePotential.getInt(W));
						if (Debug.ON) {
							final Status Wstatus = Q.getStatus(W);
							if (STNU.LOG.isLoggable(Level.FINEST)) {
								STNU.LOG.finest(
									"OneStepBackProp. potential(" + W + "): " + globalInfo.nodePotential.getInt(W) + ", newKey of " + W + ": " + newKey +
									", queue status of " + W + ": " + Wstatus);
							}
						}
						Q.insertOrUpdate(W, newKey);
						if (Debug.ON) {
							if (STNU.LOG.isLoggable(Level.FINEST)) {
								STNU.LOG.finest("OneStepBackProp. Queue after adding " + W + ": " + Q);
							}
						}
					}
				}
			}
		}
		return true;
	}
}
