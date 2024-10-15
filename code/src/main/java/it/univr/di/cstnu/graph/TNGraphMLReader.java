// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.graph;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import edu.uci.ics.jung.io.GraphMLMetadata;
import edu.uci.ics.jung.io.GraphMLReader;
import it.univr.di.Debug;
import it.univr.di.cstnu.algorithms.AbstractCSTN;
import it.univr.di.cstnu.graph.Edge.ConstraintType;
import it.univr.di.cstnu.graph.TNGraph.NetworkType;
import it.univr.di.cstnu.util.LogNormalDistributionParameter;
import it.univr.di.labeledvalue.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Allows the reading of a Temporal Network (TM) graph from a file or a string in GraphML format.
 * <br>
 * GraphML format allows the definition of different attributes for the graph, vertices and edges.
 * <br>
 * All attributes are defined in the first part of a GraphML file. Examples of GraphML file that can read by this class
 * are given in the Instances directory under CstnuTool one.
 *
 * @param <E> the type of edge
 *
 * @author posenato
 * @version $Rev: 908 $
 */
public class TNGraphMLReader<E extends Edge> {
	/**
	 * Since we want to preserve edge names given by in the file and such name can conflict with the ones given by the
	 * standard edgeFactory, we modify the standard factory altering the default name
	 *
	 * @param <E>
	 *
	 * @author posenato
	 */
	static private class InternalEdgeFactory<E extends Edge> implements Supplier<E> {
		/**
		 *
		 */
		Supplier<E> edgeFactory;

		/**
		 * @param <E1>     type of edge
		 * @param edgeImpl class representing a concrete edge type
		 */
		<E1 extends E> InternalEdgeFactory(Class<E1> edgeImpl) {
			edgeFactory = new EdgeSupplier<>(edgeImpl);
		}

		@Override
		public E get() {
			final E e = edgeFactory.get();
			e.setName(prefix + e.getName());
			return e;
		}
	}

	/**
	 * Since we want to preserve edge names given by in the file and such name can conflict with the ones given by the
	 * standard edgeFactory, we modify the standard factory altering the default name
	 *
	 * @author posenato
	 */
	static private class InternalVertexFactory implements Supplier<LabeledNode> {

		/**
		 *
		 */
		Supplier<LabeledNode> nodeFactory;

		/**
		 *
		 */
		InternalVertexFactory() {
			nodeFactory = new LabeledNodeSupplier();
		}

		@Override
		public LabeledNode get() {
			final LabeledNode e = nodeFactory.get();
			e.setName(prefix + e.getName());
			return e;
		}
	}

	/**
	 * Labeled value class used in the class.
	 */
	public static final Class<? extends LabeledIntMap> labeledValueMapImpl =
		LabeledIntMapSupplier.DEFAULT_LABELEDINTMAP_CLASS;

	/**
	 * logger
	 */
	static final Logger LOG = Logger.getLogger("it.univr.di.cstnu.graph.TNGraphMLReader");

	/**
	 *
	 */
	static final String prefix = "__";

	/**
	 * A TNGraphMLReader object can be now used many times for reading different graphs.
	 */
	public TNGraphMLReader() {

	}

	/**
	 * Reads graphXML and returns the corresponding graph as a TNGraph object. Edges of TNGraph are created using the
	 * edgeImplClass. In this way, such a reader can create more kinds of TNGraph according to the given type of edge.
	 *
	 * @param graphXML      a string representing the graph in GraphML format.
	 * @param edgeImplClass the type for the edges of the graph.
	 *
	 * @return the graphML as TNGraph.
	 *
	 * @throws java.io.IOException                            if any error occurs during the graphXML reading
	 * @throws javax.xml.parsers.ParserConfigurationException if graphXML contains character that cannot be parsed
	 * @throws org.xml.sax.SAXException                       if graphXML is not valid
	 */
	public TNGraph<E> readGraph(final String graphXML, Class<? extends E> edgeImplClass)
		throws IOException, ParserConfigurationException, SAXException {
		if (graphXML == null || graphXML.isEmpty()) {
			throw new IllegalArgumentException("The given input is null or empty.");
		}
		final Reader fileReader = new StringReader(graphXML);
		return load(fileReader, edgeImplClass);
	}

