// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
/*
 * Translator to the Time Game Automata (TIGA) model.
 */
package it.univr.di.cstnu.util;

import com.bpodgursky.jbool_expressions.Expression;
import com.bpodgursky.jbool_expressions.parsers.ExprParser;
import com.bpodgursky.jbool_expressions.rules.RuleSet;
import it.unimi.dsi.fastutil.objects.ObjectRBTreeSet;
import it.univr.di.cstnu.algorithms.CSTNU;
import it.univr.di.cstnu.algorithms.WellDefinitionException;
import it.univr.di.cstnu.graph.*;
import it.univr.di.labeledvalue.Constants;
import it.univr.di.labeledvalue.Label;
import it.univr.di.labeledvalue.Literal;
import org.kohsuke.args4j.*;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Actor class that transforms a CSTNU instance into a UppaalTiga Time Game Automate schema.<br> It is sufficient to
 * build an CSTNU2UppaalTiga object giving a TNGraph instance that represents the network of a CSTNU instance and an
 * output stream where the result must be sent (see
 * {@link CSTNU2UppaalTiga#CSTNU2UppaalTiga(TNGraph, PrintStream)}).<br> Then, invoking
 * {@link #translate()}, the result is sent to the specified output stream.
 *
 * @author posenato
 * @version $Rev: 732 $
 */
@SuppressWarnings({"DynamicRegexReplaceableByCompiledPattern", "RedundantEscapeInRegexReplacement"})
public class CSTNU2UppaalTiga {
	/**
	 * Token to represent logic AND in Tiga expression.
	 */
	static final String AND = " && ";
	/**
	 * Token to represent logic NOT in Tiga expression.
	 */
	static final String NOT = "!";

	// static final String VERSIONandDATE = "1.6.1, April, 30 2014";
	// static final String VERSIONandDATE = "1.6.2, April, 30 2015";
	// static final String VERSIONandDATE = "1.6.3, December, 30 2015";
	// static final String VERSIONandDATE = "1.64, June, 09 2019";// Edge re-factoring
	/**
	 * Token to represent logic OR in Tiga expression.
	 */
	static final String OR = " || ";
	/**
	 * Gives the version of the file
	 */
	static final String VERSIONandDATE = "1.65, January, 13 2021";// Fixed file encoding
	/**
	 * class logger
	 */
	static final Logger LOG = Logger.getLogger("CSTNU2UppaalTiga");
	/**
	 * Name of controller state/node
	 */
	@Option(name = "--controller", usage = "Name of controller node", metaVar = "agnes")
	private String AGNES = "agnes";
	private SortedSet<Contingent> contingentEdge;
	/**
	 * Contains all CSTNU contingent nodes: a CSTNU node is contingent if it is destination of a contingent constraint.
	 */
	private SortedSet<LabeledNode> contingentNode;
	/**
	 * CSTNU tNGraph to translate
	 */
	private TNGraph<CSTNUEdge> cstnuGraph;
	/**
	 * Document representing DOM of TIGA
	 */
	private Document doc;

	// /**
	// * Build an obs node name for a proposition.
	// *
	// * @param n node
	// * @return the obs node name associated to the l.
	// */
	// private static String getObsNodeName(LabeledNode n) {
	// if (n == null || n.getObservable() == null) return "";
	// return "n" + n.getObservable();
	// }
	/**
	 * The input file containing the CSTNU tNGraph in GraphML format.
	 */
	@Argument(required = true, usage = "input file. Input file has to be a CSTNU tNGraph in GraphML format.", metaVar = "CSTNU_file_name")
	private File fInput;
	/**
	 * Output file where to write the XML representing UPPAAL TIGA automata.
	 */
	@Option(name = "-o", aliases = "--output", usage = "output to this file. If file is already present, it is overwritten. If this parameter is not present, then the output is sent to the std output.", metaVar = "UPPAALTIGA_file_name")
	private File fOutput;
	/**
	 * Contains all CSTNU free nodes: a CSTNU node is free if it does not observe a proposition, and it is source of any
	 * edge, or it is destination of a non-contingent constraint.
	 */
	private SortedSet<LabeledNode> freeNode;
	/**
	 * Name of goal state/node.
	 */
	@Option(name = "--goal", usage = "Name of Goal node", metaVar = "GOAL")
	private String GOAL = "goal";
	/**
	 * Name of go state/node.
	 */
	@Option(name = "--go", usage = "Name of Go node", metaVar = "GO")
	private String GO = "go";
	/**
	 * Contains all CSTNU observation nodes: a CSTNU node is an observation if its execution determines the value of a
	 * boolean proposition associated to the node.
	 */
	private SortedSet<LabeledNode> obsNode;
	/**
	 * Contains all labeled constraint present into CSTNU constraints organized by label.
	 */
	private TreeMap<Label, HashSet<Constraint>> allConstraintsByLabel;
	/**
	 * Output stream to fOutput
	 */
	private PrintStream output;
	/**
	 * Name of loop clock.
	 */
	@Option(name = "--loop", usage = "Name of loop clock", metaVar = "t__Delta")
	private String tDelta = "t__Delta";
	/**
	 * Name of global clock.
	 */
	@Option(name = "--global", usage = "Name of global clock", metaVar = "t__G")
	private String tG = "t__G";
	/**
	 * Name of environment state/node
	 */
	@Option(name = "--environment", usage = "Name of environment node", metaVar = "vera")
	private String VERA = "vera";
	/**
	 * Software Version.
	 */
	@Option(name = "-v", aliases = "--version", usage = "Version")
	private boolean versionReq;
	/**
	 * Parameter for asking to determine a compact version (with less state) of the automata.
	 */
	@Option(name = "-compact", usage = "Translate using the minimal number of states")
	private boolean compact;

	/**
	 * Build a clock name: "t" followed by node name cleaned off not allowed chars.
	 *
	 * @param n node
	 * @return clock name associated to the node
	 */
	static String getClockName(LabeledNode n) {
		return "t" + removeCharNotAllowed(n.getName());
	}

	/**
	 * Returns the variable name "x" followed by node name cleaned off not allowed chars.
	 *
	 * @param n the input node
	 * @return executed variable name associated to the node
	 */
	private static String getExecutedName(LabeledNode n) {
		return "x" + removeCharNotAllowed(n.getName());
	}

	/**
	 * Build a proposition name.
	 *
	 * @param n node
	 * @return the proposition associated to the l.
	 */
	private static String getPropositionName(LabeledNode n) {
		if (n == null || n.getPropositionObserved() == Constants.UNKNOWN) {
			return "";
		}
		return String.valueOf(n.getPropositionObserved());
	}

	/**
	 * Return an id for the template id using the name of the tNGraph.
	 *
	 * @param g the tNGraph
	 * @return a cleaned Tga name
	 */
	private static String getTgaName(TNGraph<CSTNUEdge> g) {
		String name = removeCharNotAllowed(g.getName());
		if (name.matches("[0-9]+.+")) {
			name = "g" + name;
		}
		return name;
	}

	/**
	 * Utility class to represent a contingent link parameters.
	 */
	private static class Contingent implements Comparable<Contingent> {
		final LabeledNode dest;
		final int lower;
		final LabeledNode source;
		final int upper;

		/**
		 * @param s source node
		 * @param l lower value
		 * @param u upper value
		 * @param d destination node
		 */
		Contingent(LabeledNode s, int l, int u, LabeledNode d) {
			if (l == Constants.INT_NULL || u == Constants.INT_NULL) {
				throw new IllegalArgumentException("Integer values cannot be null!");
			}
			source = s;
			dest = d;
			lower = l;
			upper = u;
		}

		@Override
		public int compareTo(Contingent o) {
			long v = source.compareTo(o.source);
			if (v != 0) {
				return v > 0 ? 1 : -1;
			}
			v = dest.compareTo(o.dest);
			if (v != 0) {
				return v > 0 ? 1 : -1;
			}
			v = (long) lower - o.lower;
			if (v != 0) {
				return v > 0 ? 1 : -1;
			}
			v = (long) upper - o.upper;
			return v >= 0 ? (v == 0 ? 0 : 1) : -1;
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (!(o instanceof Contingent c))//pattern variable arrived with Java 14
			{
				return false;
			}
			return source.equalsByName(c.source)
			       && dest.equalsByName(c.dest)
			       && lower == c.lower
			       && upper == c.upper;
		}

		@SuppressWarnings("ObjectInstantiationInEqualsHashCode")
		@Override
		public int hashCode() {
			final String s = source.getName() + '-' + dest.getName() + lower + '-' + upper;
			return s.hashCode();
		}
	}

	/**
	 * Reads a CSTNU file and converts it into <a href="http://people.cs.aau.dk/~adavid/tiga/index.html">UPPAAL TIGA</a>
	 * format.
	 *
	 * @param args an array of {@link java.lang.String} objects.
	 * @throws java.io.IOException                            if any.
	 * @throws javax.xml.parsers.ParserConfigurationException if any.
	 * @throws org.xml.sax.SAXException                       if any.
	 */
	public static void main(String[] args) throws IOException, ParserConfigurationException, SAXException {
		LOG.finest("Start...");
		final CSTNU2UppaalTiga translator = new CSTNU2UppaalTiga();

		if (!translator.manageParameters(args)) {
			return;
		}
		LOG.finest("Parameters ok!");
		if (translator.versionReq) {
			System.out.print("CSTNU2UppaalTiga " + VERSIONandDATE +
			                 "\nSPDX-License-Identifier: LGPL-3.0-or-later, Roberto Posenato.\n");
			return;
		}

		LOG.finest("Loading tNGraph...");
		if (!translator.loadCSTNU(translator.fInput)) {
			return;
		}
		LOG.finest("TNGraph loaded!");

		LOG.finest("Translating tNGraph...");
		translator.translate();
		LOG.finest("TNGraph translated and saved!");
	}

	/**
	 * Remove all character that cannot be part of a TIGA identifier.
	 *
	 * @param s the input string
	 * @return a cleaned string
	 */
	private static String removeCharNotAllowed(String s) {
		return s.replaceAll("[-?. ]", "_");
	}

	/**
	 * Constructor for CSTNU2UppaalTiga.
	 *
	 * @param g a {@link it.univr.di.cstnu.graph.TNGraph} object. Such an object is used directly, not copied.
	 * @param o a {@link java.io.PrintStream} object. Such an object is used directly, not copied.
	 */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "I use instead of copy them for efficiency reason.")
	public CSTNU2UppaalTiga(TNGraph<CSTNUEdge> g, PrintStream o) {
		this();
		if (o == null || g == null) {
			throw new IllegalArgumentException("One parameter is null!");
		}
		output = o;
		cstnuGraph = g;
		if (!checkCSTNUSyntax()) {
			throw new IllegalArgumentException("CSTNU is not well-formed!");
		}
	}

	/*
	 * parameter ignore
	 *
	 * @Option(name = "-ignore", usage = "")
	 *                  private String ignore = "";
	 */

	/**
	 * Default constructor not accessible
	 */
	private CSTNU2UppaalTiga() {
	}

	/**
	 * @return true if the CSTNU is well written, false otherwise.
	 */
	private boolean checkCSTNUSyntax() {
		LOG.finest("Checking tNGraph...");
		final CSTNU cstnuCheck = new CSTNU(cstnuGraph);
		try {
			cstnuCheck.initAndCheck();
		} catch (IllegalArgumentException | WellDefinitionException e) {
			System.err.println(e.getMessage());
			return false;
		}
		prepareAuxiliaryCSTNUData();
		LOG.finest("TNGraph checked!");
		return true;
	}

	/**
	 * Jbool_expression represents Expression using identifiers escaped by `, logical NOT as '!', logical AND as '
	 * &amp;' and logical OR as '|'. For example, "!`a` &amp; `(D-A &lt;=)` | `a`".
	 *
	 * @param expr jbool_expression
	 * @param not  string representing not. If null, it is assumed {@link #NOT}
	 * @param and  representing not. If null, it is assumed {@link #AND}
	 * @param or   representing not. If null, it is assumed {@link #OR} @return Tiga representation of
	 *             orbitalFormulaText.
	 * @return Jbool_expression represents Expression using identifiers escaped by `, not as '!', and as '&amp;' and or
	 * as '!'.
	 */
	@SuppressWarnings("SameParameterValue")
	private static String jbool2TigaExpr(String expr, String not, String and, String or) {
		if (expr == null || expr.isEmpty()) {
			return "";
		}

		if (not == null) {
			not = NOT;
		}
		if (and == null) {
			and = AND;
		}
		if (or == null) {
			or = OR;
		}

		expr = expr.replaceAll("!", not);
		expr = expr.replaceAll(" & ", and);
		expr = expr.replaceAll(" \\| ", or);
		expr = expr.replaceAll("`", "");

		return expr;
		// String allowedTokenRE = "-\\w\\s\\.";
		// =<\\(([" + allowedTokenRE + "]+),([" + allowedTokenRE + "]+)\\)", "( ($1) <= ($2) )");
	}

	/**
	 * Convert a CSTNU TNGraph g into a Timed Game Automata in the UPPAAL TIGA format.
	 *
	 * @return true if the translation has been done and saved.
	 */
	public boolean translate() {
		try {
			final DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			docFactory.setNamespaceAware(true);
			docFactory.setValidating(true);
			docFactory.setExpandEntityReferences(false);
			final DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

			final DocumentType docType = docBuilder.getDOMImplementation().createDocumentType(
				"nta",
				"-//Uppaal Team//DTD Flat System 1.1//EN",
				"https://www.it.uu.se/research/group/darts/uppaal/flat-1_1.dtd");
			doc = docBuilder.getDOMImplementation().createDocument(null, "nta", docType);

			final Element rootElement = doc.getDocumentElement();

			// global declaration element
			// rootElement.appendChild(buildDeclarationElement(doc, g));

			// template
			rootElement.appendChild(buildTemplateElement());

			// Get the implementations
			final DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();

			final DOMImplementationLS implLS = (DOMImplementationLS) registry.getDOMImplementation("LS");
			// Prepare the output
			final LSOutput domOutput = implLS.createLSOutput();
			domOutput.setEncoding(StandardCharsets.UTF_8.name());
			domOutput.setByteStream(output);
			// Prepare the serialization
			final LSSerializer domWriter = implLS.createLSSerializer();
			domWriter.setNewLine("\r\n");
			final DOMConfiguration domConfig = domWriter.getDomConfig();
			domConfig.setParameter("format-pretty-print", true);
			domConfig.setParameter("element-content-whitespace", true);
			domConfig.setParameter("cdata-sections", Boolean.TRUE);
			// And finally, write
			domWriter.write(doc, domOutput);

			if (fOutput != null) {
				output.close();
				final String name = fOutput.getAbsolutePath().replace(".xml", ".q");
				output = new PrintStream(name, StandardCharsets.UTF_8);
			}
			output.println("control: A[] not _processMain." + GOAL);
		} catch (ParserConfigurationException | IOException | ClassNotFoundException | InstantiationException |
		         IllegalAccessException | ClassCastException pce) {
			LOG.severe(pce.getMessage());
			return false;
		}
		return true;
	}

	/**
	 * In a TGA, a location element contains the declaration of a node.
	 * <p>In the CSTNU translation, there are three locations:
	 * <ul>
	 *     <li>controller (id={@link #VERA}),</li>
	 *     <li>environment (id={@link #AGNES}), and</li>
	 *     <li>goal (id={@link #GOAL}).</li>
	 * </ul>
	 * Then, there is one location for each CSTNU observation node, (id=nodeName without?).
	 *
	 * @param template the root of the document
	 */
	private void addLocationElements(Element template) {
		final Document localDoc = template.getOwnerDocument();

		template.appendChild(buildLocationElement(AGNES, true));
		template.appendChild(buildLocationElement(VERA, false));
		template.appendChild(buildLocationElement(GOAL, false));
		template.appendChild(buildLocationElement(GO, true));

		// Add an urgent location for each proposition
		// 13/06/2014: No more!
		// for (final LabeledNode node : obsNode) {
		// template.appendChild(buildLocationElement(getObsNodeName(node), true));
		// }

		if (!compact) {
			// we add as many intermediate node GO_i as the number of significant label is.
			final int n = allConstraintsByLabel.size() - 1;// the empty label does not count!
			if (n > 0) {
				for (int i = 1; i < n; i++) {
					template.appendChild(buildLocationElement(GO + i, true));
				}
			}
		}
		// The declaration of the initial node
		final Element init = localDoc.createElement("init");
		init.setAttribute("ref", AGNES);
		template.appendChild(init);
	}

	/**
	 * In a TGA, a transition element contains the declaration of a transition between two nodes of the automaton. In
	 * the CSTNU translation, there are 8 kinds of transition.
	 *
	 * @param template the input element
	 */
	private void addTransitionElements(Element template) {
		// first of all, the always present transitions
		template.appendChild(doc.createComment("GAIN transition"));
		// (c1) The transition to guarantee to Agnes to gain the control (GAIN)
		// It is only one: (vera, tDelta > 0, gain, "", agnes)
		template.appendChild(buildTransitionElement(VERA, tDelta + " > 0", "gain", "", AGNES, false));

		template.appendChild(doc.createComment("PASS transition"));
		// (c2) The transition to return the control to Vera
		// It is only one: (vera, tDelta > 0, pass, "", agnes)
		template.appendChild(buildTransitionElement(AGNES, "", "pass", tDelta + " := 0", VERA, false));

		template.appendChild(doc.createComment("Transitions for clock setting"));
		// 1) Set of transaction
		// For each free time-point X there is a non-controllable transaction (agnes, !xX, set_X, xX:=true, tX:=0,
		// agnes)
		for (final LabeledNode n : freeNode) {
			final String exec = getExecutedName(n);
			template.appendChild(
				buildTransitionElement(AGNES, "!" + exec, "set_" + exec, exec + " := true," + getClockName(n) + " := 0",
				                       AGNES, false));
		}

		template.appendChild(doc.createComment("Transitions for proposition setting"));
		// 2) Set of transaction
		// For each Observation node P, there are four transactions
		// 2.1) (agnes, !xP, set_P, xP:=true, tP:=0, agnes) not controllable, for the clock
		// 2.2) (vera, xP && P == 0, set_P_false, P := -1, vera) controllable, to allow ENV to decide the value
		// 2.3) (vera, xP && P == 0, set_P_true, P := 1, agnes) controllable, to allow ENV to decide the value
		// 2.4) (agnes, xP && P == 0 && tDelta > 0, P_not_set, "", goal) not controllable, to force ENV to decide the value
		for (final LabeledNode n : obsNode) {
			final String exec = getExecutedName(n);
			final String prop = getPropositionName(n);
			template.appendChild(buildTransitionElement(AGNES, "!" + exec + AND + prop + " == 0", "set_" + prop,
			                                            exec + " := true, " + getClockName(n)
			                                            + " := 0", AGNES, false));
			template.appendChild(
				buildTransitionElement(VERA, exec + AND + prop + " == 0", "set_" + prop + "_false",
				                       prop + " := -1, " + tDelta + " := 0",
				                       VERA,
				                       true));
			template.appendChild(
				buildTransitionElement(VERA, exec + AND + prop + " == 0", "set_" + prop + "_true",
				                       prop + " := 1, " + tDelta + " := 0", VERA,
				                       true));
			template.appendChild(
				buildTransitionElement(AGNES, exec + AND + prop + " == 0" + AND + tDelta + " > 0", prop + "not_set", "",
				                       GOAL, false));
		}

		template.appendChild(doc.createComment("Transitions for contingent-constraint setting"));
		// 3) Set of transaction
		// For each contingent link (A,l,u,C) there is
		// 3.1) a transaction (vera, Sigma(tC,tA,tG), set_C, "xC:=true, tC:=0, tDelta:=0", vera) where Sigma(tC,tA,tG) := xA && !xC && (tA >= l) && (tA <=
		// u)//assign a right value to contingent point.
		// 3.2) a transition (agnes, Phi(tA,tC,tG), cvC, "", goal) where Phi(tA,tC,tG) := xA && !xC && tA>u //the contingent has violated its upper bound!
		// Remember that in TGA contingent point are chosen by a controllable transaction that can be 'blocked' by tDelta. So, if a controllable transaction for
		// tC cannot be executed, then the system verifies later with 3.2 the violation.
		for (final Contingent c : contingentEdge) {
			final String tA = getClockName(c.source);
			final String tC = getClockName(c.dest);
			final String xA = getExecutedName(c.source);
			final String xC = getExecutedName(c.dest);

			final String sigma =
				xA + AND + "!" + xC + AND + "(" + tA + " >= " + c.lower + ")" + AND + "(" + tA + " <= " + c.upper + ")";
			template.appendChild(
				buildTransitionElement(VERA, sigma, "set_" + xC, xC + " := true, " + tC + " := 0, " + tDelta + " := 0",
				                       VERA, true));

			final String phi = xA + AND + "!" + xC + AND + "(" + tA + " > " + c.upper + ")";
			template.appendChild(buildTransitionElement(AGNES, phi, "cv_" + tC, "", GOAL, false));
		}

		template.appendChild(doc.createComment("WIN transitions"));
		/*
		 * 4) The transition for the end of the game.
		 * We split the win transition into two sets:
		 * 1) one set is made of only one uncontrollable transition (VERA, Psi(t, tB), win_unlabelled, "", go)
		 * where Psi1 = AND_{X timepoint} xX && AND_{unlabeled non-contingent constraint Y-X<k} (tY-tX <= k)
		 * It represents the event that all timepoints are executed and all unlabeled constraints are satisfied.
		 * Such constraint is always present.
		 * 2) other set is made considering all labeled constraints.
		 * There are two possible set constructions according to the value of 'compact' value.
		 * Before showing how to build them, remember that allLabeledConstraint is a Map that, for each label, returns all constraints with the given label.
		 * If compact is false, then for each label l (assume l is the i-th one in the lexicographical order),
		 * we put a transition for each of its literal with guard the negated literal and one transition with guard the conjunction of all associated
		 * constraints
		 * between state GO_i and GO_{i+1}, where GO_0 = GO and GO_{m} = GOAL (m=#labels)
		 * If compact is true, for each label l, the implicant "l => conjunction of all associated constraints" is built.
		 * Then, we define \Psi2 = conjunction of all obtained implicants.
		 * Then, we determine \Psi2DNF := the DNF of \Psi2
		 * Then, for each disjunction d_i of \Phi2DNF, we add the following controllable transition (go, d_i, win_labelled, "", goal)
		 * If there is no labeled constraints, we add only (go, "", win_labelled, "", goal).
		 * All this transitions have to be uncontrollable.
		 */

		// First set
		final StringBuilder psi1dirty = new StringBuilder(80);
		for (final LabeledNode n : freeNode) {
			final String exec = getExecutedName(n);
			psi1dirty.append(exec).append(AND);
		}
		for (final LabeledNode n : contingentNode) {
			final String exec = getExecutedName(n);
			psi1dirty.append(exec).append(AND);
		}
		for (final LabeledNode n : obsNode) {
			final String exec = getExecutedName(n);
			psi1dirty.append(exec).append(AND);
		}

		final StringBuilder psi2dirty = new StringBuilder(80);
		final String jboolAnd = " & ";
		final String jboolOr = " | ";
		final String jboolNot = " !";

		int labelOrdinal = 0;
		String sourceState = null;
		String destState = null;
		for (final Entry<Label, HashSet<Constraint>> entry : allConstraintsByLabel.entrySet()) {// entrySet read-only
			final Label label = entry.getKey();
			final Iterable<Constraint> constSet = entry.getValue();

			if (!compact && !label.isEmpty()) {
				sourceState = (labelOrdinal == 0) ? GO : GO + labelOrdinal;
				labelOrdinal++;
				destState = (labelOrdinal == allConstraintsByLabel.size() - 1) ? GOAL : GO + labelOrdinal;
				for (final Literal lit : label.negation()) {
					template.appendChild(buildTransitionElement(sourceState, "(" + lit.getName() + " == " +
					                                                         ((lit.isNegated()) ? "-1" : "1") + ")",
					                                            "win_labelled%d%s".formatted(labelOrdinal,
					                                                                         lit.getName()), "",
					                                            destState, false));
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("Transition added= (%s, (%s == %s), win_labelled%d%s, '', %s)".formatted(sourceState,
						                                                                                    lit.getName(),
						                                                                                    (lit.isNegated())
						                                                                                    ? "-1"
						                                                                                    : "1",
						                                                                                    labelOrdinal,
						                                                                                    lit.getName(),
						                                                                                    destState));
					}
				}
				psi2dirty.delete(0, psi2dirty.length());
			}
			for (final Constraint constr : constSet) {
				final String sourceClock = getClockName(constr.source);
				final String destClock = getClockName(constr.dest);
				final Integer value = constr.value;
				if (label.isEmpty()) {
					// First set
					// Be careful!
					// A constraint (D-S≤v) has to be translated as (tS-tD≤v) because node clock will be reset when node is executed!
					psi1dirty.append("(").append(sourceClock).append(" - ").append(destClock).append(" <= ")
						.append(value).append(")").append(AND);
				} else {
					if (!compact) {
						// Be careful!
						// A constraint (D-S≤v) has to be translated as (tS-tD≤v) because node clock will be reset when node is executed!
						psi2dirty.append("(").append(sourceClock).append(" - ").append(destClock).append(" <= ")
							.append(value).append(")").append(AND);
					} else {
						// psi2 will be transformed into DNF.
						// To convert into DNF I will use an external library: com.bpodgursky.jbool_expressions
						// Therefore it is convenient to write the expression into jbool_expressions format
						// and = &
						// or = |
						// not = !
						// identificator name = `name`
						// true = true
						// false = false
						// no other operator.
						// So, (a¬b => (A-B <= t)) has to be represented as (!`a` | 'b' | `(A-B <= t)`)
						String labelNegatedandEscaped = label.toLogicalExpr(true, jboolNot, jboolAnd, jboolOr);

						labelNegatedandEscaped =
							labelNegatedandEscaped.replaceAll("(" + Literal.PROPOSITION_RANGE + ")", "`$1`");// all to 1
						// LOG.finest("labelNegatedandEscaped= " + labelNegatedandEscaped);
						psi2dirty.append("(").append(labelNegatedandEscaped).append(jboolOr).append("`((")
							.append(sourceClock).append(" - ").append(destClock).append(") <= ").append(value)
							.append(")`)").append(jboolAnd);
					}
				}
			}
			if (!compact && !label.isEmpty()) {
				final String psi2clean = psi2dirty.substring(0, psi2dirty.length() - AND.length());
				// LOG.finest("psi2" + labelOrdinal + "= " + psi2clean);
				template.appendChild(
					buildTransitionElement(sourceState, psi2clean, "win_labelled%d".formatted(labelOrdinal), "",
					                       destState, false));
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("Transition added= (%s, %s, win_labelled%d, '', %s)".formatted(sourceState, psi2clean,
					                                                                          labelOrdinal, destState));
				}

			}
		}

		// First set
		final String psi1 = psi1dirty.substring(0, psi1dirty.length() - AND.length());
		template.appendChild(buildTransitionElement(VERA, psi1, "win_unlabelled", "", GO, false));

		// Second set when !useStatesForScenarios
		if (LOG.isLoggable(Level.FINEST)) {
			LOG.finest("psi2dirty= " + psi2dirty);
		}
		if (allConstraintsByLabel.size() == 1) {
			// there are no labeled constraints!
			template.appendChild(buildTransitionElement(GO, "", "win_labelled", "", GOAL, false));
		} else {
			if (compact) {
				final String psi2 = psi2dirty.substring(0, psi2dirty.length() - jboolAnd.length());
				final String psi2DNF;
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("psi2= " + psi2);
				}

				// I use jbool_expression to obtain the DNF of psi2
				final Expression<String> psi2Jbool = ExprParser.parse(psi2);

				String psi2DnfJbool = RuleSet.toSop(psi2Jbool).toString();
				psi2DnfJbool =
					psi2DnfJbool.substring(1, psi2DnfJbool.length() - 1);// there are an initial and a final () !
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("psi2DnfJbool= " + psi2DnfJbool);
				}

				// A literal "`l`" has to be transformed to "(l == 1)"
				// while "!`l`" to "(l == -1)"
				psi2DnfJbool = psi2DnfJbool.replaceAll("`(" + Literal.PROPOSITION_RANGE + ")`",
				                                       "\\($1 == 1\\)");// all are set to 1
				psi2DnfJbool = psi2DnfJbool.replaceAll("!\\((" + Literal.PROPOSITION_RANGE + ") == 1",
				                                       "\\($1 == -1");// while we set to -1 who is negated!

				psi2DNF = jbool2TigaExpr(psi2DnfJbool, null, null, " | ");
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("psi2DNF= " + psi2DNF);
				}

				int i = 0;
				for (final String psi2Disjunct : psi2DNF.split(" \\| ")) {
					template.appendChild(
						buildTransitionElement(GO, psi2Disjunct, "win_labelled_" + i++, "", GOAL, false));
				}
			}
		}
	}

	/**
	 * In a TGA, declaration element contains the declaration of all clocks and variables. There are:
	 * <ol>
	 * <li>For each CSTNU node X, one clock 'tX'; a global clock and a loop clock, called delta clock.
	 * <li>For each CSTNU node X, one bool var 'xX' that says if X has been executed or not.
	 * <li>For each observed proposition P, one integer var 'pP' assuming only value 0, for no set, -1, for false and 1 for true. .
	 * </ol>
	 *
	 * @return the element representing the clock declaration.
	 */
	private Element buildDeclarationElement() {
		final Element declaration = doc.createElement("declaration");

		final StringBuilder clocks = new StringBuilder("clock " + tG + ", " + tDelta);
		final StringBuilder executed = new StringBuilder(80);
		final StringBuilder obs = new StringBuilder(32);
		for (final LabeledNode node : freeNode) {
			clocks.append(", ").append(getClockName(node));
			executed.append(", ").append(getExecutedName(node));
		}
		for (final LabeledNode node : contingentNode) {
			clocks.append(", ").append(getClockName(node));
			executed.append(", ").append(getExecutedName(node));
		}
		for (final LabeledNode node : obsNode) {
			clocks.append(", ").append(getClockName(node));
			executed.append(", ").append(getExecutedName(node));
			obs.append(", ").append(getPropositionName(node)).append(" = 0");
		}
		clocks.append(';');
		if (!executed.isEmpty()) {
			executed.replace(0, 1, "bool"); // remove first , and put "bool " declaration
			executed.append(';');
		}
		if (!obs.isEmpty()) {
			obs.replace(0, 1, "int [-1,1]"); // remove first , and put "int [-1,1] " declaration
			obs.append(';');
		}

		declaration.appendChild(doc.createTextNode(clocks + "\n" + executed + "\n" + obs));
		return declaration;
	}

	/**
	 * Build a location element with id and name = id and urgent child if urgent is true.
	 *
	 * @param id     the input id
	 * @param urgent if true, it adds a child element to represent 'urgent'.
	 * @return the location element
	 */
	private Element buildLocationElement(String id, Boolean urgent) {
		final Element location = doc.createElement("location");
		location.setAttribute("id", id);
		final Element name = doc.createElement("name");
		name.appendChild(doc.createTextNode(id));
		location.appendChild(name);
		if (urgent) {
			location.appendChild(doc.createElement("urgent"));
		}
		return location;
	}

	/**
	 * In a TGA, template element contains the declaration of all nodes and transitions.
	 *
	 * @return the element representing all clock, node, and transition declaration.
	 */
	private Element buildTemplateElement() {
		final Element template = doc.createElement("template");

		// name
		final Element name = doc.createElement("name");
		name.appendChild(doc.createTextNode(getTgaName(cstnuGraph)));
		template.appendChild(name);

		// local declaration
		template.appendChild(doc.createComment("Clock and proposition declarations"));
		template.appendChild(buildDeclarationElement());

		template.appendChild(doc.createComment("LabeledNode declarations"));
		addLocationElements(template);

		template.appendChild(doc.createComment("Transition declarations"));
		addTransitionElements(template);

		final Element system = doc.createElement("system");
		system.appendChild(
			doc.createTextNode("_processMain = " + getTgaName(cstnuGraph) + "();\n\t\tsystem _processMain;"));
		template.appendChild(system);
		return template;
	}

	/**
	 * Build a transition element with source, target, guard, assignment, controllable attributes.
	 *
	 * @param source       source id
	 * @param guard        guard expression
	 * @param action       action expression
	 * @param assignment   assignment expression
	 * @param target       target id
	 * @param controllable true if the transition is controllable, false otherwise.
	 * @return the location element
	 */
	private Element buildTransitionElement(
		String source, String guard, String action, String assignment, String target, boolean controllable) {
		final Element transition = doc.createElement("transition");
		transition.setAttribute("controllable", controllable ? "true" : "false");
		transition.setAttribute("action", action);

		final Element sourceE = doc.createElement("source");
		sourceE.setAttribute("ref", source);
		transition.appendChild(sourceE);

		final Element targetE = doc.createElement("target");
		targetE.setAttribute("ref", target);
		transition.appendChild(targetE);

		Element label = doc.createElement("label");
		label.setAttribute("kind", "guard");
		label.appendChild(doc.createTextNode(guard));
		transition.appendChild(label);

		label = doc.createElement("label");
		label.setAttribute("kind", "assignment");
		label.appendChild(doc.createTextNode(assignment));
		transition.appendChild(label);

		return transition;
	}

	/**
	 * Load CSTNU file and create a TNGraph g.
	 *
	 * @param fileName the name of the file containing rhe input CSTNU
	 * @return true if the file was load successfully; false otherwise.
	 * @throws SAXException                 if the file is not in GraphML format
	 * @throws ParserConfigurationException if the parser has a configuration error
	 * @throws IOException                  if any trouble in reading the file
	 */
	private boolean loadCSTNU(File fileName) throws IOException, ParserConfigurationException, SAXException {
		final TNGraphMLReader<CSTNUEdge> graphMLReader = new TNGraphMLReader<>();
		cstnuGraph = graphMLReader.readGraph(fileName, EdgeSupplier.DEFAULT_CSTNU_EDGE_CLASS);
		return checkCSTNUSyntax();
	}

	/**
	 * Simple method to manage command line parameters using args4j library.
	 *
	 * @param args the input parameters
	 * @return false if a parameter is missing, or it is wrong. True if every parameter is given in a right format.
	 */
	@SuppressWarnings("deprecation")
	private boolean manageParameters(String[] args) {
		final CmdLineParser parser = new CmdLineParser(this);
		try {
			// parse the arguments.
			parser.parseArgument(args);

			if (!fInput.exists()) {
				throw new CmdLineException(parser, "Input file does not exist.");
			}

			if (fOutput != null) {
				if (fOutput.isDirectory()) {
					throw new CmdLineException(parser, "Output file is a directory.");
				}
				if (!fOutput.getName().endsWith(".xml")) {
					final String name = fOutput.getAbsolutePath() + ".xml";
					fOutput = new File(name);
				}
				if (fOutput.exists()) {
					if (!fOutput.delete()) {
						final String m = "File " + fOutput.getAbsolutePath() + " cannot be deleted.";
						LOG.severe(m);
						throw new IllegalStateException(m);
					}
				}
				if (!fOutput.createNewFile()) {
					LOG.warning("Cannot create " + fOutput.getName());
				}
				output = new PrintStream(fOutput, StandardCharsets.UTF_8);
			} else {
				output = System.out;
			}
		} catch (CmdLineException | IOException e) {
			// if there's a problem in the command line, you'll get this exception. this will report an error message.
			System.err.println(e.getMessage());
			System.err.println("java CSTNU2UppaalTiga [options...] arguments...");
			// print the list of available options
			parser.printUsage(System.err);
			System.err.println();

			// print option sample. This is useful some time
			System.err.println(
				"Example: java -jar CSTNU2UppaalTiga.jar" + parser.printExample(OptionHandlerFilter.REQUIRED) +
				" <CSTNU_file_name>");
			return false;
		}
		return true;
	}

	/**
	 * Classifies nodes of the cstnu tNGraph
	 */
	private void prepareAuxiliaryCSTNUData() {
		freeNode = new ObjectRBTreeSet<>();
		contingentNode = new ObjectRBTreeSet<>();
		contingentEdge = new ObjectRBTreeSet<>();
		obsNode = new ObjectRBTreeSet<>();
		allConstraintsByLabel = new TreeMap<>();
		// I put the entry for all constraints not labeled
		HashSet<Constraint> constr = new HashSet<>(100);
		allConstraintsByLabel.put(Label.emptyLabel, constr);

		// This cycle is redundant, but to avoid to consider an already considered node is more expensive that consider it more times
		final SortedSet<CSTNUEdge> edges = new ObjectRBTreeSet<>(cstnuGraph.getEdges());
		for (final CSTNUEdge e : edges) {
			final LabeledNode s = cstnuGraph.getSource(e);
			final LabeledNode d = cstnuGraph.getDest(e);
			assert s != null;
			assert d != null;
			if (e.isContingentEdge()) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("Found a contingent link: " + e);
				}
				final int lower = e.getLowerCaseValue().getValue();// This works only for CSTNU without guarded links.
				final int upper;
				if (lower != Constants.INT_NULL) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("Add contingent node: " + d);
					}
					contingentNode.add(d);
					upper = -Objects.requireNonNull(cstnuGraph.findEdge(d, s)).getMinUpperCaseValue().getValue()
						.getIntValue();
					if (upper == Constants.INT_NULL) {
						throw new IllegalArgumentException(
							"There is no a companion upper case value in edge " + cstnuGraph.findEdge(d, s) +
							" w.r.t. the edge " + e);
					}
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine(
							"Add contingent edge: (" + s.getName() + ", " + lower + ", " + upper + ", " + d.getName() +
							").");
					}
					contingentEdge.add(new Contingent(s, lower, upper, d));
				}
			} else {
				// normal or constraint edge
				if (s.getPropositionObserved() != Constants.UNKNOWN) {
					obsNode.add(s);
				} else {
					freeNode.add(s);
				}

				if (d.getPropositionObserved() != Constants.UNKNOWN) {
					obsNode.add(d);
				} else {
					freeNode.add(d);
				}
				for (final Entry<Label, Integer> entry : e.getLabeledValueMap().entrySet()) {
					// Since CSTNU has been initialized, the default value is represented as labeled value with ⊡ label.
					final Label label = Objects.requireNonNull(entry.getKey().conjunction(s.getLabel()))
						.conjunction(d.getLabel());// IT IS NECESSARY for guaranteeing all possible scenario
					// are represented.
					final Integer value = entry.getValue();
					constr = allConstraintsByLabel.computeIfAbsent(label, k -> new HashSet<>(100));
					constr.add(new Constraint(d, s, value));

				}
			}
		}
		// Free node contains contingent nodes if these last ones are destination of normal edge.
		// It is necessary to remove them.
		freeNode.removeAll(contingentNode);
		if (LOG.isLoggable(Level.FINEST)) {
			LOG.finest("freeNode set: " + freeNode.toString());
			LOG.finest("contingentNode set: " + contingentNode.toString());
			LOG.finest("obsNode set: " + obsNode.toString());
			LOG.finest("all Label set: " + allConstraintsByLabel.toString());
		}
	}

	/**
	 * Utility class to represent a constraint.
	 */
	private static class Constraint implements Comparable<Constraint> {
		final LabeledNode dest;
		final LabeledNode source;
		final int value;

		Constraint(LabeledNode d, LabeledNode s, int l) {
			if (l == Constants.INT_NULL) {
				throw new IllegalArgumentException("Integer values cannot be null!");
			}
			source = s;
			dest = d;
			value = l;
		}

		@Override
		public int compareTo(@Nonnull Constraint o) {
			if (equals(o)) {
				return 0;
			}
			long b;
			if ((b = dest.compareTo(o.dest)) != 0) {
				return (int) b;
			}
			if ((b = source.compareTo(o.source)) != 0) {
				return (int) b;
			}
			return (int) ((long) value - o.value);
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (!(o instanceof Constraint cons)) {
				return false;
			}
			return cons.source != null && cons.source.equalsByName(source)
			       && cons.dest != null && cons.dest.equalsByName(dest)
			       && cons.value != Constants.INT_NULL && cons.value == value;
		}

		@Override
		public int hashCode() {
			return ((dest != null ? dest.hashCode() : 0) + 31 * (source != null ? source.hashCode() : 0)) * 31
			       + (value != Constants.INT_NULL ? value : 0);
		}

		@Override
		public String toString() {
			// Be careful!
			// A constraint (D-S≤v) has to be translated as (tS-tD≤v) when tS and tD are clock names, because node clock will be reset when node is executed!
			return "(" + getClockName(dest) + " - " + getClockName(source) + " <= " + value + ")";
		}
	}
}
