// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.graph;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.univr.di.Debug;
import it.univr.di.labeledvalue.*;

import javax.annotation.Nonnull;
import java.io.Serial;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An implementation of CSTNEdge where the labeled value set can be configured at source level.
 *
 * @author posenato
 * @version $Rev: 852 $
 */
public class CSTNEdgePluggable extends AbstractEdge implements CSTNEdge {
	/**
	 * Default class for representing labeled values. It should minimize the set of represented labeled values as much as possible.
	 */
	static final Class<? extends LabeledIntMap> DEFAULT_LABELED_INT_MAP_CLASS = LabeledIntTreeMap.class;
	/**
	 *
	 */
	@Serial
	private static final long serialVersionUID = 5L;
	/**
	 * logger
	 */
	@SuppressWarnings("NonConstantLogger")
	static Logger LOG = Logger.getLogger(CSTNEdgePluggable.class.getName());
	/**
	 * Labeled value class used in the class.
	 */
	final Class<? extends LabeledIntMap> labeledValueMapImpl;
	/**
	 * Maintains log of labeled values that have been already inserted and, therefore, cannot be reinserted. This is used for speed-up the label management.
	 */
	protected Object2IntMap<Label> consideredLabeledValue;

	/**
	 * Labeled value.
	 */
	protected LabeledIntMap labeledValue;

	/**
	 *
	 */
	CSTNEdgePluggable() {
		this((String) null, DEFAULT_LABELED_INT_MAP_CLASS);
	}

	/**
	 * Constructor with specification of labeled values map implementation.
	 */
	CSTNEdgePluggable(final Class<? extends LabeledIntMap> labeledValueMapImplem) {
		this((String) null, labeledValueMapImplem);
	}

	/**
	 * Constructor for LabeledIntEdge.
	 *
	 * @param n                     a {@link java.lang.String} object.
	 * @param labeledValueMapImplem the class for representing the labeled value maps. If null, then {@link #DEFAULT_LABELED_INT_MAP_CLASS} is used.
	 */
	CSTNEdgePluggable(String n, final Class<? extends LabeledIntMap> labeledValueMapImplem) {
		super(n);
		labeledValueMapImpl = (labeledValueMapImplem == null) ? DEFAULT_LABELED_INT_MAP_CLASS : labeledValueMapImplem;
		labeledValue = (new LabeledIntMapSupplier<>(this.labeledValueMapImpl)).get();
		consideredLabeledValue = new Object2IntArrayMap<>();
		consideredLabeledValue.defaultReturnValue(Constants.INT_NULL);
	}

	/**
	 * Constructor for LabeledIntEdge.
	 *
	 * @param n a {@link java.lang.String} object.
	 */
	CSTNEdgePluggable(String n) {
		this(n, null);
	}

	/**
	 * A simple constructor cloner.
	 *
	 * @param e                     edge to clone. If null, an empty edge is created with type = normal.
	 * @param labeledValueMapImplem the class for representing the labeled value maps. If null, then {@link #DEFAULT_LABELED_INT_MAP_CLASS}
	 *                              is used.
	 */
	CSTNEdgePluggable(Edge e, final Class<? extends LabeledIntMap> labeledValueMapImplem) {
		super(e);
		labeledValueMapImpl = (labeledValueMapImplem == null) ? DEFAULT_LABELED_INT_MAP_CLASS : labeledValueMapImplem;
		if (e != null && CSTNEdge.class.isAssignableFrom(e.getClass())) {
			labeledValue = (new LabeledIntMapSupplier<>(labeledValueMapImpl)).get(((CSTNEdge) e).getLabeledValueMap());
		} else {
			labeledValue = (new LabeledIntMapSupplier<>(labeledValueMapImpl)).get();
		}
		consideredLabeledValue = new Object2IntArrayMap<>();
		consideredLabeledValue.defaultReturnValue(Constants.INT_NULL);
	}

	/**
	 * A simple constructor cloner.
	 *
	 * @param e edge to clone. If null, an empty edge is created with type = normal.
	 */
	CSTNEdgePluggable(Edge e) {
		this(e, null);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Clear (remove) all labeled values associated to this edge.
	 */
	@Override
	public void clear() {
		super.clear();
		labeledValue.clear();
		consideredLabeledValue.clear();
	}

	@Override
	public Class<? extends LabeledIntMap> getLabeledIntMapImplClass() {
		return labeledValueMapImpl;
	}

	@Override
	public LabeledIntMap getLabeledValueMap() {
		return labeledValue;
	}

	@Override
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "For efficiency reason, it includes an external mutable object.")
	public void setLabeledValueMap(LabeledIntMap inputLabeledValue) {
		if (inputLabeledValue == null) {
			labeledValue.clear();
		} else {
			labeledValue = inputLabeledValue;
		}
	}

	@Override
	public ObjectSet<Object2IntMap.Entry<Label>> getLabeledValueSet() {
		return labeledValue.entrySet();
	}

