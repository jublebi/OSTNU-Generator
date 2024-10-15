// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.util;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import it.unimi.dsi.fastutil.objects.*;
import it.univr.di.Debug;
import it.univr.di.cstnu.algorithms.PSTN;
import it.univr.di.cstnu.algorithms.STNURTE;
import it.univr.di.cstnu.graph.*;
import it.univr.di.labeledvalue.Constants;
import org.apache.commons.math3.stat.Frequency;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.StringArrayOptionHandler;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple class to determine some statistics about the execution of PSTNs
 * <p>
 * The main idea is the following:
 * <ul>
 *  <li>give some DC PSTNs, this runner executes each of them for #times and registers how much time the instance was executed without constraint violation.
 * </ul>
 *
 * @author posenato
 * @version $Rev: 732 $
 */
public class PSTNRTEBenchmarkRunner {

	/**
	 * The possible exit of an RTE
	 */
	public enum ExitExecution {
		/**
		 * successful with all contingent durations inside the contingent bounds
		 */
		okInBound,
		/**
		 * successful with some contingent durations outside the contingent bounds
		 */
		okOutBounds,
		/**
		 * not successful with some contingent durations outside the contingent bounds
		 */
		notOkOutBounds
	}

	/**
	 * Maintains the map (contingent node, chosen duration of its contingent link), the number of chosen duration outside the bounds of the relative contingent
	 * link, and the conjunct probability mass of the contingent ranges w.r.t. the probability distribution functions of the contingent durations.
	 */
	@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Ok..., but it is not so important!")
	public record PSTNSituation(Object2IntMap<LabeledNode> situation, double maxOutlierSpan, int durationsOutOfBounds, double conjunctProbabilityMass) {

		/**
		 * @return true if there is no durations out of the admissible range.
		 */
		public boolean isWithinBounds() {
			return this.durationsOutOfBounds == 0;
		}
	}

	/**
	 * Represents a key composed by {@code (nodes, contingents)}.
	 * <p>
	 * Implements a natural order based on increasing pair {@code (nodes, contingents)}.
	 *
	 * @author posenato
	 */
	static private class GlobalStatisticsKey implements Comparable<GlobalStatisticsKey> {
		/**
		 * # contingents
		 */
		final int contingents;
		/**
		 * # of nodes
		 */
		final int nodes;

		/**
		 * default constructor
		 *
		 * @param inputNodes       #nodes
		 * @param inputContingents # cont
		 */
		GlobalStatisticsKey(final int inputNodes, final int inputContingents) {
			nodes = inputNodes;
			contingents = inputContingents;
		}

		/**
		 * The order is respect to #nodes,#contingents, and #propositions.
		 *
		 * @param o the object to be compared.
		 *
		 * @return negative if this has, in order, fewer nodes or fewer contingents or fewer proposition than the parameter 'o'; a positive value in the
		 * 	opposite case, 0 when all three values are equals to the corresponding values in 'o'.
		 */
		@Override
		public int compareTo(@Nonnull GlobalStatisticsKey o) {
			final long d = (long) nodes - o.nodes;
			if (d == 0) {
				return contingents - o.contingents;
			}
			return (int) d;
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (!(o instanceof GlobalStatisticsKey o1)) {
				return false;
			}
			return o1.compareTo(this) == 0;
		}

		/**
		 * @return #cont
		 */
		public int getContingent() {
			return contingents;
		}

		/**
		 * @return #nodes
		 */
		public int getNodes() {
			return nodes;
		}

		@Override
		public int hashCode() {
			return (contingents) * 100000 + nodes;
		}
	}

	/**
	 * Each instance of this class represents a set of global statistics. Each global statistics element represents a map
	 * {@code (GlobalStatisticsKey, SummaryStatistics)}. Each SummaryStatistics element allows the determination of different statistics of all added item to
	 * the element.
	 */
	static private class GlobalStatistics {
		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> networkEdges = new Object2ObjectAVLTreeMap<>();
		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> execTimeInSec = new Object2ObjectAVLTreeMap<>();
		//		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> minimizationExecTimeInSec = new Object2ObjectAVLTreeMap<>();
		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> probConjunctedProbMass = new Object2ObjectAVLTreeMap<>();
//		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> numberMinimizationProblem = new Object2ObjectAVLTreeMap<>();
		/**
		 * Three possible values: okInBound, okOutBounds, notOkOutBounds for early_execution strategy (_E suffix) and middle_point_execution_strategy (_M
		 * suffix)
		 */
		Object2ObjectMap<GlobalStatisticsKey, Frequency> executionExit_E = new Object2ObjectAVLTreeMap<>();
		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> durationOutsideBoundsInOK_E = new Object2ObjectAVLTreeMap<>();
		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> durationOutsideBoundsInNOTOK_E = new Object2ObjectAVLTreeMap<>();

		Object2ObjectMap<GlobalStatisticsKey, Frequency> executionExit_M = new Object2ObjectAVLTreeMap<>();
		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> durationOutsideBoundsInOK_M = new Object2ObjectAVLTreeMap<>();
		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> durationOutsideBoundsInNOTOK_M = new Object2ObjectAVLTreeMap<>();
	}

