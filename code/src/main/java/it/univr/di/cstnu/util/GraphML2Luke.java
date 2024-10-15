// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.util;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.univr.di.cstnu.graph.*;
import it.univr.di.labeledvalue.Constants;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for converting STNU file in GraphML format to Luke format.
 *
 * @author posenato
 * @version $Rev: 732 $
 */
@SuppressWarnings("DynamicRegexReplaceableByCompiledPattern")
public class GraphML2Luke {
	/**
	 * class logger
	 */
	static final Logger LOG = Logger.getLogger(GraphML2Luke.class.getName());

	/**
	 * Default extension
	 */
	static final String plainSuffix = ".plainStnu";

	/**
	 * Version
	 */
	static final String VERSIONandDATE = "1.0, January, 04 2024";

	/**
	 * @param args a CSTN file in Luke's format.
	 *
	 * @throws IOException if any file cannot be read or write
	 */
	public static void main(String[] args) throws IOException {

		LOG.finest("Start...");
		System.out.println("Start of execution...");
		final Checker tester = new Checker();

		final GraphML2Luke converter = new GraphML2Luke();

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

		final TNGraphMLReader<STNUEdge> graphMLReader = new TNGraphMLReader<>();
		final TNGraph<STNUEdge> g;
		try {
			g = graphMLReader.readGraph(converter.inputSTNUFile, EdgeSupplier.DEFAULT_STNU_EDGE_CLASS);
		} catch (ParserConfigurationException | SAXException e) {
			throw new RuntimeException(e);
		}

		stnuPlainWriter(g, converter.fOutput);

		System.out.println("Converted TNGraph saved into file " + converter.fOutput);
	}

	/**
	 * Saves the given stnu in file filename in the Hunsberger format.<br> Hunsberger format example:
	 *
	 * <pre>
	 * # KIND OF NETWORK
	 * STNU
	 * # Num Time-Points
	 * 5
	 * # Num Ordinary Edges
	 * 4
	 * # Num Contingent Links
	 * 2
	 * # Time-Point Names
	 * A0 C0 A1 C1 X
	 * # Ordinary Edges
	 * X 12 C0
	 * C1 11 C0
	 * C0 -7 X
	 * C0 -1 C1
	 * # Contingent Links
	 * A0 1 3 C0
	 * A1 1 10 C1
	 * ...
	 * </pre>
	 *
	 * @param graph      the input graph
	 * @param outputFile the output file
	 */
	@SuppressFBWarnings("VA_FORMAT_STRING_USES_NEWLINE")//false positive
	static private void stnuPlainWriter(@Nonnull TNGraph<STNUEdge> graph, @Nonnull File outputFile) {
		LOG.finest("Start to save the instance in a plain format");

		final ObjectArrayList<STNUEdge> ordinaryConstraintList = new ObjectArrayList<>();
		final ObjectArrayList<STNUEdge> upperCaseContingentList = new ObjectArrayList<>();
		final ObjectArrayList<STNUEdge> waitList = new ObjectArrayList<>();
		for (final STNUEdge edge : graph.getEdgesOrdered()) {
			if (edge.isContingentEdge()) {
				if (edge.getLabeledValue() < 0 || edge.getValue() > 0) {
					upperCaseContingentList.add(edge);
				}
				continue;
			}
			if (edge.isWait()) {
				waitList.add(edge);
				continue;
			}
			ordinaryConstraintList.add(edge);
		}
		try (final PrintWriter writer = new PrintWriter(outputFile, StandardCharsets.UTF_8)) {
			String header = """
			                # Nodes and contingent links saved in random order.
			                # KIND OF NETWORK
			                STNU
			                # Num Time-Points
			                %d
			                # Num Ordinary Edges
			                %d
			                # Num Contingent Links
			                %d
			                """.formatted(
				graph.getVertexCount()
				, (graph.getEdgeCount() - 2 * graph.getContingentNodeCount() - waitList.size())
				, graph.getContingentNodeCount());
			if (waitList.size() > 0) {
				header += """
				          # Num Waits
				          %d
				          """.formatted(waitList.size());
			}
			header += "# Time-Point Names\n";
			writer.print(header);
//			ObjectArrayList<LabeledNode> randomVertexList = new ObjectArrayList<>(graph.getVertices());
//			Collections.shuffle(randomVertexList); // Luke asked to save nodes in random order :/
			for (final LabeledNode node : graph.getNodesOrdered()) {
				writer.print("%s ".formatted(node.getName().replace('\'', '"')));
			}
			writer.println();
			writer.println("# Ordinary Edges");
			for (final STNUEdge edge : ordinaryConstraintList) {
				writer.println("%s %s %s".formatted(
					Objects.requireNonNull(graph.getSource(edge)).getName()
					, Constants.formatInt(edge.getValue())
					, Objects.requireNonNull(graph.getDest(edge)).getName()));
			}
			writer.println("# Contingent Links");
//			Collections.shuffle(randomUpperCaseContingentList);
			for (final STNUEdge e : upperCaseContingentList) {
				LabeledNode activation = graph.getSource(e);
				LabeledNode contingent = graph.getDest(e);
				assert contingent != null;
				assert activation != null;
				int upperValue = e.getValue();
				if (e.isUpperCase()) {
					//it is a solved STNU
					final LabeledNode swap = activation;
					activation = contingent;
					contingent = swap;
					upperValue = -e.getLabeledValue();
				}
				final STNUEdge lower = graph.findEdge(activation, contingent);
				assert lower != null;
				int lowerValue = -lower.getValue();
				if (lower.isLowerCase()) {
					lowerValue = lower.getLabeledValue();
				}
				writer.println("%s %s %s %s".formatted(activation.getName()
					, lowerValue
					, upperValue
					, contingent.getName()));
			}
			if (waitList.isEmpty()) {
				return;
			}
			writer.println("# Waits");
			for (final STNUEdge e : waitList) {
				final LabeledNode activation = graph.getDest(e);
				final LabeledNode node = graph.getSource(e);
				assert node != null;
				assert activation != null;
				writer.println("%s %s:%s %s".formatted(node.getName()
					, e.getCaseLabel().getName()
					, e.getLabeledValue()
					, activation.getName()));
			}
		} catch (IOException e) {
			throw new IllegalStateException(e.toString());
		}
	}

