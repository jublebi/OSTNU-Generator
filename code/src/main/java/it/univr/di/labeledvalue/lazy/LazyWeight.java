// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 *
 */
package it.univr.di.labeledvalue.lazy;

import it.univr.di.labeledvalue.Constants;
import it.univr.di.labeledvalue.Label;
import org.apache.commons.math3.fraction.Fraction;

/**
 * Represents the leftist peace of a piece-wise function (PWF) set present in an edge.
 * This entity should be the top of an hierarchy of 4 different types of PWF.
 * Best practice of object programming would require to write down such hierarchy and to implement all methods exploiting method overriding.
 * <br>
 * Although, there is a big issue: the two main methods (sum and max) have different type of behavior according to the two different type of operands (i.e.,
 * sum works in one way if both operands are of A type, in another if both are of B type, but completely different if one is A and the other is B. LazyWeight
 * has 4 different subtypes).
 * <br>
 * In other words, while hierarchy like Figure<-Circle Figure<-Rectangular with method 'area' are perfect example of hierarchy to implement,
 * this class is different and, I think, requires a more efficient implementation that avoid overload for resolving all possible combinations of types.
 * <br>
 * For this reason, the class is organized as an hierarchy but only for the member. The two methods sum and max are implemented as static member on the top
 * class,
 * where all mixes of operands are managed exploiting a tag that allows a fast subtype identification.
 * This class and its subclasses MUST BE considered IMMUTABLE.
 *
 * @author posenato
 * @version $Id: $Id
 */
@SuppressWarnings("ALL")
public abstract class LazyWeight {

	/**
	 * @author posenato
	 */
	public enum SubType {
		/**
		 *
		 */
		Max,
		/**
		*
		*/
		Number,
		/**
		*
		*/
		Piece,
		/**
		*
		*/
		Sum
	}

	/**
	 * <p>
	 * formatLazy.
	 * </p>
	 *
	 * @param n a double
	 * @return the value of {@code n} as a String using {@link it.univr.di.labeledvalue.Constants#INFINITY_SYMBOL} for the infinitive and {@code NaN} for not
	 *         valid integer
	 */
	static public final String formatLazy(double n) {
		if (n == Constants.INT_NEG_INFINITE)
			return Constants.NEGATIVE_INFINITY_SYMBOLstring;
		if (n == Constants.INT_POS_INFINITE)
			return Constants.INFINITY_SYMBOLstring;
		if (n == Constants.INT_NULL)
			return "NaN";
		return Double.toString(n);
	}

	/**
	 * <pre>
	 * P? --op1,alpha--> T <--op2,beta------------------- Y
	 *   				   <--max(op1,op2), finalLabel---
	 *
	 * finalLabel := alpha * beta
	 * Condition for applying: op1<0
	 * </pre>
	 *
	 * @param op1 a {@link it.univr.di.labeledvalue.lazy.LazyWeight} object.
	 * @param op2 a {@link it.univr.di.labeledvalue.lazy.LazyWeight} object.
	 * @param finalLabel a {@link it.univr.di.labeledvalue.Label} object.
	 * @return the max between op1 and op2
	 */
	static public LazyWeight max(LazyWeight op1, LazyWeight op2, Label finalLabel) {
		switch (op1.type) {
		case Number: {
			if (op1.getValue() >= 0)
				return null;
			if (op1.equals(LazyNumber.LazyNegInfty))
				return op2;
			if (op2.type == SubType.Number) {
				int v = (int) ((op1.getValue() < op2.getValue()) ? op2.getValue() : op1.getValue());
				return LazyNumber.get(v);
			}
			if (op2.type == SubType.Piece) {
				LazyPiece op2a = (LazyPiece) op2;
				return new LazyMax(op2a.getX(), op1, op2a, false, true, op2a.isOnlyIfNeg());
			}
			if (op2.type == SubType.Sum || op2.type == SubType.Max) {
				LazyCombiner op2a = (LazyCombiner) op2;
				return new LazyMax(op2a.getX(), op1, op2, false, true, op2a.isOnlyIfNeg());
			}
			break;
		}

		case Piece: {
			LazyPiece op1a = (LazyPiece) op1;
			if (op1.getValue() >= 0)
				return null;
			if (op2.type == SubType.Number) {
				if (op2.equals(LazyNumber.LazyNegInfty))
					return op1a;
				return new LazyMax(op1a.getX(), op1a, op2, false, true, false);
			}
			if (op2.type == SubType.Piece) {
				LazyPiece op2a = (LazyPiece) op2;
				return new LazyMax(op1a.getX(), op1, op2, false, true, op2a.isOnlyIfNeg());
			}
			if (op2.type == SubType.Sum || op2.type == SubType.Max) {
				LazyCombiner op2a = (LazyCombiner) op2;
				return new LazyMax(op1a.getX(), op1, op2, false, true, op2a.isOnlyIfNeg());
			}
			break;
		}

		case Max:
		case Sum: {
			LazyCombiner op1a = (LazyCombiner) op1;
			if (op1.getValue() >= 0)
				return null;
			if (op2.type == SubType.Number) {
				if (op2.equals(LazyNumber.LazyNegInfty))
					return op1a;
				return new LazyMax(op1a.getX(), op1, op2, false, true, false);
			}
			if (op2.type == SubType.Piece) {
				LazyPiece op2a = (LazyPiece) op2;
				return new LazyMax(op1a.getX(), op1, op2, false, true, op2a.isOnlyIfNeg());
			}
			if (op2.type == SubType.Sum || op2.type == SubType.Max) {
				LazyCombiner op2a = (LazyCombiner) op2;
				return new LazyMax(op1a.getX(), op1, op2, false, true, op2a.isOnlyIfNeg());
			}
			break;
		}
		default:
			throw new IllegalArgumentException("Type not implemented");
		}
		return null;
	}

