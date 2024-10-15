// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 *
 */
package it.univr.di.cstnu.graph.lazy;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import edu.uci.ics.jung.io.GraphMLReader;
import it.univr.di.Debug;
import it.univr.di.cstnu.algorithms.AbstractCSTN;
import it.univr.di.cstnu.graph.Edge.ConstraintType;
import it.univr.di.cstnu.graph.LabeledNode;
import it.univr.di.cstnu.graph.LabeledNodeSupplier;
import it.univr.di.cstnu.graph.TNGraphMLWriter;
import it.univr.di.labeledvalue.ALabelAlphabet;
import it.univr.di.labeledvalue.Label;
import it.univr.di.labeledvalue.lazy.LabeledLazyWeightTreeMap;
import it.univr.di.labeledvalue.lazy.LazyNumber;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Allows to read a graph from a file written in GraphML format.<br>
 * GraphML format allows the definition of different attributes for the graph, vertices and edges.<br>
 * All attributes are defined in the first part of a GraphML file. Examples of GraphML file that can read by this class are given in the Instances directory
 * under CstnuTool one.
 *
 * @author posenato
 * @version $Id: $Id
 */
@SuppressWarnings("ALL")
public class LazyGraphMLReader {
	/**
	 * * Since we want to preserve edge names given by in the file and such namescan conflict with the ones given by the standard edgeFactory,
	 * we modify the standard factory altering the default name
	 *
	 * @author posenato
	 */
	static private class InternalEdgeFactory implements Supplier<LabeledLazyWeightEdge> {

		/**
		 */
		public InternalEdgeFactory() {
			super();
		}

		@Override
		public LabeledLazyWeightEdge get() {
			LabeledLazyWeightEdge e = new LabeledLazyWeightEdge();
			e.setName(prefix + e.getName());
			return e;
		}
	}

	/**
	 * logger
	 */
	static final Logger LOG = Logger.getLogger(LazyGraphMLReader.class.getName());

	/**
	 *
	 */
	static final String prefix = "__";

	/**
	 *
	 */
	private static final Supplier<LabeledNode> vertexFactory = new Supplier<>() {

		final Supplier<LabeledNode> factory = new LabeledNodeSupplier();

		@Override
		public LabeledNode get() {
			LabeledNode node = this.factory.get();
			node.setName(prefix + node.getName());
			return node;
		}
	};

	/**
	 * ALabel alphabet for UC a-labels
	 */
	private final ALabelAlphabet aLabelAlphabet;

	/**
	 * Input file reader
	 */
	private final Reader fileReader;

	/**
	 * true if the given file ends with '.cstn'
	 */
	// private boolean isCSTN;

	/**
	 * The result of the loading action.
	 */
	LabeledLazyWeightGraph graph;

	/**
	 *
	 */
	private final Supplier<LabeledLazyWeightEdge> edgeFactory;

	/**
	 * Allows to read a graph from a file written in GraphML format.<br>
	 * GraphML format allows the definition of different attributes for the graph, vertices and edges.<br>
	 * All attributes are defined in the first part of a GraphML file. Examples of GraphML file that can read by this class are given in the Instances directory
	 * under CstnuTool one.
	 *
	 * @param graphFile a {@link java.io.File} object.
	 * @throws java.io.UnsupportedEncodingException if it is not encoded as UTF-8.
	 * @throws java.io.FileNotFoundException if any.
	 */
	public LazyGraphMLReader(final File graphFile) throws FileNotFoundException, UnsupportedEncodingException {
		if (graphFile == null) {
			throw new FileNotFoundException("The given file does not exist.");
		}
		this.fileReader = new BufferedReader(new InputStreamReader(new FileInputStream(graphFile), StandardCharsets.UTF_8));
		// this.isCSTN = graphFile.getName().endsWith(".cstn");
		this.aLabelAlphabet = new ALabelAlphabet();
		this.edgeFactory = new InternalEdgeFactory();
		this.graph = new LabeledLazyWeightGraph(this.aLabelAlphabet);
		this.graph.setFileName(graphFile);
	}

