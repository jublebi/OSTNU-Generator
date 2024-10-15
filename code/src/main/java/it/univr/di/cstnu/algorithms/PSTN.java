// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

package it.univr.di.cstnu.algorithms;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.*;
import it.univr.di.Debug;
import it.univr.di.cstnu.algorithms.STN.STNCheckStatus;
import it.univr.di.cstnu.graph.Edge.ConstraintType;
import it.univr.di.cstnu.graph.*;
import it.univr.di.cstnu.graph.STNUEdge.CaseLabel;
import it.univr.di.cstnu.util.LogNormalDistributionParameter;
import it.univr.di.cstnu.util.OptimizationEngine;
import it.univr.di.cstnu.visualization.CSTNUStaticLayout;
import it.univr.di.labeledvalue.ALabelAlphabet.ALetter;
import it.univr.di.labeledvalue.Constants;
import it.univr.di.labeledvalue.Label;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Simple class to represent a Probabilistic Simple Temporal Constraint Networks (PSTN), where the edge weight are signed integer.
 * <br>
 * <b>Beta Version</b>
 * On 2024-06-13 it is not clear whether this class would be developed to become a full-fledged model or not. Therefore, at this day, I do not extend the
 * TNGraphML* classes to represent such kind of network.
 * <br>
 * Inside this classe, the log-normal parameters associated to each contingent link are represented has a hash-map that is not saved on file.<br> The only
 * experiment we are planning to do is to load STNU instances, transform them in PSTN changing all contingent links in probabilistic contingent links, and find
 * an STNU approximation that guarantee the dynamic controllability of the original PSTN with the maximum probability.
 *
 * @author Roberto Posenato
 * @version $Rev: 878 $
 */
public class PSTN {

	/**
	 * Extends STNCheckStatus to represent result of the approximating STNU.
	 */
	public static class PSTNCheckStatus extends STNCheckStatus {
		/**
		 *
		 */
		@Serial
		static public final long serialVersionUID = 1L;
		/**
		 * DC STNU obtained setting contingent link bounds such than the conjunct probability mass is {@link #probabilityMass}.
		 */
		transient public STNU approximatingSTNU;
		/**
		 * xit flag of the minimization procedure.
		 */
		public int exitFlag;
		/**
		 * the conjuncted probability mass obtained setting the contingent bounds in {@link #approximatingSTNU}.
		 */
		public double probabilityMass = -1;
		/**
		 * Possible semi-reducible negative cycle;
		 */
		public ObjectImmutableList<STNUEdge> srn;
		/**
		 * Kind of SRNC
		 */
		STNU.STNUCheckStatus.SRNCKind srncKind;

		/**
		 * @return the approximating STNU, null if there is no, or it is not determined.
		 */
		public STNU getApproximatingSTNU() {
			return approximatingSTNU;
		}

		/**
		 * @return the conjunct probability mass
		 */
		public double getProbabilityMass() {
			return probabilityMass;
		}

		/**
		 * @return the negative semi-reducible cycle (if it has been determined).
		 */
		public ObjectImmutableList<STNUEdge> getSrn() {
			return srn;
		}

		/**
		 * @return the kind of the possible found srnc.
		 */
		public STNU.STNUCheckStatus.SRNCKind getSrncKind() {
			return srncKind;
		}

		/**
		 * @return the status of a check with all determined index values.
		 */
		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder(160);
			sb.append("The check is");
			if (!finished) {
				sb.append(" NOT");
			}
			sb.append(" finished after ").append(cycles).append(" cycle(s).\n");
			if (finished) {
				sb.append("The consistency check has determined that given network is ");
				if (!consistency) {
					sb.append("NOT ");
				}
				sb.append("consistent.\n");
			}
			if (timeout) {
				sb.append("The checking has been interrupted because execution time exceeds the given time limit.\n");
			}
			if (!consistency && negativeCycle != null) {
				sb.append("The negative cycle is ").append(negativeCycle).append("\n");
			}
			if (executionTimeNS != Constants.INT_NULL) {
				sb.append("The global execution time has been ").append(executionTimeNS).append(" ns (~")
					.append((executionTimeNS / 1E9)).append(" s.)");
			}
			if (this.probabilityMass >= 0) {
				sb.append("Found an approximating STNU that captures ").append(this.probabilityMass).append(" of the contingent possible values.");
			}
			if (!note.isEmpty()) {
				sb.append("\n").append("Note: ").append(note);
			}
			return sb.toString();
		}
	}


	/**
	 * Suffix for file name
	 */
	public static final String FILE_NAME_SUFFIX = ".pstn";
	/**
	 * The name for the initial node.
	 */
	public static final String ZERO_NODE_NAME = "Z";

	/**
	 * Version of the class
	 */
