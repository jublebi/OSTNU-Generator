// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

package it.univr.di.labeledvalue;

import javax.annotation.Nullable;

/**
 * An immutable literal.
 * <p>
 * A literal is a char that <b>can</b> be preceded by the symbol {@value it.univr.di.labeledvalue.Constants#NOT},
 * negated literal, or by the symbol {@value it.univr.di.labeledvalue.Constants#UNKNOWN}, 'unknown' literal.
 * <p>
 * While the semantics of a literal and its negation is the standard one, the semantics for unknown literal is
 * particular of the CSTN/CSTNU application.<br> An unknown literal, as '¿p' for example, is true if the value of
 * proposition letter 'p' is not assigned yet. False otherwise.
 * <p>
 * Therefore, if a state is characterized by the proposition '¿p', it means that the state is valid till the value of
 * proposition letter 'p' is unknown. In the instant the value of 'p' is set, '¿p' became false and the associated state
 * is not more valid.
 * <p>
 * A literal object is immutable and must have a propositional letter.
 * <p>
 * Lastly, for efficiency reasons, this class allows to represent literal using at most
 * {@link it.univr.di.labeledvalue.Label#NUMBER_OF_POSSIBLE_PROPOSITIONS} propositions in the range PROPOSITION_ARRAY.
 * {@link it.univr.di.labeledvalue.Label#NUMBER_OF_POSSIBLE_PROPOSITIONS} is given by the fact that
 * {@link it.univr.di.labeledvalue.Label} represents propositional labels using integer (32 bits), so labels with at
 * most 32 different propositions.
 *
 * @author Roberto Posenato
 * @version $Rev: 840 $
 */
@SuppressWarnings("NonFinalFieldReferencedInHashCode")
public final class Literal implements Comparable<Literal> {

	/**
	 * On 20171027 using VisualVM it has been shown that representing the state values of a literal using an enum
	 * consumes a lot of memory in this kind of application.<br> Therefore, I decided to simplify the representation
	 * using 4 constants value: ABSENT, {@link #STRAIGHT}, {@link #NEGATED}, and {@link #UNKNOWN}. The char
	 * corresponding to each such constant is exploit in the class (to make them more efficient). So, don't change them
	 * without revising all the class.<br> ABSENT is useful only for internal methods. It is not admitted for defining a
	 * literal.
	 */
	public static final char ABSENT = '\u0000';
	/**
	 * Constant {@code STRAIGHT='\u0001'}
	 */
	public static final char STRAIGHT = '\u0001';
	/**
	 * Constant {@code NEGATED=Constants.NOT}
	 */
	public static final char NEGATED = Constants.NOT;
	/**
	 * Constant {@code UNKNOWN=Constants.UNKNOWN}
	 */
	public static final char UNKNOWN = Constants.UNKNOWN;
	/**
	 * R.E. representation of allowed propositions.
	 */
	public static final String PROPOSITIONS = "a-zA-F";// "A-Za-z0-9α-μ";
	/**
	 * R.E. representation of PROPOSITION_ARRAY
	 */
	public static final String PROPOSITION_RANGE = "[" + PROPOSITIONS + "]";
	/**
	 * List of possible proposition managed by this class.<br> Such list is made concatenating 2 blocks: a-z, and A-F.
	 * If such blocks are changed, please revise {@link #check(char)} and {@link #index(char)} methods because it
	 * exploits the bounds of such blocks. The length of this array cannot be modified without revising all this class
	 * code and {@link Label} class.
	 *
	 * @see #PROPOSITIONS
	 */
	// 3 blocks: a-z, A-Z, α-μ.<br>
	static final char[] PROPOSITION_ARRAY = {
		// 0 1 2 3 4 5 6 7 8 9
		'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
		'w', 'x', 'y', 'z', 'A', 'B', 'C', 'D', 'E', 'F'
		// 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
		// 'α', 'β', 'γ', 'δ', 'ε', 'ζ', 'η', 'θ', 'ι', 'κ', 'λ', 'μ'
	};
	/**
	 * Literal object cache
	 */
	@SuppressWarnings("CheckForOutOfMemoryOnLargeArrayAllocation")
	private static final Literal[] CREATED_LITERAL = new Literal[Label.NUMBER_OF_POSSIBLE_PROPOSITIONS * 3];
	/**
	 * Immutable propositional letter.
	 */
	private final char name;
	/**
	 * Immutable state.
	 */
	private final char state;
	/**
	 * Hash code cache.
	 */
	private int hashCodeCached;

	/**
	 * Makes a literal using {@code v} and {@code state}. This class is immutable, use {@link #valueOf(char, char)}
	 *
	 * @param v      the proposition letter
	 * @param state1 one of possible state of a literal {@link #NEGATED} or {@link #STRAIGHT} o {@link #UNKNOWN}
	 */
	private Literal(final char v, final char state1) {
		if (!Literal.check(v)) {
			throw new IllegalArgumentException("The char is not an admissible proposition!");
		}
		if (getStateOrdinal(state1) < 0) {
			throw new IllegalArgumentException("The state is not an admissible one!");
		}
		name = v;
		state = state1;
	}

