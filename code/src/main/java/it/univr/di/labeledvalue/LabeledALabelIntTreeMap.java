// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

package it.univr.di.labeledvalue;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import it.unimi.dsi.fastutil.objects.AbstractObject2IntMap.BasicEntry;
import it.unimi.dsi.fastutil.objects.*;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.univr.di.Debug;

import javax.annotation.Nullable;
import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Allows to manage conjoin-upper-case values that are also associated to propositional labels. Labeled  values
 * ({@link Label}) are grouped by alphabetic labels {@link ALabel}. Each group of labeled values is represented as
 * {@link LabeledIntTreeMap}.<br> Therefore, such a class realizes the map
 * {@link ALabel}-->{@link LabeledIntTreeMap}.<br>
 * <p>
 * Be careful!<br> Since lower-case value are singular for each edge, it not convenient to represent it as TreeMap. A
 * specialized class has been developed to represent such values: {@link LabeledLowerCaseValue}.
 * <p>
 * At first time, I made some experiments for evaluating if it is better to use a Object2ObjectRBTreeMap or a
 * ObjectArrayMap for representing the internal map. The below table shows that for very small network, the two
 * implementation are almost equivalent. So, ObjectArrayMap was chosen. *
 * <table border="1">
 * <caption>Execution time (ms) for some operations w.r.t the core data structure of the class.</caption>
 * <tr>
 * <th>Operation</th>
 * <th>Using Object2ObjectRBTreeMap (ms)</th>
 * <th>Using ObjectArrayMap (ms)</th>
 * </tr>
 * <tr>
 * <td>Create 1st map</td>
 * <td>0.370336085</td>
 * <td>0.314329559</td>
 * </tr>
 * <tr>
 * <td>min value</td>
 * <td>0.017957532</td>
 * <td>0.014711536</td>
 * </tr>
 * <tr>
 * <td>Retrieve value</td>
 * <td>0.001397098</td>
 * <td>0.000600641</td>
 * </tr>
 * <tr>
 * <td>Simplification</td>
 * <td>~0.183388</td>
 * <td>~0.120013</td>
 * </tr>
 * </table>
 * <p>
 * In October 2017, I verified that even with medium size network, some edges can contain around 5000 labeled UC values.
 * So, I tested the two implementation again considering up to 10000 labeled UC values.
 * It resulted that up to 1000 values, the two implementation show still almost equivalent performance, BUT when the keys are 5000, using ObjectArrayMap
 * retrieve the keys requires more than ONE hour, while using Object2ObjectRBTreeMap it requires almost 96. ms!!!
 * Details using Object2ObjectRBTreeMap:
 *
 * <pre>
 * Time to retrieve 50 elements using entrySet(): ---
 * Time to retrieve 50 elements using keySet(): 0.012000000000000004ms
 *
 * Time to retrieve 100 elements using entrySet(): ---
 * Time to retrieve 100 elements using keySet(): 0.006000000000000003ms
 *
 * Time to retrieve 1000 elements using entrySet(): 0.045ms
 * Time to retrieve 1000 elements using keySet(): 0.034ms
 * The difference is 0.025000000000000015 ms. It is better to use: keySet() approach.
 *
 * Time to retrieve 5000 elements using entrySet(): 0.9623700000000001ms
 * Time to retrieve 5000 elements using keySet(): 0.352ms
 * The difference is 0.6139400000000002 ms. It is better to use: keySet() approach.
 *
 * Time to retrieve 10000 elements using entrySet(): --
 * Time to retrieve 10000 elements using keySet(): 1.292ms
 * </pre>
 * Considering then, RB Tree instead of RB Tree:
 *
 * <pre>
 * Time to retrieve 50 elements using keySet(): 0.012000000000000002ms
 *
 * Time to retrieve 100 elements using keySet(): 0.007ms
 *
 * Time to retrieve 1000 elements using entrySet(): ---
 * Time to retrieve 1000 elements using keySet(): 0.038ms
 * The difference is 0.025000000000000015 ms. It is better to use: keySet() approach.
 *
 * Time to retrieve 5000 elements using entrySet(): ---
 * Time to retrieve 5000 elements using keySet(): 0.388ms
 * The difference is 0.6139400000000002 ms. It is better to use: keySet() approach.
 *
 * Time to retrieve 10000 elements using entrySet(): --
 * Time to retrieve 10000 elements using keySet(): 1.314ms
 * </pre>
 *
 * <b>All code for testing is in LabeledALabelIntTreeMapTest class (not public available).</b>
 *
 * @author Roberto Posenato
 * @version $Rev: 851 $
 */
