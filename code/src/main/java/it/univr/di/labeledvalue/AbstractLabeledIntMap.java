// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.labeledvalue;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.univr.di.Debug;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Abstract class for {@link it.univr.di.labeledvalue.LabeledIntMap} interface.
 *
 * @author Roberto Posenato
 * @version $Rev: 851 $
 * @see LabeledIntMap
 */
public abstract class AbstractLabeledIntMap implements LabeledIntMap {
	/**
	 * Admissible values as regular expression. For now, only integer!
	 */
	static final String valueRE = "([+\\-])?(∞|[0-9]+)";
	/**
	 * Pattern of valueREString
	 */
	static final Pattern valueREPattern = Pattern.compile(valueRE);
	/**
	 * A labeled value as regular expression.
	 */
	static final String labeledValueRE =
		"(" + Label.LABEL_RE + "\\s*,\\s*" + valueRE + "|" + valueRE + "\\s*,\\s*" + Label.LABEL_RE + ")";
	/**
	 * Matcher for a set of labeled values.
	 * <b>WARNING</b>: After some test, I verified that this pattern cannot be used because it is too much
	 * time-consuming.<br> I maintain it as a check in some tests.
	 */
	@SuppressWarnings("StaticMethodOnlyUsedInOneClass")
	static final Pattern labeledValueSetREPattern = Pattern.compile(
		Pattern.quote("{\\s*") + "(" + Pattern.quote(Constants.OPEN_PAIR) + labeledValueRE +
		Pattern.quote(Constants.CLOSE_PAIR) + "\\s*)*" + Pattern.quote("}"));
	/**
	 *
	 */
	static final long serialVersionUID = 1L;
	/**
	 * Pattern for splitting a set of labeled values.
	 */
	static final Pattern splitterEntryPattern = Pattern.compile(Pattern.quote("{") + Pattern.quote("}") //empty set
	                                                            + "|" + "(" + Pattern.quote("{") + "\\s*" +
	                                                            Pattern.quote(Constants.OPEN_PAIR) + ")"
	                                                            //first element in the set
	                                                            + "|" + Pattern.quote(Constants.CLOSE_PAIR) + "\\s*[" +
	                                                            Constants.OPEN_PAIR + "}\\s]*");
	//successive element or last one
	/**
	 *
	 */
	static final Pattern splitterPair = Pattern.compile(",\\s*");
	/**
	 * logger
	 */
	static private final Logger LOG = Logger.getLogger(AbstractLabeledIntMap.class.getName());

	/**
	 * @param entry (label, value)
	 *
	 * @return string representing the labeled value, i.e., "(value, label)"
	 */
	static String entryAsString(final Entry<Label> entry) {
		if (entry == null) {
			return "";
		}
		return entryAsString(entry.getKey(), entry.getIntValue());
	}

	/**
	 * @param value the value to represent
	 * @param label must be not null!
	 *
	 * @return string representing the labeled value, i.e., "(value, label)"
	 */
	static public String entryAsString(Label label, int value) {
		return Constants.OPEN_PAIR + Constants.formatInt(value) + ", " + label.toString() + Constants.CLOSE_PAIR;
	}

	/**
	 * @param inputMap the string representing a set of labeled values
	 *
	 * @return a new labeled int map object
	 *
	 * @see AbstractLabeledIntMap#parse(String, Class)
	 */
	static public LabeledIntMap parse(final String inputMap) {
		return parse(inputMap, LabeledIntMapSupplier.DEFAULT_LABELEDINTMAP_CLASS);
	}

