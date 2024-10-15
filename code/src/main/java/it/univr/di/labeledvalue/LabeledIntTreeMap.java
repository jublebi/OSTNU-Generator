// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

package it.univr.di.labeledvalue;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.*;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.univr.di.Debug;

import javax.annotation.Nonnull;
import java.io.Serial;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple implementation of {@link it.univr.di.labeledvalue.LabeledIntMap} interface.
 * <p>
 * An experimental result on 2016-01-13 showed that using base there is a small improvement in the performance.
 * <table border="1">
 * <caption>Execution time (ms) for some operations w.r.t the core data structure of the class (Object2IntMap).</caption>
 * <tr>
 * <th>Operation</th>
 * <th>Using AVL or RB Tree (ms)</th>
 * <th>Using OpenHash (ms)</th>
 * <th>Using fastUtil.Array (ms)</th>
 * </tr>
 * <tr>
 * <td>Create 1st map</td>
 * <td>0.236499088</td>
 * <td>0.15073823</td>
 * <td>0.140981928</td>
 * </tr>
 * <tr>
 * <td>min value</td>
 * <td>0.004523044</td>
 * <td>0.005419635</td>
 * <td>0.007725364</td>
 * </tr>
 * <tr>
 * <td>Retrieve value</td>
 * <td>0.000697368</td>
 * <td>0.000216167E</td>
 * <td>0.000172576</td>
 * </tr>
 * <tr>
 * <td>Simplification</td>
 * <td>~1.275382</td>
 * <td>~1.221648</td>
 * <td>~0.328194</td>
 * </tr>
 * </table>
 * <b>All code for performance tests is in LabeledIntTreeMapTest class (not public available).</b>
 *
 * @author Roberto Posenato
 * @version $Rev: 851 $
 * @see LabeledIntMap
 */
@SuppressWarnings({"SizeReplaceableByIsEmpty", "UnusedReturnValue"})
public class LabeledIntTreeMap extends AbstractLabeledIntMap {

	/**
	 * A read-only view of an object
	 *
	 * @author posenato
	 */
	public static class LabeledIntTreeMapView extends LabeledIntTreeMap implements LabeledIntMapView {
		/**
		 *
		 */
		@Serial
		private static final long serialVersionUID = 1L;

		/**
		 * @param inputMap input
		 */
		public LabeledIntTreeMapView(LabeledIntTreeMap inputMap) {
			mainInt2SetMap = inputMap.mainInt2SetMap;
			base = inputMap.base;
			count = inputMap.count;
		}

		/**
		 * Object Read-only. It does nothing.
		 */
		@Override
		public void putForcibly(@Nonnull Label l, int i) {
		}
	}

	/**
	 * empty base;
	 */
	static private final char[] emptyBase = new char[0];
	/**
	 * logger
	 */
	static private final Logger LOG = Logger.getLogger("LabeledIntTreeMap");
	/**
	 *
	 */
	@Serial
	static private final long serialVersionUID = 2L;

	/**
	 * @return an Object2IntMap object
	 */
	private static Int2ObjectArrayMap<Object2IntMap<Label>> makeInt2ObjectMap() {
		return new Int2ObjectArrayMap<>();
	}

	/**
	 * @return an Object2IntMap object
	 */
	private static Object2IntMap<Label> makeObject2IntMap() {
		return new Object2IntArrayMap<>();// Object2IntRBTreeMap is better than Object2IntArrayMap when the set is larger than 5000 elements!
	}

	/**
	 * Set of propositions forming a base for the labels of the map.
	 */
	char[] base;
	/**
	 * Design choice: the set of labeled values of this map is organized as a collection of sets each containing labels
	 * of the same length. This allows the label minimization task to be performed in a more systematic and efficient
	 * way. The efficiency has been proved comparing this implementation with one in which the map has been realized
	 * with a standard map and the minimization task determines the same length labels every time it needs it.
	 */
	Int2ObjectArrayMap<Object2IntMap<Label>> mainInt2SetMap;


