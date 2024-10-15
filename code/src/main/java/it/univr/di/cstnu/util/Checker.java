// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.util;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import it.univr.di.cstnu.algorithms.AbstractCSTN.CSTNCheckStatus;
import it.univr.di.cstnu.algorithms.AbstractCSTN.DCSemantics;
import it.univr.di.cstnu.algorithms.*;
import it.univr.di.cstnu.algorithms.CSTNU.CSTNUCheckStatus;
import it.univr.di.cstnu.algorithms.STN.STNCheckStatus;
import it.univr.di.cstnu.algorithms.STNU.CheckAlgorithm;
import it.univr.di.cstnu.algorithms.STNU.STNUCheckStatus;
import it.univr.di.cstnu.graph.*;
import it.univr.di.cstnu.graph.TNGraph.NetworkType;
import it.univr.di.labeledvalue.Constants;
import it.univr.di.labeledvalue.Label;
import net.openhft.affinity.AffinityStrategies;
import net.openhft.affinity.AffinityThreadFactory;
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
import java.util.Locale;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple class to determine the average execution time (and std dev) of the (C)STN(U) DC checking algorithm on a given
 * set of (C)STN(U)s.
 *
 * @author posenato
 * @version $Rev: 732 $
 */
@SuppressWarnings({"NonAsciiCharacters", "AutoBoxing"})
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "STCAL", justification = "It is not relevant here!")
public class Checker {

	/**
	 * CSV separator
	 */
	static final String CSVSep = ";\t";

	/**
	 * Utility internal class to store #contingent, #nodes, and #propositions and allows the ordering among objects of
	 * this class.
	 *
	 * @author posenato
	 */
	public static class GlobalStatisticKey implements Comparable<GlobalStatisticKey> {
		/**
		 * # contingents
		 */
		protected final int contingents;
		/**
		 * # of nodes
		 */
		protected final int nodes;
		/**
		 * # of propositions
		 */
		protected final int propositions;

		/**
		 * default constructor
		 *
		 * @param inputNodes        #nodes
		 * @param inputPropositions #prop
		 * @param inputContingents  # cont
		 */
		public GlobalStatisticKey(final int inputNodes, final int inputPropositions, final int inputContingents) {
			nodes = inputNodes;
			propositions = inputPropositions;
			contingents = inputContingents;
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (!(o instanceof GlobalStatisticKey o1)) {
				return false;
			}
			return o1.compareTo(this) == 0;
		}

		/**
		 * The order is respect to #nodes,#contingents, and #propositions.
		 *
		 * @param o the object to be compared.
		 * @return negative if this has, in order, fewer nodes or fewer contingents or fewer proposition than the
		 * parameter 'o'; a positive value in the opposite case, 0 when all three values are equals to the
		 * corresponding values in 'o'.
		 */
		@SuppressWarnings("SubtractionInCompareTo")
		@Override
		public int compareTo(@Nonnull GlobalStatisticKey o) {
			final long d = (long) nodes - o.nodes;
			if (d == 0) {
				final int d1 = contingents - o.contingents;
				if (d1 == 0) {
					return propositions - o.propositions;
				}
				return d1;
			}
			return (int) d;
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

		/**
		 * @return # prop
		 */
		public int getPropositions() {
			return propositions;
		}

		@Override
		public int hashCode() {
			return (contingents + propositions * 31) * 31 + nodes;
		}
	}

