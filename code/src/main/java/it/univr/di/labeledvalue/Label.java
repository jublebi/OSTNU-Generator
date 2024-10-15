// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.labeledvalue;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.netbeans.validation.api.Problems;
import org.netbeans.validation.api.Validator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Represents an immutable propositional <em>label</em> in the CSTN/CSTNU framework.
 * <br>
 * A label is a (logic) conjunction of zero or more <em>literals</em> ({@link it.univr.di.labeledvalue.Literal}).
 * <br>
 * A label without literals is called <em>empty label</em> and it is represented graphically as
 * {@link it.univr.di.labeledvalue.Constants#EMPTY_LABEL}.
 * <br>
 * A label is <em>consistent</em> when it does not contain opposite literals. A label {@code L'} subsumes a label
 * {@code L} iff {@code L'} implies {@code L} ({@code ⊨ L' ⇒ L}).
 * <br>
 * In other words, if {@code L} is a sub-label of {@code L'}.
 * <p>
 * Design assumptions Since in CSTN(U) project the memory footprint of a label is an important aspect, after some
 * experiments, I have found that the best way to represent a label is to limit the possible propositions to the range
 * [a-z,A-F] and to use two {@code int} for representing the state of literals composing a label: the two {@code int}
 * are used in pair; each position of them is associated to a possible literal (position 0 to 'a',...,position 32 to
 * 'F'); given a position, the two corresponding bits in the two long can represent all possible four states
 * ({@link it.univr.di.labeledvalue.Literal#ABSENT}, {@link it.univr.di.labeledvalue.Literal#STRAIGHT},
 * {@link it.univr.di.labeledvalue.Literal#NEGATED}, {@link it.univr.di.labeledvalue.Literal#UNKNOWN}) of the literal
 * associated to the position.
 * <br>
 * Using only 32 possible propositions, it is possible to cache the two {@code int} of a label as a long and, therefore,
 * to cache the label for reusing it.
 * </p>
 * <p>
 * The following table represent execution times of some Label operations determined using different implementation of
 * this class.
 * </p>
 * <table border="1">
 * <caption>Execution time for some operations w.r.t the core data structure of the class.</caption>
 * <tr>
 * <th>Method</th>
 * <th>TreeSet</th>
 * <th>ObjectAVLTreeSet</th>
 * <th>ObjectRBTreeSet</th>
 * <th>byte array</th>
 * <th>two int</th>
 * <th>two long</th>
 * <th>two int Immutable</th>
 * </tr>
 * <tr>
 * <td>Simple conjunction of '¬abcd' with '¬adefjs'='¬abcdefjs' (ms)</td>
 * <td>0.076961</td>
 * <td>0.066116</td>
 * <td>0.068798</td>
 * <td>0.001309</td>
 * <td>0.000299</td>
 * <td>0.000317</td>
 * <td>0.000529</td>
 * </tr>
 * <tr>
 * <td>Execution time for an extended conjunction of '¬abcd' with
 * 'a¬c¬defjs'='¿ab¿c¿defjs' (ms)</td>
 * <td>0.07583</td>
 * <td>0.024099</td>
 * <td>0.014627</td>
 * <td>0.000843</td>
 * <td>0.000203</td>
 * <td>0.000235</td>
 * <td>0.000229</td>
 * </tr>
 * <tr>
 * <td>Execution time for checking if two (inconsistent) labels are consistent.
 * Details '¬abcd' with 'a¬c¬defjs' (ms)</td>
 * <td>0.004016</td>
 * <td>0.001666</td>
 * <td>0.00166</td>
 * <td>0.00121</td>
 * <td>0.00075</td>
 * <td>0.00040</td>
 * <td>0.000122</td>
 * </tr>
 * <tr>
 * <td>Execution time for checking if two (consistent) labels are consistent.
 * Details '¬abcd' with '¬abcd' (ms)</td>
 * <td>0.01946</td>
 * <td>0.004457</td>
 * <td>0.004099</td>
 * <td>0.000392</td>
 * <td>0.000558</td>
 * <td>0.000225</td>
 * <td>0.000089</td>
 * </tr>
 * <tr>
 * <td>Execution time for checking if a literal is present in a label (the
 * literal is the last inserted) (ms)</td>
 * <td>5.48E-4</td>
 * <td>4.96E-4</td>
 * <td>5.01E-4</td>
 * <td>2.69E-4</td>
 * <td>5.03E-4</td>
 * <td>7.47E-4</td>
 * <td>4.46E-4</td>
 * </tr>
 * <tr>
 * <td>Execution time for checking if a literal is present in a label (the
 * literal is not present) (ms)</td>
 * <td>6.48E-4</td>
 * <td>7.71E-4</td>
 * <td>5.96E-4</td>
 * <td>1.84E-4</td>
 * <td>3.07E-4</td>
 * <td>2.33E-4</td>
 * <td>2.14E-4</td>
 * </tr>
 * <tr>
 * <td>Execution time for get the literal in the label with the same proposition
 * letter (the literal is present) (ms)</td>
 * <td>0.003272</td>
 * <td>1.83E-4</td>
 * <td>1.09E-4</td>
 * <td>1.27E-4</td>
 * <td>1.60E-4</td>
 * <td>1.32E-4</td>
 * <td>7.43E-5</td>
 * </tr>
 * <tr>
 * <td>Execution time for get the literal in the label with the same proposition
 * letter (the literal is not present) (ms)</td>
 * <td>0.002569</td>
 * <td>1.68E-4</td>
 * <td>1.0E-4</td>
 * <td>1.04E-4</td>
 * <td>1.60E-4</td>
 * <td>1.30E-4</td>
 * <td>5.25E-5</td>
 * </tr>
 * </table>
 * <b>All code for performance tests is in LabelTest class .</b>
 *
 * @author Roberto Posenato
 * @version $Rev: 856 $
 */
@SuppressWarnings("SubtractionInCompareTo")
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "PZLA_PREFER_ZERO_LENGTH_ARRAYS",
	justification = "I prefer to return null for saying no answer is possible.")
public final class Label implements Comparable<Label>, Serializable {

	/**
	 * Regular expression representing a Label. The re checks only that label chars are allowed.
	 */
	public static final String LABEL_RE =
		"((" + "(" + Constants.NOTstring + "|" + Constants.UNKNOWN + "|)" + Literal.PROPOSITION_RANGE + ")+|"
		+ Constants.EMPTY_LABELstring + ")";
	/**
	 * Maximal number of possible proposition in a network.
	 * <br>
	 * This limit cannot be changed without revising all this class code.
	 */
	public static final int NUMBER_OF_POSSIBLE_PROPOSITIONS = 32;// 64;
	/**
	 *
	 */
	@Serial
	private static final long serialVersionUID = 1L;
	/**
	 * Label object cache This declaration must stay here, before any other!
	 */
	private static final Long2ObjectMap<Label> CREATED_LABEL = new Long2ObjectOpenHashMap<>();
	/**
	 * A constant empty label to represent an empty label that cannot be modified.
	 */
	public static final Label emptyLabel = valueOf(0L);
	/**
	 * <pre>
	 * Possible status of a literal
	 * 				bit1[i] bit0[i]
	 * not present 		0 		0
	 * straight 		0		1
	 * negated 			1		0
	 * unknown 			1		1
	 * </pre>
	 */
	private static final char[] LITERAL_STATE = {Literal.ABSENT, Literal.STRAIGHT, Literal.NEGATED, Literal.UNKNOWN};
	/**
	 * Validator for graphical interface
	 */
	@SuppressWarnings({"AnonymousInnerClass", "AnonymousInnerClassWithTooManyMethods",
	                   "OverlyComplexAnonymousInnerClass", "StaticMethodOnlyUsedInOneClass"})
	public static final Validator<String> labelValidator = new Validator<>() {
		@Override
		public Class<String> modelType() {
			return String.class;
		}

		@Override
		public void validate(final Problems problems, final String compName, final String model) {
			if ((model == null) || (model.isEmpty())) {
				return;
			}
			final Label l = Label.parse(model);

			if (l == null) {
				problems.append("Highlighted label is not well-formed.");
			}
		}
	};
	/**
	 * logger
	 */
	@SuppressWarnings("unused")
	private static final Logger LOG = Logger.getLogger("Label");

	/**
	 * A base is a set of same-length labels that are can be used to build any other greater-length label of the
	 * universe.
	 * <br>
	 * The label components of a base can be built from a set of literals making all possible same-length combinations
	 * of such literals and their negations.
	 *
	 * @param baseElements an array of propositions.
	 *
	 * @return return all the components of the base built using literals of baseElements. Null if baseElements is null
	 * 	or empty.
	 */
	@Nullable
	public static Label[] allComponentsOfBaseGenerator(final char[] baseElements) {
		if (baseElements.length == 0) {
			return null;
		}
		final int baseSize = baseElements.length;
		final int n = (int) Math.pow(2, baseSize);
		final Label[] components = new Label[n];
		for (int i = 0; i < n; i++) {
			components[i] = complementGenerator(baseElements, i);
		}
		return components;
	}

	/**
	 * Parse a string representing a label and return an equivalent Label object if no errors are found, null
	 * otherwise.
	 * <br>
	 * The regular expression syntax for a label is specified in {@link #LABEL_RE}.
	 *
	 * @param s a {@link java.lang.String} object.
	 *
	 * @return a Label object corresponding to the label string representation, null if the input string does not
	 * 	represent a label or is null.
	 */
	@Nullable
	public static Label parse(String s) {
		if (s == null) {
			return null;
		}

		// FIX for past label in mixed case
		// s = s.toLowerCase(); From version > 130 proposition can be also upper case and first eleven greek letters
		int n = s.length();
		if (n == 0) {
			return emptyLabel;
		}
		char c;

		// trim all internal spaces or other chars
		final StringBuilder sb = new StringBuilder(n);
		int i = 0;
		while (i < n) {
			c = s.charAt(i++);
			if (Literal.check(c) || (c == Constants.NOT) || (c == Constants.UNKNOWN) || (c == Constants.EMPTY_LABEL)) {
				sb.append(c);
			} else {
				if (!(c == ' ' || c == '\t' || c == '\n' || c == '\f' || c == '\r')) {
					return null;
				}
			}
		}
		s = sb.toString();
		n = s.length();
		// LOG.finest("String trimmed: " + s2);
		// if (!patterLabelRE.matcher(s2).matches()) // LOG.finest("Input '" +
		// s2 + "' does not satisfy Label format: " + Constants.labelRE);
		// return null;

		if ((n == 1) && (s.charAt(0) == Constants.EMPTY_LABEL)) {
			return emptyLabel; // Check only one time the special case made with the empty symbol.
		}

		Label label = emptyLabel;
		// byte literalIndex;
		char literalStatus;
		i = 0;
		while (i < n) {
			c = s.charAt(i);
			if (c == Constants.NOT || c == Constants.UNKNOWN) {
				final char sign = c;
				if (++i >= n) {
					return null;
				}
				c = s.charAt(i);
				if (!Literal.check(c)) {
					return null;
				}
				// l = Literal.create(c, (sign == Constants.NOT) ? Literal.NEGATED
				// : Literal.UNKNONW);
				// literalIndex = Literal.index(c);
				literalStatus = (sign == Constants.NOT) ? Literal.NEGATED : Literal.UNKNOWN;
			} else {
				if (!Literal.check(c)) {
					return null;
				}
				// literalIndex = Literal.index(c);
				literalStatus = Literal.STRAIGHT;
			}
			// if (label.label[literalIndex] != 0)
			if (label.contains(c)) {
				return null;
			}
			label = label.conjunctionExtended(c, literalStatus);
			i++;
		}
		return label;
	}

	/**
	 * @param proposition the input proposition as chat
	 * @param state       its state. Possible values: {@link Literal#ABSENT}, {@link Literal#NEGATED}, {@link Literal#STRAIGHT}, or {@link Literal#UNKNOWN}.
	 *
	 * @return the label initialized with literal represented by the proposition and its state. If proposition is a char
	 * 	not allowed, a IllegalArgumentException is raised.
	 */
	static public Label valueOf(final char proposition, final char state) {
		if (state == Literal.ABSENT) {
			return emptyLabel;
		}
		final byte literalIndex = Literal.index(proposition);
		if (literalIndex < 0) {
			throw new IllegalArgumentException("Proposition is not allowed!");
		}
		final long index = set(0, 0, literalIndex, state);
		return valueOf(index);
	}

	/**
	 * @param b1 a positive integer
	 * @param b0 a positive integer
	 *
	 * @return the index associated to the two index b0 and b1.
	 */
	private static long cacheIndex(int b1, int b0) {
		return (((long) b1) << 32) + b0;// << must be within ()
	}

	/**
	 * Returns a label containing the propositions specified by c. The i-th proposition of resulting label has negative
	 * state if i-th bit of mask is 1.
	 *
	 * @param proposition an array of propositions.
	 * @param mask        It has to be > 0 and <= 2^proposition.length. No range check is made!
	 *
	 * @return a label copy of label but with literals having indexes corresponding to bits 1 in the parameter 'index'
	 * 	set to negative state. If label is null or empty or contains UNKNOWN literals, returns null;
	 */
	@Nullable
	private static Label complementGenerator(final char[] proposition, final int mask) {
		final int n = proposition.length;
		if (n == 0) {
			return null;
		}
		int j = 1;
		Label newLabel = emptyLabel;
		for (int i = 0; i < n; i++, j <<= 1) {
			final char state = ((j & mask) != 0) ? Literal.NEGATED : Literal.STRAIGHT;
			newLabel = newLabel.conjunction(proposition[i], state);
			if (newLabel == null) {return null;}
		}
		return newLabel;
	}

	/**
	 * @param index the input index
	 *
	 * @return the lower part of the given index as unsigned int
	 */
	private static int getB0(long index) {
		return (int) (index & 0xFFFFFFFFL);
	}

	/**
	 * @param index the input index
	 *
	 * @return the upper part of the given index as unsigned int
	 */
	private static int getB1(long index) {
		return (int) (index >>> 32);
	}

	/**
	 * Each label is represented by two ints. In order to modify a label, it is possible to pass its two ints, the
	 * literal index to modify and the new state for such index. The method returns the index of the label representing
	 * the given parameters.
	 *
	 * @param b0            seed for the first state int.
	 * @param b1            seed for the second state int.
	 * @param literalIndex  the index of the literal to update.
	 * @param literalStatus the new state.
	 *
	 * @return the index of the label (composition of b1 and b0)
	 */
	@SuppressWarnings("lossy-conversions")
	private static long set(int b0, int b1, final byte literalIndex, final char literalStatus) {
		/*
		 * <pre>
		 * Status of i-th literal
		 *             bit1[i] bit0[i]
		 * not present       0 0
		 * straight          0 1
		 * negated           1 0
		 * unknown           1 1
		 * </pre>
		 */
		long mask = 1L << literalIndex;
		switch (literalStatus) {
			case Literal.STRAIGHT:
				b0 |= mask;
				mask = ~mask;
				b1 &= mask;
				break;
			case Literal.NEGATED:
				b1 |= mask;
				mask = ~mask;
				b0 &= mask;
				break;
			case Literal.UNKNOWN:
				b1 |= mask;
				b0 |= mask;
				break;
			case Literal.ABSENT:
			default:
				mask = ~mask;
				b1 &= mask;
				b0 &= mask;
		}
		return cacheIndex(b1, b0);
	}

	/**
	 * @param index the input index
	 *
	 * @return the label represented by the two state ints.
	 */
	static private Label valueOf(long index) {
		Label cached = CREATED_LABEL.get(index);
		if (cached == null) {
			cached = new Label(getB1(index), getB0(index));
			CREATED_LABEL.put(index, cached);
		}
		return cached;
	}

	/**
	 * Using two ints, it is possible to represent 4 states for each position.
	 * <br>
	 * Each position is associated to a proposition.
	 *
	 * <pre>
	 * Status of i-th literal
	 *              bit1[i] bit0[i]
	 * not present          0   0
	 * straight             0   1
	 * negated              1   0
	 * unknown              1   1
	 * </pre>
	 */
	private final int bit1, bit0;
	/**
	 * Index of the highest-order ("leftmost") literal of label w.r.t. lexicographical order. On 2016-03-30 I showed by
	 * SizeofUtilTest.java that using byte it is possible to define also 'size' field without incrementing the memory
	 * footprint of the object.
	 */
	private final byte maxIndex;
	/**
	 * Number of literals in the label Value -1 means that the size has to be calculated!
	 */
	private final byte count;

	/**
	 * Create a label from state integers b1 and b0.
	 *
	 * @param b1 one input index
	 * @param b0 the other input index
	 */
	private Label(final int b1, final int b0) {
		bit0 = b0;
		bit1 = b1;
		count = (byte) Long.bitCount(b0 | b1);
		int mask = 1 << 31;
		byte mi = -1;
		for (byte i = 32; ((--i) >= 0); ) {
			if (((b1 & mask) != 0) || ((b0 & mask) != 0)) {
				mi = i;
				break;
			}
			mask = mask >>> 1;
		}
		maxIndex = mi;
	}

	/**
	 * Determines an order based on length of label. Same length labels are in lexicographical order based on the
	 * natural order of type {@link Literal}.
	 */
	@Override
	public int compareTo(@Nonnull final Label label) {
		if (equals(label)) {
			return 0;// fast comparison!
		}
		final int sizeC = Integer.compare(size(), label.size());
		if (sizeC != 0) {
			return sizeC;
		}

		// they have same length and they are different
		int i = 0, j = 0, cmp;
		int thisState, labelState;
		long maskI = 1L, maskJ = 1L;
		while (i <= maxIndex && j <= label.maxIndex) {
			while ((thisState = ((((bit1 & maskI) != 0) ? 2 : 0) + (((bit0 & maskI) != 0) ? 1 : 0))) == 0
			       && i <= maxIndex) {
				i++;
				maskI <<= 1;
			}
			while ((labelState = ((((label.bit1 & maskJ) != 0) ? 2 : 0) + (((label.bit0 & maskJ) != 0) ? 1 : 0))) == 0
			       && j <= label.maxIndex) {
				j++;
				maskJ <<= 1;
			}
			if (i != j) {
				return i - j;
			}
			cmp = thisState - labelState;
			if (cmp != 0) {
				return cmp;
			}
			i++;
			maskI <<= 1;
			j++;
			maskJ <<= 1;
		}
		return 0;// impossible but necessary for avoiding the warning!
	}

	/**
	 * Conjuncts {@code label} to {@code this} if {@code this} is consistent with {@code label} and returns the result
	 * without modifying {@code this}.
	 *
	 * @param label the label to conjoin
	 *
	 * @return a new label with the conjunction of {@code this} and {@code label} if they are consistent, null
	 * 	otherwise.
	 * 	<br>
	 * 	null also if {@code this} or {@code label} contains unknown literals.
	 */
	@Nullable
	public Label conjunction(final Label label) {
		if (label == null) {
			return null;
		}
		final int unionB0 = bit0 | label.bit0;
		final int unionB1 = bit1 | label.bit1;
		if ((unionB0 & unionB1) != 0) {
			// there is at least one unknown or a pair of opposite literals
			return null;
		}
		return valueOf(cacheIndex(unionB1, unionB0));
	}

	/**
	 * Helper method for {@link it.univr.di.labeledvalue.Label#conjunction(char, char)}
	 *
	 * @param literal a literal
	 *
	 * @return the new Label if literal has been added, null otherwise.
	 */
	@Nullable
	public Label conjunction(final Literal literal) {
		if (literal == null) {
			return null;
		}
		return conjunction(literal.getName(), literal.getState());
	}

	/**
	 * It returns a new label conjunction of {@code proposition} and {@code this} if {@code this} is consistent with
	 * {@code proposition} and its {@code propositionState}. If propositionState is
	 * {@link it.univr.di.labeledvalue.Literal#ABSENT}, the effect is reset the {@code proposition} in the new label.
	 *
	 * @param proposition      the proposition to conjoin.
	 * @param propositionState a possible state of the proposition: {@link it.univr.di.labeledvalue.Literal#STRAIGHT},
	 *                         {@link it.univr.di.labeledvalue.Literal#NEGATED} or
	 *                         {@link it.univr.di.labeledvalue.Literal#ABSENT}.
	 *
	 * @return the new label if proposition can be conjuncted, null otherwise.
	 */
	@Nullable
	public Label conjunction(final char proposition, final char propositionState) {
		if (propositionState == Literal.UNKNOWN) {
			return null;
		}
		final byte propIndex = Literal.index(proposition);
		final char st = get(propIndex);
		if (st == propositionState) {
			return this;
		}
		if (Literal.areComplement(st, propositionState)) {
			return null;
		}
		final long index = Label.set(bit0, bit1, propIndex, propositionState);
		return Label.valueOf(index);
	}

	/**
	 * Create a new label that represents the conjunction of {@code this} and {@code label} using also
	 * {@link it.univr.di.labeledvalue.Literal#UNKNOWN} literals. A {@link it.univr.di.labeledvalue.Literal#UNKNOWN}
	 * literal represent the fact that in the two input labels a proposition letter is present as straight state in one
	 * label and in negated state in the other.
	 * <br>
	 * For a detail about the conjunction of unknown literals, see {@link #conjunctionExtended(char, char)}.
	 *
	 * @param label the input label.
	 *
	 * @return a new label with the conjunction of {@code this} and {@code label}.
	 * 	<br>
	 *    {@code this} is not altered by this method.
	 */
	public @Nonnull Label conjunctionExtended(final @Nonnull Label label) {
		final int unionB0 = bit0 | label.bit0;
		final int unionB1 = bit1 | label.bit1;
		return valueOf(cacheIndex(unionB1, unionB0));
	}

	/**
	 * Helper method {@link #conjunctionExtended(char, char)}
	 *
	 * @param literal a literal
	 *
	 * @return Label where literal has been added.
	 */
	@Nullable
	public Label conjunctionExtended(final Literal literal) {
		if (literal == null) {
			return null;
		}
		return conjunctionExtended(literal.getName(), literal.getState());
	}

	/**
	 * It returns the conjunction of {@code proposition} to {@code this}. If {@code proposition} state
	 * {@code literalState} is opposite to the corresponding literal in {@code this}, the opposite literal in
	 * {@code this} is substituted with {@code proposition} but with unknown state. If {@code proposition} has unknown
	 * state, it is added to {@code this} as unknown. If propositionState is
	 * {@link it.univr.di.labeledvalue.Literal#ABSENT}, the effect is reset the proposition in the label.
	 *
	 * @param proposition      the literal to conjoin.
	 * @param propositionState a possible state of the proposition: {@link it.univr.di.labeledvalue.Literal#STRAIGHT},
	 *                         {@link it.univr.di.labeledvalue.Literal#NEGATED},
	 *                         {@link it.univr.di.labeledvalue.Literal#UNKNOWN} or
	 *                         {@link it.univr.di.labeledvalue.Literal#ABSENT}.
	 *
	 * @return this label.
	 */
	public Label conjunctionExtended(final char proposition, char propositionState) {
		final byte propIndex = Literal.index(proposition);
		final char st = get(propIndex);
		if (Literal.areComplement(st, propositionState)) {
			propositionState = Literal.UNKNOWN;
		}
		final long index = Label.set(bit0, bit1, propIndex, propositionState);
		return Label.valueOf(index);
	}

	/**
	 * @param proposition the proposition to check.
	 *
	 * @return true if this contains proposition in any state: straight, negated or unknown.
	 */
	public boolean contains(final char proposition) {
		return get(Literal.index(proposition)) != Literal.ABSENT;
	}

	/**
	 * @param l the input literal
	 *
	 * @return true if the literal {@code l} is present into the label.
	 */
	public boolean contains(final Literal l) {
		if (l == null) {
			return false;
		}
		return l.getState() == get(Literal.index(l.getName()));
	}

	/**
	 * @return true if the label contains one unknown literal at least.
	 */
	public boolean containsUnknown() {
		// optimized version!
		return (bit0 & bit1) != 0;
	}

	@Override
	public boolean equals(final Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof Label l)) {
			return false;
		}
		return bit1 == l.bit1 && bit0 == l.bit0;
	}

	/**
	 * @return The array of propositions (char) that have unknown status in this label.
	 */
	public char[] getAllUnknown() {
		if (maxIndex <= 0) {
			return new char[0];
		}
		final char[] indexes = new char[size()];
		int j = 0;
		for (byte i = (byte) (maxIndex + 1); (--i) >= 0; ) {
			if (get(i) == Literal.UNKNOWN) {
				indexes[j++] = Literal.charValue(i);
			}
		}
		return Arrays.copyOf(indexes, j);
	}

	/**
	 * @return An array containing a copy of literals in this label. The array may be empty.
	 */
	public Literal[] getLiterals() {
		final Literal[] indexes = new Literal[size()];
		int j = 0;
		char state;
		for (byte i = (byte) (maxIndex + 1); (--i) >= 0; ) {
			if ((state = get(i)) != Literal.ABSENT) {
				indexes[j++] = Literal.valueOf(Literal.charValue(i), state);
			}
		}
		return indexes;
	}

	/**
	 * @return The array of proposition of present literals in this label in alphabetic order.
	 */
	public char[] getPropositions() {
		final char[] indexes = new char[size()];
		int j = 0;
		for (byte i = 0; i <= maxIndex; i++) {
			if (get(i) != Literal.ABSENT) {
				indexes[j++] = Literal.charValue(i);
			}
		}
		return indexes;
	}

	/**
	 * @param proposition the proposition to check.
	 *
	 * @return the state of the proposition in this label: straight, negated, unknown or absent.
	 */
	public char getState(final char proposition) {
		return get(Literal.index(proposition));
	}

	/**
	 * @param c the name of literal
	 *
	 * @return the state of literal with name c if it is present, {@link it.univr.di.labeledvalue.Literal#ABSENT}
	 * 	otherwise.
	 */
	public char getStateLiteralWithSameName(final char c) {
		return get(Literal.index(c));
	}

	/**
	 * Determines the sub label of {@code this} that is also present (not present) in label {@code lab}.
	 *
	 * @param label    the label in which to find the common/uncommon sub-part.
	 * @param inCommon true if the common sub-label is wanted, false if the sub-label present in {@code this} and not in
	 *                 {@code lab} is wanted.
	 *
	 * @return the sub label of {@code this} that is in common/not in common (if inCommon is true/false) with
	 *    {@code lab}. The label returned is a new object that shares only literals with this or with from. If there is no
	 * 	common part, an empty label is returned.
	 */
	public Label getSubLabelIn(final Label label, final boolean inCommon) {
		Label sub = emptyLabel;
		if (isEmpty()) {
			return sub;
		}
		if ((label == null) || label.isEmpty()) {
			return (inCommon) ? sub : this;
		}
		for (byte i = (byte) (maxIndex + 1); (--i) >= 0; ) {
			final char thisState = get(i);
			if (thisState == Literal.ABSENT) {
				continue;
			}
			final char labelState = label.get(i);
			if (inCommon) {
				if (labelState == Literal.ABSENT) {
					continue;
				}
				if (thisState == labelState) {
					sub = sub.conjunctionExtended(Literal.charValue(i), labelState);
				}
			} else {
				if (labelState != thisState) {
					sub = sub.conjunctionExtended(Literal.charValue(i), thisState);
				}
			}
		}
		return sub;
	}

	/**
	 * Determines the sub label of {@code this} that is also present (not present) in label {@code lab}.
	 * <p>
	 * When the common sub label is required ({@code inCommon} true), if {@code strict} is true, then the common sub
	 * label is as expected: it contains only the literals of {@code this} also present into {@code lab}.
	 * <br>
	 * Otherwise, when {@code strict} is false, the common part contains also each literal that has the opposite or the
	 * unknown counterpart in {@code lab}. For example, is this='a¬b¿cd' and lab='bc¿d', the not strict common part is
	 * '¿b¿c¿d'.
	 *
	 * @param label    the label in which to find the common/uncommon sub-part.
	 * @param inCommon true if the common sub-label is wanted, false if the sub-label present in {@code this} and not in
	 *                 {@code lab} is wanted.
	 * @param strict   if the common part should contain only the same literals in both labels.
	 *
	 * @return the sub label of {@code this} that is in common/not in common (if inCommon is true/false) with
	 *    {@code lab}. The label returned is a new object that shares only literals with this or with from. If there is no
	 * 	common part, an empty label is returned.
	 */
	public Label getSubLabelIn(final Label label, final boolean inCommon, boolean strict) {
		Label sub = emptyLabel;
		if (isEmpty()) {
			return sub;
		}
		if ((label == null) || label.isEmpty()) {
			return (inCommon) ? sub : this;
		}
		for (byte i = (byte) (maxIndex + 1); (--i) >= 0; ) {
			final char thisState = get(i);
			if (thisState == Literal.ABSENT) {
				continue;
			}
			final char labelState = label.get(i);
			if (inCommon) {
				if (labelState == Literal.ABSENT) {
					continue;
				}
				if (thisState == labelState) {
					sub = sub.conjunctionExtended(Literal.charValue(i), labelState);
					continue;
				}
				if (!strict) {
					sub = sub.conjunctionExtended(Literal.charValue(i), Literal.UNKNOWN);
				}
			} else {
				if (labelState == Literal.ABSENT) {
					sub = sub.conjunctionExtended(Literal.charValue(i), thisState);
				}
			}
		}
		return sub;
	}

	/**
	 * Finds and returns the unique different literal between {@code this} and {@code lab} if it exists. If label
	 * {@code this} and {@code lab} differs in more than one literal, it returns null.
	 * <br>
	 * If {@code this} and {@code lab} contain a common proposition but in one its state is unknown and in other is
	 * straight or negated, it returns null;
	 *
	 * @param label a nor null neither empty label.
	 *
	 * @return the unique literal of 'this' that has its opposite in {@code lab}.
	 * 	<br>
	 * 	null, if there is no literal of such kind or there are two or more literals of this kind or this/label is empty
	 * 	or null.
	 */
	@Nullable
	public Literal getUniqueDifferentLiteral(final Label label) {
		if (label == null || label.isEmpty() || size() != label.size()) {
			return null;
		}
		byte theDistinguished = -1;
		char thisState;
		for (byte i = (byte) (maxIndex + 1); (--i) >= 0; ) {
			thisState = get(i);
			final char labelState = label.get(i);
			if (thisState == labelState) {
				continue;
			}
			if (thisState == Literal.UNKNOWN || labelState == Literal.UNKNOWN || thisState == Literal.ABSENT
			    || labelState == Literal.ABSENT) {
				return null;
			}
			if (theDistinguished == -1) {
				theDistinguished = i;
			} else {
				return null;
			}
		}
		if (theDistinguished != -1) {
			return Literal.valueOf(Literal.charValue(theDistinguished), get(theDistinguished));
		}
		return null;
	}

	@Override
	public int hashCode() {
		// It is impossible to guarantee a unique hashCode for each possible label.
		return bit1 << maxIndex | bit0;
	}

	/**
	 * A label L<sub>1</sub> is consistent with a label L<sub>2</sub> if L<sub>1</sub> &wedge; L<sub>2</sub> is
	 * satisfiable.<br> L<sub>1</sub> subsumes L<sub>2</sub> implies L<sub>1</sub> is consistent with L<sub>2</sub> but
	 * not vice-versa.
	 * <br>
	 * If L<sub>1</sub> contains an unknown literal ¿p, and L<sub>2</sub> contains p/¬p, then the conjunction
	 * L<sub>1</sub> &wedge; L<sub>2</sub> is not defined while ¿p subsumes p/¬p is true.
	 * <br>
	 * In order to consider also labels with unknown literals, this method considers the
	 * {@link #conjunctionExtended(Label)}: L<sub>1</sub> &#x2605; L<sub>2</sub>, where ¿p are used for represent the
	 * conjunction of opposite literals p and ¬p or literals like ¿p and p/¬p.
	 * <br>
	 * Now, it holds ¿p &#x2605; p = ¿p is satisfiable.
	 *
	 * @param label the label to check
	 *
	 * @return true if the label is consistent with this label.
	 */
	public boolean isConsistentWith(final Label label) {
		if (label == null) {
			// || (label.isEmpty() && !this.containsUnknown()) || (this.isEmpty()&& !label.containsUnknown()))
			return true;
		}
		/*
		 * Method 1.
		 * We consider L<sub>1</sub> &#x2605; L<sub>2</sub>,
		 */
		// both this and label have bit0 | bit1 != 0
		final long xBit0 =
			bit0 ^ label.bit0;// each bit=1 corresponds to 1) a positive literal present only in one label
		// or 2) an unknown literal present only in one label
		// or 3) a positive literal present in one label and the opposite present in the other
		final long xBit1 =
			bit1 ^ label.bit1;// each bit=1 corresponds to 1) a negative literal present only in one label
		// or 2) an unknown literal present only in one label
		// or 3) a negative literal present in one label and the opposite present in the other
		long aBit = xBit0 & xBit1;
		aBit = aBit & ~(bit0 & bit1) & ~(label.bit0
		                                 &
		                                 label.bit1);// ~(this.bit0 & this.bit1) contains 0 in correspondence of possible ¿p in this
		return aBit == 0;
		/*
		 * Method 2.
		 * The following code manages the case in which ¿p can be present but only if there is not a corresponding 'p' or '¬p' in the other label.
		 */
		// int uBit0 = this.bit0 | label.bit0;
		// int uBit1 = this.bit1 | label.bit1;
		// int aBit01 = uBit0 & uBit1;
		// if (aBit01 == 0)
		// return true; // no opposite literals, no unknown literals.
		// uBit0 = this.bit0 & this.bit1;
		// uBit1 = label.bit0 & label.bit1;
		// if (uBit0 != 0 || uBit1 != 0) {
		// // There is an unknown literal at least
		// if ((uBit0 & (label.bit0 ^ label.bit1)) != 0) {
		// // this label has an unknown literal at least
		// return false;// unknown on first label and straight/negated on the second label.
		// }
		// if ((uBit1 & (this.bit0 ^ this.bit1)) != 0) {
		// // this label has an unknown literal at least
		// return false;// straight/negated on first label and unknown on the second label.
		// }
		// return true;
		// }
		// return false;
	}

	/**
	 * @param lit the input literal
	 *
	 * @return true if lit is consistent with this label.
	 */
	public boolean isConsistentWith(Literal lit) {
		return isConsistentWith(Literal.index(lit.getName()), lit.getState());
	}

	/**
	 * @return true if the label contains no literal.
	 */
	public boolean isEmpty() {
		return size() == 0;
	}

	/**
	 * Since a label is a conjunction of literals, its negation is a disjunction of negative literals (i.e., not a
	 * label).
	 * <br>
	 * The negation operator returns a set of all negative literals of this label.
	 * <br>
	 * Bear in mind that the complement of a literal with unknown state is a null object.
	 *
	 * @return the array of all negative literal of this as an array. If this is empty, returns an empty array.;
	 */
	public Literal[] negation() {
		if (isEmpty()) {
			return new Literal[0];
		}

		final Literal[] literals = new Literal[size()];
		int j = 0;
		for (byte i = 0; i <= maxIndex; i++) {
			final char thisState = get(i);
			if (thisState == Literal.ABSENT || thisState == Literal.UNKNOWN) {
				continue;
			}
			literals[j++] = Literal.valueOf(Literal.charValue(i),
			                                thisState == Literal.NEGATED ? Literal.STRAIGHT : Literal.NEGATED);
		}
		return literals;
	}

	/**
	 * Returns a new label that is a copy of {@code this} without {@code proposition} if it is present. Removing a
	 * proposition means to remove all literal of the given proposition.
	 *
	 * @param proposition the proposition to remove.
	 *
	 * @return the new label.
	 */
	public Label remove(final char proposition) {
		return conjunction(proposition, Literal.ABSENT);
	}

	/**
	 * Returns a new label copy of {@code this} where all literals with names in <b>inputLabel</b> are removed.
	 *
	 * @param inputLabel names of literals to remove.
	 *
	 * @return the new label.
	 */
	public Label remove(Label inputLabel) {
		if (inputLabel == null) {
			return this;
		}

		int inputPropositions = inputLabel.bit0 | inputLabel.bit1;
		inputPropositions = ~inputPropositions;

		return valueOf(cacheIndex(bit1 & inputPropositions, bit0 & inputPropositions));
	}

	/**
	 * Returns a new label that is a copy of {@code this} where {@code literal} is removed if it is present. If label
	 * contains '¬p' and input literal is 'p', then the method returns a copy of {@code this}.
	 *
	 * @param literal the literal to remove
	 *
	 * @return the new label.
	 */
	public Label remove(final Literal literal) {
		final byte index = Literal.index(literal.getName());
		if (get(index) == literal.getState()) {
			return conjunction(literal.getName(), Literal.ABSENT);
		}
		return this;
	}

	/**
	 * @return the number of literals of the label.
	 */
	public int size() {
		return count;
	}

	/**
	 * A label L<sub>1</sub> subsumes (entails) a label L<sub>2</sub> if <code>L<sub>1</sub> ⊨ L<sub>2</sub></code>.<br>
	 * In other words, L<sub>1</sub> subsumes L<sub>2</sub> if L<sub>1</sub> contains all literals of L<sub>2</sub> when
	 * both they have no unknown literals.
	 * <br>
	 * An unknown literal ¿p represents the fact that the value of p is not known. Therefore, if ¿p is true, then p can
	 * be true or ¬p can be true.
	 * <br>
	 * If p is true or false, then ¿p is false. The same for ¬p.<br>
	 * Therefore, it holds that ¿p subsumes p, ¿p subsumes
	 * ¬p, and ¿p subsumes ¿p, while p NOT subsumes ¿p and ¬p NOT subsumes ¿p.
	 *
	 * @param label the label to check
	 *
	 * @return true if this subsumes label.
	 */
	public boolean subsumes(final Label label) {
		if ((label == null) || label.isEmpty()) {
			return true;
		}
		final int max = (maxIndex > label.maxIndex) ? maxIndex : label.maxIndex;
		for (byte i = (byte) (max + 1); (--i) >= 0; ) {
			final char thisState = get(i);
			final char labelState = label.get(i);
			if (thisState == labelState || labelState == Literal.ABSENT) {
				continue;
			}
			/*
			 * When labelState[i] != thisState[i], before saying that it is false, it must be checked if thisState[i] is a ¿.
			 * ¿p subsumes p, ¿p subsumes ¬p, ¿p subsumes ¿p
			 * p NOT subsumes ¿p, ¬p NOT subsumes ¿p.
			 */
			if (thisState != Literal.UNKNOWN) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Return a string representing the label as logical expression using logical 'not', 'and', and 'or'. String
	 * representations of operators can be given as parameters. A label 'P¬A' is represented as "P and not A". If negate
	 * is true, then 'P¬A' is represented as negated: "not P or A".
	 *
	 * @param negate negate the label before the conversion. Be careful!
	 * @param not    string representing not. If null, it is assumed "!"
	 * @param and    representing not. If null, it is assumed "&amp;"
	 * @param or     representing not. If null, it is assumed " | "
	 *
	 * @return empty string if label is null or empty, the string representation as logical expression otherwise.
	 */
	public String toLogicalExpr(final boolean negate, String not, String and, String or) {
		if (isEmpty()) {
			return "";
		}
		if (not == null) {
			not = "!";
		}
		if (and == null) {
			and = " & ";
		}
		if (or == null) {
			or = " | ";
		}
		final Literal[] lit = (negate) ? negation() : getLiterals();
		final StringBuilder s = new StringBuilder(80);

		for (final Literal literal : lit) {
			s.append((literal.isUnknown()) ? not : "").append(literal.getName()).append((negate) ? or : and);
		}
		return s.substring(0, s.length() - ((negate) ? or.length() : and.length()));
	}

	@Override
	public String toString() {
		if (isEmpty()) {
			return Constants.EMPTY_LABELstring;
		}
		final StringBuilder s = new StringBuilder(80);
		char st;
		for (byte i = 0; i <= maxIndex; i++) {
			st = get(i);
			if (st != Literal.ABSENT) {
				s.append(Literal.toChars(i, st));
			}
		}
		return s.toString();
	}

	/**
	 * A literal l is consistent with a label if the last one does not contain ¬l.
	 *
	 * @param literalIndex the index of the literal
	 * @param literalState its state
	 *
	 * @return true if the literal is consistent with this label.
	 */
	boolean isConsistentWith(final byte literalIndex, char literalState) {
		if (literalState == Literal.ABSENT) {
			return true;
		}
		final char thisState = get(literalIndex);
		if (thisState == Literal.UNKNOWN && literalState != Literal.UNKNOWN) {
			return false;
		}
		if (thisState != Literal.UNKNOWN && literalState == Literal.UNKNOWN) {
			return false;
		}
		return (!Literal.areComplement(thisState, literalState));
	}

	/**
	 * @param literalIndex the index of the literal to retrieve.
	 *
	 * @return the status of literal with index literalIndex. If the literal is not present, it returns
	 *    {@link Literal#ABSENT}.
	 */
	private char get(final byte literalIndex) {
		final int mask = 1 << literalIndex;
		final int b1 = ((bit1 & mask) != 0) ? 2 : 0;
		final int b0 = ((bit0 & mask) != 0) ? 1 : 0;
		return LITERAL_STATE[b1 + b0];
	}
}
