package it.univr.di.cstnu.util;

import it.univr.di.labeledvalue.Constants;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents a time interval where lower bound is guarantee to be ≤ upper bound.
 */
public class TimeInterval {
	private int lower;
	private int upper;

	/**
	 * @param l lower time
	 * @param u upper time
	 *
	 * @throws IllegalArgumentException if lower &gt; upper.
	 */
	public TimeInterval(int l, int u) {
		if (l > u) {
			throw new IllegalArgumentException("Lower cannot be greater than upper");
		}
		this.lower = l;
		this.upper = u;
	}

	/**
	 * Fix the lower bound to l, and the upper bound to +∞.
	 *
	 * @param l the lower bound
	 */
	public TimeInterval(int l) {
		lower = l;
		upper = Constants.INT_POS_INFINITE;
	}

	/**
	 * Constructor by copy
	 *
	 * @param ti time interval to copy
	 */
	public TimeInterval(@Nonnull TimeInterval ti) {
		lower = ti.lower;
		upper = ti.upper;
	}

	/**
	 * @return the current lower.
	 */
	public int getLower() {
		return lower;
	}

	/**
	 * @return the current upper.
	 */
	public int getUpper() {
		return upper;
	}

	/**
	 * @param l lower bound
	 * @param u upper bound
	 *
	 * @return the intersect interval, if it exists, null otherwise.
	 */
	@Nullable
	public TimeInterval intersect(int l, int u) {
		final int lb = Math.max(this.lower, l);
		final int ub = Math.min(this.upper, u);
		if (lb > ub) {
			return null;
		}
		return new TimeInterval(lb, ub);
	}

	/**
	 * @param i a not null interval
	 *
	 * @return the intersect interval, if it exists, null otherwise.
	 */
	@Nullable
	public TimeInterval intersect(@Nonnull TimeInterval i) {
		return intersect(i.lower, i.upper);
	}

	/**
	 * Updates the bounds.
	 *
	 * @param l the new lower.
	 * @param u the new upper.
	 *
	 * @return true if the interval was updated, false otherwise.
	 *
	 * @throws IllegalArgumentException if lower is greater than upper.
	 */
	public boolean set(int l, int u) {
		if (l > u) {
			throw new IllegalArgumentException("Lower " + Constants.formatInt(l) + " cannot be greater than upper " + Constants.formatInt(u));
		}
		final boolean update = this.lower != l || this.upper != u;
		this.lower = l;
		this.upper = u;
		return update;
	}

	/**
	 * @return a string representing the interval
	 */
	public String toString() {
		return "[%s, %s]".formatted(Constants.formatInt(lower), Constants.formatInt(upper));
	}
}
