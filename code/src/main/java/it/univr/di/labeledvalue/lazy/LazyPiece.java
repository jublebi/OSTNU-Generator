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
 * Represent a linear piece wise function.
 * <code>
 * value = m*x+c
 * </code>
 * This class and its subclasses MUST BE considered IMMUTABLE even if we don't make it immutable for efficiency reasons.
 *
 * @author posenato
 * @version $Id: $Id
 */
@SuppressWarnings("ALL")
public final class LazyPiece extends LazyWeight {
	/**
	 * Possible value for delta.
	 */
	private Fraction x;
	/**
	 * Value of constant factor of this linear piece.
	 */
	private final int c;
	/**
	 * Value of multiplier factor of this linear piece.
	 */
	private final int m;

	/**
	 *
	 */
	double cachedValue;

	/**
	 * This value can be used only if it is negative.
	 */
	private final boolean onlyIfNeg;

	/**
	 * <p>
	 * Constructor for LazyPiece.
	 * </p>
	 *
	 * @param x1 a {@link org.apache.commons.math3.fraction.Fraction} object.
	 * @param m1 a int.
	 * @param c1 a int.
	 * @param onlyIfNeg1 a boolean.
	 */
	public LazyPiece(Fraction x1, int m1, int c1, boolean onlyIfNeg1) {
		super(SubType.Piece);
		this.x = x1;
		this.c = c1;
		this.m = m1;
		this.cachedValue = Constants.INT_NULL;
		this.onlyIfNeg = onlyIfNeg1;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return formatLazy(getValue()) + "[Piece " + formatLazy(this.getM()) + " * ∂ + " + formatLazy(this.getC()) + "]";
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

	/** {@inheritDoc} */
	@Override
	public Fraction getX() {
		return this.x;
	}

	/**
	 * <p>
	 * Getter for the field <code>c</code>.
	 * </p>
	 *
	 * @return the c
	 */
	public int getC() {
		return this.c;
	}

	/**
	 * <p>
	 * Getter for the field <code>m</code>.
	 * </p>
	 *
	 * @return the m
	 */
	public int getM() {
		return this.m;
	}

	/** {@inheritDoc} */
	@Override
	public void setX(Fraction newX) {
		if (this.x.equals(newX))
			return;
		this.x = newX;
		this.cachedValue = Constants.INT_NULL;
	}

	/** {@inheritDoc} */
	@Override
	public double getValue() {
		if (this.cachedValue == Constants.INT_NULL) {
			if (this.x.intValue() == Constants.INT_NULL) {
				this.cachedValue = Constants.INT_NULL;
			} else {
				if (this.c == Constants.INT_NEG_INFINITE || this.c == Constants.INT_POS_INFINITE) {
					this.cachedValue = this.c;
				} else {
					this.cachedValue = this.x.multiply(this.m).add(this.c).doubleValue();
				}
			}
		}
		return this.cachedValue;
	}
}
