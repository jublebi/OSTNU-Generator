// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.graph;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.univr.di.cstnu.algorithms.STN;
import it.univr.di.cstnu.util.LogNormalDistributionParameter;
import it.univr.di.labeledvalue.*;
import it.univr.di.labeledvalue.ALabelAlphabet.ALetter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serial;

/**
 * LabeledNode class.
 *
 * @author posenato
 * @version $Rev: 908 $
 */
@SuppressWarnings("UnusedReturnValue")
public class LabeledNode extends AbstractNode {

	/**
	 * Possible status of a node during execution of some visiting algorithms.
	 *
	 * @author posenato
	 */
	public enum Status {
		/**
		 *
		 */
		LABELED,
		/**
		 *
		 */
		SCANNED,
		/**
		 *
		 */
		UNREACHED
	}

	//	@SuppressWarnings("hiding")
	//	static final Logger LOG = Logger.getLogger(LabeledNode.class.getName());

	/**
	 * Labeled value class used in the class.
	 */
	public static final Class<? extends LabeledIntMap> labeledValueMapImpl =
		LabeledIntMapSupplier.DEFAULT_LABELEDINTMAP_CLASS;

	/**
	 *
	 */
	@Serial
	private static final long serialVersionUID = 4L;
	/**
	 * Labeled potential values. This map can also represent Upper-case labeled potentials. Used in HP20 and derived
	 * algorithms.
	 */
	private final LabeledALabelIntTreeMap labeledPotential;
	/**
	 * Labeled upper potential values. This map can also represent Upper-case labeled potentials. Used in HP20 and
	 * derived algorithms.
	 */
	private final LabeledALabelIntTreeMap labeledUpperPotential;
	/**
	 * Possible proposition observed. Used in CSTNU/CSTN.
	 */
	char propositionObserved;
	/**
	 * Labeled potential count. This map counts how many values have been set for each label during a computation of any
	 * algorithm working with potential. Usually, in APSP or SSSP algorithms, when a value has been updated more than
	 * #nodes time, then there is a negative cycle.
	 */
	Object2IntMap<Label> labeledPotentialCount;
	/**
	 * Flag for contingent node. Used in STNU/CSTNU.
	 */
	boolean isContingent;
	/**
	 * Flag for parameter node. Used in PCSTNU.
	 */
	boolean isParameter;
	/**
	 * ALabel associated to this node. This field has the scope to speed up the DC checking since it represents the name
	 * of a contingent time point as ALabel instead of calculating it every time. Used in CSTNU.
	 */
	private ALabel aLabel;
	/**
	 * Label associated to this node. Used in CSTNU/CSTN.
	 */
	private Label label;
	/**
	 * Potential value. Used in STN.
	 */
	private int potential;
	/**
	 * Position Coordinates. It must be double even if it is not necessary for Jung library compatibility.
	 */
	private double x;
	/**
	 * Position Coordinates. It must be double even if it is not necessary for Jung library compatibility.
	 */
	private double y;
	/**
	 * Predecessor of parent node. It is used for delimiting the subtree to disassemble. A node X_i is the predecessor
	 * of node X_j, i.e., X_i=p(X_j), if the distance or potential d(X_j) has been updated to d(x_i) + delta_{ij}
	 */
	private LabeledNode predecessor;

	/**
	 * Node in the double-link list used in subtreeDisassembly.
	 */
	private LabeledNode before;

	/**
	 * Node in the double-link list used in subtreeDisassembly.
	 */
	private LabeledNode after;

	/**
	 * Status used by algorithm SSSP_BFCT.
	 */
	private Status status;

	/**
	 * LogNormalDistributionParameter used to represent the probability function of the duration of a contingent link. Since a probabilistic STN is approximated
	 * by a STNU, we prefer to maintain the representation of the network using the STNUEdges and to represent the location and std.dev of the probability
	 * distribution as a record in the contingent node.
	 */
	private LogNormalDistributionParameter logNormalDistributionParameter;

