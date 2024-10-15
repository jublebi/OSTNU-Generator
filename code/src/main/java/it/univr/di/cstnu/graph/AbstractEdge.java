// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.graph;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.annotation.Nonnull;
import java.io.Serial;

/**
 * Base class for implementing LabeledIntEdge.
 *
 * @author posenato
 * @version $Rev: 886 $
 */
@SuppressWarnings("ClassWithoutLogger")
public abstract class AbstractEdge extends AbstractComponent implements Edge {
	/*
	 * logger
	 */
	//	static final Logger LOG = Logger.getLogger("AbstractEdge");
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
		return AbstractEdge.idSeq++;
	}

	/**
	 * The type of the edge.
	 */
	ConstraintType constraintType;


	/**
	 * Minimal constructor. the name will be 'e&lt;id&gt;'.
	 */
	public AbstractEdge() {
		this((Edge) null);
	}

	/**
	 * Creates a new edge cloning {@code e} if not null. Otherwise, it creates an empty edge.
	 *
	 * @param e the component to clone.
	 *
	 * @see #AbstractEdge(String)
	 */
	AbstractEdge(Edge e) {
		this((e != null) ? e.getName() : null);
		if (e != null) {
			setConstraintType(e.getConstraintType());
		}
	}

	/**
	 * Creates an edge with name {@code n} and type {@link ConstraintType#requirement}.
	 *
	 * @param n name of edge
	 *
	 * @see AbstractComponent#AbstractComponent(String)
	 */
	AbstractEdge(String n) {
		super(n);
		this.setName(((n == null) || (n.isEmpty())) ? "e%d".formatted(AbstractEdge.nextId()) : n);
		setConstraintType(ConstraintType.requirement);
	}

	/**
	 *
	 */
	@Override
	public final ConstraintType getConstraintType() {
		return constraintType;
	}

	/**
	 *
	 */
	@Override
	public void setConstraintType(ConstraintType type) {
		final ConstraintType old = constraintType;
		constraintType = type;
		pcs.firePropertyChange("edgeType", old, type);
	}

	/**
	 *
	 */
	@Override
	public boolean isCSTNEdge() {
		return false;
	}

	/**
	 *
	 */
	@Override
	public boolean isCSTNPSUEdge() {
		return false;
	}

	/**
	 *
	 */
	@Override
	public boolean isCSTNUEdge() {
		return false;
	}

	/**
	 *
	 */
	@Override
	public boolean isOSTNUEdge() {
		return false;
	}

	/**
	 *
	 */
	@Override
	public boolean isSTNEdge() {
		return false;
	}

	/**
	 *
	 */
	@Override
	public boolean isSTNUEdge() {
		return false;
	}


	/**
	 * Sets the name of the edge.
	 *
	 * @param newName cannot empty. If empty, it does nothing.
	 *
	 * @return the old name.
	 */
	@Nonnull
	@Override
	@SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification = "False positive.")
	public String setName(@Nonnull String newName) {
		final String old = this.getName();
		if (!newName.isEmpty()) {
			name = newName;
			pcs.firePropertyChange("edgeName", old, newName);
		}
		return old;
	}

	/**
	 *
	 */
	@Override
	public void takeIn(Edge e) {
		super.takeIn(e);
		constraintType = e.getConstraintType();
	}
}
