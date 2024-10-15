// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.util;

import it.unimi.dsi.fastutil.objects.AbstractObject2IntMap.BasicEntry;
import it.unimi.dsi.fastutil.objects.*;
import it.univr.di.labeledvalue.Constants;
import org.jheaps.AddressableHeap;
import org.jheaps.array.BinaryArrayAddressableHeap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Comparator;


/**
 * Simple implementation priority queue where elements are T objects and priorities are integers.
 * <p>It is based on <a href="https://www.jheaps.org/">BinaryArrayAddressableHeap</a> and it maintains memory of
 * elements that have been inserted and removed (see {@link #getStatus(Object)}).<br>
 * In the constructor it is possible to decide if the queue must be a max priority or a min priority queue.
 *
 * @param <T> type of object in the queue.
 * @author posenato
 * @version $Rev: 732 $
 */
public class ExtendedPriorityQueue<T> {

	/**
	 * The possible state of an element with respect to a {@link ExtendedPriorityQueue}.
	 *
	 * @author posenato
	 */
	public enum Status {
		/**
		 * the element is currently in the queue.
		 */
		isPresent,
		/**
		 * the element has never been added in the queue.
		 */
		neverPresent,
		/**
		 * the element has been added and removed from this queue.
		 */
		wasPresent
	}

	/**
	 * Comparator for maximum priority queue
	 */
	private final static Comparator<? super Integer> MAX_COMPARATOR = Comparator.reverseOrder();
	/**
	 * Comparator for min priority queue
	 */
	private final static Comparator<? super Integer> MIN_COMPARATOR = Integer::compareTo;

	/*
	 * logger
	 *
	static Logger LOG = Logger.getLogger(ExtendedPriorityQueue.class.getName());
	*/
	/**
	 * The heap organized as binary heap.
	 */
	private final BinaryArrayAddressableHeap<Integer, T> heap;

	/**
	 * For each object of type T, it returns the heap entry associated to the object, i.e., T-->(int, node) If the
	 * returned entry (int, o) has o == null, it means that o was in the queue, and it was removed.
	 */
	private final Object2ObjectMap<T, AddressableHeap.Handle<Integer, T>> map;

	/**
	 * Default constructor for minimum priority queue based on binary heap.
	 */
	public ExtendedPriorityQueue() {
		this(false);
	}

	/**
	 * General constructor for min/max priority queue based on binary heap.
	 *
	 * @param maximum true if it must be a maximum priority queue, false if it must be a minimum priority queue.
	 */
	ExtendedPriorityQueue(boolean maximum) {
		if (maximum) {
			heap = new BinaryArrayAddressableHeap<>(MAX_COMPARATOR);
		} else {
			heap = new BinaryArrayAddressableHeap<>(MIN_COMPARATOR);
		}
		map = new Object2ObjectOpenHashMap<>();
	}

	/**
	 * Makes the queue empty.
	 */
	public final void clear() {
		heap.clear();
		map.clear();
	}

	/**
	 * Removes the element if it is present in the queue.<br> It runs in O(log n) time.
	 *
	 * @param element element to remove
	 */
	public void delete(T element) {
		if (element == null) {
			return;
		}
		final AddressableHeap.Handle<Integer, T> entry = map.get(element);
		if (entry == null || getEntryStatus(entry) != Status.isPresent) {
			return;
		}
		entry.delete();
		setWasPresent(entry);
	}

	/**
	 * Removes and returns the item with minimum (maximum) priority in this heap.<br>
	 * It runs in O(1) time.
	 *
	 * @return the T with minimum priority.
	 */
	public T extractFirst() {
		final AddressableHeap.Handle<Integer, T> entry = heap.deleteMin();
		final T min = entry.getValue();
		setWasPresent(entry);
		return min;
	}

	/**
	 * Removes and returns the first entry in this heap.<br>
	 * It runs in O(1) time.
	 *
	 * @return the entry &lt;T, int&gt; with minimum priority.
	 */
	public BasicEntry<T> extractFirstEntry() {
		final AddressableHeap.Handle<Integer, T> entry = heap.deleteMin();
		final BasicEntry<T> basicEntry = new BasicEntry<>(entry.getValue(), entry.getKey());
		setWasPresent(entry);
		return basicEntry;
	}

	/**
	 * @return the map (T, priority) of all the elements that are/have been in the queue.<br>
	 * It runs in O(n) time.
	 */
	public Object2IntMap<T> getAllDeterminedPriorities() {
		final Object2IntMap<T> priority = new Object2IntOpenHashMap<>();
		for (final T item : map.keySet()) {
			final AddressableHeap.Handle<Integer, T> entry = map.get(item);
			priority.put(item, entry.getKey().intValue());
		}
		return priority;
	}

	/**
	 * It runs in O(n) time.
	 *
	 * @return the element present in the queue (without priority). Empty list if there is no element.
	 */
	public ObjectList<T> getElements() {
		final ObjectArrayList<T> list = new ObjectArrayList<>((int) heap.size());
		heap.handlesIterator().forEachRemaining((item) -> list.add(item.getValue()));
		return list;
	}