	/**
	 * CSV separator
	 */
	static final String CSVSep = ";\t";
	/**
	 * Global header
	 */
	static final String GLOBAL_HEADER =
		"%n%nGlobal statistics%n"
		+ "#networks" + CSVSep
		+ "#nodes" + CSVSep
		+ "#contingents" + CSVSep
		+ "#avgEdges" + CSVSep
		+ "stdDevEdges" + CSVSep
		+ "avgExeTime[s]" + CSVSep
		+ "stdDevExeTime[s]" + CSVSep
		+ "avgConjunctedProbMass" + CSVSep
		+ "stdDevConjunctedProbMass" + CSVSep
		+ "%%InBoundOK_E" + CSVSep
		+ "%%OutBoundOK_E" + CSVSep
		+ "%%OutBoundNOTOK_E" + CSVSep
		+ "#OutBoundDDuraOK_E" + CSVSep
		+ "#OutBoundDDuraOK_E" + CSVSep
		+ "%%InBoundOK_M" + CSVSep
		+ "%%OutBoundOK_M" + CSVSep
		+ "%%OutBoundNOTOK_M" + CSVSep
		+ "#OutBoundDDuraOK_M" + CSVSep
		+ "#OutBoundDDuraOK_M" + CSVSep
		+ "%n";
	/**
	 *
	 */
	static final String GLOBAL_HEADER_ROW =
		"%d" + CSVSep
		+ "%d" + CSVSep
		+ "%d" + CSVSep
		+ "%E" + CSVSep // avgEdges
		+ "%E" + CSVSep // stdDevEdges
		+ "%E" + CSVSep // avgExeTime
		+ "%E" + CSVSep // stdDevExeTime
		+ "%E" + CSVSep // avgConjunctedProbMass
		+ "%E" + CSVSep // scale ConjunctedProbMass
		+ "%6.4f" + CSVSep // "%InBoundOK_E"
		+ "%6.4f" + CSVSep // "%OutBoundOK_E"
		+ "%6.4f" + CSVSep // "%OutBoundNOTOK_E"
		+ "%6.4f" + CSVSep // #OutBoundDDuraInOK_E"
		+ "%6.4f" + CSVSep  // #OutBoundDDuraInOK_E"
		+ "%6.4f" + CSVSep // "%InBoundOK_M"
		+ "%6.4f" + CSVSep // "%OutBoundOK_M"
		+ "%6.4f" + CSVSep // "%OutBoundNOTOK_M"
		+ "%6.4f" + CSVSep // #OutBoundDDuraInOK_M"
		+ "%6.4f" + CSVSep  // #OutBoundDDuraInOK_M"
		+ "%n";
	/**
	 * class logger
	 */
	static final Logger LOG = Logger.getLogger(PSTNRTEBenchmarkRunner.class.getName());
	/**
	 * Output file header
	 */
	static final String OUTPUT_HEADER =
		String.format("%31s%s", "fileName", CSVSep)
		+ String.format("%6s%s", "#nodes", CSVSep)
		+ String.format("%4s%s", "#ctg", CSVSep)
		+ String.format("%5s%s", "#edges", CSVSep)
		+ String.format("%14s%s", "avgExeTime[s]", CSVSep)
		+ String.format("%14s%s", "std.dev.[s]", CSVSep)
		+ String.format("%14s%s", "ConjProbMass", CSVSep)
		+ String.format("%8s%s", "%InBOK_E", CSVSep)
		+ String.format("%8s%s", "%OutBOK_E", CSVSep)
		+ String.format("%8s%s", "%OutBNOTOK_E", CSVSep)//
		+ String.format("%8s%s", "AvgOutBDOK_E", CSVSep)
		+ String.format("%8s%s", "AvgOutBDNOTOK_E", CSVSep)
		+ String.format("%8s%s", "%InBOK_M", CSVSep)
		+ String.format("%8s%s", "%OutBOK_M", CSVSep)
		+ String.format("%8s%s", "%OutBNOTOK_M", CSVSep)//
		+ String.format("%8s%s", "AvgOutBDOK_M", CSVSep)
		+ String.format("%8s%s", "AvgOutBDNOTOK_M", CSVSep);
	/**
	 * OUTPUT_ROW is split in OUTPUT_ROW_GRAPH + OUTPUT_ROW_ALG_STATS
	 */
	static final String OUTPUT_ROW_GRAPH = "%31s" + CSVSep //name
	                                       + "%6d" + CSVSep //#nodes
	                                       + "%4d" + CSVSep //#contingents
	                                       + "%5d" + CSVSep//#edges
		;
	/**
	 * OUTPUT_ROW is split in OUTPUT_ROW_GRAPH + OUTPUT_ROW_ALG_STATS
	 */
	static final String OUTPUT_ROW_ALG_STATS = "%14E" + CSVSep // alg avgExe
	                                           + "%14E" + CSVSep // alg avgExe dev std
	                                           + "%14E" + CSVSep // ConjProbMass
	                                           + "%8.4f" + CSVSep // %InBoundOK_E
	                                           + "%8.4f" + CSVSep // %OutBoundOK_E
	                                           + "%8.4f" + CSVSep // %OutBoundNOTOK_E
	                                           + "%8.4f" + CSVSep // #OutBoundDDuraInOK_E
	                                           + "%8.4f" + CSVSep // #OutBoundDDuraInOK_E
	                                           + "%8.4f" + CSVSep // %InBoundOK_M
	                                           + "%8.4f" + CSVSep // %OutBoundOK_M
	                                           + "%8.4f" + CSVSep // %OutBoundNOTOK_M
	                                           + "%8.4f" + CSVSep // #OutBoundDDuraInOK_M
	                                           + "%8.4f" + CSVSep // #OutBoundDDuraInOK_M
		;
	/**
	 * Version
	 */
	static final String VERSIONandDATE = "1.0, July, 1 2024";
	/**
	 * Date formatter
	 */
	private final static SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");

