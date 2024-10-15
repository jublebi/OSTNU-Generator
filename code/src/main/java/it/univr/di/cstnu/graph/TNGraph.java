// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
/*
 * Bear in mind that ObjectRBTreeSet is usually a little bit more faster than ObjectAVLTreeSet on set of medium size.
 * In very large set size, ObjectAVLTreeSet shine!
 * On small set, ArraySet is more efficient in time and space.
 */
package it.univr.di.cstnu.graph;

import edu.uci.ics.jung.graph.AbstractTypedGraph;
import edu.uci.ics.jung.graph.DirectedGraph;
import edu.uci.ics.jung.graph.util.EdgeType;
import edu.uci.ics.jung.graph.util.Pair;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import it.unimi.dsi.fastutil.chars.Char2ObjectArrayMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectMap;
import it.unimi.dsi.fastutil.chars.CharSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.*;
import it.univr.di.Debug;
import it.univr.di.cstnu.graph.Edge.ConstraintType;
import it.univr.di.labeledvalue.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.Serial;
import java.lang.reflect.Array;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents (dense) temporal network graphs where nodes are {@link it.univr.di.cstnu.graph.LabeledNode} and edges are
 * (an extension of) {@link it.univr.di.cstnu.graph.Edge}. This class implements the interface
 * {@link edu.uci.ics.jung.graph.DirectedGraph} in order to allow the representation of Graph by Jung library.
 *
 * @param <E> type of edge
 *
 * @author posenato
 * @version $Rev: 897 $
 */
