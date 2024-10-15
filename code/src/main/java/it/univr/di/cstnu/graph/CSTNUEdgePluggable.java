// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.graph;

import it.unimi.dsi.fastutil.objects.AbstractObject2IntMap.BasicEntry;
import it.unimi.dsi.fastutil.objects.AbstractObject2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.univr.di.labeledvalue.*;

import javax.annotation.Nullable;
import java.io.Serial;
import java.util.logging.Logger;

/**
 * An implementation of CSTNUEdge where the labeled value set can be plugged during the creation.
 *
 * @author posenato
 * @version $Rev: 852 $
 */
public class CSTNUEdgePluggable extends BasicCSTNUEdgePluggable implements CSTNUEdge {
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
		LOG = Logger.getLogger(CSTNUEdgePluggable.class.getName());
	}

	/**
	 * Morris Lower case value augmented by a propositional label.
	 * <p>The name of node has to be equal to the original name. No case modifications are necessary!</p>
	 */
	LabeledLowerCaseValue lowerCaseValue;

	/**
	 * Internal constructor
	 */
	CSTNUEdgePluggable() {
		this((String) null);
	}

	/**
	 * @param n name of edge
	 */
	CSTNUEdgePluggable(String n) {
		this(n, null);
	}

	/**
	 * @param n                 name of edge
	 * @param labeledIntMapImpl class for representing labeled values. If null, {@link LabeledIntMapSupplier#DEFAULT_LABELEDINTMAP_CLASS} is used.
	 */
	CSTNUEdgePluggable(String n, Class<? extends LabeledIntMap> labeledIntMapImpl) {
		super(n, labeledIntMapImpl);
		lowerCaseValue = LabeledLowerCaseValue.emptyLabeledLowerCaseValue;
	}


	/**
	 * Constructor to clone the component.
	 *
	 * @param e the edge to clone.
	 */
	CSTNUEdgePluggable(Edge e) {
		this(e, null);
	}

	/**
	 * Constructor to clone the component.
	 *
	 * @param e the edge to clone.
	 */
	CSTNUEdgePluggable(Edge e, Class<? extends LabeledIntMap> labeledIntMapImpl) {
		super(e, labeledIntMapImpl);
		if (e != null && CSTNUEdge.class.isAssignableFrom(e.getClass())) {
			final CSTNUEdge e1 = (CSTNUEdge) e;
			lowerCaseValue = LabeledLowerCaseValue.create(e1.getLowerCaseValue());
		} else {
			lowerCaseValue = LabeledLowerCaseValue.emptyLabeledLowerCaseValue;
		}
	}


	@Override
	public void clear() {
		super.clear();
		lowerCaseValue = LabeledLowerCaseValue.emptyLabeledLowerCaseValue;
	}

	@Override
	public void clearLowerCaseValues() {
		removeLowerCaseValue();
	}

	@Override
	public LabeledLowerCaseValue getLowerCaseValue() {
		return lowerCaseValue;
	}

	@Override
	public void setLowerCaseValue(LabeledLowerCaseValue inputLabeledValue) {
		lowerCaseValue = inputLabeledValue;
		if (!lowerCaseValue.isEmpty()) {
			setConstraintType(ConstraintType.contingent);
			pcs.firePropertyChange("lowerLabel:add", null, lowerCaseValue);
		}
	}

	@Nullable
	@Override
	public Object2ObjectMap.Entry<Label, Object2IntMap.Entry<ALabel>> getMinLowerCaseValue() {
		if (lowerCaseValue.isEmpty()) {
			return null;
		}
		return new AbstractObject2ObjectMap.BasicEntry<>(lowerCaseValue.getLabel(), new BasicEntry<>(lowerCaseValue.getNodeName(), lowerCaseValue.getValue()));
	}

	@Override
	public boolean hasSameValues(Edge e) {
		if (!(e instanceof CSTNUEdge e1)) {
			return false;
		}
		if (e == this) {
			return true;
		}
		if (!super.hasSameValues(e)) {
			return false;
		}
		return (lowerCaseValue.equals(e1.getLowerCaseValue()));
	}

	@Override
	public boolean isCSTNUEdge() {
		return true;
	}

	@Override
	public boolean isEmpty() {
		return super.isEmpty() && lowerCaseValue.isEmpty();
	}

	@Override
	public int lowerCaseValueSize() {
		return lowerCaseValue.isEmpty() ? 0 : 1;
	}

	@Override
	public String lowerCaseValuesAsString() {
		return lowerCaseValue.toString();
	}

	@Override
	public boolean mergeLowerCaseValue(Label l, ALabel nodeName, int i) {
		setLowerCaseValue(l, nodeName, i);
		return true;
	}

	@Override
	public CSTNUEdgePluggable newInstance() {
		return new CSTNUEdgePluggable();
	}

	@Override
	public CSTNUEdgePluggable newInstance(Edge edge) {
		return new CSTNUEdgePluggable(edge);
	}

	@Override
	public CSTNUEdgePluggable newInstance(String name1) {
		return new CSTNUEdgePluggable(name1);
	}

	@Override
	public final boolean putLowerCaseValue(Label l, ALabel nodeName, int i) {
		return mergeLowerCaseValue(l, nodeName, i);
	}

	@Override
	public int removeLowerCaseValue() {
		if (lowerCaseValue.isEmpty()) {
			return Constants.INT_NULL;
		}

		final int i = lowerCaseValue.getValue();
		lowerCaseValue = LabeledLowerCaseValue.emptyLabeledLowerCaseValue;
		setConstraintType(ConstraintType.requirement);
		pcs.firePropertyChange("lowerLabel:remove", null, lowerCaseValue);
		return i;
	}

	@Override
	public void setLowerCaseValue(Label l, ALabel nodeName, int i) {
		setLowerCaseValue(LabeledLowerCaseValue.create(nodeName, i, l));
	}

	@Override
	public void takeIn(Edge e) {
		if (e == null) {
			return;
		}
		super.takeIn(e);
		if (e instanceof CSTNUEdgePluggable e1) {
			lowerCaseValue = e1.lowerCaseValue;
		}
	}
}
