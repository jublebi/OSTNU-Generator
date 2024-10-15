// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.util;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.NoSuchElementException;

/**
 * A type-specific array-based FIFO queue, supporting also dequeue operations.
 * <em>An element is added only if not already present. Moreover, it is possible to check if an element if
 * present.</em>
 * <p>
 * Instances of this class represent a FIFO queue using a backing array in a circular way. The array is enlarged and
 * shrunk as needed. You can use the {@link #trim()} method to reduce its memory usage, if necessary.
 * <p>
 * This class provides additional methods that implement a <em>dequeue</em> (double-ended queue).
 *
 * @param <K> type of the element in the queue
 *
 * @author posenato
 * @version $Rev: 732 $
 */
@SuppressWarnings({"SuspiciousSystemArraycopy", "SuspiciousArrayCast"})
public class ObjectArrayFIFOSetQueue<K> implements PriorityQueue<K>, ObjectSet<K>, Serializable {
	/**
	 * The standard initial capacity of a queue.
	 */
	public static final int INITIAL_CAPACITY = 4;
	/**
	 *
	 */
	@Serial
	private static final long serialVersionUID = 1L;
	/**
	 * The backing array.
	 */
	protected K[] backingArray;
	/**
	 * The current (cached) length of {@link #backingArray}.
	 */
	protected transient int length;
	/**
	 * The start position in {@link #backingArray}. It is always strictly smaller than {@link #length}.
	 */
	protected transient int start;
	/**
	 * The end position in {@link #backingArray}. It is always strictly smaller than {@link #length}. Might be actually
	 * smaller than {@link #start} because {@link #backingArray} is used cyclically.
	 */
	protected transient int end;

	/**
	 * To fast check if an element is present
	 */
	protected transient ObjectSet<K> present;

	/**
	 * Creates a new empty queue with standard {@linkplain #INITIAL_CAPACITY}.
	 */
	public ObjectArrayFIFOSetQueue() {
		this(INITIAL_CAPACITY);
	}

	/**
	 * Creates a new empty queue with given capacity.
	 *
	 * @param capacity the initial capacity of this queue.
	 */
	@SuppressWarnings("unchecked")
	public ObjectArrayFIFOSetQueue(final int capacity) {
		if (capacity < 0) {
			throw new IllegalArgumentException("Initial capacity (" + capacity + ") is negative");
		}
		backingArray =
			(K[]) new Object[Math.max(1, capacity)]; // Never build a queue with zero-sized backing array.
		length = backingArray.length;
		present = new ObjectOpenHashSet<>();
	}

	/**
	 *
	 */
	@Override
	public boolean add(K e) {
		if (contains(e)) {
			return false;
		}
		enqueue(e);
		return true;
	}

	/**
	 *
	 */
	@Override
	public void enqueue(K x) {
		if (contains(x)) {
			return;
		}
		backingArray[end++] = x;
		present.add(x);
		if (end == length) {
			end = 0;
		}
		if (end == start) {
			expand();
		}
	}

	/**
	 *
	 */
	@Override
	public boolean addAll(Collection<? extends K> c) {
		for (final K o : c) {
			enqueue(o);
		}
		return true;
	}

	/**
	 *
	 */
	@Override
	public void clear() {
		if (start <= end) {
			Arrays.fill(backingArray, start, end, null);
		} else {
			Arrays.fill(backingArray, start, length, null);
			Arrays.fill(backingArray, 0, end, null);
		}
		start = end = 0;
	}

	/**
	 * This implementation returns {@code null} (FIFO queues have no comparator).
	 */
	@Nullable
	@Override
	public Comparator<? super K> comparator() {
		return null;
	}

	/**
	 *
	 */
	@Override
	public boolean containsAll(Collection<?> c) {
		for (final Object o : c) {
			if (!contains(o)) {
				return false;
			}
		}
		return true;
	}

	/**
	 *
	 */
	@Override
	public K dequeue() {
		if (start == end) {
			throw new NoSuchElementException();
		}
		final K t = backingArray[start];
		backingArray[start] = null; // Clean-up for the garbage collector.
		present.remove(t);
		if (++start == length) {
			start = 0;
		}
		reduce();
		return t;
	}

