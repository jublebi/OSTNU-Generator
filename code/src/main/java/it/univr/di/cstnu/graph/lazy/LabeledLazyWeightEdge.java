// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 *
 */
package it.univr.di.cstnu.graph.lazy;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.univr.di.Debug;
import it.univr.di.cstnu.graph.AbstractComponent;
import it.univr.di.cstnu.graph.Edge.ConstraintType;
import it.univr.di.labeledvalue.Constants;
import it.univr.di.labeledvalue.Label;
import it.univr.di.labeledvalue.lazy.LabeledLazyWeightTreeMap;
import it.univr.di.labeledvalue.lazy.LazyWeight;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * It contains all information of a CSTPU edge.
 *
 * @author posenato
 * @version $Id: $Id
 */
@SuppressWarnings("ALL")
public class LabeledLazyWeightEdge extends AbstractComponent {

	/**
	 * logger
	 */
	static final Logger LOG = Logger.getLogger(LabeledLazyWeightEdge.class.getName());

	/**
	 *
	 */
	private static final long serialVersionUID = 2L;

	/**
	 * <p>createLabeledLazyWeightEdge.</p>
	 *
	 * @return a {@link it.univr.di.cstnu.graph.lazy.LabeledLazyWeightEdge} object.
	 */
	public static LabeledLazyWeightEdge createLabeledLazyWeightEdge() {
		return new LabeledLazyWeightEdge();
	}

	/**
	 * <p>createLabeledLazyWeightEdge.</p>
	 *
	 * @param e edge to clone
	 * @return a new edge clone of the given one
	 */
	public static LabeledLazyWeightEdge createLabeledLazyWeightEdge(LabeledLazyWeightEdge e) {
		return new LabeledLazyWeightEdge(e);
	}

	/**
	 * <p>createLabeledLazyWeightEdge.</p>
	 *
	 * @param name1 a {@link java.lang.String} object.
	 * @return a new empty edge with name.
	 */
	public static LabeledLazyWeightEdge createLabeledLazyWeightEdge(String name1) {
		return new LabeledLazyWeightEdge(name1);
	}

	/**
	 * The type of the edge.
	 */
	ConstraintType constraintType;

	/**
	 * Removed Labeled value.<br>
	 * Only methods inside this class should modify such field.
	 */
	Object2ObjectMap<Label, LazyWeight> removedLabeledValue;

	/**
	 * Labeled value.
	 */
	private LabeledLazyWeightTreeMap labeledValue;

	private void initInternalStructure() {
		this.labeledValue = new LabeledLazyWeightTreeMap();
		this.removedLabeledValue = new Object2ObjectOpenHashMap<>();
		this.constraintType = ConstraintType.requirement;
	}
	/**
	 * <p>Constructor for LabeledLazyWeightEdge.</p>
	 */
	public LabeledLazyWeightEdge() {
		super();
		this.initInternalStructure();
	}

	/**
	 * A simple constructor cloner.
	 *
	 * @param e edge to clone. If null, an empty edge is created with type = normal.
	 */
	public LabeledLazyWeightEdge(final LabeledLazyWeightEdge e) {
		super(e);
		if (e != null) {
			this.setConstraintType(e.getConstraintType());
			this.labeledValue = new LabeledLazyWeightTreeMap(e.getLabeledValueMap());
		} else {
			this.labeledValue = new LabeledLazyWeightTreeMap();
			this.constraintType = ConstraintType.requirement;
		}
		this.removedLabeledValue = new Object2ObjectOpenHashMap<>();
	}

	/**
	 * Constructor for LabeledIntEdge.
	 *
	 * @param n a {@link java.lang.String} object.
	 */
	public LabeledLazyWeightEdge(final String n) {
		super(n);
		this.initInternalStructure();
	}

	/**
	 * Default constructor: empty name, derived type and no labeled value.
	 *
	 * @param name1 a {@link java.lang.String} object.
	 * @param t the type of the edge
	 */
	public LabeledLazyWeightEdge(final String name1, final ConstraintType t) {
		this(name1);
		this.setConstraintType(t);
	}

	/**
	 * {@inheritDoc}
	 *
	 * Clear (remove) all labeled values associated to this edge.
	 */
	@Override
	public void clear() {
		this.labeledValue.clear();
	}

