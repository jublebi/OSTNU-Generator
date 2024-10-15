// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

package it.univr.di.cstnu.graph;

import javax.annotation.Nonnull;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.Serial;

/**
 * Abstract component as base class for nodes and edges.
 *
 * @author posenato
 * @version $Rev: 886 $
 */
@SuppressWarnings("ClassWithoutLogger")
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "ST", justification = "It is not relevant here!")
public abstract class AbstractComponent implements Component {

	/**
	 * 2L: removed color attribute.
	 */
	@Serial
	private static final long serialVersionUID = 2L;
	/**
	 * To provide a unique id for the default creation of component.
	 */
	static private int idSeq;
	/**
	 * Possible name
	 */
	String name;
	/**
	 * Both for nodes and edges, some algorithms can set this variable true if the component is in a negative cycle.
	 */
	private boolean inNegativeCycle;
	/**
	 * Since Java 9, Observable is no more supported. I decided to replace Observable using java.bean.
	 * PropertyChangeSupport allows to register listener indexed by a key (string) that represents the property. Then,
	 * when a property is changed, it is sufficient to call {@code pcs.firePropertyChange("theProperty", old, val);} A
	 * listener l of a property X must be registered as {@code addObserver("X",l)} A listener must be implement
	 * {@link PropertyChangeListener}.
	 */
	PropertyChangeSupport pcs;

	/**
	 * Minimal constructor. the name will be 'c&lt;id&gt;'.
	 */
	protected AbstractComponent() {
		this("");
	}

	/**
	 * Creates a component with name {@code n} if not null, otherwise with a unique name "c&lt;int&gt;".
	 *
	 * @param n the name for the component.
	 */
	protected AbstractComponent(String n) {
		name = ((n == null) || (n.isEmpty())) ? "c%d".formatted(idSeq++) : n;
		pcs = new PropertyChangeSupport(this);
		inNegativeCycle = false;
	}

	/**
	 * Constructor to clone the component.
	 *
	 * @param c the component to clone.
	 */
	protected AbstractComponent(Component c) {
		pcs = new PropertyChangeSupport(this);
		if (c == null) {
			name = "";
			return;
		}
		name = c.getName();
		inNegativeCycle = c.inNegativeCycle();
	}

	/**
	 * An observer for the property.
	 *
	 * @param propertyName a {@link java.lang.String} object.
	 * @param l            a {@link java.beans.PropertyChangeListener} object.
	 */
	public void addObserver(String propertyName, PropertyChangeListener l) {
		pcs.addPropertyChangeListener(propertyName, l);
	}

	/**
	 *
	 */
	@Override
	public void clear() {
		inNegativeCycle = false;
	}

	/**
	 *
	 */
	@Override
	public int compareTo(@Nonnull Object o) {
		if (this == o) {
			return 0;
		}
		return name.compareToIgnoreCase(((Component) o).getName());
	}

	/**
	 *
	 */
	@Override
	public boolean equalsByName(Component c) {
		if (c == null) {
			return false;
		}
		return name.equals(c.getName());
	}

	/**
	 *
	 */
	@Override
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "NP", justification = "It is a false positive!")
	public @Nonnull String getName() {
		return name;
	}

	/**
	 *
	 */
	@Override
	public int hashCode() {
		//It must be final. See Component#equals()
		return super.hashCode();
	}

	/**
	 * @return true if it is in a negative cycle.
	 */
	@Override
	public boolean inNegativeCycle() {
		return inNegativeCycle;
	}

	/**
	 * Removes a specific listener
	 *
	 * @param propertyName a {@link java.lang.String} object.
	 * @param listener     a {@link java.beans.PropertyChangeListener} object.
	 */
	public void removeObserver(String propertyName, PropertyChangeListener listener) {
		pcs.removePropertyChangeListener(propertyName, listener);
	}

	/**
	 * Don't use this method for checking if the component is value equal. Use {@link #equalsByName(Component)}
	 */
	@Override
	@Deprecated
	public boolean equals(Object o) {
		return super.equals(o);
	}

	/**
	 * Sets true if the edge is in a negative cycle
	 *
	 * @param isInNegativeCycle the boolean status
	 */
	@Override
	public void setInNegativeCycle(boolean isInNegativeCycle) {
		inNegativeCycle = isInNegativeCycle;
	}

	/**
	 * @param c a {@link it.univr.di.cstnu.graph.Component} object.
	 */
	public void takeIn(Component c) {
		setName(c.getName());
	}

	/**
	 * Sets the name of the component.
	 *
	 * @param newName cannot empty. If empty, it does nothing.
	 *
	 * @return the old name.
	 */
	//put firePropertyChange in the overriding method!
	@Override
	@Nonnull
	abstract public String setName(@Nonnull String newName);

	/**
	 * Return a string representation of labeled values.
	 */
	@Override
	public String toString() {
		return "〖%s〗".formatted(((name == null || name.isEmpty()) ? "<empty>" : name));
	}
}
