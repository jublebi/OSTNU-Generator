// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.labeledvalue;

import it.univr.di.Debug;

import javax.annotation.Nullable;
import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Represents an immutable Labeled Lower Case value.
 *
 * @author posenato
 * @version $Rev: 851 $
 */
@SuppressWarnings({"NonFinalFieldReferenceInEquals", "NonFinalFieldReferencedInHashCode"})
public final class LabeledLowerCaseValue implements Serializable {
	/**
	 * A constant empty label to represent an empty label that cannot be modified.
	 */
	public static final LabeledLowerCaseValue emptyLabeledLowerCaseValue = new LabeledLowerCaseValue();
	/**
	 *
	 */
	@Serial
	private static final long serialVersionUID = 1L;
	/**
	 * Logger.
	 */
	private static final Logger LOG = Logger.getLogger("LabeledLowerCaseValue");
	/**
	 *
	 */
	private Label label;
	/**
	 * Even if this field could be just a ALetter, it is an ALabel because the comparison between ALabels is faster than
	 * ALetter and ALabel.
	 */
	private ALabel nodeName;
	/**
	 *
	 */
	private int value;
	/**
	 * cached hash code
	 */
	private int hashCode;

	/**
	 * Creates a lower-case value.
	 *
	 * @param nodeName not null node name
	 * @param value    not null value
	 * @param label    not null label
	 *
	 * @return a new LabeledLowerCaseValue object
	 */
	static public LabeledLowerCaseValue create(ALabel nodeName, int value, Label label) {
		if (nodeName == null || value == Constants.INT_NULL || label == null) {
			return emptyLabeledLowerCaseValue;
		}
		if (nodeName.size() > 1) {
			throw new IllegalArgumentException("Node name label must contain only one name: " + nodeName);
		}
		return new LabeledLowerCaseValue(nodeName, value, label);
	}

	/**
	 * Copy constructor. The new object is distinct from input.<br> No null check is done!
	 *
	 * @param input the object to copy.
	 *
	 * @return a new LabeledLowerCaseValue object with equals fields of input
	 */
	static public LabeledLowerCaseValue create(LabeledLowerCaseValue input) {
		if (input == null || input.isEmpty()) {
			return emptyLabeledLowerCaseValue;
		}
		return new LabeledLowerCaseValue(ALabel.clone(input.nodeName), input.value, input.label);
	}

	/**
	 * @param nodeN a {@link ALabel} object.
	 * @param v     the new value.
	 * @param l     a {@link Label} object.
	 * @param lower true if the node name has to be written lower case
	 *
	 * @return the string representation of this lower-case value: "{(node,value,label)}"
	 */
	public static String entryAsString(ALabel nodeN, int v, Label l, boolean lower) {
		// this is necessary for saving the value in a file in the old format
		return "{" + Constants.OPEN_PAIR + ((lower) ? nodeN.toLowerCase() : nodeN) + ", " + Constants.formatInt(v) +
		       ", " + l + Constants.CLOSE_PAIR + ' ' + "}";
	}

