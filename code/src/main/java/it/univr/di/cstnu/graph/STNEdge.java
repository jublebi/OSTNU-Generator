// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.graph;

import it.univr.di.labeledvalue.Constants;

/**
 * Represents the behavior of an STN edge.
 *
 * @author posenato
 * @version $Rev: 787 $
 */
public interface STNEdge extends Edge {

	/**
	 * @return the weight associated to the edge. If the weight was not set, it returns
	 * {@link it.univr.di.labeledvalue.Constants#INT_NULL}.
	 */
	int getValue();

	/**
	 * Sets the weight to w.
	 *
	 * @param w the new weight value
	 * @return the old weight associated to the edge. If the weight was not set, it returns
	 * {@link it.univr.di.labeledvalue.Constants#INT_NULL}.
	 */
	int setValue(int w);

	/**
	 * Sets the ordinary value if v is smaller than the current one.
	 *
	 * @param v the new weight value
	 * @return true if the update occurred, false otherwise;
	 */
	default boolean updateValue(int v) {
		final int oldV = this.getValue();
		if (oldV == Constants.INT_NULL || v < oldV) {
			this.setValue(v);
			return true;
		}
		return false;
	}

}
