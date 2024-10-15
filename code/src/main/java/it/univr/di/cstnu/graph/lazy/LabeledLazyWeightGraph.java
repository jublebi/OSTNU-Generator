// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * Bear in mind that ObjectRBTreeSet is usually a little bit faster than ObjectAVLTreeSet on set of medium size. In very large set size, ObjectAVLTreeSet
 * shine!
 * On small set, ArraySet is more efficient in time and space.
 */
package it.univr.di.cstnu.graph.lazy;

import edu.uci.ics.jung.graph.AbstractTypedGraph;
import edu.uci.ics.jung.graph.DirectedGraph;
import edu.uci.ics.jung.graph.util.EdgeType;
import edu.uci.ics.jung.graph.util.Pair;
import it.unimi.dsi.fastutil.chars.Char2ObjectArrayMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectMap;
import it.unimi.dsi.fastutil.chars.CharSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap.Entry;
import it.univr.di.Debug;
import it.univr.di.cstnu.graph.LabeledNode;
import it.univr.di.labeledvalue.*;
import it.univr.di.labeledvalue.lazy.LazyWeight;

import java.io.File;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class tries to represent dense graphs where nodes {@link it.univr.di.cstnu.graph.LabeledNode} and edges are {@link it.univr.di.cstnu.graph.lazy.LabeledLazyWeightEdge} implementing the interface
 * {@link edu.uci.ics.jung.graph.DirectedGraph} in order to allow the representation of graph by Jung library.
 *
 * @author posenato
 * @version $Id: $Id
 */
@SuppressWarnings("ALL")
public class LabeledLazyWeightGraph extends AbstractTypedGraph<LabeledNode, LabeledLazyWeightEdge>
		implements DirectedGraph<LabeledNode, LabeledLazyWeightEdge>, Observer {

	/**
	 * Represents the association of an edge with its position in the adjacency matrix of the graph.
	 * index with its corresponding node.
	 *
	 * @author posenato
	 */
	private class EdgeIndex {
		/**
		 * It is not possible to use the technique used for Node (extending LabeledIntNode class) because if I extended
		 * LabeledLazyWeightEdge, then edges are viewed as LabeledLazyWeightEdge and parameterized type T cannot be used as base class for
		 * extending.
		 */

		/**
		 *
		 */
		int colAdj = -1;
		/**
		 *
		 */
		LabeledLazyWeightEdge edge = null;
		/**
		 *
		 */
		int rowAdj = -1;

		/**
		 * @param e  none
		 * @param row none
		 * @param col none
		 */
		EdgeIndex(LabeledLazyWeightEdge e, int row, int col) {
			this.edge = e;
			this.rowAdj = row;
			this.colAdj = col;
		}

		@Override
		public String toString() {
			return String.format("%s->(%dX%d)", this.edge.getName(), this.rowAdj, this.colAdj);
		}
	}

	/**
	 *
	 */
	private static final Logger LOG = Logger.getLogger(LabeledLazyWeightGraph.class.getName());

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * @return an instance for childrenOfObserver field.
	 */
	private static final Object2ObjectMap<LabeledNode, Label> newChildrenObserverInstance() {
		return new Object2ObjectArrayMap<>();// in label `I` showed that for small map, ArrayMap is faster than
		// Object2ObjectRBTreeMap and
												// Object2ObjectAVLTreeMap.
	}

	/**
	 * @return an instance for propositionToNode field.
	 */
	private static final Char2ObjectMap<LabeledNode> newProposition2NodeInstance() {
		return new Char2ObjectArrayMap<>();// I verified that Char2ObjectArrayMap is faster than Openhash when proposition are limited, as in this application.
	}

	/**
	 * The graph is represented by its adjacency matrix.
	 */
	private LabeledLazyWeightEdge[][] adjacency;

	/**
	 * Children of observation nodes
	 */
	private Map<LabeledNode, Label> childrenOfObserver;

	/**
	 * In order to guarantee a fast mapping edge-->adjacency position, a map is maintained.
	 */
	private Object2ObjectMap<String, EdgeIndex> edge2index;

	/**
	 * Adjacency grow factor; It represents the multiplication factor to use for increasing the dimension of adjacency matrix. It has to be at least 1.5.
	 */
	float growFactor = 1.8f;

	/**
	 * In order to guarantee a fast mapping adjacency position-->node, a map is maintained.
	 */
	private Int2ObjectMap<LabeledNode> index2node;

	/**
	 * Alphabet for A-Label
	 */
	private ALabelAlphabet aLabelAlphabet;

	/**
	 * Name
	 */
	private String name;

	/**
	 * A possible file name containing this graph.
	 */
	private File fileName;

	/**
	 * In order to guarantee a fast mapping node-->adjacency position, a map is maintained.
	 */
	private Object2IntMap<String> nodeName2index;

	/**
	 * Current number of nodes;
	 */
	private int order;

	/**
	 * Map of propositions observed in the graph.
	 */
	private Char2ObjectMap<LabeledNode> proposition2Observer;

	/**
	 * List of edges from observers to Z
	 */
	private ObjectList<LabeledLazyWeightEdge> observer2Z;

	/**
	 * Zero node. In temporal constraint network such node is the first node to execute.
	 */
	private LabeledNode Z;

	/**
	 * <p>Constructor for LabeledLazyWeightGraph.</p>
	 */
	public LabeledLazyWeightGraph() {
		super(EdgeType.DIRECTED);
		this.order = 0;
		this.adjacency = createAdjacency(10);
		this.nodeName2index = new Object2IntOpenHashMap<>();
		this.nodeName2index.defaultReturnValue(Constants.INT_NULL);
		this.index2node = new Int2ObjectOpenHashMap<>();
		this.edge2index = new Object2ObjectAVLTreeMap<>(); // From fastutil javadoc:
		// In general, AVL trees have slightly slower updates but faster searches; however, on very large collections the smaller height may lead in fact to
		// faster updates, too.
		this.aLabelAlphabet = new ALabelAlphabet();
	}

	/**
	 * <p>Constructor for LabeledLazyWeightGraph.</p>
	 *
	 * @param alphabet Alphabet to use for naming Upper Case label
	 */
	public LabeledLazyWeightGraph(ALabelAlphabet alphabet) {
		this();
		if (alphabet != null)
			this.aLabelAlphabet = alphabet;
	}

	/**
	 * A constructor that copy a given graph g using copy constructor even for internal structures. If g is null, this new graph will be empty.
	 *
	 * @param g the graph to be cloned
	 */
	public LabeledLazyWeightGraph(final LabeledLazyWeightGraph g) {
		this();
		if (g == null)
			return;
		this.name = g.getName();
		this.aLabelAlphabet = g.aLabelAlphabet;
		this.fileName = g.fileName;

		// clone all nodes
		LabeledNode vNew;
		for (final LabeledNode v : g.getVertices()) {
			vNew = new LabeledNode(v);
			this.addVertex(vNew);
			if (v.equalsByName(g.Z)) {
				this.Z = vNew;
			}
		}
		vNew = g.getZ();
		if (vNew != null) {
			this.setZ(this.getNode(vNew.getName()));
		}

		// clone all edges giving the right new endpoints corresponding the old ones.
		LabeledLazyWeightEdge eNew;
		for (final LabeledLazyWeightEdge e : g.getEdges()) {
			eNew = new LabeledLazyWeightEdge(e);
			addEdge(eNew, g.getSource(e).getName(), g.getDest(e).getName());
		}
	}

	/**
	 * Constructor for LabeledLazyWeightGraph.
	 *
	 * @param name1 a name for the graph
	 */
	public LabeledLazyWeightGraph(final String name1) {
		this();
		this.name = name1;
	}

	/**
	 * Constructor for LabeledLazyWeightGraph.
	 *
	 * @param name1 a name for the graph
	 * @param alphabet alfabet for upper case letter used to label values in the edges.
	 */
	public LabeledLazyWeightGraph(final String name1, ALabelAlphabet alphabet) {
		this();
		this.name = name1;
		this.aLabelAlphabet = alphabet;
	}

	/**
	 * Add child to obs.
	 * It is user responsibility to assure that 'child' is a children in CSTN sense of 'obs'.
	 * No validity check is made by the method.
	 *
	 * @param obs a {@link it.univr.di.cstnu.graph.LabeledNode} object.
	 * @param child a char.
	 */
	public void addChildToObserverNode(LabeledNode obs, char child) {
		if (this.childrenOfObserver == null)
			this.childrenOfObserver = newChildrenObserverInstance();
		Label children = this.childrenOfObserver.get(obs);
		if (children == null) {
			children = Label.emptyLabel;
		}
		children = children.conjunction(child, Literal.STRAIGHT);
		this.childrenOfObserver.put(obs, children);
	}

	/**
	 * Optimized method for adding edge. It exploits internal structure of the class.
	 *
	 * @param e a {@link it.univr.di.cstnu.graph.lazy.LabeledLazyWeightEdge} object.
	 * @param v1Name a {@link java.lang.String} object.
	 * @param v2Name a {@link java.lang.String} object.
	 * @return true if edge has been added.
	 */
	public boolean addEdge(LabeledLazyWeightEdge e, final String v1Name, final String v2Name) {
		if (e == null || v1Name == null || v2Name == null)
			return false;

		if (this.edge2index.containsKey(e.getName())) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.WARNING)) {
					LOG.warning("An edge with name " + e.getName() + " already exists. The new edge is not added.");
				}
			}
			return false;
		}
		int sourceIndex = this.nodeName2index.getInt(v1Name);
		if (sourceIndex == Constants.INT_NULL) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.info("Source node during adding edge with name " + e.getName() + " is null. The new edge is not added.");
				}
			}
			return false;
		}

		int destIndex = this.nodeName2index.getInt(v2Name);
		if (destIndex == Constants.INT_NULL) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.info("Destination node during adding edge with name " + e.getName() + " is null. The new edge is not added.");
				}
			}
			return false;
		}

		LabeledLazyWeightEdge old = this.adjacency[sourceIndex][destIndex];
		if (old != null) {
			this.edge2index.remove(old.getName());
		}
		this.adjacency[sourceIndex][destIndex] = e;

		this.edge2index.put(e.getName(), new EdgeIndex(e, sourceIndex, destIndex));