	/**
	 * Constructor to clone the structure. For optimization issue, this method clone only LabeledIntTreeMap object.
	 *
	 * @param lvm      the LabeledValueTreeMap to clone. If lvm is null, this will be an empty map.
	 * @param optimize true for having the label shortest as possible, false otherwise. For example, the set {(0, ¬C),
	 *                 (1, C)} is represented as {(0, ⊡), (1, C)} if this parameter is true.
	 */
	LabeledIntTreeMap(final LabeledIntMap lvm, final boolean optimize) {
		this(optimize);
		if (lvm == null) {
			return;
		}
		// this.base = ((LabeledIntTreeMap) lvm).base;It is wrong to set the base before because the following put can add base values as last values! The base
		// has to be determined during the put!
		for (final Entry<Label> entry : lvm.entrySet()) {
			put(entry.getKey(), entry.getIntValue());
		}
	}

	/**
	 * Constructor to clone the structure. For optimization issue, this method clone only LabeledIntTreeMap object.
	 *
	 * @param lvm the LabeledValueTreeMap to clone. If lvm is null, this will be an empty map.
	 */
	LabeledIntTreeMap(final LabeledIntMap lvm) {
		this(lvm, true);
	}


	/**
	 * Necessary constructor for the factory. The internal structure is built and empty.
	 *
	 * @param optimize true for having the label shortest as possible, false otherwise. For example, the set {(0, ¬C),
	 *                 (1, C)} is represented as {(0, ⊡), (1, C)} if this parameter is true.
	 */
	LabeledIntTreeMap(final boolean optimize) {
		mainInt2SetMap = makeInt2ObjectMap();
		base = emptyBase;
		count = 0;
		this.optimize = optimize;
	}

	/**
	 * Necessary constructor for the factory. The internal structure is built and empty.
	 */
	LabeledIntTreeMap() {
		this(true);
	}


	@Override
	public boolean alreadyRepresents(Label newLabel, int newValue) {
		final int valuePresented = get(newLabel);
		if (valuePresented > newValue) {
			return false;
		}
		if (valuePresented != Constants.INT_NULL) {
			return true;
		}
		/*
		 * Check if there is already a value in the map that represents the new value.
		 */
		final int newLabelSize = newLabel.size();

		for (final int labelLength : mainInt2SetMap.keySet()) {
			if (labelLength > newLabelSize) {
				continue;
			}
			for (final Entry<Label> entry : mainInt2SetMap.get(labelLength).object2IntEntrySet()) {
				final Label l1 = entry.getKey();
				final int v1 = entry.getIntValue();

				if (newLabel.subsumes(l1) && newValue >= v1) {
					return true;
				}
			}
		}
		return (isBaseAbleToRepresent(newLabel, newValue));
	}

	@Override
	public void clear() {
		mainInt2SetMap.clear();
		base = emptyBase;
		count = 0;
	}

	/**
	 * {@inheritDoc}
	 * <br>
	 * This method returns a copy of the view of the map. Any modification of the map IS NOT propagated to the
	 * return set.<br>
	 * In other word, it is possible to iterate on this map for modifying the original map without worried to lost
	 * any element.<br>
	 * Up to 1000 items in the map it is better to use this method instead of {@link #keySet()} and {@link #get(Label)}
	 * .<br>
	 * With 1000 or more items, it is better to use {@link #keySet()} approach.
	 */
	@Override
	public ObjectSet<Entry<Label>> entrySet() {
		final ObjectSet<Entry<Label>> coll = new ObjectArraySet<>();
		return entrySet(coll);
	}

	/**
	 * @see #entrySet()
	 */
	@Override
	public ObjectSet<Entry<Label>> entrySet(@Nonnull ObjectSet<Entry<Label>> setToReuse) {
		setToReuse.clear();
		for (final Object2IntMap<Label> mapI : mainInt2SetMap.values()) {
			setToReuse.addAll(mapI.object2IntEntrySet());
		}
		return setToReuse;
	}

	@Override
	public int get(final Label l) {
		if (l == null) {
			return Constants.INT_NULL;
		}
		final Object2IntMap<Label> map1 = mainInt2SetMap.get(l.size());
		if (map1 == null) {
			return Constants.INT_NULL;
		}
		return map1.getInt(l);
	}

