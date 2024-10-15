// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.visualization;

import com.google.common.base.Supplier;
import edu.uci.ics.jung.algorithms.layout.GraphElementAccessor;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.graph.util.EdgeType;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.jung.visualization.control.EdgeSupport;
import edu.uci.ics.jung.visualization.control.SimpleEdgeSupport;
import edu.uci.ics.jung.visualization.control.SimpleVertexSupport;
import edu.uci.ics.jung.visualization.control.VertexSupport;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Point2D;

/**
 * A plugin that can create vertices, undirected edges, and directed edges using mouse gestures. vertexSupport and
 * edgeSupport member classes are responsible for actually creating the new graph elements, and for repainting the view
 * when changes were made.
 *
 * @param <V> generic type extending for Node
 * @param <E> generic type extending for Edge
 *
 * @author Tom Nelson, Roberto Posenato
 * @version $Rev: 840 $
 */
public class EditingGraphMousePlugin<V, E> extends edu.uci.ics.jung.visualization.control.AbstractGraphMousePlugin
	implements MouseListener, MouseMotionListener {

	private enum Creating {
		EDGE, VERTEX, UNDETERMINED
	}
	/*
	 * logger
	 */
//	static Logger LOG = Logger.getLogger(EditingGraphMousePlugin.class.getName());
	/**
	 * Mask for button
	 */
	static int shiftButtonDownMask = InputEvent.BUTTON1_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
	/**
	 * Helper for creating a node
	 */
	protected VertexSupport<V, E> vertexSupport;
	/**
	 * Helper for creating an edge
	 */
	protected EdgeSupport<V, E> edgeSupport;
	private Creating createMode = Creating.UNDETERMINED;

	/**
	 * Creates an instance and prepares shapes for visual effects, using the default modifiers of designed button
	 * (button 1).
	 *
	 * @param vertexFactory for creating vertices
	 * @param edgeFactory   for creating edges
	 */
	public EditingGraphMousePlugin(Supplier<V> vertexFactory, Supplier<E> edgeFactory) {
		this(shiftButtonDownMask, vertexFactory, edgeFactory);
	}

	/**
	 * Creates an instance and prepares shapes for visual effects.
	 *
	 * @param modifiers1    the mouse event modifiers to use
	 * @param vertexFactory for creating vertices
	 * @param edgeFactory   for creating edges
	 */
	private EditingGraphMousePlugin(int modifiers1, Supplier<V> vertexFactory, Supplier<E> edgeFactory) {
		super(modifiers1);
		cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
		vertexSupport = new SimpleVertexSupport<>(vertexFactory);
		edgeSupport = new SimpleEdgeSupport<>(edgeFactory);
	}

	/**
	 * @return a {@link edu.uci.ics.jung.visualization.control.EdgeSupport} object.
	 */
	public EdgeSupport<V, E> getEdgeSupport() {
		return edgeSupport;
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		// empty block
	}

	/**
	 * @return a {@link edu.uci.ics.jung.visualization.control.VertexSupport} object.
	 */
	public VertexSupport<V, E> getVertexSupport() {
		return vertexSupport;
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		// empty block
	}

	/**
	 * {@inheritDoc} If startVertex is non-null, stretch an edge shape between startVertex and the mouse pointer to
	 * simulate edge creation
	 */
	@Override
	@SuppressWarnings("unchecked")
	public void mouseDragged(MouseEvent e) {
		if (checkModifiers(e)) {
			final VisualizationViewer<V, E> vv = (VisualizationViewer<V, E>) e.getSource();
			if (createMode == Creating.EDGE) {
				edgeSupport.midEdgeCreate(vv, e.getPoint());
			} else if (createMode == Creating.VERTEX) {
				vertexSupport.midVertexCreate(vv, e.getPoint());
			}
		}
	}

	/**
	 * {@inheritDoc} Overridden to be more flexible, and pass events with key combinations. The default responds to both
	 * ButtonOne and ButtonOne+Shift
	 */
	@Override
	public boolean checkModifiers(MouseEvent e) {
		// LOG.severe("checkModifiers: " + e.getModifiersEx() + " this.modifiers: " + this.modifiers);
		return (e.getModifiersEx() & modifiers) == modifiers;
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		final JComponent c = (JComponent) e.getSource();
		c.setCursor(cursor);
	}

	@Override
	public void mouseExited(MouseEvent e) {
		final JComponent c = (JComponent) e.getSource();
		c.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
	}

	/**
	 * {@inheritDoc} If the mouse is pressed in an empty area, create a new vertex there. If the mouse is pressed on an
	 * existing vertex, prepare to create an edge from that vertex to another
	 */
	@Override
	public void mousePressed(MouseEvent e) {
		// LOG.severe("mousePressed: " + e);
		if (checkModifiers(e)) {
			@SuppressWarnings("unchecked") final VisualizationViewer<V, E> vv =
				(VisualizationViewer<V, E>) e.getSource();
			final Point2D p = e.getPoint();
			final GraphElementAccessor<V, E> pickSupport = vv.getPickSupport();
			if (pickSupport != null) {
				final V vertex = pickSupport.getVertex(vv.getModel().getGraphLayout(), p.getX(), p.getY());
				if (vertex != null) { // get ready to make an edge
					createMode = Creating.EDGE;
					// Graph<V,E> graph = vv.getModel().getGraphLayout().getGraph();
					// set default edge type
					final EdgeType edgeType =
						EdgeType.DIRECTED;// (graph instanceof DirectedGraph) ? EdgeType.DIRECTED : EdgeType.UNDIRECTED;
					// if ((e.getModifiers() & InputEvent.SHIFT_MASK) != 0) {// && graph instanceof UndirectedGraph == false) {
					// edgeType = EdgeType.DIRECTED;
					// }
					edgeSupport.startEdgeCreate(vv, vertex, e.getPoint(), edgeType);
				}
			}
		}
	}

	/**
	 * {@inheritDoc} If startVertex is non-null, and the mouse is released over an existing vertex, create an undirected
	 * edge from startVertex to the vertex under the mouse pointer. If shift was also pressed, create a directed edge
	 * instead.
	 */
	@Override
	@SuppressWarnings("unchecked")
	public void mouseReleased(MouseEvent e) {
		// LOG.severe("mouseReleased: " + e);
		// if(checkModifiers(e)) {
		final VisualizationViewer<V, E> vv = (VisualizationViewer<V, E>) e.getSource();
		final Point2D p = e.getPoint();
		final Layout<V, E> layout = vv.getGraphLayout();
		if (createMode == Creating.EDGE) {
			final GraphElementAccessor<V, E> pickSupport = vv.getPickSupport();
			V vertex = null;
			if (pickSupport != null) {
				vertex = pickSupport.getVertex(layout, p.getX(), p.getY());
			}
			edgeSupport.endEdgeCreate(vv, vertex);
		} else if (createMode == Creating.VERTEX) {
			vertexSupport.endVertexCreate(vv, e.getPoint());
		}
		// }
		createMode = Creating.UNDETERMINED;
	}

	/**
	 * setEdgeFactory.
	 *
	 * @param edgeFactory1 a {@link com.google.common.base.Supplier} object.
	 */
	public void setEdgeFactory(Supplier<E> edgeFactory1) {
		edgeSupport = new SimpleEdgeSupport<>(edgeFactory1);
	}

	/**
	 * Setter for the field {@code edgeSupport}.
	 *
	 * @param edgeSupport1 a {@link edu.uci.ics.jung.visualization.control.EdgeSupport} object.
	 */
	public void setEdgeSupport(EdgeSupport<V, E> edgeSupport1) {
		edgeSupport = edgeSupport1;
	}

	/**
	 * Setter for the field {@code vertexSupport}.
	 *
	 * @param vertexSupport1 a {@link edu.uci.ics.jung.visualization.control.VertexSupport} object.
	 */
	public void setVertexSupport(VertexSupport<V, E> vertexSupport1) {
		vertexSupport = vertexSupport1;
	}

}
