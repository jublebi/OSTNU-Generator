// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.graph;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.annotation.Nonnull;
import java.io.Serial;

/**
 * Base class for implementing Node.
 *
 * @author posenato
 * @version $Rev: 840 $
 */
@SuppressWarnings({"AbstractClassWithOnlyOneDirectInheritor", "ClassWithoutLogger"})
public abstract class AbstractNode extends AbstractComponent implements Node {
	/**
	 *
	 */
	@Serial
	private static final long serialVersionUID = 1L;
	/**
	 * To provide a unique id for the default creation of component.
	 */
	static private int idSeq;

	/**
	 * @return next id
	 */
	static private int nextId() {
		return idSeq++;
	}

	/**
	 * Minimal constructor.
	 *
	 * @see #AbstractNode(String)
	 */
	public AbstractNode() {
		this((String) null);
	}

	/**
	 * Simplified constructor
	 *
	 * @param n name of node
	 */
	AbstractNode(String n) {
		super(n);
		this.setName(((n == null) || (n.isEmpty())) ? "n%d".formatted(AbstractNode.nextId()) : n);
	}

	/**
	 * Constructor to clone the component.
	 *
	 * @param node the component to clone.
	 */
	AbstractNode(Node node) {
		this((node != null) ? node.getName() : null);
	}

	/**
	 * Sets the name of the node.
	 *
	 * @param newName cannot empty. If empty, it does nothing.
	 * @return the old name.
	 */
	@SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification = "False positive.")
	@Override
	@Nonnull
	public String setName(@Nonnull String newName) {
		final String old = this.getName();
		if (!newName.isEmpty()) {
			name = newName;
			pcs.firePropertyChange("nodeName", old, newName);
		}
		return old;
	}

	/**
	 *
	 */
	@Override
	public void takeIn(Node n) {
		super.takeIn(n);
	}
}
