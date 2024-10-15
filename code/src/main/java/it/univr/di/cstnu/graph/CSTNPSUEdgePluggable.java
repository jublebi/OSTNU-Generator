// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.graph;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap.Entry;
import it.univr.di.Debug;
import it.univr.di.labeledvalue.*;

import javax.annotation.Nullable;
import java.io.Serial;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An implementation of CSTNUEdge where the labeled value set can be plugged during the creation.
 *
 * @author posenato
 * @version $Rev: 852 $
 */
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "SE_BAD_FIELD", justification = "I know what I'm doing")
public class CSTNPSUEdgePluggable extends BasicCSTNUEdgePluggable implements CSTNPSUEdge {

	@Serial
	private static final long serialVersionUID = 1L;

	/*
	 * class initializer
	 */
	static {
		/*
		 * logger
		 */
		LOG = Logger.getLogger(CSTNPSUEdgePluggable.class.getName());
	}

	/**
	 * The CSTNU controllability check algorithm needs to know if a labeled value has been already considered in the past in order to avoid to add it a second
	 * time.
	 */
	Object2IntMap<Entry<Label, ALabel>> consideredLowerCaseValue;
	/**
	 * Morris Lower case value augmented by a propositional label.<br> The name of node has to be equal to the original name. No case modifications are
	 * necessary!
	 */
	LabeledALabelIntTreeMap lowerCaseValue;

	/*
	 * <C extends LabeledIntMap> CSTNUEdgePluggable(Class<C> labeledIntMapImpl) {
	 * this((String) null, labeledIntMapImpl);
	 * }
	 */

	/**
	 *
	 */
	CSTNPSUEdgePluggable() {
		this((String) null);
	}

	/**
	 * @param n name of edge
	 */
	CSTNPSUEdgePluggable(String n) {
		super(n);
		lowerCaseValue = new LabeledALabelIntTreeMap(this.getLabeledIntMapImplClass());
		consideredLowerCaseValue = new Object2IntArrayMap<>();
		consideredLowerCaseValue.defaultReturnValue(Constants.INT_NULL);
	}

	/**
	 * Constructor to clone the component.
	 *
	 * @param e the edge to clone.
	 */
	CSTNPSUEdgePluggable(Edge e) {
		super(e);
		if (e != null && CSTNPSUEdge.class.isAssignableFrom(e.getClass())) {
			final CSTNPSUEdge e1 = (CSTNPSUEdge) e;
			lowerCaseValue = new LabeledALabelIntTreeMap(e1.getLowerCaseValueMap(), this.getLabeledIntMapImplClass());
		} else {
			if (e != null && CSTNUEdge.class.isAssignableFrom(e.getClass())) {
				final CSTNUEdge e1 = (CSTNUEdge) e;
				lowerCaseValue = new LabeledALabelIntTreeMap(this.getLabeledIntMapImplClass());
				final LabeledLowerCaseValue lcv = e1.getLowerCaseValue();
				if (!lcv.isEmpty()) {
					lowerCaseValue.mergeTriple(lcv.getLabel(), lcv.getNodeName(), lcv.getValue());
				}
			} else {
				lowerCaseValue = new LabeledALabelIntTreeMap(this.getLabeledIntMapImplClass());
			}
		}
		consideredLowerCaseValue = new Object2IntArrayMap<>();
		consideredLowerCaseValue.defaultReturnValue(Constants.INT_NULL);
	}

	@Override
	public void clear() {
		super.clear();
		lowerCaseValue.clear();
		consideredLowerCaseValue.clear();
	}

	@Override
	public void clearLowerCaseValues() {
		lowerCaseValue.clear();
	}

	@Override
	public final LabeledALabelIntTreeMap getAllLowerCaseAndLabeledValuesMaps() {
		final LabeledALabelIntTreeMap union = new LabeledALabelIntTreeMap(this.getLabeledIntMapImplClass());

		for (final ALabel alabel : lowerCaseValue.keySet()) {
			union.put(alabel, lowerCaseValue.get(alabel));
		}
		union.put(ALabel.emptyLabel, getLabeledValueMap());
		return union;
	}