	/**
	 *
	 */
	static final String GLOBAL_HEADER_ROW =
		"%d" + CSVSep +
		"%d" + CSVSep +
		"%d" + CSVSep +
		"%d" + CSVSep +
		"%E" + CSVSep +
		"%E" + CSVSep +
		"%E" + CSVSep +
		"%E" + CSVSep +
		"%E" + CSVSep +
		"%E" + CSVSep +
		"%E" + CSVSep +
		"%E" + CSVSep +
		"%E" + CSVSep +
		"%E" + CSVSep +
		"%E" + CSVSep +
		"%E" + CSVSep +
		"%n";
	/**
	 * class logger
	 */
	static final Logger LOG = Logger.getLogger(Checker.class.getName());
	/**
	 * Output file header
	 */
	static final String OUTPUT_HEADER =
		"fileName" + CSVSep +
		"#nodes" + CSVSep +
		"#edges" + CSVSep +
		"#propositions" + CSVSep +
		"#ctg" + CSVSep +
		"#minEdgeValue" + CSVSep +
		"#maxEdgeValue" + CSVSep +
		"avgTime[s]" + CSVSep +
		"std.dev.[s]" + CSVSep +
		"#ruleExecuted/cycles" + CSVSep +
		"DC";
	/**
	 * Header
	 */
	static final String OUTPUT_HEADER_STNU =
		OUTPUT_HEADER + CSVSep +
		"rateAddedEdges" + CSVSep +
		"rateWaitAndOrdinaryEdge" + CSVSep +
		"negativeEdgeFromContingent" + CSVSep;
	/**
	 * CSTN header
	 */
	static final String OUTPUT_HEADER_CSTN =
		OUTPUT_HEADER + CSVSep +
		"#R0" + CSVSep +
		"#R3" + CSVSep +
		"#LP" + CSVSep +
		"#PotentialUpdate" + CSVSep;
	/**
	 * CSTNU header
	 */
	static final String OUTPUT_HEADER_CSTNU = OUTPUT_HEADER_CSTN + "#LUC+FLUC+LCUC"// upperCaseRuleCalls
	                                          + CSVSep + "#LLC"// lowerCaseRuleCalls
	                                          + CSVSep + "#LCC"// crossCaseRuleCalls
	                                          + CSVSep + "#LLR"// letterRemovalRuleCalls
	                                          + CSVSep;
	/**
	 * CSTNPSU header
	 */
	static final String OUTPUT_HEADER_CSTNPSU = OUTPUT_HEADER_CSTN +
	                                            "PrototypalTime[s]" + CSVSep +
	                                            "PrototypalTimeStdDev[s]" + CSVSep +
	                                            "Prototypal" + CSVSep;
	/**
	 *
	 */
	static final String OUTPUT_ROW_GRAPH =
		"%s" + CSVSep +
		"%d" + CSVSep +
		"%d" + CSVSep +
		"%d" + CSVSep +
		"%d" + CSVSep +
		"%d" + CSVSep +
		"%d" + CSVSep;
	/**
	 *
	 */
	static final String OUTPUT_ROW_TIME = "%E"// average time
	                                      + CSVSep + "%E"// std dev
	                                      + CSVSep + "%d"// #ruleExecuted or cycles
	                                      + CSVSep + "%s"// true of false for DC
	                                      + CSVSep;
	/**
	 *
	 */
	static final String OUTPUT_ROW_TIME_CSTN = "%d"// r0
	                                           + CSVSep + "%d"// r3
	                                           + CSVSep + "%d"// LNC
	                                           + CSVSep + "%d"// PotentialUpdate
	                                           + CSVSep;
	/**
	 * For CSTNPSU statistics
	 */
	static final String OUTPUT_ROW_TIME_STATS_CSTNPSU =
		"%E"// PrototypalTime[s]"
		+ CSVSep + "%s" //std dev of prototypal time
		+ CSVSep + "%s"// Prototypal
		+ CSVSep;
	/**
	 *
	 */
	static final String OUTPUT_ROW_TIME_STATS_CSTNU = "%d"// upperCaseRuleCalls
	                                                  + CSVSep + "%d"// lowerCaseRuleCalls
	                                                  + CSVSep + "%d"// crossCaseRuleCalls
	                                                  + CSVSep + "%d"// letterRemovalRuleCalls
	                                                  + CSVSep;
	/**
	 *
	 */
	static final String OUTPUT_ROW_TIME_STNU = "%E"// added edges
	                                           + CSVSep + "%E" // waitAndOrdEdgeRate
	                                           + CSVSep + "%E" // negativeOutgoingFromC
	                                           + CSVSep;
	/**
	 * Version
	 */
	// static final String VERSIONandDATE = "1.0, March, 22 2015";
	// static final String VERSIONandDATE = "1.1, November, 18 2015";
	// static final String VERSIONandDATE = "1.2, October, 10 2017";
	// static final String VERSIONandDATE = "1.3, October, 16 2017";// executor code cleaned
	// static final String VERSIONandDATE = "1.4, November, 09 2017";// code cleaned
	// static final String VERSIONandDATE = "2.0, November, 13 2017";// Multi-thread version.
	// static final String VERSIONandDATE = "2.1, November, 14 2017";// Multi-thread version. Fixed a slip!
	// static final String VERSIONandDATE = "2.2, November, 15 2017";// Added the possibility to test
	// CSTNEpsilonWONodeLabels and CSTN2CSTN0
	// static final String VERSIONandDATE = "2.23, November, 30 2017";// Improved the print of statistics file: std dev is print only when # checks > 1
	// static final String VERSIONandDATE = "2.24, December, 04 2017";// Added CSTNEpsilon3R
	// static final String VERSIONandDATE = "2.25, January, 17 2018";// Improved print of statistics.
	// static final String VERSIONandDATE = "2.26, December, 18 2018";// Improved print of statistics adding the total number of applied rules
	// static final String VERSIONandDATE = "2.27, June, 9 2019";// Refactoring Edge
	// static final String VERSIONandDATE = "2.5, November, 09 2019";// Removed all potential counters
	// static final String VERSIONandDATE = "3, June, 29 2020";// Add check for STN and STNU
	// static final String VERSIONandDATE = "3.1, July, 28 2020";// Refined stats for STNU
	// static final String VERSIONandDATE = "3.2, January, 13 2021";// Fixed file encoding
	static final String VERSIONandDATE = "3.5, January, 13 2022";
	/**
	 * Date formatter
	 */
	private final static SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
	/**
	 *
	 */
	EdgeSupplier<Edge> currentEdgeFactory;
	// added STNU statistics about waitAndOrdinaryEdge and negativeEdgeFromContingent
	/**
	 * Class for representing edge .
	 */
	Class<? extends Edge> currentEdgeImplClass;
	/**
	 * Parameter for asking DC check epsilon DC reducing to IR DC
	 */
	@Option(name = "--cstn2cstn0",
		usage = "Check the epsilon-DC of the input instance using IR-DC on a corresponding streamlined CSTN. It transforms the input CSTN and then check its IR-DC. Epsilon value can be specified using -reactionTime parameter.")
	private boolean cstn2cstn0;
	/**
	 * Which type of CSTN are input files
	 */
	@Option(required = true, name = "--type",
		usage = "Specify if input files contain STN, STNU, CSTN, CSTNU or CSTNPSU instances (use one of such "
		        + "keywords: STN STNU CSTN CSTNU CSTNPSU).")
	private TNGraph.NetworkType networkType = NetworkType.CSTN;
	/**
	 * Parameter for asking DC check epsilon DC reducing to IR DC
	 */
	@Option(name = "--cstnu2cstn",
		usage = "Check the CSTNU DC of the input instance reducing the check to the equivalent streamlined CSTN instance and checking this last one using IR-DC. It is incompatible with -potential option.")
	private boolean cstnu2cstn;
	/**
	 * Parameter for asking how much to cut all edge values (for studying pseudo-polynomial characteristics)
	 */
	@Option(name = "--cuttingEdgeFactor",
		usage = "Cutting factor for reducing edge values. Default value is 1, i.e., no cut.")
	private int cuttingEdgeFactor = 1;
	/**
	 * Parameter for asking DC semantics.
	 */
	@Option(name = "--semantics", usage = "DC semantics. Possible values are: IR, ε, Std. Default is Std.")
	private DCSemantics dcSemantics = DCSemantics.Std;
	/**
	 * The input file names. Each file has to contain a CSTN graph in GraphML format.
	 */
	@Argument(required = true, usage = "Input files. Each input file has to be a CSTN graph in GraphML format.",
		metaVar = "CSTN_file_names", handler = StringArrayOptionHandler.class)
	private String[] inputFiles;
	/**
	 *
	 */
	private List<File> instances;
	/**
	 * Roberto: I verified that with such kind of computation, using more than one thread to check more files in
	 * parallel reduces the single performance!!!
	 */
	@Option(name = "--nCPUs",
		usage = "Number of virtual CPUs that are reserved for this execution. Default is 0=no CPU reserved, there is only one thread for all the DC checking executions: such thread can be allocated to a core, then deallocated and reallocated to another core. With nCPUs=1, there is only thread but such thread is allocated to a core till its end. With more thread, the global performance increases, but each file can require more time because there is a competition among threads to access to the memory.")
	private int nCPUs;
	/**
	 * Parameter for asking how many times to check the DC for each CSTN.
	 */
	@Option(name = "--numRepetitionDCCheck", usage = "Number of time to re-execute DC checking")
	private int nDCRepetition = 30;
	/**
	 * Parameter for avoiding DC check
	 */
	@Option(name = "--noDCCheck", usage = "Do not execute DC check. Just determine graph characteristics.")
	private boolean noDCCheck;
	/**
	 * Parameter for asking whether to consider only Lp,qR0, qR3* rules.
	 */
	@Option(name = "--onlyLPQR0QR3", aliases = "--limitToZ", depends = "--semantics", usage = "Check DC considering only rules LP, qR0 and QR3.")
	private boolean onlyLPQR0QR3OrToZ;
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
	 * Check a CSTN instance using BellmanFord algorithm
	 */
	@Option(name = "--potential", depends = "--type cstn", usage = "Check a CSTN instance using BellmanFord algorithm.")
	private boolean potential;
	/**
	 * Parameter for asking reaction time.
	 */
	@Option(name = "--reactionTime", depends = "--semantics ε", usage = "Reaction time. It must be > 0.")
	private int reactionTime = 1;
	/**
	 * Parameter for asking to remove a value from all constraints.
	 */
	@Option(name = "--removeValue",
		usage = "Value to be removed from any edge. Default value is a value representing NULL.")
	private int removeValue = Constants.INT_NULL;
	/**
	 *
	 */
	@Option(name = "--save", usage = "Save all checked instances.")
	private boolean save;
	/**
	 * Parameter for asking timeout in sec.
	 */
	@Option(name = "--timeOut", usage = "Time in seconds.")
	private int timeOut = 1200; // 20 min
	/**
	 * Software Version.
	 */
	@Option(name = "-v", aliases = "--version", usage = "Version")
	private boolean versionReq;
	/**
	 * Parameter for asking whether to consider node labels during the DC check.
	 */
	@Option(name = "--woNodeLabels",
		usage = "Check DC transforming the network in an equivalent CSTN without node labels.")
	private boolean woNodeLabels;
	/**
	 * Parameter for asking which algorithm to use for checking STNU.
	 */
	@Option(name = "--stnuCheck",
		usage = "Which algorithm to use for checking STNU network. (use one of such keywords: Morris2014 RUL2018 RUL2021 FastSTNUdispatch)")
	private STNU.CheckAlgorithm stnuCheckAlgorithm = CheckAlgorithm.RUL2021;
	/**
	 * Global header
	 */
	static final String GLOBAL_HEADER =
		"%n%nGlobal statistics%n" +
		"#Networks" + CSVSep +
		"#nodes" + CSVSep +
		"#contingent" + CSVSep +
		"#propositions" + CSVSep +
		"avgExeTime[s]" + CSVSep +
		"std.dev.[s]" + CSVSep +
		"avgRules/Cycles" + CSVSep +
		"std.dev." + CSVSep +
		"avgAddedEdgesRate" + CSVSep +
		"std.dev." + CSVSep +
		"avgWaitAndOrdinaryEdgesRate" + CSVSep +
		"std.dev." + CSVSep +
		"avgNegativeFromCtgEdgesRate" + CSVSep +
		"std.dev." + CSVSep +
		"avgPartialExeTime[s]" + CSVSep +
		"stdDevPartialExeTime[s]" + CSVSep +
		"%n";

