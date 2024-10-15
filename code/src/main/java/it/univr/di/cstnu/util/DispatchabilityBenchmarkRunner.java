// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.util;

import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import it.univr.di.cstnu.algorithms.STNU;
import it.univr.di.cstnu.graph.STNUEdge;
import it.univr.di.cstnu.graph.STNUEdgeInt;
import it.univr.di.cstnu.graph.TNGraph;
import it.univr.di.cstnu.graph.TNGraphMLReader;
import it.univr.di.labeledvalue.Constants;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.StringArrayOptionHandler;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
import java.util.concurrent.CancellationException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple class to determine the average execution time (and std dev) of the STN(U) dispatchability algorithms on a given
 * set of STNUs.
 * <p>
 * The main idea is the following:
 * <ul>
 *  <li>a user can express which algorithm to test: Morris2014Dispatchable, FD_STNU, minDispatchableESTNU
 *  <li>if minDispatchableESTNU is required, then either Morris2014Dispatchable or FD_STNU must be selected because
 *   minDispatchableESTNU work on the network already checked and augmented with wait constraints.
 * </ul>
 *
 * @author posenato
 * @version $Rev: 732 $
 */
public class DispatchabilityBenchmarkRunner {

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
		 * @return negative if this has, in order, fewer nodes or fewer contingents or fewer proposition than the
		 * parameter 'o'; a positive value in the opposite case, 0 when all three values are equals to the
		 * corresponding values in 'o'.
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
	 * Each instance of this class represents a set of global statistics.
	 * Each global statistics element represents a map {@code (GlobalStatisticsKey, SummaryStatistics)}.
	 * Each SummaryStatistics element allows the determination of different statistics of all added item to the element.
	 */
	static private class GlobalStatistics {
		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> networkEdges = new Object2ObjectAVLTreeMap<>();
		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> morris2014ExecTimeInSec =
			new Object2ObjectAVLTreeMap<>();
		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> morris2014NetworkEdges =
			new Object2ObjectAVLTreeMap<>();
		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> minDispOfMorris2014ExecTimeInSec =
			new Object2ObjectAVLTreeMap<>();
		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> minDispOfMorris2014NetworkEdges =
			new Object2ObjectAVLTreeMap<>();
		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> FD_STNUExecTimeInSec = new Object2ObjectAVLTreeMap<>();
		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> FD_STNUNetworkEdges = new Object2ObjectAVLTreeMap<>();
		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> minDispOfFD_STNUExecTimeInSec =
			new Object2ObjectAVLTreeMap<>();
		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> minDispOfFD_STNUNetworkEdges =
			new Object2ObjectAVLTreeMap<>();
		Object2ObjectMap<GlobalStatisticsKey, SummaryStatistics> maxMinEdges = new Object2ObjectAVLTreeMap<>();
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
		+ "avgExeTimeMorris2014[s]" + CSVSep
		+ "stdDevExeTimeMorris2014[s]" + CSVSep
		+ "avgEdgesMorris2014" + CSVSep
		+ "stdDevEdgesMorris2014" + CSVSep
		+ "avnExeTimeMinDispESTNU_Morris[s]" + CSVSep
		+ "stdDevExeTimeMinDispESTNU_Morris[s]" + CSVSep
		+ "avgEdgesMinDispESTNU_Morris" + CSVSep
		+ "stdDevEdgesMinDispESTNU_Morris" + CSVSep
		+ "avgExeTimeFD_STNU[s]" + CSVSep
		+ "stdDevFD_STNU[s]" + CSVSep
		+ "avgEdgesFD_STNU" + CSVSep
		+ "stdDevEdgesFD_STNU" + CSVSep
		+ "avgExeTimeMinDispESTNU_FD_STNU[s]" + CSVSep
		+ "stdDevMinDispESTNU_FD_STNU[s]" + CSVSep
		+ "avgEdgesMinDispESTNU_FD_STNU" + CSVSep
		+ "stdDevEdgesMinDispESTNU_FD_STNU" + CSVSep
		+ "avgMaxMinEdges"
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
		+ "%E" + CSVSep // Morris2014 avgExe
		+ "%E" + CSVSep
		+ "%E" + CSVSep // Morris2014 avgEdges
		+ "%E" + CSVSep
		+ "%E" + CSVSep // minDispESTNU(Morris) avgExeTime
		+ "%E" + CSVSep
		+ "%E" + CSVSep // minDispESTNU(Morris) avgEdges
		+ "%E" + CSVSep
		+ "%E" + CSVSep // FD_STNU avgExeTime
		+ "%E" + CSVSep
		+ "%E" + CSVSep // FD_STNU avgEdges
		+ "%E" + CSVSep
		+ "%E" + CSVSep // minDispESTNU(FD_STNU) avgExeTime
		+ "%E" + CSVSep
		+ "%E" + CSVSep// minDispESTNU(FD_STNU) avgEdges
		+ "%E" + CSVSep
		+ "%E"
		+ "%n";
	/**
	 * class logger
	 */
	static final Logger LOG = Logger.getLogger(DispatchabilityBenchmarkRunner.class.getName());
	/**
	 * Output file header
	 */
	static final String OUTPUT_HEADER =
		"fileName" + CSVSep
		+ "#nodes" + CSVSep
		+ "#contingents" + CSVSep
		+ "#edges" + CSVSep
		+ "Morris2014 avgExeTime[s]" + CSVSep
		+ "std.dev.[s]" + CSVSep
		+ "Morris2014 #edges" + CSVSep
		+ "DC Morris2014" + CSVSep
		+ "minDispESTNU(Morris) avgExeTime[s]" + CSVSep
		+ "std.dev." + CSVSep
		+ "minDispESTNU(Morris) #edges" + CSVSep
		+ "maxMinEdgesMorris" + CSVSep
		+ "FD_STNU avgExeTime[s]" + CSVSep
		+ "std.dev.[s]" + CSVSep
		+ "FD_STNU #edges" + CSVSep
		+ "DC FD_STNU" + CSVSep
		+ "minDispESTNU(FD_STNU) avgExeTime[s]" + CSVSep
		+ "std.dev." + CSVSep
		+ "minDispESTNU(FD_STNU) #edges" + CSVSep
		+ "maxMinEdgesFD";
	/**
	 * OUTPUT_ROW is split in OUTPUT_ROW_GRAPH + OUTPUT_ROW_ALG_STATS +  OUTPUT_ROW_ALG_STATS +  OUTPUT_ROW_ALG_STATS
	 */
	static final String OUTPUT_ROW_GRAPH =
		"%s" + CSVSep
		+ "%d" + CSVSep
		+ "%d" + CSVSep
		+ "%d" + CSVSep;
	/**
	 * OUTPUT_ROW is split in OUTPUT_ROW_GRAPH + OUTPUT_ROW_ALG_STATS +  OUTPUT_ROW_ALG_STATS +  OUTPUT_ROW_ALG_STATS
	 */
	static final String OUTPUT_ROW_ALG_STATS =
		"%E" + CSVSep // alg avgExe
		+ "%E" + CSVSep // alg avgExe dev std
		+ "%d" + CSVSep // alg #edges
		+ "%s" + CSVSep // alg status
		;
	/**
	 * Version
	 */
	static final String VERSIONandDATE = "1.0, December, 01 2023";
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
		final DispatchabilityBenchmarkRunner tester = new DispatchabilityBenchmarkRunner();

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
			                     Double.valueOf(
				                     globalStatistics.morris2014ExecTimeInSec.get(globalStatisticsKey).getMean()),
			                     Double.valueOf(globalStatistics.morris2014ExecTimeInSec.get(globalStatisticsKey)
				                                    .getStandardDeviation()),
			                     Double.valueOf(
				                     globalStatistics.morris2014NetworkEdges.get(globalStatisticsKey).getMean()),
			                     Double.valueOf(globalStatistics.morris2014NetworkEdges.get(globalStatisticsKey)
				                                    .getStandardDeviation()),
			                     Double.valueOf(
				                     globalStatistics.minDispOfMorris2014ExecTimeInSec.get(globalStatisticsKey)
					                     .getMean()),
			                     Double.valueOf(
				                     globalStatistics.minDispOfMorris2014ExecTimeInSec.get(globalStatisticsKey)
					                     .getStandardDeviation()),
			                     Double.valueOf(
				                     globalStatistics.minDispOfMorris2014NetworkEdges.get(globalStatisticsKey)
					                     .getMean()),
			                     Double.valueOf(
				                     globalStatistics.minDispOfMorris2014NetworkEdges.get(globalStatisticsKey)
					                     .getStandardDeviation()),
			                     Double.valueOf(
				                     globalStatistics.FD_STNUExecTimeInSec.get(globalStatisticsKey).getMean()),
			                     Double.valueOf(globalStatistics.FD_STNUExecTimeInSec.get(globalStatisticsKey)
				                                    .getStandardDeviation()),
			                     Double.valueOf(
				                     globalStatistics.FD_STNUNetworkEdges.get(globalStatisticsKey).getMean()),
			                     Double.valueOf(globalStatistics.FD_STNUNetworkEdges.get(globalStatisticsKey)
				                                    .getStandardDeviation()),
			                     Double.valueOf(
				                     globalStatistics.minDispOfFD_STNUExecTimeInSec.get(globalStatisticsKey).getMean()),
			                     Double.valueOf(globalStatistics.minDispOfFD_STNUExecTimeInSec.get(globalStatisticsKey)
				                                    .getStandardDeviation()),
			                     Double.valueOf(
				                     globalStatistics.minDispOfFD_STNUNetworkEdges.get(globalStatisticsKey).getMean()),
			                     Double.valueOf(globalStatistics.minDispOfFD_STNUNetworkEdges.get(globalStatisticsKey)
				                                    .getStandardDeviation()),
			                     Double.valueOf(globalStatistics.maxMinEdges.get(globalStatisticsKey).getMean())
			                    );
		}
		tester.output.printf("%n%n%n");
		tester.output.close();
	}

	/**
	 * @return current time in {@link #dateFormatter} format
	 */
	private static String getNow() {
		return dateFormatter.format(new Date());
	}

	/**
	 * @param value value in nanoseconds
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

	/**
	 * Determine min dispatchable.
	 */
	@Option(name = "--minMorris", usage = "Execute the min dispatchable ESTNU after Morris2014.")
	private boolean minDispatchableMorris;

	/**
	 * Determine min dispatchable.
	 */
	@Option(name = "--minFD", usage = "Execute the min dispatchable ESTNU after FD_STNU check.")
	private boolean minDispatchableFD;

	/**
	 *
	 */
	@Option(name = "--save", usage = "Save all checked instances.")
	private boolean save;

	/**
	 * Parameter for asking timeout in sec.
	 */
	@Option(name = "--timeOut", usage = "Time in seconds.")
	private int timeOut = 1800; // 20 min

	/**
	 * Software Version.
	 */
	@Option(name = "-v", aliases = "--version", usage = "Version")
	private boolean versionReq;

	/**
	 * Checks stnu using stnuCheckAlgorithm and update it.
	 * Then, it adds statistics to gExecTimeInSec and gNetworkEdges.
	 * It returns the string representing the ({@link #OUTPUT_ROW_ALG_STATS}) part to add to the row in the statistics file.
	 */
	private String dcCheckTester(
		@Nonnull STNU stnu,
		@Nonnull STNU.CheckAlgorithm stnuCheckAlgorithm,
		@Nonnull String rowToWrite,
		@Nonnull SummaryStatistics gExecTimeInSec,
		@Nonnull SummaryStatistics gNetworkEdges) {

		String msg;
		boolean checkInterrupted = false;
		final TNGraph<STNUEdge> graphToCheck;
		TNGraph<STNUEdge> g;
		graphToCheck = stnu.getG();
		final String fileName = graphToCheck.getFileName().getName();
		STNU.STNUCheckStatus status = stnu.getCheckStatus();

		final SummaryStatistics localSummaryStat = new SummaryStatistics();
		String error = "";
		for (int j = 0; j < nDCRepetition && !checkInterrupted && !stnu.getCheckStatus().timeout; j++) {
			LOG.info("Test " + (j + 1) + "/" + nDCRepetition + " for STNU " + fileName);

			// It is necessary to reset the graph!
			g = new TNGraph<>(graphToCheck, currentEdgeImplClass);
			stnu.setG(g);
			status = stnu.getCheckStatus();
			try {
				stnu.dynamicControllabilityCheck(stnuCheckAlgorithm);
			} catch (CancellationException ex) {
				msg = getNow() + ": Cancellation has occurred. " + graphToCheck.getFileName() + " STNU is ignored.";
				System.out.println(msg);
				LOG.severe(msg);
				checkInterrupted = true;
				status.consistency = false;
				continue;
			} catch (Exception e) {
				msg = getNow() + ": exception during DC check on " + graphToCheck.getFileName()
				      + ". STNU is ignored.\nError details: " + e;
				System.out.println(msg);
				LOG.severe(msg);
				checkInterrupted = true;
				status.consistency = false;
				error = e.getMessage();
				continue;
			}
			localSummaryStat.addValue(status.executionTimeNS);
		} // end for checking repetition for a single file

		if (status.timeout || checkInterrupted) {
			if (status.timeout) {
				msg = "\n\n" + getNow() + ": Timeout or interrupt occurred. " + graphToCheck.getFileName()
				      + " STNU is ignored.\n";
				System.out.println(msg);
			}
			status.finished = false;
		} else {
			status.executionTimeNS = (long) localSummaryStat.getMean();
			status.stdDevExecutionTimeNS = (long) localSummaryStat.getStandardDeviation();
		}
		final int nEdgesAfterChecking = stnu.getG().getEdgeCount();
		//NOW DETERMINE STATISTICS
		if (!status.finished) {
			// time out or generic error
			rowToWrite += String.format(OUTPUT_ROW_ALG_STATS,
			                            Double.valueOf(nanoSeconds2Seconds(status.executionTimeNS)),
			                            Double.valueOf(nanoSeconds2Seconds(Double.NaN)),
//Double.NaN because in case of Timeout, only one time is considered.
                                        Integer.valueOf(nEdgesAfterChecking),
                                        ((status.executionTimeNS != Constants.INT_NULL) ? "Timeout of " + timeOut +
                                                                                          " seconds."
                                                                                        : error));
			gExecTimeInSec.addValue(timeOut);
			return rowToWrite;
		}
		final double localAvgInSec = nanoSeconds2Seconds(status.executionTimeNS);
		final double localStdDevInSec = nanoSeconds2Seconds(status.stdDevExecutionTimeNS);
		gExecTimeInSec.addValue(localAvgInSec);
		gNetworkEdges.addValue(nEdgesAfterChecking);
		LOG.info(fileName + " has been checked (algorithm ends in a stable state): " + status.finished);
		LOG.info(fileName + " is DC: " + status.consistency);
		LOG.info(fileName + " average checking time [s]: " + localAvgInSec);
		LOG.info(fileName + " std. deviation [s]: " + localStdDevInSec);
		rowToWrite += String.format(OUTPUT_ROW_ALG_STATS,
		                            Double.valueOf(localAvgInSec),
		                            Double.valueOf((nDCRepetition > 1) ? localStdDevInSec : Double.NaN),
		                            Integer.valueOf(nEdgesAfterChecking),
		                            (status.consistency) ? "TRUE" : "FALSE"
		                           );
		if (status.consistency && this.save) {
			//save the result
			final String outFileName = fileName.substring(0, fileName.length() - 4) + stnuCheckAlgorithm + "-checked.stnu";
			stnu.setfOutput(new File(outFileName));
			stnu.saveGraphToFile();
		}
		return rowToWrite;
	}

	/**
	 * Returns an STNU object filled with the given graph and timeOut set as the given parameter {@link #timeOut}.
	 *
	 * @param g input graph
	 * @return an stnu instance
	 */
	private STNU makeSTNUInstance(TNGraph<STNUEdge> g) {
		return new STNU(g, timeOut);
	}

	/**
	 * Simple method to manage command line parameters using {@code args4j} library.
	 *
	 * @param args input arguments
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
				"java -cp CSTNU-<version>.jar -cp it.univr.di.cstnu.DispatchabilityBenchmarkRunner [options...] arguments...");
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
	 * Determines the minDispatchESTNU of stnu without modifying it.
	 * Then, it adds statistics to gExecTimeInSec and gNetworkEdges.
	 * It returns the string representing the ({@link #OUTPUT_ROW_ALG_STATS}) part to add to the row in the statistics file.
	 */
	@SuppressWarnings("UnnecessaryCallToStringValueOf")
	private String minDispatcherTester(
		@Nonnull STNU inputSTNU,
		@Nonnull String rowToWrite,
		@Nonnull SummaryStatistics gExecTimeInSec,
		@Nonnull SummaryStatistics gNetworkEdges,
		@Nullable SummaryStatistics gMaxMinEdges,
		@Nonnull TNGraph<STNUEdge> minimizedGraph) {

		String msg;
		boolean checkInterrupted = false;
		STNU stnuMinimized = new STNU(inputSTNU);
		final String fileName = inputSTNU.getG().getFileName().getName();

		final SummaryStatistics localSummaryStat = new SummaryStatistics();
		String error = "";
		for (int j = 0; j < nDCRepetition && !checkInterrupted && !stnuMinimized.getCheckStatus().timeout; j++) {
			LOG.info("Test " + (j + 1) + "/" + nDCRepetition + " for STNU " + fileName);
			// It is necessary to reset the graph!
			if (j > 0) {
				stnuMinimized = new STNU(inputSTNU);
			}
			final boolean minimized;
			try {
				minimized = stnuMinimized.applyMinDispatchableESTNU();
			} catch (CancellationException ex) {
				msg = getNow() + ": Cancellation has occurred. " + fileName + " STNU is ignored.";
				System.out.println(msg);
				LOG.severe(msg);
				checkInterrupted = true;
				continue;
			} catch (Exception e) {
				msg =
					getNow() + ": exception during minDispatch on " + fileName + ". STNU is ignored.\nError details: " +
					e;
				System.out.println(msg);
				LOG.severe(msg);
				checkInterrupted = true;
				error = e.getMessage();
				continue;
			}
			if (!minimized) {
				checkInterrupted = true;
			}
			localSummaryStat.addValue(stnuMinimized.getCheckStatus().executionTimeNS);
		} // end for checking repetition for a single file

		if (stnuMinimized.getCheckStatus().timeout || checkInterrupted) {
			msg = "\n\n" + getNow() + ": Timeout or interrupt occurred. " + fileName + " STNU is ignored.\n";
			System.out.println(msg);
		} else {
			msg = getNow() + ": done! ";
			LOG.info(msg);
			stnuMinimized.getCheckStatus().executionTimeNS = (long) localSummaryStat.getMean();
			stnuMinimized.getCheckStatus().stdDevExecutionTimeNS = (long) localSummaryStat.getStandardDeviation();
		}

		//NOW DETERMINE STATISTICS
		final int nEdgesAfterMinimization = stnuMinimized.getG().getEdgeCount();
		if (!stnuMinimized.getCheckStatus().finished || checkInterrupted) {
			// time out or generic error
			rowToWrite += String.format(OUTPUT_ROW_ALG_STATS,
			                            Double.valueOf(
				                            nanoSeconds2Seconds(stnuMinimized.getCheckStatus().executionTimeNS)),
			                            Double.valueOf(nanoSeconds2Seconds(Double.NaN)),
//Double.NaN because in case of Timeout, only one time is considered.
                                        Integer.valueOf(nEdgesAfterMinimization),
                                        ((stnuMinimized.getCheckStatus().executionTimeNS != Constants.INT_NULL) ?
                                         "Timeout of " + timeOut + " seconds."
                                                                                                                : error));
			gExecTimeInSec.addValue(timeOut);
			return rowToWrite;
		}
		final double localAvgInSec = nanoSeconds2Seconds(stnuMinimized.getCheckStatus().executionTimeNS);
		final double localStdDevInSec = nanoSeconds2Seconds(stnuMinimized.getCheckStatus().stdDevExecutionTimeNS);
		gExecTimeInSec.addValue(localAvgInSec);
		gNetworkEdges.addValue(nEdgesAfterMinimization);
		LOG.info(fileName + " has been minimized.");
		LOG.info(fileName + " average execution time [s]: " + localAvgInSec);
		LOG.info(fileName + " std. deviation [s]: " + localStdDevInSec);
		rowToWrite += String.format(OUTPUT_ROW_ALG_STATS,
		                            Double.valueOf(localAvgInSec),
		                            Double.valueOf((nDCRepetition > 1) ? localStdDevInSec : Double.NaN),
		                            Integer.valueOf(nEdgesAfterMinimization),
		                            String.valueOf(stnuMinimized.getCheckStatus().getMaxMinConstraint())
//here it is always minimized
		                           );
		minimizedGraph.takeFrom(stnuMinimized.getGChecked());
		if (gMaxMinEdges != null) {
			gMaxMinEdges.addValue(stnuMinimized.getCheckStatus().getMaxMinConstraint());
		}
		if (this.save) {
			//save the result
			final String outFileName = fileName.substring(0, fileName.length() - 4) + "-minimized.stnu";
			stnuMinimized.setfOutput(new File(outFileName));
			stnuMinimized.saveGraphToFile();
		}
		return rowToWrite;
	}

	/**
	 * Loads the file and execute all the actions (specified as instance parameter) on the network represented by the
	 * file.
	 *
	 * @param file             input file
	 * @param runState         current state
	 * @param globalStatistics global statistics
	 * @return true if required task ends successfully, false otherwise.
	 */
	private boolean worker(
		@Nonnull File file,
		@Nonnull RunMeter runState,
		@Nonnull GlobalStatistics globalStatistics) {

		if (LOG.isLoggable(Level.FINER)) {
			LOG.finer("Loading " + file.getName() + "...");
		}
		final TNGraphMLReader<STNUEdge> graphMLReader = new TNGraphMLReader<>();
		final TNGraph<STNUEdge> graphToCheck;
		try {
			graphToCheck = graphMLReader.readGraph(file, currentEdgeImplClass);
		} catch (IOException | ParserConfigurationException | SAXException e2) {
			final String msg =
				"File " + file.getName() + " cannot be parsed. Details: " + e2.getMessage() + ".\nIgnored.";
			LOG.warning(msg);
			System.out.println(msg);
			return false;
		}
		LOG.finer("...done!");
		//NODES and EDGES in the original graph. DO NOT MOVE such variable.
		final int nNodes = graphToCheck.getVertexCount();
		final int nEdges = graphToCheck.getEdgeCount();

		final STNU stnu;
		stnu = makeSTNUInstance(graphToCheck);
		try {
			stnu.initAndCheck();
		} catch (Exception e) {
			final String msg = getNow() + ": " + file.getName() + " is not a not well-defined instance. Details:"
			                   + e.getMessage() + "\nIgnored.";
			System.out.println(msg);
			LOG.severe(msg);
			return false;
		}
		final STNU stnuCopy = new STNU(stnu);//for FD!
		//Only now contingent node number is significative
		final int nContingents = graphToCheck.getContingentNodeCount();

		final GlobalStatisticsKey globalStatisticsKey = new GlobalStatisticsKey(nNodes, nContingents);

		SummaryStatistics gNetworkEdges = globalStatistics.networkEdges.get(globalStatisticsKey);
		if (gNetworkEdges == null) {
			gNetworkEdges = new SummaryStatistics();
			globalStatistics.networkEdges.put(globalStatisticsKey, gNetworkEdges);
		}
		gNetworkEdges.addValue(nEdges);

		SummaryStatistics gMorris2014ExecTimeInSec = globalStatistics.morris2014ExecTimeInSec.get(globalStatisticsKey);
		if (gMorris2014ExecTimeInSec == null) {
			gMorris2014ExecTimeInSec = new SummaryStatistics();
			globalStatistics.morris2014ExecTimeInSec.put(globalStatisticsKey, gMorris2014ExecTimeInSec);
		}

		SummaryStatistics gMorris2014NetworkEdges = globalStatistics.morris2014NetworkEdges.get(globalStatisticsKey);
		if (gMorris2014NetworkEdges == null) {
			gMorris2014NetworkEdges = new SummaryStatistics();
			globalStatistics.morris2014NetworkEdges.put(globalStatisticsKey, gMorris2014NetworkEdges);
		}

		SummaryStatistics gMinDispOfMorris2014ExecTimeInSec =
			globalStatistics.minDispOfMorris2014ExecTimeInSec.get(globalStatisticsKey);
		if (gMinDispOfMorris2014ExecTimeInSec == null) {
			gMinDispOfMorris2014ExecTimeInSec = new SummaryStatistics();
			globalStatistics.minDispOfMorris2014ExecTimeInSec.put(globalStatisticsKey,
			                                                      gMinDispOfMorris2014ExecTimeInSec);
		}

		SummaryStatistics gMinDispOfMorris2014NetworkEdges =
			globalStatistics.minDispOfMorris2014NetworkEdges.get(globalStatisticsKey);
		if (gMinDispOfMorris2014NetworkEdges == null) {
			gMinDispOfMorris2014NetworkEdges = new SummaryStatistics();
			globalStatistics.minDispOfMorris2014NetworkEdges.put(globalStatisticsKey, gMinDispOfMorris2014NetworkEdges);
		}

		SummaryStatistics gFD_STNUExecTimeInSec = globalStatistics.FD_STNUExecTimeInSec.get(globalStatisticsKey);
		if (gFD_STNUExecTimeInSec == null) {
			gFD_STNUExecTimeInSec = new SummaryStatistics();
			globalStatistics.FD_STNUExecTimeInSec.put(globalStatisticsKey, gFD_STNUExecTimeInSec);
		}

		SummaryStatistics gFD_STNUNetworkEdges = globalStatistics.FD_STNUNetworkEdges.get(globalStatisticsKey);
		if (gFD_STNUNetworkEdges == null) {
			gFD_STNUNetworkEdges = new SummaryStatistics();
			globalStatistics.FD_STNUNetworkEdges.put(globalStatisticsKey, gFD_STNUNetworkEdges);
		}

		SummaryStatistics gMinDispOfFD_STNUExecTimeInSec =
			globalStatistics.minDispOfFD_STNUExecTimeInSec.get(globalStatisticsKey);
		if (gMinDispOfFD_STNUExecTimeInSec == null) {
			gMinDispOfFD_STNUExecTimeInSec = new SummaryStatistics();
			globalStatistics.minDispOfFD_STNUExecTimeInSec.put(globalStatisticsKey, gMinDispOfFD_STNUExecTimeInSec);
		}

		SummaryStatistics gMinDispOfFD_STNUNetworkEdges =
			globalStatistics.minDispOfFD_STNUNetworkEdges.get(globalStatisticsKey);
		if (gMinDispOfFD_STNUNetworkEdges == null) {
			gMinDispOfFD_STNUNetworkEdges = new SummaryStatistics();
			globalStatistics.minDispOfFD_STNUNetworkEdges.put(globalStatisticsKey, gMinDispOfFD_STNUNetworkEdges);
		}

		SummaryStatistics gMaxMinEdges = globalStatistics.maxMinEdges.get(globalStatisticsKey);
		if (gMaxMinEdges == null) {
			gMaxMinEdges = new SummaryStatistics();
			globalStatistics.maxMinEdges.put(globalStatisticsKey, gMaxMinEdges);
		}

		String rowToWrite =
			String.format(OUTPUT_ROW_GRAPH, file.getName(), Integer.valueOf(nNodes), Integer.valueOf(nContingents),
			              Integer.valueOf(nEdges));

		if (this.morris2014) {
			LOG.info(getNow() + ": Morris2014Dispatchable: start.");
			rowToWrite = dcCheckTester(stnu,
			                           STNU.CheckAlgorithm.Morris2014Dispatchable,
			                           rowToWrite,
			                           gMorris2014ExecTimeInSec,
			                           gMorris2014NetworkEdges
			                          );
		} else {
			rowToWrite += String.format(OUTPUT_ROW_ALG_STATS,
			                            Double.NaN,
			                            Double.NaN,
			                            0,
			                            "NOT EXECUTED"
			                           );
		}

		final TNGraph<STNUEdge> morrisMinimized = new TNGraph<>("morrisMinimized", currentEdgeImplClass);
		if (this.minDispatchableMorris) {
			LOG.info(getNow() + ": MinDispatchable of Morris2014Dispatchable: start.");
			rowToWrite = minDispatcherTester(stnu,
			                                 rowToWrite,
			                                 gMinDispOfMorris2014ExecTimeInSec,
			                                 gMinDispOfMorris2014NetworkEdges,
			                                 null,
			                                 morrisMinimized
			                                );
		} else {
			rowToWrite += String.format(OUTPUT_ROW_ALG_STATS,
			                            Double.NaN,
			                            Double.NaN,
			                            0,
			                            "NOT EXECUTED"
			                           );
		}


		//use the second stnu to check with FD
		if (this.fd_stnu) {
			LOG.info(getNow() + ": FD_STNU: start.");
			rowToWrite = dcCheckTester(stnuCopy,
			                           STNU.CheckAlgorithm.FD_STNU,
			                           rowToWrite,
			                           gFD_STNUExecTimeInSec,
			                           gFD_STNUNetworkEdges
			                          );
		} else {
			rowToWrite += String.format(OUTPUT_ROW_ALG_STATS,
			                            Double.NaN,
			                            Double.NaN,
			                            0,
			                            "NOT EXECUTED"
			                           );
		}


		final TNGraph<STNUEdge> ftMinimized = new TNGraph<>("fdMinimized", currentEdgeImplClass);
		if (this.minDispatchableFD) {
			LOG.info(getNow() + ": MinDispatchable of FD_STNU: start.");
			rowToWrite = minDispatcherTester(stnuCopy,
			                                 rowToWrite,
			                                 gMinDispOfFD_STNUExecTimeInSec,
			                                 gMinDispOfFD_STNUNetworkEdges,
			                                 gMaxMinEdges,
			                                 ftMinimized
			                                );
		} else {
			rowToWrite += String.format(OUTPUT_ROW_ALG_STATS,
			                            Double.NaN,
			                            Double.NaN,
			                            0,
			                            "NOT EXECUTED"
			                           );
		}

		if (this.minDispatchableMorris && this.minDispatchableFD) {
			final ObjectList<ObjectObjectImmutablePair<STNUEdge, STNUEdge>> differentEdges =
				morrisMinimized.differentEdgesOf(ftMinimized);
			if (differentEdges.size() > 0) {
				System.err.println("Different edges are: " + differentEdges.size() + ":\n" + differentEdges);
			}
		}
		output.println(rowToWrite);
		output.flush();
		runState.printProgress();
		return true;
	}
}
