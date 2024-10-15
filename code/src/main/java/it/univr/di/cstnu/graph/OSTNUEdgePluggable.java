// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.graph;

import it.univr.di.labeledvalue.Label;
import it.univr.di.labeledvalue.LabeledIntMap;
import it.univr.di.labeledvalue.LabeledIntSimpleMap;

import java.io.Serial;
import java.util.logging.Logger;

/**
 * An implementation of OSTNUEdge where the labeled value set is not optimized for maintain scenarios like ¬c,0 and c,1.
 *
 * @author posenato
 * @version $Rev: 840 $
 */
public class OSTNUEdgePluggable extends CSTNUEdgePluggable {

	/**
	 * Class for representing labeled values. It is important that values are not simplified with respect to the length of the labels. In other words, {(0,¬C),
	 * (1, C)} MUST NOT BE SIMPLIFIED as  {(0,⊡), (1, C)}.
	 */
	static final Class<? extends LabeledIntMap> DEFAULT_LABELED_INT_MAP_CLASS = LabeledIntSimpleMap.class;

	/**
	 *
	 */
	@Serial
	private static final long serialVersionUID = 1L;

	/*
	 * class initializer
	 */
	static {
		/*
		 * logger
		 */
		LOG = Logger.getLogger(OSTNUEdgePluggable.class.getName());
	}

	/**
	 *
	 */
	OSTNUEdgePluggable() {
		super((String) null, DEFAULT_LABELED_INT_MAP_CLASS);
	}

	/**
	 * @param n name of edge
	 */
	OSTNUEdgePluggable(String n) {
		super(n, DEFAULT_LABELED_INT_MAP_CLASS);
	}

	/**
	 * Constructor to clone the component.
	 *
	 * @param e the edge to clone.
	 */
	OSTNUEdgePluggable(Edge e) {
		super(e, DEFAULT_LABELED_INT_MAP_CLASS);
	}


	@Override
	public OSTNUEdgePluggable newInstance() {
		return new OSTNUEdgePluggable();
	}

	@Override
	public OSTNUEdgePluggable newInstance(Edge edge) {
		return new OSTNUEdgePluggable(edge);
	}

	@Override
	public OSTNUEdgePluggable newInstance(String name1) {
		return new OSTNUEdgePluggable(name1);
	}


	//For making this more general and suitable for more general algorithm not based on labeled values
	/**
	 * Allows the use of values without label because it is possible to have a better algorithm in the future that it avoids labeled values.
	 * This method must be used only if no values labeled by oracles are used.
	 * @param i new value
	 */
	public void setValue(int i){
		this.mergeLabeledValue(Label.emptyLabel, i);

	}

	/**
	 * @return the label without label.
	 */
	public int getValue() {
		return this.getValue(Label.emptyLabel);
	}
}