//	static final String VERSIONandDATE = "Version 0.1 - June 13, 2024";
	static final String VERSIONandDATE = "Version 0.2 - October 04, 2024"; //remove the MatLab direct dependency.

	/**
	 * Logger of the class.
	 */
	private static final Logger LOG = Logger.getLogger(PSTN.class.getName());

	/**
	 * Patter for the file name suffix.
	 */
	private static final Pattern COMPILE = Pattern.compile(FILE_NAME_SUFFIX + "$");

	/**
	 * @return the version
	 */
	public static String getVersionAndDate() {
		return VERSIONandDATE;
	}

	/**
	 * Factor used to determine the bounds of a contingent link from location and std of the log-normal distribution in {@link #buildApproxSTNU()}
	 */
	double rangeFactor;

	/**
	 * Check status.
	 */
	@SuppressWarnings("FieldMayBeFinal")
	private STNU.STNUCheckStatus checkStatus;//it cannot be final because I introduced the copy constructor.

	/**
	 * Map {@code (contingentNode, activationNode)} initialized by {@link #initAndCheck()}
	 */
	private Object2ObjectMap<LabeledNode, LabeledNode> activationNode;

	/**
	 * The input file containing the STN graph in GraphML format.
	 */
	@Argument(usage = "file_name must be the input STNU graph in GraphML format.", metaVar = "file_name")
	private File fInput;

	/**
	 * Output file where to write the XML representing the minimal STN graph.
	 */
	@Option(name = "-o", aliases = "--output", usage = "Output to this file. If file is already present, it is overwritten. If this parameter is not present, then the output is sent to the std output.", metaVar = "output_file_name")
	private File fOutput;

	/**
	 * Input TNGraph.
	 */
	private TNGraph<STNUEdge> g;
	/**
	 * Horizon value. A node that has to be executed after such time means that it has not to be executed!
	 */
	private int horizon;

	/**
	 * Average width of contingent ranges
	 */
	private int avgContingentRangeWidth;

	/**
	 * Utility map that returns the edge containing the lower case constraint of a contingent link given the contingent time point.
	 * <p>
	 * In other words, if there is a contingent link {@code (A, 1, 3, C)}, `lowerContingentEdge` contains {@code C --> (A, c(1), C)}.
	 */
	private Object2ObjectMap<LabeledNode, STNUEdge> lowerContingentEdge;

	/**
	 * Absolute value of the max negative weight determined during initialization phase.
	 */
	private int maxWeight;

	/**
	 *
	 */
	@Option(name = "-save", usage = "Save the checked instance.")
	private boolean save;

	/**
	 * Timeout in seconds for the check.
	 */
	@Option(name = "-t", aliases = "--timeOut", usage = "Timeout in seconds for the check", metaVar = "seconds")
	private int timeOut;

	/**
	 * Used by {@link #buildApproxSTNU()}
	 */
	private OptimizationEngine optimizationEngine;

	/**
	 * Utility map that return the edge containing the upper-case value of a contingent link given the contingent timepoint.
	 * <p>
	 * In other words, if there is a contingent link {@code (A, 1, 3, C)}, `upperContingentEdge` contains {@code C --> (C, C:-3, A)}.
	 */
	private Object2ObjectMap<LabeledNode, STNUEdge> upperContingentEdge;

	/**
	 * Software Version.
	 */
	@Option(name = "-v", aliases = "--version", usage = "Version")
	private boolean versionReq;

	/**
	 * See {@link PSTN#PSTN(TNGraph, int, double, OptimizationEngine)}
	 *
	 * @param graph              TNGraph to check
	 * @param expanderFactor     factor to amplify each edge value
	 * @param sigmaFactor        factor to determine σ.
	 * @param rangeFactor        rangeFactor to use for determining the first contingent ranges in the approximating STNU for the check
	 * @param optimizationEngine Optimization engine for buildApproxSTNU will be used. It can be null.
	 */
	public PSTN(TNGraph<STNUEdge> graph, int expanderFactor, double sigmaFactor, double rangeFactor, OptimizationEngine optimizationEngine) {
		this(graph, expanderFactor, sigmaFactor, optimizationEngine);
		this.rangeFactor = rangeFactor;
	}

	/**
	 * Given a STNU graph, it uses it as a base for it (does not make a copy).
	 * <p>
	 * For each contingent link, it multiplies each edge value by {@code expanderFactor} to guarantee that the log-normal distribution built for each contingent
	 * link allows the determination of integer values.
	 * </p>
	 * <p>
	 * For each contingent link, it associates to its contingent node a pair {@code (μ, σ)} to represent the log-normal distribution associated to the
	 * contingent link. {@code μ} is the log-normale location and {@code σ} is the standard deviation. They are determined as follow.<br> Let {@code x, y} the
	 * (updated) bounds of a contingent link. Let {@code M = (x+y)/2, S= (y-x)/2 * #sigmaFactor}. Then, {@code μ = ln(M^2/(sqrt(M^2+S^2))} and
	 * {@code σ = ln(1+ S^2/M^2)}.
	 * </p>
	 * This method executes {@link #initAndCheck()} on the input graph.
	 *
	 * @param graph              TNGraph to check
	 * @param expanderFactor     factor to amplify each edge value
	 * @param sigmaFactor        factor to determine σ.
	 * @param optimizationEngine Optimization engine for buildApproxSTNU will be used. It can be null.
	 *
	 * @throws IllegalArgumentException if any error occurs during initial {@link #initAndCheck()}.
	 */
	public PSTN(TNGraph<STNUEdge> graph, int expanderFactor, double sigmaFactor, OptimizationEngine optimizationEngine) {
		this();
		this.optimizationEngine = optimizationEngine;
		if (expanderFactor <= 0 || sigmaFactor <= 0) {
			throw new IllegalArgumentException("contingentBoundsExpanderFactor and sigmaFactor must be positive.");
		}
		setG(graph);
		try {
			initAndCheck();
		} catch (WellDefinitionException e) {
			throw new IllegalArgumentException("Graph is not a STNU: " + e.getMessage());
		}
		final Object2ObjectMap<LabeledNode, STNUEdge> UCEdge = this.getUpperCaseEdgesMap();
		final Object2ObjectMap<LabeledNode, STNUEdge> LCEdge = this.getLowerCaseEdgesMap();
		if (UCEdge == null || UCEdge.isEmpty() || LCEdge == null || LCEdge.isEmpty()) {
			throw new IllegalArgumentException("Graph is not a STNU because has no contingent link.");
		}
		final ObjectSet<LabeledNode> ctgSet = UCEdge.keySet();
		for (final LabeledNode ctg : ctgSet) {
			int y = UCEdge.get(ctg).getLabeledValue() * expanderFactor;
			UCEdge.get(ctg).setLabeledValue(ctg.getALetter(), y, true);
			y = -y;
			final int x = LCEdge.get(ctg).getLabeledValue() * expanderFactor;
			LCEdge.get(ctg).setLabeledValue(ctg.getALetter(), x, false);

			/*
			 * Luke proposal
			 */
			final double M2 = Math.pow((x + y) / 2.0, 2.0);
			final double S2 = Math.pow((y - x) / 2.0 * sigmaFactor, 2.0);
			final double mu = Math.log(M2 / Math.sqrt(M2 + S2));
			final double sigma = Math.sqrt(Math.log(1 + S2 / M2));
			/*
			 * "A lognormal approximation of activity duration in PERT using two time estimates" paper proposal
			 */
//			final double middlePoint = (x + y) / 2.0;
//			final double sigma = 0.5 * sigmaFactor - Math.sqrt( 0.25 * Math.pow(sigmaFactor, 2) + Math.log(x/middlePoint) );
//			final double mu = Math.log(x) + sigmaFactor * sigma;

			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("For contingent link " + UCEdge.get(ctg) + "; " + LCEdge.get(ctg)
					          + ", the log-normal distribution determined is location: " + mu + " and scale: " + sigma);
				}
			}
			final LogNormalDistributionParameter logNDist = new LogNormalDistributionParameter(mu, sigma);
			ctg.setLogNormalDistributionParameter(logNDist);
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Start to multiply all ordinary edge values by factor " + expanderFactor);
			}
		}
		for (final STNUEdge e : graph.getEdges()) {
			if (e.isContingentEdge()) {
				continue;//already managed
			}
			if (e.isOrdinaryEdge()) {
				e.setValue(e.getValue() * expanderFactor);
				continue;
			}
			if (e.isWait()) {
				e.setLabeledValue(e.getCaseLabel().getName(), e.getLabeledValue() * expanderFactor, true);
			}
		}
		this.g.setType(TNGraph.NetworkType.PSTN);
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("finished adjusting all ordinary edge values.");
			}
		}
	}

	/**
	 * Given a PSTN graph, it uses it as a network (does not make a copy). The contingent nodes must have a significative
	 * {@link LabeledNode#getLogNormalDistribution()}.
	 *
	 * @param graph PSTN in TNGraph format
	 */
	@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Ok..., but it is not so important!")
	public PSTN(@Nonnull TNGraph<STNUEdge> graph) {
		this();
		this.g = graph;
	}

	/**
	 * Default constructor.
	 */
	PSTN() {
		this.activationNode = null;
		this.checkStatus = new STNU.STNUCheckStatus();
//		this.g =
		this.fInput = fOutput = null;
		this.horizon = maxWeight = Constants.INT_NULL;
		lowerContingentEdge = upperContingentEdge = null;
		this.timeOut = 2700;// seconds
		this.rangeFactor = 3.3;
		this.optimizationEngine = null;
	}

	/**
	 * Determines {@code (Su, F)}, where Su = (T, C, L) is a dynamically controllable STNU where L = {(Ai, xi, yi, Ci) | i ∈ {1, ..., k}} and F is the value of
	 * the objective function (i.e., the joint probability mass of the probabilistic durations in this PSTN network captured by the contingent links in L).
	 * <p>
	 * The STNU has the same time-points and ordinary constraints as the PSTN, together with contingent links over the same activation and contingent
	 * time-points. If unable to find such an STNU or the STNU contains a SRN made by only ordinary edges, it returns a null approximating STNU.
	 *
	 * @return The result (Su, F) is stored in {@link PSTNCheckStatus#approximatingSTNU} and {@link PSTNCheckStatus#probabilityMass}. If it is not possible to
	 * 	find an approximation because the optimization fails, {@link PSTNCheckStatus#exitFlag} assumes negative values (see
	 *    {@link OptimizationEngine.OptimizationResult}), approximatingSTNU = null and probabilityMass = -1.<br>
	 *  If it is not possible because the semi-reducible cycle is
	 * 	composed by only ordinary edges, then {@link PSTNCheckStatus#exitFlag} is -10, approximatingSTNU = null and probabilityMass = -1. <br>
	 * 	If the approximating STNU was found without resolving any minimization problem, {@link PSTNCheckStatus#exitFlag} is 10.
	 * 	<p>
	 * 	this.{@link #g} is not modified.
	 * 	</p>
	 *
	 * @throws IllegalArgumentException if {@link #g} is null or {@link #g} does not contain contingent links.
	 */
	public PSTNCheckStatus buildApproxSTNU() {
		//IT IS ASSUMED THAT:
		//2. that this.logNormalParameter is initialized for contingent links of this.g
		final Instant startBuild = Instant.now();
		final PSTNCheckStatus status = new PSTNCheckStatus();

		if (this.g == null) {
			throw new IllegalArgumentException("Graph is null or graph has not been initialized.");
		}
		if (!this.checkStatus.initialized) {
			try {
				this.initAndCheck();
			} catch (WellDefinitionException e) {
				throw new IllegalArgumentException("Graph is not a STNU: " + e.getMessage());
			}
		}
		//Initialize STNU. It is necessary to have lower/upperCaseEdge maps.
		//I use a copy of the graph for not destroying the input one.
		TNGraph<STNUEdge> stnuG = new TNGraph<>(this.g, this.g.getEdgeFactory().getEdgeImplClass());
		final STNU stnu = new STNU(stnuG);
		try {
			stnu.initAndCheck();
		} catch (WellDefinitionException e) {
			throw new IllegalArgumentException("Graph is not a STNU: " + e.getMessage());
		}
		final Object2ObjectMap<LabeledNode, STNUEdge> UCEdge = stnu.getUpperCaseEdgesMap();
		final Object2ObjectMap<LabeledNode, STNUEdge> LCEdge = stnu.getLowerCaseEdgesMap();
		if (UCEdge == null || UCEdge.isEmpty() || LCEdge == null || LCEdge.isEmpty()) {
			throw new IllegalArgumentException("Graph is not a STNU because has no contingent link.");
		}
		final Object2ObjectMap<LabeledNode, LogNormalDistributionParameter> logNormalParameterWithSTNUNode = new Object2ObjectOpenHashMap<>();
		for (final LabeledNode ctg : this.upperContingentEdge.keySet()) {
			if (ctg.getLogNormalDistribution() != null) {
				logNormalParameterWithSTNUNode.put(stnuG.getNode(ctg.getName()), ctg.getLogNormalDistribution());
			}
		}

		final ObjectSet<LabeledNode> ctgNodes = UCEdge.keySet();
		//Update contingent bound x,y of each contingent link such that [x,y] contains the 99.9\% of the log-normal distribution (when this.rangeFactor = 3.3).
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Start determining contingent bounds for contingent link using " + this.rangeFactor + " factor.");
			}
		}
		for (final LabeledNode ctg : ctgNodes) {
			STNUEdge e = UCEdge.get(ctg);
			final LogNormalDistributionParameter logNormale = logNormalParameterWithSTNUNode.get(ctg);
			if (logNormale == null) {
				if (Debug.ON) {
					LOG.info("Contingent link associated to " + ctg + " has no a log-normal distribution. It is assumed to have a uniform distribution");
				}
				continue;
			}
			final double stdDevFactorized = this.rangeFactor * logNormale.getScale();

			int newNegY = (int) -Math.round(Math.exp(logNormale.getLocation() + stdDevFactorized));
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest(
						"New bounds for UC edge " + e + ":" + "\nthis.rangeFactor : " + this.rangeFactor + "\nlogNormale.location: " +
						logNormale.getLocation() +
						"\nlogNormale.scale: " + logNormale.getScale() + "\nstdDevFactorized: " + stdDevFactorized + "\nNew Upper bound: " + newNegY);
				}
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("New bounds for UC edge " + e + ":" + newNegY);
				}
			}
			if (newNegY == -1) {
				//newX must be 1 at lease, and less than newY
				newNegY--;
			}
			e.setLabeledValue(ctg.getALetter(), newNegY, true);

			e = LCEdge.get(ctg);
			int newX = (int) Math.round(Math.exp(logNormale.getLocation() - stdDevFactorized));
			if (newX <= 0) {
				throw new IllegalStateException("The new bound for LC edge " + e + " is negative: " + newX);
			}
			if (newX == (-newNegY)) {
				newX--;
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("stdDevFactorized for LC edge " + e + ": " + stdDevFactorized + "\nNew Lower bound: " + newX);
				}
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("New bounds for LC edge " + e + ":" + newX);
				}
			}
			e.setLabeledValue(ctg.getALetter(), newX, false);
		}

		status.cycles = 0;
		Instant startMinimization;
		status.partialExecutionTimeNS = 0;
		do {
			//Find the negative cycle on a copy
			final STNU stnu1 = new STNU(stnu);
			try {
				stnu1.dynamicControllabilityCheck(STNU.CheckAlgorithm.SRNCycleFinder);
			} catch (WellDefinitionException e) {
				throw new IllegalArgumentException("Graph is not a STNU: " + e.getMessage());
			}

			/*
			 * Possible negative cycle SRN is in STNUCheckStatus.getNegativeSTNUCycleInfo(boolean, g).
			 * For each edge in the SRN, the STNUCheckStatus.getEdgePathAnnotation() represents the path of edges that generates the considered edge.
			 */
			final STNU.STNUCheckStatus stnu1Status = stnu1.getCheckStatus();
			if (stnu1Status.isControllable()) {
				//update bounds in stnu and return it
				if (status.cycles == 0) {
					// no negative cycles, so the first bounds are ok
					// Determine the joint probability mass is easy because all contingent ranges have bounds = mu +- rangeFactor * scale.
					// Simplifying F(mu+rangeFactor scale)-F(mu-rangeFactor scale) =ɸ(this.rangeFactor)-ɸ(-this.rangeFactor)
					final NormalDistribution normalD = new NormalDistribution();
					status.probabilityMass = normalD.cumulativeProbability(this.rangeFactor) - normalD.cumulativeProbability(-this.rangeFactor);
					status.exitFlag = 10;
					status.partialExecutionTimeNS = 0;
					if (LOG.isLoggable(Level.INFO)) {
						LOG.info(
							"Found solution without solving the optimization problem. All contingent links have bounds that include " + status.probabilityMass +
							" probability mass.");
					}
				} else {
					//determine the conjuncted probability mass before returning
					double probabilityMass = 1.0;
					for (final LabeledNode ctg : ctgNodes) {
						STNUEdge e = UCEdge.get(ctg);
						final LogNormalDistributionParameter logNormaleParam = logNormalParameterWithSTNUNode.get(ctg);
						if (logNormaleParam == null) {
							//standard contingent link are not considered.
							continue;
						}
						final org.apache.commons.math3.distribution.LogNormalDistribution logNormal =
							new org.apache.commons.math3.distribution.LogNormalDistribution(logNormaleParam.getLocation(), logNormaleParam.getScale());
						final double y = -e.getLabeledValue();
						final double cdfU = logNormal.cumulativeProbability(y);

						e = LCEdge.get(ctg);
						final double cdfL = logNormal.cumulativeProbability(e.getLabeledValue());
						final double probMass = cdfU - cdfL;
						probabilityMass *= probMass;
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINEST)) {
								LOG.finest("For the ctg link ending in " + ctg + " the bounds are [" + e.getLabeledValue() + ", " + y +
								           "] and the cumulative probability values are [" + cdfL + ", " + cdfU + "]");
							}
							if (LOG.isLoggable(Level.FINER)) {
								LOG.finer("The probability mass of contingent link ending in " + ctg + ": " + probMass);
							}
						}
					}
					status.probabilityMass = probabilityMass;
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							LOG.finer("The product of all probability masses of contingent links is : " + probabilityMass);
						}
					}
				}
				status.executionTimeNS = Duration.between(startBuild, Instant.now()).toNanos();
				status.consistency = true;
				status.finished = stnu1Status.finished;

				status.approximatingSTNU = stnu;
				return status;
			}
			//SRNCInfo already contains all the information determined by the fec=tchEdgeInfo procedure in the pseudocode of the TIME 2024 paper
			final STNU.STNUCheckStatus.SRNCInfo negCycle = stnu1Status.getNegativeSTNUCycleInfo(true, stnu1.getG());
			status.srncKind = stnu1Status.srncKind;
			if (negCycle.edgeType() == STNU.STNUCheckStatus.SRNCEdges.ordinary) {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.info("Found a negative cycle made only by ordinary constraints. It is not possible to solve minimizing contingent ranges. Giving up!");
				}
				//no contingent links
				status.executionTimeNS = Duration.between(startBuild, Instant.now()).toNanos();
				status.consistency = false;
				status.finished = true;
				status.approximatingSTNU = null;
				status.srn = negCycle.srnExpanded();
				status.exitFlag = -10;
				status.partialExecutionTimeNS = 0;
				status.probabilityMass = -1.0;
				return status;
			}
			//For each contingent link present in the negative cycle, put is bounds in an array (constraint coefficients for the maximization problem)
			//and associate their indices with contingent node
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Found the negative semi-reducible cycle: " + negCycle.srnExpanded() +
				          "\nStart to minimize the contingent ranges to solve the negative cycle.");
			}

			startMinimization = Instant.now();
			final Object2IntMap<LabeledNode> lowerCaseCount = negCycle.lowerCaseCount();
			final Object2IntMap<LabeledNode> upperCaseCount = negCycle.upperCaseCount();
			final ObjectArraySet<LabeledNode> ctgInSRN = new ObjectArraySet<>(lowerCaseCount.keySet());
			ctgInSRN.addAll(upperCaseCount.keySet());
			//remove contingent nodes that not have a log-normal distribution
			for (final LabeledNode node : ctgInSRN.toArray(new LabeledNode[0])) {
				if (node.getLogNormalDistribution() == null) {
					LOG.info("Contingent node " + node + " in the SRNC is removed because it hasn't a log-normal distribution.");
					ctgInSRN.remove(node);
					lowerCaseCount.removeInt(node);
					upperCaseCount.removeInt(node);
				}
			}
			final int k = ctgInSRN.size();//number of contingent links
			final int n = 2 * k;//number of variables
			final double[] x = new double[n];
			final double[][] A = new double[1][n];
			final double[] b = new double[1];
			final double[] mu = new double[k];
			final double[] sigma = new double[k];
			/*
			 *  index2Ctg is the map (i, ctg) where i is the index of the lower bound of the contingent link in x.
			 */
			final Int2ObjectMap<LabeledNode> index2Ctg = new Int2ObjectOpenHashMap<>(k);

			final Object2ObjectMap<LabeledNode, STNUEdge> lowerCaseEdges = stnu1.getLowerCaseEdgesMap();
			final Object2ObjectMap<LabeledNode, STNUEdge> upperCaseEdges = stnu1.getUpperCaseEdgesMap();
			assert lowerCaseEdges != null;
			assert upperCaseEdges != null;
			int i = 0;
			int j = 0;
			double partialSumConstantCoefficient = 0.0;

			for (final LabeledNode ctg : ctgInSRN) {
				final int nL = lowerCaseCount.getInt(ctg);
				final int nU = upperCaseCount.getInt(ctg);
				if (nL == 0 && nU == 0) {
					continue;
				}
				index2Ctg.put(i, ctg);
				x[i] = lowerCaseEdges.get(ctg).getLabeledValue();//lower bound of the current contingent link
				x[i + 1] = -upperCaseEdges.get(ctg).getLabeledValue();//upper bound of the current contingent link
				A[0][i] = -nL; //Since matLabEngine.nonLinearOptimization solve a minimization problem, I have to invert all the coefficients
				A[0][i + 1] = nU;
				partialSumConstantCoefficient +=
					(nL * x[i] - nU * x[i + 1]); //Since nonLinearOptimization solve a minimization problem, I have to invert all the coefficients
				final LogNormalDistributionParameter logNormalPar = logNormalParameterWithSTNUNode.get(stnuG.getNode(ctg.getName()));
				mu[j] = logNormalPar.getLocation();
				sigma[j] = logNormalPar.getScale();
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("Initial range for contingent link #" + i / 2 + ", relative to " + ctg.getName() + ": [" + x[i] + ", " + x[i + 1] + "]");
					}
				}
				i += 2;
				j++;
			}
			b[0] = negCycle.value() - partialSumConstantCoefficient;
			final OptimizationEngine.OptimizationResult result;

			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("MatLab parameters: Matrix A: " + Arrays.toString(A[0]) + "\nb: " +
					          Arrays.toString(b) + "\nx: " + Arrays.toString(x) + "\nmu: " + Arrays.toString(mu) + "\nsigma: " + Arrays.toString(sigma));
				}
			}
			try {
				result = optimizationEngine.nonLinearOptimization(x, A, b, mu, sigma);
			} catch (Exception e) {
				final String msg = "There is problem to run MatLab `fmincon`: " + e.getMessage();
				LOG.severe(msg);
				throw new RuntimeException(msg);
			}
			status.partialExecutionTimeNS += Duration.between(startMinimization, Instant.now()).toNanos();
			status.exitFlag = result.exitFlag();
			if (result.exitFlag() < 1) {
				LOG.severe(
					"MatLab was not able to solve the problem relative to the following SRNC: " + negCycle
					+ "\nMatrix A: " + Arrays.toString(A[0]) + "\nb: " +
					Arrays.toString(b) + "\nx: " + Arrays.toString(x) + "\nmu: " + Arrays.toString(mu) + "\nsigma: " + Arrays.toString(sigma) + "\nresult: " +
					result);
				status.executionTimeNS = Duration.between(startBuild, Instant.now()).toNanos();
				status.consistency = false;
				status.probabilityMass = -1;
				status.srn = negCycle.srnExpanded();
				status.approximatingSTNU = null;
				return status;
			}