	/**
	 * Allows to check the execution time of algorithms giving a set of instances.
	 *
	 * @param args an array of {@link String} objects.
	 */
	public static void main(String[] args) {

		LOG.finest("Checker " + VERSIONandDATE + "\nStart...");
		System.out.println("Checker " + VERSIONandDATE + "\n" + getNow() + ": Start of execution.");
		final PSTNRTEBenchmarkRunner tester = new PSTNRTEBenchmarkRunner();

		if (!tester.manageParameters(args)) {
			return;
		}
		LOG.finest("Parameters ok!");
		if (tester.versionReq) {
			return;
		}

		/*
		 * To collect statistics w.r.t. the dimension of networks
		 */
		final GlobalStatistics globalStatistics = new GlobalStatistics();

		final RunMeter runMeter = new RunMeter(System.currentTimeMillis(), tester.instances.size(), 0);
		runMeter.printProgress(0);

		tester.output.println("*".repeat(79));
		tester.output.println("* Trial date: " + getNow());
		tester.output.println("*".repeat(79));
		tester.output.println(OUTPUT_HEADER);
		tester.output.flush();
		int nTaskSuccessfullyFinished = 0;
		for (final File file : tester.instances) {
			final boolean isOk = tester.worker(file, runMeter, globalStatistics);
			if (isOk) {
				nTaskSuccessfullyFinished++;
			}
		}
		final String msg = "Number of instances processed successfully over total: " + nTaskSuccessfullyFinished + "/" + tester.instances.size() + ".";
		LOG.info(msg);
		System.out.println("\n" + getNow() + ": " + msg);

		tester.output.printf(GLOBAL_HEADER);
		//Use one of the element in globalStatistics to extract all the possible globalStatisticsKeys

		for (final Object2ObjectMap.Entry<GlobalStatisticsKey, SummaryStatistics> entryNetworkEdges : globalStatistics.networkEdges.object2ObjectEntrySet()) {
			final GlobalStatisticsKey globalStatisticsKey = entryNetworkEdges.getKey();
			tester.output.printf(GLOBAL_HEADER_ROW, //
			                     Long.valueOf(entryNetworkEdges.getValue().getN()), //
			                     Integer.valueOf(globalStatisticsKey.getNodes()),//
			                     Integer.valueOf(globalStatisticsKey.getContingent()), //
			                     Double.valueOf(entryNetworkEdges.getValue().getMean()),//
			                     Double.valueOf(entryNetworkEdges.getValue().getStandardDeviation()),//
			                     Double.valueOf(globalStatistics.execTimeInSec.get(globalStatisticsKey).getMean()),//
			                     Double.valueOf(globalStatistics.execTimeInSec.get(globalStatisticsKey).getStandardDeviation()),//
			                     Double.valueOf(globalStatistics.probConjunctedProbMass.get(globalStatisticsKey).getMean()),//
			                     Double.valueOf(globalStatistics.probConjunctedProbMass.get(globalStatisticsKey).getStandardDeviation()),//
			                     Double.valueOf(globalStatistics.executionExit_E.get(globalStatisticsKey).getPct(ExitExecution.okInBound)),//
			                     Double.valueOf(globalStatistics.executionExit_E.get(globalStatisticsKey).getPct(ExitExecution.okOutBounds)),//
			                     Double.valueOf(globalStatistics.executionExit_E.get(globalStatisticsKey).getPct(ExitExecution.notOkOutBounds)),//
			                     Double.valueOf(globalStatistics.durationOutsideBoundsInOK_E.get(globalStatisticsKey).getMean()),//
			                     Double.valueOf(globalStatistics.durationOutsideBoundsInNOTOK_E.get(globalStatisticsKey).getMean()),//
			                     Double.valueOf(globalStatistics.executionExit_M.get(globalStatisticsKey).getPct(ExitExecution.okInBound)),//
			                     Double.valueOf(globalStatistics.executionExit_M.get(globalStatisticsKey).getPct(ExitExecution.okOutBounds)),//
			                     Double.valueOf(globalStatistics.executionExit_M.get(globalStatisticsKey).getPct(ExitExecution.notOkOutBounds)),//
			                     Double.valueOf(globalStatistics.durationOutsideBoundsInOK_M.get(globalStatisticsKey).getMean()),//
			                     Double.valueOf(globalStatistics.durationOutsideBoundsInNOTOK_M.get(globalStatisticsKey).getMean())//
			                    );//
			tester.output.println();
		}
		tester.output.printf("%n%n%n");
		tester.output.close();
	}

