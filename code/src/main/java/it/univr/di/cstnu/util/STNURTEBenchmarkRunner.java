// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.util;

import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.univr.di.Debug;
import it.univr.di.cstnu.algorithms.STNURTE;
import it.univr.di.cstnu.graph.STNUEdge;
import it.univr.di.cstnu.graph.STNUEdgeInt;
import it.univr.di.cstnu.graph.TNGraph;
import it.univr.di.cstnu.graph.TNGraphMLReader;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.StringArrayOptionHandler;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple class to determine the average execution time (and std dev) of the RTE* STNU algorithm on a given set of STNUs.
 * <p>
 * The main idea is the following:
 * <ul>
 *  <li>a user can select which execution strategies to use for selecting ordinary timepoints (and execution time) and simulating contingent durations.
 *  For example, consider {@link it.univr.di.cstnu.algorithms.STNURTE.StrategyEnum#EARLY_EXECUTION_STRATEGY}, {@link it.univr.di.cstnu.algorithms.STNURTE.StrategyEnum#LATE_EXECUTION_STRATEGY}, etc.
 *  <li>this program executes the RTE algorithm with the selected strategies on 3 dispatchable instances of each instance of the benchmark.
 *  <li>The 3 dispatchable instance are:
 *  <ol>
 *      <li>one given by Morris2014Dispatchable algorithm (its file name must end with {@code .Morris2014Dispatchable-checked.stnu}),</li>
 *      <li>one given by the FD_STNU algorithm (its file name must end with {@code .FD_STNU-checked.stnu}),</li>
 *      <li>one give by the minimized version of the one determined by FD_STNU (its file name must end with {@code .FD_STNU-checked-minimized.stnu})</li>
 *  </ol>
 *   and
 *  .
 * </ul>
 *
 * @author posenato
 * @version $Rev: 732 $
 */
public class STNURTEBenchmarkRunner {

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
		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> morris2014InitTimeNS = new Object2ObjectAVLTreeMap<>();
		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> morris2014ExecTimeNS = new Object2ObjectAVLTreeMap<>();
		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> morris2014NetworkEdges = new Object2ObjectAVLTreeMap<>();
		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> FD_STNUInitTimeNS = new Object2ObjectAVLTreeMap<>();
		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> FD_STNUExecTimeNS = new Object2ObjectAVLTreeMap<>();
		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> FD_STNUNetworkEdges = new Object2ObjectAVLTreeMap<>();
		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> minDispOfFD_STNUInitTimeNS = new Object2ObjectAVLTreeMap<>();
		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> minDispOfFD_STNUExecTimeNS = new Object2ObjectAVLTreeMap<>();
		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> minDispOfFD_STNUNetworkEdges = new Object2ObjectAVLTreeMap<>();
	}
	/**
	 * Suffix of STNU file
	 */
	static final String stnuSuffix = ".stnu";
	/**
	 * Filename suffix of STNU checked by MorrisDispatchable algorithm.
	 */
	static final String morrisSuffix = ".Morris2014Dispatchable-checked.stnu";
	/**
	 * Filename suffix of STNU checked by FD_STNU algorithm.
	 */
	static final String fdSuffix = ".FD_STNU-checked.stnu";
	/**
	 * Filename suffix of STNU checked by FD_STNU and minimized by FD_ESTNU algorithm.
	 */
	static final String fdMinSuffix = ".FD_STNU-checked-minimized.stnu";
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
		+ "avgInitTimeMorris2014[ns]" + CSVSep
		+ "stdDevInitTimeMorris2014[ns]" + CSVSep
		+ "avgExeTimeMorris2014[ns]" + CSVSep
		+ "stdDevExeTimeMorris2014[ns]" + CSVSep
		+ "avgEdgesMorris2014" + CSVSep
		+ "stdDevEdgesMorris2014" + CSVSep
		+ "avgInitTimeFD_STNU[ns]" + CSVSep
		+ "stdDevInitFD_STNU[ns]" + CSVSep
		+ "avgExeTimeFD_STNU[ns]" + CSVSep
		+ "stdDevExeFD_STNU[ns]" + CSVSep
		+ "avgEdgesFD_STNU" + CSVSep
		+ "stdDevEdgesFD_STNU" + CSVSep
		+ "avgExeTimeMinDispESTNU_FD_STNU[ns]" + CSVSep
		+ "stdDevMinDispESTNU_FD_STNU[ns]" + CSVSep
		+ "avgInitTimeMinDispESTNU_FD_STNU[ns]" + CSVSep
		+ "stdDevInitMinDispESTNU_FD_STNU[ns]" + CSVSep
		+ "avgEdgesMinDispESTNU_FD_STNU" + CSVSep
		+ "stdDevEdgesMinDispESTNU_FD_STNU" + CSVSep
		+ "%n";
	/**
	 *
	 */
	static final String GLOBAL_HEADER_ROW =
		"%d" + CSVSep
		+ "%d" + CSVSep
		+ "%d" + CSVSep
		+ "%E" + CSVSep // avgEdges
		+ "%E" + CSVSep
		+ "%E" + CSVSep // Morris2014 avgInitTime
		+ "%E" + CSVSep
		+ "%E" + CSVSep // Morris2014 avgExe
		+ "%E" + CSVSep
		+ "%E" + CSVSep // Morris2014 avgEdges
		+ "%E" + CSVSep
		+ "%E" + CSVSep // FD_STNU avInitTime
		+ "%E" + CSVSep
		+ "%E" + CSVSep // FD_STNU avgExeTime
		+ "%E" + CSVSep
		+ "%E" + CSVSep // FD_STNU avgEdges
		+ "%E" + CSVSep
		+ "%E" + CSVSep // minDispESTNU(FD_STNU) avgInitTime
		+ "%E" + CSVSep
		+ "%E" + CSVSep // minDispESTNU(FD_STNU) avgExeTime
		+ "%E" + CSVSep
		+ "%E" + CSVSep// minDispESTNU(FD_STNU) avgEdges
		+ "%E" + CSVSep
		+ "%n";
	/**
	 * class logger
	 */
	static final Logger LOG = Logger.getLogger(STNURTEBenchmarkRunner.class.getName());
	/**
	 * Output file header
	 */
	static final String OUTPUT_HEADER =
		"fileName" + CSVSep
		+ "#nodes" + CSVSep
		+ "#contingents" + CSVSep
		+ "#edges" + CSVSep
		+ "RTE Morris2014 avgInitTime[ns]" + CSVSep
		+ "std.dev.[ns]" + CSVSep
		+ "RTE Morris2014 avgExeTime[ns]" + CSVSep
		+ "std.dev.[ns]" + CSVSep
		+ "Morris2014 #edges" + CSVSep
		+ "RTE FD_STNU avgInitTime[ns]" + CSVSep
		+ "std.dev.[ns]" + CSVSep
		+ "RTE FD_STNU avgExeTime[ns]" + CSVSep
		+ "std.dev.[ns]" + CSVSep
		+ "FD_STNU #edges" + CSVSep
		+ "RTE minDispESTNU(FD_STNU) avgInitTime[ns]" + CSVSep
		+ "std.dev." + CSVSep
		+ "RTE minDispESTNU(FD_STNU) avgExeTime[ns]" + CSVSep
		+ "std.dev." + CSVSep
		+ "minDispESTNU(FD_STNU) #edges" + CSVSep
		+ "%n";
	/**
	 * OUTPUT_ROW is split in OUTPUT_ROW_GRAPH + OUTPUT_ROW_ALG_STATS +  OUTPUT_ROW_ALG_STATS +  OUTPUT_ROW_ALG_STATS
	 */
	static final String OUTPUT_ROW_GRAPH =
		"%s" + CSVSep //name
		+ "%d" + CSVSep //#nodes
		+ "%d" + CSVSep //#contingents
		+ "%d" + CSVSep; //#edges

	/**
	 * OUTPUT_ROW is split in OUTPUT_ROW_GRAPH + OUTPUT_ROW_ALG_STATS +  OUTPUT_ROW_ALG_STATS +  OUTPUT_ROW_ALG_STATS
	 */
	static final String OUTPUT_ROW_ALG_STATS =
		"%E" + CSVSep // alg avgInitTime
		+ "%E" + CSVSep // alg avgInitTime dev std
		+ "%E" + CSVSep // alg avgExe
		+ "%E" + CSVSep // alg avgExe dev std
		+ "%d" + CSVSep // alg #edges
		;
	/**
	 * Version
	 */
	static final String VERSIONandDATE = "1.0, February, 27 2024";
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

		LOG.finest("STNU RTE* runner " + VERSIONandDATE + "\nStart...");
		System.out.println("STNU RTE* runner  " + VERSIONandDATE + "\n" + getNow() + ": Start of execution.");
		final STNURTEBenchmarkRunner tester = new STNURTEBenchmarkRunner();

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
			if (tester.worker(file, runMeter, globalStatistics)) {
				nTaskSuccessfullyFinished++;
			}
		}
		final String msg = "Number of instances processed successfully over total: " + nTaskSuccessfullyFinished + "/" +
		                   tester.instances.size() + ".";
		LOG.info(msg);
		System.out.println("\n" + getNow() + ": " + msg);

		tester.output.printf(GLOBAL_HEADER);
		//Use one of the element in globalStatistics to extract all the possible globalStatisticsKeys
		for (final Object2ObjectMap.Entry<GlobalStatisticsKey, SummaryStatistics> entryNetworkEdges : globalStatistics.networkEdges.object2ObjectEntrySet()) {
			final GlobalStatisticsKey globalStatisticsKey = entryNetworkEdges.getKey();
			tester.output.printf(GLOBAL_HEADER_ROW,
			                     Long.valueOf(entryNetworkEdges.getValue().getN()),
			                     Integer.valueOf(globalStatisticsKey.getNodes()),
			                     Integer.valueOf(globalStatisticsKey.getContingent()),
			                     Double.valueOf(entryNetworkEdges.getValue().getMean()),
			                     Double.valueOf(entryNetworkEdges.getValue().getStandardDeviation()),
			                     Double.valueOf(globalStatistics.morris2014InitTimeNS.get(globalStatisticsKey).getMean()),
			                     Double.valueOf(globalStatistics.morris2014InitTimeNS.get(globalStatisticsKey).getStandardDeviation()),
			                     Double.valueOf(globalStatistics.morris2014ExecTimeNS.get(globalStatisticsKey).getMean()),
			                     Double.valueOf(globalStatistics.morris2014ExecTimeNS.get(globalStatisticsKey).getStandardDeviation()),
			                     Double.valueOf(globalStatistics.morris2014NetworkEdges.get(globalStatisticsKey).getMean()),
			                     Double.valueOf(globalStatistics.morris2014NetworkEdges.get(globalStatisticsKey).getStandardDeviation()),
			                     Double.valueOf(globalStatistics.FD_STNUInitTimeNS.get(globalStatisticsKey).getMean()),
			                     Double.valueOf(globalStatistics.FD_STNUInitTimeNS.get(globalStatisticsKey).getStandardDeviation()),
			                     Double.valueOf(globalStatistics.FD_STNUExecTimeNS.get(globalStatisticsKey).getMean()),
			                     Double.valueOf(globalStatistics.FD_STNUExecTimeNS.get(globalStatisticsKey).getStandardDeviation()),
			                     Double.valueOf(globalStatistics.FD_STNUNetworkEdges.get(globalStatisticsKey).getMean()),
			                     Double.valueOf(globalStatistics.FD_STNUNetworkEdges.get(globalStatisticsKey).getStandardDeviation()),
			                     Double.valueOf(globalStatistics.minDispOfFD_STNUInitTimeNS.get(globalStatisticsKey).getMean()),
			                     Double.valueOf(globalStatistics.minDispOfFD_STNUInitTimeNS.get(globalStatisticsKey).getStandardDeviation()),
			                     Double.valueOf(globalStatistics.minDispOfFD_STNUExecTimeNS.get(globalStatisticsKey).getMean()),
			                     Double.valueOf(globalStatistics.minDispOfFD_STNUExecTimeNS.get(globalStatisticsKey).getStandardDeviation()),
			                     Double.valueOf(globalStatistics.minDispOfFD_STNUNetworkEdges.get(globalStatisticsKey).getMean()),
			                     Double.valueOf(globalStatistics.minDispOfFD_STNUNetworkEdges.get(globalStatisticsKey).getStandardDeviation())
			                    );
		}
		tester.output.printf("%n%n%n");
		tester.output.close();
	}

	/**
	 * @param state state of the execution
	 *
	 * @return the execution time determined
	 */
	private static double getExecutionTimeNS(STNURTE.RTEState state) {
		return state.executionTimeRTEns.getSum()
		       + state.executionTimeRTEUpdateNs.getSum()
		       + state.executionTimeHCEns.getSum()
		       + state.executionTimeHOEns.getSum();
	}

	/**
	 * @return current time in {@link #dateFormatter} format
	 */
	private static String getNow() {
		return dateFormatter.format(new Date());
	}
	/**
	 * Class for representing edge .
	 */
	private final Class<STNUEdgeInt> currentEdgeImplClass = STNUEdgeInt.class;
	/**
	 * The input file names. Each file has to contain a CSTN graph in GraphML format.
	 */
	@Argument(required = true, usage = "Input files. Each input file has to be an STNU graph in GraphML format.",
		metaVar = "STNU_file_names", handler = StringArrayOptionHandler.class)
	private String[] inputFiles;
	/**
	 *
	 */
	private List<File> instances;
	/**
	 * Parameter for asking how many times to check the DC for each STNU.
	 */
	@Option(name = "--numRepetitionDCCheck", usage = "Number of time to re-execute DC checking")
	private int nDCRepetition = 30;
	/**
	 * Output stream to outputFile
	 */
	private PrintStream output;
	/**
	 * Output file where to write the determined experimental execution times in CSV format.
	 */
	@Option(name = "-o", aliases = "--output",
		usage = "Output to this file in CSV format. If file is already present, data will be added.",
		metaVar = "outputFile")
	private File outputFile;
	/**
	 * Check a STNU instance using Morris algorithm
	 */
	@Option(name = "--morris", usage = "Check a STNU instance using Morris2014Dispatchable.")
	private boolean morris2014;
	/**
	 * Check a STNU instance using Morris algorithm
	 */
	@Option(name = "--fd", usage = "Check a STNU instance using FD_STNU.")
	private boolean fd_stnu;

