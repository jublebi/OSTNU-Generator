// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.algorithms;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.Stack;
import it.unimi.dsi.fastutil.objects.AbstractObject2IntMap.BasicEntry;
import it.unimi.dsi.fastutil.objects.*;
import it.univr.di.Debug;
import it.univr.di.cstnu.algorithms.AbstractCSTN.NodesToCheck;
import it.univr.di.cstnu.graph.*;
import it.univr.di.cstnu.graph.Edge.ConstraintType;
import it.univr.di.cstnu.util.ExtendedPriorityQueue;
import it.univr.di.cstnu.util.ExtendedPriorityQueue.Status;
import it.univr.di.cstnu.util.ObjectArrayFIFOSetQueue;
import it.univr.di.cstnu.util.ObjectPair;
import it.univr.di.cstnu.visualization.CSTNUStaticLayout;
import it.univr.di.labeledvalue.Constants;
import org.kohsuke.args4j.*;
import org.xml.sax.SAXException;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Represents a Simple Temporal Network (STN) and it contains some methods to manipulate and to check an STN instance.
 * In this class, edge weights are represented as signed integer.
 *
 * @author Roberto Posenato
 * @version $Rev: 894 $
 */
public class STN {

	/**
	 * Implemented algorithms for STN.
	 *
	 * @author posenato
	 */
	public enum CheckAlgorithm {
		/**
		 * All-Pairs-Shortest-Paths
		 */
		AllPairsShortestPaths,
		/**
		 *
		 */
		BannisterEppstein,
		/**
		 * Bellman-Ford
		 */
		BellmanFord,
		/**
		 * Bellman-Ford
		 */
		BellmanFordSingleSink,
		/**
		 * SSSP_BFCT
		 */
		BFCT,
		/**
		 * Dijkstra
		 */
		Dijkstra,
		/**
		 * Johnson
		 */
		Johnson,
		/**
		 * Yen
		 */
		Yen,
		/**
		 * Yen
		 */
		YenSingleSink
	}

	/**
	 * Functional interface to retrieve the edge value of interest.<br> It is made available for generalizing some static method that could be used on derived
	 * classes like STNU.
	 */
	@FunctionalInterface
	public interface EdgeValue {
		/**
		 * The interested value of the edge.
		 *
		 * @param e the edge.
		 *
		 * @return the interested value of the edge. It must return {@link Constants#INT_NULL} if the value is not defined.
		 */
		int getValue(STNEdge e);
	}

	/**
	 * Represents the status of a checking algorithm during its execution and the final result of a check. At the end of
	 * an STN-checking algorithm running, it contains the final status ({@link STNCheckStatus#consistency} or the node
	 * {@link STNCheckStatus#negativeLoopNode} where a negative loop has been found).
	 *
	 * @author Roberto Posenato
	 */
	@SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "stdDevExecutionTimeNS is used!")
	public static class STNCheckStatus implements Serializable {
		static public final long serialVersionUID = 1L;
		/**
		 * Consistency status (it is assumed true at the initialization).
		 */
		public boolean consistency;

		/**
		 * Counters about the # of application of different rules.
		 */
		public int cycles;
		/**
		 * Execution time in nanoseconds.
		 */
		public long executionTimeNS;
		/**
		 * Becomes true if no rule can be applied anymore.
		 */
		public boolean finished;
		/**
		 * The list of LabeledNode representing a negative loop has been found (if the network is not consistent and the
		 * algorithm can build it).
		 */
		public ObjectList<LabeledNode> negativeCycle;
		/**
		 * The node with a negative loop in case that the graph is not consistent and the algorithm can determine only
		 * the node with negative loop.
		 */
		public LabeledNode negativeLoopNode;
		/**
		 * A possible note
		 */
		public String note;
		/**
		 * Another counter for storing an execution time of a part of the process. In nanoseconds.
		 */
		public long partialExecutionTimeNS;
		/**
		 * Number of propagations
		 */
		public int propagationCalls;
		/**
		 * Standard Deviation of execution time if this last one is a mean. In nanoseconds.
		 */
		public long stdDevExecutionTimeNS;
		/**
		 *
		 */
		public long stdDevPartialExecutionTimeNS;
		/**
		 * Becomes true if check has been interrupted because a given time-out has occurred.
		 */
		public boolean timeout;
		/**
		 * Becomes true when all data structures have been initialized.
		 */
		boolean initialized;

		/**
		 * Default constructor
		 */
		public STNCheckStatus() {
			consistency = true;
			cycles = 0;
			executionTimeNS = partialExecutionTimeNS = Constants.INT_NULL;
			finished = timeout = false;
			initialized = false;
			negativeCycle = null;
			negativeLoopNode = null;
			note = "";
			propagationCalls = 0;
			stdDevExecutionTimeNS = stdDevPartialExecutionTimeNS = Constants.INT_NULL;
		}

		/**
		 * Copy constructor used only in benchmarks
		 *
		 * @param in the status to clone
		 */
		STNCheckStatus(STNCheckStatus in) {
			if (in == null) {
				return;
			}
			consistency = in.consistency;
			cycles = in.cycles;
			executionTimeNS = in.executionTimeNS;
			partialExecutionTimeNS = in.partialExecutionTimeNS;
			finished = in.finished;
			timeout = in.timeout;
			initialized = in.initialized;
			negativeCycle = in.negativeCycle;
			negativeLoopNode = in.negativeLoopNode;
			note = in.note;
			propagationCalls = in.propagationCalls;
			stdDevExecutionTimeNS = in.stdDevExecutionTimeNS;
			stdDevPartialExecutionTimeNS = in.stdDevPartialExecutionTimeNS;
		}

		/**
		 * If current instant is after the {@code timeoutInstant}, it adjust this object setting to true {@code timeout}
		 * and to false {@code consistency, finished}.
		 *
		 * @param timeoutInstant timeout instant
		 *
		 * @return true if timeOut has been reached.
		 */
		public boolean checkTimeOutAndAdjustStatus(@Nonnull Instant timeoutInstant) {
			if (Instant.now().isAfter(timeoutInstant)) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.log(Level.FINE, "Time out occurred!");
					}
				}
				this.timeout = true;
				this.consistency = false;
				this.finished = false;
				return true;
			}
			return false;
		}

		/**
		 * Reset all indexes.
		 */
		public void reset() {
			consistency = true;
			cycles = 0;
			propagationCalls = 0;
			executionTimeNS = partialExecutionTimeNS = Constants.INT_NULL;
			stdDevExecutionTimeNS = stdDevPartialExecutionTimeNS = Constants.INT_NULL;
			finished = timeout = false;
			initialized = false;
			negativeLoopNode = null;
			negativeCycle = null;
			note = "";
		}

		/**
		 * @return the status of a check with all determined index values.
		 */
		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder(160);
			sb.append("The check is");
			if (!finished) {
				sb.append(" NOT");
			}
			sb.append(" finished after ").append(cycles).append(" cycle(s).\n");
			if (finished) {
				sb.append("The consistency check has determined that given network is ");
				if (!consistency) {
					sb.append("NOT ");
				}
				sb.append("consistent.\n");
			}
			sb.append("Propagation has been applied ").append(propagationCalls).append(" times.\n");
			if (timeout) {
				sb.append("The checking has been interrupted because execution time exceeds the given time limit.\n");
			}
			if (!consistency && negativeLoopNode != null) {
				sb.append("The negative loop is on node ").append(negativeLoopNode).append("\n");
			}
			if (!consistency && negativeCycle != null) {
				sb.append("The negative cycle is ").append(negativeCycle).append("\n");
			}
			if (executionTimeNS != Constants.INT_NULL) {
				sb.append("The global execution time has been ").append(executionTimeNS).append(" ns (~")
					.append((executionTimeNS / 1E9)).append(" s.)");
			}
			if (!note.isEmpty()) {
				sb.append("\n").append("Note: ").append(note);
			}
			return sb.toString();
		}
	}

	/**
	 * Global time counter for {@link #GET_STRONG_CONNECTED_COMPONENTS(LabeledNode)}
	 */
	static private class SCCTime {
		private int time;

		/**
		 * @return the current value and, then, increase the time by one unit.
		 */
		public int get() {
			return time++;
		}

		/**
		 * Just for debugger
		 */
		public String toString() {
			return String.valueOf(time);
		}
	}

	/**
	 * Suffix for file name
	 */
	public final static String FILE_NAME_SUFFIX = ".stn";
	/**
	 * The name for the initial node.
	 */
	public final static String ZERO_NODE_NAME = "Z";
	/**
	 * Version of the class
	 */
	// static final String VERSIONandDATE = "Version 1.0 - July, 15 2019";
	// static final String VERSIONandDATE = "Version 1.1 - January, 19 2021";// made
	// a distinction between AllPairsShortestPaths and F-W algorithms
	// static final String VERSIONandDATE = "Version 1.2 - April, 24 2021";// renamed getPredecessorGraph. Now it is getPredecessorSubGraph
	// static final String VERSIONandDATE = "Version 1.2.1 - October, 04 2021";// main fixed
	// static final String VERSIONandDATE = "Version 1.2.2 - October, 12 2021";// fixed ExtendedPriorityQueue made generics
	// static final String VERSIONandDATE = "Version 1.3.0 - December, 24 2021";// fixed error about SSSP algorithm when Z is not connected to all other nodes.
	// Improved getSTNPredecessor. Added GET_UNDOMINATED_EDGES
	//	static final String VERSIONandDATE = "Version 1.4 - January, 19 2022";// Generalized all methods to accept edges that extend STNEdge. In this way some
	static final String VERSIONandDATE = "Version 1.4.1 - January, 17 2023";// Tweaking
	private static final Pattern FILE_NAME_SUFFIX_PATTERN = Pattern.compile(FILE_NAME_SUFFIX + "$");
	/**
	 * logger
	 */
	static private final Logger LOG = Logger.getLogger(STN.class.getName());
	// static methods can be used also by STNU class.

	/**
	 * Determines the minimal distance between all pair of nodes (all-pair-shortest-paths (APSP)) using the
	 * Floyd-Warshall algorithm.<br> If the graph contains a negative cycle, it returns false and the graph contains the
	 * edges that have determined the negative cycle.
	 *
	 * @param <E>          the kind of edge
	 * @param graph        the graph to complete
	 * @param checkStatus1 possible status to fill during the computation. It can be null.
	 *
	 * @return true if the graph is consistent, false otherwise. If the response is false, the edges do not represent
	 * 	the minimal distance between nodes.
	 */
	static <E extends STNEdge> boolean APSP_FloydWarshall(TNGraph<E> graph, STNCheckStatus checkStatus1) {
		final LabeledNode[] node = graph.getVerticesArray();
		final int n = node.length;
		LabeledNode iV, jV, kV;
		E ik, kj, ij;
		int v;

		for (int k = 0; k < n; k++) {
			kV = node[k];
			for (int i = 0; i < n; i++) {
				iV = node[i];
				for (int j = 0; j < n; j++) {
					if ((k == i) || (k == j)) {
						continue;
					}
					jV = node[j];

					ik = graph.findEdge(iV, kV);
					kj = graph.findEdge(kV, jV);
					if ((ik == null) || (kj == null)) {
						continue;
					}
					v = Constants.sumWithOverflowCheck(ik.getValue(), kj.getValue());

					if (i == j) {
						if (v < 0) {
							// check negative cycles
							LOG.info(
								"Found a negative cycle on node " + iV.getName() + "\nDetails: ik=" + ik + ", kj=" +
								kj + ",  v=" + v);
							if (checkStatus1 != null) {
								checkStatus1.consistency = false;
								checkStatus1.finished = true;
							}
							return false;
						}
						continue;
					}

					ij = graph.findEdge(iV, jV);
					int old = Constants.INT_POS_INFINITE;
					if (ij == null) {
						ij = graph.makeNewEdge(node[i].getName() + "-" + node[j].getName(),
						                       ConstraintType.derived);
						graph.addEdge(ij, iV, jV);
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINEST)) {
								LOG.finest("Added edge " + ij);
							}
						}
					} else {
						if (Debug.ON) {
							old = ij.getValue();
						}
					}
					if (ij.updateValue(v)) {
						if (checkStatus1 != null) {
							checkStatus1.propagationCalls++;
						}
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINEST)) {
								LOG.finest("Edge " + ij.getName() + ": " + Constants.formatInt(old) + " --> " +
								           Constants.formatInt(v) + " because result of " + ik + " + " + kj);
							}
						}
					}
				}
			}
			if (checkStatus1 != null) {
				checkStatus1.cycles++;
			}
		}
		if (checkStatus1 != null) {
			checkStatus1.consistency = true;
			checkStatus1.finished = true;
		}
		return true;
	}

	/**
	 * Determines the minimal distance between all pair of nodes (all-pair-shortest-paths (APSP)) using the Johnson
	 * algorithm. The minimal distance between a node {@code X} to a node {@code Y} is saved as the value of the edge
	 * {@code (X, Y)}.
	 *
	 * @param <E>          the kind of edges
	 * @param g1           input graph. It must be not null. It will be make complete with all the minimal distances.
	 * @param checkStatus1 status to update with statistics of algorithm. It can be null.
	 *
	 * @return true if all distances have been determined, false if a negative cycle or any other error occurred.
	 */
	@SuppressWarnings("UnusedReturnValue")
	static <E extends STNEdge> boolean APSP_Johnson(@Nonnull TNGraph<E> g1, final STNCheckStatus checkStatus1) {

		final TNGraph<E> finalG = new TNGraph<>(g1, g1.getEdgeImplClass());

		if (Debug.ON) {
			LOG.finer("Determining a potential by Bellman-Ford.");
		}
		final boolean ssspStatus = SSSP_BellmanFord(g1, null, checkStatus1);
		if (!ssspStatus) {
			if (Debug.ON) {
				LOG.finer("The STN is not consistent.");
			}
			return false;
		}
		if (Debug.ON) {
			LOG.finest("Re-weighting all edges.");
		}
		STN.REWEIGH(g1);

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Re-weighted graph: " + g1);
			}
		}

		// Determine the distances from each node updating the edge in the finalG
		for (final LabeledNode source : g1.getVertices()) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("\nDetermining the distances considering node " + source.getName() +
					           " as source node using Dijkstra.");
				}
			}
			// Dijkstra determines distances from source
			final Object2IntMap<LabeledNode> nodeDistanceFromSource = STN.GET_SSSP_Dijkstra(g1, source, checkStatus1);
			if (nodeDistanceFromSource == null) {
				throw new IllegalStateException("Dijkstra cannot find distances from the source " + source);
			}

			// for each other node, adjust the minimal distance from source in finalG
			for (final LabeledNode d : g1.getVertices()) {
				if (d == source) {
					continue;
				}
				// new potential value is the value of the edge in Dijkstra + the difference between original destination potential and source:
				// DijkstraDistance + (d - s)
				final int newEdgeSDValue = Constants.sumWithOverflowCheck(nodeDistanceFromSource.getInt(d),
				                                                          Constants.sumWithOverflowCheck(
					                                                          d.getPotential(),
					                                                          -source.getPotential()));
				E edgeSD = finalG.findEdge(source.getName(), d.getName());
				if (edgeSD == null) {
					// D is reachable from S, but there is no a direct edge.
					// Johnson assumes to save the value of the edge... so we add it as internal.
					edgeSD = finalG.makeNewEdge(source.getName() + "-" + d.getName(), ConstraintType.derived);
					finalG.addEdge(edgeSD, source, d);
				}
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("Adjusting edge value for edge " + edgeSD + " from " +
						           Constants.formatInt(edgeSD.getValue()) + " to " +
						           Constants.formatInt(newEdgeSDValue));
					}
				}
				edgeSD.updateValue(newEdgeSDValue);
			}
		}
		g1.takeFrom(finalG);
		return true;
	}

	/**
	 * Collapses given rigid components (RC) rerouting, in each RC, all edges to/from nodes in RC to the representative
	 * node of RC. Nodes belonging to an RC and different from the RC representative are removed from the graph.
	 *
	 * @param <E>      the kind of edges
	 * @param graph    input graph containing the rigid components
	 * @param rep      map giving the RC representative of the node. This map can be determined by
	 *                 {@link #GET_REPRESENTATIVE_RIGID_COMPONENTS(ObjectList, Object2IntMap, LabeledNode)}.
	 * @param distance map giving the distance from the RC representative. This map can be determined by
	 *                 {@link #GET_REPRESENTATIVE_RIGID_COMPONENTS(ObjectList, Object2IntMap, LabeledNode)}
	 *
	 * @throws IllegalStateException when the edge has no a standard weight.
	 */
	static <E extends STNEdge> void COLLAPSE_RIGID_COMPONENT(@Nonnull TNGraph<E> graph,
	                                                         @Nonnull Object2ObjectMap<LabeledNode, LabeledNode> rep,
	                                                         @Nonnull Object2IntMap<LabeledNode> distance) {

		// make sure that the distance of node not in an RC is 0
		distance.defaultReturnValue(0);

		// consider each node in rep and reroute its in/out edges to its RC representative node
		for (final LabeledNode X : rep.keySet()) {
			final LabeledNode Xr = rep.get(X);
			if (X == Xr) {// it is a representative
				continue;
			}
			final int XOffset = distance.getInt(X);
			/*
			 * The general rule for re-routing an edge (X,v,Y) is
			 * Xr=rep[X]
			 * Yr=rep[Y]
			 * if (Xr!=Yr) add (Xr, distance[X]+v-distance[Y], Yr)
			 * Here we consider (X,v,Y) (outgoing) and (Y,v,X) (incoming)
			 */
			// all outgoing edges
			for (final E edgeInGraph : graph.getOutEdges(X)) {
				final LabeledNode Y = graph.getDest(edgeInGraph);
				LabeledNode Yr = rep.get(Y);
				if (Yr == null) {
					Yr = Y;// it is not in a proper rigid component.
				}
				if (Yr != Xr) {
					final int edgeInGraphValue = edgeInGraph.getValue();
					if (edgeInGraphValue == Constants.INT_NULL || edgeInGraphValue == Constants.INT_POS_INFINITE) {
						throw new IllegalStateException("Found a non-ordinary edge: " + edgeInGraph);
					}
					final int edgeValue = XOffset + edgeInGraphValue - distance.getInt(
						Y);// if Y does not belong to any RC, its offset is 0 (see above
					// defaultReturnValue)
					E e1 = graph.findEdge(Xr, Yr);
					if (e1 == null) {
						assert Yr != null;
						e1 = graph.makeNewEdge(Xr.getName() + "-" + Yr.getName(), ConstraintType.derived);
						e1.setValue(edgeValue);
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("Added edge: " + e1);
							}
						}
						graph.addEdge(e1, Xr, Yr);
					} else {
						e1.updateValue(edgeValue);
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								LOG.finer("Updated edge " + e1);
							}
						}
					}
				}
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("Removed edge " + edgeInGraph);
					}
				}
				graph.removeEdge(edgeInGraph);
			}

			// all ingoing edges
			// Remember: invert the role!
			for (final E e : graph.getInEdges(X)) {
				final int edgeInGraphValue = e.getValue();
				if (edgeInGraphValue == Constants.INT_NULL || edgeInGraphValue == Constants.INT_POS_INFINITE) {
					throw new IllegalStateException("Found a non-ordinary edge: " + e);
				}
				final LabeledNode Y = graph.getSource(e);
				LabeledNode Yr = rep.get(Y);
				if (Yr == null) {
					Yr = Y;
				}
				if (Yr != Xr) {
					final int edgeValue = distance.getInt(Y) + edgeInGraphValue -
					                      XOffset;// if Y does not belong to any RC, its offset is 0 (see above defaultReturnValue)
					E e1 = graph.findEdge(Yr, Xr);
					if (e1 == null) {
						assert Yr != null;
						e1 = graph.makeNewEdge(Yr.getName() + "-" + Xr.getName(), ConstraintType.derived);
						e1.setValue(edgeValue);
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("Added edge: " + e1);
							}
						}
						graph.addEdge(e1, Yr, Xr);
					} else {
						e1.updateValue(edgeValue);
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								LOG.finer("Updated edge " + e1);
							}
						}
					}
				}
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("Removed edge " + e);
					}
				}
				graph.removeEdge(e);
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Removed vertex " + X);
				}
			}
			graph.removeVertex(X);
		}
	}

	/**
	 * Recursive method for determining a depth-first order of nodes.<br> Depth-first order sorts node with respect to
	 * their 'finishing time' in a Depth-First-Search (DFS) (see cap. 22.3 of Cormen et al.)<br> In the
	 * {@code finalOrder}, each node has index equal to its 'finishing time' in a DFS.
	 *
	 * @param graph      the graph to visit.
	 * @param node       starting node (it will be the last node in the final order).
	 * @param finalOrder the resulting order.
	 * @param translate  true if the order has to be determined in the translated graph.
	 * @param isVisited  node visit status. It can be null or an empty map. If it's a significative map, then it is used
	 *                   to limit the nodes in the order. Only nodes not visited are considered.
	 *
	 * @see #GET_DEPTH_FIRST_ORDER(TNPredecessorGraph, LabeledNode, Object2BooleanMap, boolean)
	 */
