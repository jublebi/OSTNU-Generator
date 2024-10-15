// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.graph;

import it.unimi.dsi.fastutil.objects.ObjectBooleanImmutablePair;
import it.univr.di.labeledvalue.ALabelAlphabet.ALetter;
import it.univr.di.labeledvalue.Constants;

import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * <h2>Represents the behavior of a STNU edge.</h2>
 * <p>A stnu edge can have up two values: the standard value and the upper/lower case one according to its type.
 * In particular, if the edge is:
 * <ul>
 *     <li>ordinary: only standard value is significative.</li>
 *     <li>contingent upper-case: the standard value (jf present) represents the lower bound of the contingent and
 *     the labeled value represent the upper-case value.</li>
 *     <li>contingent lower-case: the standard value (jf present) represents the upper bound of the contingent and
 *     the labeled value represent the lower-case value.</li>
 *     <li>wait: both the standard value and upper-case one are significative.</li>
 * </ul>
 *
 * <p>Even though many recent DC checking algorithms use only the upper/lower case value when the edge
 * represents a contingent link, we prefer to maintain also the standard value for giving a complete representation of
 * the edge.</p>
 *
 * <p>Therefore,
 * {@link #getValue()} must return always the standard value of the edge.<br>
 * In case the edge is an contingent edge (upper/lower case edge),
 * then {@link #isLowerCase()} or {@link #isUpperCase()} must return true,
 * {@link #getCaseLabel()} must return the name of contingent timepoint, and the nature of the edge: upper (true) or lower (false).
 * {@link #getLabeledValue()} must return the value of the upper/lower case.<br>
 * In case the edge is a wait constraint, the {@link #getValue()} may return {@link Constants#INT_NULL} if there is no a standard value,
 * but {@link #isUpperCase()} must return true and {@link #getLabeledValue()} must return the value of the wait.
 *
 * @author posenato
 * @version $Rev: 906 $
 */
@SuppressWarnings("InterfaceWithOnlyOneDirectInheritor")
public interface STNUEdge extends STNEdge {

	/**
	 * A lower/upper-case label is represented as pair: (node-name, flag), where flag is a boolean that it is true when
	 * the label is an upper-case one, false when the label is a lower-case one.
	 *
	 * @author posenato
	 */
	class CaseLabel extends ObjectBooleanImmutablePair<ALetter> {
		/**
		 *
		 */
		public static final long serialVersionUID = 1L;

		/**
		 * @param pair a pair to clone
		 */
		public CaseLabel(CaseLabel pair) {
			this(pair.left, pair.right);
		}

		/**
		 * A lower/upper-case label.
		 *
		 * @param k Name of node. It must be not null.
		 * @param v true if this contingent name is an upper-case label, false otherwise.
		 */
		public CaseLabel(ALetter k, boolean v) {
			super(k, v);
			if (k == null) {
				throw new IllegalArgumentException("Node name cannot be null");
			}
		}

		/**
		 * @return if the case label is equal to the given one, false otherwise
		 */
		public boolean equals(Object o) {
			if (!(o instanceof CaseLabel e1)) {
				return false;
			}
			return this.left.equals(e1.left) && this.right == e1.right;
		}

		/**
		 * @return the boolean status: true if this is an upper-case label, false otherwise.
		 */
		public boolean getCaseStatus() {
			return right;
		}

		/**
		 * @return the name of the node
		 */
		public ALetter getName() {
			return left;
		}

		/**
		 * @return the hash code
		 */
		public int hashCode() {
			return (this.right) ? this.left.hashCode() : 1000 + this.left.hashCode();
		}

		/**
		 * @return true if is lower case edge
		 */
		public boolean isLower() {
			return !right;
		}

		/**
		 * @return true if is upper case edge
		 */
		public boolean isUpper() {
			return right;
		}

		@Override
		public String toString() {
			return ((right) ? UC_LABEL : LC_LABEL) + Constants.OPEN_PAIR + left.toString()
			       + Constants.CLOSE_PAIR;
		}
	}

	/**
	 * Constant {@code LC_LABEL="LC"}
	 */
	String LC_LABEL = "LC";
	/**
	 * logger
	 */
	Logger LOG = Logger.getLogger(STNUEdge.class.getName());
	/**
	 * Constant {@code SEP_CASE=":"}
	 */
	String SEP_CASE = ":";
	/**
	 * Constant {@code UC_LABEL="UC"}
	 */
	String UC_LABEL = "UC";

	/**
	 * @return the node case label associated to this edge when it is a contingent or a wait one, null otherwise.
	 */
	CaseLabel getCaseLabel();

	/**
	 * @return the labeled weight if it is a contingent edge or a wait, {@link Constants#INT_NULL} otherwise.
	 */
	int getLabeledValue();

	/**
	 * Representation of the labeled value as a string.
	 *
	 * @return a string representing the labeled value. If there is no labeled value, returns an empty string.
	 */
	default String getLabeledValueFormatted() {
		//It is important to have a reference method because in many parts the string representation is used.
		if (getCaseLabel() == null) {
			return "";
		}
		return getCaseLabel().toString() + SEP_CASE + Constants.formatInt(getLabeledValue());
	}

	/**
	 * @return true if the edge is a lower-case one; false otherwise
	 */
	default boolean isLowerCase() {
		final CaseLabel p = getCaseLabel();
		return p != null && p.isLower();
	}

	/**
	 * @return true if the edge does not contain an upper/lower case value; false otherwise
	 */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	default boolean isOrdinaryEdge() {
		return getLabeledValue() == Constants.INT_NULL;
	}

	/**
	 * @return true if the edge is a lower-case one; false otherwise
	 */
	default boolean isUpperCase() {
		final CaseLabel p = getCaseLabel();
		return p != null && p.isUpper();
	}


	/**
	 * @return true if the edge contains an upper-case value and its type is not contingent; false otherwise
	 */
	default boolean isWait() {
		if (this.isContingentEdge()) {return false;}
		final CaseLabel p = getCaseLabel();
		return p != null && p.isUpper();
	}

	/**
	 * Removes the labeled value and make the edges an ordinary constraint.
	 *
	 * @return the old labeled value.
	 */
	int resetLabeledValue();

	/**
	 * Parse an upper/case labeled value for determining the new value for this. This method must be aligned with
	 * {@link #getLabeledValueFormatted()}.
	 *
	 * @param labeledValueAsString a {@link java.lang.String} object.
	 *
	 * @return true if the labeled value was set, false otherwise.
	 */
	@SuppressWarnings("UnusedReturnValue")
	default boolean setLabeledValue(String labeledValueAsString) {
		// System.err.print("\\b(" + UC_LABEL + "|" + LC_LABEL + ")\\b" + Pattern.quote(Constants.OPEN_PAIR) + ".*"
		// + Pattern.quote(Constants.CLOSE_PAIR) + SEP_CASE + ".*");
		if (labeledValueAsString == null || labeledValueAsString.isEmpty() || !labeledValueAsString.matches(
			"\\b(" + UC_LABEL + "|" + LC_LABEL + ")\\b" + Pattern.quote(Constants.OPEN_PAIR) + ".*" + Pattern.quote(
				Constants.CLOSE_PAIR) + SEP_CASE + ".*")) {
			return false;
		}
		final String[] entryPair = labeledValueAsString.split(SEP_CASE);
		// System.err.println(Arrays.toString(entryPair));
		boolean upperCase = true;
		if (entryPair[0].startsWith(LC_LABEL)) {
			upperCase = false;
			entryPair[0] = entryPair[0].substring(LC_LABEL.length() + Constants.OPEN_PAIR.length());
		} else if (!entryPair[0].startsWith(UC_LABEL)) {
			return false;
		} else {
			entryPair[0] = entryPair[0].substring(LC_LABEL.length() + Constants.OPEN_PAIR.length());
		}

		// System.err.println(Arrays.toString(entryPair));
		final ALetter nodeLabel =
			new ALetter(entryPair[0].substring(0, entryPair[0].length() - Constants.CLOSE_PAIR.length()));
		setLabeledValue(nodeLabel, Integer.parseInt(entryPair[1]), upperCase);
		return true;
	}

	/**
	 * Sets the labeled weight to w of a {@link it.univr.di.cstnu.graph.Edge.ConstraintType#contingent} edge.
	 * If nodeLabel == null or w = {@link it.univr.di.labeledvalue.Constants#INT_NULL}, the possible labeled value is removed.
	 * <p>
	 *     If a wait must be set, use {@link #updateWait(int, ALetter)}.
	 * </p>
	 *
	 * @param nodeLabel the name of the contingent node as ALetter.
	 * @param w         the new weight value
	 * @param upperCase true if the edge is an upper-case edge, false it is a lower-case edge. In case of lower-case
	 *                  edge, w must be positive.
	 *
	 * @return the old weight associated to the edge. If the weight was not set, it returns
	 *    {@link it.univr.di.labeledvalue.Constants#INT_NULL}.
	 */
	@SuppressWarnings("UnusedReturnValue")
	int setLabeledValue(ALetter nodeLabel, int w, boolean upperCase);


	/**
	 * If the edge is a wait, it sets the wait value if v is smaller than the current wait value. If the edge is not a
	 * wait, it sets the wait value if v is smaller than the current ordinary value and the ordinary value is removed if
	 * it is positive.
	 *
	 * @param waitValue the new weight value. If it is non-negative, it returns false.
	 * @param C         the contingent name
	 *
	 * @return true if the value (ordinary o labelled) was modified, false otherwise.
	 */
	default boolean updateWait(int waitValue, ALetter C) {
		if (waitValue >= 0) {
			return false;
		}
		final int value = getValue();
		if (isWait()) {
			if (waitValue >= getLabeledValue() || (value < 0 && waitValue >= value)) {
				return false;
			}
			setLabeledValue(C, waitValue, true);
			if (value >= 0) {
				setValue(Constants.INT_NULL);
			}
			return true;
		} else {
			if (value == Constants.INT_NULL || waitValue < value) {
				setLabeledValue(C, waitValue, true);
				if (value >= 0) {
					setValue(Constants.INT_NULL);
				}
				return true;
			}
		}
		return false;
	}
}
