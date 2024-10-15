// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.util;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.annotation.Nonnull;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * An implementation of {@code Collection} interface that stores exactly 2 objects and is immutable.
 * <p>
 * Such implementation uses {@code equals} method and may be used as indices or map keys.</p>
 *
 * @param <T> type of the two elements.
 */
public class ObjectPair<T> implements Collection<T>, Serializable {
	/**
	 *
	 */
	@Serial
	private static final long serialVersionUID = 2L;
	/**
	 *
	 */
	private final T first;
	/**
	 *
	 */
	private final T second;

	/**
	 * Creates a {@code Pair} from the specified elements.
	 *
	 * @param value1 the first value in the new {@code Pair}
	 * @param value2 the second value in the new {@code Pair}
	 */
	public ObjectPair(T value1, T value2) {
		first = value1;
		second = value2;
	}

	/*
	 * Creates a Pair from the passed Collection. The size of the Collection must be 2.
	 *
	 * @param values the elements of the new <code>Pair</code>
	 *
	public ObjectPair(Collection<? extends T> values) {
		if (values.size() == 2) {
			Iterator<? extends T> iter = values.iterator();
			this.first = iter.next();
			this.second = iter.next();
		} else {
			throw new IllegalArgumentException("Pair may only be created from a Collection of exactly 2 elements");
		}

	}
	*/

	/**
	 *
	 */
	@Override
	public boolean add(T o) {
		throw new UnsupportedOperationException("Pairs cannot be mutated");
	}

	/*
	 * Creates a <code>Pair</code> from the passed array. The size of the array must be 2.
	 *
	 * @param values the values to be used to construct this Pair
	 * @throws java.lang.IllegalArgumentException if the input array is null, contains null values, or has != 2
	 *                                            elements.
	 *
	public ObjectPair(T[] values) {
		if (values.length == 2) {
			this.first = values[0];
			this.second = values[1];
		} else {
			throw new IllegalArgumentException("Pair may only be created from an array of 2 elements");
		}
	}
	*/

	/**
	 *
	 */
	@Override
	public boolean addAll(@Nonnull Collection<? extends T> c) {
		throw new UnsupportedOperationException("Pairs cannot be mutated");
	}

	/**
	 *
	 */
	@Override
	public void clear() {
		throw new UnsupportedOperationException("Pairs cannot be mutated");
	}

	/**
	 *
	 */
	@Override
	public boolean containsAll(Collection<?> c) {
		if (c.size() > 2) {
			return false;
		}
		final Iterator<?> iter = c.iterator();
		final Object c_first = iter.next();
		final Object c_second = iter.next();
		return contains(c_first) && contains(c_second);
	}

	/**
	 *
	 */
	@Override
	public boolean contains(Object o) {
		return (first == o || first.equals(o) || second == o || second.equals(o));
	}

	/**
	 *
	 */
	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof ObjectPair<?> otherPair) {
			return (Objects.equals(first, otherPair.first)) && (Objects.equals(second, otherPair.second));
		}
		return false;
	}

	/**
	 * @return the first element.
	 */
	public T getFirst() {
		return first;
	}

	/**
	 * @return the second element.
	 */
	public T getSecond() {
		return second;
	}

	/**
	 *
	 */
	@Override
	public int hashCode() {
		int hashCode = 1;
		hashCode = 31 * hashCode + (first == null ? 0 : first.hashCode());
		hashCode = 31 * hashCode + (second == null ? 0 : second.hashCode());
		return hashCode;
	}

	/**
	 *
	 */
	@Override
	public boolean isEmpty() {
		return false;
	}

	/**
	 *
	 */
	@Nonnull
	@Override
	public Iterator<T> iterator() {
		return new PairIterator();
	}

	/**
	 *
	 */
	@Override
	public boolean remove(Object o) {
		throw new UnsupportedOperationException("Pairs cannot be mutated");
	}

	/**
	 *
	 */
	@Override
	public boolean removeAll(@Nonnull Collection<?> c) {
		throw new UnsupportedOperationException("Pairs cannot be mutated");
	}

	/**
	 *
	 */
	@Override
	public boolean retainAll(@Nonnull Collection<?> c) {
		throw new UnsupportedOperationException("Pairs cannot be mutated");
	}

	/**
	 *
	 */
	@Override
	public int size() {
		return 2;
	}

	/**
	 *
	 */
	@SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification = "False positive.")
	@Nonnull
	@Override
	public Object[] toArray() {
		final Object[] to_return = new Object[2];
		to_return[0] = first;
		to_return[1] = second;
		return to_return;
	}

	/**
	 *
	 */
	@Nonnull
	@Override
	@SuppressWarnings("unchecked")
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "RCN", justification =
		"RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE: I can ignore.")
	public <S> S[] toArray(@Nonnull S[] a) {
		S[] to_return = a;
		final Class<?> type = a.getClass().getComponentType();
		if (a.length < 2) {
			to_return = (S[]) java.lang.reflect.Array.newInstance(type, 2);
		}
		to_return[0] = (S) first;
		to_return[1] = (S) second;

		if (to_return.length > 2) {
			to_return[2] = null;
		}
		return to_return;
	}

	/**
	 *
	 */
	@Override
	public String toString() {
		return "<" + first.toString() + ", " + second.toString() + ">";
	}

	/**
	 * @author posenato
	 */
	private class PairIterator implements Iterator<T> {
		/**
		 *
		 */
		int position;

		/**
		 *
		 */
		PairIterator() {
			position = 0;
		}

		@Override
		public boolean hasNext() {
			return position < 2;
		}

		@Override
		public T next() {
			position++;
			if (position == 1) {
				return first;
			} else if (position == 2) {
				return second;
			} else {
				throw new NoSuchElementException("No more elements");
			}
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException("Pairs cannot be mutated");
		}
	}
}