	@Override
	public int getMaxValue() {
		int max = Constants.INT_NEG_INFINITE;
		for (final Object2IntMap<Label> mapI : mainInt2SetMap.values()) {
			for (final int j : mapI.values()) {
				if (max < j) {
					max = j;
				}
			}
		}
		return (max == Constants.INT_NEG_INFINITE) ? Constants.INT_NULL : max;
	}

	@Override
	public int getMinValue() {
		int min = Constants.INT_POS_INFINITE;
		for (final Object2IntMap<Label> mapI : mainInt2SetMap.values()) {
			for (final int j : mapI.values()) {
				if (min > j) {
					min = j;
				}
			}
		}
		return (min == Constants.INT_POS_INFINITE) ? Constants.INT_NULL : min;
	}

	@Override
	public int getMinValueSubsumedBy(final Label l) {
		if (l == null) {
			return Constants.INT_NULL;
		}
		int min = get(l);
		if (min == Constants.INT_NULL) {
			// the label does not exit, try all subsumed labels
			min = get(Label.emptyLabel);
			if (min == Constants.INT_NULL) {
				min = Constants.INT_POS_INFINITE;
			}
			int v1;
			Label l1;
			final int n = l.size();
			for (int i = 0; i < n; i++) {
				final Object2IntMap<Label> map = mainInt2SetMap.get(i);
				if (map == null) {
					continue;
				}
				for (final Entry<Label> e : map.object2IntEntrySet()) {
					l1 = e.getKey();
					if (l.subsumes(l1)) {
						v1 = e.getIntValue();
						if (min > v1) {
							min = v1;
						}
					}
				}
			}
		}
		return (min == Constants.INT_POS_INFINITE) ? Constants.INT_NULL : min;
	}

	@Override
	public ObjectSet<Label> keySet() {
		final ObjectSet<Label> coll = new ObjectArraySet<>();
		return keySet(coll);
	}

	@Override
	public ObjectSet<Label> keySet(ObjectSet<Label> setToReuse) {
		setToReuse.clear();
		for (final Object2IntMap<Label> mapI : mainInt2SetMap.values()) {
			setToReuse.addAll(mapI.keySet());
		}
		return setToReuse;
	}

	@Override
	public LabeledIntTreeMap newInstance() {
		return new LabeledIntTreeMap(true);
	}

	@Override
	public LabeledIntTreeMap newInstance(boolean optimize) {
		return new LabeledIntTreeMap(optimize);
	}

	@Override
	public LabeledIntTreeMap newInstance(LabeledIntMap lim) {
		return new LabeledIntTreeMap(lim, true);
	}

	@Override
	public LabeledIntTreeMap newInstance(LabeledIntMap lim, boolean optimize) {
		return new LabeledIntTreeMap(lim, optimize);
	}

	/**
	 * {@inheritDoc} Adds the pair &lang;l,i&rang;.<br> Moreover, tries to eliminate all labels that are redundant.<br>
	 * <b>IMPORTANT!</b><br>
	 * This version of the method is very redundant but simple to check!
	 */
	@Override
	public boolean put(final Label newLabel, int newValue) {
		if ((newLabel == null) || (newValue == Constants.INT_NULL) || alreadyRepresents(newLabel, newValue)) {
			return false;
		}
		/*
		 * Step 1.
		 * The value is not already represented.
		 * It must be added.
		 * In the following, all values already present and implied by the new one are removed before adding it.
		 * Then, the new value is added in step 2.
		 */
		removeAllValuesGreaterThan(newLabel, newValue);

		/*
		 * Step 2.
		 * Insert the new value and check if it is possible to simplify with some other labels with same value and only one different literals.
		 */
		final Object2IntMap<Label> a = makeObject2IntMap();
		a.defaultReturnValue(Constants.INT_NULL);
		a.put(newLabel, newValue);
		return insertAndSimplify(a, newLabel.size());
	}