	@NotNull
	@Override
	public String setName(@NotNull String newName) {
		return null;
	}

	/**
	 * Clears all ordinary labeled values.
	 */
	public void clearLabels() {
		this.removedLabeledValue.clear();
		this.labeledValue.clear();
	}

	/**
	 * <p>copyLabeledValueMap.</p>
	 *
	 * @param inputLabeledValue a {@link it.univr.di.labeledvalue.lazy.LabeledLazyWeightTreeMap} object.
	 */
	public void copyLabeledValueMap(final LabeledLazyWeightTreeMap inputLabeledValue) {
		LabeledLazyWeightTreeMap map = LabeledLazyWeightTreeMap.createLabeledLazyTreeMap();
		for (Entry<Label, LazyWeight> entry : inputLabeledValue.entrySet()) {
			map.put(entry.getKey(), entry.getValue());
		}
		this.labeledValue = map;
	}

	/**
	 * <p>equalsAllLabeledValues.</p>
	 *
	 * @param e a {@link it.univr.di.cstnu.graph.lazy.LabeledLazyWeightEdge} object.
	 * @return a boolean.
	 */
	public final boolean equalsAllLabeledValues(final LabeledLazyWeightEdge e) {
		if (e == null || e == this)
			return false;

		// Use getLabeledValueMap instead of labeledValueSet() to have a better control.
		return (this.getLabeledValueMap().equals(e.getLabeledValueMap()));
		// && this.getLowerCaseValue().equals(e.getLowerCaseValue())
		// && this.getUpperCaseValueMap().equals(e.getUpperCaseValueMap()));
	}

	/**
	 * <p>Getter for the field <code>constraintType</code>.</p>
	 *
	 * @return a {@link it.univr.di.cstnu.graph.Edge.ConstraintType} object.
	 */
	public final ConstraintType getConstraintType() {
		return this.constraintType;
	}

	/**
	 * <p>getLabeledValueMap.</p>
	 *
	 * @return the labeledValueMap. If there is no labeled values, return an empty map.
	 */
	public LabeledLazyWeightTreeMap getLabeledValueMap() {
		return this.labeledValue;
	}

	/**
	 * <p>getLabeledValueSet.</p>
	 *
	 * @return the labeled values as a set
	 */
	public ObjectSet<Entry<Label, LazyWeight>> getLabeledValueSet() {
		return this.labeledValue.entrySet();
	}

	/**
	 * <p>getLabeledValueSet.</p>
	 *
	 * @param setToReuse a {@link it.unimi.dsi.fastutil.objects.ObjectSet} object.
	 * @return the labeled values as a set
	 */
	public ObjectSet<Entry<Label, LazyWeight>> getLabeledValueSet(ObjectSet<Entry<Label, LazyWeight>> setToReuse) {
		return this.labeledValue.entrySet(setToReuse);
	}

	/**
	 * <p>getMinValue.</p>
	 *
	 * @return the minimal value among all ordinary labeled values if there are some values, {@link it.univr.di.labeledvalue.Constants#INT_NULL} otherwise.
	 */
	public double getMinValue() {
		return this.labeledValue.getMinValue();
	}

	/**
	 * <p>getMinValueConsistentWith.</p>
	 *
	 * @param l a {@link it.univr.di.labeledvalue.Label} object.
	 * @return the value of label l or the minimal value of labels consistent with l if it exists, null otherwise.
	 */
	public double getMinValueConsistentWith(final Label l) {
		return this.labeledValue.getMinValueConsistentWith(l);
	}

	/**
	 * <p>getMinValueSubsumedBy.</p>
	 *
	 * @param l a {@link it.univr.di.labeledvalue.Label} object.
	 * @return a double.
	 */
	public double getMinValueSubsumedBy(Label l) {
		return this.labeledValue.getMinValueSubsumedBy(l);
	}

	/**
	 * <p>getValue.</p>
	 *
	 * @param label label
	 * @return the value associated to label it exists, {@link it.univr.di.labeledvalue.Constants#INT_NULL} otherwise.
	 */
	public LazyWeight getValue(final Label label) {
		return this.labeledValue.get(label);
	}

	/**
	 * <p>isEmpty.</p>
	 *
	 * @return true if this edge has no weight.
	 */
	public boolean isEmpty() {
		return this.labeledValue.size() == 0;
	}

