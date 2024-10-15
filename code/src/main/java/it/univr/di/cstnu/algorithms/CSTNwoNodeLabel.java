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
 * integer. The dynamic consistency check (DC check) is done assuming standard DC semantics (cf. ICAPS 2016 paper, table
 * 1) and using LP, R0, qR0, R3*, and qR3* rules.<br> In this class, an input CSTN tNGraph is transformed into an
 * equivalent CSTN instance where node labels are empty.<br>
 *
 * @author Roberto Posenato
 * @version $Rev: 832 $
 */
@SuppressWarnings("ClassWithoutLogger")
public class CSTNwoNodeLabel extends CSTN {

	/*
	 * logger
	 */
//	@SuppressWarnings("hiding")
//	static Logger LOG = Logger.getLogger(CSTNwoNodeLabel.class.getName());

	/**
	 * Version of the class
	 */
	// static public final String VERSIONandDATE = "Version 1.2 - April, 25 2017";
	// static public final String VERSIONandDATE = "Version 1.3 - October, 10 2017";// removed qLabels from LP
	// static public final String VERSIONandDATE = "Version 1.4 - November, 07 2017";// restored original LP
	// static public final String VERSIONandDATE = "Version 1.5 - November, 15 2017";// Removed the possibility of auxiliary constraints.
	static public final String VERSIONandDATE = "Version  1.6 - November, 17 2017";// Adjusted LP

	/**
	 * Default constructor.
	 */
	CSTNwoNodeLabel() {
		withNodeLabels = false;
	}

	/**
	 * Constructor for
	 *
	 * @param graph TNGraph to check
	 */
	public CSTNwoNodeLabel(TNGraph<CSTNEdge> graph) {
		super(graph);
		withNodeLabels = false;
	}

	/**
	 * @param graph        a {@link it.univr.di.cstnu.graph.TNGraph} object.
	 * @param givenTimeOut timeout for the check in seconds.
	 */
	public CSTNwoNodeLabel(TNGraph<CSTNEdge> graph, int givenTimeOut) {
		super(graph, givenTimeOut);
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
		defaultMain(args, new CSTNwoNodeLabel(), "Standard DC without node labels");
	}
}
