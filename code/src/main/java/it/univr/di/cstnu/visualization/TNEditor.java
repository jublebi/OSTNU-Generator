// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.visualization;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import edu.uci.ics.jung.algorithms.layout.AbstractLayout;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.visualization.BasicVisualizationServer;
import edu.uci.ics.jung.visualization.GraphZoomScrollPane;
import edu.uci.ics.jung.visualization.RenderContext;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.jung.visualization.control.ModalGraphMouse;
import edu.uci.ics.jung.visualization.control.ModalGraphMouse.Mode;
import edu.uci.ics.jung.visualization.decorators.ConstantDirectionalEdgeValueTransformer;
import edu.uci.ics.jung.visualization.renderers.DefaultEdgeLabelRenderer;
import edu.uci.ics.jung.visualization.renderers.Renderer.VertexLabel.Position;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.univr.di.Debug;
import it.univr.di.cstnu.algorithms.AbstractCSTN.CSTNCheckStatus;
import it.univr.di.cstnu.algorithms.AbstractCSTN.CheckAlgorithm;
import it.univr.di.cstnu.algorithms.AbstractCSTN.DCSemantics;
import it.univr.di.cstnu.algorithms.AbstractCSTN.EdgesToCheck;
import it.univr.di.cstnu.algorithms.*;
import it.univr.di.cstnu.algorithms.CSTNPSU.PrototypalLink;
import it.univr.di.cstnu.algorithms.CSTNU.CSTNUCheckStatus;
import it.univr.di.cstnu.algorithms.OSTNU.OSTNUCheckStatus;
import it.univr.di.cstnu.algorithms.STN.STNCheckStatus;
import it.univr.di.cstnu.algorithms.STNU.STNUCheckStatus;
import it.univr.di.cstnu.graph.*;
import it.univr.di.cstnu.graph.TNGraph.NetworkType;
import it.univr.di.labeledvalue.Constants;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.netbeans.validation.api.builtin.stringvalidation.StringValidators;
import org.netbeans.validation.api.ui.ValidationGroup;
import org.netbeans.validation.api.ui.swing.ValidationPanel;
import org.xml.sax.SAXException;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.metal.MetalMenuBarUI;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.net.URL;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple graphical application for creating/loading/modifying/saving/checking CSTNs. It is based on EdgePluggable.
 *
 * @author posenato
 * @version $Rev: 898 $
 */
@SuppressWarnings("AnonymousInnerClass")
@SuppressFBWarnings(value = "SE_BAD_FIELD", justification = "I don't make everything serializable.")
public class TNEditor extends JFrame {

	/**
	 * @author posenato
	 */
	private static class HelpListener implements ActionListener {
		enum AvailableHelp {
			CSTNHelp, CSTNUHelp, CSTNPSUHelp, GenericHelp, STNHelp, STNUHelp, OSTNUHelp,
		}

		private static final String cstnHelp = """
		                                       <html>
		                                       <h2>Conditional Simple Temporal Network</h2>
		                                       <h4>Checking algorithm</h4>
		                                       <p>Select which algorithm will be used for checking the consistency.<br>
		                                       Hunsberger-Posenato 20 works only with instantaneous reaction semantics,<br>
		                                       Hunsberger-Posenato 19 works with any kind of semantics,<br>
		                                       while Hunsberger-Posenato 18 works only with instantaneous or ε semantics.</p>
		                                       <h4>Reaction time</h4>
		                                       <p>In case that ε semantics is selected, the system asks to set the minimum delay by which
		                                       the system can react to an observation. Only integer values are admitted.</p>
		                                       <h4>Init</h4>
		                                       <p>Checks that node <b>Z</b> is present and adds all necessary constraints to make it as the first node
		                                       to execute.<br>
		                                       Moreover, it checks that all labeled values satisfy the well-definedness rules.</p>
		                                       <h4>Controllability</h4>
		                                       <p>Executes the controllability check using the selected algorithm in the drop-down list.<br>
		                                       The resulting network is presented on the right window.</p>
		                                       <h4>One Step CSTN Check</h4>
		                                       <p>Executes only one pass of HunsbergerPosenato19 algorithm at each button press.\s
		                                       Useful for viewing the execution of the algorithm step-by-step.</p>
		                                       <h4>Saved Checked CSTN</h4>
		                                       <p>Saves the network obtained by a check.</p>
		                                       </html>""";
		private static final String cstnuHelp = """
		                                        <html>
		                                        <h2>Condition Simple Temporal Network with Uncertainty</h2>
		                                        <h4>Propagate only to Z</h4>
		                                        <p>If checked, the DC checking algorithm checks the network propagating only constraints ending to Z.<br>
		                                        For a DC network it saves a lot of time, for not DC network, it slows the checking phase.</p>
		                                        <h4>Propagate contingents also as std constraints</h4>
		                                        The DC checking algorithm does not require to propagate the contingent constraints as ordinary constraints.
		                                        The resulting network can not contain proper ranges for all timepoints.<br>
		                                        Propagating contingent constraints as ordinary constraints allows the determination of more proper ranges
		                                        for all time-points at a cost of a small increment of computation time.
		                                        <h4>CSTNU Init</h4>
		                                        <p>Checks that node <b>Z</b> is present and adds all necessary constraints to make it as the first node
		                                        to execute.<br>
		                                        Moreover, it checks that all labeled values satisfy the well-definedness rules and that all contingent
		                                        links are in the right form.</p>
		                                        <h4>CSTNU Check</h4>
		                                        <p>Executes the controllability check.<br>
		                                        The resulting network is presented on the right window.</p>
		                                        <h4>One Step CSTNU Check</h4>
		                                        <p>Executes only one pass of DC checking algorithm at each bottom press. Useful for viewing the execution
		                                         of the algorithm step-by-step.</p>
		                                        <h4>CSTNU2CSTN Check</h4>
		                                        <p>Checks the instance transforming it to an equivalent CSTN instance and using the CSTN DC checking
		                                         algorithm.</p>
		                                        <h4>CSTNPSU/FTNU Check</h4>
		                                        <p>Executes the controllability check for CSTPSU/FTNU instances. Such instances are an extension of CSTNU
		                                        ones where each contingent link can have an extended range.<br>
		                                        The resulting network is presented on the right window.</p>
		                                        </html>""";
		private static final String cstnpsuHelp = """
		                                          <html>
		                                          <h2>Condition Simple Temporal Network with Uncertainty</h2>
		                                          <h4>Propagate only to Z</h4>
		                                          <p>If checked, the DC checking algorithm checks the network propagating only constraints ending to Z.<br>
		                                          For a DC network it saves a lot of time, for not DC network, it slows the checking phase.</p>
		                                          <h4>Propagate contingents also as std constraints</h4>
		                                          The DC checking algorithm does not require to propagate the contingent constraints as ordinary constraints.
		                                          The resulting network can not contain proper ranges for all timepoints.<br>
		                                          Propagating contingent constraints as ordinary constraints allows the determination of more proper ranges for
		                                          all time-points at a cost of a small increment of computation time.
		                                          <h4>CSTNPSU/FTNU Check</h4>
		                                          <p>Executes the controllability check for CSTPSU/FTNU instances. Such instances are an extension of CSTNU
		                                          ones where each contingent link can have an extended range.<br>
		                                          The resulting network is presented on the right window.</p>
		                                          </html>""";
		private static final String stnHelp = """
		                                      <html>
		                                      <h2>Simple Temporal Network</h2>
		                                      <h4>Checking algorithm</h4>
		                                      <p>Select which algorithm will be used for checking the consistency.<br>
		                                      Single-source-shortest-paths algorithms like Bellman-Ford consider the node <b>Z</b> as source node.
		                                      <h4>Init</h4>
		                                      <p>Checks that node <b>Z</b> is present and adds all necessary constraints to make it as the first node to execute.</p>
		                                      <h4>Consistency</h4>
		                                      <p>Executes the consistency check using the selected algorithm in the drop-down list.<br>
		                                      The resulting network is presented on the right window.</p>
		                                      <h4>Fast Dispatchable</h4>
		                                      <p>Transforms a <b>consistent</b> network in a dispatchable form using the Muscettola et al. 1998 algorithm.</p>
		                                      <h4>PredecessorSubGraph</h4>
		                                      <p>Determine a predecessor graph of a given node in a <b>consistent</b> network using Muscettola algorithm.
		                                      The predecessor graph consists in all shortest paths from the given node to all other nodes.</p>
		                                      </html>""";
		private static final String stnuHelp = """
		                                       <html>
		                                       <h2>Simple Temporal Network with Uncertainty</h2>
		                                       <h4>Checking algorithm</h4>
		                                       <p>Select which algorithm will be used for checking the dynamic controllability of the network.
		                                       <h4>Init</h4>
		                                       <p>Checks that node <b>Z</b> is present and adds all necessary constraints to make it as the first node to execute.<br>
		                                       Moreover, it verifies that all contingent links are in the right format.</p>
		                                       <h4>Controllability</h4>
		                                       <p>Executes the dynamic controllability check using the selected algorithm in the drop-down list.<br>
		                                       The resulting network is presented on the right window.</p>
		                                       </html>""";
		private static final String ostnuHelp = """
		                                        <html>
		                                        <h2>Simple Temporal Network with Uncertainty and Oracles</h2>
		                                        <p>In this release, contingent node must be named with just one letter because<br>
		                                        the relation with oracle is fixed setting the proposition field in oracle nodes equals to the name of contingent node.</p>
		                                        <p>Moreover, the software can check networks having at maximum 32 pairs of <br>
		                                        <code>(contingent node, node with constraint to contingent node)</code>.</p>
		                                        <h4>Agile Controllability</h4>
		                                        <p>Executes the dynamic agile controllability check. If the network is agilely controllable, in the green box,<br>
		                                        the system shows possible scenarios that cannot be used because are not controllable,<br>
		                                        and the association <code>(contingent node, node with constraint to contingent node)</code><br>
		                                        with propositions used for labeling the values in the constraints.</p>
		                                        </html>""";
		private final String genericHelp;
		final TNEditor editor;

		HelpListener(TNEditor ed) {
			String genericHelp1;
			editor = ed;

			// Generic help depends on this.editor.extraButtons;
			genericHelp1 = """
			               <html>
			               	<h2>TNEditor Help</h2>
			               	<p>It is possible to create (File->New), load (File->Open), and save the following kind of temporal networks:
			               	STN, CSTN, CSTNU, CSTPSU/FTNU.
			               	</p>
			               	<p>The application contains two main windows. The left window is the <i>editor window</i> where it is possible
			               	to build/edit a network.<br>
			               	The right window is the <i>view window</i> where the result of an operation (like consistency check) is shown.
			               	In the view window it is not possible to edit the shown graph.
			               	</p>
			               	<p>Once a network is created, the toolbar is extended to offer all possible operations on the network.<br>
			               	A network can be modified or inspected when the window is in <i>EDITING</i> mode.<br>
			               	The <i>TRANSFORMING</i> mode is for moving and zooming the network.
			               	</p>""";
			if (editor.extraButtons) {
				genericHelp1 += """
				                <p>Button 'Layout input graph' redraws the network in the editor.
				                It works only when the network represents a business schema transformation with a proper grammar.
				                <br>""";
			}
			genericHelp1 += """
			                	Buttons 'Bigger viewer' and 'Resulting network big viewer' open a wider window for showing the
			                	input/derived graph respectively.</p>
			                	<h3>Editing Mode:</h3>
			                		<ul>
			                		<li>Right-click on an empty area for <b>Add node</b> menu.
			                		<li>Right-click on a node for <b>Delete node</b> popup.
			                		<li>Right-click on a node for <b>Add edge</b> menu (if there are selected nodes).
			                		<li>Right-click on an edge for <b>Delete edge</b> popup
			                		<li>Left-click+Shift on a node adds/removes node selection.
			                		<li>Left-click an empty area unselects all nodes.
			                		<li>Left+drag on a node moves all selected nodes.
			                		<li>Left+drag elsewhere selects nodes in a region.
			                		<li>Left+Shift+drag adds selection of nodes in a new region.
			                		<li>Left-click+drag on a selected node to another node, add an edge from the first node to the second.
			                		<li>Left+Ctrl on a node selects the node and centers the display on it.
			                		<li>Left double-click on a node or edge allows you to edit it.
			                		<li>Mouse wheel scales with a crossover value of 1.0.<br>
			                		- Scales the network layout when the combined scale is greater than 1.<br>
			                		- Scales the network view when the combined scale is less than 1.
			                		</ul>
			                	<h3>Transforming Mode:</h3>
			                		<ul>
			                		<li>Left+drag for moving the network.
			                		<li>Left+Shift+drag for rotating the network.
			                		<li>Left+Command+drag shears the network.
			                		<li>Left double-click on a node or edge allows you to edit the label.
			                		</ul>
			                </html>""";
			// + "<h3>Annotation Mode:</h3>"
			// + "<ul>"
			// + "<li>Mouse1 begins drawing of a Rectangle"
			// + "<li>Mouse1+drag defines the Rectangle shape"
			// + "<li>Mouse1 release adds the Rectangle as an annotation"
			// + "<li>Mouse1+Shift begins drawing of an Ellipse"
			// + "<li>Mouse1+Shift+drag defines the Ellipse shape"
			// + "<li>Mouse1+Shift release adds the Ellipse as an annotation"
			// + "<li>Mouse3 shows a popup to input text, which will become"
			// + "<li>a text annotation on the graph at the mouse location"
			// + "</ul>"
			genericHelp = genericHelp1;
		}

		@Override
		public final void actionPerformed(final ActionEvent e) {
			String message = genericHelp;
			switch (e.getActionCommand()) {
				case "STNHelp":
					message = stnHelp;
					break;
				case "STNUHelp":
					message = stnuHelp;
					break;
				case "OSTNUHelp":
					message = ostnuHelp;
					break;
				case "CSTNHelp":
					message = cstnHelp;
					break;
				case "CSTNUHelp":
					message = cstnuHelp;
					break;
				case "CSTNPSUHelp":
					message = cstnpsuHelp;
					break;
				case "GenericHelp":
				default:
					break;
			}
			JOptionPane.showMessageDialog(editor.vvEditor, message, "Help", JOptionPane.INFORMATION_MESSAGE);
		}
	}

	/**
	 * Name of the derived graph big viewer
	 */
	public static final String DERIVED_GRAPH_BIG_VIEWER_NAME = "Resulting network bigger viewer";

