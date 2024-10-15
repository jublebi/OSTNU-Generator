// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

package it.univr.di.labeledvalue;

import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.AbstractObject2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.Comparator;

/**
 * Realizes the map {@link Label}-->int
 * <p>
 * The semantics of a set of labeled values is defined in the paper <br> “The Dynamic Controllability of Conditional
 * STNs with Uncertainty.”<br> by Hunsberger, Luke, Roberto Posenato, and Carlo Combi. 2012. <a
 * href="https://arxiv.org/abs/1212.2005">https://arxiv.org/abs/1212.2005</a>.
 * <p>
 * All methods managing a single labeled value have to make a defensive copy of the label in order to guarantee that the
 * label insert/get is a copy of the label given/requested.<br> All methods managing bundle of labeled values do not
 * have to make a defensive copy for performance reasons.
 *
 * @author Robert Posenato
 * @version $Rev: 851 $
 */
public interface LabeledIntMap extends Serializable {
	// I do not extend Object2IntMap<Label> because I want to avoid a lot of nonsensical declarations.

	/**
	 * A read-only view of an object
	 *
	 * @author posenato
	 */
	interface LabeledIntMapView extends LabeledIntMap {

		@Override
		default void clear() {
		}

		/**
		 * Object Read-only. It does nothing.
		 */
		@Override
		default boolean put(Label l, int i) {
			return false;
		}

		@Override
		default void putAll(LabeledIntMap inputMap) {
		}

		@Override
		default int remove(Label l) {
			return Constants.INT_NULL;
		}
	}

	/**
	 * A natural comparator for Entry&lt;Label&gt;. It orders considering the alphabetical order of Label.
	 */
	Comparator<Entry<Label>> entryComparator = (o1, o2) -> {
		if (o1 == o2) {
			return 0;
		}
		if (o1 == null) {
			return -1;
		}
		if (o2 == null) {
			return 1;
		}
		return o1.getKey().compareTo(o2.getKey());
	};

	/**
	 * @param newLabel a {@link it.univr.di.labeledvalue.Label} object.
	 * @param newValue the new value.
	 *
	 * @return true if the current map can represent the value. In positive case, an add of the element does not change
	 * 	the map. If returns false, then the adding of the value to the map would modify the map.
	 */
	boolean alreadyRepresents(Label newLabel, int newValue);

	/**
	 * Remove all entries of the map.
	 *
	 * @see java.util.Map#clear()
	 */
	void clear();

	/**
	 * The set of all entries of the map. The set can be a view of the map, so any modification of the map can be
	 * reflected on the returned entrySet().<br> In other word, don't modify the map during the use of this returned
	 * set.
	 *
	 * @param setToReuse a {@link it.unimi.dsi.fastutil.objects.ObjectSet} object.
	 *
	 * @return The set of all entries of the map.
	 *
	 * @see java.util.Map#entrySet()
	 * @see ObjectSet a containter for the returned set. It will be clear before filling with the entries.
	 * @see it.unimi.dsi.fastutil.objects.Object2IntMap.Entry
	 */
	ObjectSet<Entry<Label>> entrySet(@Nonnull ObjectSet<Entry<Label>> setToReuse);

	/**
	 * The set of all entries of the map. The set can be a view of the map, so any modification of the map can be
	 * reflected on the returned entrySet.<br> In other word, don't modify the map during the use of this returned set.
	 *
	 * @return The set of all entries of the map.
	 *
	 * @see java.util.Map#entrySet()
	 * @see ObjectSet
	 * @see it.unimi.dsi.fastutil.objects.Object2IntMap.Entry
	 */
	ObjectSet<Entry<Label>> entrySet();

	/**
	 * @param l an {@link it.univr.di.labeledvalue.Label} object.
	 *
	 * @return the value associated to {@code l} if it exists, {@link it.univr.di.labeledvalue.Constants#INT_NULL} otherwise.
	 */
	int get(Label l);

	/**
	 * @return the maximum int value present in the set if the set is not empty;
	 *    {@link it.univr.di.labeledvalue.Constants#INT_NULL} otherwise.
	 */
	default int getMaxValue() {
		if (size() == 0) {
			return Constants.INT_NULL;
		}
		int max = Constants.INT_NEG_INFINITE;
		for (final int value : values()) {
			if (max < value) {
				max = value;
			}
		}
		return max;
	}