	/**
	 * Dequeues the {@linkplain PriorityQueue#last() last} element from the queue.
	 *
	 * @return the dequeued element.
	 */
	public K dequeueLast() {
		if (start == end) {
			throw new NoSuchElementException();
		}
		if (end == 0) {
			end = length;
		}
		final K t = backingArray[--end];
		backingArray[end] = null; // Clean-up for the garbage collector.
		present.remove(t);
		reduce();
		return t;
	}

	/**
	 * reduce the queue
	 */
	private void reduce() {
		final int size = size();
		if (length > INITIAL_CAPACITY && size <= length / 4) {
			resize(size, length / 2);
		}
	}

	/**
	 *
	 */
	@Override
	public int size() {
		final int apparentLength = end - start;
		return apparentLength >= 0 ? apparentLength : length + apparentLength;
	}

	/**
	 * Resizes the #backingArray.
	 *
	 * @param size      the end position in the new array
	 * @param newLength the length of the new array
	 */
	@SuppressWarnings("unchecked")
	private void resize(final int size, final int newLength) {
		final K[] newArray = (K[]) new Object[newLength];
		if (start >= end) {
			if (size != 0) {
				System.arraycopy(backingArray, start, newArray, 0, length - start);
				System.arraycopy(backingArray, 0, newArray, length - start, end);
			}
		} else {
			System.arraycopy(backingArray, start, newArray, 0, end - start);
		}
		start = 0;
		end = size;
		backingArray = newArray;
		length = newLength;
	}

	/**
	 * Enqueues a new element as the first element (in dequeue order) of the queue.
	 *
	 * @param x the element to enqueue.
	 */
	public void enqueueFirst(K x) {
		if (contains(x)) {
			return;
		}
		if (start == 0) {
			start = length;
		}
		backingArray[--start] = x;
		present.add(x);
		if (end == start) {
			expand();
		}
	}

	/**
	 *
	 */
	@Override
	public boolean contains(Object o) {
		if (o == null || (start == end)) {
			return false;
		}
		return present.contains(o);
		// return (this.getIndex((K) o) != -1);
	}

	/**
	 * expand the queue
	 */
	private void expand() {
		resize(length, (int) Math.min(it.unimi.dsi.fastutil.Arrays.MAX_ARRAY_SIZE, 2L * length));
	}

	/**
	 *
	 */
	@Override
	public K first() {
		if (start == end) {
			throw new NoSuchElementException();
		}
		return backingArray[start];
	}

	/**
	 *
	 */
	@Override
	public boolean isEmpty() {
		return start == end;
	}