	// /**
	// * @author posenato
	// */
	// private class CSTNAPSPListener implements ActionListener {
	//
	// public CSTNAPSPListener() {
	// }
	//
	// @SuppressWarnings("unchecked")
	// @Override
	// public void actionPerformed(final ActionEvent e) {
	// final JEditorPane jl = TNEditor.this.viewerMessageArea;
	// TNEditor.this.saveCSTNResultButton.setEnabled(false);
	// TNGraph<CSTNEdge> g1 = new TNGraph<>((TNGraph<CSTNEdge>) TNEditor.this.inputGraph,
	// (Class<? extends CSTNEdge>) EdgeSupplier.DEFAULT_CSTNU_EDGE_CLASS);
	// ((TNGraph<CSTNEdge>) TNEditor.this.checkedGraph).takeFrom(g1);
	// TNEditor.this.mapInfoLabel.setText(TNEditor.this.inputGraph.getEdgeFactory().toString());
	//
	// jl.setBackground(Color.orange);
	// boolean consistent = false;
	// TNEditor.this.cstn = new CSTN((TNGraph<CSTNEdge>) TNEditor.this.checkedGraph);
	// consistent = AbstractCSTN.getMinimalDistanceGraph(TNEditor.this.cstn.getG());
	// if (consistent) {
	//
	// jl.setText("<img align='middle' src='" + INFO_ICON_FILE + "'>&nbsp;<b>The network is All-Pair Shortest Paths.");
	// jl.setBackground(Color.green);
	// if (Debug.ON) {
	// if (LOG.isLoggable(Level.FINER)) {
	// TNEditor.LOG.finer("All-Pair Shortest Paths graph: " + TNEditor.this.cstn.getGChecked());
	// }
	// }
	// } else {
	// // The distance network is not consistent
	// jl.setText("<img align='middle' src='" + WARN_ICON_FILE + "'>&nbsp;<b>The network is not CSTN consistent.</b>");
	// // jl.setIcon(TNEditor.warnIcon);
	// }
	// jl.setOpaque(true);
	// updateNodePositions();
	// TNEditor.this.vvViewer.setVisible(true);
	// TNEditor.this.saveCSTNResultButton.setEnabled(false);
	//
	// TNEditor.this.vvViewer.validate();
	// TNEditor.this.vvViewer.repaint();
	//
	// TNEditor.this.validate();
	// TNEditor.this.repaint();
	// TNEditor.this.cycle = 0;
	// }
	// }
	/**
	 * Name of the distance viewer panel
	 */
	public static final String DISTANCE_VIEWER_NAME = "DistanceViewer";
	/**
	 * Name of the editor panel
	 */
	public static final String EDITOR_NAME = "Editor";
	/**
	 * Name of the input graph big viewer
	 */
	public static final String INPUT_GRAPH_BIG_VIEWER_NAME = "Bigger viewer";
	/**
	 * Tooltip rendering for the mouse movement
	 */
	public final static java.util.function.Function<MouseEvent, String> mouseEventToolTipFunction = new Function<>() {
		/**
		 * Returns the position of the mouse
		 */
		@Override
		public String apply(final MouseEvent e) {
			if (e == null) {
				return "";
			}
			return "X: " + e.getXOnScreen() + ", Y: " + e.getYOnScreen();
		}
	};
	/**
	 *
	 */
	static final URL INFO_ICON_FILE = TNEditor.class.getClassLoader().getResource("images/metal-info.png");
	/**
	 * class logger
	 */
	static final Logger LOG = Logger.getLogger(TNEditor.class.getName());
	/**
	 *
	 */
	static final URL WARN_ICON_FILE = TNEditor.class.getClassLoader().getResource("images/metal-warning.png");
	/**
	 * Standard serial number
	 */
	@SuppressWarnings("unused")
	private static final long SERIAL_VERSION_UID = 647420826043015778L;
	/**
	 * Version
	 */
	// Till version is update by SVN ($REV$), I put replace to avoid $Rev: string$
	private static final String VERSION = "Version  $Rev: 898 $".replace("$Rev: ", "").replace("$", "");
	/**
	 *
	 */
	@Serial
	private static final long serialVersionUID = 3L;

	/**
	 * @param g graph
	 *
	 * @return a string describing essential characteristics of the graph.
	 */
	static String getGraphLabelDescription(TNGraph<?> g) {
		if (g == null) {
			return "";
		}
		final StringBuilder sb = new StringBuilder(80);
		if (g.getFileName() != null) {
			sb.append("File ");
			sb.append(g.getFileName().getName());
		}
		sb.append(": #nodes: ");
		sb.append(g.getVertexCount());
		sb.append(", #edges: ");
		sb.append(g.getEdgeCount());
		sb.append(", #obs: ");
		sb.append(g.getObserverCount());
		if (g.getContingentNodeCount() > 0) {
			sb.append(", #contingent: ");
			sb.append(g.getContingentNodeCount());
		}
		return sb.toString();
	}

	/**
	 * @param args an array of {@link String} objects.
	 */
	public static void main(final String[] args) {
		final TNEditor editor = new TNEditor();
		if (!editor.manageParameters(args)) {
			return;
		}
		editor.init();
	}

	/**
	 * Sets up vertex and edges renders.
	 *
	 * @param <E>         type of edge
	 * @param viewer      viewer
	 * @param firstViewer true if viewer is in the first position
	 */
	static <E extends Edge> void setNodeEdgeRenders(BasicVisualizationServer<LabeledNode, E> viewer, boolean firstViewer) {
		final RenderContext<LabeledNode, E> renderCon = viewer.getRenderContext();
		// VERTEX setting
		// vv.getRenderContext().setVertexFillPaintTransformer(vertexPaint);
		viewer.getRenderer().getVertexLabelRenderer().setPosition(Position.CNTR);
		renderCon.setVertexLabelTransformer(NodeRendering.vertexLabelFunction);
		renderCon.setVertexDrawPaintTransformer(
			NodeRendering.nodeDrawPaintTransformer(viewer.getPickedVertexState(), Color.white, Color.magenta, Color.red, Color.yellow, Color.green,
			                                       Color.cyan));
		renderCon.setVertexFillPaintTransformer(
			NodeRendering.nodeDrawPaintTransformer(viewer.getPickedVertexState(), Color.white, Color.magenta, Color.red, Color.yellow, Color.green,
			                                       Color.cyan));
		renderCon.setVertexShapeTransformer(Functions.constant(new Ellipse2D.Float(-15, -15, 30, 30)));
		((VisualizationViewer<LabeledNode, E>) viewer).setVertexToolTipTransformer(NodeRendering.vertexToolTipFunction);

		// EDGE setting
		renderCon.setEdgeDrawPaintTransformer(
			EdgeRendering.edgeDrawPaintFunction(viewer.getPickedEdgeState(), Color.magenta, Color.black, Color.orange, Color.gray, Color.red));
		renderCon.setEdgeLabelTransformer(EdgeRendering.edgeLabelFunction);
		renderCon.setEdgeLabelRenderer(new DefaultEdgeLabelRenderer(Color.blue));
		renderCon.setEdgeStrokeTransformer(EdgeRendering.edgeStrokeFunction);
		renderCon.setEdgeLabelClosenessTransformer(new ConstantDirectionalEdgeValueTransformer<>(0.65, 0.5));
		renderCon.setArrowDrawPaintTransformer(
			EdgeRendering.edgeDrawPaintFunction(viewer.getPickedEdgeState(), Color.magenta, Color.black, Color.orange, Color.gray, Color.red));
		renderCon.setArrowFillPaintTransformer(
			EdgeRendering.edgeDrawPaintFunction(viewer.getPickedEdgeState(), Color.magenta, Color.black, Color.orange, Color.gray, Color.red));
		if (firstViewer) {
			renderCon.setEdgeFontTransformer(EdgeRendering.edgeFontFunction);
		}
		renderCon.setLabelOffset((firstViewer) ? 6 : 3);

		((VisualizationViewer<LabeledNode, E>) viewer).setMouseEventToolTipTransformer(mouseEventToolTipFunction::apply);
	}

	/**
	 * Open a bigger viewer for showing the input or derived network.
	 *
	 * @author posenato
	 */
	@SuppressFBWarnings(value = "SE_BAD_FIELD", justification = "I don't make everything serializable.")
	private class BigViewerListener implements ActionListener {

		final boolean isInputGraphLayoutToShow;
		JDialog frame;
		AbstractLayout<LabeledNode, ? extends Edge> layout;
		VisualizationViewer<LabeledNode, ? extends Edge> bvv;

		BigViewerListener(boolean isInputGraphLayoutToShow1) {
			isInputGraphLayoutToShow = isInputGraphLayoutToShow1;
		}

		@Override
		@SuppressFBWarnings(value = "UwF", justification = "False positive.")
		public final void actionPerformed(final ActionEvent e) {

			frame = new JDialog(TNEditor.this, (isInputGraphLayoutToShow) ? TNEditor.INPUT_GRAPH_BIG_VIEWER_NAME : TNEditor.DERIVED_GRAPH_BIG_VIEWER_NAME);
			frame.setBounds(getBounds());

			layout = (isInputGraphLayoutToShow) ? layoutEditor : layoutViewer;
			bvv = new VisualizationViewer<>(layout);
			final Dimension bvvDim = new Dimension(((LayoutListener) layoutToggleButton.getActionListeners()[0]).getSize().width,
			                                       ((LayoutListener) layoutToggleButton.getActionListeners()[0]).getSize().height);
//			bvv.setPreferredSize(bvvDim);
			bvv.setMaximumSize(bvvDim);
			bvv.setName((isInputGraphLayoutToShow) ? TNEditor.INPUT_GRAPH_BIG_VIEWER_NAME : TNEditor.DERIVED_GRAPH_BIG_VIEWER_NAME);

			// vertex and edge renders
			setNodeEdgeRenders(bvv, isInputGraphLayoutToShow);

			// mouse action
			@SuppressWarnings("unchecked") final EditingModalGraphMouse<LabeledNode, Edge> graphMouse =
				new EditingModalGraphMouse<>((RenderContext<LabeledNode, Edge>) bvv.getRenderContext(), new LabeledNodeSupplier(),
				                             new EdgeSupplier<>(currentEdgeImpl),
				                             // only after graph load it is possible to set edge supplier.
				                             TNEditor.this, isInputGraphLayoutToShow);
			// graphMouse.setMode(ModalGraphMouse.Mode.PICKING);
			bvv.setGraphMouse(graphMouse);
			bvv.addKeyListener(graphMouse.getModeKeyListener());
			((ModalGraphMouse) bvv.getGraphMouse()).setMode(Mode.EDITING);
			final JPanel rowForAppButtons1 = new JPanel();
			@SuppressWarnings("unchecked") final JComboBox<Mode> modeBox1 = ((EditingModalGraphMouse<LabeledNode, Edge>) bvv.getGraphMouse()).getModeComboBox();
			rowForAppButtons1.add(modeBox1);

			final JButton paint = new JButton("Capture");
			paint.addActionListener(e12 -> {
				final BufferedImage image = new BufferedImage(((LayoutListener) layoutToggleButton.getActionListeners()[0]).getSize().width,
				                                              ((LayoutListener) layoutToggleButton.getActionListeners()[0]).getSize().height,
				                                              BufferedImage.TYPE_INT_RGB);
				final Graphics2D g = image.createGraphics();
				bvv.setSize(bvvDim);
				bvv.printAll(g);
				g.dispose();
				final JFileChooser chooser = new SaveFileListener.JFileChooserCustom(defaultDir);
				chooser.setFileFilter(new FileNameExtensionFilter("Image file", "png", "jpg"));
				boolean saved = false;
				while (!saved) {
					final int option = chooser.showSaveDialog(TNEditor.this);
					if (option == JFileChooser.CANCEL_OPTION) {
						break;
					}
					if (option == JFileChooser.APPROVE_OPTION) {
						final File file = chooser.getSelectedFile();
						defaultDir = file.getParent();
						try {
							ImageIO.write(image, "jpg", (file.getName().endsWith("jpg") ? file : new File(file.getName() + ".jpg")));
							ImageIO.write(image, "png", (file.getName().endsWith("png") ? file : new File(file.getName() + ".png")));
							saved = true;
						} catch (IOException e1) {
							JOptionPane.showMessageDialog(vvViewer, "The selected file cannot be used for saving the graph");
						}
					}
				}
			});
			rowForAppButtons1.add(paint);
			final JButton close = new JButton(new AbstractAction("Close") {
				@Serial
				private static final long serialVersionUID = 1L;

				@Override
				public void actionPerformed(ActionEvent event) {
					if (frame != null) {
						frame.dispose();
					}
				}
			});
			rowForAppButtons1.add(close);
//			frame.setLayout(new GridLayout(3,1));
			frame.add(new JLabel(getGraphLabelDescription(((TNGraph<?>) layoutEditor.getGraph()))), BorderLayout.NORTH);
			frame.add(new GraphZoomScrollPane(bvv), BorderLayout.CENTER);
			frame.add(rowForAppButtons1, BorderLayout.SOUTH);

			frame.setVisible(true);
			frame.validate();
		}
	}

	/**
	 * @author posenato
	 */
	private class CheckAlgListener<T> implements ActionListener {
		final JComboBox<T> comboBox;

		CheckAlgListener(JComboBox<T> comboBox1) {
			comboBox = comboBox1;
		}

		@SuppressWarnings("ChainOfInstanceofChecks")
		@Override
		public final void actionPerformed(ActionEvent e) {
			final Object selected = comboBox.getSelectedItem();

			if (selected instanceof STN.CheckAlgorithm) {
				stnCheckAlg = (STN.CheckAlgorithm) comboBox.getSelectedItem();
				return;
			}
			if (selected instanceof STNU.CheckAlgorithm) {
				stnuCheckAlg = (STNU.CheckAlgorithm) comboBox.getSelectedItem();
				return;
			}
			if (selected instanceof CSTN.CheckAlgorithm) {
				cstnCheckAlg = (CheckAlgorithm) comboBox.getSelectedItem();
				if (cstnCheckAlg == CheckAlgorithm.HunsbergerPosenato18) {
					if (cstnDCSemanticsComboBox.getSelectedItem() == DCSemantics.Std) {
						cstnDCSemanticsComboBox.setSelectedItem(DCSemantics.IR);
					}
				}
				if (cstnCheckAlg == CheckAlgorithm.HunsbergerPosenato20) {
					cstnDCSemanticsComboBox.setSelectedItem(DCSemantics.IR);
				}
				cstnDCSemanticsComboBox.validate();
				cstnDCSemanticsComboBox.repaint();
			}
		}
	}

	/**
	 * @author posenato
	 */
	private class ContingentAlsoAsOrdinaryListener implements ItemListener {

		ContingentAlsoAsOrdinaryListener() {
		}

		@Override
		public final void itemStateChanged(ItemEvent e) {
			contingentAlsoAsOrdinary = e.getStateChange() == ItemEvent.SELECTED;
		}
	}

	/**
	 * @author posenato
	 */
	private class CSTNControllabilityCheckListener implements ActionListener {

		CSTNControllabilityCheckListener() {
		}

		@SuppressWarnings("unchecked")
		@Override
		public final void actionPerformed(final ActionEvent e) {
			final JEditorPane jl = viewerMessageArea;

			saveTNResultButton.setEnabled(false);
			vvViewer.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
			final TNGraph<CSTNEdge> g1 = new TNGraph<>((TNGraph<CSTNEdge>) inputGraph, (Class<? extends CSTNEdge>) EdgeSupplier.DEFAULT_CSTNU_EDGE_CLASS);
			((TNGraph<CSTNEdge>) checkedGraph).takeFrom(g1);

			mapInfoLabel.setText(inputGraph.getEdgeFactory().toString());

			switch (cstnCheckAlg) {
				case HunsbergerPosenato18:
					switch (dcCurrentSem) {
						case ε:
							cstn = new CSTNEpsilon3R(reactionTime, (TNGraph<CSTNEdge>) checkedGraph);
							break;
						case IR:
							cstn = new CSTNIR3R((TNGraph<CSTNEdge>) checkedGraph);
							break;
						case Std:
						default:
							jl.setBackground(Color.orange);
							jl.setText("<img align='middle' src='" + WARN_ICON_FILE +
							           "'>&nbsp;<b>There is no DC checking algorithm for STD semantics and rules restricted to Z.</b>");
							jl.setOpaque(true);
							cycle = 0;
							return;
					}
					break;
				case HunsbergerPosenato19:
					switch (dcCurrentSem) {
						case ε:
							cstn = new CSTNEpsilon(reactionTime, (TNGraph<CSTNEdge>) checkedGraph);
							break;
						case IR:
							cstn = new CSTNIR((TNGraph<CSTNEdge>) checkedGraph);
							break;
						case Std:
						default:
							cstn = new CSTN((TNGraph<CSTNEdge>) checkedGraph);
							break;
					}
					break;

				case HunsbergerPosenato20:
				default:
					cstn = new CSTNPotential((TNGraph<CSTNEdge>) checkedGraph);
					cstnDCSemanticsComboBox.setSelectedItem(DCSemantics.IR);
					break;
			}

			cstn.setOutputCleaned(cleanResult);

			jl.setBackground(Color.orange);
			try {
				cstnStatus = cstn.dynamicConsistencyCheck();
				if (cstnStatus.consistency) {

					jl.setText("<img align='middle' src='" + INFO_ICON_FILE + "'>&nbsp;<b>The network is dynamically consistent.");
					jl.setBackground(Color.green);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							TNEditor.LOG.finer("Final controllable graph: " + cstn.getGChecked());
						}
					}
				} else {
					// The distance network is not consistent
					jl.setText("<img align='middle' src='" + WARN_ICON_FILE + "'>&nbsp;<b>The network is not dynamically consistent.</b>");
					// jl.setIcon(TNEditor.warnIcon);
				}
			} catch (final WellDefinitionException ex) {
				jl.setText("There is a problem in the code: " + ex.getMessage());
				// jl.setIcon(TNEditor.warnIcon);
			}

