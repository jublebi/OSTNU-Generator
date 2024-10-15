// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

package it.univr.di.labeledvalue;

import it.univr.di.Debug;
import it.univr.di.labeledvalue.ALabelAlphabet.ALetter;
import org.netbeans.validation.api.Problems;
import org.netbeans.validation.api.Validator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serial;
import java.io.Serializable;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Simple class to represent a <em>A-label</em> in the CSTNU framework.
 * <br>
 * Scope of an A-label is to represent the conjunction of zero o more (upper-case) contingent node names.
 * <br>
 * Therefore, An A-label is a conjunction of zero or more
 * <em>A-Letters</em> in the alphabet ({@link ALabelAlphabet}).
 * <br>
 * A label without letters is called <em>empty label</em> and it is represented graphically as
 * {@value it.univr.di.labeledvalue.Constants#EMPTY_UPPER_CASE_LABEL}.
 * <h2>Design assumptions</h2>
 * Since in CSTNU project the memory footprint of a label is an important aspect, after some experiments, I have found
 * that the best way to represent an A-label is to limit the possible A-letters to 64 distinct strings and to use one
 * {@code int} for representing the state of A-letters composing an A-label: present/absent.
 *
 * @author Roberto Posenato
 * @version $Rev: 887 $
 */
@SuppressWarnings({"CompareToUsesNonFinalVariable", "NonFinalFieldReferenceInEquals", "NonFinalFieldReferencedInHashCode"})
public class ALabel implements Comparable<ALabel>, Iterable<ALetter>, Serializable {

	/**
	 * An unmodifiable empty label.
	 *
	 * @author posenato
	 */
	public static final class EmptyLabel extends ALabel {
		/**
		 *
		 */
		@Serial
		private static final long serialVersionUID = 1L;

		/**
		 * default constructor
		 */
		public EmptyLabel() {
		}

		@Override
		public boolean isEmpty() {
			return true;
		}

		@Override
		public boolean conjoin(ALetter aLetter) {
			throw new IllegalStateException("Empty label cannot be modified");
		}

		@SuppressWarnings("NullableProblems")
		@Override
		public int compareTo(ALabel l) {
			if (l == null) {
				return 1;
			}
			if (l.isEmpty()) {
				return 0;
			}
			return -1;
		}

		/**
		 * @param o the input object
		 *
		 * @return true if o is an empty label, false otherwise
		 */
		public boolean equals(Object o) {
			if (!(o instanceof ALabel o1)) {
				return false;
			}
			return o1.isEmpty();
		}

		/**
		 * @return 0 as hascode
		 */
		public int hashCode() {
			return 0;
		}

		@Override
		public ALabel conjunction(final ALabel label) {
			if (label.isEmpty()) {
				return this;
			}
			return ALabel.clone(label);
		}

		@Override
		public boolean contains(final ALabel label) {
			return label == null || label.isEmpty();
		}

		// @Override
		// public ALetter[] getAllLetter() {
		// return new ALetter[0];
		// }

		@Override
		public boolean contains(final ALetter letter) {
			return letter == null;
		}

		@Override
		public int size() {
			return 0;
		}

		@Override
		public String toString() {
			return Constants.EMPTY_UPPER_CASE_LABELstring;
		}

		@Nonnull
		@Override
		public Iterator<ALetter> iterator() {
			throw new IllegalCallerException("Empty label cannot have an iterator.");
		}

		@Override
		public void remove(final ALetter letter) {
		}

		@Override
		public void remove(final ALetter[] letter) {
		}
	}

	/**
	 * Validator for graphical interface
	 */
	@SuppressWarnings({"unused", "AnonymousInnerClass", "AnonymousInnerClassWithTooManyMethods",
	                   "OverlyComplexAnonymousInnerClass"})
	public static final Validator<String> labelValidator = new Validator<>() {
		@Override
		public void validate(final Problems problems, final String compName, final String model) {
			if ((model == null) || (model.isEmpty())) {
				return;
			}

			if (!Pattern.matches(ALABEL_RE, model)) {
				problems.append("Highlighted label is not well-formed.");
			}
		}

		@Override
		public Class<String> modelType() {
			return String.class;
		}
	};

	/**
	 * Possible state of a {@link ALetter} in an alphabetic label.
	 *
	 * @author posenato
	 */
	public enum State {
		/**
		 * Not present. Useful only for BitLabel class.
		 */
		absent,
		/**
		 * A negated literal is true if the truth value assigned to its proposition letter is false, false otherwise.
		 */
		present;

		@Override
		public String toString() {
			return "";
		}
	}

	/**
	 * ALabel separator '∙'.
	 */
	public static final char ALABEL_SEPARATOR = '∙';
	/**
	 * ALabel separator '∙'.
	 */
	public static final String ALABEL_SEPARATORstring = String.valueOf(ALABEL_SEPARATOR);
	/**
	 * Regular expression representing an A-Label. The re checks only that label chars are allowed.
	 */
	public static final String ALABEL_RE =
		"[" + ALabelAlphabet.ALETTER + ALABEL_SEPARATORstring + "]+|" + Constants.EMPTY_UPPER_CASE_LABELstring;

	/**
	 * Constructs a label cloning the given label l.
	 *
	 * @param label the label to clone. It cannot be null or empty!
	 */
	private ALabel(final ALabel label) {
		this();
		if (label == null || label.isEmpty()) {
			throw new IllegalArgumentException("Label cannot be null or empty!");
		}
		alphabet = label.alphabet;// alphabet has to be shared!
		bit0 = label.bit0;
		maxIndex = label.maxIndex;
		cacheOfSize = label.cacheOfSize;
	}

	/**
	 * Maximum size for the alphabet. Such limitation is dictated by the ALabel class implementation.
	 */
	public static final byte MAX_ALABELALPHABET_SIZE = 64;

	/**
	 * A constant empty label to represent an empty label that cannot be modified.
	 */
	@SuppressWarnings("StaticInitializerReferencesSubClass")
	public static final ALabel emptyLabel = new EmptyLabel();

	/**
	 *
	 */
	@Serial
	private static final long serialVersionUID = 1L;
	/**
	 * logger
	 */
	private static final Logger LOG = Logger.getLogger(ALabel.class.getName());
	/**
	 * The number of times this ALabel has been <i>structurally modified</i>. Structural modifications are those that
	 * change the size, or otherwise perturb it in such a fashion that iterations in progress may yield incorrect
	 * results.
	 */
	protected transient int modCount;
	/**
	 * Alphabet to map the name into A-letter
	 */
	private ALabelAlphabet alphabet;
	/**
	 * One long has 64 bits.
	 * <br>
	 * Each position is associated to a A-Letter.
	 *
	 * <pre>
	 * Status of i-th A-Letter
	 *              bit0[i]
	 * not present          0
	 * present              1
	 * </pre>
	 */
	private long bit0;
	/**
	 * Number of A-letters in the label Value -1 means that the size has to be calculated!
	 */
	private byte cacheOfSize;
	/**
	 * Index of the last significant A-Letter of label. On 2016-03-30 I showed by SizeofUtilTest.java that using byte it
	 * is possible to define also 'size' field without incrementing the memory footprint of the object.
	 */
	private byte maxIndex;

	/**
	 * Just for internal use
	 */
	private ALabel() {
		bit0 = 0;
		maxIndex = -1;
		cacheOfSize = 0;
		modCount = 0;
	}

	/**
	 * @return true if the label contains no literal.
	 */
	public boolean isEmpty() {
		return bit0 == 0;
	}

	/**
	 * Builds an a-label using the a-letter 'l' and 'alphabet'. Be aware that if 'l' is not present into alphabet, it
	 * will be added.
	 *
	 * @param l         first a-letter of label
	 * @param alphabet1 alphabet of a-letters. It may be empty!
	 */
	public ALabel(ALetter l, ALabelAlphabet alphabet1) {
		this(alphabet1);
		conjoin(l);
	}

	/**
	 * Helper constructor. It calls ALabel(ALetter, ALabelAlphabet). Be aware that if 's' is not present into alphabet
	 * as a-letter, it will be added as a-letter.
	 *
	 * @param s         the string to add.
	 * @param alphabet1 alphabet of a-letters. It may be empty!
	 */
	public ALabel(String s, ALabelAlphabet alphabet1) {
		this(new ALetter(s), alphabet1);
	}

	/**
	 * Default constructor using a given alphabet.
	 *
	 * @param alphabet1 the input alphabet. It cannot be null.
	 */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "EI_EXPOSE_REP2",
		justification = "For efficiency reason, it includes an external mutable object.")
	public ALabel(ALabelAlphabet alphabet1) {
		this();
		if (alphabet1 == null) {
			throw new IllegalArgumentException("Alphabet cannot be null!");
		}
		alphabet = alphabet1;
	}

	/**
	 * Conjoins {@code a-letter} to this.
	 *
	 * @param aLetter the a-letter to conjoin.
	 *
	 * @return true if a-letter is added, false otherwise.
	 */
	public boolean conjoin(final ALetter aLetter) {
		if (aLetter == null) {
			return false;
		}
		byte propIndex = getIndex(aLetter);
		if (propIndex < 0) {
			propIndex = alphabet.put(aLetter);
		}
		set(propIndex, State.present);
		return true;
	}

	/**
	 * @param letter the input a-letter
	 *
	 * @return the index of the letter in the alphabet.
	 */
	private byte getIndex(final ALetter letter) {
		return alphabet.index(letter);
	}

	/**
	 * @param aLetterIndex the index of the literal to update.
	 * @param letterStatus the new state.
	 */
	private void set(final byte aLetterIndex, final State letterStatus) {
		/*
		 * <pre>
		 * Status of i-th literal
		 *             bit0[i]
		 * absent       0
		 * present      1
		 * </pre>
		 */
		if (aLetterIndex < 0 || aLetterIndex > MAX_ALABELALPHABET_SIZE) {
			return;
		}
		long mask = 1L << aLetterIndex;
		switch (letterStatus) {
			case present:
				if (((bit0) & mask) == 0) {
					cacheOfSize++;
				}
				bit0 |= mask;
				if (maxIndex < aLetterIndex) {
					maxIndex = aLetterIndex;
				}
				return;
			case absent:
			default:
				if (((bit0) & mask) != 0) {
					cacheOfSize--;
				}
				mask = ~mask;
				bit0 &= mask;
				if (maxIndex == aLetterIndex) {
					final long u = bit0;
					mask = ~mask;
					do {
						maxIndex--;
						mask = mask >>> 1;
					} while ((u & mask) == 0 && maxIndex >= 0);
				}
		}
	}

	/**
	 * Makes the label empty.
	 */
	public void clear() {
		bit0 = 0;
		maxIndex = -1;
		cacheOfSize = 0;
	}

	/**
	 * In order to have a correct copy of an a-label.
	 *
	 * @param label the input value
	 *
	 * @return a distinct equal copy of label
	 */
	static public ALabel clone(final ALabel label) {
		if (label == null || label.isEmpty()) {
			return emptyLabel;
		}
		return new ALabel(label);
	}

	/**
	 * Parse a string representing an A-label and return an equivalent A-Label object if no errors are found, null
	 * otherwise.
	 * <br>
	 * The regular expression syntax for a label is specified in {@link it.univr.di.labeledvalue.Label#LABEL_RE}.
	 *
	 * @param s        a {@link java.lang.String} object.
	 * @param alphabet A {@link it.univr.di.labeledvalue.ALabelAlphabet} to use. If null, it will be generated and added
	 *                 to the return label.
	 *
	 * @return a Label object corresponding to the label string representation.
	 */
	@Nullable
	public static ALabel parse(String s, ALabelAlphabet alphabet) {
		if (s == null) {
			return null;
		}
		final int n = s.length();
		if (n == 0 || Pattern.matches(Constants.EMPTY_UPPER_CASE_LABELstring, s)) {
			return emptyLabel;
		}
		if (!Pattern.matches(ALABEL_RE, s)) {
			return null;
		}
		// split all possible letters
		final String[] letters = s.split(ALABEL_SEPARATORstring);
		final int size = letters.length;
		if (size == 0) {
			return emptyLabel;
		}

		// build alphabet
		if (alphabet == null) {
			alphabet = new ALabelAlphabet();
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Created a new ALabelAlphabet: " + alphabet);
				}
			}
		}

		// build alabel
		final ALabel alabel = new ALabel(alphabet);
		for (final String letter : letters) {
			alabel.conjoin(new ALetter(letter));
		}

		return alabel;
	}

	/**
	 * In order to speed up this method and considering that the {@link ALabelAlphabet} order may be not the expected
	 * alphabetic one, (first letter in an {@link ALabelAlphabet} can be 'nodeZ' and the last one 'aNode'), the order of
	 * labels is given w.r.t. their indexes in their {@link ALabelAlphabet}.
	 */
	@SuppressWarnings("NullableProblems")
	@Override
	public int compareTo(@Nonnull final ALabel label) {
		if (label.isEmpty()) {
			if (isEmpty()) {
				return 0;
			}
			return 1;
		}
		if (alphabet != label.alphabet) {
			throw new IllegalArgumentException(
				"Comparison is not possible because the given label has a different alphabet from the current one!");
		}
		return Long.compareUnsigned(bit0, label.bit0);
	}

	/**
	 * Conjoins {@code a-label} to {@code this} and returns the result without modifying {@code this}.
	 *
	 * @param label the label to conjoin
	 *
	 * @return a new label with the conjunction of 'this' and 'label'. Regurns null if this cannot conjuncted with
	 * 	label.
	 */
	@Nullable
	public ALabel conjunction(final ALabel label) {
		if (label == null || (!isEmpty() && !label.isEmpty() && alphabet != label.alphabet)) {
			return null;
		}
		if (isEmpty()) {
			return new ALabel(label);
		}
		if (label.isEmpty()) {
			return new ALabel(this);
		}

		final ALabel newLabel = new ALabel(alphabet);
		newLabel.bit0 = bit0 | label.bit0;
		newLabel.maxIndex = (label.maxIndex > maxIndex) ? label.maxIndex : maxIndex;
		newLabel.cacheOfSize = -1;// it has to be calculated... delay the stuff.
		return newLabel;
	}

	/**
	 * L<sub>1</sub> contains L<sub>2</sub> if L<sub>1</sub> contains all a-letters of L<sub>2</sub>.
	 *
	 * @param label the label to check
	 *
	 * @return true if this contains label.
	 */
	public boolean contains(final ALabel label) {
		if ((label == null) || label.isEmpty()) {
			return true;
		}
		if (alphabet != label.alphabet) {
			throw new IllegalArgumentException(
				"label is not defined using the same alphabet: " + alphabet.toString() + " vs "
				+ label.alphabet.toString());
		}
		// int max = (this.maxIndex > label.maxIndex) ? this.maxIndex : label.maxIndex;
		// for (byte i = (byte) (max + 1); (--i) >= 0;) {
		// State thisState = getState(i);
		// State labelState = label.getState(i);
		// if (thisState == labelState || labelState == State.absent)
		// continue;
		// if (thisState == State.absent)
		// return false;
		// }
		// return true;
		// 1st xor shows different bits. Masking them with the complement of this, shows the bits 1 in label.bit0 that are not present in this.bit0.
		return (((bit0 ^ label.bit0) & (~bit0)) == 0);
	}

	/**
	 * @param name the proposition to check.
	 *
	 * @return true if this contains proposition in any state: straight, negated or unknown.
	 */
	public boolean contains(final ALetter name) {
		if (name == null) {
			return true;
		}
		return getState(getIndex(name)) != State.absent;
	}

	/**
	 * @param letterIndex the index of the literal to retrieve.
	 *
	 * @return the status of literal with index literalIndex. If the literal is not present or the index is not in the
	 * 	range of present ALetters, it returns {@link State#absent}, otherwise {@link State#present}.
	 */
	final State getState(final byte letterIndex) {
		if (letterIndex < 0 || letterIndex > maxIndex) {
			return State.absent;
		}
		final long mask = 1L << letterIndex;
		return ((bit0 & mask) != 0) ? State.present : State.absent;
	}

	/*
	 * @return The array of a-letters of present literals in this label.
	 *         public ALetter[] getAllLetter() {
	 *         ALetter[] letters = new ALetter[size()];
	 *         int j = 0;
	 *         for (byte i = 0; i <= this.maxIndex; i++) {
	 *         if (getState(i) != State.absent) {
	 *         letters[j++] = this.getLetter(i);
	 *         }
	 *         }
	 *         return letters;
	 *         }
	 */

	/**
	 * Compare the letter with an a-letter name.
	 *
	 * @param name the input aletter
	 *
	 * @return true if the label is equal to the a-letter name.
	 */
	public boolean equals(final ALetter name) {
		if (name == null) {
			return false;
		}
		return size() == 1 && getState(getIndex(name)) == State.present;
	}

	/**
	 * @return Return the number of literals of the label
	 */
	public int size() {
		if (cacheOfSize >= 0) {
			return cacheOfSize;
		}
		// byte _cacheOfSize = 0;
		// long or = this.bit0;
		// for (int i = this.maxIndex + 1; (--i) >= 0;) {
		// _cacheOfSize += (or & 1);
		// or = or >>> 1;
		// }
		cacheOfSize = (byte) Long.bitCount(bit0);
		return cacheOfSize;
	}

	/**
	 *
	 */
	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof ALabel alabel)) {
			return false;
		}
		if (isEmpty() && alabel.isEmpty()) {
			return true;
		}
		return alphabet.equals(alabel.alphabet) && bit0 == alabel.bit0;
	}

	/**
	 * @return the alphabet
	 */
	public ALabelAlphabet getAlphabet() {
		return alphabet;
	}

	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "ICAST_INTEGER_MULTIPLY_CAST_TO_LONG",
		justification = "It is ok... because it is a hash code.")
	@Override
	public int hashCode() {
		// It is impossible to guarantee a unique hashCode for each possible label.
		return (int) (31 * maxIndex + bit0);
	}

	/**
	 * @param label the input label
	 *
	 * @return the label containing common ALetter between this and label.
	 */
	public ALabel intersect(final ALabel label) {
		if ((label == null) || isEmpty() || label.isEmpty()) {
			return emptyLabel;
		}
		if (alphabet != label.alphabet) {
			throw new IllegalArgumentException(
				"The input label is not defined using the same alphabet: " + alphabet.toString() + " vs "
				+ label.alphabet.toString());
		}
		final ALabel newLabel = new ALabel(alphabet);
		newLabel.bit0 = bit0 & label.bit0;
		if (newLabel.bit0 == 0) {
			return newLabel;
		}
		newLabel.maxIndex = (maxIndex > label.maxIndex) ? maxIndex : label.maxIndex;
		while (newLabel.maxIndex >= 0 && (newLabel.getState(newLabel.maxIndex) == State.absent)) {
			newLabel.maxIndex--;
		}
		return newLabel;
	}

	/**
	 * @return lower case version
	 */
	public String toLowerCase() {
		return toString().toLowerCase(Locale.ROOT);
	}

	/**
	 *
	 */
	@Nonnull
	@Override
	public Iterator<ALetter> iterator() {
		return new ALabelItr();
	}

	/**
	 *
	 */
	@Override
	public String toString() {
		if (isEmpty()) {
			return Constants.EMPTY_UPPER_CASE_LABELstring;
		}
		final StringBuilder s = new StringBuilder(6);
		State st;
		for (byte i = 0; i <= maxIndex; i++) {
			st = getState(i);
			if (st == State.present) {
				s.append(getLetter(i)).append(ALABEL_SEPARATOR);
			}
		}
		//there is one more ALABEL_SEPARATOR, remove it
		s.delete(s.length() - 1, s.length());
		return s.toString();
	}

	/**
	 * @return the ALetter format of this ALabel if its size is == 1; null otherwise.
	 */
	public ALetter getALetter() {
		if (this.size() != 1) {
			return null;
		}
		return iterator().next();
	}

	/**
	 * @param l the letter to add
	 *
	 * @return true if a-letter is added, false otherwise.
	 */
	public boolean put(final ALetter l) {
		return conjoin(l);
	}

	/**
	 * It removes all a-letters in <b>aLabel</b> from the current label.
	 *
	 * @param aLabel a-Label to remove.
	 */
	public void remove(final ALabel aLabel) {
		if (aLabel == null) {
			return;
		}
		for (final ALetter aletter : aLabel) {
			set(getIndex(aletter), State.absent);
		}
	}

	/**
	 * It removes a-letter if it is present, otherwise it does nothing.
	 *
	 * @param letter the letter to remove.
	 */
	public void remove(final ALetter letter) {
		if (letter == null) {
			return;
		}
		set(getIndex(letter), State.absent);
	}

	/**
	 * It removes all a-letters in <b>inputSet</b> from the current label.
	 *
	 * @param inputSet a-letters to remove.
	 */
	public void remove(final ALetter[] inputSet) {
		if (inputSet == null) {
			return;
		}
		for (int i = inputSet.length; (--i) >= 0; ) {
			set(getIndex(inputSet[i]), State.absent);
		}
	}

	/**
	 * @param index the index of the letter to remove.
	 */
	final void remove(final byte index) {
		set(index, State.absent);
	}

	/**
	 * @param letterIndex the index of the literal to retrieve.
	 *
	 * @return the letter with index literalIndex if index is in the range of present ALetter,
	 *    {@link ALabelAlphabet#DEFAULT_ALETTER_RET_VALUE} otherwise.
	 */
	final ALetter getLetter(final byte letterIndex) {
		return alphabet.get(letterIndex);
	}

	/**
	 * @return upper case version
	 */
	public String toUpperCase() {
		return toString().toUpperCase(Locale.ROOT);
	}

	/**
	 * @author posenato
	 */
	private final class ALabelItr implements Iterator<ALetter> {
		/**
		 * Index of element to be returned by subsequent call to next.
		 */
		byte cursor;

		/**
		 * The modCount value that the iterator believes that the backing List should have. If this expectation is
		 * violated, the iterator has detected concurrent modification.
		 */
		int expectedModCount = modCount;

		/**
		 * Index of element returned by most recent call to next or previous. Reset to -1 if this element is deleted by
		 * a call to remove.
		 */
		byte lastRet = -1;

		/**
		 *
		 */
		private ALabelItr() {
		}

		@Override
		public boolean hasNext() {
			return cursor < size();
		}

		@Override
		public ALetter next() {
			checkForCoModification();
			byte i = cursor;
			while (getState(i) == State.absent && i <= maxIndex) {
				i++;
			}
			if (i > maxIndex) {
				throw new NoSuchElementException("Iterator went beyond the limit.");
			}
			final ALetter next = getLetter(i);
			lastRet = i;
			cursor = (byte) (i + 1);
			return next;
		}

		/**
		 *
		 */
		void checkForCoModification() {
			if (modCount != expectedModCount) {
				throw new ConcurrentModificationException();
			}
		}

		@Override
		public void remove() {
			if (lastRet < 0) {
				throw new IllegalStateException();
			}
			checkForCoModification();

			try {
				ALabel.this.remove(lastRet);
				if (lastRet < cursor) {
					cursor--;
				}
				lastRet = -1;
				expectedModCount = modCount;
			} catch (IndexOutOfBoundsException e) {
				throw new ConcurrentModificationException();
			}
		}
	}
}
