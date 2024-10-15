// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

package it.univr.di.labeledvalue;

import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.io.Serial;
import java.util.logging.Logger;

/**
 * Simple implementation of {@link it.univr.di.labeledvalue.LabeledIntMap} interface without minimization. This class is provided only to give an evidence that
 * without minimization of labeled value sets, any CSTN algorithm can be very slowly.
 *
 * @author Roberto Posenato
 * @version $Rev: 877 $
 * @see LabeledIntMap
 */
public class LabeledIntSimpleMap extends AbstractLabeledIntMap {

	/**
	 * A read-only view of an object
	 *
	 * @author posenato
	 */
	public static class LabeledIntNotMinMapView extends LabeledIntSimpleMap implements LabeledIntMapView {
		/**
		 *
		 */
		@Serial
		private static final long serialVersionUID = 1L;

		/**
		 * @param inputMap nope
		 */
		public LabeledIntNotMinMapView(LabeledIntSimpleMap inputMap) {
			mainMap = inputMap.mainMap;
		}
	}

	/**
	 *
	 */
	@Serial
	static private final long serialVersionUID = 2L;
	/**
	 * logger
	 */
	@SuppressWarnings("unused")
	static private final Logger LOG = Logger.getLogger("LabeledIntSimpleMap");

	/**
	 * @return an Object2IntMap&lt;Label&gt; object
	 */
	private static Object2IntMap<Label> makeLabel2IntMap() {
		return new Object2IntOpenHashMap<>();// Object2IntRBTreeMap is better than Object2IntArrayMap when the set is larger than 5000 elements!
	}

	/**
	 * Map of label
	 */
	Object2IntMap<Label> mainMap;
	/**
	 * Counter of labeled value updates.
	 */
	final Object2IntMap<Label> updateCount;

	/**
	 * Constructor to clone the structure. For optimization issue, this method clone only LabeledIntTreeMap object.
	 *
	 * @param lvm the LabeledValueTreeMap to clone. If lvm is null, this will be an empty map.
	 */
	public LabeledIntSimpleMap(final LabeledIntMap lvm) {
		this();
		if (lvm == null) {return;}
		for (final Entry<Label> entry : lvm.entrySet()) {
			put(entry.getKey(), entry.getIntValue());
		}
	}

	/**
	 * Constructor to clone the structure. For optimization issue, this method clone only LabeledIntTreeMap object.
	 *
	 * @param lvm      the LabeledValueTreeMap to clone. If lvm is null, this will be an empty map.
	 * @param optimize true for having the label shortest as possible, false otherwise. For example, the set {(0, ¬C), (1, C)} is represented as {(0, ⊡), (1,
	 *                 C)} if this parameter is true.
	 */
	public LabeledIntSimpleMap(final LabeledIntMap lvm, boolean optimize) {
		this(lvm);
	}

	/**
	 * Necessary constructor for the factory. The internal structure is built and empty.
	 */
	public LabeledIntSimpleMap() {
		mainMap = makeLabel2IntMap();
		mainMap.defaultReturnValue(Constants.INT_NULL);
		updateCount = makeLabel2IntMap();
		updateCount.defaultReturnValue(Constants.INT_NULL);
	}

	/**
	 * Necessary constructor for the factory. The internal structure is built and empty.
	 *
	 * @param optimize true for having the label shortest as possible, false otherwise. For example, the set {(0, ¬C), (1, C)} is represented as {(0, ⊡), (1,
	 *                 C)} if this parameter is true.
	 */
	public LabeledIntSimpleMap(boolean optimize) {
		this();
	}