			cycle = 0;
			saveTNResultButton.setEnabled(true);
			updatevvViewer();
		}
	}

	/**
	 * @author posenato
	 */
	private class CSTNInitListener implements ActionListener {

		CSTNInitListener() {
		}

		@SuppressWarnings("unchecked")
		@Override
		public final void actionPerformed(final ActionEvent e) {
			final JEditorPane jl = viewerMessageArea;
			final TNGraph<CSTNEdge> g1 = new TNGraph<>((TNGraph<CSTNEdge>) inputGraph, (Class<? extends CSTNEdge>) EdgeSupplier.DEFAULT_CSTNU_EDGE_CLASS);
			((TNGraph<CSTNEdge>) checkedGraph).takeFrom(g1);

			mapInfoLabel.setText(inputGraph.getEdgeFactory().toString());
			switch (dcCurrentSem) {
				case ε:
					cstn = new CSTNEpsilon(reactionTime, (TNGraph<CSTNEdge>) checkedGraph);
					break;
				case IR:
					cstn = new CSTNIR((TNGraph<CSTNEdge>) checkedGraph);
					break;
				case Std:
				default:
					cstn = new CSTN((TNGraph<CSTNEdge>) checkedGraph);
					break;
			}
			try {
				cstn.initAndCheck();
			} catch (final Exception ec) {
				final String msg = "The graph has a problem, and it cannot be initialized: " + ec.getMessage();
				if (Debug.ON) {
					if (LOG.isLoggable(Level.WARNING)) {
						TNEditor.LOG.warning(msg);
					}
				}
				jl.setText("<img align='middle' src='" + WARN_ICON_FILE + "'>&nbsp;<b>" + msg + "</b>");
				// jl.setIcon(TNEditor.warnIcon);
				jl.setOpaque(true);
				jl.setBackground(Color.orange);
				// TNEditor.this.vv2.validate();
				// TNEditor.this.vv2.repaint();
				validate();
				repaint();
				return;
			}
			jl.setText("CSTN initialized.");
			// jl.setIcon(TNEditor.infoIcon);

			jl.setBackground(Color.orange);
			cycle = 0;
			saveTNResultButton.setEnabled(true);
			updatevvViewer();
		}
	}

	/**
	 * @author posenato
	 */
	private class CSTNOneStepListener implements ActionListener {

		CSTNOneStepListener() {
		}

		@SuppressWarnings("unchecked")
		@Override
		public final void actionPerformed(final ActionEvent e) {
			final JEditorPane jl = viewerMessageArea;

			checkingAlgCSTNComboBox.setSelectedItem(CheckAlgorithm.HunsbergerPosenato19);
			if (cycle == -1) {
				return;
			}
			if (cycle == 0) {
				final TNGraph<CSTNEdge> g1 = new TNGraph<>((TNGraph<CSTNEdge>) inputGraph, (Class<? extends CSTNEdge>) EdgeSupplier.DEFAULT_CSTNU_EDGE_CLASS);
				((TNGraph<CSTNEdge>) checkedGraph).takeFrom(g1);

				mapInfoLabel.setText(inputGraph.getEdgeFactory().toString());
				switch (dcCurrentSem) {
					case ε:
						cstn = new CSTNEpsilon(reactionTime, (TNGraph<CSTNEdge>) checkedGraph);
						break;
					case IR:
						cstn = new CSTNIR((TNGraph<CSTNEdge>) checkedGraph);
						break;
					case Std:
					default:
						cstn = new CSTN((TNGraph<CSTNEdge>) checkedGraph);
						break;
				}
				cstn.setOutputCleaned(cleanResult);

				try {
					cstn.initAndCheck();
				} catch (final Exception ex) {
					jl.setText("There is a problem in the graph: " + ex.getMessage());
					// jl.setIcon(TNEditor.warnIcon);
					cycle = -1;
					return;
				}
				oneStepBackGraph = new TNGraph<>((TNGraph<CSTNEdge>) checkedGraph, (Class<? extends CSTNEdge>) EdgeSupplier.DEFAULT_CSTNU_EDGE_CLASS);
				cstnStatus = new CSTNCheckStatus();
			} else {
				final TNGraph<CSTNEdge> g1 = new TNGraph<>((TNGraph<CSTNEdge>) checkedGraph, (Class<? extends CSTNEdge>) EdgeSupplier.DEFAULT_CSTNU_EDGE_CLASS);
				((TNGraph<CSTNEdge>) oneStepBackGraph).takeFrom(g1);

			}
			cycle++;

			jl.setBackground(Color.orange);
			cstnStatus = cstn.oneStepDynamicConsistencyByNode();
			cstnStatus.finished = ((TNGraph<CSTNEdge>) checkedGraph).hasSameEdgesOf((TNGraph<CSTNEdge>) oneStepBackGraph);
			final boolean reductionsApplied = !cstnStatus.finished;
			final boolean inconsistency = !cstnStatus.consistency;
			if (inconsistency) {
				jl.setText("<img align='middle' src='" + WARN_ICON_FILE + "'>&nbsp;<b>The network is inconsistent.<b>");
				// jl.setIcon(TNEditor.warnIcon);
				cycle = -1;
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINE)) {
						TNEditor.LOG.fine("INCONSISTENT GRAPH: " + oneStepBackGraph);
					}
					if (LOG.isLoggable(Level.INFO)) {
						TNEditor.LOG.info("Status stats: " + cstnStatus);
					}
				}
			} else if (reductionsApplied) {
				jl.setText("Step " + cycle + " of consistency check is done.");
				// jl.setIcon(TNEditor.warnIcon);
			} else {
				jl.setText(
					"<img align='middle' src='" + INFO_ICON_FILE + "'>&nbsp;<b>The network is dynamically consistent. The number of executed cycles is " +
					cycle);
				// jl.setIcon(TNEditor.infoIcon);
				cycle = -1;
				jl.setBackground(Color.green);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.INFO)) {
						TNEditor.LOG.info("Status stats: " + cstnStatus);
					}
				}
			}

			saveTNResultButton.setEnabled(true);
			updatevvViewer();
		}
	}

	/**
	 * @author posenato
	 */
	private class CSTNPSUCheckListener implements ActionListener {

		CSTNPSUCheckListener() {
		}

		@SuppressWarnings("unchecked")
		@Override
		public final void actionPerformed(final ActionEvent e) {
			final JEditorPane jl1 = viewerMessageArea;
			saveTNResultButton.setEnabled(false);
			final TNGraph<CSTNPSUEdge> g1 =
				new TNGraph<>((TNGraph<CSTNPSUEdge>) inputGraph, (Class<? extends CSTNPSUEdge>) EdgeSupplier.DEFAULT_CSTNPSU_EDGE_CLASS);
			((TNGraph<CSTNPSUEdge>) checkedGraph).takeFrom(g1);

			cstnpsu = new CSTNPSU((TNGraph<CSTNPSUEdge>) checkedGraph, 30 * 60);
			cstnpsu.setPropagationOnlyToZ(onlyToZCB.isSelected());
			jl1.setBackground(Color.orange);
			try {
				cstnuStatus = cstnpsu.dynamicControllabilityCheck();
				if (cstnuStatus.consistency) {
					jl1.setText("<img align='middle' src='" + INFO_ICON_FILE + "'>&nbsp;<b>The CSTNPSU/FTNU is dynamically controllable.</b>");
					// jl.setIcon(CSTNUEditor.infoIcon);
					jl1.setBackground(Color.green);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							TNEditor.LOG.finer("Final controllable graph: " + cstnpsu.getGChecked());
						}
					}
				} else {
					jl1.setText("<img align='middle' src='" + WARN_ICON_FILE + "'>&nbsp;<b>The CSTNPSU/FTNU is not dynamically controllable.</b>");
					// jl.setIcon(CSTNUEditor.warnIcon);
				}
			} catch (final WellDefinitionException ex) {
				jl1.setText("<img align='middle' src='" + WARN_ICON_FILE + "'>&nbsp;There is a problem in the code: " + ex.getMessage());
				// jl.setIcon(CSTNUEditor.warnIcon);
			}

			saveTNResultButton.setEnabled(true);
			cycle = 0;
			updatevvViewer();
		}
	}

	/**
	 * Calls the getPrototypalLink (PLC) of a FTNU network and shows it into the information bar. The viewer window shows the checked instance.
	 *
	 * @author posenato
	 */
	private class PLCListener implements ActionListener {

		PLCListener() {
		}

		@SuppressWarnings("unchecked")
		@Override
		public final void actionPerformed(final ActionEvent e) {
			final JEditorPane jl1 = viewerMessageArea;
			jl1.setContentType("text/html;charset=UTF-16");

			saveTNResultButton.setEnabled(false);
			final TNGraph<CSTNPSUEdge> g1 =
				new TNGraph<>((TNGraph<CSTNPSUEdge>) inputGraph, (Class<? extends CSTNPSUEdge>) EdgeSupplier.DEFAULT_CSTNPSU_EDGE_CLASS);
			((TNGraph<CSTNPSUEdge>) checkedGraph).takeFrom(g1);

			cstnpsu = new CSTNPSU((TNGraph<CSTNPSUEdge>) checkedGraph, 30 * 60);
			cstnpsu.setPropagationOnlyToZ(onlyToZCB.isSelected());
			jl1.setBackground(Color.orange);
			try {
				final PrototypalLink plc = cstnpsu.getPrototypalLink();
				cstnuStatus = cstnpsu.getCheckStatus();
				if (cstnuStatus.isControllable()) {
					jl1.setText("<img align='middle' src='" + INFO_ICON_FILE + "'>&nbsp;<b>Prototypal link with contingency (PLC): " + plc + "</b>");
					// jl.setIcon(CSTNUEditor.infoIcon);
					jl1.setBackground(Color.green);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							TNEditor.LOG.finer("Final controllable graph: " + cstnpsu.getGChecked());
						}
					}
				} else {
					jl1.setText("<img align='middle' src='" + WARN_ICON_FILE + "'>&nbsp;<b>The CSTNPSU/FTNU is not dynamically controllable.</b>");
					// jl.setIcon(CSTNUEditor.warnIcon);
				}
			} catch (final WellDefinitionException ex) {
				jl1.setText("<img align='middle' src='" + WARN_ICON_FILE + "'>&nbsp;There is a problem in the code: " + ex.getMessage());
				// jl.setIcon(CSTNUEditor.warnIcon);
			}

			saveTNResultButton.setEnabled(true);
			cycle = 0;
			updatevvViewer();
		}
	}

	/**
	 * @author posenato
	 */
	private class ContingencyGraphListener implements ActionListener {

		ContingencyGraphListener() {
		}

		@SuppressWarnings("unchecked")
		@Override
		public final void actionPerformed(final ActionEvent e) {
			final JEditorPane jl1 = viewerMessageArea;
			saveTNResultButton.setEnabled(false);

			final TNGraph<CSTNEdge> contingencyGraph;
			final TNGraph<CSTNPSUEdge> g1 =
				new TNGraph<>((TNGraph<CSTNPSUEdge>) inputGraph, (Class<? extends CSTNPSUEdge>) EdgeSupplier.DEFAULT_CSTNPSU_EDGE_CLASS);
			((TNGraph<CSTNPSUEdge>) checkedGraph).takeFrom(g1);

			cstnpsu = new CSTNPSU((TNGraph<CSTNPSUEdge>) checkedGraph, 30 * 60);
			cstnpsu.setPropagationOnlyToZ(onlyToZCB.isSelected());
			jl1.setBackground(Color.orange);
			try {
				cstnuStatus = cstnpsu.dynamicControllabilityCheck();
				if (cstnuStatus.isControllable()) {
					// find the contingency graph
					contingencyGraph = cstnpsu.getContingencyGraph();
					assert contingencyGraph != null;
					((TNGraph<CSTNEdge>) checkedGraph).takeFrom(contingencyGraph);

					jl1.setText("<img align='middle' src='" + INFO_ICON_FILE + "'>&nbsp;<b>Contingency graph of the CSTNPSU/FTNU instance.</b>");
					// jl.setIcon(CSTNUEditor.infoIcon);
					jl1.setBackground(Color.green);
				} else {
					jl1.setText("<img align='middle' src='" + WARN_ICON_FILE + "'>&nbsp;<b>The CSTNPSU/FTNU is not dynamically controllable.</b>");
					// jl.setIcon(CSTNUEditor.warnIcon);
				}
			} catch (final WellDefinitionException ex) {
				jl1.setText("<img align='middle' src='" + WARN_ICON_FILE + "'>&nbsp;There is a problem in the code: " + ex.getMessage());
				// jl.setIcon(CSTNUEditor.warnIcon);
			}

			saveTNResultButton.setEnabled(true);
			cycle = 0;
			updatevvViewer();
		}
	}

	/**
	 * @author posenato
	 */
	private class PCSTNUCheckListener implements ActionListener {

		@SuppressWarnings("unchecked")
		@Override
		public final void actionPerformed(final ActionEvent e) {
			final JEditorPane jl1 = viewerMessageArea;
			saveTNResultButton.setEnabled(false);
			final TNGraph<CSTNUEdge> g1 = new TNGraph<>((TNGraph<CSTNUEdge>) inputGraph, (Class<? extends CSTNUEdge>) EdgeSupplier.DEFAULT_CSTNU_EDGE_CLASS);
			((TNGraph<CSTNUEdge>) checkedGraph).takeFrom(g1);

			pcstnu = new PCSTNU((TNGraph<CSTNUEdge>) checkedGraph, 30 * 60);
			pcstnu.setPropagationOnlyToZ(true);
			jl1.setBackground(Color.orange);
			try {
				cstnuStatus = pcstnu.dynamicControllabilityCheck();
				if (cstnuStatus.consistency) {
					jl1.setText("<img align='middle' src='" + INFO_ICON_FILE + "'>&nbsp;<b>The PCSTNU is dynamically controllable.</b>");
					// jl.setIcon(CSTNUEditor.infoIcon);
					jl1.setBackground(Color.green);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							TNEditor.LOG.finer("Final controllable graph: " + pcstnu.getGChecked());
						}
					}
				} else {
					jl1.setText("<img align='middle' src='" + WARN_ICON_FILE + "'>&nbsp;<b>The PCSTNU is not dynamically controllable.</b>");
					// jl.setIcon(CSTNUEditor.warnIcon);
				}
			} catch (final WellDefinitionException ex) {
				jl1.setText("<img align='middle' src='" + WARN_ICON_FILE + "'>&nbsp;There is a problem in the code: " + ex.getMessage());
				// jl.setIcon(CSTNUEditor.warnIcon);
			}

			saveTNResultButton.setEnabled(true);
			cycle = 0;
			updatevvViewer();
		}
	}

	/**
	 * @author posenato
	 */
	private class TNSaveListener implements ActionListener {
		TNSaveListener() {
		}

		@Override
		public final void actionPerformed(final ActionEvent e) {
			final JFileChooser chooser = new JFileChooser(defaultDir);
			final int option = chooser.showSaveDialog(TNEditor.this);
			if (option == JFileChooser.APPROVE_OPTION) {
				final File file = chooser.getSelectedFile();
//				if (cstn != null) {
//					cstn.setfOutput(file);
//					cstn.saveGraphToFile();
//				}
				try {
					saveGraphToFile(TNEditor.this.checkedGraph, file);
				} catch (IOException ex) {
					throw new RuntimeException(ex);
				}
			}
		}
	}

	/**
	 * @author posenato
	 */
	private class CSTNU2CSTNCheckListener implements ActionListener {

		CSTNU2CSTNCheckListener() {
		}

		@SuppressWarnings("unchecked")
		@Override
		public final void actionPerformed(final ActionEvent e) {
			final JEditorPane jl1 = viewerMessageArea;
			saveTNResultButton.setEnabled(false);
			final TNGraph<CSTNUEdge> g1 = new TNGraph<>((TNGraph<CSTNUEdge>) inputGraph, (Class<? extends CSTNUEdge>) EdgeSupplier.DEFAULT_CSTNU_EDGE_CLASS);
			((TNGraph<CSTNUEdge>) checkedGraph).takeFrom(g1);

			cstnu2cstn = new CSTNU2CSTN((TNGraph<CSTNUEdge>) checkedGraph, 30 * 60);

			jl1.setBackground(Color.orange);
			try {
				cstnuStatus = cstnu2cstn.dynamicControllabilityCheck();
				if (cstnuStatus.consistency) {
					jl1.setText("<img align='middle' src='" + INFO_ICON_FILE + "'>&nbsp;<b>The CSTNU is dynamically controllable.</b>");
					// jl.setIcon(CSTNUEditor.infoIcon);
					jl1.setBackground(Color.green);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							TNEditor.LOG.finer("Final controllable graph: " + cstnu2cstn.getGChecked());
						}
					}
				} else {
					jl1.setText("<img align='middle' src='" + WARN_ICON_FILE + "'>&nbsp;<b>The CSTNU is not dynamically controllable.</b>");
					// jl.setIcon(CSTNUEditor.warnIcon);
				}
			} catch (final WellDefinitionException ex) {
				jl1.setText("<img align='middle' src='" + WARN_ICON_FILE + "'>&nbsp;There is a problem in the code: " + ex.getMessage());
				// jl.setIcon(CSTNUEditor.warnIcon);
			}

			saveTNResultButton.setEnabled(true);
			cycle = 0;
			updatevvViewer();
		}
	}

	/**
	 * @author posenato
	 */
	private class CSTNUInitListener implements ActionListener {

		CSTNUInitListener() {
		}

		@SuppressWarnings("unchecked")
		@Override
		public final void actionPerformed(final ActionEvent e) {
			final JEditorPane jl1 = viewerMessageArea;
			final TNGraph<CSTNUEdge> g1 = new TNGraph<>((TNGraph<CSTNUEdge>) inputGraph, (Class<? extends CSTNUEdge>) EdgeSupplier.DEFAULT_CSTNU_EDGE_CLASS);
			((TNGraph<CSTNUEdge>) checkedGraph).takeFrom(g1);

			cstnu = new CSTNU((TNGraph<CSTNUEdge>) checkedGraph, 30 * 60, onlyToZ);
			cstnu.setContingentAlsoAsOrdinary(contingentAlsoAsOrdinary);
			try {
				cstnu.initAndCheck();
			} catch (final IllegalArgumentException | WellDefinitionException ec) {
				final String msg = "The graph has a problem, and it cannot be initialized: " + ec.getMessage();
				if (Debug.ON) {
					if (LOG.isLoggable(Level.WARNING)) {
						TNEditor.LOG.warning(msg);
					}
				}
				jl1.setText("<img align='middle' src='" + WARN_ICON_FILE + "'>&nbsp;<b>" + msg + "</b>");
				// jl.setIcon(CSTNUEditor.warnIcon);
				jl1.setOpaque(true);
				jl1.setBackground(Color.orange);
				validate();
				repaint();
				return;
			}
			jl1.setText("<img align='middle' src='" + INFO_ICON_FILE + "'>&nbsp;TNGraph with Lower and Upper Case Labels.");
			// jl.setIcon(CSTNUEditor.infoIcon);
			jl1.setBackground(Color.orange);
			saveTNResultButton.setEnabled(true);
			cycle = 0;
			updatevvViewer();
		}
	}

	/**
	 * @author posenato
	 */
	private class CSTNUCheckListener implements ActionListener {

		CSTNUCheckListener() {
		}

		@SuppressWarnings("unchecked")
		@Override
		public final void actionPerformed(final ActionEvent e) {
			final JEditorPane jl1 = viewerMessageArea;
			saveTNResultButton.setEnabled(false);
			final TNGraph<CSTNUEdge> g1 = new TNGraph<>((TNGraph<CSTNUEdge>) inputGraph, (Class<? extends CSTNUEdge>) EdgeSupplier.DEFAULT_CSTNU_EDGE_CLASS);
			((TNGraph<CSTNUEdge>) checkedGraph).takeFrom(g1);

			cstnu = new CSTNU((TNGraph<CSTNUEdge>) checkedGraph, 30 * 60, onlyToZ);
			cstnu.setContingentAlsoAsOrdinary(contingentAlsoAsOrdinary);
			jl1.setBackground(Color.orange);
			try {
				cstnuStatus = cstnu.dynamicControllabilityCheck();
				if (cstnuStatus.consistency) {
					jl1.setText("<img align='middle' src='" + INFO_ICON_FILE + "'>&nbsp;<b>The network is dynamically controllable.</b>");
					// jl.setIcon(CSTNUEditor.infoIcon);
					jl1.setBackground(Color.green);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							TNEditor.LOG.finer("Final controllable graph: " + cstnu.getGChecked());
						}
					}
				} else {
					jl1.setText("<img align='middle' src='" + WARN_ICON_FILE + "'>&nbsp;<b>The network is not dynamically controllable.</b>");
					// jl.setIcon(CSTNUEditor.warnIcon);
				}
			} catch (final WellDefinitionException ex) {
				jl1.setText("<img align='middle' src='" + WARN_ICON_FILE + "'>&nbsp;There is a problem in the code: " + ex.getMessage());
				// jl.setIcon(CSTNUEditor.warnIcon);
			}

			saveTNResultButton.setEnabled(true);
			cycle = 0;
			updatevvViewer();
		}
	}

	/**
	 * @author posenato
	 */
	private class CSTNUOneStepListener implements ActionListener {

		CSTNUOneStepListener() {
		}

		@SuppressWarnings("unchecked")
		@Override
		public final void actionPerformed(final ActionEvent e) {
			final JEditorPane jl1 = viewerMessageArea;
			if (cycle == -1) {
				return;
			}
			if (cycle == 0) {
				final TNGraph<CSTNUEdge> g1 =
					new TNGraph<>((TNGraph<CSTNUEdge>) inputGraph, (Class<? extends CSTNUEdge>) EdgeSupplier.DEFAULT_CSTNU_EDGE_CLASS);
				((TNGraph<CSTNUEdge>) checkedGraph).takeFrom(g1);

				cstnu = new CSTNU((TNGraph<CSTNUEdge>) checkedGraph, 30 * 60, onlyToZ);
				cstnu.setContingentAlsoAsOrdinary(contingentAlsoAsOrdinary);
				mapInfoLabel.setText(checkedGraph.getEdgeFactory().toString());
				try {
					cstnu.initAndCheck();
				} catch (final Exception ex) {
					jl1.setText("<img align='middle' src='" + WARN_ICON_FILE + "'>&nbsp;There is a problem in the graph: " + ex.getMessage());
					// jl.setIcon(TNEditor.warnIcon);
					cycle = -1;
					return;
				}
				cstnuStatus = new CSTNUCheckStatus();
				edgesToCheck = new EdgesToCheck<>(checkedGraph.getEdges());
			}
			cycle++;

			jl1.setBackground(Color.orange);
			final Instant timeOut = Instant.now().plusSeconds(2700);
			cstnuStatus = (onlyToZ) ? cstnu.oneStepDynamicControllabilityLimitedToZ((EdgesToCheck<CSTNUEdge>) edgesToCheck, timeOut)
			                        : cstnu.oneStepDynamicControllability((EdgesToCheck<CSTNUEdge>) edgesToCheck, timeOut);
			cstnuStatus.finished = edgesToCheck.size() == 0;
			final boolean reductionsApplied = !cstnuStatus.finished;
			final boolean notControllable = !cstnuStatus.consistency;
			if (notControllable) {
				jl1.setText("<img align='middle' src='" + WARN_ICON_FILE + "'>&nbsp;<b>The network is not controllable.</b>");
				// jl.setIcon(TNEditor.warnIcon);
				cycle = -1;
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINE)) {
						TNEditor.LOG.fine("INCONSISTENT GRAPH: " + checkedGraph);
					}
					if (LOG.isLoggable(Level.INFO)) {
						TNEditor.LOG.info("Status stats: " + cstnuStatus);
					}
				}
			} else if (reductionsApplied) {
				jl1.setText("Step " + cycle + " of consistency check is done.");
				// jl.setIcon(TNEditor.warnIcon);
			} else {
				jl1.setText("<b>The network is dynamically controllable. The number of executed cycles is " + cycle);
				// jl.setIcon(TNEditor.infoIcon);
				cycle = -1;
				jl1.setBackground(Color.green);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.INFO)) {
						TNEditor.LOG.info("Status stats: " + cstnuStatus);
					}
				}
			}

			saveTNResultButton.setEnabled(true);
			updatevvViewer();
		}
	}

	/**
	 * @author posenato
	 */
	private class DCSemanticsListener implements ActionListener {
		final JComboBox<DCSemantics> comboBox;

		DCSemanticsListener(JComboBox<DCSemantics> comboBox1) {
			comboBox = comboBox1;
		}

		@Override
		public final void actionPerformed(ActionEvent e) {
			dcCurrentSem = (DCSemantics) comboBox.getSelectedItem();
			assert dcCurrentSem != null;
			epsilonPanel.setVisible(dcCurrentSem == DCSemantics.ε);
		}
	}

	/**
	 * @author posenato
	 */
	private class LayoutListener implements ActionListener {

		// @SuppressWarnings("unused")
		// CSTNLayout cstnLayout;

		/**
		 * Original CSTNUStaticLayout
		 */
		AbstractLayout<LabeledNode, ? extends Edge> originalLayout;

		/**
		 *
		 */
		LayoutListener() {
			originalLayout = null;
			// this.cstnLayout = null;
		}

		@SuppressWarnings("unchecked")
		@Override
		public final void actionPerformed(ActionEvent actionEvent) {
			final AbstractLayout<LabeledNode, ? extends Edge> nextLayout;
			if (layoutToggleButton.isSelected()) {
				originalLayout = layoutEditor;
				// if (this.cstnLayout == null) {
				nextLayout = new CSTNLayout((TNGraph<CSTNEdge>) inputGraph, vvEditor.getSize());
				((CSTNLayout) nextLayout).setInitialY(vvEditor.getSize().height / 2);
				((CSTNLayout) nextLayout).setCurrentLayout(originalLayout);
				nextLayout.initialize();
				// this.cstnLayout = (CSTNLayout) nextLayout;
				// } else {
				// nextLayout = this.cstnLayout;
				// }
			} else {
				nextLayout = originalLayout;
			}

			// IF one wants animation
			// Relaxer relaxer1 = new VisRunner((IterativeContext) this.currentLayout);
			// relaxer.stop();
			// relaxer.prerelax();
			// LayoutTransition<LabeledNode, Edge> lt = new LayoutTransition<>(TNEditor.this.vv1, TNEditor.this.layout1, nextLayout);
			// Animator animator = new Animator(lt);
			// animator.start();
			// ELSE one step transition
			((VisualizationViewer<LabeledNode, Edge>) vvEditor).setGraphLayout((Layout<LabeledNode, Edge>) nextLayout);
			vvEditor.repaint();
			// END iF
			layoutEditor = nextLayout;
		}

		public final Dimension getSize() {
			return layoutEditor.getSize();
		}

		/**
		 *
		 */
		public final void reset() {
			layoutToggleButton.setSelected(false);
			originalLayout = null;
			// this.cstnLayout = null;
		}
	}

	/**
	 * @author posenato
	 */
	private class NewNetworkActivation implements ActionListener {

		NewNetworkActivation() {
		}

		@Override
		public final void actionPerformed(final ActionEvent e) {
			if (userWantsToStayWithCurrentNetworkInEditor()) {
				return;
			}
			switch (e.getActionCommand()) {
				case "STNU":
					setDefaultParametersForNetwork(NetworkType.STNU);
					break;
				case "OSTNU":
					setDefaultParametersForNetwork(NetworkType.OSTNU);
					break;
				case "CSTN":
					setDefaultParametersForNetwork(NetworkType.CSTN);
					break;
				case "CSTNU":
					setDefaultParametersForNetwork(NetworkType.CSTNU);
					break;
				case "PCSTNU":
					setDefaultParametersForNetwork(NetworkType.PCSTNU);
					break;
				case "CSTNPSU":
					setDefaultParametersForNetwork(NetworkType.CSTNPSU);
					break;
				case "STN":
				default:
					setDefaultParametersForNetwork(NetworkType.STN);
					break;
			}
			inputGraphBiggerViewer.setEnabled(true);
		}
	}

	/**
	 * @author posenato
	 */
	private class OnlyToZListener implements ItemListener {

		OnlyToZListener() {
		}

		@Override
		public final void itemStateChanged(ItemEvent e) {
			onlyToZ = e.getStateChange() == ItemEvent.SELECTED;
		}
	}

	/**
	 * @author posenato
	 */
	private class OpenFileListener implements ActionListener {
		private final JFileChooser chooser;

		OpenFileListener() {
			chooser = new JFileChooser(defaultDir);
			chooser.setDragEnabled(true);
			final String msg = "The extension of the selected file determines the kind of network. Use *.stn, *.stnu, *.cstn, *" +
			                   ".cstnu, *.stnpsu, *.cstnpsu, *.pcstnu, *.ostnu";
			chooser.setToolTipText(msg);
			chooser.setApproveButtonToolTipText(msg);

			final FileNameExtensionFilter stnE =
				new FileNameExtensionFilter("(P)(C)(O)STN(PS)(U) file (.(p)(c)stn(ps)(u))", "stn", "stnu", "cstn", "cstnu", "cstnpsu", "stnpsu", "pcstnu",
				                            "ostnu");
			chooser.addChoosableFileFilter(stnE);
			chooser.setFileFilter(stnE);
			chooser.setAcceptAllFileFilterUsed(false);
		}

		@Override
		public final void actionPerformed(final ActionEvent e) {
			if (userWantsToStayWithCurrentNetworkInEditor()) {
				return;
			}
			final int option = chooser.showOpenDialog(TNEditor.this);
			final JEditorPane jl = viewerMessageArea;
			if (option == JFileChooser.APPROVE_OPTION) {
				final File file = chooser.getSelectedFile();
				defaultDir = file.getParent();
				try {
					loadGraphG(file);
					vvViewer.setVisible(false);
					((LayoutListener) layoutToggleButton.getActionListeners()[0]).reset();
					layoutToggleButton.setEnabled(true);
					saveTNResultButton.setEnabled(false);
					jl.setText("");
					jl.setOpaque(false);
					// TNEditor.this.setTitle("CSTNU Editor and Checker: " + file.getName() + "-" + TNEditor.this.g.getName());
					graphInfoLabel.setText(TNEditor.getGraphLabelDescription(inputGraph));
					mapInfoLabel.setText(inputGraph.getEdgeFactory().toString());

				} catch (IOException | ParserConfigurationException | SAXException e1) {
					inputGraph.clear();
					vvViewer.setVisible(false);
					saveTNResultButton.setEnabled(false);
					final String msg = "The graph has a problem in the definition:" + e1.getMessage();
					if (Debug.ON) {
						if (LOG.isLoggable(Level.WARNING)) {
							TNEditor.LOG.warning(msg);
						}
					}
					jl.setText("<b>" + msg + "</b>");
					// jl.setIcon(CSTNUEditor.warnIcon);
					jl.setOpaque(true);
					jl.setBackground(Color.orange);
				} finally {
					validate();
					repaint();
					cycle = 0;
				}
			}
		}
	}

	/**
	 * @author posenato
	 */
	private class SaveFileListener implements ActionListener, Serializable {
		private static class JFileChooserCustom extends JFileChooser {
			JFileChooserCustom(String defDir) {
				super(defDir);
			}

			@Override
			public final void approveSelection() {
				final File f = getSelectedFile();
				if (f.exists() && getDialogType() == SAVE_DIALOG) {
					final int result = JOptionPane.showConfirmDialog(this, "The file exists, overwrite?", "Existing file", JOptionPane.YES_NO_OPTION);
					switch (result) {
						case JOptionPane.YES_OPTION:
							super.approveSelection();
							return;
						case JOptionPane.NO_OPTION:
						case JOptionPane.CLOSED_OPTION:
						default:
							return;
					}
				}
				super.approveSelection();
			}
		}

		@Serial
		private static final long serialVersionUID = 2L;

		SaveFileListener() {
		}

//       private static final JFileChooser chooser = new JFileChooser(defaultDir) {
//            /**
//             *
//             */
//            @Serial
//            private static final long serialVersionUID = 2L;
//
//            @Override
//            public void approveSelection() {
//                File f = getSelectedFile();
//                if (f.exists() && getDialogType() == SAVE_DIALOG) {
//                    int result = JOptionPane.showConfirmDialog(this, "The file exists, overwrite?", "Existing file",
//                            JOptionPane.YES_NO_OPTION);
//                    switch (result) {
//                        case JOptionPane.YES_OPTION:
//                            super.approveSelection();
//                            return;
//                        case JOptionPane.NO_OPTION:
//                        case JOptionPane.CLOSED_OPTION:
//                        default:
//                            return;
//                    }
//                }
//                super.approveSelection();
//            }
//        };

		@Override
		public final void actionPerformed(final ActionEvent e) {
			// TNEditor.LOG.finest("Path wanted:" + path);
			final JFileChooser chooser = new JFileChooserCustom(defaultDir);
			boolean saved = false;
			while (!saved) {
				final int option = chooser.showSaveDialog(TNEditor.this);
				if (option == JFileChooser.CANCEL_OPTION) {
					break;
				}
				if (option == JFileChooser.APPROVE_OPTION) {
					final File file = chooser.getSelectedFile();
					defaultDir = file.getParent();
					try {
						saveGraphToFile(inputGraph, file);
						saved = true;
					} catch (IOException e1) {
						JOptionPane.showMessageDialog(vvViewer, "The selected file cannot be used for saving the graph");
					}
				}
			}
		}

	}

	/**
	 * @author posenato
	 */
	private class STNConsistencyCheckListener implements ActionListener {

		STNConsistencyCheckListener() {
		}

		@SuppressWarnings("unchecked")
		@Override
		public final void actionPerformed(final ActionEvent e) {
			final JEditorPane jl = viewerMessageArea;
			saveTNResultButton.setEnabled(false);

			final TNGraph<STNEdge> g1 = new TNGraph<>((TNGraph<STNEdge>) inputGraph, (Class<? extends STNEdge>) EdgeSupplier.DEFAULT_STN_EDGE_CLASS);
			((TNGraph<STNEdge>) checkedGraph).takeFrom(g1);

			mapInfoLabel.setText(inputGraph.getEdgeFactory().toString());
			stn = new STN((TNGraph<STNEdge>) checkedGraph);
			stn.setDefaultConsistencyCheckAlg(stnCheckAlg);
			stn.setOutputCleaned(cleanResult);

			try {
				stn.initAndCheck();
			} catch (final Exception ec) {
				final String msg = "The network has a problem, and it cannot be initialized: " + ec.getMessage();
				if (Debug.ON) {
					if (LOG.isLoggable(Level.WARNING)) {
						TNEditor.LOG.warning(msg);
					}
				}
				jl.setText("<img align='middle' src='" + WARN_ICON_FILE + "'>&nbsp;<b>" + msg + "</b>");
				// jl.setIcon(TNEditor.warnIcon);
				jl.setOpaque(true);
				jl.setBackground(Color.orange);
				// TNEditor.this.vv2.validate();
				// TNEditor.this.vv2.repaint();
				validate();
				repaint();
				return;
			}

			if (stnCheckAlg == STN.CheckAlgorithm.Dijkstra && stn.getMinNegativeWeight() < 0) {
				jl.setText("<img align='middle' src='" + WARN_ICON_FILE +
				           "'>&nbsp;<b>Dijkstra algorithm cannot be applied to a network having edges with negative values.</b>");
				// jl.setIcon(TNEditor.warnIcon);
				jl.setOpaque(true);
				jl.setBackground(Color.orange);
				// TNEditor.this.vv2.validate();
				// TNEditor.this.vv2.repaint();
				validate();
				repaint();
				return;
			}

			jl.setBackground(Color.orange);
			stnStatus = stn.consistencyCheck();
			if (stnStatus.consistency) {

				jl.setText("<img align='middle' src='" + INFO_ICON_FILE + "'>&nbsp;<b>The network is consistent.");
				jl.setBackground(Color.green);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						TNEditor.LOG.finest("Final controllable graph: " + stn.getGChecked());
					}
				}
			} else {
				jl.setText("<img align='middle' src='" + WARN_ICON_FILE + "'>&nbsp;<b>The network is not consistent.</b>");
				final ObjectList<LabeledNode> negativeCycle = stnStatus.negativeCycle;
				if (negativeCycle != null) {
					jl.setText("<img align='middle' src='" + WARN_ICON_FILE +
					           "'>&nbsp;<b>The network is not consistent. The found negative cycle is in red color.</b>");
					// draw edge in negative cycle by red
					for (int i = 0; i < negativeCycle.size() - 1; i++) {
						final LabeledNode s = negativeCycle.get(i);
						final LabeledNode d = negativeCycle.get(i + 1);
						s.setInNegativeCycle(true);
						d.setInNegativeCycle(true);

						final STNEdge edge = (STNEdge) checkedGraph.findEdge(s, d);
						assert edge != null;
						edge.setInNegativeCycle(true);
					}
				}
				if (stnStatus.negativeLoopNode != null) {
					stnStatus.negativeLoopNode.setInNegativeCycle(true);
				}
			}
			cycle = 0;
			updatevvViewer();
		}
	}

	/**
	 * @author posenato
	 */
	private class STNFastDispatchableListener implements ActionListener {
		STNFastDispatchableListener() {
		}

		@SuppressWarnings("unchecked")
		@Override
		public final void actionPerformed(final ActionEvent e) {
			final JEditorPane jl = viewerMessageArea;
			saveTNResultButton.setEnabled(false);

			final TNGraph<STNEdge> g1 = new TNGraph<>((TNGraph<STNEdge>) inputGraph, (Class<? extends STNEdge>) EdgeSupplier.DEFAULT_STN_EDGE_CLASS);
			((TNGraph<STNEdge>) checkedGraph).takeFrom(g1);

			mapInfoLabel.setText(inputGraph.getEdgeFactory().toString());
			stn = new STN((TNGraph<STNEdge>) checkedGraph);
			stn.setDefaultConsistencyCheckAlg(stnCheckAlg);
			stn.setOutputCleaned(cleanResult);

			jl.setBackground(Color.orange);
			final boolean status = stn.makeMinimalDispatchable();
			if (status) {
				jl.setText("<img align='middle' src='" + INFO_ICON_FILE + "'>&nbsp;<b>The network is dispatchable.");
				jl.setBackground(Color.green);
			} else {
				jl.setText("<img align='middle' src='" + WARN_ICON_FILE +
				           "'>&nbsp;<b>The network is not consistent and, therefore, it cannot be made dispatchable.</b>");
			}
			cycle = 0;
			updatevvViewer();
		}
	}


	/**
	 * @author posenato
	 */
	private class STNInitListener implements ActionListener {

		STNInitListener() {
		}

		@SuppressWarnings("unchecked")
		@Override
		public final void actionPerformed(final ActionEvent e) {
			final JEditorPane jl = viewerMessageArea;
			final TNGraph<STNEdge> g1 = new TNGraph<>((TNGraph<STNEdge>) inputGraph, (Class<? extends STNEdge>) EdgeSupplier.DEFAULT_STN_EDGE_CLASS);
			((TNGraph<STNEdge>) checkedGraph).takeFrom(g1);

			mapInfoLabel.setText(inputGraph.getEdgeFactory().toString());
			stn = new STN((TNGraph<STNEdge>) checkedGraph);
			try {
				stn.initAndCheck();
			} catch (final Exception ec) {
				final String msg = "The graph has a problem, and it cannot be initialized: " + ec.getMessage();
				if (Debug.ON) {
					if (LOG.isLoggable(Level.WARNING)) {
						TNEditor.LOG.warning(msg);
					}
				}
				jl.setText("<img align='middle' src='" + WARN_ICON_FILE + "'>&nbsp;<b>" + msg + "</b>");
				// jl.setIcon(TNEditor.warnIcon);
				jl.setOpaque(true);
				jl.setBackground(Color.orange);
				// TNEditor.this.vv2.validate();
				// TNEditor.this.vv2.repaint();
				validate();
				repaint();
				return;
			}
			jl.setText("STN initialized.");
			// jl.setIcon(TNEditor.infoIcon);
			jl.setBackground(Color.orange);
			cycle = 0;
			updatevvViewer();
		}
	}

	/**
	 * @author posenato
	 */
	private class STNPredecessorGraphListener implements ActionListener {

		STNPredecessorGraphListener() {
		}

		@SuppressWarnings("unchecked")
		@Override
		public final void actionPerformed(final ActionEvent e) {
			final JEditorPane jl = viewerMessageArea;
			saveTNResultButton.setEnabled(false);

			mapInfoLabel.setText(inputGraph.getEdgeFactory().toString());
			stn = new STN((TNGraph<STNEdge>) inputGraph);
			stn.setOutputCleaned(cleanResult);

			LabeledNode node = null;
			while (node == null) {
				final LabeledNode[] nodes = stn.getG().getVerticesArray();
				node =
					(LabeledNode) JOptionPane.showInputDialog(rowForSTNButtons, "Chose the source node:", "Customized Dialog", JOptionPane.PLAIN_MESSAGE, null,
					                                          nodes, "Z");
			}
			jl.setBackground(Color.orange);
			final TNGraph<STNEdge> g1 = stn.GET_STN_PREDECESSOR_SUBGRAPH(node);
			if (g1 != null) {
				((TNGraph<STNEdge>) checkedGraph).takeFrom(g1);
				jl.setText("<img align='middle' src='" + INFO_ICON_FILE + "'>&nbsp;<b>Predecessor subgraph of " + node.getName() + ".");
				jl.setBackground(Color.green);
			} else {
				jl.setText("<img align='middle' src='" + WARN_ICON_FILE +
				           "'>&nbsp;<b>The network is not consistent and, therefore, it is not possible to find the predecessor subgraph.</b>");
			}
			cycle = 0;
			updatevvViewer();
		}
	}

	/**
	 * @author posenato
	 */
	private class STNUControllabilityCheckListener implements ActionListener {

		STNUControllabilityCheckListener() {
		}

		@SuppressWarnings("unchecked")
		@Override
		public final void actionPerformed(final ActionEvent e) {
			final JEditorPane jl = viewerMessageArea;
			saveTNResultButton.setEnabled(false);
//			ordinaryTNResultButton.setEnabled(false);
			final TNGraph<STNUEdge> g1 = new TNGraph<>((TNGraph<STNUEdge>) inputGraph, (Class<? extends STNUEdge>) EdgeSupplier.DEFAULT_STNU_EDGE_CLASS);
			((TNGraph<STNUEdge>) checkedGraph).takeFrom(g1);

			mapInfoLabel.setText(inputGraph.getEdgeFactory().toString());
			stnu = new STNU((TNGraph<STNUEdge>) checkedGraph);
			stnu.setDefaultControllabilityCheckAlg(stnuCheckAlg);
			stnu.setOutputCleaned(cleanResult);

			jl.setBackground(Color.orange);
			try {
				stnuStatus = stnu.dynamicControllabilityCheck();
				if (stnuStatus.isControllable()) {

					jl.setText("<img align='middle' src='" + INFO_ICON_FILE + "'>&nbsp;<b>The network is dynamically controllable.");
					jl.setBackground(Color.green);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							TNEditor.LOG.finer("Final controllable graph: " + stnu.getGChecked());
						}
					}
				} else {
					jl.setText("<img align='middle' src='" + WARN_ICON_FILE + "'>&nbsp;<b>The network is not controllable.</b>");
					final STNUCheckStatus.SRNCInfo SRNCInfo = stnuStatus.getNegativeSTNUCycleInfo(false, stnu.getG());
					if (SRNCInfo != null) {
						final ObjectList<STNUEdge> srnPath = SRNCInfo.srnc();
						// draw edge in negative cycle by red
						final StringBuilder srnString = new StringBuilder("⟨");
						for (final STNUEdge edge : srnPath) {
//							final LabeledNode s = negativeCycle.get(i);
//							final LabeledNode d = negativeCycle.get(i + 1);
//							s.setInNegativeCycle(true);
//							d.setInNegativeCycle(true);
							edge.setInNegativeCycle(true);
							srnString.append(edge.getName()).append(", ");
						}
						if (srnString.length() >= 2) {
							srnString.replace(srnString.length() - 2, srnString.length(), "⟩");
						} else {
							srnString.append("⟩");
						}
						jl.setText("<img align='middle' src='" + WARN_ICON_FILE +
						           "'>&nbsp;<b>The network is not dynamic controllable. The found negative cycle has value " + SRNCInfo.value() +
						           "  and edges <span style='color: red'>"
						           + srnString + "</span> (in red color).</b>");
					}

				}
			} catch (final WellDefinitionException ex) {
				jl.setText("There is a problem in the code: " + ex.getMessage());
			}
			saveTNResultButton.setEnabled(true);
