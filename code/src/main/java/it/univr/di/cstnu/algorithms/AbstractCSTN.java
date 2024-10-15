// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.algorithms;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.objects.*;
import it.univr.di.Debug;
import it.univr.di.cstnu.algorithms.STN.STNCheckStatus;
import it.univr.di.cstnu.graph.*;
import it.univr.di.cstnu.graph.Edge.ConstraintType;
import it.univr.di.cstnu.util.ObjectArrayFIFOSetQueue;
import it.univr.di.cstnu.visualization.CSTNUStaticLayout;
import it.univr.di.labeledvalue.AbstractLabeledIntMap;
import it.univr.di.labeledvalue.Constants;
import it.univr.di.labeledvalue.Label;
import org.kohsuke.args4j.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Core class to represent and DC check Conditional Simple Temporal Network (CSTN) where the edge weight are signed
 * integer. The dynamic consistency check (DC check) is done assuming standard DC semantics (cf. ICAPS 2016 paper, table
 * 1) and using LP, R0, qR0, R3*, and qR3* rules.
 * <p>
 * This class is the base class for some other specialized in which DC semantics is defined in a different way.
 *
 * @param <E> kind of edges
 *
 * @author Roberto Posenato
 * @version $Rev: 840 $
 * @see CSTN
 * @see CSTNPotential
 */
public abstract class AbstractCSTN<E extends CSTNEdge> {

	/**
	 * The name for the initial node.
	 */
	public final static String ZERO_NODE_NAME = "Z";
	/**
	 * Version of the class
	 */
	// static final String VERSIONandDATE = "Version 1.0 - June, 12 2019";// Refactoring CSTN class
	// static final String VERSIONandDATE = "Version 1.1 - September, 1 2021";// Put two INFO log under Debug.ON condition
	static final String VERSIONandDATE = "Version 1.2 - December, 17 2021";
	/**
	 * Suffix for file name
	 */
	static String FILE_NAME_SUFFIX = ".cstn";
	/**
	 * logger
	 */
	static private final Logger LOG = Logger.getLogger(AbstractCSTN.class.getName());
	/**
	 * Check status
	 */
	CSTNCheckStatus checkStatus = new CSTNCheckStatus();
	/**
	 * If true, after a check, the resulting graph is cleaned: all empty edges or labeled values containing unknown
	 * literals are removed.
	 */
	@Option(name = "-cleaned",
		usage = "Output a cleaned result. A result cleaned network does not contain empty edges or labeled values containing unknown literals.")
	boolean cleanCheckedInstance = true;
	/**
	 * The input file containing the CSTN graph in GraphML format.
	 */
	@Argument(required = true, usage = "file_name must be the input network in GraphML format.", metaVar = "file_name")
	File fInput;
	// Improved coreInit: now all negative edges going to obs time-point are checked.

	/**
	 * Represents the status of a CSTN-checking algorithm during an execution.
	 * <p>
	 * At the end of a CSTN-checking algorithm running, it contains the final status ({@link STNCheckStatus#consistency}
	 * or the node {@link STNCheckStatus#negativeLoopNode} where a negative loop has been found).
	 *
	 * @author Roberto Posenato
	 */
	public static class CSTNCheckStatus extends STNCheckStatus {

		/**
		 * Counters #applications of label propagation rule
		 */
		public int labeledValuePropagationCalls;
		/**
		 * Counters #applications of potential update
		 */
		public int potentialUpdate;
		/**
		 * Counters #applications of r0 rule
		 */
		public int r0calls;
		/**
		 * Counters #applications of r3 rule
		 */
		public int r3calls;

		/**
		 * Reset all indexes.
		 */
		@Override
		public void reset() {
			super.reset();
			r0calls = 0;
			r3calls = 0;
			labeledValuePropagationCalls = 0;
			potentialUpdate = 0;
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder(80);
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
			if (!consistency && negativeLoopNode != null) {
				sb.append("The negative loop is on node ").append(negativeLoopNode).append("\n");
			}
			sb.append("Some statistics:\nRule R0 has been applied ").append(r0calls).append(" times.\n");
			sb.append("Rule R3 has been applied ").append(r3calls).append(" times.\n");
			sb.append("Rule Labeled Propagation has been applied ").append(labeledValuePropagationCalls)
				.append(" times.\n");
			sb.append("Potentials updated ").append(potentialUpdate).append(" times.\n");
			if (timeout) {
				sb.append("Checking has been interrupted because execution time exceeds the given time limit.\n");
			}

			if (executionTimeNS != Constants.INT_NULL) {
				sb.append("The global execution time has been ").append(executionTimeNS).append(" ns (~")
					.append((executionTimeNS / 1E9)).append(" s.)");
			}
			return sb.toString();
		}
	}

	/**
	 * Simple class to maintain the set of edges to check in the following phase.
	 *
	 * @param <E> Type of edge
	 *
	 * @author posenato
	 */
	public static class EdgesToCheck<E extends Edge> implements Iterable<E> {
		/**
		 *
		 */
		public boolean alreadyAddAllIncidentsToZ;
		/**
		 * It must be a set because an edge could be added more times!
		 */
		public ObjectRBTreeSet<E> edgesToCheck;

		/**
		 *
		 */
		public EdgesToCheck() {
			edgesToCheck = new ObjectRBTreeSet<>();
			alreadyAddAllIncidentsToZ = false;
		}

		/**
		 * A simple constructor when the initial set of edges is available.
		 *
		 * @param coll collection to copy
		 */
		public EdgesToCheck(Collection<? extends E> coll) {
			edgesToCheck = new ObjectRBTreeSet<>(coll);
			alreadyAddAllIncidentsToZ = false;
		}

		/**
		 * Clear the set.
		 */
		public void clear() {
			edgesToCheck.clear();
			alreadyAddAllIncidentsToZ = false;
		}


		/**
		 * @return an iterator
		 */
		@SuppressFBWarnings(value = "NP_NONNULL_RETURN_VIOLATION", justification = "It doesn't check that edgesToCheck.iterator() is not null")
		@Nonnull
		@Override
		public Iterator<E> iterator() {
			return edgesToCheck.iterator();
		}

		/**
		 * @return the number of edges in the set.
		 */
		public int size() {
			return (edgesToCheck != null) ? edgesToCheck.size() : 0;
		}

		/**
		 * Check if the edge that has to be added has one end-point that is an observer. In positive case, it adds all
		 * in edges to the destination node for guaranteeing that R3* can be applied again with new values.
		 *
		 * @param enSnD                  edge to add
		 * @param nS                     source node
		 * @param nD                     destination node
		 * @param Z                      zero node
		 * @param g                      graph where to add
		 * @param applyReducedSetOfRules true if only the reduced set of rule must be considered
		 */
		void add(E enSnD, LabeledNode nS, LabeledNode nD, LabeledNode Z, TNGraph<E> g,
		         boolean applyReducedSetOfRules) {
			// in any case, the edge has to be added.
			edgesToCheck.add(enSnD);
			// then,
			if (!nS.isObserver()) {
				return;
			}
			// add all incident to nD
			if (nD != Z) {
				if (!applyReducedSetOfRules) {
					edgesToCheck.addAll(g.getInEdges(nD));
				}
				return;
			}

			if (alreadyAddAllIncidentsToZ) {
				return;
			}
			edgesToCheck.addAll(g.getInEdges(Z));
			alreadyAddAllIncidentsToZ = true;
		}

		/**
		 * Add a set of edges without any check.
		 *
		 * @param eSet set of edges
		 *
		 * @return true if this set changed after the add.
		 */
		@SuppressWarnings("UnusedReturnValue")
		boolean addAll(Collection<E> eSet) {
			return edgesToCheck.addAll(eSet);
		}

		/**
		 * Copy fields reference of into this. After this method, this and input share the internal fields.
		 *
		 * @param input the object to cannibalize
		 */
		void takeIn(EdgesToCheck<E> input) {
			if (input == null) {
				return;
			}
			edgesToCheck = input.edgesToCheck;
			alreadyAddAllIncidentsToZ = input.alreadyAddAllIncidentsToZ;
		}

		/*
		 * Add an edge without any check.
		 *
		 * @param enSnD an edge
		 * @return true if this set did not already contain the specified element
		final boolean add(E enSnD) {
		return this.edgesToCheck.add(enSnD);
		}
		 */
	}

