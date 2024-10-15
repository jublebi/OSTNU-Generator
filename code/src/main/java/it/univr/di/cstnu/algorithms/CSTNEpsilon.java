// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

package it.univr.di.cstnu.algorithms;

import it.univr.di.cstnu.graph.CSTNEdge;
import it.univr.di.cstnu.graph.LabeledNode;
import it.univr.di.cstnu.graph.TNGraph;
import it.univr.di.labeledvalue.Constants;
import org.kohsuke.args4j.Option;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

/**
 * Simple class to represent and DC check Conditional Simple Temporal Network (CSTN) where the edge weight are signed
 * integer. The dynamic consistency check (DC check) is done assuming epsilon DC semantics (cf. ICAPS 2016 paper, table
 * 2) and using LP, R0, qR0, R3*, and qR3* rules.
 *
 * @author Roberto Posenato
 * @version $Rev: 840 $
 */
@SuppressWarnings("ClassWithoutLogger")
public class CSTNEpsilon extends CSTN {

	/**
	 * Version of the class
	 */
	// static public final String VERSIONandDATE = "Version 1.0 - April, 03 2017";// first release
	static public final String VERSIONandDATE = "Version  1.1 - October, 11 2017";

	/**
	 * Reaction time for CSTN
	 */
	@Option(name = "-e", usage = "Forced Reaction Time. It must be > 0.")
	int epsilon = 1;

	/**
	 * Constructor for CSTN.
	 *
	 * @param reactionTime1 reaction time. It must be strictly positive.
	 * @param g1            tNGraph to check
	 * @param timeOut1      timeout for the check
	 *
	 * @see CSTN#CSTN(TNGraph, int)
	 */
	public CSTNEpsilon(int reactionTime1, TNGraph<CSTNEdge> g1, int timeOut1) {
		this(reactionTime1, g1);
		timeOut = timeOut1;
	}

	/**
	 * Constructor for CSTN.
	 *
	 * @param reactionTime1 reaction time. It must be strictly positive.
	 * @param g1            tNGraph to check
	 *
	 * @see CSTN#CSTN(TNGraph)
	 */
	public CSTNEpsilon(int reactionTime1, TNGraph<CSTNEdge> g1) {
		super(g1);
		if (reactionTime1 <= 0) {
			throw new IllegalArgumentException("Reaction time must be > 0.");
		}
		epsilon = reactionTime1;
		reactionTime = reactionTime1;
	}

	/**
	 * Default constructor. Label optimization.
	 */
	CSTNEpsilon() {
	}

	/**
	 * Just for using this class also from a terminal.
	 *
	 * @param args an array of {@link java.lang.String} objects.
	 *
	 * @throws java.io.IOException                            if any.
	 * @throws javax.xml.parsers.ParserConfigurationException if any.
	 * @throws org.xml.sax.SAXException                       if any.
	 */
	@SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
	public static void main(final String[] args) throws IOException, ParserConfigurationException, SAXException {
		defaultMain(args, new CSTNEpsilon(), "Epsilon DC");
	}

	/**
	 * @return the reactionTime
	 */
	public int getEpsilonReactionTime() {
		return epsilon;
	}

	/**
	 *
	 */
	@Override
	boolean lpMustRestricted2ConsistentLabel(final int u, final int v) {
		// Table 1 ICAPS paper for standard DC
		return u >= epsilon && v < 0;
	}

	/**
	 *
	 */
	@Override
	final boolean mainConditionForSkippingInR0qR0(final int w) {
		// Table 2 ICAPS2016 paper for epsilon semantics
		return w >= epsilon;
	}

	/**
	 *
	 */
	@Override
	final boolean mainConditionForSkippingInR3qR3(final int w, final LabeledNode nD) {
		// Table 2 ICAPS for epsilon semantics
		// (w > 0 && nD==Z) is not added because w is always <=0 when nD==Z.
		return w > epsilon;
	}


	/**
	 * According to Table 2 paper ICAPS16, it returns {@code max(edgeValue, obsEdgeValue-ɛ)}.
	 */
	@Override
	final int newValueInR3qR3(final int edgeValue, final int obsEdgeValue) {
		// Table 2 ICAPS2016.
		final int w1 = (obsEdgeValue == Constants.INT_NEG_INFINITE || obsEdgeValue == Constants.INT_POS_INFINITE)
		               ? obsEdgeValue : obsEdgeValue - epsilon;
		return Math.max(edgeValue, w1);
	}

}
