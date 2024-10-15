// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

package it.univr.di.cstnu.algorithms;

import it.unimi.dsi.fastutil.objects.*;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.univr.di.Debug;
import it.univr.di.cstnu.graph.*;
import it.univr.di.cstnu.visualization.CSTNUStaticLayout;
import it.univr.di.labeledvalue.*;
import it.univr.di.labeledvalue.ALabelAlphabet.ALetter;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents a Conditional Simple Temporal Network with Uncertainty (CSTNU).<br> This class implementation considers
 * instantaneous reactions and uses only rules qR0, and qR3 as label modification rules.<br> Edge values are integers.
 * <br>
 * The input network is transformed into its streamlined version and, then, checked.<br>
 *
 * @author Roberto Posenato
 * @version $Rev: 851 $
 */
@SuppressWarnings({"CommentedOutCode", "UnusedReturnValue"})
public class CSTNU extends AbstractCSTN<CSTNUEdge> {
	/**
	 * Version of the class
	 */
	// static final String VERSIONandDATE = "Version 3.1 - Apr, 20 2016";
	// static final String VERSIONandDATE = "Version 3.2 - June, 14 2016";
	// static final public String VERSIONandDATE = "Version 5.0 - September, 8 2017";// introduced new rules
	// static final public String VERSIONandDATE = "Version 5.0 - September, 8 2017";// removed qLabels
	// static final public String VERSIONandDATE = "Version 5.1 - November, 9 2017";// Replace Ω node with equivalent constraints.
	// static final public String VERSIONandDATE = "Version 5.2 - December, 13 2017";// Adjusted after CSTN consolidation
	// static final public String VERSIONandDATE = "Version 6.0 - February, 21 2018";// zUCore added
	// static final public String VERSIONandDATE = "Version 6.1 - March, 12 2019";// full propagation option added
	// static final public String VERSIONandDATE = "Version 6.2 - June, 9 2019";// Edge refactoring
	// static final public String VERSIONandDATE = "Version 6.3 - June, 9 2019";// CSTN Refactoring
	// static final public String VERSIONandDATE = "Version 6.4 - January, 12 2021";// Fixed an error in initialization
//	static final public String VERSIONandDATE = "Version 6.5 - September, 1 2021";// Fixed the output of the status and of the main method.
	static final public String VERSIONandDATE = "Version 6.5.1- January, 17 2023";// Tweaking
	/**
	 * logger
	 */
	static private final Logger LOG = Logger.getLogger(CSTNU.class.getName());

	/*
	 * Static initializer
	 */
	static {
		FILE_NAME_SUFFIX = ".cstnu";// Override suffix
	}

	/**
	 * Utility map that returns the activation time point (node) associated to a contingent link given the contingent
	 * time point, i.e., contingent link A===&gt;C determines the entry (C,A) in this map.
	 */
	Object2ObjectMap<LabeledNode, LabeledNode> activationNode;
	/**
	 * Represent contingent links also as ordinary constraints.
	 */
	boolean contingentAlsoAsOrdinary;
	/**
	 * Utility map that return the edge containing the lower case constraint of a contingent link given the contingent
	 * time point.
	 */
	Object2ObjectMap<LabeledNode, CSTNUEdge> lowerContingentEdge;

