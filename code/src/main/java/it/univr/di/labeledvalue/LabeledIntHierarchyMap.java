// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

package it.univr.di.labeledvalue;

import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serial;
import java.io.Serializable;
import java.util.logging.Logger;

/**
 * Simple implementation of {@link it.univr.di.labeledvalue.LabeledIntMap} interface.
 * <p>
 * When creating an object it is possible to specify if the labeled values represented into the map should be maintained
 * to the minimal equivalent ones.
 *
 * @author Roberto Posenato
 * @version $Rev: 851 $
 * @see LabeledIntMap
 */

@SuppressWarnings("UnusedAssignment")
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "HE_HASHCODE_NO_EQUALS", justification = "Equals is not necessary for this complementary class.")
public class LabeledIntHierarchyMap extends AbstractLabeledIntMap {

	/**
	 * Simple class to represent a labeled value in the hierarchy.
	 *
	 * @author posenato
	 */
	@SuppressWarnings({"CompareToUsesNonFinalVariable", "NonFinalFieldReferenceInEquals",
	                   "NonFinalFieldReferencedInHashCode"})
	static class HierarchyNode implements Object2IntMap.Entry<Label>, Comparable<HierarchyNode>, Serializable {
		/**
		 *
		 */
		@Serial
		private static final long serialVersionUID = 1L;
		/**
		 * Labeled values subsumed by this.
		 */
		ObjectArraySet<HierarchyNode> father;
		/**
		 *
		 */
		Label label;
		/**
		 * Labeled values that subsume this.
		 */
		ObjectArraySet<HierarchyNode> son;
		/**
		 *
		 */
		int value;

		/**
		 * To manage visit
		 */
		int visit;

		/**
		 * @param l the input label
		 * @param v the input value
		 */
		HierarchyNode(Label l, int v) {
			this();
			label = l;
			value = v;
		}

		/**
		 *
		 */
		private HierarchyNode() {
			label = null;
			value = Constants.INT_NULL;
			visit = 0;
			father = null;
			son = null;
		}

		/**
		 * Clear all internal objects making them empty.
		 */
		public void clear() {
			if (son != null) {
				son.clear();
				son = null;
			}
			if (father != null) {
				father.clear();
				father = null;
			}
			if (label != null) {
				if (label.equals(Label.emptyLabel)) {
					label = Label.emptyLabel; // this is necessary to restore the original empty root node
					value = Constants.INT_POS_INFINITE;
					return;
				}
				label = null;// It is fundamental for checking if a HierarchyNode has been deleted.
			}
			value = Constants.INT_NULL;
		}

		// @Override
		// public int compareTo(HierarchyNode o) {
		// int v = label.compareTo(o.label);
		// if (v == 0) {
		// if (value == o.value)
		// return 0;
		// if (value == Constants.INT_NEG_INFINITE || o.value == Constants.INT_POS_INFINITE)
		// return -1;
		// if (o.value == Constants.INT_NEG_INFINITE || value == Constants.INT_POS_INFINITE)
		// return 1;
		// return value - o.value;
		// }
		// return v;
		// }

		/**
		 * * In order to minimize the number of insertions, it is better to have sons ordered in inverted lexicographical order.
		 */
		@SuppressWarnings("NullableProblems")
		@Override
		public int compareTo(HierarchyNode o) {
			if (o == null || o.label == null) {
				return -1;
			}

			return o.label.compareTo(label);
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (!(o instanceof Object2IntMap.Entry<?>)) {
				return false;
			}
			@SuppressWarnings("unchecked") final Entry<Label> o1 = (Entry<Label>) o;
			return label.equals(o1.getKey()) && value == o1.getIntValue();
		}

		@Override
		public int getIntValue() {
			return value;
		}

		@Override
		public Label getKey() {
			return label;
		}

		@Override
		public int hashCode() {
			return label.hashCode() + value * 31;
		}

		@Override
		public int setValue(int value1) {
			final int old = value;
			value = value1;
			return old;
		}

		@Override
		public String toString() {
			if (label == null) {
				return "";
			}
			return entryAsString(this);
		}
	}

	/**
	 * A read-only view of an object
	 *
	 * @author posenato
	 */
	public static class LabeledIntHierarchyMapView extends LabeledIntHierarchyMap implements LabeledIntMapView {
		/**
		 *
		 */
		@Serial
		private static final long serialVersionUID = 1L;

		/**
		 * @param inputMap the input map
		 */
		public LabeledIntHierarchyMapView(LabeledIntHierarchyMap inputMap) {
			root = inputMap.root;
		}
	}