//		e.addObserver(this);
		//FIXME
		return true;
	}

	/*
	 * It is necessary to copy the general method here because of generics T
	 */
	/** {@inheritDoc} */
	@Override
	public boolean addEdge(LabeledLazyWeightEdge e, final LabeledNode v1, final LabeledNode v2) {
		if (e == null || v1 == null || v2 == null)
			return false;
		if (!this.nodeName2index.containsKey(v1.getName())) {
			addVertex(v1);
		}
		if (!this.nodeName2index.containsKey(v2.getName())) {
			addVertex(v2);
		}
		return addEdge(e, v1.getName(), v2.getName());
	}

	/*
	 * It is necessary to copy the general method here because otherwise it calls the general addEdge(e, new Pair<LabeledNode>(v1, v2), edge_type)!!!
	 * @see edu.uci.ics.jung.graph.AbstractGraph#addEdge(java.lang.Object, java.lang.Object, java.lang.Object, edu.uci.ics.jung.graph.util.EdgeType)
	 */
	/** {@inheritDoc} */
	@Override
	public boolean addEdge(LabeledLazyWeightEdge e, final LabeledNode v1, final LabeledNode v2, final EdgeType edge_type1) {
		if (e == null || v1 == null || v2 == null)
			return false;

		return addEdge(e, v1.getName(), v2.getName());
	}

	/** {@inheritDoc} */
	@Override
	public boolean addEdge(LabeledLazyWeightEdge edge, Pair<? extends LabeledNode> endpoints, EdgeType edgeType) {
		if (edge == null || endpoints == null)
			return false;
		return this.addEdge(edge, endpoints.getFirst(), endpoints.getSecond());
	}

	/** {@inheritDoc} */
	@Override
	public boolean addVertex(LabeledNode vertex) {
		if (vertex == null || this.nodeName2index.containsKey(vertex.getName())) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.info("The new vertex for adding is null or is already present.");
				}
			}
			return false;
		}

		int currentSize = this.adjacency.length;
		if (currentSize == this.order) {
			int newSize = (int) (currentSize * this.growFactor);
			LabeledLazyWeightEdge[][] newAdjancency = createAdjacency(newSize);
			for (int i = currentSize; i-- != 0;) {
				for (int j = currentSize; j-- != 0;)
					newAdjancency[i][j] = this.adjacency[i][j];
			}
			this.adjacency = newAdjancency;
		}
		// now it is possible to add node in position 'order'
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Adding node " + vertex);
			}
		}
		this.nodeName2index.put(vertex.getName(), this.order);
		this.index2node.put(this.order, vertex);
		this.order++;
		clearCache();
