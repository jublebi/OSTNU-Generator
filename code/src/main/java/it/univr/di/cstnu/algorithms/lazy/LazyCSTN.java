// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

package it.univr.di.cstnu.algorithms.lazy;

import it.unimi.dsi.fastutil.objects.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap.Entry;
import it.univr.di.Debug;
import it.univr.di.cstnu.algorithms.WellDefinitionException;
import it.univr.di.cstnu.graph.Edge.ConstraintType;
import it.univr.di.cstnu.graph.LabeledNode;
import it.univr.di.cstnu.graph.lazy.LabeledLazyWeightEdge;
import it.univr.di.cstnu.graph.lazy.LabeledLazyWeightGraph;
import it.univr.di.cstnu.graph.lazy.LazyGraphMLReader;
import it.univr.di.cstnu.graph.lazy.LazyGraphMLWriter;
import it.univr.di.cstnu.visualization.CSTNUStaticLayout;
import it.univr.di.labeledvalue.Constants;
import it.univr.di.labeledvalue.Label;
import it.univr.di.labeledvalue.Literal;
import it.univr.di.labeledvalue.lazy.LabeledLazyWeightTreeMap;
import it.univr.di.labeledvalue.lazy.LazyNumber;
import it.univr.di.labeledvalue.lazy.LazyPiece;
import it.univr.di.labeledvalue.lazy.LazyWeight;
import it.univr.di.labeledvalue.lazy.LazyWeight.SubType;
import org.apache.commons.math3.fraction.Fraction;
import org.kohsuke.args4j.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Iterator;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple class to represent and DC check Conditional Simple Temporal Network (CSTN) where the edge weight are signed integer or linear function of a common
 * symbolic parameter ∂. The value of ∂ is determined during the check in such a way the CSTN is DC (if it is possible).
 * The dynamic consistency check (DC check) is done assuming instantaneous DC semantics and using LP, R0, qR0, R3*, and qR3*
 * rules published at ICAPS 2018.<br>
 *
 * @author Roberto Posenato
 * @version $Id: $Id
 */
@SuppressWarnings("ALL")
public final class LazyCSTN {

	/**
	 * Simple class to represent the status of the checking algorithm during an execution.
	 *
	 * @author Roberto Posenato
	 */
	public static class LazyCSTNCheckStatus {
		/**
		 * True if the network is consistent so far.
		 */
		public boolean consistency = true;

		/**
		 * Counters # cycles
		 */
		public int cycles = 0;

		/**
		 * Counters #applications of r0 rule
		 */
		public int r0calls = 0;
		/** Counters #applications of r3 rule
		 */
		public int r3calls = 0;
		/** Counters #applications of label propagation rule
		 */
		public int labeledValuePropagationCalls = 0;
		/**
		 * Counters #applications of potential update
		 */
		public int potentialUpdate;


		/**
		 * Execution time in nanoseconds.
		 */
		public long executionTimeNS = Constants.INT_NULL;

		/**
		 * Standard Deviation of Execution time if this last one is a mean. In nanoseconds.
		 */
		public long stdDevExecutionTimeNS = Constants.INT_NULL;

		/**
		 * True if no rule can be applied anymore.
		 */
		public boolean finished = false;

		/**
		 * True if check has been interrupted because a give time-out has occurred.
		 */
		public boolean timeout = false;

		/**
		 * True if all data structured have been initialized.
		 */
		boolean initialized = false;

		/**
		 * Edge representing the negative loop
		 */
		LazyWeight negativeLoop = null;

		/**
		 * Global minimum delta
		 */
		Fraction minimumDelta;

		/**
		 * Reset all indexes.
		 */
		public void reset() {
			this.consistency = true;
			this.cycles = 0;
			this.r0calls = 0;
			this.r3calls = 0;
			this.labeledValuePropagationCalls = 0;
			// this.qAllNegLoop = 0;
			// this.qSemiNegLoop = 0;
			this.executionTimeNS = this.stdDevExecutionTimeNS = Constants.INT_NULL;
			this.finished = this.timeout = false;
			this.initialized = false;
			this.negativeLoop = null;
			this.minimumDelta = new Fraction(Constants.INT_NULL);
		}

		@Override
		public String toString() {
			return ("The check is "
					+ (this.finished ? "" : "NOT")
					+ " finished after "
					+ this.cycles
					+ " cycle(s).\n"
					+ ((this.finished)
							? "the consistency check has determined that given network is " + (this.consistency ? "" : "NOT ") + "consistent.\n"
							: "")
					+ "Some statistics:\nRule R0 has been applied " + this.r0calls + " times.\n"
					// + "Rule R1 has been applied " + this.r1calls + " times.\n"
					// + "Rule R2 has been applied " + this.r2calls + " times.\n"
					+ "Rule R3 has been applied " + this.r3calls + " times.\n"
					+ "Rule Labeled Propagation has been applied " + this.labeledValuePropagationCalls + " times.\n"
					// + "Negative qLoops: " + this.qAllNegLoop + "\n"
					// + "Negative qLoops with positive edge: " + this.qSemiNegLoop + "\n"
					+ ((this.timeout) ? "Checking has been interrupted because execution time exceeds the given time limit.\n"
							: "")
					+ ((this.executionTimeNS != Constants.INT_NULL)
							? "The global execution time has been " + this.executionTimeNS + " ns (~" + (this.executionTimeNS / 1E9) + " s.)\n"
							: "")
					+ ((this.minimumDelta.intValue() != Constants.INT_NULL)
							? "The determined delta is " + this.minimumDelta
							: ""));
		}
	}

	/**
	 * Value for dcSemantics
	 */
	public enum DCSemantics {
		/**
		 * Instantaneous reaction semantics
		 */
		IR,
		/**
		 * Standard semantics
		 */
		Std,
		/**
		 * ε-reaction semantics
		 */
		ε
	}

	/**
	 * @author posenato
	 */
	public static class EdgesToCheck implements Iterable<LabeledLazyWeightEdge> {
		/**
		 * It must be a set because an edge could be added more times!
		 */
		public ObjectRBTreeSet<LabeledLazyWeightEdge> edgesToCheck;
		/**
		 *
		 */
		public boolean alreadyAddAllIncidentsToZ;

		/**
		 *
		 */
		public EdgesToCheck() {
			this.edgesToCheck = new ObjectRBTreeSet<>();
			this.alreadyAddAllIncidentsToZ = false;
		}

		/**
		 * A simple constructor when the initial set of edges is available.
		 *
		 * @param coll none
		 */
		public EdgesToCheck(Collection<LabeledLazyWeightEdge> coll) {
			this.edgesToCheck = new ObjectRBTreeSet<>(coll);
			this.alreadyAddAllIncidentsToZ = false;
		}

		/**
		 * Check if the edge that has to be added has one end-point that is an observer. In positive case, it adds
		 * all in edges to the destination node for guaranteeing that R3* can be applied again with new values.
		 *
		 * @param enSnD none
		 * @param nS none
		 * @param nD none
		 * @param Z none
		 * @param g none
		 * @param applyReducedSetOfRules none
		 */
		final void add(LabeledLazyWeightEdge enSnD, LabeledNode nS, LabeledNode nD, LabeledNode Z, LabeledLazyWeightGraph g, boolean applyReducedSetOfRules) {
			// in any case, the edge has to be added.
			this.edgesToCheck.add(enSnD);
			// then,
			if (!nS.isObserver())
				return;
			// add all incident to nD
			if (nD != Z) {
				if (!applyReducedSetOfRules)
					this.edgesToCheck.addAll(g.getInEdges(nD));
				return;
			}

			if (this.alreadyAddAllIncidentsToZ)
				return;
			this.edgesToCheck.addAll(g.getInEdges(Z));
			this.alreadyAddAllIncidentsToZ = true;
		}

		/**
		 * Add an edge without any check.
		 *
		 * @param enSnD none
		 * @return true if this set did not already contain the specified element
		 */
		final boolean add(LabeledLazyWeightEdge enSnD) {
			return this.edgesToCheck.add(enSnD);
		}

		/**
		 * Add a set of edges without any check.
		 *
		 * @param eSet none
		 * @return true if this set changed after the add.
		 */
		final boolean addAll(Collection<LabeledLazyWeightEdge> eSet) {
			return this.edgesToCheck.addAll(eSet);
		}

		/**
		 * Clear the set.
		 */
		public void clear() {
			this.edgesToCheck.clear();
			this.alreadyAddAllIncidentsToZ = false;
		}

		@Override
		public Iterator<LabeledLazyWeightEdge> iterator() {
			return this.edgesToCheck.iterator();
		}

		/**
		 * @return the number of edges in the set.
		 */
		public int size() {
			return (this.edgesToCheck != null) ? this.edgesToCheck.size() : 0;
		}

		/**
		 * Copy fields reference of into this.
		 * After this method, this and input share the internal fields.
		 *
		 * @param input none
		 */
		void takeIn(EdgesToCheck input) {
			if (input == null)
				return;
			this.edgesToCheck = input.edgesToCheck;
			this.alreadyAddAllIncidentsToZ = input.alreadyAddAllIncidentsToZ;
		}
	}

	/**
	 * logger
	 */
	static Logger LOG = Logger.getLogger(LazyCSTN.class.getName());

	/**
	 * Version of the class
	 */
	// static final String VERSIONandDATE = "Version 3.1 - Apr, 20 2016";
	// static final String VERSIONandDATE = "Version 3.3 - October, 4 2016";
	// static final String VERSIONandDATE = "Version 4.0 - October, 25 2016";// added management not all negative edges in a negative qLoop
	// static final public String VERSIONandDATE = "Version 5.0 - April, 03 2017";// re-factored
	// static final public String VERSIONandDATE = "Version 5.2 - October, 16 2017";// better log management. This version uses LP,R0,R3*,qLP,qR0,qR3* and
	// horizon!
	// static final public String VERSIONandDATE = "Version 5.3 - November, 9 2017";// Replaced Omega node with equivalent constraints.
	// static final public String VERSIONandDATE = "Version 5.4 - November, 17 2017";// Adjusted LP
	// static final public String VERSIONandDATE = "Version 5.5 - November, 23 2017";// Adjusted skipping condition in LP
	// static final public String VERSIONandDATE = "Version 5.6 - November, 23 2017";// Horizon tweaking
	// static final public String VERSIONandDATE = "Version 5.7 - December, 13 2017";// Code tweaking
	static final public String VERSIONandDATE = "Version  5.8 - January, 17 2019";// Code tweaking

	/**
	 * The name for the initial node.
	 */
	String zeroNodeName = "Z";