	/**
	 * Simple class to store some found conditions during a recursion.
	 *
	 * @author posenato
	 */
	private static final class RecursionStatus {
		/**
		 * True if a new labeled value is greater than one already present.
		 */
		boolean valueGreaterThanCurrent;
		/**
		 * True if the new labeled value substitutes one already present.
		 */
		boolean updateAPreviousValue;
		/*
		 * true if a labeled value has to be inserted and there is another equal labeled valued with a label that differs only for one opposite literal.
		 */
		// boolean foundTwin = false;

		/**
		 * true if a perfect match has been found
		 */
		boolean foundPerfectMatch;

		/**
		 *
		 */
		private RecursionStatus() {
		}

		@Override
		public String toString() {
			return "valueGreaterThanCurrentvalueGreaterThanCurrent: " + valueGreaterThanCurrent +
			       "\nvalueGreaterThanCurrent: " + valueGreaterThanCurrent + "\nupdateAPreviousValue: " +
			       updateAPreviousValue + "\nfoundPerfectMatch: " + foundPerfectMatch;
		}
	}

	/**
	 * logger
	 */
	static private final Logger LOG = Logger.getLogger("LabeledIntHierarchyMap");

	/**
	 *
	 */
	@Serial
	static private final long serialVersionUID = 3L;