	/**
	 * <p>
	 * readGraph.
	 * </p>
	 *
	 * @return the graphML as LabeledLazyWeightGraph.
	 * @throws java.io.IOException if any.
	 * @throws javax.xml.parsers.ParserConfigurationException if any.
	 * @throws org.xml.sax.SAXException if any.
	 */
	public LabeledLazyWeightGraph readGraph() throws IOException, ParserConfigurationException, SAXException {
		/*
		 * I use LazyGraphMLReader instead of GraphMLReader2 because on 2017-11-01 I discovered that GraphMLReader2 does not allow to read
		 * edge attributes that are very long, like the labeled upper case values of a checked CSTNU.
		 * LazyGraphMLReader is a little less intuitive but it manages all attributes in a right way!
		 */
		GraphMLReader<LabeledLazyWeightGraph, LabeledNode, LabeledLazyWeightEdge> graphReader = new GraphMLReader<>(vertexFactory, this.edgeFactory);
		// populate the graph.
		graphReader.load(this.fileReader, this.graph);

		// Now graph contains all vertices and edges with default names (the factory cannot set the right names).

		/*
		 * Node attribute setting!
		 */
		// Name
		graphReader.getVertexIDs().forEach(new BiConsumer<LabeledNode, String>() {
			@Override
			public void accept(LabeledNode n, String s) {
				n.setName(s);
				if (s.equals(AbstractCSTN.ZERO_NODE_NAME))
					LazyGraphMLReader.this.graph.setZ(n);
			}
		});
		// Label
		Function<LabeledNode, String> nodeLabelF = graphReader.getVertexMetadata().get(TNGraphMLWriter.NODE_LABEL_KEY).transformer;
		// Observed proposition
		Function<LabeledNode, String> nodeObservedPropF = graphReader.getVertexMetadata().get(TNGraphMLWriter.NODE_OBSERVED_KEY).transformer;
		// X position
		Function<LabeledNode, String> nodeXF = graphReader.getVertexMetadata().get(TNGraphMLWriter.NODE_X_KEY).transformer;
		// Y position
		Function<LabeledNode, String> nodeYF = graphReader.getVertexMetadata().get(TNGraphMLWriter.NODE_Y_KEY).transformer;

		for (LabeledNode n : this.graph.getVertices()) {
			n.setLabel(Label.parse(nodeLabelF.apply(n)));
			String s = nodeObservedPropF.apply(n);
			if ((s != null) && (s.length() == 1)) {
				n.setObservable(s.charAt(0));
			}
			n.setX(Double.parseDouble(nodeXF.apply(n)));
			n.setY(Double.parseDouble(nodeYF.apply(n)));
		}

		/*
		 * Edge attribute setting!
		 */
		// Name
		graphReader.getEdgeIDs().forEach(new BiConsumer<LabeledLazyWeightEdge, String>() {
			@Override
			public void accept(LabeledLazyWeightEdge e, String s) {
				e.setName(s);
				if (!e.getName().equals(s)) {
					// there is a problem that the name has been already used...
					s = LazyGraphMLReader.this.graph.getSource(e).getName() + "_" + LazyGraphMLReader.this.graph.getDest(e).getName();
					e.setName(s);
					if (Debug.ON) {
						if (LOG.isLoggable(Level.WARNING)) {
							LOG.log(Level.WARNING, "Fixing edge name using '" + s + "'.");
						}
					}
				}
			}
		});
		// Type
		Function<LabeledLazyWeightEdge, String> edgeTypeF = graphReader.getEdgeMetadata().get(TNGraphMLWriter.EDGE_TYPE_KEY).transformer;
		// Labeled Value
		Function<LabeledLazyWeightEdge, String> edgeLabeledValueF = graphReader.getEdgeMetadata().get(TNGraphMLWriter.EDGE_LABELED_VALUE_KEY).transformer;
		// I parse also value parameter that was present in the first version of the graph file
		Function<LabeledLazyWeightEdge, String> edgeOldValueF = null;
		if (graphReader.getEdgeMetadata().get("Value") != null)
			edgeOldValueF = graphReader.getEdgeMetadata().get("Value").transformer;

		for (LabeledLazyWeightEdge e : this.graph.getEdges()) {
			// Type
			e.setConstraintType(ConstraintType.valueOf(edgeTypeF.apply(e)));
			// Labeled Value
			String data = edgeLabeledValueF.apply(e);
			if (data != null) {
				LabeledLazyWeightTreeMap map = LabeledLazyWeightTreeMap.parse(data);
				if (data.length() > 2 && map == null) {
					throw new IllegalArgumentException("Labeled values in a wrong format: " + data + " in edge " + e);
				}
				e.setLabeledValueMap(map);
			}
			// I parse also value parameter that was present in the first version of the graph file
			if (edgeOldValueF != null) {
				data = edgeOldValueF.apply(e);
				if (data != null && !data.isEmpty()) {
					e.putLabeledValue(Label.emptyLabel, LazyNumber.get(Integer.parseInt(data)));
				}
			}
		}
		// if (this.isCSTN)
		// return this.graph;
		//
		// // FROM HERE the graph is assumed to be a CSTNU graph!
		//
		// GraphMLMetadata<LabeledLazyWeightEdge> edgeLabeledUCValueMD = graphReader.getEdgeMetadata().get(TNGraphMLWriter.EDGE_LABELED_UC_VALUE_KEY);
		// GraphMLMetadata<LabeledLazyWeightEdge> edgeLabeledLCValueMD = graphReader.getEdgeMetadata().get(TNGraphMLWriter.EDGE_LABELED_LC_VALUE_KEY);
		// if (edgeLabeledUCValueMD == null || edgeLabeledLCValueMD == null) {
		// // Graph file is still in old format!
		// if (Debug.ON) {
		// LOG.warning("The input file does not contain the meta declaration for upper case value or lower case value. Please, fix it adding" +
		// "<key id=\"UpperCaseLabeledValues\" for=\"edge\"> \n" +
		// "<default></default> \n" +
		// "</key>\n" +
		// " or \n" +
		// "<key id=\"LowerCaseLabeledValues\" for=\"edge\"> \n" +
		// "<default></default> \n" +
		// "</key>\n" +
		// "before <graph> tag.");
		// }
		// return this.graph;
		// }
		// Function<LabeledLazyWeightEdge, String> edgeLabeledUCValueF = edgeLabeledUCValueMD.transformer;
		// Function<LabeledLazyWeightEdge, String> edgeLabeledLCValueF = edgeLabeledLCValueMD.transformer;
		//
		// for (LabeledLazyWeightEdge e : this.graph.getEdges()) {
		// // Labeled UC Value
		// String data = edgeLabeledUCValueF.apply(e);
		// LabeledALabelIntTreeMap upperCaseMap = LabeledALabelIntTreeMap.parse(data, this.aLabelAlphabet);
		// if (data != null && data.length() > 2 && (upperCaseMap == null || upperCaseMap.isEmpty()))
		// throw new IllegalArgumentException("Upper Case values in a wrong format: " + data + " in edge " + e);
		// if (upperCaseMap == null)
		// upperCaseMap = new LabeledALabelIntTreeMap();
		// e.setUpperCaseValueMap(upperCaseMap);
		// // Labeled LC Value
		// data = edgeLabeledLCValueF.apply(e);
		// LabeledLowerCaseValue lowerCaseValue = LabeledLowerCaseValue.parse(data, this.aLabelAlphabet);
		// if (data != null && data.length() > 2 && (lowerCaseValue == null || lowerCaseValue.isEmpty()))
		// throw new IllegalArgumentException("Lower Case values in a wrong format: " + data + " in edge " + e);
		// if (lowerCaseValue == null)
		// lowerCaseValue = LabeledLowerCaseValue.emptyLabeledLowerCaseValue;
		// e.setLowerCaseValue(lowerCaseValue);
		// }

		return this.graph;
	}
}
