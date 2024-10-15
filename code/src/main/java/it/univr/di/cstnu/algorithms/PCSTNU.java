/**
 *
 */
package it.univr.di.cstnu.algorithms;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.univr.di.Debug;
import it.univr.di.cstnu.graph.*;
import it.univr.di.cstnu.visualization.CSTNUStaticLayout;
import it.univr.di.labeledvalue.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple class that extends CSTNU for representing and managing also 'parameter nodes'. Parameter nodes have been
 * introduced in the paper "Dynamic Controllability of Parameterized CSTNUs".
 *
 * @author posenato
 */
@SuppressWarnings("UnusedReturnValue")
public class PCSTNU extends CSTNU {

	/**
	 * Version of the class
	 */
	static final public String VERSIONandDATE = "Version 0.5 - September, 20 2022";// Pre-release for SAC 2023

	/**
	 * logger
	 */
	static final Logger LOG = Logger.getLogger(PCSTNU.class.getName());

	/*
	 * Static initializer
	 */
	static {
		FILE_NAME_SUFFIX = ".pcstnu";// Override suffix
	}

	/**
	 * The set of parameter nodes
	 */
	ObjectArrayList<LabeledNode> parameterNodes;

	/**
	 * @param graphXML an XML description of a parameterized PCSTNU instance
	 *
	 * @throws IOException                  any problem in reading the XML
	 * @throws ParserConfigurationException any problem in the XML format
	 * @throws SAXException                 any problem in the XML format
	 */
	public PCSTNU(String graphXML) throws IOException, ParserConfigurationException, SAXException {
		super(graphXML);
		propagationOnlyToZ =
			true;// this class version applies rules Table 1 of the paper "Dynamic Controllability of Parameterized CSTNUs". So only to Z
		// and from Z rules.

		parameterNodes = new ObjectArrayList<>();
	}

	/**
	 * @param graph a parameterized CSTNU
	 */
	public PCSTNU(TNGraph<CSTNUEdge> graph) {
		super(graph);
		propagationOnlyToZ =
			true;// this class version applies rules Table 1 of the paper "Dynamic Controllability of Parameterized CSTNUs". So only to Z
		// and from Z rules.

		parameterNodes = new ObjectArrayList<>();
	}

	/**
	 * @param graph       a parameterized CSTNU
	 * @param giveTimeOut maximum duration (in seconds) for the DC-checking.
	 */
	public PCSTNU(TNGraph<CSTNUEdge> graph, int giveTimeOut) {
		super(graph, giveTimeOut);
		propagationOnlyToZ =
			true;// this class version applies rules Table 1 of the paper "Dynamic Controllability of Parameterized CSTNUs". So only to Z
		// and from Z rules.

		parameterNodes = new ObjectArrayList<>();
	}

	/*
	 * @param graph                a parameterized CSTNU
	 * @param givenTimeOut         maximum duration (in seconds) for the DC-checking.
	 * @param isPropagationOnlyToZ true if the propagations have to be done only to Z
	 *
	public PCSTNU(TNGraph<CSTNUEdge> graph, int givenTimeOut, boolean isPropagationOnlyToZ) {
		super(graph, givenTimeOut, true);// this class version applies rules Table 1 of the paper "Dynamic Controllability of Parameterized CSTNUs". So only to
		// Z and from Z rules.

		this.parameterNodes = new ObjectArrayList<>();
	}
	*/

	/**
	 * Constructor only for the main and possible extensions.
	 */
	PCSTNU() {
		propagationOnlyToZ =
			true;// this class version applies rules Table 1 of the paper "Dynamic Controllability of Parameterized CSTNUs". So only to Z
		// and from Z rules.

		parameterNodes = new ObjectArrayList<>();
	}

