// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.util;

import edu.uci.ics.jung.algorithms.layout.SpringLayout2;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.univr.di.cstnu.graph.*;
import it.univr.di.labeledvalue.Constants;
import it.univr.di.labeledvalue.Label;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Utility class for converting (C)STN(U) file in Luke format to GraphML format.
 *
 * @author posenato
 * @version $Rev: 732 $
 */
public class Luke2GraphML {
	/**
	 * class logger
	 */
	static final Logger LOG = Logger.getLogger(Luke2GraphML.class.getName());

	/**
	 * Version
	 */
//	static final String VERSIONandDATE = "1.1, March, 11 2016";
//	static final String VERSIONandDATE = "1.2, May, 30 2021";//just tweaking
	static final String VERSIONandDATE = "1.3, January, 04 2024";//extending to STNU
	/**
	 * Pattern for edges in CSTN file.
	 */
	private static final String regExEdgeCSTN = "EDGE \\(|,|\\): ";
	private static final Pattern PATTERN4EDGECSTN = Pattern.compile(regExEdgeCSTN);
	@SuppressWarnings("RegExpRedundantEscape")
	private static final Pattern PATTERN4EDGECSTN1 = Pattern.compile("<|,\\[|\\]>");
	private static final String regExEdgeSTNU = "\\s";
	private static final Pattern PATTERN4EDGESTNU = Pattern.compile(regExEdgeSTNU);
	@SuppressWarnings("RegExpRedundantEscape")
	private static final String regExNodeCSTN = "TP\\(|\\):[\\s\u00A0]+|,\\s+\\[|\\],\\s+|\\]";
	private static final Pattern PATTERN4NODECSTN = Pattern.compile(regExNodeCSTN);
	private static final String regExNodeSTNU = "\\s";
	private static final Pattern PATTERN4NODESTNU = Pattern.compile(regExNodeSTNU);
	/**
	 * The input file names. Each file has to contain a CSTN tNGraph in GraphML format.
	 */
	@Argument(required = true, usage = "Input file. It has to be a (C)STN(U) tNGraph in Luke's format.", metaVar = "file_name")
	private String fileNameInput;
	/**
	 * Type of the network. It is not possible to desume from the filename suffix.
	 */
	private TNGraph.NetworkType networkType;
	/**
	 *
	 */
	private Class<? extends Edge> inputEdgeImplClass;
	/**
	 * Output file where to write the CSTN in GraphML format.
	 */
	@Option(name = "-o", aliases = "--output", usage = "Output to this file in GraphML format.", metaVar = "outputFile")
	@SuppressFBWarnings(value = "UWF_NULL_FIELD", justification = "This field is set by parser.parseArgument.")
	private File fOutput;
	/**
	 *
	 */
	private File inputTNFile;
	/**
	 * Software Version.
	 */
	@Option(name = "-v", aliases = "--version", usage = "Version")
	private boolean versionReq;

