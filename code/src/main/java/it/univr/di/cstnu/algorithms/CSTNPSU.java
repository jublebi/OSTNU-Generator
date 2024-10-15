// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.algorithms;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.*;
import it.univr.di.Debug;
import it.univr.di.cstnu.algorithms.CSTNU.CSTNUCheckStatus;
import it.univr.di.cstnu.graph.*;
import it.univr.di.cstnu.graph.Edge.ConstraintType;
import it.univr.di.cstnu.visualization.CSTNUStaticLayout;
import it.univr.di.labeledvalue.*;
import it.univr.di.labeledvalue.ALabelAlphabet.ALetter;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents and checks streamlined Conditional Simple Temporal Network with Partial Shrinkable Uncertainty (CSTNPSU).
 * <br>
 * Flexible Temporal Network with Uncertainty (FTNU) model is the CSTNPSU one with an improved DC checking algorithm.
 * <br>
 * Hence, this class implements the FTNU DC checking algorithm.
 * <p>
 * In CSTNPSU model, contingent link are guarded link. The input network <b>must</b> have guarded links defined
 * explicitly.
 * <br>
 * Upper bound edge must contain the lower guard as lower case contingent value and the upper bound, while the lower
 * bound edge must contain the upper guard as upper case contingent value and the lower bound.
 * </p>
 * <p>
 * If {@link #propagationOnlyToZ} is true, the DC checking algorithm does not minimize the constraint bounds saving some
 * computational time. {@link #getPrototypalLink()} starts executing a DC checking that minimizes the constraint bounds
 * and, then, builds the contingency graph for determining the PrototypalLink. So, it requires more time.
 * </p>
 *
 * @author Roberto Posenato
 * @version $Rev: 850 $
 */
@SuppressWarnings("UnusedReturnValue")
public class CSTNPSU extends AbstractCSTN<CSTNPSUEdge> {
	/**
	 * Represents a prototypal link.
	 *
	 * @author posenato
	 */
	public static class PrototypalLink {
		private final String startingNodeName;
		private final String endingNodeName;
		private final int contingency;
		private final int upperGuard;
		private int lowerBound;
		private int upperBound;
		private int lowerGuard;//no final because it must be modified by configureSubNetworks

		/**
		 * Creates an immutable prototypal link.
		 *
		 * @param startNodeName  the starting node name
		 * @param lBound         lower bound
		 * @param lGuard         lower guard
		 * @param uGuard         upper guard
		 * @param uBound         upper bound
		 * @param newContingency contingency of the link
		 * @param endNodeName    the ending node name
		 */
		public PrototypalLink(String startNodeName, int lBound, int lGuard, int uGuard, int uBound, int newContingency,
		                      String endNodeName) {
			if (lBound < 0 || lGuard < 0 || uGuard < 0 || uBound < 0 || newContingency < 0 || lBound > uBound ||
			    lBound > lGuard || uGuard > uBound) {
				throw new IllegalArgumentException(
					"Given values are not correct for a prototypal link: " + "[[" + Constants.formatInt(lBound) + ", " +
					Constants.formatInt(lGuard) + "][" + Constants.formatInt(uGuard) + ", " +
					Constants.formatInt(uBound) + "]]" + Constants.CONTINGENCY_SYMBOL +
					Constants.formatInt(newContingency));
			}
			this.startingNodeName = startNodeName;
			this.lowerBound = lBound;
			this.lowerGuard = lGuard;
			this.upperGuard = uGuard;
			this.upperBound = uBound;
			this.contingency = newContingency;
			this.endingNodeName = endNodeName;
		}

		/**
		 * Copy constructor
		 *
		 * @param l prototypal link to clone. It cannot be null.
		 */
		public PrototypalLink(PrototypalLink l) {
			if (l == null) {
				throw new NullPointerException("Given prototypal link is null");
			}
			startingNodeName = l.startingNodeName;
			lowerBound = l.lowerBound;
			lowerGuard = l.lowerGuard;
			upperGuard = l.upperGuard;
			upperBound = l.upperBound;
			contingency = l.contingency;
			endingNodeName = l.endingNodeName;
		}


		/**
		 * @param o the prototypal link to compare
		 *
		 * @return true if the input prototypal has the same values of this. false otherwise.
		 */
		public boolean equals(Object o) {
			if (!(o instanceof PrototypalLink l)) {
				return false;
			}

			return startingNodeName.equals(l.startingNodeName) && lowerBound == l.lowerBound &&
			       lowerGuard == l.lowerGuard && upperGuard == l.upperGuard && upperBound == l.upperBound &&
			       contingency == l.contingency && endingNodeName.equals(l.endingNodeName);
		}

		/**
		 * @return the contingency
		 */
		public int getContingency() {
			return contingency;
		}

		/**
		 * @return the ending node name
		 */
		public String getEndingNodeName() {
			return ((endingNodeName != null) ? endingNodeName : "");
		}

		/**
		 * @return the lowerBound
		 */
		public int getLowerBound() {
			return lowerBound;
		}

		/**
		 * @return the lowerGuard
		 */
		public int getLowerGuard() {
			return lowerGuard;
		}

		/**
		 * @return the starting node name
		 */
		public String getStartingNodeName() {
			return ((startingNodeName != null) ? startingNodeName : "");
		}

		/**
		 * @return the upperBound
		 */
		public int getUpperBound() {
			return upperBound;
		}

		/**
		 * @return the upperGuard
		 */
		public int getUpperGuard() {
			return upperGuard;
		}

		/**
		 * @return the hashcode associated to this prototypal link
		 */
		public int hashCode() {
			return startingNodeName.hashCode() + lowerBound + lowerGuard + upperGuard + upperBound + contingency +
			       endingNodeName.hashCode();
		}

		/**
		 * @return the string representation
		 */
		@Override
		public String toString() {
			return '(' + getStartingNodeName() + ",[[" + Constants.formatInt(lowerBound) + ", " +
			       Constants.formatInt(lowerGuard) + "][" + Constants.formatInt(upperGuard) + ", " +
			       Constants.formatInt(upperBound) + "]]" + Constants.CONTINGENCY_SYMBOL +
			       Constants.formatInt(contingency) + ',' + getEndingNodeName() + ')';
		}
	}

	/**
	 * Version of the class
	 */
	// static final String VERSIONandDATE = "Version 1.0 - Feb, 21 2017";
	// static final String VERSIONandDATE = "Version 2.0 - Feb, 13 2020";
	// static final String VERSIONandDATE = "Version 2.2 - Nov, 02 2022";// added the getPrototypalLink() method
	// static final String VERSIONandDATE = "Version 2.3 - Jun, 04 2023";// improved the management of propagationOnlyToZ
	static final String VERSIONandDATE = "Version 2.4 - Jun, 20 2023";// added management prototypal link
	/**
	 * logger
	 */
	static private final Logger LOG = Logger.getLogger(CSTNPSU.class.getName());

	/**
	 * <pre>
	 * A ---(u, alpha)---&gt; C ---(v, beta)---&gt; A
	 *
	 * If u+v &lt; 0, raise non controllability status of the guarded link.
	 * </pre>
	 *
	 * @param nA           activation node
	 * @param nC           contingent node
	 * @param eAC          edge
	 * @param eCA          edge
	 * @param checkStatus1 status of the checking
	 */
	static void checkGuardedLinkBounds(LabeledNode nA, LabeledNode nC, CSTNPSUEdge eAC, CSTNPSUEdge eCA,
	                                   CSTNCheckStatus checkStatus1) {
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "checkBoundGuarded: start.");
			}
		}
		String logMsg = "";

		for (final Entry<Label> entryAC : eAC.getLabeledValueSet()) {
			final int u = entryAC.getIntValue();
			for (final Entry<Label> entryCA : eCA.getLabeledValueSet()) {
				final int v = entryCA.getIntValue();
				final int sum = Constants.sumWithOverflowCheck(v, u);
				if (Debug.ON) {
					final Label alpha = entryAC.getKey();
					final Label beta = entryCA.getKey();
					final Label alphaBeta = alpha.conjunction(beta);
					logMsg =
						"CSTNPSU checkBoundGuarded:\n" + "Detail: " + nA.getName() + " ---" + pairAsString(alpha, u) +
						"---> " + nC.getName() + " ---" + pairAsString(beta, v) + "---> " + nA.getName() +
						"\nresult: " + nC.getName() + " ---" + pairAsString(alphaBeta, sum) + "---> " + nC.getName();
				}
				final boolean found = sum < 0;

				if (found) {
					if (Debug.ON) {
						if (LOG.isLoggable(Level.INFO)) {
							LOG.log(Level.INFO, logMsg);
						}
					}
					checkStatus1.consistency = false;
					checkStatus1.finished = true;
					checkStatus1.negativeLoopNode = nC;
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							LOG.log(Level.FINER, "checkBoundGuarded: end.");
						}
					}
					return;
				}
			}
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "checkBoundGuarded: end.");
			}
		}
	}

	/**
	 * Returns the labeled path contingency span of node with name {@code nodeName}.
	 * <p>
	 * {@code nodeName} has to be present in the contingencyGraph. Also, node {@code Z} has to be present.
	 * <p>
	 * Contingency graph can be determined by {@link #getContingencyGraph()}.
	 *
	 * @param nodeName         the name of the wanted node
	 * @param contingencyGraph the contingency graph of this network
	 *
	 * @return the labeled path contingency span of node 'E', {@link Constants#INT_NULL} if node 'E' does not exit.
	 */
	public static int getMaxPathContingencySpanInContingencyGraph(String nodeName, TNGraph<CSTNEdge> contingencyGraph) {
		if (Debug.ON) {
			LOG.finer("Starting getMaxDistanceInContingencyGraph.");
		}

		if (contingencyGraph == null) {
			return Constants.INT_NULL;
		}
		final LabeledNode omega = contingencyGraph.getNode(nodeName);
		if (omega == null) {
			return Constants.INT_NULL;
		}
		final LabeledNode Z = contingencyGraph.getNode(ZERO_NODE_NAME);

		// This method implements an extension of the BellamFord algorithm to find the labeled distances of all nodes from node Z.
		// The extension consists in the fact that for each node there are different distances according to the different scenarios.
		final Object2ObjectMap<LabeledNode, LabeledIntMap> distancesFromZ = new Object2ObjectOpenHashMap<>();

		// initialization
		// All distances from Z are infinite.
		for (final LabeledNode node : contingencyGraph.getVertices()) {
			final LabeledIntMap map =
				(new LabeledIntMapSupplier<>(LabeledIntMapSupplier.DEFAULT_LABELEDINTMAP_CLASS).get());
			map.put(Label.emptyLabel, Constants.INT_POS_INFINITE);
			distancesFromZ.put(node, map);
		}
		distancesFromZ.get(Z).put(Label.emptyLabel, 0);
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("distancesFromZ: " + distancesFromZ);
			}
		}

		final int n = contingencyGraph.getVertexCount();
		// n-1 cycles for determining the distances from Z
		for (int i = 1; i < n; i++) {
			for (final CSTNEdge edge : contingencyGraph.getEdges()) {
				final LabeledNode source = contingencyGraph.getSource(edge);
				final LabeledNode dest = contingencyGraph.getDest(edge);
				for (final Entry<Label> edgeEntry : edge.getLabeledValueSet()) {
					final Label alpha = edgeEntry.getKey();
					for (final Entry<Label> nodeEntry : distancesFromZ.get(source).entrySet()) {
						final Label beta = edgeEntry.getKey();
						final Label gamma = alpha.conjunction(beta);
						if (gamma == null) {
							continue;
						}
						final int a = nodeEntry.getIntValue();
						if (a == Constants.INT_POS_INFINITE) {
							continue;
						}
						final int b = edgeEntry.getIntValue();
						if (b == Constants.INT_POS_INFINITE) {
							continue;
						}

						final int newD = Constants.sumWithOverflowCheck(a, b);
						final boolean added = distancesFromZ.get(dest).put(gamma, newD);
						if (added) {
							if (Debug.ON) {
								if (LOG.isLoggable(Level.FINER)) {
									LOG.finer("Added to " + dest + " distances the value " + pairAsString(gamma, newD));
								}
							}
						}
					}
				}

			}
		}
		final int contingency = -distancesFromZ.get(omega).getMinValue();
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("The contingency is " + contingency);
			}
		}
		return contingency;
	}

	/**
	 * Reads a CSTNPSU file and determines its prototypal link.
	 *
	 * @param args an array of {@link String} objects.
	 *
	 * @throws IOException                  if any.
	 * @throws ParserConfigurationException if any.
	 * @throws SAXException                 if any.
	 */
	public static void main(final String[] args) throws IOException, ParserConfigurationException, SAXException {
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "Start...");
			}
		}

		final CSTNPSU cstnpsu = new CSTNPSU();

		if (!cstnpsu.manageParameters(args)) {
			return;
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "Parameters ok!");
			}
		}
		if (cstnpsu.versionReq) {
			System.out.println(
				"CSTNPSU " + VERSIONandDATE + "\nSPDX-License-Identifier: LGPL-3.0-or-later, Roberto Posenato.\n");
			return;
		}

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "Loading graph...");
			}
		}
		final TNGraphMLReader<CSTNPSUEdge> graphMLReader = new TNGraphMLReader<>();
		cstnpsu.setG(graphMLReader.readGraph(cstnpsu.fInput, EdgeSupplier.DEFAULT_CSTNPSU_EDGE_CLASS));
		cstnpsu.g.setInputFile(cstnpsu.fInput);

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "TNGraph loaded!\nDC Checking...");
			}
		}
		System.out.println("Checking started...");
		final CSTNUCheckStatus status;
		final PrototypalLink prototypalLink;
		try {
			prototypalLink = cstnpsu.getPrototypalLink();
			status = cstnpsu.getCheckStatus();
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
				System.out.println("The prototypal link is " + prototypalLink);
			} else {
				System.out.println("The given network is NOT Dynamic controllable!");
			}
			//			System.out.println("Final graph: " + cstnpsu.g.toString());

			System.out.println("Details: " + status);
		} else {
			System.out.println("Checking has not been finished!");
			System.out.println("Details: " + status);
		}

		if (cstnpsu.fOutput != null) {
			final TNGraphMLWriter graphWriter = new TNGraphMLWriter(new CSTNUStaticLayout<>(cstnpsu.g));
			graphWriter.save(cstnpsu.g, cstnpsu.fOutput);
		}
	}

	/**
	 * Updates upper bound of a guarded range.
	 *
	 * <pre>
	 * nA ---(u, ℵ, alpha)---&gt; Z ---(v, ד, beta)---&gt; nC
	 * adds
	 * nA ---(u+v, ⊡)---&gt; nC
	 *
	 * ℵ and/or ד can be empty and cannot contain common names or nC.
	 * Alpha and beta must be consistent or, if one has unknown literals, the other must be empty.
	 * </pre>
	 *
	 * @param nA  activation node
	 * @param nZ  zero node
	 * @param nC  contingent node
	 * @param eAZ edge
	 * @param eZC edge
	 * @param eAC edge
	 * @param eCA edge
	 *
	 * @return true if the rule has been applied.
	 */
	static boolean rG8(LabeledNode nA, LabeledNode nZ, LabeledNode nC, CSTNPSUEdge eAZ, CSTNPSUEdge eZC,
	                   CSTNPSUEdge eAC, CSTNPSUEdge eCA) {
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "rG8: start.");
			}
		}
		String logMsg = "";
		boolean ruleApplied = false;
		final int lowerGuard = eAC.getLowerCaseValue(Label.emptyLabel, nC.getALabel());
		final int upperGuard = eCA.getUpperCaseValue(Label.emptyLabel, nC.getALabel());
		if (lowerGuard > -upperGuard) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Guarded link " + nA.getName() + "--" + nC.getName() +
					                     " is a partial shrinkable guarded link. Rule does not apply.");
				}
			}
			return false;
		}
		final LabeledALabelIntTreeMap eZCMaps = eZC.getAllLowerCaseAndLabeledValuesMaps();
		final LabeledALabelIntTreeMap eAZMaps = eAZ.getAllUpperCaseAndLabeledValuesMaps();
		for (final ALabel dalet : eZCMaps.keySet()) {
			if (dalet.contains(nC.getALabel())) {
				continue;
			}
			for (final Entry<Label> entryCZ : eZCMaps.get(dalet).entrySet()) {
				final Label beta = entryCZ.getKey();
				final int v = entryCZ.getIntValue();
				for (final ALabel aleph : eAZMaps.keySet()) {
					if (aleph.contains(nC.getALabel()) || !aleph.intersect(dalet).isEmpty()) {
						continue;
					}
					for (final Entry<Label> entryZA : eAZMaps.get(aleph).entrySet()) {
						final Label alpha = entryZA.getKey();
						final int u = entryZA.getIntValue();
						final Label alphaBeta = alpha.conjunction(beta);
						if (alphaBeta == null && (!alpha.isEmpty() && !beta.isEmpty())) {
							continue;
						}
						final int sum = Constants.sumWithOverflowCheck(v, u);
						if (Debug.ON) {
							final String oldAC = eAC.toString();
							logMsg = "CSTNPSU rG8 on " + oldAC + ":\n" + "Detail: " + nA.getName() + " ---" +
							         CSTNU.upperCaseValueAsString(aleph, u, alpha) + "---> " + nZ.getName() + " ---" +
							         CSTNU.lowerCaseValueAsString(dalet, v, beta) + "---> " + nC.getName() +
							         "\nresult: " + nA.getName() + " ---" + pairAsString(Label.emptyLabel, sum) +
							         "---> " + nC.getName();
						}

						final boolean added = eAC.mergeLabeledValue(Label.emptyLabel, sum);

						if (added) {
							ruleApplied = true;
							if (Debug.ON) {
								LOG.log(Level.FINER, logMsg);
							}
						}
					}
				}
			}
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "rG8: end.");
			}
		}
		return ruleApplied;
	}

	/**
	 * Updates lower bound of a guarded range.
	 *
	 * <pre>
	 * nC ---(u, ℵ, alpha)---&gt; Z ---(v, ד, beta)---&gt; nA
	 * adds
	 * nC ---(u+v, alphaBeta)---&gt; nA
	 *
	 * ℵ and/or ד can be empty and cannot contain common names or nC.
	 * Alpha and beta must be consistent or, if one has unknown literals, the other must be empty.
	 * </pre>
	 *
	 * @param nC  contingent node
	 * @param nZ  zero node a
	 * @param nA  activation node
	 * @param eCZ edge
	 * @param eZA edge
	 * @param eAC edge
	 * @param eCA edge
	 *
	 * @return true if the rule has been applied.
	 */
	static boolean rG9(LabeledNode nC, LabeledNode nZ, LabeledNode nA, CSTNPSUEdge eCZ, CSTNPSUEdge eZA,
	                   CSTNPSUEdge eAC, CSTNPSUEdge eCA) {
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "rG9: start.");
			}
		}
		String logMsg;
		boolean ruleApplied = false;
		final int lowerGuard = eAC.getLowerCaseValue(Label.emptyLabel, nC.getALabel());
		final int upperGuard = eCA.getUpperCaseValue(Label.emptyLabel, nC.getALabel());
		if (lowerGuard > -upperGuard) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Guarded link " + nA.getName() + "--" + nC.getName() +
					                     " is a partial shrinkable guarded link. Rule does not apply.");
				}
			}
			return false;
		}

		final LabeledALabelIntTreeMap eCZMaps = eCZ.getAllUpperCaseAndLabeledValuesMaps();
		final LabeledALabelIntTreeMap eZAMaps = eZA.getAllLowerCaseAndLabeledValuesMaps();
		for (final ALabel aleph : eCZMaps.keySet()) {
			if (aleph.contains(nC.getALabel())) {
				continue;
			}
			for (final Entry<Label> entryCZ : eCZMaps.get(aleph).entrySet()) {
				final Label alpha = entryCZ.getKey();
				final int u = entryCZ.getIntValue();
				for (final ALabel dalet : eZAMaps.keySet()) {
					if (dalet.contains(nC.getALabel()) || !aleph.intersect(dalet).isEmpty()) {
						continue;
					}
					for (final Entry<Label> entryZA : eZAMaps.get(dalet).entrySet()) {
						final Label beta = entryZA.getKey();
						final int v = entryZA.getIntValue();
						final Label alphaBeta = alpha.conjunction(beta);
						if (alphaBeta == null && (!alpha.isEmpty() && !beta.isEmpty())) {
							continue;
						}
						final int sum = Constants.sumWithOverflowCheck(v, u);
						if (Debug.ON) {
							final String oldCA = eCA.toString();
							logMsg = "CSTNPSU rG9 on " + oldCA + ":\n" + "Detail: " + nC.getName() + " ---" +
							         CSTNU.upperCaseValueAsString(aleph, u, alpha) + "---> " + nZ.getName() + " ---" +
							         CSTNU.lowerCaseValueAsString(dalet, v, beta) + "---> " + nA.getName() +
							         "\nresult: " + nC.getName() + " ---" + pairAsString(Label.emptyLabel, sum) +
							         "---> " + nA.getName();
						}

						final boolean added = eCA.mergeLabeledValue(Label.emptyLabel, sum);

						if (added) {
							ruleApplied = true;
							if (Debug.ON) {
								LOG.log(Level.FINER, logMsg);
							}
						}
					}
				}
			}
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "rG9: end.");
			}
		}
		return ruleApplied;
	}

	/**
	 * Applies the propagation rule only on labeled values in the triangle
	 *
	 * <pre>
	 * nX ---(u, alpha)---> Z ---(v, beta)---> nY
	 * adds
	 * nX ---(u+v, alphaBeta)---> nY
	 * </pre>
	 *
	 * @param nX  source node
	 * @param nZ  Z node
	 * @param nY  destination node
	 * @param eXZ edge between nX and Z
	 * @param eZY edge between Z and Y
	 * @param eXY edge to be update between X and Y
	 *
	 * @return true if the new constraint has been added.
	 */
	public static boolean rUpdateBoundsUsingZ(LabeledNode nX, LabeledNode nZ, LabeledNode nY, CSTNPSUEdge eXZ,
	                                          CSTNPSUEdge eZY, CSTNPSUEdge eXY) {
		boolean added = false;
		String oldXY;
		if (Debug.ON) {
			oldXY = eXY.toString();
		}
		for (final Entry<Label> entryAZ : eXZ.getLabeledValueSet()) {
			final Label alpha = entryAZ.getKey();
			final int u = entryAZ.getIntValue();
			for (final Entry<Label> entryZY : eZY.getLabeledValueSet()) {
				final Label beta = entryZY.getKey();
				final int v = entryZY.getIntValue();

				final Label alphaBeta = alpha.conjunction(beta);
				if (alphaBeta == null) {
					continue;
				}
				final int sum = Constants.sumWithOverflowCheck(v, u);

				added = eXY.mergeLabeledValue(alphaBeta, sum);
				if (Debug.ON) {
					if (added) {// don't put in && with Debug.ON because it is considered dead code.
						if (LOG.isLoggable(Level.FINER)) {
							final String logMsg =
								"CSTNPSU update bounds of " + oldXY + ":\n" + "Detail: " + nX.getName() + " ---" +
								pairAsString(alpha, u) + "---> " + nZ.getName() + " ---" + pairAsString(beta, v) +
								"---> " + nY.getName() + "\nresult: " + nX.getName() + " ---" +
								pairAsString(alphaBeta, sum) + "---> " + nY.getName();
							LOG.log(Level.FINER, logMsg);
						}
					}
				}
			}
		}
		return added;
	}

	/**
	 * Utility map that returns the activation time point (node) associated to a contingent link given the contingent
	 * time point, i.e., contingent link A===&gt;C determines the entry (C,A) in this map.
	 */
	Object2ObjectMap<LabeledNode, LabeledNode> activationNode;
	/**
	 * Utility map that return the edge containing the lower case constraint of a contingent link given the contingent
	 * time point.
	 */
	Object2ObjectMap<LabeledNode, CSTNPSUEdge> lowerContingentLink;
	/**
	 * Cache of the prototypal link
	 */
	PrototypalLink prototypalLink;

	/**
	 * Helper constructor for CSTNPSU.
	 * <br>
	 * This constructor is useful for making easier the use of this class in environment like Node.js-Java
	 *
	 * @param graphXML the TNGraph to check in GraphML format
	 *
	 * @throws IOException                  if any error occurs during the graphXML reading
	 * @throws ParserConfigurationException if graphXML contains character that cannot be parsed
	 * @throws SAXException                 if graphXML is not valid
	 */
	public CSTNPSU(String graphXML) throws IOException, ParserConfigurationException, SAXException {
		this();
		setG((new TNGraphMLReader<CSTNPSUEdge>()).readGraph(graphXML, EdgeSupplier.DEFAULT_CSTNPSU_EDGE_CLASS));
	}

	/**
	 * Constructor for CSTNPSU
	 *
	 * @param graph        TNGraph to check
	 * @param givenTimeOut timeout for the check in seconds
	 */
	public CSTNPSU(TNGraph<CSTNPSUEdge> graph, int givenTimeOut) {
		this(graph);
		timeOut = givenTimeOut;
	}

	/**
	 * Constructor for CSTNPSU
	 *
	 * @param graph TNGraph to check
	 */
	public CSTNPSU(TNGraph<CSTNPSUEdge> graph) {
		this();
		setG(graph);
	}

	/**
	 * Default constructor, package use only!
	 */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "UwF", justification = "It says that prototypalLink " +
	                                                                                   "is never written. False " +
	                                                                                   "positive.")
	CSTNPSU() {
		checkStatus = new CSTNUCheckStatus();
		activationNode = new Object2ObjectOpenHashMap<>();
		lowerContingentLink = new Object2ObjectOpenHashMap<>();
		reactionTime = 0;// IR semantics
		propagationOnlyToZ = false;
		prototypalLink = null;
	}

	/**
	 * Checks if current network can be embedded with subnetworks represented by {@code subNetworks}. It is necessary
	 * that each subnetwork in {@code subNetworks} contains as node names the node names of placeholder in this
	 * network.
	 *
	 * @param subNetworks not-null array of prototypal link representing a subnetwork each.
	 * @param n           max number of wanted solutions.
	 *
	 * @return the subnetworks array updated with the right bounds, null if it is not possible to use all subnetworks or
	 * 	there is any other error.
	 *
	 * @throws IllegalArgumentException if any subnetwork cannot be used. Useful to have the diagnostic of the error.
	 */
	@Nullable
	public ObjectList<ObjectList<PrototypalLink>> configureSubNetworks(@Nonnull ObjectList<PrototypalLink> subNetworks,
	                                                                   int n) throws IllegalArgumentException {
		final ObjectSet<String> endingNodes = new ObjectLinkedOpenHashSet<>();
		final Object2ObjectMap<String, CSTNPSUEdge> forwardEdges = new Object2ObjectOpenHashMap<>();
		final Object2ObjectMap<String, CSTNPSUEdge> backEdges = new Object2ObjectOpenHashMap<>();
		final ObjectSet<PrototypalLink> Pplus = new ObjectLinkedOpenHashSet<>();
		final ObjectSet<PrototypalLink> Pzero = new ObjectLinkedOpenHashSet<>();
		final ObjectSet<PrototypalLink> Pplus_zero = new ObjectLinkedOpenHashSet<>();
		final Object2ObjectMap<PrototypalLink, ObjectList<PrototypalLink>> guardedOfSubProcess =
			new Object2ObjectOpenHashMap<>();
		ObjectList<PrototypalLink> possibleGuardedLinks;
		final ObjectList<ObjectList<PrototypalLink>> listOfPossibleGuardedLinks = new ObjectArrayList<>();
		final ObjectList<ObjectList<PrototypalLink>> listOfSolutions = new ObjectArrayList<>();
		final List<List<PrototypalLink>> cartesianProduct;
		PrototypalLink parentLink;
		reset();

		//check the admissibility of the prototypal links
		for (final PrototypalLink link : subNetworks) {
			//Put outer bounds into this network
			final String s = link.getStartingNodeName();
			if (s == null || s.isEmpty()) {
				throw new IllegalArgumentException("Prototypal link " + link + " has not starting node.");
			}
			final String d = link.getEndingNodeName();
			if (d == null || d.isEmpty()) {
				throw new IllegalArgumentException("Prototypal link " + link + " has not ending node.");
			}
			if (endingNodes.contains(d)) {
				throw new IllegalArgumentException(
					"Prototypal link " + link + " has ending node in common with other prototypal link.");
			}
			endingNodes.add(d);

			final LabeledNode sNode = g.getNode(s);
			if (sNode == null) {
				throw new IllegalArgumentException(
					"Prototypal link " + link + " has starting node " + s + " not present in this network.");
			}
			final LabeledNode dNode = g.getNode(d);
			if (dNode == null) {
				throw new IllegalArgumentException(
					"Prototypal link " + link + " has ending node " + d + " not present in this network.");
			}

			CSTNPSUEdge forwardEdge = g.findEdge(sNode, dNode);
			String edgeName = s + "-->" + d;
			if (forwardEdge == null) {
				forwardEdge = g.getEdgeFactory().get(edgeName);
				g.addEdge(forwardEdge, sNode, dNode);
			}
			forwardEdge.setConstraintType(ConstraintType.requirement);
			forwardEdge.mergeLabeledValue(Label.emptyLabel, link.getUpperBound());
			forwardEdges.put(edgeName, forwardEdge);

			CSTNPSUEdge backEdge = g.findEdge(dNode, sNode);
			edgeName = d + "-->" + s;
			if (backEdge == null) {
				backEdge = g.getEdgeFactory().get(edgeName);
				g.addEdge(backEdge, dNode, sNode);
			}
			backEdge.setConstraintType(ConstraintType.requirement);
			backEdge.mergeLabeledValue(Label.emptyLabel, -link.getLowerBound());
			backEdges.put(edgeName, backEdge);
			if (link.getContingency() == 0) {
				Pzero.add(link);
			} else {
				if (link.getUpperGuard() - link.getLowerGuard() <= link.getContingency()) {
					Pplus.add(link);
				} else {
					//this case is when a prototypal link is a guarded one, and it can be represented directly
					final Label l = sNode.getLabel().conjunction(dNode.getLabel());
					final ALabel al = new ALabel(dNode.getName(), g.getALabelAlphabet());
					forwardEdge.setConstraintType(ConstraintType.contingent);
					forwardEdge.putLowerCaseValue(l, al, link.getLowerGuard());
					backEdge.setConstraintType(ConstraintType.contingent);
					backEdge.putUpperCaseValue(l, al, -link.getUpperBound());
				}
			}
		}

		try {
			parentLink = getPrototypalLink();
		} catch (WellDefinitionException e) {
			throw new IllegalArgumentException(
				"Original prototypal links determine a not-DC network for a syntax error: " + e.getMessage());
		}
		if (parentLink == null) {
			LOG.info("Original prototypal links determine a not-DC network.");
			return null;
		}

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("After first check, the parent link is " + parentLink + "\nSet Pplus has " + Pplus.size() +
				          " links.\nSet Pzero has " + Pzero.size() + " links.");
			}
		}
		int combinations = 0;
		Pplus_zero.addAll(Pplus);
		Pplus_zero.addAll(Pzero);
		for (final PrototypalLink link : Pplus_zero) {
			//check the determined outbounds with original ones
			final String s = link.getStartingNodeName();
			final String d = link.getEndingNodeName();
			String edgeName = s + "-->" + d;
			final CSTNPSUEdge forwardEdge = forwardEdges.get(edgeName);
			final int upperBound = forwardEdge.getMinValue();
			if (upperBound < link.getUpperGuard()) {
				LOG.info("Original prototypal link " + link +
				         " cannot be used because the upper bound in the parent is smaller than upper guard: " +
				         upperBound + " vs " + link.getUpperGuard());
				return null;
			}
			if (upperBound < link.getUpperBound()) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer(
							"Link " + link + " upper bound is " + link.getUpperBound() + " and it is updated to " +
							upperBound);
					}
				}
				link.upperBound = upperBound;
			}

			edgeName = d + "-->" + s;
			final CSTNPSUEdge backEdge = backEdges.get(edgeName);
			final int lowerBound = -backEdge.getMinValue();
			if (lowerBound > link.getLowerGuard()) {
				LOG.info("Original prototypal link " + link +
				         " cannot be used because the lower bound in the parent is greater than lower guard: " +
				         lowerBound + " vs " + link.getLowerGuard());
				return null;
			}
			if (lowerBound > link.getLowerBound()) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer(
							"Link " + link + " lower bound is " + link.getLowerBound() + " and it is updated to " +
							lowerBound);
					}
				}
				link.lowerBound = lowerBound;
			}

			final int contLink = link.getContingency();
			if (contLink > 0) {
				final int cont = Constants.sumWithOverflowCheck(upperBound, -lowerBound);
				if (cont < contLink) {
					LOG.info("Original prototypal link " + link +
					         " cannot be used because the contingency cannot be preserved: " + cont + " vs " +
					         contLink);
					return null;
				}
			}
			// row 14 algorithm 2 paper2023TimeAwarenessExtended
			if (link.getLowerGuard() == Constants.INT_POS_INFINITE || link.getLowerGuard() > upperBound) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer(
							"Link " + link + " lower guard is " + link.getLowerGuard() + " and it is updated to " +
							upperBound);
					}
				}
				link.lowerGuard = upperBound;
			}

			if (link.getContingency() == 0) {
				possibleGuardedLinks = new ObjectArrayList<>();
				for (int b = link.upperGuard; b <= link.lowerGuard; b++) {
					possibleGuardedLinks.add(new PrototypalLink(s, link.lowerBound, b, b, link.upperBound, 0, d));
				}
				guardedOfSubProcess.put(link, possibleGuardedLinks);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer(
							"For link " + link + " found " + possibleGuardedLinks.size() + " different guarded links.");
					}
					if (LOG.isLoggable(Level.FINEST)) {
						final StringBuilder s1 = new StringBuilder(80);
						for (final PrototypalLink l : possibleGuardedLinks) {
							s1.append(l).append(", ");
						}
						LOG.finest("Guarded links: " + s1);
					}
				}
				combinations =
					(combinations == 0) ? possibleGuardedLinks.size() : combinations * possibleGuardedLinks.size();
				continue;
			}

			//for lines 16--20 algorithm 2 paper2023TimeAwarenessExtended
			//contingency is > 0
			possibleGuardedLinks = new ObjectArrayList<>();
			int b = link.lowerBound;
			while (b <= link.lowerGuard) {
				final int bc = b + link.getContingency();
				if (link.getUpperGuard() <= bc && bc <= link.getUpperBound()) {
					possibleGuardedLinks.add(
						new PrototypalLink(s, link.lowerBound, b, bc, link.upperBound, link.getContingency(), d));
				}
				b++;
			}
			guardedOfSubProcess.put(link, possibleGuardedLinks);
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer(
						"For link " + link + " found " + possibleGuardedLinks.size() + " different guarded links.");
				}
				if (LOG.isLoggable(Level.FINEST)) {
					final StringBuilder s1 = new StringBuilder(80);
					for (final PrototypalLink l : possibleGuardedLinks) {
						s1.append(l).append(", ");
					}
					LOG.finest("Guarded links: " + s1);
				}
			}
			combinations =
				(combinations == 0) ? possibleGuardedLinks.size() : combinations * possibleGuardedLinks.size();
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Combinations of all proper guarded links are " + combinations);
			}
		}
		listOfPossibleGuardedLinks.addAll(guardedOfSubProcess.values());
		cartesianProduct = Lists.cartesianProduct(listOfPossibleGuardedLinks);
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Cartesian product size is " + cartesianProduct.size());
			}
		}
		assert guardedOfSubProcess.isEmpty() || cartesianProduct.size() == combinations;

		for (final List<PrototypalLink> combination : cartesianProduct) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Combination " + combination);
				}
				if (LOG.isLoggable(Level.FINEST)) {
					final StringBuilder s1 = new StringBuilder(80);
					for (final PrototypalLink l : combination) {
						s1.append(l).append(", ");
					}
					LOG.finest("Guarded links: " + s1);
				}
			}
			//inject the guarded link
			for (final PrototypalLink link : combination) {
				final String s = link.getStartingNodeName();
				final String d = link.getEndingNodeName();
				final LabeledNode sNode = g.getNode(s);
				final LabeledNode dNode = g.getNode(d);
				String edgeName = s + "-->" + d;
				assert sNode != null;
				assert dNode != null;
				final Label l = sNode.getLabel().conjunction(dNode.getLabel());
				final ALabel al = new ALabel(dNode.getName(), g.getALabelAlphabet());
				dNode.setALabel(al);
				final CSTNPSUEdge forwardEdge = forwardEdges.get(edgeName);
				forwardEdge.clear();
				forwardEdge.putLabeledValue(l, link.getUpperBound());
				forwardEdge.setConstraintType(ConstraintType.contingent);
				forwardEdge.putLowerCaseValue(l, al, link.getLowerGuard());
				edgeName = d + "-->" + s;
				final CSTNPSUEdge backEdge = backEdges.get(edgeName);
				backEdge.clear();
				backEdge.putLabeledValue(l, -link.getLowerBound());
				backEdge.setConstraintType(ConstraintType.contingent);
				backEdge.putUpperCaseValue(l, al, -link.getUpperGuard());
			}

			reset();
			try {
				parentLink = getPrototypalLink();
			} catch (WellDefinitionException e) {
				throw new IllegalArgumentException(
					"Original prototypal links determine a not-DC network for a syntax error: " + e.getMessage());
			}
			if (parentLink == null) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("Combination " + combination + " does not determine a DC network!");
					}
				}
				continue;
			}

			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("After check, the parent link is " + parentLink);
				}
			}

			//extract determined guarded range from the network as solution
			final ObjectList<PrototypalLink> determinedLinks = new ObjectArrayList<>();
			for (final PrototypalLink link : combination) {
				final PrototypalLink determinedLink = new PrototypalLink(link);
				//check the determined outbounds with original ones
				final String s = link.getStartingNodeName();
				final String d = link.getEndingNodeName();
				String edgeName = s + "-->" + d;
				final CSTNPSUEdge forwardEdge = forwardEdges.get(edgeName);
				determinedLink.upperBound = forwardEdge.getMinValue();
				edgeName = d + "-->" + s;
				final CSTNPSUEdge backEdge = backEdges.get(edgeName);
				determinedLink.lowerBound = -backEdge.getMinValue();
				determinedLinks.add(determinedLink);
			}

			//save the solution
			listOfSolutions.add(determinedLinks);//union
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Solution associated to combination " + combination);
				}
				if (LOG.isLoggable(Level.FINEST)) {
					final StringBuilder s1 = new StringBuilder(80);
					for (final PrototypalLink l : determinedLinks) {//union
						s1.append(l).append(", ");
					}
					LOG.finest("Solution: " + s1);
				}
			}
			if (listOfSolutions.size() == n) {
				break;
			}
		}

		return listOfSolutions;
	}

	/**
	 * {@inheritDoc} Wrapper method for {@link #dynamicControllabilityCheck()}
	 */
	@Override
	public CSTNUCheckStatus dynamicConsistencyCheck() throws WellDefinitionException {
		return dynamicControllabilityCheck();
	}

	/**
	 * Checks the controllability of a CSTNPSU instance and, if the instance is controllable, determines all the minimal
	 * ranges for the constraints.
	 * <p>
	 * All propositions that are redundant at run time are removed: therefore, all labels contains only the necessary
	 * and sufficient propositions.
	 * </p>
	 *
	 * @return an {@link CSTNUCheckStatus} object containing the final status and some statistics about the executed
	 * 	checking.
	 *
	 * @throws WellDefinitionException if any.
	 */
	public CSTNUCheckStatus dynamicControllabilityCheck() throws WellDefinitionException {
		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO, "\nStarting checking CSTNPSU dynamic controllability...");
			}
		}

		Instant startInstant = Instant.now();
		final Instant timeoutInstant = startInstant.plusSeconds(timeOut);
		try {
			initAndCheck();
		} catch (final IllegalArgumentException e) {
			throw new IllegalArgumentException(
				"The graph has a problem, and it cannot be initialized: " + e.getMessage());
		}

		final LabeledNode Z = g.getZ();
		assert Z != null;
		//Since the propagation rules have Z as source or as destination,
		//I collect all edges incoming to or outgoing from Z as initial set of edges
