// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

package it.univr.di.cstnu.util;

import it.univr.di.cstnu.algorithms.CSTNU;
import it.univr.di.cstnu.algorithms.WellDefinitionException;
import it.univr.di.cstnu.graph.*;
import it.univr.di.labeledvalue.ALabel;
import it.univr.di.labeledvalue.Label;
import net.openhft.affinity.AffinityStrategies;
import net.openhft.affinity.AffinityThreadFactory;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.StringArrayOptionHandler;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads CSTNU instances and converts them into CSTNPSU (==FTNU) instances transforming each contingent link into a
 * guarded one.
 * <p>
 * The transformation of a contingent link consists into adding two ordinary constraints between the two nodes of the
 * contingent link with bounds derived by the contingent range.
 * </p>
 * <p>
 * If the contingent link is represented by the lower and upper values plus the lower and upper ordinary constraints,
 * this class modifies the ordinary constraints reducing/increasing them to make a guarded range with the required
 * reduction/increment.
 * </p>
 *
 * @author posenato
 * @version $Rev: 733 $
 */
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "STCAL", justification = "It is not relevant here!")
public final class CSTNU2CSTNPSU {

	/**
	 * Version of the class
	 */
	static public final String VERSIONandDATE = "Version 1.0 - May, 27 2023";

	/**
	 * logger
	 */
	static final Logger LOG = Logger.getLogger(CSTNU2CSTNPSU.class.getName());

	/**
	 * Date formatter
	 */
	private final static SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");

	/**
	 * Class for representing edge .
	 */
	Class<? extends Edge> currentEdgeImplClass;

	/**
	 * Lower decrement
	 */
	@Option(depends = "--decrement", name = "-d", usage = "The percentage [0, 100) of the lower guard to remove for determining the lower bound. Lower bound will be always greater than 0 and lower guard - 1 (if positive).")
	private int lowerDecrement = 20;// 10% less

	/**
	 * Percentage of the lower guard (derived by lowerDecrement in @manageParameters)
	 */
	private double decrementFactor;

	/**
	 * Upper decrement
	 */
	@Option(depends = "--increment", name = "-i", usage = "The percentage [0, 100) of the upper guard to add for determining the upper bound. Upper bound will be always greater than upper guard by one unit at least.")
	private int upperIncrement = 20;// 10% more

	/**
	 * Percentage of the upper guard (derived by upperIncrement in @manageParameters)
	 */
	private double incrementFactor;

	/**
	 * The input file names. Each file has to contain a CSTNU graph in GraphML format.
	 */
	@Argument(required = true,
		usage = "Input files. Each input file has to be a DC CSTNU graph in GraphML format. The DC property is fundamental!",
		metaVar = "CSTNU_file_names", handler = StringArrayOptionHandler.class)
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
	 * To allow the use of different suffix.
	 */
	@Option(name = "--suffix",
		usage = "The suffix to set for the converted file.")
	private String suffix = "cstnpsu";


	/**
	 * Software Version.
	 */
	@Option(name = "-v", aliases = "--version", usage = "Version")
	private boolean versionReq;

