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
 * integer. The dynamic consistency check (DC check) is done assuming epsilon DC semantics (cf. ICAPS 2016 paper, table
 * 2) and using LP, qR0, and qR3* rules.
 *
 * @author Roberto Posenato
 * @version $Rev: 832 $
 */
@SuppressWarnings("ClassWithoutLogger")
public class CSTNEpsilon3R extends CSTNEpsilon {

	/**
	 * Version of the class
	 */
	static final public String VERSIONandDATE = "Version 1.0 - November, 22 2017";

	/**
	 * Default constructor.
	 */
	CSTNEpsilon3R() {
		super();
		propagationOnlyToZ = true;
	}

	/**
	 * Constructor for CSTN with reaction time at least epsilon and without node labels.
	 *
	 * @param reactionTime1 reaction time. It must be strictly positive.
	 * @param g1            tNGraph to check
	 */
	public CSTNEpsilon3R(int reactionTime1, TNGraph<CSTNEdge> g1) {
		super(reactionTime1, g1);
		propagationOnlyToZ = true;
	}

	/**
	 * @param reactionTime1 the reaction time of the network.
	 * @param g1            a {@link it.univr.di.cstnu.graph.TNGraph} object.
	 * @param timeOut1      the timeout for the check in seconds.
	 */
	public CSTNEpsilon3R(int reactionTime1, TNGraph<CSTNEdge> g1, int timeOut1) {
		super(reactionTime1, g1, timeOut1);
		propagationOnlyToZ = true;
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
		defaultMain(args, new CSTNEpsilon3R(), "Epsilon DC based on 3 Rules");
	}
}
