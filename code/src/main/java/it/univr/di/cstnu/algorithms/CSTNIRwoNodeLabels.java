// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

package it.univr.di.cstnu.algorithms;

import it.univr.di.cstnu.graph.CSTNEdge;
import it.univr.di.cstnu.graph.TNGraph;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

/**
 * Simple class to represent and DC check Conditional Simple Temporal Network (CSTN) where the edge weight are signed
 * integer. The dynamic consistency check (DC check) is done assuming instantaneous reaction DC semantics (cf. ICAPS
 * 2016 paper, table 1) and using LP, R0, qR0, R3*, and qR3* rules.<br>
 * In this class, an input CSTN graph is transformed into an equivalent CSTN instance where node labels are empty.
 *
 * @author Roberto Posenato
 * @version $Rev: 832 $
 */
@SuppressWarnings("ClassWithoutLogger")
public class CSTNIRwoNodeLabels extends CSTNIR {

	/**
	 * Version of the class
	 */
	// static public final String VERSIONandDATE = "Version 1.0 - November, 07 2017";
	// static final public String VERSIONandDATE = "Version 1.1 - November, 11 2017";// Replace Ω node with equivalent constraints.
	// static final public String VERSIONandDATE = "Version 2.0 - November, 16 2017";// Now the super class is CSTNwoNodeLabel
	static final public String VERSIONandDATE = "Version 2.1 - November, 22 2017";// Now the super class is CSTNIR

	/**
	 * Default constructor.
	 */
	CSTNIRwoNodeLabels() {
		withNodeLabels = false;
	}

	/**
	 * Constructor for
	 *
	 * @param g1 graph to check
	 */
	public CSTNIRwoNodeLabels(TNGraph<CSTNEdge> g1) {
		super(g1);
		withNodeLabels = false;
	}

	/**
	 * @param g1       a {@link it.univr.di.cstnu.graph.TNGraph} object.
	 * @param timeOut1 the timeout for the check in seconds.
	 */
	public CSTNIRwoNodeLabels(TNGraph<CSTNEdge> g1, int timeOut1) {
		super(g1, timeOut1);
		withNodeLabels = false;
	}

	/**
	 * Just for using this class also from a terminal.
	 *
	 * @param args an array of {@link java.lang.String} objects.
	 * @throws java.io.IOException                            if any.
	 * @throws javax.xml.parsers.ParserConfigurationException if any.
	 * @throws org.xml.sax.SAXException                       if any.
	 */
	@SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
	public static void main(final String[] args) throws IOException, ParserConfigurationException, SAXException {
		defaultMain(args, new CSTNIRwoNodeLabels(), "Instantaneous Reaction  DC without node labels");
	}
}