	/**
	 * @param newLabel a {@link it.univr.di.labeledvalue.Label} object.
	 * @param newValue the new value.
	 *
	 * @return true if the (newValue, newLabel) is already represented in the map.
	 */
	@Override
	public final boolean alreadyRepresents(@Nonnull Label newLabel, int newValue) {
		for (final Entry<Label> entry : mainMap.object2IntEntrySet()) {
			if (newLabel.subsumes(entry.getKey()) && newValue >= entry.getIntValue()) {
//			if (newLabel.equals(entry.getKey()) && newValue >= entry.getIntValue()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void clear() {
		mainMap.clear();
		updateCount.clear();
	}

	/**
	 * This method returns a copy of the view of the map. Any modification of the map IS NOT propagated to the return set.<br> In other word, it is possible to
	 * iterate on this map for modifying the original map without worried to lose any element.<br>
	 */
	@Override
	public ObjectSet<Entry<Label>> entrySet() {
		final ObjectSet<Entry<Label>> coll = new ObjectArraySet<>();
		return entrySet(coll);
	}

	/**
	 * This method returns a copy of the view of the map. Any modification of the map IS NOT propagated to the return set.<br> In other word, it is possible to
	 * iterate on this map for modifying the original map without worried to lose any element.<br>
	 */
	@Override
	public final ObjectSet<Entry<Label>> entrySet(@NotNull ObjectSet<Entry<Label>> setToReuse) {
		setToReuse.clear();
		setToReuse.addAll(mainMap.object2IntEntrySet());
		return setToReuse;
	}

	@Override
	public int get(final Label l) {
		return mainMap.getInt(l);
	}

	@Override
	public ObjectSet<Label> keySet() {
		final ObjectSet<Label> coll = new ObjectArraySet<>();
		return keySet(coll);
	}

	@Override
	public final ObjectSet<Label> keySet(ObjectSet<Label> setToReuse) {
		setToReuse.clear();
		setToReuse.addAll(mainMap.keySet());
		return setToReuse;
	}

	@Override
	public final LabeledIntSimpleMap newInstance() {
		return new LabeledIntSimpleMap();
	}

	/**
	 * Factory
	 *
	 * @param optimize true for having the label shortest as possible, false otherwise. For example, the set {(0, ¬C), (1, C)} is represented as {(0, ⊡), (1,
	 *                 C)} if this parameter is true.
	 *
	 * @return an object of type LabeledIntMap.
	 */
	@Override
	public LabeledIntMap newInstance(boolean optimize) {
		return newInstance();
	}

	@Override
	public final LabeledIntSimpleMap newInstance(LabeledIntMap lim) {
		return new LabeledIntSimpleMap(lim);
	}

	/**
	 * Factory
	 *
	 * @param lim      an object to clone.
	 * @param optimize true for having the label shortest as possible, false otherwise. For example, the set {(0, ¬C), (1, C)} is represented as {(0, ⊡), (1,
	 *                 C)} if this parameter is true.
	 *
	 * @return an object of type LabeledIntMap.
	 */
	@Override
	public LabeledIntMap newInstance(LabeledIntMap lim, boolean optimize) {
		return newInstance(lim);
	}

	@Override
	public final boolean put(final Label newLabel, int newValue) {
		if ((newLabel == null) || (newValue == Constants.INT_NULL)) {
			return false;
		}
		int updated = updateCount.getInt(newLabel);
		updated = (updated == Constants.INT_NULL) ? 1 : updated + 1;
		if (!alreadyRepresents(newLabel, newValue)) {
			removeAllRedundantLabel(newLabel, newValue);
			mainMap.put(newLabel, newValue);
			updateCount.put(newLabel, updated);
			return true;
		}
		return false;
	}

	/**
	 * Put the labeled value without any control. It is dangerous, but it can help in some cases.
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
		return mainMap.removeInt(l);
	}

	@Override
	public int size() {
		return mainMap.size();
	}

	@Override
	public LabeledIntMapView unmodifiable() {
		return new LabeledIntNotMinMapView(this);
	}

	@Override
	public IntSet values() {
		return (IntSet) mainMap.values();
	}

	/**
	 * Removes all labeled values that subsume newLabel and have values ≥ newValue.<br>
	 * <p>
	 * Example: given mainMap = {(0,⊡) (-1,ab), (-2,¬a)} and newLabel=a, newValue =-2, the mainMap will be updated to {(0,⊡), (-2,¬a)}
	 *
	 * @param newLabel a {@link it.univr.di.labeledvalue.Label} object.
	 * @param newValue the new value.
	 */
	private void removeAllRedundantLabel(@Nonnull Label newLabel, int newValue) {
		//20240428 Again mainMap.removeInt does not work. I have to copy the set
		final ObjectArraySet<Entry<Label>> entrySet = new ObjectArraySet<>(mainMap.object2IntEntrySet());
		for (final Entry<Label> entry : entrySet) {
			final Label entryLabel = entry.getKey();
			if (entryLabel == null || entryLabel.equals(Label.emptyLabel)) {
				continue;
			}
			if (entryLabel.subsumes(newLabel) && newValue <= entry.getIntValue()) {
				mainMap.removeInt(entryLabel);
			}
		}
	}
}
