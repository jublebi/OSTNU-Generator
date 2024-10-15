package it.univr.di.cstnu.algorithms;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import it.unimi.dsi.fastutil.objects.*;
import it.univr.di.Debug;
import it.univr.di.cstnu.graph.*;
import it.univr.di.cstnu.util.ActiveWaits;
import it.univr.di.cstnu.util.ExtendedPriorityQueue;
import it.univr.di.cstnu.util.TimeInterval;
import it.univr.di.labeledvalue.Constants;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.jheaps.AddressableHeap;
import org.kohsuke.args4j.*;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A Real-Time Execution (RTE) algorithm is a scheduling algorithm that preserves maximum flexibility while requiring minimal computation.
 * <p>
 * This class provides an RTE algorithm for dispatchable STNUs.<br>
 * The algorithm was proposed in the article 'Foundations of Dispatchability for Simple Temporal
 * Networks with Uncertainty', by Luke Hunsberger, Roberto Posenato, ICAART 2024
 * </p>
 * <b>Assumptions at 2024-01-30</b><br>
 *     <ol>
 *         <li>Only early execution strategy.</li>
 *         <li>Instantaneous reactions.</li>
 *     </ol>
 *
 * <b>IMPLEMENTATION CHOICES</b><br>
 * <ul>
 *  <li>Each object is associated to a graph. Graph cannot be changed.</li>
 *  <li>The constructor of an object prepares only the auxiliary data structure about the graph.</li>
 * 	<li>Each execution of method rte() prepares all the data structure for an execution and fills it.
 *  So, it is possible to invoke concurrent rte() without any problem.</li>
 * </ul>
 */
public class STNURTE {

	/**
	 * Strategy for choosing a node and an instant among a set of possible ones.
	 */
	@FunctionalInterface
	public interface NodeAndExecutionTimeChoice {
		/**
		 * @param candidates set of nodes with their time windows and the minimum common time window ([lowest lower bound, lowest upper bound]).
		 *
		 * @return a time instant and nodes that must be executed at time instant.
		 */
		@Nonnull
		NodeOccurrence choice(@Nonnull NodeEnabledInTimeInterval candidates);
	}

	/**
	 * Allows the specification of a predefined strategy as an input parameter.
	 *
	 * @author Léon Planken
	 */
	public enum StrategyEnum implements Strategy {
		/**
		 * Returns the minimum possible execution time, {@code l}, with all nodes that can be executed at that time, i.e., {@code l' ≤ l}.<br> It is assumed
		 * that {@code candidates.timeInterval = [l, u]} and each node has a time interval {@code [l',u']} such that {@code l ≤ l'} and {@code u ≤ u'}.
		 */
		EARLY_EXECUTION_STRATEGY(candidates -> {
			int exeTime = candidates.timeInterval.getLower();
			Set<LabeledNode> filteredNode =
				candidates.nodes.stream().filter((item) -> (item.timeInterval.getLower() <= exeTime)).map((item) -> item.node).collect(Collectors.toSet());
			return new NodeOccurrence(exeTime, filteredNode);
		}),

		/**
		 * Returns the first node among the candidate and the lower bound of its time window compatible with the given time window.
		 */
		FIRST_NODE_EARLY_EXECUTION_STRATEGY(candidates -> {
			LabeledNode first = null;
			TimeInterval firstTW = null;
			for (NodeWithTimeInterval entry : candidates.nodes()) {
				LabeledNode node = entry.node;
				TimeInterval tw = entry.timeInterval;
				if (tw.getUpper() <= candidates.timeInterval.getUpper()) {
					first = node;
					firstTW = tw;
					break;
				}
			}
			assert first != null;
			final ObjectSet<LabeledNode> singleSet = new ObjectArraySet<>(1);
			singleSet.add(first);
			return new NodeOccurrence(Math.max(firstTW.getLower(), candidates.timeInterval.getLower()), singleSet);
		}),
		/**
		 * Returns the first node among the candidate and the upper time of its time window compatible with the given time window.
		 */
		FIRST_NODE_LATE_EXECUTION_STRATEGY(candidates -> {
			LabeledNode first = null;
			TimeInterval firstTW = null;
			for (NodeWithTimeInterval entry : candidates.nodes()) {
				LabeledNode node = entry.node;
				TimeInterval tw = entry.timeInterval;
				if (tw.getUpper() <= candidates.timeInterval.getUpper()) {
					first = node;
					firstTW = tw;
					break;
				}
			}
			assert first != null;
			final ObjectSet<LabeledNode> singleSet = new ObjectArraySet<>(1);
			singleSet.add(first);
			final int upperBound = Math.min(firstTW.getUpper(), candidates.timeInterval.getUpper());
			return new NodeOccurrence(upperBound, singleSet);
		}),

		/**
		 * Returns the first node among the candidate and the middle time of its time window compatible with the given time window.
		 */
		FIRST_NODE_MIDDLE_EXECUTION_STRATEGY(candidates -> {
			LabeledNode first = null;
			TimeInterval firstTW = null;
			int minUpperBound = Constants.INT_POS_INFINITE;
			for (NodeWithTimeInterval entry : candidates.nodes()) {
				LabeledNode node = entry.node;
				TimeInterval tw = entry.timeInterval;
				if (tw.getUpper() <= candidates.timeInterval.getUpper()) {
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
				//select the first one and limit its upper bound
				//FIXME the limitation is naive. It is only to avoid +∞
				@SuppressWarnings("OptionalGetWithoutIsPresent")
				NodeWithTimeInterval entry = candidates.nodes.stream().findFirst().get();
				first = entry.node;
				firstTW = entry.timeInterval;
				firstTW.set(entry.timeInterval.getLower(), (entry.timeInterval.getLower() + 5) * 2);
				if (Debug.ON) {
					LOG.info("All candidates have upper bound = ∞. Selected the first node " + first + " with time window " + firstTW);
				}
			}
			final ObjectSet<LabeledNode> singleSet = new ObjectArraySet<>(1);
			singleSet.add(first);
			final int lowerBound = Math.max(firstTW.getLower(), candidates.timeInterval.getLower());
			final int upperBound = Math.min(firstTW.getUpper(), candidates.timeInterval.getUpper());
			int sum = Constants.sumWithOverflowCheck(lowerBound, upperBound);
			final int exeTime = (sum != Constants.INT_POS_INFINITE) ? sum / 2 : sum;
			if (exeTime == Constants.INT_POS_INFINITE) {
				LOG.severe("Execution time is ∞!");
			}
			return new NodeOccurrence(exeTime, singleSet);
		}),
		/**
		 * Returns the maximum possible execution time, {@code l}, with all nodes that have as last possible execution time, i.e., {@code u' == l}.
		 * <br>
		 * * It is assumed that {@code candidates.timeInterval = [l, u]} and each node has a time interval * {@code [l',u']} such that {@code l ≤ l'} and
		 * {@code u ≤ u'}.
		 */
		LATE_EXECUTION_STRATEGY(candidates -> {
			int exeTime = candidates.timeInterval.getUpper();
			Set<LabeledNode> filteredNode =
				candidates.nodes.stream().filter((item) -> (item.timeInterval.getUpper() == exeTime)).map((item) -> item.node).collect(Collectors.toSet());
			return new NodeOccurrence(exeTime, filteredNode);
		}),
		/**
		 * Returns the middle possible execution time, {@code m}, with all nodes that can be executed at that time, i.e., {@code l' ≤ m}.<br> It is assumed that
		 * {@code candidates.timeInterval = [l, u], m = (l+u)/2} and each node has a time interval {@code [l',u']} such that {@code l ≤ l'} and {@code u ≤ u'}.
		 */
		MIDDLE_EXECUTION_STRATEGY(candidates -> {
			int sum = Constants.sumWithOverflowCheck(candidates.timeInterval.getLower(), candidates.timeInterval.getUpper());
			final int exeTime = (sum != Constants.INT_POS_INFINITE) ? sum / 2 : sum;
			Set<LabeledNode> filteredNode =
				candidates.nodes.stream().filter((item) -> (item.timeInterval.getLower() <= exeTime)).map((item) -> item.node).collect(Collectors.toSet());
			return new NodeOccurrence(exeTime, filteredNode);
		}),
		/**
		 * Returns a random execution time, {@code r}, in the allowed interval {@code [l, u]} with all nodes that can be executed at that time.
		 * <br>
		 * * It is assumed that {@code candidates.timeInterval = [l, u]} and each node has a time interval * {@code [l',u']} such that {@code l ≤ l'} and
		 * {@code u ≤ u'}.
		 */
		@SuppressFBWarnings(value = "DMI_RANDOM_USED_ONLY_ONCE", justification = "It is not possible to extract rnd from here.")
		RANDOM_EXECUTION_STRATEGY(candidates -> {
			final Random rnd = new Random();
			//Constants.sumWithOverflowCheck because candidates.timeInterval.getUpper() can be ∞
			int exeTime = rnd.nextInt(candidates.timeInterval.getLower(), Constants.sumWithOverflowCheck(candidates.timeInterval.getUpper(), 1));
			Set<LabeledNode> filteredNode =
				candidates.nodes.stream().filter((item) -> (item.timeInterval.getLower() <= exeTime)).map((item) -> item.node).collect(Collectors.toSet());
			return new NodeOccurrence(exeTime, filteredNode);
		});

		private final NodeAndExecutionTimeChoice strategy;

		StrategyEnum(NodeAndExecutionTimeChoice strategy) {
			this.strategy = strategy;
		}

		/**
		 * @return a NodeAndExecutionTimeChoice function
		 */
		@Override
		public NodeAndExecutionTimeChoice get() {
			return strategy;
		}
	}

