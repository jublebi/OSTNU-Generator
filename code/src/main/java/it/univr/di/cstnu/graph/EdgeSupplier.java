// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.graph;

import com.google.common.base.Supplier;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;

/**
 * This supplier <b>requires</b> as E a class that implements {@link it.univr.di.cstnu.graph.Edge} interface and that
 * contains the following 3 constructors:
 * <ol>
 * <li>E(Class&lt;? extends LabeledIntMap&gt;)
 * <li>E(E, Class&lt;? extends LabeledIntMap&gt;)
 * <li>E(String, Class&lt;? extends LabeledIntMap&gt;)
 * </ol>
 *
 * @param <E> type of edge
 *
 * @author posenato
 * @version $Rev: 851 $
 */
public class EdgeSupplier<E extends Edge> implements Supplier<E> {

	/**
	 *
	 */
	static final public Class<? extends STNEdge> DEFAULT_STN_EDGE_CLASS = STNEdgeInt.class;
	/**
	 *
	 */
	static final public Class<? extends STNUEdge> DEFAULT_STNU_EDGE_CLASS = STNUEdgeInt.class;
	/**
	 *
	 */
	static final public Class<? extends CSTNEdge> DEFAULT_CSTN_EDGE_CLASS = CSTNEdgePluggable.class;
	/**
	 *
	 */
	static final public Class<? extends CSTNUEdge> DEFAULT_CSTNU_EDGE_CLASS = CSTNUEdgePluggable.class;
	/**
	 *
	 */
	static final public Class<? extends OSTNUEdgePluggable> DEFAULT_OSTNU_EDGE_CLASS = OSTNUEdgePluggable.class;
	/**
	 *
	 */
	static final public Class<? extends CSTNPSUEdge> DEFAULT_CSTNPSU_EDGE_CLASS = CSTNPSUEdgePluggable.class;
	/*
	 *
	 */
	// private Class<? extends LabeledIntMap> labeledIntValueMapImpl;
	/**
	 *
	 */
	private final E generator;

	/**
	 *
	 */
	private final Class<? extends E> generatorClass;

	/**
	 * @param defaultStnEdgeClass vg
	 */
	public EdgeSupplier(
		Class<? extends E> defaultStnEdgeClass) {// , Class<? extends LabeledIntMap> labeledIntMapImplClass
		generatorClass = defaultStnEdgeClass;
		// this.labeledIntValueMapImpl = labeledIntMapImplClass;
		try {
			// if (labeledIntMapImplClass != null) {
			// this.generator = edgeImplClass.getDeclaredConstructor(new Class[] { this.labeledIntValueMapImpl.getClass() })
			// .newInstance(new Object[] { this.labeledIntValueMapImpl });
			// } else {
			generator = defaultStnEdgeClass.getDeclaredConstructor((Class<?>[]) null).newInstance((Object[]) null);
			// }
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException |
		         InvocationTargetException | NoSuchMethodException | SecurityException e) {
			throw new IllegalStateException("Problem constructing an edge supplier: " + e.getMessage());
		}
	}

	/**
	 * @param <K>       type of edge
	 * @param edgeClass dd
	 * @param n         dimension
	 *
	 * @return a new LabeledIntEdge array of size n.
	 */
	@SuppressWarnings("unchecked")
	public static <K extends Edge> K[] get(Class<K> edgeClass, int n) {
		return (K[]) Array.newInstance(edgeClass, n);
	}

	/**
	 *
	 */
	@Override
	public E get() {
		return generatorClass.cast(generator.newInstance());// this.labeledIntValueMapImpl
	}

	/**
	 * @param edge the edge to clone.
	 *
	 * @return a new edge
	 */
	public E get(Edge edge) {
		return generatorClass.cast(generator.newInstance(edge));// , this.labeledIntValueMapImpl
	}

	/**
	 * @param name a name for the new edge
	 *
	 * @return a new edge
	 */
	public E get(String name) {
		return generatorClass.cast(generator.newInstance(name));// , this.labeledIntValueMapImpl
	}

	/*
	 * @return the class chosen for creating new labeled value map.
	 *         public Class<? extends LabeledIntMap> getLabeledIntValueMapImplClass() {
	 *         return this.labeledIntValueMapImpl;
	 *         }
	 */

	/**
	 * @return the class chosen for creating new edge.
	 */
	public Class<? extends E> getEdgeImplClass() {
		return generatorClass;
	}

	/**
	 *
	 */
	@Override
	public String toString() {
		return "Edge type: " + generatorClass.toString();
		// + ". Labeled Value Set type: " + this.labeledIntValueMapImpl.toString();
	}
}