	/**
	 * Parses a string representing a labeled lower-case value and returns an object containing the labeled values
	 * represented by the string.<br> The format of the string is given by the method
	 * {@link #toString()}:{@code \{{(&lang;label&rang;, &lang;Alabel&rang;, &lang;value&rang;) }*\}}<br> It also parse
	 * the old format: {@code \{{(&lang;Alabel&rang;, &lang;value&rang;, &lang;label&rang;) }*\}}
	 *
	 * @param arg      a {@link String} object.
	 * @param alphabet the alphabet to use for building a new labeled lower-case value. If null, a new alphabet is
	 *                 generated and insert into the created labeled value.
	 *
	 * @return a LabeledLowerCaseValue object if arg represents a valid labeled value, null otherwise.
	 */
	@Nullable
	public static LabeledLowerCaseValue parse(String arg, ALabelAlphabet alphabet) {
		// final Pattern splitterNode = Pattern.compile("〈|; ");
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Begin parse: " + arg);
			}
		}
		if ((arg == null) || (arg.length() < 2)) {
			return null;
		}
		if ("{}".equals(arg)) {
			return emptyLabeledLowerCaseValue;
		}

		if (!LabeledALabelIntTreeMap.patternLabelCharsRE.matcher(arg).matches()) {
			return null;
		}
		final Pattern COMPILE = Pattern.compile("[{}]");
		arg = COMPILE.matcher(arg).replaceAll("");
		// arg = arg.substring(1, arg.length() - 2);
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Before split: '" + arg + "'");
			}
		}
		final Pattern splitterEntry = Pattern.compile("[)(]");
		final String[] entryThreesome = splitterEntry.split(arg);
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("EntryThreesome: " + Arrays.toString(entryThreesome));
			}
		}
		final Pattern splitterTriple = Pattern.compile(", ");
		final int j;
		final String labelStr;
		final String aLabelStr;
		final String valueStr;
		// THERE IS ONLY ONE ENTRY
		if (alphabet == null) {
			alphabet = new ALabelAlphabet();
		}
		for (final String s : entryThreesome) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("s: '" + s + "'");
				}
			}
			if (s.length() > 1) {// s can be empty or a space.
				final String[] triple = splitterTriple.split(s);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("triple: " + Arrays.toString(triple));
					}
				}
				Label l = Label.parse(triple[2]);
				if (l == null) {
					// probably it is the old format
					labelStr = triple[0];
					aLabelStr = triple[1];
					valueStr = triple[2];
				} else {
					// new format
					aLabelStr = triple[0];
					valueStr = triple[1];
					labelStr = triple[2];
				}
				if (l == null) {
					l = Label.parse(labelStr);
				}
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("Label: " + l);
					}
				}
				if (valueStr.equals("-" + Constants.INFINITY_SYMBOLstring)) {
					j = Constants.INT_NEG_INFINITE;
				} else {
					j = Integer.parseInt(valueStr);
				}
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("Value: " + j);
					}
				} // LabeledNode is represented as " 〈<id>; {}; Obs: null〉 "
				// final String nodePart = labLitInt[1];//splitterNode.split(labLitInt[1]);
				final ALabel node = new ALabel(aLabelStr, alphabet);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("LabeledNode: " + node);
					}
				}
				return new LabeledLowerCaseValue(node, j, l);
			}
		}
		return emptyLabeledLowerCaseValue;
	}

	/**
	 * Creates an empty lower-case value.
	 */
	private LabeledLowerCaseValue() {
		label = null;
		nodeName = null;
		value = Constants.INT_NULL;
	}

	/**
	 * @param nodeName1 a not null node name
	 * @param value1    a value different from {@link Constants#INT_NULL}
	 * @param label1    a non null label
	 */
	private LabeledLowerCaseValue(ALabel nodeName1, int value1, Label label1) {
		if (nodeName1 == null || value1 == Constants.INT_NULL || label1 == null) {
			return;
		}
		if (nodeName1.size() > 1) {
			throw new IllegalArgumentException("Node name label must contain only one name!");
		}
		label = label1;
		nodeName = nodeName1;
		value = value1;
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof LabeledLowerCaseValue v)) {
			return false;
		}
		return value == v.value && label.equals(v.label) && nodeName.equals(v.nodeName);
	}

	/**
	 * @return the label
	 */
	public Label getLabel() {
		return label;
	}

	/**
	 * @return the node name
	 */
	public ALabel getNodeName() {
		return nodeName;
	}

	/**
	 * @return the value
	 */
	public int getValue() {
		return value;
	}

	@Override
	public int hashCode() {
		int result = hashCode;
		if (result == 0) {
			result = (isEmpty()) ? 0 : (value * 31 + label.hashCode()) * 31 + nodeName.hashCode();
			hashCode = result;
		}
		return result;
	}

	/**
	 * @return true if the object is empty
	 */
	public boolean isEmpty() {
		return (nodeName == null || value == Constants.INT_NULL || label == null);
	}

	@Override
	public String toString() {
		return toString(false);
	}

	/**
	 * @param lower true if the node name has to be written lower case
	 *
	 * @return the string representation of this lower-case value
	 */
	public String toString(boolean lower) {
		if (isEmpty()) {
			return "{}";
		}
		return entryAsString(nodeName, value, label, lower);
	}
}