	/**
	 * Acts as a queue and a set. An element is enqueued only if it is not already present.
	 *
	 * @author posenato
	 */
	public static class NodesToCheck implements ObjectSet<LabeledNode>, PriorityQueue<LabeledNode> {
		/**
		 * It must be a queue without replication set because a node may be added more times!
		 */
		public ObjectArrayFIFOSetQueue<LabeledNode> nodes2check;

		/**
		 * A simple constructor when the initial set of nodes is available.
		 *
		 * @param coll collection to scan
		 */
		public NodesToCheck(Collection<LabeledNode> coll) {
			this();
			for (final LabeledNode node : coll) {
				nodes2check.enqueue(node);
			}
		}

		/**
		 *
		 */
		public NodesToCheck() {
			nodes2check = new ObjectArrayFIFOSetQueue<>();
		}

		@Override
		public boolean add(LabeledNode e) {
			return nodes2check.add(e);
		}

		@Override
		public boolean addAll(@Nonnull Collection<? extends LabeledNode> coll) {
			nodes2check.addAll(coll);
			return true;
		}

		/**
		 * Clear the set.
		 */
		@Override
		public void clear() {
			nodes2check.clear();
		}

		@Override
		public Comparator<? super LabeledNode> comparator() {
			throw new UnsupportedOperationException("comparator");
		}

		/**
		 * @param o node
		 *
		 * @return true if o is present.
		 */
		@Override
		public boolean contains(Object o) {
			return nodes2check.contains(o);
		}

		@Override
		public boolean containsAll(@Nonnull Collection<?> c) {
			throw new UnsupportedOperationException("containsAll");
		}

		/**
		 * @return the first LabeledNode in the queue
		 */
		@Override
		public LabeledNode dequeue() {
			return nodes2check.dequeue();
		}

		/**
		 * @param node Node to enqueue
		 */
		@Override
		public void enqueue(LabeledNode node) {
			nodes2check.enqueue(node);
		}

		@Override
		public LabeledNode first() {
			throw new UnsupportedOperationException("first");
		}

		/**
		 * @return true if there is no element
		 */
		@Override
		public boolean isEmpty() {
			return nodes2check.isEmpty();
		}

		@SuppressWarnings("NullableProblems")//do not put @Nonnull because it goes in conflict with spotbugs
		@Override
		public ObjectIterator<LabeledNode> iterator() {
			return nodes2check.iterator();
		}

		@Override
		public boolean remove(Object o) {
			return nodes2check.remove(o);
		}

		@Override
		public boolean removeAll(@Nonnull Collection<?> c) {
			throw new UnsupportedOperationException("removeAll");
		}

		@Override
		public boolean retainAll(@Nonnull Collection<?> c) {
			throw new UnsupportedOperationException("retainAll");
		}

		/**
		 * @return the number of edges in the set.
		 */
		@Override
		public int size() {
			return nodes2check.size();
		}

		@Nonnull
		@Override
		public <T> T[] toArray(@Nonnull T[] a) {
			throw new UnsupportedOperationException("toArray");
		}

		/**
		 * @return the queue as an array. If there is no element, the array is empty.
		 */
		@Nonnull
		@Override
		public LabeledNode[] toArray() {
			return (nodes2check != null) ? nodes2check.toArray(new LabeledNode[0]) : new LabeledNode[0];
		}

		@Override
		public String toString() {
			return nodes2check.toString();
		}

		/*
		 * Copy fields reference of into this. After this method, this and input share the internal fields.
		 *
		 * @param input source to copy
		void takeFrom(NodesToCheck input) {
		if (input == null) {
		return;
		}
		this.nodes2check = input.nodes2check;
		}
		 */
	}

	/**
	 * Horizon value. A node that has to be executed after such time means that it has not to be executed!
	 */
	int horizon = Constants.INT_NULL;
	/**
	 * Absolute value of the max negative weight determined during initialization phase.
	 */
	int maxWeight = Constants.INT_NULL;
	/**
	 * Check using full set of rules R0, qR0, R3, qR3, LP, qLP or the reduced set qR0, qR3, LP.
	 */
	boolean propagationOnlyToZ = true;
	/**
	 * WD2.2 epsilon value called also reaction time in ICAPS 18. It is &gt; 0 in standard CSTN, &ge; 0 in IR, &gt;
	 * epsilon in Epsilon CSTN. Even when it is 0, the dynamic consistency def. excludes that a t.p. X having p in its
	 * label can be executed at the same time of t.p. P?. This is because, at time t, the history is the same and,
	 * therefore, X should be executed at t in very scenario, even in the one where it cannot stay!
	 * <b>Such value and WD2.2 property is not necessary as required in the past because Dynamic Execution definition
	 * already contains it.</b> On the other hand, propagation rules needs such value to be complete. Therefore, WD2.2
	 * is not more required as CSTN property, but it is imposed as propagation rule.
	 */
	@Option(name = "-r", aliases = "--reactionTime", usage = "Reaction time. It must be >= 0.")
	int reactionTime = 1;
	/**
	 * Timeout in seconds for the check.
	 */
	@Option(name = "-t", aliases = "--timeOut", usage = "Timeout in seconds for the check", metaVar = "seconds")
	int timeOut = 2700;

	/**
	 * Determines the minimal distance between all pair of vertexes modifying the given consistent graph. If the graph
	 * contains a negative cycle, it returns false and the graph contains the edge that has determined the found
	 * negative loop.
	 *
	 * @param <E> type of edge
	 * @param g   the graph
	 *
	 * @return true if the graph is consistent, false otherwise. If the response is false, the edges do not represent
	 * 	the minimal distance between nodes.
	 */
	static public <E extends CSTNEdge> boolean getMinimalDistanceGraph(final TNGraph<E> g) {
		final int n = g.getVertexCount();
		final EdgeSupplier<E> edgeFactory = g.getEdgeFactory();
		final LabeledNode[] node = g.getVerticesArray();
		LabeledNode iV, jV, kV;
		E ik, kj, ij;
		int v;
		Label ijL;

		boolean consistent = true;
		for (int k = 0; k < n; k++) {
			kV = node[k];
			for (int i = 0; i < n; i++) {
				iV = node[i];
				for (int j = 0; j < n; j++) {
					if ((k == i) || (k == j)) {
						continue;
					}
					jV = node[j];
					final Label nodeLabelConjunction = iV.getLabel().conjunction(jV.getLabel());
					if (nodeLabelConjunction == null) {
						continue;
					}

					ik = g.findEdge(iV, kV);
					kj = g.findEdge(kV, jV);
					if ((ik == null) || (kj == null)) {
						continue;
					}
					ij = g.findEdge(iV, jV);

					final ObjectSet<Object2IntMap.Entry<Label>> ikMap = ik.getLabeledValueSet();
					final ObjectSet<Object2IntMap.Entry<Label>> kjMap = kj.getLabeledValueSet();

					for (final Object2IntMap.Entry<Label> ikL : ikMap) {
						for (final Object2IntMap.Entry<Label> kjL : kjMap) {
							ijL = ikL.getKey().conjunction(kjL.getKey());
							if (ijL == null) {
								continue;
							}
							ijL = ijL.conjunction(
								nodeLabelConjunction);// It is necessary to propagate with node labels!
							if (ijL == null) {
								continue;
							}
							if (ij == null) {
								ij = edgeFactory.get("e" + node[i].getName() + node[j].getName());
								ij.setConstraintType(Edge.ConstraintType.derived);
								g.addEdge(ij, iV, jV);
							}
							v = ikL.getIntValue() + kjL.getIntValue();
							ij.mergeLabeledValue(ijL, v);
							if (i == j) // check negative cycles
							{
								if (v < 0 || ij.getMinValue() < 0) {
									if (LOG.isLoggable(Level.FINER)) {
										LOG.finer("Found a negative cycle on node " + iV.getName() + ": " + (ij)
										          + "\nIn details, ik=" + ik + ", kj=" + kj + ",  v=" + v +
										          ", ij.getValue("
										          + ijL + ")=" + ij.getValue(ijL));
									}
									consistent = false;
								}
							}
						}
					}
				}
			}
		}
		return consistent;
	}