	/**
	 * @param offSet the offset to add to the lower bound for finding a new upper bound when upper bound is not finite.
	 *
	 * @return a strategy for ordinary node execution time.
	 */
	private static STNURTE.Strategy buildMiddlePointStrategy(int offSet) {

		/*
		 * It uses averageContingentRangeWidth to determine a middle exec value for nodes
		 * having ∞ as upper bound in their time window.
		 */
		final STNURTE.NodeAndExecutionTimeChoice strategy = candidates -> {
			LabeledNode first = null;
			TimeInterval firstTW = null;
			int minUpperBound = Constants.INT_POS_INFINITE;
			for (final STNURTE.NodeWithTimeInterval entry : candidates.nodes()) {
				final LabeledNode node = entry.node();
				final TimeInterval tw = entry.timeInterval();
				if (tw.getUpper() <= candidates.timeInterval().getUpper()) {
					first = node;
					firstTW = tw;
					break;
				} else {
					if (minUpperBound > tw.getUpper()) {
						minUpperBound = tw.getUpper();
						first = node;
						firstTW = tw;
					}
				}
			}
			if (minUpperBound == Constants.INT_POS_INFINITE) {
				//all candidates have an upper bound equals to +∞
				//select the first one and limit its upper bound using averageContingentRangeWidth
				@SuppressWarnings("OptionalGetWithoutIsPresent") final STNURTE.NodeWithTimeInterval entry = candidates.nodes().stream().findFirst().get();
				first = entry.node();
				firstTW = entry.timeInterval();
				final int lb = entry.timeInterval().getLower();
				firstTW.set(lb, lb + offSet);
				if (Debug.ON) {
					LOG.info("All candidates have upper bound = ∞. Selected the first node " + first + " with time window " + firstTW);
				}
			}
			final ObjectSet<LabeledNode> singleSet = new ObjectArraySet<>(1);
			singleSet.add(first);
			final int lowerBound = Math.max(firstTW.getLower(), candidates.timeInterval().getLower());
			final int upperBound = Math.min(firstTW.getUpper(), candidates.timeInterval().getUpper());
			final int exeTime = Constants.sumWithOverflowCheck(lowerBound, upperBound) / 2;
			return new STNURTE.NodeOccurrence(exeTime, singleSet);
		};

		return () -> strategy;
	}

	/**
	 * Builds a situation for the PSTN. Moreover, it collects the number of duration that are outside the bounds of the relative contingent link and the
	 * conjunct probability mass determined by the contingent bounds.
	 *
	 * @param pstn a network already initialized.
	 *
	 * @return a PSTNSituation if the network has some contingent links, null otherwise.
	 */
	private static PSTNSituation buildSituation(PSTN pstn) {
		if (pstn.getLowerCaseEdgesMap() == null) {
			//no contingent link
			LOG.info("There is no contingent link. Giving up!");
			return null;
		}
		final Object2IntMap<LabeledNode> situation = new Object2IntOpenHashMap<>(pstn.getLowerCaseEdgesMap().size());
		int durationsOutOfBounds = 0;
		double conjunctProbabilityMass = 1.0;

		double maxOutlierSpan = 0.0;
		for (final LabeledNode ctg : pstn.getLowerCaseEdgesMap().keySet()) {
			final LogNormalDistributionParameter logNormalParam = ctg.getLogNormalDistribution();
			if (logNormalParam == null) {
				continue;
			}
			final int rndValue = (int) logNormalParam.sample();
			situation.put(ctg, rndValue);
			//possible shift of activation timepoint
			final int shift = logNormalParam.getShift();
			//check if the value is outside of contingent approximated bounds.
			final int lowerBound = pstn.getLowerCaseEdgesMap().get(ctg).getLabeledValue();
			assert pstn.getUpperCaseEdgesMap() != null && pstn.getUpperCaseEdgesMap().get(ctg) != null;
			final int upperBound = (-pstn.getUpperCaseEdgesMap().get(ctg).getLabeledValue());
			if (lowerBound >= upperBound) {
				throw new IllegalStateException("Contingent ranges for " + ctg + " are wrong: [" + lowerBound + ", " + upperBound + "]");
			}
			if (rndValue < lowerBound || rndValue > upperBound) {
				LOG.info("The chosen duration for " + ctg + " is " + rndValue + ". It is outside the range [" + lowerBound + ", " + upperBound + "]");
				durationsOutOfBounds++;
				double diff;
				if (rndValue < lowerBound && (diff = lowerBound - rndValue) > maxOutlierSpan) {
					maxOutlierSpan = diff;
				}
				if (rndValue > lowerBound && (diff = rndValue - upperBound) > maxOutlierSpan) {
					maxOutlierSpan = diff;
				}
			}
			final double cdfValue = (logNormalParam.cumulativeProbability(upperBound) - logNormalParam.cumulativeProbability(lowerBound));
			if (-1E-4 < cdfValue && cdfValue < 1E-4) {
				throw new IllegalStateException(
					"Contingent ranges for " + ctg + ": [" + lowerBound + ", " + upperBound + "] determine a not significative cumulative distribution value "
					+ cdfValue + "\n Lognormale parameter: " + ctg.getLogNormalDistribution());
			}
			conjunctProbabilityMass *= cdfValue;
		}
		return new PSTNSituation(situation, maxOutlierSpan, durationsOutOfBounds, conjunctProbabilityMass);
	}