//	@Deprecated(since = "4.4")
	static void DEPTH_FIRST_ORDER(@Nonnull TNGraph<STNEdge> graph, @Nonnull LabeledNode node,
	                              @Nonnull ObjectList<LabeledNode> finalOrder,
	                              @Nullable Object2BooleanMap<LabeledNode> isVisited, boolean translate) {
		if (isVisited == null) {
			isVisited = new Object2BooleanLinkedOpenHashMap<>();
			isVisited.defaultReturnValue(false);
		}
		isVisited.put(node, true);
		final Collection<LabeledNode> adjNodes = (translate) ? graph.getPredecessors(node) : graph.getSuccessors(node);
		for (final LabeledNode adjNode : adjNodes) {
			if (isVisited.getBoolean(adjNode)) {
				continue;
			}
			DEPTH_FIRST_ORDER(graph, adjNode, finalOrder, isVisited, translate);
		}
		finalOrder.add(node);
	}

	/**
	 * * Determines the minimal distance between all pair of nodes (all-pair-shortest-paths (APSP)) using the Johnson
	 * algorithm. The minimal distance between a node {@code X} to a node {@code Y} is saved as the value of the edge
	 * {@code (X, Y)} in the returned graph.
	 * <br>
	 * It doesn't modify the input graph, but nodes of the returned graph are the same of the input graph.
	 *
	 * @param <T>          the kind of edges
	 * @param inputG       input graph. it must be not null. It is not modified.
	 * @param checkStatus1 status to update with statistics of algorithm. It can be null.
	 *
	 * @return the APSP graph if there is no negative cycle, null otherwise.
	 */
	@Nullable
	public static <T extends STNEdge> TNGraph<T> GET_APSP_Johnson(@Nonnull TNGraph<T> inputG,
	                                                              STNCheckStatus checkStatus1) {
		if (Debug.ON) {
			LOG.finer("Started.");
		}
		final TNGraph<T> finalG = new TNGraph<>(inputG, inputG.getEdgeImplClass());
		if (checkStatus1 == null) {checkStatus1 = new STNCheckStatus();}
		APSP_Johnson(finalG, checkStatus1);
		if (Debug.ON) {
			LOG.finer("Finished.");
		}
		if (!checkStatus1.consistency) {return null;}
		return finalG;
	}

	/**
	 * Iterative method for determining a depth-first order of nodes.<br> Depth-first order sorts node with respect to
	 * their 'finishing time' in a Depth-First-Search (DFS) (see cap. 22.3 of Cormen et al.).
	 *
	 * @param <E>       the kind of edge
	 * @param graph     the graph to visit.
	 * @param source    starting node (it will be the last node in the final order).
	 * @param translate true if the order has to be determined in the translated graph.
	 * @param isVisited node visit status. It can be null or an empty map. If it's a significative map, then it is used
	 *                  to limit the nodes in the order. Only nodes not visited are considered.
	 *
	 * @return a list of node, where each node has index equal to its 'finishing time' in a DFS.
	 */
	static <E extends Edge> ObjectList<LabeledNode> GET_DEPTH_FIRST_ORDER(@Nonnull TNPredecessorGraph<E> graph,
	                                                                      @Nonnull LabeledNode source,
	                                                                      @CheckForNull Object2BooleanMap<LabeledNode> isVisited,
	                                                                      boolean translate) {

		if (isVisited == null) {
			isVisited = new Object2BooleanLinkedOpenHashMap<>();
			isVisited.defaultReturnValue(false);
		}
		final ObjectList<LabeledNode> finalOrder = new ObjectArrayList<>();

		/*
		 * Be careful!<br>
		 * Classic iterative depth first search or traversal makes the visit of the node
		 * before putting the successors in the stack. In other words, it determines the
		 * discovering time of each node.<br>
		 * Here we need the finishing time of the node.
		 * So, only after processing deeply all successor, we have to add the node in the order.
		 * A way for making this operation is to enrich the info about the status of a node in the stack.<br>
		 * A node can be in one of the following state:
		 * - null (0),
		 * - started (1),
		 * - waiting (2),
		 * - finished (3).
		 * All node are in null state before the cycle.<br>
		 * When we put a node in the stack for processing it (i.e., having null or state), its state becomes 'started'.<br>
		 * When a node is popped, if its status is started, then
		 * 1) its state become 'waiting'
		 * 2) we put it again in the stack
		 * 3) and we add all its neighbors. Neighbors can be added if they have status null or started.
		 * When a node is popped and its status is waiting, then we put it in the order, and we change its status to
		 * 'finished'.
		 * When a node is popped and its status is 'finished', we ignore it because it was already processed.
		 */
		final Stack<LabeledNode> stack = new ObjectArrayList<>();

		final Object2IntOpenHashMap<LabeledNode> status = new Object2IntOpenHashMap<>();
		status.defaultReturnValue(0);

		// isVisited says also when the node is or was in the stack.
		stack.push(source);
		status.put(source, 1);

		while (!stack.isEmpty()) {
			final LabeledNode currentNode = stack.top();
			final int currentStatus = status.getInt(currentNode);

			switch (currentStatus) {
				case 1 -> { // we must add it neighbors
					status.put(currentNode, 2);
					final Collection<LabeledNode> adjNodes = new ObjectArrayList<>();
					final ObjectList<ObjectObjectImmutablePair<LabeledNode, E>> nodeEdgeList =
						(translate) ? graph.getPredecessors(currentNode) : graph.getSuccessors(currentNode);
					nodeEdgeList.forEach((entry) -> adjNodes.add(entry.key()));

					for (final LabeledNode adjNode : adjNodes) {
						if (isVisited.getBoolean(adjNode) || status.getInt(adjNode) > 1) {
							continue;
						}
						stack.push(adjNode);
						status.put(adjNode, 1);
					}
				}
				case 2 -> {
					// currentNode has returned... was waiting this moment!
					stack.pop();
					finalOrder.add(currentNode);
					isVisited.put(currentNode, true);
					status.put(currentNode, 3);
				}
				default ->
					// it is already considered, ignore it
					stack.pop();
			}
		}
		return finalOrder;
	}

	/**
	 * For each rigid components with two elements at least, determines: 1) the node that should precede the others as
	 * representative and 2) the distances from the representative and the other nodes of the rigid component.<br> Such
	 * information are stored and returned as two maps: one saying the representative for each node, the other the
	 * distance from the representative for each node.<br>
	 *
	 * @param rigidComponents the list of rigid components. Each rigid components is a list of nodes.
	 * @param solution        distances from a source. It used for deciding the representative node in each rigid
	 *                        components: it is the nearest node to the source.
	 * @param Z               the Z node. If a component contain Z, then Z is set as representative.
	 *
	 * @return two maps:
	 * 	<ul>
	 * 	    <li>left map of the pair maps each node to its representative in the rigid component.</li>
	 * 	    <li>right map maps each node to the value of its distance from the representative.
	 * 	    In other words, if R is representative and X is the node, the entry is {@code solution(X)-solution(R)}.
	 * 	    If solution was determined using a fake source, solution(R)&le;solution(X). So, the distance is always non-negative.
	 * 	    </ul>
	 * 	Nodes are represented by their name.
	 */
	static Pair<Object2ObjectMap<LabeledNode, LabeledNode>, Object2IntMap<LabeledNode>> GET_REPRESENTATIVE_RIGID_COMPONENTS(
		ObjectList<ObjectList<LabeledNode>> rigidComponents, @Nonnull Object2IntMap<LabeledNode> solution,
		@Nonnull LabeledNode Z) {

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
			for (final LabeledNode node : rc) {
				final int v = solution.getInt(node);
				if (v == Constants.INT_POS_INFINITE) {
					throw new IllegalStateException("Node " + node + " has no a distance in solution map.");
				}
				if (node.equalsByName(Z)) {
					// Z is the preferred representative
					representative = node;
					representativeDistance = v;
					break;
				}
				if (v < representativeDistance) {
					representative = node;
					representativeDistance = v;
				}
			}
			// fill the result maps
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("For rigid component " + rc + ", the representative is " + representative);
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
	 * Determine the reverse-post-order of reachable nodes from the given root node in {@code graph}.
	 *
	 * @param <E>       the kind of edges
	 * @param graph     graph
	 * @param root      starting node of the visit. It must belong to graph.
	 * @param isVisited node visit status. It can be null or an empty map. After the call, all nodes mapped to true
	 *                  value have been visited.
	 *
	 * @return the list of node in reverse-post-order if graph is not null and root is a node of graph, null otherwise.
	 */
	@Nullable
	static <E extends STNEdge> ObjectList<LabeledNode> GET_REVERSE_POST_ORDER_VISIT(
		@CheckForNull TNPredecessorGraph<E> graph, @Nonnull LabeledNode root,
		@CheckForNull Object2BooleanMap<LabeledNode> isVisited) {

		if (graph == null || !graph.isNodePresent(root)) {
			return null;
		}
		final ObjectList<LabeledNode> order = GET_DEPTH_FIRST_ORDER(graph, root, isVisited, false);
		Collections.reverse(order);
		return order;
	}

	/**
	 * Determines the minimal distance from the give source node to each other node (single-source-shortest-paths
	 * (SSSP)) using the BellmanFord algorithm.<br> The minimal distance is stored in a map
	 * {@code (node, distanceFromSource)}. If the graph contains a negative cycle, it returns null.
	 *
	 * @param <E>          the kind of edge. This method accepts any extensions of STNEdge. If
	 *                     {@link STNEdge#getValue()} does not return a valid value, the edge is ignored.
	 * @param graph        input graph. If it is null, the method returns false.
	 * @param source       the source node. If it is null, then a virtual temporary source is added for determining a
	 *                     virtual distance for each node (virtual distances are all non-positive). If it is not null,
	 *                     and it is not present in the graph, the method returns false.
	 * @param checkStatus1 status to update with statistics of algorithm. It can be null.
	 *
	 * @return the map of pairs {@code (node, distanceFromSource)} if the graph is consistent, null otherwise.
	 */
	@Nullable
	static <E extends STNEdge> Object2IntMap<LabeledNode> GET_SSSP_BellmanFord(TNGraph<E> graph,
	                                                                           final LabeledNode source,
	                                                                           STNCheckStatus checkStatus1) {
		if (graph == null) {
			return null;
		}
		final Collection<LabeledNode> nodes = graph.getVertices();
		if (source != null && !nodes.contains(source)) {
			return null;
		}
		final int n = nodes.size();
		final Collection<E> edges = graph.getEdges();
		final int horizon = (source == null) ? 0 : Constants.INT_POS_INFINITE;
		final Object2IntMap<LabeledNode> solution = new Object2IntOpenHashMap<>();
		solution.defaultReturnValue(horizon);
		graph.getVertices().forEach((node) -> solution.put(node, horizon));//to guarantee that
		//if one list the distance map, he can find all nodes.

		if (source != null) {
			solution.put(source, 0);
		}

		LabeledNode s, d;
		for (int i = 1; i < n; i++) {// n-1 rounds
			boolean update = false;
			for (final E e : edges) {
				s = graph.getSource(e);
				d = graph.getDest(e);
				// make sure that the edge has a significative value
				final int edgeValue = e.getValue();
				if (edgeValue == Constants.INT_NULL || edgeValue == Constants.INT_POS_INFINITE) {
					continue;
				}
				final int v = Constants.sumWithOverflowCheck(solution.getInt(s), edgeValue);
				final int dValue = solution.getInt(d);
				if (dValue > v) {
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							assert d != null;
							LOG.finest(
								"SSSP_BellmanFord " + d.getName() + " potential: " + Constants.formatInt(dValue) +
								" --> " + Constants.formatInt(v));
						}
					}
					solution.put(d, v);
					update = true;
					if (checkStatus1 != null) {
						checkStatus1.propagationCalls++;
					}
				}
			}
			if (!update) {
				if (checkStatus1 != null) {
					checkStatus1.cycles = i;
					checkStatus1.consistency = true;
					checkStatus1.finished = true;
				}
				return solution;
			}
		}
		// check if a negative cycle is present
		for (final E e : edges) {
			s = graph.getSource(e);
			d = graph.getDest(e);
			final int edgeValue = e.getValue();
			if (edgeValue == Constants.INT_NULL || edgeValue == Constants.INT_POS_INFINITE) {
				continue;
			}
			final int v = Constants.sumWithOverflowCheck(solution.getInt(s), edgeValue);
			final int dValue = solution.getInt(d);
			if (dValue > v) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						assert d != null;
						LOG.finest(
							"SSSP_BellmanFord " + d.getName() + " potential: " + Constants.formatInt(dValue) + " --> " +
							Constants.formatInt(v));
					}
				}
				if (checkStatus1 != null) {
					checkStatus1.consistency = false;
					checkStatus1.finished = true;
					checkStatus1.negativeLoopNode = d;
				}
				return null;
			}
		}
		if (checkStatus1 != null) {
			checkStatus1.cycles = n;
			checkStatus1.consistency = true;
			checkStatus1.finished = true;
		}
		return solution;
	}

	/**
	 * Determines the minimal distance between source node and any node reachable by source
	 * (single-source-shortest-paths (SSSP)) using the Dijkstra algorithm.<br> Minimal distances are returned as map
	 * {@code (destinationNode, distance)}.<br> If a node is not reachable from the source, its distance is +∞.<br> If
	 * the graph contains a negative edge beyond the source outgoing edges or the source is not in the graph, it returns
	 * false.
	 *
	 * @param <E>          the kind of edge
	 * @param graph        input graph. Each edge must have a positive weight but the edges outgoing from source, that
	 *                     can have a negative weight.
	 * @param source       the source node. It must belong to graph.
	 * @param checkStatus1 status to update with statistics of algorithm. It can be null.
	 *
	 * @return null or a non-empty map {@code (node, integer)} representing the distances of all nodes from the given
	 * 	source. Null is returned if graph is empty or source not in graph or negative edge beyond source edges has been
	 * 	found. If a node is not reachable from the source, its distance is +∞.
	 */
	@Nullable
	static <E extends STNEdge> Object2IntMap<LabeledNode> GET_SSSP_Dijkstra(TNGraph<E> graph, LabeledNode source,
	                                                                        STNCheckStatus checkStatus1) {

		final Collection<LabeledNode> nodes = graph.getVertices();
		final int n = nodes.size();
		if (!nodes.contains(source)) {
			return null;
		}
		int v;

		final ExtendedPriorityQueue<LabeledNode> nodeQueue = new ExtendedPriorityQueue<>();
		nodeQueue.insertOrUpdate(source, 0);

		LabeledNode s, d;
		BasicEntry<LabeledNode> entry;
		int sValue, eValue;
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Determining distance from source node " + source);
			}
		}
		while (!nodeQueue.isEmpty()) {
			entry = nodeQueue.extractFirstEntry();
			s = entry.getKey();
			sValue = entry.getIntValue();
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("Considering node " + s.getName() + " having distance " + Constants.formatInt(sValue));
				}
			}
			for (final E e : graph.getOutEdges(s)) {
				d = graph.getDest(e);
				assert d != null;
				eValue = e.getValue();
				if (eValue < 0 && !s.equalsByName(source)) {// s != source is for allowing the use of Dijkstra when the
					// edges from source are negative (it is a particular use of Dijkstra algorithm).
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest(
								"Edge " + e + " has a negative value but it shouldn't. Source is " + source.getName() +
								". Destination is " + d.getName());
						}
					}
					return null;
				}
				v = Constants.sumWithOverflowCheck(sValue, eValue);
				final int dPriority = nodeQueue.getPriority(d);
				if (dPriority == Constants.INT_POS_INFINITE) {
					// d is not in the queue
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest("Adds '" + d.getName() + "' to the queue with value " + Constants.formatInt(v));
						}
					}
					nodeQueue.insertOrUpdate(d, v);
					continue;
				}
				if (dPriority > v && nodeQueue.getStatus(d) == Status.isPresent) {
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest(
								"Updates '" + d.getName() + "' node potential adding edge value " + eValue + ": " +
								Constants.formatInt(dPriority) + " --> " + Constants.formatInt(v));
						}
					}
					nodeQueue.insertOrUpdate(d, v);
					if (checkStatus1 != null) {
						checkStatus1.propagationCalls++;
					}
				}
			}
		}
		if (checkStatus1 != null) {
			checkStatus1.cycles = n;
			checkStatus1.consistency = true;
			checkStatus1.finished = true;
		}
		final Object2IntMap<LabeledNode> result = nodeQueue.getAllDeterminedPriorities();
		result.defaultReturnValue(
			Constants.INT_POS_INFINITE);//if one asks the distance of non reached node, the answer is +∞
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Determined node distances from " + source + ": " + result);
			}
		}
		return result;
	}

	/**
	 * Returns the predecessor graph of the given {@code source} in the given STN {@code graph}.
	 * <p>
	 * <b>NOTE</b><br>
	 * This method is thought to be used as a subroutine of {@link #MAKE_MINIMAL_DISPATCHABLE(TNGraph)} and
	 * {@link #makeMinimalDispatchable()}.<br> It does not call {@link #initAndCheck()}.<br> Moreover, the given STN
	 * must be consistent, otherwise the returned solution is not correct.<br> The returned graph is an independent
	 * graph but that share nodes and edges with the input graph.<br> It contains only node that are reachable from
	 * source.<br> The distances from the {@code source} to the nodes are stored in the given map 'distanceFromSource'
	 * (that is clear before the use).<br> If a node has more than one parent edges, such edges are present in the graph
	 * even though {@link LabeledNode#getPredecessor()} returns the last found predecessor.
	 * </p>
	 *
	 * @param <E>                the kind of edges.
	 * @param graph              the input graph. It will be not modified.
	 * @param source             a node of this STN.
	 * @param graphNodePotential a solution for the STN as map {@code (node, potential)}. If null, it is determined
	 *                           locally.
	 * @param distanceFromSource a map that will be filled by the distances of nodes reachable from the source. if null,
	 *                           it is ignored.
	 * @param graphToReuse       a graph structure to use for storing the predecessor graph. This parameter can be null.
	 *                           The scope of this parameter is to avoid to generate new graphs when many predecessor
	 *                           graphs have to be generated sequentially and in short time. Reusing one predecessor
	 *                           graph avoid a possible out-of-memory (verified on 2022-01-09).
	 *
	 * @return the predecessor graph of node X, if the STN is consistent and X belongs to this STN, null otherwise.
	 *
	 * @see #GET_STN_PREDECESSOR_SUBGRAPH(LabeledNode)
	 */
	@Nullable
	static <E extends STNEdge> TNPredecessorGraph<E> GET_STN_PRECEDESSOR_SUBGRAPH_OPTIMIZED(@Nonnull TNGraph<E> graph,
	                                                                                        @Nonnull LabeledNode source,
	                                                                                        Object2IntMap<LabeledNode> graphNodePotential,
	                                                                                        Object2IntMap<LabeledNode> distanceFromSource,
	                                                                                        @Nullable TNPredecessorGraph<E> graphToReuse) {
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Start the determination of the predecessor graph of node " + source +
				           " using a given nodePotential.");
			}
		}
		if (!graph.containsVertex(source)) {
			return null;
		}
		if (graphNodePotential == null) {
			graphNodePotential = STN.GET_SSSP_BellmanFord(graph, null, null);
			if (graphNodePotential == null) {
				if (Debug.ON) {
					LOG.fine("The network is not consistent. Giving up!");
				}
				return null;
			}
		}
		final TNPredecessorGraph<E> predecessorGraph;
		if (graphToReuse != null) {
			graphToReuse.clear();
			predecessorGraph = graphToReuse;
		} else {
			predecessorGraph = new TNPredecessorGraph<>();
		}
		if (graph.getZ() != null) {
			predecessorGraph.setZ(graph.getZ());
		}
		if (source != graph.getZ()) {
			predecessorGraph.addNode(source);
		}
		if (distanceFromSource != null) {
			distanceFromSource.clear();
		}

		final ExtendedPriorityQueue<LabeledNode> queue = new ExtendedPriorityQueue<>();
		queue.insertOrUpdate(source, -graphNodePotential.getInt(source));
		while (!queue.isEmpty()) {
			final BasicEntry<LabeledNode> minQueueEntry = queue.extractFirstEntry();
			final LabeledNode X = minQueueEntry.getKey();
			final int XKey = minQueueEntry.getIntValue();

			// Convert to corresponding distance in original graph
			final int XDistance = Constants.sumWithOverflowCheck(XKey, graphNodePotential.getInt(X));
			if (distanceFromSource != null) {
				distanceFromSource.put(X, XDistance);
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("Considering node " + X + " having distance " + Constants.formatInt(XDistance) + ".");
				}
			}
			for (final Pair<E, LabeledNode> entry : graph.getOutEdgesAndNodes(X)) {
				final E e = entry.left();
				final int eValue = e.getValue();
				if (eValue == Constants.INT_NULL) {// it is not a proper STNEdge
					continue;
				}

				final LabeledNode Y = entry.right();
				predecessorGraph.addNode(Y);

				if (Y == X) {
					continue;// avoid loop
				}

				// Convert Y distance in a queue priority
				final int newYKey = Constants.sumWithOverflowCheck(Constants.sumWithOverflowCheck(XDistance, eValue),
				                                                   -graphNodePotential.getInt(Y));
				final int YKey = queue.getPriority(Y);
				if (newYKey > YKey) {
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest(
								"Successor node " + Y + " would have new priority " + Constants.formatInt(newYKey) +
								" with respect to the source " + source + " greater than its current one " +
								Constants.formatInt(YKey) + ". Ignored.");
						}
					}
					continue;
				}
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("Successor node " + Y + " has new priority " + Constants.formatInt(newYKey) +
						           " with respect to the source " + source + " while its previous priority was " +
						           Constants.formatInt(YKey));
					}
				}
				final Status YStatus = queue.getStatus(Y);
				if (YStatus == Status.wasPresent) {// already popped from the queue
					if (newYKey == YKey) {// found another shortest path
						predecessorGraph.addEdge(e, X, Y);
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINEST)) {
								LOG.finest("Case 1: " + X + " is another predecessor of " + Y + ". Added edge: " + e);
							}
						}
					}
				} else {
					// Y is present or not yet in the queue
					if (YStatus == Status.isPresent) {// Y currently in the queue
						if (newYKey == YKey) {// found another shortest path
							predecessorGraph.addEdge(e, X, Y);
							if (Debug.ON) {
								if (LOG.isLoggable(Level.FINEST)) {
									LOG.finest(
										"Case 2: " + X + " is another predecessor of " + Y + ". Added edge: " + e);
								}
							}
						} else {
							//newYKey < YKey
							// found a shorter path than any previous
							// Although we use potential for considering nodes in order by distance,
							// it can occur that a node X is not insert with the minimal key in the queue
							// because the considered edge is not in the shortest path.
							// Then, when another node Y is considered, again X is its neighbor with the same
							// key, so the edge is added... but, then, such edge must be removed when X is reconsidered
							// with the minimal key.
							queue.insertOrUpdate(Y, newYKey);
							// remove possible previous parents
							predecessorGraph.clearPredecessors(Y);

							predecessorGraph.addEdge(e, X, Y);
							if (Debug.ON) {
								if (LOG.isLoggable(Level.FINEST)) {
									LOG.finest("Case 2b: " + X + " is predecessor of " + Y +
									           " a new shortest path found. Remove previous edges and add: " + e);
								}
							}
						}
					} else {// Y is not in the queue
						queue.insertOrUpdate(Y, newYKey);
						predecessorGraph.addEdge(e, X, Y);
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINEST)) {
								LOG.finest(
									"Case 3: " + X + " is the first predecessor found of " + Y + ". Added edge: " + e);
							}
						}
					}
				}
			}
		}
		return predecessorGraph;
	}

	/**
	 * Returns the predecessor graph of the given {@code source} in the given STN {@code graph}.
	 * <p>
	 * <b>NOTE</b><br>
	 * Since this method is thought to be used as a subroutine of FastDispatchMinimization algorithm, it does not call
	 * {@link #initAndCheck()}.<br> Moreover, the given STN must be consistent, otherwise the returned solution is not
	 * correct.<br> The returned graph is an independent graph but that share nodes and edges with the input graph.<br>
	 * It contains only node that are reachable from source.<br> The distances from the {@code source} to the nodes are
	 * stored in the given map 'distanceFromSource' (that is clear before the use).<br> If a node has more than one
	 * parent edges, such edges are present in the graph even though {@link LabeledNode#getPredecessor()} returns the
	 * last found predecessor.
	 * </p>
	 *
	 * @param <E>                the kind of edges.
	 * @param graph              the input graph.  It will be not modified.
	 * @param source             a node of this STN
	 * @param graphNodePotential a solution for the STN as map {@code (node, potential)}.
	 * @param distanceFromSource a map that will be filled by the distances of nodes reachable from the source.
	 * @param graphToReuse       a graph structure to use for storing the predecessor graph. This parameter can be null.
	 *                           The scope of this parameter is to avoid to generate new graphs when many predecessor
	 *                           graphs have to be generated sequentially and in short time. Reusing one predecessor
	 *                           graph avoid a possible out-of-memory (verified on 2022-01-09).
	 *
	 * @return the predecessor graph of node X, if the STN is consistent and X belongs to this STN, null otherwise.
	 *
	 * @see #GET_STN_PREDECESSOR_SUBGRAPH(LabeledNode)
	 */
	@Nullable
	@SuppressWarnings("SameParameterValue")
	static <E extends STNEdge> TNGraph<E> GET_STN_PREDECESSOR_SUBGRAPH(@Nonnull TNGraph<E> graph,
	                                                                   @Nonnull LabeledNode source,
	                                                                   @Nonnull Object2IntMap<LabeledNode> graphNodePotential,
	                                                                   @Nonnull Object2IntMap<LabeledNode> distanceFromSource,
	                                                                   @Nullable TNGraph<E> graphToReuse) {
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Start the determination of the predecessor graph of node " + source.getName() +
				           " using a given nodePotential.");
			}
		}
		if (!graph.containsVertex(source)) {
			return null;
		}
		final TNGraph<E> predecessorGraph;
		if (graphToReuse != null) {
			graphToReuse.clear(graph.getVertexCount());
			predecessorGraph = graphToReuse;
		} else {
			predecessorGraph = new TNGraph<>(source.getName() + "Predecessor", graph.getEdgeImplClass());
		}
		if (graph.getZ() != null) {
			predecessorGraph.setZ(graph.getZ());
		}
		if (source != graph.getZ()) {
			predecessorGraph.addVertex(source);
		}
		distanceFromSource.clear();

		final ExtendedPriorityQueue<LabeledNode> queue = new ExtendedPriorityQueue<>();
		queue.insertOrUpdate(source, -graphNodePotential.getInt(source));
		while (!queue.isEmpty()) {
			final BasicEntry<LabeledNode> minQueueEntry = queue.extractFirstEntry();
			final LabeledNode X = minQueueEntry.getKey();
			assert X != null;
			final int XKey = minQueueEntry.getIntValue();

			// Convert to corresponding distance in original graph
			final int XDistance = Constants.sumWithOverflowCheck(XKey, graphNodePotential.getInt(X));
			distanceFromSource.put(X, XDistance);
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("Considering node " + X + " having distance " + Constants.formatInt(XDistance) + ".");
				}
			}
			for (final E e : graph.getOutEdges(X)) {
				final int eValue = e.getValue();
				if (eValue == Constants.INT_NULL) {// it is not a STNEdge
					continue;
				}

				final LabeledNode Y = graph.getDest(e);
				assert Y != null;
				if (!predecessorGraph.containsVertex(Y)) {
					predecessorGraph.addVertex(Y);
				}
				if (Y == X) {
					continue;// avoid loop
				}

				// Convert Y distance in a queue priority
				final int newYKey = Constants.sumWithOverflowCheck(Constants.sumWithOverflowCheck(XDistance, eValue),
				                                                   -graphNodePotential.getInt(Y));
				final int YKey = queue.getPriority(Y);
				if (newYKey > YKey) {
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest(
								"Successor node " + Y + " would have new priority " + Constants.formatInt(newYKey) +
								" with respect to the source " + source + " greater than its current one " +
								Constants.formatInt(YKey) + ". Ignored.");
						}
					}
					continue;
				}
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest(
							"Considering successor node " + Y + " having priority " + Constants.formatInt(newYKey) +
							" with respect to the source while its previous priority is " + YKey);
					}
				}
				final Status YStatus = queue.getStatus(Y);
				if (YStatus == Status.wasPresent) {// already popped from the queue
					if (newYKey == YKey) {// found another shortest path
						// E eCopy = MAKE_NEW_EDGE(predecessorGraph, e.getName(), e.getConstraintType());
						// eCopy.setValue(eValue);
						predecessorGraph.addEdge(e, X, Y);
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINEST)) {
								LOG.finest("Case 1: " + X + " is another predecessor of " + Y + ". Added edge: " + e);
							}
						}
					}
				} else {
					// Y is present or not yet in the queue
					if (YStatus == Status.isPresent) {// Y currently in the queue
						if (newYKey == YKey) {// found another shortest path
							// E eCopy = MAKE_NEW_EDGE(predecessorGraph, e.getName(), e.getConstraintType());
							// eCopy.setValue(eValue);
							predecessorGraph.addEdge(e, X, Y);
							if (Debug.ON) {
								if (LOG.isLoggable(Level.FINEST)) {
									LOG.finest(
										"Case 2: " + X + " is another predecessor of " + Y + ". Added edge: " + e);
								}
							}
						} else {
							// newYKey < YKey
							// found a shorter path than any previous
							// Although we use potential for considering nodes in order by distance,
							// it can occur that a node X is not insert with the minimal key in the queue
							// because the considered edge is not in the shortest path.
							// Then, when another node Y is considered, again X is its neighbor with the same
							// key, so the edge is added... but, then, such edge must be removed when X is reconsidered
							// with the minimal key.
							queue.insertOrUpdate(Y, newYKey);
							// remove possible previous parents
							predecessorGraph.getInEdges(Y).forEach(predecessorGraph::removeEdge);
							predecessorGraph.addEdge(e, X, Y);
							if (Debug.ON) {
								if (LOG.isLoggable(Level.FINEST)) {
									LOG.finest("Case 2b: " + X + " is predecessor of " + Y +
									           " a new shortest path found. Remove previous edges and add: " + e);
								}
							}
						}
					} else {// Y is not in the queue
						queue.insertOrUpdate(Y, newYKey);
						predecessorGraph.addEdge(e, X, Y);
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINEST)) {
								LOG.finest(
									"Case 3: " + X + " is the first predecessor found of " + Y + ". Added edge: " + e);
							}
						}
					}
				}
			}
		}
		return predecessorGraph;
	}

	/**
	 * Determines all the strong connected components (SCC) having size &gt; 1 and containing the source node, using the
	 * Tarjan algorithm.<br> If source is null, it determines all SCC of size &gt; 1 of the network.<br> It does not
	 * modify node or edges internal data.
	 *
	 * @param <E>    the kind of edges.
	 * @param graph  not null graph where to search strong connected components.
	 * @param source the source node for the SCC. If null, all nodes of the network are considered as source.
	 *
	 * @return the list of possible SCCs of size &gt; 1, each of them as a list of original nodes. If there is no SCCs,
	 * 	the list is empty.
	 */
	static public <E extends STNEdge> ObjectList<ObjectList<LabeledNode>> GET_STRONG_CONNECTED_COMPONENTS(
		TNGraph<E> graph, @Nullable LabeledNode source) {

		final SCCTime time = new SCCTime();
		final ObjectList<ObjectList<LabeledNode>> strongConnectedComponents = new ObjectArrayList<>();
		final int n = graph.getVertexCount();
		final Object2IntMap<LabeledNode> number = new Object2IntOpenHashMap<>(n);
		number.defaultReturnValue(Constants.INT_NULL);
		final Object2IntMap<LabeledNode> lowLink = new Object2IntOpenHashMap<>(n);
		lowLink.defaultReturnValue(Constants.INT_NULL);
		final ObjectArrayList<LabeledNode> stack = new ObjectArrayList<>();// it is a stack implementation
		final Object2BooleanMap<LabeledNode> onStack = new Object2BooleanLinkedOpenHashMap<>(n);
		onStack.defaultReturnValue(false);

		if (source != null) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {LOG.finest("Starting GET_STRONG_CONNECTED_COMPONENTS_HELPER from the given node " + source);}
			}
			GET_STRONG_CONNECTED_COMPONENTS_HELPER(graph, source, time, number, lowLink, stack, onStack, strongConnectedComponents);
			return strongConnectedComponents;
		}
		for (final LabeledNode node : graph.getVertices()) {
			if (number.getInt(node) == Constants.INT_NULL) {
				// node has not yet encountered, explore it in depth-first way
				GET_STRONG_CONNECTED_COMPONENTS_HELPER(graph, node, time, number, lowLink, stack, onStack, strongConnectedComponents);
			}
		}
		return strongConnectedComponents;
	}

	/**
	 * Returns all un-dominated edges emanating from a given {@code source} in the predecessor graph without determining
	 * and storing the All-Pairs-Shortest-Paths.
	 *
	 * @param <E>                the kind of edges
	 * @param source             the source node in {@code predecessorGraph}.
	 * @param predecessorGraph   the predecessor graph of source node.
	 * @param distanceFromSource distances from the source in the predecessor graph.
	 * @param edgeFactory        factory for edge of type E.
	 *
	 * @return the list of the un-dominated edges emanating from {@code source}. Each edge is new, i.e., not shared with
	 * 	predecessorGraph. Each element of the list is a pair {@code (Destination node name, edge object)}. The list is
	 * 	never null, but it could be empty.
	 */
	static <E extends STNEdge> ObjectList<Pair<LabeledNode, E>> GET_UNDOMINATED_EDGES(
		@Nonnull LabeledNode source,
		@Nonnull TNPredecessorGraph<E> predecessorGraph,
		@Nonnull Object2IntMap<LabeledNode> distanceFromSource,
		@Nonnull EdgeSupplier<E> edgeFactory) {

		final ObjectList<LabeledNode> reverseOrder = GET_REVERSE_POST_ORDER_VISIT(predecessorGraph, source, null);

		// negAncDist(C) == true if an ancestor B of C (B!=C) in predecessorGraph has been discovered with a
		// negative distance from source.
		final Object2BooleanMap<LabeledNode> negAncDist = new Object2BooleanLinkedOpenHashMap<>();
		negAncDist.defaultReturnValue(false);

		// minAncDist[C] == min value (so far) of distance from source to B, among ancestors B of C in predecessorGraph,
		// B != C and B != source.
		// use the name of node
		final Object2IntMap<LabeledNode> minAncDist = new Object2IntLinkedOpenHashMap<>();
		minAncDist.defaultReturnValue(Constants.INT_POS_INFINITE);

		// undominated edges
		final ObjectList<Pair<LabeledNode, E>> undominatedEdges = new ObjectArrayList<>();

		assert reverseOrder != null;
		reverseOrder.removeFirst();// first element is source, that is not processed.
		for (final LabeledNode node : reverseOrder) {
			// All ancestors of node in predecessorGraph (other than source) have been processed by this time
			final int distanceFromSourceOfNode = distanceFromSource.getInt(node);
			if (distanceFromSourceOfNode < 0) {
				// 1st case: (source, node) is a negative edge in inverted predecessor graph
				if (!negAncDist.getBoolean(node)) {
					// node has no ancestors with negative potential
					final E e = edgeFactory.get(source.getName() + "-" + node.getName());
					e.setConstraintType(ConstraintType.derived);
					e.setValue(distanceFromSourceOfNode);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest("Found undominated edge (1st case): " + e);
						}
					}
					undominatedEdges.add(new ObjectObjectImmutablePair<>(node, e));
				}
				// Follow successor edges emanating from node to update negAncDist? and minAncDist
				for (final ObjectObjectImmutablePair<LabeledNode, E> entry : predecessorGraph.getSuccessors(node)) {
					final LabeledNode d = entry.key();
					negAncDist.put(d, true);
					minAncDist.put(d, Math.min(minAncDist.getInt(d),
					                           Math.min(minAncDist.getInt(node), distanceFromSourceOfNode)));
				}
			} else {
				// 2nd case: (source,node) is a non-negative edge in the predecessor graph
				if (minAncDist.getInt(node) > distanceFromSourceOfNode) {
					// no ancestor B of node has distance from source <= the distance of node
					final E e = edgeFactory.get(source.getName() + "-" + node.getName());
					e.setConstraintType(ConstraintType.derived);
					e.setValue(distanceFromSourceOfNode);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest("Found undominated edge (2nd case): " + e);
						}
					}
					undominatedEdges.add(new ObjectObjectImmutablePair<>(node, e));
				}
				for (final ObjectObjectImmutablePair<LabeledNode, E> entry : predecessorGraph.getSuccessors(node)) {
					final LabeledNode d = entry.key();
					// any ancestor of node is an ancestor of d
					negAncDist.put(d, negAncDist.getBoolean(d) || negAncDist.getBoolean(node));
					minAncDist.put(d, Math.min(minAncDist.getInt(d),
					                           Math.min(minAncDist.getInt(node), distanceFromSourceOfNode)));
				}
			}
		}
		return undominatedEdges;
	}

	/**
	 * Adds to the given graph the undominated edges and return such added/modified edges as a map
	 * {@code ((sourceName, destinationName), edge)}. It also removes all ordinary dominated edges.<br> This method
	 * does:
	 * <ol>
	 *     <li>not initialize the network. So, it is up to the user to call {@link #initAndCheck()} before this method.</li>
	 *     <li>manage rigid components, removing them before the minimization and restoring them before returning the final network.
	 *     Moreover, the edge added for restoring the rigid components are also returned as undominated edges.</li>
	 * </ol>
	 * <p>
	 * This method works also with edges that are specialization of STNEdge. If an edge does not contain a {@link STNEdge#getValue()}, it is ignored.
	 * </p>
	 *
	 * @param <E>   the specific kind of edge
	 * @param graph input network.
	 *
	 * @return the map {@code ((sourceName, destinationName), edge)} of undominated edges of the network if the network
	 * 	was made dispatchable, null if any error occurred.
	 */
	@Nullable
	public static <E extends STNEdge> Object2ObjectMap<ObjectPair<LabeledNode>, E> MAKE_MINIMAL_DISPATCHABLE(
		@Nonnull TNGraph<E> graph) {
		//The rigid components are determined finding the strongly connected components in a predecessor graph that reaches all node.
		//Since we cannot assume that Z reaches all node, we use a fake source, we add edges from fase source to all nodes with 0 weight
		//and, at the end, we remove such fake edges.
		final LabeledNode fakeSource = new LabeledNode("_FAKESOURCE_%d".formatted(System.currentTimeMillis()));
		graph.addVertex(fakeSource);
		//noinspection StringConcatenationMissingWhitespace
		MAKE_NODES_REACHABLE_BY(graph, fakeSource, 0, "P" + System.currentTimeMillis());
		//
		// 1) Determine a solution that will be used by the following phases.
		//
		final Object2IntMap<LabeledNode> nodePotential = GET_SSSP_BellmanFord(graph, fakeSource, null);
		if (nodePotential == null) {
			if (Debug.ON) {
				LOG.fine("The network is not consistent. Giving up!");
			}
			return null;
		}

		//
		// 2) Using the solution and the predecessor graph of fakeSource, remove all possible rigid components.
		//
		final Object2IntMap<LabeledNode> distanceFromSource = new Object2IntOpenHashMap<>();
		if (Debug.ON) {
			LOG.finest("Finding FakeSource predecessor...");
		}

		final TNGraph<E> fakeSourcePredecessorGraph =
			GET_STN_PREDECESSOR_SUBGRAPH(graph, fakeSource, nodePotential, distanceFromSource, null);

		if (fakeSourcePredecessorGraph == null) {
			throw new IllegalStateException("Fake source predecessor graph is null while it shouldn't.");
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Fake source Predecessor:\n" + fakeSourcePredecessorGraph);
			}
			LOG.fine("Fake source predecessor done!\nFinding rigid components...");
		}

		final ObjectList<ObjectList<LabeledNode>> rigidComponents =
			GET_STRONG_CONNECTED_COMPONENTS(fakeSourcePredecessorGraph, fakeSource);

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("Rigid components: " + rigidComponents);
			}
		}

		Object2ObjectMap<LabeledNode, LabeledNode> nodeRepMap =
			Object2ObjectMaps.emptyMap();//(node->representative) map
		Object2IntMap<LabeledNode> nodeDistanceFromRep =
			Object2IntMaps.emptyMap(); //(node->int) distance from representative

		if (rigidComponents.size() > 0) {
			if (Debug.ON) {
				LOG.fine("Finding representatives for each rigid component...");
			}
			assert graph.getZ() != null;
			final Pair<Object2ObjectMap<LabeledNode, LabeledNode>, Object2IntMap<LabeledNode>> pairRepDistance =
				GET_REPRESENTATIVE_RIGID_COMPONENTS(rigidComponents, nodePotential, graph.getZ());
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
			COLLAPSE_RIGID_COMPONENT(graph, nodeRepMap, nodeDistanceFromRep);

			if (Debug.ON) {
				LOG.fine("Collapse done!");
			}
		}

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Removing fake node " + fakeSource + " and all its incident edges.");
			}
		}
		graph.removeVertex(fakeSource);

		//
		// 3) In each predecessor graph, find the undominated edges.
		//
		if (Debug.ON) {
			LOG.fine("Removed all fake structure done!\nFinding undominated edges...");
		}

		// the format of globalUndominatedEdges is ((sourceName, destinationName), edge)
		final Object2ObjectMap<ObjectPair<LabeledNode>, E> globalUndominatedEdges = new Object2ObjectOpenHashMap<>();

		final TNPredecessorGraph<E> graphToReuse = new TNPredecessorGraph<>();

		for (final LabeledNode node : graph.getVertices()) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("Finding " + node.getName() + " predecessor and its undominated edges...");
				}
			}
			final TNPredecessorGraph<E> nodePredecessor =
				GET_STN_PRECEDESSOR_SUBGRAPH_OPTIMIZED(graph, node, nodePotential, distanceFromSource, graphToReuse);
			if (nodePredecessor == null) {
				throw new IllegalStateException("Predecessor graph for node " + node + " is null. ");
			}
			final ObjectList<Pair<LabeledNode, E>> undominatedEdges =
				STN.GET_UNDOMINATED_EDGES(node, nodePredecessor, distanceFromSource, graph.getEdgeFactory());
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
							"Found a previous dominant edge " + e + " with a different value: old " + e.getValue() +
							", new " + pair.right().getValue());
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
			final E e = globalUndominatedEdges.get(s_d);
			final E eG = graph.findEdge(s, d);
			if (eG != null) {
				final int eValue = e.getValue();
				final int eGvalue = eG.getValue();
				if (eGvalue == Constants.INT_NULL || eGvalue > eValue) {
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest("Undominated edge  " + eG + " must be updated as " + e);
						}
					}
					eG.setValue(eValue);
				}
				continue;
			}
			graph.addEdge(e, s, d);
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Added edge " + e);
				}
			}
		}

		if (Debug.ON) {
			LOG.fine("Remove all dominated edges...");
		}
		// 4) remove dominated edges
		for (final E e : graph.getEdges()) {
			final int eValue = e.getValue();
			if (eValue == Constants.INT_NULL) {
				// we do not consider it as an ordinary edge. We must preserve.
				continue;
			}
			final LabeledNode s = graph.getSource(e);
			final LabeledNode d = graph.getDest(e);
			if (globalUndominatedEdges.get(new ObjectPair<>(s, d)) != null) {
				continue;
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Removing dominated " + e);
				}
			}
			graph.removeEdge(e);
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
					throw new IllegalStateException(
						"Rigid component " + rc + " has not a representative in the map " + nodeRepMap);
				}
				for (final LabeledNode node : rc) {
					if (node == rep) {
						continue;
					}
					final int nodeDistance = nodeDistanceFromRep.getInt(node);
					//FROM REP TO NODE.
					E e = graph.makeNewEdge(rep.getName() + "-" + node.getName(), ConstraintType.derived);
					e.setValue(nodeDistance);
					graph.addEdge(e, rep, node);
					globalUndominatedEdges.put(new ObjectPair<>(rep, node), e);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest("Added edge " + e + " from " + rep + " to " + node);
						}
					}
					//FROM NODE TO REP
					e = graph.makeNewEdge(node.getName() + "-" + rep.getName(), ConstraintType.derived);
					e.setValue(-nodeDistance);
					graph.addEdge(e, node, rep);
					globalUndominatedEdges.put(new ObjectPair<>(node, rep), e);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest("Added edge " + e + " from " + node + " to " + rep);
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
	 * Makes each node reachable by source adding an edge between source and each other node with value {@code horizon}
	 * and name prefixed by {@code prefix}, followed by "__", source name, "__", and node name.<br> The type of edge is
	 * {@link ConstraintType#internal}.
	 *
	 * @param <E>     the kind of edge.
	 * @param graph   a not-null graph
	 * @param source  a not-null source node. If it is not in graph, it is added.
	 * @param horizon the maximum absolute edge weight present in the graph. If it is {@link Constants#INT_NULL} or
	 *                {@link Constants#INT_POS_INFINITE}, the method returns.
	 * @param prefix  a non-null string representing the prefix in the name of the edge. It cannot be empty!
	 */
	static <E extends STNEdge> void MAKE_NODES_REACHABLE_BY(TNGraph<E> graph, LabeledNode source, int horizon,
	                                                        String prefix) {
		if (horizon == Constants.INT_NULL || horizon == Constants.INT_POS_INFINITE) {
			return;
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "Making all nodes reachable from " + source.getName());
			}
		}
		if (prefix.isEmpty()) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Prefix argument cannot be empty.");
				}
			}
			throw new IllegalArgumentException("Prefix argument cannot be empty.");
		}
		if (!graph.containsVertex(source)) {
			graph.addVertex(source);
		}

		for (final LabeledNode node : graph.getVertices()) {
			// 3. Checks that each no-source node has an edge from source
			if (node == source) {
				continue;
			}
			E edge = graph.findEdge(source, node);
			if (edge == null) {
				edge = graph.makeNewEdge(prefix + "_" + source.getName() + "_" + node.getName(),
				                         ConstraintType.internal);
				graph.addEdge(edge, source, node);
				edge.setValue(horizon);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.log(Level.FINEST,
						        "Added " + edge.getName() + ": " + source.getName() + "--(" + horizon + ")-->" +
						        node.getName());
					}
				}
			}
		}
	}

	/**
	 * Re-weights all edge weights using potentials of nodes. If any node potential is undefined or infinite, it returns
	 * false. The potential of a node is assumed to be stored in {@code node.potential}.
	 *
	 * @param <E>   the kind of edges.
	 * @param graph input graph
	 *
	 * @throws IllegalStateException it is not possible to re-weighting because potential value are not corrects.
	 */
	static <E extends STNEdge> void REWEIGH(@Nonnull TNGraph<E> graph) {
		final Collection<E> edges = graph.getEdges();
		for (final E e : edges) {
			final LabeledNode s = graph.getSource(e);
			final LabeledNode d = graph.getDest(e);
			assert s != null;
			final int sV = s.getPotential();
			assert d != null;
			final int dV = d.getPotential();
			final int eV = e.getValue();
			if (sV == Constants.INT_NULL || dV == Constants.INT_NULL || eV == Constants.INT_NULL ||
			    sV == Constants.INT_POS_INFINITE || dV == Constants.INT_POS_INFINITE ||
			    eV == Constants.INT_POS_INFINITE) {
				throw new IllegalStateException(
					"At least one of the following nodes contains a no valid value: " + s + " or " + d + " or " + e);
			}
			// new value of edge 'e' is the value of 'e' - the potential difference between destination and source: e - (d - s) = e + s - d
			final int newV = Constants.sumWithOverflowCheck(eV, Constants.sumWithOverflowCheck(sV, -dV));
			if (newV < 0) {
				throw new IllegalStateException(
					"Error in re-weighting. " + "Re-weighting " + e.getName() + ": Source potential: " +
					Constants.formatInt(sV) + ", Destination potential: " + Constants.formatInt(dV) + ". Edge value: " +
					Constants.formatInt(eV) + ". New value (source+edge-destination): " + Constants.formatInt(newV));
			}
			e.setValue(newV);
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("Re-weighting " + e.getName() + ": Source potential: " + Constants.formatInt(sV) +
					           ", Destination potential: " + Constants.formatInt(dV) + ". Edge value: " +
					           Constants.formatInt(eV) + ". New value (source+edge-destination): " +
					           Constants.formatInt(newV));
				}
			}
		}
	}

	/**
	 * Determines the minimal distance of each node from the given source (single-source-shortest-paths (SSSP)) using
	 * the Bellman-Ford-Tarjan algorithm.<br> It is the Bellman-Ford augmented by a cycle detection routine called
	 * 'Subtree disassembly' written by Tarjan.<br> If the STN graph is not consistent and the checkStatus parameter is
	 * not null, then the negative cycle is stored in the field {@link STNCheckStatus#negativeCycle}.<br> All node are
	 * made reachable by Z adding a constraint (Z,horizon,X) for each node X, where horizon is a given parameter.
	 *
	 * @param <E>          the kind of edges
	 * @param g1           The STN graph
	 * @param source       the starting node
	 * @param edgeValue     the function to retrieve the correct value of the edge. If null, it is set to {@code (e) -> e.getValue()}
	 * @param horizon      the maximum value for the potential. It is meaningful in the source-node search to guarantee
	 *                     that any node is reachable from the source. If it is not equal to
	 *                     {@value Constants#INT_NULL}, then the source node is connected to any node by an edge having
	 *                     horizon value. Otherwise, no edge is added and, if source cannot reach any node, the
	 *                     determined distances are meaningful only for reachable nodes. A safe value for horizon is the
	 *                     absolute greatest value present in the edges times the number of nodes.
	 * @param checkStatus1 status to update with statistics of algorithm. It can be null.
	 *
	 * @return A list of nodes forming a negative cycle if there is a negative cycle in g1; otherwise an empty list.<br>
	 * 	In case of an empty list, the minimal distances are stored in each node (field 'potential') and the predecessor
	 * 	graph is also implicit store as field 'p' (for parent or predecessor in each node).
	 */
	public static <E extends STNEdge> boolean SSSP_BFCT(@Nonnull TNGraph<E> g1, @Nonnull LabeledNode source,
	                                                    EdgeValue edgeValue,
	                                                    int horizon, STNCheckStatus checkStatus1) {
		if (edgeValue == null) {
			edgeValue = STNEdge::getValue;
		}

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Horizon value: " + Constants.formatInt(horizon) +
				           "\nAdding edges for guaranteeing that source node reaches each node.");
			}
		}
		final Collection<LabeledNode> nodes = g1.getVertices();
		final ObjectArrayFIFOSetQueue<LabeledNode> q = new ObjectArrayFIFOSetQueue<>();
		int localHorizon = horizon;

		@SuppressWarnings("StringConcatenationMissingWhitespace") final String prefix = "P" + System.currentTimeMillis();
		if (horizon != Constants.INT_NULL) {
			MAKE_NODES_REACHABLE_BY(g1, source, localHorizon, prefix);
			localHorizon = Constants.sumWithOverflowCheck(horizon, 1);// Use horizon+1 as +infinity value!
			// In this way, in the first cycle all nodes will be put in the queue.
		}

		for (final LabeledNode node : nodes) {
			node.setPotential((horizon != Constants.INT_NULL) ? localHorizon : Constants.INT_POS_INFINITE);
			node.setBefore(null);
			node.setAfter(null);
			node.setPredecessor(null);
			node.setStatus(LabeledNode.Status.UNREACHED);
		}

		source.setPotential(0);
		source.setBefore(source);
		source.setAfter(source);
		source.setStatus(LabeledNode.Status.LABELED);
		q.add(source);

		int n = 0;
		while (!q.isEmpty()) {
			final LabeledNode nodeX = q.dequeue();

			if (nodeX.getStatus() != LabeledNode.Status.LABELED) {
				continue;
			}
			for (final E e : g1.getOutEdges(nodeX)) {
				final LabeledNode nodeY = g1.getDest(e);
				assert nodeY != null;
				final int eValue = edgeValue.getValue(e);
				if (eValue == Constants.INT_NULL || eValue == Constants.INT_POS_INFINITE) {
					continue; //the edge is not a valid edge for #getValue or represent an infinite distance.
				}
				final int delta = Constants.sumWithOverflowCheck(nodeY.getPotential(), Constants.sumWithOverflowCheck(-nodeX.getPotential(), -eValue));
				if (delta > 0) {
					nodeY.setPotential(Constants.sumWithOverflowCheck(nodeY.getPotential(), -delta));
					nodeY.setPredecessor(nodeX);
					nodeY.setStatus(LabeledNode.Status.LABELED);
					q.add(nodeY);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest("\nNode Y: " + nodeY + "\tNode X: " + nodeX);
							LOG.finest("\nBefore subtreeDisassembly");
						}
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.log(Level.FINEST, printStatusNodesForBFCT(g1));
						}
					}
					final ObjectList<LabeledNode> cycle = subtreeDisassembly(g1, nodeY, nodeX, delta);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest("\nAfter subtreeDisassembly");
							LOG.log(Level.FINEST, printStatusNodesForBFCT(g1));
						}
					}
					if (checkStatus1 != null) {
						checkStatus1.propagationCalls++;
					}

					if (cycle != null) {
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("Found a negative cycle: " + cycle);
							}
						}
						if (checkStatus1 != null) {
							checkStatus1.consistency = false;
							checkStatus1.finished = true;
							checkStatus1.negativeCycle = cycle;
						}
						removeInternalEdgesWithPrefix(g1, source, prefix);
						return false;
					}
				}
			}
			nodeX.setStatus(LabeledNode.Status.SCANNED);
			n++;
		}
		if (checkStatus1 != null) {
			checkStatus1.cycles = n;
			checkStatus1.consistency = true;
			checkStatus1.finished = true;
		}
		removeInternalEdgesWithPrefix(g1, source, prefix);
		return true;
	}

	/**
	 * Determines the minimal distance from the give source node to each other node (single-source-shortest-paths
	 * (SSSP)) using the BellmanFord algorithm.<br> The minimal distance is stored as potential value in each node. If
	 * the graph contains a negative cycle, it resets the determined distances and returns false.
	 *
	 * @param <E>          the kind of edge. This method accepts any extensions of STNEdge. If
	 *                     {@link STNEdge#getValue()} does not return a valid value, the edge is ignored.
	 * @param graph        input graph. If it is null, the method returns false.
	 * @param source       the source node. If it is null, then a virtual temporary source is added for determining a
	 *                     virtual distance for each node, that will be non-positive. If it is not null, and it is not
	 *                     present in the graph, the method returns false.
	 * @param checkStatus1 status to update with statistics of algorithm. It can be null.
	 *
	 * @return true if the distances have been determined, false if a negative cycle or any other errors occurred.
	 */
	static <E extends STNEdge> boolean SSSP_BellmanFord(TNGraph<E> graph, final LabeledNode source, STNCheckStatus checkStatus1) {
		if (graph == null) {
			return false;
		}
		final Collection<LabeledNode> nodes = graph.getVertices();
		if (source != null && !nodes.contains(source)) {
			return false;
		}
		final int n = nodes.size();
		final Collection<E> edges = graph.getEdges();
		final int maxPotential = (source == null) ? 0 : Constants.INT_POS_INFINITE;

		graph.setAllPotential(maxPotential);

		if (source != null) {
			source.setPotential(0);
		}

		LabeledNode s, d;
		for (int i = 1; i < n; i++) {// n-1 rounds
			boolean update = false;
			for (final E e : edges) {
				s = graph.getSource(e);
				d = graph.getDest(e);
				// make sure that the edge has a significative value
				final int edgeValue = e.getValue();
				if (edgeValue == Constants.INT_NULL || edgeValue == Constants.INT_POS_INFINITE) {
					continue;
				}
				assert s != null;
				final int v = Constants.sumWithOverflowCheck(s.getPotential(), edgeValue);
				assert d != null;
				if (d.getPotential() > v) {
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest("SSSP_BellmanFord " + d.getName() + " potential: " +
							           Constants.formatInt(d.getPotential()) + " --> " + Constants.formatInt(v));
						}
					}
					d.setPotential(v);
					update = true;
					if (checkStatus1 != null) {
						checkStatus1.propagationCalls++;
					}
				}
			}
			if (!update) {
				if (checkStatus1 != null) {
					checkStatus1.cycles = i;
					checkStatus1.consistency = true;
					checkStatus1.finished = true;
				}
				return true;
			}
		}
		// check if a negative cycle is present
		for (final E e : edges) {
			s = graph.getSource(e);
			d = graph.getDest(e);
			final int edgeValue = e.getValue();
			if (edgeValue == Constants.INT_NULL || edgeValue == Constants.INT_POS_INFINITE) {
				continue;
			}
			assert s != null;
			final int v = Constants.sumWithOverflowCheck(s.getPotential(), edgeValue);
			assert d != null;
			if (d.getPotential() > v) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest(
							"SSSP_BellmanFord " + d.getName() + " potential: " + Constants.formatInt(d.getPotential()) +
							" --> " + Constants.formatInt(v));
					}
				}
				if (checkStatus1 != null) {
					checkStatus1.consistency = false;
					checkStatus1.finished = true;
					checkStatus1.negativeLoopNode = d;
				}
				return false;
			}
		}
		if (checkStatus1 != null) {
			checkStatus1.cycles = n;
			checkStatus1.consistency = true;
			checkStatus1.finished = true;
		}
		return true;
	}

	/**
	 * Determines the minimal distance between source node and any node (or any node and the sink (==source) if
	 * backward) using the BellmanFord algorithm. The minimal distance is stored as potential value in each node as well
	 * in the returned map. If the graph contains a negative cycle, it returns null.
	 *
	 * @param <E>              the kind of edge. This method accepts any extensions of STNEdge. If
	 *                         {@link STNEdge#getValue()} does not return a valid value, the edge is ignored.
	 * @param graph            input graph. If it is null, the method returns null.
	 * @param source           the source node. If it is null, then a virtual temporary source is added and search is
	 *                         forced to be forward. If it is not null, and it is not present in the graph, the method
	 *                         returns null.
	 * @param backward         true if the search has to be backward.
	 * @param horizon          the maximum value for the potential. It is meaningful in the forward search to guarantee
	 *                         that any node is reachable from the source.<br> If it is not equal to
	 *                         {@link Constants#INT_NULL}, then the source node is connected to any node by a virtual
	 *                         edge having horizon value (no edge are added to the graph).<br> If it is equal to
	 *                         {@link Constants#INT_NULL}, the determined distances are meaningful only for reachable
	 *                         nodes. A safe value for horizon is the absolute greatest value present in the edges times
	 *                         the number of nodes. In case that source is null, horizon is not considered because the
	 *                         virtual source is connected to all nodes by a virtual edge with value 0.
	 * @param setNodePotential true if also {@code node.potential} must be set.
	 * @param checkStatus1     status to update with statistics of algorithm. It can be null.
	 *
	 * @return the map of pairs (node, distanceFromSource) if the graph is consistent, null otherwise.
	 */
	@Nullable
	@SuppressWarnings({"SameParameterValue", "UnusedReturnValue"})
	static <E extends STNEdge> Object2IntMap<LabeledNode> getDistanceBellmanFord(TNGraph<E> graph, LabeledNode source,
	                                                                             boolean backward, int horizon,
	                                                                             final boolean setNodePotential,
	                                                                             STNCheckStatus checkStatus1) {
		if (graph == null) {
			return null;
		}
		final Collection<LabeledNode> nodes = graph.getVertices();
		if (source != null && !nodes.contains(source)) {
			return null;
		}
		final int n = nodes.size();
		final Collection<E> edges = graph.getEdges();
		final Object2IntMap<LabeledNode> solution = new Object2IntOpenHashMap<>();
		solution.defaultReturnValue(Constants.INT_POS_INFINITE);

		if (source == null) {
			horizon = 0;
			backward = false;
		}

		if (horizon != Constants.INT_NULL) {
			final int h = horizon;
			solution.defaultReturnValue(h);
			// defaultReturnValue is not sufficient because some nodes can be only source of edges, so never modified after.
			nodes.forEach((node) -> {
				solution.put(node, h);
				if (setNodePotential) {
					node.setPotential(h);
				}
			});
		}

		if (source != null) {
			if (setNodePotential) {
				source.setPotential(0);
			}
			solution.put(source, 0);
		}
		LabeledNode s, d;
		for (int i = 1; i < n; i++) {// n-1 rounds
			boolean update = false;
			for (final E e : edges) {
				if (backward) {// for single sink, each edge is reversed
					d = graph.getSource(e);
					s = graph.getDest(e);
				} else {
					s = graph.getSource(e);
					d = graph.getDest(e);
				}
				// make sure that the edge has a significative value
				final int edgeValue = e.getValue();
				if (edgeValue == Constants.INT_NULL || edgeValue == Constants.INT_POS_INFINITE) {
					continue;
				}
				final int v = Constants.sumWithOverflowCheck(solution.getInt(s), edgeValue);
				if (solution.getInt(d) > v) {
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							assert d != null;
							LOG.finest("BF " + d.getName() + " potential: " + Constants.formatInt(solution.getInt(d)) +
							           " --> " + Constants.formatInt(v));
						}
					}
					if (setNodePotential) {
						assert d != null;
						d.setPotential(v);
					}
					solution.put(d, v);
					update = true;
					if (checkStatus1 != null) {
						checkStatus1.propagationCalls++;
					}
				}
			}
			if (!update) {
				if (checkStatus1 != null) {
					checkStatus1.cycles = i;
					checkStatus1.consistency = true;
					checkStatus1.finished = true;
				}
				return solution;
			}
		}
		// check if a negative cycle is present
		for (final E e : edges) {
			if (backward) {// for single sink, each edge is reversed
				d = graph.getSource(e);
				s = graph.getDest(e);
			} else {
				s = graph.getSource(e);
				d = graph.getDest(e);
			}
			final int edgeValue = e.getValue();
			if (edgeValue == Constants.INT_NULL || edgeValue == Constants.INT_POS_INFINITE) {
				continue;
			}
			assert s != null;
			final int v = Constants.sumWithOverflowCheck(s.getPotential(), edgeValue);
			if (solution.getInt(d) > v) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						assert d != null;
						LOG.finest(
							"BF inconsistency:" + d.getName() + " potential: " + Constants.formatInt(d.getPotential()) +
							"-->" + Constants.formatInt(v));
					}
				}
				if (checkStatus1 != null) {
					checkStatus1.consistency = false;
					checkStatus1.finished = true;
					checkStatus1.negativeLoopNode = d;
				}
				return null;
			}
		}
		if (checkStatus1 != null) {
			checkStatus1.cycles = n;
			checkStatus1.consistency = true;
			checkStatus1.finished = true;
		}
		return solution;
	}

	/**
	 * Determines the minimal distance between source node and any node using the Dijkstra algorithm.<br> Each minimal
	 * distance is stored as potential value of the node. If a node is not reachable from the source, its distance is
	 * +∞. If the graph contains a negative edge beyond the source outgoing edges or the source is not in the graph, it
	 * returns false.
	 *
	 * @param <E>              the kind of edge
	 * @param graph            input graph. Each edge must have a positive weight but the edges outgoing from source,
	 *                         that can have a negative weight.
	 * @param source           the source node. It must belong to graph.
	 * @param setNodePotential true if also {@code node.potential} must be set. If true, it costs {@code +O(#nodes)}
	 *                         because it must initialize all the nodes with distance +∞.
	 * @param checkStatus1     status to update with statistics of algorithm. It can be null.
	 *
	 * @return null or a non-empty map (node, integer) representing the distances of all nodes from the given source.
	 * 	Null is returned if graph is empty or source not in graph or negative edge beyond source edges has been found.
	 * 	If a node is not reachable from the source, its distance is +∞.
	 */
	@SuppressWarnings({"UnusedReturnValue", "SameParameterValue"})
	static <E extends STNEdge> @Nullable Object2IntMap<LabeledNode> getDistanceDijkstra(TNGraph<E> graph,
	                                                                                    LabeledNode source,
	                                                                                    boolean setNodePotential,
	                                                                                    STNCheckStatus checkStatus1) {
		final Collection<LabeledNode> nodes = graph.getVertices();
		final int n = nodes.size();
		if (!nodes.contains(source)) {
			return null;
		}
		int v;

		final ExtendedPriorityQueue<LabeledNode> nodeQueue = new ExtendedPriorityQueue<>();
		nodeQueue.insertOrUpdate(source, 0);
		if (setNodePotential) {
			graph.getVertices().forEach((node) -> node.setPotential(Constants.INT_POS_INFINITE));
		}
		LabeledNode s, d;
		BasicEntry<LabeledNode> entry;
		int sValue, eValue;
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Determining distance from source node " + source);
			}
		}
		while (!nodeQueue.isEmpty()) {
			entry = nodeQueue.extractFirstEntry();
			s = entry.getKey();
			sValue = entry.getIntValue();
			if (setNodePotential) {
				source.setPotential(sValue);
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("Dijkstra. Considering node " + s.getName() + " having distance " +
					           Constants.formatInt(sValue));
				}
			}
			for (final E e : graph.getOutEdges(s)) {
				d = graph.getDest(e);
				assert d != null;
				eValue = e.getValue();
				if (eValue < 0 && !s.equalsByName(source)) {// s != source is for allowing the use of Dijkstra when the
					// edges from source are negative (it is a particular use of Dijkstra algorithm).
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest(
								"Edge " + e + " has a negative value but it shouldn't. Source is " + source.getName() +
								". Destination is " + d.getName());
						}
					}
					return null;
				}
				v = Constants.sumWithOverflowCheck(sValue, eValue);
				final int dPriority = nodeQueue.getPriority(d);
				if (dPriority == Constants.INT_POS_INFINITE) {
					// d is not in the queue
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest("Adds '" + d.getName() + "' to the queue with value " + Constants.formatInt(v));
						}
					}
					nodeQueue.insertOrUpdate(d, v);
					continue;
				}
				if (nodeQueue.getStatus(d) == Status.isPresent && dPriority > v) {
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest(
								"Updates '" + d.getName() + "' node potential adding edge value " + eValue + ": " +
								Constants.formatInt(dPriority) + " --> " + Constants.formatInt(v));
						}
					}
					nodeQueue.insertOrUpdate(d, v);
					if (checkStatus1 != null) {
						checkStatus1.propagationCalls++;
					}
				}
			}
		}
		if (checkStatus1 != null) {
			checkStatus1.cycles = n;
			checkStatus1.consistency = true;
			checkStatus1.finished = true;
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Dijkstra: determined node distances from " + source + ": " +
				           nodeQueue.getAllDeterminedPriorities().toString());
			}
		}
		final Object2IntMap<LabeledNode> result = nodeQueue.getAllDeterminedPriorities();
		result.defaultReturnValue(
			Constants.INT_POS_INFINITE);//if one asks the distance of non reached node, the answer is +∞

		return result;
	}

	/**
	 * @param args an array of {@link String} objects.
	 *
	 * @throws IOException                  if any.
	 * @throws ParserConfigurationException if any.
	 * @throws SAXException                 if any.
	 */
	public static void main(final String[] args) throws IOException, ParserConfigurationException, SAXException {
		final STN stn = new STN();
		System.out.println(stn.getVersionAndCopyright());
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Start...");
			}
		}
		if (!stn.manageParameters(args)) {
			return;
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Parameters ok!");
			}
		}
		System.out.println("Starting execution...");
		if (stn.versionReq) {
			return;
		}

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Loading graph...");
			}
		}
		final TNGraphMLReader<STNEdge> graphMLReader = new TNGraphMLReader<>();
		stn.setG(graphMLReader.readGraph(stn.fInput, STNEdgeInt.class));

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("STN Graph loaded!\nNow, it is time to check it...");
			}
		}
		final STNCheckStatus status;
		status = stn.consistencyCheck();
		if (status.finished) {
			System.out.println("Checking finished!");
			if (status.consistency) {
				System.out.println("The given STN is consistent!");
			} else {
				System.out.println("The given STN is not consistent!");
			}
			System.out.println("Details: " + status);
			System.out.println("Graph checked: " + stn.getGChecked());
		} else {
			System.out.println("Checking has not been finished!");
			System.out.println("Details: " + status);
		}
	}

	/**
	 * Subtree Disassembly strategy (Tarjan, 1981). Removes all descendants in graphGp of the given nodeY from Gp and
	 * from the queue Q because they are no more valid descendants.
	 *
	 * @param <E>   the kind of edges
	 * @param g     input predecessor graph. Is a subgraph of G(T,E) having the same nodes T and a set of edges
	 *              E'={p(X_i), delta_{p(X_i)i},X_i}
	 * @param nodeY subtree root node.
	 * @param nodeX the node to find
	 * @param delta the adjustment to apply to node distances.
	 *
	 * @return A list L of nodes forming a negative cycle if there is a negative cycle in G; otherwise an empty list;
	 * 	The G_p is adjusted accordingly.
	 */
	@Nullable
	static <E extends STNEdge> ObjectList<LabeledNode> subtreeDisassembly(TNGraph<E> g, LabeledNode nodeY, LabeledNode nodeX, int delta) {
		if (g == null) {
			return null;
		}
		final Collection<LabeledNode> nodes = g.getVertices();
		if (nodeX == null || !nodes.contains(nodeX)) {
			return null;
		}
		if (nodeY == null || !nodes.contains(nodeY)) {
			return null;
		}

		final LabeledNode nodeBeforeY = nodeY.getBefore();
		nodeY.setBefore(null);
		LabeledNode y = nodeY.getAfter();
		delta = delta - 1;

		while (y != null && y.getPredecessor() != null && y.getPredecessor().getBefore() == null) {

			if (y == nodeX) {
				final ObjectList<LabeledNode> l = new ObjectArrayList<>();
				// build the list in the reverse order
				while (y != nodeY) {
					l.add(y);
					y = y.getPredecessor();
				}
				l.add(nodeY);
				l.add(nodeX);
				// it is important to return in the list in the correct order
				final ObjectList<LabeledNode> l1 = new ObjectArrayList<>();
				for (int i = l.size(); --i != -1; ) {
					l1.add(l.get(i));
				}
				return l1;
			}
			y.setPotential(y.getPotential() - delta);
			y.setBefore(null);
			y.setStatus(LabeledNode.Status.UNREACHED);
			// Once the y is update with its `after`, its `after` must be `nullify`!
			final LabeledNode tmp = y.getAfter();
			y.setAfter(null);
			y = tmp;
		}
		if (nodeBeforeY != null) {
			nodeBeforeY.setAfter(y);
		}

		if (y != null) {
			y.setBefore(nodeBeforeY);
		}

		nodeY.setAfter(nodeX.getAfter());

		if (nodeY.getAfter() != null) {
			nodeY.getAfter().setBefore(nodeY); // Connect nodeY as nodeX son in the doubly-linked list
		}
		nodeY.setBefore(nodeX);
		nodeX.setAfter(nodeY);
		return null;
	}

	/**
	 * Algorithm 4: Yen's algorithm (adaptive version with early termination)
	 * <p>
	 * Implements Yen's algorithm presented in J. Y. Yen, “An algorithm for finding the shortest routes from all source
	 * nodes to a given destination in general networks,” Q. Applied Math., vol. 27, no. 4, pp. 526–530, Jan. 1970.
	 * <p>
	 * The source node is always Z. In case of forward check (backward=false), the method add the necessary edges
	 * (Z,horizon,X) for each X such that Z can reach any node. Such edges are removed at the end.
	 * <p>
	 * If the network is not initialized ({@link #initAndCheck()}, it calls {@link #initAndCheck()} before running.
	 *
	 * @param <E>         the kind of edges
	 * @param g1          input graph. If it is null, the method returns false.
	 * @param randomOrder true if nodes have to be ordered randomly. If false, nodes are ordered w.r.t. their name. In
	 *                    the case true, the algorithm is also known as Bannister and Eppstein
	 * @param backward    true if the search has to be done in backward way.
	 * @param horizon     the maximum value for the potential. It is meaningful in the source-node search to guarantee
	 *                    that any node is reachable from the source. If it is not equal to {@value Constants#INT_NULL},
	 *                    then the source node is connected to any node by an edge having horizon value. Otherwise, no
	 *                    edge is added and, if source cannot reach any node, the determined distances are meaningful
	 *                    only for reachable nodes. A safe value for horizon is the absolute greatest value present in
	 *                    the edges times the number of nodes.
	 * @param checkStatus status to update with statistics of algorithm. It can be null. *
	 *
	 * @return true if the STN is consistent, false otherwise. It also fills {@link #checkStatus}.
	 */
	@SuppressFBWarnings(value = "DMI_RANDOM_USED_ONLY_ONCE", justification = "I know what I'm doing")
	static <E extends STNEdge> boolean yenAlgorithm(@Nonnull TNGraph<E> g1, final boolean randomOrder,
	                                                final boolean backward, final int horizon,
	                                                STNCheckStatus checkStatus) {

		final LabeledNode Z = g1.getZ();
		if (checkStatus == null) {// checkStatus is necessary for counting the steps.
			checkStatus = new STNCheckStatus();
		}
		final String prefix = "_";
		if (!backward) {
			// I cannot trust that Z can reach any node
			// I add an edge Z-->node for each node with horizon value.
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Horizon value: " + Constants.formatInt(horizon) +
					          ". Adding edges for guaranteeing that Z reaches each node.");
				}
			}
			MAKE_NODES_REACHABLE_BY(g1, Z, horizon, prefix);
		}
		/*
		 * make a random order of nodes, putting Z at the first position. The random
		 * order is decided setting a random potential and, then, ordering the nodes
		 * w.r.t. the potential. The order is saved in an array and in a hash map.
		 * Potential is then reset.
		 */
		final int n = g1.getVertexCount();

		final LabeledNode[] orderedNodes;
		if (randomOrder) {
			orderedNodes = g1.getVertices().toArray(new LabeledNode[n]);
			final SecureRandom rnd = new SecureRandom();
			for (int i = 0; i < n; i++) {
				orderedNodes[i].setPotential(rnd.nextInt());
			}
			Arrays.sort(orderedNodes, 1, n, Comparator.comparingInt(LabeledNode::getPotential));
		} else {
			orderedNodes = g1.getVerticesArray();// already ordered but Z can be in the last positions
			int i;
			for (i = n; --i >= 0; ) {
				if (orderedNodes[i] == Z) {
					break;
				}
			}
			if (i > 0) {
				for (int j = i; --j >= 0; ) {
					orderedNodes[j + 1] = orderedNodes[j];
				}
				orderedNodes[0] = Z;
			}
		}
		final Object2IntMap<LabeledNode> nodeRdnIndex = new Object2IntLinkedOpenHashMap<>();
		/*
		 * Filling nodeRdnIndex and reset the potential
		 */
		for (int i = 0; i < n; i++) {
			nodeRdnIndex.put(orderedNodes[i], i);
			orderedNodes[i].setPotential(horizon + 1);
		}
		assert Z != null;
		Z.setPotential(0);

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.log(Level.FINEST, "Node ordering for Yen algorithm: " + Arrays.deepToString(orderedNodes));
			}
		}

		final NodesToCheck nodesToCheck = new NodesToCheck();
		nodesToCheck.add(Z);
		final NodesToCheck nodesModified = new NodesToCheck();
		LabeledNode s, d;
		int sIndex, dIndex, dOldValue, value;
		final int negativeCheckThreshold = n / 3 + 2;
		final int[] parent = new int[n];
		while (!nodesToCheck.isEmpty()) {
			// G-
			for (int i = n - 1; i >= 0; i--) {
				s = orderedNodes[i];
				sIndex = nodeRdnIndex.getInt(s);
				if (sIndex == 0) {
					continue;
				}
				final ObjectList<E> edges = (backward) ? g1.getIncidentEdges(s) : g1.getOutEdges(s);
				for (final E e : edges) {
					d = (backward) ? g1.getSource(e) : g1.getDest(e);
					assert d != null;
					dOldValue = d.getPotential();
					dIndex = nodeRdnIndex.getInt(d);
					if (dIndex < sIndex && (nodesToCheck.contains(s) || nodesModified.contains(s))) {
						value = Constants.sumWithOverflowCheck(s.getPotential(), e.getValue());
						if (value < dOldValue) {
							if (Debug.ON) {
								if (LOG.isLoggable(Level.FINEST)) {
									LOG.log(Level.FINEST,
									        "Subgraph G-. Edge: " + e + ((backward) ? "(reverse it)" : "") + ". " +
									        d.getName() + " value " + Constants.formatInt(dOldValue) + " new value: " +
									        Constants.formatInt(value));
								}
							}
							d.setPotential(value);
							nodesModified.add(d);
							checkStatus.propagationCalls++;
							parent[dIndex] = sIndex;
						}
					}
				}
			}
			// G+
			for (int i = 0; i < n; i++) {
				s = orderedNodes[i];
				sIndex = nodeRdnIndex.getInt(s);// i
				if (sIndex == n - 1) {
					continue;
				}
				final ObjectList<E> edges = (backward) ? g1.getIncidentEdges(s) : g1.getOutEdges(s);
				for (final E e : edges) {
					d = (backward) ? g1.getSource(e) : g1.getDest(e);
					assert d != null;
					dOldValue = d.getPotential();
					dIndex = nodeRdnIndex.getInt(d);
					if (dIndex > sIndex && (nodesToCheck.contains(s) || nodesModified.contains(s))) {
						value = Constants.sumWithOverflowCheck(s.getPotential(), e.getValue());
						if (value < dOldValue) {
							if (Debug.ON) {
								if (LOG.isLoggable(Level.FINEST)) {
									LOG.log(Level.FINEST,
									        "Subgraph G+. Edge: " + e + ((backward) ? "(reverse it)" : "") + ". " +
									        d.getName() + " value " + Constants.formatInt(dOldValue) + " new value: " +
									        Constants.formatInt(value));
								}
							}
							d.setPotential(value);
							nodesModified.add(d);
							checkStatus.propagationCalls++;
							parent[dIndex] = sIndex;
						}
					}
				}
			}
			nodesToCheck.clear();
			nodesToCheck.addAll(nodesModified);
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.log(Level.FINEST, "Next nodes to check:" + nodesToCheck);
				}
			}
			nodesModified.clear();
			checkStatus.cycles++;

			if (checkStatus.cycles > negativeCheckThreshold) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.log(Level.FINER, "Check the presence of negative cycle.");
					}
				}
				if (IS_NEGATIVE_CYCLE(parent)) {
					checkStatus.consistency = false;
					checkStatus.finished = true;
					if (!backward) {
						removeInternalEdgesWithPrefix(g1, Z, prefix);
					}
					return false;
				}
			}
		}
		// We maintain negative distance because they are equal to the values presented
		// in distance matrix
		// for (LabeledNode node : orderedNodes) {
		// node.setPotential(-node.getPotential());
		// }
		checkStatus.consistency = true;
		checkStatus.finished = true;
		if (!backward) {
			removeInternalEdgesWithPrefix(g1, Z, prefix);
		}
		return true;
	}

	/**
	 * Returns true if the given unweighted parent graph (assumed to be a tree) contains a negative cycle.<br> It is
	 * assumed that the root of tree is the node with index 0 and parent[0] == 0, i.e., root ha itself as parent.
	 *
	 * @param parent the array of parent indexes.
	 *
	 * @return true if there is a negative cycle.
	 */
	static private boolean IS_NEGATIVE_CYCLE(int[] parent) {
		if (parent[0] != 0) {
			// First node cannot have a parent!
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.log(Level.FINEST,
					        "The node has as parent the node with index " + parent[0] + ". Negative cycle!");
				}
			}
			return true;
		}
		final int n = parent.length;
		final int[] visitLevel = new int[n];// Initialized to 0

		int level = n;
		for (int i = 0; i < n; i++) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.log(Level.FINEST, "Check node of index " + i + " in the parent graph.");
				}
			}
			if (visitLevel[i] != 0) {
				continue;
			}
			// if (visitLevel[i] > level) {
			// node already visited
			// continue;
			// }
			visitLevel[i] = level;
			int p = parent[i];
			while (p != 0 && visitLevel[p] <= level) {
				if (visitLevel[p] == level) {
					// found a cycle
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							LOG.log(Level.FINER,
							        "The ancestor of index " + p + " is at the same level. Negative cycle!");
						}
					}
					return true;
				}
				visitLevel[p] = level;
				p = parent[p];
			}
			level--;
		}
		return false;
	}

	/**
	 * Helper recursive method for {@link #GET_STRONG_CONNECTED_COMPONENTS(TNGraph, LabeledNode)}.
	 *
	 * @param graph     the input graph
	 * @param V         the current node
	 * @param time      the current time of discovering.
	 * @param visitTime the time in which a node has been visit for the first time
	 * @param lowNode   earliest visited vertex (the vertex with minimum discovery time) that can be reached from
	 *                  subtree rooted with current node.
	 * @param stack     calling stack
	 * @param onStack   status of node in the stack
	 */
	private static <E extends STNEdge> void GET_STRONG_CONNECTED_COMPONENTS_HELPER(TNGraph<E> graph, LabeledNode V,
	                                                                               SCCTime time,
	                                                                               Object2IntMap<LabeledNode> visitTime,
	                                                                               Object2IntMap<LabeledNode> lowNode,
	                                                                               ObjectArrayList<LabeledNode> stack,
	                                                                               Object2BooleanMap<LabeledNode> onStack,
	                                                                               ObjectList<ObjectList<LabeledNode>> strongConnectedComponents) {
		final int VVisitTime = time.get();
		visitTime.put(V, VVisitTime);
		lowNode.put(V, VVisitTime);
		stack.push(V);
		onStack.put(V, true);
		for (final ObjectObjectImmutablePair<E, LabeledNode> entry : graph.getOutEdgesAndNodes(V)) {
			final E eVW = entry.key();
			if (eVW.getValue() == Constants.INT_NULL) {
				continue;// it is not an ordinary edge
			}
			final LabeledNode W = entry.value();
			if (visitTime.getInt(W) == Constants.INT_NULL) {
				// (node, v, d) is a "tree edge"
				GET_STRONG_CONNECTED_COMPONENTS_HELPER(graph, W, time, visitTime, lowNode, stack, onStack, strongConnectedComponents);
				// Check if the subtree rooted with v has a connection to one of the ancestors of NODE
				// Case 1
				lowNode.put(V, Math.min(lowNode.getInt(V), lowNode.getInt(W)));
			} else {
				if (onStack.getBoolean(W) && visitTime.getInt(W) < VVisitTime) {
					// (node, v, d) is a "frond" or a "cross-link" and d is not assigned to an SCC
					// Update low value of 'node' only if 'W' is still in stack (i.e. it's a back edge, not a cross edge).
					// Case 2
					lowNode.put(V, Math.min(lowNode.getInt(V), visitTime.getInt(W)));
				}
			}
		}
		// After exploring all of node's successor edges
		if (lowNode.getInt(V) == VVisitTime) {
			// V is the root of an SCC; time to accumulate the nodes in SCC
			final ObjectList<LabeledNode> newSCC = new ObjectArrayList<>();
			//PSEUDOCODE literal implementation but it is not efficient as the below implementation
//			while (!stack.isEmpty() && visitTime.getInt(stack.top()) >= VVisitTime){
//				LabeledNode node = stack.pop();
//				onStack.put(node, false);
//				newSCC.add(node);
//			}
			while (stack.top() != V) {//making the check stack.top() != node is faster
				final LabeledNode node = stack.pop();
				onStack.put(node, false);
				newSCC.add(node);
			}
			stack.pop();
			onStack.put(V, false);
			newSCC.add(V);
			if (newSCC.size() > 1) {
				strongConnectedComponents.add(newSCC);
			}
		}
	}

	/**
	 * @param g graph
	 *
	 * @return the parent/before/after fields of all nodes of g.
	 */
	@SuppressFBWarnings(value = "UPM", justification = "I know what I'm doing")
	private static <E extends STNEdge> String printStatusNodesForBFCT(TNGraph<E> g) {
		final Collection<LabeledNode> nodes = g.getVertices();
		final StringBuilder str = new StringBuilder(40);
		for (final LabeledNode node : nodes) {
			str.append("\nNode ").append(node.getName());
			str.append("   parent ").append((node.getPredecessor() != null) ? node.getPredecessor().getName() : "-");
			str.append("   after ").append((node.getAfter() != null) ? node.getAfter().getName() : "-");
			str.append("   before ").append((node.getBefore() != null) ? node.getBefore().getName() : "-");
		}
		return str.toString();
	}

	/**
	 * Removes all (added) internal edges outgoing source and having prefix in the name.
	 *
	 * @param graph  a not-null graph
	 * @param source a not-null source node. If it is not in graph, it is added.
	 * @param prefix a non-null string representing the prefix in the name of the edge. It cannot be empty!
	 */
	private static <E extends STNEdge> void removeInternalEdgesWithPrefix(TNGraph<E> graph, LabeledNode source,
	                                                                      String prefix) {
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE,
				        "Removing all internal edges from source " + source.getName() + " having prefix " + prefix);
			}
		}
		if (!graph.containsVertex(source)) {
			return;
		}
		if (prefix.isEmpty()) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Prefix argument cannot be empty.");
				}
			}
			return;
		}

		for (final E e : graph.getOutEdges(source)) {
			final String name = e.getName();
			if (e.getConstraintType() == ConstraintType.internal && name.startsWith(prefix)) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.log(Level.FINER, "Remove internal edge " + e);
					}
				}
				graph.removeEdge(e);
			}
		}
	}

	/**
	 * Check status
	 */
	STNCheckStatus checkStatus = new STNCheckStatus();
	/**
	 *
	 */
	@Option(name = "-cleaned", usage = "Output a cleaned result. A result cleaned graph does not contain empty edges or labeled values containing unknown literals.")
	boolean cleanCheckedInstance = true;
	/**
	 * Which algorithm to use for consistency check. Default is AllPairsShortestPaths (AllPairsShortestPaths)
	 */
	CheckAlgorithm defaultConsistencyCheckAlg = CheckAlgorithm.AllPairsShortestPaths;
	/**
	 * The input file containing the STN graph in GraphML format.
	 */
	@Argument(usage = "file_name must be the input STN graph in GraphML format.", metaVar = "file_name")
	File fInput;
	/**
	 * Output file where to write the XML representing the minimal STN graph.
	 */
	@Option(name = "-o", aliases = "--output", usage = "Output to this file. If file is already present, it is overwritten. If this parameter is not present, then the output is sent to the std output.", metaVar = "output_file_name")
	File fOutput;
	/**
	 * Input TNGraph.
	 */
	TNGraph<STNEdge> g;
	/**
	 * TNGraph on which to operate.
	 */
	TNGraph<STNEdge> gCheckedCleaned;
	/**
	 * Horizon value. A node that has to be executed after such time means that it has not to be executed!
	 */
	int horizon = Constants.INT_NULL;
	/**
	 * Absolute value of the max negative weight determined during initialization phase.
	 */
	int minNegativeWeight = Constants.INT_NULL;
	/**
	 * Timeout in seconds for the check.
	 */
	@Option(name = "-t", aliases = "--timeOut", usage = "Timeout in seconds for the check", metaVar = "seconds")
	int timeOut = 2700;
	/**
	 * Software Version.
	 */
	@Option(name = "-v", aliases = "--version", usage = "Version")
	boolean versionReq;

	/**
	 * @param graph       TNGraph to check
	 * @param giveTimeOut timeout for the check
	 */
	public STN(TNGraph<STNEdge> graph, int giveTimeOut) {
		this(graph);
		timeOut = giveTimeOut;
	}

	/**
	 * @param graph TNGraph to check
	 */
	public STN(TNGraph<STNEdge> graph) {
		this();
		setG(graph);// sets also checkStatus!
	}

	/**
	 * Default constructor.
	 */
	STN() {
	}

	/**
	 * Collapses rigid components of the current STN.
	 * <p>
	 * For each rigid components, only the representative node is maintained. All other nodes are removed from the
	 * graph. All incident edges on removed nodes are reallocated to the representative node. The representative node is
	 * determined as the nearest node to Z.
	 *
	 * @param rigidComponents the set of rigid components to collapse. If it is null, the method determines the rigid
	 *                        components calling {@link #getRigidComponents()}.
	 */
	public void COLLAPSE_RIGID_COMPONENT(@CheckForNull ObjectList<ObjectList<LabeledNode>> rigidComponents) {
		if (!checkStatus.initialized) {
			initAndCheck();
		}
		if (rigidComponents == null) {
			rigidComponents = getRigidComponents();
		}
		final Object2IntMap<LabeledNode> distanceFromZ = STN.GET_SSSP_BellmanFord(g, g.getZ(), null);
		if (distanceFromZ == null) {
			throw new IllegalStateException("The network is not consistent.");
		}

		assert g.getZ() != null;
		final Pair<Object2ObjectMap<LabeledNode, LabeledNode>, Object2IntMap<LabeledNode>> pair =
			GET_REPRESENTATIVE_RIGID_COMPONENTS(rigidComponents, distanceFromZ, g.getZ());
		COLLAPSE_RIGID_COMPONENT(g, pair.left(), pair.right());
	}

	/**
	 * Returns the Muscettola predecessor graph of the given {@code source} in the current STN.<br> Be aware that
	 * Muscettola predecessor graph is made by ALL shortest paths having {@code source} as origin node.<br> The returned
	 * graph is a ``view'' of this network. Nodes and edges are shared!<br> The distance from the {@code source} and
	 * node is stored in each node as potential value.<br> If current STN is not initialized ({@link #initAndCheck()},
	 * it calls {@link #initAndCheck()} before running.
	 *
	 * @param source a node of this STN
	 *
	 * @return the predecessor graph of node X, if the STN is consistent and X belongs (equals() identity) to this STN,
	 * 	null otherwise.
	 */
	@Nullable
	public TNGraph<STNEdge> GET_STN_PREDECESSOR_SUBGRAPH(@Nonnull LabeledNode source) {
		if (!g.containsVertex(source)) {
			if (STN.LOG.isLoggable(Level.FINE)) {
				STN.LOG.fine("This STN does not contain " + source.getName());
			}
			return null;
		}
		if (!checkStatus.initialized) {
			try {
				initAndCheck();
			} catch (final IllegalArgumentException e) {
				throw new IllegalArgumentException(
					"The STN graph has a problem, and it cannot be initialized: " + e.getMessage());
			}
		}
		final TNGraph<STNEdge> pGraph = new TNGraph<>(source.getName() + "Predecessor", g.getEdgeImplClass());
		pGraph.clear(g.getVertexCount());// allocate space once for all
		g.getVertices().forEach(pGraph::addVertex);
		pGraph.setZ(g.getZ());
		final Object2IntMap<LabeledNode> distance = STN.GET_SSSP_BellmanFord(g, source, null);
		if (distance == null) {
			STN.LOG.fine("The graph is not consistent.");
			return null;
		}
		final Object2BooleanMap<STNEdge> isVisited = new Object2BooleanLinkedOpenHashMap<>();
		isVisited.defaultReturnValue(false);
		if (Debug.ON) {
			if (STN.LOG.isLoggable(Level.FINEST)) {
				STN.LOG.finest("Start the determination of the predecessor graph of node " + source.getName());
			}
		}

		final ObjectArrayFIFOSetQueue<LabeledNode> nodeQueue = new ObjectArrayFIFOSetQueue<>();
		nodeQueue.add(source);
		while (!nodeQueue.isEmpty()) {
			final LabeledNode node = nodeQueue.dequeue();
			final int nodeValue = distance.getInt(node);
			node.setPotential(nodeValue);
			for (final STNEdge e : g.getOutEdges(node)) {
				final LabeledNode d = g.getDest(e);
				assert d != null;
				if (d == node) {
					continue;// avoid loop
				}
				if (!isVisited.getBoolean(e) && nodeValue + e.getValue() == distance.getInt(d)) {
					// e is in a `shortest` path
					isVisited.put(e, true);
					if (d != source) {
						nodeQueue.add(d);
					}
					if (Debug.ON) {
						if (STN.LOG.isLoggable(Level.FINER)) {
							STN.LOG.finer("Edge " + e.getName() + " must be in the predecessor graph.");
						}
					}
					pGraph.addEdge(e, node, d);
				}
			}
		}
		// pGraph.getEdges().stream().filter((e) -> !isVisited.getBoolean(e)).forEach((e) -> pGraph.removeEdge(e));
		return pGraph;
	}

	/**
	 * Determines all the strong connected components (SCC) of size &gt; 1 containing source node using the Tarjan
	 * algorithm.<br>
	 * <p>
	 * If source is null, it determines all SCC of size &gt; 1 of the network before running.
	 *
	 * @param source the source node for the SCC. If null, all nodes of the network are considered as source.
	 *
	 * @return the list of possible SCCs of size &gt; 1, each of them as a list of original nodes. If there is no SCCs,
	 * 	the list is empty.
	 *
	 * @see #GET_STRONG_CONNECTED_COMPONENTS(TNGraph, LabeledNode)
	 */
	public ObjectList<ObjectList<LabeledNode>> GET_STRONG_CONNECTED_COMPONENTS(LabeledNode source) {
		return STN.GET_STRONG_CONNECTED_COMPONENTS(g, source);
	}

	/**
	 * Checks the consistency of this STN instance within {@link #timeOut} seconds using the algorithm
	 * {@link #getDefaultConsistencyCheckAlg()}. To check the instance with a different algorithm, use
	 * {@link #setDefaultConsistencyCheckAlg(CheckAlgorithm)} before calling this method or call
	 * {@link #consistencyCheck(CheckAlgorithm)}.<br> Current STN graph will be modified.<br> If the check is
	 * successful, all constraints to node Z in g are minimized; otherwise, g contains a negative cycle at least.<br>
	 * After a check, {@link #getGChecked} returns the determined graph.
	 *
	 * @return the final status of the checking with some statistics.
	 */
	public STNCheckStatus consistencyCheck() {
		return consistencyCheck(defaultConsistencyCheckAlg);
	}

	/**
	 * Checks the consistency of this STN instance using algorithm {@code alg}. The check can last {@link #timeOut} s at
	 * most.<br> Current STN graph will be modified.<br> If the check is successful, all constraints to node Z in g are
	 * minimized; otherwise, g contains a negative cycle at least.
	 *
	 * @param alg a {@link CheckAlgorithm} object.
	 *
	 * @return a {@link STNCheckStatus} object.
	 *
	 * @see CheckAlgorithm
	 */
	public STNCheckStatus consistencyCheck(@Nonnull CheckAlgorithm alg) {
		try {
			initAndCheck();
		} catch (final IllegalArgumentException e) {
			throw new IllegalArgumentException(
				"The STN graph has a problem, and it cannot be initialized: " + e.getMessage());
		}
		final Instant startInstant = Instant.now();
		assert g.getZ() != null;
		switch (alg) {
			case AllPairsShortestPaths -> STN.APSP_FloydWarshall(g, checkStatus);
			case Johnson -> STN.APSP_Johnson(g, checkStatus);
			case Dijkstra -> STN.getDistanceDijkstra(g, g.getZ(), true, checkStatus);
			case BellmanFord -> STN.SSSP_BellmanFord(g, g.getZ(), checkStatus);
			case BellmanFordSingleSink -> STN.getDistanceBellmanFord(g, g.getZ(), true, horizon, true, checkStatus);
			case BFCT -> STN.SSSP_BFCT(g, g.getZ(), null, horizon, checkStatus);
			case Yen -> checkStatus.consistency = STN.yenAlgorithm(g, false, false, horizon, checkStatus);
			case YenSingleSink -> checkStatus.consistency = STN.yenAlgorithm(g, false, true, horizon, checkStatus);
			case BannisterEppstein -> checkStatus.consistency = STN.yenAlgorithm(g, true, false, horizon, checkStatus);
			default -> {
			}
		}
		final Instant endInstant = Instant.now();
		checkStatus.finished = true;
		checkStatus.executionTimeNS = Duration.between(startInstant, endInstant).toNanos();

		if (!checkStatus.consistency) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.log(Level.INFO, "Found an inconsistency.\nStatus: " + checkStatus);
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
				LOG.log(Level.INFO, "Stable state reached. Status: " + checkStatus);
			}
		}
		if (cleanCheckedInstance) {
			gCheckedCleaned = g;
		}
		saveGraphToFile();
		return checkStatus;
	}

	/**
	 * Getter for the field {@code checkStatus}.
	 *
	 * @return the checkStatus
	 */
	public STNCheckStatus getCheckStatus() {
		return checkStatus;
	}

	/**
	 * Getter for the field {@code defaultConsistencyCheckAlg}.
	 *
	 * @return the defaultConsistencyCheckAlg
	 */
	public CheckAlgorithm getDefaultConsistencyCheckAlg() {
		return defaultConsistencyCheckAlg;
	}

	/**
	 * @param defaultConsistencyCheckAlg1 the defaultConsistencyCheckAlg to set
	 */
	public void setDefaultConsistencyCheckAlg(CheckAlgorithm defaultConsistencyCheckAlg1) {
		defaultConsistencyCheckAlg = defaultConsistencyCheckAlg1;
	}

	/**
	 * @return the g
	 */
	public TNGraph<STNEdge> getG() {
		return g;
	}

	/**
	 * Considers the given graph as the graph to check (graph will be modified). Clear all {@link #minNegativeWeight},
	 * {@link #horizon} and {@link #checkStatus}.
	 *
	 * @param graph set internal TNGraph to g. It cannot be null.
	 */
	@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "For efficiency reason, it includes an external mutable object.")
	public void setG(TNGraph<STNEdge> graph) {
		if (graph == null) {
			throw new IllegalArgumentException("Input graph is null!");
		}
		reset();
		g = graph;
	}

	/**
	 * @return the resulting graph of a check. It is up to the caller to be sure the returned graph is the result of a
	 * 	check.
	 *
	 * @see #setOutputCleaned(boolean)
	 */
	public TNGraph<STNEdge> getGChecked() {
		if (cleanCheckedInstance && checkStatus.finished && checkStatus.consistency) {
			return gCheckedCleaned;
		}
		return g;
	}

	/**
	 * @return the min negative weight of the network
	 */
	public int getMinNegativeWeight() {
		return minNegativeWeight;
	}

	/**
	 * Determines all the Rigid Components (RC) using the linear time algorithm (w.r.t. the |edges|) proposed by
	 * Tsamardinos, I., Muscettola, N., &amp; Morris, P. (1998). Fast Transformation of Temporal Plans for Efficient
	 * Execution. In 15th National Conf. on Artificial Intelligence (AAAI-1998), pp. 254–261.<br> A rigid component is a
	 * strongly connected component in a predecessor graph.<br> Therefore, it is sufficient to determine the predecessor
	 * graphs of a graph (one for each node) and look for strongly connected components in each predecessor graphs.<br>
	 * If the network is not initialized ({@link #initAndCheck()}, it calls {@link #initAndCheck()} before running.
	 *
	 * @return the list of possible RCs, each of them as a list of original nodes. If there is no RCs, the list is
	 * 	empty. As side effects, all nodes in current STN has the distance from Z in potential field.
	 */
	@SuppressWarnings("StringConcatenationMissingWhitespace")
	public ObjectList<ObjectList<LabeledNode>> getRigidComponents() {
		if (!checkStatus.initialized) {
			initAndCheck();
		}
		final String prefix = "P" + System.currentTimeMillis();
		final LabeledNode fakeSource = new LabeledNode("_FAKESOURCE_%d".formatted(System.currentTimeMillis()));
		g.addVertex(fakeSource);
		MAKE_NODES_REACHABLE_BY(g, fakeSource, 0, prefix);

		final TNPredecessorGraph<STNEdge> stnPredecessorSubGraph =
			GET_STN_PRECEDESSOR_SUBGRAPH_OPTIMIZED(g, fakeSource, null, null, null);
		if (stnPredecessorSubGraph == null) {
			throw new IllegalStateException("The determination of the predecessor graph had a problem. Giving up!");
		}
		// such nodes are different object w.r.t. the nodes of this object.
		final ObjectList<LabeledNode> nodes = GET_REVERSE_POST_ORDER_VISIT(stnPredecessorSubGraph, fakeSource, null);
		stnPredecessorSubGraph.reverse();

		final Object2BooleanMap<LabeledNode> isVisited = new Object2BooleanLinkedOpenHashMap<>();
		isVisited.defaultReturnValue(false);

		final ObjectList<ObjectList<LabeledNode>> rc = new ObjectArrayList<>();

		assert nodes != null;
		for (final LabeledNode root : nodes) {
			final LabeledNode n1 = g.getNode(root.getName());
			assert n1 != null;
			n1.setPotential(root.getPotential());
			if (isVisited.getBoolean(root)) {
				continue;
			}
			final ObjectList<LabeledNode> revPOV =
				GET_REVERSE_POST_ORDER_VISIT(stnPredecessorSubGraph, root, isVisited);
			assert revPOV != null;
			if (revPOV.size() > 1) {
				final ObjectList<LabeledNode> localRC = new ObjectArrayList<>();
				revPOV.forEach(node -> localRC.add(g.getNode(node.getName())));
				rc.add(localRC);
			}
		}
		g.removeVertex(fakeSource);
		return rc;
	}

	/**
	 * @return version and copyright string
	 */
	public String getVersionAndCopyright() {
		// I use a non-static method for having a general method that prints the right
		// name for each derived class.
		String s = "\nSPDX-License-Identifier: LGPL-3.0-or-later, Roberto Posenato.\n";
		try {
			s = getClass().getName() + " " + getClass().getDeclaredField("VERSIONandDATE").get(this) + s;
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			//
		}
		return s;
	}

	/**
	 * @return the fOutput
	 */
	@SuppressWarnings("SpellCheckingInspection")
	public File getfOutput() {
		return fOutput;
	}

	/**
	 * @param fileOutput the file where to save the result.
	 */
	@SuppressWarnings("SpellCheckingInspection")
	public void setfOutput(File fileOutput) {
		fOutput = fileOutput;
	}

	/**
	 * Initializes the STN instance represented by graph g. It calls {@link #coreSTNInitAndCheck()}.
	 *
	 * @return true if the graph is a well-formed
	 */
	public boolean initAndCheck() {
		return coreSTNInitAndCheck();
	}

	/**
	 * @return the fOutputCleaned
	 */
	public boolean isOutputCleaned() {
		return cleanCheckedInstance;
	}

	/**
	 * Set to true for having the result graph cleaned of empty edges and labeled values having unknown literals.
	 *
	 * @param clean the resulting graph
	 */
	public void setOutputCleaned(boolean clean) {
		cleanCheckedInstance = clean;
	}

	/**
	 * Makes the graph dispatchable applying Muscettola et al. 1998 algorithm considering Z as source node. This
	 * algorithm adds the necessary edges to make any node reachable by Z. Such edges have name prefix "__".
	 *
	 * @return true if it was possible to make the graph dispatchable (i.e., the graph was consistent).
	 */
	public boolean makeDispatchable() {
		if (!checkStatus.initialized) {
			try {
				initAndCheck();
			} catch (final IllegalArgumentException e) {
				throw new IllegalArgumentException(
					"The STN graph has a problem, and it cannot be initialized: " + e.getMessage());
			}
		}
		final LabeledNode gZ = g.getZ();

		MAKE_NODES_REACHABLE_BY(g, gZ, horizon, "__");

		if (!STN.APSP_FloydWarshall(g, checkStatus)) {
			return false;
		}

		final Object2BooleanMap<STNEdge> isDominated = new Object2BooleanLinkedOpenHashMap<>();
		isDominated.defaultReturnValue(false);

		for (final LabeledNode node3 : g.getVertices()) {
			// upper dominant
			final ObjectArrayList<STNEdge> incomingEdge = (ObjectArrayList<STNEdge>) g.getInEdges(node3);
			for (int i = 0; i < incomingEdge.size() - 1; i++) {
				final STNEdge edge13 = incomingEdge.get(i);
				final LabeledNode node1 = g.getSource(edge13);
				final int v13 = edge13.getValue();
				if (v13 < 0) {
					continue;
				}
				for (int j = i + 1; j < incomingEdge.size(); j++) {
					final STNEdge edge23 = incomingEdge.get(j);
					final LabeledNode node2 = g.getSource(edge23);
					final int v23 = edge23.getValue();
					if (v23 < 0) {
						continue;
					}
					final STNEdge edge12 = g.findEdge(node1, node2);
					boolean edge13NotDominated = true;
					if (edge12 != null) {
						final int v12 = edge12.getValue();
						if (!isDominated.getBoolean(edge13) && v13 == v12 + v23) {
							// edge23 dominates
							isDominated.put(edge13, true);
							edge13NotDominated = false;
							if (Debug.ON) {
								if (LOG.isLoggable(Level.FINEST)) {
									LOG.log(Level.FINEST,
									        "Edge " + edge13 + " dominated by " + edge23 + " and " + edge12);
								}
							}
						}
					}
					if (edge13NotDominated) {
						final STNEdge edge21 = g.findEdge(node2, node1);
						if (edge21 == null) {
							continue;
						}
						final int v21 = edge21.getValue();
						if (!isDominated.getBoolean(edge23) && v23 == v21 + v13) {
							// edge13 dominates
							isDominated.put(edge23, true);
							if (Debug.ON) {
								if (LOG.isLoggable(Level.FINEST)) {
									LOG.log(Level.FINEST,
									        "Edge " + edge23 + " dominated by " + edge13 + " and " + edge21);
								}
							}

						}
					}
				}
			}

			// lower dominant
			final ObjectArrayList<STNEdge> outgoingEdge = (ObjectArrayList<STNEdge>) g.getOutEdges(node3);
			for (int i = 0; i < outgoingEdge.size() - 1; i++) {
				final STNEdge edge31 = outgoingEdge.get(i);
				final LabeledNode node1 = g.getDest(edge31);
				final int v31 = edge31.getValue();
				if (v31 >= 0) {
					continue;
				}
				for (int j = i + 1; j < outgoingEdge.size(); j++) {
					final STNEdge edge32 = outgoingEdge.get(j);
					final LabeledNode node2 = g.getDest(edge32);
					final int v32 = edge32.getValue();
					if (v32 >= 0) {
						continue;
					}
					final STNEdge edge12 = g.findEdge(node1, node2);
					boolean edge32NotDominated = true;
					if (edge12 != null) {
						final int v12 = edge12.getValue();
						if (!isDominated.getBoolean(edge32) && v32 == v31 + v12) {
							// edge31 dominates
							isDominated.put(edge32, true);
							edge32NotDominated = false;
							if (Debug.ON) {
								if (LOG.isLoggable(Level.FINEST)) {
									LOG.log(Level.FINEST,
									        "Edge " + edge32 + " dominated by " + edge31 + " and " + edge12);
								}
							}

						}
					}
					if (edge32NotDominated) {
						final STNEdge edge21 = g.findEdge(node2, node1);
						if (edge21 == null) {
							continue;
						}
						final int v21 = edge21.getValue();
						if (!isDominated.getBoolean(edge31) && v31 == v32 + v21) {
							// edge13 dominates
							isDominated.put(edge31, true);
							if (Debug.ON) {
								if (LOG.isLoggable(Level.FINEST)) {
									LOG.log(Level.FINEST,
									        "Edge " + edge31 + " dominated by " + edge32 + " and " + edge21);
								}
							}

						}
					}
				}
			}
		}
		// remove all dominated edges
		g.getEdges().stream().filter(isDominated::getBoolean).forEach((edge) -> g.removeEdge(edge));
		return true;
	}

	/**
	 * Makes this network dispatchable where edges are only undominated ones.<br> This method is equivalent to
	 * {@link #makeDispatchable()} but it uses a different algorithm.<br> If the network is not initialized
	 * ({@link #initAndCheck()}, it calls {@link #initAndCheck()} before running.
	 *
	 * @return true if the method runs successful, false otherwise.
	 */
	public boolean makeMinimalDispatchable() {
		if (!checkStatus.initialized) {
			try {
				initAndCheck();
			} catch (final IllegalArgumentException e) {
				throw new IllegalArgumentException(
					"The STN graph has a problem, and it cannot be initialized: " + e.getMessage());
			}
		}
		return MAKE_MINIMAL_DISPATCHABLE(g) != null;
	}

	/**
	 * Stores the graph after a check into the file {@link #fOutput}.
	 *
	 * @see #getGChecked()
	 */
	public void saveGraphToFile() {
		if (fOutput == null) {
			if (fInput == null) {
				LOG.info(
					"Input file and output file are null. It is not possible to save the result in automatic way.");
				return;
			}
			String outputName;
			try {
				outputName = FILE_NAME_SUFFIX_PATTERN.matcher(fInput.getCanonicalPath()).replaceFirst("");
			} catch (IOException e) {
				System.err.println(
					"It is not possible to save the result. Field fOutput is null and no the standard output file can be created: " +
					e.getMessage());
				return;
			}
			if (!checkStatus.finished) {
				outputName += "_notFinishedCheck";
				if (checkStatus.timeout) {
					outputName += "_timeout_" + timeOut;
				}
			} else {
				outputName += "_checked_" + ((checkStatus.consistency) ? "DC" : "NOTDC");
			}
			outputName += FILE_NAME_SUFFIX;
			fOutput = new File(outputName);
			LOG.info("Output file name is " + fOutput.getAbsolutePath());
		}

		final TNGraph<STNEdge> g1 = getGChecked();
		g1.setInputFile(fOutput);
		g1.setName(fOutput.getName());
		g1.removeEmptyEdges();

		final CSTNUStaticLayout<STNEdge> layout = new CSTNUStaticLayout<>(g1);
		final TNGraphMLWriter graphWriter = new TNGraphMLWriter(layout);

		try {
			graphWriter.save(g1, fOutput);
		} catch (IOException e) {
			System.err.println(
				"It is not possible to save the result. File " + fOutput + " cannot be created: " + e.getMessage());
			return;
		}

		LOG.info("Checked instance saved in file " + fOutput.getAbsolutePath());
	}

	/**
	 * Makes the STN check and initialization. The STN instance is represented by graph g.
	 * <br>
	 * <ol>
	 * <li>If g does not contains the source node Z, Z is added and all constraints (X,0,Z) for each X in the graph.<br>
	 * <li>Loops and empty edges are removed.<br>
	 * <li>Edges with +∞, are removed.<br>
	 * <li>If an edge to Z has value >0, it is reset to 0.
	 * </ol>
	 * If some constraints of the network does not observe well-definition
	 * Every time the method adjusts a constraint, logs such a fix
	 * at WARNING level. <br>
	 * <b>Note</b> This method is necessary for allowing the building of special
	 * subclass initAndCheck (in subclasses of subclasses).
	 *
	 * @return true if the graph is a well-formed
	 */
	@SuppressWarnings("SameReturnValue")
	boolean coreSTNInitAndCheck() {
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "Starting initial well definition check.");
			}
		}
		g.clearCache();
		gCheckedCleaned = null;

		// Checks the presence of Z node!
		if (g.getZ() == null) {
			LabeledNode Z = g.getNode(STN.ZERO_NODE_NAME);
			if (Z == null) {
				// We add by authority!
				Z = LabeledNodeSupplier.get(STN.ZERO_NODE_NAME);
				Z.setX(10);
				Z.setY(10);
				g.addVertex(Z);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.WARNING)) {
						LOG.log(Level.WARNING, "No " + STN.ZERO_NODE_NAME + " node found: added!");
					}
				}
			}
			g.setZ(Z);
		}

		// Checks well definiteness of edges and determine maxWeight
		int minNegWeight = 0;
		int maxWeight = 0;
		for (final STNEdge e : g.getEdges()) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.log(Level.FINEST, "Initial Checking edge e: " + e);
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
				g.removeEdge(e);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.WARNING)) {
						LOG.log(Level.WARNING, "Empty edge " + e + " has been removed.");
					}
				}
				continue;
			}
			final int ev = e.getValue();
			if (ev == Constants.INT_NULL || ev == Constants.INT_POS_INFINITE) {
				g.removeEdge(e);
				continue;
			}

			if (ev < minNegWeight) {
				minNegWeight = ev;
			}
			if (ev > maxWeight) {
				maxWeight = ev;
			}

		}

		// manage maxWeight value
		minNegativeWeight = minNegWeight;
		// Determine horizon value
		if (-minNegWeight > maxWeight) {
			maxWeight = -minNegWeight;
		}

		long product = ((long) maxWeight) * (g.getVertexCount() - 1);// Z doesn't count!
		if (product > Constants.INT_POS_INFINITE) {
			product = Constants.INT_POS_INFINITE;
		}
		horizon = (int) product;
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "The horizon value is " + String.format("%6d", product));
			}
		}

		/*
		 * Checks well definiteness of nodes
		 */
		final Collection<LabeledNode> nodeSet = g.getVertices();
		for (final LabeledNode node : nodeSet) {
			node.setPotential(Constants.INT_NULL);
			// 3. Checks that each node different from Z has an edge to Z
			if (node == g.getZ()) {
				continue;
			}
			boolean added = false;
			STNEdge edge = g.findEdge(node, g.getZ());
			if (edge == null) {
				edge = g.makeNewEdge(node.getName() + "-" + g.getZ().getName(), ConstraintType.internal);
				g.addEdge(edge, node, g.getZ());
				edge.setValue(0);
				added = true;
			} else {
				if (edge.getValue() > 0) {
					edge.setValue(0);
					added = true;
				}
			}
			if (Debug.ON) {
				if (added) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.log(Level.FINE, "Added " + edge + ": " + node + "--(0)-->" + g.getZ());
					}
				}
			}
		}

		checkStatus.reset();
		checkStatus.initialized = true;
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "Initial well definition check done!");
			}
		}
		return true;
	}

	/**
	 * Simple method to manage command line parameters using `args4j` library.
	 *
	 * @param args none
	 *
	 * @return false if a parameter is missing, or it is wrong. True if every parameter is given in a right format.
	 */
	@SuppressWarnings("deprecation")
	boolean manageParameters(final String[] args) {
		final CmdLineParser parser = new CmdLineParser(this);
		try {
			// parse the arguments.
			parser.parseArgument(args);

			if (fInput == null) {
				try (final Scanner consoleScanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
					System.out.print("Insert STN file name (absolute file name): ");
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
				if (!fOutput.getName().endsWith(".cstn")) {
					if (!fOutput.renameTo(new File(fOutput.getAbsolutePath() + ".cstn"))) {
						final String m = "File " + fOutput.getAbsolutePath() + " cannot be renamed.";
						LOG.severe(m);
						throw new IllegalStateException(m);
					}
				}
				if (fOutput.exists()) {
					if (!fOutput.delete()) {
						final String m = "File " + fOutput.getAbsolutePath() + " cannot be deleted.";
						LOG.severe(m);
						throw new IllegalStateException(m);
					}
				}
			}
		} catch (final CmdLineException e) {
			// if there's a problem in the command line, you'll get this exception. this
			// will report an error message.
			System.err.println(e.getMessage());
			System.err.println("java " + getClass().getName() + " [options...] arguments...");
			// print the list of available options
			parser.printUsage(System.err);
			System.err.println();

			// print option sample. This is useful some time
			System.err.println("Example: java -jar CSTNU-*.jar " + getClass().getName() + " " +
			                   parser.printExample(OptionHandlerFilter.REQUIRED) + " file_name");
			return false;
		}
		return true;
	}

	/**
	 * Resets all internal structures
	 */
	void reset() {
		g = null;
		minNegativeWeight = 0;
		horizon = 0;
		checkStatus.reset();
	}
}