@SuppressFBWarnings(value = "SE_BAD_FIELD", justification = "I know what I'm doing")
public class TNGraph<E extends Edge> extends AbstractTypedGraph<LabeledNode, E>
	implements DirectedGraph<LabeledNode, E>, PropertyChangeListener {

	/**
	 * Types of network that can be represented by this class. The type is determined in the constructor checking the
	 * implemented interface by the given edge class.
	 * <br>
	 * On 2019-06-13 the considered interfaces are:
	 *
	 * <pre>
	 * Edge Interface      Network Type
	 * STNEdge             STN
	 * STNUEdge            STNU
	 * CSTNEdge            CSTN
	 * CSTNUEdge           CSTNU/PCSTNU
	 * CSTNPSUEdge		   CSTPSU
	 * </pre>
	 *
	 * <b>This is not a correct design-choice but it allows one to write classes that can use TNGraph&lt;Edge&gt;
	 * objects and make only different operations according with the type of the network.</b>
	 *
	 * @author posenato
	 */
	public enum NetworkType {
		/**
		 * Conditional STN
		 */
		CSTN,

		/**
		 * FTNU === CSTNPSU Conditional STN partially shrinkable uncertainty
		 */
		CSTNPSU,

		/**
		 * Conditional STNU
		 */
		CSTNU,

		/**
		 * Parameterized CSTNU
		 */
		PCSTNU,

		/**
		 * Simple Temporal Network
		 */
		STN,

		/**
		 * Simple Temporal Network with Uncertainty
		 */
		STNU,

		/**
		 * STNU with oracles
		 */
		OSTNU,

		/**
		 * Probabilistic simple temporal network. This type must be set manually (it cannot infer by the kind of edges).
		 */
		PSTN
	}

	/**
	 * Unmodifiable version of this graph.
	 *
	 * @param <K> the type of edges
	 *
	 * @author posenato
	 */
	public static class UnmodifiableTNGraph<K extends Edge> extends TNGraph<K> {
		/**
		 *
		 */
		@Serial
		private static final long serialVersionUID = 2L;

		/**
		 * Default constructor
		 *
		 * @param network to make unmodifiable
		 */
		public UnmodifiableTNGraph(final TNGraph<K> network) {
			super.takeFrom(network);
		}

		/**
		 * Unsupported.
		 */
		@Override
		public void addChildToObserverNode(@Nonnull LabeledNode obs, char child) {
			throw new UnsupportedOperationException();
		}

		/**
		 * Unsupported.
		 */
		@Override
		public boolean addEdge(@Nonnull K e, @Nonnull final LabeledNode v1, @Nonnull final LabeledNode v2) {
			throw new UnsupportedOperationException();
		}

		/**
		 * Unsupported.
		 */
		@Override
		public boolean addEdge(@Nonnull K e, @Nonnull final LabeledNode v1, @Nonnull final LabeledNode v2,
		                       @Nonnull final EdgeType edge_type1) {
			throw new UnsupportedOperationException();
		}

		/**
		 * Unsupported.
		 */
		@Override
		public boolean addEdge(@Nonnull K edge, @Nonnull Pair<? extends LabeledNode> endpoints,
		                       @Nonnull EdgeType edgeType) {
			throw new UnsupportedOperationException();
		}

		/**
		 * Unsupported.
		 */
		@Override
		public void addEdge(@Nonnull K e, @Nonnull final String v1Name, @Nonnull final String v2Name) {
			throw new UnsupportedOperationException();
		}

		/**
		 * Unsupported.
		 */
		@Override
		public boolean addVertex(@Nonnull LabeledNode vertex) {
			throw new UnsupportedOperationException();
		}

		/**
		 * Makes this graph empty.
		 */
		@Override
		public void clear() {
			final TNGraph<K> g = new TNGraph<>(this.getName(), getEdgeImplClass());
			super.takeFrom(g);
		}

		/**
		 * Unsupported
		 */
		@Override
		public void clear(int initialAdjSize) {
			throw new UnsupportedOperationException();
		}

		/**
		 * Unsupported.
		 */
		@Override
		public void clearCache() {
			throw new UnsupportedOperationException();
		}

		/**
		 * Unsupported.
		 */
		@Override
		public void copy(@Nonnull final TNGraph<K> g) {
			throw new UnsupportedOperationException();
		}

		/**
		 * Unsupported.
		 */
		@Override
		public void copyCleaningRedundantLabels(@Nonnull TNGraph<K> g) {
			throw new UnsupportedOperationException();
		}

		/**
		 *
		 */
		@Override
		public void propertyChange(@Nonnull PropertyChangeEvent pce) {
			throw new UnsupportedOperationException();
		}

		/**
		 * Unsupported.
		 */
		@Override
		public boolean removeEdge(@Nonnull K edge) {
			throw new UnsupportedOperationException();
		}

		/**
		 * Unsupported.
		 */
		@Override
		public boolean removeEdge(@Nonnull String edgeName) {
			throw new UnsupportedOperationException();
		}

		/**
		 * Unsupported.
		 */
		@Override
		public boolean removeEmptyEdges() {
			throw new UnsupportedOperationException();
		}

		/**
		 * Unsupported.
		 */
		@Override
		public boolean removeVertex(@Nonnull LabeledNode removingNode) {
			throw new UnsupportedOperationException();
		}

		/**
		 * Unsupported.
		 */
		@Override
		public void reverse() {
			throw new UnsupportedOperationException();
		}

		/**
		 * Unsupported.
		 */
		@Override
		public void setInputFile(File file) {
			throw new UnsupportedOperationException();
		}

		/**
		 * Unsupported.
		 */
		@Override
		public void setName(@Nonnull final String graphName) {
			throw new UnsupportedOperationException();
		}

		/**
		 * Unsupported.
		 */
		@Override
		public void setZ(final LabeledNode z) {
			throw new UnsupportedOperationException();
		}

		/**
		 * Unsupported.
		 */
		@Override
		public void takeFrom(@Nonnull final TNGraph<? extends K> g) {
			throw new UnsupportedOperationException();
		}

		/**
		 * Unsupported.
		 */
		@Override
		public void transpose() {
			throw new UnsupportedOperationException();
		}
	}

	/**
	 * Adjacency grow factor; It represents the multiplication factor to use for increasing the dimension of adjacency
	 * matrix. It has to be at least 1.5.
	 */
	static final float growFactor = 1.8f;
	/**
	 *
	 */
	private static final Logger LOG = Logger.getLogger(TNGraph.class.getName());
	/**
	 *
	 */
	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * Returns an unmodifiable TNGraph backed by the given TNGraph.
	 *
	 * @param g   the TNGraph to be wrapped in an unmodifiable map.
	 * @param <K> the type of edges
	 *
	 * @return an unmodifiable view of the specified TNGraph.
	 */
	@SuppressWarnings("ClassReferencesSubclass")
	@Nonnull
	public static <K extends Edge> UnmodifiableTNGraph<K> unmodifiable(TNGraph<K> g) {
		return new UnmodifiableTNGraph<>(g);
	}

	/**
	 * @return an instance for childrenOfObserver field.
	 */
	@Nonnull
	private static Object2ObjectMap<LabeledNode, Label> newChildrenObserverInstance() {
		// in Label class, I showed that for small map, ArrayMap is faster than Object2ObjectRBTreeMap and
		// Object2ObjectAVLTreeMap.
		return new Object2ObjectArrayMap<>();
	}

	/**
	 * @return an instance for propositionToNode field.
	 */
	@Nonnull
	private static Char2ObjectMap<LabeledNode> newProposition2NodeInstance() {
		final Char2ObjectMap<LabeledNode> map = new Char2ObjectArrayMap<>();
		map.defaultReturnValue(null);
		return map;// I verified that Char2ObjectArrayMap is faster than Open hash when proposition are limited, as in this application.
	}

	/**
	 * Represents the association of an edge with its position in the adjacency matrix of the graph.
	 *
	 * @author posenato
	 */
	private class EdgeIndex {
		/*
		 * It is not possible to use the technique used for Node (extending LabeledIntNode class) because if I extended
		 * E, then edges are viewed as E and parameterized type T cannot be used as base class for extending.
		 */

		/**
		 *
		 */
		int colAdj;
		/**
		 *
		 */
		E edge;
		/**
		 *
		 */
		int rowAdj;

		/**
		 * @param e   edge
		 * @param row row
		 * @param col col
		 */
		EdgeIndex(E e, int row, int col) {
			edge = e;
			rowAdj = row;
			colAdj = col;
		}

		@Override
		@Nonnull
		public String toString() {
			return String.format("%s->(%dX%d)", edge.getName(), Integer.valueOf(rowAdj), Integer.valueOf(colAdj));
		}
	}

	/**
	 * The graph is represented by its adjacency matrix.
	 */
	private E[][] adjacency;
	/**
	 * Alphabet for A-Label
	 */
	private ALabelAlphabet aLabelAlphabet;
	/**
	 * Children of observation nodes
	 */
	private Map<LabeledNode, Label> childrenOfObserver;
	/**
	 * Map (edge--&gt;adjacency position)
	 */
	private Object2ObjectMap<String, EdgeIndex> edge2index;
	/**
	 * Edge factory
	 */
	private EdgeSupplier<E> edgeFactory;
	/**
	 * Map (adjacency row--&gt;node)
	 */
	private Int2ObjectMap<LabeledNode> index2node;
	/**
	 * Map (node) --&gt; list of its (in-going edge, sourceNode). It works as cache.
	 */
	private Map<LabeledNode, ObjectList<ObjectObjectImmutablePair<E, LabeledNode>>> inEdgesCache;
	/**
	 * Map (node) --&gt; list of its (out-going edge, destinationNode). It works as cache.
	 */
	private Map<LabeledNode, ObjectList<ObjectObjectImmutablePair<E, LabeledNode>>> outEdgesCache;
	/**
	 * A possible input file containing this graph.
	 */
	private File inputFile;
	/**
	 * List of edges with lower case label set not empty
	 */
	private ObjectList<BasicCSTNUEdge> lowerCaseEdges;
	/**
	 * Name
	 */
	private String name;
	/**
	 * Node factory
	 */
	private LabeledNodeSupplier nodeFactory;
	/**
	 * Map (node--&gt;adjacency row)
	 */
	private Object2IntMap<String> nodeName2index;
	/**
	 * List of edges from observers to Z
	 */
	private ObjectList<E> observer2Z;
	/**
	 * Current number of nodes;
	 */
	private int order;
	/**
	 * Map of (proposition--&gt;Observer node).
	 */
	private Char2ObjectMap<LabeledNode> proposition2Observer;
	/**
	 * Type of network
	 */
	private NetworkType type;
	/**
	 * Zero node. In temporal constraint network such node is the first node to execute.
	 */
	private LabeledNode Z;

	/**
	 * Constructor for TNGraph.
	 *
	 * @param <E1>               type of edge
	 * @param graphName          a name for the graph
	 * @param inputEdgeImplClass type of edges
	 */
	public <E1 extends E> TNGraph(@Nonnull final String graphName, @Nonnull Class<E1> inputEdgeImplClass) {
		super(EdgeType.DIRECTED);
		if (OSTNUEdgePluggable.class.isAssignableFrom(inputEdgeImplClass)) {
			type = NetworkType.OSTNU;
		} else if (CSTNPSUEdge.class.isAssignableFrom(inputEdgeImplClass)) {
			type = NetworkType.CSTNPSU;
		} else if (CSTNUEdge.class.isAssignableFrom(inputEdgeImplClass)) {
			type = NetworkType.CSTNU;
		} else if (CSTNEdge.class.isAssignableFrom(inputEdgeImplClass)) {
			type = NetworkType.CSTN;
		} else if (STNUEdge.class.isAssignableFrom(inputEdgeImplClass)) {
			type = NetworkType.STNU;
		} else if (STNEdge.class.isAssignableFrom(inputEdgeImplClass)) {
			type = NetworkType.STN;
		}
		//For the probabilistic STN, the inputEdgeImplClass is STNUEdge, so it is necessary to check the filename extension
		if (type == NetworkType.STNU && graphName.endsWith(".pstn")) {
			type = NetworkType.PSTN;
		}
		if (BasicCSTNUEdge.class.isAssignableFrom(inputEdgeImplClass) ||
		    STNUEdge.class.isAssignableFrom(inputEdgeImplClass)) {
			aLabelAlphabet = new ALabelAlphabet();
		}
		edgeFactory = new EdgeSupplier<>(inputEdgeImplClass);// , inputLabeledValueMapImplClass
		edge2index = new Object2ObjectOpenHashMap<>(); // From fastutil javadoc:
		// In general, AVL trees have slightly slower updates but faster searches;
		// however, on very large collections the smaller height may lead in fact to faster updates, too.
		// Posenato: I find experimentally that in many applications getDest e getEdge are the most executed operations.
		// For such operations, Object2ObjectOpenHashMap is faster than any tree!

		nodeFactory = new LabeledNodeSupplier();// inputLabeledValueMapImplClass
		order = 0;
		adjacency = createAdjacency(10);
		nodeName2index = new Object2IntOpenHashMap<>();
		nodeName2index.defaultReturnValue(Constants.INT_NULL);
		index2node = new Int2ObjectOpenHashMap<>();
		inEdgesCache = new Object2ObjectOpenHashMap<>();
		outEdgesCache = new Object2ObjectOpenHashMap<>();
		name = graphName;
	}

	/**
	 * Constructor for TNGraph.
	 *
	 * @param <E1>               type of edge
	 * @param graphName          a name for the graph
	 * @param inputEdgeImplClass type of edges
	 * @param alphabet           alphabet for upper case letter used to label values in the edges.
	 */
	@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "For efficiency reason, it includes an external mutable object.")
	public <E1 extends E> TNGraph(@Nonnull final String graphName, @Nonnull Class<E1> inputEdgeImplClass,
	                              @Nullable ALabelAlphabet alphabet) {
		this(graphName, inputEdgeImplClass);
		aLabelAlphabet = alphabet;
	}

	/**
	 * A constructor that copy a given graph g using copy constructor for internal structures.<br> If g is null, this
	 * new graph will be empty.
	 *
	 * @param <E1>          type of edge
	 * @param inputGraph    the graph to be cloned
	 * @param edgeImplClass class
	 */
	public <E1 extends E> TNGraph(@Nonnull final TNGraph<E> inputGraph, @Nonnull Class<E1> edgeImplClass) {
		this(inputGraph.name, edgeImplClass);
		if (Debug.ON) {
			LOG.finer("Creating a new copy of a given graph.");
		}
		aLabelAlphabet = (inputGraph.aLabelAlphabet != null) ? inputGraph.aLabelAlphabet
		                                                     : aLabelAlphabet;// g.aLabelAlphabet has been already used for representing
		// node.aLabel field and Upper case label in contingent
		// edges
		inputFile = inputGraph.inputFile;
		adjacency = createAdjacency(inputGraph.adjacency.length);
		nodeFactory = inputGraph.nodeFactory;

		// initialize node structures and clone all nodes of g.
		order = 0;
		inEdgesCache = new Object2ObjectOpenHashMap<>();
		outEdgesCache = new Object2ObjectOpenHashMap<>();
		index2node = new Int2ObjectOpenHashMap<>();
		nodeName2index = new Object2IntOpenHashMap<>();
		nodeName2index.defaultReturnValue(Constants.INT_NULL);
		// clone all nodes

		for (final LabeledNode node : inputGraph.getVertices()) {
			final LabeledNode newNode = LabeledNodeSupplier.get(node);
			if (node.getALabel() != null) {
				newNode.setALabel(new ALabel(node.getALabel().toString(), aLabelAlphabet));
			}
			addVertex(newNode);
			if (node.equalsByName(inputGraph.Z)) {
				Z = newNode;
			}
		}
		// clone all edges giving the right new endpoints corresponding the old ones.
		E eNew;
		for (final E e : inputGraph.getEdges()) {
			eNew = edgeFactory.get(e);
			addEdge(eNew, Objects.requireNonNull(inputGraph.getSource(e.getName())).getName(),
			        Objects.requireNonNull(inputGraph.getDest(e.getName())).getName());
		}
	}

	/**
	 * It must not be used outside.
	 */
	private TNGraph() {
		super(EdgeType.DIRECTED);
	}

	/**
	 * Add child to obs. It is user responsibility to assure that 'child' is a children in CSTN sense of 'obs'. No
	 * validity check is made by the method.
	 *
	 * @param obs   a {@link it.univr.di.cstnu.graph.LabeledNode} object.
	 * @param child a char.
	 */
	public void addChildToObserverNode(@Nonnull LabeledNode obs, char child) {
		if (childrenOfObserver == null) {
			childrenOfObserver = newChildrenObserverInstance();
		}
		Label children = childrenOfObserver.get(obs);
		if (children == null) {
			children = Label.emptyLabel;
		}
		children = children.conjunction(child, Literal.STRAIGHT);
		childrenOfObserver.put(obs, children);
	}

	/**
	 * Adds the edge if there not exist an edge between v1 and v2. Moreover, adds node(s) if it(they) is(are) not
	 * present in the graph. It calls {@link #addEdge(Edge, String, String)} method.
	 *
	 * @see #addEdge(Edge, String, String)
	 */
	@Override
	public boolean addEdge(@Nonnull E e, final @Nonnull LabeledNode v1, final @Nonnull LabeledNode v2) {
		if (!nodeName2index.containsKey(v1.getName())) {
			addVertex(v1);
		}
		if (!nodeName2index.containsKey(v2.getName())) {
			addVertex(v2);
		}
		addEdge(e, v1.getName(), v2.getName());
		return true;
	}

	/**
	 *
	 */
	@Override
	public boolean addEdge(@Nonnull E e, final @Nonnull LabeledNode v1, final @Nonnull LabeledNode v2,
	                       final @Nonnull EdgeType edge_type1) {
		return addEdge(e, v1, v2);
	}

	/**
	 *
	 */
	@Override
	public boolean addEdge(@Nonnull E edge, @Nonnull Pair<? extends LabeledNode> endpoints,
	                       @Nonnull EdgeType edgeType) {
		return addEdge(edge, endpoints.getFirst(), endpoints.getSecond());
	}

	/**
	 * Unsupported
	 */
	@Override
	public boolean addEdge(@Nonnull E edge, @Nonnull Collection<? extends LabeledNode> vertices) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Unsupported
	 */
	@Override
	public boolean addEdge(@Nonnull E edge, @Nonnull Collection<? extends LabeledNode> vertices, EdgeType t) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Optimized method for adding edge. It exploits internal structure of the class.
	 * <br>
	 * Adds the input edge if there not exist an edge between v1Name and v2Name.
	 *
	 * @param e      not null
	 * @param v1Name not null
	 * @param v2Name not null
	 *
	 * @throws IllegalArgumentException if e has an equal name of another edge in the graph or if between the two nodes
	 *                                  there is already an edge.
	 */
	public void addEdge(@Nonnull E e, @Nonnull final String v1Name, @Nonnull final String v2Name) {
		/*
		 * It is necessary to copy the general method here because otherwise it calls the general
		 * addEdge(e, new Pair<LabeledNode>(v1, v2), edge_type)!!!
		 * @see edu.uci.ics.jung.graph.AbstractGraph#addEdge(Object, Object, Object, EdgeType)
		 */
		if (edge2index.containsKey(e.getName())) {
			final String msg = "An edge with name " + e.getName() + " already exists. The new edge cannot be added.";
			LOG.severe(msg);
			throw new IllegalArgumentException(msg);
		}
		if (!nodeName2index.containsKey(v1Name)) {
			addVertex(new LabeledNode(v1Name));
		}
		if (!nodeName2index.containsKey(v2Name)) {
			addVertex(new LabeledNode(v2Name));
		}
		final int sourceIndex = nodeName2index.getInt(v1Name);
		if (sourceIndex == Constants.INT_NULL) {
			final String msg = "Source node of the new edge " + e + " is null. The new edge cannot be added.";
			LOG.severe(msg);
			throw new IllegalArgumentException(msg);
		}
		final int destIndex = nodeName2index.getInt(v2Name);
		if (destIndex == Constants.INT_NULL) {
			final String msg = "Destination node of the edge " + e + " is null. The new edge cannot be added.";
			LOG.severe(msg);
			throw new IllegalArgumentException(msg);
		}

		final E old = adjacency[sourceIndex][destIndex];
		if (old != null) {
			final String msg = "Between node " + v1Name + " and node " + v2Name + " there exists the edge " + old +
			                   ". Remove it before adding a new one " + e;
			LOG.severe(msg);
			throw new IllegalArgumentException(msg);
		}
		// removeEdgeFromIndex(old);
		adjacency[sourceIndex][destIndex] = e;

		final var retValue = edge2index.put(e.getName(), new EdgeIndex(e, sourceIndex, destIndex));
		if (retValue != null) {
			// redundant check
			final String msg = "The new edge " + e + " overwrote the entry " + retValue +
			                   " in edge2index map. This should be impossible.";
			LOG.severe(msg);
			throw new IllegalStateException(msg);
		}

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Added edge " + e);
			}
		}

		lowerCaseEdges = null;
		inEdgesCache.remove(index2node.get(destIndex));
		outEdgesCache.remove(index2node.get(sourceIndex));
		((AbstractEdge) e).addObserver("edgeType", this);
		((AbstractEdge) e).addObserver("edgeName", this);
	}

	/**
	 * Unsupported
	 */
	@Override
	public boolean addEdge(@Nonnull E edge, @Nonnull Pair<? extends LabeledNode> vertices) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Adds the given vertex to the graph.
	 *
	 * @throws IllegalArgumentException if vertex has an equal name of another vertex in the graph or if it is an
	 *                                  observer of a condition for which there is already another observer in the
	 *                                  graph.
	 */
	@Override
	public boolean addVertex(@Nonnull LabeledNode vertex) {
		if (nodeName2index.containsKey(vertex.getName()) || (vertex.getPropositionObserved() != Constants.UNKNOWN &&
		                                                     getObserver(vertex.getPropositionObserved()) != null)) {

			final String msg =
				"The new vertex is already present or is an observer of a proposition for which there is already " +
				"an observer.";
			if (Debug.ON) {
				if (LOG.isLoggable(Level.SEVERE)) {
					LOG.severe(msg);
				}
			}
			throw new IllegalArgumentException(msg);
		}

		final int currentSize = adjacency.length;
		if (currentSize == order) {
			final int newSize = (int) (currentSize * growFactor);
			final E[][] newAdjacency = createAdjacency(newSize);
			for (int i = currentSize; i-- != 0; ) {
				for (int j = currentSize; j-- != 0; ) {
					newAdjacency[i][j] = adjacency[i][j];
				}
			}
			adjacency = newAdjacency;
		}
		// now it is possible to add node in position 'order'
		nodeName2index.put(vertex.getName(), order);
		index2node.put(order, vertex);
		order++;
		clearCache();
		vertex.addObserver("nodeName", this);
		vertex.addObserver("nodeLabel", this);
		vertex.addObserver("nodeProposition", this);
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Added node " + vertex);
			}
		}
		return true;
	}

	/**
	 * clear all internal structures. The graph will be empty.
	 */
	public void clear() {
		clear(10);
	}

	/**
	 * Clear all internal structures. The graph will be erased.
	 *
	 * @param initialAdjSize initial number of vertices.
	 */
	public void clear(int initialAdjSize) {
		order = 0;// addVertex adjusts the value
		if (adjacency != null && adjacency.length <= initialAdjSize) {
			// reuse the array! This is important in some application where
			// many graphs are created in a very short time
			Arrays.fill(adjacency[0], null);
			final int adjSize = adjacency.length;
			for (int i = 1; i < adjSize; i++) {
				System.arraycopy(adjacency[0], 0, adjacency[i], 0, adjSize);
			}
		} else {
			adjacency = createAdjacency(initialAdjSize);
		}
		nodeName2index.clear();
		index2node.clear();
		edge2index.clear();
		clearCache();
	}

	/**
	 * Clear all internal caches.
	 * <p>
	 * Caches are automatically created during any modification or query about the graph structure.
	 */
	public void clearCache() {
		lowerCaseEdges = null;
		proposition2Observer = null;
		childrenOfObserver = null;
		observer2Z = null;
		inEdgesCache.clear();
		outEdgesCache.clear();
	}

	/**
	 *
	 */
	@Override
	public boolean containsEdge(@Nonnull E edge) {
		return edge2index.containsKey(edge.getName());
	}

	/**
	 *
	 */
	@Override
	public boolean containsVertex(@Nonnull LabeledNode vertex) {
		return nodeName2index.containsKey(vertex.getName());
	}

	/**
	 * Defensive copy of all internal structures of g into this.
	 * <p> This method is useful to copy a graph into the
	 * current without modifying the reference to the current.
	 * <p> 'g' internal structures are defensive copied as they are.
	 *
	 * @param g the graph to copy.
	 */
	public void copy(@Nonnull final TNGraph<E> g) {
		name = g.name;
		order = 0;// addVertex adjusts the value
		adjacency = createAdjacency(g.getVertexCount());
		nodeName2index.clear();
		index2node.clear();
		edge2index.clear();
		clearCache();
		// Clone all nodes
		LabeledNode vNew;
		for (final LabeledNode v : g.getVertices()) {
			vNew = LabeledNodeSupplier.get(v);
			addVertex(vNew);
			if (v.equalsByName(g.Z)) {
				Z = vNew;
			}
		}

		// Clone all edges giving the right new endpoints corresponding the old ones.
		for (final E e : g.getEdges()) {
			addEdge(edgeFactory.get(e), Objects.requireNonNull(g.getSource(e)).getName(),
			        Objects.requireNonNull(g.getDest(e)).getName());
		}
	}

	/**
	 * Makes a copy as {@link #copy(TNGraph)} removing all labeled values having unknown literal(s) or -∞ value.
	 *
	 * @param g a  object.
	 */
	public void copyCleaningRedundantLabels(@Nonnull TNGraph<E> g) {
		name = g.name;
		order = 0;// addVertex adjusts the value
		adjacency = createAdjacency(g.getVertexCount());
		nodeName2index.clear();
		index2node.clear();
		edge2index.clear();
		clearCache();
		// clone all nodes
		LabeledNode vNew;
		for (final LabeledNode v : g.getVertices()) {
			vNew = LabeledNodeSupplier.get(v);
			for (final Label label : vNew.getLabeledPotential().keySet()) {
				if (label.containsUnknown()) {
					vNew.removeLabeledPotential(label);
				}
			}
			for (final Label label : vNew.getLabeledUpperPotential().keySet()) {
				if (label.containsUnknown()) {
					vNew.removeLabeledUpperPotential(label);
				}
			}
			addVertex(vNew);
			if (v.equalsByName(g.Z)) {
				Z = vNew;
			}
		}

		// clone all edges giving the right new endpoints corresponding the old ones.
		E eNew;
		int value;
		Label label;
		for (final E e : g.getEdges()) {
			if (e.isEmpty()) {
				continue;
			}
			eNew = edgeFactory.get(e.getName());// I don't copy values!
			eNew.setConstraintType(e.getConstraintType());
			if (e.isSTNEdge()) {
				((STNEdge) eNew).setValue(((STNEdge) e).getValue());
				addEdge(eNew, Objects.requireNonNull(g.getSource(e)).getName(),
				        Objects.requireNonNull(g.getDest(e)).getName());
				continue;
			}
			if (e.isCSTNEdge()) {
				for (final Object2IntMap.Entry<Label> entry : ((CSTNEdge) e).getLabeledValueSet()) {
					value = entry.getIntValue();
					if (value == Constants.INT_NEG_INFINITE) {
						continue;
					}
					label = entry.getKey();
					if (label.containsUnknown()) {
						continue;
					}
					((CSTNEdge) eNew).mergeLabeledValue(entry.getKey(), value);
				}
			}
			if (BasicCSTNUEdge.class.isAssignableFrom(e.getClass())) {
				for (final ALabel alabel : ((BasicCSTNUEdge) e).getUpperCaseValueMap().keySet()) {
					for (final Object2IntMap.Entry<Label> entry1 : ((BasicCSTNUEdge) e).getUpperCaseValueMap()
						.get(alabel).entrySet()) {// entrySet read-only
						value = entry1.getIntValue();
						if (value == Constants.INT_NEG_INFINITE) {
							continue;
						}
						label = entry1.getKey();
						if (label.containsUnknown()) {
							continue;
						}
						((BasicCSTNUEdge) eNew).mergeUpperCaseValue(entry1.getKey(), alabel, entry1.getIntValue());
					}
				}
				if (e.isCSTNUEdge()) {
					// lower case value
					((CSTNUEdge) eNew).setLowerCaseValue(((CSTNUEdge) e).getLowerCaseValue());
				}
				if (e.isCSTNPSUEdge()) {
					// lower case value
					((CSTNPSUEdge) eNew).setLowerCaseValue(((CSTNPSUEdge) e).getLowerCaseValueMap());
				}
			}
			if (eNew.isEmpty()) {
				continue;
			}
			addEdge(eNew, Objects.requireNonNull(g.getSource(e)).getName(),
			        Objects.requireNonNull(g.getDest(e)).getName());
		}
	}

	/**
	 *
	 */
	@Override
	public int degree(@Nonnull LabeledNode vertex) {
		final int nodeIndex;
		if ((nodeIndex = nodeName2index.getInt(vertex.getName())) == Constants.INT_NULL) {
			return Constants.INT_NULL;
		}
		int count = 0;
		for (int i = order; --i >= 0; ) {
			if (adjacency[nodeIndex][i] != null) {
				count++;
			}
			if (adjacency[i][nodeIndex] != null) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Returns the list of different edges of this graph from g1. If an edge is present in one graph and not present in
	 * the other, it is reported as different.
	 *
	 * @param g1 a  object.
	 *
	 * @return list of edges of this graph that are not equal to g1 edges w.r.t. their name and their values, empty
	 * 	immutable list if the two graphs have the same set of edges.
	 */
	@Nonnull
	public ObjectList<ObjectObjectImmutablePair<E, E>> differentEdgesOf(@Nonnull final TNGraph<E> g1) {

		final StringBuilder difference = new StringBuilder("Different edges:");
		final String currentName = name;
		final String g1name = g1.name;

		boolean sameEdges = true;
		final ObjectList<ObjectObjectImmutablePair<E, E>> differentEdges = new ObjectArrayList<>();
		final ObjectSet<E> allEdges = new ObjectAVLTreeSet<>(getEdges());
		allEdges.addAll(g1.getEdges());
		E eg, eg1;
		for (final E e : allEdges) {
			eg = getEdge(e.getName());
			eg1 = g1.getEdge(e.getName());
			if (eg == null || eg1 == null || !eg.hasSameValues(eg1)) {//e.g., or eg1 is always != null
				differentEdges.add(new ObjectObjectImmutablePair<>(eg, eg1));
				difference.append("\nGraph ").append(currentName).append(":\t").append(eg).append("\nGraph ")
					.append(g1name).append(":\t").append(eg1);
				sameEdges = false;// Log all differences!!!
			}
		}

		if (Debug.ON) {
			if (!sameEdges) {
				if (LOG.isLoggable(Level.FINE)) {
					TNGraph.LOG.log(Level.FINE, difference.toString());
				}
			}
		}
		return differentEdges;
	}

	/*
	 * Equals based on equals of edges and vertices.
	 * To avoid because it is quite heavy, and it requires to modify hashCode.
	 * Finding a hashCode() working also for TNGraphReader is not simple.
	 */
//	@Override
//	public boolean equals(final Object o) {
//		if (this == o) {
//			return true;
//		}
//		if (!(o instanceof TNGraph)) {
//			return false;
//		}
//		@SuppressWarnings("unchecked") final TNGraph<E> g1 = (TNGraph<E>) o;
//		return hasSameEdgesOf(g1) && hasSameVerticesOf(g1);
//	}

	/**
	 *
	 */
	@Override
	@Nullable
	public E findEdge(@Nullable LabeledNode s, @Nullable LabeledNode d) {
		if (s == null || d == null) {
			return null;
		}
		return findEdge(s.getName(), d.getName());
	}

	/**
	 * Find the edge given the name of source node and destination one.
	 *
	 * @param s a {@link java.lang.String} object.
	 * @param d a {@link java.lang.String} object.
	 *
	 * @return null if any parameter is null or there not exists at least one of two nodes or the edge does not exist.
	 */
	@Nullable
	public E findEdge(String s, String d) {
		if (s == null || d == null) {
			return null;
		}
		final int sourceNI = nodeName2index.getInt(s);
		if (sourceNI == Constants.INT_NULL) {
			return null;
		}
		final int destNI = nodeName2index.getInt(d);
		if (destNI == Constants.INT_NULL) {
			return null;
		}
		return adjacency[sourceNI][destNI];
	}

	/**
	 * @return the aLabelAlphabet
	 */
	public ALabelAlphabet getALabelAlphabet() {
		return aLabelAlphabet;
	}

	/**
	 * Given an observation node {@code obs} that observes the proposition 'p', its 'children' are all observation
	 * nodes, Q, for which 'p' appears in the label of node Q.
	 * <p>
	 * This method returns the set of children of a given node as a <b>label of straight propositions</b> associated to
	 * the children instead of a <b>set of children nodes</b>.
	 *
	 * @param obs a {@link it.univr.di.cstnu.graph.LabeledNode} object.
	 *
	 * @return the set of children of observation node {@code obs}, null if there is no children.
	 */
	@Nullable
	public Label getChildrenOf(@Nonnull LabeledNode obs) {
		// The soundness of this method is based on the property that the observed proposition of an observation node is represented as a straight literal.
		if (childrenOfObserver == null) {
			// Build the cache map of childrenOfObserver
			childrenOfObserver = newChildrenObserverInstance();

			for (final Char2ObjectMap.Entry<LabeledNode> entryObservedObserverNode : getObservedAndObserver().char2ObjectEntrySet()) {
				final char observedProposition = entryObservedObserverNode.getCharKey();
				final LabeledNode observator = entryObservedObserverNode.getValue();
				final Label observatorLabel = observator.getLabel();
				for (final char propInObsLabel : observatorLabel.getPropositions()) {
					final LabeledNode father = getObserver(propInObsLabel);// for the well property, father must exist!
					Label children = childrenOfObserver.get(father);
					if (children == null) {
						children = Label.emptyLabel;
					}
					children = children.conjunction(observedProposition, Literal.STRAIGHT);
					childrenOfObserver.put(father, children);
				}
			}
		}
		return childrenOfObserver.get(obs);
	}

	/**
	 * @return the number of contingent nodes.
	 */
	public int getContingentNodeCount() {
		int c = 0;
		for (final E e : getEdges()) {
			if (e.isContingentEdge()) {
				c++;
			}
		}
		return c / 2;
	}

	/**
	 * Wrapper {@link #getDest(Edge)}
	 *
	 * @param edgeName a {@link java.lang.String} object.
	 *
	 * @return a {@link it.univr.di.cstnu.graph.LabeledNode} object.
	 */
	@Nullable
	public LabeledNode getDest(@Nonnull String edgeName) {
		final EdgeIndex ei = edge2index.get(edgeName);
		if (ei == null) {
			return null;
		}
		return index2node.get(ei.colAdj);
	}

	/**
	 *
	 */
	@Override
	@Nullable
	public LabeledNode getDest(@Nonnull E directedEdge) {
		final EdgeIndex ei = edge2index.get(directedEdge.getName());
		if (ei == null) {
			return null;
		}

		return index2node.get(ei.colAdj);
	}

	/**
	 * Returns the edge associated to the name.
	 *
	 * @param s a {@link java.lang.String} object.
	 *
	 * @return the edge associated to the name.
	 */
	@Nullable
	public E getEdge(@Nonnull final String s) {
		if (s.isEmpty()) {
			return null;
		}
		final EdgeIndex ei = edge2index.get(s);
		if (ei == null) {
			return null;
		}
		return ei.edge;
	}

	/**
	 *
	 */
	@Override
	public int getEdgeCount() {
		return edge2index.size();
	}

	/**
	 * Getter for the field {@code edgeFactory}.
	 *
	 * @return the edgeFactory
	 */
	public EdgeSupplier<E> getEdgeFactory() {
		return edgeFactory;
	}

	/**
	 * @return the edgeImplementationClass
	 */
	@SuppressFBWarnings(value = "NP_NONNULL_RETURN_VIOLATION", justification = "False positive.")
	@Nonnull
	public Class<? extends E> getEdgeImplClass() {
		return edgeFactory.getEdgeImplClass();
	}

	/**
	 * Returns an independent collection of the edges of this graph.
	 */
	@Override
	@Nonnull
	public Collection<E> getEdges() {
		final ObjectArrayList<E> coll = new ObjectArrayList<>(getEdgeCount());
		for (final EdgeIndex ei : edge2index.values()) {
			coll.add(ei.edge);
		}
		return coll;
		// ObjectCollection<?> tmp = edge2index.values();
		// return (Collection<T>) tmp;
	}

	/**
	 * @return An independent collection of the edges of this graph ordered by name.
	 */
	@Nonnull
	public Collection<E> getEdgesOrdered() {
		return new ObjectAVLTreeSet<>(this.getEdges());
	}

	/**
	 * getEdgesArray.
	 */
	@Nullable
	@Override
	public Pair<LabeledNode> getEndpoints(@Nonnull E edge) {
		final EdgeIndex ei = edge2index.get(edge.getName());
		if (ei == null) {
			return null;
		}

		return new Pair<>(index2node.get(ei.rowAdj), index2node.get(ei.colAdj));
	}

	/**
	 * @return the name of the file that contains this graph.
	 */
	public File getFileName() {
		return inputFile;
	}

	/**
	 *
	 */
	@Override
	@Nonnull
	public ObjectList<E> getInEdges(@Nonnull LabeledNode vertex) {
		final int nodeIndex;
		if ((nodeIndex = nodeName2index.getInt(vertex.getName())) == Constants.INT_NULL) {
			return new ObjectArrayList<>();
		}

		final ObjectList<E> inEdges = new ObjectArrayList<>();
		E e;
		final int n = order;
		for (int i = n; --i >= 0; ) {
			e = adjacency[i][nodeIndex];
			if (e != null) {
				inEdges.add(e);
			}
		}
		return inEdges;
	}

	/**
	 * It is an optimization of {@link #getInEdges(LabeledNode)} that returns also the source node of the in-going
	 * edge.
	 *
	 * @param vertex the destination node
	 *
	 * @return the pair (edge, sourceNode)
	 */
	@Nonnull
	public ObjectList<ObjectObjectImmutablePair<E, LabeledNode>> getInEdgesAndNodes(@Nonnull LabeledNode vertex) {
		final int nodeIndex;
		if ((nodeIndex = nodeName2index.getInt(vertex.getName())) == Constants.INT_NULL) {
			return new ObjectArrayList<>();
		}
		E e;
		ObjectList<ObjectObjectImmutablePair<E, LabeledNode>> edgeNodeList = inEdgesCache.get(vertex);
		if (edgeNodeList != null) {
			return edgeNodeList;
		}
		edgeNodeList = new ObjectArrayList<>();
		final int n = order;
		for (int i = n; --i >= 0; ) {
			e = adjacency[i][nodeIndex];
			if (e != null) {
				edgeNodeList.add(new ObjectObjectImmutablePair<>(e, index2node.get(i)));
			}
		}
		inEdgesCache.put(vertex, edgeNodeList);
		return edgeNodeList;
	}

	/**
	 *
	 */
	@Override
	@Nonnull
	public ObjectList<E> getIncidentEdges(@Nonnull LabeledNode vertex) {
		final int index;
		final ObjectArrayList<E> coll = new ObjectArrayList<>();
		if ((index = nodeName2index.getInt(vertex.getName())) == Constants.INT_NULL) {
			return coll;
		}

		E e;
		for (int i = 0; i < order; i++) {
			e = adjacency[index][i];
			if (e != null) {
				coll.add(e);
			}
			if (i == index) {
				continue;
			}
			e = adjacency[i][index];
			if (e != null) {
				coll.add(e);
			}
		}
		return coll;
	}

	/**
	 * @return the list of edges containing Lower Case Labels (when type of network is CSTNU/CSTPSU). If there is no
	 * 	such edges, it returns an empty list.
	 */
	@Nonnull
	public ObjectList<BasicCSTNUEdge> getLowerLabeledEdges() {
		if (lowerCaseEdges == null) {
			lowerCaseEdges = new ObjectArrayList<>();
			if (type == NetworkType.CSTNU || type == NetworkType.CSTNPSU) {
				BasicCSTNUEdge edge;
				for (int i = 0; i < order; i++) {
					for (int j = 0; j < order; j++) {
						if ((edge = (BasicCSTNUEdge) adjacency[i][j]) != null && edge.lowerCaseValueSize() == 1 &&
						    edge.isContingentEdge()) {
							lowerCaseEdges.add(edge);
						}
					}
				}
			}
		}
		return lowerCaseEdges;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 *
	 */
	@Nullable
	@Override
	public Collection<LabeledNode> getNeighbors(@Nonnull LabeledNode vertex) {
		final int nodeIndex;
		if ((nodeIndex = nodeName2index.getInt(vertex.getName())) == Constants.INT_NULL) {
			return null;
		}
		final ObjectArraySet<LabeledNode> neighbors = new ObjectArraySet<>();
		for (int i = 0; i < order; i++) {
			if (adjacency[nodeIndex][i] != null) {
				neighbors.add(index2node.get(i));
			}
			if (adjacency[i][nodeIndex] != null) {
				neighbors.add(index2node.get(i));
			}
		}
		return neighbors;
	}

	/**
	 * Returns the node associated to the name.
	 *
	 * @param s a {@link java.lang.String} object.
	 *
	 * @return the node associated to the name if present, null otherwise.
	 */
	@Nullable
	public LabeledNode getNode(@Nonnull final String s) {
		if (s.isEmpty()) {
			return null;
		}
		return index2node.get(nodeName2index.getInt(s));
	}

	/**
	 * @return the nodeFactory
	 */
	public LabeledNodeSupplier getNodeFactory() {
		return nodeFactory;
	}

	/**
	 * @return return the set of node ordered w.r.t. the lexicographical order of their names.
	 *
	 * @see #getVertices()
	 */
	@Nonnull
	public Collection<LabeledNode> getNodesOrdered() {
		return new ObjectAVLTreeSet<>(this.getVertices());
	}

	/**
	 * @return the map of propositions and their observers (nodes). If there is no observer node, it returns an empty
	 * 	map.
	 *
	 * @throws IllegalStateException sanity check: if there are two observers for the same proposition. Such an error
	 *                               should never occur.
	 */
	@Nonnull
	public Char2ObjectMap<LabeledNode> getObservedAndObserver() throws IllegalStateException {
		if (proposition2Observer == null) {
			proposition2Observer = newProposition2NodeInstance();
			char proposition;
			for (final LabeledNode n : getVertices()) {
				if ((proposition = n.getPropositionObserved()) != Constants.UNKNOWN) {
					if (proposition2Observer.put(proposition, n) != null) {
						throw new IllegalStateException(
							"There is two observer nodes for the same proposition " + proposition);
					}
				}
			}
		}
		return proposition2Observer;
	}

	/**
	 * @param c the proposition
	 *
	 * @return the node that observes the proposition if it exists, null otherwise.
	 */
	@Nullable
	public LabeledNode getObserver(final char c) {
		final Char2ObjectMap<LabeledNode> observer = getObservedAndObserver();
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				TNGraph.LOG.finest("Proposition: " + c + "; observer: " + observer.get(c));
			}
		}
		return observer.get(c);

	}

	/**
	 * Be careful! The returned value is not a copy as the edges contained!
	 *
	 * @return the list of edges from observers to Z if Z is defined, empty set otherwise.
	 */
	@Nonnull
	public ObjectList<E> getObserver2ZEdges() {
		if (observer2Z == null) {
			buildObserver2ZEdgesSet();
		}
		assert observer2Z != null;
		return observer2Z;
	}

	/**
	 * @return the number of observers.
	 */
	public int getObserverCount() {
		return getObservers().size();
	}

	/**
	 * @return the set of observer time-points.
	 */
	@Nonnull
	public Collection<LabeledNode> getObservers() {
		if (proposition2Observer == null) {
			getObservedAndObserver();
		}
		assert proposition2Observer != null;
		return proposition2Observer.values();
	}

	/**
	 *
	 */
	@Override
	@Nonnull
	public ObjectList<E> getOutEdges(@Nonnull LabeledNode vertex) {
		final int nodeIndex;
		if ((nodeIndex = nodeName2index.getInt(vertex.getName())) == Constants.INT_NULL) {
			return new ObjectArrayList<>();
		}
		final ObjectList<E> outEdges = new ObjectArrayList<>();
		E e;
		final int n = order;
		for (int i = n; --i >= 0; ) {
			e = adjacency[nodeIndex][i];
			if (e != null) {
				outEdges.add(e);
			}
		}
		return outEdges;
	}

	/**
	 * It is an optimization of {@link #getOutEdges(LabeledNode)} that returns also the destination node of the outgoing
	 * edge.
	 *
	 * @param vertex the source node
	 *
	 * @return the pair (edge,destinationNode)
	 */
	@Nonnull
	public ObjectList<ObjectObjectImmutablePair<E, LabeledNode>> getOutEdgesAndNodes(@Nonnull LabeledNode vertex) {
		final int nodeIndex;
		if ((nodeIndex = nodeName2index.getInt(vertex.getName())) == Constants.INT_NULL) {
			return new ObjectArrayList<>();
		}
		E e;
		ObjectList<ObjectObjectImmutablePair<E, LabeledNode>> edgeNodeList = outEdgesCache.get(vertex);
		if (edgeNodeList != null) {
			return edgeNodeList;
		}
		edgeNodeList = new ObjectArrayList<>();
		final int n = order;
		for (int i = n; --i >= 0; ) {
			e = adjacency[nodeIndex][i];
			if (e != null) {
				edgeNodeList.add(new ObjectObjectImmutablePair<>(e, index2node.get(i)));
			}
		}
		outEdgesCache.put(vertex, edgeNodeList);
		return edgeNodeList;
	}

	/**
	 *
	 */
	@Override
	@Nonnull
	public Collection<LabeledNode> getPredecessors(@Nonnull LabeledNode vertex) {
		final ObjectArrayList<LabeledNode> predecessor = new ObjectArrayList<>();
		for (final E e : getInEdges(vertex)) {
			predecessor.add(getSource(e));
		}
		return predecessor;
	}

	/**
	 * getPropositions.
	 *
	 * @return the set of propositions of the graph.
	 */
	@Nonnull
	public CharSet getPropositions() {
		if (proposition2Observer == null) {
			getObservedAndObserver();
		}
		assert proposition2Observer != null;
		return proposition2Observer.keySet();
	}

	/**
	 *
	 */
	@Nullable
	@Override
	public LabeledNode getSource(@Nonnull E edge) {
		return getSource(edge.getName());
	}

	/**
	 * Wrapper of {@link #getSource(Edge)}
	 *
	 * @param edgeName a {@link java.lang.String} object.
	 *
	 * @return a {@link it.univr.di.cstnu.graph.LabeledNode} object.
	 */
	@Nullable
	public LabeledNode getSource(@Nonnull String edgeName) {
		final EdgeIndex ei = edge2index.get(edgeName);
		if (ei == null) {
			return null;
		}
		return index2node.get(ei.rowAdj);
	}

	/**
	 *
	 */
	@Override
	@Nonnull
	public Collection<LabeledNode> getSuccessors(@Nonnull LabeledNode vertex) {
		final ObjectArrayList<LabeledNode> successors = new ObjectArrayList<>();
		for (final E e : getOutEdges(vertex)) {
			successors.add(getDest(e));
		}
		return successors;
	}

	/**
	 * @param edgeName a desired name for an edge
	 *
	 * @return name with a possible suffix for making it a name of new edge without conflicts with name of already
	 * 	present edges.
	 */
	public String getUniqueEdgeName(@Nonnull String edgeName) {
		int i = 0;
		String s = edgeName;
		while (getEdge(s) != null) {
			s = edgeName + "_" + i++;
		}
		return s;
	}

	/**
	 * @return the type of network
	 */
	public NetworkType getType() {
		return type;
	}

	/**
	 * Sets the type of network.<br> Warning: this method was introduced for setting the PSTN type because it is not simple to determine it from the kind of
	 * edges. Don't use this method for other types because the constructor sets the type automatically. Change the type of network after its creation can make
	 * not readable many edge properties.
	 *
	 * @param type new type for the network.
	 */
	public void setType(NetworkType type) {
		if (type == null) {
			return;
		}
		this.type = type;
	}

	/**
	 * @return the set of edges containing Upper Case Label only for TNGraphs that contain BasicCSTNEdge or derived.
	 */
	public Set<BasicCSTNUEdge> getUpperLabeledEdges() {
		final ObjectArraySet<BasicCSTNUEdge> es1 = new ObjectArraySet<>();
		for (final E e : getEdges()) {
			final BasicCSTNUEdge e1 = ((BasicCSTNUEdge) e);
			if (e1.upperCaseValueSize() > 0) {
				es1.add(e1);
			}
		}
		return es1;
	}

	/**
	 *
	 */
	@Override
	public int getVertexCount() {
		return nodeName2index.size();
	}

	/**
	 *
	 */
	@SuppressWarnings("NP_NONNULL_RETURN_VIOLATION")
	@Override
	public @Nonnull Collection<LabeledNode> getVertices() {
		return index2node.values();
	}

	/**
	 * getVerticesArray.
	 *
	 * @return the array of vertices as an array ordered w.r.t the name of node in ascending order.
	 */
	@SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE", justification = "It is not important!")
	public @Nonnull LabeledNode[] getVerticesArray() {
		final LabeledNode[] nodes = getVertices().toArray(new LabeledNode[0]);
		Arrays.sort(nodes);
		return nodes;
	}

	/**
	 * @return the Z node
	 */
	public @Nullable LabeledNode getZ() {
		return Z;
	}

	/**
	 * Sets {@code z} as first node (Zero node) of the temporal constraint network.
	 *
	 * @param z the node to be set as Z node of the network. If z is null, then the Z information is nullified.
	 */
	@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "For efficiency reason, it includes an external mutable object.")
	public void setZ(@Nullable final LabeledNode z) {
		if (z == null) {
			Z = null;
			return;
		}
		if (getNode(z.getName()) == null) {
			addVertex(z);
		}
		Z = z;
	}

	/**
	 * Returns true if this graph has the same set of edges of g1.
	 *
	 * @param g1 a  object.
	 *
	 * @return true if this graph contains edges equal to g1 edges w.r.t. their name and their values. False, otherwise.
	 */
	public boolean hasSameEdgesOf(@Nonnull final TNGraph<E> g1) {
		return this.differentEdgesOf(g1).isEmpty();
	}

	/**
	 * @param g1 the given graph
	 *
	 * @return true if g1 has the set of vertices equals to the set of vertices of this.
	 */
	public boolean hasSameVerticesOf(@Nonnull final TNGraph<E> g1) {
		boolean sameNodes = true;
		final ObjectSet<LabeledNode> allNodes = new ObjectAVLTreeSet<>(getVertices());
		allNodes.addAll(g1.getVertices());
		LabeledNode nodeG, nodeG1;
		for (final LabeledNode n : allNodes) {
			nodeG = getNode(n.getName());
			nodeG1 = g1.getNode(n.getName());
			if (nodeG == null || nodeG1 == null || nodeG.propositionObserved != nodeG1.propositionObserved ||
			    !nodeG.getLabel().equals(nodeG1.getLabel())) {
				sameNodes = false;
			}
		}
		return sameNodes;
	}

	/**
	 *
	 */
	@Override
	public int inDegree(@Nonnull LabeledNode vertex) {
		final int nodeIndex;
		if ((nodeIndex = nodeName2index.getInt(vertex.getName())) == Constants.INT_NULL) {
			return Constants.INT_NULL;
		}
		final ObjectList<ObjectObjectImmutablePair<E, LabeledNode>> cache = inEdgesCache.get(vertex);
		if (cache != null) {
			return cache.size();
		}
		int count = 0;
		final int n = order;
		for (int i = n; --i >= 0; ) {
			if (adjacency[i][nodeIndex] != null) {
				count++;
			}
		}
		return count;
	}

	/**
	 *
	 */
	@Override
	public boolean isDest(@Nonnull LabeledNode vertex, @Nonnull E edge) {
		final int nodeIndex = nodeName2index.getInt(vertex.getName());
		if (nodeIndex == Constants.INT_NULL) {
			return false;
		}

		for (int i = 0; i < order; i++) {
			if (edge.equalsByName(adjacency[i][nodeIndex])) {
				return true;
			}
		}
		return false;
	}