	/**
	 * Merges the labeled value i to the set of labeled values of this edge.
	 *
	 * @param l a {@link it.univr.di.labeledvalue.Label} object.
	 * @param i a integer.
	 * @return true if the operation was successful, false otherwise.
	 */
	public boolean mergeLabeledValue(final Label l, final LazyWeight i) {
		if ((l == null) || (i == null))
			return false;
		LazyWeight oldLW= this.removedLabeledValue.get(l);
		final double oldValue = (oldLW != null) ? oldLW.getValue() : Constants.INT_NULL;
		if ((oldValue != Constants.INT_NULL) && (i.getValue() >= oldValue)) {
			// The new value is greater or equal the old one, the new value can be ignored.
			// the labeled value (l,i) was already removed in the past, it will be not stored.
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST))
					LOG.log(Level.FINEST,
							"The labeled value " + LabeledLazyWeightTreeMap.entryAsString(l, i) + " will be not stored because the labeled value "
									+ LabeledLazyWeightTreeMap.entryAsString(l, oldLW) + " is in the removed list");
			}
			return false;
		}
		this.removedLabeledValue.put(l, i); // once a value has been inserted, it is useless to insert it again in the future.
		boolean added = this.labeledValue.put(l, i);
		return added;
	}

	/**
	 * <p>mergeLabeledValue.</p>
	 *
	 * @param map a {@link it.univr.di.labeledvalue.lazy.LabeledLazyWeightTreeMap} object.
	 */
	public void mergeLabeledValue(LabeledLazyWeightTreeMap map) {
		for (Entry<Label, LazyWeight> entry : map.entrySet())
			this.labeledValue.put(entry.getKey(), entry.getValue());
	}

	/**
	 * <p>putLabeledValue.</p>
	 *
	 * @param l a {@link it.univr.di.labeledvalue.Label} object.
	 * @param i a {@link it.univr.di.labeledvalue.lazy.LazyWeight} object.
	 * @return a boolean.
	 */
	public boolean putLabeledValue(final Label l, LazyWeight i) {
		return this.labeledValue.put(l, i);
	}

	/**
	 * Remove the label l from the map. If the label is not present, it does nothing.
	 *
	 * @param l a {@link it.univr.di.labeledvalue.Label} object.
	 * @return the old value if it exists, null otherwise.
	 */
	public double removeLabeledValue(final Label l) {
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LabeledLazyWeightEdge.LOG.finer("Removing label '" + l + "' from the edge " + this);
			}
		}
		this.removedLabeledValue.remove(l);
		return this.labeledValue.remove(l);
	}

	/**
	 * <p>Setter for the field <code>constraintType</code>.</p>
	 *
	 * @param type a {@link it.univr.di.cstnu.graph.Edge.ConstraintType} object.
	 */
	public void setConstraintType(final ConstraintType type) {
		this.constraintType = type;
	}

	/**
	 * <p>setLabeledValueMap.</p>
	 *
	 * @param inputLabeledValue a {@link it.univr.di.labeledvalue.lazy.LabeledLazyWeightTreeMap} object.
	 */
	public void setLabeledValueMap(LabeledLazyWeightTreeMap inputLabeledValue) {
		if (inputLabeledValue == null) {
			this.labeledValue.clear();
		} else {
			this.labeledValue = inputLabeledValue;
		}
	}

	/**
	 * @return the number of elements in the set.
	 */
	public int size() {
		return this.labeledValue.size();
	}

	/**
	 * A copy by reference of internal structure of edge e.
	 *
	 * @param e edge to clone. If null, it returns doing nothing.
	 */
	public void takeIn(final LabeledLazyWeightEdge e) {
		if (e == null)
			return;
		this.constraintType = e.constraintType;
		this.labeledValue = e.labeledValue;
		this.removedLabeledValue = e.removedLabeledValue;
	}

	/**
	 * {@inheritDoc} Return a string representation of labeled values.h
	 */
	@Override
	public String toString() {
		return "❮" + (this.getName().length() == 0 ? "<empty>" : this.getName()) + "; " + this.getConstraintType() + "; "
				+ ((this.labeledValue.size() > 0) ? this.labeledValue.toString() + "; " : "")
				+ "❯";
	}

}