@SuppressWarnings("UnusedReturnValue")
public class LabeledALabelIntTreeMap implements Serializable {

	/**
	 * A read-only view of an object
	 *
	 * @author posenato
	 */
	public static class LabeledALabelIntTreeMapView extends LabeledALabelIntTreeMap {
		/**
		 *
		 */
		@Serial
		private static final long serialVersionUID = 1L;

		/**
		 * @param inputMap the input map
		 */
		public LabeledALabelIntTreeMapView(LabeledALabelIntTreeMap inputMap) {
			map = inputMap.map;
		}


		/**
		 * Object Read-only. It does nothing.
		 */
		@Override
		public boolean mergeTriple(Label newLabel, ALabel newAlabel, int newValue, boolean force) {
			return false;
		}

		/**
		 * Object Read-only. It does nothing.
		 */
		@Override
		public boolean mergeTriple(Label l, ALabel p, int i) {
			return false;
		}

		/**
		 * Object Read-only. It does nothing.
		 */
		@Override
		public boolean mergeTriple(String label, ALabel p, int i) {
			return false;
		}

		/**
		 * Object Read-only. It does nothing.
		 */
		@Override
		public boolean mergeTriple(String label, ALabel p, int i, boolean force) {
			return false;
		}

		/**
		 * Object Read-only. It does nothing.
		 */
		@Nullable
		@Override
		public LabeledIntTreeMap put(ALabel alabel, LabeledIntMap labeledValueMap) {
			return null;
		}

		/**
		 * Object Read-only. It does nothing.
		 */
		@Override
		public boolean putTriple(Label l, ALabel p, int i) {
			return false;
		}

		/**
		 * Object Read-only. It does nothing.
		 */
		@Override
		public int remove(Label l, ALabel p) {
			return Constants.INT_NULL;
		}
	}

	/**
	 * Keyword \w because it is necessary to accept also node names!
	 */
	@SuppressWarnings("RegExpRedundantEscape")
	static final String labelCharsRE =
		ALabelAlphabet.ALETTER + ALabel.ALABEL_SEPARATORstring + ",\\-" + Constants.NOTstring +
		Constants.EMPTY_LABELstring + Constants.INFINITY_SYMBOLstring + Constants.UNKNOWNstring +
		Constants.EMPTY_UPPER_CASE_LABELstring + Literal.PROPOSITIONS;
	/**
	 * Matcher for RE
	 */
	@SuppressWarnings("RegExpRedundantEscape")
	static final Pattern patternLabelCharsRE = Pattern.compile("\\{[\\(" + labelCharsRE + "\\) ]*\\}");
	private static final Pattern PARENTESIS_PATTERN = Pattern.compile("[{}]");

	/**
	 * logger
	 */
	private static final Logger LOG = Logger.getLogger(LabeledALabelIntTreeMap.class.getName());

	/**
	 *
	 */
	@Serial
	private static final long serialVersionUID = 2L;

	/**
	 * <p>
	 * entryAsString.
	 * </p>
	 *
	 * @param label    the input label
	 * @param value    the input value
	 * @param nodeName this name is printed as it is. This method is necessary for saving the values of the map in a file.
	 *
	 * @return the canonical representation of the triple (as stated in ICAPS/ICAART papers), i.e. {@link Constants#OPEN_PAIR}Alabel, value,
	 * 	label{@link Constants#CLOSE_PAIR}
	 */
	static public String entryAsString(Label label, int value, ALabel nodeName) {
		return Constants.OPEN_PAIR + nodeName + ", " + Constants.formatInt(value) + ", " + label + Constants.CLOSE_PAIR;
	}