//			status.probabilityMass *= -result.optimumValue();
			//update the bound of contingent link in stnu (the original stnu).
			final double[] newVals = result.solution();
			stnuG = stnu.getG();
			for (i = 0; i < newVals.length; i += 2) {
				//index2Ctg is the map (indexLowerBound, ContingentNode), so the FOR manages pair of values at each cycle.
				final LabeledNode ctg = index2Ctg.get(i);
				final LabeledNode ctgInSTNU = stnuG.getNode(ctg.getName());
				STNUEdge edge = stnu.getLowerCaseEdgesMap().get(ctgInSTNU);
				final int lowerBound = (int) Math.ceil(newVals[i]);
				final int upperBound = (int) newVals[i + 1];

				if (upperBound <= lowerBound || lowerBound < 0) {
					LOG.severe(
						"The new bounds for contingent link ending in " + ctg + " is not an admissible solution: [" + lowerBound + ", " + upperBound + "]");
					status.executionTimeNS = Duration.between(startBuild, Instant.now()).toNanos();
					status.consistency = false;
					status.probabilityMass = -1;
					status.srn = null;
					status.approximatingSTNU = null;
					status.exitFlag = -2;
					return status;
				}
				edge.setLabeledValue(edge.getCaseLabel().getName(), lowerBound, false);

				edge = stnu.getUpperCaseEdgesMap().get(ctgInSTNU);
				edge.setLabeledValue(edge.getCaseLabel().getName(), -upperBound, true);//in distance graph, y is stored negated.
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer(
							"New range for contingent link #" + i / 2 + ", relative to " + ctg.getName() + ": [" + lowerBound + ", " + upperBound + "]");
					}
				}
			}
			status.cycles++;
		} while (true);
	}

	/**
	 * @return the activationNode map if the network has been {@link #initAndCheck()}, null otherwise.
	 * 	<p>
	 * 	The map is (contingentNode, activationNode).
	 */
	@Nullable
	public final Object2ObjectMap<LabeledNode, LabeledNode> getActivationNodeMap() {
		if (!this.checkStatus.initialized) {
			return null;
		}
		return activationNode;
	}

	/**
	 * @return the avgContingentRangeWidth
	 */
	public int getAvgContingentRangeWidth() {
		return avgContingentRangeWidth;
	}

	/**
	 * @return the g
	 */
	public final TNGraph<STNUEdge> getG() {
		return g;
	}

	/**
	 * Considers the given graph as the graph to check (graph will be modified). Clear all internal parameter.
	 *
	 * @param graph set internal TNGraph to g. It cannot be null.
	 */
	@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "For efficiency reason, it includes an external mutable object.")
	public final void setG(TNGraph<STNUEdge> graph) {
		if (graph == null) {
			throw new IllegalArgumentException("Input graph is null!");
		}
		reset();
		this.g = graph;
	}

	/**
	 * @return the horizon of the network
	 */
	public int getHorizon() {
		return horizon;
	}

	/**
	 * Returns a map containing the lower-case edges associated to the contingent link of the network if the network was initialized ({@link #initAndCheck()}
	 * executed successfully); null otherwise. In particular, if the network contains the contingent link {@code (A, 1, 3, C)}, the returned map contains the
	 * pair {@code C --> (A, c(1), C)}.
	 *
	 * @return the lowerContingentEdge map
	 */
	@Nullable
	public final Object2ObjectMap<LabeledNode, STNUEdge> getLowerCaseEdgesMap() {
		if (!this.checkStatus.initialized) {
			return null;
		}
		return lowerContingentEdge;
	}

	/**
	 * @return the matLabEngine used by this class.
	 */
	public OptimizationEngine getOptimizationEngine() {
		return optimizationEngine;
	}

	/**
	 * @param optimizationEngine the new matLabEngine to use.
	 */
	public void setOptimizationEngine(OptimizationEngine optimizationEngine) {
		this.optimizationEngine = optimizationEngine;
	}

	/**
	 * @return the absolute max weight of the network
	 */
	public int getMaxWeight() {
		return maxWeight;
	}

	/**
	 * @return the time-out in seconds for the DC checking method.
	 */
	public int getTimeOut() {
		return timeOut;
	}

	/**
	 * Returns a map containing the upper-case edges associated to the contingent link of the network if the network was initialized ({@link #initAndCheck()}
	 * executed successfully); null otherwise. In particular, if the network contains the contingent link {@code (A, 1, 3, C)}, the returned map contains
	 * {@code C --> (C, C:-3, A)}.
	 *
	 * @return the upper-case constraints map
	 */
	@Nullable
	public Object2ObjectMap<LabeledNode, STNUEdge> getUpperCaseEdgesMap() {
		if (!this.checkStatus.initialized) {
			return null;
		}
		return upperContingentEdge;
	}

	/**
	 * @return version and copyright string
	 */
	public final String getVersionAndCopyright() {
		// I use a non-static method for having a general method that prints the right name for each derived class.
		try {
			return getClass().getName() + " " + getClass().getDeclaredField("VERSIONandDATE").get(this) +
			       "\nSPDX-License-Identifier: LGPL-3.0-or-later, Roberto Posenato.\n";

		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			throw new IllegalStateException("Not possible exception I think :-)");
		}
	}

	/**
	 * @return the fInput
	 */
	@SuppressWarnings("SpellCheckingInspection")
	public File getfInput() {
		return fInput;
	}

	/**
	 * @return the fOutput
	 */
	@SuppressWarnings("SpellCheckingInspection")
	public File getfOutput() {
		return fOutput;
	}

	/**
	 * @param fileOutput the file where to save the result.
	 */
	@SuppressWarnings("SpellCheckingInspection")
	public final void setfOutput(File fileOutput) {
		fOutput = fileOutput;
	}

	/**
	 * Makes the PSTN check and initialization. The PSTN instance is represented by graph g. If some constraints of the network does not observe well-definition
	 * properties AND they can be adjusted, then the method fixes them and logs such fixes in log system at WARNING level. If the method cannot fix such
	 * not-well-defined constraints, it raises a {@link WellDefinitionException}.
	 *
	 * @throws WellDefinitionException if any error about the format of the network occurs
	 */
	public final void initAndCheck() throws WellDefinitionException {
		if (Debug.ON) {
			if (PSTN.LOG.isLoggable(Level.FINE)) {
				PSTN.LOG.log(Level.FINE, "Starting initial well definition check.");
			}
		}
		g.clearCache();
//		gCheckedCleaned = null;
		LabeledNode Z = g.getZ();
		activationNode = new Object2ObjectOpenHashMap<>();
		lowerContingentEdge = new Object2ObjectOpenHashMap<>();
		upperContingentEdge = new Object2ObjectOpenHashMap<>();

		// Checks the presence of Z node!
		// Z = this.g.getZ(); already done in setG()
		if (Z == null) {
			Z = g.getNode(PSTN.ZERO_NODE_NAME);
			if (Z == null) {
				// We add by authority!
				Z = LabeledNodeSupplier.get(PSTN.ZERO_NODE_NAME);
				Z.setX(10);
				Z.setY(10);
				g.addVertex(Z);
				if (Debug.ON) {
					if (PSTN.LOG.isLoggable(Level.WARNING)) {
						PSTN.LOG.log(Level.WARNING, "No " + PSTN.ZERO_NODE_NAME + " node found: added!");
					}
				}
			}
			g.setZ(Z);
		} else {
			if (!Z.getLabel().isEmpty()) {
				if (Debug.ON) {
					if (PSTN.LOG.isLoggable(Level.WARNING)) {
						PSTN.LOG.log(Level.WARNING, "In the graph, Z node has not empty label. Label removed!");
					}
				}
				Z.setLabel(Label.emptyLabel);
			}
		}

		// Checks well definiteness of edges and determine maxWeight
		this.maxWeight = 0;
		for (final STNUEdge e : g.getEdges()) {
			if (Debug.ON) {
				if (PSTN.LOG.isLoggable(Level.FINEST)) {
					PSTN.LOG.log(Level.FINEST, "Initial Checking edge e: " + e);
				}
			}
			final LabeledNode s = g.getSource(e);
			final LabeledNode d = g.getDest(e);
			assert s != null;
			assert d != null;

			if (s == d) {
				// loop are not admissible
				g.removeEdge(e);
				continue;
			}
			if (e.isEmpty()) {
				e.isEmpty();
				g.removeEdge(e);
				if (Debug.ON) {
					if (PSTN.LOG.isLoggable(Level.WARNING)) {
						PSTN.LOG.log(Level.WARNING, "Empty edge " + e + " has been removed.");
					}
				}
				continue;
			}
			final int initialValue = e.getValue();
			if (initialValue != Constants.INT_NULL) {
				final int absValue = Math.abs(initialValue);
				if (absValue > maxWeight) {
					maxWeight = absValue;
				}
			}

			if (!e.isContingentEdge()) {
				continue;
			}
			// Check contingent properties!
			final int labeledValue = e.getLabeledValue();

			if (initialValue == Constants.INT_NULL && labeledValue == Constants.INT_NULL) {
				throw new WellDefinitionException(
					"Contingent edge " + e + " cannot be initialized because it hasn't an initial value neither a lower/upper case value.");
			}

			final STNUEdge eInverted = g.findEdge(d, s);
			if (eInverted == null) {
				throw new WellDefinitionException(
					"Contingent edge " + e + " is alone. The companion contingent edge between " + d.getName() + " and " + s.getName() +
					" does not exist while it must exist!");
			}
			if (!eInverted.isContingentEdge()) {
				throw new WellDefinitionException("Edge " + e + " is contingent while the companion edge " + eInverted + " is not contingent!\nIt must be!");
			}
			if (Debug.ON) {
				if (PSTN.LOG.isLoggable(Level.FINEST)) {
					PSTN.LOG.log(Level.FINEST, "Edge " + e + " is contingent. Found its companion: " + eInverted);
				}
			}
			/*
			 * Memo.
			 * If current initialValue is negative, current edge is the lower bound C--->A. The lower case labeled value has to be put in the inverted edge.
			 * If the lower case labeled value is already present, it must be equal.
			 * If current initialValue is positive, current edge is the upper bound A--->C. The upper case labeled value has to be put in the inverted edge.
			 * If the upper case labeled value is already present, it must be equal.
			 * if current initialValue is undefined, then we assume that the contingent link is already set.
			 */
			if (initialValue != Constants.INT_NULL) {
				final int eInvertedInitialValue;
				int lowerCaseValue;
				int upperCaseValue;
				eInvertedInitialValue = eInverted.getValue();

				if (initialValue < 0) {
					// e : A<---C
					// d s
					// eInverted : A--->C
					// d s
					// current edge 'e' is the lower bound.
					lowerCaseValue = eInverted.getLabeledValue();
					final ALetter contingentALetter = new ALetter(s.getName());

					if (lowerCaseValue != Constants.INT_NULL && -initialValue != lowerCaseValue) {
						throw new WellDefinitionException("Edge " + e + " is contingent with a negative value and the inverted " + eInverted +
						                                  " already contains a ***different*** lower case value: " + eInverted.getLabeledValueFormatted() +
						                                  ".");
					}
					if (lowerCaseValue == Constants.INT_NULL && (eInvertedInitialValue <= 0)) {
						//|| eInvertedInitialValue == Constants.INT_NULL is subsumed by <= 0
						throw new WellDefinitionException("Edge " + e + " is contingent with a negative value but the inverted " + eInverted +
						                                  " does not contain a lower case value neither a proper initial value. ");
					}

					if (lowerCaseValue == Constants.INT_NULL) {
						lowerCaseValue = -initialValue;
						eInverted.setLabeledValue(contingentALetter, lowerCaseValue, false);

						upperCaseValue = -eInvertedInitialValue;
						e.setLabeledValue(contingentALetter, upperCaseValue, true);

						if (Debug.ON) {
							if (PSTN.LOG.isLoggable(Level.FINEST)) {
								PSTN.LOG.log(Level.FINEST, "Inserted the upper label value: " + e.getLabeledValueFormatted() + " to edge " + e);
							}
						}
						if (Debug.ON) {
							if (PSTN.LOG.isLoggable(Level.FINEST)) {
								PSTN.LOG.log(Level.FINEST, "Inserted the lower label value: " + eInverted.getLabeledValueFormatted() + " to edge " + eInverted);
							}
						}
						if (lowerCaseValue >= -upperCaseValue) {
							throw new WellDefinitionException(
								"Edge " + eInverted + " is a lower-case edge but its value equal or greater than upper-case value " + (-upperCaseValue));
						}
					}
					// In order to speed up the checking, prepare some auxiliary data structure
					lowerContingentEdge.put(s, eInverted);
					upperContingentEdge.put(s, e);
					STNU.CHECK_ACTIVATION_UNIQUENESS(d, s, activationNode);
					activationNode.put(s, d);
					s.setContingent(true);

				} else {
					// e : A--->C
					// eInverted : C--->A
					final ALetter contingentALetter = new ALetter(d.getName());
					upperCaseValue = eInverted.getLabeledValue();

					if (upperCaseValue != Constants.INT_NULL && -initialValue != upperCaseValue) {
						throw new WellDefinitionException("Edge " + e + " is contingent with a positive value and the inverted " + eInverted +
						                                  " already contains a ***different*** upper case value: " + eInverted.getLabeledValueFormatted() +
						                                  ".");
					}
					if (upperCaseValue == Constants.INT_NULL && (eInvertedInitialValue == Constants.INT_NULL || eInvertedInitialValue >= 0)) {
						throw new WellDefinitionException("Edge " + e + " is contingent with a positive value but the inverted " + eInverted +
						                                  " does not contain a upper case value neither a proper initial value. ");
					}

					if (upperCaseValue == Constants.INT_NULL) {
						upperCaseValue = -initialValue;
						eInverted.setLabeledValue(contingentALetter, upperCaseValue, true);

						lowerCaseValue = -eInvertedInitialValue;
						e.setLabeledValue(contingentALetter, lowerCaseValue, false);

						if (Debug.ON) {
							if (PSTN.LOG.isLoggable(Level.FINEST)) {
								PSTN.LOG.log(Level.FINEST, "Inserted the lower label value: " + e.getLabeledValueFormatted() + " to edge " + e);
							}
						}
						if (Debug.ON) {
							if (PSTN.LOG.isLoggable(Level.FINEST)) {
								PSTN.LOG.log(Level.FINEST, "Inserted the upper label value: " + eInverted.getLabeledValueFormatted() + " to edge " + eInverted);
							}
						}
						if (lowerCaseValue >= -upperCaseValue) {
							throw new WellDefinitionException(
								"Edge " + e + " is a lower-case edge but its value equal or greater than upper-case value " + (-upperCaseValue));
						}
					}
					// In order to speed up the checking, prepare some auxiliary data structure
					lowerContingentEdge.put(d, e);
					upperContingentEdge.put(d, eInverted);
					STNU.CHECK_ACTIVATION_UNIQUENESS(s, d, activationNode);
					activationNode.put(d, s);
					d.setContingent(true);
				}
			} else {
				// here initialValue is null.
				// UC and LC values are already present.
				final CaseLabel pair = e.getCaseLabel();
				if (pair != null) {
					final ALetter ctg = pair.left();
					if (pair.rightBoolean()) {// it is an upper case value
						// check that the node name is correct
						if (!ctg.toString().equals(s.getName())) {
							throw new WellDefinitionException(
								"Edge " + e + " is upper case contingent edge but the name of node is not the name of contingent node: " +
								"\n upper case label: " + ctg + "\n ctg node: " + s);
						}
						final int lowerCaseValue = eInverted.getLabeledValue();
						if (lowerCaseValue != Constants.INT_NULL && lowerCaseValue > -e.getLabeledValue()) {
							throw new WellDefinitionException(
								"Edge " + eInverted + " is a lower-case edge but its value equal or greater than upper-case value " + (-e.getLabeledValue()));
						}
						STNU.CHECK_ACTIVATION_UNIQUENESS(d, s, activationNode);
						activationNode.put(s, d);
						upperContingentEdge.put(s, e);
						s.setContingent(true);
					} else {// it is a lower case value
						if (!ctg.toString().equals(d.getName())) {
							throw new WellDefinitionException(
								"Edge " + e + " is upper case contingent edge but the name of node is not the name of contingent node: " +
								"\n upper case label: " + ctg + "\n ctg node: " + d);
						}
						final int upperCaseValue = eInverted.getLabeledValue();
						if (upperCaseValue != Constants.INT_NULL && e.getLabeledValue() > -upperCaseValue) {
							throw new WellDefinitionException(
								"Edge " + e + " is a lower-case edge but its value equal or greater than upper-case value " + (-upperCaseValue));
						}
						lowerContingentEdge.put(d, e);
						STNU.CHECK_ACTIVATION_UNIQUENESS(s, d, activationNode);
						activationNode.put(d, s);
						d.setContingent(true);
					}
				}
			}
			// it is necessary to check max value
			int m = e.getLabeledValue();
			// LOG.warning("m value: " + m);
			if (m != Constants.INT_NULL) {
				final int absValue = Math.abs(m);
				if (absValue > maxWeight) {
					maxWeight = absValue;
				}
			}
			m = eInverted.getLabeledValue();
			if (m != Constants.INT_NULL) {
				final int absValue = Math.abs(m);
				if (absValue > maxWeight) {
					maxWeight = absValue;
				}
			}
		} // end contingent edges cycle

		//A contingent link can be checked two time.
		//So, to determine avgContingentRangeWidth it is better to do another cycle after the analysis of all contingent links
		this.avgContingentRangeWidth = 0;
		for (final LabeledNode ctg : this.lowerContingentEdge.keySet()) {
			final int with = -this.upperContingentEdge.get(ctg).getLabeledValue() - this.lowerContingentEdge.get(ctg).getLabeledValue();
			this.avgContingentRangeWidth += with;
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {LOG.finer("The width of contingent link associated to " + ctg + ": " + with);}
			}
		}
		this.avgContingentRangeWidth /= this.lowerContingentEdge.size();//average width
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {LOG.finer("The average width of contingent links: " + this.avgContingentRangeWidth);}
		}
		// Determine horizon value
		final long product = ((long) maxWeight) * (g.getVertexCount() - 1);// Z doesn't count!
		if (product >= Constants.INT_POS_INFINITE) {
			throw new ArithmeticException("Horizon value is not representable by an integer. maxWeight = " + maxWeight + ", #vertices = " + g.getVertexCount());
		}
		horizon = (int) product;
		if (Debug.ON) {
			if (PSTN.LOG.isLoggable(Level.FINER)) {
				PSTN.LOG.log(Level.FINER, "The horizon value is " + Constants.formatInt(horizon));
			}
		}

		/*
		 * Checks well definiteness of nodes.
		 * The following part is no more necessary because
		 * BellmanFord algorithm with null source adds a symbolic source node that reaches every other node
		 * in order to determine a potential value for each node even though are not reachable from Z.
		 * Adding the following edges that forces all nodes after Z, alters the solution.
		 */
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "Adding edges to force all nodes to be at or after zero node " + Z);
			}
		}
		final Collection<LabeledNode> nodeSet = this.g.getVertices();
		for (final LabeledNode node : nodeSet) {
			// 3. Checks that each node different from Z has an edge to Z
			if (node == Z) {
				continue;
			}
			boolean added = false;
			STNUEdge edge = this.g.findEdge(node, Z);
			if (edge == null) {
				edge = this.g.makeNewEdge(node.getName() + "_" + Z.getName(), ConstraintType.derived);
				this.g.addEdge(edge, node, Z);
				edge.setValue(0);
				added = true;
			}
			if (edge.getValue() == Constants.INT_NULL || edge.getValue() > 0) {
				edge.setValue(0);
				added = true;
			}
			if (Debug.ON) {
				if (added) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.log(Level.FINEST, "Added " + edge.getName() + ": " + node.getName() + "--(0)-->" + Z.getName());
					}
				}
			}
		}

		checkStatus.reset();
		checkStatus.initialized = true;
		if (Debug.ON) {
			if (PSTN.LOG.isLoggable(Level.FINE)) {
				PSTN.LOG.log(Level.FINE, "Initial well definition check done!");
			}
		}
	}

	/**
	 * @return the save
	 */
	public boolean isSave() {
		return save;
	}