	/**
	 * @return current time in {@link #dateFormatter} format
	 */
	private static String getNow() {
		return dateFormatter.format(new Date());
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
			System.err.println("java -cp CSTNU-<version>.jar -cp it.univr.di.cstnu.Checker [options...] arguments...");
			// print the list of available options
			parser.printUsage(System.err);
			System.err.println();

			// print option sample. This is useful some time
			// System.err.println("Example: java -jar Checker.jar" + parser.printExample(OptionHandlerFilter.REQUIRED) +
			// " <CSTN_file_name0> <CSTN_file_name1>...");
			return false;
		}
		if (versionReq) {
			System.out.print(
				getClass().getName() + " " + VERSIONandDATE + ". Academic and non-commercial use only.\n"
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
				output =
					new PrintStream(new FileOutputStream(outputFile, true), true, StandardCharsets.UTF_8);
			} catch (IOException e) {
				System.err.println("Output file cannot be created: " + e.getMessage());
				parser.printUsage(System.err);
				System.err.println();
				return false;
			}
		} else {
			output = System.out;
		}

		if (reactionTime <= 0) {
			System.err.println("Reaction time must be > 0");
			return false;
		}

		final String suffix;
		//if you add a new type, update also getHeader().
		if (networkType == NetworkType.CSTN) {
			currentEdgeImplClass = CSTNEdgePluggable.class;
			suffix = "cstn";
		} else {
			if (networkType == NetworkType.CSTNU) {
				currentEdgeImplClass = CSTNUEdgePluggable.class;
				suffix = "cstnu";
			} else {
				if (networkType == NetworkType.CSTNPSU) {
					currentEdgeImplClass = CSTNPSUEdgePluggable.class;
					suffix = "cstnpsu";
				} else {
					if (networkType == NetworkType.STNU) {
						currentEdgeImplClass = STNUEdgeInt.class;
						suffix = "stnu";
					} else {
						if (networkType == NetworkType.STN) {
							currentEdgeImplClass = STNEdgeInt.class;
							suffix = "stn";
						} else {
							final String msg =
								"Type of network not managed by current version of this class. Game over :-/";
							throw new IllegalArgumentException(msg);
						}
					}
				}
			}
		}
		currentEdgeFactory = new EdgeSupplier<>(currentEdgeImplClass);

		// LOG.finest("File number: " + this.fileNameInput.length);
		// LOG.finest("File names: " + Arrays.deepToString(this.fileNameInput));
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

	private String getHeader() {
		if (networkType == NetworkType.CSTN) {
			return (OUTPUT_HEADER_CSTN + CSVSep);
		}
		if (networkType == NetworkType.CSTNU) {
			return (OUTPUT_HEADER_CSTNU + CSVSep);
		}
		if (networkType == NetworkType.CSTNPSU) {
			return (OUTPUT_HEADER_CSTNPSU + CSVSep);
		}
		if (networkType == NetworkType.STNU) {
			return (OUTPUT_HEADER_STNU + CSVSep);
		}
		if (networkType == NetworkType.STN) {
			return (OUTPUT_HEADER + CSVSep);
		}
		return "";
	}