//	/**
//	 *
//	 */
//	@Option(name = "--save", usage = "Save all checked instances.")
//	private boolean save;

//	/**
//	 * Parameter for asking timeout in sec.
//	 */
//	@Option(name = "--timeOut", usage = "Time in seconds.")
//	private int timeOut = 1800; // 20 min
	/**
	 * Determine min dispatchable.
	 */
	@Option(name = "--minFD", usage = "Execute the min dispatchable ESTNU after FD_STNU check.")
	private boolean minDispatchableFD;
	/**
	 * Software Version.
	 */
	@Option(name = "-v", aliases = "--version", usage = "Version")
	private boolean versionReq;

	/**
	 * @param file   file representing a STNU instance. Its name it is used to open other filename built using the suffix.
	 * @param suffix suffix that replaces the {@link #stnuSuffix} in the name associated to file. The resulting name must be relative a file containing the STNU
	 *               instance used to build the RTE object. Use "" if the file is correct as it is.
	 *
	 * @return an RTE if the filename+suffix is relative to a file containing an STNU, null otherwise.
	 */
	private STNURTE getRTE(@Nonnull File file, @Nonnull String suffix) {
		if (suffix.length() > 0) {
			final String fileName = file.getName();
			final String fileNameNewSuffix = fileName.substring(0, fileName.length() - stnuSuffix.length()) + suffix;
			file = new File(fileNameNewSuffix);
		}
		final TNGraph<STNUEdge> graphToExecute;
		final TNGraphMLReader<STNUEdge> graphMLReader = new TNGraphMLReader<>();
		try {
			graphToExecute = graphMLReader.readGraph(file, currentEdgeImplClass);
		} catch (IOException | ParserConfigurationException | SAXException e2) {
			final String msg = "File " + file.getName() + " cannot be parsed. Details: " + e2.getMessage() + ".\nIgnored.";
			LOG.warning(msg);
			System.out.println(msg);
			return null;
		}
		return new STNURTE(graphToExecute);
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
			System.err.println(
				"java -cp CSTNU-<version>.jar -cp it.univr.di.cstnu.STNURTEBenchmarkRunner [options...] arguments...");
			// print the list of available options
			parser.printUsage(System.err);
			System.err.println();

			// print option sample. This is useful some time
			// System.err.println("Example: java -jar Checker.jar" + parser.printExample(OptionHandlerFilter.REQUIRED) +
			// " <STNU_file_name0> <STNU_file_name1>...");
			return false;
		}

		if (versionReq) {
			System.out.print(getClass().getName() + " " + VERSIONandDATE + ". Academic and non-commercial use only.\n"
			                 + "Copyright © 2017-2020, Roberto Posenato");
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
				if (outputFile.exists()) {
					if (!outputFile.renameTo(new File(outputFile.getAbsolutePath() + ".csv"))) {
						final String m = "File " + outputFile.getAbsolutePath() + " cannot be renamed!";
						LOG.severe(m);
						System.err.println(m);
						return false;
					}
				} else {
					outputFile = new File(outputFile.getAbsolutePath() + ".csv");
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

		final String suffix = "stnu";

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
				System.err.println("File " + fileName
				                   +
				                   " has not the right suffix associated to the suffix of the given network type (right suffix: "
				                   + suffix + "). Game over :-/");
				parser.printUsage(System.err);
				System.err.println();
				return false;
			}
			instances.add(file);
		}

		return true;
	}

	/**
	 * Execute stnu using {@link STNURTE}. Then, it adds statistics to gExecTimeInSec and gNetworkEdges. It returns the string representing the
	 * ({@link #OUTPUT_ROW_ALG_STATS}) part to add to the row in the statistics file.
	 */
	private String rteExecutor(
		@Nonnull STNURTE rte,
		@Nonnull String rowToWrite,
		@Nonnull SummaryStatistics gInitTimeInMS,
		@Nonnull SummaryStatistics gExecTimeInMS,
		@Nonnull SummaryStatistics gNetworkEdges) {

		STNURTE.RTEState status;
		final SummaryStatistics localSummaryInitStat = new SummaryStatistics();
		final SummaryStatistics localSummaryExeStat = new SummaryStatistics();
		final String fileName = rte.getG().getName();
		for (int j = 0; j < nDCRepetition; j++) {
			if (Debug.ON) {
				LOG.info("Test " + (j + 1) + "/" + nDCRepetition + " for STNU " + fileName);
			}
			try {
				status = rte.rte(STNURTE.StrategyEnum.FIRST_NODE_EARLY_EXECUTION_STRATEGY, STNURTE.StrategyEnum.MIDDLE_EXECUTION_STRATEGY);
			} catch (Exception e) {
				final String msg = "It was not possible to find a schedule for " + fileName + " because of the following error: " + e.getMessage() +
				                   ". The instance is ignored.";
				LOG.severe(msg);
				System.err.println(msg);
				continue;
			}
			localSummaryInitStat.addValue(status.executionTimeRTEinitNs.getSum());
			localSummaryExeStat.addValue(getExecutionTimeNS(status));
		} // end for checking repetition for a single file

		final double localAvgInitInNS = localSummaryInitStat.getMean();
		final double localStdDevInitInNS = localSummaryInitStat.getStandardDeviation();
		final double localAvgExeInNS = localSummaryExeStat.getMean();
		final double localStdDevExeInNS = localSummaryExeStat.getStandardDeviation();
		if (!Double.isNaN(localAvgInitInNS)) {
			gInitTimeInMS.addValue(localAvgInitInNS);
		}
		if (!Double.isNaN(localAvgExeInNS)) {
			gExecTimeInMS.addValue(localAvgExeInNS);
		}
		gNetworkEdges.addValue(rte.getG().getEdgeCount());
		if (Debug.ON) {
			LOG.info(fileName + " has been executed.");
			LOG.info(fileName + " average checking time [ns]: " + localAvgExeInNS);
			LOG.info(fileName + " std. deviation [ns]: " + localStdDevExeInNS);
		}
		rowToWrite += String.format(OUTPUT_ROW_ALG_STATS,
		                            Double.valueOf(localAvgInitInNS),
		                            Double.valueOf((nDCRepetition > 1) ? localStdDevInitInNS : Double.NaN),
		                            Double.valueOf(localAvgExeInNS),
		                            Double.valueOf((nDCRepetition > 1) ? localStdDevExeInNS : Double.NaN),
		                            Integer.valueOf(rte.getG().getEdgeCount())
		                           );
		return rowToWrite;
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
	private boolean worker(
		@Nonnull File file,
		@Nonnull RunMeter runState,
		@Nonnull GlobalStatistics globalStatistics) {

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Considering " + file.getName() + "...");
			}
		}
		final TNGraph<STNUEdge> graphToExecute;
		final TNGraphMLReader<STNUEdge> graphMLReader = new TNGraphMLReader<>();
		try {
			graphToExecute = graphMLReader.readGraph(file, currentEdgeImplClass);
		} catch (IOException | ParserConfigurationException | SAXException e2) {
			final String msg = "File " + file.getName() + " cannot be parsed. Details: " + e2.getMessage() + ".\nIgnored.";
			LOG.warning(msg);
			System.out.println(msg);
			return false;
		}
		//NODES and EDGES in the original graph. DO NOT MOVE such variable.
		final int nNodes = graphToExecute.getVertexCount();
		final int nEdges = graphToExecute.getEdgeCount();
		final int nContingents = graphToExecute.getContingentNodeCount();
		//Only now contingent node number is significative
		final GlobalStatisticsKey globalStatisticsKey = new GlobalStatisticsKey(nNodes, nContingents);

		SummaryStatistics gNetworkEdges = globalStatistics.networkEdges.get(globalStatisticsKey);
		if (gNetworkEdges == null) {
			gNetworkEdges = new SummaryStatistics();
			globalStatistics.networkEdges.put(globalStatisticsKey, gNetworkEdges);
		}
		gNetworkEdges.addValue(nEdges);

		SummaryStatistics gMorris2014InitTimeInNS = globalStatistics.morris2014InitTimeNS.get(globalStatisticsKey);
		if (gMorris2014InitTimeInNS == null) {
			gMorris2014InitTimeInNS = new SummaryStatistics();
			globalStatistics.morris2014InitTimeNS.put(globalStatisticsKey, gMorris2014InitTimeInNS);
		}

		SummaryStatistics gMorris2014ExecTimeInNS = globalStatistics.morris2014ExecTimeNS.get(globalStatisticsKey);
		if (gMorris2014ExecTimeInNS == null) {
			gMorris2014ExecTimeInNS = new SummaryStatistics();
			globalStatistics.morris2014ExecTimeNS.put(globalStatisticsKey, gMorris2014ExecTimeInNS);
		}

		SummaryStatistics gMorris2014NetworkEdges = globalStatistics.morris2014NetworkEdges.get(globalStatisticsKey);
		if (gMorris2014NetworkEdges == null) {
			gMorris2014NetworkEdges = new SummaryStatistics();
			globalStatistics.morris2014NetworkEdges.put(globalStatisticsKey, gMorris2014NetworkEdges);
		}

		SummaryStatistics gFD_STNUInitTimeInNS = globalStatistics.FD_STNUInitTimeNS.get(globalStatisticsKey);
		if (gFD_STNUInitTimeInNS == null) {
			gFD_STNUInitTimeInNS = new SummaryStatistics();
			globalStatistics.FD_STNUInitTimeNS.put(globalStatisticsKey, gFD_STNUInitTimeInNS);
		}
		SummaryStatistics gFD_STNUExecTimeInNS = globalStatistics.FD_STNUExecTimeNS.get(globalStatisticsKey);
		if (gFD_STNUExecTimeInNS == null) {
			gFD_STNUExecTimeInNS = new SummaryStatistics();
			globalStatistics.FD_STNUExecTimeNS.put(globalStatisticsKey, gFD_STNUExecTimeInNS);
		}

		SummaryStatistics gFD_STNUNetworkEdges = globalStatistics.FD_STNUNetworkEdges.get(globalStatisticsKey);
		if (gFD_STNUNetworkEdges == null) {
			gFD_STNUNetworkEdges = new SummaryStatistics();
			globalStatistics.FD_STNUNetworkEdges.put(globalStatisticsKey, gFD_STNUNetworkEdges);
		}

		SummaryStatistics gMinDispOfFD_STNUInitTimeInNS =
			globalStatistics.minDispOfFD_STNUInitTimeNS.get(globalStatisticsKey);
		if (gMinDispOfFD_STNUInitTimeInNS == null) {
			gMinDispOfFD_STNUInitTimeInNS = new SummaryStatistics();
			globalStatistics.minDispOfFD_STNUInitTimeNS.put(globalStatisticsKey, gMinDispOfFD_STNUInitTimeInNS);
		}

		SummaryStatistics gMinDispOfFD_STNUExecTimeInNS =
			globalStatistics.minDispOfFD_STNUExecTimeNS.get(globalStatisticsKey);
		if (gMinDispOfFD_STNUExecTimeInNS == null) {
			gMinDispOfFD_STNUExecTimeInNS = new SummaryStatistics();
			globalStatistics.minDispOfFD_STNUExecTimeNS.put(globalStatisticsKey, gMinDispOfFD_STNUExecTimeInNS);
		}

		SummaryStatistics gMinDispOfFD_STNUNetworkEdges =
			globalStatistics.minDispOfFD_STNUNetworkEdges.get(globalStatisticsKey);
		if (gMinDispOfFD_STNUNetworkEdges == null) {
			gMinDispOfFD_STNUNetworkEdges = new SummaryStatistics();
			globalStatistics.minDispOfFD_STNUNetworkEdges.put(globalStatisticsKey, gMinDispOfFD_STNUNetworkEdges);
		}

		String rowToWrite =
			String.format(OUTPUT_ROW_GRAPH, file.getName(), Integer.valueOf(nNodes), Integer.valueOf(nContingents),
			              Integer.valueOf(nEdges));
		STNURTE rte;
		if (this.morris2014) {
			if (Debug.ON) {
				LOG.info(getNow() + ": Morris2014Dispatchable: start.");
			}
			rte = getRTE(file, morrisSuffix);
			if (rte == null) {
				return false;
			}
			rowToWrite = rteExecutor(rte,
			                         rowToWrite,
			                         gMorris2014InitTimeInNS,
			                         gMorris2014ExecTimeInNS,
			                         gMorris2014NetworkEdges
			                        );
		} else {
			rowToWrite += String.format(OUTPUT_ROW_ALG_STATS,
			                            Double.NaN,
			                            Double.NaN,
			                            Double.NaN,
			                            Double.NaN,
			                            0
			                           );
		}

		if (this.fd_stnu) {
			if (Debug.ON) {
				LOG.info(getNow() + ": FD_STNU: start.");
			}
			rte = getRTE(file, fdSuffix);
			if (rte == null) {
				return false;
			}
			rowToWrite = rteExecutor(rte,
			                         rowToWrite,
			                         gFD_STNUInitTimeInNS,
			                         gFD_STNUExecTimeInNS,
			                         gFD_STNUNetworkEdges
			                        );
		} else {
			rowToWrite += String.format(OUTPUT_ROW_ALG_STATS,
			                            Double.NaN,
			                            Double.NaN,
			                            Double.NaN,
			                            Double.NaN,
			                            0
			                           );
		}

		if (this.minDispatchableFD) {
			if (Debug.ON) {
				LOG.info(getNow() + ": MinDispatchable of FD_STNU: start.");
			}
			rte = getRTE(file, fdMinSuffix);
			if (rte == null) {
				return false;
			}
			rowToWrite = rteExecutor(rte,
			                         rowToWrite,
			                         gMinDispOfFD_STNUInitTimeInNS,
			                         gMinDispOfFD_STNUExecTimeInNS,
			                         gMinDispOfFD_STNUNetworkEdges
			                        );
		} else {
			rowToWrite += String.format(OUTPUT_ROW_ALG_STATS,
			                            Double.NaN,
			                            Double.NaN,
			                            Double.NaN,
			                            Double.NaN,
			                            0
			                           );
		}

		output.println(rowToWrite);
		output.flush();
		runState.printProgress();
		return true;
	}
}