	/**
	 * @param l a {@link it.univr.di.labeledvalue.Label} object.
	 * @param i the new value. If it is Constants#INT_NULL, the method does nothing.
	 */
	public void putForcibly(@Nonnull final Label l, final int i) {
		if (i == Constants.INT_NULL) {
			return;
		}
		Object2IntMap<Label> map1 = mainInt2SetMap.get(l.size());
		if (map1 == null) {
			map1 = makeObject2IntMap();
			map1.defaultReturnValue(Constants.INT_NULL);
			mainInt2SetMap.put(l.size(), map1);
		}
		final int old = map1.put(l, i);
		if (old == Constants.INT_NULL) {
			count++;//'i' is a new value
		}
	}

	@Override
	public int remove(final Label l) {
		final Object2IntMap<Label> map1 = mainInt2SetMap.get(l.size());
		if (map1 == null) {
			return Constants.INT_NULL;
		}
		final int oldValue = map1.removeInt(l);
		if (map1.size() == 0) {
			mainInt2SetMap.remove(l.size());
		}
		if (oldValue != Constants.INT_NULL) {// && this.optimize)
			// The base could have been changed. To keep things simple, for now it is better to rebuild all instead of to try to rebuild a possible damaged
			// base.
			count--;
			if (checkValidityOfTheBaseAfterRemoving(l)) {
				final LabeledIntTreeMap newMap = new LabeledIntTreeMap(this, optimize);
				mainInt2SetMap = newMap.mainInt2SetMap;
				base = newMap.base;
			}
		}
		return oldValue;
	}

	@SuppressWarnings("ClassReferencesSubclass")
	@Override
	public LabeledIntTreeMapView unmodifiable() {
		return new LabeledIntTreeMapView(this);
	}

	@Override
	public IntSet values() {
		final IntArraySet coll = new IntArraySet();
		for (final Object2IntMap<Label> mapI : mainInt2SetMap.values()) {
			for (final Entry<Label> i : mapI.object2IntEntrySet()) {
				coll.add(i.getIntValue());
			}
		}
		return coll;
	}

	/**
	 * If the removed label {@code l} is a component of the base, then the base is reset.
	 * <p>
	 * An experiment result on 2016-01-13 showed that using base there is a small improvement in the performance.
	 *
	 * @param l the input label
	 *
	 * @return true if {@code l} is a component of the base, false otherwise.
	 */
	private boolean checkValidityOfTheBaseAfterRemoving(final Label l) {
		final int lSize = l.size();
		if ((base.length == 0) || (lSize == 0) || (lSize != base.length) || l.containsUnknown()) {
			return false;
		}

		// l and base have same length.
		for (final char c : base) {
			if (!l.contains(c)) {
				return false;
			}
		}
		// l is a component of the base, and it was removed.
		base = emptyBase;
		return true;
	}