	/**
	 * Loads the file and execute all the actions (specified as instance parameter) on the network represented by the
	 * file.
	 *
	 * @param <E1>                                   type of edge
	 * @param file                                   input file
	 * @param runState                               current state
	 * @param globalExecutionTimeStatisticsInSecMap  global statistics
	 * @param globalRuleExecutionStatisticsMap       another global statistics
	 * @param globalAddedEdgeStatisticsMap           only for STNU
	 * @param globalPrototypalTimeStatisticsInSecMap only for CSTNPSU
	 * @return true if required task ends successfully, false otherwise.
	 */
	@SuppressWarnings({"unchecked", "null"})
	private <E1 extends Edge> boolean worker(
		File file, RunMeter runState,
		Object2ObjectMap<GlobalStatisticKey, SummaryStatistics> globalExecutionTimeStatisticsInSecMap,
		Object2ObjectMap<GlobalStatisticKey, SummaryStatistics> globalRuleExecutionStatisticsMap,
		Object2ObjectMap<GlobalStatisticKey, SummaryStatistics> globalAddedEdgeStatisticsMap,
		Object2ObjectMap<GlobalStatisticKey, SummaryStatistics> globalWaitAndOrdEdgeStatisticsMap,
		Object2ObjectMap<GlobalStatisticKey, SummaryStatistics> globalNegativeFromContingentStatisticsMap,
		Object2ObjectMap<GlobalStatisticKey, SummaryStatistics> globalPrototypalTimeStatisticsInSecMap
	                                        ) {
		// System.out.println("Analyzing file " + file.getName() + "...");
		if (LOG.isLoggable(Level.FINER)) {
			LOG.finer("Loading " + file.getName() + "...");
		}
		final TNGraphMLReader<E1> graphMLReader = new TNGraphMLReader<>();
		final TNGraph<E1> graphToCheck;
		try {
			graphToCheck = graphMLReader.readGraph(file, (Class<E1>) currentEdgeImplClass);
		} catch (IOException | ParserConfigurationException | SAXException e2) {
			final String msg =
				"File " + file.getName() + " cannot be parsed. Details: " + e2.getMessage() + ".\nIgnored.";
			LOG.warning(msg);
			System.out.println(msg);
			return false;
		}
		LOG.finer("...done!");

		/*
		 * *************************************************
		 * Possible required modifications of the structure.
		 * *************************************************
		 */
		if (cuttingEdgeFactor > 1 || removeValue != Constants.INT_NULL) {
			if (cuttingEdgeFactor > 1) {
				LOG.info("Cutting all edge values by a factor " + cuttingEdgeFactor + "...");
				for (final Edge e : graphToCheck.getEdges()) {
					if (e.isSTNEdge()) {
						((STNEdge) e).setValue(((STNEdge) e).getValue() / cuttingEdgeFactor);
					}
					if (isCSTN() || isCSTNU()) {
						final CSTNEdge eNew = (CSTNEdge) currentEdgeFactory.get(e);
						for (final Entry<Label> entry : ((CSTNEdge) e).getLabeledValueSet()) {
							final int v = entry.getIntValue() / cuttingEdgeFactor;
							eNew.mergeLabeledValue(entry.getKey(), v);
						}
						e.takeIn(eNew);
					}
				}
			}
			if (removeValue != Constants.INT_NULL && (isCSTN() || isCSTNU())) {
				LOG.info("Removing all edge values equal to " + removeValue + "...");

				final int value = removeValue;
				for (final Edge e : graphToCheck.getEdges()) {
					if (e.isSTNEdge()) {
						if (((STNEdge) e).getValue() == value) {
							graphToCheck.removeEdge(e.getName());
						}
					}
					for (final Entry<Label> entry : ((CSTNEdge) e).getLabeledValueSet()) {
						if (entry.getIntValue() == value) {
							((CSTNEdge) e).removeLabeledValue(entry.getKey());
						}
					}
				}
			}
			final String suffix = "_cut";
			final TNGraphMLWriter graphWrite = new TNGraphMLWriter(null);

			final File outputfile = new File(file.getAbsolutePath() + suffix);
			try {
				graphWrite.save(graphToCheck, outputfile);
			} catch (IOException e) {
				System.err.println(
					"It is not possible to save the result. File " + outputfile + " cannot be created: "
					+ e.getMessage() + ". Computation continues.");
			}
		}

		// In order to start with well-defined cstn, we preliminarily make a check.
		// Use g because next instructions change the structure of graph.
		final TNGraph<E1> g = new TNGraph<>(graphToCheck, (Class<E1>) currentEdgeImplClass);

		// Statistics about min, max values and #edges have to be done before init!
		int min = 0;
		int max = 0;
		for (final Edge e : g.getEdges()) {
			if (e.isSTNEdge() || e.isSTNUEdge()) {
				final int v = ((STNEdge) e).getValue();
				if (v == Constants.INT_NULL) {
					continue;
				}
				if (v > max) {
					max = v;
					continue;
				}
				if (v < min) {
					min = v;
				}
			}
			if (e.isCSTNEdge() || e.isCSTNUEdge() || e.isCSTNPSUEdge()) {
				for (final Entry<Label> entry : ((CSTNEdge) e).getLabeledValueSet()) {
					final int v = entry.getIntValue();
					if (v > max) {
						max = v;
						continue;
					}
					if (v < min) {
						min = v;
					}
				}
			}
			if (e.isSTNUEdge() && e.isContingentEdge()) {
				final int v = ((STNUEdge) e).getLabeledValue();
				if (v == Constants.INT_NULL) {
					continue;
				}
				if (v > max) {
					max = v;
					continue;
				}
				if (v < min) {
					min = v;
				}
			}
		}
		final int nEdges = g.getEdgeCount();

		CSTN cstn = null;
		CSTNU cstnu = null;
		STNU stnu = null;
		STN stn = null;
		CSTNPSU cstnpsu = null;

		final TNGraph<CSTNEdge> gCSTN;
		final TNGraph<CSTNUEdge> gCSTNU;
		final TNGraph<STNEdge> gSTN;
		final TNGraph<STNUEdge> gSTNU;
		final TNGraph<CSTNPSUEdge> gCSTNPSU;

		try {
			if (isCSTN()) {
				gCSTN = (TNGraph<CSTNEdge>) new TNGraph<>(graphToCheck, (Class<E1>) currentEdgeImplClass);
				cstn = makeCSTNInstance(gCSTN);
				cstn.initAndCheck();
			} else {
				if (isCSTNU()) {
					gCSTNU =
						(TNGraph<CSTNUEdge>) new TNGraph<>(graphToCheck, (Class<E1>) currentEdgeImplClass);
					cstnu = makeCSTNUInstance(gCSTNU);
					cstnu.initAndCheck();
				} else {
					if (isSTNU()) {
						gSTNU = (TNGraph<STNUEdge>) new TNGraph<>(graphToCheck,
						                                          (Class<E1>) currentEdgeImplClass);
						stnu = makeSTNUInstance(gSTNU);
						stnu.initAndCheck();
					} else {
						if (isCSTNPSU()) {
							gCSTNPSU = (TNGraph<CSTNPSUEdge>) new TNGraph<>(graphToCheck,
							                                                (Class<E1>) currentEdgeImplClass);
							cstnpsu = makeCSTNPSUInstance(gCSTNPSU);
							cstnpsu.initAndCheck();
						} else {
							gSTN = (TNGraph<STNEdge>) new TNGraph<>(graphToCheck,
							                                        (Class<E1>) currentEdgeImplClass);
							stn = makeSTNInstance(gSTN);
							stn.initAndCheck();
						}
					}
				}
			}
		} catch (Exception e) {
			final String msg = getNow() + ": " + file.getName() + " is not a not well-defined instance. Details:"
			                   + e.getMessage() + "\nIgnored.";
			System.out.println(msg);
			LOG.severe(msg);
			return false;
		}

		String rowToWrite = String.format(OUTPUT_ROW_GRAPH, file.getName(), graphToCheck.getVertexCount(), nEdges,
		                                  graphToCheck.getObserverCount(), graphToCheck.getContingentNodeCount(), min,
		                                  max);

		if (noDCCheck) {
			//			synchronized (this.output) {
			output.println(rowToWrite);
			output.flush();
			//			}
			runState.printProgress();
			return true;
		}

		final GlobalStatisticKey globalStatisticsKey =
			new GlobalStatisticKey(graphToCheck.getVertexCount(), graphToCheck.getObserverCount(),
			                       graphToCheck.getContingentNodeCount());
		SummaryStatistics globalExecutionTimeStatisticsInSec =
			globalExecutionTimeStatisticsInSecMap.get(globalStatisticsKey);
		SummaryStatistics globalPrototypalTimeStatisticsInSec =
			globalPrototypalTimeStatisticsInSecMap.get(globalStatisticsKey);
		SummaryStatistics globalRuleExecutionStatistics = globalRuleExecutionStatisticsMap.get(globalStatisticsKey);
		SummaryStatistics globalAddedEdgeStatistics = globalAddedEdgeStatisticsMap.get(globalStatisticsKey);
		SummaryStatistics globalWaitAndOrdEdgeStatistics =
			globalWaitAndOrdEdgeStatisticsMap.get(globalStatisticsKey);
		SummaryStatistics globalNegativeFromContingentStatistics =
			globalNegativeFromContingentStatisticsMap.get(globalStatisticsKey);
		if (globalExecutionTimeStatisticsInSec == null) {
			globalExecutionTimeStatisticsInSec = new SummaryStatistics();
			globalExecutionTimeStatisticsInSecMap.put(globalStatisticsKey, globalExecutionTimeStatisticsInSec);
		}
		if (globalPrototypalTimeStatisticsInSec == null) {
			globalPrototypalTimeStatisticsInSec = new SummaryStatistics();
			globalPrototypalTimeStatisticsInSecMap.put(globalStatisticsKey, globalPrototypalTimeStatisticsInSec);
		}
		if (globalRuleExecutionStatistics == null) {
			globalRuleExecutionStatistics = new SummaryStatistics();
			globalRuleExecutionStatisticsMap.put(globalStatisticsKey, globalRuleExecutionStatistics);
		}
		if (globalAddedEdgeStatistics == null) {
			globalAddedEdgeStatistics = new SummaryStatistics();
			globalAddedEdgeStatisticsMap.put(globalStatisticsKey, globalAddedEdgeStatistics);
		}
		if (globalWaitAndOrdEdgeStatistics == null) {
			globalWaitAndOrdEdgeStatistics = new SummaryStatistics();
			globalWaitAndOrdEdgeStatisticsMap.put(globalStatisticsKey, globalWaitAndOrdEdgeStatistics);
		}
		if (globalNegativeFromContingentStatistics == null) {
			globalNegativeFromContingentStatistics = new SummaryStatistics();
			globalNegativeFromContingentStatisticsMap.put(globalStatisticsKey,
			                                              globalNegativeFromContingentStatistics);
		}

		final String msg =
			getNow() + ": Determining DC check execution time of " + file.getName() + " repeating DC check for "
			+ nDCRepetition + " times.";
		// System.out.println(msg);
		LOG.info(msg);

		final STNCheckStatus status;
		if (isCSTN() || isCSTNU()) {
			status = DCChecker(cstn, cstnu, runState);
		} else {
			if (isCSTNPSU()) {
				assert cstnpsu != null;
				status = CSTNPSUChecker(cstnpsu, runState);
			} else {
				status = STNUDCChecker(stn, stnu, runState);
			}
		}

		assert status != null : "Previous DCChecker has a problem";

		if (!status.finished) {
			// time out or generic error
			rowToWrite += String.format(OUTPUT_ROW_TIME, nanoSeconds2Seconds(status.executionTimeNS), Double.NaN,
			                            getNumberExecutedRules(status),
			                            ((status.executionTimeNS != Constants.INT_NULL) ? "Timeout of " + timeOut +
			                                                                              " seconds."
			                                                                            : "Generic error. See log."));
			//			synchronized (this.output) {
			output.println(rowToWrite);
			output.flush();
			//			}
			globalExecutionTimeStatisticsInSec.addValue(timeOut);
			runState.printProgress();
			return false;
		}

		final double localAvgInSec = nanoSeconds2Seconds(status.executionTimeNS);
		final double localStdDevInSec = nanoSeconds2Seconds(status.stdDevExecutionTimeNS);

		globalExecutionTimeStatisticsInSec.addValue(localAvgInSec);

		LOG.info(file.getName() + " has been checked (algorithm ends in a stable state): " + status.finished);
		LOG.info(file.getName() + " is " + status.consistency);
		LOG.info(file.getName() + " average checking time [s]" + localAvgInSec);
		LOG.info(file.getName() + " std. deviation [s]" + localStdDevInSec);

		final int nRules = getNumberExecutedRules(status);
		globalRuleExecutionStatistics.addValue(nRules);

		rowToWrite +=
			String.format(OUTPUT_ROW_TIME, localAvgInSec, ((nDCRepetition > 1) ? localStdDevInSec : Double.NaN), nRules,
			              // ((!this.noDCCheck) ? (status.finished ? Boolean.toString(status.consistency).toUpperCase() : "FALSE") : "-"));
			              (status.finished ? Boolean.toString(status.consistency).toUpperCase(Locale.ROOT) : "FALSE"));
		if (isCSTN()) {
			rowToWrite += String.format(OUTPUT_ROW_TIME_CSTN, ((CSTNCheckStatus) status).r0calls,
			                            ((CSTNCheckStatus) status).r3calls,
			                            ((CSTNCheckStatus) status).labeledValuePropagationCalls,
			                            ((CSTNCheckStatus) status).potentialUpdate);
		}
		if (isSTNU()) {
			assert stnu != null;
			final TNGraph<STNUEdge> graph = stnu.getG();
			final double nEdgesFinal = graph.getEdgeCount();
			final double edgeRate = (nEdgesFinal / nEdges);
			globalAddedEdgeStatistics.addValue(edgeRate);
			// temporary statistics
			// count #wait with also ordinary value
			int waitsWithAlsoOrdinaryValue = 0;
			final Object2ObjectMap<LabeledNode, LabeledNode> activationNodeMap = stnu.getActivationNodeMap();
			if (activationNodeMap != null) {
				for (final LabeledNode activation : activationNodeMap.values()) {
					for (final ObjectObjectImmutablePair<STNUEdge, LabeledNode> entry : graph.getInEdgesAndNodes(
						activation)) {
						final STNUEdge e = entry.left();
						if (e.getLabeledValue() != Constants.INT_NULL && e.getValue() != Constants.INT_NULL) {
							// it contains a wait and an ordinary value
							waitsWithAlsoOrdinaryValue++;
						}
					}
				}
			}
			final double waitsWithAlsoOrdinaryValueRate = (waitsWithAlsoOrdinaryValue / nEdgesFinal);
			globalWaitAndOrdEdgeStatistics.addValue(waitsWithAlsoOrdinaryValueRate);
			// count negative edge outgoing from contingent time point
			double negativeOrdinaryFromContingent = 0.0;
			if (activationNodeMap != null) {
				for (final LabeledNode contingent : activationNodeMap.keySet()) {
					for (final ObjectObjectImmutablePair<STNUEdge, LabeledNode> entry : graph.getOutEdgesAndNodes(
						contingent)) {
						final STNUEdge e = entry.left();
						final int u = e.getLabeledValue();
						final int v = e.getValue();

						if ((u != Constants.INT_NULL && u <= 0) || (v != Constants.INT_NULL && v <= 0)) {
							// it is a negative edge outgoing from a contingent node
							negativeOrdinaryFromContingent++;
						}
					}
				}
			}
			final double negativeOrdinaryFromContingentRate = negativeOrdinaryFromContingent / nEdgesFinal;
			globalNegativeFromContingentStatistics.addValue(negativeOrdinaryFromContingentRate);

			rowToWrite += String.format(OUTPUT_ROW_TIME_STNU, edgeRate, waitsWithAlsoOrdinaryValueRate,
			                            negativeOrdinaryFromContingentRate);
		}
		if (isCSTNU()) {
			rowToWrite += String.format(OUTPUT_ROW_TIME_STATS_CSTNU, ((CSTNUCheckStatus) status).zExclamationRuleCalls,
			                            ((CSTNUCheckStatus) status).lowerCaseRuleCalls,
			                            ((CSTNUCheckStatus) status).crossCaseRuleCalls,
			                            ((CSTNUCheckStatus) status).letterRemovalRuleCalls);
		}

		if (isCSTNPSU()) {
			final double partialTimeInSec = nanoSeconds2Seconds(status.partialExecutionTimeNS);
			final double partialStdDevInSec = nanoSeconds2Seconds(status.stdDevPartialExecutionTimeNS);
			LOG.info(file.getName() + " average prototypal only determination time [s]" + partialTimeInSec);

			globalPrototypalTimeStatisticsInSec.addValue(partialTimeInSec);
			rowToWrite += String.format(OUTPUT_ROW_TIME_STATS_CSTNPSU
				, partialTimeInSec//the prototypal calculation time
				, ((nDCRepetition > 1) ? partialStdDevInSec : Double.NaN)
				, status.note//prototypal value
			                           );

		}
		output.println(rowToWrite);
		output.flush();
		runState.printProgress();
		return true;
	}

