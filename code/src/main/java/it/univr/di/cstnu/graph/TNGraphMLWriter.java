// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.graph;

import edu.uci.ics.jung.algorithms.layout.AbstractLayout;
import edu.uci.ics.jung.graph.Hypergraph;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import it.univr.di.cstnu.graph.TNGraph.NetworkType;
import it.univr.di.labeledvalue.Constants;
import it.univr.di.labeledvalue.Literal;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Allows the writing of a Temporal Network graph to a file or a string in GraphML format.
 * <br>
 * GraphML format allows the definition of different attributes for the TNGraph, vertices and edges.
 * <br>
 * All attributes are defined in the first part of a GraphML file. Examples of GraphML file that can read by this class
 * are given in the Instances directory under CstnuTool one.
 * <br>
 * It assumes that nodes are {@linkplain LabeledNode} and edges are {@linkplain Edge}.
 *
 * @author posenato
 * @version $Rev: 897 $
 */
@SuppressFBWarnings(value = "SIC_INNER_SHOULD_BE_STATIC_ANON", justification = "The gain making static inner class is not so worth.")
public class TNGraphMLWriter extends edu.uci.ics.jung.io.GraphMLWriter<LabeledNode, Edge> {

	/**
	 *
	 */
	static final public String EDGE_CASE_VALUE_KEY = "LabeledValue";
	/**
	 *
	 */
	static final public String EDGE_LABELED_LC_VALUE_KEY = "LowerCaseLabeledValues";
	/**
	 *
	 */
	static final public String EDGE_LABELED_UC_VALUE_KEY = "UpperCaseLabeledValues";
	/**
	 *
	 */
	static final public String EDGE_LABELED_VALUE_KEY = "LabeledValues";
	/**
	 *
	 */
	static final public String EDGE_TYPE_KEY = "Type";
	/**
	 *
	 */
	static final public String EDGE_VALUE_KEY = "Value";
	/**
	 *
	 */
	static final public String GRAPH_NAME_KEY = "Name";
	/**
	 *
	 */
	static final public String GRAPH_nCTG_KEY = "nContingent";
	/**
	 *
	 */
	static final public String GRAPH_nEDGES_KEY = "nEdges";
	/**
	 *
	 */
	static final public String GRAPH_nOBS_KEY = "nObservedProposition";
	/**
	 *
	 */
	static final public String GRAPH_nVERTICES_KEY = "nVertices";
	/**
	 *
	 */
	static final public String NETWORK_TYPE_KEY = "NetworkType";
	/**
	 *
	 */
	static final public String NODE_LABEL_KEY = "Label";

	/**
	 *
	 */
	static final public String NODE_LOGNORMALDISTRIBUTION_KEY = "LogNormalDistribution";

	/**
	 *
	 */
	static final public String NODE_OBSERVED_KEY = "Obs";
	/**
	 *
	 */
	static final public String NODE_PARAMETER_KEY = "Parameter";
	/**
	 *
	 */
	static final public String NODE_POTENTIAL_KEY = "Potential";
	/**
	 *
	 */
	static final public String NODE_X_KEY = "x";
	/**
	 *
	 */
	static final public String NODE_Y_KEY = "y";
	/**
	 * logger
	 */
	static final Logger LOG = Logger.getLogger(TNGraphMLWriter.class.getName());
	/**
	 *
	 */
	AbstractLayout<LabeledNode, ? extends Edge> layout;

	/**
	 * Graph type
	 */
	TNGraph.NetworkType networkType;