//	/**
//	 * Since equals has been specialized, hashCode too.
//	 * TNGraphReader needs original hashCode() method, so I maintain the Object#equals.
//	 just the name.
//	 */
//	@Override
//	public int hashCode() {
//		return super.hashCode();
//	}

	/**
	 *
	 */
	@Override
	public boolean isSource(@Nonnull LabeledNode vertex, @Nonnull E edge) {
		final int nodeIndex = nodeName2index.getInt(vertex.getName());
		if (nodeIndex == Constants.INT_NULL) {
			return false;
		}

		for (int i = 0; i < order; i++) {
			if (edge.equalsByName(adjacency[nodeIndex][i])) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Creates a new edge proper for this graph using edgeName as a proposed name (it will be modified adding a suffix
	 * if there is already another edge with name edgeName) and setting the type to type.
	 *
	 * @param edgeName the proposed name for the edge.
	 * @param edgeType the type of the edge.
	 *
	 * @return a new edge (without adding it to the graph) of the proper class for this graph.
	 */
	public E makeNewEdge(@Nonnull final String edgeName, @Nonnull final ConstraintType edgeType) {
		final String s = getUniqueEdgeName(edgeName);
		final E e = edgeFactory.get(s);
		e.setConstraintType(edgeType);
		return e;
	}

	/**
	 *
	 */
	@Override
	public int outDegree(@Nonnull LabeledNode vertex) {
		final int nodeIndex;
		if ((nodeIndex = nodeName2index.getInt(vertex.getName())) == Constants.INT_NULL) {
			return Constants.INT_NULL;
		}
		final ObjectList<ObjectObjectImmutablePair<E, LabeledNode>> cache = outEdgesCache.get(vertex);
		if (cache != null) {
			return cache.size();
		}
		int count = 0;
		final int n = order;
		for (int i = n; --i >= 0; ) {
			if (adjacency[nodeIndex][i] != null) {
				count++;
			}
		}
		return count;
	}

	/**
	 *
	 */
	@SuppressWarnings("ChainOfInstanceofChecks")
	@Override
	public void propertyChange(@Nonnull PropertyChangeEvent pce) {
		final Object o = pce.getSource();
		final String property = pce.getPropertyName();
		final Object old = pce.getOldValue();
		if (o instanceof LabeledNode node) {
			if ("nodeName".equals(property)) {
				final String oldValue = (String) old;
				final int oldI = nodeName2index.getInt(oldValue);
				final int newI = nodeName2index.getInt(node.getName());
				if (newI != Constants.INT_NULL) {
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							LOG.finer("Values in nodeName2index: " + nodeName2index.toString());
						}
					}
					if (Debug.ON) {
						LOG.severe("It is not possible to rename node " + oldValue + " with " + node.getName() +
						           " because there is already a node with name " + node.getName());
					}
					node.name = oldValue;
					return;
				}
				if (nodeName2index.removeInt(oldValue) != oldI) {
					LOG.severe("It is not possible to remove the node " + oldValue);
				}
				nodeName2index.put(node.getName(), oldI);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer(
							"The nodeName2Index is updated. Removed old name " + oldValue + ". Add the new one: " +
							node + " at position " + oldI + "\nnodeName2index.size(): " + nodeName2index.size());
					}
				}
				node.setALabel(null);
				if (Z != null && oldValue.equals(Z.getName())) {
					setZ(null);
				}
				return;
			}
			if ("nodeProposition".equals(property)) {
				final char newP = node.propositionObserved;
				// it is complicated to rely on proposition2Observer because it can be erased for some reason.
				// So, the check is made checking all nodes.
				if (newP != Constants.UNKNOWN) {
					for (final LabeledNode n : getVertices()) {
						if (n != node && n.getPropositionObserved() == newP) {
							if (Debug.ON) {
								LOG.severe(
									"It is not possible to assign proposition " + newP + " to node " + node.getName() +
									" because there is already a node that observes the proposition: node " + n);
								node.propositionObserved = (Character) old;
							}
							return;
						}
					}
				}
				proposition2Observer = null;
				childrenOfObserver = null;
				observer2Z = null;
				return;
			}
			if ("nodeLabel".equals(property)) {
				childrenOfObserver = null;
			}
		} // LabeledNode

		if (o instanceof AbstractEdge edge) {
			if ("edgeName".equals(property)) {
				final String oldName = (String) old;
				final EdgeIndex oldI = edge2index.get(oldName);
				final EdgeIndex newI = edge2index.get(edge.getName());
				if (newI != null) {
					if (Debug.ON) {
						LOG.severe(
							"oldIndex: " + oldI + "\nnewIndex: " + newI + "\nIt is not possible to rename edge " +
							(oldName) + " with " + edge.getName() +
							" because there is already an edge with the same name. Rollback.");
					}
					edge.name = (String) old;
					return;
				}
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("Edge2index size before remove: " + edge2index.size());
					}
				}
				edge2index.remove(oldName);
				edge2index.put(edge.getName(), oldI);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("Edge2index size after add: " + edge2index.size());
						LOG.finer(
							"The edge2index is updated. Removed old name " + oldName + ". Add the new one: " + edge +
							" at position " + oldI);
					}
				}
				return;
			}
			if ("lowerLabel:remove".equals(property)) {
				final BasicCSTNUEdge e1 = (BasicCSTNUEdge) edge;
				lowerCaseEdges.remove(e1);
				return;
			}
			if ("lowerLabel:add".equals(property)) {
				final BasicCSTNUEdge e1 = (BasicCSTNUEdge) edge;
				if (lowerCaseEdges == null) {
					lowerCaseEdges = new ObjectArrayList<>();
				}
				lowerCaseEdges.add(e1);
				return;
			}
			if ("edgeType".equals(property)) {
				final ConstraintType oldT = (ConstraintType) (old);
				final ConstraintType newT = (ConstraintType) pce.getNewValue();
				if (oldT != newT && newT != ConstraintType.contingent) {
					lowerCaseEdges = null;
				}
			}
		}
	}

	/**
	 *
	 */
	@Override
	public boolean removeEdge(@Nonnull E edge) {
		return removeEdge(edge.getName());
	}

	/**
	 * Removes edge from this graph and clear all cache data structure that could contain the removed edge.
	 *
	 * @param edgeName a {@link java.lang.String} object.
	 *
	 * @return true if the edge was removed, false if the edge is not present.
	 */
	public boolean removeEdge(@Nonnull String edgeName) {
		final EdgeIndex ei;
		if ((ei = edge2index.get(edgeName)) == null) {
			return false;
		}

		adjacency[ei.rowAdj][ei.colAdj] = null;
		removeEdgeFromIndex(getEdge(edgeName));
		lowerCaseEdges = null;
		inEdgesCache.remove(index2node.get(ei.colAdj));
		outEdgesCache.remove(index2node.get(ei.rowAdj));
		return true;
	}

	/**
	 * Removes all empty edges in the graph.
	 *
	 * @return true if at least one edge was removed.
	 */
	public boolean removeEmptyEdges() {
		boolean removed = false;
		for (final E e : getEdges()) {
			if (e.isEmpty()) {
				removeEdge(e);
				removed = true;
			}
		}
		return removed;
	}

	/**
	 * Removes the given node and all edges having it as endpoint.
	 *
	 * @return true if the node was removed, false if the node was not present and, therefore, not removed.
	 */
	@Override
	public boolean removeVertex(@Nonnull LabeledNode removingNode) {
		/*
		 * I don't touch the adjacency size. I just move last col and row to the col and row that have to be removed.
		 */
		final int removingNodeIndex;
		if ((removingNodeIndex = nodeName2index.getInt(removingNode.getName())) == Constants.INT_NULL) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("I cannot remove vertex " + removingNode + " because it is null or its index is null.");
			}
			return false;
		}
		removingNode.removeObserver("nodeLabel", this);
		removingNode.removeObserver("nodeName", this);
		removingNode.removeObserver("nodeProposition", this);
		final int last = order - 1;
		// Move the removed node to the end of adjacency matrix and remove all its edges.
		if (removingNodeIndex == last) {
			for (int i = 0; i < last; i++) {
				// I just nullify last col and last row
				removeEdgeFromIndex(adjacency[i][last]);
				removeEdgeFromIndex(adjacency[last][i]);
				adjacency[i][last] = adjacency[last][i] = null;
			}
			removeEdgeFromIndex(adjacency[last][last]);
			adjacency[last][last] = null;
		} else {
			for (int i = 0; i < last; i++) {
				if (i == removingNodeIndex) {
					removeEdgeFromIndex(adjacency[i][i]);
					adjacency[i][i] = adjacency[last][last];
					updateEdgeInIndex(adjacency[i][i], i, i);
					removeEdgeFromIndex(adjacency[i][last]);
					removeEdgeFromIndex(adjacency[last][i]);
					adjacency[last][last] = adjacency[i][last] = adjacency[last][i] = null;
					continue;
				}
				removeEdgeFromIndex(adjacency[i][removingNodeIndex]);
				adjacency[i][removingNodeIndex] = adjacency[i][last];
				updateEdgeInIndex(adjacency[i][removingNodeIndex], i, removingNodeIndex);
				adjacency[i][last] = null;

				removeEdgeFromIndex(adjacency[removingNodeIndex][i]);
				adjacency[removingNodeIndex][i] = adjacency[last][i];
				updateEdgeInIndex(adjacency[removingNodeIndex][i], removingNodeIndex, i);
				adjacency[last][i] = null;
			}
		}
		// End of moving node to the end of adjacency matrix and to remove all its edges.
		index2node.remove(removingNodeIndex);
		nodeName2index.removeInt(removingNode.getName());
		if (removingNodeIndex != last) {
			final LabeledNode nodeMovedToRemovedNodePosition = index2node.get(last);
			index2node.remove(last);
			index2node.put(removingNodeIndex, nodeMovedToRemovedNodePosition);
			nodeName2index.put(nodeMovedToRemovedNodePosition.getName(), removingNodeIndex);
		}
		order = last;
		clearCache();

		return true;
	}

	/**
	 * Reverse (transpose) the current graph.
	 */
	public void reverse() {
		transpose();
	}

	/**
	 * @param graphName the name to set
	 */
	public void setName(@Nonnull final String graphName) {
		name = graphName;
	}

	/**
	 * Sets the potential of each node to {@code v}.
	 *
	 * @param v new potential value
	 */
	public void setAllPotential(final int v) {
		this.getVertices().forEach((node) -> node.setPotential(v));
	}

	/**
	 * @param file a {@link java.io.File} object.
	 */
	public void setInputFile(File file) {
		inputFile = file;
	}

	/**
	 * Takes from g all its internal structures.
	 * <br>
	 * This method is useful to copy the references of internal data structure of the given graph 'g' into the current.
	 * <br>
	 * After this method, this shares the internal structures with input g.
	 *
	 * @param g1 the graph to cannibalise.
	 */
	@SuppressWarnings("unchecked")
	public void takeFrom(@Nonnull final TNGraph<? extends E> g1) {
		final TNGraph<E> g = (TNGraph<E>) g1;
		adjacency = g.adjacency;
		aLabelAlphabet = g.aLabelAlphabet;
		childrenOfObserver = g.childrenOfObserver;
		edge2index = g.edge2index;
		edgeFactory = g.edgeFactory;
		index2node = g.index2node;
		inputFile = g.inputFile;
		lowerCaseEdges = g.lowerCaseEdges;
		name = g.name;
		nodeName2index = g.nodeName2index;
		observer2Z = g.observer2Z;
		order = g.order;
		proposition2Observer = g.proposition2Observer;
		Z = g.Z;
		type = g.type;
	}

	/**
	 *
	 */
	@Override
	public @Nonnull String toString() {
		final StringBuilder sb = new StringBuilder(
			"%TNGraph: " + ((name != null) ? name : (inputFile != null) ? inputFile.toString() : "no name")
			// + "\n%TNGraph<E> Syntax\n"
			// + "%LabeledNode: <name, label, [observed proposition], [>\n"
			// + "%T: <name, type, source node, destination node, L:{labeled values}, LL:{labeled lower-case values}, UL:{labeled upper-case values}>"
			+ "\n");
		sb.append("%Nodes:\n");

		for (final LabeledNode n : getVertices().stream().sorted().toList()) {
			sb.append(n.toString());
			sb.append("\n");
		}
		sb.append("%Edges:\n");
		for (final E e : getEdges().stream().sorted().toList()) {
			sb.append(Objects.requireNonNull(getSource(e))).append("--").append(e).append("-->")
				.append(Objects.requireNonNull(getDest(e)));
			sb.append("\n");
		}
		return sb.toString();
	}

	/**
	 * Transposes {@code this} inverting only the source/destination of each edge. All other attributes of an edge are
	 * not modified.
	 */
	public void transpose() {
		final int n = getVertexCount();
		for (int i = 1; i < n; i++) {
			for (int j = 0; j < i; j++) {
				final E eIJ = adjacency[i][j];
				final E eJI = adjacency[j][i];
				adjacency[i][j] = eJI;
				adjacency[j][i] = eIJ;
				updateEdgeInIndex(eIJ, j, i);
				updateEdgeInIndex(eJI, i, j);
			}
		}
		inEdgesCache.clear();
		outEdgesCache.clear();
	}

	/**
	 * builds this.observer2Z;
	 */
	private void buildObserver2ZEdgesSet() {
		observer2Z = new ObjectArrayList<>();
		if (Z == null) {
			return;
		}
		final Char2ObjectMap<LabeledNode> observers = getObservedAndObserver();
		for (final LabeledNode node : observers.values()) {
			final E e = findEdge(node, Z);
			if (e != null) {
				observer2Z.add(e);
			}
		}
	}

	/**
	 * @param ord the wanted order of the graph
	 *
	 * @return a bi-dimensional size x size vector for containing T elements.
	 */
	@SuppressWarnings("unchecked")
	private E[][] createAdjacency(int ord) {
		return (E[][]) Array.newInstance(edgeFactory.getEdgeImplClass(), ord, ord);
	}

	/**
	 * Removes input edge from {@code edge2index} and remove its observers.
	 *
	 * @param e input edge
	 */
	private void removeEdgeFromIndex(E e) {
		if (e == null) {
			return;
		} else {
			e.getName();
		}
		((AbstractEdge) e).pcs = new PropertyChangeSupport(e);
		// ((AbstractEdge) e).removeObserver("edgeType", this);//Don't activate this remover because it slows down
		// ((AbstractEdge) e).removeObserver("edgeName", this);//the DC checking a lot.
		edge2index.remove(e.getName());
	}

	/**
	 * @param e   edge
	 * @param row where the edge must be put
	 * @param col where the edge must be put
	 */
	private void updateEdgeInIndex(E e, int row, int col) {
		if (e == null) {
			return;
		} else {
			e.getName();
		}
		final EdgeIndex ei = edge2index.get(e.getName());
		ei.rowAdj = row;
		ei.colAdj = col;
	}
}