	/**
	 * @return true if the input graph represents a CSTN instance
	 */
	private boolean isCSTN() {
		return networkType == NetworkType.CSTN;
	}

	/**
	 * @return true if the input graph represents a CSTNU instance
	 */
	private boolean isCSTNU() {
		return networkType == NetworkType.CSTNU;
	}

	/**
	 * @param g input graph
	 * @return a cstn instance
	 */

	private CSTN makeCSTNInstance(TNGraph<CSTNEdge> g) {
		if (cstn2cstn0) {
			return new CSTN2CSTN0(reactionTime, g, timeOut);
		}
		if (potential)// the implicit semantics is IR
		{
			return new CSTNPotential(g, timeOut);
		}
		CSTN cstn = null;
		switch (dcSemantics) {
			case ε -> {
				if (onlyLPQR0QR3OrToZ) {
					cstn = (woNodeLabels) ? new CSTNEpsilon3RwoNodeLabels(reactionTime, g, timeOut)
					                      : new CSTNEpsilon3R(reactionTime, g, timeOut);
				} else {
					cstn = (woNodeLabels) ? new CSTNEpsilonwoNodeLabels(reactionTime, g, timeOut)
					                      : new CSTNEpsilon(reactionTime, g, timeOut);
				}
			}
			case IR -> {
				if (onlyLPQR0QR3OrToZ) {
					cstn = (woNodeLabels) ? new CSTNIR3RwoNodeLabels(g, timeOut)
					                      : new CSTNIR3R(g, timeOut);
				} else {
					cstn = (woNodeLabels) ? new CSTNIRwoNodeLabels(g, timeOut) : new CSTNIR(g, timeOut);
				}
			}
			case Std -> {
				if (onlyLPQR0QR3OrToZ) {
					throw new IllegalArgumentException(
						"For standard semantics there is no DC checking algorithm that works only considering constraints ending to Z.");
				}
				cstn = (woNodeLabels) ? new CSTNwoNodeLabel(g, timeOut) : new CSTN(g, timeOut);
			}
		}
		return cstn;
	}