	@Override
	public ObjectSet<Object2IntMap.Entry<Label>> getLabeledValueSet(ObjectSet<Object2IntMap.Entry<Label>> setToReuse) {
		return labeledValue.entrySet(setToReuse);
	}

	@Override
	public Entry<Label> getMinLabeledValue() {
		return labeledValue.getMinLabeledValue();
	}

	@Override
	public int getMinValue() {
		return labeledValue.getMinValue();
	}

	@Override
	public int getMinValueAmongLabelsWOUnknown() {
		return labeledValue.getMinValueAmongLabelsWOUnknown();
	}

	@Override
	public int getMinValueConsistentWith(Label l) {
		return labeledValue.getMinValueConsistentWith(l);
	}

	@Override
	public int getMinValueSubsumedBy(Label l) {
		return labeledValue.getMinValueSubsumedBy(l);
	}

	@Override
	public int getValue(Label label) {
		return labeledValue.get(label);
	}

	@Override
	public boolean hasSameValues(Edge e) {
		if (!(e instanceof CSTNEdge)) {
			return false;
		}
		if (e == this) {
			return true;
		}
		// Use getLabeledValueMap instead of labeledValueSet() to have a better control.
		return (labeledValue.equals(((CSTNEdge) e).getLabeledValueMap()));
	}

	@Override
	public boolean isCSTNEdge() {
		return true;
	}

	@Override
	public boolean isEmpty() {
		return labeledValue.isEmpty();
	}

	@Override
	public void mergeLabeledValue(LabeledIntMap map) {
		for (final Object2IntMap.Entry<Label> entry : map.entrySet()) {
			mergeLabeledValue(entry.getKey(), entry.getIntValue());
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Merges the labeled value i to the set of labeled values of this edge.
	 */
	@Override
	public boolean mergeLabeledValue(Label l, int i) {
		if ((l == null) || (i == Constants.INT_NULL) || (i == Constants.INT_POS_INFINITE)) {
			return false;
		}
		final int oldValue = consideredLabeledValue.getInt(l);
		if ((oldValue != Constants.INT_NULL) && (i >= oldValue)) {
			// The new value is greater or equal the old one, the new value can be ignored.
			// the labeled value (l,i) was already removed in the past, it will be not stored.
			if (Debug.ON) {
				if (CSTNEdgePluggable.LOG.isLoggable(Level.FINEST)) {
					CSTNEdgePluggable.LOG.log(Level.FINEST,
					                          "The labeled value (" + l + ", " + i + ") will be not stored because the labeled value (" + l + ", " + oldValue +
					                          ") is in the removed list");
				}
			}
			return false;
		}
		consideredLabeledValue.put(l, i); // once a value has been inserted, it is useless to insert it again in the future.
		return labeledValue.put(l, i);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Wrapper method for {@link #mergeLabeledValue(Label, int)}.
	 *
	 * @see #mergeLabeledValue(Label, int)
	 */
	@Override
	public boolean mergeLabeledValue(String ls, int i) {
		// just a wrapper!
		return mergeLabeledValue(Label.parse(ls), i);
	}

	@Override
	public CSTNEdgePluggable newInstance() {
		return new CSTNEdgePluggable();
	}

	@Override
	public CSTNEdgePluggable newInstance(Edge edge) {
		return new CSTNEdgePluggable(edge);
	}

	@Override
	public CSTNEdgePluggable newInstance(String name1) {
		return new CSTNEdgePluggable(name1);
	}

	@Override
	public boolean putLabeledValue(Label l, int i) {
		consideredLabeledValue.put(l, i); // once a value has been inserted, it is useless to insert it again in the future.
		return labeledValue.put(l, i);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Remove the label l from the map. If the label is not present, it does nothing.
	 */
	@Override
	public void removeLabeledValue(Label l) {
		if (Debug.ON) {
			if (CSTNEdgePluggable.LOG.isLoggable(Level.FINER)) {
				CSTNEdgePluggable.LOG.finer("Removing label '" + l + "' from the edge " + this);
			}
		}
		consideredLabeledValue.removeInt(l); // If it is removed, we assume that it can be reconsidered to be added.
		labeledValue.remove(l);
	}

	@Override
	public int size() {
		return labeledValue.size();
	}

	@Override
	public void takeIn(Edge e) {
		if (e == null) {
			return;
		}
		super.takeIn(e);
		if (e instanceof CSTNEdgePluggable e1) {
			constraintType = e1.constraintType;
			labeledValue = e1.labeledValue;
			consideredLabeledValue = e1.consideredLabeledValue;
		}
	}

	/**
	 * @return the string representation of this edge
	 */
	@SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification = "False positive.")
	@Nonnull
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder(20);
		sb.append(Constants.OPEN_TUPLE);
		if (getName().isEmpty()) {
			sb.append("<empty>");
		} else {
			sb.append(getName());
		}
		sb.append("; ").append(getConstraintType()).append("; ");
		if (!labeledValue.isEmpty()) {
			sb.append(labeledValue).append("; ");
		}
		sb.append(Constants.CLOSE_TUPLE);
		return sb.toString();
	}
}
