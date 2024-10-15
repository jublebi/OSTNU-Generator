// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

package it.univr.di.cstnu.util;

import com.google.common.io.Files;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.univr.di.cstnu.algorithms.STNU;
import it.univr.di.cstnu.algorithms.STNU.CheckAlgorithm;
import it.univr.di.cstnu.algorithms.STNU.STNUCheckStatus;
import it.univr.di.cstnu.graph.*;
import it.univr.di.cstnu.graph.Edge.ConstraintType;
import it.univr.di.labeledvalue.Constants;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Reads STNU instances and makes them more dense adding up to d (n (n-1)) edges, where n is the number of nodes and d the
 * density. It is also possible to fix the exact number of final edges. It adds negative redundant edges.<br>
 * <p>
 * For now, it works only on instance built using the swimming-pool lane pattern.
 *
 * @author posenato
 * @version $Rev: 733 $
 */
@SuppressWarnings("ClassWithOnlyPrivateConstructors")
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "STCAL", justification = "It is not relevant here!")
public class STNUDensifier {

	/**
	 * Version of the class
	 */
	// static public final String VERSIONandDATE = "Version 1.0 - September, 21 2020";
	static public final String VERSIONandDATE = "Version 2.0 - May, 20 2022";

	/*
	 * CSV separator
	 */
//	static final String CSVSep = ";\t";
	/**
	 * Default instance name suffix for contingent node reduction
	 */
	static final String CTG_FILE_NAME_SUFFIX = "SQRT_CTG";
	/**
	 * Default instance name suffix for dense transformation
	 */
	static final String DENSE_FILE_NAME_SUFFIX = "DENSE";
	/**
	 * Default hops
	 */
	static final int HOPS = 5;
	/**
	 * logger
	 */
	static final Logger LOG = Logger.getLogger(STNUDensifier.class.getName());

	/*
	 * Checker
	 */
//	static final Class<STNU> STNU_CLASS = it.univr.di.cstnu.algorithms.STNU.class;
	/**
	 * Maximum checks for a network
	 */
	static final int MAX_CHECKS = 50;
	/**
	 * Weight for a redundant edge. The edges are built adding the weights of the followed path to the destination
	 * making #HOPS. So, if it 0, no more restrictive constraint is added.
	 */
	static final int WEIGHT = 0;
	private static final Pattern NAME_PATTERN = Pattern.compile("[a-zA-Z,_]*");
	/**
	 * Date formatter
	 */
	private final static SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
	/**
	 * Class for representing edge .
	 */
	Class<? extends Edge> currentEdgeImplClass;
	/**
	 * Density required
	 */
	@Option(depends = "--makeDense", name = "-d", usage = "The density of the network. The network is made dense adding edges to it preserving its DC property till a density index is satisfied, i.e., #edges > d*(n(n-1)).")
	private double density = -1;// -1 means it has not been set by input.
	/**
	 * Edge weight required. The edges are built adding the weights of the followed path to the destination making
	 * #HOPS. So, if it is 0, no more restrictive constraint is added.
	 */
	@Option(name = "-w", usage =
		"The initial weight for the added edges. The edges are built adding the weights of the followed path to the destination making #HOPS hops.  \n" +
		"So, if this parameter is 0, no more restrictive constraint is added.")
	private int defaultWeight = STNUDensifier.WEIGHT;
	/**
	 * The input file names. Each file has to contain a STNU graph in GraphML format.
	 */
	@Argument(required = true, usage = "Input files. Each input file has to be a DC STNU graph in GraphML format. The DC property is fundamental!", metaVar = "STNU_file_names", handler = StringArrayOptionHandler.class)
	private String[] inputFiles;
	/**
	 *
	 */
	private List<File> instances;
	/**
	 * Dense request
	 */
	@Option(name = "--makeDense", usage = "Make the instance dense, i.e., #edges= d(n (n-1)), where n is the number of nodes.")
	private boolean makeDense;
	/**
	 * The number of edge required
	 */
	@Option(depends = "--makeDense", forbids = "-d", name = "-e", usage = "The number of edge required. The network is modified adding/removing edges to it preserving its DC property till the number of edges is == this parameter.")
	private int edgeRequired = -1;// -1 means it has not been set by input.
	/**
	 * Dense request
	 */
	@Option(name = "--reduceCtg", usage = "Reduce the number of contingents to n^(0.5), where n is the number of nodes.")
	private boolean reduceCtg;
	/**
	 *
	 */
	@Option(name = "--alsoNotDC", usage = "Transform the input instance and find a not DC version decreasing edge values.")
	private boolean alsoNotDCInstances;
	/**
	 * Roberto: I verified that with such kind of computation, using more than one thread to check more files in
	 * parallel reduces the single performance!!!
	 */
	@Option(name = "--nCPUs", usage = "Number of virtual CPUs that are reserved for this execution. Default is 0=no CPU reserved, there is only one thread for all the DC checking executions: such thread can be allocated to a core, then desallocated and reallocated to another core. With nCPUs=1, there is only thread but such thread is allocated to a core till its end. With more thread, the global performance increases, but each file can require more time because there is a competition among threads to access to the memory.")
	private int nCPUs;
	/**
	 * Timeout in seconds for the check.
	 */
	@Option(name = "-t", aliases = "--timeOut", usage = "Timeout in seconds for the check", metaVar = "seconds")
	private int timeOut = 60 * 15;
	/**
	 * Local temporary network
	 */
	private final File tmpNetwork;
	/**
	 * Software Version.
	 */
	@Option(name = "-v", aliases = "--version", usage = "Version")
	private boolean versionReq;
	/**
	 * weight adjustment. This value is determined in the constructor.
	 */
	@Option(name = "-weightAdj", usage = "Weight adjustment factor during the searching of DCor NOT DC instances")
	private int weightAdjustment = 1;

