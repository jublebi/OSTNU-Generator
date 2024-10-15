// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.graph;

import it.univr.di.labeledvalue.ALabel;
import it.univr.di.labeledvalue.Label;
import it.univr.di.labeledvalue.LabeledALabelIntTreeMap;

/**
 * Represents the behavior of a CSTNPSU edge.
 *
 * @author posenato
 * @version $Rev: 804 $
 */
@SuppressWarnings("InterfaceWithOnlyOneDirectInheritor")
public interface CSTNPSUEdge extends BasicCSTNUEdge {
	/**
	 * @return the set of maps of labeled values and labeled lower-case ones. The maps of labeled values has ALabel
	 * empty.
	 */
	LabeledALabelIntTreeMap getAllLowerCaseAndLabeledValuesMaps();

	/**
	 * @param l    a {@link it.univr.di.labeledvalue.Label} object.
	 * @param name a {@link it.univr.di.labeledvalue.ALabel} node name.
	 * @return the labeled lower-Case value object. Use {@link it.univr.di.labeledvalue.LabeledLowerCaseValue#isEmpty()}
	 * to check if it contains or not a significant value.
	 */
	default int getLowerCaseValue(Label l, ALabel name) {
		return 0;
	}

	/**
	 * @return the Lower-Case labeled Value Map.
	 */
	LabeledALabelIntTreeMap getLowerCaseValueMap();

	/**
	 * @param l a {@link it.univr.di.labeledvalue.Label} object.
	 * @param n a {@link it.univr.di.labeledvalue.ALabel} node name
	 * @return the value of the removed labeled value
	 */
	@SuppressWarnings("UnusedReturnValue")
	int removeLowerCaseValue(Label l, ALabel n);

	/**
	 * @param lowerCaseValue the labeled lower case value to use for initializing the current one.
	 */
	void setLowerCaseValue(LabeledALabelIntTreeMap lowerCaseValue);
}
