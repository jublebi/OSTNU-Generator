// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

package it.univr.di.labeledvalue.lazy;

import it.unimi.dsi.fastutil.doubles.DoubleArraySet;
import it.unimi.dsi.fastutil.doubles.DoubleSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap.Entry;
import it.univr.di.Debug;
import it.univr.di.labeledvalue.Constants;
import it.univr.di.labeledvalue.Label;
import it.univr.di.labeledvalue.Literal;
import org.apache.commons.math3.fraction.Fraction;

import java.util.Arrays;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Simple implementation of {@link it.univr.di.labeledvalue.lazy.LabeledLazyWeightTreeMap} interface using LazyWieght instead of int.
 *
 * @author Roberto Posenato
 * @see LabeledLazyWeightTreeMap
 * @version $Id: $Id
 */
@SuppressWarnings("ALL")
public class LabeledLazyWeightTreeMap {

	/**
	 * A natural comparator for Entry&lt;Label&gt;.
	 * It orders considering the alphabetical order of Label.
	 */
	static public final Comparator<Entry<Label, LazyWeight>> entryComparator = (o1, o2) -> {
		if (o1 == o2)
			return 0;
		if (o1 == null)
			return -1;
		if (o2 == null)
			return 1;
		return o1.getKey().compareTo(o2.getKey());
	};

	/**
	 * Admissible lazy values as regular expression.
	 */
	static final String valueRE = "[ ;0-9∞" + Pattern.quote("-") + Pattern.quote(".") + "]+";// ; is for pair 'm; c' that represents linear function 'm ∂ + c'

	/** Constant <code>lazyWeightLabelSeparator=", "</code> */
	static public final String lazyWeightLabelSeparator = ", ";

	/**
	 * format of a labeled value as regular expression.
	 */
	static final String labeledValueRE = "(" + valueRE + lazyWeightLabelSeparator + Label.LABEL_RE + "|" + Label.LABEL_RE + lazyWeightLabelSeparator + valueRE
			+ ")";

	/**
	 * Matcher for a set of labeled values.
	 */
	static final Pattern patternLabelCharsRE = Pattern
			.compile(Pattern.quote("{")
					+ "("
					+ Pattern.quote(Constants.OPEN_PAIR) + labeledValueRE + Pattern.quote(Constants.CLOSE_PAIR)
					+ "[ ]*)*"
					+ Pattern.quote("}"));
	/**
	 * RE for splitting a list of labeled values.
	 */
	static final Pattern splitterEntry = Pattern
			.compile(Pattern.quote("{") + Pattern.quote("}") + "|[{" + Pattern.quote(Constants.OPEN_PAIR) + "]+|" + Pattern.quote(Constants.CLOSE_PAIR) + " ["
					+ Constants.OPEN_PAIR + "} ]*");

	/**
	 *
	 */
	static final Pattern splitterPair = Pattern.compile(", ");

	/**
	 *
	 */
	static final Pattern splitterLazyWeightPair = Pattern.compile("; ");

	/**
	 * empty base
	 */
	static private final char[] emptyBase = new char[0];

	/**
	 * logger
	 */
	static private final Logger LOG = Logger.getLogger(LabeledLazyWeightTreeMap.class.getName());

	/**
	 * <p>
	 * createLabeledLazyTreeMap.
	 * </p>
	 *
	 * @return a {@link it.univr.di.labeledvalue.lazy.LabeledLazyWeightTreeMap} object.
	 */
	public static LabeledLazyWeightTreeMap createLabeledLazyTreeMap() {
		return new LabeledLazyWeightTreeMap();
	}

	/**
	 * <p>
	 * createLabeledLazyTreeMap.
	 * </p>
	 *
	 * @param lim a {@link it.univr.di.labeledvalue.lazy.LabeledLazyWeightTreeMap} object.
	 * @return a {@link it.univr.di.labeledvalue.lazy.LabeledLazyWeightTreeMap} object.
	 */
	public static LabeledLazyWeightTreeMap createLabeledLazyTreeMap(LabeledLazyWeightTreeMap lim) {
		return new LabeledLazyWeightTreeMap(lim);
	}

	/**
	 * <p>
	 * entryAsString.
	 * </p>
	 *
	 * @param value a {@link it.univr.di.labeledvalue.lazy.LazyWeight} object.
	 * @param label a {@link it.univr.di.labeledvalue.Label} object.
	 * @return string representing the labeled value, i.e., "(value, label)"
	 */
	static final public String entryAsString(Label label, LazyWeight value) {
		if (label == null)
			return "";
		String sb = Constants.OPEN_PAIR +
				value.toString() +
				", " +
				label +
				Constants.CLOSE_PAIR;
		return sb;
	}