	/**
	 * @param state1 a possible state of a literal. No integrity check is done
	 * @param state2 a possible state of a literal. No integrity check is done
	 *
	 * @return true if state1 and state2 are complement. False otherwise
	 *
	 * @see #STRAIGHT
	 * @see #NEGATED
	 */
	static boolean areComplement(char state1, char state2) {
		return (state1 == STRAIGHT && state2 == NEGATED) || (state1 == NEGATED && state2 == STRAIGHT);
	}

	/**
	 * @param i a positive value smaller than {@value Label#NUMBER_OF_POSSIBLE_PROPOSITIONS}.
	 *
	 * @return char at position i in PROPOSITION_ARRAY.
	 */
	public static char charValue(final int i) {
		return PROPOSITION_ARRAY[i];
	}

	/**
	 * Returns the positive literal of {@code v}.
	 *
	 * @param v a char in the range {@link #PROPOSITION_RANGE}
	 *
	 * @return the straight literal of proposition v
	 */
	public static Literal valueOf(final char v) {
		return valueOf(v, STRAIGHT);
	}

	/**
	 * Return the literal having the given {@code state} of {@code v}.
	 *
	 * @param v     the proposition letter
	 * @param state one of possible state of a literal {@link #NEGATED} or {@link #STRAIGHT} o {@link #UNKNOWN}
	 *
	 * @return a literal with name {@code v} and state {@code state}, null if the char is not valid or state if
	 *    {@link #ABSENT}.
	 */
	@Nullable
	public static Literal valueOf(final char v, char state) {
		if (!check(v) || state == ABSENT) {
			return null;
		}
		final int hc = hashCode(v, state);
		Literal l = CREATED_LITERAL[hc];
		if (l == null) {
			l = new Literal(v, state);
			CREATED_LITERAL[hc] = l;
		}
		return l;
	}

	/**
	 * @param c the char to check
	 *
	 * @return true if the char represents a valid literal identifier
	 */
	public static boolean check(final char c) {
		return ('a' <= c && c <= 'z') || ('A' <= c && c <= 'F');// 'Z') || ('α' <= c && c <= 'μ');
	}

	/**
	 * Returns the ordinal associate to {@code state}.
	 *
	 * @param state One of the following value: {@value #NEGATED}, {@value #STRAIGHT}, {@value #UNKNOWN}
	 *
	 * @return the ordinal associated to a proper state, a negative integer if the state is not recognized
	 */
	static byte getStateOrdinal(char state) {
		return switch (state) {
			case STRAIGHT -> 1;
			case NEGATED -> 2;
			case UNKNOWN -> 3;
			case ABSENT -> 0;
			default -> -1;
		};
	}

	/**
	 * Hash code for a literal given as char {@code c} and state {@code state}.
	 *
	 * @param c     char for proposition
	 * @param state one of possible state of a literal {@link #NEGATED} or {@link #STRAIGHT} o {@link #UNKNOWN}. No
	 *              integrity check is done.
	 *
	 * @return an integer that is surely unique when 'a' &le; c &le; 'z'.
	 */
	static int hashCode(char c, char state) {
		return index(c) * 3 + getStateOrdinal(state) - 1;// -1 because ABSENT is not admissible.
	}

	/**
	 * @param c char for proposition
	 *
	 * @return the index of the given proposition {@code c} in {@link #PROPOSITION_ARRAY} if it is a proposition, a
	 * 	negative integer otherwise.
	 */
	static byte index(final char c) {
		if ('a' <= c && c <= 'z') {
			return (byte) (c - 'a');
		}
		if ('A' <= c && c <= 'F') // if ('A' <= c && c <= 'Z')
		{
			return (byte) ((c - 'A') + 26);// 26 is 'A' position in PROPOSITION_ARRAY
		}
		// if ('α' <= c && c <= 'μ')
		// return (byte) ((c - 'α') + 52);// 26 is 'α' position in PROPOSITION_ARRAY
		return -1;
	}

	/**
	 * Parses the string {@code s} returning the literal represented.
	 *
	 * @param s It can be a single char (PROPOSITION_ARRAY) or one of characters [{@value Constants#NOT}
	 *          {@value Constants#UNKNOWN}] followed by a char of PROPOSITION_ARRAY. No spaces are allowed
	 *
	 * @return the literal represented by {@code s} if {@code s} is a valid representation of a literal, null otherwise
	 */
	@Nullable
	public static Literal parse(final String s) {
		final int len;
		final char p;
		final char state;
		if (s == null || (len = s.length()) > 2) {
			return null;
		}
		if (len == 1) {
			p = s.charAt(0);
			if (!check(p)) {
				return null;
			}
			return valueOf(p, STRAIGHT);
		}
		state = s.charAt(0);
		p = s.charAt(1);
		if (!check(p)) {
			return null;
		}
		if (state == Constants.NOT) {
			return valueOf(p, NEGATED);
		}
		if (state == Constants.UNKNOWN) {
			return valueOf(p, UNKNOWN);
		}

		return null;
	}

