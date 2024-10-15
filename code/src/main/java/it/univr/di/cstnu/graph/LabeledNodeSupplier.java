// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.graph;

import com.google.common.base.Supplier;

import java.util.logging.Logger;

/**
 * LabeledIntEdgePluggable supplier.
 *
 * @author posenato
 * @version $Rev: 742 $
 */
public class LabeledNodeSupplier implements Supplier<LabeledNode> {

	/**
	 * class logger
	 */
	@SuppressWarnings("unused")
	static private final Logger LOG = Logger.getLogger(LabeledNodeSupplier.class.getName());

	/**
	 *
	 */
	public LabeledNodeSupplier() {// Class<C> labeledIntMapImplementation
		// this.labeledIntValueMapImpl = labeledIntMapImplementation;
	}

	/**
	 * @param node the edge to clone.
	 * @return a new edge
	 */
	@SuppressWarnings("static-method")
	public static LabeledNode get(LabeledNode node) {
		return new LabeledNode(node);// , this.labeledIntValueMapImpl
	}

	/**
	 * @param n           a {@link java.lang.String} object.
	 * @param proposition a char.
	 * @return a new edge
	 */
	@SuppressWarnings("static-method")
	public static LabeledNode get(final String n, final char proposition) {
		return new LabeledNode(n, proposition);// , this.labeledIntValueMapImpl
	}

	/**
	 *
	 */
	@Override
	public LabeledNode get() {
		return LabeledNodeSupplier.get("");
	}

	/**
	 * @param name a {@link java.lang.String} object.
	 * @return a new LabeledIntMap concrete object.
	 */
	@SuppressWarnings("static-method")
	public static LabeledNode get(String name) {
		return new LabeledNode(name);// , this.labeledIntValueMapImpl
	}

	/*
	 * @return the class chosen for creating new object.
	 *         public Class<C> getInternalObjectClass() {
	 *         return this.labeledIntValueMapImpl;
	 *         }
	 */

	// @Override
	// public String toString() {
	// return "Labeled value set managed as " + this.labeledIntValueMapImpl.toString() + ".";
	// }

}