	/**
	 * Returns the value associated to the {@code l} if it exists, otherwise the maximal value among all labels
	 * consistent with {@code l}.
	 *
	 * @param l If it is null, {@link it.univr.di.labeledvalue.Constants#INT_NULL} is returned.
	 *
	 * @return the value associated to the {@code l} if it exists or the maximal value among values associated to labels
	 * 	consistent with {@code l}. If no labels are consistent by {@code l},
	 *    {@link it.univr.di.labeledvalue.Constants#INT_NULL} is returned.
	 */
	default int getMaxValueSubsumedBy(final Label l) {
		if (l == null) {
			return Constants.INT_NULL;
		}
		int max = get(l);
		if (max == Constants.INT_NULL) {
			// the label does not exits, try all consistent labels
			max = Constants.INT_NEG_INFINITE;
			int v1;
			Label l1;
			for (final Entry<Label> e : entrySet()) {
				l1 = e.getKey();
				if (l.subsumes(l1)) {
					v1 = e.getIntValue();
					if (max < v1) {
						max = v1;
					}
				}
			}
		}
		return (max == Constants.INT_NEG_INFINITE) ? Constants.INT_NULL : max;
	}

	/**
	 * @return the minimum entry in the set if the set is not empty; an entry with
	 *    {@link it.univr.di.labeledvalue.Constants#INT_NULL} otherwise.
	 */
	default Entry<Label> getMinLabeledValue() {
		if (size() == 0) {
			return new AbstractObject2IntMap.BasicEntry<>(Label.emptyLabel, Constants.INT_NULL);
		}
		int min = Constants.INT_POS_INFINITE;
		Label label = null;
		for (final Entry<Label> entry : entrySet()) {
			final int value = entry.getIntValue();
			if (min > value) {
				min = value;
				label = entry.getKey();
			}
		}
		return new AbstractObject2IntMap.BasicEntry<>(label, min);
	}

	/**
	 * @return the minimum int value present in the set if the set is not empty;
	 *    {@link it.univr.di.labeledvalue.Constants#INT_NULL} otherwise.
	 */
	default int getMinValue() {
		if (size() == 0) {
			return Constants.INT_NULL;
		}
		int min = Constants.INT_POS_INFINITE;

		for (final int value : values()) {
			if (min > value) {
				min = value;
			}
		}
		return min;
	}

	/**
	 * @return the min value among all labeled value having label without unknown literals.
	 */
	default int getMinValueAmongLabelsWOUnknown() {
		int v = Constants.INT_POS_INFINITE, i;
		Label l;
		for (final Entry<Label> entry : entrySet()) {
			l = entry.getKey();
			if (l.containsUnknown()) {
				continue;
			}
			i = entry.getIntValue();
			if (v > i) {
				v = i;
			}
		}
		return (v == Constants.INT_POS_INFINITE) ? Constants.INT_NULL : v;
	}

	/**
	 * Returns the value associated to the {@code l} if it exists, otherwise the minimal value among all labels
	 * consistent with {@code l}.
	 *
	 * @param l If it is null, {@link it.univr.di.labeledvalue.Constants#INT_NULL} is returned.
	 *
	 * @return the value associated to the {@code l} if it exists or the minimal value among values associated to labels
	 * 	consistent with {@code l}. If no labels are consistent by {@code l},
	 *    {@link it.univr.di.labeledvalue.Constants#INT_NULL} is returned.
	 */
	default int getMinValueConsistentWith(final Label l) {
		if (l == null) {
			return Constants.INT_NULL;
		}
		int min = get(l);
		if (min == Constants.INT_NULL) {
			// the label does not exits, try all consistent labels
			min = Constants.INT_POS_INFINITE;
			int v1;
			Label l1;
			for (final Entry<Label> e : entrySet()) {
				l1 = e.getKey();
				if (l.isConsistentWith(l1)) {
					v1 = e.getIntValue();
					if (min > v1) {
						min = v1;
					}
				}
			}
		}
		return (min == Constants.INT_POS_INFINITE) ? Constants.INT_NULL : min;
	}

