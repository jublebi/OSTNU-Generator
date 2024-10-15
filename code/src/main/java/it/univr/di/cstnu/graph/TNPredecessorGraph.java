package it.univr.di.cstnu.graph;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import it.unimi.dsi.fastutil.objects.*;

import javax.annotation.Nullable;

/**
 * Optimized representation of immutable predecessor graphs.
 * <p>
 * Scope of this class is to increase the performance of procedure STNU#GET_STN_PREDECESSOR_SUBGRAPH and
 * STNU#getUndominatedEdges.
 * </p>
 *
 * @param <E> kind of edges
 *
 * @author posenato
 */
public class TNPredecessorGraph<E extends Edge> {

	/**
	 * List of nodes
	 */
	private final ObjectSet<LabeledNode> nodes;
	/**
	 * For each node, gives the list of (predecessor node, incoming edge)
	 */
	private Object2ObjectMap<LabeledNode, ObjectList<ObjectObjectImmutablePair<LabeledNode, E>>> predecessorNodes;
	/**
	 * For each node, gives the list of (successor node, incoming edge)
	 */
	private Object2ObjectMap<LabeledNode, ObjectList<ObjectObjectImmutablePair<LabeledNode, E>>> successorNodes;
	/**
	 * Z node
	 */
	private LabeledNode Z;

	/**
	 * Default constructor
	 */
	public TNPredecessorGraph() {
		nodes = new ObjectOpenHashSet<>();
		predecessorNodes = new Object2ObjectOpenHashMap<>();
		predecessorNodes.defaultReturnValue(ObjectLists.emptyList());
		successorNodes = new Object2ObjectOpenHashMap<>();
		successorNodes.defaultReturnValue(ObjectLists.emptyList());
		Z = null;
	}

	/**
	 * Adds the edge to this graph.
	 *
	 * @param edge   the edge to add
	 * @param source the source node
	 * @param dest   the destination node
	 *
	 * @return true if the edge was added, false otherwise
	 */
	public boolean addEdge(E edge, LabeledNode source, LabeledNode dest) {
		if (edge == null || source == null || dest == null) {
			return false;
		}
		ObjectList<ObjectObjectImmutablePair<LabeledNode, E>> list = predecessorNodes.get(dest);
		if (list == predecessorNodes.defaultReturnValue()) {
			list = new ObjectArrayList<>();
			predecessorNodes.put(dest, list);
		}
		list.add(new ObjectObjectImmutablePair<>(source, edge));

		list = successorNodes.get(source);
		if (list == successorNodes.defaultReturnValue()) {
			list = new ObjectArrayList<>();
			successorNodes.put(source, list);
		}
		list.add(new ObjectObjectImmutablePair<>(dest, edge));
		return true;
	}

	/**
	 * Adds node to the set of nodes. For efficiency reason, it does not check if there is already a node with the same
	 * name, that it is not allowed, but it does not add the same node (object identity) more times.
	 *
	 * @param node node to add
	 */
	public void addNode(LabeledNode node) {
		nodes.add(node);
	}

	/**
	 * Clear all structures.
	 */
	public void clear() {
		nodes.clear();
		predecessorNodes.clear();
		successorNodes.clear();
	}

	/**
	 * Clears all predecessors of the given node.
	 *
	 * @param node the node
	 */
	public void clearPredecessors(final LabeledNode node) {
		if (node == null) {
			return;
		}
		final ObjectList<ObjectObjectImmutablePair<LabeledNode, E>> predecessorList = predecessorNodes.get(node);
		if (predecessorList.isEmpty()) {
			return;
		}
		//before removing all predecessors, we remove current node as successor of all predecessors.
		for (final ObjectObjectImmutablePair<LabeledNode, E> entry : predecessorList) {
			final LabeledNode predecessor = entry.left();
			final ObjectList<ObjectObjectImmutablePair<LabeledNode, E>> successorListOfPredecessor =
				successorNodes.get(predecessor);
			for (final ObjectObjectImmutablePair<LabeledNode, E> entry1 : successorListOfPredecessor) {
				if (entry1.key().getName().equals(node.getName())) {
					successorListOfPredecessor.remove(entry1);
					break;
				}
			}
		}
		predecessorList.clear();
	}

	/**
	 * Return the node associated to the name
	 *
	 * @param name name of searched node
	 *
	 * @return the node if it exists, null otherwise
	 */
	@Nullable
	public LabeledNode getNode(String name) {
		if (name == null || name.isEmpty()) {
			return null;
		}
		for (final LabeledNode node : nodes) {
			if (node.getName().equals(name)) {
				return node;
			}
		}
		return null;
	}

	/**
	 * Returns the list of predecessors (with incoming edge) of the given node. If a node has no predecessors, it
	 * returns an empty immutable list.
	 *
	 * @param node node
	 *
	 * @return the list of predecessors (with incoming edge) of the given node if it exists, empty immutable list
	 * 	otherwise
	 */
	public ObjectList<ObjectObjectImmutablePair<LabeledNode, E>> getPredecessors(LabeledNode node) {
		if (node == null) {
			return ObjectLists.emptyList();
		}
		return this.predecessorNodes.get(node);
	}

	/**
	 * Returns the list of successors (with outgoing edge) of the given node. If a node has no successors, it returns an
	 * empty immutable list.
	 *
	 * @param node node
	 *
	 * @return the list of successors (with outgoing edge) of the given node if it exists, empty immutable list
	 * 	otherwise
	 */
	public ObjectList<ObjectObjectImmutablePair<LabeledNode, E>> getSuccessors(LabeledNode node) {
		if (node == null) {
			return ObjectLists.emptyList();
		}
		return this.successorNodes.get(node);
	}

	/**
	 * @return the Z value
	 */
	public LabeledNode getZ() {
		return Z;
	}

	/**
	 * @param z set the Z node
	 */
	@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "For efficiency reason, it includes an external mutable object.")
	public void setZ(final LabeledNode z) {
		if (z == null) {
			Z = null;
			return;
		}
		if (getNode(z.getName()) == null) {
			nodes.add(z);
		}
		Z = z;
	}

	/**
	 * @param node node to check
	 *
	 * @return true if the node is present in the graph.
	 */
	public boolean isNodePresent(LabeledNode node) {
		if (node == null) {
			return false;
		}
		return nodes.contains(node);
	}

	/**
	 * Transpose the predecessor graph
	 */
	public void reverse() {
		final Object2ObjectMap<LabeledNode, ObjectList<ObjectObjectImmutablePair<LabeledNode, E>>> swap =
			predecessorNodes;
		predecessorNodes = successorNodes;
		successorNodes = swap;
	}

	/**
	 * A simple string representation.
	 */
	@Override
	public String toString() {
		return "Predecessors: " + predecessorNodes + "\nSuccessors: " + successorNodes;
	}
}