	/**
	 * Reads a PCSTNU file and checks it.
	 *
	 * @param args an array of {@link java.lang.String} objects.
	 *
	 * @throws java.io.IOException                            if any.
	 * @throws javax.xml.parsers.ParserConfigurationException if any.
	 * @throws org.xml.sax.SAXException                       if any.
	 */
	@SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
	public static void main(final String[] args) throws IOException, ParserConfigurationException, SAXException {
		final PCSTNU pcstnu = new PCSTNU();
		System.out.println(pcstnu.getVersionAndCopyright());
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "Start...");
			}
		}
		if (!pcstnu.manageParameters(args)) {
			return;
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "Parameters ok!");
			}
		}
		if (pcstnu.versionReq) {
			System.out.println("PCSTNU " + VERSIONandDATE + ". Academic and non-commercial use only.\n"
			                   + "Copyright © 2022-2023 Roberto Posenato");
			return;
		}

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "Loading graph...");
			}
		}
		final TNGraphMLReader<CSTNUEdge> graphMLReader = new TNGraphMLReader<>();

		pcstnu.setG(graphMLReader.readGraph(pcstnu.fInput, EdgeSupplier.DEFAULT_CSTNU_EDGE_CLASS));
		pcstnu.g.setInputFile(pcstnu.fInput);

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "TNGraph loaded!\nDC Checking...");
			}
		}
		System.out.println("Checking started...");
		final CSTNUCheckStatus status;
		try {
			status = pcstnu.dynamicControllabilityCheck();
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
			System.out.println("Checked graph saved as " + pcstnu.fOutput.getCanonicalPath());

			System.out.println("Details: " + status);
		} else {
			System.out.println("Checking has not been finished!");
			System.out.println("Details: " + status);
		}

		if (pcstnu.fOutput != null) {
			final TNGraphMLWriter graphWriter = new TNGraphMLWriter(new CSTNUStaticLayout<>(pcstnu.g));
			graphWriter.save(pcstnu.getGChecked(), pcstnu.fOutput);
		}
	}

	@Override
	public CSTNUCheckStatus oneStepDynamicControllability(final EdgesToCheck<CSTNUEdge> edgesToCheck,
	                                                      Instant timeoutInstant) {
		throw new IllegalCallerException("For PCSTNU, method oneStepDynamicControllability is not implemented.");
	}

	/**
	 * {@inheritDoc} Calls  and, then, check all constraints about parameter nodes. This method works only with
	 * streamlined instances!
	 *
	 * @throws WellDefinitionException if the initial graph is not well-defined. We preferred to throw an exception
	 *                                 instead of returning a negative status to stress that any further operation
	 *                                 cannot be made on this instance.
	 */
	@Override
	public void initAndCheck() throws WellDefinitionException {
		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO, "Starting checking graph as CSTNU well-defined instance...");
			}
		}
		super.initAndCheck();
		checkStatus.initialized = false;

		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO,
				        "Now checking that al parameter nodes have a well-defined and finite distance from Z...");
			}
		}

		final LabeledNode Z = g.getZ();
		for (final LabeledNode node : g.getVertices()) {
			if (!node.isParameter()) {
				continue;
			}
			final CSTNUEdge toZ = g.findEdge(node, Z);
			Entry<Label> minLValue;
			if (toZ == null || !toZ.isRequirementEdge() || (minLValue = toZ.getMinLabeledValue()) == null
			    || minLValue.getIntValue() == Constants.INT_NULL
			    || minLValue.getIntValue() == Constants.INT_NEG_INFINITE) {
				throw new WellDefinitionException(
					"Parameter node " + node + " has not a finite requirement constraint to " + Z + ": " + toZ);
			}
			// make sure that the constraint is always present (although the node is in a specific scenario)
			toZ.mergeLabeledValue(Label.emptyLabel, minLValue.getIntValue());
			final CSTNUEdge fromZ = g.findEdge(Z, node);
			if (fromZ == null || !fromZ.isRequirementEdge() || (minLValue = fromZ.getMinLabeledValue()) == null
			    || minLValue.getIntValue() == Constants.INT_NULL
			    || minLValue.getIntValue() == Constants.INT_NEG_INFINITE) {
				throw new WellDefinitionException(
					"Parameter node " + node + " has not a finite requirement constraint from " + Z + ": " + fromZ);
			}
			// make sure that the constraint is always present (although the node is in a specific scenario)
			fromZ.mergeLabeledValue(Label.emptyLabel, minLValue.getIntValue());

			parameterNodes.add(node);
		}

		checkStatus.initialized = true;

		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO, "Checking graph as PCSTNU well-defined instance finished!\n");
			}
		}
	}

	/**
	 * Executes one step of the dynamic controllability check considering only a pair of edges going to/from Z or
	 * parameter nodes.<br> The name says limited to Z because it is inherited from CSTNU, but such method calls other
	 * that consider also parameter nodes as Z node.<br> Before the first execution of this method, it is necessary to
	 * execute {@link #initAndCheck()}.<br>
	 * <b>Note: this version is not optimized with respect to the number of times and edge is considered
	 * at each call. Future versions will improve such an aspect.</b>
	 *
	 * @param edgesToCheck   set of edges that have to be checked.
	 * @param timeoutInstant time instant limit allowed to the computation.
	 *
	 * @return the update status (for convenience. It is not necessary because return the same parameter status).
	 */
	@Override
	public CSTNUCheckStatus oneStepDynamicControllabilityLimitedToZ(final EdgesToCheck<CSTNUEdge> edgesToCheck,
	                                                                Instant timeoutInstant) {

		checkStatus.cycles++;
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE,
				        "Starting LOWER bound rules and label removal rules limited to a parameter node or Z.");
			}
		}

		final EdgesToCheck<CSTNUEdge> newEdgesToCheck = new EdgesToCheck<>();
		int i = 1;
		if (Debug.ON) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.log(Level.INFO, "Number of edges to analyze: " + edgesToCheck.size());
			}
		}
		final LabeledNode Z = g.getZ();
		assert Z != null;
		CSTNUEdge edgeCopy;
		/*
		 * LOWER BOUND propagation
		 * Using the pattern D<----B where D must be Z or a parameter
		 *
		 * For upper bound propagation, I do analogous code below.
		 * It is not optimized, but it is more simple to check for now.
		 */
		for (final CSTNUEdge BD : edgesToCheck) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Considering edge " + (i++) + "/" + edgesToCheck.size() + ": " + BD + "\n");
				}
			}
			final LabeledNode D = g.getDest(BD);
			assert D != null;
			final boolean isD_Parameter = D.isParameter();
			if (!isD_Parameter && D != Z) {
				continue;
			}
			final LabeledNode B = g.getSource(BD);
			assert B != null;
			if (D == Z) {
				// initAndCheck does not resolve completely a possible qStar. So, it is necessary to check here the edge before to consider the second edge.
				// The check has to be done in case B==Z, and it consists in applying R0, R3 and
				// zLabeledLetterRemovalRule!
				edgeCopy = g.getEdgeFactory().get(BD);
				if (B.isObserver()) {
					// R0 on the resulting new values
					labelModificationqR0(B, BD);
				}
				labelModificationqR3(B, BD);
				if (B.isObserver()) {// R3 can add new values that have to be minimized. Experimentally VERIFIED on June, 28 2015
					// R0 on the resulting new values
					labelModificationqR0(B, BD);
				}

				if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
					return getCheckStatus();
				}

				// LLR is put here because it works like R0 and R3
				zLabeledLetterRemovalRule(B, BD);

				if (!BD.hasSameValues(edgeCopy)) {
					newEdgesToCheck.add(BD, B, D, Z, g, propagationOnlyToZ);
				}

				if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
					return getCheckStatus();
				}
			}

			/*
			 * Make all propagation considering edge AB as first edge in the chain.<br>
			 * A--->B--->D
			 */
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Apply rules to " + BD.getName() + " as second edge.");
				}
			}

			for (final CSTNUEdge AB : g.getInEdges(B)) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.log(Level.FINER, "Considering edge " + AB.getName() + " as first edge.");
					}
				}
				final LabeledNode A = g.getSource(AB);
				assert A != null;
				CSTNUEdge AD = g.findEdge(A, D);

				// I need to preserve the old edge to compare below
				if (AD != null) {
					edgeCopy = g.getEdgeFactory().get(AD);
				} else {
					AD = makeNewEdge(A.getName() + "_" + D.getName(), CSTNUEdge.ConstraintType.derived);
					edgeCopy = null;
				}

				labelPropagation(A, B, D, AB, BD, AD);

				if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
					return getCheckStatus();
				}

				if (!AB.getLowerCaseValue().isEmpty()) {
					labeledCrossLowerCaseRule(A, B, D, AB, BD, AD);
				}

				boolean add = false;
				if (edgeCopy == null && !AD.isEmpty()) {
					// the new CB has to be added to the graph!
					g.addEdge(AD, A, D);
					add = true;
				} else if (edgeCopy != null && !edgeCopy.hasSameValues(AD)) {
					// CB was already present and it has been changed!
					add = true;
				}
				if (add) {
					newEdgesToCheck.add(AD, A, D, Z, g, propagationOnlyToZ);
				}

				if (!checkStatus.consistency) {
					checkStatus.finished = true;
					return getCheckStatus();
				}

				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Rules phase done.\n");
				}
			}

			if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
				return getCheckStatus();
			}

			if (isD_Parameter) {
				/*
				 * Make all propagations considering edge AB as second edge in the chain only when D is parameter to guarantee that it can be propagated to other
				 * parameter nodes.<br>
				 * B--->D--->A
				 */
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.log(Level.FINER,
						        "Apply rules to " + BD.getName() + " as first edge because " + D + " is a parameter.");
					}
				}

				for (final CSTNUEdge DA : g.getOutEdges(D)) {
					final LabeledNode A = g.getDest(DA);
					assert A != null;
					if (!A.isParameter()) {
						continue;// target is not a parameter... ignore.
					}
					if (Debug.ON) {
						if (LOG.isLoggable(Level.FINER)) {
							LOG.log(Level.FINER, "Considering edge " + DA.getName() + " as second edge.");
						}
					}

					CSTNUEdge BA = g.findEdge(B, A);

					// I need to preserve the old edge to compare below
					if (BA != null) {
						edgeCopy = g.getEdgeFactory().get(BA);
					} else {
						BA = makeNewEdge(B.getName() + "_" + A.getName(), CSTNUEdge.ConstraintType.derived);
						edgeCopy = null;
					}

					labelPropagation(B, D, A, BD, DA, BA);

					if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
						return getCheckStatus();
					}

					if (!DA.getLowerCaseValue().isEmpty()) {
						labeledCrossLowerCaseRule(B, D, A, BD, DA, BA);
					}

					boolean add = false;
					if (edgeCopy == null && !BA.isEmpty()) {
						// the new CB has to be added to the graph!
						g.addEdge(BA, A, Z);
						add = true;
					} else if (edgeCopy != null && !edgeCopy.hasSameValues(BA)) {
						// CB was already present and it has been changed!
						add = true;
					}
					if (add) {
						newEdgesToCheck.add(BA, B, A, Z, g, propagationOnlyToZ);
					}

					if (!checkStatus.consistency) {
						checkStatus.finished = true;
						return getCheckStatus();
					}

					if (LOG.isLoggable(Level.FINER)) {
						LOG.log(Level.FINER, "Rules phase done.\n");
					}
				}

				if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
					return getCheckStatus();
				}
			}
		}

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, "Starting UPPER bound rules starting from a parameter node or Z.");
			}
		}
		/*
		 * UPPER BOUND propagation
		 * Using the pattern B-->D where B must be Z or a parameter node.
		 */
		for (final CSTNUEdge BD : edgesToCheck) {
			final LabeledNode D = g.getDest(BD);
			final LabeledNode B = g.getSource(BD);
			assert B != null;
			assert D != null;
			final boolean isB_Parameter = B.isParameter();
			if (!isB_Parameter && B != Z) {
				continue;
			}
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Considering edge " + (i++) + "/" + edgesToCheck.size() + ": " + BD + "\n");
				}
			}

			if (B == Z) {
				edgeCopy = g.getEdgeFactory().get(BD);
				if (D.isObserver()) {
					// R1 on the resulting new values
					labelModificationR1(B, BD, D);
				}
				labelModificationqR2(B, BD);

				if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
					return getCheckStatus();
				}

				if (!BD.hasSameValues(edgeCopy)) {
					newEdgesToCheck.add(BD, B, D, Z, g, propagationOnlyToZ);
				}

				if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
					return getCheckStatus();
				}
			}

			/*
			 * Make all propagation considering edge AB as second edge in the chain.<br>
			 * B--->D--->A
			 */
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Apply rules to " + BD.getName() + " as first edge in the chain.");
				}
			}

			for (final CSTNUEdge DA : g.getOutEdges(D)) {
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.log(Level.FINER, "Considering first edge " + DA.getName());
					}
				}
				final LabeledNode A = g.getDest(DA);
				assert A != null;
				CSTNUEdge BA = g.findEdge(B, A);

				// I need to preserve the old edge to compare below
				if (BA != null) {
					edgeCopy = g.getEdgeFactory().get(BA);
				} else {
					BA = makeNewEdge(B.getName() + "_" + A.getName(), CSTNUEdge.ConstraintType.derived);
					edgeCopy = null;
				}

				ruleAPlus_AparameterPlus(B, D, A, BD, DA, BA);

				if (checkStatus.checkTimeOutAndAdjustStatus(timeoutInstant)) {
					return getCheckStatus();
				}

				if (D.isContingent()) {
					ruleBPlus_BparameterPlus(B, D, A, BD, DA, BA);
				}

				boolean add = false;
				if (edgeCopy == null && !BA.isEmpty()) {
					// the new CB has to be added to the graph!
					g.addEdge(BA, B, A);
					add = true;
				} else if (edgeCopy != null && !edgeCopy.hasSameValues(BA)) {
					// CB was already present and it has been changed!
					add = true;
				}
				if (add) {
					newEdgesToCheck.add(BA, B, A, Z, g, propagationOnlyToZ);
				}

				if (!checkStatus.consistency) {
					checkStatus.finished = true;
					return getCheckStatus();
				}

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
	 * Apply A, A-Parameter, C rules of paper about Parameter-CSTNU.
	 *
	 * <pre>
	 * 1) CASE A
	 *        ℵ:v,β           u,α
	 * W==Z &lt;------------ Y &lt;------------ X
	 * adds
	 *     ℵ:u+v,𝛾
	 * Z &lt;------------------------------X
	 * 𝛾=αβ has to be consistent and u+v &lt; 0.
	 * ℵ can be empty.
	 * if X is parameter, set ℵ and 𝛾 empty in the generated edge
	 *
	 * 2) CASE A-Parameter
	 *        v,β           ℵ:u,α
	 * W==P &lt;------------ Y &lt;------------ X
	 * adds
	 *     ℵ:u+v,𝛾
	 * P &lt;------------------------------X
	 * P is a parameter, 𝛾=αβ has to be consistent.
	 * ℵ can be empty.
	 * if X is parameter or Z, set ℵ and 𝛾 empty in the generated edge
	 *
	 * 2) CASE C
	 *     ℵ:v,β           C:u,α
	 * Z &lt;------------ Y &lt;------------ C
	 * adds
	 *     Cℵ:u+v,αβ
	 * Z &lt;------------------------------C
	 * if u+v &lt; 0 and αβ is consistent
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
	@Override
	boolean labelPropagation(final LabeledNode nX, final LabeledNode nY, final LabeledNode nW, final CSTNUEdge eXY,
	                         final CSTNUEdge eYW, final CSTNUEdge eXW) {
		// Don't rename such method because it has to overwrite the CSTN one!

		boolean ruleApplied = false;
		final LabeledNode Z = g.getZ();
		final boolean nWisZ = nW == Z;
		final boolean nWisParameter = nW.isParameter();
		if (!nWisZ && !nWisParameter) {
			return false;
		}
		final LabeledALabelIntTreeMap YWAllLabeledValueMap = eYW.getAllUpperCaseAndLabeledValuesMaps();
		final LabeledALabelIntTreeMap XYAllLabeledValueMap = eXY.getAllUpperCaseAndLabeledValuesMaps();
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "A + A-parameter + C rule: starting...");
			}
		}

		boolean nXisParameter = nX.isParameter();
		// 1) CASE A
		if (nWisZ) {
			for (final Object2IntMap.Entry<Label> entryXY : XYAllLabeledValueMap.get(ALabel.emptyLabel).entrySet()) {
				final Label alpha = entryXY.getKey();
				final int u = entryXY.getIntValue();

				for (final ALabel aleph : YWAllLabeledValueMap.keySet()) {
					for (final Object2IntMap.Entry<Label> entryYW : YWAllLabeledValueMap.get(aleph)
						.entrySet()) {// entrySet read-only
						final Label beta = entryYW.getKey();
						Label alphaBeta;
						alphaBeta = alpha.conjunction(beta);
						if (alphaBeta == null) {
							continue;
						}

						final int v = entryYW.getIntValue();
						final int sum = Constants.sumWithOverflowCheck(u, v);

						if (sum >= 0)// W is Z, so also ==0 is redundant
						{
							continue;// rule condition
						}

						ALabel aleph1 = aleph;
						if (nXisParameter) {
							alphaBeta = Label.emptyLabel;
							aleph1 = ALabel.emptyLabel;
						}
						final int oldValue =
							(aleph1.isEmpty()) ? eXW.getValue(alphaBeta) : eXW.getUpperCaseValue(alphaBeta, aleph1);
						if ((oldValue != Constants.INT_NULL) && (sum >= oldValue)) {
							// value is stored only if it is more negative than the current one.
							continue;
						}

						String logMsg = null;
						if (Debug.ON) {
							final String oldXW = eXW.toString();
							logMsg = "A rule applied to edge " + oldXW + ":\n" + "detail: " + nW.getName() + " <---"
							         + upperCaseValueAsString(aleph, v, beta) + "--- " + nY.getName() + " <---"
							         + upperCaseValueAsString(ALabel.emptyLabel, u, alpha) + "--- " + nX.getName()
							         + "\nresult: " + nW.getName() + " <---" + upperCaseValueAsString(aleph1, sum,
							                                                                          alphaBeta) +
							         "--- " + nX.getName() + "; old value: " + Constants.formatInt(
								oldValue);
						}

						final boolean mergeStatus = (aleph1.isEmpty()) ? eXW.mergeLabeledValue(alphaBeta, sum)
						                                               : eXW.mergeUpperCaseValue(alphaBeta, aleph1,
						                                                                         sum);

						if (mergeStatus) {
							ruleApplied = true;
							if (aleph1.isEmpty()) {
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
		}

		// 2) A-parameter
		if (nWisParameter) {
			nXisParameter = nXisParameter || nX == Z;
			for (final ALabel aleph : XYAllLabeledValueMap.keySet()) {
				for (final Object2IntMap.Entry<Label> entryXY : XYAllLabeledValueMap.get(aleph).entrySet()) {
					final Label alpha = entryXY.getKey();
					final int u = entryXY.getIntValue();

					for (final Object2IntMap.Entry<Label> entryYW : YWAllLabeledValueMap.get(ALabel.emptyLabel)
						.entrySet()) {// entrySet read-only
						final Label beta = entryYW.getKey();
						Label alphaBeta = alpha.conjunction(beta);
						if (alphaBeta == null) {
							continue;
						}

						final int v = entryYW.getIntValue();
						final int sum = Constants.sumWithOverflowCheck(u, v);

						if (sum >= 0 && nW == nX) {
							//positive loop are meaningless.
							continue;
						}

						ALabel aleph1 = aleph;
						if (nXisParameter) {
							alphaBeta = Label.emptyLabel;
							aleph1 = ALabel.emptyLabel;
						}
						final int oldValue =
							(aleph1.isEmpty()) ? eXW.getValue(alphaBeta) : eXW.getUpperCaseValue(alphaBeta, aleph1);
						if ((oldValue != Constants.INT_NULL) && (sum >= oldValue)) {
							// value is stored only if it is more negative than the current one.
							continue;
						}

						String logMsg = null;
						if (Debug.ON) {
							final String oldXW = eXW.toString();
							logMsg = "A-parameter rule applied to edge " + oldXW + ":\n" + "detail: " + nW.getName()
							         + " <---" + upperCaseValueAsString(ALabel.emptyLabel, v, beta) + "--- "
							         + nY.getName() + " <---" + upperCaseValueAsString(aleph, u, alpha) + "--- "
							         + nX.getName() + "\nresult: " + nW.getName() + " <---" + upperCaseValueAsString(
								aleph1, sum, alphaBeta) + "--- " + nX.getName() + "; old value: "
							         + Constants.formatInt(oldValue);
						}

						final boolean mergeStatus = (aleph1.isEmpty()) ? eXW.mergeLabeledValue(alphaBeta, sum)
						                                               : eXW.mergeUpperCaseValue(alphaBeta, aleph1,
						                                                                         sum);

						if (mergeStatus) {
							ruleApplied = true;
							if (aleph1.isEmpty()) {
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
		}

		if (!nWisZ) {
			// it is possible to stop here, because the second part is applicable only when nW==Z.
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "A + A-parameter + C: finished.");
				}
			}
			return ruleApplied;
		}

		final ObjectSet<ALabel> XYUpperCaseALabels = eXY.getUpperCaseValueMap().keySet();

		// 2) CASE C
		final ALabel nXasALabel = nX.getALabel();
		for (final ALabel upperCaseLabel : XYUpperCaseALabels) {
			if (upperCaseLabel.size() != 1 || !upperCaseLabel.equals(nXasALabel)) {
				continue;// only UC label corresponding to original contingent upper case value is considered.
			}
			for (final Object2IntMap.Entry<Label> entryXY : eXY.getUpperCaseValueMap().get(upperCaseLabel)
				.entrySet()) {// entrySet
				// read-only
				final Label alpha = entryXY.getKey();
				final int u = entryXY.getIntValue();

				for (final ALabel aleph : YWAllLabeledValueMap.keySet()) {
					for (final Object2IntMap.Entry<Label> entryYW : YWAllLabeledValueMap.get(aleph)
						.entrySet()) {// entrySet read-only
						final Label beta = entryYW.getKey();

						final Label alphaBeta = alpha.conjunction(beta);
						if (alphaBeta == null) {
							continue;
						}

						final ALabel upperCaseLetterAleph = upperCaseLabel.conjunction(aleph);
						final int v = entryYW.getIntValue();

						final int sum = Constants.sumWithOverflowCheck(u, v);
						if (sum >= 0) {
							continue;
						}
						final int oldValue = eXW.getUpperCaseValue(alphaBeta, upperCaseLetterAleph);

						if ((oldValue != Constants.INT_NULL) && (sum >= oldValue)) {
							// in the case of A != C, a value is stored only if it is more negative than the current one.
							continue;
						}

						String logMsg = null;
						if (Debug.ON) {
							final String oldXW = eXW.toString();
							logMsg = "C applied to edge " + oldXW + ":\n" + "detail: " + nW.getName() + " <---"
							         + upperCaseValueAsString(aleph, v, beta) + "--- " + nY.getName() + " <---"
							         + upperCaseValueAsString(upperCaseLabel, u, alpha) + "--- " + nX.getName()
							         + "\nresult: " + nW.getName() + " <---" + upperCaseValueAsString(
								upperCaseLetterAleph, sum, alphaBeta) + "--- " + nX.getName() + "; old value: "
							         + Constants.formatInt(oldValue);
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
				LOG.log(Level.FINER, "A + A-parameter + C: finished.");
			}
		}
		return ruleApplied;
	}

	/**
	 * Apply B and B-parameter rules of paper about Parameter-CSTNU. 1) Case B
	 *
	 * <pre>
	 *     ℵ:v,β           c:u,α
	 * Z &lt;------------ C &lt;------------ A
	 * adds
	 *             ℵ:u+v,αβ
	 * Z &lt;----------------------------A
	 *
	 * Conditions: αβ∈P*, C ∉ ℵ, u+v &lt; 0
	 * </pre>
	 * <p>
	 * 2) Case B-parameter
	 *
	 * <pre>
	 *     v,β             c:u,α
	 * X &lt;------------ C &lt;------------ A
	 * adds
	 *             u+v,αβ
	 * X &lt;----------------------------A
	 *
	 * X is a parameter node.
	 * Conditions: αβ∈P*
	 * </pre>
	 * <p>
	 * Since it is assumed that L(C)=L(A)=α, there is only ONE lower-case labeled value u,c,α! The name is inherited
	 * from CSTNU.
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
	@Override
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
				LOG.log(Level.FINER, "B + B-parameter: starting...");
			}
		}

		final boolean nXisParameter = nX.isParameter();
		final boolean nXisZ = nX == g.getZ();

		for (final ALabel aleph : CXAllValueMap.keySet()) {
			LabeledIntMap CXValuesMap = CXAllValueMap.get(aleph);
			if (CXValuesMap == null) {
				continue;
			}
			final boolean emptyAleph = aleph.isEmpty();
			if (!emptyAleph && aleph.contains(c)) {
				continue;// rule condition
			}

			if (nXisZ) {
				for (final Object2IntMap.Entry<Label> entryCX : CXValuesMap.entrySet()) {// entrySet read-only
					// Rule condition: upper case label cannot be equal or contain c name

					final int v = entryCX.getIntValue();
					final int sum = Constants.sumWithOverflowCheck(v, u);
					if (sum >= 0) {
						continue; // Rule condition!
					}

					final Label beta = entryCX.getKey();
					final Label alphaBeta = beta.conjunction(alpha);
					if (alphaBeta == null) {
						continue;
					}

					final int oldValue =
						(emptyAleph) ? eAX.getValue(alphaBeta) : eAX.getUpperCaseValue(alphaBeta, aleph);

					if (oldValue != Constants.INT_NULL && oldValue <= sum) {
						continue;
					}
					String logMsg = null;
					if (Debug.ON) {
						final String oldAX = eAX.toString();
						logMsg = "B rule applied to edge " + oldAX + ":\ndetail: " + nX.getName() + " <---"
						         + upperCaseValueAsString(aleph, v, beta) + "--- " + nC.getName() + " <---"
						         + lowerCaseValueAsString(c, u, alpha) + "--- " + nA.getName() + "\nresult: "
						         + nX.getName() + " <---" + upperCaseValueAsString(aleph, sum, alphaBeta) + "--- "
						         + nA.getName() + "; oldValue: " + Constants.formatInt(oldValue);
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
			} // end if X == Z

			if (nXisParameter) {
				CXValuesMap = CXAllValueMap.get(ALabel.emptyLabel);
				if (CXValuesMap == null) {
					return ruleApplied;// no value to consider
				}

				for (final Object2IntMap.Entry<Label> entryCX : CXValuesMap.entrySet()) {// entrySet read-only

					final int v = entryCX.getIntValue();
					final int sum = Constants.sumWithOverflowCheck(v, u);
					if (sum >= 0) {
						continue; // Rule condition!
					}

					final Label beta = entryCX.getKey();
					final Label alphaBeta = beta.conjunction(alpha);
					if (alphaBeta == null) {
						continue;
					}

					final int oldValue =
						(emptyAleph) ? eAX.getValue(alphaBeta) : eAX.getUpperCaseValue(alphaBeta, aleph);

					if (oldValue != Constants.INT_NULL && oldValue <= sum) {
						continue;
					}
					String logMsg = null;
					if (Debug.ON) {
						final String oldAX = eAX.toString();
						logMsg = "B-parameter rule applied to edge " + oldAX + ":\ndetail: " + nX.getName() + " <---"
						         + upperCaseValueAsString(aleph, v, beta) + "--- " + nC.getName() + " <---"
						         + lowerCaseValueAsString(c, u, alpha) + "--- " + nA.getName() + "\nresult: "
						         + nX.getName() + " <---" + upperCaseValueAsString(aleph, sum, alphaBeta) + "--- "
						         + nA.getName() + "; oldValue: " + Constants.formatInt(oldValue);
					}

					final boolean localApp = eAX.mergeLabeledValue(alphaBeta, sum);

					if (localApp) {
						ruleApplied = true;
						getCheckStatus().crossCaseRuleCalls++;
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
		}

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "B + B-parameter: finished.");
			}
		}
		return ruleApplied;
	}

	/**
	 * Apply zR1 rule assuming instantaneous reaction and a streamlined network.<br>
	 *
	 * <pre>
	 *       v,pβ
	 * W ------------&gt; P?
	 * adds
	 *       v,β
	 * W ------------&gt; P?
	 * W can be Z or a parameter node. Value v has to be >= 0
	 * </pre>
	 *
	 * @param nW   the parameter or the Z node.
	 * @param nObs the observation node
	 * @param eWP  the edge connecting W ---&gt; P?
	 *
	 * @return true if the rule has been applied one time at least.
	 */
	boolean labelModificationR1(final LabeledNode nW, final CSTNUEdge eWP, final LabeledNode nObs) {

		boolean ruleApplied = false, mergeStatus;
		final LabeledNode Z = g.getZ();
		final char p = nObs.getPropositionObserved();
		if (p == Constants.UNKNOWN) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "First parameter is not a parameter-node.");
				}
			}
			return false;
		}
		if (g.getSource(eWP) != nW || !nW.isParameter() || nW != Z) {
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.log(Level.FINER, "Rule R1 cannot be applied to edge: " + eWP);
				}
			}
			return false;
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "Label Modification R1: start.");
			}
		}

		/*
		 * After some test, I verified that analyzing labeled value map and labeled upper-case map separately is not more efficient than
		 * making a union of them and analyzing then.
		 */
		final ObjectSet<Entry<Label>> WPentrySet = eWP.getLabeledValueMap().entrySet();
		for (final Entry<Label> entryWP : WPentrySet) {
			final Label alpha = entryWP.getKey();
			if (alpha == null || !alpha.contains(p)) {
				continue;
			}
			final int w = entryWP.getIntValue();
			// It is necessary to re-check if the value is still present. Verified that it is necessary on Nov, 26 2015
			if (w < 0) {//w == Constants.INT_NULL is redundant, but I left it as a record of condition.
				continue;
			}

			final Label alphaPrime = alpha.remove(p);

			// Prepare the log message now with old values of the edge. If R0 modifies, then we can log it correctly.
			String logMessage = null;
			if (Debug.ON) {
				if (LOG.isLoggable(Level.FINER)) {
					logMessage =
						"R1 simplifies a label of edge " + eWP.getName() + ":\nsource: " + nW.getName() + " ---"
						+ pairAsString(alpha, w) + "---> " + nObs.getName() + "\nresult: " + nW.getName()
						+ " ---" + pairAsString(alphaPrime, w) + "---> " + nObs.getName();
				}
			}

			mergeStatus = eWP.mergeLabeledValue(alphaPrime, w);
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
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "Label Modification R1: end.");
			}
		}
		return ruleApplied;
	}

	/**
	 * Apply zR2 rule assuming instantaneous reaction and a streamlined network.<br>
	 *
	 * <pre>
	 *       v,pβ            w,𝛾
	 * Y &lt;------------Z &lt;------------ Obs?
	 * adds
	 *          v,β
	 * Y &lt;------------ Z
	 * Value w &lt; 0, v &lt; -w
	 * </pre>
	 *
	 * @param nY  node destination of the edge to simplify
	 * @param eZY edge to simplify
	 *
	 * @return true if a rule has been applied.
	 */
	// Visibility is package because there is Junit Class test that checks this method.
	boolean labelModificationqR2(final LabeledNode nY, final CSTNUEdge eZY) {

		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "Label Modification R2: start.");
			}
		}
		boolean ruleApplied = false;
		final ObjectSet<Label> eZYLabelSet = eZY.getLabeledValueMap().keySet();
		if (eZYLabelSet.isEmpty()) {
			return false;
		}

		final LabeledNode Z = g.getZ();
		assert Z != null;
		final ObjectList<CSTNUEdge> Obs2ZEdges = getEdgeFromObserversToNode(Z);

		// determine all the literal present in all labeled value in the edge Z-->Y
		Label allLiteralsSZ = Label.emptyLabel;
		for (final Label l : eZYLabelSet) {
			allLiteralsSZ = allLiteralsSZ.conjunctionExtended(l);
		}

		// check each edge from an observer to Z.
		for (final CSTNUEdge eObsZ : Obs2ZEdges) {
			final LabeledNode nObs = g.getSource(eObsZ);
			if (nObs == nY) {
				continue;
			}

			assert nObs != null;
			final char p = nObs.getPropositionObserved();

			if (!allLiteralsSZ.contains(p)) {
				// no label in nS-->Z contain any literal of p.
				continue;
			}

			// all labels from current Obs
			final ObjectSet<Entry<Label>> eObsZEntrySet = eObsZ.getLabeledValueSet();
			for (final Object2IntMap.Entry<Label> entryObsZ : eObsZEntrySet) {// entrySet read-only
				final int w = entryObsZ.getIntValue();
				if (w >= 0) {
					continue;
				}

				for (final Entry<Label> entry : eZY.getLabeledValueSet()) {
					final Label betaConObs = entry.getKey();
					if (betaConObs == null || !betaConObs.contains(p)) {
						continue;
					}
					final int v = entry.getIntValue();
					if (v == Constants.INT_NULL) {
						// the value has been removed in a previous merge! Verified that it is necessary on Nov, 26 2015
						continue;
					}

					final Label newLabel = betaConObs.remove(p);
					if (newLabel == null) {
						continue;
					}
					final int max = newValueInR3qR3(v, w);

					final boolean localRuleApplied = eZY.mergeLabeledValue(newLabel, max);

					if (localRuleApplied) {
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								LOG.log(Level.FINER,
								        "R2 adds a labeled value to edge " + eZY.getName() + ":\n" + "source: "
								        + nObs.getName() + " ---" + pairAsString(entryObsZ.getKey(), w)
								        + "---> " + Z.getName() + " ---" + pairAsString(betaConObs, v) + "--->"
								        + nY.getName() + "\nresult: add " + Z.getName() + " ---" + pairAsString(
									        newLabel, max) + "---> " + nY.getName());
							}
						}
						checkStatus.r3calls++;
						ruleApplied = true;
					}
				}
			}
		}
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "Label Modification R2: end.");
			}
		}
		return ruleApplied;
	}

	/**
	 * Apply A^+, or A^+-Parameter of paper about Parameter-CSTNU.
	 *
	 * <pre>
	 * 1) CASE A^+
	 *        v,β           u,α
	 * W==Z ------------&gt; Y ------------&gt; X
	 * adds
	 *     u+v,𝛾
	 * Z ------------------------------&gt; X
	 * 𝛾=αβ has to be consistent.
	 * if X is parameter, set 𝛾 empty in the generated edge
	 *
	 * 2) CASE A^+-Parameter
	 *        v,β           ℵ:u,α
	 * W==P ------------&gt; Y ------------&gt; X
	 * adds
	 *     u+v,𝛾
	 * P ------------------------------&gt;X
	 * P is a parameter, 𝛾=αβ has to be consistent.
	 * ℵ can be empty.
	 * if X is parameter or Z, set 𝛾 empty in the generated edge
	 * </pre>
	 *
	 * @param nX  node
	 * @param nY  node
	 * @param nW  node
	 * @param eYX CANNOT BE NULL
	 * @param eWY CANNOT BE NULL
	 * @param eWX CANNOT BE NULL
	 *
	 * @return true if a reduction is applied at least
	 */
	boolean ruleAPlus_AparameterPlus(final LabeledNode nW, final LabeledNode nY, final LabeledNode nX,
	                                 final CSTNUEdge eWY, final CSTNUEdge eYX, final CSTNUEdge eWX) {

		boolean ruleApplied = false;
		final LabeledNode Z = g.getZ();
		final boolean nWisZ = nW == Z;
		final boolean nWisParameter = nW.isParameter();
		if (!nWisZ && !nWisParameter) {
			return false;
		}
		final ObjectSet<Entry<Label>> WYLabeledValueSet = eWY.getLabeledValueSet();
		final LabeledALabelIntTreeMap YXAllLabeledValueMap = eYX.getAllUpperCaseAndLabeledValuesMaps();
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "A^+ + A^+-parameter: starting...");
			}
		}

		final boolean nXisParameter = nX.isParameter();
		// 1) CASE A^1+
		if (nWisZ) {
			for (final Object2IntMap.Entry<Label> entryWY : WYLabeledValueSet) {
				final int v = entryWY.getIntValue();
				final Label beta = entryWY.getKey();

				for (final Object2IntMap.Entry<Label> entryYX : YXAllLabeledValueMap.get(ALabel.emptyLabel)
					.entrySet()) {// entrySet read-only
					final Label alpha = entryYX.getKey();
					Label alphaBeta = alpha.conjunction(beta);
					if (alphaBeta == null) {
						continue;
					}

					final int u = entryYX.getIntValue();
					final int sum = Constants.sumWithOverflowCheck(u, v);

					if (sum >= 0 && nW == nX) {
						//positive loop are meaningless.
						continue;
					}
					if (nXisParameter) {
						alphaBeta = Label.emptyLabel;
					}
					final int oldValue = eWX.getValue(alphaBeta);
					if ((oldValue != Constants.INT_NULL) && (sum >= oldValue)) {
						// value is stored only if it is more negative than the current one.
						continue;
					}

					String logMsg = null;
					if (Debug.ON) {
						final String oldXW = eWX.toString();
						logMsg = "A+ rule applied to edge " + oldXW + ":\n" + "detail: " + nW.getName() + " ---"
						         + pairAsString(beta, v) + "--->" + nY.getName() + "---" + pairAsString(alpha, u)
						         + "--->" + nX.getName() + "\nresult: " + nW.getName() + " ---" +
						         pairAsString(alphaBeta,
						                      sum) + "--->" + nX.getName() + "; old value: " +
						         Constants.formatInt(oldValue);
					}

					final boolean mergeStatus = eWX.mergeLabeledValue(alphaBeta, sum);

					if (mergeStatus) {
						ruleApplied = true;
						checkStatus.labeledValuePropagationCalls++;
						if (Debug.ON) {
							if (LOG.isLoggable(Level.FINER)) {
								LOG.log(Level.FINER, logMsg);
							}
						}

						if (checkAndManageIfNewLabeledValueIsANegativeLoop(sum, nX, nW, eWX, checkStatus)) {
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
		// TODO checked till here
		// 2) A-parameter
		if (nWisParameter) {
			//			nXisParameter = nXisParameter || nX == Z;
			for (final ALabel aleph : YXAllLabeledValueMap.keySet()) {
				for (final Object2IntMap.Entry<Label> entryYX : YXAllLabeledValueMap.get(aleph).entrySet()) {
					final Label alpha = entryYX.getKey();
					final int u = entryYX.getIntValue();

					for (final Object2IntMap.Entry<Label> entryWY : WYLabeledValueSet) {
						final Label beta = entryWY.getKey();
						final Label alphaBeta = alpha.conjunction(beta);
						if (alphaBeta == null) {
							continue;
						}

						final int v = entryWY.getIntValue();
						final int sum = Constants.sumWithOverflowCheck(u, v);

						if (sum >= 0 && nW == nX) {
							//positive loop are meaningless.
							continue;
						}

						final int oldValue = eWX.getValue(alphaBeta);
						if ((oldValue != Constants.INT_NULL) && (sum >= oldValue)) {
							// value is stored only if it is more negative than the current one.
							continue;
						}

						String logMsg = null;
						if (Debug.ON) {
							final String oldWX = eWX.toString();
							logMsg = "A^+-parameter rule applied to edge " + oldWX + ":\n" + "detail: " + nW.getName()
							         + " ---" + pairAsString(beta, v) + "--->" + nY.getName() + "---"
							         + upperCaseValueAsString(aleph, u, alpha) + "--->" + nX.getName() + "\nresult: "
							         + nW.getName() + " ---" + pairAsString(alphaBeta, sum) + "--->" + nX.getName()
							         + "; old value: " + Constants.formatInt(oldValue);
						}

						final boolean mergeStatus = eWX.mergeLabeledValue(alphaBeta, sum);

						if (mergeStatus) {
							ruleApplied = true;
							getCheckStatus().zExclamationRuleCalls++;
							if (Debug.ON) {
								if (LOG.isLoggable(Level.FINER)) {
									LOG.log(Level.FINER, logMsg);
								}
							}

							if (checkAndManageIfNewLabeledValueIsANegativeLoop(sum, nX, nW, eWX, checkStatus)) {
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
				LOG.log(Level.FINER, "A^+ + A^+-parameter: finished.");
			}
		}
		return ruleApplied;
	}

	/**
	 * Apply B^+, or B^+-Parameter of paper about Parameter-CSTNU.
	 *
	 * <pre>
	 * 1) CASE B^+ (W==Z) or CASE CASE B^+-parameter (W==parameter)
	 *        v,β           C:u,α
	 * W ------------&gt; C ------------&gt; A
	 * adds
	 *     u+v,𝛾
	 * Z ------------------------------&gt; A
	 * 𝛾=αβ has to be consistent.
	 * </pre>
	 *
	 * @param nW  node
	 * @param nC  node
	 * @param nA  node
	 * @param eCA CANNOT BE NULL
	 * @param eWC CANNOT BE NULL
	 * @param eWA CANNOT BE NULL
	 *
	 * @return true if a reduction is applied at least
	 */
	boolean ruleBPlus_BparameterPlus(final LabeledNode nW, final LabeledNode nC, final LabeledNode nA,
	                                 final CSTNUEdge eWC, final CSTNUEdge eCA, final CSTNUEdge eWA) {

		if (!eCA.isContingentEdge()) {
			return false;
		}
		final LabeledNode Z = g.getZ();
		final boolean nWisZ = nW == Z;
		final boolean nWisParameter = nW.isParameter();
		if (!nWisZ && !nWisParameter) {
			return false;
		}

		final LabeledIntMap CXUpperCaseTree = eCA.getUpperCaseValueMap().get(nC.getALabel());
		if (CXUpperCaseTree == null || CXUpperCaseTree.size() != 1) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Searching for a contingent upper case value. Found: " + CXUpperCaseTree);
			}
			return false;
		}
		final Entry<Label> upperCase = CXUpperCaseTree.entrySet().iterator().next();
		final int u = upperCase.getIntValue();
		if (u == Constants.INT_NULL) {
			return false;
		}
		final Label alpha = upperCase.getKey();

		boolean ruleApplied = false;
		final ObjectSet<Entry<Label>> WCLabeledValueSet = eWC.getLabeledValueSet();
		if (Debug.ON) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.log(Level.FINER, "B^+ + B^+-parameter: starting...");
			}
		}

		for (final Object2IntMap.Entry<Label> entryWY : WCLabeledValueSet) {
			final int v = entryWY.getIntValue();
			final Label beta = entryWY.getKey();
			final Label alphaBeta = alpha.conjunction(beta);
			if (alphaBeta == null)// extends the rule
			{
				continue;
			}

			final int sum = Constants.sumWithOverflowCheck(u, v);
			final int oldValue = eWA.getValue(alphaBeta);
			if ((oldValue != Constants.INT_NULL) && (sum >= oldValue)) {
				// value is stored only if it is more negative than the current one.
				continue;
			}

			String logMsg = null;
			if (Debug.ON) {
				final String oldXW = eWA.toString();
				logMsg = "B+ rule applied to edge " + oldXW + ":\n" + "detail: " + nW.getName() + " ---" + pairAsString(
					beta, v) + "--->" + nC.getName() + "---" + upperCaseValueAsString(nC.getALabel(), u, alpha)
				         + "--->" + nA.getName() + "\nresult: " + nW.getName() + " ---" + pairAsString(alphaBeta, sum)
				         + "--->" + nA.getName() + "; old value: " + Constants.formatInt(oldValue);
			}

			final boolean mergeStatus = eWA.mergeLabeledValue(alphaBeta, sum);

			if (mergeStatus) {
				ruleApplied = true;
				checkStatus.labeledValuePropagationCalls++;
				if (Debug.ON) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.log(Level.FINER, logMsg);
					}
				}

				if (checkAndManageIfNewLabeledValueIsANegativeLoop(sum, nA, nW, eWA, checkStatus)) {
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
				LOG.log(Level.FINER, "B^+ + B^+-parameter: finished.");
			}
		}
		return ruleApplied;
	}
}