//			ordinaryTNResultButton.setEnabled(true);
			cycle = 0;
			updatevvViewer();
		}
	}

	/**
	 * @author posenato
	 */
	private class OSTNUControllabilityCheckListener implements ActionListener {

		OSTNUControllabilityCheckListener() {
		}

		@SuppressWarnings("unchecked")
		@Override
		public final void actionPerformed(final ActionEvent e) {
			final JEditorPane jl = viewerMessageArea;
			saveTNResultButton.setEnabled(false);
//			ordinaryTNResultButton.setEnabled(false);
			final TNGraph<OSTNUEdgePluggable> g1 = new TNGraph<>((TNGraph<OSTNUEdgePluggable>) inputGraph, EdgeSupplier.DEFAULT_OSTNU_EDGE_CLASS);
			((TNGraph<OSTNUEdgePluggable>) checkedGraph).takeFrom(g1);

			mapInfoLabel.setText(inputGraph.getEdgeFactory().toString());
			ostnu = new OSTNU((TNGraph<OSTNUEdgePluggable>) checkedGraph);
			ostnu.setOutputCleaned(cleanResult);

			jl.setBackground(Color.orange);
			try {
				ostnuStatus = ostnu.agileControllabilityCheck();
				if (ostnuStatus.isControllable()) {
					jl.setText("<img align='middle' src='" + INFO_ICON_FILE + "'>" + "&nbsp;<b>The network is dynamically controllable.</b> " +
					           "&nbsp;Negative scenarios: " + ostnuStatus.getNegativeScenarios() + "&nbsp;Proposition mapping: " +
					           ostnuStatus.propositionOfPair);
					jl.setBackground(Color.green);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							TNEditor.LOG.finer("Final controllable graph: " + stnu.getGChecked());
						}
					}
				} else {
					jl.setText("<img align='middle' src='" + WARN_ICON_FILE + "'>&nbsp;<b>The network is not controllable.</b>");
				}
			} catch (final WellDefinitionException ex) {
				jl.setText("There is a problem in the code: " + ex.getMessage());
			}
			saveTNResultButton.setEnabled(true);