	/**
	 * Reads graphFile and returns the corresponding graph as a TNGraph object. Edges of TNGraph are created using the
	 * edgeImplClass. In this way, such a reader can create more kinds of TNGraph according to the given type of edge.
	 *
	 * @param graphFile     file containing the graph in GraphML format.
	 * @param edgeImplClass the type for the edges of the graph.
	 *
	 * @return the graphML as TNGraph.
	 *
	 * @throws java.io.IOException                            if any error occurs during the graphFile reading.
	 * @throws javax.xml.parsers.ParserConfigurationException if graphXML contains character that cannot be parsed
	 * @throws org.xml.sax.SAXException                       if graphFile does not contain a valid GraphML instance.
	 */
	public TNGraph<E> readGraph(final File graphFile, Class<? extends E> edgeImplClass)
		throws IOException, ParserConfigurationException, SAXException {
		try (final Reader fileReader = new BufferedReader(
			new InputStreamReader(new FileInputStream(graphFile), StandardCharsets.UTF_8))) {
			final TNGraph<E> tnGraph = load(fileReader, edgeImplClass);
			tnGraph.setInputFile(graphFile);
			return tnGraph;
		} catch (UnsupportedEncodingException | FileNotFoundException e) {
			throw new FileNotFoundException(
				"There is a problem to read the file containing the network. Details: " + e.getMessage());
		}
	}