	/**
	 * @param args a CSTN file in Luke's format.
	 *
	 * @throws IOException if any file cannot be read or write
	 */
	@SuppressWarnings("unchecked")
	public static void main(final String[] args) throws IOException {

		// System.out.println(Arrays.toString("<694,[A(-E)]>".split("<|,\\[|\\]>")));
		// System.out.println("<694,[A(-E)]>".split("<|,\\[|\\]>")[2].replace("(",
		// "").replace(")", "").replace("-", "¬"));

		LOG.finest("Start...");
		System.out.println("Start of execution...");
		final Checker tester = new Checker();

		final Luke2GraphML converter = new Luke2GraphML();

		if (!converter.manageParameters(args)) {
			return;
		}

		LOG.finest("Parameters ok!");
		System.out.println("Parameters ok!");
		if (converter.versionReq) {
			System.out.print(
				tester.getClass().getName() + " " + VERSIONandDATE + ". Academic and non-commercial use only.\n" +
				"Copyright © 2016-2022 Roberto Posenato");
			return;
		}

		TNGraph<? extends Edge> g = null;

		final Int2ObjectMap<LabeledNode> int2Node = new Int2ObjectOpenHashMap<>();
		int2Node.defaultReturnValue(null);

		try (final BufferedReader reader = new BufferedReader(
			new InputStreamReader(new FileInputStream(converter.inputTNFile), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("Line: " + line);
				}
				if (!line.isEmpty() && line.charAt(0) == ';'
				    || line.startsWith("--")
				    || line.startsWith("==")
				    || line.isEmpty()) {
					// it is comment, ignore
					// it is a separator, ignore
					continue;
				}
				if (line.startsWith("# KIND OF NETWORK")) {
					line = reader.readLine();
					if (line == null) {
						throw new IllegalStateException(
							"Missing the line containing the kind of the network value. Such a line must be present after '# KIND OF NETWORK' line.");
					}
					converter.networkType = getNetworkType(line);
					converter.inputEdgeImplClass = getInputEdgeImplClass(converter.networkType);
					if (converter.inputEdgeImplClass == null) {
						System.out.print(
							"Sorry, the conversion of " + converter.networkType + " is still not implemented.");
						return;
					}
					g = new TNGraph<>(converter.inputTNFile.getName() + "Converted", converter.inputEdgeImplClass);
					converter.prepareFileOutput();
					continue;
				}
				if (g == null) {
					System.out.print("Sorry, it is not found '# KIND OF NETWORK' line in the file.");
					return;
				}
				if (line.startsWith("TP(")) {
					// it is node
					addNodeCSTN(line, g, int2Node);
					continue;
				}
				if (line.startsWith("EDGE")) {
					// it is the start of an edge
					addEdgeCSTN(reader, line, (TNGraph<CSTNEdge>) g, int2Node);
					continue;
				}
				if (line.startsWith("# Time-Point Names")) {
					line = reader.readLine();
					if (line == null) {
						throw new IllegalStateException(
							"Missing the line containing the timepoint names. Such a line must be present after '# Time-Point Name' line.");
					}
					addNodeSTNU(line, g);
				}
				if (line.startsWith("# Ordinary Edges")) {
					addEdgeSTNU(reader, line, (TNGraph<? extends STNEdge>) g);
				}
			}
		}
		System.out.println("TNGraph parsing ended.");

		if (g == null) {
			System.out.print("Sorry, it is not found '# KIND OF NETWORK' line in the file.");
			return;
		}
		final SpringLayout2<LabeledNode, ? extends Edge> layout = new SpringLayout2<>(g);
		layout.setSize(new Dimension(1024, 800));
		layout.initialize();
		final TNGraphMLWriter graphWriter = new TNGraphMLWriter(layout);

		graphWriter.save(g, converter.fOutput);
		System.out.println("TNGraph saved into file " + converter.fOutput);
	}

	/**
	 * @param reader   the reader
	 * @param line     the considered line
	 * @param g        the graph
	 * @param int2Node the map index->node
	 *
	 * @throws IOException if the reader has problem to read
	 */
	private static void addEdgeCSTN(final BufferedReader reader, final String line, final TNGraph<CSTNEdge> g,
	                                final Int2ObjectMap<LabeledNode> int2Node) throws IOException {

		final String[] nodeParts = PATTERN4EDGECSTN.split(line);
		// nodeParts[0] is empty!

		final int sI = Integer.parseInt(nodeParts[1]);
		final int dI = Integer.parseInt(nodeParts[2]);
		final LabeledNode sourceNode = int2Node.get(sI);
		final LabeledNode destNode = int2Node.get(dI);
		final CSTNEdge edge = g.getEdgeFactory().get(sourceNode.getName() + "-" + destNode.getName());
		Label label;
		String[] labelParts;
		while (reader.ready()) {
			final String line1 = reader.readLine();
			if (line1 == null) {
				break;
			}
			if (line1.startsWith("<*POS-INF*") || !line1.isEmpty() && line1.charAt(0) == ';' || line1.isEmpty()) {
				continue;
			}
			if (line1.startsWith("---")) {
				break;
			}
			LOG.info("line1: " + line1);
			labelParts = PATTERN4EDGECSTN1.split(line1);
			if (line1.contains("[]")) {
				// empty label not captured by split
				label = Label.emptyLabel;
			} else {
				label = Label.parse(toLabel(labelParts[2]));
				LOG.info("line1: " + line1 + ". label parts: " + Arrays.toString(labelParts) + ". label: " + label);
			}
			final int value = Integer.parseInt(labelParts[1]);
			edge.mergeLabeledValue(label, value);
		}
		if (edge.getLabeledValueSet().size() > 0) {
			g.addEdge(edge, sourceNode, destNode);
		}
	}

