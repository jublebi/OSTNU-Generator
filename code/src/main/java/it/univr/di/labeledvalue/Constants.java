// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

package it.univr.di.labeledvalue;

import it.univr.di.Debug;

import java.io.Serial;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Some useful constants for the package.
 *
 * @author Roberto Posenato
 * @version $Rev: 889 $
 */
@SuppressWarnings({"UnnecessaryUnicodeEscape", "StaticMethodOnlyUsedInOneClass"})
public final class Constants implements Serializable {

	/**
	 * Char representing labeled-value closing ")".
	 */
	public static final String CLOSE_PAIR = ")";// '⟩';
	/**
	 * A tuple closing char
	 */
	public static final String CLOSE_TUPLE = "❯";
	/**
	 * A tuple opening char
	 */
	public static final String OPEN_TUPLE = "❮";
	/**
	 * Char representing empty label: ⊡.
	 */
	public static final char EMPTY_LABEL = '\u22A1';
	/**
	 * @see #EMPTY_LABEL
	 */
	public static final String EMPTY_LABELstring = String.valueOf(EMPTY_LABEL);
	/**
	 * Char representing empty upper case label: ◇.
	 */
	public static final char EMPTY_UPPER_CASE_LABEL = '\u25C7';
	/**
	 * @see Constants#EMPTY_UPPER_CASE_LABEL
	 */
	public static final String EMPTY_UPPER_CASE_LABELstring = String.valueOf(EMPTY_UPPER_CASE_LABEL);
	/**
	 * Char representing infinity symbol: ∞.
	 */
	public static final char INFINITY_SYMBOL = '\u221E';
	/**
	 * @see #INFINITY_SYMBOL
	 */
	public static final String INFINITY_SYMBOLstring = String.valueOf(INFINITY_SYMBOL);
	/**
	 * Negative infinitive.
	 *
	 * @see #INFINITY_SYMBOL
	 */
	public static final String NEGATIVE_INFINITY_SYMBOLstring = "-" + INFINITY_SYMBOLstring;
	/**
	 * Char representing the contingency symbol (0x2B0D): ↕︎
	 */
	public static final String CONTINGENCY_SYMBOL = String.valueOf('\u2B0D');
	/**
	 * Default value to represent a no valid integer value. It is necessary in the type oriented implementation of
	 * {@code Map(Label,int)}. It has to be different to the value {@link Constants#INT_POS_INFINITE}, used to represent
	 * an edge with a no bound labeled value.
	 */
	static public final int INT_NULL = Integer.MIN_VALUE;
	/**
	 * THe integer value representing the -∞.
	 */
	public static final int INT_NEG_INFINITE = INT_NULL + 1;
	/**
	 * THe integer value representing the +∞.
	 */
	public static final int INT_POS_INFINITE = Integer.MAX_VALUE;
	/**
	 * Regular expression for an acceptable value in a LabeledValue.
	 */
	public static final String LabeledValueRE = "[-[0-9]|[0-9]]*";
	/**
	 * Regular expression for an acceptable positive integer.
	 */
	public static final String NonNegIntValueRE = "[0-9]+";
	/**
	 * Char representing logic not symbol: ¬.
	 */
	public static final char NOT = '\u00AC';
	/**
	 * @see #NOT
	 */
	public static final String NOTstring = String.valueOf(NOT);
	/**
	 * String representing labeled-value opening.
	 */
	public static final String OPEN_PAIR = "(";
// '⟨';//It is not possible to use this angular parenthesis, too much saved files with (

	/**
	 * Regular expression for positive and not 0 integer.
	 */
	@SuppressWarnings("unused")
	public static final String StrictlyPositiveIntValueRE = "[1-9]+";
	/**
	 * Char representing logic not know symbol: ¿.
	 */
	public static final char UNKNOWN = '\u00BF';
	/**
	 * @see #UNKNOWN
	 */
	public static final String UNKNOWNstring = String.valueOf(UNKNOWN);
	/**
	 * logger
	 */
	private static final Logger LOG = Logger.getLogger("it.univr.di.cstnu.labeledvalue.Constants");
	/**
	 *
	 */
	@Serial
	private static final long serialVersionUID = 2L;

	/**
	 * Prevents instantiation
	 */
	private Constants() {
	}

	/**
	 * Determines the sum of {@code a} and {@code b}. If any of them is already INFINITY, returns INFINITY.
	 *
	 * @param a the first addendum
	 * @param b the second addendum
	 *
	 * @return the controlled sum
	 *
	 * @throws java.lang.ArithmeticException if any.
	 * @throws java.lang.ArithmeticException if any.
	 * @throws java.lang.ArithmeticException if any.
	 */
	@SuppressWarnings("ThrowsRuntimeException")
	static public int sumWithOverflowCheck(final int a, final int b) throws ArithmeticException {
		final int max;
		final int min;
		if (a >= b) {
			max = a;
			min = b;
		} else {
			min = a;
			max = b;
		}
		if (a == INT_NULL || b == INT_NULL) {
			throw new ArithmeticException("Integer sum with a null value: " + formatInt(a) + " + " + formatInt(b));
		}
		if (min == INT_NEG_INFINITE) {
			if (max == INT_POS_INFINITE) {
				throw new ArithmeticException(
					"Integer overflow in a sum of labeled values: " + formatInt(a) + " + " + formatInt(b));
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("Sum of " + formatInt(a) + " and " + formatInt(b) + " = " + formatInt(INT_NEG_INFINITE));
				}
			}
			return INT_NEG_INFINITE;
		}
		if (max == INT_POS_INFINITE) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("Sum of " + formatInt(a) + " and " + formatInt(b) + " = " + formatInt(INT_POS_INFINITE));
				}
			}
			return INT_POS_INFINITE;
		}

		final long sum = (long) a + b;
		if ((sum >= INT_POS_INFINITE) || (sum <= INT_NEG_INFINITE)) {
			throw new ArithmeticException(
				"Integer overflow in a sum of labeled values: " + formatInt(a) + " + " + formatInt(b));
		}
		return (int) sum;
	}

	/**
	 * @param n the integer to format
	 *
	 * @return the value of {@code n} as a String using {@link it.univr.di.labeledvalue.Constants#INFINITY_SYMBOL} for
	 * 	the infinitive and {@code NaN} for a not assigned value (similar to null for the object).
	 */
	static public String formatInt(int n) {
		return switch (n) {
			case INT_NEG_INFINITE -> NEGATIVE_INFINITY_SYMBOLstring;
			case INT_POS_INFINITE -> INFINITY_SYMBOLstring;
			case INT_NULL -> "NaN";
			default -> Integer.toString(n);
		};
	}
}