	/**
	 * @param situation a situation
	 *
	 * @return a strategy for contingent durations.
	 */
	private static STNURTE.Strategy buildSituationStrategy(@Nonnull Object2IntMap<LabeledNode> situation) {

		final Object2IntMap<LabeledNode> localSituation = new Object2IntOpenHashMap<>(situation);
		/*
		 * It is assumed that for each contingent node, the proper time window has value [executionTimeActivationTP, ∞]
		 * The candidates.timeInterval() is used only to guarantee that the chosen value is greater that the minimum
		 */
		final STNURTE.NodeAndExecutionTimeChoice strategy = candidates -> {
			final ObjectSet<LabeledNode> nodes = new ObjectLinkedOpenHashSet<>();
			if (candidates.nodes() == null) {
				return new STNURTE.NodeOccurrence(0, nodes);
			}
			final ExtendedPriorityQueue<LabeledNode> queue = new ExtendedPriorityQueue<>();
			for (final STNURTE.NodeWithTimeInterval entry : candidates.nodes()) {
				final int ctgTime = Constants.sumWithOverflowCheck(entry.timeInterval().getLower(), localSituation.getInt(entry.node()));
				if (ctgTime < candidates.timeInterval().getLower()) {
					throw new IllegalArgumentException(
						"The chosen time " + ctgTime + " for ctg " + entry.node() + " is before the lower bound of the time window " +
						candidates.timeInterval());
				}
				queue.insertOrUpdate(entry.node(), ctgTime);
			}
			assert queue.getFirstPriority() != null;
			AbstractObject2IntMap.BasicEntry<LabeledNode> entry = queue.extractFirstEntry();
			final int minTime = entry.getIntValue();
			nodes.add(entry.getKey());

			while (!queue.isEmpty()) {
				entry = queue.extractFirstEntry();
				if (entry.getIntValue() == minTime) {
					nodes.add(entry.getKey());
				} else {
					break;
				}
			}

			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {LOG.finer("Environment chooses that " + Arrays.toString(nodes.toArray()) + " occur at time " + minTime);}
			}
			return new STNURTE.NodeOccurrence(minTime, nodes);
		};

		return () -> strategy;
	}

	/**
	 * @return current time in {@link #dateFormatter} format
	 */
	private static String getNow() {
		return dateFormatter.format(new Date());
	}

	/**
	 * @param value value in nanoseconds
	 *
	 * @return the value in seconds
	 */
	private static double nanoSeconds2Seconds(double value) {
		return value / 1E9;
	}

	/**
	 * Class for representing edge .
	 */
	private final Class<STNUEdgeInt> currentEdgeImplClass = STNUEdgeInt.class;
	/**
	 * The input file names. Each file has to contain a CSTN graph in GraphML format.
	 */
	@Argument(required = true, usage = "Input files. Each input file has to be an STNU graph in GraphML format.", metaVar = "STNU_file_names", handler = StringArrayOptionHandler.class)
	private String[] inputFiles;
	/**
	 *
	 */
	private List<File> instances;
	/**
	 * Parameter for asking how many times to execute a PSTN.
	 */
	@Option(name = "--numExecution", usage = "Number of time to re-execute a PSTN")
	private int nPSTNExecution = 1;
//	/**
//	 * Parameter for asking the execution strategy
//	 */
//	@Option(name = "--executionStrategy", usage = "Execution strategy for ordinary nodes.")
//	private STNURTE.StrategyEnum executionStrategy;

//	/**
//	 *
//	 */
//	@Option(name = "--save", usage = "Save all checked instances in dispatchable form.")
//	private boolean save;

