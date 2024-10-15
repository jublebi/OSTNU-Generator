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
 * Represent the max of two other LazyWeight
 *
 * @author posenato
 * @version $Id: $Id
 */
@SuppressWarnings("ALL")
public class LazyMax extends LazyCombiner {

	/**
	 * <p>Constructor for LazyMax.</p>
	 *
	 * @param x1 a {@link org.apache.commons.math3.fraction.Fraction} object.
	 * @param op_1 a {@link it.univr.di.labeledvalue.lazy.LazyWeight} object.
	 * @param op_2 a {@link it.univr.di.labeledvalue.lazy.LazyWeight} object.
	 * @param onlyIfNeg a boolean.
	 * @param onlyIfNegOp1 a boolean.
	 * @param onlyIfNegOp2 a boolean.
	 */
	public LazyMax(Fraction x1, LazyWeight op_1, LazyWeight op_2, boolean onlyIfNeg, boolean onlyIfNegOp1, boolean onlyIfNegOp2) {
		super(SubType.Max, x1, (op_1.getValue() >= op_2.getValue()) ? op_1.getValue() : op_2.getValue(), op_1, op_2, onlyIfNeg, onlyIfNegOp1, onlyIfNegOp2);
	}

	/** {@inheritDoc} */
	@Override
	public double getValue() {
		if (this.cachedValue == Constants.INT_NULL) {
			this.cachedValue = this.op1.getValue() < this.op2.getValue() ? this.op2.getValue() : this.op1.getValue();
		}
		return this.cachedValue;
	}

}