//		vertex.addObserver(this);
//		FIXME
		return true;
	}

	/**
	 * builds this.observer2Z;
	 */
	private void buildObserver2ZEdgesSet() {
		this.observer2Z = new ObjectArrayList<>();
		if (this.Z == null)
			return;
		Char2ObjectMap<LabeledNode> observers = getObservedAndObserver();
		for (final LabeledNode node : observers.values()) {
			LabeledLazyWeightEdge e = this.findEdge(node, this.Z);
			if (e != null)
				this.observer2Z.add(e);
		}
	}

	/**
	 * clear all internal structures.
	 * The graph will be empty.
	 */
	public void clear() {
		clear(10);
	}

	/**
	 * Clear all internal structures.
	 * The graph will be erased.
	 *
	 * @param initialAdjSize initial number of vertices.
	 */
	public void clear(int initialAdjSize) {
		this.order = 0;// addVertex adjusts the value
		this.adjacency = createAdjacency(initialAdjSize);
		this.nodeName2index.clear();
		this.index2node.clear();
		this.edge2index.clear();
		this.clearCache();
	}

	/**
	 * Clear all internal caches.
	 * <p>
	 * Caches are automatically created during any modification or query about the graph structure.
	 */
	public void clearCache() {
		this.proposition2Observer = null;
		this.childrenOfObserver = null;
		this.observer2Z = null;
	}

	/** {@inheritDoc} */
	@Override
	public boolean containsEdge(LabeledLazyWeightEdge edge) {
		if (edge == null || edge.getName() == null)
			return false;
		return this.edge2index.containsKey(edge.getName());
	}

	/** {@inheritDoc} */
	@Override
	public boolean containsVertex(LabeledNode vertex) {
		if (vertex == null || vertex.getName() == null)
			return false;
		return this.nodeName2index.containsKey(vertex.getName());
	}

	/**
	 * Defensive copy of all internal structures of g into this.<br>
	 * This method is useful to copy a graph into the current without modifying the reference to the current. 'g' internal structures are copied as they are.
	 *
	 * @param g the graph to copy.
	 */
	public void copy(final LabeledLazyWeightGraph g) {
		this.name = g.getName();
		this.order = 0;// addVertex adjusts the value
		this.growFactor = g.growFactor;
		this.adjacency = createAdjacency(g.getVertexCount());
		this.nodeName2index.clear();
		this.index2node.clear();
		this.edge2index.clear();
		this.clearCache();
		// Clone all nodes
		LabeledNode vNew;
		for (final LabeledNode v : g.getVertices()) {
			vNew = new LabeledNode(v);
			this.addVertex(vNew);
			if (v.equalsByName(g.Z)) {
				this.Z = vNew;
			}
		}

		// Clone all edges giving the right new endpoints corresponding the old ones.
		for (final LabeledLazyWeightEdge e : g.getEdges()) {
			addEdge(new LabeledLazyWeightEdge(e), g.getSource(e).getName(), g.getDest(e).getName());
		}
	}

	/**
	 * Makes a copy as {@link #copy(LabeledLazyWeightGraph)} removing all possible redundant labeled values in the given graph.
	 *
	 * @param g a LabeledLazyWeightGraph object.
	 */
	public void copyCleaningRedundantLabels(LabeledLazyWeightGraph g) {
		this.name = g.getName();
		this.order = 0;// addVertex adjusts the value
		this.adjacency = createAdjacency(g.getVertexCount());
		this.nodeName2index.clear();
		this.index2node.clear();
		this.edge2index.clear();
		this.clearCache();
		// clone all nodes
		LabeledNode vNew;
		for (final LabeledNode v : g.getVertices()) {
			vNew = new LabeledNode(v);
			this.addVertex(vNew);
			if (v.equalsByName(g.Z)) {
				this.Z = vNew;
			}
		}

		// clone all edges giving the right new endpoints corresponding the old ones.
		LabeledLazyWeightEdge eNew;
		LazyWeight value;
		Label label;
		for (final LabeledLazyWeightEdge e : g.getEdges()) {
			eNew = new LabeledLazyWeightEdge();
			eNew.setName(e.getName());
			eNew.setConstraintType(e.getConstraintType());
			for (Entry<Label, LazyWeight> entry : e.getLabeledValueSet()) {
				value = entry.getValue();
				if (value == null)
					continue;
				label = entry.getKey();
				if (label.containsUnknown())
					continue;
				eNew.mergeLabeledValue(entry.getKey(), value);
			}
			// for (ALabel alabel : e.getUpperCaseValueMap().keySet()) {
			// LabeledIntTreeMap labeledValues = e.getUpperCaseValueMap().get(alabel);
			// for (Object2IntMap.Entry<Label> entry1 : labeledValues.entrySet()) {
			// eNew.mergeUpperCaseValue(entry1.getKey(), alabel, entry1.getIntValue());
			// }
			// }
			// // lower case value
			// eNew.setLowerCaseValue(e.getLowerCaseValue());

			addEdge(eNew, g.getSource(e).getName(), g.getDest(e).getName());
		}
	}

	/**
	 * @param size
	 * @return a bi-dimensional size x size vector for containing T elements.
	 */
	private static LabeledLazyWeightEdge[][] createAdjacency(int size) {
		return (LabeledLazyWeightEdge[][]) Array.newInstance(LabeledLazyWeightEdge.class, size, size);
	}

	/**
	 * {@inheritDoc} Equals based on equals of edges and vertices.
	 */
	@Override
	public boolean equals(final Object o) {
		if (this == o)
			return true;
		if (!(o instanceof final LabeledLazyWeightGraph g1))
			return false;
		return g1.getEdges().equals(this.getEdges()) && g1.getVertices().equals(this.getVertices());
	}

	/** {@inheritDoc} */
	@Override
	public LabeledLazyWeightEdge findEdge(LabeledNode s, LabeledNode d) {
		if (s == null || s.getName() == null || d == null || d.getName() == null)
			return null;
		return findEdge(s.getName(), d.getName());
	}

	/**
	 * Find the edge given the name of source node and destination one.
	 *
	 * @param s a {@link java.lang.String} object.
	 * @param d a {@link java.lang.String} object.
	 * @return null if any parameter is null or there not exists at least one of two nodes or the edge does not exist.
	 */
	public LabeledLazyWeightEdge findEdge(String s, String d) {
		if (s == null || d == null)
			return null;
		int sourceNI = this.nodeName2index.getInt(s);
		if (sourceNI == Constants.INT_NULL)
			return null;
		int destNI = this.nodeName2index.getInt(d);
		if (destNI == Constants.INT_NULL)
			return null;
		return this.adjacency[sourceNI][destNI];
	}

	/**
	 * <p>Getter for the field <code>aLabelAlphabet</code>.</p>
	 *
	 * @return the aLabelAlphabet
	 */
	public ALabelAlphabet getALabelAlphabet() {
		return this.aLabelAlphabet;
	}

	/**
	 * Given an observation node <code>obs</code> that observes the proposition 'p', its 'children' are all
	 * observation nodes, Q, for which 'p' appears in the
	 * label of node Q.
	 * <p>
	 * This method returns the set of children of a given node as a <b>label of straight propositions</b> associated to the children instead of a <b>set of
	 * children nodes</b>.
	 *
	 * @param obs a {@link it.univr.di.cstnu.graph.LabeledNode} object.
	 * @return the set of children of observation node <code>obs</code>, null if there is no children.
	 */
	public Label getChildrenOf(LabeledNode obs) {
		if (obs == null)
			return null;

		// The soundness of this method is based on the property that the observed proposition of an observation node is represented as a straight literal.
		if (this.childrenOfObserver == null) {
			// Build the cache map of childrenOfObserver
			this.childrenOfObserver = newChildrenObserverInstance();

			for (Char2ObjectMap.Entry<LabeledNode> entryObservedObserverNode : this.getObservedAndObserver().char2ObjectEntrySet()) {
				char observedProposition = entryObservedObserverNode.getCharKey();
				LabeledNode observator = entryObservedObserverNode.getValue();
				Label observatorLabel = observator.getLabel();
				for (char propInObsLabel : observatorLabel.getPropositions()) {
					LabeledNode father = this.getObserver(propInObsLabel);// for the well property, father must exist!
					Label children = this.childrenOfObserver.get(father);
					if (children == null) {
						children = Label.emptyLabel;
					}
					children = children.conjunction(observedProposition, Literal.STRAIGHT);
					this.childrenOfObserver.put(father, children);
				}
			}
		}
		return this.childrenOfObserver.get(obs);
	}

	/**
	 * <p>getContingentCount.</p>
	 *
	 * @return the number of contingents
	 */
	@SuppressWarnings("static-method")
	public int getContingentCount() {
		return 0;
		// int c = 0;
		// for (LabeledLazyWeightEdge e : this.getEdges()) {
		// if (e.isContingentEdge())
		// c++;
		// }
		// return c / 2;
	}

	/** {@inheritDoc} */
	@Override
	public LabeledNode getDest(LabeledLazyWeightEdge directedEdge) {
		EdgeIndex ei = this.edge2index.get(directedEdge.getName());
		if (ei == null)
			return null;

		return this.index2node.get(ei.colAdj);
	}

	/**
	 * Returns the edge associated to the name.
	 *
	 * @param s a {@link java.lang.String} object.
	 * @return the edge associated to the name.
	 */
	public LabeledLazyWeightEdge getEdge(final String s) {
		if ((s == null) || s.isEmpty())
			return null;
		EdgeIndex ei = this.edge2index.get(s);
		if (ei == null)
			return null;
		return ei.edge;
	}

	/** {@inheritDoc} */
	@Override
	public int getEdgeCount() {
		return this.edge2index.size();
	}

	// /**
	// * @return the edgeFactory
	// */
	// public LabeledLazyWeightEdgeSupplier getEdgeFactory() {
	// return this.edgeFactory;
	// }

	/** {@inheritDoc} */
	@Override
	public Collection<LabeledLazyWeightEdge> getEdges() {
		ObjectArrayList<LabeledLazyWeightEdge> coll = new ObjectArrayList<>();
		for (EdgeIndex ei : this.edge2index.values()) {
			coll.add(ei.edge);
		}
		return coll;
		// ObjectCollection<?> tmp = edge2index.values();
		// return (Collection<T>) tmp;
	}

	/**
	 * getEdgesArray.
	 *
	 * @return the set of edges as an array.
	 */
	public LabeledLazyWeightEdge[] getEdgesArray() {
		final LabeledLazyWeightEdge[] edgesA = this.getEdges().toArray(new LabeledLazyWeightEdge[this.getEdgeCount()]);
		return edgesA;
	}

	/**
	 * {@inheritDoc}
	 *
	 * getEdgesArray.
	 * NON FUNZIONA... è come scrivere LabeledLazyWeightEdge[] getEdgesArray() {
	 */

	@Override
	public Pair<LabeledNode> getEndpoints(LabeledLazyWeightEdge edge) {
		if (edge == null)
			return null;
		EdgeIndex ei = this.edge2index.get(edge.getName());
		if (ei == null)
			return null;

		return new Pair<>(this.index2node.get(ei.rowAdj), this.index2node.get(ei.colAdj));
	}

	/**
	 * <p>Getter for the field <code>fileName</code>.</p>
	 *
	 * @return the name of the file that contains this graph.
	 */
	public File getFileName() {
		return this.fileName;
	}

	/** {@inheritDoc} */
	@Override
	public Collection<LabeledLazyWeightEdge> getIncidentEdges(LabeledNode vertex) {
		int index;
		ObjectArrayList<LabeledLazyWeightEdge> coll = new ObjectArrayList<>();
		if (vertex == null || (index = this.nodeName2index.getInt(vertex.getName())) == Constants.INT_NULL)
			return coll;

		LabeledLazyWeightEdge e;
		for (int i = 0; i < this.order; i++) {
			e = this.adjacency[index][i];
			if (e != null)
				coll.add(e);
			if (i == index)
				continue;
			e = this.adjacency[i][index];
			if (e != null)
				coll.add(e);
		}
		return coll;

	}

	/** {@inheritDoc} */
	@Override
	public Collection<LabeledLazyWeightEdge> getInEdges(LabeledNode vertex) {
		int nodeIndex;
		ObjectArrayList<LabeledLazyWeightEdge> inEdges = new ObjectArrayList<>();
		if (vertex == null || (nodeIndex = this.nodeName2index.getInt(vertex.getName())) == Constants.INT_NULL)
			return inEdges;
		LabeledLazyWeightEdge e;
		for (int i = 0; i < this.order; i++) {
			e = this.adjacency[i][nodeIndex];
			if (e != null)
				inEdges.add(e);
		}
		return inEdges;
	}

	// /**
	// * getLowerLabeledEdges.
	// *
	// * @return the set of edges containing Lower Case Labels!
	// */
	// public ObjectList<LabeledLazyWeightEdge> getLowerLabeledEdges() {
	// if (this.lowerCaseEdges == null) {
	// this.lowerCaseEdges = new ObjectArrayList<>();
	// LabeledLazyWeightEdge edge;
	// for (int i = 0; i < this.order; i++) {
	// for (int j = 0; j < this.order; j++) {
	// if ((edge = this.adjacency[i][j]) != null && !edge.getLowerCaseValue().isEmpty()) {
	// this.lowerCaseEdges.add(edge);
	// }
	// }
	// }
	// }
	// return this.lowerCaseEdges;
	// }

	/**
	 * Getter for the field <code>name</code>.
	 *
	 * @return the name
	 */
	public String getName() {
		return this.name;
	}

	/** {@inheritDoc} */
	@Override
	public Collection<LabeledNode> getNeighbors(LabeledNode vertex) {
		int nodeIndex;
		if (vertex == null || (nodeIndex = this.nodeName2index.getInt(vertex.getName())) == Constants.INT_NULL)
			return null;
		ObjectArraySet<LabeledNode> neighbors = new ObjectArraySet<>();
		for (int i = 0; i < this.order; i++) {
			if (this.adjacency[nodeIndex][i] != null)
				neighbors.add(this.index2node.get(i));
			if (this.adjacency[i][nodeIndex] != null)
				neighbors.add(this.index2node.get(i));
		}
		return neighbors;
	}

	/**
	 * Returns the node associated to the name.
	 *
	 * @param s a {@link java.lang.String} object.
	 * @return the node associated to the name if present, null otherwise.
	 */
	public LabeledNode getNode(final String s) {
		if ((s == null) || s.isEmpty())
			return null;
		return this.index2node.get(this.nodeName2index.getInt(s));
	}

	/**
	 * getNodes.
	 *
	 * @return return the set of node ordered w.r.t. the lexicographical order of their names.
	 */
	public Collection<LabeledNode> getNodes() {
		return this.getVertices();
	}

	/**
	 * <p>getObservedAndObserver.</p>
	 *
	 * @return the map of propositions and their observers (nodes). If there is no observer node, it returns an empty map. The key is the literal observed.
	 */
	public Char2ObjectMap<LabeledNode> getObservedAndObserver() {
		if (this.proposition2Observer == null) {
			this.proposition2Observer = newProposition2NodeInstance();
			char proposition;
			for (final LabeledNode n : getVertices()) {
				if ((proposition = n.getPropositionObserved()) != Constants.UNKNOWN) {
					this.proposition2Observer.put(proposition, n);
				}
			}
		}
		return this.proposition2Observer;
	}

	/**
	 * <p>getObserver.</p>
	 *
	 * @param c the request proposition
	 * @return the node that observes the proposition l if it exists, null otherwise.
	 */
	public LabeledNode getObserver(final char c) {
		final Char2ObjectMap<LabeledNode> observer = this.getObservedAndObserver();
		if (observer == null)
			return null;

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST))
				LabeledLazyWeightGraph.LOG.finest("Propositione=" + c + "; observer=" + observer);
		}
		return observer.get(c);
	}

	/**
	 * Be careful! The returned value is not a copy as the edges contained!
	 *
	 * @return the set of edges from observers to Z if Z is defined, empty set otherwise.
	 */
	public ObjectList<LabeledLazyWeightEdge> getObserver2ZEdges() {
		if (this.observer2Z == null) {
			this.buildObserver2ZEdgesSet();
		}
		return this.observer2Z;
	}

	/**
	 * <p>getObserverCount.</p>
	 *
	 * @return the number of observers.
	 */
	public int getObserverCount() {
		return this.getObservers().size();
	}

	/**
	 * <p>getObservers.</p>
	 *
	 * @return the set of observator time-points.
	 */
	public Collection<LabeledNode> getObservers() {
		if (this.proposition2Observer == null) {
			getObservedAndObserver();
		}
		return this.proposition2Observer.values();
	}

	/** {@inheritDoc} */
	@Override
	public Collection<LabeledLazyWeightEdge> getOutEdges(LabeledNode vertex) {
		int nodeIndex;
		ObjectArrayList<LabeledLazyWeightEdge> outEdges = new ObjectArrayList<>();
		if (vertex == null || (nodeIndex = this.nodeName2index.getInt(vertex.getName())) == Constants.INT_NULL)
			return outEdges;
		LabeledLazyWeightEdge e;
		// for (int i = 0; i < order; i++) {
		for (int i = this.order; --i >= 0;) {
			e = this.adjacency[nodeIndex][i];
			if (e != null)
				outEdges.add(e);
		}
		return outEdges;
	}

	/** {@inheritDoc} */
	@Override
	public Collection<LabeledNode> getPredecessors(LabeledNode vertex) {
		throw new UnsupportedOperationException("The Javadoc is not clear!");
	}

	/**
	 * getPropositions.
	 *
	 * @return the set of propositions of the graph.
	 */
	public CharSet getPropositions() {
		if (this.proposition2Observer == null) {
			this.getObservedAndObserver();
		}
		return this.proposition2Observer.keySet();
	}

	/** {@inheritDoc} */
	@Override
	public LabeledNode getSource(LabeledLazyWeightEdge edge) {
		if (edge == null)
			return null;

		EdgeIndex ei = this.edge2index.get(edge.getName());
		if (ei == null)
			return null;

		return this.index2node.get(ei.rowAdj);
	}

	/** {@inheritDoc} */
	@Override
	public Collection<LabeledNode> getSuccessors(LabeledNode vertex) {
		ObjectArrayList<LabeledNode> successors = new ObjectArrayList<>();
		for (LabeledLazyWeightEdge e : this.getOutEdges(vertex)) {
			successors.add(this.getDest(e));
		}
		return successors;
	}

	// /**
	// * @return the set of edges containing Upper Case Label.
	// */
	// public Set<LabeledLazyWeightEdge> getUpperLabeledEdges() {
	// final ObjectArraySet<LabeledLazyWeightEdge> es1 = new ObjectArraySet<>();
	// for (final LabeledLazyWeightEdge e : this.getEdges())
	// if (e.upperCaseValueSize() > 0) {
	// es1.add(e);
	// }
	// return es1;
	// }

	/** {@inheritDoc} */
	@Override
	public int getVertexCount() {
		return this.nodeName2index.size();
	}

	/** {@inheritDoc} */
	@Override
	public Collection<LabeledNode> getVertices() {
		return this.index2node.values();
	}

	/**
	 * getVerticesArray.
	 *
	 * @return the set of vertices as an array ordered w.r.t the name of node in ascending order.
	 */
	public LabeledNode[] getVerticesArray() {
		final LabeledNode[] nodes = this.getVertices().toArray(new LabeledNode[0]); // At pag. 248 of Effective Java, author shows that it is better to not
																					// pre-allocate.
		return nodes;
	}

	/**
	 * <p>getZ.</p>
	 *
	 * @return the Z node
	 */
	public LabeledNode getZ() {
		return this.Z;
	}

	/**
	 * {@inheritDoc} Since equals has been specialized, hashCode too.
	 */
	@Override
	public int hashCode() {
		return this.getEdges().hashCode() + 31 * this.getVertices().hashCode();
	}


	/**
	 * hasSameEdgesOf.
	 *
	 * @param g1 a LabeledLazyWeightGraph object.
	 * @return true if this graph contains edges equal to g1 edges. Equals is checked using method {@link #equals(Object)}. False otherwise.
	 */
	public boolean hasSameEdgesOf(final LabeledLazyWeightGraph g1) {
		if (g1 == null)
			return false;
		final StringBuffer sb = new StringBuffer("Different edges:");
		final String currentName = this.getName();
		final String g1name = g1.getName();

		boolean sameEdges = true;
		ObjectSet<LabeledLazyWeightEdge> allEdges = new ObjectAVLTreeSet<>(getEdges());
		allEdges.addAll(g1.getEdges());
		LabeledLazyWeightEdge eg, eg1;
		for (LabeledLazyWeightEdge e : allEdges) {
			eg = getEdge(e.getName());
			eg1 = g1.getEdge(e.getName());
			if (eg == null || !eg.equalsAllLabeledValues(eg1)) {
				sb.append('\n').append(currentName).append(":\t").append(eg).append("\n").append(g1name).append(":\t").append(eg1);
				sameEdges = false;// i want to log all differences!!!
			}
		}

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE))
				LabeledLazyWeightGraph.LOG.log(Level.FINE, sb.toString());
		}
		return sameEdges;
	}

	/** {@inheritDoc} */
	@Override
	public boolean isDest(LabeledNode vertex, LabeledLazyWeightEdge edge) {
		if (vertex == null || edge == null)
			return false;
		int nodeIndex = this.nodeName2index.getInt(vertex.getName());
		if (nodeIndex == Constants.INT_NULL)
			return false;

		for (int i = 0; i < this.order; i++) {
			if (edge.equalsByName(this.adjacency[i][nodeIndex]))
				return true;
		}
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public boolean isSource(LabeledNode vertex, LabeledLazyWeightEdge edge) {
		if (vertex == null || edge == null)
			return false;
		int nodeIndex = this.nodeName2index.getInt(vertex.getName());
		if (nodeIndex == Constants.INT_NULL)
			return false;

		for (int i = 0; i < this.order; i++) {
			if (edge.equalsByName(this.adjacency[nodeIndex][i]))
				return true;
		}
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public boolean removeEdge(LabeledLazyWeightEdge edge) {
		EdgeIndex ei;
		if (edge == null || (ei = this.edge2index.get(edge.getName())) == null)
			return false;

		this.adjacency[ei.rowAdj][ei.colAdj] = null;
		this.edge2index.remove(ei.edge.getName());
		// this.lowerCaseEdges = null;
		return true;
	}

	/**
	 * Reverse (transpose) the current graph.
	 *
	 * @return true if the operation was successful.
	 */
	public boolean reverse() {
		int n = this.getVertexCount();
		LabeledLazyWeightEdge swap;
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < i; j++) {
				swap = this.adjacency[i][j];
				this.adjacency[i][j] = this.adjacency[j][i];
				this.adjacency[j][i] = swap;
				if (swap != null)
					this.edge2index.put(swap.getName(), new EdgeIndex(swap, j, i));
				if ((swap = this.adjacency[i][j]) != null)
					this.edge2index.put(swap.getName(), new EdgeIndex(swap, i, j));
			}
		}
		return true;
	}

	/**
	 * @param e
	 */
	private void removeEdgeFromIndex(LabeledLazyWeightEdge e) {
		if (e == null || e.getName() == null)
			return;
		this.edge2index.remove(e.getName());
	}

	/** {@inheritDoc} */
	@Override
	public boolean removeVertex(LabeledNode removingNode) {
		/**
		 * I don't touch the adjacency size. I just move last col and row to the col and row that have to be removed.
		 */
		int removingNodeIndex;
		if (removingNode == null || (removingNodeIndex = this.nodeName2index.getInt(removingNode.getName())) == Constants.INT_NULL) {
			LOG.fine("I cannot remove vertex " + removingNode + " because it is null or its index is null.");
			return false;
		}

		int last = this.order - 1;
		// Start to move node to remove at the end of adjacency matrix and to remove all its edges.
		if (removingNodeIndex == last) {
			for (int i = 0; i < last; i++) {
				// I just nullify last col and last row
				removeEdgeFromIndex(this.adjacency[i][last]);
				removeEdgeFromIndex(this.adjacency[last][i]);
				this.adjacency[i][last] = this.adjacency[last][i] = null;
			}
			removeEdgeFromIndex(this.adjacency[last][last]);
			this.adjacency[last][last] = null;
		} else {
			for (int i = 0; i < last; i++) {
				if (i == removingNodeIndex) {
					removeEdgeFromIndex(this.adjacency[i][i]);
					this.adjacency[i][i] = this.adjacency[last][last];
					updateEdgeInIndex(this.adjacency[i][i], i, i);
					removeEdgeFromIndex(this.adjacency[i][last]);
					removeEdgeFromIndex(this.adjacency[last][i]);
					this.adjacency[last][last] = this.adjacency[i][last] = this.adjacency[last][i] = null;
					continue;
				}
				removeEdgeFromIndex(this.adjacency[i][removingNodeIndex]);
				this.adjacency[i][removingNodeIndex] = this.adjacency[i][last];
				updateEdgeInIndex(this.adjacency[i][removingNodeIndex], i, removingNodeIndex);
				this.adjacency[i][last] = null;

				removeEdgeFromIndex(this.adjacency[removingNodeIndex][i]);
				this.adjacency[removingNodeIndex][i] = this.adjacency[last][i];
				updateEdgeInIndex(this.adjacency[removingNodeIndex][i], removingNodeIndex, i);
				this.adjacency[last][i] = null;
			}
		}
		// End to move node to remove at the end of adjacency matrix and to remove all its edges.
		this.index2node.remove(removingNodeIndex);
		this.nodeName2index.remove(removingNode.getName());
		if (removingNodeIndex != last) {
			LabeledNode nodeMovedToRemovedNodePosition = this.index2node.get(last);
			this.index2node.remove(last);
			this.index2node.put(removingNodeIndex, nodeMovedToRemovedNodePosition);
			this.nodeName2index.put(nodeMovedToRemovedNodePosition.getName(), removingNodeIndex);
		}
		this.order = last;
		clearCache();

		return true;
	}

	/**
	 * <p>Setter for the field <code>fileName</code>.</p>
	 *
	 * @param fileName1 a {@link java.io.File} object.
	 */
	public void setFileName(File fileName1) {
		this.fileName = fileName1;
	}

	/**
	 * Setter for the field <code>name</code>.
	 *
	 * @param name1 the name to set
	 */
	public void setName(final String name1) {
		this.name = name1;
	}

	/**
	 * <p>setZ.</p>
	 *
	 * @param z the node to be set as Z node of the graph.
	 */
	public void setZ(final LabeledNode z) {
		if (getNode(z.getName()) == null)
			addVertex(z);
		this.Z = z;
	}

	/**
	 * Takes in all internal structures of g.<br>
	 * This method is useful to copy the references of internal data structure of the given graph 'g' into the current. It is not a clone because even if
	 * g!=this, all internal data structures are the same.
	 *
	 * @param g the graph to copy.
	 */
	public void takeIn(final LabeledLazyWeightGraph g) {
		this.adjacency = g.adjacency;
		this.aLabelAlphabet = g.aLabelAlphabet;
		this.childrenOfObserver = g.childrenOfObserver;
		this.edge2index = g.edge2index;
		this.fileName = g.fileName;
		this.growFactor = g.growFactor;
		this.index2node = g.index2node;
		// this.lowerCaseEdges = g.lowerCaseEdges;
		this.name = g.getName();
		this.nodeName2index = g.nodeName2index;
		this.observer2Z = g.observer2Z;
		this.order = g.order;
		this.proposition2Observer = g.proposition2Observer;
		this.Z = g.Z;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		final StringBuffer sb = new StringBuffer(
				"%LabeledLazyWeightGraph: "
						+ ((this.getName() != null) ? this.getName() : (this.fileName != null) ? this.fileName.toString() : "no name")
						+ "\n%LabeledLazyWeightGraph Syntax\n"
						+ "%LabeledNode: <name, label, proposition observed>\n"
						+ "%T: <name, type, source node, dest. node, L:{labeled values}, LL:{labeled lower-case values}, UL:{labeled upper-case values}>\n");
		sb.append("Nodes:\n");

		for (final LabeledNode n : this.getVertices()) {
			sb.append("<" + n.getName() + ",\t" + n.getLabel() + ",\t" + n.getPropositionObserved());
			LabeledALabelIntTreeMap potential = n.getULCaseLabeledPotential();
			if (potential != null && !potential.isEmpty()) {
				sb.append(",\tPotential: " + potential);
			}
			sb.append(">\n");
		}
		sb.append("Edges:\n");
		for (final LabeledLazyWeightEdge e : this.getEdges()) {
			sb.append("<" + e.getName() + ",\t" + e.getConstraintType() + ",\t" + this.getSource(e).getName() + ",\t"
					+ this.getDest(e).getName() + ",\t" + e.getLabeledValueMap().toString()
					// + ", LL:"
					// + e.lowerCaseValueAsString()
					// + ", UL:" + e.upperCaseValuesAsString()
					+ ">\n");
		}
		return sb.toString();
	}

	/** {@inheritDoc} */