//		EdgesToCheck<CSTNPSUEdge> edgesToCheck = new EdgesToCheck<>(this.g.getEdges());
		final EdgesToCheck<CSTNPSUEdge> edgesToCheck = new EdgesToCheck<>();
		edgesToCheck.addAll(g.getInEdges(Z));
		edgesToCheck.addAll(g.getOutEdges(Z));

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
		if (maxCycles < 0) {
			maxCycles = Integer.MAX_VALUE;
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.info("The maximum number of possible cycles is " + maxCycles);
			}
		}

		int i;
		checkStatus.finished = false;
		for (i = 1; i <= maxCycles && checkStatus.consistency && !checkStatus.finished; i++) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.log(Level.INFO, "*** Start Main Cycle " + i + "/" + maxCycles + " ***");
				}
			}

			checkStatus = oneStepDynamicControllabilityLimitedToZ(edgesToCheck, timeoutInstant);

			if (!checkStatus.finished) {
				if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
					if (Debug.ON) {
						final String msg =
							"During the check # " + i + " time out of " + timeOut + " seconds occurred. ";
						if (LOG.isLoggable(Level.INFO)) {
							LOG.log(Level.INFO, msg);
						}
					}
					checkStatus.executionTimeNS = ChronoUnit.NANOS.between(startInstant, Instant.now());
					return getCheckStatus();
				}
				if (Debug.ON) {
					if (checkStatus.consistency) {
						if (LOG.isLoggable(Level.FINER)) {
							final StringBuilder log = new StringBuilder(
								"During the check n. " + i + ", " + edgesToCheck.size() +
								" edges have been added/modified. Check has to continue.\nDetails of only modified edges having values:\n");
							for (final CSTNPSUEdge e : edgesToCheck) {
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
		} // fine DC check
		Instant endInstant = Instant.now();
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
		// minimize the requirement link if requested
		if (!propagationOnlyToZ) {
			startInstant = Instant.now();
			if (Debug.ON) {
				LOG.finer("Minimizing bounds of original edges of the network.");
			}
			for (final LabeledNode source : g.getVertices()) {
				if (source == Z) {
					continue;
				}
				for (final ObjectObjectImmutablePair<CSTNPSUEdge, LabeledNode> edgeNode : g.getOutEdgesAndNodes(
					source)) {
					final LabeledNode dest = edgeNode.right();
					if (dest == Z) {
						continue;
					}
					final CSTNPSUEdge sourceZ = g.findEdge(source, Z);// always present by initAndCheck()
					final CSTNPSUEdge ZDest = g.findEdge(Z, dest); // always present by addUpperBounds()
					rUpdateBoundsUsingZ(source, Z, dest, sourceZ, ZDest, edgeNode.left());
				}
			}
			endInstant = Instant.now();
			checkStatus.partialExecutionTimeNS = Duration.between(startInstant, endInstant).toNanos();
		}

		if (cleanCheckedInstance) {
			gCheckedCleaned = new TNGraph<>(g.getName(), g.getEdgeImplClass());
			gCheckedCleaned.copyCleaningRedundantLabels(g);
		}
		return getCheckStatus();
	}

	@Override
	public CSTNUCheckStatus getCheckStatus() {
		return ((CSTNUCheckStatus) checkStatus);
	}

	/**
	 * The contingency graph is a labeled graph for determining the contingency value of each node. Once the contingency
	 * graph is built, the contingency value of a node is determined using a modified BellmanFord algorithm, implemented
	 * by the {@link #getMaxPathContingencySpanInContingencyGraph} that returns the maximum contingency value of the
	 * given node name.<br> The contingency span is defined only for DC networks. If the given network is not DC, it
	 * returns null.
	 *
	 * @return the contingency graph associated to the current network, null if this current network is not checked or
	 * 	not controllable.
	 */
	@Nullable
	public TNGraph<CSTNEdge> getContingencyGraph() {
		final CSTNUCheckStatus dcStatus = getCheckStatus();
		if (dcStatus.finished && !dcStatus.isControllable()) {
			return null;
		}
		final TNGraph<CSTNEdge> contingencyGraph = new TNGraph<>("", CSTNEdgePluggable.class);
		// build the contingencyGraph
		if (Debug.ON) {
			LOG.finer("Starting to build contingency graph.");
		}
		for (final LabeledNode node : g.getVertices()) {
			contingencyGraph.addVertex(new LabeledNode(node));
		}
		final LabeledNode ZinContingencyGraph = contingencyGraph.getNode(ZERO_NODE_NAME);
		contingencyGraph.setZ(ZinContingencyGraph);
		final EdgeSupplier<CSTNEdge> edgeFactory = new EdgeSupplier<>(EdgeSupplier.DEFAULT_CSTN_EDGE_CLASS);
		// edges
		// For each guarded link A--[x, x′][y′, y]-->B ∈ G, in the contingency graph there is a single edge A--⟨x'-y', ⊡⟩-->B.
		// For each requirement link A--[x,y],α-->B, in the contingency graph there are two edges A--⟨y-x,α⟩-->B, and B--⟨y-x,α⟩-->A.
		// For each time-point T ∈ T , in the contingency graph there is a single edge Z--⟨0,⊡⟩-->T.
		// Consider only original links, not the derived.
		final ObjectSet<CSTNPSUEdge> alreadyChecked = new ObjectLinkedOpenHashSet<>();
		int x, y;
		for (final CSTNPSUEdge edge : g.getEdges()) {
			boolean newEdge = false, newCompanion = false;
			final ConstraintType type = edge.getConstraintType();
			if (type == ConstraintType.derived || alreadyChecked.contains(edge)) {
				continue;
			}
			final LabeledNode s = g.getSource(edge);
			final LabeledNode d = g.getDest(edge);
			final CSTNPSUEdge edgeCompanion = g.findEdge(d, s);
			assert s != null;
			assert d != null;
			CSTNEdge edgeInConsistencyGraph = contingencyGraph.findEdge(s.getName(), d.getName());
			if (edgeInConsistencyGraph == null) {
				edgeInConsistencyGraph = edgeFactory.get(s.getName() + "_" + d.getName());
				newEdge = true;
			}
			if (type == ConstraintType.contingent) {
				if (edgeCompanion == null) {
					throw new IllegalStateException(
						"Contingent edge " + edge + " has not the companion contingent edge.");
				}
				final LabeledLowerCaseValue lcvObject = edge.getLowerCaseValue();
				if (lcvObject.isEmpty()) {
					continue;// we prefer to check a contingent link when the edge to contingent node is found.
				}
				if (lcvObject.getNodeName().equals(d.getALabel())) {
					// edge is the lower case edge
					x = lcvObject.getValue();
				} else {
					throw new IllegalStateException(
						"Contingent edge " + edge + " has not the lower case value associated to the contingent node " +
						d + ". lcvObject.getNodeName()=" + lcvObject.getNodeName() + ", d.getALabel()=" +
						d.getALabel());
				}
				final Object2ObjectMap.Entry<Label, Entry<ALabel>> entry = edgeCompanion.getMinUpperCaseValue();
				if (!entry.getValue().getKey().equals(d.getALabel())) {
					throw new IllegalStateException("Contingent edge " + edgeCompanion +
					                                " has not the upper case value associated to the contingent node " +
					                                d);
				}
				y = entry.getValue().getIntValue();
				edgeInConsistencyGraph.mergeLabeledValue(Label.emptyLabel, Constants.sumWithOverflowCheck(x, y));
				contingencyGraph.addEdge(edgeInConsistencyGraph,
				                         Objects.requireNonNull(contingencyGraph.getNode(s.getName())),
				                         Objects.requireNonNull(contingencyGraph.getNode(d.getName())));
				alreadyChecked.add(edgeCompanion);
				alreadyChecked.add(edge);
			}
			if (type == ConstraintType.requirement) {
				// Given a requirement X---[x,y]--->Y, there is two edges in the distance graph (this.g)
				// X<--(-x)---Y and X--(y)-->Y.
				// We determine the -Delta_XY and the two edges for contingency graph when we meet X<--(-x)---Y

				x = edge.getMinValue();
				if (x > 0) {
					// all values are positive, then we can ignore this edge. We will consider it when the companion edge
					// will be met.
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							LOG.finer("Edge " + edge +
							          " has positive values, we will consider it when the companion edge is the current edge.");
						}
						continue;
					}
				}

				int delta;
				CSTNEdge companionEdgeInConsistencyGraph = contingencyGraph.findEdge(d.getName(), s.getName());
				if (companionEdgeInConsistencyGraph == null) {
					companionEdgeInConsistencyGraph = edgeFactory.get(d.getName() + "_" + s.getName());
					newCompanion = true;
				}
				for (final Entry<Label> entry : edge.getLabeledValueSet()) {
					x = entry.getIntValue();
					if (x <= 0) {
						// Given a requirement X---[x,y]--->Y, here `edge` represents D=X<--(-x)---S=Y
						if (edgeCompanion == null) {
							// this is the case where there is not the upper bound, i.e., s --(<=0,l)--> d
							delta = horizon;
						} else {
							y = edgeCompanion.getMinValueSubsumedBy(entry.getKey());
							if (y == Constants.INT_NULL) {
								if (Debug.ON) {
									if (LOG.isLoggable(Level.FINER)) {
										LOG.finer("There are no values compatible with " + entry + " in edge " +
										          edgeCompanion);
									}
									continue;
								}
							}
							if (y < 0) {
								throw new IllegalStateException(
									"Found two strange values in edge " + edge + " and " + edgeCompanion + ": x = " +
									x + ", y= " + y);
							}
							delta = Constants.sumWithOverflowCheck(y, x);
						}
						edgeInConsistencyGraph.mergeLabeledValue(entry.getKey(), delta);
						companionEdgeInConsistencyGraph.mergeLabeledValue(entry.getKey(), delta);
					}
					// else {
					// Positive value, we can ignore.
					// continue;
					// }
				}
				if (newEdge) {
					contingencyGraph.addEdge(edgeInConsistencyGraph,
					                         Objects.requireNonNull(contingencyGraph.getNode(s.getName())),
					                         Objects.requireNonNull(contingencyGraph.getNode(d.getName())));
				}
				if (newCompanion) {
					contingencyGraph.addEdge(companionEdgeInConsistencyGraph,
					                         Objects.requireNonNull(contingencyGraph.getNode(d.getName())),
					                         Objects.requireNonNull(contingencyGraph.getNode(s.getName())));
				}
			}
		}
		// add edges from Z
		for (final LabeledNode node : contingencyGraph.getVertices()) {
			if (node == ZinContingencyGraph) {
				continue;
			}
			CSTNEdge edgeZNode = contingencyGraph.findEdge(ZinContingencyGraph, node);
			if (edgeZNode == null) {
				assert ZinContingencyGraph != null;
				edgeZNode = edgeFactory.get(ZinContingencyGraph.getName() + "_" + node.getName());
				contingencyGraph.addEdge(edgeZNode, ZinContingencyGraph, node);
			}
			edgeZNode.mergeLabeledValue(Label.emptyLabel, 0);
		}
		if (Debug.ON) {
			LOG.finer("Contingency graph built.");
		}
		return contingencyGraph;
	}

	/**
	 * Checks the DC property and, if DC, returns the prototypal link that represents the global temporal behavior of
	 * the network.
	 * <p>
	 * It is assumed that the network contains node Z (that is added if not present by DC checking algorithm) and node
	 * 'Ω' (End), assumed to be the end node of the network (the node that is executed as last one).
	 * <p>
	 * If 'Ω' is not present, it is added. To be sure that 'Ω' is the last node, before DC checking, this procedure adds
	 * an X--(0,⊡)--&gt;Ω constraint for each other node X.
	 * <p>
	 * In {@code getCheckStatus().executionTimeInNS} there is the execution time for the DC checking, while in
	 * {@code getCheckStatus().partialExecutionTimeNS} there is the execution time for the determination of the
	 * prototypal link after the DC checking. The total time is given by the sum of these two values.
	 * <p>
	 * It sets {@link #propagationOnlyToZ} to false because prototypal lik need all constraints minimized.
	 * </p>
	 *
	 * @return the prototypal link if the network is DC, null otherwise.
	 *
	 * @throws WellDefinitionException if the network is not well-defined.
	 */
	@Nullable
	public PrototypalLink getPrototypalLink() throws WellDefinitionException {
		if (prototypalLink != null) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Reusing the calculated prototypal link " + prototypalLink);
				}
			}
			return prototypalLink;
		}
		if (Debug.ON) {
			LOG.finer("Making Ω the last node");
		}
		LabeledNode Omega = g.getNode("Ω");
		if (Omega == null) {
			// node Ω does not exist, add it
			Omega = new LabeledNode("Ω");
			g.addVertex(Omega);
		}
		// Make sure that E is the last node
		if (Debug.ON) {
			LOG.finer("Making Ω the last node");
		}
		for (final LabeledNode X : g.getVertices()) {
			if (X == Omega) {
				continue;
			}
			CSTNPSUEdge eOmegaX = g.findEdge(Omega, X);
			if (eOmegaX == null) {
				eOmegaX = makeNewEdge("Ω_" + X.getName(), ConstraintType.derived);
				g.addEdge(eOmegaX, Omega, X);
			}
			eOmegaX.mergeLabeledValue(Label.emptyLabel, 0);
		}

		propagationOnlyToZ = false;//getPrototypalLink requires to have all constraints minimized.
		final CSTNUCheckStatus status = dynamicControllabilityCheck();
		if (status.finished && !status.isControllable()) {
			return null;
		}

		final Instant startInstant = Instant.now();
		// determine all 5 values for LPC
		final CSTNPSUEdge OmegaZ = g.findEdge(Omega, g.getZ());
		final CSTNPSUEdge ZOmega = g.findEdge(g.getZ(), Omega);

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Edges for determining prototypal link values are: " + OmegaZ + "\n" + ZOmega);
			}
		}
		// lower bound
		int lBound = Constants.INT_POS_INFINITE;
		assert OmegaZ != null;
		for (final Entry<Label> entry : OmegaZ.getLabeledValueSet()) {
			final int v = -entry.getIntValue();
			if (lBound > v) {
				lBound = v;
			}
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Prototypal link lower bound: " + lBound);
			}
		}

		// upper bound
		int uBound = 0;
		assert ZOmega != null;
		for (final Entry<Label> entry : ZOmega.getLabeledValueSet()) {
			final int v = entry.getIntValue();
			if (uBound < v) {
				uBound = v;
			}
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Prototypal link upper bound: " + uBound);
			}
		}

		// lower guard
		final Object2ObjectMap.Entry<Label, Entry<ALabel>> lGuardObject = ZOmega.getMinLowerCaseValue();
		final int lowerUpperBound = ZOmega.getMinValue();// DC checking guarantees it is horizon in the worst case.
		final int lGuard =
			(lGuardObject == null) ? lowerUpperBound : Math.min(lGuardObject.getValue().getIntValue(), lowerUpperBound);
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Prototypal link lower guard: " + lGuard);
			}
		}

		// upper guard
		final Object2ObjectMap.Entry<Label, Entry<ALabel>> uGuardObject = OmegaZ.getMinUpperCaseValue();
		final int greatestLowerBound = -OmegaZ.getMinValue();// DC checking guarantees it is 0 in the worst case.
		final int uGuard = (uGuardObject == null) ? greatestLowerBound
		                                          : Math.max(-uGuardObject.getValue().getIntValue(),
		                                                     greatestLowerBound);
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Prototypal link upper guard: " + uGuard);
			}
		}

		final TNGraph<CSTNEdge> contingencyGraph = getContingencyGraph();
		final int c = getMaxPathContingencySpanInContingencyGraph("Ω", contingencyGraph);

		final PrototypalLink lpc =
			new PrototypalLink(Objects.requireNonNull(g.getZ()).getName(), lBound, lGuard, uGuard, uBound, c,
			                   Omega.getName());
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("Final prototypal link with contingency: " + lpc);
			}
		}
		final Instant endInstant = Instant.now();
		final long partialTime = Duration.between(startInstant, endInstant).toNanos();
		checkStatus.partialExecutionTimeNS += partialTime;

		return lpc;
	}

	/**
	 * Calls {@link CSTN#coreCSTNInitAndCheck()} and, then, check all guarded links. This method works only with
	 * streamlined instances!
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
					        "Initialization of a CSTNPSU can be done only one time! Reload the graph if a new init is necessary!");
				}
			}
			return;
		}

		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO, "Starting checking graph as CSTNPSU well-defined instance...");
			}
		}

		// check underneath CSTN
		//It is necessary to find the maximum absolute value among negative and positive values
		//because, otherwise, the upper bound Z--->X can be lower than the already present value
		//this.propagationOnlyToZ = true guaranties this.
		final boolean originalPropagationOnlyToZ = propagationOnlyToZ;
		propagationOnlyToZ = false;
		coreCSTNInitAndCheck();
		checkStatus.initialized = originalPropagationOnlyToZ;

		// Contingent link have to be checked AFTER WD1 and WD3 have been checked and fixed!
		int maxWeightContingent = Constants.INT_POS_INFINITE;
		for (final CSTNPSUEdge e : g.getEdges()) {
			if (!e.isContingentEdge()) {
				continue;
			}
			final LabeledNode s = g.getSource(e);
			final LabeledNode d = g.getDest(e);
			/*
			 * Manage guarded link.
			 */
			final Entry<Label> minLabeledValue = e.getMinLabeledValue();
			// we assume that instance was streamlined! Moreover, we consider only one value, the one
			// with label == conjunctedLabel in the original network.
			int initialValue = minLabeledValue.getIntValue();
			final Label conjunctedLabel = minLabeledValue.getKey();// s.getLabel().conjunction(d.getLabel());

			if (initialValue == Constants.INT_NULL) {
				if (e.lowerCaseValueSize() == 0 && e.upperCaseValueSize() == 0) {
					throw new IllegalArgumentException("Guarded edge " + e +
					                                   " cannot be initialized because it hasn't an initial value neither a lower/upper case value.");
				}
			}
			if (initialValue == 0) {
				assert d != null;
				if (d.isObserver() && e.lowerCaseValueSize() > 0) {
					e.removeLabeledValue(conjunctedLabel);
					initialValue = Constants.INT_NULL;
				} else {
					throw new IllegalArgumentException("Guarded edge " + e +
					                                   " cannot have a bound equals to 0. The two bounds [x,y] have to be 0 < x < y < ∞.");
				}
			}

			final CSTNPSUEdge eInverted = g.findEdge(d, s);
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Edge " + e + " is contingent. Found its companion: " + eInverted);
				}
			}
			if (eInverted == null) {
				assert d != null;
				assert s != null;
				throw new IllegalArgumentException(
					"Guarded edge " + e + " is alone. The companion guarded edge between " + d.getName() + " and " +
					s.getName() + " does not exist. It has to!");
			}
			if (!eInverted.isContingentEdge()) {
				throw new IllegalArgumentException("Edge " + e + " is guarded while the companion edge " + eInverted +
				                                   " is not guarded!\nIt has to be!");
			}
			/*
			 * Memo.
			 * If current initialValue is < 0, current edge is the lower bound A<---C.
			 * The lower case labeled value has to be put in the inverted edge if it is not already present.
			 * <br>
			 * If current initialValue is >=0, current edge is the upper bound A--->C.
			 * The upper case labeled value has to be put in the inverted edge if it is not already present.
			 * <br>
			 * If current initialValue is undefined, then we assume that the contingent link is already set, and
			 * it contains only upper/lower values!
			 */
			if (initialValue != Constants.INT_NULL) {
				int lowerCaseValueInEInverted;
				int ucValue;
				final int eInvertedInitialValue = eInverted.getValue(conjunctedLabel);

				if (initialValue < 0) {
					// current edge is the lower bound.
					assert s != null;
					final ALabel contingentALabel = new ALabel(s.getName(), g.getALabelAlphabet());
					if (!contingentALabel.equals(s.getALabel())) {
						s.setALabel(contingentALabel);// to speed up DC checking!
					}
					lowerCaseValueInEInverted = eInverted.getLowerCaseValue(conjunctedLabel, contingentALabel);
					if (lowerCaseValueInEInverted != Constants.INT_NULL && -initialValue > lowerCaseValueInEInverted) {
						throw new IllegalArgumentException(
							"Edge " + e + " is guarded with a negative value and the inverted " + eInverted +
							" has a guard that is smaller: " +
							CSTNU.lowerCaseValueAsString(contingentALabel, lowerCaseValueInEInverted, conjunctedLabel) +
							".");
					}
					if (lowerCaseValueInEInverted == Constants.INT_NULL &&
					    (eInvertedInitialValue <= 0)) {// || eInvertedInitialValue == Constants.INT_NULL)
						throw new IllegalArgumentException(
							"Edge " + e + " is guarded with a negative value but the inverted " + eInverted +
							" does not contain a lower case value neither a proper initial value. ");
					}

					if (lowerCaseValueInEInverted == Constants.INT_NULL) {
						lowerCaseValueInEInverted = -initialValue;
						eInverted.mergeLowerCaseValue(conjunctedLabel, contingentALabel, lowerCaseValueInEInverted);
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								LOG.log(Level.FINER, "Inserted the lower label value: " +
								                     CSTNU.lowerCaseValueAsString(contingentALabel,
								                                                  lowerCaseValueInEInverted,
								                                                  conjunctedLabel) + " to edge " +
								                     eInverted);
							}
						}
					}
					final Object2ObjectMap.Entry<Label, Entry<ALabel>> minUC = e.getMinUpperCaseValue();
					final Label ucLabel = minUC.getKey();
					final ALabel ucALabel = minUC.getValue().getKey();
					ucValue = minUC.getValue().getIntValue();
					if (Debug.ON) {
						if (ucValue == Constants.INT_NULL || !ucALabel.equals(contingentALabel)) {
							if (LOG.isLoggable(Level.FINER)) {
								LOG.log(Level.FINER,
								        "The upper-case value is missing, or it has a wrong ALabel: " + minUC +
								        "\n Fixing it!");
							}
						}
					}
					if (ucValue == Constants.INT_NULL) {
						if (eInvertedInitialValue != Constants.INT_NULL) {
							ucValue = -eInvertedInitialValue;
							e.mergeUpperCaseValue(conjunctedLabel, contingentALabel, ucValue);
							if (Debug.ON) {
								if (LOG.isLoggable(Level.FINER)) {
									LOG.log(Level.FINER, "Inserted the upper label value: " +
									                     CSTNU.upperCaseValueAsString(contingentALabel, ucValue,
									                                                  conjunctedLabel) + " to edge " +
									                     e);
								}
							}
						} else {
							throw new IllegalArgumentException(
								"Edge " + e + " is guarded without an upper-case value and the inverted " + eInverted +
								" does not contain an initial value. It is not possible to set current edge correctly.");
						}
					} else {
						if (-ucValue > eInvertedInitialValue) {
							throw new IllegalArgumentException("Edge " + e +
							                                   " is guarded with an upper-case value greater that the upper value in the companion edge " +
							                                   eInverted +
							                                   ". It is not possible to set current edge correctly.");
						}
						if (!ucALabel.equals(contingentALabel) ||
						    !ucLabel.equals(conjunctedLabel)) {// || !ucLabel.isEmpty()) {
							// By completeness theorem, ucLabel should be empty. This requirement was made only for making easier the proof.
							// Here we force to be equals to conjunctedLabel to limit the propagations.
							if (Debug.ON) {
								if (LOG.isLoggable(Level.INFO)) {
									LOG.log(Level.INFO, "Edge " + e + " is guarded with an upper-case value " + minUC +
									                    " having either a wrong ALabel w.r.t. the correct label " +
									                    contingentALabel + " or a wrong propositional label: " +
									                    ucLabel + " w.r.t. the context " + conjunctedLabel +
									                    ". It is fixed");
								}
							}
							e.removeUpperCaseValue(ucLabel, contingentALabel);
							e.mergeUpperCaseValue(conjunctedLabel, contingentALabel, ucValue);
						}
					}
					// In order to speed up the checking, prepare some auxiliary data structure
					s.setALabel(contingentALabel);// s is the contingent node.
					assert d != null;
					STNU.CHECK_ACTIVATION_UNIQUENESS(s, d, activationNode);
					activationNode.put(s, d);
					lowerContingentLink.put(s, eInverted);

				} else {
					// e : A--->C
					// eInverted : C--->A
					assert d != null;
					final ALabel contingentALabel = new ALabel(d.getName(), g.getALabelAlphabet());
					if (!contingentALabel.equals(d.getALabel())) {
						d.setALabel(contingentALabel);// to speed up DC checking!
					}
					final Object2ObjectMap.Entry<Label, Entry<ALabel>> minUC = eInverted.getMinUpperCaseValue();
					final Label ucLabel = minUC.getKey();
					final ALabel ucALabel = minUC.getValue().getKey();
					ucValue = minUC.getValue().getIntValue();

					if (ucValue != Constants.INT_NULL) {
						if (initialValue < -ucValue) {
							throw new IllegalArgumentException(
								"Edge " + e + " is guarded with a positive value and the inverted " + eInverted +
								" already contains an upper guard that is smaller: " +
								CSTNU.upperCaseValueAsString(contingentALabel, ucValue, conjunctedLabel) + ".");
						}
						if (!ucALabel.equals(contingentALabel) ||
						    !ucLabel.equals(conjunctedLabel)) {// !ucLabel.isEmpty()) {
							// By completeness theorem, ucLabel should be empty. This requirement was made only for making easier the proof.
							// Here we force to be equals to conjunctedLabel to limit the propagations.
							if (Debug.ON) {
								if (LOG.isLoggable(Level.INFO)) {
									LOG.log(Level.INFO,
									        "Edge " + e + " is lower guard edge and the inverted " + eInverted +
									        " has a wrong upper guard because either the node name is wrong or the label is wrong. Current upper guard: " +
									        CSTNU.upperCaseValueAsString(ucALabel, ucValue, ucLabel) +
									        " Name of contingent node: " + contingentALabel +
									        " Context propositional label: " + conjunctedLabel + " It is fixed.");
								}
							}
							eInverted.removeUpperCaseValue(ucLabel, contingentALabel);
							eInverted.mergeUpperCaseValue(conjunctedLabel, contingentALabel, ucValue);
						}

					}
					if (ucValue == Constants.INT_NULL &&
					    (eInvertedInitialValue == Constants.INT_NULL || eInvertedInitialValue >= 0)) {
						throw new IllegalArgumentException(
							"Edge " + e + " is guarded with a positive value but the inverted " + eInverted +
							" does not contain a upper case value neither a proper initial value. ");
					}
					if (ucValue == Constants.INT_NULL) {
						ucValue = -initialValue;
						eInverted.mergeUpperCaseValue(conjunctedLabel, contingentALabel, ucValue);
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								LOG.log(Level.FINER, "Inserted the upper label value: " +
								                     CSTNU.upperCaseValueAsString(contingentALabel, ucValue,
								                                                  conjunctedLabel) + " to edge " +
								                     eInverted);
							}
						}
						//						if (eInvertedInitialValue != Constants.INT_NULL) { eInvertedInitialValue for sure is != Constants.INT_NULL
						lowerCaseValueInEInverted = -eInvertedInitialValue;
						e.mergeLowerCaseValue(conjunctedLabel, contingentALabel, lowerCaseValueInEInverted);
						// In order to speed up the checking, prepare some auxiliary data structure
						assert s != null;
						STNU.CHECK_ACTIVATION_UNIQUENESS(s, d, activationNode);
						activationNode.put(d, s);
						lowerContingentLink.put(d, e);
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								LOG.log(Level.FINER, "Inserted the lower label value: " +
								                     CSTNU.lowerCaseValueAsString(contingentALabel,
								                                                  lowerCaseValueInEInverted,
								                                                  conjunctedLabel) + " to edge " + e);
							}
						}
						//						}
					}
				}
			} else {
				// here initial value is indefinite... UC and LC values are already present.
				if (!e.getLowerCaseValueMap().isEmpty()) {
					assert s != null;
					assert d != null;
					STNU.CHECK_ACTIVATION_UNIQUENESS(s, d, activationNode);
					activationNode.put(d, s);
					lowerContingentLink.put(d, e);
				}
				if (e.upperCaseValueSize() > 0) {
					assert s != null;
					final ALabel sourceALabel = new ALabel(s.getName(), g.getALabelAlphabet());
					if (!sourceALabel.equals(s.getALabel())) {
						s.setALabel(sourceALabel);// to speed up DC checking!
					}
				}
				if (eInverted.upperCaseValueSize() > 0) {
					assert d != null;
					final ALabel destALabel = new ALabel(d.getName(), g.getALabelAlphabet());
					if (!destALabel.equals(d.getALabel())) {
						d.setALabel(destALabel);// to speed up DC checking!
					}
				}
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
						"-" + maxWeightContingent + " is the new most negative found in contingent " + "while -" +
						maxWeight + " is the most negative found in normal constraint.");
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

		// init CSTNPSU structures.
		g.getLowerLabeledEdges();
		checkStatus.initialized = true;

		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO, "Checking graph as CSTNPSU well-defined instance finished!");
			}
		}
	}

	/**
	 * Executes one step of the dynamic controllability check.
	 * <p>
	 * Before the first execution of this method, it is necessary to execute {@link #initAndCheck()}.
	 *
	 * @param edgesToCheck   set of edges that have to be checked.
	 * @param timeoutInstant time instant limit allowed to the computation.
	 *
	 * @return the update status (for convenience. It'd be not necessary because it updates {@link #checkStatus}).
	 */
	public CSTNUCheckStatus oneStepDynamicControllabilityLimitedToZ(final EdgesToCheck<CSTNPSUEdge> edgesToCheck,
	                                                                Instant timeoutInstant) {
		// This version consider only the pair of edges going to Z, i.e., in the form A-->B-->Z,
		LabeledNode B, A;
		CSTNPSUEdge AZorZA, edgeCopy;

		checkStatus.cycles++;
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE,
				        "Start application labeled constraint generation and label removal rules limited to Z.");
			}
		}

		final EdgesToCheck<CSTNPSUEdge> newEdgesToCheck = new EdgesToCheck<>();
		int i = 1, n;
		// int maxNumberOfValueInAnEdge = 0, maxNumberOfUpperCaseValuesInAnEdge = 0;
		// CSTNPSUEdge fatEdgeInLabeledValues = null, fatEdgeInUpperCaseValues = null;// for sure they will be initialized!
		if (Debug.ON) {
			n = edgesToCheck.size();
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO, "Number of edges to analyze: " + n);
			}
		}

		boolean isBZ;
		final LabeledNode Z = g.getZ();
		for (final CSTNPSUEdge currentEdge : edgesToCheck) {
			if (g.getDest(currentEdge) == Z) {
				isBZ = true;
			} else {
				if (g.getSource(currentEdge) == Z) {
					isBZ = false;
				} else {
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							LOG.log(Level.FINER, "Ignoring edge " + (i++) + "/" + n + ": " + currentEdge +
							                     " because no one of its endpoints is Z.\n");
						}
					}
					continue;
				}
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Considering edge " + (i++) + "/" + n + ": " + currentEdge + "\n");
				}
			}
			B = (isBZ) ? g.getSource(currentEdge) : g.getDest(currentEdge);

			edgeCopy = g.getEdgeFactory().get(currentEdge);

			// initAndCheck does not resolve completely a possible qStar. So, it is necessary to check here the edge before to consider the second edge.
			// The check has to be done in case B==Z, and it consists in applying R0, R3 and zLabeledLetterRemovalRule!
			if (isBZ) {
				assert B != null;
				if (B.isObserver()) {
					// R0 on the resulting new values
					rM1(B, currentEdge);
				}
			}
			if (isBZ) {
				rM2(B, currentEdge);
			}
			if (isBZ && B.isObserver()) {// R2 can add new values that have to be minimized. Experimentally VERIFIED on
				// June, 28 2015
				// R0 on the resulting new values
				rM1(B, currentEdge);
			}

			if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
				return getCheckStatus();
			}

			// LLR is put here because it works like R0 and R3
			if (isBZ) {
				rG4(B, currentEdge);
			}

			if (isBZ && !currentEdge.hasSameValues(edgeCopy)) {
				newEdgesToCheck.add(currentEdge, B, Z, Z, g, true);// true because propagation goes to Z only
			}

			if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
				return getCheckStatus();
			}

			/*
			 * Make all propagation considering current edge as follows:<br>
			 * if (BZ) ==&gt; A--&gt;B--&gt;Z
			 * else ==&gt; Z--&gt;B--&gt;A
			 */
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Apply propagation rules to " + currentEdge.getName());
				}
			}

			for (final CSTNPSUEdge ABorBA : (isBZ) ? g.getInEdges(B) : g.getOutEdges(Objects.requireNonNull(B))) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.log(Level.FINER, "Considering other edge " + ABorBA.getName());
					}
				}
				A = (isBZ) ? g.getSource(ABorBA) : g.getDest(ABorBA);

				AZorZA = (isBZ) ? g.findEdge(A, Z) : g.findEdge(Z, A);

				// I need to preserve the old edge to compare below
				if (AZorZA != null) {
					edgeCopy = g.getEdgeFactory().get(AZorZA);
				} else {
					AZorZA = makeNewEdge(
						(isBZ) ? ((A != null ? A.getName() : null) + "_" + Objects.requireNonNull(Z).getName())
						       : (Objects.requireNonNull(Z).getName() + "_" + (A != null ? A.getName() : null)),
						ConstraintType.derived);
					edgeCopy = null;
				}

				if (isBZ) {
					rG1G3(A, B, Z, ABorBA, currentEdge, AZorZA);
				} else {
					rG5rG6rG7(Z, B, A, currentEdge, ABorBA, AZorZA);
				}

				if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
					return getCheckStatus();
				}

				if (isBZ && B.isContingent()) {
					rG2(A, B, Z, ABorBA, currentEdge, AZorZA);
				}

				boolean add = false;
				if (edgeCopy == null && !AZorZA.isEmpty()) {
					// the new CB has to be added to the graph!
					if (isBZ) {
						g.addEdge(AZorZA, Objects.requireNonNull(A), Objects.requireNonNull(Z));
					} else {
						g.addEdge(AZorZA, Objects.requireNonNull(Z), Objects.requireNonNull(A));
					}
					add = true;
				} else if (edgeCopy != null && !edgeCopy.hasSameValues(AZorZA)) {
					// CB was already present and it has been changed!
					add = true;
				}
				if (add) {
					if (isBZ) {
						assert A != null;
						newEdgesToCheck.add(AZorZA, A, Z, Z, g, true);// true because propagation goes to Z only
					} else {
						newEdgesToCheck.add(AZorZA, Objects.requireNonNull(Z), A, Z, g,
						                    true);// true because propagation goes to Z only
					}
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

		// check guarded-link bounds
		for (final LabeledNode nC : activationNode.keySet()) {
			final LabeledNode nA = getActivationNode(nC);
			if (nA == null) {
				continue;
			}
			final CSTNPSUEdge eAC = g.findEdge(nA, nC);
			final CSTNPSUEdge eCA = g.findEdge(nC, nA);
			final CSTNPSUEdge eAZ = g.findEdge(nA, Z);
			final CSTNPSUEdge eZA = g.findEdge(Z, nA);
			final CSTNPSUEdge eCZ = g.findEdge(nC, Z);
			final CSTNPSUEdge eZC = g.findEdge(Z, nC);
			if (rG8(nA, Z, nC, eAZ, eZC, eAC, eCA)) {
				// eAC has been modified, eAZ and eZC must be added
				newEdgesToCheck.add(eAZ, nA, Z, Z, g, true);// true because propagation goes to Z only
				newEdgesToCheck.add(eZC, Objects.requireNonNull(Z), nC, Z, g,
				                    true);// true because propagation goes to Z only
			}
			if (rG9(nC, Z, nA, eCZ, eZA, eAC, eCA)) {
				// eCA has been modified, eCZ and eZA must be added
				newEdgesToCheck.add(eCZ, nC, Z, Z, g, true);// true because propagation goes to Z only
				newEdgesToCheck.add(eZA, Objects.requireNonNull(Z), nA, Z, g,
				                    true);// true because propagation goes to Z only
			}
			checkGuardedLinkBounds(nA, nC, eAC, eCA, checkStatus);
			if (!checkStatus.consistency) {
				checkStatus.finished = true;
				return getCheckStatus();
			}
		}

		if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
			return getCheckStatus();
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
			lowerContingentLink = new Object2ObjectOpenHashMap<>();
			return;
		}
		activationNode.clear();
		lowerContingentLink.clear();
		prototypalLink = null;
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
	CSTNPSUEdge getLowerContingentLink(LabeledNode nC) {
		return lowerContingentLink.get(nC);
	}

	/**
	 * Applies rules rG1 and rG3 of Table 1 in 'Extending CSTN with partially shrinkable uncertainty' (TIME18).<br>
	 *
	 * <pre>
	 * 1) rG1
	 *        v,ℵ,β           u,◇,α
	 * Z &lt;------------ Y &lt;------------ X
	 * adds
	 *     u+v,ℵ,α★β
	 * Z &lt;------------------------------X
	 * when u+v &lt; 0 and u &lt; 0.
	 * If u &ge; 0, α★β must be αβ
	 * ℵ can be empty.
	 *
	 * 2) rG3
	 *     v,ℵ,β           u,X,α
	 * Z &lt;------------ Y &lt;------------ X
	 * adds
	 *     u+v,Xℵ,α★β
	 * Z &lt;------------------------------X
	 * when u+v &lt; 0 and X ∉ ℵ
	 * ℵ can be empty.
	 * </pre>
	 *
	 * @param nX  node
	 * @param nY  node
	 * @param nZ  node
	 * @param eXY CANNOT BE NULL
	 * @param eYW CANNOT BE NULL
	 * @param eXZ CANNOT BE NULL
	 *
	 * @return true if a reduction is applied at least
	 */
	boolean rG1G3(final LabeledNode nX, final LabeledNode nY, final LabeledNode nZ, final CSTNPSUEdge eXY,
	              final CSTNPSUEdge eYW, final CSTNPSUEdge eXZ) {

		boolean ruleApplied = false;

		final LabeledALabelIntTreeMap YZAllLabeledValueMap = eYW.getAllUpperCaseAndLabeledValuesMaps();
		if (YZAllLabeledValueMap.isEmpty()) {
			return false;
		}

		final Set<Entry<Label>> XYLabeledValueMap = eXY.getLabeledValueSet();
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "rG1G3: start.");
			}
		}

		// 1) rG1
		for (final Entry<Label> entryXY : XYLabeledValueMap) {
			final Label alpha = entryXY.getKey();
			final int u = entryXY.getIntValue();

			for (final ALabel aleph : YZAllLabeledValueMap.keySet()) {
				for (final Entry<Label> entryYW : YZAllLabeledValueMap.get(aleph).entrySet()) {// entrySet read-only
					final Label beta = entryYW.getKey();
					final Label alphaBeta;
					alphaBeta = (u < 0) ? alpha.conjunctionExtended(beta) : alpha.conjunction(beta);
					if (alphaBeta == null) {
						continue;
					}
					final int v = entryYW.getIntValue();
					final int sum = Constants.sumWithOverflowCheck(u, v);
					/*
					 * 2018-07-18. With the sound-and-complete algorithm, positive values are not necessary anymore.
					 */
					if (sum > 0 || (sum == 0 && nZ == nX)) {
						continue;
					}

					final int oldValue =
						(aleph.isEmpty()) ? eXZ.getValue(alphaBeta) : eXZ.getUpperCaseValue(alphaBeta, aleph);

					if ((oldValue != Constants.INT_NULL) && (sum >= oldValue)) {
						// value is stored only if it is more negative than the current one.
						continue;
					}

					String logMsg = null;
					if (Debug.ON) {
						final String oldXW = eXZ.toString();
						logMsg = "CSTNPSU rG1 applied to edge " + oldXW + ":\n" + "Detail: " + nZ.getName() + " <---" +
						         CSTNU.upperCaseValueAsString(aleph, v, beta) + "--- " + nY.getName() + " <---" +
						         CSTNU.upperCaseValueAsString(ALabel.emptyLabel, u, alpha) + "--- " + nX.getName() +
						         "\nresult: " + nZ.getName() + " <---" +
						         CSTNU.upperCaseValueAsString(aleph, sum, alphaBeta) + "--- " + nX.getName() +
						         "; old value: " + Constants.formatInt(oldValue);
					}

					final boolean mergeStatus = (aleph.isEmpty()) ? eXZ.mergeLabeledValue(alphaBeta, sum)
					                                              : eXZ.mergeUpperCaseValue(alphaBeta, aleph, sum);

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

						if (CSTNU.checkAndManageIfNewLabeledValueIsANegativeLoop(sum, nX, nZ, eXZ, checkStatus)) {
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

		// 2) rG3
		if (!nX.isContingent() || getActivationNode(nX) != nY)// the second check is made when nX is contingent!
		{
			return ruleApplied;
		}

		final LabeledIntMap eXYUpperCaseValues = eXY.getUpperCaseValueMap().get(nX.getALabel());
		if (eXYUpperCaseValues != null) {

			for (final Entry<Label> entryXY : eXYUpperCaseValues.entrySet()) {// entrySet read-only
				final Label alpha = entryXY.getKey();
				final int u = entryXY.getIntValue();
				for (final ALabel aleph : YZAllLabeledValueMap.keySet()) {
					if (aleph.contains(nX.getALabel())) {
						continue;
					}
					for (final Entry<Label> entryYW : YZAllLabeledValueMap.get(aleph).entrySet()) {// entrySet read-only
						final Label beta = entryYW.getKey();
						final Label alphaBeta = alpha.conjunctionExtended(beta);
//						if (alphaBeta == null) {
//							continue;
//						}
						final ALabel newXAleph = nX.getALabel().conjunction(aleph);
						final int v = entryYW.getIntValue();

						final int sum = Constants.sumWithOverflowCheck(u, v);
						if (sum > 0) {
							continue;
						}

						final int oldValue = eXZ.getUpperCaseValue(alphaBeta, newXAleph);
						if ((oldValue != Constants.INT_NULL) && (sum >= oldValue)) {
							continue;
						}

						String logMsg = null;
						if (Debug.ON) {
							final String oldXW = eXZ.toString();
							logMsg =
								"CSTNPSU rG3 applied to edge " + oldXW + ":\n" + "Detail: " + nZ.getName() + " <---" +
								CSTNU.upperCaseValueAsString(aleph, v, beta) + "--- " + nY.getName() + " <---" +
								CSTNU.upperCaseValueAsString(nX.getALabel(), u, alpha) + "--- " + nX.getName() +
								"\nresult: " + nZ.getName() + " <---" +
								CSTNU.upperCaseValueAsString(newXAleph, sum, alphaBeta) + "--- " + nX.getName() +
								"; old value: " + Constants.formatInt(oldValue);
						}

						final boolean mergeStatus = eXZ.mergeUpperCaseValue(alphaBeta, newXAleph, sum);
						if (mergeStatus) {
							ruleApplied = true;
							getCheckStatus().zExclamationRuleCalls++;
							if (Debug.ON) {
								if (LOG.isLoggable(Level.FINER)) {
									LOG.log(Level.FINER, logMsg);
								}
							}

							if (CSTNU.checkAndManageIfNewLabeledValueIsANegativeLoop(sum, nX, nZ, eXZ, checkStatus)) {
								if (Debug.ON) {
									if (LOG.isLoggable(Level.INFO)) {
										LOG.log(Level.INFO, logMsg);
									}
								}
								return true;
							}

							//
							// FIXME clean redundant aleph
							// if (newXAleph.size() > 1) {
							// ALabel[] alephSet = eXZ.getUpperCaseValueMap().keySet().toArray(new ALabel[0]);
							// if (alephSet.length == 0)
							// continue;
							// for (ALabel aleph1 : alephSet) {
							// if (aleph1.contains(nX.getAlabel()))
							// continue;
							// if (newXAleph.contains(aleph1)) {
							// eXZ.getUpperCaseValueMap().remove(aleph1);
							// continue;
							// }
							// }
							// }
						}
					}
				}
			}
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "rG1rG3: end.");
			}
		}
		return ruleApplied;
	}

	/**
	 * Applies {@code rG2} rule.
	 *
	 * <pre>
	 *     v,ℵ,β           u,c,α
	 * Z &lt;------------ C &lt;------------ A
	 * adds
	 *             u+v,ℵ,α★β
	 * Z &lt;----------------------------A
	 *
	 * if C ∉ ℵ and v+u &lt; 0.
	 * </pre>
	 *
	 * @param nA  node
	 * @param nC  none
	 * @param nZ  none
	 * @param eAC CANNOT BE NULL
	 * @param eCZ CANNOT BE NULL
	 * @param eAZ CANNOT BE NULL
	 *
	 * @return true if the rule has been applied.
	 */
	boolean rG2(final LabeledNode nA, final LabeledNode nC, final LabeledNode nZ, final CSTNPSUEdge eAC,
	            final CSTNPSUEdge eCZ, final CSTNPSUEdge eAZ) {

		boolean ruleApplied = false;
		if (activationNode.get(nC) != nA) {
			return false;
		}

		final LabeledIntMap lowerCaseValueMap = eAC.getLowerCaseValueMap().get(nC.getALabel());
		if (lowerCaseValueMap == null || lowerCaseValueMap.isEmpty()) {
			return false;
		}

		final LabeledALabelIntTreeMap CZAllValueMap = eCZ.getAllUpperCaseAndLabeledValuesMaps();
		if (CZAllValueMap.isEmpty()) {
			return false;
		}

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "rG2: start.");
			}
		}

		// for (ALabel c : lowerCaseValueMap.keySet()) {
		for (final Entry<Label> entryLowerCase : lowerCaseValueMap.entrySet()) {
			final Label alpha = entryLowerCase.getKey();
			final int u = entryLowerCase.getIntValue();
			for (final ALabel aleph : CZAllValueMap.keySet()) {
				final LabeledIntMap czValuesMap = CZAllValueMap.get(aleph);
				if (czValuesMap == null) {
					continue;
				}
				if (aleph.contains(nC.getALabel())) {
					continue;// Rule condition: upper case label cannot be equal or contain c name
				}
				final boolean emptyAleph = aleph.isEmpty();
				for (final Entry<Label> entryCZ : czValuesMap.entrySet()) {// entrySet read-only
					final int v = entryCZ.getIntValue();
					final int sum = Constants.sumWithOverflowCheck(v, u);
					if (sum >= 0) {
						continue;
					}

					final Label beta = entryCZ.getKey();
					final Label alphaBeta = beta.conjunctionExtended(alpha);
//					if (alphaBeta == null) {
//						continue;
//					}

					final int oldValue =
						(emptyAleph) ? eAZ.getValue(alphaBeta) : eAZ.getUpperCaseValue(alphaBeta, aleph);

					if (oldValue != Constants.INT_NULL && oldValue <= sum) {
						continue;
					}
					String logMsg = null;
					if (Debug.ON) {
						final String oldAX = eAZ.toString();
						logMsg = "rG2 applied to edge " + oldAX + ":\nDetail: " + nZ.getName() + " <---" +
						         CSTNU.upperCaseValueAsString(aleph, v, beta) + "--- " + nC.getName() + " <---" +
						         CSTNU.lowerCaseValueAsString(nC.getALabel(), u, alpha) + "--- " + nA.getName() +
						         "\nresult: " + nZ.getName() + " <---" +
						         CSTNU.upperCaseValueAsString(aleph, sum, alphaBeta) + "--- " + nA.getName() +
						         "; oldValue: " + Constants.formatInt(oldValue);
					}

					final boolean localApp = (emptyAleph) ? eAZ.mergeLabeledValue(alphaBeta, sum)
					                                      : eAZ.mergeUpperCaseValue(alphaBeta, aleph, sum);

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

					if (CSTNU.checkAndManageIfNewLabeledValueIsANegativeLoop(sum, nA, nZ, eAZ, checkStatus)) {
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
		// }
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "rG2: end.");
			}
		}
		return ruleApplied;
	}

	/**
	 * Applies {@code rG4} rule.
	 * <p>
	 * Overrides {@link CSTNU#zLabeledLetterRemovalRule(LabeledNode, CSTNUEdge)} considering guarded links instead of
	 * contingent ones.
	 *
	 * <pre>
	 * Y ---(v,ℵ,β)---&gt; Z &lt;---(w,ℵ1,αl)--- A ---(x',c,l)---&gt; C
	 *                                             &lt;---(-x,◇,l)---
	 * adds
	 *
	 * Y ---(m,ℵℵ1,β*(αl))---&gt; Z
	 *
	 * if m = max(v, w + (-x))
	 * </pre>
	 *
	 * @param nY  none
	 * @param eYZ none
	 *
	 * @return true if the reduction has been applied.
	 */
	boolean rG4(final LabeledNode nY, final CSTNPSUEdge eYZ) {
		boolean ruleApplied = false;

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "zLR: start.");
			}
		}
		final LabeledNode Z = g.getZ();
		for (final ALabel aleph : eYZ.getUpperCaseValueMap().keySet()) {
			final LabeledIntMap YZvaluesMap = eYZ.getUpperCaseValueMap().get(aleph);
			if (YZvaluesMap == null) {
				continue;
			}
			for (final Label beta : YZvaluesMap.keySet()) {
				final int v = eYZ.getUpperCaseValue(beta, aleph);
				if (v == Constants.INT_NULL) {
					continue;
				}
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
					final CSTNPSUEdge AC = getLowerContingentLink(nC);
					assert nC != null;
					final Label guardedLinkLabel = nC.getLabel().conjunction(nA.getLabel());
					final int lowerCaseEntry = AC.getLowerCaseValue(guardedLinkLabel, nC.getALabel());
					if (lowerCaseEntry == Constants.INT_NULL) {
						continue;
					}
					final CSTNPSUEdge CA = g.findEdge(nC, nA);
					assert CA != null;
					final int x = CA.getValue(
						guardedLinkLabel);// guarded link, x must be the lower bound, not the lower guard lowerCaseEntry.getValue();
					if (x == Constants.INT_NULL) {
						continue;
					}
					final CSTNPSUEdge AZ = g.findEdge(nA, Z);

					assert AZ != null;
					for (final ALabel aleph1 : AZ.getAllUpperCaseAndLabeledValuesMaps().keySet()) {
						if (aleph1.contains(nodeLetter)) {
							continue;
						}
						final LabeledIntMap AZAlephMap = AZ.getAllUpperCaseAndLabeledValuesMaps().get(aleph1);
						if (AZAlephMap == null) {
							continue;
						}
						for (final Entry<Label> entryAZ : AZAlephMap.entrySet()) {
							final Label alpha = entryAZ.getKey();
							final int w = entryAZ.getIntValue();

							if (!alpha.subsumes(guardedLinkLabel)) {
								continue;// rule condition
							}

							final int m = Math.max(v, w + x);// lower bound x of a guarded link is already negative!

							final ALabel alephAleph1 = aleph.conjunction(aleph1);
							assert alephAleph1 != null;
							alephAleph1.remove(nodeLetter);

							final Label alphaBeta = alpha.conjunctionExtended(beta);

							final int oldValue = (Debug.ON) ? eYZ.getUpperCaseValue(alphaBeta, alephAleph1) : -1;
							final String oldYZ = (Debug.ON) ? eYZ.toString() : "";

							final boolean mergeStatus = (alephAleph1.isEmpty()) ? eYZ.mergeLabeledValue(alphaBeta, m)
							                                                    : eYZ.mergeUpperCaseValue(alphaBeta,
							                                                                              alephAleph1,
							                                                                              m);

							if (mergeStatus) {
								ruleApplied = true;
								getCheckStatus().letterRemovalRuleCalls++;
								if (Debug.ON) {
									if (LOG.isLoggable(Level.FINER)) {
										LOG.log(Level.FINER,
										        "CSTNPSU rG4 applied to edge " + oldYZ + ":\n" + "Detail: " +
										        nY.getName() + "---" + CSTNU.upperCaseValueAsString(aleph, v, beta) +
										        "---> Z <---" + CSTNU.upperCaseValueAsString(aleph1, w, alpha) +
										        "--- " + nA.getName() + "---" +
										        CSTNU.lowerCaseValueAsString(nC.getALabel(), x, guardedLinkLabel) +
										        "---> " + nodeLetter + "\nresult: " + nY.getName() + "---" +
										        CSTNU.upperCaseValueAsString(alephAleph1, m, alphaBeta) + "---> Z" +
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
				LOG.log(Level.FINER, "rG4: end.");
			}
		}
		return ruleApplied;
	}

	/**
	 * Applies rules {@code rG5 and rG6 and rG7} of Table 2 paper submitted to CP20.<br>
	 *
	 * <pre>
	 * 1) rG5
	 *      v,ד,β              u,◇,α
	 * Z ------------&gt; X ------------&gt; Y
	 * adds
	 *      u+v,ד,α★β
	 * Z ------------------------------&gt; Y
	 * when u+v &ge; 0. If (u &lt; 0), α★β must be αβ
	 * ד can be empty.
	 *
	 * 2) rG6
	 *     v,ד,β               u,y,α
	 * Z ------------&gt; X ------------&gt; Y
	 * adds
	 *     u+v,yד,α★β
	 * Z ------------------------------&gt; Y
	 * when u+v &ge; 0.
	 * ד can be empty. If not empty, y not in ד.
	 *
	 * 2) rG7
	 *     v,ד,β               -u,X,α
	 * Z ------------&gt; X ------------&gt; Y
	 * adds
	 *     -u+v,xד,α★β
	 * Z ------------------------------&gt; Y
	 * when u+v &ge; 0, ד can be empty and X not in ד
	 * </pre>
	 *
	 * @param nX  node
	 * @param nY  node
	 * @param nZ  node
	 * @param eZX CANNOT BE NULL
	 * @param eXY CANNOT BE NULL
	 * @param eZY CANNOT BE NULL
	 *
	 * @return true if a reduction is applied at least
	 */
	boolean rG5rG6rG7(final LabeledNode nZ, final LabeledNode nX, final LabeledNode nY, final CSTNPSUEdge eZX,
	                  final CSTNPSUEdge eXY, final CSTNPSUEdge eZY) {
		boolean ruleApplied = false;
		String logMsg = null;
		final LabeledALabelIntTreeMap ZXLowerCAndLabeledValueMap = eZX.getAllLowerCaseAndLabeledValuesMaps();
		if (ZXLowerCAndLabeledValueMap.isEmpty()) {
			return false;
		}

		final Set<Entry<Label>> XYLabeledValueMap = eXY.getLabeledValueSet();
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "rG5rG6rG7: start.");
			}
		}

		// 1) rG5
		for (final Entry<Label> entryXY : XYLabeledValueMap) {
			final Label alpha = entryXY.getKey();
			final int u = entryXY.getIntValue();

			for (final ALabel dalet : ZXLowerCAndLabeledValueMap.keySet()) {
				for (final Entry<Label> entryZX : ZXLowerCAndLabeledValueMap.get(dalet)
					.entrySet()) {// entrySet read-only
					final Label beta = entryZX.getKey();
					final Label alphaBeta;
					alphaBeta = (u >= 0) ? alpha.conjunctionExtended(beta) : alpha.conjunction(beta);
					if (alphaBeta == null) {
						continue;
					}
					final int v = entryZX.getIntValue();
					final int sum = Constants.sumWithOverflowCheck(u, v);
					final int oldValue =
						(dalet.isEmpty()) ? eZY.getValue(alphaBeta) : eZY.getLowerCaseValue(alphaBeta, dalet);
					if (Debug.ON) {
						final String oldZY = eZY.toString();
						logMsg = "CSTNPSU rG5 applied to edge " + oldZY + ":\n" + "Detail: " + nZ.getName() + " ---" +
						         CSTNU.lowerCaseValueAsString(dalet, v, beta) + "---> " + nX.getName() + " ---" +
						         CSTNU.lowerCaseValueAsString(ALabel.emptyLabel, u, alpha) + "---> " + nY.getName() +
						         "\nresult: " + nZ.getName() + " ---" +
						         CSTNU.lowerCaseValueAsString(dalet, sum, alphaBeta) + "---> " + nY.getName() +
						         "; old value: " + Constants.formatInt(oldValue);
					}
					if (CSTNU.checkAndManageIfNewLabeledValueIsANegativeLoop(sum, nZ, nY, eZY, checkStatus)) {
						if (Debug.ON) {
							if (LOG.isLoggable(Level.INFO)) {
								LOG.log(Level.INFO, logMsg);
							}
						}
						return true;
					}

					if ((sum >= 0 && nZ == nY) || ((oldValue != Constants.INT_NULL) && (sum >= oldValue))) {
						// value is stored only if it is smaller than the current one.
						continue;
					}

					final boolean mergeStatus = (dalet.isEmpty()) ? eZY.mergeLabeledValue(alphaBeta, sum)
					                                              : eZY.mergeLowerCaseValue(alphaBeta, dalet, sum);

					if (mergeStatus) {
						ruleApplied = true;
						// if (aleph.isEmpty()) {
						// this.checkStatus.labeledValuePropagationCalls++;
						// } else {
						getCheckStatus().zExclamationRuleCalls++;
						// }
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								LOG.log(Level.FINER, logMsg);
							}
						}
					}
				}
			}
		}

		// 2) rG6
		if (nY.isContingent() && getActivationNode(nY) == nX) {

			final LabeledIntMap eXYLowerCaseValues = eXY.getLowerCaseValueMap().get(nY.getALabel());
			if (eXYLowerCaseValues != null) {
				for (final Entry<Label> entryXY : eXYLowerCaseValues.entrySet()) {
					final Label alpha = entryXY.getKey();
					final int u = entryXY.getIntValue();
					for (final ALabel dalet : ZXLowerCAndLabeledValueMap.keySet()) {
						if (dalet.contains(nY.getALabel())) {
							continue;
						}
						for (final Entry<Label> entryZX : ZXLowerCAndLabeledValueMap.get(dalet)
							.entrySet()) {// It should be one!
							final Label beta = entryZX.getKey();
							Label alphaBeta = alpha.conjunctionExtended(beta);
							if (alpha.isEmpty()) {
								alphaBeta = beta;
							}
//							if (alphaBeta == null) {
//								continue;
//							}
							final int v = entryZX.getIntValue();
							final int sum = Constants.sumWithOverflowCheck(u, v);
							final ALabel newXAleph = nY.getALabel().conjunction(dalet);
							final int oldValue = eZY.getLowerCaseValue(alphaBeta, newXAleph);

							if (Debug.ON) {
								final String oldZY = eZY.toString();
								logMsg = "CSTNPSU rG6 applied to edge " + oldZY + ":\n" + "Detail: " + nZ.getName() +
								         " ---" + CSTNU.lowerCaseValueAsString(dalet, v, beta) + "---> " +
								         nX.getName() + " ---" +
								         CSTNU.lowerCaseValueAsString(nY.getALabel(), u, alpha) + "---> " +
								         nY.getName() + "\nresult: " + nZ.getName() + " ---" +
								         CSTNU.lowerCaseValueAsString(newXAleph, sum, alphaBeta) + "---> " +
								         nY.getName() + "; old value: " + Constants.formatInt(oldValue);
							}
							if (CSTNU.checkAndManageIfNewLabeledValueIsANegativeLoop(sum, nZ, nY, eZY, checkStatus)) {
								if (Debug.ON) {
									if (LOG.isLoggable(Level.INFO)) {
										LOG.log(Level.INFO, logMsg);
									}
								}
								return true;
							}

							if ((sum >= 0 && nZ == nY) || (oldValue != Constants.INT_NULL) && (sum >= oldValue)) {
								continue;
							}

							final boolean mergeStatus = eZY.mergeLowerCaseValue(alphaBeta, newXAleph, sum);
							if (mergeStatus) {
								ruleApplied = true;
								getCheckStatus().zExclamationRuleCalls++;
								if (Debug.ON) {
									if (LOG.isLoggable(Level.FINER)) {
										LOG.log(Level.FINER, logMsg);
									}
								}

								// FIXME clean redundant aleph
								// if (newXAleph.size() > 1) {
								// ALabel[] alephSet = eZY.getLowerCaseValueMap().keySet().toArray(new ALabel[0]);
								// if (alephSet.length == 0)
								// continue;
								// for (ALabel aleph1 : alephSet) {
								// if (aleph1.contains(nY.getAlabel()))
								// continue;
								// if (newXAleph.contains(aleph1)) {
								// if (Debug.ON) {
								// final String oldZY = eZY.toString();
								// logMsg = "CSTNPSU rG7 removal applied to edge " + oldZY + ":\n" + "Detail: "
								// + "Removing " + aleph1;
								// }
								// eZY.getLowerCaseValueMap().remove(aleph1);
								// if (Debug.ON) {
								// logMsg += "\nNew edge " + eZY;
								// if (LOG.isLoggable(Level.FINER)) {
								// LOG.log(Level.FINER, logMsg);
								// }
								// }
								// continue;
								// }
								// }
								// }
							}
						}
					}
				}
			}
		}

		// 2) rG7
		if (nX.isContingent() && getActivationNode(nX) == nY) {
			final LabeledIntMap eXYUpperCaseValue = eXY.getUpperCaseValueMap().get(nX.getALabel());
			if (eXYUpperCaseValue != null) {
				for (final Entry<Label> entryXY : eXYUpperCaseValue.entrySet()) {// entrySet read-only
					final Label alpha = entryXY.getKey();
					final int u = entryXY.getIntValue();
					for (final ALabel dalet : ZXLowerCAndLabeledValueMap.keySet()) {
						if (dalet.contains(nX.getALabel())) {
							continue;
						}
						for (final Entry<Label> entryZX : ZXLowerCAndLabeledValueMap.get(dalet)
							.entrySet()) {// it should be only one!
							final Label beta = entryZX.getKey();
							final Label alphaBeta = alpha.conjunctionExtended(beta);
//							if (alphaBeta == null) {
//								continue;
//							}
							final int v = entryZX.getIntValue();
							final int sum = Constants.sumWithOverflowCheck(u, v);
							// ALabel newDalet = dalet.conjunction(nX.getAlabel());
							final int oldValue = eZY.getLowerCaseValue(alphaBeta, dalet);
							if (Debug.ON) {
								final String oldZY = eZY.toString();
								logMsg = "CSTNPSU rG7 applied to edge " + oldZY + ":\n" + "Detail: " + nZ.getName() +
								         " ---" + CSTNU.lowerCaseValueAsString(dalet, v, beta) + "---> " +
								         nX.getName() + " ---" +
								         CSTNU.upperCaseValueAsString(nX.getALabel(), u, alpha) + "---> " +
								         nY.getName() + "\nresult: " + nZ.getName() + " ---" +
								         CSTNU.lowerCaseValueAsString(dalet, sum, alphaBeta) + "---> " + nY.getName() +
								         "; old value: " + Constants.formatInt(oldValue);
							}

							if (CSTNU.checkAndManageIfNewLabeledValueIsANegativeLoop(sum, nZ, nY, eZY, checkStatus)) {
								if (Debug.ON) {
									if (LOG.isLoggable(Level.INFO)) {
										LOG.log(Level.INFO, logMsg);
									}
								}
								return true;
							}

							if ((sum >= 0 && nZ == nY) || (oldValue != Constants.INT_NULL) && (sum >= oldValue)) {
								continue;
							}

							final boolean mergeStatus = (dalet.isEmpty()) ? eZY.mergeLabeledValue(alphaBeta, sum)
							                                              : eZY.mergeLowerCaseValue(alphaBeta, dalet,
							                                                                        sum);

							if (mergeStatus) {
								ruleApplied = true;
								getCheckStatus().zExclamationRuleCalls++;
								if (Debug.ON) {
									if (LOG.isLoggable(Level.FINER)) {
										LOG.log(Level.FINER, logMsg);
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
				LOG.log(Level.FINER, "rG5rG6rG7: end.");
			}
		}
		return ruleApplied;
	}

	/**
	 * Implements the CSTNPSU rM1 rule assuming instantaneous reaction and a streamlined network.<br>
	 * <b>This differs from
	 * {@link CSTN#labelModificationR0qR0(LabeledNode, LabeledNode, CSTNEdge)} in the checking also upper case
	 * value</b>
	 *
	 * @param nObs the observation node
	 * @param ePZ  the edge connecting P? ---&gt; Z
	 *
	 * @return true if the rule has been applied one time at least.
	 */
	boolean rM1(final LabeledNode nObs, final CSTNPSUEdge ePZ) {

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
							" ---" + CSTNU.upperCaseValueAsString(aleph, w, alpha) + "---> " +
							Objects.requireNonNull(Z).getName() + "\nresult: " + nObs.getName() + " ---" +
							CSTNU.upperCaseValueAsString(aleph, w, alphaPrime) + "---> " + Z.getName();
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
	 * Implements the CSTNPSU rM2 rule assuming instantaneous reaction and a streamlined network.<br>
	 * <b>This differs from
	 * {@link CSTNIR3RwoNodeLabels#labelModificationR3qR3(LabeledNode, LabeledNode, CSTNEdge)} in the checking also
	 * upper case value.</b>
	 *
	 * @param nS  node
	 * @param eSZ CSTNPSUEdge containing the constraint to modify
	 *
	 * @return true if a rule has been applied.
	 */
	boolean rM2(final LabeledNode nS, final CSTNPSUEdge eSZ) {
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "rM2: start.");
			}
		}
		boolean ruleApplied = false;
		final LabeledNode Z = g.getZ();
		final ObjectList<CSTNPSUEdge> Obs2ZEdges = getEdgeFromObserversToNode(Z);

		final LabeledALabelIntTreeMap allValueMapSZ = eSZ.getAllUpperCaseAndLabeledValuesMaps();
		if (allValueMapSZ.isEmpty()) {
			return false;
		}

		final ObjectSet<Label> SZLabelSet = eSZ.getLabeledValueMap().keySet();
		SZLabelSet.addAll(eSZ.getUpperCaseValueMap().labelSet());// it adds all the labels from upper-case values!

		Label allLiteralsSZ = Label.emptyLabel;
		for (final Label l : SZLabelSet) {
			allLiteralsSZ = allLiteralsSZ.conjunctionExtended(l);
//			if (allLiteralsSZ == null) {
//				break;
//			}
		}

		// check each edge from an observator to Z.
		for (final CSTNPSUEdge eObsZ : Obs2ZEdges) {
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
				for (final Entry<Label> entryObsZ : allValueMapObsZ.get(aleph1).entrySet()) {// entrySet read-only
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
										        "rM2 adds a labeled value to edge " + eSZ.getName() + ":\n" +
										        "source: " + nObs.getName() + " ---" +
										        CSTNU.upperCaseValueAsString(aleph1, w, gamma) + "---> " +
										        Objects.requireNonNull(Z).getName() + " <---" +
										        CSTNU.upperCaseValueAsString(aleph, v, SZLabel) + "--- " +
										        nS.getName() + "\nresult: add " + Z.getName() + " <---" +
										        CSTNU.upperCaseValueAsString(newUpperCaseLetter, max, newLabel) +
										        "--- " + nS.getName());
									}
								}
								ruleApplied = true;
								checkStatus.r3calls++;
							}
						}
					}
				}
			}
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "rM2: end.");
			}
		}
		return ruleApplied;
	}
}