	/**
	 * @param g input graph
	 * @return a cstnu instance
	 */
	private CSTNU makeCSTNUInstance(TNGraph<CSTNUEdge> g) {
		if (isCSTNU()) {
			if (cstnu2cstn) {
				return new CSTNU2CSTN(g, timeOut);
			}
			return new CSTNU(g, timeOut, onlyLPQR0QR3OrToZ);
		}
		throw new IllegalArgumentException("Required a CSTNU instance but the graph is not a CSTNU graph.");
	}

	/**
	 * @return true if the input graph represents a STNU instance
	 */
	private boolean isSTNU() {
		return networkType == NetworkType.STNU;
	}

	/**
	 * @param g input graph
	 * @return an stnu instance
	 */

	private STNU makeSTNUInstance(TNGraph<STNUEdge> g) {
		if (isSTNU()) {
			final STNU stnu = new STNU(g, timeOut);
			stnu.setSave(save);
			return stnu;
		}
		throw new IllegalArgumentException("Required a STNU instance but the input graph is not a STNU graph.");
	}

	/**
	 * @return true if the input graph represents a CSTNPSU instance
	 */
	private boolean isCSTNPSU() {
		return networkType == NetworkType.CSTNPSU;
	}

	/**
	 * @param g input graph
	 * @return a cstnu instance
	 */
	private CSTNPSU makeCSTNPSUInstance(TNGraph<CSTNPSUEdge> g) {
		if (isCSTNPSU()) {
			return new CSTNPSU(g, timeOut);
		}
		throw new IllegalArgumentException("Required a CSTNPSU instance but the graph is not a CSTNU graph.");
	}

	/**
	 * @param g input graph
	 * @return an STN instance
	 */
	private STN makeSTNInstance(TNGraph<STNEdge> g) {
		if (isSTN()) {
			return new STN(g, timeOut);
		}
		throw new IllegalArgumentException("Required an STN instance but the input graph is not an STN graph.");
	}

	@Nullable
	@SuppressWarnings({"unchecked", "rawtypes"})
	private CSTNCheckStatus DCChecker(CSTN cstn, CSTNU cstnu, RunMeter ignoredRunState) {
		String msg;
		boolean checkInterrupted = false;
		CSTNCheckStatus status;
		final TNGraph<? extends CSTNEdge> graphToCheck;
		TNGraph g;// even though raw is dangerous, here it is safe!

		if (isCSTNU()) {
			status = new CSTNUCheckStatus();
			graphToCheck = cstnu.getG();
		} else if (isCSTN()) {
			status = new CSTNCheckStatus();
			graphToCheck = cstn.getG();
		} else {
			return null;// we check only cstn o cstnu
		}

		final SummaryStatistics localSummaryStat = new SummaryStatistics();
		for (int j = 0; j < nDCRepetition && !checkInterrupted && !status.timeout; j++) {
			LOG.info("Test " + (j + 1) + "/" + nDCRepetition + " for (C)STN(U) " + graphToCheck.getFileName()
				.getName());

			// It is necessary to reset the graph!
			g = new TNGraph(graphToCheck, currentEdgeImplClass);
			if (isCSTNU()) {
				cstnu.setG(g);
			} else {
				cstn.setG(g);
			}
			try {
				status = (isCSTNU()) ? cstnu.dynamicControllabilityCheck() : (cstn.dynamicConsistencyCheck());
			} catch (CancellationException ex) {
				msg = getNow() + ": Cancellation has occurred. " + graphToCheck.getFileName() + " CSTN(U) is ignored.";
				System.out.println(msg);
				LOG.severe(msg);
				checkInterrupted = true;
				status.consistency = false;
				continue;
			} catch (Exception e) {
				msg = getNow() + ": a different exception has occurred. " + graphToCheck.getFileName()
				      + " CSTN(U) is ignored.\nError details: " + e;
				System.out.println(msg);
				LOG.severe(msg);
				checkInterrupted = true;
				status.consistency = false;
				continue;
			}
			localSummaryStat.addValue(status.executionTimeNS);
		} // end for checking repetition for a single file

		if (status.timeout || checkInterrupted) {
			if (status.timeout) {
				msg = "\n\n" + getNow() + ": Timeout or interrupt occurred. " + graphToCheck.getFileName()
				      + " CSTN(U) is ignored.\n";
				System.out.println(msg);
			}
			return status;
		}

		msg = getNow() + ": done! It is " + ((!status.consistency) ? "NOT " : "") + "DC.";
		// System.out.println(msg);
		LOG.info(msg);

		status.executionTimeNS = (long) localSummaryStat.getMean();
		status.stdDevExecutionTimeNS = (long) localSummaryStat.getStandardDeviation();

		return status;
	}

