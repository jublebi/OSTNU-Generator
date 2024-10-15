// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 *
 */
package it.univr.di.labeledvalue.lazy;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.univr.di.labeledvalue.Constants;
import org.apache.commons.math3.fraction.Fraction;

/**
 * Represent a simple value.
 *
 * @author posenato
 * @version $Id: $Id
 */
@SuppressWarnings("ALL")
public final class LazyNumber extends LazyWeight {

	/** Constant <code>LazyNegInfty</code> */
	static public final LazyNumber LazyNegInfty = new LazyNumber(Constants.INT_NEG_INFINITE);

	static private final Int2ObjectOpenHashMap<LazyNumber> cache;

	static {
		cache = new Int2ObjectOpenHashMap<>();
		cache.defaultReturnValue(null);
		cache.put(Constants.INT_NEG_INFINITE, LazyNegInfty);
	}

	/**
	 * <p>
	 * get.
	 * </p>
	 *
	 * @param v a int.
	 * @return a {@link it.univr.di.labeledvalue.lazy.LazyNumber} object.
	 */
	public static LazyNumber get(int v) {
		LazyNumber o = cache.get(v);
		if (o == null) {
			o = new LazyNumber(v);
			cache.put(v, o);
		}
		return o;
	}

	/**
	 *
	 */
	int value;

	private LazyNumber(int v) {
		super(SubType.Number);
		this.value = v;
	}

	/** {@inheritDoc} */
	@Override
	public double getValue() {
		return this.value;
	}

	/** {@inheritDoc} */
	@Override
	public Fraction getX() {
		return new Fraction(this.value);
	}

	/** {@inheritDoc} */
	@Override
	public void setX(Fraction newX) {
		// a LazyNumber is a read-only object!
	}

}