	/**
	 * If false, node labels are ignored during the check.
	 */
	boolean withNodeLabels = true;

	/**
	 * Initialize the CSTN using graph.<br> For saving the resulting graph in a file during/after a check, field
	 * {@link #fOutput} must be set. Setting {@link #fInput} instead of {@link #fOutput}, the name of output file is
	 * build using {@link #fInput}.
	 *
	 * @param graph       TNGraph to check
	 * @param giveTimeOut timeout for the check
	 */
	public AbstractCSTN(TNGraph<E> graph, int giveTimeOut) {
		this(graph);
		timeOut = giveTimeOut;
	}

	/**
	 * Initialize the CSTN using graph.<br> For saving the resulting graph in a file during/after a check, field
	 * {@link #fOutput} must be set. Setting {@link #fInput} instead of {@link #fOutput}, the name of output file is
	 * build using {@link #fInput}.
	 *
	 * @param graph TNGraph to check
	 */
	public AbstractCSTN(TNGraph<E> graph) {
		this();
		setG(graph);// sets also checkStatus!
	}

	/**
	 * Default constructor.
	 */
	AbstractCSTN() {
	}

	/**
	 * Output file where to write the XML representing the CSTN graph after a check.
	 */
	@Option(name = "-o", aliases = "--output",
		usage = "Output to this file. If file is already present, it is overwritten.", metaVar = "output_file_name")
	File fOutput;


	/**
	 * @param label input
	 * @param value input
	 *
	 * @return the conventional representation of a labeled value
	 */
	static String pairAsString(Label label, int value) {
		return AbstractLabeledIntMap.entryAsString(label, value);
	}

	/**
	 * Resets all internal structures but not g.
	 */
	public void reset() {
		maxWeight = 0;
		horizon = 0;
		checkStatus.reset();
	}

	/**
	 * Input TNGraph.
	 */
	TNGraph<E> g;