	/**
	 * Returns the minimal value among those associated to labels subsumed by {@code l} if it exists,
	 * {@link it.univr.di.labeledvalue.Constants#INT_NULL} otherwise.
	 *
	 * @param l If it is null, {@link it.univr.di.labeledvalue.Constants#INT_NULL} is returned.
	 *
	 * @return minimal value among those associated to labels subsumed by {@code l} if it exists,
	 *    {@link it.univr.di.labeledvalue.Constants#INT_NULL} otherwise.
	 */
	default int getMinValueSubsumedBy(final Label l) {
		if (l == null) {
			return Constants.INT_NULL;
		}
		int min = get(l);
		if (min == Constants.INT_NULL) {
			// the label does not exits, try all consistent labels
			min = Constants.INT_POS_INFINITE;
			int v1;
			Label l1;
			for (final Entry<Label> e : entrySet()) {
				l1 = e.getKey();
				if (l.subsumes(l1)) {
					v1 = e.getIntValue();
					if (min > v1) {
						min = v1;
					}
				}
			}
		}
		return (min == Constants.INT_POS_INFINITE) ? Constants.INT_NULL : min;
	}

	/**
	 * @return true if the map has no elements.
	 */
	boolean isEmpty();

	/**
	 * A copy of all labels in the map. The set must not be connected with the map.
	 *
	 * @return a copy of all labels in the map.
	 */
	ObjectSet<Label> keySet();

	/**
	 * @param setToReuse a set to be reused for filling with the copy of labels
	 *
	 * @return a copy of all labels in the map. The set must not be connected with the map.
	 */
	ObjectSet<Label> keySet(ObjectSet<Label> setToReuse);

	/**
	 * Factory
	 *
	 * @return an object of type LabeledIntMap.
	 */
	LabeledIntMap newInstance();

	/**
	 * Factory
	 *
	 * @param optimize true for having the label shortest as possible, false otherwise. For example, the set {(0, ¬C),
	 *                 (1, C)} is represented as {(0, ⊡), (1, C)} if this parameter is true.
	 *
	 * @return an object of type LabeledIntMap.
	 */
	LabeledIntMap newInstance(boolean optimize);

	/**
	 * Factory
	 *
	 * @param lim an object to clone.
	 *
	 * @return an object of type LabeledIntMap.
	 */
	LabeledIntMap newInstance(LabeledIntMap lim);

	/**
	 * Factory
	 *
	 * @param lim      an object to clone.
	 * @param optimize true for having the label shortest as possible, false otherwise. For example, the set {(0, ¬C), (1, C)} is represented as {(0, ⊡), (1,
	 *                 C)} if this parameter is true.
	 *
	 * @return an object of type LabeledIntMap.
	 */
	LabeledIntMap newInstance(LabeledIntMap lim, boolean optimize);

	/**
	 * Put a label with value {@code i} if label {@code l} is not null and there is not a labeled value in the set with
	 * label {@code l} or it is present but with a value higher than {@code l}.
	 * <p>
	 * Not mandatory: the method can remove or modify other labeled values of the set in order to minimize the labeled
	 * values present guaranteeing that no info is lost.
	 *
	 * @param l a not null label.
	 * @param i a not {@link it.univr.di.labeledvalue.Constants#INT_NULL} value.
	 *
	 * @return true if {@code (l,i)} has been inserted. Since an insertion can remove more than one redundant labeled
	 * 	values, it is nonsensical to return "the old value" as expected from a classical put method.
	 */
	boolean put(Label l, int i);

	/**
	 * Put all elements of inputMap into the current one without making a defensive copy.
	 *
	 * @param inputMap an object.
	 *
	 * @see Object2IntMap#putAll(java.util.Map)
	 */
	default void putAll(final LabeledIntMap inputMap) {
		if (inputMap == null) {
			return;
		}
		for (final Entry<Label> entry : inputMap.entrySet()) {
			put(entry.getKey(), entry.getIntValue());
		}
	}

	/**
	 * Put the labeled value without any control. It is dangerous, but it can help in some cases.
	 *
	 * @param l a {@link it.univr.di.labeledvalue.Label} object.
	 * @param i the new value.
	 */
	void putForcibly(Label l, int i);

	/**
	 * Remove the label {@code l} from the map. If the {@code l} is not present, it does nothing.
	 *
	 * @param l a not null label.
	 *
	 * @return the previous value associated with {@code l}, or {@link it.univr.di.labeledvalue.Constants#INT_NULL} if
	 * 	there was no mapping for {@code l}.
	 *
	 * @see java.util.Map#remove(Object)
	 */
	int remove(Label l);

	/**
	 * @return the number of labeled value (value with empty label included).
	 *
	 * @see java.util.Map#size()
	 */
	int size();

	/**
	 * @return a read-only view of this.
	 */
	@SuppressWarnings("ClassReferencesSubclass")
	LabeledIntMapView unmodifiable();

	/**
	 * @return the set of all integer present in the map as an ordered list.
	 */
	IntSet values();
}