	/**
	 * Parse a string representing a LabeledValueTreeMap and return an object containing the labeled values represented
	 * by the string.<br> The format of the string is given by the method {@link #toString()}: {\[(&lt;value&gt;,
	 * &lt;key&gt;) \]*} This method is also capable to parse the old format: {\[(&lt;key&gt;, &lt;value&gt;) \]*}
	 *
	 * @param inputMap           a {@link java.lang.String} object.
	 * @param labeledIntMapClass the class to manage the labeled values
	 *
	 * @return a LabeledValueTreeMap object if {@code inputMap} represents a valid map, null otherwise.
	 */
	@Nullable
	static public LabeledIntMap parse(final String inputMap, Class<? extends LabeledIntMap> labeledIntMapClass) {
		if (inputMap == null) {
			return null;
		}

		// It is not possible to check the integrity of inputMap with a RE because
		// I verified that when inputMap has a big length, the RE match goes in stack overflow.

		// if (!AbstractLabeledIntMap.patternLabelCharsRE.matcher(inputMap).matches()) {
		// if (Debug.ON) {
		// if (LOG.isLoggable(Level.WARNING)) {
		// AbstractLabeledIntMap.LOG.warning("Input string is not well-formed for representing a set of labeled values: " + patternLabelCharsRE);
		// }
		// }
		// return null;
		// }

		final LabeledIntMapSupplier<? extends LabeledIntMap> factory = new LabeledIntMapSupplier<>(labeledIntMapClass);
		final LabeledIntMap newMap = factory.get();

		final String[] entryPair = splitterEntryPattern.split(inputMap);
		// LabeledValueTreeMap.LOG.finest("EntryPairs: " + Arrays.toString(entryPair));
		Label l;
		int value;
		for (final String s : entryPair) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("s: " + s);
				}
			}
			try {
				if (!s.isEmpty()) {
					final String[] labInt = splitterPair.split(s);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest("labInt: " + Arrays.toString(labInt));
						}
					}
					l = Label.parse(labInt[0]);
					// Manage old and new format!
					if (l == null) {
						if (!valueREPattern.matcher(labInt[0]).matches()) {
							LOG.warning(
								"Input string is not well-formed for representing a set of labeled values. The entry " +
								s + " has the value not well-format. The value must satisfy the regular expression " +
								valueRE);
							return null;
						}
						if (labInt[0].equals("-" + Constants.INFINITY_SYMBOL)) {
							value = Constants.INT_NEG_INFINITE;
						} else {
							value = Integer.parseInt(labInt[0]);
						}
						l = Label.parse(labInt[1]);
						if (l == null) {
							LOG.warning(
								"Input string is not well-formed for representing a set of labeled values. The entry " +
								s + " has the label not well-format. The label must satisfy the regular expression " +
								Label.LABEL_RE);
							return null;
						}
					} else {
						if (!valueREPattern.matcher(labInt[1]).matches()) {
							LOG.warning(
								"Input string is not well-formed for representing a set of labeled values. The entry " +
								s + " has the value not well-format. The value must satisfy the regular expression " +
								valueRE);
							return null;
						}
						if (labInt[1].equals("-" + Constants.INFINITY_SYMBOL)) {
							value = Constants.INT_NEG_INFINITE;
						} else {
							value = Integer.parseInt(labInt[1]);
						}
					}
					newMap.put(l, value);
				}
			} catch (NumberFormatException e) {
				LOG.warning(
					"Input string is not well-formed for representing a set of labeled values. The number in entry " +
					s + " is not well format.");
				return null;
			}
		}
		return newMap;
	}

	/**
	 * The number of elements in the map
	 */
	int count;
	/**
	 * Optimize In same applications it is important to maintain ¬C,0 and C,1. The optimization would reduce them to ⊡,0 and C,1. So, set false optimized to
	 * maintain ¬C,0 and C,1. Default is true.
	 */
	boolean optimize;

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof LabeledIntMap lvm)) {
			return false;
		}
		if (size() != lvm.size()) {
			return false;
		}
		return entrySet().equals(lvm.entrySet());// The internal representation is not important!.
	}

	@Override
	public int hashCode() {
		return entrySet().hashCode();
	}

	@Override
	public boolean isEmpty() {
		return size() == 0;
	}

	@Override
	public int size() {
		return count;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("{");
		final ObjectList<Entry<Label>> sorted = new ObjectArrayList<>(entrySet());
		sorted.sort(LabeledIntMap.entryComparator);
		for (final Entry<Label> entry : sorted) {
			sb.append(AbstractLabeledIntMap.entryAsString(entry)).append(" ");
		}
		sb.append("}");
		return sb.toString();
	}

}