	/**
	 * <code>
	 * X --op1,alpha--> Y -op2,beta--> T
	 *   -------op1+op2,sumLabel--->
	 *
	 * sumLabel := alpha * beta
	 * Condition for applying: op1+op2<0 and (op1<0 or sumLabel in P*)
	 * </code>
	 *
	 * @param op1 a {@link it.univr.di.labeledvalue.lazy.LazyWeight} object.
	 * @param op2 a {@link it.univr.di.labeledvalue.lazy.LazyWeight} object.
	 * @param sumLabel must be determined in extended (with unknowns) way.
	 * @return the sum of op1 and op2
	 */
	static public LazyWeight sum(LazyWeight op1, LazyWeight op2, Label sumLabel) {
		switch (op1.type) {
		case Number: {
			if (op1.getValue() > 0 && sumLabel.containsUnknown())
				return null;
			if (op1.equals(LazyNumber.LazyNegInfty))
				return LazyNumber.LazyNegInfty;
			if (op2.type == SubType.Number) {
				if (op2.equals(LazyNumber.LazyNegInfty))
					return LazyNumber.LazyNegInfty;
				int sum = (int) sumWithOverflowCheck(op1.getValue(), op2.getValue());
				if (sum > 0)
					return null;
				return LazyNumber.get(sum);
			}
			if (op2.type == SubType.Piece) {
				LazyPiece op2a = (LazyPiece) op2;
				return new LazyPiece(op2a.getX(), op2a.getM(), (int) sumWithOverflowCheck(op2a.getC(), op1.getValue()), true);
			}
			if (op2.type == SubType.Sum || op2.type == SubType.Max) {
				LazyCombiner op2a = (LazyCombiner) op2;
				return new LazyCombiner(op2a.getX(), op1, op2, true, op1.getValue() < 0, op2a.isOnlyIfNeg());
			}
			break;
		}

		case Piece: {
			LazyPiece op1a = (LazyPiece) op1;
			if (!op1a.isOnlyIfNeg() && sumLabel.containsUnknown())
				return null;
			if (op2.type == SubType.Number) {
				if (op2.equals(LazyNumber.LazyNegInfty))
					return LazyNumber.LazyNegInfty;
				return new LazyPiece(op1a.getX(), op1a.getM(), (int) sumWithOverflowCheck(op1a.getC(), op2.getValue()), true);
			}
			if (op2.type == SubType.Piece) {
				LazyPiece op2a = (LazyPiece) op2;
				return new LazyPiece(op1a.getX(), (int) sumWithOverflowCheck(op1a.getM(), op2a.getM()), (int) sumWithOverflowCheck(op1a.getC(), op2a.getC()),
						true);// Luke decided this instead of SUM
			}
			if (op2.type == SubType.Sum) {
				LazyCombiner op2a = (LazyCombiner) op2;
				return new LazyCombiner(op1a.getX(), op1, op2, true, op1a.isOnlyIfNeg(), op2a.isOnlyIfNeg());
			}
			if (op2.type == SubType.Max) {// Luke decided this instead of SUM
				LazyCombiner op2a = (LazyMax) op2;
				return new LazyMax(op1a.getX(), op1, op2, true, op1a.isOnlyIfNeg(), op2a.isOnlyIfNeg());
			}

			break;
		}

		case Max: {
			LazyMax op1a = (LazyMax) op1;
			if (!op1a.isOnlyIfNeg() && sumLabel.containsUnknown())
				return null;
			if (op2.type == SubType.Number) {
				if (op2.equals(LazyNumber.LazyNegInfty))
					return LazyNumber.LazyNegInfty;
				return new LazyMax(op1a.getX(), op1, op2, true, op1a.isOnlyIfNeg(), false);
			}
			if (op2.type == SubType.Piece) {
				LazyPiece op2a = (LazyPiece) op2;
				return new LazyMax(op1a.getX(), op1, op2, true, op1a.isOnlyIfNeg(), op2a.isOnlyIfNeg());
			}
			if (op2.type == SubType.Sum || op2.type == SubType.Max) {
				LazyCombiner op2a = (LazyCombiner) op2;
				return new LazyMax(op1a.getX(), op1, op2, true, op1a.isOnlyIfNeg(), op2a.isOnlyIfNeg());
			}
			break;
		}
		case Sum: {
			LazyCombiner op1a = (LazyCombiner) op1;
			if (!op1a.isOnlyIfNeg() && sumLabel.containsUnknown())
				return null;
			if (op2.type == SubType.Number) {
				if (op2.equals(LazyNumber.LazyNegInfty))
					return LazyNumber.LazyNegInfty;
				return new LazyCombiner(op1a.getX(), op1, op2, true, op1a.isOnlyIfNeg(), false);
			}
			if (op2.type == SubType.Piece) {
				LazyPiece op2a = (LazyPiece) op2;
				return new LazyCombiner(op1a.getX(), op1, op2, true, op1a.isOnlyIfNeg(), op2a.isOnlyIfNeg());
			}
			if (op2.type == SubType.Sum) {
				LazyCombiner op2a = (LazyCombiner) op2;
				return new LazyCombiner(op1a.getX(), op1, op2, true, op1a.isOnlyIfNeg(), op2a.isOnlyIfNeg());
			}
			if (op2.type == SubType.Max) {// Luke decided this instead of SUM
				LazyMax op2a = (LazyMax) op2;
				return new LazyMax(op1a.getX(), op1, op2, true, op1a.isOnlyIfNeg(), op2a.isOnlyIfNeg());
			}

			break;
		}
		default:
			throw new IllegalArgumentException("Type not implemented");
		}
		return null;
	}