	/**
	 * Tries to add all given labeled values into the current map:
	 * <ol>
	 * <li>Given a set of labeled values to insert (all labels have the same length)
	 * <li>For each of them, compares all same-length labels already in the map with the current one looking for if there is one with same value and only one
	 * opposite literal (this allows the simplification of the two labels with a shorter one). In case of a positive search, shorten the current label to
	 * insert.
	 * <li>For each of the labeled values to insert (possibly updated), removes all labeled values in the map greater than it. Ad each round it tries to add
	 * labeled values of the same size.
	 * </ol>
	 *
	 * @param inputMap            contains all the elements that have to be inserted.
	 * @param inputMapLabelLength length of labels contained into inputMap
	 *
	 * @return true if any element of inputMap has been inserted into the map.
	 */
	private boolean insertAndSimplify(Object2IntMap<Label> inputMap, int inputMapLabelLength) {

		final ObjectArraySet<Label> toRemove = new ObjectArraySet<>();
		boolean add = false;

		while (inputMapLabelLength >= 0) {
			// All entries of inputMap should have label of same size.
			// currentMapLimitedToLabelOfNSize contains all the labeled values with label size = inputMapSize;
			final Object2IntMap<Label> currentMapLimitedToLabelOfNSize = mainInt2SetMap.get(inputMapLabelLength);
			final Object2IntMap<Label> toAdd = makeObject2IntMap();
			toAdd.defaultReturnValue(Constants.INT_NULL);
			toRemove.clear();

			if (currentMapLimitedToLabelOfNSize != null) {
				for (final Entry<Label> inputEntry : inputMap.object2IntEntrySet()) {
					final Label inputLabel = inputEntry.getKey();
					final int inputValue = inputEntry.getIntValue();

					// check if there is any labeled value with same value and only one opposite literal
					final ObjectSet<Entry<Label>> currentMapLimitedToLabelSizeEntrySet =
						new ObjectArraySet<>(currentMapLimitedToLabelOfNSize.object2IntEntrySet());
					for (final Entry<Label> entry : currentMapLimitedToLabelSizeEntrySet) {
						Label l1 = entry.getKey();
						final int v1 = entry.getIntValue();

						final Literal lit;
						// Management of two labels that differ for only one literal (one contains the straight one while the other contains the negated).
						// Such labels can be reduced in two different way.
						// 1) The label with the maximum value is always replaced with a labeled value where value is the same but the label does not contain
						// the different literal.
						// If both have the same value, the management is equivalent to 2) first part.
						// The disadvantage of this management is that is quite difficult to build base.
						// 2) If they have the same value, then they can be replaced with one only labeled value (same value) where label does not contain the
						// different literal.
						// If they haven't the same value, they are ignored and, in the following, they will constitute a base for the set.
						//
						// An experimental test showed that is 1) management makes the algorithm ~30% faster.
						// On 2019-03-22 I discovered that it is more important to have labels with fewer literals,
						// so Management 1 is more important!
						if (this.optimize) {
							/*
							 * Management 1)
							 */
							if ((lit = l1.getUniqueDifferentLiteral(inputLabel)) != null) {
								final int max = Math.max(inputValue, v1);
								// we can simplify (newLabel, newValue) and (v1,l1)
								// we maintain the pair with lower value
								// while we insert the one with greater value removing from its label 'lit'
								final Label labelWOLiteral = l1.remove(lit.getName());

								if (max == inputValue && max == v1) {
									//both values are equals, remove both
									toRemove.add(inputLabel);
									toRemove.add(l1);
									if (currentMapLimitedToLabelOfNSize.removeInt(l1) != Constants.INT_NULL) {
										count--;
									}
								} else {
									if (max == inputValue) {
										toRemove.add(inputLabel);
									} else {
										toRemove.add(l1);
										if (currentMapLimitedToLabelOfNSize.removeInt(l1) != Constants.INT_NULL) {
											count--;
										}
									}
								}
								toAdd.put(labelWOLiteral, max);
							}
						} else {
							/*
							 * Management 2)
							 */
							if (inputValue == v1 && (lit = l1.getUniqueDifferentLiteral(inputLabel)) != null) {
								// // we can simplify (newLabel, newValue) and (v1,l1) removing them and putting in map (v1/lit,l1)
								toRemove.add(inputLabel);
								toRemove.add(entry.getKey());
								if (Debug.ON) {
									if (LOG.isLoggable(Level.FINEST)) {
										LOG.log(Level.FINEST, "Label " + l1 + ", combined with label " + inputLabel +
										                      " induces a simplification. " + "Firstly, (" +
										                      inputLabel + ", " + inputValue + ") in removed.");
									}
								}
								l1 = l1.remove(lit.getName());
								if (l1.size() < 0) {
									throw new IllegalStateException(
										"There is no literal to remove, there is a problem in the code!");
								}
								if (Debug.ON) {
									if (LOG.isLoggable(Level.FINEST)) {
										LOG.log(Level.FINEST,
										        "Then, (" + l1 + ", " + v1 + ") is considering for adding at the end.");
									}
								}
								toAdd.put(l1, v1);
							}
						}
					}
				}
				if (currentMapLimitedToLabelOfNSize.size() == 0) {
					mainInt2SetMap.remove(inputMapLabelLength);
				}
			}
			for (final Label l : toRemove) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.log(Level.FINEST, "Label " + l + " is removed from inputMap.");
					}
				}
				inputMap.removeInt(l);
			}
			// inputMap has been updated. Now it contains all the elements that have to be inserted.
			for (final Entry<Label> entry : inputMap.object2IntEntrySet()) {
				removeAllValuesGreaterThan(entry.getKey(), entry.getIntValue());
				if (isBaseAbleToRepresent(entry)) {
					continue;
				}
				putForcibly(entry.getKey(), entry.getIntValue());
				add = true;
				if (makeABetterBase(entry)) {
					removeAllValuesGreaterThanBase();
				}
			}
			if (toAdd.size() != 0) {
				inputMap = toAdd;
				inputMapLabelLength--;
			} else {
				inputMapLabelLength = -1;
			}
		}
		return add;
	}

	/**
	 * Determines whether the value can be represented by any component of the base.<br> There are the following cases:
	 * <ol>
	 * <li>A component of the base can represent the labeled value {@code (l,v)} if {@code l} subsumes the component label and the {@code v} is
	 * greater or equal the component value.
	 * <li>If the entry has value that is greater that any value of the base, then the base can represent it.
	 * </ol>
	 * <p>
	 * An experiment result on 2016-01-13 showed that using base there is a small improvement in the performance.
	 *
	 * @param inputLabel the input label
	 * @param inputValue the input value
	 *
	 * @return true if {@code inputValue} is greater or equal than a base component value that is subsumed by
	 *    {@code inputLabel}. False otherwise.
	 */
	private boolean isBaseAbleToRepresent(final Label inputLabel, final int inputValue) {
		if (Arrays.equals(base, emptyBase)) {
			return false;
		}

		final Object2IntMap<Label> map1 = mainInt2SetMap.get(base.length);
		for (final Label baseLabel : Objects.requireNonNull(Label.allComponentsOfBaseGenerator(base))) {
			final int baseValue = map1.getInt(baseLabel);

			if (baseValue == Constants.INT_NULL) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.SEVERE)) {
						LabeledIntTreeMap.LOG.severe(
							"The base is not sound: base=" + Arrays.toString(base) + ". Map1=" + map1);
					}
				}
				base = emptyBase;
				return false;
				// throw new IllegalStateException("A base component has a null value. It is not possible.");
			}
			if (inputLabel.subsumes(baseLabel)) {
				if (inputLabel.size() == baseLabel.size()) {
					// inputLabel subsumes all the literals of baseLabel, and it has no more literals.
					// so, they are equal or unknown.
					return true;
				}
				// entry.setValue(baseValue);// case 6
				return inputValue >= baseValue;
			}
			if (inputLabel.isConsistentWith(baseLabel)) {
				if (inputValue < baseValue) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * @param entry the entry
	 *
	 * @return true if {@code inputValue} is greater or equal than a base component value that is subsumed by
	 *    {@code inputLabel}. False otherwise.
	 *
	 * @see #isBaseAbleToRepresent(Label, int)
	 */
	private boolean isBaseAbleToRepresent(final Entry<Label> entry) {
		return isBaseAbleToRepresent(entry.getKey(), entry.getIntValue());
	}

	/**
	 * If entry (label, value) determines a new (better) base, the base is updated.<br> An experiment result on
	 * 2016-01-13 showed that using base there is a small improvement in the performance.
	 *
	 * @param entry the entry
	 *
	 * @return true if (label, value) determine a new (better) base. If true, the base is update. False otherwise.
	 */
	private boolean makeABetterBase(final Entry<Label> entry) {
		if (entry.getIntValue() == Constants.INT_NULL) {
			return false;
		}

		final int n = entry.getKey().size();

		if (n == 0) {
			// The new labeled value (l,v) has universal label, the base is not more necessary!
			base = emptyBase;
			return true;
		}
		final Object2IntMap<Label> map1 = mainInt2SetMap.get(n);
		if (map1.size() < Math.pow(2.0, n)) // there are no sufficient elements!
		{
			return false;
		}
		final char[] baseCandidateColl = entry.getKey().getPropositions();
		for (final Label label1 : Objects.requireNonNull(Label.allComponentsOfBaseGenerator(baseCandidateColl))) {
			final int value = map1.getInt(label1);
			if (value == Constants.INT_NULL) {
				return false;
			}
		}
		base = baseCandidateColl;
		return true;
	}

	/**
	 * Remove all labeled values that subsume {@code l} and have values greater or equal to {@code i}.
	 *
	 * @param givenLabel the input label
	 * @param givenValue the new value
	 *
	 * @return true if one element at least has been removed, false otherwise.
	 */
	private boolean removeAllValuesGreaterThan(final Label givenLabel, final int givenValue) {
		if (givenLabel == null || givenValue == Constants.INT_NULL) {
			return false;
		}
		boolean removed = false;
		final int inputLabelSize = givenLabel.size();
		//since this.mainInt2SetMap can change during the cycle, its keyset must be stored at the start (see note
		// below).
		final IntSet labelLengthSet = new IntArraySet(mainInt2SetMap.keySet());
		for (final int labelLength : labelLengthSet) {
			if (labelLength < inputLabelSize) {
				continue;
			}
			final Object2IntMap<Label> internalMap = mainInt2SetMap.get(labelLength);
			// BE CAREFUL! Since it is necessary to remove (label and map),
			// it is not possible to use internalMap.keySet() directly
			// because removing an element in the map changes the key set, and it is possible to lose the checking of some label (the following
			// one a deleted element).
			// Iterator are not rightly implemented as 2019-03-30!
			// The last resource is to copy the labeled value set.
			final ObjectSet<Label> labelsOfInternalMap = new ObjectArraySet<>(internalMap.keySet());
			for (final Label currentLabel : labelsOfInternalMap) {
				final int currentValue = internalMap.getInt(currentLabel);
				if ((currentValue >= givenValue) && currentLabel.subsumes(givenLabel)) {
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.log(Level.FINEST,
							        "New label " + givenLabel + " induces a remove of (" + currentLabel + ", " +
							        currentValue + ")");
						}
					}
					internalMap.removeInt(currentLabel);
					count--;
					checkValidityOfTheBaseAfterRemoving(currentLabel);
					removed = true;
				}
			}
			if (internalMap.size() == 0) {//remove from the mainInt2SetMap
				mainInt2SetMap.remove(labelLength);
			}
		}
		return removed;
	}

	/**
	 * Remove all labeled values having each value greater than all values of base components consistent with it. An
	 * experiment result on 2016-01-13 showed that using base there is a small improvement in the performance.
	 *
	 * @return true if one element at least has been removed, false otherwise.
	 */
	private boolean removeAllValuesGreaterThanBase() {
		if ((base == null) || (base.length == 0)) {
			return false;
		}
		final LabeledIntTreeMap newMap = new LabeledIntTreeMap(optimize);
		// build the list of labeled values that form the base
		final ObjectArrayList<Entry<Label>> baseComponent = new ObjectArrayList<>((int) Math.pow(2, base.length));
		for (final Label l : Objects.requireNonNull(Label.allComponentsOfBaseGenerator(base))) {
			baseComponent.add(new AbstractObject2IntMap.BasicEntry<>(l, get(l)));
		}
		Label l1, lb;
		int v1, vb;
		boolean toInsert;
		for (final Object2IntMap<Label> map1 : mainInt2SetMap.values()) {
			for (final Entry<Label> entry : map1.object2IntEntrySet()) {
				l1 = entry.getKey();
				v1 = entry.getIntValue();
				toInsert = false;

				for (final Entry<Label> baseEntry : baseComponent) {
					lb = baseEntry.getKey();
					vb = baseEntry.getIntValue();
					if (l1.equals(lb)) {
						toInsert = true; // a base component has to be always insert!
						break;
					}
					if (l1.isConsistentWith(lb) && v1 < vb) {// isConsistent is necessary to manage cases like base =
						// {(b,3)(¬b,4)} l1={(a,1)}
						toInsert = true;
						break;
					}
				}
				if (toInsert) {
					newMap.putForcibly(l1, v1);
				}
			}
		}
		if (!newMap.equals(this)) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("Base changed: the old map " + this + " is substituted by " + newMap);
				}
			}
			mainInt2SetMap = newMap.mainInt2SetMap;
			count = newMap.count;
			return true;
		}
		return false;
	}

}