	/**
	 * Creates the graph object using the given reader for acquiring the input.
	 *
	 * @param reader        none
	 * @param edgeImplClass none
	 *
	 * @return the graphML as TNGraph.
	 *
	 * @throws java.io.IOException                            none
	 * @throws org.xml.sax.SAXException                       none
	 * @throws javax.xml.parsers.ParserConfigurationException none
	 */
	TNGraph<E> load(Reader reader, Class<? extends E> edgeImplClass)
		throws IOException, ParserConfigurationException, SAXException {
		final ALabelAlphabet aLabelAlphabet = new ALabelAlphabet();
		final Supplier<E> edgeFactory = new InternalEdgeFactory<>(edgeImplClass);
		final Supplier<LabeledNode> nodeFactory = new InternalVertexFactory();
		final TNGraph<E> tnGraph = new TNGraph<>("", edgeImplClass, aLabelAlphabet);

		/*
		 * I use TNGraphMLReader instead of GraphMLReader2 because on 2017-11-01 I discovered that GraphMLReader2 does not allow to read
		 * edge attributes that are very long, like the labeled upper case values of a checked CSTNU.
		 * TNGraphMLReader is a little less intuitive, but it manages all attributes in a right way!
		 */
		final GraphMLReader<TNGraph<E>, LabeledNode, E> graphReader = new GraphMLReader<>(nodeFactory, edgeFactory);
		// populate the graph.
		graphReader.load(reader, tnGraph);

		//Set the graph attribute name.
		//Remember that this works if the TNGraph.hashcode() is NOT override.
		final GraphMLMetadata<TNGraph<E>> graphMD = graphReader.getGraphMetadata().get(TNGraphMLWriter.GRAPH_NAME_KEY);
		final Function<TNGraph<E>, String> graphNameF = (graphMD != null) ? graphMD.transformer : null;
		tnGraph.setName(((graphNameF != null) ? graphNameF.apply(tnGraph) : "no name"));


		// Now graph contains all vertices and edges with default names (the factory cannot set the right names).
		/*
		 * Node attribute setting!
		 */
		// Name
		graphReader.getVertexIDs().forEach((n, s) -> {
			n.setName(s);
			if (s.equals(AbstractCSTN.ZERO_NODE_NAME)) {
				// TNGraphMLReader.tnGraph.setZ(n);
				tnGraph.setZ(n);
			}
		});
		// Label
		final GraphMLMetadata<LabeledNode> nodeLabelMD =
			graphReader.getVertexMetadata().get(TNGraphMLWriter.NODE_LABEL_KEY);
		final Function<LabeledNode, String> nodeLabelF = (nodeLabelMD != null) ? nodeLabelMD.transformer : null;

		// Observed proposition
		final GraphMLMetadata<LabeledNode> nodeObservedPropMD =
			graphReader.getVertexMetadata().get(TNGraphMLWriter.NODE_OBSERVED_KEY);
		final Function<LabeledNode, String> nodeObservedPropF =
			(nodeObservedPropMD != null) ? nodeObservedPropMD.transformer : null;
		// Parameter
		final GraphMLMetadata<LabeledNode> nodeParameterFMD =
			graphReader.getVertexMetadata().get(TNGraphMLWriter.NODE_PARAMETER_KEY);
		final Function<LabeledNode, String> nodeParameterF =
			(nodeParameterFMD != null) ? nodeParameterFMD.transformer : null;

		// X position
		final Function<LabeledNode, String> nodeXF =
			graphReader.getVertexMetadata().get(TNGraphMLWriter.NODE_X_KEY).transformer;
		// Y position
		final Function<LabeledNode, String> nodeYF =
			graphReader.getVertexMetadata().get(TNGraphMLWriter.NODE_Y_KEY).transformer;
		// Potential
		final GraphMLMetadata<LabeledNode> nodeLabeledPotentialValueMD = graphReader.getVertexMetadata().get(TNGraphMLWriter.NODE_POTENTIAL_KEY);
		final Function<LabeledNode, String> nodeLabeledPotentialValueF = (nodeLabeledPotentialValueMD != null) ? nodeLabeledPotentialValueMD.transformer : null;

		// LogNormal distribution
		final GraphMLMetadata<LabeledNode> nodeLogNormalDistributionMD = graphReader.getVertexMetadata().get(TNGraphMLWriter.NODE_LOGNORMALDISTRIBUTION_KEY);
		final Function<LabeledNode, String> nodeLogNormalDistributionF = (nodeLogNormalDistributionMD != null) ? nodeLogNormalDistributionMD.transformer : null;

		for (final LabeledNode n : tnGraph.getVertices()) {
			if (nodeLabelF != null) {
				n.setLabel(Label.parse(nodeLabelF.apply(n)));
			}
			if (nodeObservedPropF != null) {
				final String s = nodeObservedPropF.apply(n);
				if ((s != null) && (s.length() == 1)) {
					n.setObservable(s.charAt(0));
				}
			}
			if (nodeParameterF != null) {
				final String s = nodeParameterF.apply(n);
				if (Boolean.toString(true).equals(s)) {
					n.setParameter(true);
				}
			}
			if (nodeLabeledPotentialValueF != null) {
				final String data = nodeLabeledPotentialValueF.apply(n);
				final LabeledIntMap potentialMap = AbstractLabeledIntMap.parse(data, labeledValueMapImpl);
				if (data != null && data.length() > 2 && (potentialMap == null || potentialMap.isEmpty())) {
					throw new IllegalArgumentException("Potential values in a wrong format: " + data + " in node " + n);
				}
				n.setLabeledPotential(potentialMap);
			}

			if (nodeLogNormalDistributionF != null) {
				final String data = nodeLogNormalDistributionF.apply(n);
				if (data != null && data.length() > 0) {
					final LogNormalDistributionParameter logNormalDist = LogNormalDistributionParameter.parse(data);
					if (data.length() > 2 && logNormalDist == null) {
						throw new IllegalArgumentException("LogNormalDistributionParameter values in a wrong format: " + data + " in node " + n);
					}
					n.setLogNormalDistributionParameter(logNormalDist);
				}
			}

			n.setX(Double.parseDouble(nodeXF.apply(n)));
			n.setY(Double.parseDouble(nodeYF.apply(n)));
		}

		/*
		 * Edge attribute setting!
		 */
		// Name
		graphReader.getEdgeIDs().forEach((e, s) -> {
			e.setName(s);
			if (!e.getName().equals(s)) {
				// there is a problem that the name has been already used...
				// s = TNGraphMLReader.tnGraph.getSource(e).getName() + "_" + TNGraphMLReader.tnGraph.getDest(e).getName();
				s = Objects.requireNonNull(tnGraph.getSource(e)).getName() + "_" +
				    Objects.requireNonNull(tnGraph.getDest(e)).getName();
				e.setName(s);
				if (Debug.ON) {
					if (LOG.isLoggable(Level.WARNING)) {
						LOG.log(Level.WARNING, "Fixing edge name using '" + s + "'.");
					}
				}
			}
		});
		// Type
		final Function<E, String> edgeTypeF = graphReader.getEdgeMetadata().get(TNGraphMLWriter.EDGE_TYPE_KEY).transformer;
		// Labeled Value
		// For STN graph it can be not present.
		GraphMLMetadata<E> fieldReader = graphReader.getEdgeMetadata().get(TNGraphMLWriter.EDGE_LABELED_VALUE_KEY);
		final Function<E, String> edgeLabeledValueF = (fieldReader != null) ? fieldReader.transformer : null;
		// I parse also value parameter that was present in the first version of the graph file
		fieldReader = graphReader.getEdgeMetadata().get(TNGraphMLWriter.EDGE_VALUE_KEY);
		final Function<E, String> edgeValueF = (fieldReader != null) ? fieldReader.transformer : null;

		// STNU
		fieldReader = graphReader.getEdgeMetadata().get(TNGraphMLWriter.EDGE_CASE_VALUE_KEY);
		final Function<E, String> edgeCaseValueF = (fieldReader != null) ? fieldReader.transformer : null;

		final LabeledIntMapSupplier<? extends LabeledIntMap> LabIntMapSupplier = new LabeledIntMapSupplier<>(labeledValueMapImpl);
		String data;
		final boolean notCSTNUCSTNPSU = tnGraph.getType() == NetworkType.STN || tnGraph.getType() == NetworkType.CSTN
		                                || tnGraph.getType() == NetworkType.STNU || tnGraph.getType() == NetworkType.PSTN;
		for (final E e : tnGraph.getEdges()) {
			// Type
			String t = edgeTypeF.apply(e);
			if ("normal".equals(t) ||
			    "constraint".equals(t)) {// 20211014 I removed normal and constraint type because they are not clear.
				t = "requirement";
			}
			e.setConstraintType(ConstraintType.valueOf(t));
			// Labeled Value
			final LabeledNode s = tnGraph.getSource(e);
			final LabeledNode d = tnGraph.getDest(e);

			final boolean containsLabeledValues = CSTNEdge.class.isAssignableFrom(e.getClass());
			if (edgeLabeledValueF != null) {
				data = edgeLabeledValueF.apply(e);
				if (data != null && !data.isEmpty()) {
					final LabeledIntMap map = LabIntMapSupplier.get((AbstractLabeledIntMap.parse(data)));
					if (data.length() > 2 && map == null) {
						throw new IllegalArgumentException(
							"Labeled values in a wrong format: " + data + " in edge " + e);
					}
					if (!containsLabeledValues) {
						throw new IllegalArgumentException(
							"Labeled value set is present, but it cannot be stored because edge type is not a CSTN o derived type: "
							+ data);
					}
					((CSTNEdge) e).setLabeledValueMap(map);
				}
			}
			// I parse also value parameter that was present in the first version of the graph file or in STN graph
			if (edgeValueF != null) {
				data = edgeValueF.apply(e);
				if (data != null && !data.isEmpty()) {
					if (tnGraph.getType() == NetworkType.STN || tnGraph.getType() == NetworkType.STNU || tnGraph.getType() == NetworkType.PSTN) {
						((STNEdge) e).setValue(Integer.parseInt(data));
						if (e.getConstraintType() == ConstraintType.contingent) {
							final STNUEdge e1 = (STNUEdge) e;
							if (e1.getValue() <= 0) {
								assert s != null;
								s.setContingent(true);
							} else {
								assert d != null;
								d.setContingent(true);
							}
						}
					}
					if (containsLabeledValues) {
						if (((CSTNEdge) e).getLabeledValueMap().isEmpty()) {
							final LabeledIntMap map = LabIntMapSupplier.get();
							map.put(Label.emptyLabel, Integer.parseInt(data));
							((CSTNEdge) e).setLabeledValueMap(map);
						}
					}
				}
			}
			// STNU
			if (edgeCaseValueF != null) {
				data = edgeCaseValueF.apply(e);
				if (data != null && !data.isEmpty()) {
					if (tnGraph.getType() == NetworkType.STNU || tnGraph.getType() == NetworkType.PSTN) {
						final STNUEdge e1 = ((STNUEdge) e);
						e1.setLabeledValue(data);
						if (e1.getConstraintType() == ConstraintType.contingent) {
							if (e1.isUpperCase()) {
								assert s != null;
								s.setContingent(true);
							} else {
								assert d != null;
								d.setContingent(true);
							}
						}
					}
				}
			}
			if (e.isEmpty() && notCSTNUCSTNPSU) {
				tnGraph.removeEdge(e);
			}
		}
		if (notCSTNUCSTNPSU) {
			return tnGraph;
		}


		// FROM HERE the graph is assumed to be a CSTNU or CSTNPSU graph!
		final GraphMLMetadata<E> edgeLabeledUCValueMD =
			graphReader.getEdgeMetadata().get(TNGraphMLWriter.EDGE_LABELED_UC_VALUE_KEY);
		final GraphMLMetadata<E> edgeLabeledLCValueMD =
			graphReader.getEdgeMetadata().get(TNGraphMLWriter.EDGE_LABELED_LC_VALUE_KEY);
		if (edgeLabeledUCValueMD == null || edgeLabeledLCValueMD == null) {
			// TNGraph file is still in old format!
			if (Debug.ON) {
				LOG.warning("""
				            The input file does not contain the meta declaration for upper case value or lower case value.
				            Please, fix it adding
				            <key id="UpperCaseLabeledValues" for="edge">
				                <default></default>
				            </key>
				            or
				            <key id="LowerCaseLabeledValues" for="edge">
				                <default></default>
				            </key>
				            before <graph> tag.""");
			}
			return tnGraph;
		}
		final Function<E, String> edgeLabeledUCValueF = edgeLabeledUCValueMD.transformer;
		final Function<E, String> edgeLabeledLCValueF = edgeLabeledLCValueMD.transformer;

		final Class<? extends LabeledIntMap> labeledIntMapImpl;
		if (tnGraph.getType() == NetworkType.OSTNU) {
			labeledIntMapImpl = LabeledIntTreeSimpleMap.class;
		} else {
			labeledIntMapImpl = LabeledIntMapSupplier.DEFAULT_LABELEDINTMAP_CLASS;
		}
		for (final E e1 : tnGraph.getEdges()) {
			final BasicCSTNUEdge e = (BasicCSTNUEdge) e1;
			// Labeled UC Value
			data = edgeLabeledUCValueF.apply(e1);
			LabeledALabelIntTreeMap upperCaseMap = LabeledALabelIntTreeMap.parse(data, aLabelAlphabet, labeledIntMapImpl);
			if (data != null && data.length() > 2 && (upperCaseMap == null || upperCaseMap.isEmpty())) {
				throw new IllegalArgumentException("Upper Case values in a wrong format: " + data + " in edge " + e);
			}
			if (upperCaseMap == null) {
				upperCaseMap = new LabeledALabelIntTreeMap(labeledIntMapImpl);
			}
			e.setUpperCaseValueMap(upperCaseMap);
			// Labeled LC Value
			data = edgeLabeledLCValueF.apply(e1);
			if (tnGraph.getType() == NetworkType.CSTNU) {
				LabeledLowerCaseValue lowerCaseValue = LabeledLowerCaseValue.parse(data, aLabelAlphabet);
				if (data != null && data.length() > 2 && (lowerCaseValue == null || lowerCaseValue.isEmpty())) {
					throw new IllegalArgumentException(
						"Lower Case values in a wrong format: " + data + " in edge " + e);
				}
				if (lowerCaseValue == null) {
					lowerCaseValue = LabeledLowerCaseValue.emptyLabeledLowerCaseValue;
				}
				((CSTNUEdge) e1).setLowerCaseValue(lowerCaseValue);
			}
			if (tnGraph.getType() == NetworkType.CSTNPSU) {
				LabeledALabelIntTreeMap lowerCaseValue = LabeledALabelIntTreeMap.parse(data, aLabelAlphabet, labeledIntMapImpl);
				if (data != null && data.length() > 2 && (lowerCaseValue == null || lowerCaseValue.isEmpty())) {
					throw new IllegalArgumentException(
						"Lower Case values in a wrong format: " + data + " in edge " + e);
				}
				if (lowerCaseValue == null) {
					lowerCaseValue = new LabeledALabelIntTreeMap(labeledIntMapImpl);
				}
				((CSTNPSUEdge) e1).setLowerCaseValue(lowerCaseValue);
			}

			if (e.isEmpty()) {
				tnGraph.removeEdge(e1);
			}
		}

		return tnGraph;
	}
}