	/**
	 * For CSTNPSU (FTNU), it determines the prototypal link. In this way, executionTime is the time for DC checking
	 * and, when the instance is DC, partialExecutionTime is the time for PrototypalLink determination. The sum of these
	 * two values is the global time for the method.
	 *
	 * @param cstnpsu         instance
	 * @param ignoredRunState ignored
	 * @return the status of the check
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private CSTNCheckStatus CSTNPSUChecker(CSTNPSU cstnpsu, RunMeter ignoredRunState) {
		String msg;
		boolean checkInterrupted = false;
		CSTNCheckStatus status;
		final TNGraph<CSTNPSUEdge> graphToCheck;
		TNGraph g;

		status = new CSTNUCheckStatus();
		graphToCheck = cstnpsu.getG();

		final SummaryStatistics localSummaryStat = new SummaryStatistics();
		final SummaryStatistics localSummaryStat4PrototypalTime = new SummaryStatistics();
		CSTNPSU.PrototypalLink prototypal = null;
		for (int j = 0; j < nDCRepetition && !checkInterrupted && !status.timeout; j++) {
			LOG.info("Test " + (j + 1) + "/" + nDCRepetition + " for CSTNPSU " + graphToCheck.getFileName()
				.getName());

			// It is necessary to reset the graph!
			g = new TNGraph(graphToCheck, currentEdgeImplClass);
			cstnpsu.setG(g);

			try {
				prototypal = cstnpsu.getPrototypalLink();
				status = cstnpsu.getCheckStatus();
			} catch (CancellationException ex) {
				msg = getNow() + ": Cancellation has occurred. " + graphToCheck.getFileName() + " CSTN(U) is ignored.";
				System.out.println(msg);
				LOG.severe(msg);
				checkInterrupted = true;
				status.consistency = false;
				continue;
			} catch (Exception e) {
				msg = getNow() + ": a different exception has occurred. " + graphToCheck.getFileName()
				      + " CSTN(U) is ignored.\nError details: " + e;
				System.out.println(msg);
				LOG.severe(msg);
				checkInterrupted = true;
				status.consistency = false;
				continue;
			}
			localSummaryStat.addValue(status.executionTimeNS);
			localSummaryStat4PrototypalTime.addValue(status.partialExecutionTimeNS);//partial time
			// for the determination of the prototypal link after the DC checking.
		} // end for checking repetition for a single file

		if (status.timeout || checkInterrupted) {
			if (status.timeout) {
				msg = "\n\n" + getNow() + ": Timeout or interrupt occurred. " + graphToCheck.getFileName()
				      + " CSTN(U) is ignored.\n";
				System.out.println(msg);
			}
			return status;
		}

		msg = getNow() + ": done! It is " + ((!status.consistency) ? "NOT " : "") + "DC.";
		// System.out.println(msg);
		LOG.info(msg);

		status.executionTimeNS = (long) localSummaryStat.getMean();
		status.stdDevExecutionTimeNS = (long) localSummaryStat.getStandardDeviation();
		status.partialExecutionTimeNS = (long) localSummaryStat4PrototypalTime.getMean();
		status.stdDevPartialExecutionTimeNS = (long) localSummaryStat4PrototypalTime.getStandardDeviation();
		status.note = (prototypal != null) ? prototypal.toString() : "not determined";
		return status;

	}

	@Nullable
	@SuppressWarnings({"unchecked", "rawtypes"})
	private STNCheckStatus STNUDCChecker(STN stn, STNU stnu, RunMeter ignoredRunState) {
		String msg;
		boolean checkInterrupted = false;
		STNCheckStatus status;
		final TNGraph<? extends STNEdge> graphToCheck;
		TNGraph g;// even though raw is dangerous, here it is safe!

		if (isSTN()) {
			status = new STNCheckStatus();
			graphToCheck = stn.getG();
		} else if (isSTNU()) {
			status = new STNUCheckStatus();
			graphToCheck = stnu.getG();
		} else {
			return null;// check only STN o STNU
		}

		final SummaryStatistics localSummaryStat = new SummaryStatistics();
		for (int j = 0; j < nDCRepetition && !checkInterrupted && !status.timeout; j++) {
			LOG.info("Test " + (j + 1) + "/" + nDCRepetition + " for STN(U) " + graphToCheck.getFileName()
				.getName());

			// It is necessary to reset the graph!
			g = new TNGraph(graphToCheck, currentEdgeImplClass);
			if (isSTN()) {
				stn.setG(g);
			} else {
				stnu.setG(g);
			}
			try {
				status = (isSTNU()) ? stnu.dynamicControllabilityCheck(stnuCheckAlgorithm)
				                    : (stn.consistencyCheck());
			} catch (CancellationException ex) {
				msg = getNow() + ": Cancellation has occurred. " + graphToCheck.getFileName() + " STN(U) is ignored.";
				System.out.println(msg);
				LOG.severe(msg);
				checkInterrupted = true;
				status.consistency = false;
				continue;
			} catch (Exception e) {
				msg = getNow() + ": a different exception has occurred. " + graphToCheck.getFileName()
				      + " STN(U) is ignored.\nError details: " + e;
				System.out.println(msg);
				LOG.severe(msg);
				checkInterrupted = true;
				status.consistency = false;
				continue;
			}
			localSummaryStat.addValue(status.executionTimeNS);
		} // end for checking repetition for a single file

		if (status.timeout || checkInterrupted) {
			if (status.timeout) {
				msg = "\n\n" + getNow() + ": Timeout or interrupt occurred. " + graphToCheck.getFileName()
				      + " STN(U) is ignored.\n";
				System.out.println(msg);
			}
			return status;
		}

		msg = getNow() + ": done! It is " + ((!status.consistency) ? "NOT " : "") + "DC.";
		// System.out.println(msg);
		LOG.info(msg);

		status.executionTimeNS = (long) localSummaryStat.getMean();
		status.stdDevExecutionTimeNS = (long) localSummaryStat.getStandardDeviation();

		return status;
	}

	/**
	 * @param value value in nanoseconds
	 * @return the value in seconds
	 */
	private static double nanoSeconds2Seconds(double value) {
		return value / 1E9;
	}

	/**
	 * @param status current status
	 * @return the number of executed rules.
	 */
	private int getNumberExecutedRules(STNCheckStatus status) {
		int nRules = status.propagationCalls;

		if (isCSTN()) {
			final CSTNCheckStatus s = ((CSTNCheckStatus) status);
			nRules = s.r0calls + s.r3calls + s.labeledValuePropagationCalls + s.potentialUpdate;
		}
		if (isSTNU()) {
			final STNUCheckStatus s = ((STNUCheckStatus) status);
			nRules = s.cycles;
		}
		if (isCSTNU()) {
			nRules += ((CSTNUCheckStatus) status).zExclamationRuleCalls + ((CSTNUCheckStatus) status).lowerCaseRuleCalls
			          + ((CSTNUCheckStatus) status).crossCaseRuleCalls
			          + ((CSTNUCheckStatus) status).letterRemovalRuleCalls;
		}
		return nRules;
	}

	/**
	 * @return true if the input graph represents an STN instance
	 */
	private boolean isSTN() {
		return networkType == NetworkType.STN;
	}