//			ordinaryTNResultButton.setEnabled(true);
			cycle = 0;
			updatevvViewer();
		}
	}

	/**
	 * @author posenato
	 */
	private class STNUDispatchabilityCheckListener implements ActionListener {

		STNUDispatchabilityCheckListener() {
		}

		@SuppressWarnings("unchecked")
		@Override
		public final void actionPerformed(final ActionEvent e) {
			final JEditorPane jl = viewerMessageArea;
			saveTNResultButton.setEnabled(false);

			final TNGraph<STNUEdge> g1 = new TNGraph<>((TNGraph<STNUEdge>) inputGraph, (Class<? extends STNUEdge>) EdgeSupplier.DEFAULT_STNU_EDGE_CLASS);
			((TNGraph<STNUEdge>) checkedGraph).takeFrom(g1);

			mapInfoLabel.setText(inputGraph.getEdgeFactory().toString());
			stnu = new STNU((TNGraph<STNUEdge>) checkedGraph);
			stnu.setOutputCleaned(cleanResult);
			if (stnuCheckAlg != STNU.CheckAlgorithm.Morris2014Dispatchable && stnuCheckAlg != STNU.CheckAlgorithm.FD_STNU &&
			    stnuCheckAlg != STNU.CheckAlgorithm.FD_STNU_IMPROVED) {
				//stnuCheckAlg is updated by combo-box listener
				//mindiaspatchability can work only with Morris2014 or FD_STNU
				stnuCheckAlgComboSelect.setSelectedItem(STNU.CheckAlgorithm.FD_STNU_IMPROVED);
			}
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("Checking algorithm for minDispatchESTNU: " + stnuCheckAlg);
			}
			stnu.setDefaultControllabilityCheckAlg(stnuCheckAlg);

			jl.setBackground(Color.orange);
			try {
				boolean isDispatchable = false;
				stnuStatus = stnu.dynamicControllabilityCheck();
				if (stnuStatus.isControllable()) {
					isDispatchable = stnu.applyMinDispatchableESTNU();
				}
				if (stnuStatus.isControllable() && isDispatchable) {
					jl.setText("<img align='middle' src='" + INFO_ICON_FILE + "'>&nbsp;<b>The network was made dispatchable by " + stnuCheckAlg +
					           " and, then, minimized.");
					jl.setBackground(Color.green);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							TNEditor.LOG.finer("Final dispatchable graph: " + stnu.getGChecked());
						}
					}
				} else {
					if (!stnuStatus.isControllable()) {
						jl.setText("<img align='middle' src='" + WARN_ICON_FILE + "'>&nbsp;<b>The network is not controllable.</b>");
					} else {
						jl.setText("<img align='middle' src='" + WARN_ICON_FILE +
						           "'>&nbsp;<b>The network is dynamically controllable but there was a problem to make it dispatchable.</b>");
					}
				}
			} catch (final WellDefinitionException ex) {
				jl.setText("There is a problem in the code: " + ex.getMessage());
			}
			cycle = 0;
			saveTNResultButton.setEnabled(true);
			updatevvViewer();
		}
	}

	/**
	 * @author posenato
	 */
	private class STNUInitListener implements ActionListener {

		STNUInitListener() {
		}

		@SuppressWarnings("unchecked")
		@Override
		public final void actionPerformed(final ActionEvent e) {
			final JEditorPane jl = viewerMessageArea;
			final TNGraph<STNUEdge> g1 = new TNGraph<>((TNGraph<STNUEdge>) inputGraph, (Class<? extends STNUEdge>) EdgeSupplier.DEFAULT_STNU_EDGE_CLASS);
			((TNGraph<STNUEdge>) checkedGraph).takeFrom(g1);

			mapInfoLabel.setText(inputGraph.getEdgeFactory().toString());
			stnu = new STNU((TNGraph<STNUEdge>) checkedGraph);
			try {
				stnu.initAndCheck();
			} catch (final Exception ec) {
				final String msg = "The graph has a problem, and it cannot be initialized: " + ec.getMessage();
				if (Debug.ON) {
					if (LOG.isLoggable(Level.WARNING)) {
						TNEditor.LOG.warning(msg);
					}
				}
				jl.setText("<img align='middle' src='" + WARN_ICON_FILE + "'>&nbsp;<b>" + msg + "</b>");
				// jl.setIcon(TNEditor.warnIcon);
				jl.setOpaque(true);
				jl.setBackground(Color.orange);
				// TNEditor.this.vv2.validate();
				// TNEditor.this.vv2.repaint();
				validate();
				repaint();
				return;
			}
			jl.setText("STNU initialized.");
			// jl.setIcon(TNEditor.infoIcon);
			cycle = 0;
			saveTNResultButton.setEnabled(true);
			jl.setBackground(Color.orange);
			updatevvViewer();
		}
	}

	/**
	 * TNGraph structures necessary to represent derived graph.
	 */
	final TNGraph<? extends Edge> checkedGraph;
	/**
	 * TNGraph structures necessary to represent input graph.
	 */
	final TNGraph<? extends Edge> inputGraph;
	/**
	 * The current wanted semantics
	 */
	public DCSemantics dcCurrentSem = DCSemantics.Std;
	/**
	 * Cleaned result. True to store a cleaned result
	 */
	@Option(name = "-cleaned", usage = "Show a cleaned result. A cleaned graph does not contain empty edges or labeled values containing unknown literals.")
	boolean cleanResult;
	/**
	 * True if contingent link as to be represented also as ordinary constraints.
	 */
	@Option(name = "-ctgAsOrdinary", usage = "Manage contingent link also as ordinary constraints. It is not necessary, but it helps to find some stricter upper bounds.")
	boolean contingentAlsoAsOrdinary = true;
	/**
	 *
	 */
	JPanel controlSouthPanel;
	/**
	 * CSTN checker
	 */
	CSTN cstn;
	/**
	 * Which check alg for CSTN
	 */
	CheckAlgorithm cstnCheckAlg = CheckAlgorithm.HunsbergerPosenato20;
	/**
	 * Drop-down list for selecting CSTN CheckingAlgorithm
	 */
	JComboBox<CheckAlgorithm> checkingAlgCSTNComboBox;
	/**
	 * semantic combo for CSTN
	 */
	JComboBox<DCSemantics> cstnDCSemanticsComboBox;
	/**
	 * CSTNPSU checker
	 */
	CSTNPSU cstnpsu;
	/**
	 * PCSTNU checker
	 */
	PCSTNU pcstnu;
	/**
	 * CSTN check status
	 */
	CSTNCheckStatus cstnStatus;
	/**
	 * CSTNU checker
	 */
	CSTNU cstnu;
	/**
	 * CSTNU2CSTN checker
	 */
	CSTNU2CSTN cstnu2cstn;
	/**
	 * CSTNU check status
	 */
	CSTNUCheckStatus cstnuStatus;
	/**
	 * Current edge implementation class
	 */
	Class<? extends Edge> currentEdgeImpl;
	/**
	 * The kind of network the system is currently showing
	 */
	@SuppressWarnings("unused")
	NetworkType currentTNGraphType;
	/**
	 * Number of cycles of CSTN(U) check step-by-step
	 */
	int cycle;
	/**
	 * Default load/save directory
	 */
	String defaultDir = "./";
	/**
	 * Edges to check in CSTN(U) check step-by-step
	 */
	EdgesToCheck<? extends Edge> edgesToCheck;
	/**
	 * The epsilon panel
	 */
	JPanel epsilonPanel;
	/**
	 * The graph info label
	 */
	JLabel graphInfoLabel;
	/**
	 * Button for input network bigger viewer
	 */
	JButton inputGraphBiggerViewer;
	/**
	 * Button for derived network bigger viewer
	 */
	JButton derivedGraphBiggerViewer;
	/**
	 * Layout for input graph.
	 */
	AbstractLayout<LabeledNode, ? extends Edge> layoutEditor;
	/**
	 * Button for re-layout input graph
	 */
	JToggleButton layoutToggleButton;
	/**
	 * Layout for derived graph.
	 */
	final AbstractLayout<LabeledNode, ? extends Edge> layoutViewer;
	/**
	 *
	 */
	JLabel mapInfoLabel;
	/**
	 * Position of the mode box for the main editor
	 */
	int modeBoxIndex;
	/**
	 * Position of the mode box for the viewer
	 */
	int modeBoxViewerIndex;
	/**
	 * TNGraph structures necessary to represent an auxiliary graph.
	 */
	TNGraph<? extends Edge> oneStepBackGraph;
	/**
	 * OnlyToZ says if the DC checking has to be made propagating constraints only to time-point Z
	 */
	boolean onlyToZ = true;
	/**
	 *
	 */
	JCheckBox onlyToZCB;
	/**
	 * the preferred sizes for the two views
	 */
	final Dimension preferredSize;// = new Dimension(780, 768);
	/**
	 * Reaction time for CSTN
	 */
	int reactionTime = 1;
	/**
	 *
	 */
	JPanel rowForAppButtons;
	/**
	 *
	 */
	JPanel rowForSTNButtons;
	/**
	 *
	 */
	JPanel rowForSTNUButtons;
	/**
	 *
	 */
	JPanel rowForOSTNUButtons;
	/**
	 * Result Save Button
	 */
	JButton saveTNResultButton;

