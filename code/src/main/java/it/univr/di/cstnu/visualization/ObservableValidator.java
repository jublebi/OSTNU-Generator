// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.visualization;

import it.univr.di.cstnu.graph.LabeledNode;
import it.univr.di.cstnu.graph.TNGraph;
import org.netbeans.validation.api.Problems;
import org.netbeans.validation.api.Validator;

/**
 * Validator for observable.
 *
 * @author posenato
 * @version $Rev: 840 $
 */
public class ObservableValidator implements Validator<String> {

	/*
	 * logger
	 */
	//	@SuppressWarnings("unused")
	//	private static Logger LOG = Logger.getLogger(ObservableValidator.class.getName());

	/**
	 * Version
	 */
	static final public String VERSIONandDATE = "1.0, June, 9 2019";// Refactoring Edge

	/**
	 *
	 */
	TNGraph<?> tnGraph;
	/**
	 *
	 */
	LabeledNode node;

	/**
	 * @param g a {@link it.univr.di.cstnu.graph.TNGraph} object.
	 * @param n a {@link it.univr.di.cstnu.graph.LabeledNode} object.
	 */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "EI_EXPOSE_REP2",
		justification = "For efficiency reason, it includes an external mutable object.")
	public ObservableValidator(final TNGraph<?> g, final LabeledNode n) {
		if (g == null) {
			throw new NullPointerException("TNGraph cannot be null!");
		}
		tnGraph = g;
		if (n == null) {
			throw new NullPointerException("LabeledNode cannot be null!");
		}
		node = n;
	}

	@Override
	public void validate(final Problems problems, final String compName, final String model) {
		if ((model == null) || (model.isEmpty())) {
			return;
		}
		final LabeledNode currentNodeForProposition = tnGraph.getObserver(model.charAt(0));

		// LOG.finest("Validate: p=" + p + "; currentNodeForProposition=" + currentNodeForProposition + "; editedNode="
		// + node);

		if (currentNodeForProposition == null) {
			return;
		}
		if (currentNodeForProposition != node) {
			problems.append("An observer for '" + model.charAt(0) + "' already exists: " + currentNodeForProposition);
		}
	}

	@Override
	public Class<String> modelType() {
		return String.class;
	}
}
