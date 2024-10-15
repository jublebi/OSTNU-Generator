package it.univr.di.cstnu.util;

import it.univr.di.cstnu.graph.LabeledNode;
import it.univr.di.labeledvalue.Constants;

import javax.annotation.Nonnull;

/**
 * Active wait of a timepoint.<br> Scope of this class is to maintain the list of active waits for a node allowing a
 * fast determination of the greatest wait and fast removing of useless waits.<br> For these reasons, a max priority
 * queue with key the wait value and value the associated contingent node is used.<br> Since the library has a min
 * priority queue implementation, I reuse it making it private and giving all the proper methods for manipulating it as
 * max priority queue.
 * <b>Assumption</b>
 * The wait values are positive integer because it is supposed to manage waits at runtime.
 */
public class ActiveWaits {
	/**
	 * (delay-->contingent node)
	 */
	private final ExtendedPriorityQueue<LabeledNode> wait = new ExtendedPriorityQueue<>(true);

	/**
	 * Add an active wait represented as (Contingent, positive value) to the queue.
	 *
	 * @param contingent the contingent node.
	 * @param value      the positive value representing the wait.
	 */
	public void addWait(LabeledNode contingent, int value) {
		if (value < 0) {
			throw new IllegalArgumentException("Only positive values are admitted.");
		}
		wait.insertOrUpdate(contingent, value);
	}


	/**
	 * @return the maximum wait value presented in the queue if the queue not empty, {@link Constants#INT_NULL}
	 * 	otherwise.
	 */
	public int getMaximum() {
		if (wait.isEmpty()) {
			return Constants.INT_NULL;
		}
		assert wait.getFirstEntry() != null;
		return wait.getFirstEntry().getKey();
	}

	/**
	 * Remove the wait associated to contingent from the queue.
	 *
	 * @param contingent node to remove
	 *
	 * @return true if the wait associated to the contingent node was removed.
	 */
	public boolean remove(@Nonnull LabeledNode contingent) {
		if (wait.getStatus(contingent) != ExtendedPriorityQueue.Status.isPresent) {
			return false;
		}
		wait.delete(contingent);
		return true;
	}

	/**
	 * @return the number of active waits
	 */
	public int size() {
		return this.wait.size();
	}

	/**
	 * @return the string representation. Beware that values are negated in this representation.
	 */
	public String toString() {
		return wait.toString();
	}
}