	/**
	 * Allows the modification of a set of input instances.
	 *
	 * @param args an array of {@link String} objects.
	 */
	@SuppressWarnings("null")
	public static void main(final String[] args) {

		LOG.finest("CSTNU2CSTNPSU " + VERSIONandDATE + "\nStart...");
		System.out.println("CSTNU2CSTNPSU " + VERSIONandDATE + "\n"
		                   + "\nSPDX-License-Identifier: LGPL-3.0-or-later, Roberto Posenato.\n" + getNow()
		                   + ": Start of execution.");
		final CSTNU2CSTNPSU converter = new CSTNU2CSTNPSU();

		if (!converter.manageParameters(args)) {
			return;
		}
		LOG.finest("Parameters ok!");
		if (converter.versionReq) {
			return;
		}
		// All parameters are set

		/*
		 * <a class="" id="affinity">AffinityLock allows to lock a CPU to a thread.</a>
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
		 * 1) It doesn't worth to run more than 2 processor in parallel because this kind of app does not allow to scale. For each added process,
		 * the performance lowers about 10%.
		 * 2) Running two processes in the two different sockets lowers the performance about the 20%! It is better to run the two process on the same socket.
		 * 3) Therefore, I modified /boot/grub/grub.cfg setting "isolcpus=8,9,10,11"
		 */
		final int nCPUs = converter.nCPUs;// Runtime.getRuntime().availableProcessors();

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
		 * Very nice, but it suffers with a known problem with streams:
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
		final ExecutorService CSTNU2FTNUExecutor = (nCPUs > 0) ? Executors.newFixedThreadPool(nCPUs,
		                                                                                      new AffinityThreadFactory(
			                                                                                      "cstnWorker",
			                                                                                      AffinityStrategies.DIFFERENT_CORE))
		                                                       : null;

		System.out.println(getNow() + ": #Processors for computation: " + nCPUs);
		System.out.println(getNow() + ": Instances to check are STNU instances.");
		final RunMeter runMeter = new RunMeter(System.currentTimeMillis(), converter.instances.size(), 0);
		runMeter.printProgress(0);

		final List<Future<Boolean>> future = new ArrayList<>(10);

		int nTaskSuccessfullyFinished = 0;
		for (final File file : converter.instances) {
			if (nCPUs > 0) {
				future.add(CSTNU2FTNUExecutor.submit(() -> Boolean.valueOf(converter.worker(file, runMeter))));
			} else {
				if (converter.worker(file, runMeter)) {
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
				} catch (final Exception ex) {
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
		                   + converter.instances.size() + ".";
		LOG.info(msg);
		System.out.println("\n" + getNow() + ": " + msg);

		if (nCPUs > 0) {
			// executor shutdown!
			try {
				System.out.println(getNow() + ": Shutdown executors.");
				CSTNU2FTNUExecutor.shutdown();
				if (!CSTNU2FTNUExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
					System.err.println(getNow() + ": Tasks is very long to showdown...");
				}
			} catch (final InterruptedException e) {
				System.err.println(getNow() + ": Tasks interrupted.");
			} finally {
				if (!CSTNU2FTNUExecutor.isTerminated()) {
					System.err.println(getNow() + ": Cancel non-finished tasks.");
				}
				CSTNU2FTNUExecutor.shutdownNow();
				System.out.println(getNow() + ": Shutdown finished.\nExecution finished.");
			}
		}
	}

	/**
	 * @return current time in {@link #dateFormatter} format
	 */
	private static String getNow() {
		return dateFormatter.format(new Date());
	}

	/**
	 * Simple method to manage command line parameters using {@code args4j} library.
	 *
	 * @param args input args
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
				"java -cp CSTNU-<version>.jar -cp it.univr.di.cstnu.CSTNU2CSTNPSU [options...] arguments...");
			// print the list of available options
			parser.printUsage(System.err);
			System.err.println();

			// print option sample. This is useful some time
			// System.err.println("Example: java -jar Checker.jar" + parser.printExample(OptionHandlerFilter.REQUIRED) +
			// " <CSTN_file_name0> <CSTN_file_name1>...");
			return false;
		}
		if (versionReq) {
			System.out.print(getClass().getName() + " " + CSTNU2CSTNPSU.VERSIONandDATE
			                 + ". Academic and non-commercial use only.\n" + "Copyright © 2023, Roberto Posenato");
			return true;
		}

		if (lowerDecrement < 0 || upperIncrement < 0 || lowerDecrement >= 100
		    || upperIncrement >= 100) {
			throw new IllegalArgumentException(
				"lower decrement and upper increment parameters must be a percentage in the range [0, 100).");
		}

		decrementFactor = (100 - lowerDecrement) / 100.0;
		incrementFactor = 1 + (100 - upperIncrement) / 100.0;
		if (LOG.isLoggable(Level.FINER)) {
			LOG.finer("Decrement factor: " + decrementFactor + "\nIncrement factor: " + incrementFactor);
		}

		final String localSuffix;
		currentEdgeImplClass = CSTNUEdgePluggable.class;
		localSuffix = "cstnu";

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
			if (!file.getName().endsWith(localSuffix)) {
				System.err.println("File " + fileName
				                   +
				                   " has not the right suffix associated to the suffix of the given network type (right suffix: "
				                   + localSuffix + "). Game over :-/");
				parser.printUsage(System.err);
				System.err.println();
				return false;
			}
			instances.add(file);
		}

		if (this.suffix.isEmpty()) {
			this.suffix = "cstnpsu";//prevent error
		}
		this.suffix = "." + this.suffix;
		return true;
	}

	/**
	 * @param file     input file
	 * @param runState current state
	 * @return true if required task ends successfully, false otherwise.
	 */
	@SuppressWarnings("unchecked")
	private boolean worker(File file, RunMeter runState) {
		// System.out.println("Analyzing file " + file.getName() + "...");
		if (CSTNU2CSTNPSU.LOG.isLoggable(Level.FINER)) {
			CSTNU2CSTNPSU.LOG.finer("Loading " + file.getName() + "...");
		}
		final TNGraphMLReader<CSTNUEdge> graphMLReader = new TNGraphMLReader<>();
		final TNGraph<CSTNUEdge> graphToAdjust;
		try {
			graphToAdjust = graphMLReader.readGraph(file, (Class<CSTNUEdge>) currentEdgeImplClass);
		} catch (IOException | ParserConfigurationException | SAXException e2) {
			final String msg =
				"File " + file.getName() + " cannot be parsed. Details: " + e2.getMessage() + ".\nIgnored.";
			CSTNU2CSTNPSU.LOG.warning(msg);
			System.out.println(msg);
			return false;
		}
		CSTNU2CSTNPSU.LOG.finer("...done!");

		final TNGraph<CSTNUEdge> newFtnu = contingent2guarded(graphToAdjust);

		if (newFtnu != null) {
			final TNGraphMLWriter ftnuWriter = new TNGraphMLWriter(null);
			final String fileName = getNewFileName(file.getName());
			final File outputFile = new File(fileName);
			try {
				ftnuWriter.save(newFtnu, outputFile);
			} catch (IOException e) {
				System.err.println("It is not possible to save the result. File " + outputFile + " cannot be created: "
				                   + e.getMessage() + ". Computation continues.");
			}
			runState.printProgress();
			return true;
		}
		System.out.println("It was not possible to densify " + file.getName() + ".");
		runState.printProgress();
		return false;
	}

	/**
	 * Given an instance, for each contingent link, determines lower and upper bound values and add them as ordinary
	 * constraint between the two nodes.
	 *
	 * @param instance input instance to modify
	 * @return the instance with each contingent link converted into a guarded one.
	 */
	@Nullable
	private TNGraph<CSTNUEdge> contingent2guarded(TNGraph<CSTNUEdge> instance) {
		if (instance == null) {
			return null;
		}
		final int nCtg = instance.getContingentNodeCount();
		if (LOG.isLoggable(Level.FINER)) {
			LOG.finer("Converting " + nCtg + " contingent links to guarded ones");
		}
		final CSTNU cstnu = new CSTNU(instance);
		cstnu.setContingentAlsoAsOrdinary(false);
		try {
			cstnu.initAndCheck();
		} catch (WellDefinitionException e) {
			throw new RuntimeException("Trovato errore durante costruzione CSTNU: " + e.getMessage());
		}

		final TNGraph<CSTNUEdge> newInstance = new TNGraph<>(cstnu.getG(), instance.getEdgeImplClass());
		int lowerBound, upperBound;
		boolean added;
		final Set<CSTNUEdge> alreadyChecked = new HashSet<>(100);
		for (final CSTNUEdge e : newInstance.getEdges()) {
			final Edge.ConstraintType edgeType = e.getConstraintType();
			if (edgeType == Edge.ConstraintType.internal || edgeType == Edge.ConstraintType.derived) {
				//this edge is not necessary
				newInstance.removeEdge(e.getName());
				continue;
			}
			if (!e.isContingentEdge() || alreadyChecked.contains(e)) {
				continue;
			}
			final LabeledNode s = newInstance.getSource(e);
			final LabeledNode d = newInstance.getDest(e);
			assert s != null;
			assert d != null;
			final Label label = s.getLabel().conjunction(d.getLabel());
			final CSTNUEdge invertedE = newInstance.findEdge(d, s);
			alreadyChecked.add(invertedE);
			if (d.isContingent()) {
				assert invertedE != null;
				upperBound = makeUpperBound(invertedE, d.getALabel());
				lowerBound = makeLowerBound(e);
				added = e.mergeLabeledValue(label, upperBound);
				if (!added) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("Upper bound " + upperBound + " not added to " + e);
					}
				}
				added = invertedE.mergeLabeledValue(label, lowerBound);
				if (!added) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("Lower bound " + lowerBound + " not added to " + invertedE);
					}
				}

			} else {
				if (!s.isContingent()) {
					throw new IllegalStateException(
						"For contingent link " + e + " no one of its end points is contingent.");
				}
				upperBound = makeUpperBound(e, s.getALabel());
				assert invertedE != null;
				lowerBound = makeLowerBound(invertedE);
				added = e.mergeLabeledValue(label, lowerBound);
				if (!added) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("Lower bound " + upperBound + " not added to " + e);
					}
				}
				added = invertedE.mergeLabeledValue(label, upperBound);
				if (!added) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("Upper bound " + lowerBound + " not added to " + invertedE);
					}
				}
			}
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("New contingent link pair:\n\t" + e + " and\n\t" + invertedE);
			}
		}
		return newInstance;
	}

	/**
	 * @param fileName the input file name.
	 * @return new file name con the right suffixes.
	 */
	private String getNewFileName(String fileName) {
		if (fileName == null || !fileName.contains(".cstnu")) {
			throw new IllegalArgumentException("File name " + fileName + " is not a CSTNU file name.");
		}
		return fileName.replace(".cstnu", suffix);
	}

	/**
	 * @param e      an edge containing an upper case value
	 * @param aLabel a-label of the contingent node
	 * @return the upper bound determined using {@link #incrementFactor}*upper case value of e
	 */
	private int makeUpperBound(CSTNUEdge e, ALabel aLabel) {
		//a contingent link has a single upper case value, e.getMinUpperCaseValue() returns it without need to know
		return (int) (-e.getUpperCaseValueMap().get(aLabel).getMinValue() * incrementFactor);
	}

	/**
	 * @param e an edge containing a lower case value
	 * @return the lower bound (already negative value) determined using {@link #decrementFactor}*lower case value of e
	 */
	private int makeLowerBound(CSTNUEdge e) {
		//a contingent link has a single upper case value, e.getMinUpperCaseValue() returns it without need to
		//know
		return (int) (-e.getLowerCaseValue().getValue() * decrementFactor);
	}

	/**
	 * It cannot be used outside.
	 */
	private CSTNU2CSTNPSU() {
	}

	/**
	 * Print version of this class in System.out.
	 */
	public void printVersion() {
		// I use a non-static method for having a general method that prints the right name for each derived class.
		System.out.println(getClass().getName() + " " + CSTNU2CSTNPSU.VERSIONandDATE
		                   + ".\nAcademic and non-commercial use only.\n" + "Copyright © 2020, Roberto Posenato");
	}
}