	/**
	 * Parse a string representing a LabeledValueTreeMap and return an object containing the labeled values represented
	 * by the string.<br> The format of the string is given by the method {@link #toString()}. For historical reasons,
	 * the method is capable to parse two different map
	 * format:{@code "{[(&lang;label&rang;, &lang;Alabel&rang;, &lang;value&rang;) ]*}} or
	 * {@code "{[(&lang;Alabel&rang;, &lang;value&rang;, &lang;label&rang;) ]*}"}, where [a]* is a meta constructor for
	 * saying zero o more 'a'.
	 *
	 * @param arg                  a {@link String} object.
	 * @param alphabet             the alphabet to use to code the labels
	 * @param labeledValueMapImple the class used for storing labeled values. If null, it is
	 *                             {@link LabeledIntMapSupplier#DEFAULT_LABELEDINTMAP_CLASS}.
	 *
	 * @return a LabeledPairMap object if args represents a valid map, null otherwise.
	 */
	@Nullable
	public static LabeledALabelIntTreeMap parse(String arg, ALabelAlphabet alphabet,
	                                            final Class<? extends LabeledIntMap> labeledValueMapImple) {
		// final Pattern splitterNode = Pattern.compile("〈|; ");
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Begin parse: " + arg);
			}
		}
		if ((arg == null) || (arg.length() < 3)) {
			return null;
		}

		if (!patternLabelCharsRE.matcher(arg).matches()) {
			return null;
		}
		final LabeledALabelIntTreeMap newMap = new LabeledALabelIntTreeMap(labeledValueMapImple);

		arg = PARENTESIS_PATTERN.matcher(arg).replaceAll("");
		// arg = arg.substring(1, arg.length() - 2);
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Before split: '" + arg + "'");
			}
		}
		@SuppressWarnings("RegExpSingleCharAlternation") final Pattern splitterEntry = Pattern.compile("\\)|\\(");
		final String[] entryThreesome = splitterEntry.split(arg);
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("EntryThreesome: " + Arrays.toString(entryThreesome));
			}
		}

		final Pattern splitterTriple = Pattern.compile(", ");
		if (alphabet == null) {
			alphabet = new ALabelAlphabet();
		}
		int j;
		String labelStr, aLabelStr, valueStr;
		for (final String s : entryThreesome) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("s: '" + s + "'");
				}
			}
			if (s.length() > 1) {// s can be empty or a space.
				final String[] triple = splitterTriple.split(s);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("triple: " + Arrays.toString(triple));
					}
				}
				Label l = Label.parse(triple[2]);
				if (l == null) {
					// probably it is the old format
					labelStr = triple[0];
					aLabelStr = triple[1];
					valueStr = triple[2];
				} else {
					// new format
					aLabelStr = triple[0];
					valueStr = triple[1];
					labelStr = triple[2];
				}
				if (l == null) {
					l = Label.parse(labelStr);
				}
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("Label: " + l);
					}
				}
				if (valueStr.equals("-" + Constants.INFINITY_SYMBOLstring)) {
					j = Constants.INT_NEG_INFINITE;
				} else {
					j = Integer.parseInt(valueStr);
				}
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("Value: " + j);
					}
				}
				// LabeledNode is represented as " 〈<id>; {}; Obs: null〉 "
				// final String nodePart = labLitInt[1];//splitterNode.split(labLitInt[1]);
				// System.out.println(aLabelStr);
				final ALabel node = ALabel.parse(aLabelStr, alphabet);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("LabeledNode: " + node);
					}
				}

				newMap.mergeTriple(l, node, j, false);
			}
		}
		return newMap;
	}
	// ObjectArrayMap is not suitable when the map is greater than 1000 values!
	/**
	 * Data structure.
	 * <ol>
	 * <li>A Upper/Lower Case value is a pair (nodeName, value) where nodeName is a name of a node
	 * and can be written either in all UPPER case or in all lower case.
	 * Such kind of constraint has been introduced by Morris Muscettola 2005.
	 * <li>A labeled Upper/Lower Case value is a pair (nodeName, (proposition_label, value)), where proposition_label
	 * represents scenario where value holds.
	 * Such kind of constraint has been introduced by Hunsberger, Combi, and Posenato in 2012.
	 * <li>Each proposition_label is a conjunction of literals, i.e., of type {@link Label}.</li>
	 * <li>Since there may be more pairs with the same 'nodeName', a labeled Upper/Lower Case value is as a map of (nodeName, LabeledIntMap). See
	 * {@link LabeledIntMap}.
	 * <li>In 2017-10, nodeName has been substituted by Alabel. ALabel represent the name of a node or a conjunction of node names. Such modification has been
	 * introduced because CSTNU DC checking algorithm requires such kind of values.
	 * <li>Since the introduction of ALabel, we suggest to use two @{link {@link LabeledALabelIntTreeMap}. One to represent the upper-case values.
	 * The other for lower-case ones.
	 * </ol>
	 */
	protected Object2ObjectRBTreeMap<ALabel, LabeledIntMap> map;
	/**
	 * Labeled value class used in the class.
	 */
	Class<? extends LabeledIntMap> labeledValueMapImpl;
	/**
	 * Number of elements
	 */
	private int count;

	/**
	 * Constructor to clone the structure. All internal maps will be independent of lvm ones while elements of maps will
	 * be shared. The motivation is that usually a Label inside a map is managed as read-only.
	 *
	 * @param lvm                  the map to clone. If null, 'this' will be an empty map.
	 * @param labeledValueMapImple the class used for storing labeled values. If null, it is
	 *                             {@link LabeledIntMapSupplier#DEFAULT_LABELEDINTMAP_CLASS}.
	 */
	public LabeledALabelIntTreeMap(final LabeledALabelIntTreeMap lvm,
	                               final Class<? extends LabeledIntMap> labeledValueMapImple) {
		this(labeledValueMapImple);
		if (lvm == null) {
			return;
		}
		for (final ALabel alabel : lvm.keySet()) {
			final LabeledIntMap map1 = (new LabeledIntMapSupplier<>(this.labeledValueMapImpl)).get(lvm.get(alabel));
			map.put(alabel, map1);
			count += map1.size();
		}
	}