	/**
	 * @param reader the reader
	 * @param line   the considered line
	 * @param g      the graph
	 *
	 * @throws IOException if the reader has problem to read
	 */
	@SuppressWarnings("unchecked")
	private static <E extends STNEdge> void addEdgeSTNU(
		@Nonnull final BufferedReader reader
		, @Nonnull String line
		, @Nonnull final TNGraph<E> g) throws IOException {
		//line for ordinary edges are like
		// # Ordinary Edges
		// a -1 aa
		// y 1 w
		// ...
		// # Contingent Links
		//a 5 9 c
		//...
		//EOF

		//check it is the right start
		if (!line.startsWith("# Ordinary Edges")) {
			LOG.warning("Line " + line + " is not the start of edges section. It should be '# Ordinary Edges'.");
			return;
		}

		while ((line = reader.readLine()) != null && !line.startsWith("# Contingent Links")) {
			final String[] edgeParts = PATTERN4EDGESTNU.split(line.replace("'", ""));
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Edge parts: " + Arrays.toString(edgeParts) + ". Length: " + edgeParts.length);
			}
			final String sourceNodeName = edgeParts[0];
			final String destNodeName = edgeParts[2];
			final int value = Integer.parseInt(edgeParts[1]);
			if (value == Constants.INT_NULL || value == Constants.INT_POS_INFINITE ||
			    value == Constants.INT_NEG_INFINITE) {
				continue;
			}
			final E edge = g.getEdgeFactory().get(sourceNodeName + "-" + destNodeName);
			edge.setValue(value);
			edge.setConstraintType(Edge.ConstraintType.requirement);
			g.addEdge(edge, sourceNodeName, destNodeName);
			if (LOG.isLoggable(Level.INFO)) {
				LOG.info("Added edge " + edge);
			}
		}
		//contingent parts
		if (line != null && !line.startsWith("# Contingent Links")) {
			return;
		}
		final TNGraph<STNUEdge> g1 = (TNGraph<STNUEdge>) g;
		while ((line = reader.readLine()) != null) {
			final String[] edgeParts = PATTERN4EDGESTNU.split(line.replace("'", ""));
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Edge parts: " + Arrays.toString(edgeParts) + ". Length: " + edgeParts.length);
			}
			final String sourceNodeName = edgeParts[0];
			final String destNodeName = edgeParts[3];
			final int lower = Integer.parseInt(edgeParts[1]);
			final int upper = Integer.parseInt(edgeParts[2]);
			if (lower < 0 || upper < 0 || upper <= lower || upper == Constants.INT_POS_INFINITE) {
				continue;
			}
			STNUEdge edge = g1.getEdgeFactory().get(sourceNodeName + "-" + destNodeName);
			edge.setValue(upper);
			edge.setConstraintType(Edge.ConstraintType.contingent);
			g1.addEdge(edge, sourceNodeName, destNodeName);
			if (LOG.isLoggable(Level.INFO)) {
				LOG.info("Added edge " + edge);
			}
			edge = g1.getEdgeFactory().get(destNodeName + "-" + sourceNodeName);
			edge.setValue(-lower);
			edge.setConstraintType(Edge.ConstraintType.contingent);
			g1.addEdge(edge, destNodeName, sourceNodeName);
			if (LOG.isLoggable(Level.INFO)) {
				LOG.info("Added edge " + edge);
			}
		}
	}

	/**
	 * @param line     the considered line
	 * @param g        the graph
	 * @param int2Node the map index->node
	 */
	private static void addNodeCSTN(@Nonnull final String line
		, @Nonnull final TNGraph<? extends Edge> g
		, @Nonnull final Int2ObjectMap<LabeledNode> int2Node) {
		final String[] nodeParts = PATTERN4NODECSTN.split(line);
		// nodeParts[0] is empty!
		LOG.info("NodeParts: " + Arrays.toString(nodeParts) + ". Length: " + nodeParts.length);
		LOG.info(
			"nodeParts[2]: '" + nodeParts[2] + "'");// . Leading char code: "+ Character.codePointAt(nodeParts[2], 0));
		final LabeledNode node = new LabeledNode(nodeParts[2]);
		final boolean added = g.addVertex(node);
		if (!added) {
			throw new IllegalStateException("Node " + node + " cannot be inserted.");
		}

		if (int2Node.put(Integer.parseInt(nodeParts[1]), node) != null) {
			throw new IllegalStateException("Node " + node + " already inserted.");
		}
		if (nodeParts.length == 3) {
			node.setLabel(Label.emptyLabel);
		} else {
			node.setLabel(Label.parse(toLabel(nodeParts[3])));
		}
		if (nodeParts.length == 5) {
			// it is an observation time point
			LOG.info("nodeParts[4]: " + nodeParts[4]);
			node.setObservable(nodeParts[4].trim().charAt(0));
		}
	}

	/**
	 * @param line the considered line
	 * @param g    the graph
	 */
	private static void addNodeSTNU(@Nonnull final String line, @Nonnull final TNGraph<? extends Edge> g) {
		//line has a format like
		//a c u v w x y p aa
		final String[] nodes = PATTERN4NODESTNU.split(line.replace('\'', ' '));
		if (LOG.isLoggable(Level.FINEST)) {
			LOG.finest("Nodes: " + Arrays.toString(nodes) + ". Length: " + nodes.length);
		}
		if (nodes.length == 0) {
			throw new IllegalStateException("The line of nodes " + line + " is wrong.");
		}
		for (final String nodeName : nodes) {
			final LabeledNode node = new LabeledNode(nodeName);
			final boolean added = g.addVertex(node);
			if (!added) {
				throw new IllegalStateException("Node " + node + " cannot be inserted.");
			}
			if (LOG.isLoggable(Level.INFO)) {
				LOG.info("Added node " + node);
			}
		}
	}

	/**
	 * @param networkType the input network type
	 *
	 * @return the default edge implementation class corresponding to the given network type. If the conversion is not
	 * 	defined for the given type, it returns null.
	 */
	@Nullable
	static private Class<? extends Edge> getInputEdgeImplClass(@Nonnull final TNGraph.NetworkType networkType) {
		return switch (networkType) {
			case STN -> EdgeSupplier.DEFAULT_STN_EDGE_CLASS;
			case STNU -> EdgeSupplier.DEFAULT_STNU_EDGE_CLASS;
			case CSTN -> EdgeSupplier.DEFAULT_CSTN_EDGE_CLASS;
			default -> null;
//			case CSTNPSU -> EdgeSupplier.DEFAULT_CSTNPSU_EDGE_CLASS;
//			case CSTNU, PCSTNU -> EdgeSupplier.DEFAULT_CSTNU_EDGE_CLASS;
		};
	}

	/**
	 * @param line the input string. It must an upper case string representing one type in {@link TNGraph.NetworkType}.
	 *
	 * @return the networks type converting the string 'line'
	 *
	 * @see TNGraph.NetworkType
	 */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "Dm", justification = "DM_CONVERT_CASE is not " +
	                                                                                  "relevant.")
	private static TNGraph.NetworkType getNetworkType(@Nonnull final String line) {
		return TNGraph.NetworkType.valueOf(line.toUpperCase());
	}

	/**
	 * @param lukeFormat the input formatted as Luke style
	 *
	 * @return label as string
	 */
	private static String toLabel(final String lukeFormat) {
		return lukeFormat.trim().replace("(", "").replace(")", "").replace("-", "¬");
	}

	/**
	 * Simple method to manage command line parameters using args4j library.
	 *
	 * @param args the input parameters
	 *
	 * @return false if a parameter is missing, or it is wrong. True if every parameter is given in a right format.
	 */
	private boolean manageParameters(final String[] args) {
		final CmdLineParser parser = new CmdLineParser(this);
		try {
			parser.parseArgument(args);
		} catch (final CmdLineException e) {
			// if there's a problem in the command line, you'll get this
			// exception. this will report an error message.
			System.err.println(e.getMessage());
			System.err.println(
				"java -cp CSTNU-<version>.jar -cp it.univr.di.cstnu.Luke2GraphML [options...] argument.");
			// print the list of available options
			parser.printUsage(System.err);
			System.err.println();

			// print option sample. This is useful some time
			// System.err.println("Example: java -jar Checker.jar" +
			// parser.printExample(OptionHandlerFilter.REQUIRED) +
			// " <CSTN_file_name0> <CSTN_file_name1>...");
			return false;
		}
		if (LOG.isLoggable(Level.FINEST)) {
			LOG.finest("File name: " + fileNameInput);
		}
		inputTNFile = new File(fileNameInput);
		if (!inputTNFile.exists()) {
			System.err.println("File " + inputTNFile + " does not exit.");
			parser.printUsage(System.err);
			System.err.println();
			return false;
		}
		if (LOG.isLoggable(Level.FINEST)) {
			LOG.finest("File: " + inputTNFile);
		}
		return true;
	}

	/**
	 * Once the network type is know, it is possible to build a good suffix for the output file.
	 */
	@SuppressWarnings("DynamicRegexReplaceableByCompiledPattern")
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "Dm", justification = "DM_CONVERT_CASE is not " +
	                                                                                  "relevant.")
	private void prepareFileOutput() {
		if (networkType == null) {
			return;
		}
		final String finalSuffix = "." + networkType.name().toLowerCase();
		if (fOutput == null) {
			final String outputFileName = fileNameInput.replaceFirst("(\\.stnu)*$", finalSuffix);
			fOutput = new File(outputFileName);
		}
		if (fOutput.isDirectory()) {
			throw new IllegalStateException("Output file is a directory.");
		}
		if (!fOutput.getName().endsWith(finalSuffix)) {
			final File newOutput = new File(fOutput.getAbsolutePath() + finalSuffix);
			if (!fOutput.exists()) {
				fOutput = newOutput;
			}
		}
		if (fOutput.exists()) {
			if (!fOutput.renameTo(new File(fOutput.getAbsoluteFile() + ".old"))) {
				final String m = "File " + fOutput.getAbsolutePath() + " cannot be renamed in .old.";
				LOG.severe(m);
				throw new IllegalStateException(m);
			}
		}
	}
}