	/**
	 * Constructor for TNGraphMLWriter.
	 *
	 * @param lay a {@link edu.uci.ics.jung.algorithms.layout.AbstractLayout} object. If it is null, vertex coordinates
	 *            are determined from the property of the vertex.
	 */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "For efficiency reason, it includes an external mutable object.")
	public TNGraphMLWriter(final AbstractLayout<LabeledNode, ? extends Edge> lay) {
		layout = lay;
		networkType = NetworkType.CSTNU;
	}

	/**
	 * Helper method for making {@link #save(Hypergraph, Writer)} easier.
	 *
	 * @param graph      the network to save
	 * @param outputFile file object where to save the XML string representing the graph. File encoding is UTF8.
	 *
	 * @throws java.io.IOException if it is not possible to save to outputFile
	 */
	public void save(TNGraph<? extends Edge> graph, File outputFile) throws IOException {
		try (final Writer writer = new BufferedWriter(
			new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
			this.save(graph, writer);
		} catch (UnsupportedEncodingException e) {
			LOG.severe(e.getMessage());
			throw new IllegalStateException(e.toString());
		}
	}

	/**
	 * @param graph  the graph to save
	 * @param writer the writer to use
	 *
	 * @throws IOException if the writer cannot write the file
	 */
	@SuppressWarnings("unchecked")
	public void save(TNGraph<? extends Edge> graph, Writer writer) throws IOException {
		networkType = graph.getType();
		addMetaData();
		super.save((TNGraph<Edge>) graph, writer);
	}

	/**
	 * Helper method for making {@link #save(Hypergraph, Writer)} easier.
	 *
	 * @param graph the network to save
	 *
	 * @return a GraphML string representing the graph.
	 */
	public String save(TNGraph<? extends Edge> graph) {
		final StringWriter writer = new StringWriter();
		try {
			this.save(graph, writer);
		} catch (IOException e) {
			// a non likely possibility
			LOG.severe(e.getMessage());
			throw new IllegalStateException(e.toString());
		}
		return writer.toString();
	}

	/**
	 * Adds metadata to the file
	 */
	@SuppressWarnings("ReturnOfNull")
	private void addMetaData() {
		/*
		 * TNGraph attributes
		 */
		addGraphData(NETWORK_TYPE_KEY, "Network Type", "CSTNU", g -> networkType.toString());

		addGraphData(GRAPH_NAME_KEY, "Graph Name", "", g -> {
			if (g == null) {
				return "";
			}
			final TNGraph<Edge> g1 = (TNGraph<Edge>) (g);
			return g1.getName();
		});

		if (networkType == NetworkType.STNU || networkType == NetworkType.PSTN || networkType == NetworkType.CSTNU || networkType == NetworkType.CSTNPSU ||
		    networkType == NetworkType.PCSTNU) {
			addGraphData(GRAPH_nCTG_KEY, "Number of contingents in the graph", "0", g -> {
				if (g == null) {
					//noinspection ReturnOfNull
					return null;
				}
				return String.valueOf(((TNGraph<Edge>) (g)).getContingentNodeCount());
			});
		}

		addGraphData(GRAPH_nEDGES_KEY, "Number of edges in the graph", "0",
		             (Hypergraph<LabeledNode, Edge> g) -> String.valueOf(g.getEdgeCount()));

		if (networkType == NetworkType.OSTNU || networkType == NetworkType.CSTN || networkType == NetworkType.CSTNU || networkType == NetworkType.CSTNPSU ||
		    networkType == NetworkType.PCSTNU) {
			addGraphData(GRAPH_nOBS_KEY, "Number of observed propositions in the graph", "0", g -> {
				if (g == null) {
					return null;
				}
				return String.valueOf(((TNGraph<Edge>) (g)).getObserverCount());
			});
		}

		addGraphData(GRAPH_nVERTICES_KEY, "Number of vertices in the graph", "0", g -> {
			if (g == null) {
				return "";
			}
			return String.valueOf(g.getVertexCount());
		});

		/*
		 * Node attributes
		 */
		setVertexIDs(v -> {
			if (v == null) {
				return "";
			}
			return v.getName();
		});

		addVertexData(NODE_X_KEY, "The x coordinate for the visualization. A positive value.", "0",
		              v -> Double.toString((layout != null) ? layout.getX(v) : v.getX()));
		addVertexData(NODE_Y_KEY, "The y coordinate for the visualization. A positive value.", "0",
		              v -> Double.toString((layout != null) ? layout.getY(v) : v.getY()));
		if (networkType == NetworkType.OSTNU || networkType == NetworkType.CSTN || networkType == NetworkType.CSTNU || networkType == NetworkType.CSTNPSU ||
		    networkType == NetworkType.PCSTNU) {
			addVertexData(NODE_OBSERVED_KEY, "Proposition Observed. Value specification: " + Literal.PROPOSITION_RANGE,
			              "", v -> {
					if (v == null || (v.propositionObserved == Constants.UNKNOWN)) {
						return null;
					}
					return String.valueOf(v.propositionObserved);
				});
		}
		if (networkType == NetworkType.PCSTNU) {
			addVertexData(NODE_PARAMETER_KEY, "Parameter node. Value specification: true", "", v -> {
				if (v == null) {
					return null;
				}
				return (v.isParameter()) ? "" + true : null;
			});
		}

		if (networkType == NetworkType.OSTNU || networkType == NetworkType.CSTN || networkType == NetworkType.CSTNU || networkType == NetworkType.CSTNPSU ||
		    networkType == NetworkType.PCSTNU) {
			addVertexData(NODE_POTENTIAL_KEY,
			              "Labeled Potential Values. Format: {[('node name (no case modification)', 'integer', 'label') ]+}|{}",
			              "", v -> {
					if (v == null) {
						return "";
					}
					final String s = v.getLabeledPotential().toString();
					return (s.startsWith("{}")) ? null : s;
				});
		}

		if (networkType == NetworkType.CSTN || networkType == NetworkType.CSTNU || networkType == NetworkType.CSTNPSU ||
		    networkType == NetworkType.PCSTNU) {
			addVertexData(NODE_LABEL_KEY,
			              "Label. Format: [¬" + Literal.PROPOSITION_RANGE + "|" + Literal.PROPOSITION_RANGE + "]+|⊡",
			              "⊡", v -> {
					if (v == null) {
						return "";
					}
					return v.getLabel().toString();
				});
		}

		/*
		 * Write possible distribution probability function associated to the duration of the contingent link
		 */
		if (networkType == NetworkType.PSTN) {
			addVertexData(NODE_LOGNORMALDISTRIBUTION_KEY,
			              "LogNormalDistributionParameter. Format: LogNormalDistributionParameter[location=..., scale=...]",
			              "", v -> {
					if (v == null || v.getLogNormalDistribution() == null) {
						return null;
					}
					return v.getLogNormalDistribution().toString();
				});
		}

		/*
		 * Edge attributes
		 */
		setEdgeIDs(e -> {
			if (e == null) {
				return "";
			}
			return e.getName();
		});
		addEdgeData(EDGE_TYPE_KEY, "Type: Possible values: contingent|requirement|derived|internal.", "requirement",
		            e -> {
			            if (e == null) {
				            return "";
			            }
			            return e.getConstraintType().toString();
		            });
		if (networkType == NetworkType.OSTNU || networkType == NetworkType.CSTN || networkType == NetworkType.CSTNU || networkType == NetworkType.CSTNPSU ||
		    networkType == NetworkType.PCSTNU) {
			addEdgeData(EDGE_LABELED_VALUE_KEY, "Labeled Values. Format: {[('integer', 'label') ]+}|{}", "", e -> {
				if (e == null) {
					return "";
				}
				if (CSTNEdge.class.isAssignableFrom(e.getClass())) {
					return ((CSTNEdge) e).getLabeledValueMap().toString();
				}
				return null;
			});
		}

		if (networkType == NetworkType.OSTNU || networkType == NetworkType.STN || networkType == NetworkType.STNU || networkType == NetworkType.PSTN) {
			addEdgeData(EDGE_VALUE_KEY, "Value for STN edge. Format: 'integer'", "", e -> {
				if (e == null) {
					return "";
				}
				if (e.isSTNEdge() || e.isSTNUEdge() || e.isOSTNUEdge()) {
					final int v = ((STNEdge) e).getValue();
					if (v == Constants.INT_NULL) {
						return null;
					}
					return String.valueOf(v);
				}
				return null;
			});
		}

		if (networkType == NetworkType.OSTNU || networkType == NetworkType.STNU || networkType == NetworkType.PSTN) {
			addEdgeData(EDGE_CASE_VALUE_KEY, "Case Value. Format: 'LC(NodeName):integer' or 'UC(NodeName):integer'", "",
			            e -> {
				            if (e == null) {
					            return "";
				            }
				            if (e.isSTNUEdge()) {
					            final STNUEdge e1 = ((STNUEdge) e);
					            final String s = e1.getLabeledValueFormatted();
					            if (s.isEmpty()) {
						            return null;
					            }
					            return s;
				            }
				            return null;
			            });

		}

		if (networkType == NetworkType.OSTNU || networkType == NetworkType.CSTNU || networkType == NetworkType.CSTNPSU || networkType == NetworkType.PCSTNU) {
			addEdgeData(EDGE_LABELED_UC_VALUE_KEY,
			            "Labeled Upper-Case Values. Format: {[('node name (no case modification)', 'integer', 'label') ]+}|{}",
			            "", e -> {
					if (e == null) {
						return "";
					}
					if (BasicCSTNUEdge.class.isAssignableFrom(e.getClass())) {
						final String s = ((BasicCSTNUEdge) e).getUpperCaseValueMap().toString();
						return (s.startsWith("{}")) ? null : s;
					}
					return null;
				});
			addEdgeData(EDGE_LABELED_LC_VALUE_KEY,
			            "Labeled Lower-Case Values. Format: {[('node name (no case modification)', 'integer', 'label') ]+}|{}",
			            "", e -> {
					if (e == null) {
						return "";
					}
					if (BasicCSTNUEdge.class.isAssignableFrom(e.getClass())) {
						final String s = (e.isCSTNUEdge()) ? ((CSTNUEdge) e).getLowerCaseValue().toString()
						                                   : ((CSTNPSUEdge) e).getLowerCaseValueMap().toString();
						return (s.startsWith("{}")) ? null : s;
					}
					return null;
				});
		}
	}
}