//	/**
//	 * Constructor to clone the structure. All internal maps will be independent of lvm ones while elements of maps will
//	 * be shared. The motivation is that usually a Label inside a map is managed as read-only.
//	 *
//	 * @param lvm the map to clone. If null, 'this' will be an empty map.
//	 */
//	public LabeledALabelIntTreeMap(final LabeledALabelIntTreeMap lvm) {
//		this(lvm, true);
//	}

	/**
	 * Simple constructor. The internal structure is built and empty.
	 *
	 * @param labeledValueMapImple the class used for storing labeled values. If null, it is {@link LabeledIntMapSupplier#DEFAULT_LABELEDINTMAP_CLASS}.
	 */
	public LabeledALabelIntTreeMap(final Class<? extends LabeledIntMap> labeledValueMapImple) {
		map = new Object2ObjectRBTreeMap<>();
		count = 0;
		this.labeledValueMapImpl = (labeledValueMapImple == null) ? LabeledIntMapSupplier.DEFAULT_LABELEDINTMAP_CLASS
		                                                          : labeledValueMapImple;
	}


	/*
	 * @return a set view of this map. In particular, it returns a set of (ALabel, LabeledIntTreeMap) objects.<br>
	 *         Be careful: returned LabeledIntTreeMap(s) are not a copy but the maps inside this object.
	 *         THIS METHOD HAS NOT A GOOD PERFORMANCE
	 * public ObjectSet<Entry<ALabel, LabeledIntTreeMap>> entrySet() {
	 *         return this.map.entrySet();
	 * }
	 */

	/**
	 * Simple constructor. The internal structure is built and empty.
	 */
	private LabeledALabelIntTreeMap() {
	}

	/**
	 * @param newLabel  it must be not null
	 * @param newAlabel it must be not null
	 * @param newValue  the new value
	 *
	 * @return true if the current map can represent the value. In positive case, an add of the element does not change
	 * 	the map. If returns false, then the adding of the value to the map would modify the map.
	 */
	public boolean alreadyRepresents(final Label newLabel, final ALabel newAlabel, final int newValue) {
		final LabeledIntMap map1 = map.get(newAlabel);
		if (map1 != null && map1.alreadyRepresents(newLabel, newValue)) {
			return true;
		}
		/*
		 * Check if there is already a value in the map having shorter ALabel that can represent the new value.
		 */
		final int newALabelSize = newAlabel.size();
		for (final ALabel otherALabel : keySet()) {
			if (newALabelSize <= otherALabel.size() || !newAlabel.contains(otherALabel)) {
				continue;
			}
			final LabeledIntMap labeledValuesOfOtherALabel = get(otherALabel);
			if (labeledValuesOfOtherALabel.alreadyRepresents(newLabel, newValue)) {
				// a smaller conjuncted upper case value map already contains the input value
				return true;
			}
		}
		return false;
	}

	/**
	 *
	 */
	public void clear() {
		map.clear();
		count = 0;
	}

	@Override
	public boolean equals(final Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof final LabeledALabelIntTreeMap lvm)) {
			return false;
		}
		return map.equals(lvm.map);// this equals checks the size... so NO empty pair (key, {}) cannot be stored!
	}

	/**
	 * @param alabel the input label
	 *
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
	 */
	public final LabeledIntMap get(final ALabel alabel) {
		return map.get(alabel);
	}

	/**
	 * @return the minimal value of this map not considering upper/lower case label (node label). If the map is null,
	 * 	returns the entry ({@link Label#emptyLabel}, ({@link ALabel#emptyLabel}, {@link Constants#INT_NULL})).
	 */
	@SuppressFBWarnings(value = "DLS_DEAD_LOCAL_STORE", justification = "v is used.")
	public Object2ObjectMap.Entry<Label, Entry<ALabel>> getMinValue() {
		if (size() == 0) {
			return new AbstractObject2ObjectMap.BasicEntry<>(Label.emptyLabel,
			                                                 new BasicEntry<>(ALabel.emptyLabel, Constants.INT_NULL));
		}
		int min = Integer.MAX_VALUE;
		int v;
		Entry<Label> vEntry;
		ALabel aMin = ALabel.emptyLabel;
		Label lMin = Label.emptyLabel;
		for (final ALabel alabel : keySet()) {
			final LabeledIntMap map1 = get(alabel);
			if (map1 != null) {
				vEntry = map1.getMinLabeledValue();
				v = vEntry.getIntValue();
				if (v != Constants.INT_NULL && v < min) {
					min = v;
					aMin = alabel;
					lMin = vEntry.getKey();
				}
			}
		}
		return new AbstractObject2ObjectMap.BasicEntry<>(lMin, new BasicEntry<>(aMin, min));
	}

	/**
	 * Returns the value associated to {@code (l, p)} if it exists, otherwise the minimal value among all labels
	 * consistent with {@code (l, p)}.
	 *
	 * @param l if it is null, {@link Constants#INT_NULL} is returned.
	 * @param p if it is null or empty, {@link Constants#INT_NULL} is returned.
	 *
	 * @return the value associated to the {@code (l, p)} if it exists or the minimal value among values associated to
	 * 	labels consistent by {@code l}. If no labels are subsumed by {@code l}, {@link Constants#INT_NULL} is returned.
	 */
	public int getMinValueConsistentWith(final Label l, final ALabel p) {
		if ((l == null) || (p == null))// || p.isEmpty())
		{
			return Constants.INT_NULL;
		}
		final LabeledIntMap map1 = map.get(p);
		if (map1 == null) {
			return Constants.INT_NULL;
		}
		return map1.getMinValueConsistentWith(l);
	}

	/**
	 * @param l a {@link Label} object.
	 * @param p a {@link ALabel} representing the upper/lower case label (node label).
	 *
	 * @return the value associate to the key (label, p) if it exits, {@link Constants#INT_NULL} otherwise.
	 */
	public int getValue(final Label l, final ALabel p) {
		if ((l == null) || (p == null))// || p.isEmpty())
		{
			return Constants.INT_NULL;
		}
		final LabeledIntMap map1 = map.get(p);
		if (map1 == null) {
			return Constants.INT_NULL;
		}
		return map1.get(l);
	}

	@Override
	public int hashCode() {
		return map.hashCode();
	}

	/**
	 * <p>
	 * isEmpty.
	 * </p>
	 *
	 * @return true if the map does not contain any labeled value.
	 */
	public final boolean isEmpty() {
		return size() == 0;
	}

	/**
	 * @return a set view of all a-labels present into this map.
	 */
	public ObjectSet<ALabel> keySet() {
		return map.keySet();
	}

	/**
	 * <p>
	 * labelSet.
	 * </p>
	 *
	 * @return a set of all labels present into this map.
	 */
	public ObjectSet<Label> labelSet() {
		final ObjectSet<Label> labelSet = new ObjectRBTreeSet<>();
		for (final LabeledIntMap localMap : map.values()) {
			labelSet.addAll(localMap.keySet());
		}
		return labelSet;
	}

	/**
	 * Merges a label case value {@code (p,l,i)}.
	 * <p>
	 * The value is insert if there is not a labeled value in the set with label {@code (l,p)} or it is present with a value higher than {@code i}.
	 * <p>
	 * The method can remove or modify other labeled values of the set in order to minimize the labeled values present guaranteeing that no info is lost.
	 *
	 * @param newLabel  a {@link Label} object.
	 * @param newAlabel a case name.
	 * @param newValue  the new value.
	 * @param force     true if the value has to be stored without label optimization.
	 *
	 * @return true if the triple is stored, false otherwise.
	 */
	public boolean mergeTriple(final Label newLabel, final ALabel newAlabel, final int newValue, final boolean force) {

		if (!force && alreadyRepresents(newLabel, newAlabel, newValue)) {
			return false;
		}
		final int prioriNewAlabelMapSize;
		final int newAlabelSize = newAlabel.size();
		LabeledIntMap newAlabelMap = map.get(newAlabel);
		if (newAlabelMap == null) {
			newAlabelMap = (new LabeledIntMapSupplier<>(this.labeledValueMapImpl)).get();
			map.put(ALabel.clone(newAlabel), newAlabelMap);
			prioriNewAlabelMapSize = 0;
		} else {
			prioriNewAlabelMapSize = newAlabelMap.size();
		}

		final boolean added;
		if (force) {
			newAlabelMap.putForcibly(newLabel, newValue);
			added = true;
		} else {
			added = newAlabelMap.put(newLabel, newValue);
		}

		// update the count
		final boolean newAlabelModifiedTheAlreadyPresentMap = prioriNewAlabelMapSize == newAlabelMap.size();
		count += newAlabelMap.size() - prioriNewAlabelMapSize;

		if (force) {
			return true;
		}
		/*
		 * 2017-10-31
		 * Algorithm removes all a-labeled values that will become redundant after the insertion of the input a-labeled value.
		 * The a-label removed contain newALabel strictly.
		 * I verified that following optimization reduces global computation time.
		 */
		final ObjectSet<Entry<Label>> newAlabelEntrySet = newAlabelMap.entrySet();
		LabeledIntMap otherLabelValueMap;
		for (final ALabel otherALabel : keySet()) {
			if (otherALabel.equals(newAlabel) || otherALabel.size() < newAlabelSize ||
			    !otherALabel.contains(newAlabel)) {
				continue;
			}
			otherLabelValueMap = get(otherALabel);

			// Check only a-labels that contain newALabel strictly.
			for (final Entry<Label> entry : otherLabelValueMap.entrySet()) {// entrySet read-only
				final Label otherLabel = entry.getKey();
				final int otherValue = entry.getIntValue();

				if (newAlabelModifiedTheAlreadyPresentMap) {
					// it is necessary to check all values in the newAlabelMap
					for (final Entry<Label> inputEntry : newAlabelEntrySet) {
						final Label inputLabel = inputEntry.getKey();
						final int inputValue = inputEntry.getIntValue();
						if (otherLabel.subsumes(inputLabel) && otherValue >= inputValue) {
							remove(otherLabel, otherALabel);
						}
					}
				} else {
					if (otherLabel.subsumes(newLabel) && otherValue >= newValue) {
						remove(otherLabel, otherALabel);
					}
				}
			}
		}
		return added;
	}

	/**
	 * <p>
	 * mergeTriple.
	 * </p>
	 *
	 * @param l the {@link Label} object.
	 * @param p the {@link String} object.
	 * @param i the value to merge.
	 *
	 * @return see {@link #mergeTriple(Label, ALabel, int, boolean)}
	 *
	 * @see #mergeTriple(Label, ALabel, int, boolean)
	 */
	public boolean mergeTriple(final Label l, final ALabel p, final int i) {
		return mergeTriple(l, p, i, false);
	}

	/**
	 * Wrapper method. It calls mergeTriple(label, p, i, false);
	 *
	 * @param label a {@link String} object.
	 * @param p     a {@link String} object.
	 * @param i     the new value.
	 *
	 * @return see {@link #mergeTriple(String, ALabel, int, boolean)}
	 *
	 * @see #mergeTriple(String, ALabel, int, boolean)
	 */
	public boolean mergeTriple(final String label, final ALabel p, final int i) {
		return mergeTriple(label, p, i, false);
	}

	/**
	 * Wrapper method to {@link #mergeTriple(Label, ALabel, int, boolean)}. 'label' parameter is converted to a Label
	 * before calling {@link #mergeTriple(Label, ALabel, int, boolean)}.
	 *
	 * @param label a {@link String} object.
	 * @param p     a {@link String} object.
	 * @param i     the new value.
	 * @param force true if the value has to be stored without label optimization.
	 *
	 * @return true if the triple is stored, false otherwise.
	 */
	public boolean mergeTriple(final String label, final ALabel p, final int i, final boolean force) {
		if ((label == null) || (p == null) || (i == Constants.INT_NULL))// p.isEmpty() ||
		{
			return false;
		}
		final Label l = Label.parse(label);
		return mergeTriple(l, p, i, force);
	}

	/**
	 * Put a map associate to key alabel. Possible previous map will be replaced.
	 *
	 * @param alabel          the input label
	 * @param labeledValueMap its map
	 *
	 * @return the old map if one was associated to alabel, null otherwise
	 */
	public LabeledIntMap put(ALabel alabel, LabeledIntMap labeledValueMap) {

		final LabeledIntMap oldMap = map.get(alabel);
		if (oldMap != null) {
			count -= oldMap.size();
		}
		count += labeledValueMap.size();
		return map.put(alabel, labeledValueMap);
	}

	/**
	 * Put the triple {@code (p,l,i)} into the map. If the triple is already present, it is overwritten.
	 *
	 * @param l a {@link Label} object.
	 * @param p a {@link String} object.
	 * @param i the new value to add.
	 *
	 * @return true if the valued has been added.
	 */
	public boolean putTriple(final Label l, final ALabel p, final int i) {
		return mergeTriple(l, p, i, false);
	}

	/**
	 * @param l a {@link Label} object.
	 * @param p a {@link ALabel} object.
	 *
	 * @return the old value if it exists, {@link Constants#INT_NULL} otherwise.
	 */
	public int remove(final Label l, final ALabel p) {
		if ((l == null) || (p == null)) {
			return Constants.INT_NULL;
		}
		final LabeledIntMap map1 = map.get(p);
		if (map1 == null) {
			return Constants.INT_NULL;
		}
		final int old = map1.remove(l);
		if (old != Constants.INT_NULL) {
			count--;
		}
		if (map1.isEmpty()) {
			map.remove(p);// it is necessary for making equals working.
		}
		return old;
	}

	/**
	 * <p>
	 * remove.
	 * </p>
	 *
	 * @param aleph a {@link ALabel} object.
	 *
	 * @return true if removed, false otherwise.
	 */
	public boolean remove(final ALabel aleph) {
		if (aleph == null) {
			return false;
		}
		return map.remove(aleph) != null;
	}

	/**
	 * <p>
	 * size.
	 * </p>
	 *
	 * @return the number of elements of the map.
	 */
	public final int size() {
		return count;
		// int n = 0;
		// for (final LabeledIntTreeMap map1 : this.map.values()) {
		// n += map1.size();
		// }
		// return n;
	}

	/**
	 * {@inheritDoc} Returns a string representing the content of the map, i.e., "{[&langle;entry&rangle; ]*}", where
	 * each &langle;entry&rangle; is written by {@link #entryAsString(Label, int, ALabel)}.
	 */
	@Override
	public String toString() {
		final StringBuilder s = new StringBuilder("{");
		for (final ALabel entryE : keySet()) {
			final LabeledIntMap entry = get(entryE);
			if (entry.isEmpty()) {
				continue;
			}
			final ObjectList<Entry<Label>> sorted = new ObjectArrayList<>(entry.entrySet());
			sorted.sort(LabeledIntMap.entryComparator);

			for (final Entry<Label> entry1 : sorted) {
				s.append(entryAsString(entry1.getKey(), entry1.getIntValue(), entryE));
				s.append(' ');
			}
		}
		s.append("}");
		return s.toString();
	}

	/**
	 * @return a read-only view of this.
	 */
	@SuppressWarnings("ClassReferencesSubclass")
	public LabeledALabelIntTreeMapView unmodifiable() {
		return new LabeledALabelIntTreeMapView(this);
	}

}