//	JButton ordinaryTNResultButton;

	/**
	 * STN checker
	 */
	STN stn;
	/**
	 * Which check alg to use for STN
	 */
	STN.CheckAlgorithm stnCheckAlg = STN.CheckAlgorithm.AllPairsShortestPaths;
	/**
	 * STN check status
	 */
	STNCheckStatus stnStatus;
	/**
	 * STNU checker
	 */
	STNU stnu;
	/**
	 * OSTNU checker
	 */
	OSTNU ostnu;
	/**
	 * Which check alg to use for STNU
	 */
	STNU.CheckAlgorithm stnuCheckAlg = STNU.CheckAlgorithm.FD_STNU_IMPROVED;
	/**
	 * Swing combo select for the stnuCheckAlg.
	 */
	JComboBox<STNU.CheckAlgorithm> stnuCheckAlgComboSelect;
	/**
	 * STNU check status
	 */
	STNUCheckStatus stnuStatus;

	/**
	 * OSTNU check status
	 */
	OSTNUCheckStatus ostnuStatus;

	/**
	 * Validation panel for CSTN row
	 */
	ValidationPanel validationPanelCSTN;
	/**
	 * Validation panel for CSTNU row
	 */
	ValidationPanel validationPanelCSTNU;
	/**
	 * Validation panel for CSTNPSU row
	 */
	ValidationPanel validationPanelCSTNPSU;
	/**
	 * Message area above the derived (no input) graph.
	 */
	JEditorPane viewerMessageArea;
	/**
	 * The BasicVisualizationServer&lt;V,E&gt; for input graph.
	 */
	final VisualizationViewer<LabeledNode, ? extends Edge> vvEditor;

	/*
	 * with unknown literal
	 * boolean withUnknown = true;
	 */
	/**
	 * The BasicVisualizationServer&lt;V,E&gt; for derived graph.
	 */
	final VisualizationViewer<LabeledNode, ? extends Edge> vvViewer;

	/**
	 * Some buttons have meaning only for some contexts. The default is not to show.
	 */
	@Option(name = "-extraButtons", usage = "To see some extra buttons for some special feature (development mode).")
	boolean extraButtons;

	/**
	 * Initializes the fundamental fields. The initialization of the rest of fields and the starting of GUI is made by {@link #init()} method, after that
	 * possible input parameter are read.
	 */
	public TNEditor() {
		super("TNEditor " + VERSION);
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		final GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
		final Rectangle bounds = env.getMaximumWindowBounds();
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Screen Bounds: " + bounds);
			}
		}

		// Using a null input TNGraph for setting all graphical aspects.
		// When the graph will be load, inputGraph will be updated copying all the graph inside it (takeFrom method).
		inputGraph = new TNGraph<>("", EdgeSupplier.DEFAULT_CSTNU_EDGE_CLASS);
		checkedGraph = new TNGraph<>("", EdgeSupplier.DEFAULT_CSTNU_EDGE_CLASS);

		preferredSize = new Dimension((bounds.width - 30) / 2, bounds.height - 260);
		layoutEditor = new CSTNUStaticLayout<>(inputGraph);
		layoutViewer = new CSTNUStaticLayout<>(checkedGraph);
		vvEditor = new VisualizationViewer<>(layoutEditor, preferredSize);
		vvEditor.setName(TNEditor.EDITOR_NAME);
		vvViewer = new VisualizationViewer<>(layoutViewer, preferredSize);
		vvViewer.setName(TNEditor.DISTANCE_VIEWER_NAME);
	}

	/**
	 * Initialize all others component of the GUI using the parameter values passed by
	 */
	@SuppressWarnings("MagicConstant")
	public final void init() {
		// buildRenderContext(this.vvEditor, true);
		// buildRenderContext(this.vvViewer, false);
		// content is the canvas of the application.
		final Container contentPane = getContentPane();

		// NORTH
		// I put a row for messages: since there will 2 graphs, the row contains two columns,
		// corresponding to the two graphs.
		final JPanel messagePanel = new JPanel(new GridLayout(1, 2));
		graphInfoLabel = new JLabel("  ");
		graphInfoLabel.setHorizontalAlignment(SwingConstants.CENTER);
		mapInfoLabel = new JLabel("  ");
		mapInfoLabel.setHorizontalAlignment(SwingConstants.CENTER);
		// Info for first graph
		JPanel message1Graph = new JPanel(new GridLayout(2, 1));
		message1Graph.setBackground(new Color(253, 253, 253));
		message1Graph.add(graphInfoLabel);
		message1Graph.add(mapInfoLabel);
		messagePanel.add(message1Graph);

		message1Graph = new JPanel(new GridLayout(1, 1));// even if in the second cell there is only one element, derivedGraphMessageArea, a JPanel is necessary
		// to have the same padding of first cell.
		viewerMessageArea = new JEditorPane("text/html", "");
		viewerMessageArea.setBorder(new EmptyBorder(2, 2, 2, 2));
		viewerMessageArea.setEditable(false);
		viewerMessageArea.setVisible(true);
		message1Graph.add(viewerMessageArea);
		messagePanel.add(message1Graph);

		contentPane.add(messagePanel, BorderLayout.NORTH);

		// USING WEST AND EAST zone results that, after a resize, the central panel may be displayed, and it is ugly.
		// LEFT contentPane.add(new GraphZoomScrollPane(this.vv1), BorderLayout.WEST);
		// RIGHT contentPane.add(new GraphZoomScrollPane(this.vv2), BorderLayout.EAST);
		// USING CENTER is better even if it requires a new layout
		final JPanel centralPanel = new JPanel(new GridLayout(1, 2));
		centralPanel.add(new GraphZoomScrollPane(vvEditor));// GraphZoomScrollPane is necessary to show border!
		centralPanel.add(new GraphZoomScrollPane(vvViewer));
		contentPane.add(centralPanel, BorderLayout.CENTER);

		// SOUTH
		controlSouthPanel = new JPanel(new GridLayout(2, 1));// one row for AppButtons, one for STN or for STNU
		// and one for validationPanelCSTN or for validationPanelCSTNU
		rowForAppButtons = new JPanel();
		controlSouthPanel.add(rowForAppButtons, 0);// for tuning application
		contentPane.add(controlSouthPanel, BorderLayout.SOUTH);

		rowForSTNButtons = new JPanel();
		rowForSTNButtons.setBorder(BorderFactory.createLineBorder(getForeground(), 1));
		rowForSTNUButtons = new JPanel();
		rowForSTNUButtons.setBorder(BorderFactory.createLineBorder(getForeground(), 1));

		rowForOSTNUButtons = new JPanel();
		rowForOSTNUButtons.setBorder(BorderFactory.createLineBorder(getForeground(), 1));

		final JPanel rowForCSTNButtons = new JPanel();
		validationPanelCSTN = new ValidationPanel();
		final ValidationGroup validationGroupCSTN = validationPanelCSTN.getValidationGroup();
		validationPanelCSTN.setInnerComponent(rowForCSTNButtons);
		validationPanelCSTN.setBorder(BorderFactory.createLineBorder(getForeground(), 1));

		final JPanel rowForCSTNUButtons = new JPanel();
		validationPanelCSTNU = new ValidationPanel();
		validationPanelCSTNU.setInnerComponent(rowForCSTNUButtons);
		validationPanelCSTNU.setBorder(BorderFactory.createLineBorder(getForeground(), 1));

		final JPanel rowForCSTNPSUButtons = new JPanel();
		validationPanelCSTNPSU = new ValidationPanel();
		validationPanelCSTNPSU.setInnerComponent(rowForCSTNPSUButtons);
		validationPanelCSTNPSU.setBorder(BorderFactory.createLineBorder(getForeground(), 1));

		// FIRST ROW OF COMMANDS
		// mode box for the editor
		rowForAppButtons.add(new JComboBox<>());// the real ComboBox is added after the initialization.
		modeBoxIndex = 0;

		layoutToggleButton = new JToggleButton("Layout input graph");
		layoutToggleButton.setEnabled(false);
		layoutToggleButton.addActionListener(new LayoutListener());
		if (extraButtons) {
			// I create this.layoutToggleButton in any case because it is manipulated in many places...
			rowForAppButtons.add(layoutToggleButton);
		}
		inputGraphBiggerViewer = new JButton(INPUT_GRAPH_BIG_VIEWER_NAME);
		inputGraphBiggerViewer.setEnabled(false);
		inputGraphBiggerViewer.addActionListener(new BigViewerListener(true));
		rowForAppButtons.add(inputGraphBiggerViewer);

		// AnnotationControls<LabeledNode,Edge> annotationControls =
		// new AnnotationControls<LabeledNode,Edge>(gm.getAnnotatingPlugin());
		// controls.add(annotationControls.getAnnotationsToolBar());

		// final JRadioButton excludeR1R2Button = new JRadioButton("R1 and R2 rule disabled", this.excludeR1R2);
		// excludeR1R2Button.addItemListener(new ItemListener() {
		// @Override
		// public void itemStateChanged(final ItemEvent ev) {
		// if (ev.getStateChange() == ItemEvent.SELECTED) {
		// TNEditor.this.excludeR1R2 = true;
		// TNEditor.LOG.fine("excludeR1R2 flag set to true");
		// } else if (ev.getStateChange() == ItemEvent.DESELECTED) {
		// TNEditor.this.excludeR1R2 = false;
		// TNEditor.LOG.fine("excludeR1R2 flag set to false");
		// }
		// }
		// });
		// rowForAppButtons.add(excludeR1R2Button);

		derivedGraphBiggerViewer = new JButton(DERIVED_GRAPH_BIG_VIEWER_NAME);
		derivedGraphBiggerViewer.setEnabled(false);
		derivedGraphBiggerViewer.addActionListener(new BigViewerListener(false));
		rowForAppButtons.add(derivedGraphBiggerViewer);

		// mode box for the distance viewer
		rowForAppButtons.add(new JComboBox<>());
		modeBoxViewerIndex = rowForAppButtons.getComponentCount() - 1;

		saveTNResultButton = new JButton("Save resulting network STNU");
		saveTNResultButton.setEnabled(false);
		saveTNResultButton.addActionListener(new TNSaveListener());
		rowForAppButtons.add(saveTNResultButton);
//		if (extraButtons) {
//			ordinaryTNResultButton = new JButton("Show STN subnetwork STNU");
//			ordinaryTNResultButton.setEnabled(false);
//			ordinaryTNResultButton.addActionListener(new TNOrdinaryListener());
//			rowForAppButtons.add(ordinaryTNResultButton);
//		}

		final HelpListener help = new HelpListener(this);

		JButton buttonCheck = new JButton("Help");
		buttonCheck.addActionListener(help);
		buttonCheck.setActionCommand(HelpListener.AvailableHelp.GenericHelp.toString());
		rowForAppButtons.add(buttonCheck);

		// SECOND ROW OF COMMANDS
		// ROW FOR STNs
		rowForSTNButtons.add(new JLabel("Checking Algorithm: "));
		final JComboBox<STN.CheckAlgorithm> cAlgCombo = new JComboBox<>(STN.CheckAlgorithm.values());
		cAlgCombo.setSelectedItem(stnCheckAlg);
		cAlgCombo.addActionListener(new CheckAlgListener<>(cAlgCombo));
		rowForSTNButtons.add(cAlgCombo);

		buttonCheck = new JButton("Init");
		buttonCheck.addActionListener(new STNInitListener());
		rowForSTNButtons.add(buttonCheck);

		buttonCheck = new JButton("Consistency");
		buttonCheck.addActionListener(new STNConsistencyCheckListener());
		rowForSTNButtons.add(buttonCheck);

		buttonCheck = new JButton("Fast Dispatchable");
		buttonCheck.addActionListener(new STNFastDispatchableListener());
		rowForSTNButtons.add(buttonCheck);

		buttonCheck = new JButton("PredecessorSubGraph");
		buttonCheck.addActionListener(new STNPredecessorGraphListener());
		rowForSTNButtons.add(buttonCheck);

		buttonCheck = new JButton("STN Help");
		buttonCheck.addActionListener(help);
		buttonCheck.setActionCommand(HelpListener.AvailableHelp.STNHelp.toString());
		rowForSTNButtons.add(buttonCheck);

		// ROW FOR STNUs
		rowForSTNUButtons.add(new JLabel("Checking Algorithm: "));
		this.stnuCheckAlgComboSelect = new JComboBox<>(STNU.CheckAlgorithm.values());
		this.stnuCheckAlgComboSelect.setSelectedItem(stnuCheckAlg);
		this.stnuCheckAlgComboSelect.addActionListener(new CheckAlgListener<>(this.stnuCheckAlgComboSelect));
		rowForSTNUButtons.add(this.stnuCheckAlgComboSelect);


		buttonCheck = new JButton("Init");
		buttonCheck.addActionListener(new STNUInitListener());
		rowForSTNUButtons.add(buttonCheck);

		buttonCheck = new JButton("Controllability");
		buttonCheck.addActionListener(new STNUControllabilityCheckListener());
		rowForSTNUButtons.add(buttonCheck);

		buttonCheck = new JButton("Dispatchability");
		buttonCheck.addActionListener(new STNUDispatchabilityCheckListener());
		rowForSTNUButtons.add(buttonCheck);

		buttonCheck = new JButton("STNU Help");
		buttonCheck.addActionListener(help);
		buttonCheck.setActionCommand(HelpListener.AvailableHelp.STNUHelp.toString());
		rowForSTNUButtons.add(buttonCheck);

		// ROW FOR OSTNUs
		buttonCheck = new JButton("Oracle Agile Controllability");
		buttonCheck.addActionListener(new OSTNUControllabilityCheckListener());
		rowForOSTNUButtons.add(buttonCheck);

		buttonCheck = new JButton("OSTNU Help");
		buttonCheck.addActionListener(help);
		buttonCheck.setActionCommand(HelpListener.AvailableHelp.OSTNUHelp.toString());
		rowForOSTNUButtons.add(buttonCheck);

		// ROW FOR CSTNs
		// JCheckBox withUnknown = new JCheckBox("With unknown literals");
		// withUnknown.setSelected(this.withUnknown);
		// withUnknown.addItemListener(new ItemListener() {
		// @Override
		// public void itemStateChanged(ItemEvent e) {
		// TNEditor.this.withUnknown = e.getStateChange() == ItemEvent.SELECTED;
		// }
		// });
		// rowForCSTNButtons.add(withUnknown);
		cstnDCSemanticsComboBox = new JComboBox<>(DCSemantics.values());// this panel must be declared here because used by checkingAlgCSTNComboBox
		epsilonPanel = new JPanel(new FlowLayout());// this panel must be declared here because is used inside DCSemanticsListener

		rowForCSTNButtons.add(new JLabel("Checking Algorithm: "));
		checkingAlgCSTNComboBox = new JComboBox<>(CheckAlgorithm.values());
		checkingAlgCSTNComboBox.addActionListener(new CheckAlgListener<>(checkingAlgCSTNComboBox));
		checkingAlgCSTNComboBox.setSelectedItem(cstnCheckAlg);
		rowForCSTNButtons.add(checkingAlgCSTNComboBox);

		rowForCSTNButtons.add(new JLabel("DC Semantics: "));
		cstnDCSemanticsComboBox.addActionListener(new DCSemanticsListener(cstnDCSemanticsComboBox));
		cstnDCSemanticsComboBox.setSelectedItem(dcCurrentSem); // set by cascade from checkingAlgCSTNComboBox.setSelectedItem(this.cstnCheckAlg);
		rowForCSTNButtons.add(cstnDCSemanticsComboBox);

		//
		// epsilon panel
		//
		// TNEditor.this.epsilonPanel = new JPanel(new FlowLayout());declared before because needed by this.cstnDCSemanticsCombo
		epsilonPanel.add(new JLabel("System reacts "));
		final JFormattedTextField jReactionTime = new JFormattedTextField();
		jReactionTime.setValue(Integer.valueOf(reactionTime));
		jReactionTime.setColumns(3);
		jReactionTime.addPropertyChangeListener("value", evt -> {
			reactionTime = ((Number) jReactionTime.getValue()).intValue();
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					TNEditor.LOG.finest("Property: " + evt.getPropertyName());
				}
				if (LOG.isLoggable(Level.INFO)) {
					TNEditor.LOG.info("New reaction time: " + reactionTime);
				}
			}
		});
		validationGroupCSTN.add(jReactionTime, StringValidators.regexp(Constants.NonNegIntValueRE, "A > 0 integer!", false));
		epsilonPanel.add(jReactionTime);
		epsilonPanel.add(new JLabel("time units after (≥) an observation."));
		epsilonPanel.setVisible(dcCurrentSem == DCSemantics.ε);
		rowForCSTNButtons.add(epsilonPanel);

		buttonCheck = new JButton("Init");
		buttonCheck.addActionListener(new CSTNInitListener());
		rowForCSTNButtons.add(buttonCheck);

		// buttonCheck = new JButton("DC Check HP_18 (only with IR or ε)");
		// buttonCheck.addActionListener(new CSTNRestrictedCheckListener());
		// rowForCSTNButtons.add(buttonCheck);
		//
		buttonCheck = new JButton("Controllability");
		buttonCheck.addActionListener(new CSTNControllabilityCheckListener());
		rowForCSTNButtons.add(buttonCheck);
		//
		// buttonCheck = new JButton("CSTN Check HP_20");
		// buttonCheck.addActionListener(new CSTNPotentialCheckListener());
		// rowForCSTNButtons.add(buttonCheck);

		buttonCheck = new JButton("One Step CSTN Check");
		buttonCheck.addActionListener(new CSTNOneStepListener());
		rowForCSTNButtons.add(buttonCheck);

		// buttonCheck = new JButton("CSTN All-Pair Shortest Paths");
		// buttonCheck.addActionListener(new CSTNAPSPListener());
		// rowForCSTNButtons.add(buttonCheck);

		buttonCheck = new JButton("CSTN Help");
		buttonCheck.addActionListener(help);
		buttonCheck.setActionCommand(HelpListener.AvailableHelp.CSTNHelp.toString());
		rowForCSTNButtons.add(buttonCheck);

		// ROW FOR CSTNU
		onlyToZCB = new JCheckBox("Propagate only to Z");
		onlyToZCB.setSelected(onlyToZ);
		onlyToZCB.addItemListener(new OnlyToZListener());
		rowForCSTNUButtons.add(onlyToZCB);

		final JCheckBox contingentAlsoAsOrdinaryCB = new JCheckBox("Propagate contingents also as std constraints");
		contingentAlsoAsOrdinaryCB.setSelected(contingentAlsoAsOrdinary);
		contingentAlsoAsOrdinaryCB.addItemListener(new ContingentAlsoAsOrdinaryListener());
		rowForCSTNUButtons.add(contingentAlsoAsOrdinaryCB);

		buttonCheck = new JButton("CSTNU Init");
		buttonCheck.addActionListener(new CSTNUInitListener());
		rowForCSTNUButtons.add(buttonCheck);

		buttonCheck = new JButton("CSTNU Check");
		buttonCheck.addActionListener(new CSTNUCheckListener());
		rowForCSTNUButtons.add(buttonCheck);

		buttonCheck = new JButton("One Step CSTNU Check");
		buttonCheck.addActionListener(new CSTNUOneStepListener());
		rowForCSTNUButtons.add(buttonCheck);

		buttonCheck = new JButton("CSTNU2CSTN Check");
		buttonCheck.addActionListener(new CSTNU2CSTNCheckListener());
		rowForCSTNUButtons.add(buttonCheck);

		buttonCheck = new JButton("PCSTNU Check");
		buttonCheck.addActionListener(new PCSTNUCheckListener());
		rowForCSTNUButtons.add(buttonCheck);

		buttonCheck = new JButton("CSTNU Help");
		buttonCheck.addActionListener(help);
		buttonCheck.setActionCommand(HelpListener.AvailableHelp.CSTNUHelp.toString());
		rowForCSTNUButtons.add(buttonCheck);

		//ROW FOR CSTNPSU
		rowForCSTNPSUButtons.add(onlyToZCB);
		rowForCSTNPSUButtons.add(contingentAlsoAsOrdinaryCB);

		buttonCheck = new JButton("CSTNPSU/FTNU Check");
		buttonCheck.addActionListener(new CSTNPSUCheckListener());
		rowForCSTNPSUButtons.add(buttonCheck);

		buttonCheck = new JButton("Contingency Graph");
		buttonCheck.addActionListener(new ContingencyGraphListener());
		rowForCSTNPSUButtons.add(buttonCheck);

		buttonCheck = new JButton("Prototypal Link");
		buttonCheck.addActionListener(new PLCListener());
		rowForCSTNPSUButtons.add(buttonCheck);

		buttonCheck = new JButton("CSTNPSU Help");
		buttonCheck.addActionListener(help);
		buttonCheck.setActionCommand(HelpListener.AvailableHelp.CSTNPSUHelp.toString());
		rowForCSTNPSUButtons.add(buttonCheck);

		final NewNetworkActivation newNetAct = new NewNetworkActivation();

		// MENU
		final JMenuBar menuBar = new JMenuBar();
		menuBar.setBackground(Color.LIGHT_GRAY);
		menuBar.setOpaque(true);
		menuBar.setUI(new MetalMenuBarUI());

		final JMenu menu = new JMenu("File");
		menu.setOpaque(false);

		final JMenuItem aboutItem = new JMenuItem("About TNEditor");
		aboutItem.setMnemonic('A');
		aboutItem.addActionListener(
			event -> JOptionPane.showMessageDialog(this, "TNEditor: Temporal Network Editor\n" + VERSION + "\nby Roberto Posenato (roberto.posenato@univr.it)",
			                                       "About", JOptionPane.INFORMATION_MESSAGE)
			// end method actionPerformed
		                           ); // end call to addActionListener
		menu.add(aboutItem); // add about item to file menu
		menu.addSeparator();

		final JMenu newFile = new JMenu("New network");
		final JMenuItem newStn = new JMenuItem("STN");
		newStn.setActionCommand("STN");
		newStn.addActionListener(newNetAct);
		final JMenuItem newStnu = new JMenuItem("STNU");
		newStnu.setActionCommand("STNU");
		newStnu.addActionListener(newNetAct);
		final JMenuItem newCstn = new JMenuItem("CSTN");
		newCstn.setActionCommand("CSTN");
		newCstn.addActionListener(newNetAct);
		final JMenuItem newCstnu = new JMenuItem("CSTNU");
		newCstnu.setActionCommand("CSTNU");
		newCstnu.addActionListener(newNetAct);
		final JMenuItem newCstnpsu = new JMenuItem("CSTNPSU/FTNU");
		newCstnpsu.setActionCommand("CSTNPSU");
		newCstnpsu.addActionListener(newNetAct);

		final JMenuItem newPcstnu = new JMenuItem("PCSTNU");
		newPcstnu.setActionCommand("PCSTNU");
		newPcstnu.addActionListener(newNetAct);

		final JMenuItem newOstnu = new JMenuItem("OSTNU");
		newOstnu.setActionCommand("OSTNU");
		newOstnu.addActionListener(newNetAct);

		newFile.add(newStn);
		newFile.add(newStnu);
		newFile.add(newCstn);
		newFile.add(newCstnu);
		newFile.add(newCstnpsu);
		newFile.add(newPcstnu);
		newFile.add(newOstnu);
		menu.add(newFile);

		final JMenuItem openItem = new JMenuItem("Open...");
		openItem.addActionListener(new OpenFileListener());
		menu.add(openItem);
		final JMenuItem saveItem = new JMenuItem("Save...");
		saveItem.addActionListener(new SaveFileListener());
		menu.add(saveItem);

		final JMenuItem quitItem = new JMenuItem("Quit TNEditor");
		quitItem.setMnemonic(KeyEvent.VK_Q);
		quitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, 4));//ActionEvent.META_MASK InputEvent.META_MASK
		quitItem.setToolTipText("Exit application");
		quitItem.addActionListener(e -> dispose());
		menu.add(quitItem);

		menuBar.add(menu);
		setJMenuBar(menuBar);

		pack();
		setVisible(true);
	}

	/**
	 * Adds vertex and edges renders, tooltips and mouse behavior to a viewer.
	 *
	 * @param <E>         type of edge
	 * @param viewer      viewer
	 * @param firstViewer true if viewer is in the first position
	 */
	@SuppressWarnings("ObjectToString")
	final <E extends Edge> void buildRenderContext(VisualizationViewer<LabeledNode, E> viewer, boolean firstViewer) {
		if (LOG.isLoggable(Level.FINEST)) {
			LOG.finest("buildRenderContext: " + viewer + ", firstViewer:" + firstViewer);
		}

		// vertex and edge renders
		setNodeEdgeRenders(viewer, firstViewer);

		// mouse action
		@SuppressWarnings("unchecked") final EditingModalGraphMouse<LabeledNode, E> graphMouse =
			new EditingModalGraphMouse<>(viewer.getRenderContext(), new LabeledNodeSupplier(), (EdgeSupplier<E>) new EdgeSupplier<>(currentEdgeImpl),
			                             // only after graph load it is possible to set edge supplier.
			                             TNEditor.this, firstViewer);
		if (LOG.isLoggable(Level.FINEST)) {
			LOG.finest("buildRenderContext.graphMouse " + graphMouse);
		}
		// graphMouse.setMode(ModalGraphMouse.Mode.PICKING);
		viewer.setGraphMouse(graphMouse);
		viewer.addKeyListener(graphMouse.getModeKeyListener());

		// set the operation mode
		if (firstViewer) {
			rowForAppButtons.remove(modeBoxIndex);
			rowForAppButtons.add(graphMouse.getModeComboBox(), modeBoxIndex);
		} else {
			rowForAppButtons.remove(modeBoxViewerIndex);
			rowForAppButtons.add(graphMouse.getModeComboBox(), modeBoxViewerIndex);
		}
	}

	/**
	 * Loads TNGraph stored in file 'fileName' into attribute this.g.<br>
	 * <b>Be careful!</b>
	 * The extension of the file name determines the kind of TNGraph.
	 *
	 * <pre>
	 * .stn ===&gt; STN
	 * .cstn ===&gt; CSTN
	 * .stnu ===&gt; STNU
	 * .cstnu ===&gt; CSTNU
	 * .pcstnu ===&gt; PCSTNU
	 * .cstpsu ===&gt; CSTNPSU
	 * .ostnu ===&gt; OSTNU
	 * </pre>
	 *
	 * @param fileName file name
	 *
	 * @throws SAXException                 none
	 * @throws ParserConfigurationException none
	 * @throws IOException                  none
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	final void loadGraphG(final File fileName) throws IOException, ParserConfigurationException, SAXException {

		final String name = fileName.getName();
		if (name.endsWith(".stn")) {
			setDefaultParametersForNetwork(NetworkType.STN);
		} else {
			if (name.endsWith(".stnu")) {
				setDefaultParametersForNetwork(NetworkType.STNU);
			} else {
				if (name.endsWith(".cstn")) {
					setDefaultParametersForNetwork(NetworkType.CSTN);
				} else {
					if (name.endsWith(".cstnu")) {
						setDefaultParametersForNetwork(NetworkType.CSTNU);
					} else {
						if (name.endsWith(".stnpsu") || name.endsWith(".cstnpsu")) {
							setDefaultParametersForNetwork(NetworkType.CSTNPSU);
						} else {
							if (name.endsWith(".pcstnu")) {
								setDefaultParametersForNetwork(NetworkType.PCSTNU);
							} else {
								if (name.endsWith(".ostnu")) {
									setDefaultParametersForNetwork(NetworkType.OSTNU);
								}
							}
						}
					}
				}
			}
		}
		inputGraph.takeFrom((new TNGraphMLReader()).readGraph(fileName, currentEdgeImpl));
		inputGraph.setInputFile(fileName);
		mapInfoLabel.setText(inputGraph.getEdgeFactory().toString());
		inputGraphBiggerViewer.setEnabled(true);
		// LOG.severe(TNEditor.this.inputGraph.getEdgeFactory().toString());
		validate();
		repaint();
	}

	/**
	 * Simple method to manage command line parameters using args4j library.
	 *
	 * @param args none
	 *
	 * @return false if a parameter is missing, or it is wrong. True if every parameter is given in a right format.
	 */
	final boolean manageParameters(final String[] args) {
		final CmdLineParser parser = new CmdLineParser(this);

		try {
			parser.parseArgument(args);
		} catch (final CmdLineException e) {
			// if there's a problem in the command line, you'll get this exception. this
			// will report an error message.
			System.err.println(e.getMessage());
			System.err.println("java " + getClass().getName() + " [options...]");
			// print the list of available options
			parser.printUsage(System.err);
			System.err.println();

			return false;
		}
		return true;
	}

	/**
	 *
	 */
	final void resetDerivedGraphStatus() {
		vvViewer.setVisible(false);
		viewerMessageArea.setText("");
		viewerMessageArea.setBackground(Color.lightGray);
		cycle = 0;
		stnStatus = stnuStatus = null;
		cstnStatus = cstnuStatus = null;
	}

	/**
	 * @param graphToSave graph to save
	 * @param file        file where to save
	 *
	 * @throws IOException if file cannot be used for saving the graph.
	 */
	final void saveGraphToFile(final TNGraph<? extends Edge> graphToSave, final File file) throws IOException {
		final TNGraphMLWriter graphWriter = new TNGraphMLWriter(layoutEditor);
		graphToSave.setName(file.getName());
		graphWriter.save(graphToSave, file);
	}

	/*
	 * Updates Edge Supplier in viewer considering the current type of loaded graph.
	 *
	 * @param viewer
	 *            @SuppressWarnings("unchecked")
	 *            <E extends Edge> void updateEdgeSupplierInViewer(VisualizationViewer<LabeledNode, E> viewer) {
	 *            ((EditingModalGraphMouse<LabeledNode, E>) viewer.getGraphMouse()).setEdgeEditingPlugin((EdgeSupplier<E>) new
	 *            EdgeSupplier<>(this.currentEdgeImpl));
	 *            }
	 */

	/**
	 * Set all default parameter about the editor according to the input type.
	 *
	 * @param networkType network type
	 */
	@SuppressWarnings("unchecked")
	final void setDefaultParametersForNetwork(NetworkType networkType) {
		switch (networkType) {
			case STNU:
				currentTNGraphType = NetworkType.STNU;
				currentEdgeImpl = EdgeSupplier.DEFAULT_STNU_EDGE_CLASS;
				break;
			case CSTN:
				currentTNGraphType = NetworkType.CSTN;
				currentEdgeImpl = EdgeSupplier.DEFAULT_CSTN_EDGE_CLASS;
				break;
			case CSTNU:
				currentTNGraphType = NetworkType.CSTNU;
				currentEdgeImpl = EdgeSupplier.DEFAULT_CSTNU_EDGE_CLASS;
				break;
			case OSTNU:
				currentTNGraphType = NetworkType.OSTNU;
				currentEdgeImpl = EdgeSupplier.DEFAULT_CSTNU_EDGE_CLASS;
				break;
			case PCSTNU:
				currentTNGraphType = NetworkType.PCSTNU;
				currentEdgeImpl = EdgeSupplier.DEFAULT_CSTNU_EDGE_CLASS;
				break;
			case CSTNPSU:
				currentTNGraphType = NetworkType.CSTNPSU;
				currentEdgeImpl = EdgeSupplier.DEFAULT_CSTNPSU_EDGE_CLASS;
				break;
			case STN:
			default:
				currentTNGraphType = NetworkType.STN;
				currentEdgeImpl = EdgeSupplier.DEFAULT_STN_EDGE_CLASS;
				break;
		}

		@SuppressWarnings("rawtypes") TNGraph g = new TNGraph<>("", currentEdgeImpl);
		inputGraph.takeFrom(g);
		g = new TNGraph<>("", currentEdgeImpl);
		checkedGraph.takeFrom(g);

		showCommandRow(networkType);
		buildRenderContext(vvEditor, true);
		buildRenderContext(vvViewer, false);

		// updateEdgeSupplierInViewer(this.vvEditor);
		// updateEdgeSupplierInViewer(this.vvViewer);
		validate();
		repaint();
	}

	/**
	 * In the command panel, only one row of commands is visible. This method makes visible one row, hiding the others.
	 *
	 * @param networkType network type
	 */
	final void showCommandRow(NetworkType networkType) {
		switch (networkType) {
			case CSTNU:
				validationPanelCSTNU.setVisible(true);
				validationPanelCSTN.setVisible(false);
				validationPanelCSTNPSU.setVisible(false);
				rowForSTNUButtons.setVisible(false);
				rowForOSTNUButtons.setVisible(false);
				rowForSTNButtons.setVisible(false);
				if (controlSouthPanel.getComponentCount() == 2) {
					controlSouthPanel.remove(1);
				}
				controlSouthPanel.add(validationPanelCSTNU, 1);// for button regarding CSTNU
				break;
			case CSTNPSU:
			case PCSTNU:
				validationPanelCSTNPSU.setVisible(true);
				validationPanelCSTN.setVisible(false);
				validationPanelCSTNU.setVisible(false);
				rowForSTNUButtons.setVisible(false);
				rowForOSTNUButtons.setVisible(false);
				rowForSTNButtons.setVisible(false);
				if (controlSouthPanel.getComponentCount() == 2) {
					controlSouthPanel.remove(1);
				}
				controlSouthPanel.add(validationPanelCSTNPSU, 1);// for button regarding CSTNPSU
				break;

			case CSTN:
				validationPanelCSTNU.setVisible(false);
				validationPanelCSTN.setVisible(true);
				validationPanelCSTNPSU.setVisible(false);
				rowForSTNUButtons.setVisible(false);
				rowForOSTNUButtons.setVisible(false);
				rowForSTNButtons.setVisible(false);
				if (controlSouthPanel.getComponentCount() == 2) {
					controlSouthPanel.remove(1);
				}
				controlSouthPanel.add(validationPanelCSTN, 1);// for button regarding CSTN
				break;
			case STNU:
				validationPanelCSTNU.setVisible(false);
				validationPanelCSTN.setVisible(false);
				validationPanelCSTNPSU.setVisible(false);
				rowForSTNButtons.setVisible(false);
				rowForSTNUButtons.setVisible(true);
				rowForOSTNUButtons.setVisible(false);
				if (controlSouthPanel.getComponentCount() == 2) {
					controlSouthPanel.remove(1);
				}
				controlSouthPanel.add(rowForSTNUButtons, 1);// for button regarding STN
				break;
			case OSTNU:
				validationPanelCSTNU.setVisible(false);
				validationPanelCSTN.setVisible(false);
				validationPanelCSTNPSU.setVisible(false);
				rowForSTNButtons.setVisible(false);
				rowForSTNUButtons.setVisible(false);
				rowForOSTNUButtons.setVisible(true);
				if (controlSouthPanel.getComponentCount() == 2) {
					controlSouthPanel.remove(1);
				}
				controlSouthPanel.add(rowForOSTNUButtons, 1);// for button regarding STN
				break;
			case STN:
			default:
				validationPanelCSTNU.setVisible(false);
				validationPanelCSTN.setVisible(false);
				validationPanelCSTNPSU.setVisible(false);
				rowForSTNUButtons.setVisible(false);
				rowForOSTNUButtons.setVisible(false);
				rowForSTNButtons.setVisible(true);
				if (controlSouthPanel.getComponentCount() == 2) {
					controlSouthPanel.remove(1);
				}
				controlSouthPanel.add(rowForSTNButtons, 1);// for button regarding STN
		}
	}

	/**
	 * Update node positions in derived graph.
	 */
	final void updateNodePositions() {
		LabeledNode gV;
		for (final LabeledNode v : checkedGraph.getVertices()) {
			gV = inputGraph.getNode(v.getName());
			if (gV != null) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						TNEditor.LOG.finest(
							"Vertex of original graph: " + gV + "\nOriginal position (" + layoutEditor.getX(gV) + ";" + layoutEditor.getY(gV) + ")");
					}
				}
				layoutViewer.setLocation(v, layoutEditor.getX(gV), layoutEditor.getY(gV));
			} else {
				layoutViewer.setLocation(v, v.getX(), v.getY());
			}
		}
	}

	/**
	 * Update the vvViewer after a check making some common operations.
	 */
	final void updatevvViewer() {
		viewerMessageArea.setOpaque(true);
		updateNodePositions();
		vvViewer.setCursor(Cursor.getDefaultCursor());
		vvViewer.setVisible(true);
		vvViewer.validate();
		vvViewer.repaint();
		derivedGraphBiggerViewer.setEnabled(true);
		validate();
		repaint();
	}

	/**
	 * Shows a ConfirmDialog to ask user if he wants to stay or not with the current input network.
	 *
	 * @return true if the user wants to stay with current graph, false for any other action or if the input network is void.
	 */
	final boolean userWantsToStayWithCurrentNetworkInEditor() {
		if (inputGraph != null && inputGraph.getVertexCount() > 0) {
			final int result = JOptionPane.showConfirmDialog(this, "Do you want to abandon current network?", "Consent request", JOptionPane.YES_NO_OPTION);
			return result != JOptionPane.YES_OPTION;
		}
		return false;
	}

}// end_of_file