	/**
	 * Allows to check the execution time of DC checking algorithm giving a set of instances. The set of instances are
	 * checked in parallel if the machine is a multi-cpus one.<br> Moreover, this method tries to exploit <a
	 * href="https://github.com/OpenHFT/Java-Thread-Affinity">thread affinity</a> if kernel allows it.<br> So, if it is
	 * possible to reserve some CPU modifying the kernel as explained in <a
	 * href="https://github.com/OpenHFT/Java-Thread-Affinity">thread affinity page</a>. it is possible to run the
	 * parallel thread in the better conditions.
	 *
	 * @param args an array of {@link java.lang.String} objects.
	 */
	@SuppressWarnings("null")
	public static void main(String[] args) {

		LOG.finest("Checker " + VERSIONandDATE + "\nStart...");
		System.out.println("Checker " + VERSIONandDATE + "\n" + getNow() + ": Start of execution.");
		final Checker tester = new Checker();

		if (!tester.manageParameters(args)) {
			return;
		}
		LOG.finest("Parameters ok!");
		if (tester.versionReq) {
			return;
		}
		// All parameters are set

		/*
		 * <b>AffinityLock allows to lock a CPU to a thread.</b>
		 * It seems that allows better performance when a CPU-bound task has to be executed!
		 * To work, it requires to reserve some CPUs.
		 * In our server I modified /boot/grub/grub.cfg adding "isolcpus=4,5,6,7,8,9,10,11" to the line that boot the kernel to reserve 8 CPUs.
		 * Such CPU have (socketId-coreId): 4(0-4), 5(0-5), 6(1-0), 7(1-1), 8(1-2), 9(1-3), 10(1-4), 11(1-5).
		 * Then I reboot the server.
		 * This class has to be started as normal (no using taskset!)
		 * I don't modify in /etc/default/grub and, then, update-grub because it changes a lot of things.
		 * **NOTE**
		 * After some simulations on AMD Opteron™ 4334, I discovered that:
		 * 0) The best performance is obtained checking one file at time!
		 * 1) It doesn't worth to run more than 2 processor in parallel because this kind of app does not allow to scale.
		 * For each added process, the performance lowers about 10%.
		 * 2) Running two processes in the two different sockets lowers the performance about the 20%!
		 * It is better to run the two process on the same socket.
		 * 3) Therefore, I modified /boot/grub/grub.cfg setting "isolcpus=8,9,10,11"
		 */
		final int nCPUs = tester.nCPUs;// Runtime.getRuntime().availableProcessors();

		// Logging stuff for learning Affinity behaviour.
		// System.out.println("Base CPU affinity mask: " + AffinityLock.BASE_AFFINITY);
		// System.out.println("Reserved CPU affinity mask: " + AffinityLock.RESERVED_AFFINITY);
		// System.out.println("Current CPU affinity: " + Affinity.getCpu());
		// CpuLayout cpuLayout = AffinityLock.cpuLayout();
		// System.out.println("CPU Layout: " + cpuLayout.toString());
		// for (int k = 11; k > 3; k--) {
		// System.out.println("Cpu " + k + "\nSocket: " + cpuLayout.socketId(k) + ". Core:" + cpuLayout.coreId(k));
		// }
		/*
		 * check all files in parallel.
		 */
		/*
		 * 1st method using streams (parallelStream)
		 * Very nice, but it is affected by a known problem with streams:
		 * the use of default ForkJoinPool in the implementation of parallel() makes possible that
		 * a heavy task can block following tasks.
		 */
		// tester.inputCSTNFile.parallelStream().forEach(file -> cstnWorker(tester, file, executor, edgeFactory));

		/*
		 * 2nd method using Callable.
		 * A newFixedThreadPool executor create nProcessor threads and pipeline all process associated to file to such pool.
		 * There is no problem if one thread requires a lot of time.
		 * Final synchronization is obtained requesting .get from Callable.
		 * AffinityThreadFactory allows to lock a thread in one core for all the time (less overhead)
		 */
		final ExecutorService cstnExecutor = (nCPUs > 0) ? Executors.newFixedThreadPool(nCPUs,
		                                                                                new AffinityThreadFactory(
			                                                                                "cstnWorker",
			                                                                                AffinityStrategies.DIFFERENT_CORE))
		                                                 : null;

		/*
		 * To collect statistics w.r.t. the dimension of networks
		 */
		final Object2ObjectMap<GlobalStatisticKey, SummaryStatistics> groupExecutionTimeStatisticsInSec =
			new Object2ObjectAVLTreeMap<>();
		final Object2ObjectMap<GlobalStatisticKey, SummaryStatistics> groupPrototypalExecutionTimeStatisticsInSec =
			new Object2ObjectAVLTreeMap<>();
		final Object2ObjectMap<GlobalStatisticKey, SummaryStatistics> groupRuleExecutionStatistics =
			new Object2ObjectAVLTreeMap<>();
		final Object2ObjectMap<GlobalStatisticKey, SummaryStatistics> groupAddedEdgeStatistics =
			new Object2ObjectAVLTreeMap<>();
		final Object2ObjectMap<GlobalStatisticKey, SummaryStatistics> groupWaitAndOrdinaryEdgeStatistics =
			new Object2ObjectAVLTreeMap<>();
		final Object2ObjectMap<GlobalStatisticKey, SummaryStatistics> groupNegativeFromContingentEdgeStatistics =
			new Object2ObjectAVLTreeMap<>();

		System.out.println(getNow() + ": #Processors for computation: " + nCPUs);
		System.out.println(getNow() + ": Instances to check are " + tester.networkType + " instances.");
		final RunMeter runMeter = new RunMeter(System.currentTimeMillis(), tester.instances.size(), 0);
		runMeter.printProgress(0);

		final List<Future<Boolean>> future = new ArrayList<>(8);

		tester.output.println("*".repeat(79));
		tester.output.println("* Trial date: " + getNow());
		tester.output.println("*".repeat(79));
		tester.output.println(tester.getHeader());
		tester.output.flush();
		int nTaskSuccessfullyFinished = 0;
		for (final File file : tester.instances) {
			if (nCPUs > 0) {
				future.add(cstnExecutor.submit(
					() -> tester.worker(file, runMeter, groupExecutionTimeStatisticsInSec,
					                    groupRuleExecutionStatistics,
					                    groupAddedEdgeStatistics, groupWaitAndOrdinaryEdgeStatistics,
					                    groupNegativeFromContingentEdgeStatistics,
					                    groupPrototypalExecutionTimeStatisticsInSec)));
			} else {
				if (tester.worker(file, runMeter, groupExecutionTimeStatisticsInSec, groupRuleExecutionStatistics,
				                  groupAddedEdgeStatistics, groupWaitAndOrdinaryEdgeStatistics,
				                  groupNegativeFromContingentEdgeStatistics,
				                  groupPrototypalExecutionTimeStatisticsInSec)) {
					nTaskSuccessfullyFinished++;
				}
			}
		}
		if (nCPUs > 0) {
			// System.out.println(getNow() + ": #Tasks queued: " + future.size());
			// wait all tasks have been finished and count!
			for (final Future<Boolean> f : future) {
				try {
					if (f.get()) {
						nTaskSuccessfullyFinished++;
					}
				} catch (Exception ex) {
					System.out.println("\nA problem occurred during a check: " + ex.getMessage() + ". File ignored.");
				} finally {
					if (!f.isDone()) {
						LOG.warning("It is necessary to cancel the task before continuing.");
						f.cancel(true);
					}
				}
			}
		}
		final String msg = "Number of instances processed successfully over total: " + nTaskSuccessfullyFinished + "/"
		                   + tester.instances.size() + ".";
		LOG.info(msg);
		System.out.println("\n" + getNow() + ": " + msg);

		tester.output.printf(GLOBAL_HEADER);

		for (final it.unimi.dsi.fastutil.objects.Object2ObjectMap.Entry<GlobalStatisticKey, SummaryStatistics> entry : groupExecutionTimeStatisticsInSec.object2ObjectEntrySet()) {
			final GlobalStatisticKey globalStatisticsKey = entry.getKey();
			tester.output.printf(GLOBAL_HEADER_ROW,
			                     entry.getValue().getN(),
			                     globalStatisticsKey.getNodes(),
			                     globalStatisticsKey.getContingent(),
			                     globalStatisticsKey.getPropositions(),
			                     entry.getValue().getMean(),
			                     entry.getValue().getStandardDeviation(),
			                     groupRuleExecutionStatistics.get(globalStatisticsKey).getMean(),
			                     groupRuleExecutionStatistics.get(globalStatisticsKey).getStandardDeviation(),
			                     groupAddedEdgeStatistics.get(globalStatisticsKey).getMean(),
			                     groupAddedEdgeStatistics.get(globalStatisticsKey).getStandardDeviation(),
			                     groupWaitAndOrdinaryEdgeStatistics.get(globalStatisticsKey).getMean(),
			                     groupWaitAndOrdinaryEdgeStatistics.get(globalStatisticsKey).getStandardDeviation(),
			                     groupNegativeFromContingentEdgeStatistics.get(globalStatisticsKey).getMean(),
			                     groupNegativeFromContingentEdgeStatistics.get(globalStatisticsKey)
				                     .getStandardDeviation(),
			                     groupPrototypalExecutionTimeStatisticsInSec.get(globalStatisticsKey).getMean(),
			                     groupPrototypalExecutionTimeStatisticsInSec.get(globalStatisticsKey)
				                     .getStandardDeviation());
		}
		tester.output.printf("%n%n%n");

		if (nCPUs > 0) {
			// executor shutdown!
			try {
				System.out.println(getNow() + ": Shutdown executors.");
				cstnExecutor.shutdown();
				if (!cstnExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
					System.err.println(getNow() + ": shutdown no yet finished...");
				}
			} catch (InterruptedException e) {
				System.err.println(getNow() + ": Tasks interrupted.");
			} finally {
				if (!cstnExecutor.isTerminated()) {
					System.err.println(getNow() + ": remove non-finished tasks.");
				}
				cstnExecutor.shutdownNow();
				System.out.println(getNow() + ": Shutdown finished.\nExecution finished.");
			}
		}
		tester.output.close();
	}
}
