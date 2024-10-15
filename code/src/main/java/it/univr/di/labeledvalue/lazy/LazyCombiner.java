// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 *
 */
package it.univr.di.labeledvalue.lazy;

import it.univr.di.labeledvalue.Constants;
import org.apache.commons.math3.fraction.Fraction;

/**
 * Represent the sum of two other LazyWeight
 *
 * @author posenato
 * @version $Id: $Id
 */
public class LazyCombiner extends LazyWeight {
	/**
	 * Possible value for delta.
	 */
	private Fraction x;

	/**
	 * first operator
	 */
	LazyWeight op1;
	/**
	 * second operator
	 */
	LazyWeight op2;

	private final boolean onlyIfNeg;
	private final boolean onlyIfNegOp1;
	private final boolean onlyIfNegOp2;

	/**
	 *
	 */
	double cachedValue;

	/**
	 * <p>
	 * Constructor for LazyCombiner.
	 * </p>
	 *
	 * @param x1 a {@link org.apache.commons.math3.fraction.Fraction} object.
	 * @param op11 a {@link it.univr.di.labeledvalue.lazy.LazyWeight} object.
	 * @param op21 a {@link it.univr.di.labeledvalue.lazy.LazyWeight} object.
	 * @param onlyIfNeg1 a boolean.
	 * @param onlyIfNegOp11 a boolean.
	 * @param onlyIfNegOp21 a boolean.
	 */
	public LazyCombiner(Fraction x1, LazyWeight op11, LazyWeight op21, boolean onlyIfNeg1, boolean onlyIfNegOp11, boolean onlyIfNegOp21) {
		this(SubType.Sum, x1, sumWithOverflowCheck(op11.getValue(), op21.getValue()), op11, op21, onlyIfNeg1, onlyIfNegOp11, onlyIfNegOp21);
	}

	/**
	 * @param type none
	 * @param x1  none
	 * @param value none
	 * @param op11 none
	 * @param op21 none
	 * @param onlyIfNeg1 none
	 * @param onlyIfNegOp11 none
	 * @param onlyIfNegOp21 none
	 */
	LazyCombiner(SubType type, Fraction x1, double value, LazyWeight op11, LazyWeight op21, boolean onlyIfNeg1, boolean onlyIfNegOp11, boolean onlyIfNegOp21) {
		super(type);
		this.x = x1; // it must be ==op1.x==op2.x if they contain such x.
		this.op1 = op11;
		this.op2 = op21;
		this.onlyIfNeg = onlyIfNeg1;
		this.onlyIfNegOp1 = onlyIfNegOp11;
		this.onlyIfNegOp2 = onlyIfNegOp21;
		this.cachedValue = Constants.INT_NULL;
	}

	@Override
	public String toString() {
		return formatLazy(this.getValue()) + "[" + this.getType() + " " + this.op1.toString() + "; " + this.op2.toString() + "]";
	}

	/**
	 * <p>
	 * isOnlyIfNeg.
	 * </p>
	 *
	 * @return the onlyIfNeg
	 */
	public boolean isOnlyIfNeg() {
		return this.onlyIfNeg;
	}

	@Override
	public Fraction getX() {
		return this.x;
	}

	/**
	 * <p>
	 * isOnlyIfNegOp1.
	 * </p>
	 *
	 * @return the onlyIfNegOp1
	 */
	public boolean isOnlyIfNegOp1() {
		return this.onlyIfNegOp1;
	}

	/**
	 * <p>
	 * isOnlyIfNegOp2.
	 * </p>
	 *
	 * @return the onlyIfNegOp2
	 */
	public boolean isOnlyIfNegOp2() {
		return this.onlyIfNegOp2;
	}

	@Override
	public double getValue() {
		if (this.cachedValue == Constants.INT_NULL) {
			double v1 = this.op1.getValue(), v2;

			if (v1 == Constants.INT_POS_INFINITE || (v2 = this.op2.getValue()) == Constants.INT_POS_INFINITE) {
				this.cachedValue = Constants.INT_POS_INFINITE;
			} else {
				if (v1 == Constants.INT_NEG_INFINITE || v2 == Constants.INT_NEG_INFINITE) {
					this.cachedValue = Constants.INT_NEG_INFINITE;
				} else {
					this.cachedValue = LazyWeight.sumWithOverflowCheck(v1, v2);
				}
			}
		}
		return this.cachedValue;
	}

	@Override
	public void setX(Fraction newX) {
		if (this.x.equals(newX))
			return;
		this.op1.setX(newX);
		this.op2.setX(newX);
		this.x = newX;
		this.cachedValue = Constants.INT_NULL;
	}

}
