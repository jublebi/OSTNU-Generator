// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.graph;

import it.univr.di.labeledvalue.ALabel;
import it.univr.di.labeledvalue.Label;
import it.univr.di.labeledvalue.LabeledLowerCaseValue;

/**
 * Represents the behavior of a CSTNU edge.
 *
 * @author posenato
 * @version $Rev: 804 $
 */
@SuppressWarnings("InterfaceWithOnlyOneDirectInheritor")
public interface CSTNUEdge extends BasicCSTNUEdge {

	/**
	 * @return the value of the removed labeled value
	 */
	@SuppressWarnings("UnusedReturnValue")
	int removeLowerCaseValue();

	/**
	 * Set a lower label constraint with delay i for the node n with label l.<br> If a lower label with label l for node
	 * n is already present, it is overwritten.
	 *
	 * @param l        It cannot be null or empty.
	 * @param nodeName the node. It cannot be null.
	 * @param i        It cannot be null.
	 */
	void setLowerCaseValue(Label l, ALabel nodeName, int i);

	/**
	 * @param lowerCaseValue the labeled lower case value to use for initializing the current one.
	 */
	void setLowerCaseValue(LabeledLowerCaseValue lowerCaseValue);
}