	/**
	 * Constructor for cloning.
	 *
	 * @param n the node to copy.
	 */
	public LabeledNode(final LabeledNode n) {// , Class<? extends LabeledIntMap> labeledIntMapImplementation
		super(n);
		setLabel(n.label);
		setObservable(n.propositionObserved);
		x = n.x;
		y = n.y;
//		setALabel(n.aLabel);
		isContingent = n.isContingent;
		potential = n.potential;
		isParameter = n.isParameter;
		labeledPotential = new LabeledALabelIntTreeMap(n.getULCaseLabeledPotential(), labeledValueMapImpl);
		labeledUpperPotential = new LabeledALabelIntTreeMap(n.getULCaseLabeledPotential(), labeledValueMapImpl);
		labeledPotentialCount = new Object2IntOpenHashMap<>();
		labeledPotentialCount.defaultReturnValue(Constants.INT_NULL);
		logNormalDistributionParameter = n.logNormalDistributionParameter;
	}

	/**
	 * Standard constructor for an observation node
	 *
	 * @param n           name of the node.
	 * @param proposition proposition observed by this node.
	 */
	public LabeledNode(final String n, final char proposition) {// , Class<C> labeledIntMapImplementation
		this(n);
		propositionObserved = (Literal.check(proposition)) ? proposition : Constants.UNKNOWN;
		potential = Constants.INT_NULL;
	}

	/**
	 * Constructor for LabeledNode.
	 *
	 * @param string a {@link java.lang.String} object.
	 */
	public LabeledNode(final String string) {// , Class<? extends LabeledIntMap> labeledIntMapImplementation
		super(string);
		label = Label.emptyLabel;
		x = y = 0;
		propositionObserved = Constants.UNKNOWN;
		potential = Constants.INT_NULL;
		aLabel = null;
		isContingent = false;
		isParameter = false;
		labeledPotential = new LabeledALabelIntTreeMap(labeledValueMapImpl);
		labeledPotential.put(ALabel.emptyLabel, new LabeledIntMapSupplier<>(labeledValueMapImpl).get());
		labeledUpperPotential = new LabeledALabelIntTreeMap(labeledValueMapImpl);
		labeledUpperPotential.put(ALabel.emptyLabel, new LabeledIntMapSupplier<>(labeledValueMapImpl).get());
		labeledPotentialCount = new Object2IntOpenHashMap<>();
		labeledPotentialCount.defaultReturnValue(Constants.INT_NULL);
		logNormalDistributionParameter = null;
	}

	/**
	 * Clears all fields but name.
	 */
	@Override
	public void clear() {
		super.clear();
		setLabel(Label.emptyLabel);
		setObservable(Constants.UNKNOWN);
		x = 0;
		y = 0;
//		setALabel(null);
		isContingent = false;
		logNormalDistributionParameter = null;
		clearPotential();
	}

	/**
	 * Clears all fields about potential values.
	 */
	public void clearPotential() {
		potential = Constants.INT_NULL;
		labeledPotential.clear();
		labeledPotential.put(ALabel.emptyLabel, new LabeledIntMapSupplier<>(labeledValueMapImpl).get());
		labeledUpperPotential.clear();
		labeledUpperPotential.put(ALabel.emptyLabel, new LabeledIntMapSupplier<>(labeledValueMapImpl).get());
		labeledPotentialCount.clear();
	}

	/**
	 * @return the alabel. It could be null  if the setter was not used before. It is not possible to determine the ALabel without knowing the ALabelAlphabet.
	 */
	@Nullable
	public ALabel getALabel() {
		return aLabel;
	}