	/**
	 * It runs in O(1) time.
	 *
	 * @return the min first entry of the queue without modifying it. If the queue is empty, it returns null.
	 */
	@Nullable
	public AddressableHeap.Handle<Integer, T> getFirstEntry() {
		if (heap.isEmpty()) {
			return null;
		}
		return heap.findMin();
	}

	/**
	 * @return the first priority
	 */
	@Nullable
	public Integer getFirstPriority() {
		if (heap.isEmpty()) {
			return null;
		}
		return heap.findMin().getKey();
	}

	/**
	 * Returns the priority of item if it is or was present in the queue, {@link Constants#INT_POS_INFINITE} if the item was
	 * never inserted and this is a min queue, {@link Constants#INT_NEG_INFINITE} if the item was
	 * never inserted and this is a max queue.<br>
	 * It runs in O(alpha) time because the element is searched in a companion hash table.
	 *
	 * @param item the search element.
	 * @return the priority of item.
	 */
	public int getPriority(T item) {
		final AddressableHeap.Handle<Integer, T> entry = map.get(item);
		if (entry == null) {
			return (heap.comparator() == MIN_COMPARATOR) ? Constants.INT_POS_INFINITE : Constants.INT_NEG_INFINITE;
		}
		return entry.getKey();
	}

	/**
	 * Usually, in a priority queue an object is firstly added and, possibly, removed.<br>
	 * This class remembers all objects that have been added in the queue.<br>
	 * Therefore, this method returns the possible state of an element (see {@link Status}).<br>
	 * It runs in O(alpha) time because the element is searched in a companion hash table.
	 *
	 * @param obj an object.
	 * @return a {@link ExtendedPriorityQueue.Status} object.
	 * If obj is null or the queue is empty, it returns {@link ExtendedPriorityQueue.Status#neverPresent}.
	 */
	public Status getStatus(T obj) {
		if (obj == null) {
			return Status.neverPresent;
		}
		final AddressableHeap.Handle<Integer, T> entry = map.get(obj);
		return getEntryStatus(entry);
	}

	/**
	 * Inserts or updates the given item and its priority into this heap.<br>
	 * The update is performed only when the new value is lower/greater than the present one
	 * according to the min/max type of the queue.<br>
	 * If the item was present (i.e., already extracted), the method does nothing.<br>
	 * It runs in O(log n) time.
	 *
	 * @param item     an object.
	 * @param priority the value of priority
	 * @return true if item was inserted or updated or was present with a value smaller/greater than the given one
	 *          according to the min/max type of the queue;
	 *         false if the item was present and extracted with a priority greater/smaller than the given one according
	 *         to the min/max type of the queue.
	 * @throws IllegalArgumentException is item is null.
	 */
	public boolean insertOrUpdate(@Nonnull T item, int priority) {
		AddressableHeap.Handle<Integer, T> entry = map.get(item);
		final Status status = getEntryStatus(entry);
		switch (status) {
			case isPresent:
				final int cmp = heap.comparator().compare(entry.getKey(), priority);
				if (cmp == 0) {
					return true;
				}
				if (cmp > 0) {
					entry.decreaseKey(priority);
				}
				break;
			case wasPresent:
				if (heap.comparator().compare(entry.getKey(), priority) > 0) {
					return false;
				}
				break;
			case neverPresent:
			default:
				entry = heap.insert(priority, item);
				map.put(item, entry);
				break;
		}
		return true;
	}

	/**
	 * It runs in O(1) time.
	 *
	 * @return true if the queue is empty, false otherwise.
	 */
	public final boolean isEmpty() {
		return heap.isEmpty();
	}

	/**
	 * It runs in O(1) time.
	 *
	 * @return the number of elements in the queue.
	 */
	public final int size() {
		return (int) heap.size();
	}

	/**
	 * @return a string representing the queue. Be aware that the queue is based on a binary heap, so only the first
	 * 	element is guaranteed to be the minimum/maximum w.r.t. the other.
	 */
	@Override
	public String toString() {
		if (this.isEmpty()) {
			return "[]";
		}
		final StringBuilder sb = new StringBuilder("[");
		heap.handlesIterator().forEachRemaining((item) -> sb.append(handleToString(item)).append(", "));
		//last two chars are ' ,'
		sb.replace(sb.length() - 2, sb.length(), "]");
		return sb.toString();
	}

	/**
	 * @param entry the entry to check
	 *
	 * @return the status of the object
	 */
	private Status getEntryStatus(AddressableHeap.Handle<Integer, T> entry) {
		if (entry == null) {
			return Status.neverPresent;
		}
		if (entry.getValue() == null) {
			return Status.wasPresent;
		}
		return Status.isPresent;
	}

	/**
	 * @param item handle to convert
	 *
	 * @return the string 'v->i' where v is the value and i the item of the handle, 'null' if the handle is null.
	 */
	private String handleToString(AddressableHeap.Handle<Integer, T> item) {
		if (item == null) {
			return "null";
		}
		return item.getKey().toString() + "->" + item.getValue().toString();
	}

	/**
	 * @param entry deleted.
	 */
	private void setWasPresent(AddressableHeap.Handle<Integer, T> entry) {
		final T o = entry.getValue();
		entry.setValue(null);
		map.put(o, entry);
	}
}