	/**
	 *
	 */
	@SuppressWarnings({"AnonymousInnerClassWithTooManyMethods", "AnonymousInnerClass",
	                   "OverlyComplexAnonymousInnerClass"})
	@Nonnull
	@Override
	public ObjectIterator<K> iterator() {
		return new ObjectIterator<>() {
			final int last = end;
			final int max = length;
			int pos = start;

			@Override
			public K next() {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}
				final K t = backingArray[pos];
				if (++pos == max) {
					pos = 0;
				}
				return t;
			}

			@Override
			public boolean hasNext() {
				return pos != last;
			}
		};
	}

	/**
	 *
	 */
	@Override
	public K last() {
		if (start == end) {
			throw new NoSuchElementException();
		}
		return backingArray[(end == 0 ? length : end) - 1];
	}

	/**
	 *
	 */
	@Override
	public boolean removeAll(Collection<?> c) {
		for (final Object o : c) {
			remove(o);
		}
		return true;
	}

	/**
	 *
	 */
	@Override
	@SuppressWarnings("unchecked")
	public boolean remove(Object o) {
		if (o == null || start == end) {
			return false;
		}
		if (!present.contains(o)) {
			return false;
		}
		final K ok = (K) o;
		int i = getIndex(ok);
		if (i <= end) {
			while (i < end) {
				backingArray[i] = backingArray[++i];
			}
			end--;
		} else {
			while (i > start) {
				backingArray[i] = backingArray[--i];
			}
			start++;
		}
		return true;
	}

	/**
	 * @param k index
	 *
	 * @return the index of k if present, -1 otherwise
	 */
	private int getIndex(K k) {
		int size = size();
		for (int i = start; size-- != 0; ) {
			if (backingArray[i++].equals(k)) {
				return i - 1;
			}
			if (i == length) {
				i = 0;
			}
		}
		return -1;
	}

	/**
	 *
	 */
	@Override
	public boolean retainAll(@Nonnull Collection<?> c) {
		throw new UnsupportedOperationException("retainAll");
	}

	/**
	 *
	 */
	@Nonnull
	@Override
	public K[] toArray() {
		final int size = size();
		@SuppressWarnings("unchecked") final K[] newArray = (K[]) new Object[size];
		return copyArray(newArray);
	}

	/**
	 * @param newArray the given array where to copy the backing array.
	 *
	 * @return the copy of this array.
	 */
	@SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification = "False positive.")
	@Nonnull
	private <T> T[] copyArray(@Nonnull T[] newArray) {
		if (start <= end) {
			System.arraycopy(backingArray, start, newArray, 0, end - start);
		} else {
			System.arraycopy(backingArray, start, newArray, 0, length - start);
			System.arraycopy(backingArray, 0, newArray, length - start, end);
		}
		return newArray;
	}

	/**
	 *
	 */
	@Nonnull
	@SuppressWarnings("unchecked")
	@Override
	public <T> T[] toArray(@Nonnull T[] a) {
		final int size = size();
		if (a.length < size) {
			a = (T[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size);
		} else {
			if (a.length > size) {
				a[size] = null;
			}
		}
		return copyArray(a);
	}

	/**
	 * @return string representation of this object.
	 */
	@SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification = "False positive.")
	@Override
	@Nonnull
	public String toString() {
		int size = size();
		final StringBuilder sb = new StringBuilder("[");
		for (int i = start; size-- != 0; ) {
			sb.append(backingArray[i++].toString());
			sb.append(", ");
			if (i == length) {
				i = 0;
			}
		}
		final int l = sb.length() - 2;
		if (l >= 0) {
			sb.delete(sb.length() - 2, sb.length());
		}
		sb.append("]");
		return sb.toString();
	}

	/**
	 * Trims the queue to the smallest possible size.
	 */
	@SuppressWarnings("unchecked")
	public void trim() {
		final int size = size();
		final K[] newArray = (K[]) new Object[size + 1];
		if (start <= end) {
			System.arraycopy(backingArray, start, newArray, 0, end - start);
		} else {
			System.arraycopy(backingArray, start, newArray, 0, length - start);
			System.arraycopy(backingArray, 0, newArray, length - start, end);
		}
		start = 0;
		length = (end = size) + 1;
		backingArray = newArray;
	}

	/**
	 * @param s input stream
	 *
	 * @throws ClassNotFoundException none
	 * @throws IOException            none
	 */
	@SuppressWarnings("unchecked")
	@SuppressFBWarnings("MC_OVERRIDABLE_METHOD_CALL_IN_READ_OBJECT")//false positive
	@Serial
	private void readObject(java.io.ObjectInputStream s) throws ClassNotFoundException, IOException {
		s.defaultReadObject();
		end = s.readInt();
		backingArray = (K[]) new Object[length = HashCommon.nextPowerOfTwo(end + 1)];
		for (int i = 0; i < end; i++) {
			backingArray[i] = (K) s.readObject();
			present.add(backingArray[i]);
		}
	}

	/**
	 * @param s out stream
	 *
	 * @throws IOException none
	 */
	@SuppressFBWarnings("MC_OVERRIDABLE_METHOD_CALL_IN_READ_OBJECT")//false positive
	@Serial
	private void writeObject(java.io.ObjectOutputStream s) throws IOException {
		s.defaultWriteObject();
		int size = size();
		s.writeInt(size);
		for (int i = start; size-- != 0; ) {
			s.writeObject(backingArray[i++]);
			if (i == length) {
				i = 0;
			}
		}
	}
}
