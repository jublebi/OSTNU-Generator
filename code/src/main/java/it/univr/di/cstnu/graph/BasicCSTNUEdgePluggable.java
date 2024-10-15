// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.graph;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap.Entry;
import it.univr.di.Debug;
import it.univr.di.labeledvalue.*;

import javax.annotation.Nonnull;
import java.io.Serial;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An implementation of CSTNUEdge where the labeled value set can be plugged during the creation.
 *
 * @author posenato
 * @version $Rev: 852 $
 */
public abstract class BasicCSTNUEdgePluggable extends CSTNEdgePluggable implements BasicCSTNUEdge {
	/**
	 * Represents a pair (Label, String).
	 *
	 * @author posenato
	 */
	@SuppressWarnings({"CompareToUsesNonFinalVariable", "NonFinalFieldReferenceInEquals", "NonFinalFieldReferencedInHashCode"})
	final static class InternalEntry implements Object2ObjectMap.Entry<Label, ALabel>, Comparable<Object2ObjectMap.Entry<Label, ALabel>> {
		/**
		 *
		 */
		final Label label;
		/**
		 *
		 */
		ALabel aLabel;

		/**
		 * @param inputLabel  propositional label
		 * @param inputALabel alphabetic label
		 */
		InternalEntry(Label inputLabel, ALabel inputALabel) {
			label = inputLabel;
			aLabel = inputALabel;
		}

