// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

package it.univr.di.cstnu.algorithms;

import it.univr.di.Debug;
import it.univr.di.cstnu.graph.CSTNEdge;
import it.univr.di.cstnu.graph.LabeledNode;
import it.univr.di.cstnu.graph.LabeledNodeSupplier;
import it.univr.di.cstnu.graph.TNGraph;
import it.univr.di.labeledvalue.Constants;
import it.univr.di.labeledvalue.Label;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple class to represent and check Conditional Simple Temporal Network assuming epsilon semantics and reducing an
 * instance to an appropriate CSTN where DC checking is made assuming instantaneous reaction semantics.
 *
 * @author Roberto Posenato
 * @version $Rev: 840 $
 */
public class CSTN2CSTN0 extends CSTNEpsilonwoNodeLabels {
	/**
	 * Version of the class
	 */
	// static final String VERSIONandDATE = "Version 1.0 - November, 15 2017";
	static public final String VERSIONandDATE = "Version  1.1 - November, 20 2017";// It derives from
	/**
	 * logger
	 */
	static private final Logger LOG = Logger.getLogger(CSTN2CSTN0.class.getName());
	// CSTNEpsilonWONodeLabels

	/**
	 *
	 */
	private CSTN2CSTN0() {
	}

	/**
	 * @param givenReactionTime the reaction time of the network
	 * @param graph             a {@link it.univr.di.cstnu.graph.TNGraph} object.
	 */
	public CSTN2CSTN0(int givenReactionTime, TNGraph<CSTNEdge> graph) {
		super(givenReactionTime, graph);
	}

	/**
	 * @param givenReactionTime the reaction time of the network.
	 * @param graph             a {@link it.univr.di.cstnu.graph.TNGraph} object.
	 * @param givenTimeOut      the timeout for the check in seconds.
	 */
	public CSTN2CSTN0(int givenReactionTime, TNGraph<CSTNEdge> graph, int givenTimeOut) {
		super(givenReactionTime, graph, givenTimeOut);
	}

	/**
	 * @param args an array of {@link java.lang.String} objects.
	 *
	 * @throws java.io.IOException                            if any.
	 * @throws javax.xml.parsers.ParserConfigurationException if any.
	 * @throws org.xml.sax.SAXException                       if any.
	 */
	@SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
	public static void main(final String[] args) throws IOException, ParserConfigurationException, SAXException {
		defaultMain(args, new CSTN2CSTN0(), "Reduction to CSTN IR DC");
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Checks the controllability of a CSTNU instance. This method transform the given CSTNU instance into a
	 * corresponding CSTN instance such that the original instance is dynamic <em>controllable</em> iff the
	 * corresponding CSTN is dynamic <em>consistent</em>.
	 */
	@Override
	public CSTNCheckStatus dynamicConsistencyCheck() throws WellDefinitionException {
		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO, "Starting checking CSTN2CSTN0 DC...\n");
			}
		}

		initAndCheck();

		final TNGraph<CSTNEdge> nextGraph = new TNGraph<>(g, g.getEdgeImplClass());
		nextGraph.setName("Next tNGraph");
		final CSTNCheckStatus status = new CSTNCheckStatus();

		final Instant startInstant = Instant.now();

		LOG.info("Conversion to the corresponding CSTN instance...");
		final TNGraph<CSTNEdge> cstnGraph = transform();
		LOG.info("Conversion to the corresponding CSTN instance done.");

		LOG.info("CSTN DC-checking...");
		final CSTNIR3RwoNodeLabels cstnChecker = new CSTNIR3RwoNodeLabels(cstnGraph, timeOut);
		final CSTNCheckStatus cstnStatus = cstnChecker.dynamicConsistencyCheck();
		LOG.info("CSTN DC-checking done.");

		status.finished = cstnStatus.finished;
		status.consistency = cstnStatus.finished;
		status.cycles = cstnStatus.cycles;
		status.r0calls = cstnStatus.r0calls;
		status.r3calls = cstnStatus.r3calls;
		status.labeledValuePropagationCalls = cstnStatus.labeledValuePropagationCalls;

		final Instant endInstant = Instant.now();
		status.executionTimeNS = Duration.between(startInstant, endInstant).toNanos();

		if (!status.consistency) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.log(Level.INFO, "The CSTN instance is not DC controllable.\nStatus: " + status);
				}
			}
			return status;
		}

		// controllable && finished
		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO, "The CSTNU instance is DC controllable.\nStatus: " + status);
			}
		}
		// Put all data structures of currentGraph<CSTNEdge> in g
		// g.copyCleaningRedundantLabels(cstnGraph<CSTNEdge> );
		// g.setName(originalName);
		return status;
	}

	/**
	 * Returns the corresponding CSTN having each observation node P? is replaced with a pair of nodes P? and P?0. P? is
	 * standard node while P?0 is a new observation node that observes 'p'.<br> P?0 is set to be at distance epsilon
	 * after P?, exactly.<br>
	 *
	 * @return g represented as a CSTN0. In order to minimize name conflicts, the new name associated to P? is P?^0.
	 */
	TNGraph<CSTNEdge> transform() {
		final TNGraph<CSTNEdge> cstn = new TNGraph<>(g, g.getEdgeImplClass());

		final int nOfObservers = g.getObserverCount();
		if (nOfObservers == 0) {
			return cstn;
		}

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Input tNGraph: " + g);
			}
		}
		final Collection<LabeledNode> observers =
			new ArrayList<>(g.getObservers());// this.g.getObservers() will change at each
		// oldObs.setObservable(Constants.UNKNOWN);

		for (final LabeledNode oldObs : observers) {
			final LabeledNode newObs = LabeledNodeSupplier.get(oldObs);
			newObs.setName(newObs.getName() + "^0");
			newObs.setX(newObs.getX() + 30);
			newObs.setY(newObs.getY() + 30);
			oldObs.setObservable(Constants.UNKNOWN);
			while (!cstn.addVertex(newObs)) {
				newObs.setName(newObs.getName() + "0");// in case the name has been already used!
			}
			// add the two constraints for fixing the distance between the two nodes at epsilon.
			// To oldObs
			CSTNEdge newE = cstn.getEdgeFactory().get(newObs.getName() + "_" + oldObs.getName());
			newE.mergeLabeledValue(Label.emptyLabel, -getReactionTime());
			cstn.addEdge(newE, newObs, oldObs);
			// To newObs
			newE = cstn.getEdgeFactory().get(oldObs.getName() + "-" + newObs.getName());
			newE.mergeLabeledValue(Label.emptyLabel, getReactionTime());
			cstn.addEdge(newE, oldObs, newObs);
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Transformed tNGraph: " + cstn);
			}
		}
		return cstn;
	}

}