	/**
	 * @param args an array of {@link java.lang.String} objects.
	 */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "DLS_DEAD_LOCAL_STORE", justification = "min variable is just for a test.")
	static public void main(final String[] args) {

		final int nTest = (int) 1E3;
		final double msNorm = 1.0E6 * nTest;

		final LabeledIntMap map = new LabeledIntHierarchyMap();

		final Label l1 = Label.parse("abc¬f");
		final Label l2 = Label.parse("abcdef");
		final Label l3 = Label.parse("a¬bc¬de¬f");
		final Label l4 = Label.parse("¬b¬d¬f");
		final Label l5 = Label.parse("ec");
		final Label l6 = Label.parse("¬fedcba");
		final Label l7 = Label.parse("ae¬f");
		final Label l8 = Label.parse("¬af¿b");
		final Label l9 = Label.parse("¬af¿b");
		final Label l10 = Label.parse("¬ec");
		final Label l11 = Label.parse("abd¿f");
		final Label l12 = Label.parse("a¿d¬f");
		final Label l13 = Label.parse("¬b¿d¿f");
		final Label l14 = Label.parse("b¬df¿e");
		final Label l15 = Label.parse("e¬c");
		final Label l16 = Label.parse("ab¿d¿f");
		final Label l17 = Label.parse("ad¬f");
		final Label l18 = Label.parse("b¿d¿f");
		final Label l19 = Label.parse("¬b¬df¿e");
		final Label l20 = Label.parse("¬e¬c");

		long startTime = System.nanoTime();
		for (int i = 0; i < nTest; i++) {
			map.clear();
			map.put(Label.emptyLabel, 109);
			map.put(l1, 10);
			map.put(l2, 20);
			map.put(l3, 25);
			map.put(l4, 23);
			map.put(l5, 22);
			map.put(l6, 23);
			map.put(l7, 20);
			map.put(l8, 20);
			map.put(l9, 21);
			map.put(l10, 11);
			map.put(l11, 11);
			map.put(l12, 11);
			map.put(l13, 24);
			map.put(l14, 22);
			map.put(l15, 23);
			map.put(l16, 20);
			map.put(l17, 23);
			map.put(l18, 23);
			map.put(l19, 23);
			map.put(l20, 23);
		}
		long endTime = System.nanoTime();
		System.out.println(
			"LABELED VALUE SET-HIERARCHY MANAGED\nExecution time for some merge operations (mean over " + nTest +
			" tests).\nFinal map: " + map + ".\nTime: (ms): " + ((endTime - startTime) / msNorm));
		final String rightAnswer =
			"{(⊡, 23) (¬a¿bf, 20) (abcdef, 20) (abc¬f, 10) (abd¿f, 11) (a¿d¬f, 11) (ae¬f, 20) (b¬d¿ef, 22) (c, 22) (c¬e, 11) }";
		System.out.println("The right final set is " + rightAnswer + ".");
		System.out.println("Is equal? " + AbstractLabeledIntMap.parse(rightAnswer).equals(map));

		startTime = System.nanoTime();
		int min = 1000;
		for (int i = 0; i < nTest; i++) {
			min = map.getMinValue();
		}
		endTime = System.nanoTime();
		System.out.println(
			"Execution time for determining the min value (" + min + ") (mean over " + nTest + " tests). (ms): " +
			((endTime - startTime) / msNorm));

		startTime = System.nanoTime();
		final Label l = Label.parse("abd¿f");
		for (int i = 0; i < nTest; i++) {
			min = map.get(l);
		}
		endTime = System.nanoTime();
		System.out.println(
			"Execution time for retrieving value of label " + l + " (mean over " + nTest + " tests). (ms): " +
			((endTime - startTime) / msNorm));

		map.put(Label.parse("c"), 11);
		map.put(Label.parse("¬c"), 11);
		System.out.println("After the insertion of (c,11) and (¬c,11) the map becomes: " + map);
	}

	/**
	 * When a father has to be added a set of father, it is necessary to check that there is no other father that
	 * subsumes the new one.
	 *
	 * @param newFather            the new father
	 * @param nodeWhereToAddFather the other node
	 */
	private static void addFatherTo(HierarchyNode newFather, HierarchyNode nodeWhereToAddFather) {
		if (newFather == null || nodeWhereToAddFather == null) {
			return;
		}

		if (nodeWhereToAddFather.father == null) {
			nodeWhereToAddFather.father = new ObjectArraySet<>();
		}
		if (nodeWhereToAddFather.father.isEmpty()) {
			nodeWhereToAddFather.father.add(newFather);
			return;
		}
		// iterator remove is not implemented in iterator of ObjectArraySet.
		final HierarchyNode[] fatherA = nodeWhereToAddFather.father.toArray(new HierarchyNode[1]);
		for (final HierarchyNode fatherInSet : fatherA) {
			if (fatherInSet == null) {
				continue;
			}
			if (fatherInSet.label.subsumes(newFather.label)) {
				return;
			}
			if (newFather.label.subsumes(fatherInSet.label)) {
				nodeWhereToAddFather.father.remove(fatherInSet);
				addFatherTo(fatherInSet, newFather);
			}
		}
		nodeWhereToAddFather.father.add(newFather);
	}

	/**
	 * This helper method checks if newSon has nodeWhereToAddSon as father and, in positive case, adds newSon as so to nodeWhereToAddSon.
	 *
	 * @param newSon            the input node
	 * @param nodeWhereToAddSon where to add it
	 */
	private static void addSonTo(HierarchyNode newSon, HierarchyNode nodeWhereToAddSon) {
		if (newSon == null || nodeWhereToAddSon == null || newSon.father == null ||
		    !newSon.father.contains(nodeWhereToAddSon)) {
			return;
		}

		if (nodeWhereToAddSon.son == null) {
			nodeWhereToAddSon.son = new ObjectArraySet<>();
		}
		nodeWhereToAddSon.son.add(newSon);
	}

	/**
	 * @param label the label to check
	 * @param value the value to checl
	 *
	 * @return true if the node is the original emptyRoot node.
	 */
	private static boolean isEmptyRootNode(Label label, int value) {
		if (label == null || value == Constants.INT_NULL) {
			return false;
		}
		return (label == Label.emptyLabel && value == Constants.INT_POS_INFINITE);
	}

	/**
	 * Checks and manages the case that currentNode has a label that differs from newNode one by only one literal. This procedure can determine a removing
	 * cascade of element that cannot be controlled in a safe way during a recursion (this procedure is called during a recursion insert). Therefore, for now,
	 * it is disabled.
	 *
	 * @param currentNode   current node
	 * @param newNode       the new node
	 * @param sonOfSpin     son of spin
	 * @param ignoredStatus the status
	 */
	@SuppressWarnings("static-method")
	private static void manageSpin(HierarchyNode currentNode, HierarchyNode newNode,
	                               ObjectArraySet<HierarchyNode> sonOfSpin, RecursionStatus ignoredStatus) {
		if (currentNode == null || newNode == null) {
			return;
		}

		final Literal p = newNode.label.getUniqueDifferentLiteral(currentNode.label);
		if (p == null) {
			return;
		}
		final int max = Math.max(newNode.value, currentNode.value);

		// if (max == newNode.value && max == currentNode.value) {
		// // Both nodes have to be replaced!
		// removeNode(currentNode);
		// removeNode(newNode);
		// } else {
		// if (max == newNode.value) {
		// // Only the newNode have to be adjusted!
		// removeNode(newNode);
		// } else {
		// // currentNode has to be replaced by the shorten one, but it cannot be removed because its sons could be lost.
		// removeNode(currentNode);
		// // newNode has to be inserted!
		// // I prefer to insert again from root!
		// if (newNode.father == null)// newNode has not yet inserted!
		// put(newNode.label, newNode.value);
		// }
		// }
		final Label labelWOp = newNode.label.remove(p.getName());
		// status.foundTwin = true;
		final HierarchyNode spin = new HierarchyNode(labelWOp, max);
		sonOfSpin.add(spin);
	}

	/**
	 * @param node  node
	 * @param coll  collection
	 * @param visit level
	 */
	private static void recursiveBuildingSet(HierarchyNode node, ObjectSet<Entry<Label>> coll, int visit) {
		if (node.visit == visit) {
			return;
		}
		if (node.son != null && !node.son.isEmpty()) {
			for (final HierarchyNode n : node.son) {
				recursiveBuildingSet(n, coll, visit);
			}
		}
		if (node.label != null && node.value != Constants.INT_NULL && !isEmptyRootNode(node.label, node.value)) {
			coll.add(node);
		}
		node.visit = visit;
	}

	/**
	 * Clear the hierarchy in a recursive way.
	 *
	 * @param node  node
	 * @param visit level
	 */
	private static void recursiveClear(HierarchyNode node, int visit) {
		if (node.visit == visit) {
			return;
		}
		if (node.son != null && !node.son.isEmpty()) {
			for (final HierarchyNode n : node.son) {
				recursiveClear(n, visit);
			}
		}
		node.clear();
		node.visit = visit;
	}

	/**
	 * @param node node
	 * @param l    label
	 *
	 * @return the value associated to label l if it exists, {@link Constants#INT_NULL} otherwise.
	 */
	@Nullable
	private static HierarchyNode recursiveGet(final HierarchyNode node, final Label l) {
		if (node.label == null) {
			LOG.severe("A removed node is still present as son!");
			return null;
		}
		if (node.label.equals(l)) {
			return node;
		}
		if (node.son == null || node.son.isEmpty()) {
			return null;
		}
		for (final HierarchyNode n : node.son) {
			if (!l.subsumes(n.label)) {
				continue;
			}
			final HierarchyNode v = recursiveGet(n, l);
			if (v != null) {
				return v;
			}
		}
		return null;
	}

	/**
	 * @param node   node
	 * @param l      label
	 * @param min    min
	 * @param status status
	 *
	 * @return the min value associated or consistent to label l if it exists, {@link Constants#INT_NULL} otherwise.
	 */
	private static int recursiveGetMinConsistentWith(final HierarchyNode node, final Label l, int min,
	                                                 RecursionStatus status) {
		if (status.foundPerfectMatch) {
			return min;
		}
		if (node.label.equals(l)) {
			status.foundPerfectMatch = true;
			return node.value;
		}
		if (node.label.isConsistentWith(l)) {
			if (node.value < min) {
				min = node.value;
			}
		} else {
			return min;
		}
		if (node.son == null || node.son.isEmpty()) {
			return min;
		}
		for (final HierarchyNode n : node.son) {
			if (!l.subsumes(n.label)) {
				continue;
			}
			final int v = recursiveGetMinConsistentWith(n, l, min, status);
			if (status.foundPerfectMatch) {
				return v;
			}
			if (v < min) {
				min = v;
			}
		}
		return min;
	}

	/**
	 * @param node   node
	 * @param l      label
	 * @param min    minimum
	 * @param status recursion status
	 *
	 * @return the min value subsumed by label l if it exists, {@link Constants#INT_NULL} otherwise.
	 */
	private static int recursiveGetMinSubsumedBy(final HierarchyNode node, final Label l, int min,
	                                             RecursionStatus status) {
		if (status.foundPerfectMatch) {
			return min;
		}
		if (node.label.equals(l)) {
			status.foundPerfectMatch = true;
			return node.value;
		}
		if (node.label.subsumes(l)) {
			if (node.value < min) {
				min = node.value;
			}
		} else {
			return min;
		}
		if (node.son == null || node.son.isEmpty()) {
			return min;
		}
		for (final HierarchyNode n : node.son) {
			if (!l.subsumes(n.label)) {
				continue;
			}
			final int v = recursiveGetMinSubsumedBy(n, l, min, status);
			if (status.foundPerfectMatch) {
				return v;
			}
			if (v < min) {
				min = v;
			}
		}
		return min;
	}

	/**
	 * After the insertion of newNode, it can occur that siblings' sons has to be updated with respect to newNode.
	 *
	 * @param siblingsSons    sibling sons
	 * @param index           index
	 * @param newNode         new node
	 * @param sonOfSpin       sons of spin
	 * @param recursionStatus status of the recursion.
	 */
	private static void recursiveUpdateSiblingSons(HierarchyNode[] siblingsSons, int index, HierarchyNode newNode,
	                                               ObjectArraySet<HierarchyNode> sonOfSpin,
	                                               RecursionStatus recursionStatus) {
		if (siblingsSons == null || siblingsSons.length == 0 || index == siblingsSons.length) {
			return;
		}
		// go to the last son
		if (index < siblingsSons.length) {
			recursiveUpdateSiblingSons(siblingsSons, index + 1, newNode, sonOfSpin, recursionStatus);
		}
		final HierarchyNode currentSiblingSon = siblingsSons[index];
		if (currentSiblingSon == null) {
			return;
		}
		// goto the last grandson
		// boolean consistent = newNode.label.isConsistentWith(currentSiblingSon.label); NO, because there could be ¿ literal!
		if (currentSiblingSon.son != null && !currentSiblingSon.son.isEmpty()) {
			// Remember that it is necessary to go down even if newNode label is not consistent with currenSiblingSon one
			// because some sons can contain ¿ literals
			recursiveUpdateSiblingSons(currentSiblingSon.son.toArray(new HierarchyNode[1]), 0, newNode, sonOfSpin,
			                           recursionStatus);
		}
		// last son unchecked.
		manageSpin(currentSiblingSon, newNode, sonOfSpin,
		           recursionStatus);// possible case: ¬ab-->¬abc and new node is ¬a¬bc
		// if (recursionStatus.foundTwin) {
		// return;
		// }
		if (currentSiblingSon.label.subsumes(newNode.label)) {
			if (currentSiblingSon.value >= newNode.value) {
				// It could that a label with ? can be nullified by a label with a smaller value and without ?
				// LOG.warning("CurrentNode: " + currentNode + "; NewNode: " + newNode);
				removeNodeAndAddFather(currentSiblingSon, newNode);
				return;
			}
			addFatherTo(newNode, currentSiblingSon);// currentSiblingSon.father.add(newNode);
			addSonTo(currentSiblingSon, newNode);
		}
	}

	/**
	 * Recursive removing all sons having a value greater than the given one.
	 *
	 * @param son      array of son nodes
	 * @param index    index
	 * @param newValue new value
	 */
	private static void recursiveUpdateSons(HierarchyNode[] son, int index, int newValue) {
		if (son == null || son.length == 0 || index == son.length) {
			return;
		}
		// go to the last son
		if (index < son.length) {
			recursiveUpdateSons(son, index + 1, newValue);
		}
		final HierarchyNode current = son[index];
		if (current == null) {
			return;
		}
		// goto the last grandson
		if (current.son != null && !current.son.isEmpty()) {
			recursiveUpdateSons(current.son.toArray(new HierarchyNode[1]), 0, newValue);
		}
		// last son unchecked.
		if (current.value >= newValue) {
			removeNode(current);
		}
	}

	/**
	 * Remove the given node from the hierarchy adjusting possible its sons w.r.t. its father(s).
	 *
	 * @param nodeToRemove node to remove
	 */
	private static void removeNode(HierarchyNode nodeToRemove) {
		if (nodeToRemove == null || nodeToRemove.label == null || nodeToRemove.father == null ||
		    nodeToRemove.father.isEmpty()) {
			return;
		}

		// It is efficient to remove nodeToRemove as father in each son as first action!
		if (nodeToRemove.son != null && !nodeToRemove.son.isEmpty()) {
			for (final HierarchyNode sonOfNodeToRemove : nodeToRemove.son) {
				sonOfNodeToRemove.father.remove(nodeToRemove);
			}
		}

		// for each father gFather of nodeToRemove,
		// 1. remove nodeToRemove as son
		// 2. check if each nodeToRemove's son X has to become son of gFather
		for (final HierarchyNode gFather : nodeToRemove.father) {
			assert (gFather.son != null && !gFather.son.isEmpty());
			gFather.son.remove(nodeToRemove);

			if (nodeToRemove.son == null || nodeToRemove.son.isEmpty()) {
				continue;
			}

			for (final HierarchyNode sonOfNodeToRemove : nodeToRemove.son) {
				// Each nodeToRemove's son X becomes son of gFather if and only if no other X's father subsumes gFather.
				if (sonOfNodeToRemove.value >= gFather.value)// sanity check
				{
					continue;
				}
				addFatherTo(gFather, sonOfNodeToRemove);// sonOfNodeToRemove.father.add(gFather);
				addSonTo(sonOfNodeToRemove, gFather);
			}
		}
		nodeToRemove.clear();
	}

	/**
	 * Removes 'nodeToRemove' because it has been made redundant by 'newNode' and adjusts nodeToRemove's sons w.r.t.
	 * newNode as a possible father.
	 *
	 * @param nodeToRemove node to remove
	 * @param newNode      the node that replaces the node to remove
	 */
	private static void removeNodeAndAddFather(HierarchyNode nodeToRemove, HierarchyNode newNode) {
		if (nodeToRemove == null || nodeToRemove.label == null || nodeToRemove.father == null ||
		    nodeToRemove.father.isEmpty() || newNode == null) {
			return;
		}

		// Maintain a copy of son set of nodeToRemove
		final ObjectArraySet<HierarchyNode> grandChildren =
			(nodeToRemove.son != null) ? new ObjectArraySet<>(nodeToRemove.son) : null;

		removeNode(nodeToRemove);

		if (grandChildren == null || grandChildren.isEmpty()) {
			return;
		}

		for (final HierarchyNode son : grandChildren) {
			addFatherTo(newNode, son);
			addSonTo(son, newNode);
		}
	}

	/**
	 * Replace currentNode with newNode.
	 *
	 * @param currentNode node to be replaced
	 * @param newNode     the new node
	 */
	private static void replace(HierarchyNode currentNode, HierarchyNode newNode) {
		for (final HierarchyNode father : currentNode.father) {
			father.son.remove(currentNode);
		}
		if (currentNode.son != null && !currentNode.son.isEmpty()) {
			if (newNode.son == null) {
				newNode.son = new ObjectArraySet<>();
			}
			for (final HierarchyNode son : currentNode.son) {
				son.father.remove(currentNode);
				addFatherTo(newNode, son);// son.father.add(newNode);
				addSonTo(son, newNode);
			}
		}
	}

	/**
	 * Just to force the control that, after each put, the format of hierarchy is still valid. Don't set true in a production program!
	 */
	public boolean wellFormatCheck;
	/**
	 * Root of hierarchy Design choice: the set of labeled values of this map is organized as a double linked hierarchy of labeled values. A labeled value
	 * (label, value) is father of another labeled value (label1, value1) if label1 subsumes label and value1 &lt; value.
	 */
	HierarchyNode root;
	/**
	 * Just for debugging
	 */
	private String putHistory = "";

	/**
	 * Constructor to clone the structure.
	 *
	 * @param lvm the LabeledValueTreeMap to clone. If lvm is null, this will be an empty map.
	 */
	LabeledIntHierarchyMap(final LabeledIntMap lvm) {
		this();
		if (lvm == null) {
			return;
		}

		for (final Entry<Label> e : lvm.entrySet()) {
			put(e.getKey(), e.getIntValue());
		}
	}

	/**
	 * Necessary constructor for the factory. The internal structure is built and empty.
	 */
	LabeledIntHierarchyMap() {
		// Root has to be set. I choose to use (+infinity, emptyLabel) as root of an empty hierarchy
		/*
		 * EmptyRoot necessary to initialize the tree. If the definition is changed, then
		 * {@link #isEmptyRootNode(Label, int)} and {@link HierarchyNode#clear()} have to be
		 * updated!
		 */
		root = new HierarchyNode(Label.emptyLabel, Constants.INT_POS_INFINITE);
	}

	@Override
	public boolean alreadyRepresents(Label newLabel, int newValue) {
		// Auto-generated method stub
		return false;
	}

	@Override
	public void clear() {
		recursiveClear(root, root.visit + 1);
		root.visit++;
		putHistory = "";
	}

	@Override
	public ObjectSet<Entry<Label>> entrySet(@Nonnull ObjectSet<Entry<Label>> setToReuse) {
		setToReuse.clear();
		recursiveBuildingSet(root, setToReuse, (root.visit + 1));
		root.visit++;
		return setToReuse;
	}

	@Override
	public ObjectSet<Entry<Label>> entrySet() {
		final ObjectSet<Entry<Label>> coll = new ObjectArraySet<>();
		return entrySet(coll);
	}

	@Override
	public int get(final Label l) {
		if (l == null) {
			return Constants.INT_NULL;
		}
		final HierarchyNode node = recursiveGet(root, l);
		return (node == null || node.label == null) ? Constants.INT_NULL : node.value;
	}

	@Override
	public int getMinValueConsistentWith(final Label l) {
		if (l == null) {
			return Constants.INT_NULL;
		}
		final RecursionStatus status = new RecursionStatus();
		final int min = recursiveGetMinConsistentWith(root, l, Constants.INT_POS_INFINITE, status);
		return (min == Constants.INT_POS_INFINITE) ? Constants.INT_NULL : min;
	}

	@Override
	public int getMinValueSubsumedBy(final Label l) {
		if (l == null) {
			return Constants.INT_NULL;
		}
		final RecursionStatus status = new RecursionStatus();
		final int min = recursiveGetMinSubsumedBy(root, l, Constants.INT_POS_INFINITE, status);
		return (min == Constants.INT_POS_INFINITE) ? Constants.INT_NULL : min;
	}

	@Override
	public ObjectSet<Label> keySet(ObjectSet<Label> setToReuse) {
		setToReuse.clear();
		for (final Entry<Label> entry : entrySet()) {
			if (isEmptyRootNode(entry.getKey(), entry.getIntValue())) {
				continue;
			}
			setToReuse.add(entry.getKey());
		}
		return setToReuse;
	}

	@Override
	public ObjectSet<Label> keySet() {
		final ObjectSet<Label> coll = new ObjectArraySet<>();
		return keySet(coll);
	}

	@Override
	public LabeledIntHierarchyMap newInstance() {
		return new LabeledIntHierarchyMap();
	}

	/**
	 * Factory
	 *
	 * @param optimize true for having the label shortest as possible, false otherwise. For example, the set {(0, ¬C),
	 *                 (1, C)} is represented as {(0, ⊡), (1, C)} if this parameter is true.
	 *
	 * @return an object of type LabeledIntMap.
	 */
	@Override
	public LabeledIntMap newInstance(boolean optimize) {
		return newInstance();
	}

	@Override
	public LabeledIntHierarchyMap newInstance(LabeledIntMap lim) {
		return new LabeledIntHierarchyMap(lim);
	}

	/**
	 * Factory
	 *
	 * @param lim      an object to clone.
	 * @param optimize true for having the label shortest as possible, false otherwise. For example, the set {(0, ¬C),
	 *                 (1, C)} is represented as {(0, ⊡), (1, C)} if this parameter is true.
	 *
	 * @return an object of type LabeledIntMap.
	 */
	@Override
	public LabeledIntMap newInstance(LabeledIntMap lim, boolean optimize) {
		return newInstance(lim);
	}

	@Override
	public boolean put(Label newLabel, int newValue) {
		if ((newLabel == null) || (newValue == Constants.INT_NULL)) {
			return false;
		}

		final HierarchyNode newNode = new HierarchyNode(newLabel, newValue);
		final RecursionStatus status = new RecursionStatus();
		if (wellFormatCheck) {
			putHistory += newNode + " ";
		}
		final ObjectArraySet<HierarchyNode> sonOfSpin = new ObjectArraySet<>();
		boolean st = recursivePut(root, newNode, sonOfSpin, status);
		// I don't remember which optimization should be done
		if (!sonOfSpin.isEmpty()) {
			// there is new element determined by simplification of labels with one opposite literal (e.g., (¬ab,3) (ab,4) --> (b,4) has to be added).
			for (final HierarchyNode newNodeSpin : sonOfSpin) {
				if (newNodeSpin.label != null) {
					st = put(newNodeSpin.label, newNodeSpin.value) || st;
				}
			}
		}
		// I don't remember which optimization should be done
		if (wellFormatCheck && root != null && root.son != null) {
			for (final HierarchyNode son : root.son) {
				for (final HierarchyNode son1 : root.son) {
					if (son != null && son1 != null && son != son1 &&
					    (son.label.subsumes(son1.label) || son1.label.subsumes(son.label))) {
						LOG.severe("Hierarchy: " + this);
						LOG.severe("Son: " + root.son);
						LOG.severe("Put history: " + putHistory);
						throw new IllegalStateException("Hierarchy is not in a right format.");
					}
				}
			}
		}
		return st;
	}

	/**
	 * Put the labeled value without any control. It is dangerous but it can help in some cases.
	 *
	 * @param l a {@link Label} object.
	 * @param i the new value.
	 */
	@Override
	public void putForcibly(Label l, int i) {
		this.put(l, i);
	}

	@Override
	public int remove(final Label l) {
		if (l == null) {
			return Constants.INT_NULL;
		}
		final HierarchyNode node = recursiveGet(root, l);
		if (node == null || node.label == null) {
			return Constants.INT_NULL;
		}
		final int v = node.value;
		removeNode(node);
		return v;
	}

	@Override
	public int size() {
		if (root == null) {
			return 0;
		}
		final int sum = recursiveCount(root, root.visit + 1);
		root.visit++;
		return sum;
	}

	@Override
	public LabeledIntMapView unmodifiable() {
		return new LabeledIntHierarchyMapView(this);
	}

	@Override
	public IntSet values() {
		final IntArraySet coll = new IntArraySet(size());
		for (final Entry<Label> entry : entrySet()) {
			if (isEmptyRootNode(entry.getKey(), entry.getIntValue())) {
				continue;
			}
			coll.add(entry.getIntValue());
		}
		return coll;
	}

	/**
	 * Counts elements of the hierarchy in a recursive way.
	 *
	 * @param node  node level
	 * @param visit level
	 *
	 * @return number of nodes below node
	 */
	private int recursiveCount(HierarchyNode node, int visit) {
		if (node.visit == visit) {
			return 0;
		}
		int sum = 0;
		if (node.son != null && !node.son.isEmpty()) {
			for (final HierarchyNode n : node.son) {
				sum += recursiveCount(n, visit);
			}
		}
		node.visit = visit;
		if (node != root) {
			return sum + 1;
		}
		if (!isEmptyRootNode(node.label, node.value)) {
			return sum + 1;
		}
		return sum;
	}

	/**
	 * @param current         node
	 * @param newNode         the new node
	 * @param sonOfSpin       son of spin
	 * @param recursionStatus status of recursion
	 *
	 * @return true if the newNode has been inserted; false otherwise.
	 */
	private boolean recursivePut(HierarchyNode current, HierarchyNode newNode, ObjectArraySet<HierarchyNode> sonOfSpin,
	                             RecursionStatus recursionStatus) {
		if (current == null || newNode == null || newNode.label == null) {
			final String msg = "Something wrong. newNode: " + newNode + ", current:" + current;
			LOG.severe(msg);
			throw new IllegalArgumentException(msg);
		}

		boolean status;

		// 1. Manage possible spin label
		manageSpin(current, newNode, sonOfSpin, recursionStatus);
		// if (recursionStatus.foundTwin) {
		// return status;
		// }

		// 2. If new label does not subsume current label, it cannot stay in this sub-hierarchy.
		if (!newNode.label.subsumes(current.label)) {
			return false;
		}

		// 3. If new value is greater, it cannot stay in this sub-hierarchy.
		if (newNode.value >= current.value) {
			recursionStatus.valueGreaterThanCurrent = true;
			return false;
		}

		// 4. Manage case current element has same label. (A value is already present)
		if (current.label.equals(newNode.label)) {
			current.value = newNode.value;
			if (current.son != null && !current.son.isEmpty()) {
				recursiveUpdateSons(current.son.toArray(new HierarchyNode[1]), 0, newNode.value);
			}
			recursionStatus.updateAPreviousValue = true;
			return true;
		}

		// 5. Descent the sub hierarchy checking if new node has to be inserted as an update or as new node.
		status = false;
		if (current.son != null && !current.son.isEmpty()) {
			// LOG.severe("Current.son: " + current.son);
			final HierarchyNode[] sonsReferenceCopy = current.son.toArray(new HierarchyNode[1]);
			for (final HierarchyNode son : sonsReferenceCopy) {
				if (son.label == null) { // === !currentNode.son.contains(son))
					continue;
				}
				status = recursivePut(son, newNode, sonOfSpin, recursionStatus) || status;
				if (recursionStatus.updateAPreviousValue) {
					return status;
				}
				if (recursionStatus.valueGreaterThanCurrent) {
					if (status) {
						// If newNode has been already inserted but, then, it has been discovered that it shouldn't be inserted because there is a smaller
						// value, then newNode has to be removed!
						removeNode(newNode);
					}
					return false;
				}
			}
		}

		// 6. If it has been inserted in the sub-hierarchy, everything has been done!
		if (status) {
			return true;
		}

		// Element has to be inserted as son.
		addFatherTo(current, newNode);// newNode.father.add(current);
		addSonTo(newNode, current);

		// It could occur that some brothers have to be become sons of newNode!
		// Since currentNode.son will be possibly shorten, I need a copy to be sure to check all components.
		final HierarchyNode[] sonsReferenceCopy = current.son.toArray(new HierarchyNode[1]);
		for (final HierarchyNode son : sonsReferenceCopy) {
			if (son == null || son.label == null || son == newNode) {
				continue;
			}
			if (son.label.subsumes(newNode.label)) {
				if (son.value >= newNode.value) {
					// newNode has a shorter label and a better value, 'son' can be deleted.
					if (son.son != null && !son.son.isEmpty()) {
						recursiveUpdateSons(son.son.toArray(new HierarchyNode[1]), 0, newNode.value);
					}
					replace(son, newNode);
				} else {
					// son label subsumes newNode one and son value is smaller ==> son becomes son of newNode.
					current.son.remove(son);
					son.father.remove(current);
					addFatherTo(newNode, son);// son.father.add(newNode);
					addSonTo(son, newNode);
				}
			} else {
				if (son.son != null && !son.son.isEmpty()) {
					recursiveUpdateSiblingSons(son.son.toArray(new HierarchyNode[1]), 0, newNode, sonOfSpin,
					                           recursionStatus);
				}
				if (newNode.father != null && !newNode.father.isEmpty()) {// newNode is still valid
					if (newNode.label.subsumes(son.label)) {
						LOG.warning(
							"It is strange that program is here. newNode: " + newNode + ", son: " + son + ", global:" +
							this);
						addFatherTo(son, newNode);
						addSonTo(son, newNode);
					}
				}
			}
		}
		return true;
	}
}
