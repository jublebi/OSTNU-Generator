// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.labeledvalue;

import com.google.common.base.Supplier;

import java.lang.reflect.InvocationTargetException;
import java.util.logging.Logger;

/**
 * Basic factory of LabeledIntMap objects. An implementation C must be provided.
 *
 * @param <C> implementation class of LabeledIntMap interface.
 *
 * @author posenato
 * @version $Rev: 852 $
 */
public final class LabeledIntMapSupplier<C extends LabeledIntMap> implements Supplier<C> {

	/**
	 *
	 */
	static final public Class<? extends LabeledIntMap> DEFAULT_LABELEDINTMAP_CLASS = LabeledIntTreeMap.class;
	/**
	 *
	 */
	static final public Class<? extends LabeledIntMap> SIMPLE_LABELEDINTMAP_CLASS = LabeledIntSimpleMap.class;
	//LabeledIntTreeSimpleMap.class;

	/**
	 * class logger
	 */
	@SuppressWarnings("unused")
	static private final Logger LOG = Logger.getLogger(LabeledIntMapSupplier.class.getCanonicalName());
	/**
	 *
	 */
	private final Class<C> generatorClass;
	/**
	 *
	 */
	private final C generator;

	/**
	 * Constructor for LabeledIntMapSupplier.
	 *
	 * @param implementationClass a {@link java.lang.Class} object.
	 */
	public LabeledIntMapSupplier(Class<C> implementationClass) {
		generatorClass = implementationClass;
		try {
			generator = implementationClass.getDeclaredConstructor().newInstance();
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException |
		         SecurityException e) {
			LOG.severe(e.getMessage());
			throw new IllegalStateException(e.getMessage());
		}
	}

	@Override
	public C get() {
		return generatorClass.cast(generator.newInstance());
	}

	/**
	 * @param optimize false if the labels have not to shorten.
	 *
	 * @return a new instance
	 */
	public C get(boolean optimize) {
		return generatorClass.cast(generator.newInstance(optimize));
	}


	/**
	 * @param lim a {@link it.univr.di.labeledvalue.LabeledIntMap} object.
	 *
	 * @return a new LabeledIntMap concrete object.
	 */
	public C get(LabeledIntMap lim) {
		return generatorClass.cast(generator.newInstance(lim));
	}

	/**
	 * @param lim      a {@link it.univr.di.labeledvalue.LabeledIntMap} object.
	 * @param optimize false if the labels have not to shorten.
	 *
	 * @return a new instance
	 */
	public C get(LabeledIntMap lim, boolean optimize) {
		return generatorClass.cast(generator.newInstance(lim, optimize));
	}

	/**
	 * @return a {@link java.lang.Class} object.
	 */
	public Class<C> getReturnedObjectClass() {
		return generatorClass;
	}

	@Override
	public String toString() {
		return generatorClass.getSimpleName();
	}
}
