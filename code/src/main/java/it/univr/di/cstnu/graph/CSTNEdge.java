// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.graph;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.univr.di.labeledvalue.Label;
import it.univr.di.labeledvalue.LabeledIntMap;

/**
 * Represents the behavior of a CSTN edge.
 *
 * @author posenato
 * @version $Rev: 840 $
 */
public interface CSTNEdge extends Edge {
	/**
	 * @return the implementing class to represent labeled values
	 */
	@SuppressWarnings("SameReturnValue")
	Class<? extends LabeledIntMap> getLabeledIntMapImplClass();

	/**
	 * @return the labeledValueMap. If there is no labeled values, return an empty map.
	 */
	LabeledIntMap getLabeledValueMap();

	/**
	 * Uses inputLabeledValue as internal labeled value map.
	 *
	 * @param inputLabeledValue the labeledValue to use
	 */
	void setLabeledValueMap(LabeledIntMap inputLabeledValue);

	/**
	 * @return the labeled values as a set
	 */
	ObjectSet<Object2IntMap.Entry<Label>> getLabeledValueSet();

	/**
	 * @param setToReuse a {@link it.unimi.dsi.fastutil.objects.ObjectSet} object.
	 * @return the labeled values as a set
	 */
	ObjectSet<Object2IntMap.Entry<Label>> getLabeledValueSet(ObjectSet<Object2IntMap.Entry<Label>> setToReuse);

	/**
	 * @return the minimal value among all ordinary labeled values if there are some values,
	 * {@link it.univr.di.labeledvalue.Constants#INT_NULL} otherwise.
	 */
	Entry<Label> getMinLabeledValue();

	/**
	 * @return the minimal value among all ordinary labeled values if there are some values,
	 * {@link it.univr.di.labeledvalue.Constants#INT_NULL} otherwise.
	 */
	int getMinValue();

	/**
	 * @return the minimal value among all ordinary labeled values having label without unknown literals, if there are
	 * some; {@link it.univr.di.labeledvalue.Constants#INT_NULL} otherwise.
	 */
	int getMinValueAmongLabelsWOUnknown();

	/**
	 * @param l a {@link it.univr.di.labeledvalue.Label} object.
	 * @return the value associated to label l or the minimal value associated to labels consistent with l if it exists,
	 * {@link it.univr.di.labeledvalue.Constants#INT_NULL} otherwise.
	 */
	int getMinValueConsistentWith(Label l);

	/**
	 * @param l a {@link it.univr.di.labeledvalue.Label} object.
	 * @return the value associated to label l or the minimal value associated to labels subsumed by {@code l} if
	 * it exists, {@link it.univr.di.labeledvalue.Constants#INT_NULL} otherwise. Just for the record, a label L1
	 * subsumes a label L2 when L1 contains all literals of L2.
	 */
	int getMinValueSubsumedBy(Label l);

	/**
	 * @param label label
	 * @return the value associated to label if it exists, {@link it.univr.di.labeledvalue.Constants#INT_NULL}
	 * otherwise.
	 */
	int getValue(Label label);

	/**
	 * Merges the labeled value i to the set of labeled values of this edge.
	 *
	 * @param l a {@link it.univr.di.labeledvalue.Label} object.
	 * @param i the new weight.
	 * @return true if the operation was successful, false otherwise.
	 */
	boolean mergeLabeledValue(Label l, int i);

	/**
	 * @param map a {@link it.univr.di.labeledvalue.LabeledIntMap} object.
	 */
	void mergeLabeledValue(LabeledIntMap map);

	/**
	 * Wrapper method for {@link #mergeLabeledValue(Label, int)}.
	 *
	 * @param i  the new weight
	 * @param ls a {@link java.lang.String} object.
	 * @return true if the operation was successful, false otherwise.
	 *
	 * @see #mergeLabeledValue(Label, int)
	 */
	boolean mergeLabeledValue(String ls, int i);

	/**
	 * @param l a {@link it.univr.di.labeledvalue.Label} object.
	 * @param i the new weight
	 * @return true if the value has been inserted, false otherwise.
	 */
	boolean putLabeledValue(Label l, int i);

	/**
	 * Remove the value labeled by l from the map. If the 'l' is not present, it does nothing.
	 *
	 * @param l a {@link it.univr.di.labeledvalue.Label} object.
	 */
	void removeLabeledValue(Label l);

	/**
	 * @return the number of labeled values associated to this edge.
	 */
	int size();
}
