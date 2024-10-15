// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.algorithms;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.univr.di.Debug;
import it.univr.di.cstnu.graph.*;
import it.univr.di.cstnu.graph.Edge.ConstraintType;
import it.univr.di.labeledvalue.Constants;
import it.univr.di.labeledvalue.Label;
import it.univr.di.labeledvalue.Literal;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * <p>
 * Represents a Conditional Simple Temporal Network (CSTN) and it contains a method to check the dynamic consistency of
 * the instance.<br> Edge weights are signed integer.<br> This class is the base class for 9 classes for CSTNs: each
 * class is a specialization of this one where some assumptions about the system reaction type and/or the presence or
 * not of labels on nodes are done.<br> In more details, in this class the dynamic consistency (DC) check is done
 * assuming the standard DC semantics (a system reacts to the observations after a small but not quantified delay (cf.
 * ICAPS 2016 paper, table 1))<br> In the derived class {@link it.univr.di.cstnu.algorithms.CSTNIR}, for example, the
 * dynamic consistency check is done assuming that the system reacts instantaneously to the observations.
 * </p>
 * <p>
 * As regards the {@link #dynamicConsistencyCheck()}, there are 3 possible algorithms: HunsbergerPosenato2018,
 * HunsbergerPosenato2019, and HunsbergerPosenato2020. HunsbergerPosenato2018 is the algorithm presented at IJCAI 2018
 * conference and it is executed by {@link #dynamicConsistencyCheck()} when method
 * {@link #setPropagationOnlyToZ(boolean)} is executed before with true value.<br> HunsbergerPosenato2019 is the
 * algorithm presented at ICAPS 2019and it is executed by {@link #dynamicConsistencyCheck()} when method
 * {@link #setPropagationOnlyToZ(boolean)} is executed before with false value.<br> This last version is not efficient
 * as IJCAI18 one when instances are DC while is very efficient when instances are NOT DC.<br> For a very efficient
 * version for both DC and not DC instances, consider
 * {@link it.univr.di.cstnu.algorithms.CSTNPotential#dynamicConsistencyCheck()} that runs DC checking assuming IR
 * semantics and nodes without labels, algorithm presented at ICAPS 2020.
 * </p>
 *
 * @author Roberto Posenato
 * @version $Rev: 868 $
 */
public class CSTN extends AbstractCSTN<CSTNEdge> {
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
	// static final public String VERSIONandDATE = "Version 5.8 - January, 17 2019";// Code tweaking
	// static final public String VERSIONandDATE = "Version 6 - January, 31 2019";// Infinity value management moved to
	// nodes!
	// static final public String VERSIONandDATE = "Version 6.1 - June, 9 2019";// Refactoring Edge
	// static final public String VERSIONandDATE = "Version 6.2 - June, 12 2019";// Refactoring CSTN class
	// static final public String VERSIONandDATE = "Version 6.5 - November, 07 2019";// 9Rule version. SVN version 363
	//	static final public String VERSIONandDATE = "Version 7.0 - November, 07 2019";// This version restores IJCAI18
	//	and ICAPS19 DC checking algorithm. The DC checking alg. based on potential is only one, represented in the class
	//	CSTNPotential.
	static final public String VERSIONandDATE = "Version 7.1 - June, 16, 2022";//Removed a useless check

	static private final Logger LOG = Logger.getLogger(CSTN.class.getName());

	/**
	 * Initialize the CSTN using graph.<br> For saving the resulting graph in a file during/after a check, field
	 * {@link #fOutput} must be set. Setting {@link #fInput} instead of {@link #fOutput}, the name of output file is
	 * build using {@link #fInput}.
	 *
	 * @param graph       TNGraph to check
	 * @param giveTimeOut timeout for the check
	 */
	public CSTN(TNGraph<CSTNEdge> graph, int giveTimeOut) {
		this(graph);
		timeOut = giveTimeOut;
	}

	/**
	 * Initialize the CSTN using graph.<br> For saving the resulting graph in a file during/after a check, field
	 * {@link #fOutput} must be set. Setting {@link #fInput} instead of {@link #fOutput}, the name of output file is
	 * build using {@link #fInput}.
	 *
	 * @param graph TNGraph to check
	 */
	public CSTN(TNGraph<CSTNEdge> graph) {
		this();
		setG(graph);// sets also checkStatus!
	}

	/**
	 * Default constructor.
	 */
	CSTN() {
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
	public static void main(final String[] args) throws IOException, ParserConfigurationException, SAXException {
		defaultMain(args, new CSTN(), "Standard DC");
	}

	/**
	 * @param args           node
	 * @param cstn           cstn
	 * @param kindOfChecking type name of the network for showing the type of checking
	 *
	 * @throws SAXException                 none
	 * @throws ParserConfigurationException none
	 * @throws IOException                  none
	 */
	static void defaultMain(final String[] args, final CSTN cstn, String kindOfChecking)
		throws IOException, ParserConfigurationException, SAXException {
		System.out.println(cstn.getVersionAndCopyright());
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Start...");
			}
		}
		if (!cstn.manageParameters(args)) {
			return;
		}
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
		final TNGraphMLReader<CSTNEdge> graphMLReader = new TNGraphMLReader<>();
		cstn.setG(graphMLReader.readGraph(cstn.fInput, EdgeSupplier.DEFAULT_CSTN_EDGE_CLASS));

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("TNGraph<E> loaded!\n" + kindOfChecking + " Checking...");
			}
		}
		final CSTNCheckStatus status;
		try {
			status = cstn.dynamicConsistencyCheck();
		} catch (final WellDefinitionException e) {
			System.out.print("An error has been occurred during the checking: " + e.getMessage());
			return;
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
	}

	/**
	 *
	 */
	@Override
	public CSTNCheckStatus dynamicConsistencyCheck() throws WellDefinitionException {
		try {
			initAndCheck();
		} catch (final IllegalArgumentException e) {
			throw new IllegalArgumentException("The CSTN graph has a problem: " + e.getMessage());
		}
		return dynamicConsistencyCheckWOInit();
	}

	/**
	 * Executes one step of the dynamic consistency check.<br> For each possible triangle of the network, label
	 * propagation rule is applied and, on the resulting edge, all other rules, R0, R3 and potential ones, are also
	 * applied.<br>
	 * <em>This method is offered for studying the propagation node by node. It is not efficient!<br>
	 * {@link #dynamicConsistencyCheck()} uses a different propagation technique!</em>
	 *
	 * @return the update status (for convenience. The status is also stored in {@link #checkStatus}).
	 */
	public CSTNCheckStatus oneStepDynamicConsistencyByNode() {
		LabeledNode B, C;
		CSTNEdge AC;// AB, BC
		boolean createEdge;

		checkStatus.cycles++;

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "\nStart application labeled propagation rule+R0+R3.");
			}
		}
		/*
		  March, 03 2016 I try to apply the rules on all edges making a by-row-visit to the adjacency matrix.
		 */
		final LabeledNode Z = g.getZ();
		for (final LabeledNode A : g.getVertices()) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Considering node " + A + ". Considering edges outgoing.");
				}
			}
			for (final CSTNEdge AB : g.getOutEdges(A)) {
				B = g.getDest(AB);
				assert B != null;
				// Attention! It is necessary to consider also self loop, e.g. A==B and B==C to propagate rightly -∞

				if (B == Z || !propagationOnlyToZ) {
					// Since in some graphs it is possible that there is not BC, we apply R0 and R3 to AB
					if (A.isObserver()) {
						// R0 on the resulting new values
						labelModificationR0qR0(A, B, AB);
					}
					labelModificationR3qR3(A, B, AB);
					if (A.isObserver()) {// R3 can add new values that have to be minimized. Experimentally VERIFIED on June, 28 2015
						// R0 on the resulting new values
						labelModificationR0qR0(A, B, AB);
					}
				}
				for (final CSTNEdge BC : g.getOutEdges(B)) {
					C = g.getDest(BC);
					assert C != null;
					// Attention! It is necessary to consider also self loop, e.g. A==B and B==C to possibly create -∞

					if (!propagationOnlyToZ || C == Z) {
						if (B.isObserver()) {
							// R0 on the resulting new values
							labelModificationR0qR0(B, C, BC);
						}
						labelModificationR3qR3(B, C, BC);
						if (B.isObserver()) {// R3 can add new values that have to be minimized.
							// R0 on the resulting new values
							labelModificationR0qR0(B, C, BC);
						}
					}
					// Now it is possible to propagate the labels with the standard rules
					AC = g.findEdge(A, C);
					// I need to preserve the old edge to compare below
					createEdge = (AC == null);
					if (createEdge) {
						AC = makeNewEdge(A.getName() + "_" + C.getName(), ConstraintType.derived);
					}

					labelPropagation(A, B, C, AB, BC, AC);

					@SuppressWarnings("null") final boolean empty = AC.isEmpty();
					if (createEdge && !empty) {
						// the new CB has to be added to the graph!
						g.addEdge(AC, A, C);
					} else {
						if (empty) {
							continue;
						}
					}
					if (!checkStatus.consistency) {
						checkStatus.finished = true;
						return checkStatus;
					}

					if (!propagationOnlyToZ || C == Z) {
						if (A.isObserver()) {
							// R0 on the resulting new values
							labelModificationR0qR0(A, C, AC);
						}
						// R3 on the resulting new values
						labelModificationR3qR3(A, C, AC);

						if (A.isObserver()) {// R3 can add new values that have to be minimized. Experimentally VERIFIED on June, 28 2015
							// R0 on the resulting new values
							labelModificationR0qR0(A, C, AC);
						}
					}
				}
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Finished label propagation considering edges outgoing from " + A);
				}
			}
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, """
				                     End application labeled propagation rule+R0+R3.
				                     Situation after the labeled propagation rule+R0+R3.
				                     """);
			}
		}
		return checkStatus;
	}

	/**
	 * <b>Rule R3*</b><br>
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
	 * <b>Rule qR3*</b><br>
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
	 * @param nS  node must be different from nD
	 * @param nD  node must be different from nS
	 * @param eSD E containing the constraint to modify
	 *
	 * @return true if a rule has been applied.
	 */
	// Visibility is package because there is Junit Class test that checks this method.
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "DLS_DEAD_LOCAL_STORE",
		justification = "firstLog variable is used for debugging.")
	boolean labelModificationR3qR3(
		final LabeledNode nS, final LabeledNode nD, final CSTNEdge eSD) {
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.log(Level.FINEST, "Label Modification R3: start.");
			}
		}
		boolean ruleApplied = false;
		final boolean nSisObs = nS.isObserver();

		final ObjectList<CSTNEdge> Obs2nDEdges = getEdgeFromObserversToNode(nD);
		if (Obs2nDEdges.isEmpty()) {
			return false;
		}

		final ObjectSet<Label> SDLabelSet = eSD.getLabeledValueMap().keySet();

		/*
		 * allLiteralsSD is a label that contains all propositions (each represented with any of its literals)
		 * that compares in labels of labeled values in the edge nS-->nD.
		 */
		Label allLiteralsSD = Label.emptyLabel;
		for (final Label l : SDLabelSet) {
			allLiteralsSD = allLiteralsSD.conjunctionExtended(l);
		}
		final LabeledNode Z = g.getZ();
		for (final CSTNEdge eObsD : Obs2nDEdges) {
			final LabeledNode nObs = g.getSource(eObsD);
			if (nObs == nS) {
				continue;
			}

			assert nObs != null;
			final char p = nObs.getPropositionObserved();

			if (!allLiteralsSD.contains(p)) {
				// no label in nS-->nD contain any literal of p.
				continue;
			}
			if (withNodeLabels) {
				if (nS.getLabel().contains(p) || nD.getLabel().contains(p)) {// WD1 must be preserved!
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.log(Level.FINEST,
							        "R3: Proposition " + p + " is present in the nS label '" + nS.getLabel()
							        + " or nD label " + nD.getLabel()
							        + ". WD1 must be preserved, so R3 cannot be applied.");
						}
					}
					continue;
				}
			}

			String firstLog = "R3 considers edge " + eSD.getName() + " and observation t.p. " + nObs.getName();
			// all labels from current Obs
			for (final Object2IntMap.Entry<Label> entryObsD : eObsD.getLabeledValueSet()) {
				final int w = entryObsD.getIntValue();
				if (mainConditionForSkippingInR3qR3(w, nD)) {
					continue;
				}

				final Label ObsDLabel = entryObsD.getKey();

				// all labels from nS-->nD
				for (final Label SDLabel : SDLabelSet) {
					if (SDLabel == null || !SDLabel.contains(p)) {
						continue;
					}

					final int v = eSD.getValue(SDLabel);
					if (v == Constants.INT_NULL) {
						// the value has been removed in a previous merge! Verified that it is necessary on Nov, 26 2015
						continue;
					}

					Label newLabel = (nD != Z) ? makeAlphaBetaGammaPrime4R3(nS, nD, nObs, p, ObsDLabel, SDLabel)
					                           : makeBetaGammaDagger4qR3(nS, nObs, p, ObsDLabel, SDLabel);
					if (newLabel == null) {
						continue;
					}

					final int max = newValueInR3qR3(v, w);

					String log = "";
					if (Debug.ON) {
						log = firstLog + "\nsource: " + nObs.getName() + " ---" + pairAsString(ObsDLabel, w) + "⟶ "
						      + nD.getName() + " ⟵" + pairAsString(SDLabel, v) + "---" + nS.getName();
					}

					// If nS is an obs, apply R0 to the new label.
					if (nSisObs) {
						newLabel = labelModificationR0qR0Core(nS, nD, newLabel, max);
					}
					ruleApplied = eSD.mergeLabeledValue(newLabel, max);

					if (ruleApplied) {
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								LOG.log(Level.FINER,
								        log + "\nresult: add " + nD.getName() + " ⟵" + pairAsString(newLabel, max)
								        + "--- " + nS.getName() + "\n");
								firstLog = "";
							}
						}
						checkStatus.r3calls++;
					}
				} // all labeled value in nS-->nD has been checked.
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
	 * Applies R0 and R3 to the edge AB.
	 *
	 * @param AB edge
	 * @param A  source
	 * @param B  destination
	 *
	 * @return true if the rules were applied, false otherwise.
	 */
	boolean applyR0R3(CSTNEdge AB, LabeledNode A, LabeledNode B) {
		final CSTNEdge edgeCopy = g.getEdgeFactory().get(AB);
		if (A.isObserver()) {
			labelModificationR0qR0(A, B, AB);
		}
		labelModificationR3qR3(A, B, AB);
		return !AB.hasSameValues(edgeCopy);
	}

	/**
	 * Checks the dynamic consistency of a CSTN instance without initialize the network.<br> This method can be used ONLY when it is guaranteed that the network
	 * is already initialize by method {@link #initAndCheck()}.
	 * <br>
	 * See {@link #dynamicConsistencyCheck()} for a complete description.
	 *
	 * @return the final status of the checking with some statistics.
	 *
	 * @see CSTN#dynamicConsistencyCheck
	 */
	CSTNCheckStatus dynamicConsistencyCheckWOInit() {
		if (!checkStatus.initialized) {
			throw new IllegalStateException(
				"TNGraph<E> has not been initialized! Please, consider dynamicConsistencyCheck() method!");
		}
		final EdgesToCheck<CSTNEdge> edgesToCheck = new EdgesToCheck<>(g.getEdges());
		final int propositionN = g.getObserverCount();
		final int nodeN = g.getVertexCount();
		final int m = (getMaxWeight() != 0) ? getMaxWeight() : 1;
		int maxCycles = m * nodeN * nodeN * (int) Math.pow(propositionN, 3);
		if (maxCycles < 0) {
			maxCycles = Integer.MAX_VALUE;
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "The maximum number of possible cycles is " + maxCycles);
			}
		}

		int i;
		final Instant startInstant = Instant.now();
		final Instant timeoutInstant = startInstant.plusSeconds(timeOut);
		for (i = 1; (i <= maxCycles) && checkStatus.consistency && !checkStatus.finished; i++) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.log(Level.FINE, "*** Start Main Cycle " + i + "/" + maxCycles + " ***");
				}
			}

			if (propagationOnlyToZ) {
				oneStepDynamicConsistencyByEdgesLimitedToZ(edgesToCheck, timeoutInstant);
			} else {
				oneStepDynamicConsistencyByEdges(edgesToCheck, timeoutInstant);// Don't use 'this.' because such
				// method is overridden!
			}

			if (!checkStatus.finished) {
				if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
					if (Debug.ON) {
						final String msg = "During the check # " + i + ", " + timeOut + " seconds timeout occurred. ";
						if (LOG.isLoggable(Level.INFO)) {
							LOG.log(Level.INFO, msg);
						}
					}
					checkStatus.executionTimeNS = Duration.between(startInstant, Instant.now()).toNanos();
					saveGraphToFile();
					return checkStatus;
				}
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINE)) {
						final StringBuilder log =
							new StringBuilder("During the check # " + i + ", " + edgesToCheck.size()
							                  +
							                  " edges have been added/modified. Check has to continue.\nDetails of only modified edges having values:\n");
						for (final CSTNEdge e : edgesToCheck) {
							if (e.isEmpty()) {
								continue;
							}
							log.append("Edge ").append(e).append("\n");
						}
						LOG.log(Level.FINE, log.toString());
					}
				}
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.log(Level.FINE, "*** End Main Cycle " + i + "/" + maxCycles + " ***\n\n");
				}
			}
		}
		final Instant endInstant = Instant.now();
		checkStatus.executionTimeNS = Duration.between(startInstant, endInstant).toNanos();

		if (!checkStatus.consistency) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.log(Level.INFO,
					        "After " + (i - 1) + " cycle, found an inconsistency.\nStatus: " + checkStatus);
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.log(Level.FINEST, "Final inconsistent graph: " + g);
					}
				}
			}
			saveGraphToFile();
			return checkStatus;
		}

		if ((i > maxCycles) && !checkStatus.finished) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.WARNING)) {
					LOG.log(Level.WARNING,
					        "The maximum number of cycle (+" + maxCycles + ") has been reached!\nStatus: "
					        + checkStatus);
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.log(Level.FINEST, "Last determined graph: " + g);
					}
				}
			}
			checkStatus.consistency = checkStatus.finished;
			saveGraphToFile();
			return checkStatus;
		}

		// consistent && finished
		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO,
				        "Stable state reached. Number of cycles: " + (i - 1) + " over the maximum allowed " + maxCycles
				        + ".\nStatus: " + checkStatus);
			}
		}
		if (cleanCheckedInstance) {
			gCheckedCleaned = new TNGraph<>(g.getName(), g.getEdgeImplClass());
			gCheckedCleaned.copyCleaningRedundantLabels(g);
		}
		saveGraphToFile();
		return checkStatus;
	}

	/**
	 * Applies the labeled propagation rule:<br>
	 * <b>Standard DC semantics is assumed.</b><br>
	 * <b>This method is also valid assuming Instantaneous Reaction semantics or epsilon-reaction time.</b><br>
	 * The rule implements 2018-11 qLP+, submitted to ICAPS19.
	 *
	 * <pre>
	 * if A ---(u,α)⟶ B ---(v,β)⟶ C and (u+v &lt; 0 and u &lt; 0) or (u+v &lt; 0 and αβ in P*)
	 * then A ---[(α★β)†, u+v]⟶ C
	 *
	 * α,β in Q*
	 * (α★β)† is the label without children of unknown.
	 *
	 * If A==C and u+v &lt; 0, then
	 * - if (α★β)† does not contain ¿ literals, the network is not DC
	 * - if (α★β)† contains ¿ literals, the u+v becomes -∞
	 * </pre>
	 * <p>
	 * Before storing a new value, it checks the value considering potential rules and R0 one to guarantee to store a
	 * necessary value with the shortest label.
	 *
	 * @param nA  first node.
	 * @param nB  second node.
	 * @param nC  third node.
	 * @param eAB edge nA⟶nB
	 * @param eBC edge nB⟶nC
	 * @param eAC edge nA⟶nC
	 *
	 * @return true if a reduction has been applied.
	 */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "DLS_DEAD_LOCAL_STORE",
		justification = "I know what I'm doing")
	boolean labelPropagation(final LabeledNode nA,
	                         final LabeledNode nB, final LabeledNode nC, final CSTNEdge eAB, final CSTNEdge eBC,
	                         CSTNEdge eAC) {
		// * Be careful, in order to propagate correctly possibly -∞ self-loop, it is necessary call this method also for triple like with nodes A == B or B==C!
		// Visibility is package because there is Junit Class test that checks this method.

		boolean ruleApplied = false;
		Label nAnCLabel = null;
		if (withNodeLabels) {
			nAnCLabel = nA.getLabel().conjunction(nC.getLabel());
			if (nAnCLabel == null) {
				return false;
			}
		}

		final boolean nAisAnObserver = nA.isObserver();
		final char propositionA = nA.getPropositionObserved();
		final boolean nCisAnObserver = nC.isObserver();
		final Literal unkPropositionC = Literal.valueOf(nC.getPropositionObserved(), Literal.UNKNOWN);

		final ObjectSet<Object2IntMap.Entry<Label>> setToReuse = new ObjectArraySet<>();
		String firstLog = "Labeled Propagation Rule considers edges " + eAB.getName() + ", " + eBC.getName() + " for "
		                  + eAC.getName();
		for (final Object2IntMap.Entry<Label> ABEntry : eAB.getLabeledValueSet()) {
			final Label labelAB = ABEntry.getKey();
			final int u = ABEntry.getIntValue();
			for (final Object2IntMap.Entry<Label> BCEntry : eBC.getLabeledValueSet(setToReuse)) {
				final int v = BCEntry.getIntValue();
				int sum = Constants.sumWithOverflowCheck(u, v);
				if (sum > 0) {
					// // It is not necessary to propagate positive values.
					// // Fewer propagations, less useless labeled values.
					// 2018-01-25: I verified that for some negative instances, avoiding the positive propagations can increase the execution time.
					// For positive instances, surely avoiding the positive propagations shorten the execution time.
					// 2018-11-28: Luke and I verified that it is useless to propagate -infinity forward if the
					// destination is not Z. NO!!!
					// 2019-11-09: I verified that if we don't propagate -∞ forward, then LP continues to update edges of a negative q-loop
					// till an overflow is generated. Instance size20-05/template_127_consistent.cstn is a witness.
					continue;
				}
				final Label labelBC = BCEntry.getKey();
				boolean qLabel = false;
				Label newLabelAC;
				if (lpMustRestricted2ConsistentLabel(u, v) || propagationOnlyToZ) {
					// Even if we published that when nC == Z, the label must be consistent, we
					// also showed (but not published) that if u<0, then label can contain unknown even when nC==Z.
					newLabelAC = labelAB.conjunction(labelBC);
					if (newLabelAC == null) {
						continue;
					}
				} else {
					newLabelAC = labelAB.conjunctionExtended(labelBC);
					qLabel = newLabelAC.containsUnknown();
					if (qLabel && withNodeLabels) {
						newLabelAC = removeChildrenOfUnknown(newLabelAC);
						qLabel = newLabelAC.containsUnknown();
					}
				}
				if (withNodeLabels) {
					if (!newLabelAC.subsumes(nAnCLabel)) {
						if (Debug.ON) {
							LOG.log(Level.FINEST,
							        "New alphaBeta label " + newLabelAC + " does not subsume node labels " + nAnCLabel
							        + ". New value cannot be added.");
						}
						continue;
					}
				}

				final int oldValue = eAC.getValue(newLabelAC);
				if (oldValue != Constants.INT_NULL && sum >= oldValue) {
					continue;
				}

				// Prepare the log in advance in order to avoid repetition
				final StringBuilder log = new StringBuilder(80);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						log.append(firstLog).append("\nsource: ")
							.append(nA.getName()).append(" ---").append(pairAsString(labelAB, u)).append("⟶ ")
							.append(nB.getName()).append(" ---").append(pairAsString(labelBC, v)).append("⟶ ")
							.append(nC.getName());
					}
				}
				if (nA == nC) {
					if (sum == 0) {// positive values already discarded.
						continue;
					}
					if (!qLabel) {
						// The labeled value is negative and label is in Q*.
						eAC.mergeLabeledValue(newLabelAC, sum);
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								log.append("\nresult: ")
									.append(nA.getName()).append(" ---").append(pairAsString(newLabelAC, sum))
									.append("---> ")
									.append(nC.getName()).append("; old value: ").append(Constants.formatInt(oldValue));
								LOG.log(Level.FINER,
								        log + "\n***\nFound a negative loop " + pairAsString(newLabelAC, sum)
								        + " in the edge  " + eAC + "\n***");
							}
						}
						checkStatus.consistency = false;
						checkStatus.finished = true;
						checkStatus.labeledValuePropagationCalls++;
						checkStatus.negativeLoopNode = nA;
						return true;
					}
					sum = Constants.INT_NEG_INFINITE;
				}

				// here sum has to be added!
				if (eAC.mergeLabeledValue(newLabelAC, sum)) {
					ruleApplied = true;
					checkStatus.labeledValuePropagationCalls++;
					// R0qR0 rule applied to new labeled value in order to minimize the propagation of dirty values
					if (nAisAnObserver && newLabelAC.contains(propositionA)) {
						final Label newLabelAC1 = labelModificationR0qR0Core(nA, nC, newLabelAC, sum);
						if (!newLabelAC1.equals(newLabelAC)) {
							newLabelAC = newLabelAC1;
							eAC.mergeLabeledValue(newLabelAC, sum);
						}
					}
					// The following is equivalent to R3qR33 when nD==Obs and newLabel contains the unknown literal of obs prop.
					if (nCisAnObserver && newLabelAC.contains(unkPropositionC)) {
						final Label newLabelAC1 = newLabelAC.remove(unkPropositionC);
						if (!newLabelAC1.equals(newLabelAC)) {
							newLabelAC = newLabelAC1;
							sum = 0;
							eAC.mergeLabeledValue(newLabelAC, sum);
						}
					}
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							log.append("\nresult: ").append(nA.getName()).append(" ---")
								.append(pairAsString(newLabelAC, sum)).append("⟶ ").append(nC.getName())
								.append("; old value: ").append(Constants.formatInt(oldValue)).append("\n");
							LOG.log(Level.FINER, log.toString());
							firstLog = "";
						}
					}
				}
			}
		}
		return ruleApplied;

	}

	/**
	 * Returns true if label propagation rule (for example, {@link CSTN#labelPropagation} method) has to apply only for consistent labels.<br> Overriding this
	 * method it is possible implement the different semantics in the {@link CSTN#labelPropagation} method.
	 *
	 * @param u value
	 * @param v value
	 *
	 * @return true if the rule has to be applied only when the resulting label does not contain unknown literals.
	 */
	@SuppressWarnings("static-method")
	boolean lpMustRestricted2ConsistentLabel(final int u, final int v) {
		// Table 1 2016 ICAPS paper for standard DC extended with rules on page 6 of file noteAboutLP.tex
		// Moreover, Luke and I verified on 2018-11-22 that with u≤0, qLP+ can be applied.
		return u > 0;
	}

	/**
	 * Executes one step of the dynamic consistency check. For each edge in edgesToCheck, rules R0--R3 are applied on it
	 * and, then, label propagation rule is applied two times: one time having the edge as first edge, one time having
	 * the edge as second edge.<br> All modified or new edges are returned in the set 'edgesToCheck'.<br> It is assumed
	 * that {@link #propagationOnlyToZ} is false!
	 *
	 * @param edgesToCheck   set of edges that have to be checked.
	 * @param timeoutInstant time instant limit allowed to the computation.
	 *
	 * @return the update status (it is for convenience. It is not necessary because return the same parameter status).
	 *
	 * @throws IllegalStateException if {@link #propagationOnlyToZ} is true.
	 */
	@SuppressWarnings("UnusedReturnValue")
	CSTNCheckStatus oneStepDynamicConsistencyByEdges(final EdgesToCheck<CSTNEdge> edgesToCheck,
	                                                 Instant timeoutInstant) {

		if (propagationOnlyToZ) {
			throw new IllegalStateException(
				"oneStepDynamicConsistencyByEdges can be called only when propagationOnlyToZ is false.");
		}

		LabeledNode A, B, C;
		CSTNEdge AC, CB, edgeCopy;

		checkStatus.cycles++;

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "*** Starting of application labeled propagation rule+R0+R3.");
			}
		}
		/*
		 * March, 06 2016 Apply the rules on all edges that have been modified in the previous cycle.
		 */
		final EdgesToCheck<CSTNEdge> newEdgesToCheck = new EdgesToCheck<>(edgesToCheck.edgesToCheck);
		final EdgesToCheck<CSTNEdge> newEdgesToCheckR0R3 = new EdgesToCheck<>();
		int i = 1, j = 1;
		// Find a stable state using R0 e R3.
		final LabeledNode Z = g.getZ();
		while (edgesToCheck.size() != 0) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "*** R3R0 CYCLE " + (j++));
				}
			}
			for (final CSTNEdge AB : edgesToCheck) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.log(Level.FINER, "*** R3R0: considering edge " + (i++) + "/" + edgesToCheck.size() + ": "
						                     + AB.getName());
					}
				}
				A = g.getSource(AB);
				B = g.getDest(AB);
				assert A != null;
				if (applyR0R3(AB, A, B)) {
					newEdgesToCheckR0R3.add(AB, A, B, Z, g, propagationOnlyToZ);
					newEdgesToCheck.add(AB, A, B, Z, g, propagationOnlyToZ);
				}

				if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
					return checkStatus;
				}
			}
			edgesToCheck.clear();
			edgesToCheck.addAll(newEdgesToCheckR0R3.edgesToCheck);
			newEdgesToCheckR0R3.clear();
			i = 1;
		}

		edgesToCheck.addAll(newEdgesToCheck.edgesToCheck);
		newEdgesToCheck.clear();
		// now it is time to propagate a stable configuration
		for (final CSTNEdge AB : edgesToCheck) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER,
					        "*** LP: considering edge " + (i++) + "/" + edgesToCheck.size() + ": " + AB.getName());
				}
			}
			A = g.getSource(AB);
			B = g.getDest(AB);
			assert A != null;
			assert B != null;
			/*
			  Step 1/2: Make all propagation considering edge AB as first edge.<br>
			  A-->B-->C
			 */
			for (final CSTNEdge BC : g.getOutEdges(B)) {
				C = g.getDest(BC);
				assert C != null;
				// It is necessary to consider also self loop to store first negative loop.
				// In this class, (-∞,q), where q\in Q* are not more propagate on edges, but on nodes!

				AC = g.findEdge(A, C);
				// I need to preserve the old edge to compare below
				if (AC != null) {
					edgeCopy = g.getEdgeFactory().get(AC);
				} else {
					AC = makeNewEdge(A.getName() + "_" + C.getName(), CSTNEdge.ConstraintType.derived);
					edgeCopy = null;
				}

				labelPropagation(A, B, C, AB, BC, AC);

				/*
				  2016-03-08: It has been experimented that it is not necessary to clean AC values using R0 and R3
				 */
				final boolean edgeModified;
				if (edgeCopy == null && !AC.isEmpty()) {
					// the new CB has to be added to the graph!
					g.addEdge(AC, A, C);
					edgeModified = true;
				} else {
					// CB was already present and it has been changed!
					edgeModified = (edgeCopy != null && !edgeCopy.hasSameValues(AC));
				}

				if (edgeModified) {
					applyR0R3(AC, A, C);
					newEdgesToCheck.add(AC, A, C, Z, g, propagationOnlyToZ);
				}

				if (!checkStatus.consistency) {
					// it must be here because a potential can be modified!
					checkStatus.finished = true;
					return checkStatus;
				}
				if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
					return checkStatus;
				}
			}

			/*
			  Step 2/2: Make all propagation considering edge AB as second edge.<br>
			  C-->A-->B
			 */
			for (final CSTNEdge CA : g.getInEdges(A)) {
				C = g.getSource(CA);
				assert C != null;
				if (C == B) {// it has been checked in the Step 1/2
					continue;
				}

				CB = g.findEdge(C, B);
				// I need to preserve the old edge to compare below
				if (CB != null) {
					edgeCopy = g.getEdgeFactory().get(CB);
				} else {
					CB = makeNewEdge(C.getName() + "_" + B.getName(), CSTNEdge.ConstraintType.derived);
					edgeCopy = null;
				}

				labelPropagation(C, A, B, CA, AB, CB);

				boolean edgeModified = false;
				if (edgeCopy == null && !CB.isEmpty()) {
					// the new CB has to be added to the graph!
					g.addEdge(CB, C, B);
				} else {
					// CB was already present and it has been changed!
					edgeModified = edgeCopy != null && !edgeCopy.hasSameValues(CB);
				}

				if (edgeModified) {
					applyR0R3(CB, C, B);
					newEdgesToCheck.add(CB, C, B, Z, g, propagationOnlyToZ);
				}

				if (!checkStatus.consistency) {
					// it must be here because a potential can be modified!
					checkStatus.finished = true;
					return checkStatus;
				}
				if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
					return checkStatus;
				}
			}
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "End application labeled propagation rule+R0+R3.");
			}
		}

		// check the halt conditions
		edgesToCheck.clear();
		checkStatus.finished = newEdgesToCheck.size() == 0;
		if (!checkStatus.finished) {
			edgesToCheck.takeIn(newEdgesToCheck);
		}
		return checkStatus;
	}

	/**
	 * Executes one step of the dynamic consistency check. For each edge B--&gt;Z in edgesToCheck, rules R0--R3 are
	 * applied on it and, then, label propagation rule is applied to A--&gt;B--&gt;Z for all A--&gt;B. All modified or
	 * new edges are returned in the set 'edgesToCheck'. This method does not manage –∞ values (IJCAI18 algorithm).
	 *
	 * @param edgesToCheck   set of edges that have to be checked.
	 * @param timeoutInstant time instant limit allowed to the computation.
	 *
	 * @return the update status (it is for convenience. It is not necessary because return the same parameter status).
	 */
	@SuppressWarnings("UnusedReturnValue")
	CSTNCheckStatus oneStepDynamicConsistencyByEdgesLimitedToZ(final EdgesToCheck<CSTNEdge> edgesToCheck,
	                                                           Instant timeoutInstant) {
		// This version consider only a pair of edges going to Z, i.e., in the form A-->B-->Z,
		LabeledNode B, A;
		CSTNEdge AZ, edgeCopy;

		checkStatus.cycles++;

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "\nStart application labeled propagation rule+R0+R3.");
			}
		}

		final EdgesToCheck<CSTNEdge> newEdgesToCheck = new EdgesToCheck<>(edgesToCheck.edgesToCheck);
		final EdgesToCheck<CSTNEdge> newEdgesToCheckR0R3 = new EdgesToCheck<>();
		int i = 1, j = 1;
		final LabeledNode Z = g.getZ();
		// Find a stable state using R0 and R3.
		while (edgesToCheck.size() != 0) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "\n\n*** R3R0 CYCLE " + (j++));
				}
			}
			for (final CSTNEdge BZ : edgesToCheck) {
				if (g.getDest(BZ) != Z) {
					continue;
				}
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.log(Level.FINER,
						        "\n\n*** R3R0: considering edge " + (i++) + "/" + edgesToCheck.size() + ": "
						        + BZ.getName());
					}
				}
				B = g.getSource(BZ);
				// initAndCheck does not resolve completely a qStar.
				// It is necessary to check here the edge before to consider the second edge.
				// If the second edge is not present, in any case the current edge has been analyzed by R0 and R3 (qStar can be solved)!
				edgeCopy = g.getEdgeFactory().get(BZ);
				assert B != null;
				if (B.isObserver()) {
					// R0 on the resulting new values
					labelModificationR0qR0(B, Z, BZ);
				}

				labelModificationR3qR3(B, Z, BZ);

				if (!BZ.hasSameValues(edgeCopy)) {
					newEdgesToCheckR0R3.add(BZ, B, Z, Z, g, propagationOnlyToZ);
					newEdgesToCheck.add(BZ, B, Z, Z, g, propagationOnlyToZ);
				}

				if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
					return checkStatus;
				}
			}
			edgesToCheck.clear();
			edgesToCheck.addAll(newEdgesToCheckR0R3.edgesToCheck);
			newEdgesToCheckR0R3.clear();
			i = 1;
		}

		edgesToCheck.addAll(newEdgesToCheck.edgesToCheck);
		newEdgesToCheck.clear();

		// now it is time to propagate a stable configuration
		for (final CSTNEdge BZ : edgesToCheck) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER,
					        "\n\n*** LP: considering edge " + (i++) + "/" + edgesToCheck.size() + ": " + BZ.getName());
				}
			}
			B = g.getSource(BZ);
			assert B != null;
			/*
			  Make all propagation considering edge AB as first edge.<br>
			  A-->B-->Z
			 */
			for (final CSTNEdge AB : g.getInEdges(B)) {
				A = g.getSource(AB);
				assert A != null;
				assert Z != null;
				// Attention! It is necessary to consider also self loop, e.g. A==B and B==C to propagate rightly -∞

				AZ = g.findEdge(A, Z);
				// I need to preserve the old edge to compare below
				if (AZ != null) {
					edgeCopy = g.getEdgeFactory().get(AZ);
				} else {
					AZ = makeNewEdge(A.getName() + "_" + Z.getName(), CSTNEdge.ConstraintType.derived);
					edgeCopy = null;
				}

				labelPropagation(A, B, Z, AB, BZ, AZ);

				boolean edgeModified = false;
				if (edgeCopy == null && !AZ.isEmpty()) {
					// the new CB has to be added to the graph!
					g.addEdge(AZ, A, Z);
				} else {
					// CB was already present and it has been changed!
					edgeModified = edgeCopy != null && !edgeCopy.hasSameValues(AZ);
				}

				if (edgeModified) {
					newEdgesToCheck.add(AZ, A, Z, Z, g, propagationOnlyToZ);
					// potentialR3(A, this.Z, AZ, null);
				}

				if (!checkStatus.consistency) {
					checkStatus.finished = true;
					return checkStatus;
				}

				if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
					return checkStatus;
				}
			}
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "End application labeled propagation rule+R0+R3.\n"
				                    + "Starts execution of propagation -∞ from nodes.");
			}
		}

		if (!checkStatus.consistency) {
			checkStatus.finished = true;
			return checkStatus;
		}

		edgesToCheck.clear();
		checkStatus.finished = newEdgesToCheck.size() == 0;
		if (!checkStatus.finished) {
			edgesToCheck.takeIn(newEdgesToCheck);
		}
		return checkStatus;
	}
}
