// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.visualization;

import com.google.common.base.Supplier;
import edu.uci.ics.jung.algorithms.layout.GraphElementAccessor;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.util.EdgeType;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.jung.visualization.control.AbstractPopupGraphMousePlugin;
import edu.uci.ics.jung.visualization.picking.PickedState;
import it.univr.di.cstnu.graph.Edge;
import it.univr.di.cstnu.graph.LabeledNode;
import it.univr.di.cstnu.graph.TNGraph;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 2017-10-23 I added a menu item to manage the export of a graph.
 *
 * @param <V> generic type extending for Node
 * @param <E> generic type extending for Edge
 *
 * @author Roberto Posenato
 * @version $Rev: 840 $
 */
@SuppressWarnings("AnonymousInnerClass")
public class EditingPopupGraphMousePlugin<V extends LabeledNode, E extends Edge> extends AbstractPopupGraphMousePlugin {
	/**
	 * logger
	 */
	static final Logger LOG = Logger.getLogger(EditingPopupGraphMousePlugin.class.getName());

	/**
	 *
	 */
	Supplier<V> vertexFactory;
	/**
	 *
	 */
	Supplier<E> edgeFactory;

	/**
	 * @param vertexFactory1 a {@link com.google.common.base.Supplier} object.
	 * @param edgeFactory1   a {@link com.google.common.base.Supplier} object.
	 */
	public EditingPopupGraphMousePlugin(Supplier<V> vertexFactory1, Supplier<E> edgeFactory1) {
		vertexFactory = vertexFactory1;
		edgeFactory = edgeFactory1;
	}

	/**
	 * @param edgeFactory1 a {@link com.google.common.base.Supplier} object.
	 */
	public void setEdgeFactory(Supplier<E> edgeFactory1) {
		edgeFactory = edgeFactory1;
	}

	@Override
	@SuppressWarnings("unchecked")
	protected void handlePopup(MouseEvent e) {
		final VisualizationViewer<V, E> vv = (VisualizationViewer<V, E>) e.getSource();
		final Layout<V, E> layout = vv.getGraphLayout();
		final Graph<V, E> graph = layout.getGraph();
		final Point2D p = e.getPoint();

		final GraphElementAccessor<V, E> pickSupport = vv.getPickSupport();
		if (pickSupport != null) {

			final V vertex = pickSupport.getVertex(layout, p.getX(), p.getY());
			final E edge = pickSupport.getEdge(layout, p.getX(), p.getY());
			final PickedState<V> pickedVertexState = vv.getPickedVertexState();
			final PickedState<E> pickedEdgeState = vv.getPickedEdgeState();

			final JPopupMenu popup = new JPopupMenu();
			if (vertex != null) {
				final Set<V> picked = pickedVertexState.getPicked();
				if (!picked.isEmpty()) {
					final JMenu directedMenu = new JMenu("Add edge");
					popup.add(directedMenu);
					for (final V other : picked) {
						if (other.equalsByName(vertex)) {
							continue;
						}
						directedMenu.add(getEdgeAction(vertex, other, graph, vv));
						directedMenu.add(getEdgeAction(other, vertex, graph, vv));
					}

					popup.add(new AbstractAction("Delete all nodes") {
						@Override
						public void actionPerformed(ActionEvent a) {
							final Iterable<V> toRemove = new ArrayList<>(picked);
							for (final V other : toRemove) {
								pickedVertexState.pick(other, false);
								graph.removeVertex(other);
							}
							vv.repaint();
						}
					});
				}

				popup.add(new AbstractAction("Delete node") {
					@Override
					public void actionPerformed(ActionEvent a) {
						pickedVertexState.pick(vertex, false);
						graph.removeVertex(vertex);
						vv.repaint();
					}
				});
			} else if (edge != null) {
				popup.add(new AbstractAction("Delete edge") {
					@Override
					public void actionPerformed(ActionEvent a) {
						pickedEdgeState.pick(edge, false);
						graph.removeEdge(edge);
						vv.repaint();
					}
				});
			} else {
				popup.add(new AbstractAction("Add node") {
					@Override
					public void actionPerformed(ActionEvent a) {
						final V newVertex = vertexFactory.get();
						graph.addVertex(newVertex);
						layout.setLocation(newVertex,
						                   vv.getRenderContext().getMultiLayerTransformer().inverseTransform(p));
						vv.repaint();
					}
				});
				// Unfortunately, FREEHEP 2.4 library is not more JDK11 compatible... I have to disable till a new version or workaround is found!
				// popup.add(new AbstractAction("Export graph to vector image...") {
				// @Override
				// public void actionPerformed(ActionEvent a) {
				// ExportDialog export = new ExportDialog("Roberto Posenato");
				// VisualizationImageServer<V, E> vis = new VisualizationImageServer<>(vv.getGraphLayout(), vv.getGraphLayout().getSize());
				// TNEditor.setNodeEdgeRenders((BasicVisualizationServer<LabeledNode, Edge>) vis, false);
				// export.showExportDialog(vv.getParent(), "Export view as ...", vis, "cstnExported.pdf");
				// }
				// });
			}
			if (popup.getComponentCount() > 0) {
				popup.show(vv, e.getX(), e.getY());
			}
		}
	}

	/**
	 * Checks if the edge between source and destination does not exist. If it exists, returns; otherwise, it adds one
	 * with a name derived by the names of nodes.
	 *
	 * @param source      source node
	 * @param destination destination node
	 * @param graph       the current network
	 * @param vv          the viewer
	 *
	 * @return the abstract action for adding an edge.
	 */
	private AbstractAction getEdgeAction(V source, V destination, Graph<V, E> graph, VisualizationViewer<V, E> vv) {
		return (new AbstractAction("[" + source + "→" + destination + "]") {
			@Serial
			private static final long serialVersionUID = -803364237059005270L;

			@SuppressWarnings("unchecked")
			@Override
			public void actionPerformed(ActionEvent e1) {
				E newEdge;
				if ((newEdge = graph.findEdge(source, destination)) != null) {
					LOG.warning(
						"Edge between " + source.getName() + " and " + destination.getName() + " already exists: "
						+ newEdge + ". A new one is NOT added.");
					return;
				}
				newEdge = edgeFactory.get();
				// make sure that the name is unique
				final StringBuilder eName = new StringBuilder("e" + source.getName() + "-" + destination.getName());
				final TNGraph<E> g1 = (TNGraph<E>) graph;
				while (g1.getEdge(eName.toString()) != null) {
					eName.append("_");
				}
				newEdge.setName(eName.toString());
				graph.addEdge(newEdge, source, destination, EdgeType.DIRECTED);
				vv.repaint();
			}
		});
	}
}
