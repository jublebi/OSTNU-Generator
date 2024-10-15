// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.visualization;
/*
 * [07/02/2012] Made serializable by Posenato
 */

import com.google.common.base.Function;
import com.google.common.cache.LoadingCache;
import edu.uci.ics.jung.algorithms.util.IterativeContext;
import edu.uci.ics.jung.graph.Graph;
import it.univr.di.cstnu.graph.LabeledNode;

import java.awt.*;
import java.awt.geom.Point2D;

/**
 * Extends CSTNUStaticLayout retrieving initial node positions from node attributes.
 *
 * @param <E> edge type
 *
 * @author posenato
 * @version $Rev: 840 $
 */
public class CSTNUStaticLayout<E> extends edu.uci.ics.jung.algorithms.layout.StaticLayout<LabeledNode, E>
	implements IterativeContext {

	/**
	 * Version
	 */
	// static final public String VERSIONandDATE = "1.0 - October, 20 2017";
	static final public String VERSIONandDATE = "1.1, June, 9 2019";// Refactoring Edge
	/**
	 * It is used for getting the coordinates of node stored inside LabelNode object.
	 */
	static public final Function<LabeledNode, Point2D> positionInitializer = v -> {
		if (v == null) {
			//noinspection ReturnOfNull
			return null;
		}
		return new Point2D.Double(v.getX(), v.getY());
	};

	/**
	 * Creates an instance for the specified graph and default size; vertex locations are determined by
	 * {@link #positionInitializer}.
	 *
	 * @param graph1 a {@link edu.uci.ics.jung.graph.Graph} object.
	 */
	public CSTNUStaticLayout(final Graph<LabeledNode, E> graph1) {
		super(graph1, positionInitializer);
	}

	/**
	 * Creates an instance for the specified graph and size.
	 *
	 * @param graph1 a {@link edu.uci.ics.jung.graph.Graph} object.
	 * @param size1  a {@link java.awt.Dimension} object.
	 */
	public CSTNUStaticLayout(final Graph<LabeledNode, E> graph1, final Dimension size1) {
		super(graph1, positionInitializer, size1);
	}

	@Override
	public boolean done() {
		return true;
	}

	/**
	 * @return the position of all vertices.
	 */
	public LoadingCache<LabeledNode, Point2D> getLocations() {
		return locations;
	}

	/**
	 * {@inheritDoc} It has been erased.
	 */
	@Override
	public void initialize() {
		// empty
	}

	/**
	 * {@inheritDoc} It has been erased.
	 */
	@Override
	public void reset() {
		// empty
	}

	@Override
	public void step() {
	}

	@Override
	public String toString() {
		return CSTNUStaticLayout.VERSIONandDATE;
	}
}
