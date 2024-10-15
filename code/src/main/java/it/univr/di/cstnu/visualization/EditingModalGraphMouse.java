// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.visualization;

import com.google.common.base.Supplier;
import edu.uci.ics.jung.visualization.RenderContext;
import edu.uci.ics.jung.visualization.control.*;
import it.univr.di.cstnu.graph.Edge;
import it.univr.di.cstnu.graph.EdgeSupplier;
import it.univr.di.cstnu.graph.LabeledNode;

import javax.swing.*;
import javax.swing.plaf.basic.BasicIconFactory;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * I modified the source to use a local EditingGraphMousePlugin and to remove some extra useless features.
 *
 * @param <V> vertex type
 * @param <E> edge type
 *
 * @author posenato
 * @version $Rev: 840 $
 * @see edu.uci.ics.jung.visualization.control.EditingModalGraphMouse
 */
public class EditingModalGraphMouse<V extends LabeledNode, E extends Edge> extends AbstractModalGraphMouse {

	/**
	 * Creates an instance with default values.
	 *
	 * @param ignoredRc1     a render contest.
	 * @param vertexFactory1 a vertex factory.
	 * @param edgeFactory1   an edge factory.
	 * @param cstnEditor1    reference to the editor
	 * @param ignoredEditor1 true for having 'editing' function in modeComboBox.
	 */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
		value = {"MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR", "EI_EXPOSE_REP2"},
		justification = "loadPlugins() is not so dangerous. For efficiency reason, it includes an external mutable object.")
	public EditingModalGraphMouse(final RenderContext<V, E> ignoredRc1, final Supplier<V> vertexFactory1,
	                              final Supplier<E> edgeFactory1, TNEditor cstnEditor1, boolean ignoredEditor1) {
		super(1.1f, 1 / 1.1f);
		vertexFactory = vertexFactory1;
		edgeFactory = edgeFactory1;
		//		this.rc = rc1;
		//		this.basicTransformer = rc1.getMultiLayerTransformer();
		//		this.editor = editor1;
		cstnEditor = cstnEditor1;
		loadPlugins();
		setModeKeyListener(new ModeKeyAdapter(this));
	}

	/*
	 * logger
	 */
	//	static Logger LOG = Logger.getLogger(EditingModalGraphMouse.class.getName());
	/**
	 * Internal reference to the main JFrame.
	 */
	TNEditor cstnEditor;
	/*
	 * Internal flag for activating 'editing' functions.
	 */
	//	boolean editor;
	/**
	 *
	 */
	Supplier<V> vertexFactory;
	/**
	 *
	 */
	Supplier<E> edgeFactory;
	/**
	 *
	 */
	EditingGraphMousePlugin<V, E> editingPlugin;
	/**
	 *
	 */
	CSTNUGraphAttributeEditingMousePlugin<V, E> labelEditingPlugin;
	/**
	 * Plugin for editing
	 */
	EditingPopupGraphMousePlugin<V, E> popupEditingPlugin;
	/*
	 *
	 */
	//	AnnotatingGraphMousePlugin<V, E> annotatingPlugin;
	/*
	 *
	 */
	//	MultiLayerTransformer basicTransformer;
	/*
	 *
	 */
	//	RenderContext<V, E> rc;

	/**
	 * @return the labelEditingPlugin
	 */
	public CSTNUGraphAttributeEditingMousePlugin<V, E> getLabelEditingPlugin() {
		return labelEditingPlugin;
	}

	/**
	 * {@inheritDoc} setter for the Mode. Removed annotating mode.
	 */
	@Override
	public void setMode(final Mode mode1) {
		if (mode != mode1) {
			fireItemStateChanged(
				new ItemEvent(this, ItemEvent.ITEM_STATE_CHANGED, mode, ItemEvent.DESELECTED));
			mode = mode1;
			if (mode1 == Mode.EDITING) {
				setEditingMode();
			} else {
				setTransformingMode();
			}
			// if (mode1 == Mode.PICKING) {
			// }
			// this.setPickingMode();
			// } else if (mode1 == Mode.EDITING && this.editor) {
			// this.setOLDEditingMode();
			// }
			// // else if (mode == Mode.ANNOTATING) {
			// setAnnotatingMode();
			// }
			if (modeBox != null) {
				modeBox.setSelectedItem(mode1);
			}
			fireItemStateChanged(new ItemEvent(this, ItemEvent.ITEM_STATE_CHANGED, mode1, ItemEvent.SELECTED));
		}
	}

	/**
	 * setEditingMode.
	 */
	protected void setEditingMode() {
		remove(translatingPlugin);
		remove(rotatingPlugin);
		remove(shearingPlugin);
		remove(editingPlugin);
		// remove(this.annotatingPlugin);
		add(pickingPlugin);
		add(animatedPickingPlugin);
		add(labelEditingPlugin);
		add(popupEditingPlugin);
		add(editingPlugin);
	}

	@Override
	protected void setTransformingMode() {
		remove(pickingPlugin);
		remove(animatedPickingPlugin);
		remove(editingPlugin);
		// remove(this.annotatingPlugin);
		add(translatingPlugin);
		add(rotatingPlugin);
		add(shearingPlugin);
		add(labelEditingPlugin);
		add(popupEditingPlugin);
	}

	/**
	 * getEdgeEditingPlugin.
	 *
	 * @return the edge factory
	 */
	public Supplier<E> getEdgeEditingPlugin() {
		return edgeFactory;
	}

	/**
	 * @return the editingPlugin
	 */
	public EditingGraphMousePlugin<V, E> getEditingPlugin() {
		return editingPlugin;
	}

	// protected void setAnnotatingMode() {
	// remove(this.pickingPlugin);
	// remove(this.animatedPickingPlugin);
	// remove(this.translatingPlugin);
	// remove(this.rotatingPlugin);
	// remove(this.shearingPlugin);
	// remove(this.labelEditingPlugin);
	// remove(this.editingPlugin);
	// remove(this.popupEditingPlugin);
	// add(this.annotatingPlugin);
	// }

	@Override
	public JComboBox<Mode> getModeComboBox() {
		if (modeBox == null) {
			modeBox = new JComboBox<>(new Mode[]{Mode.EDITING, Mode.TRANSFORMING});// , Mode.PICKING, Mode.ANNOTATING
			modeBox.addItemListener(getModeListener());
		}
		modeBox.setSelectedItem(mode);
		return modeBox;
	}

	/**
	 * {@inheritDoc} create the plugins, and load the plugins for TRANSFORMING mode
	 */
	@SuppressWarnings("deprecation")
	@Override
	protected void loadPlugins() {
		pickingPlugin = new PickingGraphMousePlugin<V, E>();
		animatedPickingPlugin = new AnimatedPickingGraphMousePlugin<V, E>();
		translatingPlugin = new TranslatingGraphMousePlugin(InputEvent.BUTTON1_MASK);
		scalingPlugin = new ScalingGraphMousePlugin(new CrossoverScalingControl(), 0, in, out);
		rotatingPlugin = new RotatingGraphMousePlugin();
		shearingPlugin = new ShearingGraphMousePlugin();
		editingPlugin = new EditingGraphMousePlugin<>(vertexFactory, edgeFactory);
		labelEditingPlugin = new CSTNUGraphAttributeEditingMousePlugin<>(cstnEditor);
		// this.annotatingPlugin = new AnnotatingGraphMousePlugin<>(this.rc);
		popupEditingPlugin = new EditingPopupGraphMousePlugin<>(vertexFactory, edgeFactory);
		add(scalingPlugin);// for zooming
		setMode(Mode.EDITING);
	}

	/**
	 * {@inheritDoc} create (if necessary) and return a menu that will change the mode
	 */
	@Override
	public JMenu getModeMenu() {
		if (modeMenu == null) {
			modeMenu = new JMenu();// {
			final Icon icon = BasicIconFactory.getMenuArrowIcon();
			modeMenu.setIcon(BasicIconFactory.getMenuArrowIcon());
			modeMenu.setPreferredSize(new Dimension(icon.getIconWidth() + 10, icon.getIconHeight() + 10));

			final JRadioButtonMenuItem transformingButton = new JRadioButtonMenuItem(Mode.TRANSFORMING.toString());
			transformingButton.addItemListener(e -> {
				if (e.getStateChange() == ItemEvent.SELECTED) {
					setMode(Mode.TRANSFORMING);
				}
			});

			// final JRadioButtonMenuItem pickingButton = new JRadioButtonMenuItem(Mode.PICKING.toString());
			// pickingButton.addItemListener(new ItemListener() {
			// @Override
			// public void itemStateChanged(ItemEvent e) {
			// if (e.getStateChange() == ItemEvent.SELECTED) {
			// setMode(Mode.PICKING);
			// }
			// }
			// });

			final JRadioButtonMenuItem editingButton = new JRadioButtonMenuItem(Mode.EDITING.toString());
			editingButton.addItemListener(e -> {
				if (e.getStateChange() == ItemEvent.SELECTED) {
					setMode(Mode.EDITING);
				}
			});

			final ButtonGroup radio = new ButtonGroup();
			radio.add(transformingButton);
			// radio.add(pickingButton);
			radio.add(editingButton);
			transformingButton.setSelected(true);
			modeMenu.add(transformingButton);
			// this.modeMenu.add(pickingButton);
			modeMenu.add(editingButton);
			modeMenu.setToolTipText("Menu for setting Mouse Mode");
			addItemListener(e -> {
				if (e.getStateChange() == ItemEvent.SELECTED) {
					if (e.getItem() == Mode.TRANSFORMING) {
						transformingButton.setSelected(true);
						// } else if (e.getItem() == Mode.PICKING) {
						// pickingButton.setSelected(true);
					} else if (e.getItem() == Mode.EDITING) {
						editingButton.setSelected(true);
					}
				}
			});
		}
		return modeMenu;
	}

	/*
	 * @return the annotatingPlugin
	 */
	// public AnnotatingGraphMousePlugin<V, E> getAnnotatingPlugin() {
	// return this.annotatingPlugin;
	// }
	//

	/**
	 * @return the popupEditingPlugin
	 */
	public EditingPopupGraphMousePlugin<V, E> getPopupEditingPlugin() {
		return popupEditingPlugin;
	}

	/**
	 * @param edgeSupp edge factory
	 */
	public void setEdgeEditingPlugin(EdgeSupplier<E> edgeSupp) {
		edgeFactory = edgeSupp;
		editingPlugin.setEdgeFactory(edgeSupp);
		popupEditingPlugin.setEdgeFactory(edgeSupp);
	}

	/**
	 * Personalization of key adapter.
	 *
	 * @author posenato
	 */
	@SuppressWarnings("hiding")
	public static class ModeKeyAdapter extends KeyAdapter {
		/**
		 *
		 */
		protected ModalGraphMouse graphMouse;
		private char t = 't';
		// private char a = 'a';
		// private char p = 'p';
		private char e = 'e';

		/**
		 * @param graphMouse nope
		 */
		@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "EI_EXPOSE_REP2",
			justification = "For efficiency reason, it includes an external mutable object.")
		public ModeKeyAdapter(ModalGraphMouse graphMouse) {
			this.graphMouse = graphMouse;
		}

		/**
		 * @param t          nope
		 * @param ignoredP   nope
		 * @param e          nope
		 * @param ignoredA   nope
		 * @param graphMouse nope
		 */
		@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "EI_EXPOSE_REP2",
			justification = "For efficiency reason, it includes an external mutable object.")
		public ModeKeyAdapter(char t, char ignoredP, char e, char ignoredA, ModalGraphMouse graphMouse) {
			this.t = t;
			// this.p = p;
			this.e = e;
			// this.a = a;
			this.graphMouse = graphMouse;
		}

		@Override
		public void keyTyped(KeyEvent event) {
			final char keyChar = event.getKeyChar();
			if (keyChar == t) {
				((Component) event.getSource()).setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
				graphMouse.setMode(Mode.TRANSFORMING);
				// } else if (keyChar == this.a) {
				// ((Component) event.getSource()).setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				// this.graphMouse.setMode(Mode.ANNOTATING);
			} else if (keyChar == e) {
				((Component) event.getSource()).setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
				graphMouse.setMode(Mode.EDITING);
			}
			// else if (keyChar == this.a) {
			// ((Component) event.getSource()).setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
			// this.graphMouse.setMode(Mode.ANNOTATING);
			// }
		}
	}

}