	@Override
	public LabeledLowerCaseValue getLowerCaseValue() {
		if (lowerCaseValue.size() != 1) {
			return LabeledLowerCaseValue.emptyLabeledLowerCaseValue;
		}

		final ALabel aLabel = lowerCaseValue.keySet().iterator().next();
		if (aLabel.size() > 1) {
			return LabeledLowerCaseValue.emptyLabeledLowerCaseValue;
		}
		final LabeledIntMap labeledValueT = lowerCaseValue.get(aLabel);
		final Object2IntMap.Entry<Label> entry = labeledValueT.entrySet().iterator().next();

		final int value = entry.getIntValue();

		return (value != Constants.INT_NULL) ? LabeledLowerCaseValue.create(aLabel, value, entry.getKey()) : LabeledLowerCaseValue.emptyLabeledLowerCaseValue;
	}

	@Override
	public void setLowerCaseValue(LabeledALabelIntTreeMap lowerCaseValue1) {
		lowerCaseValue = (lowerCaseValue1 == null) ? new LabeledALabelIntTreeMap(this.getLabeledIntMapImplClass()) : lowerCaseValue1;
	}

	@Override
	public int getLowerCaseValue(Label l, ALabel name1) {
		return lowerCaseValue.getValue(l, name1);
	}

	@Override
	public LabeledALabelIntTreeMap getLowerCaseValueMap() {
		return lowerCaseValue;
	}

	@Nullable
	@Override
	public Object2ObjectMap.Entry<Label, Object2IntMap.Entry<ALabel>> getMinLowerCaseValue() {
		final Object2ObjectMap.Entry<Label, Object2IntMap.Entry<ALabel>> entry = lowerCaseValue.getMinValue();
		if (entry.getValue().getKey().isEmpty()) {
			return null;
		}
		return entry;
	}

	@Override
	public final boolean hasSameValues(Edge e) {
		if (!(e instanceof CSTNPSUEdge e1)) {
			return false;
		}
		if (e == this) {
			return true;
		}
		if (!super.hasSameValues(e)) {
			return false;
		}
		return (lowerCaseValue.equals(e1.getLowerCaseValueMap()));
	}

	@Override
	public boolean isCSTNPSUEdge() {
		return true;
	}

	@Override
	public boolean isEmpty() {
		return super.isEmpty() && lowerCaseValue.isEmpty();
	}

	@Override
	public final int lowerCaseValueSize() {
		return lowerCaseValue.size();
	}

	@Override
	public String lowerCaseValuesAsString() {
		return lowerCaseValue.toString();
	}

	@Override
	public boolean mergeLabeledValue(Label l, int i) {
		final boolean added = super.mergeLabeledValue(l, i);
		// If a value is positive, and there are more lower-case values, some of them may be simplified.
		if (added && i >= 0 && !lowerCaseValue.isEmpty()) {
			for (final ALabel LCALabel : lowerCaseValue.keySet()) {
				final LabeledIntMap labeledLCValues = lowerCaseValue.get(LCALabel);
				for (final Label LCLabel : labeledLCValues.keySet()) {
					final int LCValue = labeledLCValues.get(LCLabel);
					if (i <= LCValue && LCLabel.subsumes(l)) {
						if (Debug.ON) {
							if (CSTNEdgePluggable.LOG.isLoggable(Level.FINEST)) {
								CSTNEdgePluggable.LOG.log(Level.FINEST,
								                          "The value (" + l + ", " + i + ") makes redundant lower case value (" + LCLabel + ", " + LCALabel +
								                          ":" + LCValue + ").  Last one is removed.");
							}
						}
						setLowerCaseValueAsConsidered(LCLabel, LCALabel, LCValue);
						removeLowerCaseValue(LCLabel, LCALabel);
					}
				}
			}
		}
		return added;
	}