	/**
	 * Parse a string representing a LabeledLazyWeightTreeMap and return an object containing the labeled values represented by the string.<br>
	 * The format of the string is:
	 *
	 * <pre>
	 * \{[\(&lt;lazyWeight&gt;, &lt;key&gt;\) ]*\}
	 * </pre>
	 *
	 * where a lazyWeight can be an integer or a linear function (multiplier factor and constant, both integer, separated by a space), i.e.,
	 *
	 * <pre>
	 * &lt;integer&gt; | &lt;multiplier constant&gt;
	 * </pre>
	 *
	 * @param inputMap a {@link java.lang.String} object.
	 * @return a LabeledValueTreeMap object if <code>inputMap</code> represents a valid map, null otherwise.
	 */
	static public LabeledLazyWeightTreeMap parse(final String inputMap) {
		if (inputMap == null)
			return null;

		if (!patternLabelCharsRE.matcher(inputMap).matches()) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.WARNING)) {
					LOG.warning("Input string is not well-formed for representing a set of labeled lazy values: " + patternLabelCharsRE);
				}
			}
			return null;
		}

		final LabeledLazyWeightTreeMap newMap = new LabeledLazyWeightTreeMap();

		final String[] entryPair = splitterEntry.split(inputMap);
		// LOG.finest("EntryPairs: " + Arrays.toString(entryPair));
		Label l;
		int value;
		for (final String s : entryPair) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("s: " + s);
				}
			}
			if (s.length() != 0) {
				final String[] lazyWeightLabelPair = splitterPair.split(s);
				LOG.finest("lazyWeightLabelPair: " + Arrays.toString(lazyWeightLabelPair));
				// lazyWeightLabelPair[0] must be the lazy weight.
				// lazyWeightLabelPair[1] must be the label.
				// I manage also old format, in which the components are in inverted.
				l = Label.parse(lazyWeightLabelPair[1]);
				String[] lazyWeightPair = splitterLazyWeightPair.split(lazyWeightLabelPair[0]);
				if (l == null) {
					// May be it is in the old format?
					l = Label.parse(lazyWeightLabelPair[0]);
					if (l == null) {
						if (Debug.ON) {
							if (LOG.isLoggable(Level.WARNING)) {
								LOG.warning("Input string is not well-formed for representing a set of labeled lazy "
										+ "values: " + patternLabelCharsRE);
							}
						}
						return null;
					}
					lazyWeightPair = splitterLazyWeightPair.split(lazyWeightLabelPair[1]);
				}
				// just an integer
				if (lazyWeightPair[0].equals("-" + Constants.INFINITY_SYMBOL))
					value = Constants.INT_NEG_INFINITE;
				else
					value = Integer.parseInt(lazyWeightPair[0]);
				if (lazyWeightPair.length == 1) {
					// it is a number
					newMap.put(l, LazyNumber.get(value));
				} else {
					// a possible piece!
					int c;
					if (lazyWeightPair[1].equals("-" + Constants.INFINITY_SYMBOL))
						c = Constants.INT_NEG_INFINITE;
					else
						c = Integer.parseInt(lazyWeightPair[1]);
					if (value == 0) {
						// multiplier factor is 0, so it is a number in real
						newMap.put(l, LazyNumber.get(c));
					} else {
						newMap.put(l, new LazyPiece(new Fraction(Constants.INT_NULL), value, c, false));
					}
				}

			}
		}
		return newMap;
	}

	/**
	 * @param entry (label, value)
	 * @return string representing the labeled value, i.e., "(value, label)"
	 */
	static final String entryAsString(final Entry<Label, LazyWeight> entry) {
		if (entry == null)
			return "";
		return entryAsString(entry.getKey(), entry.getValue());
	}

	/**
	 * @return a map between int and Object2ObjectMap<Label, LazyWeight> object.
	 */
	private static final Int2ObjectMap<Object2ObjectMap<Label, LazyWeight>> makeInt2ObjectMap() {
		return new Int2ObjectArrayMap<>();
	}

	/**
	 * @return an Object2ObjectMap<Label, LazyWeight> object
	 */
	private static final Object2ObjectMap<Label, LazyWeight> makeObject2ObjectMap() {
		return new Object2ObjectArrayMap<>();// Object2IntRBTreeMap is better than Object2IntArrayMap when the set is larger than 5000 elements!
	}

	/**
	 * Set of propositions forming a base for the labels of the map.
	 */
	private char[] base;

	/**
	 * Design choice: the set of labeled values of this map is organized as a collection of sets each containing labels of the same length. This allows the
	 * label minimization task to be performed in a more systematic and efficient way. The efficiency has been proved comparing this implementation
	 * with one in which the map has been realized with a standard map and the minimization task determines the same length labels every time it needs it.
	 */
	private Int2ObjectMap<Object2ObjectMap<Label, LazyWeight>> mainInt2SetMap;

	/**
	 * Necessary constructor for the factory. The internal structure is built and empty.
	 */
	public LabeledLazyWeightTreeMap() {
		this.mainInt2SetMap = makeInt2ObjectMap();
		this.base = emptyBase;
	}

	/**
	 * Constructor to clone the structure. For optimization issue, this method clone only LabeledIntTreeMap object.
	 *
	 * @param lvm the LabeledValueTreeMap to clone. If lvm is null, this will be an empty map.
	 */
	public LabeledLazyWeightTreeMap(final LabeledLazyWeightTreeMap lvm) {
		this();
		if (lvm == null)
			return;
		this.base = lvm.base;
		for (final Entry<Label, LazyWeight> entry : lvm.entrySet()) {
			this.putForcibly(entry.getKey(), entry.getValue());
		}
	}

	/**
	 * <p>
	 * alreadyRepresents.
	 * </p>
	 *
	 * @param newLabel a {@link it.univr.di.labeledvalue.Label} object.
	 * @param newLW a {@link it.univr.di.labeledvalue.lazy.LazyWeight} object.
	 * @return true if the current map can represent the value. In positive case, an addition of the element does not change the map.
	 *         If returns false, then an addition of the value to the map would modify the map.
	 */
	public boolean alreadyRepresents(Label newLabel, LazyWeight newLW) {
		double valuePresented = getInt(newLabel);
		if (valuePresented > newLW.getValue())
			return false;// the newValue would simplify the map.
		if (valuePresented != Constants.INT_NULL && valuePresented < newLW.getValue())
			return true;
		/**
		 * Check if there is already a value in the map that represents the new value.
		 */
		final int newLabelSize = newLabel.size();

		for (int labelLenght : this.mainInt2SetMap.keySet()) {
			if (labelLenght > newLabelSize)
				continue;
			for (Entry<Label, LazyWeight> entry : this.mainInt2SetMap.get(labelLenght).object2ObjectEntrySet()) {
				final Label l1 = entry.getKey();
				final double v1 = entry.getValue().getValue();

				if (newLabel.subsumes(l1) && newLW.getValue() >= v1) {
					return true;
				}
			}
		}
		return (isBaseAbleToRepresent(newLabel, newLW));
	}

	/**
	 * <p>
	 * clear.
	 * </p>
	 */
	public void clear() {
		this.mainInt2SetMap.clear();
		this.base = emptyBase;
	}

	/**
	 * Up to 1000 items in the map it is better to use {@link #entrySet()} instead of {@link #keySet()} and, then, {@link #get(Label)}. With 1000 or more items,
	 * it is better to use {@link #keySet()} approach.
	 *
	 * @return a set view of all elements.
	 */
	public ObjectSet<Entry<Label, LazyWeight>> entrySet() {
		final ObjectSet<Entry<Label, LazyWeight>> coll = new ObjectArraySet<>();
		for (final Object2ObjectMap<Label, LazyWeight> mapI : this.mainInt2SetMap.values()) {
			coll.addAll(mapI.object2ObjectEntrySet());
		}
		return coll;
	}

	/**
	 * <p>
	 * entrySet.
	 * </p>
	 *
	 * @param setToReuse a {@link it.unimi.dsi.fastutil.objects.ObjectSet} object.
	 * @return a {@link it.unimi.dsi.fastutil.objects.ObjectSet} object.
	 */
	public ObjectSet<Entry<Label, LazyWeight>> entrySet(ObjectSet<Entry<Label, LazyWeight>> setToReuse) {
		setToReuse.clear();
		for (final Object2ObjectMap<Label, LazyWeight> mapI : this.mainInt2SetMap.values()) {
			setToReuse.addAll(mapI.object2ObjectEntrySet());
		}
		return setToReuse;
	}

	/** {@inheritDoc} */
	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof LabeledLazyWeightTreeMap lvm))
			return false;
		if (this.size() != lvm.size())
			return false;
		return this.entrySet().equals(lvm.entrySet());// The internal representation is not important!.
	}

	/**
	 * <p>
	 * get.
	 * </p>
	 *
	 * @param l a {@link it.univr.di.labeledvalue.Label} object.
	 * @return a {@link it.univr.di.labeledvalue.lazy.LazyWeight} object.
	 */
	public LazyWeight get(final Label l) {
		if (l == null)
			return null;
		final Object2ObjectMap<Label, LazyWeight> map1 = this.mainInt2SetMap.get(l.size());
		if (map1 == null)
			return null;
		return map1.get(l);
	}

	/**
	 * <p>
	 * getInt.
	 * </p>
	 *
	 * @param l a {@link it.univr.di.labeledvalue.Label} object.
	 * @return a double.
	 */
	public double getInt(final Label l) {
		LazyWeight lw = this.get(l);
		if (lw == null)
			return Constants.INT_NULL;
		return lw.getValue();
	}

	/**
	 * <p>
	 * getMaxValue.
	 * </p>
	 *
	 * @return a double.
	 */
	public double getMaxValue() {
		double max = Constants.INT_NEG_INFINITE;
		for (final Object2ObjectMap<Label, LazyWeight> mapI : this.mainInt2SetMap.values()) {
			for (LazyWeight j : mapI.values())
				if (max < j.getValue())
					max = j.getValue();
		}
		return (max == Constants.INT_NEG_INFINITE) ? Constants.INT_NULL : max;
	}

	/**
	 * <p>
	 * getMinValue.
	 * </p>
	 *
	 * @return a double.
	 */
	public double getMinValue() {
		double min = Constants.INT_POS_INFINITE;
		for (final Object2ObjectMap<Label, LazyWeight> mapI : this.mainInt2SetMap.values()) {
			for (LazyWeight j : mapI.values())
				if (min > j.getValue())
					min = j.getValue();
		}
		return (min == Constants.INT_POS_INFINITE) ? Constants.INT_NULL : min;
	}

	/**
	 * <p>
	 * getMinValueSubsumedBy.
	 * </p>
	 *
	 * @param l a {@link it.univr.di.labeledvalue.Label} object.
	 * @return a double.
	 */
	public double getMinValueSubsumedBy(final Label l) {
		if (l == null)
			return Constants.INT_NULL;
		double min = this.getInt(l);
		if (min == Constants.INT_NULL) {
			// the label does not exist, try all subsumed labels
			min = this.getInt(Label.emptyLabel);
			if (min == Constants.INT_NULL) {
				min = Constants.INT_POS_INFINITE;
			}
			double v1;
			Label l1 = null;
			int n = l.size();
			for (int i = 0; i < n; i++) {
				Object2ObjectMap<Label, LazyWeight> map = this.mainInt2SetMap.get(i);
				if (map == null)
					continue;
				for (final Entry<Label, LazyWeight> e : map.object2ObjectEntrySet()) {
					l1 = e.getKey();
					if (l.subsumes(l1)) {
						v1 = e.getValue().getValue();
						if (min > v1) {
							min = v1;
						}
					}
				}
			}
		}
		return (min == Constants.INT_POS_INFINITE) ? Constants.INT_NULL : min;
	}

	/**
	 * <p>
	 * getMinValueConsistentWith.
	 * </p>
	 *
	 * @param l a {@link it.univr.di.labeledvalue.Label} object.
	 * @return a double.
	 */
	public double getMinValueConsistentWith(final Label l) {
		if (l == null)
			return Constants.INT_NULL;
		double min = this.getInt(l);
		if (min == Constants.INT_NULL) {
			// the label does not exist, try all subsumed labels
			min = this.getInt(Label.emptyLabel);
			if (min == Constants.INT_NULL) {
				min = Constants.INT_POS_INFINITE;
			}
			double v1;
			Label l1 = null;
			int n = l.size();
			for (int i = 0; i < n; i++) {
				Object2ObjectMap<Label, LazyWeight> map = this.mainInt2SetMap.get(i);
				if (map == null)
					continue;
				for (final Entry<Label, LazyWeight> e : map.object2ObjectEntrySet()) {
					l1 = e.getKey();
					if (l.isConsistentWith(l1)) {
						v1 = e.getValue().getValue();
						if (min > v1) {
							min = v1;
						}
					}
				}
			}
		}
		return (min == Constants.INT_POS_INFINITE) ? Constants.INT_NULL : min;
	}

	/** {@inheritDoc} */
	@Override
	public int hashCode() {
		return this.mainInt2SetMap.hashCode();
	}

	/**
	 * <p>
	 * keySet.
	 * </p>
	 *
	 * @return a set view of all labels present into this map.
	 */
	public ObjectSet<Label> keySet() {
		ObjectSet<Label> coll = new ObjectArraySet<>();
		return keySet(coll);
	}

	/**
	 * <p>
	 * keySet.
	 * </p>
	 *
	 * @param setToReuse a {@link it.unimi.dsi.fastutil.objects.ObjectSet} object.
	 * @return a set view of all labels present into this map.
	 */
	public ObjectSet<Label> keySet(ObjectSet<Label> setToReuse) {
		setToReuse.clear();
		for (final Object2ObjectMap<Label, LazyWeight> mapI : this.mainInt2SetMap.values()) {
			setToReuse.addAll(mapI.keySet());
		}
		return setToReuse;
	}

	/**
	 * Adds the pair &lang;l,i&rang;.<br>
	 * Moreover, tries to eliminate all labels that are redundant.<br>
	 * <b>IMPORTANT!</b><br>
	 * This version of the method is very redundant but simple to check!
	 *
	 * @param newLabel a {@link it.univr.di.labeledvalue.Label} object.
	 * @param newLW a {@link it.univr.di.labeledvalue.lazy.LazyWeight} object.
	 * @return true if the LW has been inserted.
	 */
	public boolean put(final Label newLabel, LazyWeight newLW) {
		if ((newLabel == null) || (newLW == null) || alreadyRepresents(newLabel, newLW))
			return false;
		/**
		 * Step 1.
		 * The value is not already represented.
		 * It must be added.
		 * In the following, all values already present and implied by the new one are removed before adding it.
		 * Then, the new value is added in step 2.
		 */
		removeAllValuesGreaterThan(newLabel, newLW);

		/**
		 * Step 2.
		 * Insert the new value and check if it is possible to simplify with some other labels with same value and only
		 * one different literals.
		 */
		final Object2ObjectMap<Label, LazyWeight> a = makeObject2ObjectMap();
		a.put(newLabel, newLW);
		return this.insertAndSimplify(a, newLabel.size());
	}

	/**
	 * @param l a {@link it.univr.di.labeledvalue.Label} object.
	 * @param lw a {@link it.univr.di.labeledvalue.lazy.LazyWeight} object.
	 * @return previous value if present, Constants.INT_NULL otherwise;
	 */
	public double putForcibly(final Label l, final LazyWeight lw) {
		if ((l == null) || (lw == null))
			return Constants.INT_NULL;
		Object2ObjectMap<Label, LazyWeight> map1 = this.mainInt2SetMap.get(l.size());
		if (map1 == null) {
			map1 = makeObject2ObjectMap();
			this.mainInt2SetMap.put(l.size(), map1);
		}
		LazyWeight old = map1.put(l, lw);
		if (old != null)
			return old.getValue();
		return Constants.INT_NULL;
	}

	/**
	 * <p>
	 * remove.
	 * </p>
	 *
	 * @param l a {@link it.univr.di.labeledvalue.Label} object.
	 * @return a double.
	 */
	public double remove(final Label l) {
		if (l == null)
			return Constants.INT_NULL;

		final Object2ObjectMap<Label, LazyWeight> map1 = this.mainInt2SetMap.get(l.size());
		if (map1 == null)
			return Constants.INT_NULL;
		final double oldValue = map1.remove(l).getValue();
		if (oldValue != Constants.INT_NULL) {// && this.optimize) {
			// The base could have been changed. To keep things simple, for now it is better to rebuild all instead of to try to rebuild a possible damaged
			// base.
			if (this.checkValidityOfTheBaseAfterRemoving(l)) {
				final LabeledLazyWeightTreeMap newMap = new LabeledLazyWeightTreeMap(this);
				this.mainInt2SetMap = newMap.mainInt2SetMap;
				this.base = newMap.base;
			}
		}
		return oldValue;
	}

	/**
	 * @return the number of elements in the set.
	 */
	public int size() {
		int n = 0;
		for (Object2ObjectMap<Label, LazyWeight> map1 : this.mainInt2SetMap.values())
			n += map1.size();
		return n;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		final StringBuffer sb = new StringBuffer("{");
		final ObjectList<Entry<Label, LazyWeight>> sorted = new ObjectArrayList<>(this.entrySet());
		sorted.sort(entryComparator);
		for (final Entry<Label, LazyWeight> entry : sorted) {
			sb.append(entryAsString(entry) + " ");
		}
		sb.append("}");
		return sb.toString();
	}

	/**
	 * <p>
	 * values.
	 * </p>
	 *
	 * @return a {@link it.unimi.dsi.fastutil.doubles.DoubleSet} object.
	 */
	public DoubleSet values() {
		final DoubleArraySet coll = new DoubleArraySet();
		for (final Object2ObjectMap<Label, LazyWeight> mapI : this.mainInt2SetMap.values()) {
			for (Entry<Label, LazyWeight> i : mapI.object2ObjectEntrySet())
				coll.add(i.getValue().getValue());
		}
		return coll;
	}

	/**
	 * If the removed label <code>l</code> is a component of the base, then the base is reset.
	 * <p>
	 * An experiment result on 2016-01-13 showed that using base there is a small improvement in the performance.
	 *
	 * @param l
	 * @return true if <code>l</code> is a component of the base, false otherwise.
	 */
	private boolean checkValidityOfTheBaseAfterRemoving(final Label l) {
		if ((this.base.length == 0) || (l.size() == 0) || (l.size() != this.base.length) || l.containsUnknown())
			return false;

		// l and base have same length.
		for (char c : this.base) {
			if (!l.contains(c))
				return false;
		}
		// l is a component of the base, and it was removed.
		this.base = emptyBase;
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
	 * @param inputMap contains all the elements that have to be inserted.
	 * @param inputMapLabelLength length of labels contained into inputMap
	 * @return true if any element of inputMap has been inserted into the map.
	 */
	private boolean insertAndSimplify(Object2ObjectMap<Label, LazyWeight> inputMap, int inputMapLabelLength) {

		ObjectArraySet<Label> toRemove = new ObjectArraySet<>();
		boolean add = false;

		while (inputMapLabelLength >= 0) {
			// All entries of inputMap should have label of same size.
			// currentMapLimitedToLabelOfNSize contains all the labeled values with label size = inputMapSize;
			final Object2ObjectMap<Label, LazyWeight> currentMapLimitedToLabelOfNSize = this.mainInt2SetMap.get(inputMapLabelLength);
			final Object2ObjectMap<Label, LazyWeight> toAdd = makeObject2ObjectMap();
			toRemove.clear();

			if (currentMapLimitedToLabelOfNSize != null) {
				for (final Entry<Label, LazyWeight> inputEntry : inputMap.object2ObjectEntrySet()) {
					final Label inputLabel = inputEntry.getKey();
					final double inputValue = inputEntry.getValue().getValue();

					// check is there is any labeled value with same value and only one opposite literal
					for (final Entry<Label, LazyWeight> entry : currentMapLimitedToLabelOfNSize.object2ObjectEntrySet()) {
						Label l1 = entry.getKey();
						final double v1 = entry.getValue().getValue();

						Literal lit = null;
						// Management of two labels that differ for only one literal (one contains the straight one
						// while the other contains the negated).
						// Such labels can be reduced in two different way.
						// 1) If they have the same value, then they can be replaced with one only labeled value (same value) where label does not contain the
						// different literal.
						// If they haven't the same value, they are ignored and, in the following, they will constitute a base for the set.
						// 2) The label with the maximum value is always replaced with a labeled value where value is the same but the label does not contain
						// the different literal.
						// If both have the same value, the management is equivalent to 1) first part.
						// The disadvantage of this management is that is quite difficult to build base.
						//
						// An experimental test showed that is 2) management makes the algorithm ~30% faster.
						// On 2016-03-30 I discovered that with Management 2) there is a potential problem in the representation of situations like:
						// Current set={ (b,-1), (¬b,-2) }. Request to insert (¿b,-3).
						// Even value (¿b,-3) should not be inserted because the base is able to represent it, since
						// ¿b is consistent with b/¬b (extended
						// consistency)
						// the value (¿b,-3) is insert in both the two management.
						/**
						 * Management 1)
						 */
						if (inputValue == v1 && (lit = l1.getUniqueDifferentLiteral(inputLabel)) != null) {
							// we can simplify (newLabel, newValue) and (v1,l1) removing them and putting in map (v1/lit,l1)
							toRemove.add(inputLabel);
							toRemove.add(entry.getKey());
							if (Debug.ON) {
								if (LOG.isLoggable(Level.FINEST)) {
									LOG.log(Level.FINEST, "Label " + l1 + ", combined with label " + inputLabel + " induces a simplification. "
											+ "Firstly, (" + inputLabel + ", " + inputValue + ") in removed.");
								}
							}
							l1 = l1.remove(lit.getName());
							if (l1.size() < 0)
								throw new IllegalStateException("There is no literal to remove, there is a problem in the code!");
							if (Debug.ON) {
								if (LOG.isLoggable(Level.FINEST)) {
									LOG.log(Level.FINEST, "Then, (" + l1 + ", " + v1 + ") is considering for adding at the end.");
								}
							}
							toAdd.put(l1, entry.getValue());
						}
						/**
						 * Management 2)
						 */
						// if ((lit = l1.getUniqueDifferentLiteral(inputLabel)) != null) {
						// int max = (inputValue > v1) ? inputValue : v1;
						// // we can simplify (newLabel, newValue) and (v1,l1)
						// // we maintain the pair with lower value
						// // while we insert the one with greater value removing from its label 'lit'
						// Label labelWOlit = new Label(l1);
						// labelWOlit.remove(lit.getName());
						//
						// if (max == inputValue && max == v1) {
						// toRemove.add(inputLabel);
						// toRemove.add(l1);
						// } else {
						// if (max == inputValue) {
						// toRemove.add(inputLabel);
						// } else {
						// toRemove.add(l1);
						// }
						// }
						// toAdd.put(labelWOlit, max);
						// }
					}
				}
			}
			for (Label l : toRemove) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.log(Level.FINEST, "Label " + l + " is removed from inputMap.");
					}
				}
				inputMap.remove(l);
			}
			// inputMap has been updated. Now it contains all the elements that have to be inserted.
			for (final Entry<Label, LazyWeight> entry : inputMap.object2ObjectEntrySet()) {
				this.removeAllValuesGreaterThan(entry);
				if (this.isBaseAbleToRepresent(entry))
					continue;
				this.putForcibly(entry.getKey(), entry.getValue());
				add = true;
				if (this.makeABetterBase(entry))
					this.removeAllValuesGreaterThanBase();
			}
			if (toAdd.size() > 0) {
				inputMap = toAdd;
				inputMapLabelLength--;
			} else {
				inputMapLabelLength = -1;
			}
		}
		return add;
	}

	/**
	 * @param entry
	 * @return true if <code>inputValue</code> is greater or equal than a base component value that is subsumed by <code>inputLabel</code>. False otherwise.
	 */
	private final boolean isBaseAbleToRepresent(final Entry<Label, LazyWeight> entry) {
		return isBaseAbleToRepresent(entry.getKey(), entry.getValue());
	}

	/**
	 * Determines whether the value can be represented by any component of the base.<br>
	 * There are the following cases:
	 * <ol>
	 * <li>A component of the base can represent the labeled value <code>(l,v)</code> if <code>l</code> subsumes the component label and the <code>v</code> is
	 * greater or equal the component value.
	 * <li>If the entry has value that is greater that any value of the base, then the base can represent it.
	 * </ol>
	 * <p>
	 * An experiment result on 2016-01-13 showed that using base there is a small improvement in the performance.
	 *
	 * @param inputLabel
	 * @param inputLW
	 * @return true if <code>inputValue</code> is greater or equal than a base component value that is subsumed by <code>inputLabel</code>. False otherwise.
	 */
	private boolean isBaseAbleToRepresent(final Label inputLabel, final LazyWeight inputLW) {
		if (this.base == emptyBase)
			return false;

		final Object2ObjectMap<Label, LazyWeight> map1 = this.mainInt2SetMap.get(this.base.length);
		for (final Label baseLabel : Label.allComponentsOfBaseGenerator(this.base)) {
			final double baseValue = map1.get(baseLabel).getValue();

			if (baseValue == Constants.INT_NULL) {
				if (Debug.ON)
					if (LOG.isLoggable(Level.SEVERE)) {
						LabeledLazyWeightTreeMap.LOG.severe("The base is not sound: base=" + Arrays.toString(this.base) + ". Map1=" + map1);
					}
				this.base = emptyBase;
				return false;
				// throw new IllegalStateException("A base component has a null value. It is not possible.");
			}
			if (inputLabel.subsumes(baseLabel)) {
				// entry.setValue(baseValue);// case 6
				return inputLW.getValue() >= baseValue;
			}
			if (inputLabel.isConsistentWith(baseLabel)) {
				if (inputLW.getValue() < baseValue) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * If entry (label, value) determines a new (better) base, the base is updated.<br>
	 * An experiment result on 2016-01-13 showed that using base there is a small improvement in the performance.
	 *
	 * @param entry
	 * @return true if (label, value) determine a new (better) base. If true, the base is update. False otherwise.
	 */
	private boolean makeABetterBase(final Entry<Label, LazyWeight> entry) {
		if (entry.getValue().getValue() == Constants.INT_NULL)
			return false;

		final int n = entry.getKey().size();

		if (n == 0) {
			// The new labeled value (l,v) has universal label, the base is not more necessary!
			this.base = emptyBase;
			return true;
		}
		final Object2ObjectMap<Label, LazyWeight> map1 = this.mainInt2SetMap.get(n);
		if (map1.size() < Math.pow(2.0, n)) // there are no sufficient elements!
			return false;
		final char[] baseCandidateColl = entry.getKey().getPropositions();
		for (final Label label1 : Label.allComponentsOfBaseGenerator(baseCandidateColl)) {
			LazyWeight lw = map1.get(label1);
			if (lw == null)
				return false;
			double value = lw.getValue();
			if (value == Constants.INT_NULL)
				return false;
		}
		this.base = baseCandidateColl;
		return true;
	}

	/**
	 * Remove all labeled values that subsume <code>l</code> and have values greater or equal to <code>i</code>.
	 *
	 * @param entry
	 * @return true if one element at least has been removed, false otherwise.
	 */
	private boolean removeAllValuesGreaterThan(Entry<Label, LazyWeight> entry) {
		if (entry == null)
			return false;
		return removeAllValuesGreaterThan(entry.getKey(), entry.getValue());

	}

	/**
	 * Remove all labeled values that subsume <code>l</code> and have values greater or equal to <code>i</code>.
	 *
	 * @param inputLabel
	 * @param inputLW
	 * @return true if one element at least has been removed, false otherwise.
	 */
	private boolean removeAllValuesGreaterThan(final Label inputLabel, final LazyWeight inputLW) {
		if (inputLabel == null || inputLW == null)
			return false;
		boolean removed = false;
		final int inputLabelSize = inputLabel.size();
		for (int labelLenght : this.mainInt2SetMap.keySet()) {
			if (labelLenght < inputLabelSize)
				continue;
			final Object2ObjectMap<Label, LazyWeight> internalMap = this.mainInt2SetMap.get(labelLenght);
			// BE CAREFUL! Since it is necessary to remove, it is not possible to use internalMap.keySet() directly
			// because removing an element in the map changes the keyset, and it is possible to lose the checking of
			// some label (the following one a deleted element).
			// Iterator are not supported!
			// The last resource is to copy the labeled value set using object2ObjectEntrySet! :-(
			for (Entry<Label, LazyWeight> entry1 : internalMap.object2ObjectEntrySet()) {
				final Label currentLabel = entry1.getKey();
				final double currentValue = entry1.getValue().getValue();
				if (currentLabel.subsumes(inputLabel) && (currentValue >= inputLW.getValue())) {
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.log(Level.FINEST,
									"New (" + inputLabel + ", " + inputLW.getValue() + ") induces a remove of (" + currentLabel + ", " + currentValue + ")");
						}
					}
					internalMap.remove(currentLabel);
					this.checkValidityOfTheBaseAfterRemoving(currentLabel);
					removed = true;
				}
			}
		}
		return removed;
	}

	/**
	 * Remove all labeled values having each value greater than all values of base components consistent with it.
	 * An experiment result on 2016-01-13 showed that using base there is a small improvement in the performance.
	 *
	 * @return true if one element at least has been removed, false otherwise.
	 */
	private boolean removeAllValuesGreaterThanBase() {
		if ((this.base == null) || (this.base.length == 0))
			return false;
		final LabeledLazyWeightTreeMap newMap = new LabeledLazyWeightTreeMap();
		// build the list of labeled values that form the base
		final ObjectArrayList<Entry<Label, LazyWeight>> baseComponent = new ObjectArrayList<>((int) Math.pow(2, this.base.length));
		for (final Label l : Label.allComponentsOfBaseGenerator(this.base)) {
			baseComponent.add(new AbstractObject2ObjectMap.BasicEntry<>(l, this.get(l)));
		}
		Label l1, lb;
		double v1, vb;
		boolean toInsert = true;
		for (final Object2ObjectMap<Label, LazyWeight> map1 : this.mainInt2SetMap.values()) {
			for (final Entry<Label, LazyWeight> entry : map1.object2ObjectEntrySet()) {
				l1 = entry.getKey();
				v1 = entry.getValue().getValue();
				toInsert = false;

				for (final Entry<Label, LazyWeight> baseEntry : baseComponent) {
					lb = baseEntry.getKey();
					vb = baseEntry.getValue().getValue();
					if (l1.equals(lb)) {
						toInsert = true; // a base component has to be always insert!
						break;
					}
					if (l1.isConsistentWith(lb) && v1 < vb) {// isConsistent is necessary to manage cases like base = {(b,3)(¬b,4)} l1={(a,1)}
						toInsert = true;
						break;
					}
				}
				if (toInsert) {
					newMap.putForcibly(l1, entry.getValue());
				}
			}
		}
		if (!newMap.equals(this)) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("Base changed: the old map " + this + " is subsituted by " + newMap);
				}
			}
			this.mainInt2SetMap = newMap.mainInt2SetMap;
			return true;
		}
		return false;
	}

	/**
	 * <p>
	 * main.
	 * </p>
	 *
	 * @param args an array of {@link java.lang.String} objects.
	 */
	public static void main(final String[] args) {
		LabeledLazyWeightTreeMap actual = parse("{(30, a) (0; 25, ¬a) (0; 30, b) (25; 25, ¬b) }");

		LabeledLazyWeightTreeMap expected = new LabeledLazyWeightTreeMap();

		expected.put(Label.parse("a"), LazyNumber.get(30));
		expected.put(Label.parse("¬a"), new LazyPiece(Fraction.ONE, 0, 25, false));
		expected.put(Label.parse("b"), new LazyPiece(Fraction.ONE, 0, 30, false));
		expected.put(Label.parse("¬b"), new LazyPiece(Fraction.ONE, 25, 25, false));

		System.out.println("Actual " + actual);
		System.out.println("Expected " + expected);

		LazyPiece lp1 = new LazyPiece(Fraction.ONE, 10, -1, false);
		LazyPiece lp2 = new LazyPiece(Fraction.ONE, -4, 10, false);
		LazyMax lm = new LazyMax(Fraction.ONE, lp1, lp2, false, false, false);
		LazyCombiner ls = new LazyCombiner(Fraction.ONE, lp1, lm, false, false, false);
		expected.put(Label.parse("b"), ls);
		expected.put(Label.parse("c"), lm);

		// System.out.println("Expected with a combiner " + expected);
		// String s = expected.toString().replaceAll("\\([-0-9\\.]*\\[Piece ", "(").replaceAll(" \\* ∂ \\+", ";").replaceAll("\\],", ",");
		// System.out.println("Expected with a combiner cleaned 1: " + s);
		// s = s.replaceAll("\\(([-0-9\\.]*)\\[Sum [-0-9\\.SumPiecMax;\\[\\] ]*, ([¬a-zA-Z]*)\\) ", "($1, $2) ");
		// System.out.println("Expected with a combiner cleaned 2: " + s);
		// s = s.replaceAll("\\(([-0-9\\.]*)\\[Max [-0-9\\.SumPiecMax;\\[\\] ]*, ([¬a-zA-Z]*)\\) ", "($1, $2) ");
		// System.out.println("Expected with a combiner cleaned 3: " + s);

		System.out.println("Combiner at 1:\t" + ls);
		System.out.println("Max at 1:\t" + lm);
		ls.setX(new Fraction(1, 2));
		lm.setX(new Fraction(1, 2));
		System.out.println("\n\nCombiner at 1/2:\t" + ls);
		System.out.println("Max at 1/2:\t" + lm);
	}

}
