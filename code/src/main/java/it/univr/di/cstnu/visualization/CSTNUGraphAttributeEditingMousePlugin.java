// Copyright (c) 2005, the JUNG Project and the Regents of the University of California All rights reserved. This
// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.visualization;

import edu.uci.ics.jung.algorithms.layout.GraphElementAccessor;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.univr.di.Debug;
import it.univr.di.cstnu.graph.*;
import it.univr.di.cstnu.graph.Edge.ConstraintType;
import it.univr.di.cstnu.graph.TNGraph.NetworkType;
import it.univr.di.labeledvalue.*;
import it.univr.di.labeledvalue.Label;
import it.univr.di.labeledvalue.ALabelAlphabet.ALetter;
import org.netbeans.validation.api.builtin.stringvalidation.StringValidators;
import org.netbeans.validation.api.ui.ValidationGroup;
import org.netbeans.validation.api.ui.swing.ValidationPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Allows to edit vertex or edge attributes.
 *
 * @param <V> vertex type
 * @param <E> edge type
 *
 * @author Roberto Posenato.
 * @version $Rev: 851 $
 */
@SuppressWarnings("DynamicRegexReplaceableByCompiledPattern")
public class CSTNUGraphAttributeEditingMousePlugin<V extends LabeledNode, E extends Edge>
	extends edu.uci.ics.jung.visualization.control.LabelEditingGraphMousePlugin<V, E> {

	/**
	 *
	 */
	static final Logger LOG = Logger.getLogger(CSTNUGraphAttributeEditingMousePlugin.class.getName());
	/**
	 * The editor in which this plugin works.
	 */
	TNEditor cstnEditor;

	/**
	 * Create an instance with default settings
	 *
	 * @param cstnEditor1 reference to the editor object (useful for finding some part of its panels).
	 */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "EI_EXPOSE_REP2",
		justification = "For efficiency reason, it includes an external mutable object.")
	public CSTNUGraphAttributeEditingMousePlugin(TNEditor cstnEditor1) {
		//I use InputEvent.BUTTON1_DOWN_MASK instead of InputEvent.BUTTON1_MASK because InputEvent.BUTTON1_MASK is deprecated.
		//Then, I don't use it for detecting when mouse1 is clicked because getModifiersEx changed w.r.t. getModifiers although manuals says to use getModifiersEx!
		super(InputEvent.BUTTON1_DOWN_MASK);
		cstnEditor = cstnEditor1;
	}

	/**
	 * General method to set up a dialog to edit the attributes of a vertex or of an edge.
	 *
	 * @param e          the edge
	 * @param viewerName the viewer name
	 * @param g          graph
	 *
	 * @return true if one attribute at least has been modified
	 */
	@SuppressWarnings({"unchecked", "null", "DataFlowIssue"})
	@SuppressFBWarnings(value = "NP_NULL_PARAM_DEREF", justification = "It is a false positive.")
	private static <E extends Edge> boolean edgeAttributesEditor(final E e, final String viewerName,
	                                                             final TNGraph<E> g) {

		// Edge has a name, a default value (label for this value is determined by the conjunction of labels of its end-points and a type).

		final boolean editorPanel = viewerName.equals(TNEditor.EDITOR_NAME);
		// Create a ValidationPanel - this is a panel that will show
		// any problem with the input at the bottom with an icon
		final ValidationPanel panel = new ValidationPanel();

		/*
		 * The layout is a grid of 3 columns.
		 */
		final JPanel jp = new JPanel(new GridLayout(0, 3));
		panel.setInnerComponent(jp);
		final ValidationGroup group = panel.getValidationGroup();

		// Name
		final JTextField name = new JTextField(e.getName());
		JLabel jl = new JLabel("Name:");
		jl.setLabelFor(name);
		jp.add(jl);
		jp.add(name);
		setConditionToEnable(name, viewerName, false);
		jp.add(new JLabel("RE: [%s0-9_]".formatted(Literal.PROPOSITIONS)));
		group.add(name, StringValidators.REQUIRE_NON_EMPTY_STRING);

		// Endpoints
		final LabeledNode sourceNode = g.getSource(e);
		final LabeledNode destNode = g.getDest(e);

		jp.add(new JLabel("Endpoints:"));
		jp.add(new JLabel(sourceNode + "→"));
		jp.add(new JLabel("→" + destNode));

		// Default Value
		Integer v;
		// Integer v = e.getInitialValue();
		// final JTextField value = new JTextField((v == null || v.equals(Constants.INT_POS_INFINITE)) ? "" : v.toString());
		// jl = new JLabel("Initial Value:");
		// jl.setLabelFor(value);
		// jp.add(jl);
		// jp.add(value);
		// setConditionToEnable(value, viewerName, false);
		// jp.add(new JLabel("Syntax: " + Constants.labeledValueRE));
		// group.add(value, StringValidators.regexp(Constants.labeledValueRE, "Check the syntax!", false));

		// Type
		// Group the radio buttons.
		final ButtonGroup buttonGroup = new ButtonGroup();
		jp.add(new JLabel("Edge type: "));

		final JRadioButton constraintButton = new JRadioButton(Edge.ConstraintType.requirement.toString());
		constraintButton.setActionCommand(Edge.ConstraintType.requirement.toString());
		constraintButton.setSelected(e.getConstraintType() == Edge.ConstraintType.requirement);
		setConditionToEnable(constraintButton, viewerName, false);
		jp.add(constraintButton);
		buttonGroup.add(constraintButton);

		boolean ctgAdded = false;
		final JRadioButton contingentButton = new JRadioButton(Edge.ConstraintType.contingent.toString());
		if (g.getType() == NetworkType.CSTNU || g.getType() == NetworkType.CSTNPSU || g.getType() == NetworkType.STNU) {
			contingentButton.setActionCommand(Edge.ConstraintType.contingent.toString());
			contingentButton.setSelected(e.isContingentEdge());
			setConditionToEnable(contingentButton, viewerName, false);
			jp.add(contingentButton);
			buttonGroup.add(contingentButton);
			jp.add(new JLabel(""));// in order to jump a cell
			ctgAdded = true;
		}

		final JRadioButton derivedButton = new JRadioButton(Edge.ConstraintType.derived.toString());
		derivedButton.setActionCommand(Edge.ConstraintType.derived.toString());
		derivedButton.setSelected(e.getConstraintType() == Edge.ConstraintType.derived
		                          || e.getConstraintType() == Edge.ConstraintType.internal);
		derivedButton.setEnabled(false);
		jp.add(derivedButton);
		buttonGroup.add(derivedButton);

		if (ctgAdded) {
			jp.add(new JLabel(""));// in order to jump a cell
		}

		JTextField jt;
		int i = 0;
		JTextField jtLabel, jtValue;

		int inputsN = -1;
		JTextField[] labelInputs = null;
		JTextField[] newIntInputs = null;
		Integer[] oldIntInputs = null;

		if (e.isSTNEdge() || e.isSTNUEdge()) {
			inputsN = 1;
			newIntInputs = new JTextField[inputsN];
			oldIntInputs = new Integer[inputsN];

			// Show value label
			// jp.add(new JLabel(""));// in order to jump a cell
			jp.add(new JLabel("Value: "));// in order to jump a cell
			// Show value
			oldIntInputs[0] = Integer.valueOf(((STNEdge) e).getValue());
			jtValue = new JTextField(Constants.formatInt(oldIntInputs[0]));
			newIntInputs[0] = jtValue;
			setConditionToEnable(jtValue, viewerName, false);
			jp.add(jtValue);
			group.add(jtValue,
			          StringValidators.regexp(Constants.LabeledValueRE + "|", "Integer please or let it empty!",
			                                  false));
			// Show syntax
			jt = new JTextField("RE: " + Constants.LabeledValueRE);
			setConditionToEnable(jt, viewerName, true);
			jp.add(jt);

			// if (editorPanel) {
			// jtValue = new JTextField();
			// newIntInputs[i] = jtValue;
			// oldIntInputs[i] = null;
			// setConditionToEnable(jtValue, viewerName, false);
			// jp.add(jtValue);
			// group.add(jtValue, StringValidators.regexp(Constants.LabeledValueRE + "|", "Integer please or let it empty!", false));
			// }
		}

		if (e.isSTNUEdge()) {
			final STNUEdge e1 = (STNUEdge) e;
			final String labeledValue = e1.getLabeledValueFormatted();
			if (!labeledValue.isEmpty()) {
				// jp.add(new JLabel(""));// in order to jump a cell
				jp.add(new JLabel("Case value: "));// in order to jump a cell
				jtValue = new JTextField(labeledValue);
				setConditionToEnable(jtValue, viewerName, true);
				jp.add(jtValue);
			}
		}

		if (e.isCSTNEdge()) {
			// Show possible labeled values
			final Set<it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Label>> labeledValueSet =
				((CSTNEdge) e).getLabeledValueSet();
			jp.add(new JLabel("Labeled value syntax:"));
			jt = new JTextField(Label.LABEL_RE);
			setConditionToEnable(jt, viewerName, false);
			jp.add(jt);
			jt = new JTextField(Constants.LabeledValueRE);
			setConditionToEnable(jt, viewerName, false);
			jp.add(jt);

			inputsN = labeledValueSet.size() + 1;
			labelInputs = new JTextField[inputsN];
			newIntInputs = new JTextField[inputsN];
			oldIntInputs = new Integer[inputsN];

			if (!labeledValueSet.isEmpty()) {
				for (final Entry<Label> entry : labeledValueSet) {
					jl = new JLabel("Assigned Label " + i + ":");
					jtLabel = new JTextField(entry.getKey().toString());
					labelInputs[i] = jtLabel;
					setConditionToEnable(jtLabel, viewerName, false);
					jl.setLabelFor(jtLabel);
					jp.add(jl);
					jp.add(jtLabel);
					group.add(jtLabel, StringValidators.regexp(Label.LABEL_RE + "|", "Check the syntax!", false),
					          Label.labelValidator);

					oldIntInputs[i] = Integer.valueOf(entry.getIntValue());
					jtValue = new JTextField(Constants.formatInt(oldIntInputs[i]));
					newIntInputs[i] = jtValue;
					setConditionToEnable(jtValue, viewerName, false);
					jp.add(jtValue);
					group.add(jtValue,
					          StringValidators.regexp(Constants.LabeledValueRE + "|", "Integer please or let it empty!",
					                                  false));

					i++;
				}
			}
			// Show a row where it is possible to specify a new labeled value
			if (editorPanel) {
				jl = new JLabel("Labeled value " + i + ":");
				jtLabel = new JTextField();
				labelInputs[i] = jtLabel;
				setConditionToEnable(jtLabel, viewerName, false);
				jl.setLabelFor(jtLabel);
				jp.add(jl);
				jp.add(jtLabel);
				group.add(jtLabel, StringValidators.regexp(Label.LABEL_RE + "|", "Check the syntax!", false),
				          Label.labelValidator);

				jtValue = new JTextField();
				newIntInputs[i] = jtValue;
				oldIntInputs[i] = null;
				setConditionToEnable(jtValue, viewerName, false);
				jp.add(jtValue);
				group.add(jtValue,
				          StringValidators.regexp(Constants.LabeledValueRE + "|", "Integer please or let it empty!",
				                                  false));
			}
		}

		int nUpperLabels = 0, nLowerLabels = 0;
		JTextField[] newUpperValueInputs = null;
		JTextField[] newLowerValueInputs = null;

		if (e.isCSTNUEdge() || e.isCSTNPSUEdge()) {
			nUpperLabels = ((BasicCSTNUEdge) e).upperCaseValueSize();

			final LabeledLowerCaseValue lowerValue;
			nLowerLabels = ((BasicCSTNUEdge) e).lowerCaseValueSize();

			if (e.isContingentEdge() || nUpperLabels > 0 || nLowerLabels > 0) {
				// Show all upper and lower case values allowing also the possibility of insertion.
				final BasicCSTNUEdge e1 = (BasicCSTNUEdge) e;
				newUpperValueInputs = new JTextField[(nUpperLabels == 0) ? 1 : nUpperLabels];

				newLowerValueInputs = new JTextField[(nLowerLabels == 0) ? 1 : nLowerLabels];

				// If the edge type is contingent, then we allow the modification of the single possible lower/upper case value.
				// Show additional label

				jp.add(new JLabel("Syntax:"));
				jt = new JTextField("Label (read-only)");
				setConditionToEnable(jt, viewerName, true);
				jp.add(jt);
				jt = new JTextField("<node Name>: <value>");
				setConditionToEnable(jt, viewerName, true);
				jp.add(jt);
				i = 0;
				if (nUpperLabels > 0) {
					for (final ALabel alabel : e1.getUpperCaseValueMap().keySet()) {
						final LabeledIntMap labeledValues = e1.getUpperCaseValueMap().get(alabel);
						for (final Object2IntMap.Entry<Label> entry1 : labeledValues.entrySet()) {
							// It should be only one! I put a cycle in order to verify
							jp.add(new JLabel("Upper Label"));
							jtLabel = new JTextField(entry1.getKey().toString());
							// labelUpperInputs[i] = jtLabel;
							setConditionToEnable(jtLabel, viewerName, true);
							jp.add(jtLabel);
							jtLabel = new JTextField(
								alabel.toString() + ": " + Constants.formatInt(entry1.getIntValue()));
							newUpperValueInputs[i] = jtLabel;
							setConditionToEnable(jtLabel, viewerName, nUpperLabels > 1);
							jp.add(jtLabel);
							group.add(jtLabel, StringValidators.regexp("^" + sourceNode.getName() + "\\s*:.*" + "|",
							                                           "Contingent name is wrong!", false));
							i++;
						}
					}
				} else {
					jp.add(new JLabel("Upper Label"));
					jtLabel = new JTextField("");
					// labelUpperInputs[i] = jtLabel;
					setConditionToEnable(jtLabel, viewerName, true);
					jp.add(jtLabel);
					jtLabel = new JTextField("");
					newUpperValueInputs[i] = jtLabel;
					setConditionToEnable(jtLabel, viewerName, !editorPanel);
					jp.add(jtLabel);
					group.add(jtLabel, StringValidators.regexp("^" + sourceNode.getName() + ":.*" + "|",
					                                           "Contingent name is wrong, or it is not followed by : without spaces!",
					                                           false));
				}
				i = 0;
				if (nLowerLabels > 0) {
					if (e1.isCSTNUEdge()) {
						lowerValue = e1.getLowerCaseValue();
						jp.add(new JLabel("Lower Label"));
						jtLabel = new JTextField(lowerValue.getLabel().toString());// entry1.getKey().toString());
						// labelLowerInputs[i] = jtLabel;
						setConditionToEnable(jtLabel, viewerName, true);
						jp.add(jtLabel);
						jtLabel = new JTextField(lowerValue.getNodeName().toString() + ": " + Constants.formatInt(
							lowerValue.getValue()));
						newLowerValueInputs[i] = jtLabel;
						setConditionToEnable(jtLabel, viewerName, false);
						group.add(jtLabel, StringValidators.regexp("^" + destNode.getName() + "\\s*:.*" + "|",
						                                           "Contingent name is wrong!", false));
						jp.add(jtLabel);
					} else {
						// CSTNPSU edge
						final CSTNPSUEdge e2 = (CSTNPSUEdge) e1;
						for (final ALabel alabel : e2.getLowerCaseValueMap().keySet()) {
							final LabeledIntMap labeledValues = e2.getLowerCaseValueMap().get(alabel);
							for (final Object2IntMap.Entry<Label> entry1 : labeledValues.entrySet()) {
								// It should be only one! I put a cycle in order to verify
								jp.add(new JLabel("Lower Label"));
								jtLabel = new JTextField(entry1.getKey().toString());
								// labelLowerInputs[i] = jtLabel;
								setConditionToEnable(jtLabel, viewerName, true);
								jp.add(jtLabel);
								jtLabel = new JTextField(
									alabel.toString() + ": " + Constants.formatInt(entry1.getIntValue()));
								newLowerValueInputs[i] = jtLabel;
								setConditionToEnable(jtLabel, viewerName, nLowerLabels > 1);
								jp.add(jtLabel);
								group.add(jtLabel, StringValidators.regexp("^" + destNode.getName() + "\\s*:.*" + "|",
								                                           "Contingent name is wrong!", false));
								i++;
							}
						}
					}
				} else {
					if (editorPanel) {
						jp.add(new JLabel("Lower Label"));
						jtLabel = new JTextField("");
						// labelLowerInputs[i] = jtLabel;
						setConditionToEnable(jtLabel, viewerName, true);
						jp.add(jtLabel);
						jtLabel = new JTextField("");
						newLowerValueInputs[i] = jtLabel;
						setConditionToEnable(jtLabel, viewerName, false);
						group.add(jtLabel, StringValidators.regexp("^" + destNode.getName() + "\\s*:.*" + "|",
						                                           "Contingent name is wrong!", false));
						jp.add(jtLabel);
					}
				}
			}
		}
		// Build the new object from the return values.
		boolean modified = false;
		if (panel.showOkCancelDialog("Attributes editor") && editorPanel) {
			final String newValue;
			// Name
			newValue = name.getText();
			if (!e.getName().equals(newValue)) {
				e.setName(newValue);
				modified = true;
			}

			// Default value
			// newValue = value.getText().trim();
			// v = e.getInitialValue();
			// if ((v == null) || !newValue.equals(v.toString())) {
			// v = (newValue.isEmpty()) ? null : Integer.valueOf(newValue);
			// CSTNUGraphAttributeEditingMousePlugin.LOG.finest("New default value: " + v);
			// e.clear();
			// e.setInitialValue(v);
			// modified = true;
			// }

			final Edge.ConstraintType t = (constraintButton.isSelected()) ? Edge.ConstraintType.requirement
			                                                              : (contingentButton.isSelected())
			                                                                ? Edge.ConstraintType.contingent
			                                                                : Edge.ConstraintType.requirement;

			// manage edge type
			if (e.getConstraintType() != t) {
				e.setConstraintType(t);
				modified = true;
			}

			if (e.isSTNEdge()) {
				final STNEdge e1 = (STNEdge) e;
				final String is = newIntInputs[0].getText();
				v = (!is.isEmpty()) ? Integer.valueOf(is) : null;
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finest("Value: " + is + " [old:" + oldIntInputs[0] + "])");
					}
				}
//				if (v == null) {
//					v = Integer.valueOf(Constants.INT_POS_INFINITE);
//				} else {
				if (v != null) {
					if (e1.getValue() != v) {
//						modified = true;
						e1.setValue(v);
					}
				}
				modified = true;
			}

			if (e.isSTNUEdge()) {
				final STNUEdge e1 = (STNUEdge) e;
				final String is = newIntInputs[0].getText();
				v = (!is.isEmpty()) ? Integer.valueOf(is) : null;
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finest("Value: " + is + " [old:" + oldIntInputs[0] + "])");
					}
				}
				if (v != null) {
					if (e1.getValue() != v) {
//						modified = true;
						e1.resetLabeledValue();
						e1.setValue(v);
						e.setConstraintType(t);
					}
				}
				modified = true;
			}

			if (e.isCSTNEdge()) {
				final CSTNEdge e1 = (CSTNEdge) e;

				final LabeledIntMapSupplier<LabeledIntMap> mapFactory =
					new LabeledIntMapSupplier<>((Class<LabeledIntMap>) e1.getLabeledValueMap().getClass());
				final LabeledIntMap comp = mapFactory.get();
				Label l;
				String s, is;
				// It is more safe to build a new Label set and put substitute the old one with the present.
				for (i = 0; i < (inputsN - 1); i++) {
					s = labelInputs[i].getText();
					is = newIntInputs[i].getText();
					v = (!is.isEmpty()) ? Integer.valueOf(is) : null;
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							LOG.finest("Label value " + i + ": (" + s + ", " + is + " [old:" + oldIntInputs[i] + "])");
						}
					}
					if (v == null) {
						continue; // if label is null or empty, the value is the default value!
					}
					l = ((s == null) || (s.isEmpty())) ? Label.emptyLabel : Label.parse(s);
					comp.put(l, v);
					modified = true;
				}
				// the row representing possible new value can have the two fields null!
				is = (newIntInputs[i] != null) ? newIntInputs[i].getText() : "";
				if (!is.isEmpty()) {
					l = (labelInputs[i] != null) ? Label.parse(labelInputs[i].getText()) : Label.emptyLabel;
					is = newIntInputs[i].getText();
					v = Integer.valueOf(is);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							LOG.finest("New label value: (" + l + ", " + v + ")");
						}
					}
					comp.put(l, v);
					modified = true;
				}
				if (!e1.getLabeledValueMap().equals(comp)) {
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							LOG.finer("Original label set of the component: " + e1.getLabeledValueMap());
							LOG.finer("New label set for the component: " + comp);
						}
					}
					e1.clear();
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							LOG.finer("Label set of the component after the clear: " + e1.getLabeledValueMap());
						}
					}
					e1.mergeLabeledValue(comp);
					modified = true;
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							LOG.finer("New label set assigned to the component: " + e1.getLabeledValueMap());
						}
					}
				}
			}

			if ((e.isCSTNUEdge() || e.isCSTNPSUEdge()) &&
			    (newUpperValueInputs != null && newLowerValueInputs != null)) {

				// I manage possible specification of lower/upper case new values
				if (Debug.ON) {
					LOG.info("It is a modified CSTNU (" + e.isCSTNUEdge() + ") or CSTPSU (" + e.isCSTNPSUEdge()
					         + ") edge. nUpperValues: " + nUpperLabels + ", nLowerValues:" + nLowerLabels);
				}
				String caseValue, nodeName = null;
				String[] split;
				if (BasicCSTNUEdge.class.isAssignableFrom(e.getClass())) {
					final BasicCSTNUEdge e1 = (BasicCSTNUEdge) e;

					if (e1.getConstraintType() != ConstraintType.contingent) {
						LOG.info("Constraint type: " + e1.getConstraintType() + ". It is not contingent. I clear all.");
						//reset possible upper/lower case values and return
						e1.clearUpperCaseValues();
						e1.clearLowerCaseValues();
						return modified;
					}
					// UPPER CASE VALUE
					// we consider only the first row
					// We don't use label because it has to be only one s = (labelUpperInputs[0] != null) ? labelUpperInputs[0].getText() : "";
					final JTextField s = newUpperValueInputs[0];
					caseValue = (s != null) ? s.getText() : "";
					if (!caseValue.isEmpty()) {
						// the value is in the form "<node name>: <int>"
						split = caseValue.split(": *");
						if (split.length < 2) {
							v = null;
						} else {
							// nodeName = split[0].toUpperCase();
							nodeName = split[0];
							v = Integer.valueOf(split[1]);
							if (nodeName != null && nodeName.isEmpty()) {
								nodeName = null;
							} else {
								if (g.getNode(nodeName) == null) {
									if (Debug.ON) {
										if (LOG.isLoggable(Level.SEVERE)) {
											LOG.severe(
												"ALabel " + nodeName + " does not correspond to a node name. Abort!"
												+ caseValue);
										}
									}
									nodeName = null;
								}
							}

							if (Debug.ON) {
								if (LOG.isLoggable(Level.FINEST)) {
									LOG.finest("New Upper value input: " + nodeName + ": " + v + ".");
								}
							}
						}
					} else {
						v = null;
					}
					if ((v == null || nodeName == null) && nUpperLabels > 0) {
						// The upper case label was removed.
						e1.clearUpperCaseValues();
						modified = true;
					}
					if (nodeName != null) {
						e1.clearUpperCaseValues();
						final LabeledNode source = g.getSource(e);
						final LabeledNode dest = g.getDest(e);
						final Label endpointsLabel = dest.getLabel().conjunction(source.getLabel());
						final ALabel alabel = new ALabel(new ALetter(source.getName()), g.getALabelAlphabet());
						if (alabel.toString().equals(nodeName)) {
							e1.clearUpperCaseValues();
							e1.putUpperCaseValue(endpointsLabel, alabel,
							                     v);// Temporally I ignore the label specified by user because an upper/lower case
							// value of a contingent must have the label of its endpoints.
							if (Debug.ON) {
								if (LOG.isLoggable(Level.FINEST)) {
									LOG.finest("Merged Upper value input: " + endpointsLabel + ", " + alabel + ": " + v
									           + ".");
								}
							}
							modified = true;
						}
					}
					// LOWER CASE
					// we consider only the first row
					// s = (labelLowerInputs[0] != null) ? labelLowerInputs[0].getText() : "";
					caseValue = newLowerValueInputs[0].getText();
					// the value is in the form "<node name>: <int>"
					if (!caseValue.isEmpty()) {
						split = caseValue.split(": *");
						if (split.length < 2) {
							nodeName = null;
							v = null;
						} else {
							// nodeName = split[0].toLowerCase();
							nodeName = split[0];
							v = Integer.valueOf(split[1]);
							if (nodeName != null && nodeName.isEmpty()) {
								nodeName = null;
							} else {
								if (g.getNode(nodeName) == null) {
									if (Debug.ON) {
										if (LOG.isLoggable(Level.SEVERE)) {
											LOG.severe("ALabel " + nodeName
											           + " does not correspond to a node name. Abort!");
										}
									}
									nodeName = null;
								}
							}
						}
					} else {
						nodeName = null;
						v = null;
					}
					if ((v == null || nodeName == null) && nLowerLabels > 0) {
						// The upper case label was removed.
						e1.clearLowerCaseValues();
						modified = true;
					}
					if (nodeName != null) {
						e1.clearLowerCaseValues();
						final LabeledNode source = g.getSource(e);
						final LabeledNode dest = g.getDest(e);
						final Label endpointsLabel = dest.getLabel().conjunction(source.getLabel());

						if (dest.getName().equals(nodeName)) {
							final ALabel destALabel = (dest.getALabel() != null) ? ALabel.clone(dest.getALabel())
							                                                     : new ALabel(dest.getName(),
							                                                                  g.getALabelAlphabet());
							dest.setALabel(destALabel);
							e1.putLowerCaseValue(endpointsLabel, destALabel,
							                     v);// Temporally I ignore the label specified by user because an upper/lower
							// case value of a contingent must have the label of its endpoints.
							modified = true;
						}
					}
				}
			}
		}
		return modified;
	}

	/**
	 * General method to set up a dialog to edit the attributes of a node.
	 *
	 * @param node       the node
	 * @param viewerName the viewer name
	 * @param g          graph
	 *
	 * @return true if one attribute at least has been modified
	 */
	@SuppressWarnings("null")
	private static boolean nodeAttributesEditor(final LabeledNode node, final String viewerName,
	                                            final TNGraph<? extends Edge> g) {

		// Planning a possible extension, a node could contain more labels with associated integers.
		// For now, we use only one entry and only the label part.

		final boolean editorPanel = viewerName.equals(TNEditor.EDITOR_NAME);
		// Create a ValidationPanel - this is a panel that will show
		// any problem with the input at the bottom with an icon
		final ValidationPanel panel = new ValidationPanel();

		// LabeledNode contains only the possible observed proposition and the possible associated label.
		/*
		 * The layout is a grid of 3 columns.
		 */
		final JPanel jp = new JPanel(new GridLayout(0, (editorPanel) ? 3 : 2));
		panel.setInnerComponent(jp);
		final ValidationGroup group = panel.getValidationGroup();

		// Name
		final JTextField name = new JTextField(node.getName());
		JLabel jl = new JLabel("Name:");
		jl.setLabelFor(name);
		jp.add(jl);
		jp.add(name);
		setConditionToEnable(name, viewerName, false);
		if (editorPanel) {
			jp.add(new JLabel("Syntax: [" + ALabelAlphabet.ALETTER + "?]+"));
			group.add(name,
			          StringValidators.regexp("[" + ALabelAlphabet.ALETTER + "?]+", "Must be a well format name",
			                                  false));
		}

		// Potential
		final int potential = node.getPotential();
		if (potential != Constants.INT_NULL) {
			final JTextField potentialValue = new JTextField(Constants.formatInt(potential));
			jl = new JLabel("Potential: ");
			jl.setLabelFor(potentialValue);
			jp.add(jl);
			jp.add(potentialValue);
			setConditionToEnable(potentialValue, viewerName, true);
		}


		JTextField observedProposition = null;
		char p;
		Label l = null;
		JTextField label = null;
		boolean parameter = false;
		JCheckBox parameterCheck = null;
		if (CSTNEdge.class.isAssignableFrom(g.getEdgeImplClass())) {
			// Observed proposition
			p = node.getPropositionObserved();
			observedProposition = new JTextField((p == Constants.UNKNOWN) ? "" : String.valueOf(p));
			jl = new JLabel("Observed proposition/contingent node:");
			jl.setLabelFor(observedProposition);
			jp.add(jl);
			jp.add(observedProposition);
			setConditionToEnable(observedProposition, viewerName, false);
			if (editorPanel) {
				jp.add(new JLabel("Syntax: " + Literal.PROPOSITION_RANGE + "| "));
				group.add(observedProposition,
				          StringValidators.regexp(Literal.PROPOSITION_RANGE + "|",
				                                  "Must be a single char in the range!",
				                                  false), new ObservableValidator(g, node));
			}
			//Parameter
			parameter = node.isParameter();
			parameterCheck = new JCheckBox("", parameter);
			jl = new JLabel("Parameter?");
			jl.setLabelFor(parameterCheck);
			jp.add(jl);
			jp.add(parameterCheck);
			setConditionToEnable(parameterCheck, viewerName, false);
			if (editorPanel) {
				jp.add(new JLabel("Check if the node is a parameter one."));
			}

			// Label
			l = node.getLabel();
			label = new JTextField(l.toString());
			jl = new JLabel("Label:");
			jl.setLabelFor(label);
			jp.add(jl);
			jp.add(label);
			setConditionToEnable(label, viewerName, false);
			if (editorPanel) {
				final JTextField jtf = new JTextField("Syntax: " + Label.LABEL_RE);
				jp.add(jtf);
				group.add(label, StringValidators.regexp(Label.LABEL_RE, "Check the syntax!", false),
				          Label.labelValidator);
			}
			// Labeled Potential
			LabeledIntMap potentialMap = node.getLabeledPotential();
			if (!potentialMap.isEmpty()) {
				jl = new JLabel("Labeled Potential: ");
				jp.add(jl);
				final JLabel potentialValues =
					new JLabel("<html>" + potentialMap.toString().replace("{", "").replace("}", "")
						.replaceAll("\\) \\(", ")<br />(") + "</html>", SwingConstants.LEFT);
				potentialValues.setBackground(Color.white);
				potentialValues.setOpaque(true);
				jp.add(potentialValues);
			}

			// Labeled Upper Potential
			potentialMap = node.getLabeledUpperPotential();
			if (!potentialMap.isEmpty()) {
				jl = new JLabel("Labeled Upper Potential: ");
				jp.add(jl);
				final JLabel potentialValues =
					new JLabel("<html>" + potentialMap.toString().replace("{", "").replace("}", "")
						.replaceAll("\\) \\(", ")<br />(") + "</html>", SwingConstants.LEFT);
				potentialValues.setBackground(Color.white);
				potentialValues.setOpaque(true);
				jp.add(potentialValues);
			}

		}

		// Build the new object from the return values.
		boolean modified = false;
		if (panel.showOkCancelDialog("Attributes editor") && editorPanel) {
			// Name
			String newValue = name.getText();
			if (!node.getName().equals(newValue)) {
				node.setName(newValue);
				modified = true;
			}

			if (CSTNEdge.class.isAssignableFrom(g.getEdgeImplClass())) {
				// Observable
				assert observedProposition != null;
				newValue = observedProposition.getText();
				if (newValue != null) {
					final char oldP = node.getPropositionObserved();
					if (!newValue.isEmpty()) {
						p = newValue.charAt(0);
						if ((oldP == Constants.UNKNOWN) || oldP != p) {
							node.setObservable(p);
							modified = true;
						}
					} else {
						node.setObservable(Constants.UNKNOWN);
						if (oldP != Constants.UNKNOWN) {
							modified = true;
						}
					}
				}
				//parameter
				final boolean newParameterValue = parameterCheck.isSelected();
				if (newParameterValue != parameter) {
					//the parameter flag has been changed
					node.setParameter(newParameterValue);
					modified = true;
				}
				// Label
				newValue = label.getText();
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("New label for node " + node.getName() + ": " + newValue + ". Old: " + l);
					}
				}
				if (!l.toString().equals(newValue)) {
					// syntax check allows a fast assignment!
					node.setLabel(Label.parse(newValue));
					modified = true;
				}
			}
		}
		return modified;
	}

	/**
	 * Simple method to disable the editing of the property jc if forceDisable is true or if the viewerName is not
	 * equals to TNEditor.editorName.
	 *
	 * @param jc           the JComponent
	 * @param viewerName   the viewer name
	 * @param forceDisable true if the component must be disabled.
	 */
	private static void setConditionToEnable(final JComponent jc, final String viewerName, final boolean forceDisable) {
		if (forceDisable) {
			jc.setEnabled(false);
			return;
		}
		jc.setEnabled(viewerName.equals(TNEditor.EDITOR_NAME));
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * check the mouse event modifiers against the instance member modifiers. Default implementation checks equality.
	 * Can be overridden to test with a mask
	 */
	@SuppressWarnings("MagicConstant")
	@Override
	public boolean checkModifiers(MouseEvent e) {
		return e.getModifiersEx() == modifiers;
	}

	/**
	 * @return the cstnEditor
	 */
	public TNEditor getCstnEditor() {
		return cstnEditor;
	}

	/**
	 * @param cstnEditor1 the cstnEditor to set
	 */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "EI_EXPOSE_REP2",
		justification = "For efficiency reason, it includes an external mutable object.")
	public void setCstnEditor(TNEditor cstnEditor1) {
		cstnEditor = cstnEditor1;
	}

	/**
	 * {@inheritDoc} For primary modifiers (default, MouseButton1):
	 * <ol>
	 * <li>Pick a single Vertex or Edge that is under the mouse pointer.<br>
	 * <li>If no Vertex or Edge is under the pointer, unselect all picked Vertices and Edges, and set up to draw a rectangle for multiple selection of
	 * contained Vertices.
	 * </ol>
	 * For additional selection (default Shift+MouseButton1):
	 * <ol>
	 * <li>Add to the selection, a single Vertex or Edge that is under the mouse pointer.
	 * <li>If a previously picked Vertex or Edge is under the pointer, it is un-picked.
	 * <li>If no vertex or Edge is under the pointer, set up to draw a multiple selection rectangle (as above) but do not unpick previously picked
	 * elements.
	 * </ol>
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void mouseClicked(final MouseEvent e) {
		// DON'T USE e.getModifier() because it always returns 0 for a simple click!
		if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
			// LOG.severe("CSTNUGraphAttributeEditingMousePlugin.mouseClicked.cstnEditor " + this.cstnEditor);
			@SuppressWarnings("unchecked") final VisualizationViewer<V, E> vv =
				(VisualizationViewer<V, E>) e.getSource();
			final GraphElementAccessor<V, E> pickSupport = vv.getPickSupport();
			final String viewerName = vv.getName();
			if (pickSupport != null) {
				final Layout<V, E> layout = vv.getGraphLayout();
				final TNGraph<E> g = (TNGraph<E>) layout.getGraph();
				final Point2D p = e.getPoint(); // p is the screen point for the mouse event
				vertex = pickSupport.getVertex(layout, p.getX(), p.getY());
				if (vertex != null) {
					if (nodeAttributesEditor(vertex, viewerName, g)) {
						cstnEditor.resetDerivedGraphStatus();
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								CSTNUGraphAttributeEditingMousePlugin.LOG.finer(
									"The graph has been modified. Disable the distance viewer.");
							}
						}
						g.clearCache();
					}
					e.consume();
					vv.validate();
					vv.repaint();
					return;
				}

				// p is the screen point for the mouse event take away the view transform
				// Point2D ip = vv.getRenderContext().getMultiLayerTransformer().inverseTransform(Layer.VIEW, p);
				edge = pickSupport.getEdge(layout, p.getX(), p.getY());
				if (edge != null) {
					if (edgeAttributesEditor(edge, viewerName, g)) {
						cstnEditor.resetDerivedGraphStatus();
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								CSTNUGraphAttributeEditingMousePlugin.LOG.finer(
									"The graph has been modified. Disable the distance viewer.");
							}
						}
						g.clearCache();
					}
					e.consume();
					vv.validate();
					vv.repaint();
					return;
				}
			}
			e.consume();
		}
	}

}