	/**
	 * Adds 'nEdgesToAdd' redundant edges having source 'node'. For determining the destination node, it makes 'hop'
	 * hops. If all the edges cannot be added because already present, or it is not possible making them after 'hop'
	 * hops, it tries to add the remaining making fewer hops.
	 *
	 * @param source      node
	 * @param dest        node (used by the method for making a recursive search. In the main call, pass null).
	 * @param weight      weight new edge
	 * @param nEdgesToAdd if <=0, the procedure returns 0.
	 * @param hop         # of hops. If 0, it tries to add an edge between source and dest.
	 * @param newInstance graph where to add
	 * @param addedEdges  added edges
	 *
	 * @return the number of edges added if it was possible. If there is no possibility to add edges because there not
	 * 	|paths| = hop, returns {@link Constants#INT_NULL}.
	 */
	private static int addRedundantNewEdges(final LabeledNode source, LabeledNode dest, final int weight, final int hop,
	                                        final int nEdgesToAdd, TNGraph<STNUEdge> newInstance,
	                                        ObjectList<STNUEdge> addedEdges) {
		if (nEdgesToAdd <= 0) {
			return 0;
		}
		if (hop == 0) {
			final STNUEdge e = newInstance.findEdge(source, dest);
			if (e == null && source != dest) {
				final STNUEdge newEdge = newInstance.getEdgeFactory().get(source.getName() + "-" + dest.getName());
				newEdge.setValue(weight + 1);
				newInstance.addEdge(newEdge, source, dest);
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Added edge " + newEdge);
				}
				addedEdges.add(newEdge);
				return 1;
			}
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Cannot add an edge because it is already present: " + e);
			}
			return 0;
		}
		// I have to make a hop
		int added = Constants.INT_NULL;
		int w;
		if (dest == null) {
			dest = source;
		}
		if (newInstance.outDegree(dest) == 0) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Node " + dest.getName() + " has no neighbours.");
			}
			return added;
		}
		int rAdd;
		for (final STNUEdge edge : newInstance.getOutEdges(dest)) {
			if (added != Constants.INT_NULL && nEdgesToAdd - added <= 0) {
				LOG.finer("No more edges are necessary!");
				break;
			}
			w = edge.getValue();
			if (w == Constants.INT_NULL) {
				continue;// contingent link
			}
			final LabeledNode nextNode = newInstance.getDest(edge);
			final int stillRequiredEdges = (added == Constants.INT_NULL) ? nEdgesToAdd : nEdgesToAdd - added;
			rAdd = addRedundantNewEdges(source, nextNode, weight + w, hop - 1, stillRequiredEdges, newInstance,
			                            addedEdges);
			if (rAdd != Constants.INT_NULL) {
				if (added == Constants.INT_NULL) {
					added = rAdd;
				} else {
					added += rAdd;
				}
			}
		}
		if (added > 0) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Added " + added + " edges. Return.");
			}
			return added;
		}
		// since it was no possible to add at desired hop, I try to add here
		if (source != dest) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("No edges were added. Try to add with present node " + dest);
			}
			if (newInstance.findEdge(source, dest) == null) {
				final STNUEdge newEdge = newInstance.getEdgeFactory().get(source.getName() + "-" + dest.getName());
				newEdge.setValue(weight + 10);
				newInstance.addEdge(newEdge, source, dest);
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Added edge " + newEdge);
				}
				addedEdges.add(newEdge);
				return 1;
			}
		}
		return added;
	}

	/**
	 * @return current time in {@link #dateFormatter} format
	 */
	private static String getNow() {
		return dateFormatter.format(new Date());
	}

	/**
	 * Allows the modification of a set of input instances.
	 *
	 * @param args an array of {@link String} objects.
	 */
	@SuppressWarnings("null")
	public static void main(final String[] args) {

		LOG.finest("STNUDensifier " + VERSIONandDATE + "\nStart...");
		System.out.println(
			"Checker " + VERSIONandDATE + "\n" + "\nSPDX-License-Identifier: LGPL-3.0-or-later, Roberto Posenato.\n" +
			getNow() + ": Start of execution.");
		final STNUDensifier densifier = new STNUDensifier();

		if (!densifier.manageParameters(args)) {
			return;
		}
		LOG.finest("Parameters ok!");
		if (densifier.versionReq) {
			return;
		}
		// All parameters are set

		/*
		 * <a id="affinity">AffinityLock allows to lock a CPU to a thread.</a>
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
		final int nCPUs = densifier.nCPUs;// Runtime.getRuntime().availableProcessors();

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
		final ExecutorService stnuDensifierExecutor = (nCPUs > 0) ? Executors.newFixedThreadPool(nCPUs,
		                                                                                         new AffinityThreadFactory(
			                                                                                         "cstnWorker",
			                                                                                         AffinityStrategies.DIFFERENT_CORE))
		                                                          : null;

		System.out.println(getNow() + ": #Processors for computation: " + nCPUs);
		System.out.println(getNow() + ": Instances to check are STNU instances.");
		final RunMeter runMeter = new RunMeter(System.currentTimeMillis(), densifier.instances.size(), 0);
		runMeter.printProgress(0);

		final List<Future<Boolean>> future = new ArrayList<>(10);

		int nTaskSuccessfullyFinished = 0;
		for (final File file : densifier.instances) {
			if (nCPUs > 0) {
				future.add(stnuDensifierExecutor.submit(() -> densifier.worker(file, runMeter)));
			} else {
				if (densifier.worker(file, runMeter)) {
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
		final String msg = "Number of instances processed successfully over total: " + nTaskSuccessfullyFinished + "/" +
		                   densifier.instances.size() + ".";
		LOG.info(msg);
		System.out.println("\n" + getNow() + ": " + msg);

		if (nCPUs > 0) {
			// executor shutdown!
			try {
				System.out.println(getNow() + ": Shutdown executors.");
				stnuDensifierExecutor.shutdown();
				if (!stnuDensifierExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
					System.err.println(getNow() + ": Tasks is very long to showdown...");
				}
			} catch (final InterruptedException e) {
				System.err.println(getNow() + ": Tasks interrupted.");
			} finally {
				if (!stnuDensifierExecutor.isTerminated()) {
					System.err.println(getNow() + ": Cancel non-finished tasks.");
				}
				stnuDensifierExecutor.shutdownNow();
				System.out.println(getNow() + ": Shutdown finished.\nExecution finished.");
			}
		}
	}

	/**
	 * Assuming than node name can be N+int, n_+int, A+int, C+int, it returns the int
	 *
	 * @param nodeName the name of node
	 *
	 * @return the int in the name
	 */
	private static int nodeIndex(String nodeName) {
		if (nodeName == null) {
			return 0;
		}
		nodeName = NAME_PATTERN.matcher(nodeName).replaceAll("");
		return Integer.parseInt(nodeName);
	}

	/**
	 * Given an instance, randomly reduce its contingent links to be, in total, sqrt(n), where n is the number of
	 * nodes.
	 *
	 * @param instance input instance to modify
	 *
	 * @return the instance with sqrt(n) contingent links at maximum.
	 */
	@Nullable
	private static TNGraph<STNUEdge> reduceCtg(TNGraph<STNUEdge> instance) {
		if (instance == null) {
			return null;
		}
		final int nNodes = instance.getVertexCount();
		int nCtg = instance.getContingentNodeCount();
		final int nCtgRequired = (int) (Math.sqrt(nNodes));
		if (LOG.isLoggable(Level.FINER)) {
			LOG.finer("Reducing the number of contingent links from " + nCtg + " to " + nCtgRequired);
		}

		final TNGraph<STNUEdge> newInstance = new TNGraph<>(instance, instance.getEdgeImplClass());
		if ((nCtg - nCtgRequired) <= 0) {
			return newInstance;
		}

		int nodeC = nNodes;
		for (final STNUEdge e : newInstance.getEdges()) {
			if (e.isContingentEdge()) {
				final LabeledNode s = newInstance.getSource(e);
				final LabeledNode d = newInstance.getDest(e);
				// make sure that the two endpoints have contingent property false.
				assert d != null;
				d.setALabel(null);
				d.setContingent(false);
				assert s != null;
				s.setALabel(null);
				s.setContingent(false);
				final STNUEdge invertedE = newInstance.findEdge(d, s);
				// make links normal
				e.setConstraintType(ConstraintType.requirement);
				assert invertedE != null;
				invertedE.setConstraintType(ConstraintType.requirement);
				s.setName("n_" + (nodeC++));
				d.setName("n_" + (nodeC++));
				final LabeledNode[] list = {s, d};
				for (final LabeledNode node : list) {
					for (final STNUEdge e1 : newInstance.getInEdges(node)) {
						final LabeledNode source = newInstance.getSource(e1);
						assert source != null;
						e1.setName("e" + source.getName() + "-" + node.getName());
					}
					for (final STNUEdge e1 : newInstance.getOutEdges(node)) {
						final LabeledNode dest = newInstance.getSource(e1);
						assert dest != null;
						e1.setName("e" + node.getName() + "-" + dest.getName());
					}
				}
				nCtg--;
				if ((nCtg - nCtgRequired) == 0) {
					break;
				}
			}
		}
		return newInstance;
	}

	/**
	 * Remove 'edgesForNode' redundant edges (== positive weight) having source 'node'. The edges are the one that
	 * connect nodes in other lanes. This method works assuming that the instance is modeled as a swimming-pool lanes
	 * pattern.
	 *
	 * @param node                 source node
	 * @param edgesForNodeToRemove if <=0, the procedure returns 0.
	 * @param newInstance          graph where to remove
	 *
	 * @return the number of edges removed if it was possible. If there is no possibility to remove edges, returns
	 *    {@link Constants#INT_NULL}.
	 */
	private static int removeRedundantEdges(LabeledNode node, int edgesForNodeToRemove, TNGraph<STNUEdge> newInstance) {
		if (node == null) {
			return 0;
		}
		final String nodeName = node.getName();
		if (edgesForNodeToRemove <= 0 || !nodeName.isEmpty() && nodeName.charAt(0) == 'A' ||
		    !nodeName.isEmpty() && nodeName.charAt(0) == 'C') {
			// we not remove edges from activation or contingent nodes
			return 0;
		}
		if (newInstance.outDegree(node) == 0) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Node " + node.getName() + " has no neighbours.");
			}
			return Constants.INT_NULL;
		}
		final int nodeIndex = nodeIndex(nodeName);
		int removed = 0;
		for (final STNUEdge edge : newInstance.getOutEdges(node)) {
			if (edgesForNodeToRemove - removed <= 0) {
				LOG.finer("No more removing action are necessary!");
				break;
			}
			final int w = edge.getValue();
			if (w <= 0) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Edge is negative, ignore. Details: " + edge);
				}
				continue;
			}
			final LabeledNode destNode = newInstance.getDest(edge);
			assert destNode != null;
			final int destIndex = nodeIndex(destNode.getName());
			if (Math.abs(nodeIndex - destIndex) <= 1) {
				continue;// don't remove precedence edges
			}

			newInstance.removeEdge(edge);
			removed++;
		}
		return (removed == 0) ? Constants.INT_NULL : removed;
	}

	/**
	 * It cannot be used outside.
	 */
	private STNUDensifier() {
		try {
			tmpNetwork = File.createTempFile("currentNetwork", "xml");
		} catch (final IOException e) {
			LOG.severe(e.getMessage());
			throw new IllegalStateException(e.toString());
		}
	}

	/**
	 * Print version of this class in System.out.
	 */
	public void printVersion() {
		// I use a non-static method for having a general method that prints the right name for each derived class.
		System.out.println(
			getClass().getName() + " " + STNUDensifier.VERSIONandDATE + ".\nAcademic and non-commercial use only.\n" +
			"Copyright © 2020, Roberto Posenato");
	}

	/**
	 * Adjusts the weight of all edges (increase/decrease) considering {@link #weightAdjustment}.
	 *
	 * @param addedEdges addedEdge to modify
	 * @param increase   true if the weight must be increased.
	 */
	private void adjustEdgeWeights(ObjectList<STNUEdge> addedEdges, boolean increase) {
		/*
		 * Don't implement the following actions:
		 * 1. Limit the adjustment to <= this.maxWeight because, otherwise, many values are squeezed to this.maxWeight.
		 * 2. Adjust only positive values when increase and negative value when !increase because it never reaches a DC instance.
		 */
		final int sign = (increase) ? +1 : -1;
		for (final STNUEdge e : addedEdges) {
			final int oldV = e.getValue();
			int adjustment = sign * weightAdjustment;
			if (increase && oldV < 0) {
				adjustment /= 2;
			}

			e.setValue(oldV + adjustment);
		}
	}

	/**
	 * @param fileName the input file name.
	 *
	 * @return new file name con the right suffixes.
	 */
	private String getNewFileName(String fileName) {
		final StringBuilder sb = new StringBuilder(fileName.replace(".stnu", ""));
		if (reduceCtg) {
			sb.append("_");
			sb.append(STNUDensifier.CTG_FILE_NAME_SUFFIX);
		}
		if (makeDense) {
			sb.append("_");
			if (edgeRequired != -1) {
				sb.append("_").append(edgeRequired).append("edges");
			} else {
				sb.append(STNUDensifier.DENSE_FILE_NAME_SUFFIX);
			}
		}
		sb.append(".stnu");
		return sb.toString();
	}

	/**
	 * Given an instance, determines a dense version of it adding/removing redundant edges according to the input
	 * parameter -e/-d.
	 *
	 * @param instance   input instance to make dense
	 * @param addedEdges list containing the added edges
	 *
	 * @return the dense version
	 */
	@Nullable
	private TNGraph<STNUEdge> makeDenseInstance(TNGraph<STNUEdge> instance, ObjectList<STNUEdge> addedEdges) {
		if (instance == null) {
			return null;
		}
		final int nNodes = instance.getVertexCount();
		// int log2OfN = 32 - Integer.numberOfLeadingZeros(nNodes);
		// int nEdgesRequired = (int) ((nNodes * log2OfN) * this.density);
		final int nEdgesRequired = (edgeRequired != -1) ? edgeRequired : (int) (nNodes * (nNodes - 1) * density);

		// LOG.finer("Making a dense instance. Number of nodes: " + nNodes + ". log_2 n: " + log2OfN + ". Required density: " + this.density + ". Required
		// edges: " + nEdgesRequired);
		if (STNUDensifier.LOG.isLoggable(Level.FINER)) {
			STNUDensifier.LOG.finer(
				"Making a dense instance. Number of nodes: " + nNodes + ". (n*(n-1)) " + (nNodes * (nNodes - 1)) +
				". Required density: " + density + ". Required #edges: " + edgeRequired +
				". Final value for #edgeRequired: " + nEdgesRequired);
		}

		final TNGraph<STNUEdge> newInstance = new TNGraph<>(instance, instance.getEdgeImplClass());
		final int nCurrentEdges = newInstance.getEdgeCount();
		int missingEdges = nEdgesRequired - nCurrentEdges;
		STNUDensifier.LOG.info("Current #edges: " + nCurrentEdges + ". Missing edges: " + missingEdges);

		if (missingEdges == 0) {
			return instance;
		}
		if (missingEdges < 0) {
			STNUDensifier.LOG.info("In the new instance there will be " + (-missingEdges) + " edges less.");
		}

		int nNodeWithOutgoingEdges = 0;
		for (final LabeledNode node : newInstance.getVertices()) {
			if (newInstance.outDegree(node) > 0) {
				nNodeWithOutgoingEdges++;
			}
		}
		int edgesForNode = missingEdges / nNodeWithOutgoingEdges;
		if (missingEdges > 0) {
			int hop = STNUDensifier.HOPS;
			int added;
			if (STNUDensifier.LOG.isLoggable(Level.FINER)) {
				STNUDensifier.LOG.finer("#edges for node: " + edgesForNode);
			}
			while (missingEdges > 0) {
				boolean stillPossibleToAdd = false;
				for (final LabeledNode node : newInstance.getVertices()) {
					added = addRedundantNewEdges(node, null, defaultWeight, hop, edgesForNode, newInstance, addedEdges);
					if (added != Constants.INT_NULL) {
						missingEdges -= added;
						stillPossibleToAdd = true;
						if (STNUDensifier.LOG.isLoggable(Level.FINER)) {
							STNUDensifier.LOG.finer("Added #edges: " + added + ". Missing edges: " + missingEdges);
						}
					}
					if (missingEdges <= 0) {
						break;
					}
				}
				if (!stillPossibleToAdd) {
					STNUDensifier.LOG.warning("It is not possible to add more edges! Giving up!");
					break;
				}
				hop++;
			}
			return newInstance;
		}
		// here we have to remove edges
		while (missingEdges < 0) {
			edgesForNode = -edgesForNode;
			boolean stillPossibleToRemove = false;
			int removed;
			for (final LabeledNode node : newInstance.getVertices()) {
				removed = STNUDensifier.removeRedundantEdges(node, edgesForNode, newInstance);
				if (removed != Constants.INT_NULL) {
					missingEdges += removed;
					stillPossibleToRemove = true;
					if (STNUDensifier.LOG.isLoggable(Level.FINER)) {
						STNUDensifier.LOG.finer("Removed #edges: " + removed + ". Surplus #edges: " + missingEdges);
					}
				}
				if (missingEdges >= 0) {
					break;
				}
			}
			if (!stillPossibleToRemove) {
				STNUDensifier.LOG.warning("It is not possible to remove more edges! Giving up!");
				break;
			}
		}

		return newInstance;

	}

	/**
	 * Builds a pair of DC and not DC of the given STNU instance according to the input parameters. The not DC instance
	 * is build adding one or more constraints to the previous generated DC instance.
	 *
	 * @param g the input DC instance
	 *
	 * @return a pair of DC and not DC of CSTN instances. If the first member is null, it means that a generic error in
	 * 	the building has occurred. If alsoNotDcInstance is false, the returned not DC instance is null.
	 */
	@Nullable
	private ObjectPair<TNGraph<STNUEdge>> makePairSTNUInstances(TNGraph<STNUEdge> g) {

		STNUDensifier.LOG.info("Start transforming the instance.");
		TNGraph<STNUEdge> workingGraph = null;
		TNGraph<STNUEdge> notDCNewGraph = null;

		final ObjectList<STNUEdge> addedEdges = new ObjectArrayList<>();

		if (reduceCtg) {
			workingGraph = STNUDensifier.reduceCtg(g);
		}

		if (makeDense) {
			workingGraph = makeDenseInstance((workingGraph != null) ? workingGraph : g, addedEdges);
		}

		TNGraph<STNUEdge> lastDC = workingGraph;

		final TNGraphMLWriter cstnWriter = new TNGraphMLWriter(null);

		int checkN = 0;// number of checks
		boolean nonDCfound = false, DCfound = false;
		final STNU stnu = new STNU(workingGraph, timeOut);
		stnu.setDefaultControllabilityCheckAlg(CheckAlgorithm.RUL2021);

		STNUCheckStatus status = new STNUCheckStatus();

		while (true) {
			if (checkN > 0) {
				stnu.reset();
				stnu.setG(new TNGraph<>(workingGraph, EdgeSupplier.DEFAULT_STNU_EDGE_CLASS));
			}

			if (STNUDensifier.LOG.isLoggable(Level.FINER)) {
				try {
					cstnWriter.save(stnu.getG(), tmpNetwork);
				} catch (IOException e) {
					System.err.println(
						"It is not possible to save the result. File " + tmpNetwork + " cannot be created: " +
						e.getMessage() + "\n Computation  continues.");
				}
				STNUDensifier.LOG.finer("Current cstn saved as 'current.stnu' before checking.");
			}

			try {
				STNUDensifier.LOG.fine("DC Check started.");
				if (addedEdges.size() == 0 || !alsoNotDCInstances || defaultWeight >= 0) {
					// it is not necessary to check the consistency because it is assumed that the input instance is DC.
					status.consistency = true;
					status.finished = true;
					status.timeout = false;
				} else {
					status = stnu.dynamicControllabilityCheck();
				}
				checkN++;
				STNUDensifier.LOG.fine("DC Check finished.");
			} catch (Exception ex) {
				LOG.severe(ex.getMessage());
				throw new IllegalStateException(ex.toString());
			}

			if (status.timeout) {
				final String fileName = "timeOut%d.stnu".formatted(System.currentTimeMillis());
				if (STNUDensifier.LOG.isLoggable(Level.FINER)) {
					STNUDensifier.LOG.finer("DC Check finished for timeout. Instance is saved as " + fileName + ".");
				}
				final File d = new File(fileName);
				try {
					Files.move(tmpNetwork, d);
				} catch (IOException ex) {
					STNUDensifier.LOG.finer(
						"Problem to save 'current.cstn' as time out instance. Program continues anyway.");
				}
				return null;
			}

			if (status.isControllable()) {
				STNUDensifier.LOG.finer("Random instance is DC.");
				assert workingGraph != null;
				lastDC = new TNGraph<>(workingGraph, EdgeSupplier.DEFAULT_STNU_EDGE_CLASS);
				DCfound = true;
				if (!nonDCfound && alsoNotDCInstances) {
					if (STNUDensifier.LOG.isLoggable(Level.FINER)) {
						STNUDensifier.LOG.finer("Now, a not DC instance must be generated. Tentative #" + checkN);
					}
					// we lower the edge value
					adjustEdgeWeights(addedEdges, false);
				} else {
					if (STNUDensifier.LOG.isLoggable(Level.FINER)) {
						STNUDensifier.LOG.finer("The pair has been found after " + checkN + " iterations.");
					}
					return new ObjectPair<>(lastDC, notDCNewGraph);
				}
			} else {
				STNUDensifier.LOG.finer("Random instance is not DC.");
				assert workingGraph != null;
				notDCNewGraph = new TNGraph<>(workingGraph, EdgeSupplier.DEFAULT_STNU_EDGE_CLASS);
				nonDCfound = true;
				if (!DCfound) {
					if (STNUDensifier.LOG.isLoggable(Level.FINER)) {
						STNUDensifier.LOG.finer("Now, a DC instance must be generated. Tentative #" + checkN);
					}
					adjustEdgeWeights(addedEdges, true);
				} else {
					if (STNUDensifier.LOG.isLoggable(Level.FINER)) {
						STNUDensifier.LOG.finer("The pair has been found after " + checkN + " iterations.");
					}
					return new ObjectPair<>(lastDC, notDCNewGraph);
				}
			}

			if (checkN > STNUDensifier.MAX_CHECKS) {
				STNUDensifier.LOG.finer("This network was checked more than " + STNUDensifier.MAX_CHECKS +
				                        " times without finding the wanted pair. Program continues witho another network.");
				return null;
			}
		}
	}

	/**
	 * Simple method to manage command line parameters using {@code args4j} library.
	 *
	 * @param args input args
	 *
	 * @return false if a parameter is missing, or it is wrong. True if every parameter is given in a right format.
	 */
	@SuppressWarnings("FloatingPointEquality")
	private boolean manageParameters(String[] args) {
		final CmdLineParser parser = new CmdLineParser(this);
		try {
			parser.parseArgument(args);
		} catch (CmdLineException e) {
			// if there's a problem in the command line, you'll get this exception. this will report an error message.
			System.err.println(e.getMessage());
			System.err.println(
				"java -cp CSTNU-<version>.jar -cp it.univr.di.cstnu.STNUDensifier [options...] arguments...");
			// print the list of available options
			parser.printUsage(System.err);
			System.err.println();

			// print option sample. This is useful some time
			// System.err.println("Example: java -jar Checker.jar" + parser.printExample(OptionHandlerFilter.REQUIRED) +
			// " <CSTN_file_name0> <CSTN_file_name1>...");
			return false;
		}
		if (versionReq) {
			System.out.print(getClass().getName() + " " + STNUDensifier.VERSIONandDATE +
			                 ". Academic and non-commercial use only.\n" + "Copyright © 2020-2022, Roberto Posenato");
			return true;
		}
		if (!makeDense && !reduceCtg) {
			throw new IllegalArgumentException("No modification required. So, nothing has to be done.");
		}
		if (!makeDense) {
			alsoNotDCInstances = false;// FIXME It is not managed.
		}

		if (density == -1 && edgeRequired == -1) {
			throw new IllegalArgumentException(
				"-d or -e is mandatory. The density must be in [0, 1]. The number of edge must be positive.");
		}

		if ((density < 0 || density > 1) && edgeRequired == -1) {
			throw new IllegalArgumentException("-d or -e is mandatory. The density must be in [0, 1].");
		}
		if (edgeRequired < 0 && density == -1) {
			throw new IllegalArgumentException("-d or -e is mandatory. The number of edge must be positive.");
		}

		final String suffix;
		currentEdgeImplClass = STNUEdgeInt.class;
		suffix = "stnu";

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
				System.err.println("File " + fileName +
				                   " has not the right suffix associated to the suffix of the given network type (right suffix: " +
				                   suffix + "). Game over :-/");
				parser.printUsage(System.err);
				System.err.println();
				return false;
			}
			instances.add(file);
		}

		return true;
	}

	/**
	 * @param file     input file
	 * @param runState current state
	 *
	 * @return true if required task ends successfully, false otherwise.
	 */
	@SuppressWarnings("unchecked")
	private boolean worker(File file, RunMeter runState) {
		// System.out.println("Analyzing file " + file.getName() + "...");
		if (STNUDensifier.LOG.isLoggable(Level.FINER)) {
			STNUDensifier.LOG.finer("Loading " + file.getName() + "...");
		}
		final TNGraphMLReader<STNUEdge> graphMLReader = new TNGraphMLReader<>();
		final TNGraph<STNUEdge> graphToAdjust;
		try {
			graphToAdjust = graphMLReader.readGraph(file, (Class<STNUEdge>) currentEdgeImplClass);
		} catch (IOException | ParserConfigurationException | SAXException e2) {
			final String msg =
				"File " + file.getName() + " cannot be parsed. Details: " + e2.getMessage() + ".\nIgnored.";
			STNUDensifier.LOG.warning(msg);
			System.out.println(msg);
			return false;
		}
		STNUDensifier.LOG.finer("...done!");

		final ObjectPair<TNGraph<STNUEdge>> pair = makePairSTNUInstances(graphToAdjust);
		final TNGraphMLWriter stnuWriter = new TNGraphMLWriter(null);

		if (pair != null) {
			// save the two instances.
			String fileName = getNewFileName(file.getName());
			File outputFile = new File(fileName);

			try {
				stnuWriter.save(pair.getFirst(), outputFile);
			} catch (IOException e) {
				System.err.println(
					"It is not possible to save the result. File " + outputFile + " cannot be created: " +
					e.getMessage() + ". Computation continues.");
			}

			if (pair.getSecond() != null) {
				fileName = "NOT" + fileName;
				outputFile = new File(fileName);

				try {
					stnuWriter.save(pair.getSecond(), outputFile);
				} catch (IOException e) {
					System.err.println(
						"It is not possible to save the result. File " + outputFile + " cannot be created: " +
						e.getMessage() + ". Computation continues.");
				}
				System.out.println("NOT DC instance " + fileName + " saved.");
			}
			runState.printProgress();
			return true;
		}
		System.out.println("It was not possible to densify " + file.getName() + ".");
		runState.printProgress();
		return false;
	}

}