	@Override
	public int compareTo(final Literal o) {
		if (name < o.name) {
			return -1;
		} else if (name > o.name) {
			return 1;
		}
		// Since compareTo has to be consistent with equals, when the two names are equal,
		// it returns a different value than 0 if the two literals have different state.
		return getStateOrdinal(state) - getStateOrdinal(o.state);
	}

	@Override
	public boolean equals(final Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof final Literal l)) {
			return false;
		}
		return name == l.name && state == l.state;
	}

	/**
	 * Returns a new literal having same proposition of {@code v} but with state given by {@code state}.
	 *
	 * @param v     a non null literal v
	 * @param state one of possible state of a literal: {@link #NEGATED} or {@link #STRAIGHT} o {@link #UNKNOWN}
	 *
	 * @return a new literal having same proposition of {@code v} but with state given by {@code state}.
	 */
	public static Literal valueOf(final Literal v, char state) {
		if (state == ABSENT || v == null) {
			throw new IllegalArgumentException("The state or the input literal is not valid!");
		}
		final int hc;
		Literal l = CREATED_LITERAL[hc = hashCode(v.name, state)];
		if (l == null) {
			l = new Literal(v.name, state);
			CREATED_LITERAL[hc] = l;
		}
		return l;
	}

	/**
	 * Returns the complement of this.
	 * <p>
	 * The complement of a straight literal is the negated one.<br> The complement of a negated literal is the straight
	 * one.<br> The complement of an unknown literal is null (an empty literal is not possible).<br>
	 *
	 * @return a new literal that it is the negated of this. null if it is request the complement of unknown literal
	 */
	@Nullable
	public Literal getComplement() {
		if (state == UNKNOWN || state == ABSENT) {
			return null;
		}
		return valueOf(this, (isNegated()) ? STRAIGHT : NEGATED);
	}

	/**
	 * @return true if it is a negated literal
	 */
	public boolean isNegated() {
		return state == NEGATED;
	}

	/**
	 * Returns a literal that is the negated of this.
	 *
	 * @return a new literal with the same name and state negated
	 */
	public Literal getNegated() {
		return valueOf(this, NEGATED);
	}

	/**
	 * @return the propositional letter associated to this
	 */
	public char getName() {
		return name;
	}

	/**
	 * Returns a literal that is the straight literal of this.
	 *
	 * @return a new literal with the same name and state straight
	 */
	public Literal getStraight() {
		return valueOf(this, STRAIGHT);
	}

	/**
	 * Returns a literal that is the unknown literal of this.
	 *
	 * @return a new literal with the same name and state unknown
	 */
	public Literal getUnknown() {
		return valueOf(this, UNKNOWN);
	}

	/**
	 * @return the state
	 */
	public char getState() {
		return state;
	}

	@Override
	public int hashCode() {
		if (hashCodeCached == 0) {
			hashCodeCached = Literal.hashCode(name, state);
		}
		return hashCodeCached;
	}

	/**
	 * @param l a  object.
	 *
	 * @return true if it is a complement literal of the given one
	 */
	public boolean isComplement(Literal l) {
		if (l == null) {
			return false;
		}
		return areComplement(state, l.state);
	}

	/**
	 * @param state state of the literal
	 *
	 * @return the string representation of {@code state}
	 */
	static String stateAsString(char state) {
		if (state <= STRAIGHT) {
			return "";
		}
		return String.valueOf(state);
	}

	/**
	 * @return true if it is a straight literal
	 */
	public boolean isStraight() {
		return state == STRAIGHT;
	}

	/**
	 * @param propositionIndex index of proposition
	 * @param state            one of possible state of a literal {@link #NEGATED} or {@link #STRAIGHT} o
	 *                         {@link #UNKNOWN}
	 *
	 * @return the char-array representation of a literal identified by its index and state parameter. If state is not
	 * 	correct, an empty array is returned
	 */
	@SuppressWarnings("StaticMethodOnlyUsedInOneClass")
	static char[] toChars(int propositionIndex, char state) {
		// Exploits the state fixed order.
		if (state > STRAIGHT) {
			return new char[]{state, charValue(propositionIndex)};
		}
		if (state == STRAIGHT) {
			return new char[]{charValue(propositionIndex)};
		}
		return new char[0];
	}

	/**
	 * @return true if it is a literal in the unknown state
	 */
	public boolean isUnknown() {
		return state == UNKNOWN;
	}

	@Override
	public String toString() {
		return stateAsString(state) + name;
	}

}