//	/**
//	 * @return true if it was required a cleaned output.
//	 */
//	public boolean isCleanedOutputRequired() {
//		return cleanCheckedInstance;
//	}

	/**
	 * @param s the save to set
	 */
	public final void setSave(boolean s) {
		save = s;
	}

	/**
	 * @return the versionReq
	 */
	public boolean isVersionReq() {
		return versionReq;
	}

	/**
	 * Resets all internal structures.
	 */
	public final void reset() {
		g = null;
		maxWeight = 0;
		horizon = 0;
		checkStatus.reset();
		activationNode = null;
		lowerContingentEdge = null;
	}

	/**
	 * Stores the graph after a check to the file.
	 */
	public final void saveGraphToFile() {
		if (fOutput == null) {
			if (fInput == null) {
				PSTN.LOG.info("Input file and output file are null. It is not possible to save the result in automatic way.");
				return;
			}
			String outputName;
			try {
				outputName = COMPILE.matcher(fInput.getCanonicalPath()).replaceFirst("");
			} catch (IOException e) {
				System.err.println(
					"It is not possible to save the result. Field fOutput is null and no the standard output file can be created: " + e.getMessage());
				return;
			}
			if (!checkStatus.finished) {
				outputName += "_notFinishedCheck";
				if (checkStatus.timeout) {
					outputName += "_timeout_" + timeOut;
				}
			} else {
				outputName += "_checked_" + ((checkStatus.isControllable() ? "DC" : "NOTDC"));
			}
			outputName += PSTN.FILE_NAME_SUFFIX;
			fOutput = new File(outputName);
			PSTN.LOG.info("Output file name is " + fOutput.getAbsolutePath());
		}

		final TNGraph<STNUEdge> g1 = this.g;
//			getGChecked();
		g1.setInputFile(fOutput);
		g1.setName(fOutput.getName());
		g1.removeEmptyEdges();
		g1.setType(TNGraph.NetworkType.PSTN);

		final CSTNUStaticLayout<STNUEdge> layout = new CSTNUStaticLayout<>(g1);
		final TNGraphMLWriter graphWriter = new TNGraphMLWriter(layout);
		try {
			graphWriter.save(g1, fOutput);
		} catch (IOException e) {
			System.err.println("It is not possible to save the result. File " + fOutput + " cannot be created: " + e.getMessage());
			return;
		}
		PSTN.LOG.info("Checked instance saved in file " + fOutput.getAbsolutePath());
	}

	/**
	 * A PSTN maintains bounds of a contingent link as lower/upper case edge value. This is due to the fact that, even though contingent link durations are
	 * described as distribution probability functions, the STRONG/DYNAMIC controllability of a PSTN is realized approximating the network as an STNU.
	 * <p>
	 * {@link #buildApproxSTNU()} allows the determination of a Dynamic Controllable approximating STNU that maximizes the probability mass for each contingent
	 * link. Such a method return a DC STNU (if it exists) and doesn't touch the current network. This method saves the contingent bounds determined in the
	 * approximating STNU in the current network.
	 *
	 * @param approximatedSTNU the approximating STNU. It has to have the same contingent links (name) and same coningent node names for copying the bounds
	 *                         successfully.
	 */
	public void updateContingentBounds(STNU approximatedSTNU) {
		if (approximatedSTNU == null || approximatedSTNU.getG() == null) {
			throw new IllegalArgumentException("Input approximatedSTNU does not contain a network graph.");
		}
		final TNGraph<STNUEdge> approximatingG = approximatedSTNU.getG();
		for (final LabeledNode ctg : this.upperContingentEdge.keySet()) {
			if (ctg.getLogNormalDistribution() == null) {
				//the contingent link is an STNU one, no distribution probability function present
				continue;
			}

			STNUEdge e = this.upperContingentEdge.get(ctg);
			STNUEdge approxE = approximatingG.getEdge(e.getName());
			if (approxE == null) {
				throw new IllegalArgumentException("approximatedSTNU does not contain required edge " + e.getName());
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Updating " + e + " to " + approxE);
				}
			}
			if (approxE.getLabeledValue() > 0) {
				throw new IllegalStateException("The new bound for UC edge " + e + " is positive: " + approxE.getLabeledValue());
			}
			e.setLabeledValue(ctg.getALetter(), approxE.getLabeledValue(), true);

			e = this.lowerContingentEdge.get(ctg);
			approxE = approximatingG.getEdge(e.getName());
			if (approxE == null) {
				throw new IllegalArgumentException("approximatedSTNU does not contain required edge " + e.getName());
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Updating " + e + " to " + approxE);
				}
			}
			if (approxE.getLabeledValue() > 0) {
				throw new IllegalStateException("The new bound for LC edge " + e + " is negative: " + approxE.getLabeledValue());
			}
			e.setLabeledValue(ctg.getALetter(), approxE.getLabeledValue(), false);
		}
	}
}