	@Override
	public final boolean mergeLowerCaseValue(Label l, ALabel nodeName, int i) {
		if ((l == null) || (nodeName == null) || (i == Constants.INT_NULL)) {
			throw new IllegalArgumentException(
				"The label or the value has a not admitted value: (" + l + ", " + nodeName + ", " + Constants.formatInt(i) + ").");
		}
		final InternalEntry se = new InternalEntry(l, nodeName);
		final int oldValue = consideredLowerCaseValue.getInt(se);
		if ((oldValue != Constants.INT_NULL) && (i >= oldValue)) {
			// the labeled value (l,i) was already removed by label modification rule.
			// A labeled value with a value equal or smaller will be modified again.
			if (Debug.ON) {
				if (CSTNEdgePluggable.LOG.isLoggable(Level.FINEST)) {
					CSTNEdgePluggable.LOG.finest(
						"The labeled value (" + l + ", " + nodeName + ", " + i + ") has not been stored because the previous (" + l + ", " + nodeName + ", " +
						oldValue + ") is in the removed list");
				}
			}
			return false;
		}
		setLowerCaseValueAsConsidered(l, nodeName, i);// once it has been added, it is useless to add it again!
		// Check if a standard labeled value is more restrictive of the one to put.
		final int minNormalValueSubSummedByL = getLabeledValueMap().getMinValueSubsumedBy(l);
		if ((minNormalValueSubSummedByL != Constants.INT_NULL) && (minNormalValueSubSummedByL <= i)) {
			if (Debug.ON) {
				if (CSTNEdgePluggable.LOG.isLoggable(Level.FINEST)) {
					CSTNEdgePluggable.LOG.finest("The labeled value (" + l + ", " + nodeName + ", " + i +
					                             ") has not been stored because the value is greater than the labeled minimal value subsume by " + l + ".");
				}
			}
			return false;
		}
		return lowerCaseValue.mergeTriple(l, nodeName, i, false);
		// if (added && !this.lowerCaseValue.isEmpty()) {
		// this.setConstraintType(ConstraintType.contingent);
		// this.setChanged();
		// notifyObservers("LowerLabel:add");
		// }
	}

	@Override
	public CSTNPSUEdgePluggable newInstance() {
		return new CSTNPSUEdgePluggable();
	}

	@Override
	public CSTNPSUEdgePluggable newInstance(Edge edge) {
		return new CSTNPSUEdgePluggable(edge);
	}

	@Override
	public CSTNPSUEdgePluggable newInstance(String name1) {
		return new CSTNPSUEdgePluggable(name1);
	}

	@Override
	public final boolean putLowerCaseValue(Label l, ALabel nodeName, int i) {
		if ((l == null) || (nodeName == null) || (i == Constants.INT_NULL)) {
			throw new IllegalArgumentException(
				"The label or the value has a not admitted value: (" + l + ", " + nodeName + ", " + Constants.formatInt(i) + ").");
		}
		//When I put, I don't consider if I put it in the past
//		InternalEntry se = new InternalEntry(l, nodeName);
//		int oldValue = consideredLowerCaseValue.getInt(se);
//		if ((oldValue != Constants.INT_NULL) && (i >= oldValue)) {
//			// the labeled value (l,i) was already removed by label modification rule.
//			// A labeled value with a value equal or smaller will be modified again.
//			if (Debug.ON) {
//				if (CSTNEdgePluggable.LOG.isLoggable(Level.FINEST)) {
//					CSTNEdgePluggable.LOG.finest("The labeled value (" + l + ", " + nodeName + ", " + i + ") has not been stored because the previous (" + l + ", " + nodeName + ", " + oldValue + ") is in the removed list");
//				}
//			}
//			return false;
//		}
//		setLowerCaseValueAsConsidered(l, nodeName, i);// once it has been added, it is useless to add it again!
		return lowerCaseValue.mergeTriple(l, nodeName, i, true);
	}

	@Override
	public final int removeLowerCaseValue(Label l, ALabel n) {
		// this.consideredLowerCaseValue.removeInt(new InternalEntry(l, n));
		return lowerCaseValue.remove(l, n);
	}

	@Override
	public void takeIn(Edge e) {
		if (e == null) {
			return;
		}
		super.takeIn(e);
		if (e instanceof CSTNPSUEdgePluggable e1) {
			lowerCaseValue = e1.lowerCaseValue;
			consideredLowerCaseValue = e1.consideredLowerCaseValue;
		}
	}

	/**
	 * Set the triple as already considered in order to avoid to consider it again in the future.
	 *
	 * @param l label
	 * @param n name of node as a-label
	 * @param i edge weight
	 *
	 * @return the old value associated to (l,n), or the {@link Constants#INT_NULL} if no value was present.
	 */
	@SuppressWarnings("UnusedReturnValue")
	int setLowerCaseValueAsConsidered(Label l, ALabel n, int i) {
		return consideredLowerCaseValue.put(new InternalEntry(l, n), i);
	}

}