	/**
	 * Node enabled in the given time window. Each node has its specific time window that has a not empty intersection with the given time window with its time
	 * window.
	 */
	@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "It is not important!")
	public record NodeEnabledInTimeInterval(TimeInterval timeInterval, ObjectSet<NodeWithTimeInterval> nodes) {
		@Override
		public String toString() {
			return "[%s: %s]".formatted(timeInterval, nodes);
		}
	}

	/**
	 * Node occurrence {@code (t, nodes)}.<br> {@code t} represent an execution time. {@code nodes} the set of nodes executed at time {@code t}. {@code nodes}
	 * can be empty but not null.
	 */
	@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "It is not important!")
	public record NodeOccurrence(int occurrenceTime, Set<LabeledNode> nodes) {
		@Override
		public String toString() {
			return "(%s, %s)".formatted(Constants.formatInt(occurrenceTime), nodes);
		}
	}

	/**
	 * This interface allows the use of an enum of predefined strategies and the possibility of defining new ones.<br>
	 * Enum of predefined strategies allows the specification of a strategy as input parameter.
	 */
	@SuppressWarnings("InterfaceWithOnlyOneDirectInheritor")
	@FunctionalInterface
	public interface Strategy {
		/**
		 * @return a NodeAndExecutionTimeChoice function
		 */
		NodeAndExecutionTimeChoice get();
	}

	/**
	 * Node with its time window.
	 */
	@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "It is not important!")
	public record NodeWithTimeInterval(TimeInterval timeInterval, LabeledNode node) implements Comparable<NodeWithTimeInterval> {
		/**
		 * @param other other node with time interval
		 *
		 * @return negative value if this node is alphabetical before other node, 0 if both have the same name, positive value if this node is alphabetical
		 * 	after other node.
		 */
		public int compareTo(NodeWithTimeInterval other) {
			return this.node.compareTo(other.node);
		}

		@Override
		public String toString() {
			return "[%s: %s]".formatted(node, timeInterval);
		}
	}


	/**
	 * Represents a contingent link
	 *
	 * @param activationNode
	 * @param lowerBound
	 * @param upperBound
	 * @param contingentNode
	 */
	record ContingentLink(LabeledNode activationNode, int lowerBound, int upperBound, LabeledNode contingentNode) {}

	/**
	 * Class to represent the data for an execution of {@link #rte(Strategy, Strategy)}.
	 */
	public static class RTEState {
	/*
	Luke's proposal to manage efficiently enabled nodes and wait lists.
	let X be any executable TP.  *all* updates to LB(x) arising from propagations
	along negative ordinary edges *must* be done *before* X becomes enabled
	(by definition of enabled).  Therefore, the instant that X becomes enabled, its
	ordinary LB is fixed forever.  (So no increase_key ops for X's lower bound.)
	Similarly, *all* insertions of activated waits into
	X's activatedWaits queue *must* be done *before* X becomes enabled
	(again, by definition of enabled).  Therefore, the instant that X becomes enabled,
	all the *numerical* values of its activated waits are fixed forever.  HOWEVER,
	when a contingent timepoint executes, an activated wait may be *deleted* from
	X's activatedWaits queue, causing glb(X) to decrease.
	---> goal:  avoid walking through enabled each time genDecn is called
	(i.e., avoid O(n^2), at least for that portion of RTE*).
    ***
	The above description is valid if there are no rigid components.
	If two node, X, Y are rigidly connected at distance 0, and one has positive distance v to a
	contingent t.p., then both will have a wait to the activation t.p. A.
	When A is executed, X and Y become enabled with time window [A+wait, ∞].
	Suppose that C occurs promptly such that waits are removed. The time window of X, Y is
	updated to [C,∞] with C possibly ≤ A+wait (as suggested by the previous analysis).
	If X is executed at C, then the time window of Y becomes [C,C] violating the assumption that
	upper bound is never touched after a node is enabled.
    ***
	Possible solution:
	min priority queue for GLB.  only contains entries for enabled tps.
	key = timepoint (e.g., X).
	value/priority = GLB(X) = max{lb(x), peek_max{ActWaits(X)}}
	before becoming enabled, lb(x) may change (constant time per propagation)
	and up to k entries may be put into ActWaits(X) (log(k) per insert)
	before becoming enabled, it may happen that some entries in ActWaits(X)
	need to be deleted (log(k) per deletion)
	-------------
	the instant X becomes enabled, it is inserted into GLB with priority shown above.
	--------------
	afterward, some actWaits may be deleted from ActWaits(X):  log(k) per deletion.
	and a check:  if that deletion changes peek_max{ActWaits(X)}, then may need
	to do a *decrease key* on GLB(X).
	and when X is eventually executed, need to remove X from GLB(x) in O(log n) time.

	TOTAL COMPLEXITY
    -----------------------------
	For each ActWaits(X) queue, there are at most k insertions and k deletions at
	a cost of O(log k) each.
	So for n ActWaits queues, there are at most nk insertions and deletions
	the total cost is O(nk log(k)) ... well O(w log(k)) where w = num waits
	For the GLB queue.  n insertions and n deletions.  cost:  O(n log(n))
	But up to nk decrease keys, at a cost of log(n) each:  total: O(nk log(n)).
	Total cost for GLB queue:  O(nk log(n)).... well O(w log(n)) where w = num waits
    */
		/**
		 * Global lower bound priority queue. It maintains the lower bound of each enabled node.
		 */
		private final ExtendedPriorityQueue<LabeledNode> glb;
		/**
		 * Global upper bound priority queue.
		 */
		private final ExtendedPriorityQueue<LabeledNode> gub;
		/**
		 * Map (node, timeWindow). Time window is built considering only ordinary constraints and ignoring possible waits.
		 */
		private final Object2ObjectMap<LabeledNode, TimeInterval> timeWindow;
		/**
		 * Map (node, activeWaits). It maintains, for each node, the list of active waits for it.
		 */
		private final Object2ObjectMap<LabeledNode, ActiveWaits> activeWaits;
		/**
		 * Set of un-executed ordinary node.
		 */
		private final ObjectSet<LabeledNode> uONode;
		/**
		 * Set of un-executed contingent node.
		 */
		private final ObjectSet<LabeledNode> uCNode;
		/**
		 * Set of enabled ordinary node.
		 */
		private final ObjectSet<LabeledNode> enabledNode;
		/**
		 * Active contingent links, represented as map (activationNode, ContingentLink). A contingent link (A,x,yC) is active when schedule(A) ≤ currentTime and
		 * schedule(C) = +∞
		 */
		private final Object2ObjectMap<LabeledNode, ContingentLink> activeContingentLink;
		/**
		 * Strategy used by Environment to choice a duration.
		 */
		public NodeAndExecutionTimeChoice environmentStrategy;

		/**
		 * Execution time statistics. Average execution time for each node (it is cumulative of all other times).
		 */
		public SummaryStatistics executionTimeRTEns,
		/**
		 * Execution time statistics.  Average execution time for INIT each node.
		 */
		executionTimeRTEinitNs,
		/**
		 * Execution time statistics.  Average execution time for RTEDecision of each controllable node.
		 */
		executionTimeRTEDecisionNs,
		/**
		 * Execution time statistics. Average execution time for updating each controllable node.
		 */
		executionTimeRTEUpdateNs,
		/**
		 * Execution time statistics.  Average execution time for observe contingent nodes
		 */
		executionTimeObserveNs,
		/**
		 * Execution time statistics. Average execution time for each HCE
		 */
		executionTimeHCEns,
		/**
		 * Execution time statistics. Average execution time for each HOE
		 */
		executionTimeHOEns;

		/**
		 * Strategy used by RTED to choice a time instant.
		 */
		public NodeAndExecutionTimeChoice rtedStrategy;
		/**
		 * Schedule. Map (node, real) that represents the schedule of the network. It is filled by {@link #rte(Strategy, Strategy)} method.
		 */
		public Object2IntOpenHashMap<LabeledNode> schedule;
		/**
		 * Current time
		 */
		private int currentTime;

		/**
		 * Default constructor
		 */
		RTEState() {
			glb = new ExtendedPriorityQueue<>();
			gub = new ExtendedPriorityQueue<>();
			activeWaits = new Object2ObjectOpenHashMap<>();
			activeWaits.defaultReturnValue(null);
			schedule = new Object2IntOpenHashMap<>();
			schedule.defaultReturnValue(Constants.INT_NULL);
			uONode = new ObjectArraySet<>();
			uCNode = new ObjectArraySet<>();
			enabledNode = new ObjectArraySet<>();
			activeContingentLink = new Object2ObjectOpenHashMap<>();
			activeContingentLink.defaultReturnValue(null);
			timeWindow = new Object2ObjectOpenHashMap<>();
			timeWindow.defaultReturnValue(null);
			currentTime = 0;
			rtedStrategy = environmentStrategy = null;
			executionTimeRTEns = new SummaryStatistics();
			executionTimeRTEinitNs = new SummaryStatistics();
			executionTimeRTEDecisionNs = new SummaryStatistics();
			executionTimeRTEUpdateNs = new SummaryStatistics();
			executionTimeObserveNs = new SummaryStatistics();
			executionTimeHCEns = new SummaryStatistics();
			executionTimeHOEns = new SummaryStatistics();
		}

		/**
		 * @return the schedule as an array sorted w.r.t. the order of node execution.
		 */
		public Object2IntMap.Entry<LabeledNode>[] getOrderedSchedule() {
			final Comparator<? super Object2IntMap.Entry<LabeledNode>> cmp = (o1, o2) -> {
				final int diff = o1.getIntValue() - o2.getIntValue();
				if (diff == 0) {
					return o1.getKey().compareTo(o2.getKey());
				}
				return diff;
			};
			@SuppressWarnings("unchecked") final Object2IntMap.Entry<LabeledNode>[] result = new AbstractObject2IntMap.Entry[schedule.size()];

			return schedule.object2IntEntrySet().stream().sorted(cmp).toList().toArray(result);
		}

		/**
		 * @return a view-only copy of the current schedule.
		 */
		public Object2IntMap<LabeledNode> getSchedule() {
			return Object2IntMaps.unmodifiable(schedule);
		}

		/**
		 * @return the string representation of the current state
		 */
		@SuppressFBWarnings("VA_FORMAT_STRING_USES_NEWLINE")//false positive
		public String toString() {
			return """
			       schedule: %s
			       GLB: %s
			       GUB: %s
			       uONodes: %s
			       uCNodes: %s
			       enabledNodes: %s
			       timeWindows: %s
			       activeWaits: %s
			       activeContingentsLinks: %s
			       currentTime: %s
			       """.formatted(schedule, glb.toString().replaceAll(String.valueOf(Constants.INT_POS_INFINITE), Constants.INFINITY_SYMBOLstring),
			                     gub.toString().replaceAll(String.valueOf(Constants.INT_POS_INFINITE), Constants.INFINITY_SYMBOLstring), uONode, uCNode,
			                     enabledNode, timeWindow, activeWaits, activeContingentLink.values(), Constants.formatInt(currentTime));
		}

		/**
		 * @return a copy of the current time window of the given node considering its time window and possible waits.
		 */
		TimeInterval getCurrentTimeWindow(LabeledNode node) {
			final TimeInterval nodeTimeWindow = timeWindow.get(node);
			if (nodeTimeWindow == null) {
				throw new IllegalStateException("There is no time window for node " + node);
			}
			final ActiveWaits waitQueue = activeWaits.get(node);
			if (waitQueue != null) {
				final int maxWait = waitQueue.getMaximum();
				if (nodeTimeWindow.getLower() < maxWait) {
					if (maxWait > nodeTimeWindow.getUpper()) {
						throw new IllegalStateException(
							"Time window lower bound of " + node + " is " + Constants.formatInt(maxWait) + " while upper bound is " +
							Constants.formatInt(nodeTimeWindow.getUpper()));
					}
					return new TimeInterval(maxWait, nodeTimeWindow.getUpper());
				}
			}
			return new TimeInterval(nodeTimeWindow.getLower(), nodeTimeWindow.getUpper());
		}

		/**
		 * Updates the lower bound of the time window of the given node to lower value. If the node is already executed, it checks that the execution time is ≥
		 * the given lower as sanity check.
		 *
		 * @throws IllegalStateException if the update raises an inconsistency.
		 */
		void updateLowerBound(@Nonnull LabeledNode node, int lower) {
			final TimeInterval nodeTimeWindow = timeWindow.get(node);
			if (nodeTimeWindow == null) {
				throw new IllegalStateException("There is no time window for node " + node);
			}
			//sanity check
			final int executionTime = schedule.getInt(node);
			if (executionTime != Constants.INT_NULL) {
				if (executionTime < lower) {
					throw new IllegalStateException(
						"Node " + node + " already executed at " + Constants.formatInt(executionTime) + ". The new lower bound for its time window " +
						Constants.formatInt(lower) + " is not admissible.");
				}
				return;
			}
			if (nodeTimeWindow.getLower() == lower) {
				//confirm
				return;
			}
			if (nodeTimeWindow.getLower() > lower) {
				//the time window lower is stricter
				return;
			}
			if (lower > nodeTimeWindow.getUpper()) {
				throw new IllegalStateException(
					"Problem with node " + node + ": current time window of node is " + nodeTimeWindow + " but the new lower bound should be " + lower);
			}
			nodeTimeWindow.set(lower, nodeTimeWindow.getUpper());
		}

		/**
		 * Updates the upper bound of the time window of the given node to upper.<br> If the node is already executed, it checks that the execution time is ≤
		 * the given upper as sanity check.
		 *
		 * @throws IllegalStateException if the update raises an inconsistency.
		 */
		void updateUpperBound(@Nonnull LabeledNode node, int upper) {
			final TimeInterval nodeTimeWindow = timeWindow.get(node);
			if (nodeTimeWindow == null) {
				throw new IllegalStateException("There is no time window for node " + node);
			}
			//sanity check
			final int executionTime = schedule.getInt(node);
			if (executionTime != Constants.INT_NULL) {
				if (executionTime > upper) {
					throw new IllegalStateException(
						"Node " + node + " already executed at " + Constants.formatInt(executionTime) + ". The new upper bound of its time window " +
						Constants.formatInt(upper) + " is not compatible.");
				}
				return;
			}
			//end sanity check
			if (nodeTimeWindow.getUpper() == upper) {
				//confirm
				return;
			}
			if (nodeTimeWindow.getUpper() < upper) {
				//there is already a stricter upper bound
				return;
			}
			if (upper < nodeTimeWindow.getLower()) {
				throw new IllegalStateException(
					"Problem with node " + node + ": current time window of node is " + nodeTimeWindow + " but the new upper bound should be " + upper);
			}
			nodeTimeWindow.set(nodeTimeWindow.getLower(), upper);
		}
	}

	/**
	 * RTE Decision. RTED can have one of two forms: `Wait` or (t, χ).<br> A `Wait` decision can be glossed as “wait for a contingent timepoint to execute”.<br>
	 * A (t, X) decision can be glossed as “if no contingent timepoints execute before time t, then execute the timepoints in the set X”.<br>
	 */
	static class RTED {
		/**
		 * True if the engine is waiting for a contingent node occurrence
		 */
		private final boolean wait;
		/**
		 * Time at which to execute the set of ordinary nodes {@link #nodesToExecute}
		 */
		private final int executionTime;
		/**
		 * The ordinary nodes to execute at {@link #executionTime}
		 */
		Set<LabeledNode> nodesToExecute;

		RTED(boolean w) {
			if (!w) {
				throw new IllegalStateException("Don't use this constructor");
			}
			wait = true;
			executionTime = 0;
			nodesToExecute = null;
		}

		RTED(@Nonnull Set<LabeledNode> nodes, int time) {
			wait = false;
			nodesToExecute = nodes;
			executionTime = time;
		}

		public boolean isWait() {
			return wait;
		}

		@Override
		public String toString() {
			if (wait) {
				return "(wait)";
			}
			return "(%s, %s)".formatted(Constants.formatInt(executionTime), nodesToExecute);
		}
	}

	/**
	 * Version of the class.
	 */
	static final String VERSIONandDATE = "Version beta - January 30, 2024";
	/**
	 * Logger of the class.
	 */
	private static final Logger LOG = Logger.getLogger(STNURTE.class.getName());

	/**
	 * @param args an array of {@link String} objects.
	 */
	public static void main(String[] args) {
		final STNURTE rte = new STNURTE();
		System.out.println(rte.getVersionAndCopyright());
		if (Debug.ON) {
			LOG.info("Start...");
		}
		if (!rte.manageParameters(args)) {
			return;
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.info("Parameters ok!");
			}
		}
		if (rte.versionReq) {
			return;
		}
		if (Debug.ON) {
			LOG.info("Loading graph...");
		}
		final TNGraphMLReader<STNUEdge> graphMLReader = new TNGraphMLReader<>();
		try {
			final TNGraph<STNUEdge> graph = graphMLReader.readGraph(rte.fInput, STNUEdgeInt.class);
			rte.setG(graph);
		} catch (IOException | ParserConfigurationException | SAXException e) {
			throw new RuntimeException(e);
		}
		if (Debug.ON) {
			LOG.info("STNU Graph loaded!\nNow, it is time to execute it...");
		}
		final Strategy rtedStrategy = rte.chosenRtedStrategy != null ? rte.chosenRtedStrategy : StrategyEnum.FIRST_NODE_EARLY_EXECUTION_STRATEGY;
		final Strategy environmentStrategy = rte.chosenEnvironmentStrategy != null ? rte.chosenEnvironmentStrategy : StrategyEnum.LATE_EXECUTION_STRATEGY;
		final RTEState rteState = rte.rte(rtedStrategy, environmentStrategy);
		if (rte.fOutput != null) {
			System.out.println("Saving the schedule in file " + rte.fOutput.getName());
			try (final PrintWriter pw = new PrintWriter(rte.fOutput.getAbsolutePath(), StandardCharsets.UTF_8)) {
				pw.println(rteState.schedule);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		//prepare the print of schedule by increasing time
		System.out.println("Final schedule: " + Arrays.toString(rteState.getOrderedSchedule()));
		System.out.println("RTE execution statistics (ns): " + rteState.executionTimeRTEns.getSum());
		System.out.println("RTEinit execution statistics (ns): " + rteState.executionTimeRTEinitNs.getSum());
		System.out.println("RTEdecision execution statistics (ns): " + rteState.executionTimeRTEDecisionNs.getSum());
		System.out.println("RTEupdate execution statistics (ns): " + rteState.executionTimeRTEUpdateNs.getSum());
		System.out.println("Observe execution statistics (ns): " + rteState.executionTimeObserveNs.getSum());
		System.out.println("Handling Contingent execution statistics (ns): " + rteState.executionTimeHCEns.getSum());
		System.out.println("Handling Ordinary execution statistics (ns): " + rteState.executionTimeHOEns.getSum());
		if (Debug.ON) {
			LOG.info("Finished.");
		}
	}

	/**
	 * Input TNGraph.
	 */
	private TNGraph<STNUEdge> g;
	/**
	 * Set of ordinary node.
	 */
	private ObjectSet<LabeledNode> oNode;
	/**
	 * Set of contingent node.
	 */
	private ObjectSet<LabeledNode> cNode;
	/**
	 * Contingent links, represented as map (activationNode, ContingentLink).
	 */
	private Object2ObjectMap<LabeledNode, ContingentLink> contingentLink;
	/**
	 * Sanity checks of the durations selected by the environment strategy. In some cases, it is useful to disable such a check. For example, when executing
	 * STNUs that are approximations of probabilistic STNs, the duration of a contingent link is chosen by a distribution probability function; sometimes, the
	 * chosen values can be outside the contingent bounds of the associated STNU.
	 */
	boolean strictEnvironmentCheck;
	/**
	 * Running contingent link, represented as map (contingentNode, activationNode) that is built during an {@link #rteInit(Strategy, Strategy)}
	 */
	private Object2ObjectMap<LabeledNode, LabeledNode> activationNode;
	/**
	 * Strategy used by Environment to choose a duration.
	 */
	@Option(name = "-e", aliases = "--env", usage = "Which environment strategy to use.")
	private StrategyEnum chosenEnvironmentStrategy;
	/**
	 * The input file containing the STN graph in GraphML format.
	 */
	@Argument(usage = "file_name must be the input dispatchable STNU graph in GraphML format.", metaVar = "file_name")
	private File fInput;
	/**
	 * Output file where to write the execution trace.
	 */
	@Option(name = "-o", aliases = "--output", usage = "Output to this file. If file is already present, it is overwritten. If this parameter is not present, then the output is sent to the std output.", metaVar = "output_file_name")
	private File fOutput;

	/**
	 * Software Version.
	 */
	@Option(name = "-v", aliases = "--version", usage = "Version")
	private boolean versionReq;
	/**
	 * Strategy used by RTED to choose a duration.
	 */
	@Option(name = "-r", aliases = "--rted", usage = "Which strategy to use for RTE decision.")
	private StrategyEnum chosenRtedStrategy;

	/**
	 * @param graph                  TNGraph to check
	 * @param strictEnvironmentCheck Disables (=true) the sanity checks on the durations selected by the environment strategy.
	 */
	public STNURTE(@Nonnull TNGraph<STNUEdge> graph, boolean strictEnvironmentCheck) {
		this();
		setG(graph);
		this.strictEnvironmentCheck = strictEnvironmentCheck;
	}


	/**
	 * Default constructor. Graph is necessary.
	 *
	 * @param graph TNGraph to check
	 */
	public STNURTE(@Nonnull TNGraph<STNUEdge> graph) {
		this();
		setG(graph);
	}

	/**
	 * Internal use
	 */
	private STNURTE() {
		this.strictEnvironmentCheck = true;
	}

	/**
	 * @return the g
	 */
	@SuppressFBWarnings(value = "NP_NONNULL_RETURN_VIOLATION", justification = "Because the only possible case is when private constructor is called.")
	public @Nonnull TNGraph<STNUEdge> getG() {
		return g;
	}

	/**
	 * @return version and copyright string
	 */
	public String getVersionAndCopyright() {
		// I use a non-static method for having a general method that prints the right name for each derived class.
		try {
			return getClass().getName() + " " + getClass().getDeclaredField("VERSIONandDATE").get(this) +
			       "\nSPDX-License-Identifier: LGPL-3.0-or-later, Roberto Posenato.\n";

		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			throw new IllegalStateException("Not possible exception I think :-)");
		}
	}

	/**
	 * Verifies that the given schedule does not violate any constraints.
	 *
	 * @param schedule              the schedule that has to be checked. It is assumed that if a node is not in the schedule, its time is
	 *                              {@link Constants#INT_NULL}.
	 * @param contingentStrictCheck false if contingent node value must not verified.
	 *
	 * @return true if the given schedule does not violate any constraints, false otherwise.
	 */
	public boolean isAViableSchedule(@Nonnull Object2IntMap<LabeledNode> schedule, boolean contingentStrictCheck) {
		for (final STNUEdge edge : this.g.getEdges()) {
			final LabeledNode source = this.g.getSource(edge);
			final LabeledNode dest = this.g.getDest(edge);

			final int sourceTime = schedule.getInt(source);
			if (sourceTime == Constants.INT_NULL) {
				if (Debug.ON) {
					LOG.info("Node " + source + " has no an execution time. It is not in the schedule.");
				}
				return false;
			}
			final int destTime = schedule.getInt(dest);
			if (destTime == Constants.INT_NULL) {
				if (Debug.ON) {
					LOG.info("Node " + dest + " has no an execution time. It is not in the schedule.");
				}
				return false;
			}
			if (contingentStrictCheck && edge.isContingentEdge()) {
				if (edge.isLowerCase()) {
					if ((destTime - sourceTime) < edge.getLabeledValue()) {//Edge is A--(c:x)-->C. Therefore, it must be C-A >= x
						if (Debug.ON) {
							LOG.info(
								"Contingent lower case constraint " + edge + " is not satisfy. Source node time: " + sourceTime + ", destination node time: " +
								destTime);
						}
						return false;
					}
				}
				if (edge.isUpperCase()) {
					if ((sourceTime - destTime) > -edge.getLabeledValue()) { //Edge is A<--(C:-y)--C. Therefore, it must be C-A <= y
						if (Debug.ON) {
							LOG.info(
								"Contingent lower case constraint " + edge + " is not satisfy. Source node time: " + sourceTime + ", destination node time: " +
								destTime);
						}
						return false;
					}
				}
				continue;
			}
			if (edge.isWait()) {
				// is source--(C:-y)-->dest. If must be 'source <= dest-y' or 'C -y <= source'
				final LabeledNode C = this.g.getNode(edge.getCaseLabel().getName().toString());
				final int cTime = schedule.getInt(C);
				if (cTime == Constants.INT_NULL) {
					if (Debug.ON) {
						LOG.info("Node " + C + " has no an execution time. It is not in the schedule.");
					}
					return false;
				}
				final int y = edge.getLabeledValue();
				if (y > 0) {
					if (Debug.ON) {
						LOG.info("Edge " + edge + " is a wait with a positive value. It should be simplified!");
					}
					continue;
				}
				if (!(sourceTime <= (destTime + y) || (cTime + y) <= sourceTime)) {
					if (Debug.ON) {
						LOG.info("Edge " + edge + " is a not satisfied wait. Source node time: " + sourceTime + ", destination node time: " + destTime +
						         ", contingent time: " + cTime);
					}
					return false;
				}
			}
			//ordinary constraint source --v--> dest. It must be dest-source <= v
			if (destTime - sourceTime > edge.getValue()) {
				if (Debug.ON) {
					LOG.info("Edge " + edge + " is a not satisfied. Source node time: " + sourceTime + ", destination node time: " + destTime);
				}
				return false;
			}
		}
		return true;
	}

	/**
	 * Real-Time Execution (RTE)
	 * <p>
	 * Considering the current network, {@link #g}, it determines and executes a schedule {@link RTEState#schedule} for it. If the execution cannot be
	 * determined or the execution has any problem, it throws an IllegalStateException with a status message.
	 * </p>
	 *
	 * @param rtedStrategy        strategy to choice the execution instant for ordinary nodes. If null, {@link StrategyEnum#EARLY_EXECUTION_STRATEGY} is
	 *                            chosen.
	 * @param environmentStrategy strategy that the environment uses to choice the duration of contingent links. If null,
	 *                            {@link StrategyEnum#RANDOM_EXECUTION_STRATEGY} is chosen.
	 *
	 * @return the state of the execution. Inside such an object, field `schedule` is the map (node, execution time) representing the scheduling executed.
	 *
	 * @throws IllegalStateException if the schedule cannot be determined.
	 */
	public RTEState rte(@Nullable Strategy rtedStrategy, @Nullable Strategy environmentStrategy)
	throws IllegalStateException {
		final StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		if (Debug.ON) {
			LOG.finer("rte() started.");
		}
		if (rtedStrategy == null) {
			rtedStrategy = StrategyEnum.EARLY_EXECUTION_STRATEGY;
		}
		if (environmentStrategy == null) {
			environmentStrategy = StrategyEnum.RANDOM_EXECUTION_STRATEGY;
		}
		stopWatch.suspend();
		final RTEState state = this.rteInit(rtedStrategy, environmentStrategy);
		stopWatch.resume();
		while (state.uONode.size() > 0 || state.uCNode.size() > 0) {
			//Some nodes are un-executed
			stopWatch.suspend();
			final RTED rted = rteDecision(state); //rted is not null for sure
			final NodeOccurrence ctgs = observe(state, rted); //observe occurrence of contingent nodes
			rteUpdate(state, rted, ctgs);
			stopWatch.resume();
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("RTE State: " + state);
				}
			}
		}
		stopWatch.suspend();
		if (Debug.ON) {
			LOG.finer("rte() finished.");
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("Scheduler: " + state.schedule);
			}
		}
		state.executionTimeRTEns.addValue(stopWatch.getNanoTime());
		return state;
	}


	/**
	 * Updates {@code state} in response to contingent executions {@code nodeOccurrence}.
	 */
	private void handleContingentExecution(@Nonnull RTEState state, @Nonnull NodeOccurrence nodeOccurrence) {
		final StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		if (Debug.ON) {
			LOG.finer("handleContingentExecution started.");
		}
		for (final LabeledNode contingentNode : nodeOccurrence.nodes) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.info("Schedule of " + contingentNode + ": " + Constants.formatInt(nodeOccurrence.occurrenceTime));
				}
			}
			state.schedule.put(contingentNode, nodeOccurrence.occurrenceTime);
			state.uCNode.remove(contingentNode);
			//Update time window for neighbors of contingentNode
			final ObjectSet<LabeledNode> updatedNode = updateTimeWindowNeighbors(contingentNode, nodeOccurrence.occurrenceTime, state);

			//remove C from all active waits
			//I search active waits considering all incoming wait of activation node
			final LabeledNode actNode = this.activationNode.get(contingentNode);
			assert actNode != null;
			for (final STNUEdge incoming : this.g.getInEdges(actNode)) {
				if (!incoming.isWait()) {
					continue;
				}
				final LabeledNode source = this.g.getSource(incoming);
				assert source != null;
				final ActiveWaits activeWaits = state.activeWaits.get(source);
				boolean removed = false;
				if (activeWaits != null) {
					removed = activeWaits.remove(contingentNode);
				}
				if (removed) {
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest("Remove wait for node " + source + ". Now active waits are: " + state.activeWaits.get(source));
						}
					}
					updatedNode.add(source);
				}
			}
			//remove the active contingent link
			state.activeContingentLink.remove(actNode);

			updateEnabledNodeSet(updatedNode, state);
			if (Debug.ON) {
				LOG.finer("handleContingentExecution finished.");
			}
			state.executionTimeHCEns.addValue(stopWatch.getNanoTime());
		}
	}

	/**
	 * Updates {@code state} in response to an {@link RTED}.
	 */
	private void handleOrdinaryExecution(@Nonnull RTEState state, @Nonnull RTED rted) {
		final StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		if (Debug.ON) {
			LOG.finer("handleOrdinaryExecution started.");
		}
		final int executionTime = rted.executionTime;
		for (final LabeledNode node : rted.nodesToExecute) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.info("Schedule of " + node + ": " + Constants.formatInt(executionTime));
				}
			}
			state.schedule.put(node, executionTime);
			state.uONode.remove(node);
			state.glb.delete(node);
			state.gub.delete(node);
			state.activeWaits.remove(node);
			state.enabledNode.remove(node);

			final ObjectSet<LabeledNode> updatedNode = updateTimeWindowNeighbors(node, executionTime, state);

			if (this.contingentLink.containsKey(node)) {
				//node is an activation time point
				//active the corresponding contingent link
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("Node " + node + " is an activation one.");
					}
				}
				state.activeContingentLink.put(node, this.contingentLink.get(node));
				//active all the waits going to node
				for (final STNUEdge wait : this.g.getInEdges(node)) {
					if (!wait.isWait()) {
						continue;
					}
					final int waitValue = -wait.getLabeledValue();
					final LabeledNode contingent = this.g.getNode(wait.getCaseLabel().getName().toString());
					final LabeledNode waitingNode = this.g.getSource(wait);
					ActiveWaits actWaitsOfWaitingNode = state.activeWaits.get(waitingNode);
					if (actWaitsOfWaitingNode == null) {
						actWaitsOfWaitingNode = new ActiveWaits();
						state.activeWaits.put(waitingNode, actWaitsOfWaitingNode);
					}
					actWaitsOfWaitingNode.addWait(contingent, Constants.sumWithOverflowCheck(executionTime, waitValue));
					updatedNode.add(waitingNode);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("\tActivate wait on " + waitingNode + ": " + actWaitsOfWaitingNode);
						}
					}
				}
			}
			//updated nodes can become enabled
			updateEnabledNodeSet(updatedNode, state);
		}
		state.executionTimeHOEns.addValue(stopWatch.getNanoTime());
	}

	/**
	 * @param node  a node
	 * @param state the current state
	 *
	 * @return true if the node is currently enabled in the given state.
	 */
	private boolean isANewEnabled(@Nonnull LabeledNode node, @Nonnull RTEState state) {
		if (state.enabledNode.contains(node)) {
			return false;
		}
		for (final STNUEdge outgoingEdge : this.g.getOutEdges(node)) {
			if (outgoingEdge.isContingentEdge()) {
				if (outgoingEdge.isUpperCase()) {
					return false;// contingent node cannot be enabled here.
				}
				continue;//it is an activation time point, ignore the lower-case edge and evaluate its enable status considering others edges.
			}
			final int value = outgoingEdge.getValue();
			if (value >= 0) {
				//it must be ≥ because of rigid component a 0 distance. Putting >, such rigid component nodes never become enabled.
				continue;
			}
			final LabeledNode dest = this.g.getDest(outgoingEdge);
			if (state.schedule.getInt(dest) == Constants.INT_NULL) {
				//there is a negative edge to dest and dest is not executed, node cannot be enabled
				return false;
			}
		}
		return true;
	}

	/**
	 * Simple method to manage command line parameters using args4j library.
	 *
	 * @param args the input args
	 *
	 * @return false if a parameter is missing, or it is wrong. True if every parameter is given in a right format.
	 */
	@SuppressWarnings("deprecation")
	private boolean manageParameters(String[] args) {
		final CmdLineParser parser = new CmdLineParser(this);
		try {
			// parse the arguments.
			parser.parseArgument(args);

			if (fInput == null) {
				try (final Scanner consoleScanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
					System.out.print("Insert STNU file name (absolute file name): ");
					final String fileName = consoleScanner.next();
					fInput = new File(fileName);
				}
			}
			if (!fInput.exists()) {
				throw new CmdLineException(parser, "Input file does not exist.");
			}

			if (fOutput != null) {
				if (fOutput.isDirectory()) {
					throw new CmdLineException(parser, "Output file is a directory.");
				}
				if (fOutput.exists()) {
					if (!fOutput.delete()) {
						final String m = "File " + fOutput.getAbsolutePath() + " cannot be deleted.";
						LOG.severe(m);
						throw new IllegalStateException(m);
					}
				}
			}
		} catch (CmdLineException e) {
			// if there's a problem in the command line, you'll get this exception. this will report an error message.
			System.err.println(e.getMessage());
			System.err.println("java " + getClass().getName() + " [options...] arguments...");
			// print the list of available options
			parser.printUsage(System.err);
			System.err.println();

			// print option sample. This is useful some time
			System.err.println(
				"Example: java -jar CSTNU-*.jar " + getClass().getName() + " " + parser.printExample(OptionHandlerFilter.REQUIRED) + " file_name");
			return false;
		}
		return true;
	}

	/**
	 * The environment non-deterministically decides whether to execute any contingent nodes and, if so, when.
	 *
	 * @param state the current state
	 * @param rted  the current decision
	 *
	 * @return the set of contingent nodes that occur at the specified time.
	 *
	 * @throws IllegalStateException if the state is wait and there is no contingent node to execute.
	 */
	private NodeOccurrence observe(@Nonnull RTEState state, @Nonnull RTED rted) throws IllegalStateException {
		final StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		if (Debug.ON) {
			LOG.finer("observe started.");
		}
		if (state.activeContingentLink.isEmpty()) {
			if (rted.isWait()) {
				throw new IllegalStateException("Waiting contingent node but there is no contingent node to execute.");
			}
			final NodeOccurrence obs = new NodeOccurrence(rted.executionTime, ObjectSets.emptySet());
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("Observation done: " + obs);
				}
				LOG.finer("observe finished.");
			}
			state.executionTimeObserveNs.addValue(stopWatch.getNanoTime());
			return obs;
		}
		int lowerBound = Constants.INT_POS_INFINITE;
		int upperBound = Constants.INT_POS_INFINITE;
		final ObjectSet<NodeWithTimeInterval> contingentsWithTI = new ObjectArraySet<>();
		//find the common lower and upper bounds of active contingent links.
		for (final ContingentLink link : state.activeContingentLink.values()) {
			final int timeA = state.schedule.getInt(link.activationNode);
			int l;
			final int u;
			if (strictEnvironmentCheck) {
				l = Constants.sumWithOverflowCheck(timeA, link.lowerBound);
				u = Constants.sumWithOverflowCheck(timeA, link.upperBound);
				if (l < state.currentTime) {
					l = state.currentTime;
				}
			} else {
				l = timeA;
				u = Constants.INT_POS_INFINITE;
			}
			contingentsWithTI.add(new NodeWithTimeInterval(new TimeInterval(l, u), link.contingentNode));
			if (l < lowerBound) {
				lowerBound = l;
			}
			if (u < upperBound) {
				upperBound = u;
			}
		}

		final TimeInterval allowedTI =
			(this.strictEnvironmentCheck) ? new TimeInterval(lowerBound, upperBound) : new TimeInterval(state.currentTime, upperBound);

		final NodeEnabledInTimeInterval candidates = new NodeEnabledInTimeInterval(allowedTI, contingentsWithTI);
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest(
					"Data for the environment." + "\n\tAllowed time instant interval: " + allowedTI + "\n\tPossible contingent nodes: " + contingentsWithTI);
			}
		}
		stopWatch.suspend();
		final NodeOccurrence environmentChoice = state.environmentStrategy.choice(candidates);
		stopWatch.resume();
		final int chosenInstant = environmentChoice.occurrenceTime;
		final Set<LabeledNode> chosenContingent = environmentChoice.nodes;

		if (!rted.isWait() && chosenInstant > rted.executionTime) {
			//oracle decides not to execute any contingents yet because the chosenInstant is greater than the execution time of ordinary nodes
			//already decides by the engine.
			final NodeOccurrence obs = new NodeOccurrence(rted.executionTime, ObjectSets.emptySet());
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("Since chosen execution time " + chosenInstant + " is greater than RTED time " + Constants.formatInt(rted.executionTime) +
					           ", no contingent time point occur: " + obs);
				}
				LOG.finer("observe finished.");
			}
			state.executionTimeObserveNs.addValue(stopWatch.getNanoTime());
			return obs;
		}

		//start sanity check
		if (this.strictEnvironmentCheck) {
			if (chosenInstant < allowedTI.getLower() || chosenInstant > allowedTI.getUpper()) {
				throw new IllegalStateException(
					"Environment chose a wrong time instant " + Constants.formatInt(chosenInstant) + ".\nAllowed time instant interval: " + allowedTI);
			}
			for (final LabeledNode node : chosenContingent) {
				boolean verified = false;
				for (final NodeWithTimeInterval nodeTW : contingentsWithTI) {
					if (nodeTW.node().equalsByName(node) && nodeTW.timeInterval.getLower() <= chosenInstant &&
					    nodeTW.timeInterval.getUpper() >= chosenInstant) {
						verified = true;
						break;
					}
				}
				if (!verified) {
					throw new IllegalStateException(
						"Environment chose a wrong node " + node + ".\n\t to execute at time " + chosenInstant + ".\n\tChosen contingents: " +
						chosenContingent +
						".\nEnabled nodes with time window were: " + contingentsWithTI);
				}
			}
		}
		//end sanity check
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Possible range for contingent execution time: " + allowedTI + "\n\tChosen instant: " + Constants.formatInt(chosenInstant) +
				          "\n\tChosen contingents: " + chosenContingent);
			}
		}
		final NodeOccurrence obs = new NodeOccurrence(chosenInstant, chosenContingent);
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Observation done: " + obs);
			}
		}
		state.executionTimeObserveNs.addValue(stopWatch.getNanoTime());
		return obs;
	}

	/**
	 * Given the current state of an execution, it makes a decision (RTED) about the next step.
	 * <p>
	 * RTED can have one of two forms: `Wait` or (t, χ).
	 * <p>
	 * A `Wait` decision can be glossed as “wait for a contingent timepoint to execute”.
	 * <p>
	 * A (t, X) decision can be glossed as “if no contingent timepoints execute before time t, then execute the timepoints in the set X”.
	 * <p>
	 * Given the assumption about instantaneous reactivity, it suffices to limit X to a single timepoint.
	 *
	 * @param state the current state of an execution.
	 *
	 * @return an RTED.
	 *
	 * @throws IllegalStateException if no enabled node has a time window compatible with the current time window.
	 */
	@SuppressWarnings("MethodMayBeStatic")
	private RTED rteDecision(@Nonnull RTEState state) throws IllegalStateException {
		final StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		if (Debug.ON) {
			LOG.finer("rteDecision started.");
		}
		if (state.enabledNode.isEmpty()) {
			if (Debug.ON) {
				LOG.finest("No enabled node -> wait.");
				LOG.finer("rteDecision finished.");
			}
			state.executionTimeRTEDecisionNs.addValue(stopWatch.getNanoTime());
			return new RTED(true);
		}
		final AddressableHeap.Handle<Integer, LabeledNode> lowerEntry = state.glb.getFirstEntry();
		final AddressableHeap.Handle<Integer, LabeledNode> upperEntry = state.gub.getFirstEntry();
		assert lowerEntry != null;
		assert upperEntry != null;
		/*
		 * allowedTI must be [max(glb, currentTime), gup]
		 * remember that glp does not consider possible waits.
		 */
		final int glbWithoutWaits = Math.max(lowerEntry.getKey(), state.currentTime);
		TimeInterval allowedTI;
		try {
			allowedTI = new TimeInterval(glbWithoutWaits, upperEntry.getKey());
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("Allowed time interval of enabled node (not considering possible waits): " + allowedTI);
				}
			}
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException("The time window implied by global lower and global upper is [" + Constants.formatInt(lowerEntry.getKey()) + ", " +
			                                Constants.formatInt(upperEntry.getKey()) + "]." +
			                                "\nNo one enabled node has a time window compatible with the current time" + " window: [" +
			                                Constants.formatInt(state.currentTime) + ", " + Constants.INFINITY_SYMBOLstring + "].");
		}
		//prepare list of candidates
		final ObjectSet<NodeWithTimeInterval> enabledNodeInAllowedTI = new ObjectArraySet<>();
		int glbWithWaits = Constants.INT_POS_INFINITE;//to maintain record if enabled-node lower bounds
		//are greater than glbWithoutWaits.
		int updatedNodeLowerBound;
		for (final LabeledNode node : state.enabledNode) {
			//tw is the TimeInterval of node considering also possible waits.
			final TimeInterval tw = state.getCurrentTimeWindow(node);
			assert tw != null;
			updatedNodeLowerBound = Math.max(glbWithoutWaits, tw.getLower());
			//time window can be updated considering globalLowerBound, while original upper bound must be preserved.
			if (updatedNodeLowerBound > tw.getUpper()) {
				throw new IllegalStateException(
					"Problem with node " + node + ": current time window of node is " + tw + " but the new lower bound should be " + updatedNodeLowerBound);
			}
			tw.set(updatedNodeLowerBound, tw.getUpper());
			enabledNodeInAllowedTI.add(new NodeWithTimeInterval(new TimeInterval(tw), node));
			if (glbWithWaits > updatedNodeLowerBound) {
				glbWithWaits = updatedNodeLowerBound;
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("Node " + node + " enabled with time window " + tw);
				}
			}
		}
		if (glbWithWaits > glbWithoutWaits) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("glbWithWaits: " + glbWithWaits + ".  glbWithoutWaits: " + glbWithoutWaits);
				}
			}
			//waits increases the lower bound of allowed time interval
			allowedTI = new TimeInterval(glbWithWaits, upperEntry.getKey());
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("Allowed time interval of enabled node considering possible waits: " + allowedTI);
				}
			}
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Asking to choose nodes " + enabledNodeInAllowedTI + " considering time allowed time window " + allowedTI);
			}
		}
		final NodeEnabledInTimeInterval nodeEnabled = new NodeEnabledInTimeInterval(allowedTI, enabledNodeInAllowedTI);
		stopWatch.suspend();//the choice isn't a part of RTE
		final NodeOccurrence chosenNodes = state.rtedStrategy.choice(nodeEnabled);
		stopWatch.resume();
		final int t = chosenNodes.occurrenceTime;
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Chosen nodes: " + chosenNodes.nodes + "\n\tChosen execution time: " + Constants.formatInt(t));
			}
		}
		final RTED rted = new RTED(chosenNodes.nodes, t);
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("RTED: " + rted);
			}
		}
		if (Debug.ON) {
			LOG.finer("rteDecision finished.");
		}
		state.executionTimeRTEDecisionNs.addValue(stopWatch.getNanoTime());
		return rted;
	}

	/**
	 * Initializes an RTEState object for an execution of {@link #rte(Strategy, Strategy)}.
	 *
	 * @param rtedStrategy        strategy to choice an execution instant and ordinary nodes.
	 * @param environmentStrategy strategy that the environment uses to choice the duration of contingent links and which contingent nodes to execute.
	 *
	 * @return the data structure used by {@link #rte(Strategy, Strategy)} for an execution.
	 */
	private RTEState rteInit(@Nonnull Strategy rtedStrategy, @Nonnull Strategy environmentStrategy) {
		final StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		if (Debug.ON) {
			LOG.finer("rteInit started.");
		}
		final RTEState rteState = new RTEState();
		rteState.rtedStrategy = rtedStrategy.get();
		rteState.environmentStrategy = environmentStrategy.get();
		rteState.uCNode.clear();
		rteState.uCNode.addAll(this.cNode);
		rteState.uONode.clear();
		rteState.uONode.addAll(this.oNode);
		final LabeledNode Z = this.g.getZ();
		for (final LabeledNode node : this.oNode) {
			if (node == Z) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.INFO)) {
						LOG.info("Node " + node + " is scheduled at 0 by design.");
					}
				}
				rteState.schedule.put(node, 0);
				rteState.timeWindow.put(node, new TimeInterval(0, 0));
				rteState.glb.insertOrUpdate(node, 0);
				rteState.gub.insertOrUpdate(node, 0);
				rteState.enabledNode.add(Z);
				continue;
			}
			rteState.timeWindow.put(node, new TimeInterval(0));
			boolean enabled = true;
			final ObjectList<STNUEdge> outGoingEdges = this.g.getOutEdges(node);
			if (outGoingEdges.size() == 1) {
				//it could be a node in a rigid component.
				final STNUEdge outgoingEdge = outGoingEdges.getFirst();
				int value = outgoingEdge.getValue();
				if (outgoingEdge.isWait() || outgoingEdge.isUpperCase()) {
					enabled = false;
				} else {
					if (!outgoingEdge.isLowerCase()) {
						//it is a requirement edge
						if (value < 0) {
							enabled = false;
						} else {
							if (value == 0) {
								//it could be a node in a rigid component at a distance of 0
								final ObjectList<STNUEdge> inGoingEdges = this.g.getInEdges(node);
								if (inGoingEdges.size() == 1) {
									final STNUEdge inEdge = inGoingEdges.getFirst();
									value = inEdge.getValue();
									if (value == 0) {
										//node is in a rigid component with source node
										final LabeledNode source = this.g.getSource(inEdge);
										assert source != null;
										final ObjectList<STNUEdge> inEdgeSource = this.g.getInEdges(source);
										if (inEdgeSource.size() > 1) {
											//node is not representative
											enabled = false;
										}
									}
								}
							}
						}
					}
				}
			} else {
				for (final STNUEdge outgoingEdge : outGoingEdges) {
					final int value = outgoingEdge.getValue();
					if ((value != Constants.INT_NULL && value < 0) || outgoingEdge.isWait()) {//<0 because it could happen that Z is not present.
						enabled = false;
						break;
					}
				}
			}
			if (enabled) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("Node " + node + " is enabled.");
					}
				}
				rteState.enabledNode.add(node);
				rteState.glb.insertOrUpdate(node, 0);
				rteState.gub.insertOrUpdate(node, Constants.INT_POS_INFINITE);
			}
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("rteState initialized: " + rteState);
			}
			LOG.finer("rteInit finished.");
		}
		rteState.executionTimeRTEinitNs.addValue(stopWatch.getNanoTime());
		return rteState;
	}

	/**
	 * Updates the state of the execution.
	 */
	private void rteUpdate(@Nonnull RTEState state, @Nonnull RTED rted, @Nonnull NodeOccurrence nodeOccurrence) {
		/*
		 * nodeOccurrence.occurrenceTime can be ≤ rted.executionTime, never greater.
		 */
		final StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		if (Debug.ON) {
			LOG.finer("rteUpdate started.");
		}

		if (rted.wait || nodeOccurrence.occurrenceTime < rted.executionTime) {
			handleContingentExecution(state, nodeOccurrence);
		} else {
			//nodeOccurrence.occurrenceTime == rted.executionTime
			handleOrdinaryExecution(state, rted);
			if (!nodeOccurrence.nodes.isEmpty()) {
				handleContingentExecution(state, nodeOccurrence);
			}
		}
		state.currentTime = nodeOccurrence.occurrenceTime;
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("Execution time: " + Constants.formatInt(state.currentTime));
			}
			LOG.finer("rteUpdate finished.");
		}
		state.executionTimeRTEUpdateNs.addValue(stopWatch.getNanoTime());
	}

	private void setG(@Nonnull TNGraph<STNUEdge> graph) {
		this.g = graph;
		this.cNode = new ObjectArraySet<>();
		this.oNode = new ObjectArraySet<>();
		this.contingentLink = new Object2ObjectOpenHashMap<>();
		this.activationNode = new Object2ObjectOpenHashMap<>();
		if (Debug.ON) {
			LOG.fine("STNURTE Constructor started.");
		}
		for (final LabeledNode node : this.g.getVertices()) {
			if (node.isContingent()) {
				this.cNode.add(node);
				//find the contingent link
				final LabeledNode activation;
				final int lowerBound;
				final int upperBound;
				boolean added = false;
				for (final STNUEdge e : this.g.getOutEdges(node)) {
					if (e.isUpperCase() && e.getCaseLabel().getName().toString().equals(node.getName())) {
						upperBound = -e.getLabeledValue();
						activation = this.g.getDest(e);
						//noinspection DataFlowIssue
						lowerBound = this.g.findEdge(activation, node).getLabeledValue();
						this.activationNode.put(node, activation);
						this.contingentLink.put(activation, new ContingentLink(activation, lowerBound, upperBound, node));
						added = true;
						break;
					}
				}
				if (!added) {
					throw new IllegalStateException(
						"Contingent node " + node + " has no out-going upper-case edge." + "\nIt is not possible to execute the network. Check whether the " +
						"network has been made dispatchable and it is represented as " + "distance graph.");
				}
				continue;
			}
			//ordinary node
			this.oNode.add(node);
		}
	}

	/**
	 * Updates enabledNode set considering the set of given nodes.
	 *
	 * @param updatedNodes a set of updated nodes.
	 * @param state        the current state of execution.
	 */
	private void updateEnabledNodeSet(ObjectSet<LabeledNode> updatedNodes, RTEState state) {
		for (final LabeledNode node : updatedNodes) {
			//sanity check
			assert (state.schedule.getInt(node) == Constants.INT_NULL) : "Node " + node + " already scheduled!";
			if (state.enabledNode.contains(node)) {
				//check if a wait was removed and eventually update the glb queue.
				final ActiveWaits waits = state.activeWaits.get(node);
				final TimeInterval currentTimeWindow = state.getCurrentTimeWindow(node);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("Updating time window " + currentTimeWindow + " of node " + node);
					}
				}
				if (waits != null) {//it is assumed that waits == null means no waits added. waits.isEmpty() means that a wait have been removed.
					final int timeInGLB = state.glb.getPriority(node);
					if (timeInGLB > currentTimeWindow.getLower()) {
						//timeInGLB should be == currentTimeWindow.lower. If it is greater,
						//it means that a wait was removed. GLB must be updated.
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINEST)) {
								LOG.finest("\tWaits removed. Update its lower bound in glb: " + Constants.formatInt(timeInGLB) + " --> " +
								           Constants.formatInt(currentTimeWindow.getLower()));
							}
						}
						state.glb.insertOrUpdate(node, currentTimeWindow.getLower());
					}
					if (waits.size() == 0) {
						state.activeWaits.remove(node);
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINEST)) {
								LOG.finest("\tRemoved empty waits queue for node " + node);
							}
						}
					}
				}
				//check if also the gub queue must be updated.
				final int timeInGUB = state.gub.getPriority(node);
				if (timeInGUB > state.timeWindow.get(node).getUpper()) {
					//we have to update also gub when the node is in a rigid component.
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest("\tUpdate its upper bound in gub: " + Constants.formatInt(timeInGUB) + " --> " +
							           Constants.formatInt(currentTimeWindow.getUpper()));
						}
					}
					state.gub.insertOrUpdate(node, currentTimeWindow.getUpper());
				}
			} else {
				if (isANewEnabled(node, state)) {
					state.enabledNode.add(node);
					final TimeInterval timeWindow = state.timeWindow.get(node);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest("New enabled node: " + node + " with time window: " + timeWindow);
						}
					}
					state.glb.insertOrUpdate(node, timeWindow.getLower());
					state.gub.insertOrUpdate(node, timeWindow.getUpper());
				}
			}
		}
	}

	/**
	 * Updates the time window of the neighbors of the given node executed at executionTime.
	 *
	 * @param node          the executed node
	 * @param executionTime its execution time
	 * @param state         the current state of execution
	 *
	 * @return the set of un-executed node whose time window is modified or confirmed by this execution.
	 */
	private ObjectSet<LabeledNode> updateTimeWindowNeighbors(@Nonnull LabeledNode node, int executionTime, @Nonnull RTEState state) {
		final ObjectSet<LabeledNode> updatedNodes = new ObjectArraySet<>();
		//UPPER
		for (final STNUEdge edge : this.g.getOutEdges(node)) {
			if (edge.isContingentEdge() || edge.isWait()) {
				continue;
			}
			final LabeledNode destination = this.g.getDest(edge);
			assert destination != null;
			if (destination.isContingent() || state.schedule.getInt(destination) != Constants.INT_NULL) {
				continue;
			}
			final int edgeValue = edge.getValue();
			final int upper = Constants.sumWithOverflowCheck(executionTime, edgeValue);
			state.updateUpperBound(destination, upper);
			updatedNodes.add(destination);
		}
		//LOWER
		for (final STNUEdge edge : this.g.getInEdges(node)) {
			if (edge.isContingentEdge() || edge.isWait()) {
				continue;
			}
			final LabeledNode source = this.g.getSource(edge);
			assert source != null;
			if (source.isContingent() || state.schedule.getInt(source) != Constants.INT_NULL) {
				continue;
			}
			final int edgeValue = edge.getValue();
			final int lower = Constants.sumWithOverflowCheck(executionTime, -edgeValue);
			if (lower < 0) {
				//it is possible that a contingent C is executed (at 2 for example) before a node X and C<--4--X.
				//lower bound of X should be updated to -2, but it is useless.
				continue;
			}
			state.updateLowerBound(source, lower);
			updatedNodes.add(source);
		}
		return updatedNodes;
	}
}
