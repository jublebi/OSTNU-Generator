// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.graph;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import it.univr.di.labeledvalue.Constants;

import javax.annotation.Nonnull;
import java.io.Serial;

/**
 * Implementation of STNEdge using integer for weight.
 *
 * @author posenato
 * @version $Rev: 840 $
 */
public class STNEdgeInt extends AbstractEdge implements STNEdge {

	/**
	 *
	 */
	@Serial
	private static final long serialVersionUID = 2L;

	/**
	 * the value associated to the edge
	 */
	int value = Constants.INT_NULL;

	/**
	 * Default constructor.
	 */
	public STNEdgeInt() {
	}

	/**
	 * Creates a new edge cloning {@code e} if not null. Otherwise, it creates an empty edge.
	 *
	 * @param e the component to clone.
	 *
	 * @see AbstractEdge#AbstractEdge(Edge)
	 */
	public STNEdgeInt(Edge e) {
		super(e);
		if (e instanceof STNEdge) {
			value = ((STNEdge) e).getValue();
		}
	}

	/**
	 * @param n a {@link java.lang.String} object.
	 */
	public STNEdgeInt(String n) {
		super(n);
	}

	/**
	 *
	 */
	@Override
	public void clear() {
		super.clear();
		value = Constants.INT_NULL;
	}

	/**
	 *
	 */
	@Override
	public int getValue() {
		return value;
	}

	/**
	 *
	 */
	@Override
	public boolean hasSameValues(Edge e) {
		if (!(e instanceof STNEdge)) {
			return false;
		}
		return value == ((STNEdge) e).getValue();
	}

	/**
	 *
	 */
	@Override
	public boolean isEmpty() {
		return value == Constants.INT_NULL;
	}

	/**
	 *
	 */
	@Override
	public boolean isSTNEdge() {
		return true;
	}

	/**
	 *
	 */
	@Override
	public STNEdgeInt newInstance() {
		return new STNEdgeInt();
	}

	// @Override
	// public STNEdgeInt newInstance(Class<? extends LabeledIntMap> labeledIntMapImpl) {
	// return newInstance();
	// }

	/**
	 *
	 */
	@Override
	public STNEdgeInt newInstance(Edge edge) {
		return new STNEdgeInt(edge);
	}

	// @Override
	// public STNEdgeInt newInstance(Edge e, Class<? extends LabeledIntMap> labeledIntMapImpl) {
	// return new STNEdgeInt(edge);
	// }

	/**
	 *
	 */
	@Override
	public STNEdgeInt newInstance(String name1) {
		return new STNEdgeInt(name1);
	}

	// @Override
	// public STNEdgeInt newInstance(String name1, Class<? extends LabeledIntMap> labeledIntMapImpl) {
	// return new STNEdgeInt(name1);
	// }

	/**
	 *
	 */
	@Override
	public int setValue(int w) {
		final int old = value;
		value = w;
		return old;
	}

	/**
	 *
	 */
	@Override
	public void takeIn(Edge e) {
		if (!(e instanceof STNEdge)) {
			return;
		}
		super.takeIn(e);
		value = ((STNEdge) e).getValue();
	}

	/**
	 * @return string representation of the edge.
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
		sb.append(Constants.formatInt(value)).append("; ");
		sb.append(Constants.CLOSE_TUPLE);
		return sb.toString();
	}

	/**
	 * Sets the ordinary value if v is smaller than the current one.
	 *
	 * @param v the new weight value
	 *
	 * @return true if the update occurred, false otherwise;
	 */
	@Override
	public boolean updateValue(int v) {
		final int oldV = this.value;
		if (oldV == Constants.INT_NULL || v < oldV) {
			this.value = v;
			return true;
		}
		return false;
	}

}