//	/**
//	 * Parameter for asking timeout in sec.
//	 */
//	@Option(name = "--timeOut", usage = "Time in seconds.")
//	private int timeOut = 1800; // 20 min
	/**
	 * Output stream to outputFile
	 */
	private PrintStream output;
	/**
	 * Output file where to write the determined experimental execution times in CSV format.
	 */
	@Option(name = "-o", aliases = "--output", usage = "Output to this file in CSV format. If file is already present, data will be added.", metaVar = "outputFile")
	private File outputFile;
	/**
	 * Software Version.
	 */
	@Option(name = "-v", aliases = "--version", usage = "Version")
	private boolean versionReq;

	/**
	 * Execute the STNU graph as PSTN determining some statistics. It returns the string representing the ({@link #OUTPUT_ROW_ALG_STATS}) part to add to the row
	 * in the statistics file.
	 *
	 * @param pstn a network already initialized.
	 */
	private String executePSTNTest(@Nonnull PSTN pstn, @Nonnull String rowToWrite, //
	                               @Nonnull SummaryStatistics gExecTimeInSec,//
	                               @Nonnull SummaryStatistics gProbConjunctedProbMass,//
	                               @Nonnull Frequency gExecutionExit_E,//
	                               @Nonnull SummaryStatistics gDurationOutsideBoundsInOK_E,//
	                               @Nonnull SummaryStatistics gDurationOutsideBoundsInNOTOK_E,//
	                               @Nonnull Frequency gExecutionExit_M,//
	                               @Nonnull SummaryStatistics gDurationOutsideBoundsInOK_M,//
	                               @Nonnull SummaryStatistics gDurationOutsideBoundsInNOTOK_M//
	                              ) {

		String msg;
		boolean checkInterrupted = false;
		final String fileName = pstn.getG().getFileName().getName();
		PSTNSituation situation = null;
		final SummaryStatistics localExecTimeStat_E = new SummaryStatistics();
		final SummaryStatistics localDurationOutsideBoundsInOK_E = new SummaryStatistics();
		final SummaryStatistics localDurationOutsideBoundsInNOTOK_E = new SummaryStatistics();
		final Frequency localExitExe_E = new Frequency();
		final SummaryStatistics localDurationOutsideBoundsInOK_M = new SummaryStatistics();
		final SummaryStatistics localDurationOutsideBoundsInNOTOK_M = new SummaryStatistics();
		final Frequency localExitExe_M = new Frequency();

//		final STNURTE.Strategy middlePointStrategy = buildMiddlePointStrategy(pstn.getAvgContingentRangeWidth());


		STNURTE.RTEState status = null;
		for (int j = 0; j < nPSTNExecution && !checkInterrupted; j++) {
			LOG.info("Execution " + (j + 1) + "/" + nPSTNExecution + " for PSTN " + fileName);
			final STNURTE stnuRTE = new STNURTE(pstn.getG(), false);

			boolean rteExists = true;
			try {
				situation = buildSituation(pstn);
			} catch (IllegalArgumentException e) {
				msg = getNow() + ": " + fileName + " does not admit a situation. Details:" + e.getMessage() + "\nIgnored.";
				System.out.println(msg);
				LOG.severe(msg);
				checkInterrupted = true;
				continue;
			} catch (IllegalStateException e1) {
				msg = getNow() + ": " + fileName + " cannot be executed for the following unmanaged reason. Details:" + e1.getMessage() +
				      "\nIgnored.";
				LOG.severe(msg);
				checkInterrupted = true;
				continue;
			}
			if (situation == null) {
				msg = getNow() + ": " + fileName + " does not admit a situation";
				System.out.println(msg);
				LOG.severe(msg);
				checkInterrupted = true;
				continue;
			}
			LOG.info("Situation: " + situation);

			final STNURTE.Strategy middlePointStrategy = buildMiddlePointStrategy((int) (situation.maxOutlierSpan() * 2.5));

			if (situation.isWithinBounds()) {
				LOG.info("Execution OKinBounds: All contingent durations are within their bounds. Execution is not necessary.");
				localExitExe_E.addValue(ExitExecution.okInBound);
				localExitExe_M.addValue(ExitExecution.okInBound);
				gExecutionExit_E.addValue(ExitExecution.okInBound);
				gExecutionExit_M.addValue(ExitExecution.okInBound);
//				localExecTimeStat_E.addValue(1);
				continue;
			}

			final STNURTE.Strategy situationStrategy = buildSituationStrategy(situation.situation);

			//EARLY EXECUTION STRATEGY
			LOG.info("***\nTest with FIRST_NODE_EARLY_EXECUTION_STRATEGY\n***");
			try {
				status = stnuRTE.rte(middlePointStrategy, situationStrategy);
			} catch (IllegalStateException e) {
				msg = getNow() + ": " + fileName + " cannot be executed with the current situation. Details: " + e.getMessage()
				      + "\nContinue with another try!";
//				System.out.println(msg);
				LOG.info("EARLY Execution notOKOutBounds: " + msg);
				localExitExe_E.addValue(ExitExecution.notOkOutBounds);
				gExecutionExit_E.addValue(ExitExecution.notOkOutBounds);
				localDurationOutsideBoundsInNOTOK_E.addValue(situation.durationsOutOfBounds());
				rteExists = false;
			}

			if (rteExists) {
				LOG.info("EARLY Execution oKOutBounds.");
				localExitExe_E.addValue(ExitExecution.okOutBounds);
				gExecutionExit_E.addValue(ExitExecution.okOutBounds);
				localExecTimeStat_E.addValue(status.executionTimeRTEns.getMean());
				localDurationOutsideBoundsInOK_E.addValue(situation.durationsOutOfBounds());
			}

			//MIDDLE POINT EXECUTION STRATEGY
			LOG.info("***\nTest with FIRST_NODE_MIDDLE_EXECUTION_STRATEGY\n***");
			try {
				status = stnuRTE.rte(STNURTE.StrategyEnum.FIRST_NODE_MIDDLE_EXECUTION_STRATEGY, situationStrategy);
			} catch (IllegalStateException e) {
				msg =
					getNow() + ": " + fileName + " cannot be executed with the current situation. Details: " + e.getMessage() + "\nContinue with another try!";
//				System.out.println(msg);
				LOG.info("MIDDLE Execution notOKOutBounds: " + msg);
				localExitExe_M.addValue(ExitExecution.notOkOutBounds);
				gExecutionExit_M.addValue(ExitExecution.notOkOutBounds);
				localDurationOutsideBoundsInNOTOK_M.addValue(situation.durationsOutOfBounds());
				continue;
			}
			LOG.info("MIDDLE Execution oKOutBounds.");
			localExitExe_M.addValue(ExitExecution.okOutBounds);
			gExecutionExit_M.addValue(ExitExecution.okOutBounds);
//			localExecTimeStat_E.addValue(status.executionTimeRTEns.getSum());
			localDurationOutsideBoundsInOK_M.addValue(situation.durationsOutOfBounds());
		} // end for checking repetition for a single file

		if (checkInterrupted) {
			return "NOT EXECUTABLE";
		}
		final double localAvgExeInSec = nanoSeconds2Seconds(localExecTimeStat_E.getMean());
		final double localStdDevExeInSec = nanoSeconds2Seconds(localExecTimeStat_E.getStandardDeviation());

		assert situation != null;
		if (!Double.isNaN(localAvgExeInSec)) {
			gExecTimeInSec.addValue(localAvgExeInSec);
			gProbConjunctedProbMass.addValue(situation.conjunctProbabilityMass);
		}
		if (!Double.isNaN(localDurationOutsideBoundsInOK_E.getMean())) {
			gDurationOutsideBoundsInOK_E.addValue(localDurationOutsideBoundsInOK_E.getMean());
		}
		if (!Double.isNaN(localDurationOutsideBoundsInNOTOK_E.getMean())) {
			gDurationOutsideBoundsInNOTOK_E.addValue(localDurationOutsideBoundsInNOTOK_E.getMean());
		}
		if (!Double.isNaN(localDurationOutsideBoundsInOK_M.getMean())) {
			gDurationOutsideBoundsInOK_M.addValue(localDurationOutsideBoundsInOK_M.getMean());
		}
		if (!Double.isNaN(localDurationOutsideBoundsInNOTOK_M.getMean())) {
			gDurationOutsideBoundsInNOTOK_M.addValue(localDurationOutsideBoundsInNOTOK_M.getMean());
		}

		LOG.info(fileName + " has been executed: average execution time [s]: " + localAvgExeInSec);
		rowToWrite += String.format(OUTPUT_ROW_ALG_STATS,//
		                            localAvgExeInSec, //
		                            localStdDevExeInSec,//
		                            situation.conjunctProbabilityMass,//
		                            //EARLY
		                            localExitExe_E.getPct(ExitExecution.okInBound),//
		                            localExitExe_E.getPct(ExitExecution.okOutBounds),//
		                            localExitExe_E.getPct(ExitExecution.notOkOutBounds),//
		                            localDurationOutsideBoundsInOK_E.getMean(),//
		                            localDurationOutsideBoundsInNOTOK_E.getMean(),//
		                            //MIDDLE
		                            localExitExe_M.getPct(ExitExecution.okInBound),//
		                            localExitExe_M.getPct(ExitExecution.okOutBounds),//
		                            localExitExe_M.getPct(ExitExecution.notOkOutBounds),//
		                            localDurationOutsideBoundsInOK_M.getMean(),//
		                            localDurationOutsideBoundsInNOTOK_M.getMean()//
		                           );
		return rowToWrite;
	}

	/**
	 * Simple method to manage command line parameters using {@code args4j} library.
	 *
	 * @param args input arguments
	 *
	 * @return false if a parameter is missing, or it is wrong. True if every parameter is given in a right format.
	 */
	private boolean manageParameters(String[] args) {
		final CmdLineParser parser = new CmdLineParser(this);
		try {
			parser.parseArgument(args);
		} catch (CmdLineException e) {
			// if there's a problem in the command line, you'll get this exception. this will report an error message.
			System.err.println(e.getMessage());
			System.err.println("java -cp CSTNU-<version>.jar -cp it.univr.di.cstnu.DispatchabilityBenchmarkRunner [options...] arguments...");
			// print the list of available options
			parser.printUsage(System.err);
			System.err.println();

			// print option sample. This is useful some time
			// System.err.println("Example: java -jar Checker.jar" + parser.printExample(OptionHandlerFilter.REQUIRED) +
			// " <STNU_file_name0> <STNU_file_name1>...");
			return false;
		}

		if (versionReq) {
			System.out.print(
				getClass().getName() + " " + VERSIONandDATE + ". Academic and non-commercial use only.\n" + "Copyright © 2017-2020, Roberto Posenato");
			return true;
		}

		if (outputFile != null) {
			if (outputFile.isDirectory()) {
				System.err.println("Output file is a directory.");
				parser.printUsage(System.err);
				System.err.println();
				return false;
			}
			// filename has to end with .csv
			if (!outputFile.getName().endsWith(".csv")) {
				if (!outputFile.renameTo(new File(outputFile.getAbsolutePath() + ".csv"))) {
					final String m = "File " + outputFile.getAbsolutePath() + " cannot be renamed!";
					LOG.severe(m);
					System.err.println(m);
					return false;
				}
			}
			try {
				output = new PrintStream(new FileOutputStream(outputFile, true), true, StandardCharsets.UTF_8);
			} catch (IOException e) {
				System.err.println("Output file cannot be created: " + e.getMessage());
				parser.printUsage(System.err);
				System.err.println();
				return false;
			}
		} else {
			output = System.out;
		}

		final String suffix = "pstn";

		instances = new ArrayList<>(inputFiles.length);
		for (final String fileName : inputFiles) {
			final File file = new File(fileName);
			if (!file.exists()) {
				System.err.println("File " + fileName + " does not exit.");
				parser.printUsage(System.err);
				System.err.println();
				return false;
			}
			if (!file.getName().endsWith(suffix)) {
				System.err.println(
					"File " + fileName + " has not the right suffix associated to the suffix of the given network type (right suffix: " + suffix +
					"). Game over :-/");
				parser.printUsage(System.err);
				System.err.println();
				return false;
			}
			instances.add(file);
		}

		return true;
	}

	/**
	 * Loads the file and execute all the actions (specified as instance parameter) on the network represented by the file.
	 *
	 * @param file             input file
	 * @param runState         current state
	 * @param globalStatistics global statistics
	 *
	 * @return true if required task ends successfully, false otherwise.
	 */
	private boolean worker(@Nonnull File file,//
	                       @Nonnull RunMeter runState,//
	                       @Nonnull GlobalStatistics globalStatistics) {

		if (LOG.isLoggable(Level.FINER)) {
			LOG.finer("Loading " + file.getName() + "...");
		}
		final TNGraphMLReader<STNUEdge> graphMLReader = new TNGraphMLReader<>();
		final TNGraph<STNUEdge> graphToExecute;
		try {
			graphToExecute = graphMLReader.readGraph(file, currentEdgeImplClass);
		} catch (Exception e2) {
			final String msg = "File " + file.getName() + " cannot be parsed. Details: " + e2.getMessage() + ".\nIgnored.";
			LOG.warning(msg);
			System.out.println(msg);
			return false;
		}
		LOG.finer("...done!");
		//NODES and EDGES in the original graph. DO NOT MOVE such variable.
		final int nNodes = graphToExecute.getVertexCount();
		final int nEdges = graphToExecute.getEdgeCount();

		final PSTN pstn = new PSTN(graphToExecute);
		try {
			pstn.initAndCheck();
		} catch (Exception e) {
			final String msg = getNow() + ": " + file.getName() + " is not a not well-defined instance. Details:" + e.getMessage() + "\nIgnored.";
			System.out.println(msg);
			LOG.severe(msg);
			return false;
		}
		//Only now contingent node number is significative
		final int nContingents = graphToExecute.getContingentNodeCount();

		final GlobalStatisticsKey globalStatisticsKey = new GlobalStatisticsKey(nNodes, nContingents);

		SummaryStatistics gNetworkEdges = globalStatistics.networkEdges.get(globalStatisticsKey);
		if (gNetworkEdges == null) {
			gNetworkEdges = new SummaryStatistics();
			globalStatistics.networkEdges.put(globalStatisticsKey, gNetworkEdges);
		}
		gNetworkEdges.addValue(nEdges);

		SummaryStatistics gExecTimeInSec = globalStatistics.execTimeInSec.get(globalStatisticsKey);
		if (gExecTimeInSec == null) {
			gExecTimeInSec = new SummaryStatistics();
			globalStatistics.execTimeInSec.put(globalStatisticsKey, gExecTimeInSec);
		}

		SummaryStatistics gProbConjunctedProbMass = globalStatistics.probConjunctedProbMass.get(globalStatisticsKey);
		if (gProbConjunctedProbMass == null) {
			gProbConjunctedProbMass = new SummaryStatistics();
			globalStatistics.probConjunctedProbMass.put(globalStatisticsKey, gProbConjunctedProbMass);
		}

		Frequency gExecutionExit_E = globalStatistics.executionExit_E.get(globalStatisticsKey);
		if (gExecutionExit_E == null) {
			gExecutionExit_E = new Frequency();
			globalStatistics.executionExit_E.put(globalStatisticsKey, gExecutionExit_E);
		}

		SummaryStatistics gDurationOutsideBoundsInOK_E = globalStatistics.durationOutsideBoundsInOK_E.get(globalStatisticsKey);
		if (gDurationOutsideBoundsInOK_E == null) {
			gDurationOutsideBoundsInOK_E = new SummaryStatistics();
			globalStatistics.durationOutsideBoundsInOK_E.put(globalStatisticsKey, gDurationOutsideBoundsInOK_E);
		}

		SummaryStatistics gDurationOutsideBoundsInNOTOK_E = globalStatistics.durationOutsideBoundsInNOTOK_E.get(globalStatisticsKey);
		if (gDurationOutsideBoundsInNOTOK_E == null) {
			gDurationOutsideBoundsInNOTOK_E = new SummaryStatistics();
			globalStatistics.durationOutsideBoundsInNOTOK_E.put(globalStatisticsKey, gDurationOutsideBoundsInNOTOK_E);
		}

		Frequency gExecutionExit_M = globalStatistics.executionExit_M.get(globalStatisticsKey);
		if (gExecutionExit_M == null) {
			gExecutionExit_M = new Frequency();
			globalStatistics.executionExit_M.put(globalStatisticsKey, gExecutionExit_M);
		}

		SummaryStatistics gDurationOutsideBoundsInOK_M = globalStatistics.durationOutsideBoundsInOK_M.get(globalStatisticsKey);
		if (gDurationOutsideBoundsInOK_M == null) {
			gDurationOutsideBoundsInOK_M = new SummaryStatistics();
			globalStatistics.durationOutsideBoundsInOK_M.put(globalStatisticsKey, gDurationOutsideBoundsInOK_M);
		}

		SummaryStatistics gDurationOutsideBoundsInNOTOK_M = globalStatistics.durationOutsideBoundsInNOTOK_M.get(globalStatisticsKey);
		if (gDurationOutsideBoundsInNOTOK_M == null) {
			gDurationOutsideBoundsInNOTOK_M = new SummaryStatistics();
			globalStatistics.durationOutsideBoundsInNOTOK_M.put(globalStatisticsKey, gDurationOutsideBoundsInNOTOK_M);
		}


		String rowToWrite = String.format(OUTPUT_ROW_GRAPH, file.getName(), Integer.valueOf(nNodes), Integer.valueOf(nContingents), Integer.valueOf(nEdges));

//		final PSTN.PSTNCheckStatus status;
		LOG.info(getNow() + ": Executing PSTN: start.");
		rowToWrite = executePSTNTest(pstn, rowToWrite,//
		                             gExecTimeInSec,//
		                             gProbConjunctedProbMass,//
		                             gExecutionExit_E,//
		                             gDurationOutsideBoundsInOK_E,//
		                             gDurationOutsideBoundsInNOTOK_E,//
		                             gExecutionExit_M,//
		                             gDurationOutsideBoundsInOK_M,//
		                             gDurationOutsideBoundsInNOTOK_M//
		                            );
		LOG.info(getNow() + ": Executing PSTN: finished.");

		output.println(rowToWrite);
		output.flush();
		runState.printProgress();
		return !rowToWrite.contains("NOT EXECUTABLE");
	}
}