	/**
	 * Sets the ALabel of the node. The contingent status is updated as side effect: contingent = inputALabel != null.<br>
	 * It is responsibility of programmer to maintain the correspondence between name and alabel.
	 * <p>
	 *     <b>Warning</b><br>
	 *      Since ALabelAlphabet can represents 64 distinct ALetters, setting the ALabel of a contingent node only when an algorithm must combine different contingent
	 *      names in one ALabel.
	 * </p>
	 * @param inputAlabel the alabel to set
	 */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "For efficiency reason, it includes an external mutable object.")
	public void setALabel(ALabel inputAlabel) {
		aLabel = inputAlabel;
		isContingent = aLabel != null;
	}

	/**
	 * @return the logNormalDistributionParameter (significative only for PSTN).
	 */
	public LogNormalDistributionParameter getLogNormalDistribution() {
		return logNormalDistributionParameter;
	}

	/**
	 * @param logNormalDistributionParameter the logNormalDistributionParameter associated to the contingent link that ends in this node (if the network is PSTN
	 *                                       and this node is a contingent node of a contingent link described by a logNormalDistributionParameter).
	 */
	@SuppressFBWarnings("EI_EXPOSE_REP2")
	public void setLogNormalDistributionParameter(LogNormalDistributionParameter logNormalDistributionParameter) {
		this.logNormalDistributionParameter = logNormalDistributionParameter;
	}

	/**
	 * @return the name as ALetter
	 */
	public ALetter getALetter() {
		return new ALetter(this.name);
	}

	/**
	 * @param aletter one A-Letter
	 *
	 * @return true if {@link #getName()} is represented by the {@code aletter}, false otherwise.
	 */
	public boolean hasNameEquals(ALetter aletter) {
		if (aletter == null) {
			return false;
		}
		return aletter.equals(this.getName());
	}

	/**
	 * @return the node after in the double linked representation of the predecessor graph. This field is managed by
	 * 	DisassemblyTree procedure
	 */
	public LabeledNode getAfter() {
		return after;
	}

	/**
	 * @param after1 new after-node in the double linked representation of the predecessor graph.
	 */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "EI_EXPOSE_REP2",
		justification = "For efficiency reason, it includes an external mutable object.")
	public void setAfter(LabeledNode after1) {
		after = after1;
	}

	/**
	 * @return the node before in the double linked representation of the predecessor graph. This field is managed by
	 * 	DisassemblyTree procedure
	 */
	public LabeledNode getBefore() {
		return before;
	}

	/**
	 * @param before1 new before node in the double linked representation of the predecessor graph.
	 */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "EI_EXPOSE_REP2",
		justification = "For efficiency reason, it includes an external mutable object.")
	public void setBefore(LabeledNode before1) {
		before = before1;
	}

	/**
	 * @return the label
	 */
	public Label getLabel() {
		return label;
	}

	/**
	 * @param inputLabel the label to set. If it is null, this label is set to
	 *                   {@link it.univr.di.labeledvalue.Label#emptyLabel}.
	 */
	public void setLabel(final Label inputLabel) {
		final String old = (label == null) ? "null" : label.toString();
		label = (inputLabel == null || inputLabel.isEmpty()) ? Label.emptyLabel : inputLabel;
		pcs.firePropertyChange("nodeLabel", old, inputLabel);
	}

	/**
	 * @param s the label to set
	 */
	public void setLabel(final String s) {
		setLabel(Label.parse(s));
	}

	/**
	 * Returns the potential associated to label l, if it exists; {@link it.univr.di.labeledvalue.Constants#INT_NULL}
	 * otherwise.
	 *
	 * @param l a {@link it.univr.di.labeledvalue.Label} object.
	 *
	 * @return the labeled value getPotential(ALabel.emptyLabel, Label) if it was present,
	 *    {@link it.univr.di.labeledvalue.Constants#INT_NULL} otherwise.
	 */
	public int getLabeledPotential(Label l) {
		return labeledPotential.get(ALabel.emptyLabel).get(l);
	}

	/**
	 * Returns the map of labeled potential of the node.
	 *
	 * @return an unmodifiable view of the labeled potential values
	 */
	public LabeledIntMap getLabeledPotential() {
		return labeledPotential.get(ALabel.emptyLabel).unmodifiable();
	}

	/**
	 * If potential is not null, it is used (not copied) as new potential of the node. If potential is null, it does
	 * nothing.
	 *
	 * @param potentialMap a {@link it.univr.di.labeledvalue.LabeledIntMap} object.
	 */
	public void setLabeledPotential(LabeledIntMap potentialMap) {
		if (potentialMap == null) {
			return;
		}
		labeledPotential.put(ALabel.emptyLabel, potentialMap);
	}

	/**
	 * @param l the value to search
	 *
	 * @return the value associated to the label if it exists, {@link Constants#INT_NULL} otherwise.
	 */
	public final int getLabeledPotentialCount(Label l) {
		if (l == null) {
			return Constants.INT_NULL;
		}
		return labeledPotentialCount.getInt(l);
	}

	/**
	 * Returns the upper potential associated to label l, if it exists;
	 * {@link it.univr.di.labeledvalue.Constants#INT_NULL} otherwise.
	 *
	 * @param l a {@link it.univr.di.labeledvalue.Label} object.
	 *
	 * @return the labeled value getPotential(ALabel.emptyLabel, Label) if it was present,
	 *    {@link it.univr.di.labeledvalue.Constants#INT_NULL} otherwise.
	 */
	public int getLabeledUpperPotential(Label l) {
		return labeledUpperPotential.get(ALabel.emptyLabel).get(l);
	}

	/**
	 * Returns the map of labeled upper potential of the node.
	 *
	 * @return an unmodifiable view of the labeled potential values
	 */
	public LabeledIntMap getLabeledUpperPotential() {
		return labeledUpperPotential.get(ALabel.emptyLabel).unmodifiable();
	}

	/**
	 * If potential is not null, it is used (not copied) as new potential of the node. If potential is null, it does
	 * nothing.
	 *
	 * @param potentialMap a {@link it.univr.di.labeledvalue.LabeledIntMap} object.
	 */
	public void setLabeledUpperPotential(LabeledIntMap potentialMap) {
		if (potentialMap == null) {
			return;
		}
		labeledUpperPotential.put(ALabel.emptyLabel, potentialMap);
	}

	/**
	 * @return the potential. If {@link it.univr.di.labeledvalue.Constants#INT_NULL}, it means that it was not
	 * 	determined.
	 */
	public int getPotential() {
		return potential;
	}

	/**
	 * @param potential1 the potential to set
	 */
	public void setPotential(int potential1) {
		potential = potential1;
	}

	/**
	 * @return the predecessor LabeledNode or null.
	 */
	public LabeledNode getPredecessor() {
		return predecessor;
	}

	/**
	 * Set the predecessor.
	 *
	 * @param p the predecessor node
	 */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "EI_EXPOSE_REP2",
		justification = "For efficiency reason, it includes an external mutable object.")
	public void setPredecessor(LabeledNode p) {
		predecessor = p;
	}

	/**
	 * @return the proposition under the control of this node. {@link it.univr.di.labeledvalue.Constants#UNKNOWN}, if no
	 * 	observation is made.
	 */
	public char getPropositionObserved() {
		return propositionObserved;
	}

	/**
	 * @return the status of the node during {@link STN#SSSP_BFCT(TNGraph, LabeledNode, STN.EdgeValue, int, STN.STNCheckStatus)}
	 * 	execution.
	 */
	public Status getStatus() {
		return status;
	}

	/**
	 * @param status1 new status of the node during {@link STN#SSSP_BFCT} execution.
	 */
	public void setStatus(Status status1) {
		status = status1;
	}

	/**
	 * Returns the map of upper/lower case labeled potential of the node.
	 *
	 * @return a unmodifiable {@link it.univr.di.labeledvalue.LabeledALabelIntTreeMap} object.
	 */
	public LabeledALabelIntTreeMap getULCaseLabeledPotential() {
		return labeledPotential.unmodifiable();
	}

	/**
	 * Returns the map of upper/lower case labeled UPPER potential of the node.
	 *
	 * @return a unmodifiable {@link it.univr.di.labeledvalue.LabeledALabelIntTreeMap} object.
	 */
	public LabeledALabelIntTreeMap getULCaseLabeledUpperPotential() {
		return labeledUpperPotential.unmodifiable();
	}

	/**
	 * @return the x
	 */
	public double getX() {
		return x;
	}

	/**
	 * @param x1 the x to set
	 */
	public void setX(final double x1) {
		x = x1;
	}

	/**
	 * @return the y
	 */
	public double getY() {
		return y;
	}

	/**
	 * @param y1 the y to set
	 */
	public void setY(final double y1) {
		y = y1;
	}

	/**
	 * Overriding method to ensure that name is coherent with ALabel
	 */
	@SuppressFBWarnings(value = {"RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", "RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE"}, justification = "Silly cycle")
	@Nonnull
	@Override
	public String setName(@Nonnull String name) {
		if (this.name.equals(name)) {
			return this.name;
		}
		final String old = super.setName(name);
		this.setALabel(null);
		return old;
	}

	/**
	 * @return true if this node represents a contingent node. It is assumed that a node representing a contingent
	 * 	time-point has its field 'alabel' not null.
	 */
	@Override
	public boolean isContingent() {
		return isContingent;
	}

	/**
	 * @param b true if the node is becoming a contingent one.
	 */
	public void setContingent(boolean b) {
		isContingent = b;
	}

	/**
	 * @return true if this node is an observator one (it is associated to a proposition letter), false otherwise;
	 */
	public boolean isObserver() {
		return propositionObserved != Constants.UNKNOWN;
	}

	/**
	 * @return true if this node represents a parameter node.
	 */
	@Override
	public boolean isParameter() {
		return isParameter;
	}

	/**
	 * @param b true if the node is becoming a parameter one.
	 */
	public void setParameter(boolean b) {
		isParameter = b;
	}

	/**
	 * @param inputPotential a {@link it.univr.di.labeledvalue.LabeledIntMap} object.
	 *
	 * @return true if node potential is equal to inputPotential
	 */
	public boolean isPotentialEqual(LabeledIntMap inputPotential) {
		return labeledPotential.get(ALabel.emptyLabel).equals(inputPotential);
	}

	/**
	 * @param inputPotential a {@link it.univr.di.labeledvalue.LabeledIntMap} object.
	 *
	 * @return true if node upper potential is equal to inputPotential
	 */
	public boolean isUpperPotentialEqual(LabeledIntMap inputPotential) {
		return labeledUpperPotential.get(ALabel.emptyLabel).equals(inputPotential);
	}

	@Override
	public Node newInstance() {
		return new LabeledNode("");
	}

	@Override
	public Node newInstance(Node node) {
		return new LabeledNode((LabeledNode) node);
	}

	@Override
	public Node newInstance(String name1) {
		return new LabeledNode(name1);
	}

	/**
	 * Puts the labeled value (value, l) into the potential map.
	 *
	 * @param l     a {@link it.univr.di.labeledvalue.Label} object.
	 * @param value the new value.
	 *
	 * @return true if the pair has been merged.
	 */
	final public boolean putLabeledPotential(Label l, int value) {
		return labeledPotential.get(ALabel.emptyLabel).put(l, value);
	}

	/**
	 * Puts the labeled value (value, l) into the upper potential map.
	 *
	 * @param l     a {@link it.univr.di.labeledvalue.Label} object.
	 * @param value the new value.
	 *
	 * @return true if the pair has been merged.
	 */
	final public boolean putLabeledUpperPotential(Label l, int value) {
		return labeledUpperPotential.get(ALabel.emptyLabel).put(l, value);
	}

	/**
	 * Removes the labeled potential associated to label l, if it exists.
	 *
	 * @param l the label to remove
	 *
	 * @return the old value if it was present, {@link it.univr.di.labeledvalue.Constants#INT_NULL} otherwise.
	 */
	public int removeLabeledPotential(Label l) {
		return labeledPotential.get(ALabel.emptyLabel).remove(l);
	}

	/**
	 * Removes the labeled upper potential associated to label l, if it exists.
	 *
	 * @param l the label to remove
	 *
	 * @return the old value if it was present, {@link it.univr.di.labeledvalue.Constants#INT_NULL} otherwise.
	 */
	public int removeLabeledUpperPotential(Label l) {
		return labeledUpperPotential.get(ALabel.emptyLabel).remove(l);
	}

	/**
	 * Put the value with label l into the map.
	 *
	 * @param l     the label of new value
	 * @param value the new value
	 *
	 * @return the old value associated to label if it exists, {@link Constants#INT_NULL} otherwise.
	 */
	public final int setLabeledPotentialCount(Label l, int value) {
		if (l == null || value == Constants.INT_NULL) {
			return Constants.INT_NULL;
		}
		return labeledPotentialCount.put(l, value);
	}

	/**
	 * Set the proposition to be observed.
	 *
	 * @param c the proposition to observe. If {@link it.univr.di.labeledvalue.Constants#UNKNOWN}, the node became not
	 *          observable node.
	 */
	public void setObservable(final char c) {
		final char old = propositionObserved;
		propositionObserved = (Literal.check(c)) ? c : Constants.UNKNOWN;
		pcs.firePropertyChange("nodeProposition", Character.valueOf(old), Character.valueOf(c));
	}

	@SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification = "False positive.")
	@Nonnull
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder(Constants.OPEN_TUPLE);
		sb.append(getName());
		if (!label.isEmpty()) {
			sb.append("; ");
			sb.append(label);
		}
		if (propositionObserved != Constants.UNKNOWN) {
			sb.append("; Obs: ");
			sb.append(propositionObserved);
		}
		if (!labeledPotential.isEmpty()) {
			sb.append("; Labeled Potential: ");
			sb.append(labeledPotential);
		}
		if (!labeledUpperPotential.isEmpty()) {
			sb.append("; Labeled Upper Potential: ");
			sb.append(labeledUpperPotential);
		}
		if (potential != Constants.INT_NULL) {
			sb.append("; Potential: ");
			sb.append(potential);
		}
		sb.append(Constants.CLOSE_TUPLE);
		return sb.toString();
	}
}