	/**
	 * Just to check if a new labeled value is negative, its label has not unknown literals, and it is in a self loop.
	 *
	 * @param value   value
	 * @param source  source node
	 * @param dest    destination node
	 * @param newEdge new edge
	 * @param status  status of the checking.
	 *
	 * @return true if the value represent a negative loop!
	 */
	static boolean checkAndManageIfNewLabeledValueIsANegativeLoop(final int value, final LabeledNode source,
	                                                              final LabeledNode dest, final BasicCSTNUEdge newEdge,
	                                                              CSTNCheckStatus status) {
		if (source == dest && value < 0) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Found a negative loop in the edge " + newEdge);
				}
			}
			status.consistency = false;
			status.finished = true;
			status.negativeLoopNode = source;
			return true;
		}
		return false;
	}

	/**
	 * @param value    value
	 * @param nodeName node name to put as lower case label
	 * @param label    label to represent
	 *
	 * @return the conventional representation of a labeled value
	 */
	static String lowerCaseValueAsString(ALabel nodeName, int value, Label label) {
		return LabeledLowerCaseValue.entryAsString(nodeName, value, label, true);
	}

	/**
	 * Helper constructor for CSTNU.
	 * <p>
	 * This constructor is useful for making easier the use of this class in environment like Node.js-Java
	 *
	 * @param graphXML the TNGraph to check in GraphML format
	 *
	 * @throws java.io.IOException                            if any error occurs during the graphXML reading
	 * @throws javax.xml.parsers.ParserConfigurationException if graphXML contains character that cannot be parsed
	 * @throws org.xml.sax.SAXException                       if graphXML is not valid
	 */
	public CSTNU(String graphXML) throws IOException, ParserConfigurationException, SAXException {
		this();
		setG((new TNGraphMLReader<CSTNUEdge>()).readGraph(graphXML, EdgeSupplier.DEFAULT_CSTNU_EDGE_CLASS));
	}

	/**
	 * Default constructor, package use only!
	 */
	CSTNU() {
		checkStatus = new CSTNUCheckStatus();
		activationNode = new Object2ObjectOpenHashMap<>();
		lowerContingentEdge = new Object2ObjectOpenHashMap<>();
		propagationOnlyToZ = false;
		contingentAlsoAsOrdinary = true;
		reactionTime = 0;// IR semantics
	}

	/**
	 * Constructor for CSTNU
	 *
	 * @param graph       TNGraph to check
	 * @param giveTimeOut timeout for the check in seconds
	 */
	public CSTNU(TNGraph<CSTNUEdge> graph, int giveTimeOut) {
		this(graph);
		timeOut = giveTimeOut;
	}

	/**
	 * Constructor for CSTNU
	 *
	 * @param graph TNGraph to check
	 */
	public CSTNU(TNGraph<CSTNUEdge> graph) {
		this();
		setG(graph);
	}

	/**
	 * Constructor for CSTNU
	 *
	 * @param graph                TNGraph to check
	 * @param givenTimeOut         timeout for the check in seconds
	 * @param isPropagationOnlyToZ true if it must propagate only to Z
	 */
	public CSTNU(TNGraph<CSTNUEdge> graph, int givenTimeOut, boolean isPropagationOnlyToZ) {
		this(graph);
		timeOut = givenTimeOut;
		propagationOnlyToZ = isPropagationOnlyToZ;
	}

	/**
	 * Reads a CSTNU file and checks it.
	 *
	 * @param args an array of {@link java.lang.String} objects.
	 *
	 * @throws java.io.IOException                            if any.
	 * @throws javax.xml.parsers.ParserConfigurationException if any.
	 * @throws org.xml.sax.SAXException                       if any.
	 */
	public static void main(final String[] args) throws IOException, ParserConfigurationException, SAXException {
		final CSTNU cstnu = new CSTNU();
		System.out.println(cstnu.getVersionAndCopyright());
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "Start...");
			}
		}
		if (!cstnu.manageParameters(args)) {
			return;
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "Parameters ok!");
			}
		}
		if (cstnu.versionReq) {
			System.out.println("CSTNU " + VERSIONandDATE + ". Academic and non-commercial use only.\n" +
			                   "Copyright © 2017-2021 Roberto Posenato");
			return;
		}

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "Loading graph...");
			}
		}
		final TNGraphMLReader<CSTNUEdge> graphMLReader = new TNGraphMLReader<>();

		cstnu.setG(graphMLReader.readGraph(cstnu.fInput, EdgeSupplier.DEFAULT_CSTNU_EDGE_CLASS));
		cstnu.g.setInputFile(cstnu.fInput);

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "TNGraph loaded!\nDC Checking...");
			}
		}
		System.out.println("Checking started...");
		final CSTNUCheckStatus status;
		try {
			status = cstnu.dynamicControllabilityCheck();
		} catch (final WellDefinitionException e) {
			System.out.print("An error has been occurred during the checking: " + e.getMessage());
			return;
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "TNGraph minimized!");
			}
		}
		if (status.finished) {
			System.out.println("Checking finished!");
			if (status.consistency) {
				System.out.println("The given network is Dynamic controllable!");
			} else {
				System.out.println("The given network is NOT Dynamic controllable!");
			}
			System.out.println("Checked graph saved as " + cstnu.fOutput.getCanonicalPath());

			System.out.println("Details: " + status);
		} else {
			System.out.println("Checking has not been finished!");
			System.out.println("Details: " + status);
		}

		if (cstnu.fOutput != null) {
			final TNGraphMLWriter graphWriter = new TNGraphMLWriter(new CSTNUStaticLayout<>(cstnu.g));
			graphWriter.save(cstnu.getGChecked(), cstnu.fOutput);
		}
	}

	/**
	 * IR Semantics
	 *
	 * @param u        value
	 * @param ignoredV value
	 *
	 * @return true if a restricted LP can be applied
	 */
	static public boolean mainConditionForRestrictedLP(final int u, final int ignoredV) {
		// Table 1 ICAPS paper for standard DC
		// u must be < 0
		return u >= 0;
	}

	/**
	 * {@inheritDoc} Wrapper method for {@link #dynamicControllabilityCheck()}
	 */
	@Override
	public CSTNUCheckStatus dynamicConsistencyCheck() throws WellDefinitionException {
		return dynamicControllabilityCheck();
	}

	/**
	 * Checks the dynamic controllability (DC) of the given network (see {@link #CSTNU(TNGraph)} or
	 * {@link #setG(TNGraph)}).<br> If the network is DC, it determines all the minimal ranges for the constraints. <br>
	 * During the execution of this method, the given network is modified. <br> If the check is successful, all
	 * constraints to node Z in the network are minimized; otherwise, the network contains a negative loop at least.
	 * <br>
	 * After a check, {@link #getGChecked()} returns the graph resulting after the check and {@link #getCheckStatus()}
	 * the result of the checking action with some statistics and the node with the negative loop is the network is NOT
	 * DC.<br> In any case, before returning, this method call {@link #saveGraphToFile()} for saving the computed
	 * graph.
	 *
	 * @return an {@link it.univr.di.cstnu.algorithms.CSTNU.CSTNUCheckStatus} object containing the final status and
	 * 	some statistics about the executed checking.
	 *
	 * @throws it.univr.di.cstnu.algorithms.WellDefinitionException if any.
	 */
	public CSTNUCheckStatus dynamicControllabilityCheck() throws WellDefinitionException {
		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO, "\nStarting checking CSTNU dynamic controllability...\n");
			}
		}

		try {
			initAndCheck();
		} catch (final IllegalArgumentException e) {
			throw new IllegalArgumentException(
				"The graph has a problem, and it cannot be initialized: " + e.getMessage());
		}

		final EdgesToCheck<CSTNUEdge> edgesToCheck = new EdgesToCheck<>(g.getEdges());

		final int n = g.getVertexCount();
		int k = g.getContingentNodeCount();
		if (k == 0) {
			k = 1;
		}
		int p = g.getObserverCount();
		if (p == 0) {
			p = 1;
		}
		// FROM TIME 2018: horizon * |T|^2 3^|P| 2^|L|
		int maxCycles = horizon * n * n * p * p * p * k * k;
		if (maxCycles <= 0) {
			maxCycles = Integer.MAX_VALUE;
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.info("The maximum number of possible cycles is " + maxCycles);
			}
		}

		int i;
		checkStatus.finished = false;
		final Instant startInstant = Instant.now();
		final Instant timeoutInstant = startInstant.plusSeconds(timeOut);
		for (i = 1; i <= maxCycles && checkStatus.consistency && !checkStatus.finished; i++) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.log(Level.INFO, "*** Start Main Cycle " + i + "/" + maxCycles + " ***");
				}
			}

			checkStatus = (propagationOnlyToZ) ? oneStepDynamicControllabilityLimitedToZ(edgesToCheck, timeoutInstant)
			                                   : oneStepDynamicControllability(edgesToCheck, timeoutInstant);

			if (!checkStatus.finished) {
				if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
					if (Debug.ON) {
						final String msg =
							"During the check # " + i + " time out of " + timeOut + " seconds occurred. ";
						if (LOG.isLoggable(Level.INFO)) {
							LOG.log(Level.INFO, msg);
						}
					}
					checkStatus.executionTimeNS = Duration.between(startInstant, Instant.now()).toNanos();
					saveGraphToFile();
					return getCheckStatus();
				}
				if (Debug.ON) {
					if (checkStatus.consistency) {
						if (LOG.isLoggable(Level.FINER)) {
							final StringBuilder log = new StringBuilder(
								"During the check n. " + i + ", " + edgesToCheck.size() +
								" edges have been added/modified. Check has to continue.\nDetails of only modified edges having values:\n");
							for (final CSTNUEdge e : edgesToCheck) {
								log.append("Edge ").append(e).append("\n");
							}
							LOG.log(Level.FINER, log.toString());
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

		final Instant endInstant = Instant.now();
		checkStatus.executionTimeNS = Duration.between(startInstant, endInstant).toNanos();

		if (!checkStatus.consistency) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.log(Level.INFO, "After " + (i - 1) +
					                    " cycle, it has been stated that the network is NOT DC controllable.\nStatus: " +
					                    checkStatus);
					if (LOG.isLoggable(Level.FINER)) {
						LOG.log(Level.FINER, "Final NOT DC controllable network: " + g);
					}
				}
			}
			saveGraphToFile();
			return getCheckStatus();
		}

		if (i > maxCycles && !checkStatus.finished) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.log(Level.INFO, "The maximum number of cycle (+" + maxCycles + ") has been reached!\nStatus: " +
					                    checkStatus);
					if (LOG.isLoggable(Level.FINER)) {
						LOG.log(Level.FINER, "Last NOT DC controllable network determined: " + g);
					}
				}
			}
			checkStatus.consistency = checkStatus.finished;
			saveGraphToFile();
			return getCheckStatus();
		}

		// controllable && finished
		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO,
				        "Stable state reached. The network is DC controllable.\nNumber of cycles: " + (i - 1) +
				        " over the maximum allowed " + maxCycles + ".\nStatus: " + checkStatus);
			}
		}
		if (cleanCheckedInstance) {
			gCheckedCleaned = new TNGraph<>(g.getName(), g.getEdgeImplClass());
			gCheckedCleaned.copyCleaningRedundantLabels(g);
		}
		saveGraphToFile();
		return getCheckStatus();
	}

	@Override
	public CSTNUCheckStatus getCheckStatus() {
		return ((CSTNUCheckStatus) checkStatus);
	}

	/**
	 * {@inheritDoc} Calls  and, then, check all contingent links. This method works only with streamlined instances!
	 *
	 * @throws WellDefinitionException if the initial graph is not well-defined. We preferred to throw an exception
	 *                                 instead of returning a negative status to stress that any further operation
	 *                                 cannot be made on this instance.
	 */
	@Override
	public void initAndCheck() throws WellDefinitionException {
		if (checkStatus.initialized) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.WARNING)) {
					LOG.log(Level.WARNING,
					        "Initialization of a CSTNU can be done only one time! Reload the graph if a new init is necessary!");
				}
			}
			return;
		}

		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO, "Starting checking graph as CSTNU well-defined instance...");
			}
		}

		// check underneath CSTN
		coreCSTNInitAndCheck();
		checkStatus.initialized = false;

		// Contingent link have to be checked AFTER WD1 and WD3 have been checked and fixed!
		int maxWeightContingent = Constants.INT_POS_INFINITE;
		for (final CSTNUEdge e : g.getEdges()) {
			if (!e.isContingentEdge()) {
				continue;
			}
			final LabeledNode s = g.getSource(e);
			final LabeledNode d = g.getDest(e);
			/*
			 * Manage contingent link.
			 * A contingent link can have, alternatively:
			 * 1. only one labeled value
			 * 2. a labeled value and a lower case one
			 * 3. a labeled value and an upper case one.
			 */
			final Entry<Label> minLabeledValue =
				e.getMinLabeledValue(); // among possible labeled values, we get the minimum one as a possible.

			int initialValue = minLabeledValue.getIntValue();
			final Label conjunctedLabel = minLabeledValue.getKey();

			if (initialValue == Constants.INT_NULL) {
				if (e.lowerCaseValueSize() == 0 && e.upperCaseValueSize() == 0) {
					throw new IllegalArgumentException("Contingent edge " + e +
					                                   " cannot be initialized because it hasn't an initial value neither a lower/upper case value.");
				}
			}
			if (initialValue == 0) {
				assert d != null;
				if (d.isObserver() && e.upperCaseValueSize() > 0) {
					e.removeLabeledValue(conjunctedLabel);
					initialValue = Constants.INT_NULL;
				} else {
					throw new IllegalArgumentException("Contingent edge " + e +
					                                   " cannot have a bound equals to 0. The two bounds [x,y] have to be 0 < x < y < ∞.");
				}
			}

			final CSTNUEdge eInverted = g.findEdge(d, s);
			if (eInverted == null) {
				assert d != null;
				assert s != null;
				throw new IllegalArgumentException(
					"Contingent edge " + e + " is alone. The companion contingent edge between " + d.getName() +
					" and " + s.getName() + " does not exist while it must exist!");
			}
			if (!eInverted.isContingentEdge()) {
				throw new IllegalArgumentException(
					"Edge " + e + " is contingent while the companion edge " + eInverted +
					" is not contingent!\nIt must be!");
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Edge " + e + " is contingent. Found its companion: " + eInverted);
				}
			}
			/*
			 * Memo.
			 * If current initialValue is negative, current edge is the lower bound C---&gt;A. The lower case labeled value has to be put in the inverted edge.
			 * If the lower case labeled value is already present, it must be equal.
			 * If current initialValue is positive, current edge is the upper bound A---&gt;C. The upper case labeled value has to be put in the inverted edge.
			 * If the upper case labeled value is already present, it must be equal.
			 * if current initialValue is undefined, then we assume that the contingent link is already set.
			 */
			if (initialValue != Constants.INT_NULL) {
				final int eInvertedInitialValue;
				int lowerCaseValue;
				int upperCaseValue;
				eInvertedInitialValue = eInverted.getValue(conjunctedLabel);

				if (initialValue < 0) {
					// current edge is the lower bound.
					assert s != null;
					final ALabel sourceALabel = new ALabel(s.getName(), g.getALabelAlphabet());
					lowerCaseValue = eInverted.getLowerCaseValue().getValue();
					if (lowerCaseValue != Constants.INT_NULL && -initialValue != lowerCaseValue) {
						throw new IllegalArgumentException(
							"Edge " + e + " is contingent with a negative value and the inverted " + eInverted +
							" already contains a ***different*** lower case value: " +
							lowerCaseValueAsString(sourceALabel, lowerCaseValue, conjunctedLabel) + ".");
					}
					if (lowerCaseValue == Constants.INT_NULL &&
					    (eInvertedInitialValue <= 0)) {//eInvertedInitialValue == Constants.INT_NULL is redundant
						throw new IllegalArgumentException(
							"Edge " + e + " is contingent with a negative value but the inverted " + eInverted +
							" does not contain a lower case value neither a proper initial value. ");
					}

					if (lowerCaseValue == Constants.INT_NULL) {
						lowerCaseValue = -initialValue;
						eInverted.setLowerCaseValue(conjunctedLabel, sourceALabel, lowerCaseValue);

						/*
						 * History for lower bound.
						 * 2017-10-11 initialValue = minLabeledValue.getIntValue() is not necessary for the check, but only for AllMax. AllMax building
						 * method cares of it.
						 * 2017-12-22 If activation t.p. is Z, then removing initial value the contingent t.p. has not a right lower bound w.r.t. Z!
						 * 2018-02-21 initialValue = minLabeledValue.getIntValue() allows the reduction of # propagations.
						 */
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								LOG.log(Level.FINER, "Inserted the lower label value: " +
								                     lowerCaseValueAsString(sourceALabel, lowerCaseValue,
								                                            conjunctedLabel) + " to edge " + eInverted);
							}
						}
						upperCaseValue = -eInvertedInitialValue;
						e.mergeUpperCaseValue(conjunctedLabel, sourceALabel, upperCaseValue);
						/*
						 * History for upper bound.
						 * 2017-10-11 such value is not necessary for the check, but only for AllMax. AllMax building method cares of it.
						 * 2017-12-22 If activation t.p. is Z, then removing initial value the contingent t.p. has not a right upper bound w.r.t. Z!
						 * 2018-02-21 Upper bound are not necessary for the completeness, we ignore it.
						 */
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								LOG.log(Level.FINER, "Inserted the upper label value: " +
								                     upperCaseValueAsString(sourceALabel, upperCaseValue,
								                                            conjunctedLabel) + " to edge " + e);
							}
						}
					}
					// In order to speed up the checking, prepare some auxiliary data structure
					s.setALabel(sourceALabel);// s is the contingent node.
					assert d != null;
					STNU.CHECK_ACTIVATION_UNIQUENESS(d, s, activationNode);
					activationNode.put(s, d);
					lowerContingentEdge.put(s, eInverted);

				} else {
					// e : A--->C
					// eInverted : C--->A
					assert d != null;
					final ALabel destALabel = new ALabel(d.getName(), g.getALabelAlphabet());
					if (!destALabel.equals(d.getALabel())) {
						d.setALabel(destALabel);// to speed up DC checking!
					}
					upperCaseValue = eInverted.getUpperCaseValue(conjunctedLabel, destALabel);
					if (upperCaseValue != Constants.INT_NULL && -initialValue != upperCaseValue) {
						throw new IllegalArgumentException(
							"Edge " + e + " is contingent with a positive value and the inverted " + eInverted +
							" already contains a ***different*** upper case value: " +
							upperCaseValueAsString(destALabel, upperCaseValue, conjunctedLabel) + ".");
					}
					if (upperCaseValue == Constants.INT_NULL &&
					    (eInvertedInitialValue == Constants.INT_NULL || eInvertedInitialValue >= 0)) {
						throw new IllegalArgumentException(
							"Edge " + e + " is contingent with a positive value but the inverted " + eInverted +
							" does not contain a upper case value neither a proper initial value. ");
					}
					if (upperCaseValue == Constants.INT_NULL) {
						upperCaseValue = -initialValue;
						eInverted.mergeUpperCaseValue(conjunctedLabel, destALabel, upperCaseValue);
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								LOG.log(Level.FINER, "Inserted the upper label value: " +
								                     upperCaseValueAsString(destALabel, upperCaseValue,
								                                            conjunctedLabel) + " to edge " + eInverted);
							}
						}

						/*
						 * @see comment "History for upper bound." above.
						 */
						lowerCaseValue = -eInvertedInitialValue;
						e.setLowerCaseValue(conjunctedLabel, destALabel, lowerCaseValue);
						// In order to speed up the checking, prepare some auxiliary data structure
						assert s != null;
						STNU.CHECK_ACTIVATION_UNIQUENESS(s, d, activationNode);
						activationNode.put(d, s);
						lowerContingentEdge.put(d, e);
						/*
						 * @see comment "History for lower bound." above.
						 */
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								LOG.log(Level.FINER, "Inserted the lower label value: " +
								                     lowerCaseValueAsString(destALabel, lowerCaseValue,
								                                            conjunctedLabel) + " to edge " + e);
							}
						}
					}
				}
			} else {
				// here initialValue is indefinite... UC and LC values are already present.
				if (!e.getLowerCaseValue().isEmpty()) {
					assert s != null;
					assert d != null;
					STNU.CHECK_ACTIVATION_UNIQUENESS(s, d, activationNode);
					activationNode.put(d, s);
					lowerContingentEdge.put(d, e);
				}
				if (e.upperCaseValueSize() > 0) {
					assert s != null;
					final ALabel sourceALabel = new ALabel(s.getName(), g.getALabelAlphabet());
					if (!sourceALabel.equals(s.getALabel())) {
						s.setALabel(sourceALabel);// to speed up DC checking!
					}
				}
				if (!eInverted.getLowerCaseValue().isEmpty()) {
					assert d != null;
					assert s != null;
					STNU.CHECK_ACTIVATION_UNIQUENESS(d, s, activationNode);
					activationNode.put(s, d);
					lowerContingentEdge.put(s, eInverted);
				}
				if (eInverted.upperCaseValueSize() > 0) {
					assert d != null;
					final ALabel destALabel = new ALabel(d.getName(), g.getALabelAlphabet());
					if (!destALabel.equals(d.getALabel())) {
						d.setALabel(destALabel);// to speed up DC checking!
					}
				}
			}
			if (!contingentAlsoAsOrdinary) {
				e.removeLabeledValue(conjunctedLabel);
				eInverted.removeLabeledValue(conjunctedLabel);
			}

			// it is necessary to check max value
			int m = e.getMinUpperCaseValue().getValue().getIntValue();
			// LOG.warning("m value: " + m);
			if (m != Constants.INT_NULL && m < maxWeightContingent) {
				maxWeightContingent = m;
			}
			m = eInverted.getMinUpperCaseValue().getValue().getIntValue();
			if (m != Constants.INT_NULL && m < maxWeightContingent) {
				maxWeightContingent = m;
			}
		} // end contingent edges cycle

		maxWeightContingent = -maxWeightContingent;
		// LOG.warning("maxWeightContingent value: " + maxWeightContingent);
		// LOG.warning("this.maxWeight value: " + this.maxWeight);
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("maxWeightContingent value found: " + maxWeightContingent + ". MaxWeight not contingent: " +
				          maxWeight);
			}
		}
		if (maxWeightContingent > maxWeight) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.WARNING)) {
					LOG.warning(
						maxWeightContingent + " is the greatest unsigned value found in contingent " + "while " +
						maxWeight + " is the greater unsigned value found in normal constraint.");
				}
			}
			// it is necessary to recalculate horizon
			maxWeight = maxWeightContingent;
			// Determine horizon value
			final long product = ((long) maxWeight) * (g.getVertexCount() - 1);// Z doesn't count!
			if (product >= Constants.INT_POS_INFINITE) {
				throw new ArithmeticException("Horizon value is not representable by an integer.");
			}
			horizon = (int) product;
		}
		addUpperBounds();

		// init CSTNU structures.
		g.getLowerLabeledEdges();
		checkStatus.initialized = true;

		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO, "Checking graph as CSTNU well-defined instance finished!\n");
			}
		}
	}

	/**
	 * @return the contingentAlsoAsOrdinary
	 */
	public boolean isContingentAlsoAsOrdinary() {
		return contingentAlsoAsOrdinary;
	}

	/**
	 * Executes one step of the dynamic controllability check.<br> Before the first execution of this method, it is
	 * necessary to execute {@link #initAndCheck()}.
	 *
	 * @param edgesToCheck   set of edges that have to be checked.
	 * @param timeoutInstant time instant limit allowed to the computation.
	 *
	 * @return the update status (for convenience. It is not necessary because return the same parameter status).
	 */
	public CSTNUCheckStatus oneStepDynamicControllability(final EdgesToCheck<CSTNUEdge> edgesToCheck,
	                                                      Instant timeoutInstant) {

		LabeledNode A, B, C;
		CSTNUEdge AC, CB, edgeCopy;

		checkStatus.cycles++;
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "Start application labeled constraint generation and label removal rules.");
			}
		}

		final EdgesToCheck<CSTNUEdge> newEdgesToCheck = new EdgesToCheck<>();
		int i = 1;
		// int maxNumberOfValueInAnEdge = 0, maxNumberOfUpperCaseValuesInAnEdge = 0;
		// CSTNUEdge fatEdgeInLabeledValues = null, fatEdgeInUpperCaseValues = null;// for sure they will be initialized!
		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO, "Number of edges to analyze: " + edgesToCheck.size());
			}
		}
		final LabeledNode Z = g.getZ();
		for (final CSTNUEdge AB : edgesToCheck) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Considering edge " + (i++) + "/" + edgesToCheck.size() + ": " + AB + "\n");
				}
			}
			A = g.getSource(AB);
			B = g.getDest(AB);
			assert A != null;
			assert B != null;
			// initAndCheck does not resolve completely a possible qStar. So, it is necessary to check here the edge before to consider the second edge.
			// The check has to be done in case B==Z, and it consists in applying R0, R3 and zLabeledLetterRemovalRule!
			edgeCopy = g.getEdgeFactory().get(AB);
			if (B == Z) {
				if (A.isObserver()) {
					// R0 on the resulting new values
					labelModificationqR0(A, AB);
				}
				labelModificationqR3(A, AB);
				if (A.isObserver()) {// R3 can add new values that have to be minimized. Experimentally VERIFIED on June, 28 2015
					// R0 on the resulting new values
					labelModificationqR0(A, AB);
				}
				// zLLR is put here because it works like R0 and R3
				zLabeledLetterRemovalRule(A, AB);
			} else {
				// labeledLetterRemovalRule cleans possible redundant a-letters.
				labeledLetterRemovalRule(A, B, AB);
			}
			if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
				return getCheckStatus();
			}

			if (!AB.hasSameValues(edgeCopy)) {
				assert A != null;
				newEdgesToCheck.add(AB, A, B, Z, g, propagationOnlyToZ);
			}

			if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
				return getCheckStatus();
			}

			/*
			 * Step 1/2: Make all propagation considering edge AB as first edge.<br>
			 * A-->B-->C
			 */
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Rules, phase 1/2: edge " + AB.getName() + " as first component.");
				}
			}
			for (final CSTNUEdge BC : g.getOutEdges(B)) {
				C = g.getDest(BC);
				assert C != null;

				AC = g.findEdge(A, C);
				// I need to preserve the old edge to compare below
				if (AC != null) {
					edgeCopy = g.getEdgeFactory().get(AC);
				} else {
					AC = makeNewEdge(A.getName() + "_" + C.getName(), Edge.ConstraintType.derived);
					edgeCopy = null;
				}

				labeledLetterRemovalRule(B, C, BC);

				labelPropagation(A, B, C, AB, BC, AC);

				if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
					return getCheckStatus();
				}

				/*
				 * The following rule are called if there are condition (avoid to call for nothing)
				 */
				if (!AB.getLowerCaseValue().isEmpty()) {
					labeledCrossLowerCaseRule(A, B, C, AB, BC, AC);
				}

				boolean add = false;
				if (edgeCopy == null && !AC.isEmpty()) {
					// the new CB has to be added to the graph!
					g.addEdge(AC, A, C);
					add = true;
				} else if (edgeCopy != null && !edgeCopy.hasSameValues(AC)) {
					// CB was already present and it has been changed!
					add = true;
				}
				if (add) {
					assert A != null;
					newEdgesToCheck.add(AC, A, C, Z, g, propagationOnlyToZ);
				}

				if (!checkStatus.consistency) {
					checkStatus.finished = true;
					return getCheckStatus();
				}

			}

			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Rules, phase 1/2 done.");
				}
			}

			if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
				return getCheckStatus();
			}

			/*
			 * Step 2/2: Make all propagation considering edge AB as second edge.<br>
			 * C-->A-->B
			 */
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Rules, phase 2/2: edge " + AB.getName() + " as second component.");
				}
			}
			for (final CSTNUEdge CA : g.getInEdges(A)) {
				C = g.getSource(CA);
				assert C != null;

				CB = g.findEdge(C, B);
				// I need to preserve the old edge to compare below
				if (CB != null) {
					edgeCopy = g.getEdgeFactory().get(CB);
				} else {
					assert C != null;
					CB = makeNewEdge(C.getName() + "_" + B.getName(), CSTNUEdge.ConstraintType.derived);
					edgeCopy = null;
				}

				labeledLetterRemovalRule(C, A, CA);

				labelPropagation(C, A, B, CA, AB, CB);

				if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
					return getCheckStatus();
				}

				if (!CA.getLowerCaseValue().isEmpty()) {
					labeledCrossLowerCaseRule(C, A, B, CA, AB, CB);
				}

				boolean add = false;
				if (edgeCopy == null && !CB.isEmpty()) {
					// the new CB has to be added to the graph!
					g.addEdge(CB, C, B);
					add = true;
				} else if (edgeCopy != null && !edgeCopy.hasSameValues(CB)) {
					// CB was already present and it has been changed!
					add = true;
				}
				if (add) {
					assert C != null;
					newEdgesToCheck.add(CB, C, B, Z, g, propagationOnlyToZ);
				}

				if (!checkStatus.consistency) {
					checkStatus.finished = true;
					return getCheckStatus();
				}
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Rules phase 2/2 done.\n");
				}
			}

			if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
				return getCheckStatus();
			}

		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "End application all rules.");
			}
		}
		edgesToCheck.clear();// in any case, this set has been elaborated. It is better to clear it out.
		checkStatus.finished = newEdgesToCheck.size() == 0;
		if (!checkStatus.finished) {
			edgesToCheck.takeIn(newEdgesToCheck);
		}
		return getCheckStatus();
	}

	/**
	 * Executes one step of the dynamic controllability check considering only a pair of edges going to Z, i.e., in the
	 * form A-->B-->Z.<br> Before the first execution of this method, it is necessary to execute
	 * {@link #initAndCheck()}.
	 *
	 * @param edgesToCheck   set of edges that have to be checked.
	 * @param timeoutInstant time instant limit allowed to the computation.
	 *
	 * @return the update status (for convenience. It is not necessary because return the same parameter status).
	 */
	public CSTNUCheckStatus oneStepDynamicControllabilityLimitedToZ(final EdgesToCheck<CSTNUEdge> edgesToCheck,
	                                                                Instant timeoutInstant) {
		//
		// This version consider only a pair of edges going to Z, i.e., in the form A-->B-->Z,
		// 2018-01-25: with this method, performances get worse.
		LabeledNode B, A;
		CSTNUEdge AZ, edgeCopy;


		checkStatus.cycles++;
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE,
				        "Start application labeled constraint generation and label removal rules limited to Z.");
			}
		}

		final EdgesToCheck<CSTNUEdge> newEdgesToCheck = new EdgesToCheck<>();
		int i = 1;
		// int maxNumberOfValueInAnEdge = 0, maxNumberOfUpperCaseValuesInAnEdge = 0;
		// CSTNUEdge fatEdgeInLabeledValues = null, fatEdgeInUpperCaseValues = null;// for sure they will be initialized!
		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO, "Number of edges to analyze: " + edgesToCheck.size());
			}
		}
		final LabeledNode Z = g.getZ();
		assert Z != null;
		for (final CSTNUEdge BZ : edgesToCheck) {
			if (g.getDest(BZ) != Z) {
				continue;
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Considering edge " + (i++) + "/" + edgesToCheck.size() + ": " + BZ + "\n");
				}
			}
			B = g.getSource(BZ);
			// initAndCheck does not resolve completely a possible qStar. So, it is necessary to check here the edge before to consider the second edge.
			// The check has to be done in case B==Z, and it consists in applying R0, R3 and zLabeledLetterRemovalRule!
			edgeCopy = g.getEdgeFactory().get(BZ);
			assert B != null;
			if (B.isObserver()) {
				// R0 on the resulting new values
				labelModificationqR0(B, BZ);
			}
			labelModificationqR3(B, BZ);
			if (B.isObserver()) {// R3 can add new values that have to be minimized. Experimentally VERIFIED on June, 28 2015
				// R0 on the resulting new values
				labelModificationqR0(B, BZ);
			}

			if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
				return getCheckStatus();
			}

			// LLR is put here because it works like R0 and R3
			zLabeledLetterRemovalRule(B, BZ);

			if (!BZ.hasSameValues(edgeCopy)) {
				newEdgesToCheck.add(BZ, B, Z, Z, g, propagationOnlyToZ);
			}

			if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
				return getCheckStatus();
			}

			/*
			 * Make all propagation considering edge AB as first edge in the chain.<br>
			 * A-->B-->Z
			 */
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Apply rules to " + BZ.getName() + " as second edge.");
				}
			}

			for (final CSTNUEdge AB : g.getInEdges(B)) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.log(Level.FINER, "Considering first edge " + AB.getName());
					}
				}
				A = g.getSource(AB);
				assert A != null;

				AZ = g.findEdge(A, Z);
				// I need to preserve the old edge to compare below
				if (AZ != null) {
					edgeCopy = g.getEdgeFactory().get(AZ);
				} else {
					AZ = makeNewEdge(A.getName() + "_" + Z.getName(), CSTNUEdge.ConstraintType.derived);
					edgeCopy = null;
				}

				labelPropagation(A, B, Z, AB, BZ, AZ);

				if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
					return getCheckStatus();
				}

				if (!AB.getLowerCaseValue().isEmpty()) {
					labeledCrossLowerCaseRule(A, B, Z, AB, BZ, AZ);
				}

				boolean add = false;
				if (edgeCopy == null && !AZ.isEmpty()) {
					// the new CB has to be added to the graph!
					g.addEdge(AZ, A, Z);
					add = true;
				} else if (edgeCopy != null && !edgeCopy.hasSameValues(AZ)) {
					// CB was already present and it has been changed!
					add = true;
				}
				if (add) {
					newEdgesToCheck.add(AZ, A, Z, Z, g, propagationOnlyToZ);
				}

				if (!checkStatus.consistency) {
					checkStatus.finished = true;
					return getCheckStatus();
				}
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Rules phase done.\n");
				}
			}

			if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
				return getCheckStatus();
			}

		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "End application all rules.");
			}
		}
		edgesToCheck.clear();// in any case, this set has been elaborated. It is better to clear it out.
		checkStatus.finished = newEdgesToCheck.size() == 0;
		if (!checkStatus.finished) {
			edgesToCheck.takeIn(newEdgesToCheck);
		}
		return getCheckStatus();
	}

	/**
	 * Resets all internal structures
	 */
	@Override
	public void reset() {
		super.reset();
		if (activationNode == null) {
			activationNode = new Object2ObjectOpenHashMap<>();
			lowerContingentEdge = new Object2ObjectOpenHashMap<>();
			return;
		}
		activationNode.clear();
		lowerContingentEdge.clear();
	}

	/**
	 * Setter for the field {@code contingentAlsoAsOrdinary}.
	 *
	 * @param givenContingentAlsoAsOrdinary the contingentAlsoAsOrdinary to set
	 */
	public void setContingentAlsoAsOrdinary(boolean givenContingentAlsoAsOrdinary) {
		if (contingentAlsoAsOrdinary == givenContingentAlsoAsOrdinary) {
			return;
		}
		contingentAlsoAsOrdinary = givenContingentAlsoAsOrdinary;
		setG(g);// this resets everything.
	}

	/**
	 * Calls and, then, checks upper and lower case values.
	 *
	 * @param source       the source node of the edge.
	 * @param destination  the destination node of the edge.
	 * @param e            edge representing a labeled constraint.
	 * @param hasToBeFixed true for fixing well-definition errors that can be fixed!
	 *
	 * @return false if the check fails, true otherwise
	 *
	 * @throws WellDefinitionException if definition property is not satisfied
	 */
	@SuppressWarnings("AssignmentToForLoopParameter")
	@Override
	boolean checkWellDefinitionProperty1and3(final LabeledNode source, final LabeledNode destination, final CSTNUEdge e,
	                                         boolean hasToBeFixed) throws WellDefinitionException {

		super.checkWellDefinitionProperty1and3(source, destination, e, hasToBeFixed);
		final Label conjunctedLabel = source.getLabel().conjunction(destination.getLabel());

		// check the upper case labeled values
		int value;
		for (final ALabel alabel : e.getUpperCaseValueMap().keySet()) {
			final LabeledIntMap labeledValues = e.getUpperCaseValueMap().get(alabel);
			for (Label currentLabel : labeledValues.keySet()) {
				value = e.getUpperCaseValue(currentLabel, alabel);
				if (value == Constants.INT_NULL) {
					continue;
				}
				if (!currentLabel.subsumes(conjunctedLabel)) {
					final String msg =
						"Upper case Labeled value " + upperCaseValueAsString(alabel, value, currentLabel) +
						" of edge " + e.getName() + " does not subsume the conjunct endpoint labels " +
						conjunctedLabel;
					if (hasToBeFixed) {
						e.removeUpperCaseValue(currentLabel, alabel);
						e.mergeUpperCaseValue(conjunctedLabel, alabel, value);
						if (Debug.ON) {
							if (LOG.isLoggable(Level.WARNING)) {
								LOG.log(Level.WARNING,
								        msg + " Labeled value " + upperCaseValueAsString(alabel, value, currentLabel) +
								        " removed. Labeled value " +
								        upperCaseValueAsString(alabel, value, conjunctedLabel) + " added.");
							}
						}
						currentLabel = conjunctedLabel;
					} else {
						throw new WellDefinitionException(msg, WellDefinitionException.Type.LabelNotSubsumes);
					}
				}

				// Checks if label subsumes all observer-t.p. labels of observer t.p. whose proposition is present into the label.
				// WD3 property.
				Label currentLabelModified = currentLabel;
				for (final char l : currentLabel.getPropositions()) {
					final LabeledNode obs = g.getObserver(l);
					if (obs == null) {
						final String msg =
							"Observation node of literal " + l + " of upper case label " + currentLabel + " in edge " +
							e + " does not exist.";
						if (Debug.ON) {
							if (LOG.isLoggable(Level.WARNING)) {
								LOG.log(Level.WARNING, msg);
							}
						}
						throw new WellDefinitionException(msg,
						                                  WellDefinitionException.Type.ObservationNodeDoesNotExist);
					}
					// Checks WD3 and adjusts
					final Label obsLabel = obs.getLabel();
					if (!currentLabel.subsumes(obsLabel)) {
						final String msg =
							"Label " + currentLabel + " of edge " + e + " does not subsume label of obs node " + obs +
							". It has been fixed.";
						if (Debug.ON) {
							if (LOG.isLoggable(Level.WARNING)) {
								LOG.log(Level.WARNING, msg);
							}
						}
						currentLabelModified = currentLabelModified.conjunction(obsLabel);
						if (currentLabelModified == null) {
							if (Debug.ON) {
								if (LOG.isLoggable(Level.WARNING)) {
									LOG.log(Level.WARNING, "Label " + currentLabel + " of edge " + e +
									                       " does not subsume label of obs node " + obs +
									                       " and cannot be expanded because it becomes inconsistent.");
								}
							}
							throw new WellDefinitionException(msg, WellDefinitionException.Type.LabelInconsistent);
						}
					}
				}
				if (!currentLabelModified.equals(currentLabel)) {
					e.removeUpperCaseValue(currentLabel, alabel);
					e.mergeUpperCaseValue(currentLabelModified, alabel, value);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.WARNING)) {
							LOG.log(Level.WARNING,
							        "Labeled value " + upperCaseValueAsString(alabel, value, currentLabelModified) +
							        " replace dishonest labeled value " +
							        upperCaseValueAsString(alabel, value, currentLabel) + " in edge " + e + ".");
						}
					}
				}
			}
		}
		// lower case
		final LabeledLowerCaseValue lowerValue = e.getLowerCaseValue();
		if (!lowerValue.isEmpty()) {
			if (!lowerValue.getLabel().subsumes(conjunctedLabel)) {
				final String msg = "Labeled lower-case value " + lowerValue + " of edge " + e.getName() +
				                   " does not subsume the endpoint labels.";
				throw new WellDefinitionException(msg, WellDefinitionException.Type.LabelNotSubsumes);
			}
		}
		return true;
	}

	/**
	 * @param value    value
	 * @param nodeName node name to put as upper case label
	 * @param label    label to represent
	 *
	 * @return the conventional representation of a labeled value
	 */
	static String upperCaseValueAsString(ALabel nodeName, int value, Label label) {
		return LabeledALabelIntTreeMap.entryAsString(label, value, nodeName);
	}

	/**
	 * @param nC node
	 *
	 * @return the activation node associated to the contingent link having nC as contingent time point.
	 */
	LabeledNode getActivationNode(LabeledNode nC) {
		return activationNode.get(nC);
	}

	/**
	 * @param nC node
	 *
	 * @return the edge containing the lower case value associated to the contingent link having nC as contingent time
	 * 	point.
	 */
	CSTNUEdge getLowerContingentLink(LabeledNode nC) {
		return lowerContingentEdge.get(nC);
	}

	/**
	 * @param nA node
	 *
	 * @return true if nA is an activation time point
	 */
	boolean isActivationNode(LabeledNode nA) {
		return activationNode.containsValue(nA);
	}

	/**
	 * Implements the zqR0 rule assuming instantaneous reaction and a streamlined network.<br>
	 * <b>This differs from
	 * {@link CSTN#labelModificationR0qR0(LabeledNode, LabeledNode, it.univr.di.cstnu.graph.CSTNEdge)} in the checking
	 * also upper case value</b>
	 *
	 * @param nObs the observation node
	 * @param ePZ  the edge connecting P? ---&gt; Z
	 *
	 * @return true if the rule has been applied one time at least.
	 */
	boolean labelModificationqR0(final LabeledNode nObs, final CSTNUEdge ePZ) {

		boolean ruleApplied = false, mergeStatus;

		final char p = nObs.getPropositionObserved();
		if (p == Constants.UNKNOWN) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Method zqR0 called passing a non observation node as first parameter!");
				}
			}
			return false;
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "Label Modification zqR0: start.");
			}
		}

		/*
		 * After some test, I verified that analyzing labeled value map and labeled upper-case map separately is not more efficient than
		 * making a union of them and analyzing then.
		 */
		final LabeledALabelIntTreeMap mapOfAllValues = ePZ.getAllUpperCaseAndLabeledValuesMaps();
		final LabeledNode Z = g.getZ();
		assert Z != null;
		for (final ALabel aleph : mapOfAllValues.keySet()) {
			final boolean alephNOTEmpty = !aleph.isEmpty();
			for (final Label alpha : mapOfAllValues.get(aleph).keySet()) {
				if (alpha == null || !alpha.contains(p)) {
					continue;
				}
				final int w = (alephNOTEmpty) ? ePZ.getUpperCaseValue(alpha, aleph) : ePZ.getValue(alpha);
				// It is necessary to re-check if the value is still present. Verified that it is necessary on Nov, 26 2015
				if (w == Constants.INT_NULL || mainConditionForSkippingInR0qR0(w)) {// Table 1 ICAPS paper
					continue;
				}

				final Label alphaPrime = makeAlphaPrime(Z, nObs, p, alpha);
				if (alphaPrime == null) {
					continue;
				}

				// Prepare the log message now with old values of the edge. If R0 modifies, then we can log it correctly.
				String logMessage = null;
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						logMessage =
							"zqR0 simplifies a label of edge " + ePZ.getName() + ":\nsource: " + nObs.getName() +
							" ---" + upperCaseValueAsString(aleph, w, alpha) + "---> " + Z.getName() + "\nresult: " +
							nObs.getName() + " ---" + upperCaseValueAsString(aleph, w, alphaPrime) + "---> " +
							Z.getName();
					}
				}

				mergeStatus = (alephNOTEmpty) ? ePZ.mergeUpperCaseValue(alphaPrime, aleph, w)
				                              : ePZ.mergeLabeledValue(alphaPrime, w);
				if (mergeStatus) {
					ruleApplied = true;
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							LOG.log(Level.FINER, logMessage);
						}
					}
					checkStatus.r0calls++;
				}
			}
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "Label Modification zqR0: end.");
			}
		}
		return ruleApplied;
	}

	/**
	 * Implements the qR3* rule assuming instantaneous reaction and a streamlined network.<br>
	 * <b>This differs from
	 * {@link CSTNIR3RwoNodeLabels#labelModificationR3qR3(LabeledNode, LabeledNode, it.univr.di.cstnu.graph.CSTNEdge)}
	 * in the checking also upper case value.</b>
	 *
	 * @param nS  node
	 * @param eSZ CSTNUEdge containing the constraint to modify
	 *
	 * @return true if a rule has been applied.
	 */
	// Visibility is package because there is Junit Class test that checks this method.
	boolean labelModificationqR3(final LabeledNode nS, final CSTNUEdge eSZ) {

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "Label Modification zqR3*: start.");
			}
		}
		boolean ruleApplied = false;
		final LabeledNode Z = g.getZ();
		assert Z != null;
		final ObjectList<CSTNUEdge> Obs2ZEdges = getEdgeFromObserversToNode(Z);

		final LabeledALabelIntTreeMap allValueMapSZ = eSZ.getAllUpperCaseAndLabeledValuesMaps();
		if (allValueMapSZ.isEmpty()) {
			return false;
		}

		final ObjectSet<Label> SZLabelSet = eSZ.getLabeledValueMap().keySet();
		SZLabelSet.addAll(eSZ.getUpperCaseValueMap().labelSet());

		Label allLiteralsSZ = Label.emptyLabel;
		for (final Label l : SZLabelSet) {
			allLiteralsSZ = allLiteralsSZ.conjunctionExtended(l);
		}

		// check each edge from an observer node to Z.
		for (final CSTNUEdge eObsZ : Obs2ZEdges) {
			final LabeledNode nObs = g.getSource(eObsZ);
			if (nObs == nS) {
				continue;
			}

			assert nObs != null;
			final char p = nObs.getPropositionObserved();

			if (!allLiteralsSZ.contains(p)) {
				// no label in nS-->Z contain any literal of p.
				continue;
			}

			// all labels from current Obs
			final LabeledALabelIntTreeMap allValueMapObsZ = eObsZ.getAllUpperCaseAndLabeledValuesMaps();
			for (final ALabel aleph1 : allValueMapObsZ.keySet()) {
				for (final it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Label> entryObsZ : allValueMapObsZ.get(
					aleph1).entrySet()) {// entrySet read-only
					final int w = entryObsZ.getIntValue();
					if (mainConditionForSkippingInR3qR3(w, Z)) { // Table 1 ICAPS
						continue;
					}

					final Label gamma = entryObsZ.getKey();

					for (final ALabel aleph : allValueMapSZ.keySet()) {

						for (final Label SZLabel : allValueMapSZ.get(aleph).keySet()) {

							if (SZLabel == null || !SZLabel.contains(p)) {
								continue;
							}

							final int v =
								(aleph.isEmpty()) ? eSZ.getValue(SZLabel) : eSZ.getUpperCaseValue(SZLabel, aleph);
							if (v == Constants.INT_NULL) {
								// the value has been removed in a previous merge! Verified that it is necessary on Nov, 26 2015
								continue;
							}

							final Label newLabel = makeBetaGammaDagger4qR3(nS, nObs, p, gamma, SZLabel);
							if (newLabel == null) {
								continue;
							}
							final int max = newValueInR3qR3(v, w);
							final ALabel newUpperCaseLetter = aleph.conjunction(aleph1);

							final boolean localRuleApplied =
								(newUpperCaseLetter == null || newUpperCaseLetter.isEmpty()) ? eSZ.mergeLabeledValue(
									newLabel, max) : eSZ.mergeUpperCaseValue(newLabel, newUpperCaseLetter, max);

							if (localRuleApplied) {
								if (Debug.ON) {
									if (LOG.isLoggable(Level.FINER)) {
										LOG.log(Level.FINER,
										        "zqR3* adds a labeled value to edge " + eSZ.getName() + ":\n" +
										        "source: " + nObs.getName() + " ---" +
										        upperCaseValueAsString(aleph1, w, gamma) + "---> " + Z.getName() +
										        " <---" + upperCaseValueAsString(aleph, v, SZLabel) + "--- " +
										        nS.getName() + "\nresult: add " + Z.getName() + " <---" +
										        upperCaseValueAsString(newUpperCaseLetter, max, newLabel) + "--- " +
										        nS.getName());
									}
								}
								checkStatus.r3calls++;
								ruleApplied = true;
							}
						}
					}
				}
			}
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "Label Modification zqR3*: end.");
			}
		}
		return ruleApplied;
	}

	/**
	 * Applies 'labeled no case' and 'labeled upper case' and 'forward labeled upper case' and 'labeled conjunct upper
	 * case' rules.<br>
	 *
	 * <pre>
	 * 1) CASE zLP/Nc/Uc
	 *        v,ℵ,β           u,◇,α
	 * W &lt;------------ Y &lt;------------ X
	 * adds
	 *     u+v,ℵ,αβ
	 * W &lt;------------------------------X
	 *
	 * ℵ can be empty. If |ℵ| &gt; 1, then W must be Z.
	 *
	 * 2) CASE z!
	 * Also known as z!
	 *     v,ℵ,β           u,C,α
	 * Z &lt;------------ Y &lt;------------ C
	 * adds
	 *     u+v,Cℵ,αβ
	 * Z &lt;------------------------------C
	 *
	 * ℵ can be empty.
	 * </pre>
	 *
	 * @param nX  node
	 * @param nY  node
	 * @param nW  node
	 * @param eXY CANNOT BE NULL
	 * @param eYW CANNOT BE NULL
	 * @param eXW CANNOT BE NULL
	 *
	 * @return true if a reduction is applied at least
	 */
	// Don't rename such method because it has to overwrite the CSTN one!
	// Don't rename such method because it has to overwrite the CSTN one!
	// Don't rename such method because it has to overwrite the CSTN one!
	boolean labelPropagation(final LabeledNode nX, final LabeledNode nY, final LabeledNode nW, final CSTNUEdge eXY,
	                         final CSTNUEdge eYW, final CSTNUEdge eXW) {

		boolean ruleApplied = false;
		final LabeledNode Z = g.getZ();
		final boolean nWisNotZ = nW != Z;
		final LabeledALabelIntTreeMap YWAllLabeledValueMap = eYW.getAllUpperCaseAndLabeledValuesMaps();
		if (YWAllLabeledValueMap.isEmpty()) {
			return false;
		}

		final Set<Object2IntMap.Entry<Label>> XYLabeledValueMap = eXY.getLabeledValueSet();
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "zLP/Nc/Uc + z!: start.");
			}
		}

		// 1) CASE LNC + LUC*
		for (final Object2IntMap.Entry<Label> entryXY : XYLabeledValueMap) {
			final Label alpha = entryXY.getKey();
			final int u = entryXY.getIntValue();

			for (final ALabel aleph : YWAllLabeledValueMap.keySet()) {
				if (nWisNotZ && aleph.size() > 1) {
					continue;// rule condition
				}

				for (final it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Label> entryYW : YWAllLabeledValueMap.get(
					aleph).entrySet()) {// entrySet read-only
					final Label beta = entryYW.getKey();
					final Label alphaBeta;
					alphaBeta = alpha.conjunction(beta);
					if (alphaBeta == null) {
						continue;
					}

					final int v = entryYW.getIntValue();
					final int sum = Constants.sumWithOverflowCheck(u, v);
					/*
					 * 2018-07-18. With the sound-and-complete algorithm, positive values are not necessary anymore.
					 * 2018-01-25. We discovered that it is necessary to propagate positive UPPER CASE values!
					 * normal positive values may be not propagate for saving computation time!
					 * aleph.isEmpty() is necessary!
					 */
					if (propagationOnlyToZ &&
					    sum > 0)// && aleph.isEmpty()) // New condition that works well for big instances!
					{
						continue;
					}

					if (nX == nW && sum >= 0) {
						// it would be a redundant edge
						continue;
					}

					final int oldValue =
						(aleph.isEmpty()) ? eXW.getValue(alphaBeta) : eXW.getUpperCaseValue(alphaBeta, aleph);

					if ((oldValue != Constants.INT_NULL) && (sum >= oldValue)) {
						// value is stored only if it is more negative than the current one.
						continue;
					}

					String logMsg = null;
					if (Debug.ON) {
						final String oldXW = eXW.toString();
						logMsg = "zLP/Nc/Uc applied to edge " + oldXW + ":\n" + "Detail: " + nW.getName() + " <---" +
						         upperCaseValueAsString(aleph, v, beta) + "--- " + nY.getName() + " <---" +
						         upperCaseValueAsString(ALabel.emptyLabel, u, alpha) + "--- " + nX.getName() +
						         "\nresult: " + nW.getName() + " <---" + upperCaseValueAsString(aleph, sum, alphaBeta) +
						         "--- " + nX.getName() + "; old value: " + Constants.formatInt(oldValue);
					}

					final boolean mergeStatus = (aleph.isEmpty()) ? eXW.mergeLabeledValue(alphaBeta, sum)
					                                              : eXW.mergeUpperCaseValue(alphaBeta, aleph, sum);

					if (mergeStatus) {
						ruleApplied = true;
						if (aleph.isEmpty()) {
							checkStatus.labeledValuePropagationCalls++;
						} else {
							getCheckStatus().zExclamationRuleCalls++;
						}
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								LOG.log(Level.FINER, logMsg);
							}
						}

						if (checkAndManageIfNewLabeledValueIsANegativeLoop(sum, nX, nW, eXW, checkStatus)) {
							if (Debug.ON) {
								if (LOG.isLoggable(Level.INFO)) {
									LOG.log(Level.INFO, logMsg);
								}
							}
							return true;
						}
					}
				}
			}
		}

		if (nWisNotZ) {
			// it is possible to stop here, because the second part is applicable only when nW==Z.
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "zLP/Nc/Uc + z!: end.");
				}
			}
			return ruleApplied;
		}

		final ObjectSet<ALabel> XYUpperCaseALabels = eXY.getUpperCaseValueMap().keySet();

		// 2) CASE FLUC + LCUC
		final ALabel nXasALabel = nX.getALabel();
		for (final ALabel upperCaseLabel : XYUpperCaseALabels) {
			if (upperCaseLabel.size() != 1 || !upperCaseLabel.equals(nXasALabel)) {
				continue;// only UC label corresponding to original contingent upper case value is considered.
			}
			for (final it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Label> entryXY : eXY.getUpperCaseValueMap()
				.get(upperCaseLabel).entrySet()) {// entrySet
				// read-only
				final Label alpha = entryXY.getKey();
				final int u = entryXY.getIntValue();

				for (final ALabel aleph : YWAllLabeledValueMap.keySet()) {
					for (final it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Label> entryYW : YWAllLabeledValueMap.get(
						aleph).entrySet()) {// entrySet read-only
						final Label beta = entryYW.getKey();

						final Label alphaBeta = alpha.conjunction(beta);
						if (alphaBeta == null) {
							continue;
						}

						final ALabel upperCaseLetterAleph = upperCaseLabel.conjunction(aleph);
						final int v = entryYW.getIntValue();

						final int sum = Constants.sumWithOverflowCheck(u, v);
						if (sum > 0) {
							if (propagationOnlyToZ) {// && upperCaseLetterAleph.isEmpty()) // upperCaseLetterAleph is never empty!
								continue;
							}
							if (nX == nW) {
								// it would be a redundant edge
								continue;
							}
							// transform it as no upper-case value (useful for CSTNPSU)
							final int oldValue = eXW.getValue(alphaBeta);
							String logMsg = null;
							if (Debug.ON) {
								final String oldXW = eXW.toString();
								logMsg = "z! applied to edge " + oldXW + ":\n" + "Detail: " + nW.getName() + " <---" +
								         upperCaseValueAsString(aleph, v, beta) + "--- " + nY.getName() + " <---" +
								         upperCaseValueAsString(upperCaseLabel, u, alpha) + "--- " + nX.getName() +
								         "\nresult: " + nW.getName() + " <---" +
								         upperCaseValueAsString(ALabel.emptyLabel, sum, alphaBeta) + "--- " +
								         nX.getName() + "; old value: " + Constants.formatInt(oldValue);

							}
							if ((oldValue != Constants.INT_NULL) && (sum >= oldValue)) {
								// in the case of A != C, a value is stored only if it is more negative than the current one.
								continue;
							}
							final boolean mergeStatus = eXW.mergeLabeledValue(alphaBeta, sum);
							if (mergeStatus) {
								ruleApplied = true;
								getCheckStatus().zExclamationRuleCalls++;
								if (Debug.ON) {
									if (LOG.isLoggable(Level.FINER)) {
										LOG.log(Level.FINER, logMsg);
									}
								}

								if (checkAndManageIfNewLabeledValueIsANegativeLoop(sum, nX, nW, eXW, checkStatus)) {
									if (Debug.ON) {
										if (LOG.isLoggable(Level.INFO)) {
											LOG.log(Level.INFO, logMsg);
										}
									}
									return true;
								}
							}
						}

						final int oldValue = eXW.getUpperCaseValue(alphaBeta, upperCaseLetterAleph);

						if ((oldValue != Constants.INT_NULL) && (sum >= oldValue)) {
							// in the case of A != C, a value is stored only if it is more negative than the current one.
							continue;
						}

						String logMsg = null;
						if (Debug.ON) {
							final String oldXW = eXW.toString();
							logMsg = "z! applied to edge " + oldXW + ":\n" + "Detail: " + nW.getName() + " <---" +
							         upperCaseValueAsString(aleph, v, beta) + "--- " + nY.getName() + " <---" +
							         upperCaseValueAsString(upperCaseLabel, u, alpha) + "--- " + nX.getName() +
							         "\nresult: " + nW.getName() + " <---" +
							         upperCaseValueAsString(upperCaseLetterAleph, sum, alphaBeta) + "--- " +
							         nX.getName() + "; old value: " + Constants.formatInt(oldValue);
						}

						final boolean mergeStatus = eXW.mergeUpperCaseValue(alphaBeta, upperCaseLetterAleph, sum);

						if (mergeStatus) {
							ruleApplied = true;
							getCheckStatus().zExclamationRuleCalls++;
							if (Debug.ON) {
								if (LOG.isLoggable(Level.FINER)) {
									LOG.log(Level.FINER, logMsg);
								}
							}

							if (checkAndManageIfNewLabeledValueIsANegativeLoop(sum, nX, nW, eXW, checkStatus)) {
								if (Debug.ON) {
									if (LOG.isLoggable(Level.INFO)) {
										LOG.log(Level.INFO, logMsg);
									}
								}
								return true;
							}
						}
					}
				}
			}
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "zLP/Nc/Uc + z!: end.");
			}
		}
		return ruleApplied;
	}

	/**
	 * <b>Labeled Cross-Lower Case (zLc/CC)</b>
	 *
	 * <pre>
	 *     v,ℵ,β           u,c,α
	 * X &lt;------------ C &lt;------------ A
	 * adds
	 *             u+v,ℵ,αβ
	 * X &lt;----------------------------A
	 *
	 * if αβ∈P*, C ∉ ℵ, and v &lt; 0. If |ℵ| &gt; 1, then X must be Z.
	 * </pre>
	 * <p>
	 * Since it is assumed that L(C)=L(A)=α, there is only ONE lower-case labeled value u,c,α!
	 *
	 * @param nA  node
	 * @param nC  node
	 * @param nX  node
	 * @param eAC CANNOT BE NULL
	 * @param eCX CANNOT BE NULL
	 * @param eAX CANNOT BE NULL
	 *
	 * @return true if the rule has been applied.
	 */
	boolean labeledCrossLowerCaseRule(final LabeledNode nA, final LabeledNode nC, final LabeledNode nX,
	                                  final CSTNUEdge eAC, final CSTNUEdge eCX, final CSTNUEdge eAX) {

		boolean ruleApplied = false;
		final LabeledLowerCaseValue lowerCaseValue = eAC.getLowerCaseValue();
		if (lowerCaseValue.isEmpty()) {
			return false;
		}

		// Since it is assumed that L(C)=L(A)=α, there is only ONE lower-case labeled value u,c,α!
		final ALabel c = lowerCaseValue.getNodeName();
		final Label alpha = lowerCaseValue.getLabel();
		final int u = lowerCaseValue.getValue();

		final LabeledALabelIntTreeMap CXAllValueMap = eCX.getAllUpperCaseAndLabeledValuesMaps();
		if (CXAllValueMap.isEmpty()) {
			return false;
		}

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "zLc/CC: start.");
			}
		}

		final LabeledNode Z = g.getZ();
		for (final ALabel aleph : CXAllValueMap.keySet()) {
			final LabeledIntMap valuesMap = CXAllValueMap.get(aleph);
			if (valuesMap == null) {
				continue;
			}
			final boolean emptyAleph = aleph.isEmpty();

			final boolean alephNOTEmpty = !aleph.isEmpty();
			// Rule condition: upper case label cannot be equal or contain c name
			if (alephNOTEmpty && aleph.contains(c)) {
				continue;// rule condition
			}

			for (final it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Label> entryCX : valuesMap.entrySet()) {// entrySet read-only
				final int v = entryCX.getIntValue();
				if (v >= 0 && nX !=
				              Z)// the following condition is not applicable because we are considering instantaneous reaction: || (v == 0 && nX == nC))
				{
					continue; // Rule condition!
				}

				final Label beta = entryCX.getKey();
				final Label alphaBeta = beta.conjunction(alpha);
				if (alphaBeta == null) {
					continue;
				}
				final int sum = Constants.sumWithOverflowCheck(v, u);

				if (sum >
				    0)// && aleph.isEmpty()) // (sum > 0) works well for big DC instances! (sum > 0 && aleph.isEmpty()) works well for not-DC ones!
				{
					continue;
				}

				final int oldValue = (emptyAleph) ? eAX.getValue(alphaBeta) : eAX.getUpperCaseValue(alphaBeta, aleph);

				if (oldValue != Constants.INT_NULL && oldValue <= sum) {
					continue;
				}
				String logMsg = null;
				if (Debug.ON) {
					final String oldAX = eAX.toString();
					logMsg = "zLc/CC applied to edge " + oldAX + ":\nDetail: " + nX.getName() + " <---" +
					         upperCaseValueAsString(aleph, v, beta) + "--- " + nC.getName() + " <---" +
					         lowerCaseValueAsString(c, u, alpha) + "--- " + nA.getName() + "\nresult: " + nX.getName() +
					         " <---" + upperCaseValueAsString(aleph, sum, alphaBeta) + "--- " + nA.getName() +
					         "; oldValue: " + Constants.formatInt(oldValue);
				}

				final boolean localApp = (emptyAleph) ? eAX.mergeLabeledValue(alphaBeta, sum)
				                                      : eAX.mergeUpperCaseValue(alphaBeta, aleph, sum);

				if (localApp) {
					ruleApplied = true;
					if (emptyAleph) {
						getCheckStatus().crossCaseRuleCalls++;
					} else {
						getCheckStatus().lowerCaseRuleCalls++;
					}
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							LOG.log(Level.FINER, logMsg);
						}
					}
				}

				if (checkAndManageIfNewLabeledValueIsANegativeLoop(sum, nA, nX, eAX, checkStatus)) {
					if (Debug.ON) {
						if (LOG.isLoggable(Level.INFO)) {
							LOG.log(Level.INFO, logMsg);
						}
					}
					return true;
				}
			}
		}

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "zLc/CC: end.");
			}
		}
		return ruleApplied;
	}

	/**
	 * Labeled Letter Removal (Lr)<br>
	 *
	 * <pre>
	 * X ---(v,ℵ,β)---&gt; A ---(x,c,α)---&gt; C
	 *
	 * adds
	 *
	 * X ---(m,ℵ',β)---&gt; A
	 *
	 * if C ∈ ℵ, m = max(v, −x), β entails α.
	 * ℵ'=ℵ'/C
	 * </pre>
	 *
	 * @param nX  node
	 * @param nA  node
	 * @param eXA edge
	 *
	 * @return true if the reduction has been applied.
	 */
	boolean labeledLetterRemovalRule(final LabeledNode nX, final LabeledNode nA, final CSTNUEdge eXA) {

		if (!isActivationNode(nA) || eXA.getUpperCaseValueMap().isEmpty()) {
			return false;
		}

		boolean ruleApplied = false;
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "Lr: start.");
			}
		}

		for (final CSTNUEdge eAC : g.getOutEdges(nA)) {
			if (eAC.getLowerCaseValue().isEmpty()) {
				continue;
			}
			// found a contingent link A===>C
			final LabeledNode nC = g.getDest(eAC);
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "zLr: found contingent link " + eAC);
				}
			}
			for (final ALabel aleph : eXA.getUpperCaseValueMap().keySet()) {
				assert nC != null;
				if (!aleph.contains(nC.getALabel())) {
					continue;
				}
				final LabeledIntMap eXAValueMap = eXA.getUpperCaseValueMap().get(aleph);
				if (eXAValueMap == null) {
					continue;
				}
				for (final Label beta : eXAValueMap.keySet()) {
					final int v = eXA.getUpperCaseValue(beta, aleph);
					if (v == Constants.INT_NULL) {
						continue;
					}
					final LabeledLowerCaseValue ACLowerCaseValueObj = eAC.getLowerCaseValue();
					final Label alpha = ACLowerCaseValueObj.getLabel();
					final int x = ACLowerCaseValueObj.getValue();
					if (!beta.subsumes(alpha)) {
						continue;
					}
					final int newV = Math.max(v, -x);
					final int oldZ = (Debug.ON) ? eXA.getUpperCaseValue(beta, aleph) : -1;
					final String oldXA = (Debug.ON) ? eXA.toString() : "";

					final ALabel aleph1 = ALabel.clone(aleph);
					aleph1.remove(nC.getALabel());

					final boolean mergeStatus = (aleph1.isEmpty()) ? eXA.mergeLabeledValue(beta, newV)
					                                               : eXA.mergeUpperCaseValue(beta, aleph1, newV);
					if (mergeStatus) {
						ruleApplied = true;
						getCheckStatus().letterRemovalRuleCalls++;
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								LOG.log(Level.FINER, "Lr applied to edge " + oldXA + ":\n" + "Detail: " + nC + " <---" +
								                     lowerCaseValueAsString(ACLowerCaseValueObj.getNodeName(), x,
								                                            alpha) + "--- " + nA.getName() + " <---" +
								                     upperCaseValueAsString(aleph, v, beta) + "--- " + nX.getName() +
								                     "\nresult: " + nA.getName() + " <---" +
								                     upperCaseValueAsString(aleph1, newV, beta) + "--- " +
								                     nX.getName() + "; oldValue: " + Constants.formatInt(oldZ));
							}
						}
					}
				}
			}
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "Lr: end.");
			}
		}
		return ruleApplied;
	}

	/**
	 * IR Semantics {@inheritDoc}
	 */
	@SuppressWarnings("FinalMethod")
	@Override
	final boolean mainConditionForSkippingInR0qR0(final int w) {
		// Table 1 ICAPS2016 paper for IR semantics
		// w must be < 0.
		return w >= 0;
	}

	/*
	 * Create a copy of this.g merging, for each each of g, all ordinary and upper case values.
	 * Moreover, for each edge representing lower bound of a contingent, sets its ordinary value to the maximum of the contingent.
	 *
	 * @return the all-max projection of the graph g (CSTN graph) without edges connecting nodes with non consistent labels.
	 */
	// @SuppressWarnings("null")
	// TNGraph makeAllMaxProjection() {
	// TNGraph allMax = new TNGraph(this.g.getInternalLabeledValueMapImplementationClass(), this.g.getALabelAlphabet());
	// // clone all nodes
	// LabeledNode vNew;
	// for (final LabeledNode v : this.g.getVertices()) {
	// vNew = new LabeledNode(v);
	// allMax.addVertex(vNew);
	// }
	// allMax.setZ(allMax.getNode(this.g.getZ().getName()));
	//
	// // clone all edges giving the right new endpoints corresponding the old ones.
	// // we do not add edges connecting nodes in not consistent scenarios (such edges have only unknown labels).
	// CSTNUEdge eNew;
	// CSTNUEdgeSupplier<? extends LabeledIntMap> edgeFactory = allMax.getEdgeFactory();
	// for (final CSTNUEdge e : this.g.getEdges()) {
	// boolean toAdd = false;
	// LabeledNode s = this.g.getSource(e);
	// LabeledNode d = this.g.getDest(e);
	// String sName = s.getName();
	// String dName = d.getName();
	// Label sdLabel = s.getLabel().conjunction(d.getLabel());
	// if (sdLabel == null)
	// continue;
	// eNew = allMax.findEdge(sName, dName);
	// toAdd = (eNew == null);
	// if (toAdd)
	// eNew = edgeFactory.get(e); // to preserve the name
	// eNew.setConstraintType(ConstraintType.normal);
	// LabeledALabelIntTreeMap allValueMapE = e.getAllUpperCaseAndLabeledValuesMaps();
	// for (final ALabel alabel : allValueMapE.keySet()) {
	// eNew.mergeLabeledValue(allValueMapE.get(alabel));
	// }
	// if (e.isContingentEdge()) {
	// LabeledALabelIntTreeMap map = e.getUpperCaseValueMap();
	// if (map != null && map.size() > 0) {
	// CSTNUEdge eNewInverted = allMax.findEdge(dName, sName);
	// if (eNewInverted == null) {
	// // this is the lower bound
	// eNewInverted = edgeFactory.get(this.g.findEdge(d, s));
	// allMax.addEdge((CSTNUEdgePluggable) eNewInverted, dName, sName);
	// }
	// // eNewInverted.clearLowerCaseValue();
	// // eNewInverted.clearUpperCaseValues();
	// int ub = map.getValue(sdLabel, new ALabel(dName, this.g.getALabelAlphabet()));
	// if (ub != Constants.INT_NULL) {
	// eNewInverted.mergeLabeledValue(sdLabel, -ub);
	// }
	// }
	// }
	// if (toAdd)
	// allMax.addEdge((CSTNUEdgePluggable) eNew, sName, dName);
	// }
	// return allMax;
	// }

	// /**
	// * Determines the minimal distance between all pairs of vertexes of the given graph if the graph does not contain any negative cycles. If the graph
	// * contains a negative cycle, the method stops and returns false (the graph is anyway modified).
	// *
	// * @param g
	// * the graph
	// * @return true if the input graph does not contain any negative cycle, false otherwise.
	// */
	// boolean minimalDistanceGraphFast(final TNGraph<CSTNUEdge> g) {
	// final int n = g.getVertexCount();
	// final LabeledNode[] node = g.getVerticesArray();
	// LabeledNode iV, jV, kV;
	// CSTNUEdge ik, kj, ij;
	// int v;
	// Label l;
	// LabeledIntMap ijMap = null;
	// for (int k = 0; k < n; k++) {
	// kV = node[k];
	// for (int i = 0; i < n; i++) {
	// iV = node[i];
	// for (int j = 0; j < n; j++) {
	// if ((k == i) && (i == j)) {
	// continue;
	// }
	// jV = node[j];
	// Label nodeLabelConjunction = iV.getLabel().conjunction(jV.getLabel());
	// if (nodeLabelConjunction == null)
	// continue;
	//
	// ik = g.findEdge(iV, kV);
	// kj = g.findEdge(kV, jV);
	// if ((ik == null) || (kj == null)) {
	// continue;
	// }
	// ij = g.findEdge(iV, jV);
	//
	// final Set<Object2IntMap.Entry<Label>> ikMap = ik.labeledValueSet();
	// final Set<Object2IntMap.Entry<Label>> kjMap = kj.labeledValueSet();
	// if ((k == i) || (k == j)) {
	// ijMap = labeledIntMapSupplier.create(ij.getLabeledValueMap());// this is necessary to avoid concurrent access to the same map by the
	// // iterator.
	// } else {
	// ijMap = null;
	// }
	// for (final Object2IntMap.Entry<Label> ikL : ikMap) {
	// for (final Object2IntMap.Entry<Label> kjL : kjMap) {
	// l = ikL.getKey().conjunction(kjL.getKey());
	// if (l == null) {
	// continue;
	// }
	// l = l.conjunction(nodeLabelConjunction);// It is necessary to propagate with node labels!
	// if (l == null) {
	// continue;
	// }
	// if (ij == null) {
	// ij = CSTN.MAKE_NEW_EDGE(node[i].getName() + "_" + node[j].getName(), CSTNUEdge.ConstraintType.derived, g);
	// g.addEdge(ij, iV, jV);
	// }
	// v = ikL.getValue() + kjL.getValue();
	// if (ijMap != null) {
	// ijMap.put(l, v);
	// } else {
	// ij.mergeLabeledValue(l, v);
	// }
	// if (i == j) // check negative cycles
	// if (v < 0 || ij.getMinValue() < 0) {
	// LOG.log(Level.FINER, "Found a negative cycle on node " + iV.getName() + ": "
	// + ((ijMap != null) ? ijMap : ij) + "\nIn details, ik=" + ik + ", kj="
	// + kj + ", v=" + v + ", ij.getValue(" + l + ")=" + ij.getValue(l));
	// return false;
	// }
	// }
	// }
	// if (ijMap != null) {
	// ij.setLabeledValue(ijMap);
	// }
	// }
	// }
	// }
	// return true;
	// }

	// /**
	// * @param e contingent edge. It is assumed that it is the edge between that firstContingentNode to Z.
	// * @param firstContingentNode the source node of e.
	// * @return
	// */
	// private boolean checkMutualWait(CSTNUEdge e, LabeledNode firstContingentNode) {
	//
	// ALabel firstContingentNodeName = firstContingentNode.getAlabel();
	// int firstValueInE = e.getUpperCaseValue(Label.emptyLabel, firstContingentNodeName), //it will be a cycle
	// secondValueInE= 0;
	// for (ALabel secondContingentName : e.getUpperCaseValueMap().keySet()) {
	// if (secondContingentName.size() > 1)
	// continue;
	//
	// secondValueInE = e.getUpperCaseValue(Label.emptyLabel, secondContingentName);
	//
	// LabeledNode secondContingentNode = this.g.getNode(secondContingentName.toString());
	// CSTNUEdge e1 = this.g.findEdge(secondContingentNode, Z);
	//
	// int secondValueInE1 = e1.getUpperCaseValue(Label.emptyLabel, secondContingentName);// it must be present!
	// int firstValueInE1 = Constants.INT_NULL;
	// for (ALabel contingentName : e1.getUpperCaseValueMap().keySet()) {
	// if (!contingentName.equals(firstContingentNodeName))
	// continue;
	// firstValueInE1 = e1.getUpperCaseValue(Label.emptyLabel, firstContingentNodeName);
	// }
	// if (firstValueInE1 == Constants.INT_NULL)
	// return false;
	//
	// if (firstValueInE >= secondValueInE && secondValueInE1 >= firstValueInE1) {
	// // it is probable that it is a negative loop
	//
	// }
	// }
	//
	// return false;
	// }

	/**
	 * Labeled LetterRemoval (zLr) and (zLr*)<br>
	 *
	 * <pre>
	 * Y ---(v,Cℵ,β)---&gt; Z &lt;---(w,ℵ1,α)--- A ---(x,c,⊡)---&gt; C
	 *
	 * adds
	 *
	 * Y ---(m,ℵℵ1,β*α)---&gt; Z
	 *
	 * m = max(v, w-x)
	 * </pre>
	 * <p>
	 * zLr*
	 *
	 * <pre>
	 * C &lt;---(x,c,⊡)--- Y ---(v,Cℵ,β)---&gt; Z
	 *
	 * adds
	 *
	 * Y ---(v,ℵ,β)---&gt; Z
	 *
	 * if v &lt; 0
	 * </pre>
	 *
	 * @param nY  node
	 * @param eYZ edge
	 *
	 * @return true if the reduction has been applied.
	 */
	boolean zLabeledLetterRemovalRule(final LabeledNode nY, final CSTNUEdge eYZ) {
		boolean ruleApplied = false;

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "zLR: start.");
			}
		}
		final LabeledNode Z = g.getZ();
		for (final ALabel aleph : eYZ.getUpperCaseValueMap().keySet()) {
			if (aleph.isEmpty()) {
				continue;
			}
			final LabeledIntMap YZvaluesMap = eYZ.getUpperCaseValueMap().get(aleph);
			if (YZvaluesMap == null) {
				continue;
			}
			for (final it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Label> upperCaseEntryOfYA : YZvaluesMap.entrySet()) {// entrySet read-only
				final Label beta = upperCaseEntryOfYA.getKey();
				final int v = upperCaseEntryOfYA.getIntValue();

				for (final ALetter nodeLetter : aleph) {
					final LabeledNode nC = g.getNode(nodeLetter.name);
					if (nY == nC) // Z is the activation time point!
					{
						continue;
					}
					final LabeledNode nA = getActivationNode(nC);
					if (nA == Z) {
						continue;
					}

					if (nA == nY) {
						// zLr* special case
						if (v >= 0) {
							continue;
						}
						final ALabel alephAleph1 = ALabel.clone(aleph);
						alephAleph1.remove(nodeLetter);

						final int oldValue = (Debug.ON) ? eYZ.getUpperCaseValue(beta, alephAleph1) : -1;
						final String oldYZ = (Debug.ON) ? eYZ.toString() : "";

						final boolean mergeStatus = (alephAleph1.isEmpty()) ? eYZ.mergeLabeledValue(beta, v)
						                                                    : eYZ.mergeUpperCaseValue(beta, alephAleph1,
						                                                                              v);

						if (mergeStatus) {
							ruleApplied = true;
							getCheckStatus().letterRemovalRuleCalls++;
							if (Debug.ON) {
								if (LOG.isLoggable(Level.FINER)) {
									assert nC != null;
									LOG.log(Level.FINER,
									        "zLR* applied to edge " + oldYZ + ":\n" + "Detail: " + "Z <---" +
									        upperCaseValueAsString(aleph, v, beta) + "--- " + nY.getName() + "---(" +
									        nC.getALabel().toLowerCase() + ",...," + Label.emptyLabel + ")---> " +
									        nodeLetter + "\nresult: " + "Z <---" +
									        upperCaseValueAsString(alephAleph1, v, beta) + "--- " + nY.getName() +
									        "; oldValue: " + Constants.formatInt(oldValue));
								}
							}
						}
						continue;
					}
					final CSTNUEdge AC = getLowerContingentLink(nC);

					final LabeledLowerCaseValue lowerCaseEntry = AC.getLowerCaseValue();
					if (lowerCaseEntry.isEmpty()) {
						continue;
					}
					// Label l = lowerCaseEntry.getLabel();IT SHOULD BE empty!
					final int x = lowerCaseEntry.getValue();

					final CSTNUEdge AZ = g.findEdge(nA, Z);

					assert AZ != null;
					for (final ALabel aleph1 : AZ.getAllUpperCaseAndLabeledValuesMaps().keySet()) {
						if (aleph1.contains(nodeLetter)) {
							continue;
						}
						final LabeledIntMap AZAlephMap = AZ.getAllUpperCaseAndLabeledValuesMaps().get(aleph1);
						if (AZAlephMap == null) {
							continue;
						}
						for (final Entry<Label> entryAZ : AZAlephMap.entrySet()) {// entrySet read-only
							final Label alpha = entryAZ.getKey();
							final int w = entryAZ.getIntValue();

							// if (!alpha.subsumes(l));l must be empty!
							// continue;// rule condition

							final int newV = Math.max(v, w - x);

							final ALabel alephAleph1 = aleph.conjunction(aleph1);
							assert alephAleph1 != null;
							alephAleph1.remove(nodeLetter);

							final Label alphaBeta = alpha.conjunctionExtended(beta);

							final int oldValue = (Debug.ON) ? eYZ.getUpperCaseValue(alphaBeta, alephAleph1) : -1;
							final String oldYZ = (Debug.ON) ? eYZ.toString() : "";

							final boolean mergeStatus = (alephAleph1.isEmpty()) ? eYZ.mergeLabeledValue(alphaBeta, newV)
							                                                    : eYZ.mergeUpperCaseValue(alphaBeta,
							                                                                              alephAleph1,
							                                                                              newV);

							if (mergeStatus) {
								ruleApplied = true;
								getCheckStatus().letterRemovalRuleCalls++;
								if (Debug.ON) {
									if (LOG.isLoggable(Level.FINER)) {
										assert nC != null;
										LOG.log(Level.FINER,
										        "zLR applied to edge " + oldYZ + ":\n" + "Detail: " + nY.getName() +
										        "---" + upperCaseValueAsString(aleph, v, beta) + "---> Z <---" +
										        upperCaseValueAsString(aleph1, w, alpha) + "--- " + nA.getName() +
										        "---" + lowerCaseValueAsString(nC.getALabel(), x, Label.emptyLabel) +
										        "---> " + nodeLetter + "\nresult: " + nY.getName() + "---" +
										        upperCaseValueAsString(alephAleph1, newV, alphaBeta) + "---> Z" +
										        "; oldValue: " + Constants.formatInt(oldValue));
									}
								}
							}
						}
					}
				}
			}
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "zLR: end.");
			}
		}
		return ruleApplied;
	}

	/**
	 * Simple class to represent the status of the checking algorithm during an execution.<br>
	 * {@code controllability = super.consistency}.
	 *
	 * @author Roberto Posenato
	 */
	public static class CSTNUCheckStatus extends CSTNCheckStatus {

		// controllability = super.consistency!
		/**
		 * Counters about the # of application of different rules.
		 */
		public int crossCaseRuleCalls;
		/**
		 * Counters about the # of application of different rules.
		 */
		public int letterRemovalRuleCalls;
		/**
		 * Counters about the # of application of different rules.
		 */
		public int lowerCaseRuleCalls;
		/**
		 * Counters about the # of application of different rules.
		 */
		public int zExclamationRuleCalls;

		/**
		 * @return the value of controllability
		 */
		public boolean isControllable() {
			return consistency;
		}

		@Override
		public void reset() {
			super.reset();
			zExclamationRuleCalls = 0;
			lowerCaseRuleCalls = 0;
			crossCaseRuleCalls = 0;
			letterRemovalRuleCalls = 0;
		}

		/**
		 * Set the controllability value!
		 *
		 * @param controllability true if it is controllable
		 */
		public void setControllability(boolean controllability) {
			consistency = controllability;
		}

		@Override
		public String toString() {
			return ("The check is" + (finished ? "" : " NOT") + " finished after " + cycles + " cycle(s).\n" +
			        ((finished) ? "The controllability check has determined that given network is" +
			                      (consistency ? " " : " NOT ") + "dynamic controllable.\n" : "") +
			        ((!consistency && negativeLoopNode != null) ? "The negative loop is on node " + negativeLoopNode +
			                                                      "\n" : "") +
			        "Some statistics:\nRule R0 has been applied " + r0calls + " times.\n" +
			        "Rule R3 has been applied " + r3calls + " times.\n" +
			        "Rule Labeled Propagation (zLp/Nc/Uc) has been applied " + labeledValuePropagationCalls +
			        " times.\n" + "Rule Labeled z! has been applied " + zExclamationRuleCalls + " times.\n" +
			        "Rule Labeled Lower Case (zLc) has been applied " + lowerCaseRuleCalls + " times.\n" +
			        "Rule Labeled Cross-Lower Case (Cc) has been applied " + crossCaseRuleCalls + " times.\n" +
			        "Rule Labeled Letter Removal (zLR/zLR*) has been applied " + letterRemovalRuleCalls + " times.\n"
			        // + "Negative qLoops: " + this.qAllNegLoop + "\n"
			        // + "Negative qLoops with positive edge: " + this.qSemiNegLoop + "\n"
			        + "The global execution time has been " + executionTimeNS + " ns (~" + (executionTimeNS / 1E9) +
			        " s.)");
		}
	}

}
