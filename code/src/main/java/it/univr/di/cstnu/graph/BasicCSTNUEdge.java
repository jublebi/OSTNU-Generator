// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.graph;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.univr.di.labeledvalue.ALabel;
import it.univr.di.labeledvalue.Label;
import it.univr.di.labeledvalue.LabeledALabelIntTreeMap;
import it.univr.di.labeledvalue.LabeledLowerCaseValue;

/**
 * Represents the behavior of a CSTNU edge.
 *
 * @author posenato
 * @version $Rev: 851 $
 */
@SuppressWarnings("UnusedReturnValue")
public interface BasicCSTNUEdge extends CSTNEdge {
	/**
	 * Clears the labeled lower case values. For CSTNU there is always only one value, but for some extensions can be
	 * more.
	 */
	void clearLowerCaseValues();

	/**
	 * Clears all upper case labeled values.
	 */
	void clearUpperCaseValues();

	/**
	 * @return the set of maps of labeled values and labeled upper-case ones. The maps of labeled values has ALabel
	 * empty.
	 */
	LabeledALabelIntTreeMap getAllUpperCaseAndLabeledValuesMaps();

	/**
	 * @return the labeled lower-case value object characteristics of a contingent/guarded link. Use
	 * {@link it.univr.di.labeledvalue.LabeledLowerCaseValue#isEmpty()} to check if it contains or not a significant
	 * value. If there is no lower-case value or there are more lower-case values (in extensions of CSTNUs), it returns
	 * an empty LabeledLowerCaseValue object. In case that there is more lower-case values (extensions of CSTNU),
	 * consider {@link #getMinLowerCaseValue()}.
	 */
	LabeledLowerCaseValue getLowerCaseValue();

	/**
	 * @return the minimal value (with the ALabel) among all lower-case labeled values if there are some of them, null
	 * otherwise.
	 */
	Object2ObjectMap.Entry<Label, Entry<ALabel>> getMinLowerCaseValue();


	/**
	 * @return the minimal value (with the ALabel) among all upper-case labeled values if there are some of them,
	 * {@link it.univr.di.labeledvalue.Constants#INT_NULL} otherwise.
	 */
	Object2ObjectMap.Entry<Label, Entry<ALabel>> getMinUpperCaseValue();

	/**
	 * @param l    a {@link it.univr.di.labeledvalue.Label} object.
	 * @param name a {@link it.univr.di.labeledvalue.ALabel} node name.
	 * @return the value associated to the upper label of the occurrence of node n if it exists,
	 * {@link it.univr.di.labeledvalue.Constants#INT_NULL} otherwise.
	 */
	int getUpperCaseValue(Label l, ALabel name);

	/**
	 * @return the Upper-Case labeled Value
	 */
	LabeledALabelIntTreeMap getUpperCaseValueMap();

	/**
	 * @param labeledValue the upper case labeled value map to use for initializing the set
	 */
	void setUpperCaseValueMap(LabeledALabelIntTreeMap labeledValue);

	/**
	 * @return the representation of all Lower-Case Labels of the edge.
	 */
	String lowerCaseValuesAsString();

	/**
	 * @return the number of Lower-Case Labels of the edge.
	 */
	int lowerCaseValueSize();

	/**
	 * Merge a lower label constraint with value {@code i} for the node name {@code n} with label {@code l}.<br>
	 *
	 * @param l        It cannot be null or empty.
	 * @param nodeName the node name. It cannot be null. It must be the unmodified name of the node.
	 * @param i        It cannot be nullInt.
	 * @return true if the merge has been successful.
	 */
	boolean mergeLowerCaseValue(Label l, ALabel nodeName, int i);

	/**
	 * Put a lower label constraint with value {@code i} for the node name {@code n} with label {@code l}.<br> Putting
	 * does not make any label optimization.
	 *
	 * @param l        It cannot be null or empty.
	 * @param nodeName the node name. It cannot be null. It must be the unmodified name of the node.
	 * @param i        It cannot be nullInt.
	 * @return true if the merge has been successful.
	 */
	boolean putLowerCaseValue(Label l, ALabel nodeName, int i);

	/**
	 * Merge an upper label constraint with delay {@code i} for the node name n with propositional label {@code l}.<br>
	 * If the new value makes other already present values redundant, such values are removed. If the new value is
	 * redundant, it is ignored.
	 *
	 * @param l        label of the value
	 * @param nodeName the node name. It cannot be null. It must be the unmodified name of the node.
	 * @param i        It cannot be nullInt.
	 * @return true if the merge has been successful.
	 */
	boolean mergeUpperCaseValue(Label l, ALabel nodeName, int i);

	/**
	 * Put an upper label constraint with delay i for the node name n with label l.<br> There is no optimization of the
	 * labeled values present after the insertion of this one.
	 *
	 * @param l        It cannot be null or empty.
	 * @param nodeName the node name. It cannot be null. It must be the unmodified name of the node.
	 * @param i        It cannot be nullInt.
	 * @return true if the merge has been successful.
	 */
	boolean putUpperCaseValue(Label l, ALabel nodeName, int i);

	/**
	 * Remove the upper label for node name n with label l.
	 *
	 * @param l a {@link it.univr.di.labeledvalue.Label} object.
	 * @param n a {@link it.univr.di.labeledvalue.ALabel} node name
	 * @return the old value
	 */
	int removeUpperCaseValue(Label l, ALabel n);

	/**
	 * @return the representation of all Upper Case Labels of the edge.
	 */
	String upperCaseValuesAsString();

	/**
	 * @return the number of Upper Case Labels of the edge.
	 */
	int upperCaseValueSize();
}
