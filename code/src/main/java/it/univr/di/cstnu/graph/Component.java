// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.graph;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.HashMap;

/**
 * Component interface.
 *
 * @author posenato
 * @version $Rev: 840 $
 */
public interface Component extends Serializable, Comparable<Object> {

	/**
	 * Clear all component but name.
	 */
	void clear();

	/**
	 * A component is assumed to be equal to another if it has the same name.
	 *
	 * @param c a  object.
	 * @return true if this component has the same name of c.
	 * @see #equals(Object)
	 */
	boolean equalsByName(Component c);

	/**
	 * @return the name of the component
	 */
	@Nonnull
	String getName();

	/**
	 * @see #equals(Object)
	 */
	@Override
	int hashCode();

	/**
	 * In general, we assume that a component is equal to another if it has the same name and that a user can modify a
	 * name even after the creation of the component. Clearly, if there is a set of components, it is responsibility of
	 * the user/software to allow a change only if there is no conflict with the names of other components.
	 * <br>
	 * On the other hand, we assume also that a component can be memorized in a structure like {@link HashMap}, where
	 * {@link #hashCode()} is used for addressing elements. {@link #hashCode()} needs to identify an object even after
	 * its renaming, and it must be coherent with. Therefore, this method and {@link #hashCode()} must not be
	 * modified.
	 */
	@Deprecated
	boolean equals(Object o);

	/**
	 * Return a string representation of labeled values.
	 */
	@Override
	@Nonnull
	String toString();

	/**
	 * @return true if it is in a negative cycle.
	 */
	boolean inNegativeCycle();

	/**
	 * Sets true if the edge is in a negative cycle
	 *
	 * @param inNegativeCycle the boolean status
	 */
	void setInNegativeCycle(boolean inNegativeCycle);

	/**
	 * Set the name of the component. Cannot be null or empty.
	 *
	 * @param name the not-null not-empty new name
	 * @return the old name
	 */
	@Nonnull
	String setName(@Nonnull String name);
}
