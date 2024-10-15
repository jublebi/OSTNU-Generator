// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

package it.univr.di.cstnu.algorithms;

import it.unimi.dsi.fastutil.chars.CharSet;
import it.univr.di.Debug;
import it.univr.di.cstnu.graph.*;
import it.univr.di.cstnu.graph.Edge.ConstraintType;
import it.univr.di.labeledvalue.Constants;
import it.univr.di.labeledvalue.Label;
import it.univr.di.labeledvalue.LabeledLowerCaseValue;
import it.univr.di.labeledvalue.Literal;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple class to represent and check Conditional Simple Temporal Network with Uncertainty (CSTNU) where the DC
 * checking is done reducing the instance to an equivalent CSTN instance where the DC checking is done assuming
 * instantaneous reaction.<br> In other words, the CSTNU instance is transformed into a CSTN one and checked invoking
 * {@link it.univr.di.cstnu.algorithms.CSTNIR3R#dynamicConsistencyCheck()}.
 *
 * @author Roberto Posenato
 * @version $Rev: 840 $
 */
public class CSTNU2CSTN extends CSTNU {

	/**
	 * Version of the class
	 */
	// static final String VERSIONandDATE = "Version 3.1 - Apr, 20 2016";
	// static final String VERSIONandDATE = "Version 1.0 - September, 25 2016";
	// static final String VERSIONandDATE = "Version 1.1 - November, 14 2017";
	// static final String VERSIONandDATE = "Version 1.2 - December, 12 2017";
	// static final String VERSIONandDATE = "Version 1.3 - December, 23 2018";// tweaking the transformation
	public static final String VERSIONandDATE = "Version 1.4 - January, 21 2019";// fixed an error on timeOut
	/**
	 * logger
	 */
	static private final Logger LOG = Logger.getLogger(CSTNU2CSTN.class.getName());

	/**
	 * @param graph a {@link it.univr.di.cstnu.graph.TNGraph} object.
	 */
	public CSTNU2CSTN(TNGraph<CSTNUEdge> graph) {
		super(graph);
	}

	/**
	 * Constructor for CSTNU
	 *
	 * @param graph        TNGraph to check
	 * @param givenTimeOut timeout for the check
	 */
	public CSTNU2CSTN(TNGraph<CSTNUEdge> graph, int givenTimeOut) {
		super(graph, givenTimeOut);
	}

	/**
	 * Constructor for CSTNU2CSTN.
	 */
	CSTNU2CSTN() {
	}

	/**
	 * @param args an array of {@link java.lang.String} objects.
	 */
	@SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
	public static void main(final String[] args) {
		final String s = "\nSPDX-License-Identifier: LGPL-3.0-or-later, Roberto Posenato.\n";
		LOG.finest("Start...");
		final CSTNU2CSTN cstnu2cstn = new CSTNU2CSTN();

		System.out.print(s);
		if (!cstnu2cstn.manageParameters(args)) {
			return;
		}
		System.out.println("Parameters ok!");

		System.out.println("Loading tNGraph...");
		final TNGraphMLReader<CSTNUEdge> graphMLReader = new TNGraphMLReader<>();
		try {
			cstnu2cstn.setG(graphMLReader.readGraph(cstnu2cstn.fInput, EdgeSupplier.DEFAULT_CSTNU_EDGE_CLASS));
		} catch (IOException | ParserConfigurationException | SAXException e) {
			throw new RuntimeException(e);
		}
		System.out.println("TNGraph loaded!");

		System.out.println("DC Checking...");
		final CSTNUCheckStatus status;
		try {
			status = cstnu2cstn.dynamicControllabilityCheck();
		} catch (final WellDefinitionException e) {
			System.out.print("An error has been occurred during the checking: " + e.getMessage());
			return;
		}
		if (status.finished) {
			System.out.println("Checking finished!");
			if (status.consistency) {
				System.out.println("The given cstnu is Dynamic controllable!");
			} else {
				System.out.println("The given cstnu is NOT DC!");
			}
			System.out.println("Details: " + status);
		} else {
			System.out.println("Checking has not been finished!");
			System.out.println("Details: " + status);
		}

		if (cstnu2cstn.fOutput != null) {
			final TNGraphMLWriter graphWriter = new TNGraphMLWriter(null);
			try {
				graphWriter.save(cstnu2cstn.getG(), cstnu2cstn.fOutput);
			} catch (IOException e) {
				throw new RuntimeException("Problem saving the result", e);
			}
		}
	}

	/**
	 * {@inheritDoc} Checks the controllability of a CSTNU instance. This method transform the given CSTNU instance into
	 * a corresponding CSTN instance such that the original instance is dynamic <em>controllable</em> iff the
	 * corresponding CSTN is dynamic <em>consistent</em>.
	 */
	@Override
	public CSTNUCheckStatus dynamicControllabilityCheck() throws WellDefinitionException {
		if (Debug.ON) {
			LOG.log(Level.INFO, "Starting checking CSTNU2CSTN dynamic controllability.\n");
		}

		initAndCheck();

		final TNGraph<CSTNUEdge> nextGraph = new TNGraph<>(g, g.getEdgeImplClass());
		nextGraph.setName("Next tNGraph");
		final CSTNUCheckStatus status = new CSTNUCheckStatus();

		final Instant startInstant = Instant.now();

		LOG.info("Conversion to the corresponding CSTN instance...");
		final TNGraph<CSTNEdge> cstnGraph = transform();
		LOG.info("Conversion to the corresponding CSTN instance done.");

		LOG.info("CSTN DC-checking...");
		final CSTNIR3RwoNodeLabels cstnChecker = new CSTNIR3RwoNodeLabels(cstnGraph, timeOut);
		final CSTNCheckStatus cstnStatus = cstnChecker.dynamicConsistencyCheck();
		LOG.info("CSTN DC-checking done.");

		status.finished = cstnStatus.finished;
		status.consistency = cstnStatus.consistency;
		status.cycles = cstnStatus.cycles;
		status.r0calls = cstnStatus.r0calls;
		status.r3calls = cstnStatus.r3calls;
		status.labeledValuePropagationCalls = cstnStatus.labeledValuePropagationCalls;

		final Instant endInstant = Instant.now();
		status.executionTimeNS = Duration.between(startInstant, endInstant).toNanos();

		if (!status.consistency) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.log(Level.INFO, "The CSTNU instance is not DC controllable.\nStatus: " + status);
				}
			}
			return status;
		}

		// controllable && finished
		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO, "The CSTNU instance is DC controllable."
				                    + "\nStatus: " + status);
			}
		}
		// Put all data structures of currentGraph in 'g'
		// g.copyCleaningRedundantLabels(cstnGraph);
		// g.setName(originalName);
		return status;
	}

	/**
	 * Returns the corresponding CSTN of the given CSTNU g. The transformation consists in replacing each contingent
	 * link with a pattern that use a proper new observation timepoint (associated to the contingent link).
	 *
	 * <pre>
	 * A ---(c:x, alpha) ---&gt; C is transformed to A ---(x,alpha) ---&gt; K? ---(0, alpha k) (y-x,alpha) ---&gt; C
	 *   &lt;--- (C:-y, alpha)--                       &lt;---(-x,alpha)--    &lt;---(0,alpha) (x-y,alpha¬k)--
	 * </pre>
	 *
	 * @return g represented as a CSTN
	 */
	@SuppressWarnings("unchecked")
	TNGraph<CSTNEdge> transform() {
		final TNGraph<CSTNEdge> cstnGraph = new TNGraph<>("", EdgeSupplier.DEFAULT_CSTN_EDGE_CLASS);
		cstnGraph.copy(cstnGraph.getClass().cast(g));

		final int nOfContingents = g.getContingentNodeCount();
		if (nOfContingents == 0) {
			return cstnGraph;
		}

		final CharSet usedProposition = g.getPropositions();
		final int nOfTrueConditions = usedProposition.size();
		final int nOfVertices = g.getVertexCount();

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Used proposition: " + usedProposition
				           + "\nConditions: " + nOfTrueConditions
				           + "\nContingents: " + nOfContingents
				           + "\nVertices: " + nOfVertices);
			}
		}

		// Current implementation of Label allows only NUMBER_OF_POSSIBLE_PROPOSITION propositions at maximum
		if (nOfContingents + nOfTrueConditions > Label.NUMBER_OF_POSSIBLE_PROPOSITIONS) {
			throw new IllegalArgumentException(
				"The network cannot be checked by this method because the sum of the number of contingent links and the number of observation time points is greater than "
				+ Label.NUMBER_OF_POSSIBLE_PROPOSITIONS
				+ ", the maximum capacity of this implementation.");
		}
		// cleaning cstn
		cstnGraph.clear(nOfVertices + nOfContingents);

		// build the vector of proposition that can be used for codifying contingents.
		final char[] availableProposition = new char[nOfContingents];
		int j = 0;
		for (int i = 0; j < nOfContingents; i++) {
			final char c = Literal.charValue(i);
			if (usedProposition.contains(c)) {
				continue;
			}
			availableProposition[j++] = c;
		}

		// Clone all nodes
		LabeledNode newV;
		final LabeledNode Z = g.getZ();
		for (final LabeledNode v : g.getVertices()) {
			newV = LabeledNodeSupplier.get(v);
			cstnGraph.addVertex(newV);
			if (v.equalsByName(Z)) {
				cstnGraph.setZ(newV);
			}
		}

		// clone all edges, transforming the contingent ones
		CSTNEdge newE;
		CSTNUEdge eInverted;
		LabeledLowerCaseValue lowerCaseValueTuple;
		int firstPropAvailable = 0;
		for (final CSTNUEdge e : g.getEdges()) {
			final LabeledNode sInG = g.getSource(e);
			final LabeledNode dInG = g.getDest(e);
			if (!e.isContingentEdge()) {
				newE = cstnGraph.getEdgeFactory().get(e);
				assert sInG != null;
				assert dInG != null;
				cstnGraph.addEdge(newE, sInG.getName(), dInG.getName());
				continue;
			}
			// e is contingent!
			// Since for each contingent link, there is 2 contingent edges, we consider them only when we meet the
			// contingent edge with positive value (lower case value);
			// We assume that contingent edges contains only lower case value or upper case value, i.e., the network was already initialized!
			lowerCaseValueTuple = e.getLowerCaseValue();
			if (lowerCaseValueTuple.isEmpty()) {
				if (e.getUpperCaseValueMap().size() != 1) {
					throw new IllegalStateException("Edge " + e +
					                                " is contingent, but it doesn't contain upper case value neither lower case one.");
				}
				continue;
			}
			assert dInG != null;
			assert sInG != null;
			eInverted = g.findEdge(dInG.getName(), sInG.getName());

			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Considering e: " + e + "\nand its companion eInverted: " + eInverted);
				}
			}

			final int lowerCaseValue = lowerCaseValueTuple.getValue();
			assert eInverted != null;
			final int upperCaseValue = -eInverted.getMinUpperCaseValue().getValue().getIntValue();

			if (lowerCaseValue == Constants.INT_NULL || upperCaseValue == Constants.INT_NULL) {
				throw new IllegalStateException(
					"Something is wrong with the two contingent edges " + e + " and " + eInverted);
			}
			// new observation time point K
			final LabeledNode newK = LabeledNodeSupplier.get(availableProposition[firstPropAvailable] + "?",
			                                                 availableProposition[firstPropAvailable++]);
			// newK.setLabel(cstn.getNode(dInG.getName()).getLabel()); we consider only streamlined CSTN
			newK.setX(sInG.getX() + 10);
			newK.setY(sInG.getY());
			cstnGraph.addVertex(newK);
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Node added: " + newK);
				}
			}

			// two edges between X and K
			newE = cstnGraph.getEdgeFactory().get(sInG.getName() + "-" + newK.getName());
			newE.setConstraintType(ConstraintType.internal);
			final Label contingentOriginalLabel = lowerCaseValueTuple.getLabel();
			newE.mergeLabeledValue(contingentOriginalLabel, lowerCaseValue);
			cstnGraph.addEdge(newE, sInG.getName(), newK.getName());
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("New edge added: " + newE);
				}
			}

			newE = cstnGraph.getEdgeFactory().get(newK.getName() + "-" + sInG.getName());
			newE.setConstraintType(ConstraintType.internal);
			newE.mergeLabeledValue(contingentOriginalLabel, -lowerCaseValue);
			cstnGraph.addEdge(newE, newK.getName(), sInG.getName());
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("New edge added: " + newE);
				}
			}
			// two edges between K and C
			newE = cstnGraph.getEdgeFactory().get(dInG.getName() + "-" + newK.getName());
			newE.setConstraintType(ConstraintType.internal);
			newE.mergeLabeledValue(
				contingentOriginalLabel.conjunction(Label.valueOf(newK.getPropositionObserved(), Literal.NEGATED)),
				lowerCaseValue - upperCaseValue);// it is x-y.
			newE.mergeLabeledValue(contingentOriginalLabel, 0);
			cstnGraph.addEdge(newE, dInG.getName(), newK.getName());
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("New edge added: " + newE);
				}
			}

			newE = cstnGraph.getEdgeFactory().get(newK.getName() + "-" + dInG.getName());
			newE.setConstraintType(ConstraintType.internal);
			newE.mergeLabeledValue(
				contingentOriginalLabel.conjunction(Label.valueOf(newK.getPropositionObserved(), Literal.STRAIGHT)), 0);
			newE.mergeLabeledValue(contingentOriginalLabel, upperCaseValue - lowerCaseValue);// it is y-x
			cstnGraph.addEdge(newE, newK.getName(), dInG.getName());
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("New edge added: " + newE);
				}
			}
		}
		return cstnGraph;
	}

}
