// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.graph;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import it.univr.di.Debug;
import it.univr.di.labeledvalue.ALabelAlphabet.ALetter;
import it.univr.di.labeledvalue.Constants;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Implements {@link STNUEdge} interface using signed integer values.
 *
 * @author posenato
 * @version $Rev: 906 $
 */
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "SE_BAD_FIELD", justification = "I know what I'm doing")
public class STNUEdgeInt extends STNEdgeInt implements STNUEdge {

	/**
	 *
	 */
	public static final long serialVersionUID = 4L;


//	/**
//	 * logger
//	 */
////	private static final Logger LOG = Logger.getLogger(STNUEdgeInt.class.getName());

	/**
	 * The labeled value associated to the edge when it is a contingent link or a wait constraint. Its value is
	 * {@link Constants#INT_NULL} when this edge is an ordinary one.
	 */
	int labeledValue = Constants.INT_NULL;

	/**
	 * The upper/lower case label.
	 * <br>
	 * The first component is the node label, the second specifies the nature: true for upper-case, false for
	 * lower-case.
	 */
	CaseLabel caseLabel;

	/**
	 * Constructor for STNUEdgeInt.
	 */
	public STNUEdgeInt() {
	}

	/**
	 * @param e a {@link it.univr.di.cstnu.graph.Edge} object.
	 */
	public STNUEdgeInt(Edge e) {
		super(e);
		if (e instanceof STNUEdge e1) {
			caseLabel = e1.getCaseLabel();// pair is read-only
			labeledValue = e1.getLabeledValue();
		}
	}

	/**
	 * @param n a {@link java.lang.String} object.
	 */
	public STNUEdgeInt(String n) {
		super(n);
	}

	/**
	 * @param n a {@link java.lang.String} object.
	 * @param v the weight of the edge
	 */
	public STNUEdgeInt(String n, int v) {
		super(n);
		setValue(v);
	}

	/**
	 *
	 */
	@Override
	public void clear() {
		super.clear();
		labeledValue = Constants.INT_NULL;
		caseLabel = null;
	}

	/**
	 *
	 */
	@Override
	public CaseLabel getCaseLabel() {
		return caseLabel;
	}

	/**
	 *
	 */
	@Override
	public int getLabeledValue() {
		return labeledValue;
	}

	/**
	 *
	 */
	@Override
	public boolean hasSameValues(Edge e) {
		if (!(e instanceof STNUEdge e1)) {
			return false;
		}
		if (value != e1.getValue()) {
			return false;
		}
		if (!this.isWait() && !e1.isWait()) {
			return true;
		}
		if (this.isWait() && e1.isWait()) {
			return labeledValue == e1.getLabeledValue() && caseLabel.equals(e1.getCaseLabel());
		}
		return false;
	}

	/**
	 *
	 */
	@Override
	public boolean isEmpty() {
		return getValue() == Constants.INT_NULL && labeledValue == Constants.INT_NULL;
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
		return true;
	}


	/**
	 *
	 */
	@Override
	public STNUEdgeInt newInstance() {
		return new STNUEdgeInt();
	}

	/**
	 *
	 */
	@Override
	public STNUEdgeInt newInstance(Edge edge) {
		return new STNUEdgeInt(edge);
	}

	/**
	 *
	 */
	@Override
	public STNUEdgeInt newInstance(String name1) {
		return new STNUEdgeInt(name1);
	}


	/**
	 * Removes the labeled value and make the edges an ordinary constraint if it was contingent.
	 *
	 * @return the old labeled value.
	 */
	public int resetLabeledValue() {
		final int old = labeledValue;
		caseLabel = null;
		labeledValue = Constants.INT_NULL;
		if (constraintType == ConstraintType.contingent) {
			constraintType = ConstraintType.requirement;
		}
		return old;
	}

	/**
	 *
	 */
	@Override
	public int setLabeledValue(ALetter nodeALetter, int w, boolean upperCase) {
		final int old = labeledValue;
		if (nodeALetter == null || w == Constants.INT_NULL) {
			return resetLabeledValue();
		}
		if (!upperCase && w < 0) {
			throw new IllegalArgumentException(
				"A lower-case value cannot be negative. Details: " + nodeALetter + ": " + w + ".");
		}
		labeledValue = w;
		caseLabel = new CaseLabel(nodeALetter, upperCase);
		//		this.constraintType = ConstraintType.contingent;NO because contingent type is only for original contingent link, not for wait!
		return old;
	}


	/**
	 * If the edge is not a wait, it sets the ordinary value if v is smaller than the current one. If the edge is a wait, it sets the ordinary value if v is
	 * negative. Moreover, if v is smaller (i.e., stronger) than the wait value, the wait is removed.
	 *
	 * @param v the new weight value
	 *
	 * @return true if the update occurred, false otherwise;
	 */
	@Override
	public boolean updateValue(int v) {
		//Posenato 2024-10-03
		//This method HAS TO STAY in this class and not into STNUEdge.java
		//because it is the only way to override the updateValue present in the super class STNEdgeInt
		if (this.isWait()) {
			if (v >= 0) {
				return false;
			}
			if (v <= this.labeledValue) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("Edge " + this + ": new value " + v + " removed wait " + this.labeledValue);
					}
				}
				this.resetLabeledValue();
			}
		}
		return super.updateValue(v);
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
		if ((e instanceof STNUEdge e1)) {
			labeledValue = e1.getLabeledValue();
			caseLabel = e1.getCaseLabel();
		}
	}

	/**
	 * @return string representation of the edge
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
		if (getValue() != Constants.INT_NULL) {
			sb.append(Constants.formatInt(value)).append("; ");
		}
		final String lvf = getLabeledValueFormatted();
		if (lvf.length() > 0) {
			sb.append(lvf);
		}
		sb.append(Constants.CLOSE_TUPLE);
		return sb.toString();
	}
}