//	@Override
//	public void update(Observable o, Object arg) {
//		if (arg == null)
//			return;
//		String argS = (String) arg;
//
//		String[] args = argS.split(":");
//		String obj = args[0];
//		String oldValue = args[1];
//
//		if (o instanceof LabeledNode) {
//			LabeledNode node = (LabeledNode) o;
//			if (obj.equals("Name")) {
//				int oldI = this.nodeName2index.getInt(oldValue);
//				int newI = this.nodeName2index.getInt(node.getName());
//				if (newI != Constants.INT_NULL) {
//					if (Debug.ON) {
//						if (LOG.isLoggable(Level.FINER))
//							LOG.finer("Values in nodeName2index: " + this.nodeName2index.toString());
//					}
//					if (Debug.ON)
//						LOG.severe("It is not possible to rename node " + oldValue + " with " + node.getName()
//								+ " because there is already a node with name " + node.getName());
//					node.setName(oldValue);
//					return;
//				}
//				this.nodeName2index.remove(oldValue);
//				this.nodeName2index.put(node.getName(), oldI);
//				if (Debug.ON) {
//					if (LOG.isLoggable(Level.FINER)) {
//						LOG.finer("The nodeName2Index is updated. Removed old name " + oldValue + ". Add the new one: " + node + " at position " + oldI
//								+ "\nValues in nodeName2index: " + this.nodeName2index.toString());
//					}
//				}
//				node.setALabel(null);
//				if (this.getZ() != null && oldValue.equals(this.getZ().getName())) {
//					this.setZ(node);
//				}
//				return;
//			}
//			if (obj.equals("Proposition")) {
//				char newP = node.getPropositionObserved();
//				char oldP = oldValue.charAt(0);
//				if (newP != Constants.UNKNOWN) {
//					LabeledNode obsNewProp = this.proposition2Observer.get(newP);
//					if (obsNewProp != null) {
//						if (Debug.ON) {
//							if (LOG.isLoggable(Level.FINER))
//								LOG.finer("Values in proposition2Node: " + this.proposition2Observer.toString());
//						}
//						if (Debug.ON)
//							LOG.severe("It is not possible to set observed proposition " + newP + " to node " + node.getName()
//									+ " because there is already a node that observes the proposition: node " + obsNewProp);
//						node.setObservable(oldP);
//						return;
//					}
//				}
//				if (oldP != Constants.UNKNOWN) {
//					this.proposition2Observer.remove(oldP);
//				}
//				if (newP != Constants.UNKNOWN) {
//					this.proposition2Observer.put(newP, node);
//				}
//				if (Debug.ON) {
//					if (LOG.isLoggable(Level.FINER)) {
//						LOG.finer("The proposition2Node is updated. Removed old key " + oldP + ". Add the new one: " + newP + " with node " + node +
//								"\nValues in proposition2Node: " + this.proposition2Observer.toString());
//					}
//				}
//				this.childrenOfObserver = null;
//				this.observer2Z = null;
//				return;
//			}
//			if (obj.equals("Label")) {
//				this.childrenOfObserver = null;
//			}
//		} // LabeledNode
//
//		if (o instanceof LabeledLazyWeightEdge) {
//			LabeledLazyWeightEdge edge = (LabeledLazyWeightEdge) o;
//			if (obj.equals("Name")) {
//				EdgeIndex oldI = this.edge2index.get(oldValue);
//				EdgeIndex newI = this.edge2index.get(edge.getName());
//				if (newI != null) {
//					if (Debug.ON) {
//						if (LOG.isLoggable(Level.FINER))
//							LOG.finer("Values in edge2index: " + this.edge2index.toString());
//					}
//					if (Debug.ON)
//						LOG.severe("It is not possible to rename edge " + oldValue + " with " + edge.getName()
//								+ " because there is already a edge with name " + edge.getName());
//					edge.setName(oldValue);
//					return;
//				}
//				this.edge2index.remove(oldValue);
//				this.edge2index.put(edge.getName(), oldI);
//				if (Debug.ON) {
//					if (LOG.isLoggable(Level.FINER)) {
//						LOG.finer("The edge2index is updated. Removed old name " + oldValue + ". Add the new one: " + edge + " at position " + oldI +
//								"\nValues in edge2index: " + this.edge2index.toString());
//					}
//				}
//				return;
//			}
//			// if (obj.equals("LowerLabel")) {
//			// this.lowerCaseEdges.remove(edge);
//			// }
//		}
//	}

	@Override
	public void update(Observable o, Object arg) {
		// TODO Auto-generated method stub

	}
	/**
	 * @param e
	 * @param row
	 * @param col
	 */
	private void updateEdgeInIndex(LabeledLazyWeightEdge e, int row, int col) {
		if (e == null || e.getName() == null)
			return;
		EdgeIndex ei = this.edge2index.get(e.getName());
		ei.rowAdj = row;
		ei.colAdj = col;
	}


}