	/**
	 * Considers the given graph as the graph to check (graph will be modified). Clear all auxiliary variables.
	 *
	 * @param graph set internal TNGraph to g. It cannot be null.
	 */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "EI_EXPOSE_REP2",
		justification = "For efficiency reason, it includes an external mutable object.")
	public void setG(TNGraph<E> graph) {
		// CSTNU overrides this.
		if (graph == null) {
			throw new IllegalArgumentException("Input graph is null!");
		}
		reset();
		g = graph;
	}

	/**
	 * The graph obtained by a check and the cleaning action.
	 */
	TNGraph<E> gCheckedCleaned;

	/**
	 * Checks the dynamic consistency (DC) of a CSTN instance within timeout seconds. During the execution of this
	 * method, the given network is modified. <br> If the check is successful, all constraints to node Z in the network
	 * are minimized; otherwise, the network contains a negative loop at least.
	 * <br>
	 * After a check, {@link #getGChecked()} returns the network determined by the check and {@link #getCheckStatus()}
	 * the result of the checking action with some statistics and the node having the negative loop if the network is
	 * NOT DC.<br> In any case, before returning, this method call {@link #saveGraphToFile()} for saving the computed
	 * graph.
	 *
	 * @return the final status of the checking with some statistics.
	 *
	 * @throws it.univr.di.cstnu.algorithms.WellDefinitionException if any.
	 */
	abstract public CSTNCheckStatus dynamicConsistencyCheck() throws WellDefinitionException;

	/**
	 * Getter for the resulting graph of a check.<br> In order to obtain a resulting graph without redundant labels or
	 * labels having unknown literals, set output cleaned flag by {@link #setOutputCleaned(boolean)} before calling this
	 * method.
	 *
	 * @return the resulting graph of a check. It is up to the called to be sure the returned graph is the result of a
	 * 	check. It can be used also by subclasses with a proper cast.
	 *
	 * @see #setOutputCleaned(boolean)
	 */
	public TNGraph<E> getGChecked() {
		if (cleanCheckedInstance && getCheckStatus().finished && getCheckStatus().consistency) {
			if (gCheckedCleaned == null) {
				gCheckedCleaned = new TNGraph<>(g.getName(), g.getEdgeImplClass());
				gCheckedCleaned.copyCleaningRedundantLabels(g);
			}
			return gCheckedCleaned;
		}
		return g;
	}

	/**
	 * Getter for the field {@code checkStatus}, the status of a checking algorithm.
	 *
	 * @return the status of a checking algorithm. At the end of the running, this contains the final status and some
	 * 	statistics.
	 */
	public CSTNCheckStatus getCheckStatus() {
		// CSTNU override this
		return checkStatus;
	}

	/**
	 * Software Version.
	 */
	@Option(name = "-v", aliases = "--version", usage = "Version")
	boolean versionReq;

	/**
	 * Getter for the field {@code g}, the input graph.
	 *
	 * @return the input graph
	 */
	public TNGraph<E> getG() {
		return g;
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
	 * Getter for the field {@code fOutput}.
	 *
	 * @return the fOutput
	 */
	@SuppressWarnings("SpellCheckingInspection")
	public File getfOutput() {
		return fOutput;
	}

	/**
	 * Helper method for having the graph obtained by {@link #getGChecked()} in GraphML format.
	 *
	 * @return the resulting graph of a check in GraphML format. It is up to the called to be sure the returned graph is
	 * 	the result of a check. It can be used also by subclasses with a proper cast.
	 *
	 * @see #getGChecked()
	 */
	public String getGCheckedAsGraphML() {
		final TNGraph<E> g1 = getGChecked();
		final TNGraphMLWriter graphWriter = new TNGraphMLWriter(new CSTNUStaticLayout<>(g1));
		return graphWriter.save(g1);
	}

	/**
	 * Checks and initializes the CSTN instance represented by graph {@code g}. The check is made by
	 * {@link #coreCSTNInitAndCheck()}.<br> Since many DC checking algorithms are complete if and only if the CSTN
	 * instance contains an upper bound to the distance from Z (the first node) for each node, this method calls also
	 * {@link #addUpperBounds()} for adding such bounds as constraints between Z and each node.
	 *
	 * @throws WellDefinitionException if the initial graph is not well-defined. We preferred to throw an exception
	 *                                 instead of returning a negative status to stress that any further operation
	 *                                 cannot be made on this instance.
	 * @see #coreCSTNInitAndCheck()
	 * @see #addUpperBounds()
	 */
	public void initAndCheck() throws WellDefinitionException {
		coreCSTNInitAndCheck();
		addUpperBounds();
	}

	/**
	 * <p>
	 * isOutputCleaned.
	 * </p>
	 *
	 * @return the fOutputCleaned
	 */
	public boolean isOutputCleaned() {
		return cleanCheckedInstance;
	}

	/**
	 * Set to true for having the result graph cleaned of empty edges and of labeled values having unknown literals.
	 *
	 * @param clean the resulting graph
	 */
	public void setOutputCleaned(boolean clean) {
		cleanCheckedInstance = clean;
	}

	/**
	 * Getter for the field {@code maxWeight}.
	 *
	 * @return the maxWeight
	 */
	public int getMaxWeight() {
		return maxWeight;
	}

	/**
	 * <p>
	 * Setter for the field {@code withNodeLabels}.
	 * </p>
	 *
	 * @param withNodeLabels1 true if node labels have to be considered.
	 */
	public void setWithNodeLabels(boolean withNodeLabels1) {
		withNodeLabels = withNodeLabels1;
		setG(g);// reset all
	}

	/**
	 * Getter for the field {@code reactionTime}.
	 *
	 * @return the reactionTime
	 */
	public int getReactionTime() {
		return reactionTime;
	}

	/**
	 * Setter for the field {@code propagationOnlyToZ}.
	 *
	 * @param propagationOnlyToZ1 true if propagations have to be done only to Z
	 */
	public void setPropagationOnlyToZ(boolean propagationOnlyToZ1) {
		propagationOnlyToZ = propagationOnlyToZ1;
		setG(g);// reset all
	}

	/**
	 * <p>
	 * isWithNodeLabels.
	 * </p>
	 *
	 * @return the withNodeLabels
	 */
	public boolean isWithNodeLabels() {
		return withNodeLabels;
	}

	/**
	 * Helper method for making easier the storing of the resulting graph during a check. <br> If field {@link #fOutput}
	 * is not null (or if {@link #fInput} is not null), for any possible result of a {@link #dynamicConsistencyCheck()},
	 * the resulting graph is stored according to the following rules:
	 * <ul>
	 * <li>"_notFinishedCheck" if the check was interrupted
	 * <li>"_timeout_" if a timeout has occurred
	 * <li>"_checked_DC" or "_checked_NOTDC" if the the check has finished correctly. DC/NOTDC stands for
	 * DynamicConsistent or DynamicControllable and NOTDC for Not DynamicConsistent or Not DynamicControllable.
	 * </ul>
	 * If {@link #fOutput} is null, it tries to build a name from the {@link #fInput}. If also {@link #fInput} is null, ti does nothing.
	 *
	 * @see #getGChecked()
	 */
	public void saveGraphToFile() {
		if (fOutput == null) {
			if (fInput == null) {
				if (Debug.ON) {
					LOG.info(
						"Input file and output file are null. It is not possible to save the result in automatic way.");
				}
				return;
			}
			String outputName;
			try {
				outputName = fInput.getCanonicalPath().replaceFirst(FILE_NAME_SUFFIX + "$", "");
			} catch (IOException e) {
				System.err.println(
					"It is not possible to save the result. Field fOutput is null and no the standard output file can be created: "
					+ e.getMessage());
				return;
			}
			if (!getCheckStatus().finished) {
				outputName += "_notFinishedCheck";
				if (getCheckStatus().timeout) {
					outputName += "_timeout_" + timeOut;
				}
			} else {
				outputName += "_checked_" + ((getCheckStatus().consistency) ? "DC" : "NOTDC");
			}
			outputName += FILE_NAME_SUFFIX;
			fOutput = new File(outputName);
			if (Debug.ON) {
				LOG.info("Output file name is " + fOutput.getAbsolutePath());
			}
		}

		final TNGraph<E> g1 = getGChecked();
		g1.setInputFile(fOutput);
		g1.setName(fOutput.getName());
		g1.removeEmptyEdges();

		final CSTNUStaticLayout<E> layout = new CSTNUStaticLayout<>(g1);
		final TNGraphMLWriter graphWriter = new TNGraphMLWriter(layout);
		try {
			graphWriter.save(g1, fOutput);
		} catch (IOException e) {
			System.err.println("It is not possible to save the result. File " + fOutput + " cannot be created: "
			                   + e.getMessage() + ". Computation continues.");
		}

		if (Debug.ON) {
			LOG.info("Checked instance saved in file " + fOutput.getAbsolutePath());
		}
	}

	/**
	 * @param fileOutput the file where to save the result.
	 */
	@SuppressWarnings("SpellCheckingInspection")
	public void setfOutput(File fileOutput) {
		if (fileOutput == null) {
			return;
		}
		if (!fileOutput.getName().endsWith(FILE_NAME_SUFFIX)) {
			if (!fileOutput.renameTo(new File(fileOutput.getAbsolutePath() + FILE_NAME_SUFFIX))) {
				final String m = "File " + fileOutput.getAbsolutePath() + " cannot be renamed.";
				LOG.severe(m);
				throw new IllegalArgumentException(m);
			}
		}
		if (fileOutput.exists()) {
			if (!fileOutput.delete()) {
				final String m = "File " + fileOutput.getAbsolutePath() + " cannot be deleted.";
				LOG.severe(m);
				throw new IllegalArgumentException(m);
			}
		}
		fOutput = fileOutput;
	}

	/**
	 * The upper bounds from Z to each node have to be set after the horizon is determined. Since, the horizon depends
	 * on edge values and CSTNs, CSTNUs, CSTNPSUs have different type of edges, it is better that each class adds such
	 * edges after the determination of horizon. Therefore, such edges cannot be determined in
	 * {@link #coreCSTNInitAndCheck}.<br> The upper bound values is = #nodes * max weight absolute value.
	 */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "DLS_DEAD_LOCAL_STORE",
		justification = "It is used when DEBUG is on.")
	void addUpperBounds() {
		final Collection<LabeledNode> nodeSet = g.getVertices();
		final LabeledNode Z = g.getZ();
		assert Z != null;
		for (final LabeledNode node : nodeSet) {
			// Checks that each node has an edge from Z with bound = horizon.
			if (node != Z) {
				// UPPER BOUND FROM Z
				E edge = g.findEdge(Z, node);
				if (edge == null) {
					edge = makeNewEdge(Z.getName() + "_" + node.getName(), ConstraintType.internal);
					g.addEdge(edge, Z, node);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.WARNING)) {
							LOG.log(Level.WARNING,
							        "It is necessary to add a constraint to guarantee that '" + node.getName()
							        + "' occurs before the horizon (if it occurs).");
						}
					}
				}
				final boolean added = edge.mergeLabeledValue(node.getLabel(), horizon);
				if (Debug.ON) {
					if (added) {
						if (LOG.isLoggable(Level.FINER)) {
							LOG.log(Level.FINER, "Added " + edge.getName() + ": " + Z.getName() + "--" + pairAsString(
								node.getLabel(), horizon) + "-->" + node.getName() + ". Results: " + edge);
						}
					}
				}
			}
		}

	}

	/**
	 * checkWellDefinitionProperties. It checks WD1, WD2 and WD3.
	 *
	 * @return true if the g is a CSTN well-defined.
	 *
	 * @throws it.univr.di.cstnu.algorithms.WellDefinitionException if any.
	 */
	@SuppressWarnings("UnusedReturnValue")
	boolean checkWellDefinitionProperties() throws WellDefinitionException {
		boolean flag = false;
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "Checking if graph is well-defined...");
			}
		}
		for (final E e : g.getEdges()) {
			flag = (checkWellDefinitionProperty1and3(Objects.requireNonNull(g.getSource(e)),
			                                         Objects.requireNonNull(g.getDest(e)), e, false));
		}
		for (final LabeledNode node : g.getVertices()) {
			flag = flag && checkWellDefinitionProperty2(node, false);
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, ((flag) ? "done: all is well-defined.\n"
				                            : "done: something is wrong. Not well-defined graph!\n"));
			}
		}
		return flag;
	}

	/**
	 * Checks whether the constraint represented by an edge 'e' satisfies the well definition 1 property (WD1):<br>
	 * <em>any labeled valued of the edge has a label that subsumes both labels of two endpoints.</em>
	 * In case a label is not WD1 and {@code hasToBeFixed}, then it is fixed. Otherwise, a
	 * {@link WellDefinitionException} is raised.
	 * <br>
	 * Moreover, it checks the well definition 3 property (WD3):
	 * <em>a label subsumes all observer-t.p. labels of observer t.p.s whose propositions are present into the
	 * label.</em>
	 *
	 * @param nS           the source node of the edge. It must be not null!
	 * @param nD           the destination node of the edge. It must be not null!
	 * @param eSN          edge representing a labeled constraint. It must be not null!
	 * @param hasToBeFixed true for fixing well-definition errors that can be fixed!
	 *
	 * @return false if the check fails, true otherwise
	 *
	 * @throws WellDefinitionException if a label definition or use is wrong
	 */
	@SuppressWarnings("SameReturnValue")
	boolean checkWellDefinitionProperty1and3(final LabeledNode nS, final LabeledNode nD, final E eSN,
	                                         boolean hasToBeFixed) throws WellDefinitionException {

		final Label conjunctedLabel = nS.getLabel().conjunction(nD.getLabel());
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.log(Level.FINEST,
				        "Source label: " + nS.getLabel() + "; dest label: " + nD.getLabel() + " conjuncted label: "
				        + conjunctedLabel);
			}
		}
		if (conjunctedLabel == null) {
			final String msg =
				"Two endpoints do not allow any constraint because they have inconsistent labels." + "\nHead node: "
				+ nD + "\nTail node: " + nS + "\nConnecting edge: " + eSN;
			if (Debug.ON) {
				if (LOG.isLoggable(Level.WARNING)) {
					LOG.log(Level.WARNING, msg);
				}
			}
			throw new WellDefinitionException(msg, WellDefinitionException.Type.LabelInconsistent);
		}
		// check the ordinary labeled values
		for (final Label currentLabel : eSN.getLabeledValueMap().keySet()) {
			final int v = eSN.getValue(currentLabel);
			if (v == Constants.INT_NULL) {
				continue;
			}
			if (!currentLabel.isConsistentWith(conjunctedLabel)) {
				final String msg = "Found a labeled value in " + eSN
				                   + " that is not consistent with the conjunction of node labels, " + conjunctedLabel +
				                   ".";
				if (hasToBeFixed) {
					eSN.removeLabeledValue(currentLabel);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.WARNING)) {
							LOG.log(Level.WARNING, msg + " Labeled value '" + currentLabel + "' removed.");
						}
					}
					continue;
				}
				throw new WellDefinitionException(msg, WellDefinitionException.Type.LabelInconsistent);
			}
			if (!currentLabel.subsumes(conjunctedLabel)) {
				final String msg = "Labeled value " + pairAsString(currentLabel, v) + " of edge " + eSN.getName()
				                   + " does not subsume the endpoint labels '" + conjunctedLabel + "'.";
				if (Debug.ON) {
					if (LOG.isLoggable(Level.WARNING)) {
						LOG.log(Level.WARNING, msg);
					}
				}
				if (hasToBeFixed) {
					eSN.removeLabeledValue(currentLabel);
					eSN.putLabeledValue(currentLabel.conjunction(conjunctedLabel), v);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.WARNING)) {
							LOG.log(Level.WARNING, "Fixed as required!");
						}
					}
				} else {
					throw new WellDefinitionException(msg, WellDefinitionException.Type.LabelNotSubsumes);
				}
			}
			// Checks if label subsumes all observer-t.p. labels of observer t.p. whose proposition is present into the label.
			// WD3 property.
			Label currentLabelModified = currentLabel;
			for (final char l : currentLabel.getPropositions()) {
				final LabeledNode obs = g.getObserver(l);
				if (obs == null) {
					final String msg =
						"Observation node of literal " + l + " of label " + currentLabel + " in edge " + eSN
						+ " does not exist.";
					if (Debug.ON) {
						if (LOG.isLoggable(Level.SEVERE)) {
							LOG.log(Level.SEVERE, msg);
						}
					}
					throw new WellDefinitionException(msg, WellDefinitionException.Type.ObservationNodeDoesNotExist);
				}
				// Checks WD3 and adjusts
				final Label obsLabel = obs.getLabel();
				if (!currentLabel.subsumes(obsLabel)) {
					final String msg =
						"Label " + currentLabel + " of edge " + eSN + " does not subsume label " + obsLabel
						+ " of obs node " + obs + ". It has been fixed.";
					if (Debug.ON) {
						if (LOG.isLoggable(Level.WARNING)) {
							LOG.log(Level.WARNING, msg);
						}
					}
					currentLabelModified = currentLabelModified.conjunction(obsLabel);
					if (currentLabelModified == null) {
						if (Debug.ON) {
							if (LOG.isLoggable(Level.SEVERE)) {
								LOG.log(Level.SEVERE, "Label " + currentLabel + " of edge " + eSN
								                      + " does not subsume label of obs node " + obs
								                      + " and cannot be expanded because it becomes inconsistent.");
							}
						}
						throw new WellDefinitionException(msg, WellDefinitionException.Type.LabelInconsistent);
					}
				}
			}
			if (!currentLabelModified.equals(currentLabel)) {
				eSN.removeLabeledValue(currentLabel);
				eSN.putLabeledValue(currentLabelModified, v);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.WARNING)) {
						LOG.log(Level.WARNING, "Labeled value " + pairAsString(currentLabelModified, v)
						                       + " replace dishonest labeled value " +
						                       pairAsString(currentLabelModified, v)
						                       + " in edge " + eSN + ".");
					}
				}
			}
		}
		return true;
	}

	/**
	 * Applies rule R0/qR0: label containing a proposition that can be decided only in the future is simplified removing
	 * such proposition.
	 * <b>Standard DC semantics is assumed.</b>
	 * Derived classes can modify rule conditions of this method overriding
	 * {@link #mainConditionForSkippingInR0qR0(int)}.
	 *
	 * <pre>
	 * R0:
	 * P? --[w, α p]--&gt; X
	 * changes in
	 * P? --[w, α']--&gt; X when w &le; 0
	 * where:
	 * p is the positive or the negative literal associated to proposition observed in P?,
	 * α is a label,
	 * α' is α without 'p', P? children, and any children of possible q-literals.
	 * </pre>
	 * <p>
	 * It is assumed that P? != X.<br> Rule qR0 has X==Z.
	 *
	 * @param nObs  the observation node
	 * @param nX    the other node
	 * @param eObsX the edge connecting nObs? ---&gt; X
	 *
	 * @return true if the rule has been applied one time at least.
	 */
	boolean labelModificationR0qR0(final LabeledNode nObs, final LabeledNode nX, final E eObsX) {
		// Visibility is package because there is Junit Class test that checks this method.

		boolean ruleApplied = false, mergeStatus;

		final char p = nObs.getPropositionObserved();
		if (p == Constants.UNKNOWN) {
			return false;
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.log(Level.FINEST, "Label Modification R0: start.");
			}
		}
		if (withNodeLabels) {
			if (nX.getLabel().contains(p)) {
				// It is a strange case because only with IR it is possible to manage such case.
				// In all other case is the premise of a negative loop.
				// We let this possibility
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.log(Level.FINEST, "R0qR0: Proposition " + p + " is present in the X label '" + nX.getLabel()
						                      + "'. Rule cannot be applied.");
					}
				}
				return false;
			}
		}
		final ObjectSet<Label> obsXLabelSet = eObsX.getLabeledValueMap().keySet();

		for (final Label alpha : obsXLabelSet) {
			if (alpha == null || !alpha.contains(p)) {// l can be nullified in a previous cycle.
				continue;
			}

			final int w = eObsX.getValue(alpha);
			final Label alphaPrime = labelModificationR0qR0Core(nObs, nX, alpha, w);

			if (alphaPrime == alpha) {
				continue;
			}
			// Prepare the log message now with old values of the edge. If R0 modifies, then we can log it correctly.
			String logMessage = null;
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					logMessage =
						"R0 simplifies a label of edge " + eObsX.getName() + ":\nsource: " + nObs.getName() + " ---"
						+ pairAsString(alpha, w) + "⟶ " + nX.getName() + "\nresult: " + nObs.getName()
						+ " ---" + pairAsString(alphaPrime, w) + "⟶ " + nX.getName() + "\n";
				}
			}

			mergeStatus = eObsX.mergeLabeledValue(alphaPrime, w);
			if (mergeStatus) {
				ruleApplied = true;
				checkStatus.r0calls++;
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.log(Level.FINER, logMessage);
					}
				}
			}
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.log(Level.FINEST, "Label Modification R0: end.");
			}
		}
		return ruleApplied;
	}

	/**
	 * Execute the core of {@link #labelModificationR0qR0(LabeledNode, LabeledNode, CSTNEdge)}.<br> It can be used for
	 * applying the rule to a specific pair (w, alpha).<br> Returns the label to use for storing the new value
	 * considering rule R0qR0.
	 *
	 * @param nP    the observation node. Per efficiency reason, there is no a security check!
	 * @param nX    the other node
	 * @param alpha alpha value
	 * @param w     the value to check
	 *
	 * @return the newLabel adjusted if the rule has been applied, original label otherwise.
	 */
	Label labelModificationR0qR0Core(final LabeledNode nP, final LabeledNode nX, final Label alpha, int w) {
		final char p = nP.getPropositionObserved();
		if (withNodeLabels) {
			if (nX.getLabel().contains(p)) {
				// It is a strange case because only with IR it is possible to manage such case.
				// In all other case is the premise of a negative loop.
				// We let this possibility
				return alpha;
			}
		}

		if (w == Constants.INT_NULL || mainConditionForSkippingInR0qR0(w)) {
			return alpha;
		}

		final Label alphaPrime = makeAlphaPrime(nX, nP, p, alpha);
		if (alphaPrime == null || alphaPrime.equals(alpha)) {
			return alpha;
		}
		checkStatus.r0calls++;
		return alphaPrime;
	}

	/**
	 * Returns true if {@link CSTN#labelModificationR0qR0} method has to not apply.<br> Overriding this method it is
	 * possible implement the different semantics in the {@link CSTN#labelModificationR0qR0} method.
	 *
	 * @param w value
	 *
	 * @return true if the rule has to not apply.
	 */
	@SuppressWarnings("static-method")
	boolean mainConditionForSkippingInR0qR0(final int w) {
		// Table 1 ICAPS paper for standard DC
		// w must be <= 0 for applying the rule.
		return w > 0;
	}

	/**
	 * Returns true if {@link CSTN#labelModificationR3qR3} method has to not apply.<br> Overriding this method it is
	 * possible implement the different semantics in the {@link CSTN#labelModificationR3qR3} method.
	 *
	 * @param w  value
	 * @param nD destination node
	 *
	 * @return true if the rule has to not apply
	 */
	boolean mainConditionForSkippingInR3qR3(final int w, final LabeledNode nD) {
		// Table 1 ICAPS paper for standard DC
		// When nD==Z, it is possible to skip the rule even when w==0 because the value on the other edge, v, can be negative or 0 at most (it cannot be v>0
		// because nD==Z). Then, the max == 0, and the resulting constraint is already represented by the fact that any nodes is after or at Z in any scenario.
		return w > 0 || (w == 0 && nD == g.getZ());
	}

	/**
	 * Checks whether the label of a node satisfies the well definition 2 property (WD2):<br>
	 * <blockquote>For each literal present in a node label label:
	 * <ol>
	 * <li>the label of the observation node of the considered literal is subsumed by the label of the current node.
	 * <li>the observation node is constrained to occur before the current node.
	 * </ol>
	 * </blockquote>
	 * [2017-04-07] Posenato
	 * It has been proved that this property is not necessary for DC checking.
	 * I maintain it just to add an order among nodes and obs ones for speeding up the algorithm.
	 *
	 * @param node         the current node to check. It must be not null!
	 * @param hasToBeFixed true to add the required precedences.
	 *
	 * @return false if the check fails, true otherwise
	 *
	 * @throws WellDefinitionException if a label definition or use is wrong
	 */
	@SuppressWarnings("SameReturnValue")
	boolean checkWellDefinitionProperty2(final LabeledNode node, boolean hasToBeFixed) throws WellDefinitionException {
		final Label nodeLabel = node.getLabel();
		if (nodeLabel.isEmpty()) {
			return true;
		}

		int v;
		String msg;
		LabeledNode obs;
		// Checks whether the node label is well defined w.r.t. each involved observation node label.
		for (final char l : nodeLabel.getPropositions()) {
			obs = g.getObserver(l);
			if (obs == null) {
				msg = "Observation node of literal " + l + " of node " + node + " does not exist.";
				if (Debug.ON) {
					if (LOG.isLoggable(Level.SEVERE)) {
						LOG.log(Level.SEVERE, msg);
					}
				}
				throw new WellDefinitionException(msg, WellDefinitionException.Type.ObservationNodeDoesNotExist);
			}

			// No more necessary as required property, but the algorithm guarantees it as property.
			final Label obsLabel = obs.getLabel();
			final Label newNodeLabel;
			if (!nodeLabel.subsumes(obsLabel)) {
				newNodeLabel = nodeLabel.conjunction(obsLabel);
				if (newNodeLabel == null) {
					msg = "Label of node " + node + " is not consistent with label of obs node " + obs
					      + " while it should subsume it! The network is not well-defined.";
					if (Debug.ON) {
						if (LOG.isLoggable(Level.SEVERE)) {
							LOG.log(Level.SEVERE, msg);
						}
					}
					throw new WellDefinitionException(msg, WellDefinitionException.Type.LabelNotSubsumes);
				}

				if (hasToBeFixed) {
					node.setLabel(newNodeLabel);
				}
				msg = "Label of node " + node + " does not subsume label of obs node " + obs + ((hasToBeFixed)
				                                                                                ? ". It has been adjusted!"
				                                                                                : ".");
				if (Debug.ON) {
					if (LOG.isLoggable(Level.WARNING)) {
						LOG.log(Level.WARNING, msg);
					}
				}
				// it is necessary to restart because the set of propositions has been changed!
				if (hasToBeFixed) {
					checkWellDefinitionProperty2(node, true);
				}
			}
		}

		// LabelNode is ok with all involved observation node labels.
		// It is possible to check and assure that node is after such observation nodes.
		for (final char l : nodeLabel.getPropositions()) {
			obs = g.getObserver(l);
			assert obs != null;
			E e = g.findEdge(node, obs);
			if ((e == null) || ((v = e.getValue(nodeLabel)) == Constants.INT_NULL) || (v > 0)) {// WD2.2 ICAPS paper
				// WD2.2 has been proved to be redundant. So, it can be removed. Here we maintain a light version of it.
				// Light version: a node with label having 'p' has to be just after P?, i.e., P?⟵[0,p]---X_p.
				msg = "WD2.2 simplified: There is no constraint to execute obs node " + obs + " before node " + node;
				if (Debug.ON) {
					if (LOG.isLoggable(Level.WARNING)) {
						LOG.log(Level.WARNING, msg);
					}
				}
				if (hasToBeFixed) {
					if (e == null) {
						e = makeNewEdge(node.getName() + "_" + obs.getName(), E.ConstraintType.internal);
						g.addEdge(e, node, obs);
					}
					e.mergeLabeledValue(nodeLabel,
					                    -reactionTime);//it's not necessary, but it can speed up the DC checking.
					if (Debug.ON) {
						if (LOG.isLoggable(Level.WARNING)) {
							LOG.log(Level.WARNING,
							        "Fixed adding " + pairAsString(nodeLabel, -reactionTime) + " to " + e);
						}
					}
					//					continue;
					// Since it is redundant, it cannot raise an exception!
					// throw new WellDefinitionException(msg, WellDefinitionException.Type.ObservationNodeDoesNotOccurBefore);
				}
			}
		}
		return true;
	}

	/**
	 * Simple method to determine the {@code α'} to use in rules R0 and in rule qR0.<br> Check paper TIME15 and ICAPS
	 * 2016 about CSTN sound&amp;complete DC check.<br> {@code α'} is obtained by α removing all children of the
	 * observed proposition.<br> If X==Z, then it is necessary also to remove all children of unknown from {@code α'}.
	 *
	 * @param nX           the destination node
	 * @param nObs         observer node
	 * @param observed     the proposition observed by observer (since this value usually is already determined before
	 *                     calling this method, this parameter is just for speeding up.)
	 * @param labelFromObs label of the edge from observer
	 *
	 * @return α' the new label
	 */
	// Visibility is package because there is Junit Class test that checks this method.
	@Nullable
	Label makeAlphaPrime(final LabeledNode nX, final LabeledNode nObs, final char observed, final Label labelFromObs) {
		if (withNodeLabels && !propagationOnlyToZ) {
			if (nX.getLabel().contains(observed)) {
				return null;
			}
		}
		Label alphaPrime = labelFromObs.remove(observed);
		if (withNodeLabels) {
			alphaPrime = alphaPrime.remove(g.getChildrenOf(nObs));
			if (nX == g.getZ() && alphaPrime.containsUnknown()) {
				alphaPrime = removeChildrenOfUnknown(alphaPrime);
			}
			if (!alphaPrime.subsumes(nX.getLabel().conjunction(nObs.getLabel()))) {
				return null;
			}
		}
		return alphaPrime;
	}

	/**
	 * Makes the CSTN well-definedness check and initialization. The CSTN instance is represented by {@link #g}.<br> If
	 * some constraints of the network does not observe well-definition properties <b>and</b> they can be adjusted, then
	 * the method fixes them and logs such fixes in log system at WARNING level.<br> If the method cannot fix such
	 * not-well-defined constraints, it raises a {@link WellDefinitionException}.
	 * <p>
	 * The well-definedness properties are Wd1, Wd2, and Wd3 presented in the paper
	 * <blockquote> L. Hunsberger, R. Posenato, and
	 * C. Combi, <br> “A Sound-and-Complete Propagation-Based Algorithm for Checking the Dynamic Consistency of
	 * Conditional Simple Temporal Networks,” <br> in TIME 2015: 22nd International Symposium on Temporal Representation
	 * and Reasoning (TIME 2015), Sep. 2015, pp. 4–18. doi: 10.1109/TIME.2015.26.
	 * </blockquote>
	 * <b>Note</b>
	 * This method is necessary for allowing the building of specialization of initAndCheck (in subclasses of
	 * subclasses).
	 *
	 * @throws WellDefinitionException if the initial graph is not well-defined. We preferred to throw an exception
	 *                                 instead of returning a negative status to stress that any further operation
	 *                                 cannot be made on this instance.
	 */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "DLS_DEAD_LOCAL_STORE",
		justification = "It is used when DEBUG is on.")
	void coreCSTNInitAndCheck() throws WellDefinitionException {
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "Starting initial well definition check.");
			}
		}
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

		if (withNodeLabels) {
			// check if at least one node has label
			boolean foundLabel = false;
			for (final LabeledNode node : g.getVertices()) {
				if (!node.getLabel().isEmpty()) {
					foundLabel = true;
					break;
				}
			}
			withNodeLabels = foundLabel;
		}
		// Checks well definiteness of edges and determine maxWeight
		int minNegWeightFound = 0, maxWeightFound = 0;
		for (final E e : g.getEdges()) {
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
			// WD1 is checked and adjusted here
			if (withNodeLabels) {
				try {
					assert s != null;
					assert d != null;
					checkWellDefinitionProperty1and3(s, d, e, true);
				} catch (final WellDefinitionException ex) {
					throw new WellDefinitionException("Edge " + e + " has the following problem: " + ex.getMessage());
				}
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
		if (propagationOnlyToZ) {
			maxWeight = -minNegWeightFound;
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.log(Level.FINE, "Propagating only to Z, so masWeight is the opposite of the most negative one: "
					                    + maxWeight);
				}
			}
		} else {
			// don't remove this code because it is fundamental for determining the right upper bounds!
			maxWeight = Math.max(-minNegWeightFound, maxWeightFound);
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.log(Level.FINE,
					        "Propagating everywhere, so masWeight is the max between the opposite of the most negative one and the max positive : "
					        + (-minNegWeightFound) + ", " + maxWeightFound);
				}
			}

		}
		// Determine horizon value
		final long product = ((long) maxWeight) * (g.getVertexCount() - 1);// Z doesn't count!
		if (product >= Constants.INT_POS_INFINITE) {
			throw new ArithmeticException(
				"Horizon value is not representable by an integer. maxWeight = " + maxWeight + ", #vertices = "
				+ g.getVertexCount());
		}
		horizon = (int) product;
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "The horizon value is " + String.format("%6d", product));
			}
		}

		// Init two useful structures
		g.getPropositions();

		/*
		 * Checks well definiteness of nodes
		 */
		final Collection<LabeledNode> nodeSet = g.getVertices();
		for (final LabeledNode node : nodeSet) {
			node.clearPotential();
			if (withNodeLabels) {
				// 1. Checks that observation node doesn't have the observed proposition in its label!
				final char obs = node.getPropositionObserved();
				if (obs != Constants.UNKNOWN) {
					Label label = node.getLabel();
					if (label.contains(obs)) {
						if (Debug.ON) {
							if (LOG.isLoggable(Level.WARNING)) {
								LOG.log(Level.WARNING, "Literal '" + obs + "' cannot be part of the label '" + label
								                       + "' of the observation node '" + node.getName() +
								                       "'. Removed!");
							}
						}
						label = label.remove(obs);
						node.setLabel(label);
					}
				}

				// WD2 is checked and adjusted here
				try {
					checkWellDefinitionProperty2(node, true);
				} catch (final WellDefinitionException ex) {
					throw new WellDefinitionException(
						"WellDefinition 2 problem found at node " + node + ": " + ex.getMessage());
				}
			}
			// 3. Checks that each node has an edge to Z and edge from Z with bound = horizon.
			if (node != Z) {
				// LOWER BOUND FROM Z
				E edge = g.findEdge(node, Z);
				if (edge == null) {
					edge = makeNewEdge(node.getName() + "_" + Z.getName(), ConstraintType.internal);
					g.addEdge(edge, node, Z);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.WARNING)) {
							LOG.log(Level.WARNING,
							        "It is necessary to add a constraint to guarantee that '" + node.getName()
							        + "' occurs after '" + AbstractCSTN.ZERO_NODE_NAME + "'.");
						}
					}
				}
				final Label nodeLabel;

				if (withNodeLabels) {
					nodeLabel = node.getLabel();
				} else {
					nodeLabel = Label.emptyLabel;
					node.setLabel(nodeLabel);
				}

				final boolean added = edge.mergeLabeledValue(nodeLabel, 0);// in any case, all nodes must be after Z!
				if (Debug.ON) {
					if (added) {
						if (LOG.isLoggable(Level.FINER)) {
							LOG.log(Level.FINER,
							        "Added " + edge.getName() + ": " + node.getName() + "--" + pairAsString(nodeLabel,
							                                                                                0) + "-->" +
							        Z.getName());
						}
					}
				}
			}
		}

		// it is useful to apply R0 before starting, otherwise first cycles of algorithm can propagate dirty values before R0 can clean it
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "Preliminary cleaning by R0");
			}
		}
		// Clean all negative outgoing edges from observation time-points removing possible literals related to the current observation time-point.
		for (final LabeledNode obs : g.getObservers()) {
			for (final E e : g.getOutEdges(obs)) {
				labelModificationR0qR0(obs, g.getDest(e), e);
			}
		}
		checkStatus.reset();
		checkStatus.initialized = true;

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "Initial core well definition check done!");
			}
		}
	}

	/**
	 * Determines the set of edges P?--&gt;nX where P? is an observer node and nX is the given node.
	 *
	 * @param nX the given node.
	 *
	 * @return the list of edges P?--&gt;nX, an empty set if nX is empty or there is no observer or there is no such
	 * 	edges.
	 */
	ObjectList<E> getEdgeFromObserversToNode(final LabeledNode nX) {
		if (nX == g.getZ()) {
			return g.getObserver2ZEdges();
		}
		final ObjectList<E> fromObs = new ObjectArrayList<>();

		final Collection<LabeledNode> obsSet = g.getObservers();
		if (obsSet.isEmpty()) {
			return fromObs;
		}

		E e;
		for (final LabeledNode n : obsSet) {
			if ((e = g.findEdge(n, nX)) != null) {
				fromObs.add(e);
			}
		}
		return fromObs;
	}

	/**
	 * Create an edge assuring that its name is unique in the graph 'g'.
	 *
	 * @param name the proposed name. If an edge with name already exists, then name is modified adding a suitable
	 *             integer such that the name becomes unique in 'g'.
	 * @param type the type of edge to create.
	 *
	 * @return an edge with a unique name.
	 */
	@SuppressWarnings("MethodCallInLoopCondition")
	E makeNewEdge(final String name, final Edge.ConstraintType type) {
		int i = g.getEdgeCount();
		String name1 = name;
		while (g.getEdge(name1) != null) {
			name1 = name + "_" + i++;
		}
		final E e = g.getEdgeFactory().get(name1);
		e.setConstraintType(type);
		return e;
	}

	/**
	 * Simple method to determine the label "αβγ" for rule
	 * {@link CSTN#labelModificationR3qR3(LabeledNode, LabeledNode, CSTNEdge)}.<br> See Table 1 and Table 2 ICAPS 2016
	 * paper.
	 *
	 * @param nS           source node
	 * @param nD           destination node
	 * @param nObs         observation node
	 * @param observed     the proposition observed by observer (since this value usually is already determined before
	 *                     calling this method, this parameter is just for speeding up).
	 * @param labelFromObs label of the edge from observer
	 * @param labelToClean true if the observation must be removed
	 *
	 * @return alphaBetaGamma if all conditions are satisfied. null otherwise.
	 */
	// Visibility is package because there is Junit Class test that checks this method.
	@Nullable
	Label makeAlphaBetaGammaPrime4R3(final LabeledNode nS, final LabeledNode nD, final LabeledNode nObs,
	                                 final char observed, final Label labelFromObs, Label labelToClean) {
		StringBuilder slog;
		if (Debug.ON) {
			slog = new StringBuilder(80);
			if (LOG.isLoggable(Level.FINEST)) {
				slog.append("labelEdgeFromObs = ").append(labelFromObs);
			}
		}
		if (withNodeLabels) {
			if (labelFromObs.contains(observed) || nS.getLabel().contains(observed) || nD.getLabel()
				.contains(observed)) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.log(Level.FINEST, slog
						                      +
						                      " αβγ' cannot be calculated because labelFromObs or labels of nodes contain the prop "
						                      + observed + " that has to be removed.");
					}
				}
				return null;
			}
		}
		final Label labelToCleanWOp = labelToClean.remove(observed);
		final Label alpha = labelFromObs.getSubLabelIn(labelToCleanWOp, false);
		if (alpha.containsUnknown()) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.log(Level.FINEST, slog + " α contains unknown: " + alpha);
				}
			}
			return null;
		}
		final Label beta = labelFromObs.getSubLabelIn(labelToCleanWOp, true);
		if (beta.containsUnknown()) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.log(Level.FINEST, slog + " β contains unknown " + beta);
				}
			}
			return null;
		}
		Label gamma = labelToCleanWOp.getSubLabelIn(labelFromObs, false);

		if (withNodeLabels) {
			gamma = gamma.remove(g.getChildrenOf(nObs));
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.log(Level.FINEST, slog + " γ: " + gamma + "\n.");
			}
		}
		Label alphaBetaGamma = alpha.conjunction(beta);
		assert alphaBetaGamma != null;
		alphaBetaGamma = alphaBetaGamma.conjunction(gamma);
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				slog.append(", αβγ'=").append(alphaBetaGamma);
			}
		}

		if (withNodeLabels) {
			if (alphaBetaGamma == null) {
				return null;
			}
			if (!alphaBetaGamma.subsumes(nD.getLabel().conjunction(nS.getLabel()))) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.log(Level.FINEST, slog + " αβγ' does not subsume labels from nodes:" + nD.getLabel()
							.conjunction(nS.getLabel()));
					}
				}
				return null;
			}
		}
		return alphaBetaGamma;
	}

	/**
	 * Determines the new value that rules R3 and qR3 (see Table 1 in ICAPS 2016 paper) must add. In general, it is
	 * {@code max(edgeValue, obsEdgeValue)}, but it depends on the DC semantics.<br> For example, in case of ɛ-DC, the
	 * obsEdgeValue should be adjusted subtracting ɛ before comparison. Overriding this method allows the customization
	 * of {@link CSTN#labelModificationR3qR3(LabeledNode, LabeledNode, CSTNEdge)} according to the semantics that must
	 * be applied.
	 *
	 * @param obsEdgeValue value
	 * @param edgeValue    value
	 *
	 * @return the max between the two values.
	 */
	@SuppressWarnings("static-method")
	int newValueInR3qR3(final int edgeValue, final int obsEdgeValue) {
		// Table 1 ICAPS2016 paper for standard and IR DC
		return Math.max(edgeValue, obsEdgeValue);
	}

	/**
	 * Only for stating which kind of DC checking algorithms have been implemented so far.
	 *
	 * @author posenato
	 */
	public enum CheckAlgorithm {
		/**
		 * Hunsberger Posenato 2018. Limited to instantaneous reaction or ε semantics. It is selected when
		 * {@link #setPropagationOnlyToZ(boolean)} with true value is executed before the execution of
		 * {@link CSTN#dynamicConsistencyCheck()}
		 */
		HunsbergerPosenato18,
		/**
		 * Hunsberger Posenato 2019 It is selected when {@link #setPropagationOnlyToZ(boolean)} with false value is
		 * executed before the execution of {@link CSTN#dynamicConsistencyCheck()}
		 */
		HunsbergerPosenato19,
		/**
		 * Hunsberger Posenato 2020. It is implemented by method {@link CSTNPotential#dynamicConsistencyCheck()} and it
		 * is limited to instantaneous reaction semantic.
		 */
		HunsbergerPosenato20
	}

	/**
	 * Value for dcSemantics
	 */
	@SuppressWarnings("NonAsciiCharacters")
	public enum DCSemantics {
		/**
		 * Instantaneous reaction semantics
		 */
		IR,
		/**
		 * Standard semantics
		 */
		Std,
		/**
		 * ε-reaction semantics
		 */
		ε
	}

	/**
	 * Simple method to determine the label (β*γ)† to use in rules qR3*
	 * {@link CSTN#labelModificationR3qR3(LabeledNode, LabeledNode, CSTNEdge)}.<br> See Table 1 and Table 2 ICAPS 2016
	 * paper.
	 *
	 * @param nS           source node
	 * @param nObs         obs node
	 * @param observed     the proposition observed by observer (since this value usually is already determined before
	 *                     calling this method, this parameter is just for speeding up).
	 * @param labelFromObs label of the edge from observer
	 * @param labelToClean true if obs. prop. must be removed
	 *
	 * @return αβγ'
	 */
	// Visibility is package because there is Junit Class test that checks this method.
	// Visibility is package because there is Junit Class test that checks this method.
	// Visibility is package because there is Junit Class test that checks this method.
	// Visibility is package because there is Junit Class test that checks this method.
	@Nullable
	Label makeBetaGammaDagger4qR3(final LabeledNode nS, final LabeledNode nObs, final char observed,
	                              final Label labelFromObs, Label labelToClean) {
		if (withNodeLabels) {
			if (labelFromObs.contains(observed)) {
				return null;
			}
		}
		Label beta = labelToClean.remove(observed);
		if (withNodeLabels) {
			final Label childrenOfP = g.getChildrenOf(nObs);
			if (childrenOfP != null && !childrenOfP.isEmpty()) {
				final Label test = labelFromObs.remove(childrenOfP);
				if (!labelFromObs.equals(test)) {
					return null;// labelFromObs must not contain p or its children.
				}
				beta = beta.remove(childrenOfP);
			}
		}
		Label betaGamma = labelFromObs.conjunctionExtended(beta);
		if (withNodeLabels) {
			// remove all children of unknowns.
			betaGamma = removeChildrenOfUnknown(betaGamma);
			if (!betaGamma.subsumes(nS.getLabel())) {
				return null;
			}
		}
		return betaGamma;
	}

	/**
	 * Returns a new label removing all children of possibly present unknown literals in {@code l}. {@code l} is
	 * unchanged!
	 *
	 * @param l label
	 *
	 * @return the label modified.
	 */
	Label removeChildrenOfUnknown(Label l) {
		for (final char unknownLit : l.getAllUnknown()) {
			final LabeledNode o = g.getObserver(unknownLit);
			if (o != null) {
				final Label children = g.getChildrenOf(o);
				if (children != null) {
					l = l.remove(children);
				}
			}
		}
		return l;
	}

	/**
	 * Simple method to manage command line parameters using "args4j" library.
	 *
	 * @param args args
	 *
	 * @return false if a parameter is missing, or it is wrong. True if every parameter is given in a right format.
	 */
	@SuppressWarnings({"deprecation", "BooleanMethodIsAlwaysInverted"})
	boolean manageParameters(final String[] args) {
		final CmdLineParser parser = new CmdLineParser(this);
		try {
			// parse the arguments.
			parser.parseArgument(args);
			if (!fInput.exists()) {
				throw new CmdLineException(parser, "Input file does not exist.");
			}

			if (fOutput != null) {
				setfOutput(fOutput);
				// try {
				// this.fOutput.createNewFile();
				// new OutputStreamWriter(new FileOutputStream(this.fOutput));
				// } catch (final IOException e) {
				// throw new CmdLineException(parser, "Output file cannot be created.");
				// }
			}
		} catch (final CmdLineException e) {
			// if there's a problem in the command line, you'll get this exception. this will report an error message.
			System.err.println(e.getMessage());
			System.err.println("java " + getClass().getName() + " [options...] arguments...");
			// print the list of available options
			parser.printUsage(System.err);
			System.err.println();

			// print option sample. This is useful some time
			System.err.println(
				"Example: java -jar CSTNU-*.jar " + getClass().getName() + " " + parser.printExample(
					OptionHandlerFilter.REQUIRED) + " file_name");
			return false;
		}
		return true;
	}
}