	/**
	 * The input file names. Each file has to contain a CSTN tNGraph in GraphML format.
	 */
	@Argument(required = true, usage = "Input file. It has to be a GraphML format.", metaVar = "STNU_file_name")
	private String fileNameInput;
	/**
	 * Output file where to write the STNU in Luke's format.
	 */
	@Option(name = "-o", aliases = "--output", usage =
		"Output to this file in Luke's format. If not specified, it is set to the input file name with suffix '" +
		plainSuffix + "'", metaVar = "outputFile")
	@SuppressFBWarnings(value = "UWF_NULL_FIELD", justification = "This field is set by parser.parseArgument.")
	private File fOutput;
	/**
	 *
	 */
	private File inputSTNUFile;
	/**
	 * Software Version.
	 */
	@Option(name = "-v", aliases = "--version", usage = "Version")
	private boolean versionReq;

	/**
	 * Simple method to manage command line parameters using args4j library.
	 *
	 * @param args the input parameters
	 *
	 * @return false if a parameter is missing, or it is wrong. True if every parameter is given in a right format.
	 */
	private boolean manageParameters(String[] args) {
		final CmdLineParser parser = new CmdLineParser(this);
		try {
			parser.parseArgument(args);
		} catch (CmdLineException e) {
			// if there's a problem in the command line, you'll get this
			// exception. this will report an error message.
			System.err.println(e.getMessage());
			System.err.println(
				"java -cp CSTNU-<version>.jar " + GraphML2Luke.class.getCanonicalName() + " [options...] argument.");
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
		inputSTNUFile = new File(fileNameInput);
		if (!inputSTNUFile.exists()) {
			System.err.println("File " + inputSTNUFile + " does not exit.");
			parser.printUsage(System.err);
			System.err.println();
			return false;
		}
		if (LOG.isLoggable(Level.FINEST)) {
			LOG.finest("File: " + inputSTNUFile);
		}

		if (fOutput == null) {
			final String outputFileName = fileNameInput.replaceFirst("(\\.stnu)*$", plainSuffix);
			fOutput = new File(outputFileName);
		}
		if (fOutput.isDirectory()) {
			System.err.println("Output file is a directory.");
			parser.printUsage(System.err);
			System.err.println();
			return false;
		}
		if (!fOutput.getName().endsWith(plainSuffix)) {
			final File newOutput = new File(fOutput.getAbsolutePath() + plainSuffix);
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
		return true;
	}

}
