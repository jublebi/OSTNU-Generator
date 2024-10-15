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
 * 2016 paper, table 1) and using LP, qR0, and qR3* rules.
 * <p>
 * In this class, an input CSTN TNGraph is transformed into an equivalent CSTN instance where node labels are empty.
 *
 * @author Roberto Posenato
 * @version $Rev: 840 $
 */
@SuppressWarnings("ClassWithoutLogger")
public class CSTNIR3RwoNodeLabels extends CSTNIR3R {

	/**
	 * Version of the class
	 */
	// static public final String VERSIONandDATE = "Version 1.0 - November, 07 2017";
	// static final public String VERSIONandDATE = "Version 1.1 - November, 11 2017";// Replace Ω node with equivalent constraints.
	// static final public String VERSIONandDATE = "Version 1.2 - November, 21 2017";// Now it is derived from CSTNIR3R
	static final public String VERSIONandDATE = "Version 1.3 - November, 22 2017";// Now it is derived from CSTNIR3R

	/**
	 * Default constructor.
	 */
	CSTNIR3RwoNodeLabels() {
		withNodeLabels = false;
	}

	/**
	 * Constructor for
	 *
	 * @param g1 tNGraph to check
	 */
	public CSTNIR3RwoNodeLabels(TNGraph<CSTNEdge> g1) {
		super(g1);
		withNodeLabels = false;
	}

	/**
	 * @param g1       a {@link it.univr.di.cstnu.graph.TNGraph} object.
	 * @param timeOut1 the timeout for the check in seconds.
	 */
	public CSTNIR3RwoNodeLabels(TNGraph<CSTNEdge> g1, int timeOut1) {
		super(g1, timeOut1);
		withNodeLabels = false;
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
		defaultMain(args, new CSTNIR3RwoNodeLabels(),
		            "Instantaneous Reaction DC based on 3 Rules and without node labels");
	}
}