	/**
	 * Determines the sum of {@code a} and {@code b}. If any of them is already INFINITY, returns INFINITY.
	 *
	 * @param a an integer
	 * @param b an integer
	 * @return the controlled sum
	 * @throws java.lang.ArithmeticException if any.
	 * @throws java.lang.ArithmeticException if any.
	 * @throws java.lang.ArithmeticException if any.
	 */
	static public final double sumWithOverflowCheck(final double a, final double b) throws ArithmeticException {
		double max, min;
		if (a >= b) {
			max = a;
			min = b;
		} else {
			min = a;
			max = b;
		}
		if (min == Constants.INT_NEG_INFINITE) {
			if (max == Constants.INT_POS_INFINITE)
				throw new ArithmeticException("Integer overflow in a sum of labeled values: " + formatLazy(a) + " + " + formatLazy(b));
			return Constants.INT_NEG_INFINITE;
		}
		if (max == Constants.INT_POS_INFINITE) {
			if (min == Constants.INT_NEG_INFINITE)
				throw new ArithmeticException("Integer overflow in a sum of labeled values: " + formatLazy(a) + " + " + formatLazy(b));
			return Constants.INT_POS_INFINITE;
		}

		final long sum = (long) a + (long) b;
		if ((sum >= Constants.INT_POS_INFINITE) || (sum <= Constants.INT_NEG_INFINITE))
			throw new ArithmeticException("Integer overflow in a sum of labeled values: " + formatLazy(a) + " + " + formatLazy(b));
		return (int) sum;
	}

	/**
	 * Type of subclass
	 */
	private SubType type;

	/**
	 * Resulting weight for a specific value of delta (that it can be represented in subclasses).
	 * private double value;
	 */

	/**
	 * @param t1  none
	 */
	LazyWeight(SubType t1) {
		this.type = t1;
	}

	@SuppressWarnings("unused")
	private LazyWeight() {

	}

	/** {@inheritDoc} */
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof LazyWeight lw))
			return false;
		return this.getType() == lw.getType() && lw.getValue() == this.getValue();
	}

	/**
	 * <p>
	 * Getter for the field <code>type</code>.
	 * </p>
	 *
	 * @return a {@link it.univr.di.labeledvalue.lazy.LazyWeight.SubType} object.
	 */
	public SubType getType() {
		return this.type;
	}

	/**
	 * <p>
	 * getValue.
	 * </p>
	 *
	 * @return a double.
	 */
	public abstract double getValue();

	/**
	 * <p>
	 * getX.
	 * </p>
	 *
	 * @return a {@link org.apache.commons.math3.fraction.Fraction} object.
	 */
	public abstract Fraction getX();

	/** {@inheritDoc} */
	@Override
	public int hashCode() {
		return Double.hashCode(this.getValue()) + this.getType().hashCode();
	}

	/**
	 * <p>
	 * setX.
	 * </p>
	 *
	 * @param newX a {@link org.apache.commons.math3.fraction.Fraction} object.
	 */
	public abstract void setX(Fraction newX);

	/** {@inheritDoc} */
	@Override
	public String toString() {
		/**
		 * It must be overrode.
		 * Be aware that parse is based on the format of this method. see entryAsString in #AbstractLabeledLazyMap
		 */
		return formatLazy(this.getValue());
	}
}