//
// buttonCheck = new JButton("Translation to UPPAAL TIGA");
// buttonCheck.addActionListener(new AbstractAction("UPPAAL Tiga Translation") {
// /**
// *
// */
// private static final long serialVersionUID = 1L;
//
// @Override
// public void actionPerformed(final ActionEvent e) {
// final JTextArea jl = (JTextArea) TNEditor.this.messagesPanel.getComponent(0);
// PrintStream output;
// final JFileChooser chooser = new JFileChooser(TNEditor.defaultDir);
// chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
// final int option = chooser.showSaveDialog(TNEditor.this);
// if (option == JFileChooser.APPROVE_OPTION) {
// File file = chooser.getSelectedFile();
// TNEditor.defaultDir = file.getParent();
// if (!file.getName().endsWith("xml")) {
// file = new File(file.getAbsolutePath() + ".xml");
// }
// try {
// output = new PrintStream(file);
// } catch (final FileNotFoundException e1) {
// e1.printStackTrace();
// output = System.out;
// }
// CSTNU2UppaalTiga translator = null;
//
// jl.setOpaque(true);
// jl.setBackground(Color.orange);
//
// try {
// translator = new CSTNU2UppaalTiga(TNEditor.this.g, output);
// if (!translator.translate())
// throw new IllegalArgumentException();
// } catch (final IllegalArgumentException e1) {
// String msg = "The graph has a problem and it cannot be translated to an UPPAAL Tiga automaton:" + e1.getMessage();
// TNEditor.LOG.warning(msg);
// jl.setText(msg);
// // jl.setIcon(CSTNUEditor.warnIcon);
// jl.setOpaque(true);
// jl.setBackground(Color.orange);
// TNEditor.this.graphPanel.validate();
// TNEditor.this.graphPanel.repaint();
// return;
// } finally {
// output.close();
// }
// jl.setText("The graph has been translated and saved into file '" + file.getName() + "'.");
// try {
// output = new PrintStream(file.getAbsolutePath().replace(".xml", ".q"));
// } catch (final FileNotFoundException e1) {
// e1.printStackTrace();
// output = System.out;
// }
// output.println("control: A[] not _processMain.goal");
//
// // jl.setIcon(CSTNUEditor.infoIcon);
// TNEditor.this.graphPanel.validate();
// TNEditor.this.graphPanel.repaint();
// output.close();
// }
// }
// });
// rowForCSTNUButtons.add(buttonCheck);