	/**
	 * @param timeoutInstant none
	 * @param status none
	 * @return true if timeOut has been reached.
	 */
	static boolean checkTimeOutAndAdjustStatus(Instant timeoutInstant, LazyCSTNCheckStatus status) {
		if (Instant.now().isAfter(timeoutInstant)) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.log(Level.FINE, "Time out occurred!");
				}
			}
			status.timeout = true;
			status.consistency = false;
			status.finished = false;
			return true;
		}
		return false;
	}

	/**
	 * @param args none
	 * @param cstn none
	 * @param kindOfChecking none
	 * @throws SAXException none
	 * @throws ParserConfigurationException none
	 * @throws IOException none
	 */
	static void defaultMain(final String[] args, final LazyCSTN cstn, String kindOfChecking) throws IOException, ParserConfigurationException, SAXException {
		cstn.printVersion();
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Start...");
			}
		}
		if (!cstn.manageParameters(args))
			return;
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Parameters ok!");
			}
		}
		System.out.println("Starting execution...");
		if (cstn.versionReq) {
			return;
		}

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Loading graph...");
			}
		}
		LazyGraphMLReader graphMLReader = new LazyGraphMLReader(cstn.fInput);
		cstn.setG(graphMLReader.readGraph());

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("LabeledIntGraph loaded!\n" + kindOfChecking + " Checking...");
			}
		}
		LazyCSTNCheckStatus status;
		try {
			status = cstn.dynamicConsistencyCheck();
		} catch (final WellDefinitionException e) {
			System.out.print("An error has been occured during the checking: " + e.getMessage());
			return;
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("LabeledIntGraph minimized!");
			}
		}
		if (status.finished) {
			System.out.println("Checking finished!");
			if (status.consistency) {
				System.out.println("The given CSTN is Dynamic consistent!");
			} else {
				System.out.println("The given CSTN is not Dynamic consistent!");
			}
			System.out.println("Details: " + status);
		} else {
			System.out.println("Checking has not been finished!");
			System.out.println("Details: " + status);
		}

		if (cstn.fOutput != null) {
			final LazyGraphMLWriter graphWriter = new LazyGraphMLWriter(new CSTNUStaticLayout<>(cstn.g));
			try {
				graphWriter.save(cstn.g, new PrintWriter(cstn.output));
			} catch (final IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Just for using this class also from a terminal.
	 *
	 * @param args an array of {@link java.lang.String} objects.
	 * @throws java.io.IOException if any.
	 * @throws javax.xml.parsers.ParserConfigurationException if any.
	 * @throws org.xml.sax.SAXException if any.
	 */
	public static void main(final String[] args) throws IOException, ParserConfigurationException, SAXException {
		defaultMain(args, new LazyCSTN(), "Standard DC");
	}

	/**
	 * @param label none
	 * @param lazyWeight none
	 * @return the conventional representation of a labeled value
	 */
	static String pairAsString(Label label, LazyWeight lazyWeight) {
		return LabeledLazyWeightTreeMap.entryAsString(label, lazyWeight);
	}

	/**
	 * Check using full set of rules R0, qR0, R3, qR3, LP, qLP or the reduced set qR0, qR3, LP.
	 */
	// @Option(required = false, name = "-limitToZ", usage = "Check DC propagating only values on edges to Z.")
	final boolean propagationOnlyToZ = false;

	/**
	 * Check status
	 */
	LazyCSTNCheckStatus checkStatus = new LazyCSTNCheckStatus();

	/**
	 * The input file containing the CSTN graph in GraphML format.
	 */
	@Argument(required = false, index = 0, usage = "file_name must be the input CSTN graph in GraphML format.", metaVar = "file_name")
	File fInput;

	/**
	 * Output file where to write the XML representing the minimal CSTN graph.
	 */
	@Option(required = false, name = "-o", aliases = "--output", usage = "output to this file. If file is already "
			+ "present, it is overwritten. If this parameter is not present, then the output is sent to the std output.", metaVar = "output_file_name")
	File fOutput = null;

	/**
	 * Timeout in seconds for the check.
	 */
	@Option(required = false, name = "-t", aliases = "--timeOut", usage = "Timeout in seconds for the check", metaVar = "seconds")
	int timeOut = 2700;

	/**
	 * Graph on which to operate.
	 */
	LabeledLazyWeightGraph g = null;

	/**
	 * WD2.2 epsilon value called also reaction time in ICAPS 18.
	 * It is > 0 in standard CSTN, >= 0 in IR, > epsilon in Epsilon CSTN.
	 * Even when it is 0, the dynamic consistency def. excludes that a t.p. X having p in its label can be executed at the same time of t.p. P?.
	 * This is because, at time t, the history is the same and, therefore, X should be executed at t in very scenario, even in the one where it cannot stay!
	 * <b>Such value and WD2.2 property is not necessary as required in the past because Dynamic Execution definition already contains it.</b>
	 * On the other hand, propagation rules needs such value to be complete.
	 * Therefore, WD2.2 is not more required as CSTN property, but it is imposed as propagation rule.
	 */
	// @Option(required = false, name = "-r", aliases = "--reactionTime", usage = "Reaction time. It must be >= 0.")
	final int reactionTime = 0;// IR reaction time

	/**
	 * DCchecking can be done also assuming that all node labels are empty.
	 * This assumption usually make the checking slower in medium size network, but faster in small ones.
	 * So, in standard class it is assumed to consider also node labels.
	 * Derived classes can put this values false for checking the network with the alternative approach.
	 */
	final boolean withNodeLabels = false;

	/**
	 * DCChecking requires to use unknown literals to be complete.
	 * This flag can disable unknown literals if one want to verify if they are necessary for a specific check.
	 */
	final public boolean withUnknown = true;

	/**
	 * Absolute value of the max negative weight determined during initialization phase.
	 */
	int maxWeight = Constants.INT_NULL;

	/**
	 * Horizon value. A node that has to be executed after such time means that it has not to be executed!
	 */
	int horizon = Constants.INT_NULL;

	/**
	 * Z node of the graph.
	 * Utility reference for many method. #initAndCheck sets this value.
	 */
	LabeledNode Z = null;

	/**
	 * Output stream to fOutput
	 */
	PrintStream output = null;

	/**
	 * Software Version.
	 */
	@Option(required = false, name = "-v", aliases = "--version", usage = "Version")
	boolean versionReq = false;

	/**
	 * Default constructor.
	 */
	LazyCSTN() {
	}

	/**
	 * <p>
	 * Constructor for LazyCSTN.
	 * </p>
	 *
	 * @param g1 graph to check
	 */
	public LazyCSTN(LabeledLazyWeightGraph g1) {
		this();
		this.setG(g1);// sets also checkStatus!
	}

	/**
	 * <p>
	 * Constructor for LazyCSTN.
	 * </p>
	 *
	 * @param g1 graph to check
	 * @param timeOut1 timeout for the check
	 */
	public LazyCSTN(LabeledLazyWeightGraph g1, int timeOut1) {
		this(g1);
		this.timeOut = timeOut1;
	}

	/**
	 * checkWellDefinitionProperties.
	 * It checks only WD1, WD2 (light version).
	 *
	 * @return true if the g is a CSTN well-defined.
	 * @throws it.univr.di.cstnu.algorithms.WellDefinitionException if any.
	 */
	boolean checkWellDefinitionProperties() throws WellDefinitionException {
		boolean flag = false;
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "Checking if graph is well-defined...");
			}
		}
		for (final LabeledLazyWeightEdge e : this.g.getEdges()) {
			flag = checkWellDefinitionProperty1and3(this.g.getSource(e), this.g.getDest(e), e, false);
		}
		for (final LabeledNode node : this.g.getNodes()) {
			flag = flag && checkWellDefinitionProperty2(node, false);
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, ((flag) ? "done: all is well-defined.\n" : "done: something is wrong. Not "
						+ "well-defined graph!\n"));
			}
		}
		return flag;
	}

	/**
	 * Checks whether the constraint represented by an edge 'e' satisfies the well definition 1 property (WD1):<br>
	 * any labeled valued of the edge has a label that subsumes both labels of two endpoints.
	 * As a sanity check, this method checks also that for each literal there exists an observation time point.
	 * Since in 2017-04-07 it has been shown that WD3 is not more necessary,
	 * this method was augmented by the check of WD3 (edge label honesty) but with the change that, now, if an edge
	 * label is dishonest, it is fixed without throwing exceptions!
	 *
	 * @param nS the source node of the edge. It must be not null!
	 * @param nD the destination node of the edge. It must be not null!
	 * @param eSN edge representing a labeled constraint. It must be not null!
	 * @param hasToBeFixed true for fixing well-definition errors that can be fixed!
	 * @return false if the check fails, true otherwise
	 * @throws WellDefinitionException none
	 */
	boolean checkWellDefinitionProperty1and3(final LabeledNode nS, final LabeledNode nD, final LabeledLazyWeightEdge eSN, boolean hasToBeFixed)
			throws WellDefinitionException {

		final Label conjunctedLabel = nS.getLabel().conjunction(nD.getLabel());
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "Source label: " + nS.getLabel() + "; dest label: " + nD.getLabel() + " conjuncted label: " + conjunctedLabel);
			}
		}
		if (conjunctedLabel == null) {
			final String msg = "Two endpoints do not allow any constraint because they have inconsistent labels."
					+ "\nHead node: " + nD
					+ "\nTail node: " + nS
					+ "\nConnecting edge: " + eSN;
			if (Debug.ON) {
				if (LOG.isLoggable(Level.WARNING)) {
					LOG.log(Level.WARNING, msg);
				}
			}
			throw new WellDefinitionException(msg, WellDefinitionException.Type.LabelInconsistent);
		}
		// check the ordinary labeled values
		for (final Entry<Label, LazyWeight> entry : eSN.getLabeledValueMap().entrySet()) {
			Label currentLabel = entry.getKey();
			if (!currentLabel.isConsistentWith(conjunctedLabel)) {
				String msg = "Found a labeled value in " + eSN + " that does not subsume the conjunction of node labels, "
						+ conjunctedLabel + ".";
				if (hasToBeFixed) {
					eSN.removeLabeledValue(currentLabel);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.WARNING)) {
							LOG.log(Level.WARNING, msg + " Labeled value '" + currentLabel + "' removed.");
						}
					}
					continue;
				}
				throw new WellDefinitionException(msg, WellDefinitionException.Type.LabelInconsistent);
			}
			if (!currentLabel.subsumes(conjunctedLabel)) {
				final String msg = "Labeled value " + pairAsString(currentLabel, entry.getValue()) + " of edge " + eSN.getName()
						+ " does not subsume the endpoint labels '" + conjunctedLabel + "'.";
				if (Debug.ON) {
					if (LOG.isLoggable(Level.WARNING)) {
						LOG.log(Level.WARNING, msg);
					}
				}
				if (hasToBeFixed) {
					LazyWeight v = entry.getValue();
					eSN.removeLabeledValue(currentLabel);
					currentLabel = currentLabel.conjunction(conjunctedLabel);
					eSN.putLabeledValue(currentLabel, v);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.WARNING)) {
							LOG.log(Level.WARNING, "Fixed as required!");
						}
					}
				} else {
					throw new WellDefinitionException(msg, WellDefinitionException.Type.LabelNotSubsumes);
				}
			}
			// Checks if label subsumes all observer-t.p. labels of observer t.p. whose proposition is present into the label.
			// WD3 property.
			Label currentLabelModified = currentLabel;
			for (final char l : currentLabel.getPropositions()) {
				LabeledNode obs = this.g.getObserver(l);
				if (obs == null) {
					final String msg = "Observation node of literal " + l + " of label " + currentLabel + " in edge " + eSN + " does not exist.";
					if (Debug.ON) {
						if (LOG.isLoggable(Level.SEVERE)) {
							LOG.log(Level.SEVERE, msg);
						}
					}
					throw new WellDefinitionException(msg, WellDefinitionException.Type.ObservationNodeDoesNotExist);
				}
				// Checks WD3 and adjusts
				final Label obsLabel = obs.getLabel();
				if (!currentLabel.subsumes(obsLabel)) {
					final String msg = "Label " + currentLabel + " of edge " + eSN + " does not subsume label of obs node " + obs + ". It has been fixed.";
					if (Debug.ON) {
						if (LOG.isLoggable(Level.WARNING)) {
							LOG.log(Level.WARNING, msg);
						}
					}
					currentLabelModified = currentLabelModified.conjunction(obsLabel);
					if (currentLabelModified == null) {
						if (Debug.ON) {
							if (LOG.isLoggable(Level.SEVERE)) {
								LOG.log(Level.SEVERE, "Label " + currentLabel + " of edge " + eSN + " does not subsume label of obs node " + obs
										+ " and cannot be expanded because it becomes inconsistent.");
							}
						}
						throw new WellDefinitionException(msg, WellDefinitionException.Type.LabelInconsistent);
					}
				}
			}
			if (!currentLabelModified.equals(currentLabel)) {
				LazyWeight v = entry.getValue();
				eSN.removeLabeledValue(currentLabel);
				eSN.putLabeledValue(currentLabelModified, v);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.WARNING)) {
						LOG.log(Level.WARNING, "Labeled value " + pairAsString(currentLabelModified, v) + " replace dishonest labeled value "
								+ pairAsString(currentLabelModified, v) + " in edge " + eSN + ".");
					}
				}
			}
		}
		return true;
	}

	/**
	 * Checks whether the label of a node satisfies the second well definition property:<br>
	 * <blockquote>For each literal present in a node label label:
	 * <ol>
	 * <li><s>the label of the observation node of the considered literal is subsumed by the label of the current node.</s>
	 * <li>the observation node is constrained to occur before the current node.
	 * </ol>
	 * </blockquote>
	 * [2017-04-07] Posenato
	 * It has been proved that this property is not necessary for DC checking.
	 * I maintain it just to add an order among nodes and obs ones for speeding up the algorithm.
	 *
	 * @param node the current node to check. It must be not null!
	 * @param hasToBeFixed true to add the required precedences.
	 * @return false if the check fails, true otherwise
	 * @throws WellDefinitionException none
	 */
	boolean checkWellDefinitionProperty2(final LabeledNode node, boolean hasToBeFixed) throws WellDefinitionException {
		final Label nodeLabel = node.getLabel();
		if (nodeLabel.isEmpty())
			return true;

		LazyWeight v;
		String msg;
		LabeledNode obs;
		// Checks whether the node label is well defined w.r.t. each involved observation node label.
		for (final char l : nodeLabel.getPropositions()) {
			obs = this.g.getObserver(l);
			if (obs == null) {
				msg = "Observation node of literal " + l + " of node " + node + " does not exist.";
				if (Debug.ON) {
					if (LOG.isLoggable(Level.SEVERE)) {
						LOG.log(Level.SEVERE, msg);
					}
				}
				throw new WellDefinitionException(msg, WellDefinitionException.Type.ObservationNodeDoesNotExist);
			}

			// No more necessary as required property, but the algorithm guarantees it as property.
			final Label obsLabel = obs.getLabel();
			Label newNodeLabel;
			if (!nodeLabel.subsumes(obsLabel)) {
				newNodeLabel = nodeLabel.conjunction(obsLabel);
				if (newNodeLabel == null) {
					msg = "Label of node " + node + " is not consistent with label of obs node " + obs
							+ " but it should be subsumed it! The network is not well-defined.";
					if (Debug.ON) {
						if (LOG.isLoggable(Level.SEVERE)) {
							LOG.log(Level.SEVERE, msg);
						}
					}
					throw new WellDefinitionException(msg, WellDefinitionException.Type.LabelNotSubsumes);
				}

				if (hasToBeFixed)
					node.setLabel(newNodeLabel);
				msg = "Label of node " + node + " does not subsume label of obs node " + obs + ((hasToBeFixed) ? ". It has been adjusted!" : ".");
				if (Debug.ON) {
					if (LOG.isLoggable(Level.WARNING)) {
						LOG.log(Level.WARNING, msg);
					}
				}
				// it is necessary to restart because the set of propositions has been changed!
				if (hasToBeFixed)
					checkWellDefinitionProperty2(node, hasToBeFixed);
			}
		}

		// LabelNode is ok with all involved observation node labels.
		// It is possible to check and assure that node is after such observation nodes.
		LazyWeight reactionTimeLW = LazyNumber.get(-this.reactionTime);
		for (final char l : nodeLabel.getPropositions()) {
			obs = this.g.getObserver(l);
			// if (hasToBeFixed) {
			LabeledLazyWeightEdge e = this.g.findEdge(node, obs);
			if ((e == null) || ((v = e.getValue(nodeLabel)) == null) || (v.getValue() > 0)) {// WD2.2 ICAPS paper
				// WD2.2 has been proved to be redundant. So, it can be removed. Here we maintain a light version of it.
				// Light version: a node with label having 'p' has to be just after P?, i.e., P?<---[0,p]---X_p.
				msg = "WD2.2 simplified: There is no constraint to execute obs node " + obs + " before node " + node;
				if (Debug.ON) {
					if (LOG.isLoggable(Level.WARNING)) {
						LOG.log(Level.WARNING, msg);
					}
				}
				if (hasToBeFixed) {
					if (e == null) {
						e = makeNewEdge(node.getName() + "_" + obs.getName(), ConstraintType.derived);
						this.g.addEdge(e, node, obs);
					}
					e.mergeLabeledValue(nodeLabel, reactionTimeLW);// this is not necessary, but it can speed up the DC checking.
					if (Debug.ON) {
						if (LOG.isLoggable(Level.WARNING)) {
							LOG.log(Level.WARNING, "Fixed adding " + pairAsString(nodeLabel, reactionTimeLW) + " to " + e);
						}
					}
					continue;
					// Since it is redundant, it cannot raise an exception!
					// throw new WellDefinitionException(msg, WellDefinitionException.Type.ObservationNodeDoesNotOccurBefore);
				}
			}
		}
		return true;
	}

	/**
	 * Checks the dynamic consistency of a CSTN instance within timeout seconds.
	 * During the execution of this method, the given graph is modified. <br>
	 * If the check is successful, all constraints to node Z in g are minimized; otherwise, g contains a negative cycle at least.
	 *
	 * @return the final status of the checking with some statistics.
	 * @throws it.univr.di.cstnu.algorithms.WellDefinitionException if any.
	 */
	public LazyCSTNCheckStatus dynamicConsistencyCheck() throws WellDefinitionException {
		try {
			initAndCheck();
		} catch (final IllegalArgumentException e) {
			throw new IllegalArgumentException("The CSTN graph has a problem, and it cannot be initialized: " + e.getMessage());
		}

		LabeledLazyWeightGraph initializedGraph = new LabeledLazyWeightGraph(this.g);

		int adjustement = 0;
		do {
			dynamicConsistencyCheckWOInit();
			if (this.checkStatus.timeout) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.INFO)) {
						LOG.log(Level.INFO, "Timeout occurred (" + this.timeOut + "). Gives up!");
					}
				}
				return this.checkStatus;
			}
			if (!this.checkStatus.consistency) {
				// adjust delta value
				if (Debug.ON) {
					if (LOG.isLoggable(Level.INFO)) {
						LOG.log(Level.INFO, "Inconsistency found. Try to adjust ∂ value.");
					}
				}
				adjustDeltaValue();
				adjustement++;
				if (this.checkStatus.finished) {
					if (Debug.ON) {
						if (LOG.isLoggable(Level.INFO)) {
							LOG.log(Level.INFO, "The negative loop does not depend on ∂. Finished after " + adjustement + " ∂-adjustments.");
						}
					}
					// if delta cannot adjust, return!
					return this.checkStatus;
				}
				if (Debug.ON) {
					if (LOG.isLoggable(Level.INFO)) {
						LOG.log(Level.INFO, "Adjustment #" + adjustement + ". A new value for ∂ is " + this.checkStatus.minimumDelta
								+ "\nNow a new initialized graph with this new value for ∂ will be prepared and checked.");
					}
				}

				this.g = initializeGraphWithDelta(initializedGraph, this.checkStatus.minimumDelta);
			}
		} while (!this.checkStatus.finished && !this.checkStatus.timeout);
		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO, "Total number of ∂-adjustments: " + adjustement);
			}
		}
		return this.checkStatus;
	}

	/**
	 * Sets the X value of all LazyPiece(s) to newDelta.
	 * All LazySum(s) and LazyMax(s) are removed.
	 *
	 * @param g1
	 * @param newDelta
	 * @return a copy of the input graph with all LazyPiece(s) initialized with newDelta value.
	 */
	private static LabeledLazyWeightGraph initializeGraphWithDelta(LabeledLazyWeightGraph g1, Fraction newDelta) {
		LabeledLazyWeightGraph newG = new LabeledLazyWeightGraph(g1);
		for (LabeledLazyWeightEdge e : newG.getEdges()) {
			for (Entry<Label, LazyWeight> entry : e.getLabeledValueSet()) {
				LazyWeight lw = entry.getValue();
				Label label = entry.getKey();
				if (lw.getType() == SubType.Number)
					continue;
				e.removeLabeledValue(label);
				if (lw.getType() == SubType.Piece) {
					lw.setX(newDelta);
					e.putLabeledValue(label, lw);
				}
			}
		}
		return newG;
	}

	private void adjustDeltaValue() {
		Fraction newDelta;
		double newValue;
		LazyWeight negativeLW = this.checkStatus.negativeLoop;
		if (negativeLW == null || negativeLW.getType() == SubType.Number) {
			this.checkStatus.finished = true;
			return;
		}
		// negative loop is a Piece or Max or a Sum
		if (negativeLW.getType() == SubType.Piece) {
			LazyPiece negativePiece = (LazyPiece) negativeLW;
			newDelta = new Fraction(-negativePiece.getC(), negativePiece.getM());
			if (this.checkStatus.minimumDelta.compareTo(newDelta) >= 0) {
				String log = "New ∂ value is smaller than the global one. Check the algorithm. New ∂: " + newDelta + ". Global minimum ∂: "
						+ this.checkStatus.minimumDelta;
				if (Debug.ON) {
					if (LOG.isLoggable(Level.INFO)) {
						LOG.log(Level.INFO, log);
					}
				}
				throw new IllegalStateException(log);
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Found a new value for ∂, " + newDelta + ", from the piece " + negativePiece + ". The previous value was "
							+ this.checkStatus.minimumDelta);
				}
			}
			this.checkStatus.minimumDelta = newDelta;
			this.checkStatus.finished = false;
			this.checkStatus.consistency = true;
			return;
		}
		if (negativeLW.getType() == SubType.Sum || negativeLW.getType() == SubType.Max) {
			Fraction maxDelta = new Fraction(this.horizon); // FIXME For now, we assume that we are **not** looking for a lower bound constraint having delta
															// like Z<--(-∂)--X
			negativeLW.setX(maxDelta);
			newValue = negativeLW.getValue();
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "At maximum ∂, " + maxDelta + ", the value of negative loop is " + newValue);
				}
			}
			if (newValue < 0) {
				String log = "Max ∂ value make the " + negativeLW.getType() + " piece negative. Give up New ∂: " + maxDelta + ". Global minimum ∂: "
						+ this.checkStatus.minimumDelta;
				if (Debug.ON) {
					if (LOG.isLoggable(Level.INFO)) {
						LOG.log(Level.INFO, log);
					}
				}
				this.checkStatus.finished = true;
				return;
			}
			if (newValue == 0) {
				this.checkStatus.minimumDelta = maxDelta;
				this.checkStatus.finished = false;
				this.checkStatus.consistency = true;
				return;
			}
			// newValue is positive, start a binary search of 0 value for negativeLW
			double newValue1;
			Fraction upperDelta = maxDelta;
			Fraction lowerDelta = this.checkStatus.minimumDelta;
			do {
				newDelta = upperDelta.add(lowerDelta).divide(2);// half point
				negativeLW.setX(newDelta);
				newValue1 = negativeLW.getValue();
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.log(Level.FINER, "At ∂ = " + newDelta + ", the value of negative loop is " + newValue1 + ". Previous value was: " + newValue);
					}
				}
				newValue = newValue1;
				if (newValue == 0) {
					this.checkStatus.minimumDelta = newDelta;
					this.checkStatus.finished = false;
					this.checkStatus.consistency = true;
					return;
				}
				if (newValue > 0) {
					upperDelta = newDelta;
				} else {
					lowerDelta = newDelta;
				}
			} while (upperDelta.subtract(lowerDelta).doubleValue() > 0.001);
			// newDelta wasn't found
			String log = "New ∂ value cannot be found. Binary search is not able to find a ∂ value making 0 the negative loop! Check the algorithm. New ∂: "
					+ newDelta + ". Global minimum ∂: " + this.checkStatus.minimumDelta + ". ∂ range: [" + lowerDelta + ",  " + upperDelta + "].";
			if (Debug.ON) {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.log(Level.INFO, log);
				}
			}
			throw new IllegalStateException(log);
		}
	}

	/**
	 * Checks the dynamic consistency of a CSTN instance without initialize the network.<br>
	 * This method can be used ONLY when it is guaranteed that the network is already initialize by method {@link #initAndCheck()}.
	 * In case of doubts, use {@link #dynamicConsistencyCheck()}.
	 *
	 * @return the final status of the checking with some statistics.
	 */
	LazyCSTNCheckStatus dynamicConsistencyCheckWOInit() {
		if (!this.checkStatus.initialized) {
			throw new IllegalStateException("Graph has not been initialized! Please, consider dynamicConsistencyCheck() method!");
		}
		EdgesToCheck edgesToCheck = new EdgesToCheck(this.g.getEdges());
		final int propositionN = this.g.getObserverCount();
		final int nodeN = this.g.getVertexCount();
		int m = (this.getMaxWeight() != 0) ? this.getMaxWeight() : 1;
		// From CSTNU TIME 2018: m |T|^2 3^|P|
		int maxCycles = m * nodeN * nodeN * (int) Math.pow(propositionN, 3);
		if (maxCycles < 0)
			maxCycles = Integer.MAX_VALUE;
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "The maximum number of possible cycles is " + maxCycles);
			}
		}

		int i;
		Instant startInstant = Instant.now();
		Instant timeoutInstant = startInstant.plusSeconds(this.timeOut);
		for (i = 1; (i <= maxCycles) && this.checkStatus.consistency && !this.checkStatus.finished; i++) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.log(Level.FINE, "*** Start Main Cycle " + i + "/" + maxCycles + " ***");
				}
			}

			// if (this.propagationOnlyToZ) {
			// oneStepDynamicConsistencyByEdgesLimitedToZ(edgesToCheck, timeoutInstant);
			// } else {
			oneStepDynamicConsistencyByEdges(edgesToCheck, timeoutInstant);// Don't use 'this.' because such method is overrided!
			// }

			if (!this.checkStatus.finished) {
				if (checkTimeOutAndAdjustStatus(timeoutInstant, this.checkStatus)) {
					if (Debug.ON) {
						String msg = "During the check # " + i + ", " + this.timeOut + " seconds timeout occured. ";
						if (LOG.isLoggable(Level.INFO)) {
							LOG.log(Level.INFO, msg);
						}
					}
					this.checkStatus.executionTimeNS = ChronoUnit.NANOS.between(startInstant, Instant.now());
					return this.checkStatus;
				}
				if (this.checkStatus.consistency) {
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINE)) {
							StringBuilder log = new StringBuilder("During the check # " + i + ", " + edgesToCheck.size()
									+ " edges have been added/modified. Check has to continue.\nDetails of only "
									+ "modified edges having values:\n");
							for (LabeledLazyWeightEdge e : edgesToCheck) {
								if (e.size() == 0)
									continue;
								log.append("Edge " + e + "\n");
							}
							LOG.log(Level.FINE, log.toString());
						}
					}
				}
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.log(Level.FINE, "*** End Main Cycle " + i + "/" + maxCycles + " ***\n\n");
				}
			}
		}
		Instant endInstant = Instant.now();
		this.checkStatus.executionTimeNS = Duration.between(startInstant, endInstant).toNanos();

		if (!this.checkStatus.consistency) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.log(Level.INFO, "After " + (i - 1) + " cycle, found an inconsistency.\nStatus: " + this.checkStatus);
					// if (LOG.isLoggable(Level.FINEST)) {
					// LOG.log(Level.FINEST, "Final inconsistent graph: " + this.g);
					// }
				}
			}
			return this.checkStatus;
		}

		if ((i > maxCycles) && !this.checkStatus.finished) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.WARNING)) {
					LOG.log(Level.WARNING, "The maximum number of cycle (+" + maxCycles + ") has been reached!\nStatus: " + this.checkStatus);
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.log(Level.FINEST, "Last determined graph: " + this.g);
					}
				}
			}
			this.checkStatus.timeout = true;
			return this.checkStatus;
		}

		// consistent && finished
		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO,
						"Stable state reached. Number of cycles: " + (i - 1) + " over the maximum allowed " + maxCycles + ".\nStatus: " + this.checkStatus);
			}
		}
		LabeledLazyWeightGraph optimizedGraph = new LabeledLazyWeightGraph(this.g.getName());
		optimizedGraph.copyCleaningRedundantLabels(this.g);
		this.g = optimizedGraph;
		return this.checkStatus;
	}

	/**
	 * <p>
	 * Getter for the field <code>checkStatus</code>.
	 * </p>
	 *
	 * @return the checkStatus
	 */
	public LazyCSTNCheckStatus getCheckStatus() {
		// CSTNU override this
		return this.checkStatus;
	}

	/**
	 * Determine the set of edges P?-->nX where P? is an observer node and nX is the given node.
	 *
	 * @param nX the given node.
	 * @return the set of edges P?-->nX, an empty set if nX is empty or there is no observer or there is no such edges.
	 */
	ObjectList<LabeledLazyWeightEdge> getEdgeFromObserversToNode(final LabeledNode nX) {

		if (nX == this.Z) {
			return this.g.getObserver2ZEdges();
		}
		final ObjectList<LabeledLazyWeightEdge> fromObs = new ObjectArrayList<>();

		Collection<LabeledNode> obsSet = this.g.getObservers();
		if (obsSet.size() == 0)
			return fromObs;

		LabeledLazyWeightEdge e;
		for (final LabeledNode n : obsSet) {
			if ((e = this.g.findEdge(n, nX)) != null) {
				fromObs.add(e);
			}
		}
		return fromObs;
	}

	/**
	 * <p>
	 * Getter for the field <code>g</code>.
	 * </p>
	 *
	 * @return the g
	 */
	public LabeledLazyWeightGraph getG() {
		return this.g;
	}

	/**
	 * <p>
	 * Getter for the field <code>reactionTime</code>.
	 * </p>
	 *
	 * @return the reactionTime
	 */
	public int getReactionTime() {
		return this.reactionTime;
	}

	/**
	 * <p>
	 * Getter for the field <code>maxWeight</code>.
	 * </p>
	 *
	 * @return the maxWeight
	 */
	public int getMaxWeight() {
		return this.maxWeight;
	}

	/**
	 * Help method to initialize and check the CSTN represented by graph g. The {@link #dynamicConsistencyCheck()} calls this method before
	 * to execute the check. If some constraints of the network does not observe well-definition properties AND they can be adjusted, then the method fixes them
	 * and logs such fixes in log system at WARNING level.
	 * Since the current DC checking algorithm is complete only if the CSTN instance contains an upper bound to the distance between Z (the first node) and
	 * each
	 * node,
	 * this procedure add such upper bound (= #nodes * max weight value) to each node.
	 *
	 * @return true if the graph is a well-formed
	 * @throws it.univr.di.cstnu.algorithms.WellDefinitionException if any.
	 */
	public boolean initAndCheck() throws WellDefinitionException {
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "Starting initial well definition check.");
			}
		}
		this.g.clearCache();

		// Checks the presence of Z node!
		// this.Z = this.g.getZ(); already done in setG()
		if (this.Z == null) {
			this.Z = this.g.getNode(this.zeroNodeName);
			if (this.Z == null) {
				// We add by authority!
				this.Z = new LabeledNode(this.zeroNodeName);
				this.Z.setX(5);
				this.Z.setY(5);
				this.g.addVertex(this.Z);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.WARNING))
						LOG.log(Level.WARNING, "No " + this.zeroNodeName + " node found: added!");
				}
			}
			this.g.setZ(this.Z);
		} else {
			if (!this.Z.getLabel().isEmpty()) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.WARNING))
						LOG.log(Level.WARNING, "In the graph, Z node has not empty label. Label removed!");
				}
				this.Z.setLabel(Label.emptyLabel);
			}
			this.zeroNodeName = this.Z.getName();
		}

		// Checks well definiteness of edges and determine maxWeight
		double minNegWeight = 0;
		for (final LabeledLazyWeightEdge e : this.g.getEdges()) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Initial Checking edge e: " + e);
				}
			}
			// Determines the absolute max weight value
			for (Entry<Label, LazyWeight> entry : e.getLabeledValueSet()) {
				double v = entry.getValue().getValue();
				if (v < minNegWeight)
					minNegWeight = v;
			}

			final LabeledNode s = this.g.getSource(e);
			final LabeledNode d = this.g.getDest(e);

			if (s == d) {
				// loop are not admissible
				this.g.removeEdge(e);
				continue;
			}
			// WD1 is checked and adjusted here
			try {
				checkWellDefinitionProperty1and3(s, d, e, true);
			} catch (final WellDefinitionException ex) {
				throw new IllegalArgumentException("Edge " + e + " has the following problem: " + ex.getMessage());
			}

			if (e.isEmpty()) {
				// The merge removed labels...
				this.g.removeEdge(e);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.WARNING)) {
						LOG.log(Level.WARNING, "Labels fixing on edge " + e + " removed all labels. Edge " + e + " has been removed.");
					}
				}
				continue;
			}

			// TODO if contingent links have to be considered.
			// if (e.isContingentEdge()) {
			// if (Debug.ON) {
			// if (LOG.isLoggable(Level.WARNING)) {
			// LOG.log(Level.WARNING,
			// "Found a contingent edge: " + e + ". The consistency check does not difference between ordinary and contingent edges.");
			// }
			// }
			// }
		}

		// manage maxWeight value
		this.maxWeight = (int) -minNegWeight;
		// Determine horizon value
		long product = ((long) this.maxWeight) * (this.g.getVertexCount() - 1);// Z doesn't count!
		if (product >= Constants.INT_POS_INFINITE) {
			throw new ArithmeticException("Horizon value is not representable by an integer.");
		}
		this.horizon = (int) product;
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE))
				LOG.log(Level.FINE, "The horizon value is " + String.format("%6d", product));
		}

		// Init two useful structures
		this.g.getPropositions();

		/*
		 * Checks well definiteness of nodes
		 */
		boolean thereIsASignificantNodeLabel = false;
		final Collection<LabeledNode> nodeSet = this.g.getVertices();
		LazyNumber zeroLW = LazyNumber.get(0);
		this.checkStatus.minimumDelta = new Fraction(this.maxWeight);
		LazyPiece horizonLW = new LazyPiece(this.checkStatus.minimumDelta, 1, 0, false);// TODO For now, we are looking for the minimal horizon
		for (final LabeledNode node : nodeSet) {

			// 1. Checks that observation node doesn't have the observed proposition in its label!
			final char obs = node.getPropositionObserved();
			Label label = node.getLabel();
			if (obs != Constants.UNKNOWN && label.contains(obs)) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.WARNING)) {
						LOG.log(Level.WARNING, "Literal '" + obs + "' cannot be part of the label '" + label + "' of the observation node '" + node.getName()
								+ "'. Removed!");
					}
				}
				label = label.remove(obs);
			}
			node.setLabel(label);

			// WD2 is checked and adjusted here
			try {
				checkWellDefinitionProperty2(node, true);
			} catch (final WellDefinitionException ex) {
				throw new WellDefinitionException("WellDefinition 2 problem found at node " + node + ": " + ex.getMessage());
			}

			// 3. Checks that each node has an edge to Z, and edge from Z with bound = horizon.
			if (node != this.Z) {
				// LOWER BOUND FROM Z
				LabeledLazyWeightEdge edge = this.g.findEdge(node, this.Z);
				if (edge == null) {
					edge = makeNewEdge(node.getName() + "_" + this.Z.getName(), ConstraintType.internal);
					this.g.addEdge(edge, node, this.Z);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.WARNING)) {
							LOG.log(Level.WARNING,
									"It is necessary to add a constraint to guarantee that '" + node.getName() + "' occurs after '" + this.zeroNodeName
											+ "'.");
						}
					}
				}
				Label nodeLabel;

				if (this.withNodeLabels) {
					nodeLabel = node.getLabel();
				} else {
					nodeLabel = Label.emptyLabel;
					node.setLabel(nodeLabel);
				}

				edge.mergeLabeledValue(nodeLabel, zeroLW);// in any case, all nodes must be after Z!

				// UPPER BOUND FROM Z
				edge = this.g.findEdge(this.Z, node);
				if (edge == null) {
					edge = makeNewEdge(this.Z.getName() + "_" + node.getName(), ConstraintType.internal);
					this.g.addEdge(edge, this.Z, node);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.WARNING)) {
							LOG.log(Level.WARNING,
									"It is necessary to add a constraint to guarantee that '" + node.getName() + "' occurs before the horizon (if it occurs).");
						}
					}
				}
				edge.mergeLabeledValue(nodeLabel, horizonLW);
			}

			if (this.withNodeLabels) {
				// maybe all node labels are empty... so this.withNodeLabels can be reset.
				thereIsASignificantNodeLabel |= !node.getLabel().isEmpty();
			}
		}

		// TODO if node labels have to be considered.
		// // if withNodeLabel has been set false in a derived class, such assignment has to be preserved.
		// if (this.withNodeLabels) {
		// // it can be reset...in that case, algorithm is faster.
		// this.withNodeLabels &= thereIsASignificantNodeLabel;
		// }

		this.checkStatus.reset();
		this.checkStatus.minimumDelta = new Fraction(this.maxWeight);
		this.checkStatus.initialized = true;

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "Initial well definition check done!");
			}
		}
		return true;
	}

	/**
	 * Applies the labeled propagation rule:<br>
	 * <b>Standard DC semantics is assumed.</b><br>
	 * <b>This method is also valid assuming Instantaneous Reaction semantics or epsilon-reaction time.</b><br>
	 * The rule implements 2018-11 qLP+, submitted to ICAPS19.
	 *
	 * <pre>
	 * if A ---(u,α)&xrarr; B ---(v,β)&xrarr; C, then A ---[(α★β)†, u+v]&xrarr; C if (u+v < 0 and u < 0) or (u+v < 0 and αβ in P*)
	 *
	 * α,β in Q*
	 * (α★β)† is the label without children of unknown.
	 *
	 * If A==C and u+v < 0, then
	 * - if (α★β)† does not contain ¿ literals, the network is not DC
	 * - if (α★β)† contains ¿ literals, the u+v becomes -∞
	 * </pre>
	 *
	 * Be careful, in order to propagate correctly possibly -∞ self-loop, it is necessary call this method also for triple like with nodes A == B or B==C!
	 *
	 * @param nA first node.
	 * @param nB second node.
	 * @param nC third node.
	 * @param eAB edge nA&xrarr;nB
	 * @param eBC edge nB&xrarr;nC
	 * @param eAC edge nA&xrarr;nC
	 * @return true if a reduction has been applied.
	 */
	boolean labeledPropagationqLP(final LabeledNode nA, final LabeledNode nB, final LabeledNode nC, final LabeledLazyWeightEdge eAB,
			final LabeledLazyWeightEdge eBC, LabeledLazyWeightEdge eAC) {
		// Visibility is package because there is Junit Class test that checks this method.

		boolean ruleApplied = false;
		Label nAnCLabel = null;
		if (this.withNodeLabels) {
			nAnCLabel = nA.getLabel().conjunction(nC.getLabel());
			if (nAnCLabel == null)
				return false;
		}

		for (final Entry<Label, LazyWeight> ABEntry : eAB.getLabeledValueSet()) {
			final Label labelAB = ABEntry.getKey();
			final LazyWeight u = ABEntry.getValue();
			for (final Entry<Label, LazyWeight> BCEntry : eBC.getLabeledValueSet()) {
				final LazyWeight v = BCEntry.getValue();
				final Label labelBC = BCEntry.getKey();
				boolean qLabel = false;
				Label newLabelAC = null;
				newLabelAC = labelAB.conjunctionExtended(labelBC);

				LazyWeight sum = LazyWeight.sum(u, v, newLabelAC);

				if (sum == null || sum.getValue() > 0) {
					// All the criterion have been put into LazyWeight.sum
					//
					// // It is not necessary to propagate positive values.
					// // Fewer propagations, less useless labeled values.
					// 2018-01-25: I verified that for some negative instances, avoiding the positive propagations can increase the execution time.
					// For positive instances, surely avoiding the positive propagations shorten the execution time.
					// 2018-11-28: Luke and I verified that it is useless to propagate -infty forward! So, u must be different from Constants.INT_NEG_INFINITE
					continue;
				}
				qLabel = newLabelAC.containsUnknown();
				if (this.propagationOnlyToZ || LPMainConditionForSkipping(u, v)) {
					if (qLabel) {
						continue;
					}
				} else {
					if (this.withNodeLabels) {
						if (qLabel) {
							removeChildrenOfUnknown(newLabelAC);
							qLabel = newLabelAC.containsUnknown();
						}
					}
				}
				if (this.withNodeLabels) {
					if (!newLabelAC.subsumes(nAnCLabel)) {
						if (Debug.ON)
							LOG.log(Level.FINEST,
									"New alphaBeta label " + newLabelAC + " does not subsume node labels " + nAnCLabel + ". New value cannot be added.");
						continue;
					}
				}

				LazyWeight oldValue = eAC.getValue(newLabelAC);

				if (nA == nC) {
					if (sum.getValue() >= 0) {
						continue;
					}
					// sum is negative!
					if (!qLabel) {
						eAC.mergeLabeledValue(newLabelAC, sum);
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								String log = "Label Propagation Rule applied to edge " + eAC.getName()
										+ ":\nsource: "
										+ nA.getName() + " ---" + pairAsString(labelAB, u) + "---> " + nB.getName() + " ---" + pairAsString(labelBC, v)
										+ "---> "
										+ nC.getName()
										+ "\nresult: "
										+ nA.getName() + " ---" + pairAsString(newLabelAC, sum) + "---> " + nC.getName()
										+ "; old value: " + oldValue;
								LOG.log(Level.FINER, log + "\n***\nFound a negative loop " + pairAsString(newLabelAC, sum) + " in the edge  " + eAC + "\n***");
							}
						}
						this.checkStatus.consistency = false;
						this.checkStatus.finished = true;
						this.checkStatus.negativeLoop = sum;
						this.checkStatus.labeledValuePropagationCalls++;
						return true;
					}
					sum = LazyNumber.LazyNegInfty;
				} else {
					// in the case of A != C, a value is stored only if it is more negative than the current one.
					if ((oldValue != null) && (sum.getValue() >= oldValue.getValue())) {
						continue;
					}
				}
				if (nC.isObserver() && newLabelAC.contains(Literal.valueOf(nC.getPropositionObserved(), Literal.UNKNOWN))) {
					// it is a constraint like A---(-4,p?)--->P?
					// Useless
					continue;
				}

				// here sum has to be added!
				// I have to prepare the log before the execution of the merge!
				String log = null;
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINE)) {
						log = "Label Propagation Rule applied to edge " + eAC.getName()
								+ ":\nsource: "
								+ nA.getName() + " ---" + pairAsString(labelAB, u) + "---> " + nB.getName() + " ---" + pairAsString(labelBC, v)
								+ "---> "
								+ nC.getName()
								+ "\nresult: "
								+ nA.getName() + " ---" + pairAsString(newLabelAC, sum) + "---> " + nC.getName()
								+ "; old value: " + oldValue;
					}
				}

				if (eAC.mergeLabeledValue(newLabelAC, sum)) {
					ruleApplied = true;
					this.checkStatus.labeledValuePropagationCalls++;
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							LOG.log(Level.FINER, log);
						}
					}
				}
			}
		}
		return ruleApplied;
	}

	/**
	 * Applies rule R0/qR0: label containing a proposition that can be decided only in the future is simplified removing such proposition.
	 * <b>Standard DC semantics is assumed.</b>
	 *
	 * <pre>
	 * R0:
	 * P? --[w, α p]--&gt; X
	 * changes in
	 * P? --[w, α']--&gt; X when w &le; 0
	 * where:
	 * p is the positive or the negative literal associated to proposition observed in P?,
	 * α is a label,
	 * α' is α without 'p', P? children, and any children of possible q-literals.
	 * </pre>
	 *
	 * It is assumed that P? != X.<br>
	 * Rule qR0 has X==Z.
	 *
	 * @param nObs the observation node
	 * @param nX the other node
	 * @param eObsX the edge connecting nObs? ---&gt; X
	 * @return true if the rule has been applied one time at least.
	 */
	boolean labelModificationR0qR0(final LabeledNode nObs, final LabeledNode nX, final LabeledLazyWeightEdge eObsX) {
		// Visibility is package because there is Junit Class test that checks this method.

		if (this.propagationOnlyToZ) {
			if (nX != this.Z)
				return false;
		}

		boolean ruleApplied = false, mergeStatus;

		final char p = nObs.getPropositionObserved();
		if (p == Constants.UNKNOWN) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.log(Level.FINEST, "Method labelModificationR0 called passing a non observation node as first parameter!");
				}
			}
			return false;
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.log(Level.FINEST, "Label Modification R0: start.");
			}
		}
		if (this.withNodeLabels) {
			if (nX.getLabel().contains(p)) {
				// // It is a strange case because only with IR it is possible to manage such case.
				// // In all other case is the premise of a negative loop.
				// // We let this possibility
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.log(Level.FINEST,
								"R0qR0: Proposition " + p + " is present in the X label '" + nX.getLabel()
										+ "'. Rule cannot be applied.");
					}
				}
				return false;
			}
		}
		final ObjectSet<Label> obsXLabelSet = eObsX.getLabeledValueMap().keySet();

		for (final Label alpha : obsXLabelSet) {
			if (alpha == null || !alpha.contains(p)) {// l can be nullified in a previous cycle.
				continue;
			}

			final LazyWeight w = eObsX.getValue(alpha);
			if (w == null || R0qR0MainConditionForSkipping(w)) {
				// the value has been removed in a previous merge! Verified that it is necessary on Nov, 26 2015
				continue;
			}

			final Label alphaPrime = makeAlphaPrime(nX, nObs, p, alpha);
			if (alphaPrime == null) {
				continue;
			}
			// Prepare the log message now with old values of the edge. If R0 modifies, then we can log it correctly.
			String logMessage = null;
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					logMessage = "R0 simplifies a label of edge " + eObsX.getName()
							+ ":\nsource: " + nObs.getName() + " ---" + pairAsString(alpha, w) + "---> " + nX.getName()
							+ "\nresult: " + nObs.getName() + " ---" + pairAsString(alphaPrime, w) + "---> " + nX.getName();
				}
			}

			this.checkStatus.r0calls++;
			mergeStatus = eObsX.mergeLabeledValue(alphaPrime, w);
			if (mergeStatus) {
				ruleApplied = true;
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.log(Level.FINER, logMessage);
					}
				}
			}
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.log(Level.FINEST, "Label Modification R0: end.");
			}
		}
		return ruleApplied;
	}

	/**
	 * <h1>Rule R3*</h1>
	 * <b>Standard DC semantics is assumed.</b><br>
	 * <b>This method is also valid assuming Instantaneous Reaction semantics.</b>
	 *
	 * <pre>
	 * if P? --[w, αβ]--&gt; nD &lt;--[v, βγp]-- nS  and w &le; 0
	 * then the constraint between Y and X is modified adding the following label:
	 * nD &lt;--[max{w,v}, αβγ']-- nS
	 * where:
	 * α, β and γ do not share any literals.
	 * α, β do not contain any literal of p.
	 * p cannot compare also in label of nodes nD and nS.
	 * γ' is obtained by removing children of p from γ.
	 * </pre>
	 *
	 * <h2>Rule qR3*</h2>
	 *
	 * <pre>
	 * if P? --[w, γ]--&gt; Z &lt;--[v, βθp']-- nS  and w &le; 0
	 * then the constraint between Y and X is modified adding the following label:
	 * Z &lt;--[max{w,v}, (γ★β)†]-- nS
	 * where:
	 * β, θ and γ are in Q*.
	 * p' is p or ¬p or ¿p
	 * γ does not contain p' and any of its children.
	 * β does not contain any children of p'.
	 * θ contains only children of p'.
	 * p cannot compare also in label of nodes nD and nS.
	 * γ' is obtained by removing children of p from γ.
	 * (γ★β)† is the extended conjunction without any children of unknown literals.
	 * </pre>
	 *
	 * It is assumed that nS!=nD!
	 *
	 * @param nS node
	 * @param nD node
	 * @param eSD LabeledLazyWeightEdge containing the constraint to modify
	 * @return true if a rule has been applied.
	 */
	// Visibility is package because there is Junit Class test that checks this method.
	// Visibility is package because there is Junit Class test that checks this method.
	// Visibility is package because there is Junit Class test that checks this method.
	// Visibility is package because there is Junit Class test that checks this method.
	boolean labelModificationR3qR3(final LabeledNode nS, final LabeledNode nD, final LabeledLazyWeightEdge eSD) {

		if (this.propagationOnlyToZ) {
			if (nD != this.Z)
				return false;
		}

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.log(Level.FINEST, "Label Modification R3: start.");
			}
		}
		boolean ruleApplied = false;

		ObjectList<LabeledLazyWeightEdge> Obs2nDEdges = this.getEdgeFromObserversToNode(nD);
		if (Obs2nDEdges.isEmpty())
			return false;

		final ObjectSet<Label> SDLabelSet = eSD.getLabeledValueMap().keySet();

		Label allLiteralsSD = Label.emptyLabel;
		for (Label l : SDLabelSet) {
			if (this.withUnknown) {
				allLiteralsSD = allLiteralsSD.conjunctionExtended(l);
			} else {
				allLiteralsSD = allLiteralsSD.conjunction(l);
				if (allLiteralsSD == null) {
					allLiteralsSD = Label.emptyLabel;
					break;
				}
			}
		}
		for (final LabeledLazyWeightEdge eObsD : Obs2nDEdges) {
			final LabeledNode nObs = this.g.getSource(eObsD);
			if (nObs == nS)
				continue;

			final char p = nObs.getPropositionObserved();

			if (!allLiteralsSD.contains(p)) {
				// no label in nS-->nD contain any literal of p.
				continue;
			}
			if (this.withNodeLabels) {
				if (nS.getLabel().contains(p) || nD.getLabel().contains(p)) {// WD1 must be preserved!
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.log(Level.FINEST, "R3: Proposition " + p + " is present in the nS label '" + nS.getLabel() + " or nD label " + nD.getLabel()
									+ ". WD1 must be preserved, so R3 cannot be applied.");
						}
					}
					continue;
				}
			}

			// all labels from current Obs
			for (final Entry<Label, LazyWeight> entryObsD : eObsD.getLabeledValueSet()) {
				final LazyWeight w = entryObsD.getValue();
				if (R3qR3MainConditionForSkipping(w, nD)) {
					continue;
				}

				final Label ObsDLabel = entryObsD.getKey();

				for (final Label SDLabel : SDLabelSet) {
					if (SDLabel == null || !SDLabel.contains(p)) {
						continue;
					}

					final LazyWeight v = eSD.getValue(SDLabel);
					if (v == null) {
						// the value has been removed in a previous merge! Verified that it is necessary on Nov, 26 2015
						continue;
					}

					Label newLabel = (nD != this.Z) ? makeAlphaBetaGammaPrime4R3(nS, nD, nObs, p, ObsDLabel, SDLabel)
							: makeBetaGammaDagger4qR3(nS, nObs, p, ObsDLabel, SDLabel);
					if (newLabel == null) {
						continue;
					}

					final LazyWeight max = LazyWeight.max(w, v, newLabel);
					if (max == null || (nS == nD && max.getValue() >= 0))
						continue;

					ruleApplied = eSD.mergeLabeledValue(newLabel, max);

					if (ruleApplied) {
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								LOG.log(Level.FINER, "R3 adds a labeled value to edge " + eSD.getName() + ":\n"
										+ "source: " + nObs.getName() + " ---" + pairAsString(ObsDLabel, w) + "---> " + nD.getName()
										+ " <---" + pairAsString(SDLabel, v) + "--- " + nS.getName()
										+ "\nresult: add " + nD.getName() + " <---" + pairAsString(newLabel, max) + "--- " + nS.getName());
							}
						}
						this.checkStatus.r3calls++;
						if (nS == nD && max.getValue() < 0 && !newLabel.containsUnknown()) {
							LOG.log(Level.FINER, "\n***\nFound a negative loop " + pairAsString(newLabel, max) + " in the edge  " + eSD + "\n***");
							this.checkStatus.consistency = false;
							this.checkStatus.finished = true;
							this.checkStatus.negativeLoop = max;
							this.checkStatus.labeledValuePropagationCalls++;
							return true;
						}
					}
				}
			}
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.log(Level.FINEST, "Label Modification R3: end.");
			}
		}
		return ruleApplied;
	}

	/**
	 * Simple method to determine the label αβγ' for rule R3*.<br>
	 * See Table 1 and Table 2 ICAPS 2016 paper.
	 * Rule R3* is:
	 *
	 * <pre>
	 * if P? --[αβ, w]--&gt; nD &lt;--[βγp, v]-- nS  and w &le; 0 (ε)
	 * then the constraint between Y and X is modified adding the following label:
	 * nD &lt;--[αβγ', max{w',v}]-- nS
	 * where:
	 * α, β and γ do not share any literals.
	 * α, β do not contain any literal of p.
	 * p cannot compare also in label of nodes nD and nS.
	 * γ' is obtained by removing children of p from γ.
	 * ε>0 is the reaction time.
	 * w' is w or w-ε according with the kind of semantics.
	 * </pre>
	 *
	 * @param nS none
	 * @param nD none
	 * @param nObs none
	 * @param observed the proposition observed by observer (since this value usually is already determined before calling this method, this parameter is just
	 *            for speeding up).
	 * @param labelFromObs label of the edge from observer
	 * @param labelToClean none
	 * @return alphaBetaGamma' if all conditions are satisfied. null otherwise.
	 */
	// Visibility is package because there is Junit Class test that checks this method.
	// Visibility is package because there is Junit Class test that checks this method.
	// Visibility is package because there is Junit Class test that checks this method.
	// Visibility is package because there is Junit Class test that checks this method.
	Label makeAlphaBetaGammaPrime4R3(final LabeledNode nS, final LabeledNode nD, final LabeledNode nObs, final char observed,
			final Label labelFromObs, Label labelToClean) {

		StringBuilder slog;
		if (Debug.ON) {
			slog = new StringBuilder();
			if (LOG.isLoggable(Level.FINEST))
				slog.append("labelEdgeFromObs = " + labelFromObs);
		}
		if (this.withNodeLabels) {
			if (labelFromObs.contains(observed) || nS.getLabel().contains(observed) || nD.getLabel().contains(observed)) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.log(Level.FINEST,
								slog + " αβγ' cannot be calculated because labelFromObs or lables of nodes contain the prop " + observed
										+ " that has to be removed.");
					}
				}
				return null;
			}
		}

		Label labelToCleanWOp = labelToClean.remove(observed);

		final Label alpha = labelFromObs.getSubLabelIn(labelToCleanWOp, false);
		if (alpha.containsUnknown()) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.log(Level.FINEST, slog + " α contains unknow: " + alpha);
				}
			}
			return null;
		}
		final Label beta = labelFromObs.getSubLabelIn(labelToCleanWOp, true);
		if (beta.containsUnknown()) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.log(Level.FINEST, slog + " β contains unknow " + beta);
				}
			}
			return null;
		}
		Label gamma = labelToCleanWOp.getSubLabelIn(labelFromObs, false);

		if (this.withNodeLabels) {
			gamma = gamma.remove(this.g.getChildrenOf(nObs));
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.log(Level.FINEST, slog + " γ: " + gamma + "\n.");
			}
		}
		Label alphaBetaGamma = alpha.conjunction(beta).conjunction(gamma);
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST))
				slog.append(", αβγ'=" + alphaBetaGamma);
		}

		if (this.withNodeLabels) {
			if (alphaBetaGamma == null)
				return null;
			if (!alphaBetaGamma.subsumes(nD.getLabel().conjunction(nS.getLabel()))) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.log(Level.FINEST, slog + " αβγ' does not subsume labels from nodes:" + nD.getLabel().conjunction(nS.getLabel()));
					}
				}
				return null;
			}
		}
		return alphaBetaGamma;
	}

	/**
	 * Simple method to determine the α' to use in rules R0 and in rule qR0.
	 * Check paper TIME15 and ICAPS 2016 about CSTN sound&amp;complete DC check.
	 * α' is obtained by α removing all children of the observed proposition.
	 * If X==Z, then it is necessary also to remove all children of unknown from α'.
	 *
	 * @param nX the destination node
	 * @param nObs observer node
	 * @param observed the proposition observed by observer (since this value usually is already determined before calling this method, this parameter is just
	 *            for speeding up.)
	 * @param labelFromObs label of the edge from observer
	 * @return α'
	 */
	// Visibility is package because there is Junit Class test that checks this method.
	// Visibility is package because there is Junit Class test that checks this method.
	// Visibility is package because there is Junit Class test that checks this method.
	// Visibility is package because there is Junit Class test that checks this method.
	Label makeAlphaPrime(final LabeledNode nX, final LabeledNode nObs, final char observed, final Label labelFromObs) {
		if (this.withNodeLabels) {
			if (!this.propagationOnlyToZ) {
				if (nX.getLabel().contains(observed))
					return null;
			}
		}
		Label alphaPrime = labelFromObs.remove(observed);
		if (this.withNodeLabels) {
			alphaPrime = alphaPrime.remove(this.g.getChildrenOf(nObs));
			if (nX == this.Z && alphaPrime.containsUnknown()) {
				removeChildrenOfUnknown(alphaPrime);
			}
			if (!alphaPrime.subsumes(nX.getLabel().conjunction(nObs.getLabel()))) {
				return null;
			}
		}
		return alphaPrime;
	}

	/**
	 * Simple method to determine the label (β*γ)† to use in rules qR3*.
	 * See Table 1 and Table 2 ICAPS 2016.
	 *
	 * <pre>
	 * if P? --[γ, w]--&gt; Z &lt;--[βp'θ, v]-- nS  and w &le; 0
	 * then the constraint between Y and X is modified adding the following label:
	 * Z &lt;--[(β*γ)†, max{w',v}]-- nS
	 * where:
	 * p' is any literal (¿p included) of p.
	 * γ does not contain p' or P? children.
	 * β cannot contain children of P?
	 * θ contains only children of P?.
	 * (β*γ)† is the q-label obtained by removing children of any q-literals that appear in β*γ
	 * ε>0 is the reaction time.
	 * w' is w or w-ε according with the kind of semantics.
	 * </pre>
	 *
	 * @param nS none
	 * @param nObs none
	 * @param observed the proposition observed by observer (since this value usually is already determined before calling this method, this parameter is just
	 *            for speeding up).
	 * @param labelFromObs label of the edge from observer
	 * @param labelToClean none
	 * @return αβγ'
	 */
	// Visibility is package because there is Junit Class test that checks this method.
	// Visibility is package because there is Junit Class test that checks this method.
	// Visibility is package because there is Junit Class test that checks this method.
	// Visibility is package because there is Junit Class test that checks this method.
	Label makeBetaGammaDagger4qR3(final LabeledNode nS, final LabeledNode nObs, final char observed, final Label labelFromObs, Label labelToClean) {
		if (this.withNodeLabels) {
			if (labelFromObs.contains(observed)) {
				return null;
			}
		}
		Label beta = labelToClean.remove(observed);
		if (this.withNodeLabels) {
			Label childrenOfP = this.g.getChildrenOf(nObs);
			if (childrenOfP != null && !childrenOfP.isEmpty()) {
				Label test = labelFromObs.remove(childrenOfP);
				if (!labelFromObs.equals(test)) {
					return null;// labelFromObs must not contain p or its children.
				}
				beta = beta.remove(childrenOfP);
			}
		}
		Label betaGamma = (this.withUnknown) ? labelFromObs.conjunctionExtended(beta) : labelFromObs.conjunction(beta);
		if (this.withNodeLabels) {
			// // remove all children of unknowns.
			removeChildrenOfUnknown(betaGamma);
			if (!betaGamma.subsumes(nS.getLabel())) {
				return null;
			}
		}
		return betaGamma;
	}

	/**
	 * Create an edge assuring that its name is unique in the graph 'g'.
	 *
	 * @param name the proposed name. If an edge with name already exists, then name is modified adding a suitable integer such that the name becomes unique
	 *            in 'g'.
	 * @param type the type of edge to create.
	 * @return an edge with a unique name.
	 */
	LabeledLazyWeightEdge makeNewEdge(final String name, final ConstraintType type) {
		int i = this.g.getEdgeCount();
		String name1 = name;
		while (this.g.getEdge(name1) != null) {
			name1 = name + "_" + i++;
		}
		final LabeledLazyWeightEdge e = new LabeledLazyWeightEdge(name1);
		e.setConstraintType(type);
		return e;
	}

	/**
	 * Simple method to manage command line parameters using args4j library.
	 *
	 * @param args none
	 * @return false if a parameter is missing, or it is wrong. True if every parameter is given in a right format.
	 */
	@SuppressWarnings("deprecation")
	boolean manageParameters(final String[] args) {
		final CmdLineParser parser = new CmdLineParser(this);
		try {
			// parse the arguments.
			parser.parseArgument(args);
			if (this.fInput == null) {
				System.out.print("Input a CSTN file name: ");
				try (Scanner sc = new Scanner(System.in)) {
					String name = sc.next();
					this.fInput = new File(name);
					if (!this.fInput.exists()) {
						this.fInput = new File("/Users/posenato/Dropbox/_CSTNU/CSTN/" + name);
					}
				}
			}
			if (!this.fInput.exists())
				throw new CmdLineException(parser, "Input file does not exist.");

			if (this.fOutput != null) {
				if (this.fOutput.isDirectory())
					throw new CmdLineException(parser, "Output file is a directory.");
				if (!this.fOutput.getName().endsWith(".cstn")) {
					this.fOutput.renameTo(new File(this.fOutput.getAbsolutePath() + ".cstn"));
				}
				if (this.fOutput.exists()) {
					this.fOutput.delete();
				}
				try {
					this.fOutput.createNewFile();
					this.output = new PrintStream(this.fOutput, StandardCharsets.UTF_8);
				} catch (final IOException e) {
					throw new CmdLineException(parser, "Output file cannot be created.");
				}
			} else {
				this.output = System.out;
			}
		} catch (final CmdLineException e) {
			// if there's a problem in the command line, you'll get this exception. this will report an error message.
			System.err.println(e.getMessage());
			System.err.println("java " + this.getClass().getName() + " [options...] arguments...");
			// print the list of available options
			parser.printUsage(System.err);
			System.err.println();

			// print option sample. This is useful some time
			System.err.println("Example: java -jar CSTNU-*.*.*-SNAPSHOT.jar " + this.getClass().getName() + " "
					+ parser.printExample(OptionHandlerFilter.REQUIRED)
					+ " file_name");
			return false;
		}
		return true;
	}

	/**
	 * Executes one step of the dynamic consistency check: for each edge in edgesToCheck, rules R0--R3 are applied on it and, then, label propagation rule is
	 * applied
	 * two times: one time having the edge as first edge, one time having the edge as second edge.
	 * All modified or new edges are returned in the set 'edgesToCheck'.
	 *
	 * @param edgesToCheck set of edges that have to be checked.
	 * @param timeoutInstant time instant limit allowed to the computation.
	 * @return the update status (it is for convenience. It is not necessary because return the same parameter status).
	 */
	LazyCSTNCheckStatus oneStepDynamicConsistencyByEdges(final EdgesToCheck edgesToCheck, Instant timeoutInstant) {

		LabeledNode A, B, C;
		LabeledLazyWeightEdge AC, CB, edgeCopy;

		this.checkStatus.cycles++;

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "\nStart application labeled propagation rule+R0+R3.");
			}
		}
		/**
		 * March, 06 2016 I try to apply the rules on all edges that have been modified in the previous cycle.
		 */
		EdgesToCheck newEdgesToCheck = new EdgesToCheck();
		int i = 1, n = edgesToCheck.size();
		for (LabeledLazyWeightEdge AB : edgesToCheck) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "\n***Considering edge " + (i++) + "/" + n + ": " + AB + "\n");
				}
			}
			A = this.g.getSource(AB);
			B = this.g.getDest(AB);
			// initAndCheck does not resolve completely a qStar.
			// It is necessary to check here the edge before to consider the second edge.
			// If the second edge is not present, in any case the current edge has been analyzed by R0 and R3 (qStar can be solved)!
			if (B == this.Z || !this.propagationOnlyToZ) {
				edgeCopy = new LabeledLazyWeightEdge(AB);
				if (A.isObserver()) {
					// R0 on the resulting new values
					labelModificationR0qR0(A, B, AB);
				}
				labelModificationR3qR3(A, B, AB);
				if (A.isObserver()) {// R3 can add new values that have to be minimized. Experimentally VERIFIED on June, 28 2015
					// R0 on the resulting new values
					labelModificationR0qR0(A, B, AB);
				}
				if (!AB.equalsAllLabeledValues(edgeCopy)) {
					newEdgesToCheck.add(AB, A, B, this.Z, this.g, this.propagationOnlyToZ);
				}
				if (!this.checkStatus.consistency) {// R3 can be applied to a node loop
					this.checkStatus.finished = true;
					return this.checkStatus;
				}
			}

			if (checkTimeOutAndAdjustStatus(timeoutInstant, this.checkStatus)) {
				return this.checkStatus;
			}

			/**
			 * Step 1/2: Make all propagation considering edge AB as first edge.<br>
			 * A-->B-->C
			 */
			for (LabeledLazyWeightEdge BC : this.g.getOutEdges(B)) {
				C = this.g.getDest(BC);
				// Attention! It is necessary to consider also self loop, e.g. A==B and B==C to propagate rightly -∞

				AC = this.g.findEdge(A, C);
				// I need to preserve the old edge to compare below
				if (AC != null) {
					edgeCopy = new LabeledLazyWeightEdge(AC);
				} else {
					AC = makeNewEdge(A.getName() + "_" + C.getName(), ConstraintType.derived);
					edgeCopy = null;
				}

				this.labeledPropagationqLP(A, B, C, AB, BC, AC);

				/**
				 * I need to clean values on AC
				 * March, 8 2016 By an experimental results, it seems that it is not necessary to clean values using R0 and R3.
				 * Without it, the final number of rule applications does not change!
				 */

				if (edgeCopy == null && !AC.isEmpty()) {
					// the new CB has to be added to the graph!
					this.g.addEdge(AC, A, C);
					newEdgesToCheck.add(AC, A, C, this.Z, this.g, this.propagationOnlyToZ);
				} else if (edgeCopy != null && !edgeCopy.equalsAllLabeledValues(AC)) {
					// CB was already present and it has been changed!
					newEdgesToCheck.add(AC, A, C, this.Z, this.g, this.propagationOnlyToZ);
				}

				if (!this.checkStatus.consistency) {
					this.checkStatus.finished = true;
					return this.checkStatus;
				}
			}

			if (checkTimeOutAndAdjustStatus(timeoutInstant, this.checkStatus)) {
				return this.checkStatus;
			}

			/**
			 * Step 2/2: Make all propagation considering edge AB as second edge.<br>
			 * C-->A-->B
			 */
			for (LabeledLazyWeightEdge CA : this.g.getInEdges(A)) {
				C = this.g.getSource(CA);
				// Attention! It is necessary to consider also self loop, e.g. A==B and B==C to propagate rightly -∞

				CB = this.g.findEdge(C, B);
				// I need to preserve the old edge to compare below
				if (CB != null) {
					edgeCopy = new LabeledLazyWeightEdge(CB);
				} else {
					CB = makeNewEdge(C.getName() + "_" + B.getName(), ConstraintType.derived);
					edgeCopy = null;
				}

				this.labeledPropagationqLP(C, A, B, CA, AB, CB);

				if (edgeCopy == null && !CB.isEmpty()) {
					// the new CB has to be added to the graph!
					this.g.addEdge(CB, C, B);
					newEdgesToCheck.add(CB, C, B, this.Z, this.g, this.propagationOnlyToZ);
				} else if (edgeCopy != null && !edgeCopy.equalsAllLabeledValues(CB)) {
					// CB was already present and it has been changed!
					newEdgesToCheck.add(CB, C, B, this.Z, this.g, this.propagationOnlyToZ);
				}

				if (!this.checkStatus.consistency) {
					this.checkStatus.finished = true;
					return this.checkStatus;
				}

				if (checkTimeOutAndAdjustStatus(timeoutInstant, this.checkStatus)) {
					return this.checkStatus;
				}
			}
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "End application labeled propagation rule+R0+R3.");
			}
		}
		edgesToCheck.clear();
		this.checkStatus.finished = newEdgesToCheck.size() == 0;
		if (!this.checkStatus.finished) {
			edgesToCheck.takeIn(newEdgesToCheck);
		}
		return this.checkStatus;
	}

	// /**
	// * Executes one step of the dynamic consistency check: for each edge B-->Z in edgesToCheck, rules R0--R3 are applied on it and, then, label propagation
	// rule
	// * is
	// * applied to A-->B-->Z for all A-->B
	// * All modified or new edges are returned in the set 'edgesToCheck'.
	// *
	// * @param edgesToCheck set of edges that have to be checked.
	// * @param timeoutInstant time instant limit allowed to the computation.
	// * @return the update status (it is for convenience. It is not necessary because return the same parameter status).
	// */
	// private CSTNCheckStatus oneStepDynamicConsistencyByEdgesLimitedToZ(final EdgesToCheck edgesToCheck, Instant timeoutInstant) {
	// // This version consider only pairs of edges going to Z, i.e., in the form A-->B-->Z,
	// // 2018-01-25: with this method, performances worsen.
	// LabeledNode B, A;
	// LabeledLazyWeightEdge AZ, edgeCopy;
	//
	// this.checkStatus.cycles++;
	//
	// if (Debug.ON) {
	// if (LOG.isLoggable(Level.FINE)) {
	// LOG.log(Level.FINE, "\nStart application labeled propagation rule+R0+R3.");
	// }
	// }
	// /**
	// * March, 06 2016 I try to apply the rules on all edges that have been modified in the previous cycle.
	// */
	// EdgesToCheck newEdgesToCheck = new EdgesToCheck();
	// int i = 1, n = edgesToCheck.size();
	// for (LabeledLazyWeightEdge BZ : edgesToCheck) {
	// if (this.g.getDest(BZ) != this.Z)
	// continue;
	// if (Debug.ON) {
	// if (LOG.isLoggable(Level.FINER)) {
	// LOG.log(Level.FINER, "\n***Considering edge " + (i++) + "/" + n + ": " + BZ + "\n");
	// }
	// }
	// B = this.g.getSource(BZ);
	// // initAndCheck does not resolve completely a qStar.
	// // It is necessary to check here the edge before to consider the second edge.
	// // If the second edge is not present, in any case the current edge has been analyzed by R0 and R3 (qStar can be solved)!
	// edgeCopy = new LabeledLazyWeightEdge(BZ);
	// if (B.isObserver()) {
	// // R0 on the resulting new values
	// labelModificationR0qR0(B, this.Z, BZ);
	// }
	// labelModificationR3qR3(B, this.Z, BZ);
	// if (B.isObserver()) {// R3 can add new values that have to be minimized. Experimentally VERIFIED on June, 28 2015
	// // R0 on the resulting new values
	// labelModificationR0qR0(B, this.Z, BZ);
	// }
	// if (!BZ.equalsAllLabeledValues(edgeCopy)) {
	// newEdgesToCheck.add(BZ, B, this.Z, this.Z, this.g, this.propagationOnlyToZ);
	// }
	//
	// if (checkTimeOutAndAdjustStatus(timeoutInstant, this.checkStatus)) {
	// return this.checkStatus;
	// }
	//
	// /**
	// * Make all propagation considering edge AB as first edge.<br>
	// * A-->B-->Z
	// */
	// for (LabeledLazyWeightEdge AB : this.g.getInEdges(B)) {
	// A = this.g.getSource(AB);
	// // Attention! It is necessary to consider also self loop, e.g. A==B and B==C to propagate rightly -∞
	//
	// AZ = this.g.findEdge(A, this.Z);
	// // I need to preserve the old edge to compare below
	// if (AZ != null) {
	// edgeCopy = new LabeledLazyWeightEdge(AZ);
	// } else {
	// AZ = makeNewEdge(A.getName() + "_" + this.Z.getName(), LabeledLazyWeightEdge.ConstraintType.derived);
	// edgeCopy = null;
	// }
	//
	// this.labeledPropagationqLP(A, B, this.Z, AB, BZ, AZ);
	//
	// if (edgeCopy == null && !AZ.isEmpty()) {
	// // the new CB has to be added to the graph!
	// this.g.addEdge(AZ, A, this.Z);
	// newEdgesToCheck.add(AZ, A, this.Z, this.Z, this.g, this.propagationOnlyToZ);
	// } else if (edgeCopy != null && !edgeCopy.equalsAllLabeledValues(AZ)) {
	// // CB was already present, and it has been changed!
	// newEdgesToCheck.add(AZ, A, this.Z, this.Z, this.g, this.propagationOnlyToZ);
	// }
	//
	// if (!this.checkStatus.consistency) {
	// this.checkStatus.finished = true;
	// return this.checkStatus;
	// }
	//
	// if (checkTimeOutAndAdjustStatus(timeoutInstant, this.checkStatus)) {
	// return this.checkStatus;
	// }
	// }
	// }
	// if (Debug.ON) {
	// if (LOG.isLoggable(Level.FINE)) {
	// LOG.log(Level.FINE, "End application labeled propagation rule+R0+R3.");
	// }
	// }
	// edgesToCheck.clear();
	// this.checkStatus.finished = newEdgesToCheck.size() == 0;
	// if (!this.checkStatus.finished) {
	// edgesToCheck.takeFrom(newEdgesToCheck);
	// }
	// return this.checkStatus;
	// }

	// /**
	// * Executes one step of the dynamic consistency check: for each possible triangle of the network, label propagation rule is applied and, on the resulting
	// * edge, all other rules R0--R3 are also applied.
	// *
	// * @return the update status (for convenience. It is not necessary because return the same parameter status).
	// * @throws WellDefinitionException if the nextGraph is not well-defined (does not observe all well definition
	// properties). If this exception occurs, then
	// * there is a problem in the rules coding.
	// */
	// public CSTNCheckStatus oneStepDynamicConsistencyByNode() throws WellDefinitionException {
	//
	// LabeledNode B, C;
	// LabeledLazyWeightEdge AC;// AB, BC
	// boolean createEdge = false;
	//
	// this.checkStatus.cycles++;
	//
	// if (Debug.ON) {
	// if (LOG.isLoggable(Level.FINER)) {
	// LOG.log(Level.FINER, "\nStart application labeled propagation rule+R0+R3.");
	// }
	// }
	// /**
	// * March, 03 2016 I try to apply the rules on all edges making a by-row-visit to the adjacency matrix.
	// */
	// for (LabeledNode A : this.g.getVertices()) {
	// if (Debug.ON) {
	// if (LOG.isLoggable(Level.FINER)) {
	// LOG.log(Level.FINER, "Considering edges outgoing from " + A);
	// }
	// }
	// for (LabeledLazyWeightEdge AB : this.g.getOutEdges(A)) {
	// B = this.g.getDest(AB);
	// // Attention! It is necessary to consider also self loop, e.g. A==B and B==C to propagate rightly -∞
	//
	// if (B == this.Z || !this.propagationOnlyToZ) {
	// // Since in some graphs it is possible that there is not BC, we apply R0 and R3 to AB
	// if (A.isObserver()) {
	// // R0 on the resulting new values
	// labelModificationR0qR0(A, B, AB);
	// }
	// this.labelModificationR3qR3(A, B, AB);
	// if (A.isObserver()) {// R3 can add new values that have to be minimized. Experimentally VERIFIED on June, 28 2015
	// // R0 on the resulting new values
	// labelModificationR0qR0(A, B, AB);
	// }
	// }
	// for (LabeledLazyWeightEdge BC : this.g.getOutEdges(B)) {
	// C = this.g.getDest(BC);
	// // Attention! It is necessary to consider also self loop, e.g. A==B and B==C to propagate rightly -∞
	//
	// if (C == this.Z || !this.propagationOnlyToZ) {
	// if (B.isObserver()) {
	// // R0 on the resulting new values
	// labelModificationR0qR0(B, C, BC);
	// }
	// this.labelModificationR3qR3(B, C, BC);
	// if (B.isObserver()) {// R3 can add new values that have to be minimized.
	// // R0 on the resulting new values
	// labelModificationR0qR0(B, C, BC);
	// }
	// }
	// // Now it is possible to propagate the labels with the standard rules
	// AC = this.g.findEdge(A, C);
	// // I need to preserve the old edge to compare below
	// createEdge = (AC == null);
	// if (createEdge) {
	// AC = makeNewEdge(A.getName() + "_" + C.getName(), LabeledLazyWeightEdge.ConstraintType.derived);
	// }
	//
	// this.labeledPropagationqLP(A, B, C, AB, BC, AC);
	//
	// @SuppressWarnings("null")
	// boolean empty = AC.isEmpty();
	// if (createEdge && !empty) {
	// // the new CB has to be added to the graph!
	// this.g.addEdge(AC, A, C);
	// } else {
	// if (empty)
	// continue;
	// }
	// if (!this.checkStatus.consistency) {
	// this.checkStatus.finished = true;
	// return this.checkStatus;
	// }
	//
	// if (C == this.Z || !this.propagationOnlyToZ) {
	// if (A.isObserver()) {
	// // R0 on the resulting new values
	// labelModificationR0qR0(A, C, AC);
	// }
	// // R3 on the resulting new values
	// this.labelModificationR3qR3(A, C, AC);
	//
	// if (A.isObserver()) {// R3 can add new values that have to be minimized. Experimentally VERIFIED on June, 28 2015
	// // R0 on the resulting new values
	// labelModificationR0qR0(A, C, AC);
	// }
	// }
	// }
	// }
	// }
	// if (Debug.ON) {
	// if (LOG.isLoggable(Level.FINER)) {
	// LOG.log(Level.FINER, "End application labeled propagation rule+R0+R3."
	// + "\nSituation after the labeled propagation rule+R0+R3.\n");
	// }
	// }
	// return this.checkStatus;
	// }

	/**
	 * Print version of this class in System.out.
	 */
	public void printVersion() {
		// I use a non-static method for having a general method that prints the right name for each derived class.
		System.out.println(this.getClass().getName() + " " + VERSIONandDATE + ".\nAcademic and non-commercial use only.\n"
				+ "Copyright © 2017-2019, Roberto Posenato");
	}

	/**
	 * Utility method to simply override
	 * {@link #labeledPropagationqLP(LabeledNode, LabeledNode, LabeledNode, LabeledLazyWeightEdge, LabeledLazyWeightEdge, LabeledLazyWeightEdge)}
	 * in derived class without rewriting all method.
	 * Many derived classes have only to change the main condition for testing if the rule has to be applied or not.
	 * Here WE ASSUME IR SEMANTICS!
	 *
	 * @param u none
	 * @param v none
	 * @return true if the rule has to not apply.
	 */
	@SuppressWarnings("static-method")
	boolean LPMainConditionForSkipping(final LazyWeight u, final LazyWeight v) {
		// Table 1 2016 ICAPS paper for standard DC extended with rules on page 6 of file noteAboutLP.tex
		// Moreover, Luke and I verified on 2018-11-22 that with u<=0, qLP+ can be applied.
		return u.getValue() >= 0;// IR SEMANTICS
		// return !u.isNegative();
	}

	/**
	 * Utility method to simply override {@link #labelModificationR0qR0(LabeledNode, LabeledNode, LabeledLazyWeightEdge)}
	 * in derived class without rewriting all method.
	 * Many derived classes have only to change the main condition for testing if the rule has to be applied or not.
	 * Overriding this method is sufficient for overriding {@link #labelModificationR3qR3(LabeledNode, LabeledNode, LabeledLazyWeightEdge)}.
	 *
	 * @param w none
	 * @return true if the rule has to not apply.
	 */
	@SuppressWarnings("static-method")
	boolean R0qR0MainConditionForSkipping(final LazyWeight w) {
		// Table 1 ICAPS paper for IR DC
		// w must be < 0 for applying the rule. If w==0, then it is necessary to apply the rule because standard DC.
		return w.getValue() >= 0;
	}

	/**
	 * Utility method to simply override {@link #labelModificationR3qR3(LabeledNode, LabeledNode, LabeledLazyWeightEdge)}
	 * in derived class without rewriting all method.
	 * Many derived classes have only to change the main condition for testing if the rule has to be applied or not.
	 * Overriding this method is sufficient for overriding {@link #labelModificationR3qR3(LabeledNode, LabeledNode, LabeledLazyWeightEdge)}.
	 *
	 * @param w none
	 * @param nD none
	 * @return true if the rule has to not apply
	 */
	boolean R3qR3MainConditionForSkipping(final LazyWeight w, final LabeledNode nD) {
		// Table 1 ICAPS paper for IR DC
		// (w == 0 && nD==Z), it means that P? is executed at 0. So, even if v==0 (it cannot be v>0),
		// the constraint does not imply an implicit constraint (stripping p). So, we don't touch the constraint.
		return (w.getValue() > 0) || (w.getValue() == 0 && nD == this.Z);
	}

	/**
	 * Utility method to simply override {@link #labelModificationR3qR3(LabeledNode, LabeledNode, LabeledLazyWeightEdge)}
	 * in derived class without rewriting all method.
	 * Some derived classes have only to change the value for the new constraint.
	 * Overriding this method is sufficient for overriding {@link #labelModificationR3qR3(LabeledNode, LabeledNode, LabeledLazyWeightEdge)}.
	 *
	 * @param w
	 * @param v
	 * @return true if the rule has to not apply
	 *         @SuppressWarnings("static-method")
	 *         int R3qR3NewValue(final int v, final int w) {
	 *         // Table 1 ICAPS2016 paper for standard and IR DC
	 *         return (v >= w) ? v : w;
	 *         }
	 */

	/**
	 * Modifies label removing all children of possibly present unknown literals in label.
	 *
	 * @param label none
	 * @return the label modified.
	 */
	Label removeChildrenOfUnknown(Label label) {
		Label l = label;
		for (final char unknownLit : label.getAllUnknown()) {
			l = l.remove(this.g.getChildrenOf(this.g.getObserver(unknownLit)));
		}
		return l;
	}

	/**
	 * Considers the given graph as the graph to check (graph will be modified).
	 * Clear all {@link #maxWeight}, {@link #horizon} and {@link #checkStatus}.
	 *
	 * @param g1 set internal graph to g. It cannot be null.
	 */
	public void setG(LabeledLazyWeightGraph g1) {
		// CSTNU overrides this.
		if (g1 == null)
			throw new IllegalArgumentException("Input graph is null!");
		reset();
		this.g = g1;
		this.Z = g1.getZ();// Don't remove this assignment!
	}

	/**
	 * Determines the minimal distance between all pair of vertexes modifying the given consistent graph.
	 * If the graph contains a negative cycle, it returns false and the graph contains the edges that
	 * have determined the negative cycle.
	 *
	 * @param g the graph
	 * @return true if the graph is consistent, false otherwise.
	 *         If the response is false, the edges do not represent the minimal distance between nodes.
	 */
	static public boolean getMinimalDistanceGraph(final LabeledLazyWeightGraph g) {
		final int n = g.getVertexCount();
		final LabeledNode[] node = g.getVerticesArray();
		LabeledNode iV, jV, kV;
		LabeledLazyWeightEdge ik, kj, ij;
		Label ijL;

		boolean consistent = true;
		for (int k = 0; k < n; k++) {
			kV = node[k];
			for (int i = 0; i < n; i++) {
				iV = node[i];
				for (int j = 0; j < n; j++) {
					if ((k == i) || (k == j)) {
						continue;
					}
					jV = node[j];
					Label nodeLabelConjunction = iV.getLabel().conjunction(jV.getLabel());
					if (nodeLabelConjunction == null)
						continue;

					ik = g.findEdge(iV, kV);
					kj = g.findEdge(kV, jV);
					if ((ik == null) || (kj == null)) {
						continue;
					}
					ij = g.findEdge(iV, jV);

					final ObjectSet<Object2ObjectMap.Entry<Label, LazyWeight>> ikMap = ik.getLabeledValueSet();
					final ObjectSet<Object2ObjectMap.Entry<Label, LazyWeight>> kjMap = kj.getLabeledValueSet();

					for (final Object2ObjectMap.Entry<Label, LazyWeight> ikL : ikMap) {
						for (final Object2ObjectMap.Entry<Label, LazyWeight> kjL : kjMap) {
							ijL = ikL.getKey().conjunction(kjL.getKey());
							if (ijL == null) {
								continue;
							}
							ijL = ijL.conjunction(nodeLabelConjunction);// It is necessary to propagate with node labels!
							if (ijL == null) {
								continue;
							}
							if (ij == null) {
								ij = new LabeledLazyWeightEdge("e" + node[i].getName() + node[j].getName());
								ij.setConstraintType(ConstraintType.derived);
								g.addEdge(ij, iV, jV);
							}
							LazyWeight sum = LazyWeight.sum(ikL.getValue(), kjL.getValue(), ijL);
							// v = ikL.getIntValue() + kjL.getIntValue();
							ij.mergeLabeledValue(ijL, sum);
							if (i == j) // check negative cycles
								if (sum.getValue() < 0 || ij.getMinValue() < 0) {
									LOG.finer("Found a negative cycle on node " + iV.getName() + ": " + (ij)
											+ "\nIn details, ik=" + ik + ", kj=" + kj + ",  sum=" + sum + ", ij.getValue(" + ijL + ")=" + ij.getValue(ijL));
									consistent = false;
								}
						}
					}
				}
			}
		}
		return consistent;
	}

	/**
	 * Resets all internal structures
	 */
	void reset() {
		this.g = null;
		this.Z = null;
		this.maxWeight = 0;
		this.horizon = 0;
		this.checkStatus.reset();
	}
}
