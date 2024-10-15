// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.labeledvalue;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import javax.annotation.Nonnull;
import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * A customizable alphabet, where elements are strings. Each element has an index (integer) that can be used to retrieve
 * the element.
 * <br>
 * On 2017-4-13 it turns out that an A-Label, introduced in the first articles, is not just a name of a node
 * representing an upper-case letter (as used in Morris's rules), but it must be a set of upper-case letters.
 * <br>
 * Moreover, such upper-case letters represent node names and, therefore, they should be strings instead of letters.
 * <br>
 * To limit the memory footprint and to speed up some computation, class {@link ALabel} uses an alphabet,
 * {@link ALabelAlphabet}, for building labels. {@link ALabelAlphabet} associate each node name with one {@link ALetter}
 * univocally. Each {@link ALetter} has a codepoint (position) in the alphabet. Limiting to 32 the possible ALetters,
 * one ALabel can be represented by just an {@code int}.
 *
 * @author posenato
 * @version $Rev: 886 $
 */
public class ALabelAlphabet implements Serializable {

	/**
	 * ALetter makes simpler to check if a node name is appropriate.
	 * <br>
	 * ALabelAlphabet is built over ALetter.
	 *
	 * @author posenato
	 */
	public static class ALetter implements Comparable<ALetter>, Serializable {
		/**
		 *
		 */
		@Serial
		private static final long serialVersionUID = 1L;
		/**
		 *
		 */
		public final String name;

		/**
		 * @param s the input string represent the letter
		 */
		public ALetter(String s) {
			if (s == null || s.isEmpty()) {
				throw new IllegalArgumentException("An ALetter cannot be null or empty");
			}
			if (!Pattern.matches(ALabelAlphabet.ALETTER_RANGE, s)) {
				throw new IllegalArgumentException("The argument " + s + " must be in the regular-expression range: "
				                                   + ALabelAlphabet.ALETTER_RANGE);
			}
			name = s;
		}

		@Override
		public int compareTo(@Nonnull ALetter o) {
			return name.compareTo(o.name);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o instanceof ALetter inputAletter) {
				return this.name.equals(inputAletter.name);
			}
			return false;
		}

		/**
		 * @param inputName a string
		 *
		 * @return true if this a-letter represents the string, false otherwise.
		 */
		public boolean equals(String inputName) {
			return this.name.equals(inputName);
		}

		@Override
		public int hashCode() {
			return name.hashCode();
		}

		@Override
		public String toString() {
			return name;
		}
	}

	/**
	 * A-Letter.
	 */
	public static final String ALETTER = "A-Za-z0-9_ωΩ? ";// α-μ_

	/**
	 * A-Letter range.
	 */
	@SuppressWarnings("StaticMethodOnlyUsedInOneClass")
	public static final String ALETTER_RANGE = "[" + ALETTER + "]+";

	/**
	 * Default value for not found index.
	 */
	public static final ALetter DEFAULT_ALETTER_RET_VALUE = null;

	/**
	 * Default value for not found name.
	 */
	public static final byte DEFAULT_BYTE_RET_VALUE = -1;
	/**
	 * Maximum size for the alphabet. Such limitation is dictated by the ALabel class implementation.
	 */
	public static final byte MAX_ALABELALPHABET_SIZE = ALabel.MAX_ALABELALPHABET_SIZE;
	/**
	 *
	 */
	static final long serialVersionUID = 1L;
	/**
	 * The empty a-letter.
	 */
	private static final ALetter[] EMPTY_ALPHABET = {};
	/**
	 * The a-letters of this alphabet.
	 * <br>
	 * Such array does not contain holes.
	 */
	private ALetter[] value;
	/**
	 * The number of valid entries in {@link #value}.
	 */
	private byte size;
	/**
	 * In order to speed up the #index method, a map int2Aletter is maintained.
	 */
	private Object2IntOpenHashMap<ALetter> value2int;

	/**
	 * Constructor by copy. The new alphabet is an independent copy.
	 *
	 * @param alpha alphabet to copy.
	 */
	public ALabelAlphabet(ALabelAlphabet alpha) {
		size = alpha.size;
		value = Arrays.copyOf(alpha.value, alpha.value.length);
		value2int = new Object2IntOpenHashMap<>(alpha.value2int);
		value2int.defaultReturnValue(DEFAULT_BYTE_RET_VALUE);
	}

	/**
	 * @param size1 initial size of alphabet
	 */
	public ALabelAlphabet(int size1) {
		this();
		if (size1 > MAX_ALABELALPHABET_SIZE) {
			throw new IllegalArgumentException("Dimension exceeds the maximum capacity!");
		}
		value = new ALetter[size1];
		value2int = new Object2IntOpenHashMap<>(size1);
		value2int.defaultReturnValue(DEFAULT_BYTE_RET_VALUE);
	}

	/**
	 * Default constructor.
	 */
	public ALabelAlphabet() {
		size = 0;
		value = EMPTY_ALPHABET;
		value2int = new Object2IntOpenHashMap<>();
		value2int.defaultReturnValue(DEFAULT_BYTE_RET_VALUE);
	}

	/**
	 * Cleans the map.
	 */
	public void clear() {
		for (int i = size; i-- != 0; ) {
			value[i] = null;
		}
		value2int.clear();
		size = 0;
	}

	/**
	 * @param v the input letter
	 *
	 * @return true if v is present, false otherwise
	 */
	public boolean containsValue(ALetter v) {
		return index(v) >= 0;
	}

	/**
	 * @param k the index of the wanted a-letter
	 *
	 * @return the a-letter associated to index k, {@link #DEFAULT_ALETTER_RET_VALUE} if it does not exist
	 */
	public ALetter get(final byte k) {
		if (k < 0 || k >= size) {
			return DEFAULT_ALETTER_RET_VALUE;
		}
		return value[k];
	}

	/**
	 * @param name the input a-letter
	 *
	 * @return the index associated to name if it exists, {@link #DEFAULT_BYTE_RET_VALUE} otherwise
	 */
	public byte index(final ALetter name) {
		return (byte) value2int.getInt(name);
	}

	/**
	 * @return true is this does not contain a-letter
	 */
	public boolean isEmpty() {
		return size == 0;
	}

	/**
	 * Puts the element v in the map if not present.
	 *
	 * @param v a non-null a-letter
	 *
	 * @return the index associate to the element v if {@code v} is present, {@value #DEFAULT_BYTE_RET_VALUE} if
	 *    {@code v} is null
	 *
	 * @throws java.lang.IllegalArgumentException if there are already {@link #MAX_ALABELALPHABET_SIZE} elements in the
	 *                                            map
	 */
	public byte put(ALetter v) {
		if (v == null) {
			return DEFAULT_BYTE_RET_VALUE;
		}

		byte k = index(v);
		if (k >= 0) {
			return k;
		}
		if (size == value.length) {
			if (size == MAX_ALABELALPHABET_SIZE) {
				throw new IllegalArgumentException("It is not possible to add new ALetter to this alphabet. The maximum size is " + MAX_ALABELALPHABET_SIZE);
			}
			value = Arrays.copyOf(value, size + MAX_ALABELALPHABET_SIZE / 4);
		}
		k = size;
		value[k] = v;
		value2int.put(v, k);
		size++;
		// Arrays.sort(this.value, 0, this.size); It is not possible to sort because indexes could be already used.
		// return this.index(v);
		return k;
	}

	/**
	 * @return the current size of this alphabet
	 */
	public int size() {
		return size;
	}

	/**
	 *
	 */
	@Override
	public String toString() {
		return Arrays.toString(value);
	}
}