		@Override
		public int compareTo(@Nonnull Object2ObjectMap.Entry<Label, ALabel> o) {
			if (this == o) {
				return 0;
			}
			final int i = label.compareTo(o.getKey());
			if (i != 0) {
				return i;
			}
			return aLabel.compareTo(o.getValue());
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof InternalEntry e)) {
				return false;
			}
			return label.equals(e.label) && aLabel.equals(e.aLabel);
		}

		@Override
		public Label getKey() {
			return label;
		}

		@Override
		public ALabel getValue() {
			return aLabel;
		}

		@Override
		public int hashCode() {
			return label.hashCode() + 31 * aLabel.hashCode();
		}

		@Override
		public ALabel setValue(ALabel value) {
			final ALabel old = ALabel.clone(aLabel);
			aLabel = value;
			return old;
		}

		@Override
		public String toString() {
			return "(" + aLabel + ", " + label + ")";
		}

	}

	/**
	 *
	 */
	@Serial
	private static final long serialVersionUID = 3L;

	/*
	 * class initializer
	 */
	static {
		/*
		 * logger
		 */
		LOG = Logger.getLogger(BasicCSTNUEdgePluggable.class.getName());
	}

	/**
	 * @param label input
	 * @param value input
	 *
	 * @return the conventional representation of a labeled value
	 */
	static String pairAsString(Label label, int value) {
		return AbstractLabeledIntMap.entryAsString(label, value);
	}

	/**
	 * @param value    value
	 * @param nodeName node name to put as upper case label
	 * @param label    label to represent
	 *
	 * @return the conventional representation of a labeled value
	 */
	static String upperCaseValueAsString(ALabel nodeName, int value, Label label) {
		return LabeledALabelIntTreeMap.entryAsString(label, value, nodeName);
	}

	/**
	 * The CSTNU controllability check algorithm needs to know if a labeled value has been already considered in the past in order to avoid to add it a second
	 * time.
	 */
	Object2IntMap<Entry<Label, ALabel>> consideredUpperCaseValue;
	/**
	 * Morris Upper case value augmented by a propositional label.
	 * <br>
	 * The name of node has to be equal to the original name. No case modifications are necessary!
	 */
	LabeledALabelIntTreeMap upperCaseValue;

	/**
	 * @param n                 name of edge
	 * @param labeledIntMapImpl the class for representing the labeled value maps. If null, then {@link #DEFAULT_LABELED_INT_MAP_CLASS} is used.
	 */
	public BasicCSTNUEdgePluggable(String n, Class<? extends LabeledIntMap> labeledIntMapImpl) {
		super(n, labeledIntMapImpl);
		upperCaseValue = new LabeledALabelIntTreeMap(this.getLabeledIntMapImplClass());
		consideredUpperCaseValue = new Object2IntArrayMap<>();
		consideredUpperCaseValue.defaultReturnValue(Constants.INT_NULL);
	}

	/**
	 * @param n name of edge
	 */
	BasicCSTNUEdgePluggable(String n) {
		this(n, null);
	}

	/**
	 * @param e                 edge to clone
	 * @param labeledIntMapImpl the class for representing the labeled value maps. If null, then {@link #DEFAULT_LABELED_INT_MAP_CLASS} is used.
	 */
	BasicCSTNUEdgePluggable(Edge e, Class<? extends LabeledIntMap> labeledIntMapImpl) {
		super(e, labeledIntMapImpl);
		if (e != null && BasicCSTNUEdge.class.isAssignableFrom(e.getClass())) {
			final BasicCSTNUEdge e1 = (BasicCSTNUEdge) e;
			upperCaseValue = new LabeledALabelIntTreeMap(e1.getUpperCaseValueMap(), this.getLabeledIntMapImplClass());
		} else {
			upperCaseValue = new LabeledALabelIntTreeMap(this.getLabeledIntMapImplClass());
		}
		consideredUpperCaseValue = new Object2IntArrayMap<>();
		consideredUpperCaseValue.defaultReturnValue(Constants.INT_NULL);
	}

	/**
	 * @param e edge to clone
	 */
	BasicCSTNUEdgePluggable(Edge e) {
		this(e, null);
	}

	/**
	 *
	 */
	@Override
	public void clear() {
		super.clear();
		upperCaseValue.clear();
		consideredUpperCaseValue.clear();
	}

	/**
	 *
	 */
	@Override
	public final void clearUpperCaseValues() {
		upperCaseValue.clear();
	}

	/**
	 *
	 */
	@Override
	public final LabeledALabelIntTreeMap getAllUpperCaseAndLabeledValuesMaps() {
		final LabeledALabelIntTreeMap union = new LabeledALabelIntTreeMap(this.getLabeledIntMapImplClass());

		for (final ALabel alabel : upperCaseValue.keySet()) {
			union.put(alabel, upperCaseValue.get(alabel));
		}
		union.put(ALabel.emptyLabel, getLabeledValueMap());
		return union;
	}

	/**
	 *
	 */
	@Override
	public final Object2ObjectMap.Entry<Label, Object2IntMap.Entry<ALabel>> getMinUpperCaseValue() {
		return upperCaseValue.getMinValue();
	}

	/**
	 *
	 */
	@Override
	public final int getUpperCaseValue(Label l, ALabel name1) {
		return upperCaseValue.getValue(l, name1);
	}

	/**
	 *
	 */
	@Override
	public final LabeledALabelIntTreeMap getUpperCaseValueMap() {
		return upperCaseValue;
	}

	/**
	 *
	 */
	@Override
	public final void setUpperCaseValueMap(LabeledALabelIntTreeMap inputLabeledValue) {
		upperCaseValue = (inputLabeledValue == null) ? new LabeledALabelIntTreeMap(this.getLabeledIntMapImplClass()) : inputLabeledValue;
	}

	/**
	 *
	 */
	@Override
	public boolean hasSameValues(Edge e) {
		if (!(e instanceof BasicCSTNUEdge e1)) {
			return false;
		}
		if (e == this) {
			return true;
		}
		if (!super.hasSameValues(e)) {
			return false;
		}
		return (upperCaseValue.equals(e1.getUpperCaseValueMap()));
	}

	/**
	 *
	 */
	@Override
	public boolean isEmpty() {
		return super.isEmpty() && upperCaseValue.isEmpty();
	}

	/**
	 *
	 */
	@Override
	public boolean mergeLabeledValue(Label l, int i) {
		final boolean added = super.mergeLabeledValue(l, i);
		if (added && !upperCaseValue.isEmpty()) {
			// I try to clean UPPER values
			// Since this.labeledValue.put(l, i) can simplify labeled value set,
			// it is necessary to check every UPPER CASE with any labeled value, not only the last one inserted!
			// 2017-10-31 I verified that it is necessary to improve the performance!
			// 2018-12-24 I re-verified that it is necessary to improve the performance!
			// int maxValueWOUpperCase = this.labeledValue.getMaxValue();
			// if (this.labeledValue.size() >= nBeforeAdd && (this.labeledValue.get(l) == i)) {
			// the added element did not simplify the set, we compare UC values only with it.
			for (final ALabel UCALabel : upperCaseValue.keySet()) {
				final LabeledIntMap labeledUCValues = upperCaseValue.get(UCALabel);
				for (final Label UCLabel : labeledUCValues.keySet()) {
					final int UCValue = labeledUCValues.get(UCLabel);
					// if (UCaseValue >= maxValueWOUpperCase) {
					// //this wait is useless because a normal constraint
					// this.putUpperCaseValueToRemovedList(UCLabel, UCALabel, UCaseValue);
					// this.removeUpperCaseValue(UCLabel, UCALabel);
					// continue;
					// }
					if (i <= UCValue && UCLabel.subsumes(l)) {
						if (Debug.ON) {
							if (CSTNEdgePluggable.LOG.isLoggable(Level.FINEST)) {
								CSTNEdgePluggable.LOG.log(Level.FINEST,
								                          "The value " + BasicCSTNUEdgePluggable.pairAsString(l, i) + " makes redundant upper case value " +
								                          BasicCSTNUEdgePluggable.upperCaseValueAsString(UCALabel, UCValue, UCLabel) +
								                          ". Last one is removed.");
							}
						}
						setUpperCaseValueAsConsidered(UCLabel, UCALabel, UCValue);
						removeUpperCaseValue(UCLabel, UCALabel);
					}
				}
			}
			// } else {
			// // this.labeledvalue set has been simplified. We consider all values of this.labeledvalue
			// 2018-12-17 Too much expensive, we check only the new inserted value.
			// for (ALabel UCALabel : upperCaseValueValueMap.keySet()) {
			// LabeledIntTreeMap labeledUCValues = upperCaseValueValueMap.get(UCALabel);
			// for (Label UCLabel : labeledUCValues.keySet()) {
			// int UCaseValue = labeledUCValues.get(UCLabel);
			// int min = this.labeledValue.getMinValueSubsumedBy(UCLabel);
			// if (min == Constants.INT_NULL)
			// continue;
			// if (min <= UCaseValue) {
			// this.putUpperCaseValueToRemovedList(UCLabel, UCALabel, UCaseValue);
			// this.removeUpperCaseValue(UCLabel, UCALabel);
			// }
			// }
			// }
			// }
		}
		return added;
	}

	/**
	 *
	 */
	@Override
	public final boolean mergeUpperCaseValue(@Nonnull Label l, @Nonnull ALabel nodeName, int i) {
		if (i == Constants.INT_NULL) {
			throw new IllegalArgumentException(
				"The label or the value has a not allowed value: " + BasicCSTNUEdgePluggable.upperCaseValueAsString(nodeName, Constants.INT_NULL, l) + ".");
		}
		final InternalEntry se = new InternalEntry(l, nodeName);
		final int oldValue = consideredLabeledValue.getInt(se);
		if ((oldValue != Constants.INT_NULL) && (i >= oldValue)) {
			// the labeled value (l,oldValue) was already removed.
			// Only new smaller value can be added.
			if (Debug.ON) {
				if (CSTNEdgePluggable.LOG.isLoggable(Level.FINEST)) {
					CSTNEdgePluggable.LOG.finest(
						"The labeled value " + BasicCSTNUEdgePluggable.upperCaseValueAsString(nodeName, i, l) + " has not been stored " +
						" because the previous " + BasicCSTNUEdgePluggable.upperCaseValueAsString(nodeName, oldValue, l) + " is in the removed list");
				}
			}
			return false;
		}
		setUpperCaseValueAsConsidered(l, nodeName, i);// once it has been added, it is useless to add it again!
		// Check if a standard labeled value is more restrictive of the one to put.
		final int minNormalValueSubSummedByL = getLabeledValueMap().getMinValueSubsumedBy(l);
		if ((minNormalValueSubSummedByL != Constants.INT_NULL) && (minNormalValueSubSummedByL <= i)) {
			if (Debug.ON) {
				if (CSTNEdgePluggable.LOG.isLoggable(Level.FINEST)) {
					CSTNEdgePluggable.LOG.finest("The labeled value " + BasicCSTNUEdgePluggable.upperCaseValueAsString(nodeName, i, l) +
					                             " has not been stored because the value is greater than the labeled minimal value subsume by " + l + ".");
				}
			}
			return false;
		}
		return upperCaseValue.mergeTriple(l, nodeName, i, false);
	}

	/**
	 *
	 */
	@Override
	public final boolean putLabeledValue(Label l, int i) {
		// once a value has been inserted, it is useless to insert it again in the future.
		consideredLabeledValue.put(l, i);
		return labeledValue.put(l, i);
	}

	/**
	 *
	 */
	@Override
	public final boolean putUpperCaseValue(Label l, ALabel nodeName, int i) {
		if ((l == null) || (nodeName == null) || (i == Constants.INT_NULL)) {
			throw new IllegalArgumentException(
				"The label or the value has a not admitted value: " + BasicCSTNUEdgePluggable.upperCaseValueAsString(nodeName, i, l) + ".");
		}
		//When I put, I don't consider if I put it in the past
//		InternalEntry se = new InternalEntry(l, nodeName);
//		int oldValue = consideredLabeledValue.getInt(se);
//		if ((oldValue != Constants.INT_NULL) && (i >= oldValue)) {
//			// the labeled value (l,i) was already removed by label modification rule.
//			// A labeled value with a value equal or smaller will be modified again.
//			if (Debug.ON) {
//				if (CSTNEdgePluggable.LOG.isLoggable(Level.FINEST)) {
//					CSTNEdgePluggable.LOG.finest(
//							"The labeled value " + BasicCSTNUEdgePluggable.upperCaseValueAsString(nodeName, i, l)
//									+ " has not been stored " + " because the previous "
//									+ BasicCSTNUEdgePluggable.upperCaseValueAsString(nodeName, oldValue, l)
//									+ " is in the removed list");
//				}
//			}
//			return false;
//		}
		setUpperCaseValueAsConsidered(l, nodeName, i);// once it has been added, it is useless to add it again!
		return upperCaseValue.mergeTriple(l, nodeName, i, true);
	}

	/**
	 *
	 */
	@Override
	public final int removeUpperCaseValue(Label l, ALabel n) {
		// this.consideredUpperCaseValue.removeInt(new InternalEntry(l, n));
		return upperCaseValue.remove(l, n);
	}

	/**
	 *
	 */
	@Override
	public void takeIn(Edge e) {
		if (e == null) {
			return;
		}
		super.takeIn(e);
		if (e instanceof BasicCSTNUEdgePluggable e1) {
			upperCaseValue = e1.upperCaseValue;
			consideredUpperCaseValue = e1.consideredUpperCaseValue;
		}
	}

	/**
	 * Return a string representation of labeled values.h
	 */
	@SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification = "False positive.")
	@Nonnull
	@Override
	public String toString() {
		final StringBuilder superS = new StringBuilder(super.toString());
		superS.delete(superS.length() - Constants.CLOSE_TUPLE.length(), superS.length());
		if (upperCaseValueSize() > 0) {
			superS.append("UL: ").append(upperCaseValuesAsString()).append("; ");
		}
		if (lowerCaseValueSize() > 0) {
			superS.append("LL: ").append(lowerCaseValuesAsString()).append("; ");
		}
		superS.append(Constants.CLOSE_TUPLE);
		return superS.toString();
	}

	/**
	 *
	 */
	@Override
	public final int upperCaseValueSize() {
		return upperCaseValue.size();
	}

	/**
	 *
	 */
	@Override
	public final String upperCaseValuesAsString() {
		return upperCaseValue.toString();
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
	final int setUpperCaseValueAsConsidered(Label l, ALabel n, int i) {
		return consideredUpperCaseValue.put(new InternalEntry(l, n), i);
	}
}
