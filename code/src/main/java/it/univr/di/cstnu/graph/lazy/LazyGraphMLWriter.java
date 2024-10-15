// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 *
 */
package it.univr.di.cstnu.graph.lazy;

import com.google.common.base.Function;
import edu.uci.ics.jung.algorithms.layout.AbstractLayout;
import edu.uci.ics.jung.graph.Hypergraph;
import it.univr.di.cstnu.graph.LabeledNode;
import it.univr.di.labeledvalue.Constants;
import it.univr.di.labeledvalue.Literal;

/**
 * <p>LazyGraphMLWriter class.</p>
 *
 * @author posenato
 * @version $Id: $Id
 */
@SuppressWarnings("ALL")
public class LazyGraphMLWriter extends edu.uci.ics.jung.io.GraphMLWriter<LabeledNode, LabeledLazyWeightEdge> {

	/**
	 *
	 */
	static final public String GRAPH_NAME_KEY = "Name";

	/**
	 *
	 */
	static final public String GRAPH_nVERTICES_KEY = "nVertices";

	/**
	 *
	 */
	static final public String GRAPH_nEDGES_KEY = "nEdges";

	/**
	 *
	 */
	static final public String GRAPH_nOBS_KEY = "nObservedProposition";

	/**
	 * static final public String GRAPH_nCTG_KEY = "nContingent";
	 */

	/**
	 *
	 */
	static final public String NODE_X_KEY = "x";
	/**
	 *
	 */
	static final public String NODE_Y_KEY = "y";

	/**
	 *
	 */
	static final public String NODE_OBSERVED_KEY = "Obs";

	/**
	 *
	 */
	static final public String NODE_LABEL_KEY = "Label";

	/**
	 *
	 */
	static final public String EDGE_TYPE_KEY = "Type";

	/**
	 *
	 */
	static final public String EDGE_LABELED_VALUE_KEY = "LabeledValues";

	/**
	 * static final public String EDGE_LABELED_UC_VALUE_KEY = "UpperCaseLabeledValues";
	 */

	/**
	 * static final public String EDGE_LABELED_LC_VALUE_KEY = "LowerCaseLabeledValues";
	 */

	/**
	 *
	 */
	AbstractLayout<LabeledNode, LabeledLazyWeightEdge> layout;

	/**
	 * Constructor for LazyGraphMLWriter.
	 *
	 * @param lay a {@link edu.uci.ics.jung.algorithms.layout.AbstractLayout} object. If it is null, vertex coordinates are determined from the property of the vertex.
	 */
	public LazyGraphMLWriter(final AbstractLayout<LabeledNode, LabeledLazyWeightEdge> lay) {
		super();
		this.layout = lay;

		/*
		 * Graph attributes
		 */
		this.addGraphData(GRAPH_NAME_KEY, "Graph Name", "",
				new Function<Hypergraph<LabeledNode, LabeledLazyWeightEdge>, String>() {
					@Override
					public String apply(final Hypergraph<LabeledNode, LabeledLazyWeightEdge> g) {
						return ((LabeledLazyWeightGraph) (g)).getName();
					}
				});

		// this.addGraphData(GRAPH_nCTG_KEY, "Number of contingents in the graph", "0",
		// new Function<Hypergraph<LabeledNode, LabeledLazyWeightEdge>, String>() {
		// @Override
		// public String apply(final Hypergraph<LabeledNode, LabeledLazyWeightEdge> g) {
		// return String.valueOf(((LabeledLazyWeightGraph) (g)).getContingentCount());
		// }
		// });
		this.addGraphData(GRAPH_nEDGES_KEY, "Number of edges in the graph", "0",
				(Hypergraph<LabeledNode, LabeledLazyWeightEdge> g) -> String.valueOf(g.getEdgeCount()));
		// new Function<Hypergraph<LabeledNode, LabeledLazyWeightEdge>, String>() {
		// @Override
		// public String apply(final Hypergraph<LabeledNode, LabeledLazyWeightEdge> g) {
		// return String.valueOf(((LabeledLazyWeightGraph) (g)).getEdgeCount());
		// }
		// });
		this.addGraphData(GRAPH_nOBS_KEY, "Number of observed propositions in the graph", "0",
				new Function<Hypergraph<LabeledNode, LabeledLazyWeightEdge>, String>() {
					@Override
					public String apply(final Hypergraph<LabeledNode, LabeledLazyWeightEdge> g) {
						return String.valueOf(((LabeledLazyWeightGraph) (g)).getObserverCount());
					}
				});
		this.addGraphData(GRAPH_nVERTICES_KEY, "Number of vertices in the graph", "0",
				new Function<Hypergraph<LabeledNode, LabeledLazyWeightEdge>, String>() {
					@Override
					public String apply(final Hypergraph<LabeledNode, LabeledLazyWeightEdge> g) {
						return String.valueOf(g.getVertexCount());
					}
				});

		/*
		 * Node attributes
		 */
		this.setVertexIDs(new Function<LabeledNode, String>() {
			@Override
			public String apply(final LabeledNode v) {
				return v.getName();
			}
		});

		this.addVertexData(NODE_X_KEY, "The x coordinate for the visualitation. A positive value.", "0",
				new Function<LabeledNode, String>() {
					@Override
					public String apply(final LabeledNode v) {
						return Double.toString((LazyGraphMLWriter.this.layout != null) ? LazyGraphMLWriter.this.layout.getX(v) : v.getX());
					}
				});
		this.addVertexData(NODE_Y_KEY, "The y coordinate for the visualitation. A positive value.", "0",
				new Function<LabeledNode, String>() {
					@Override
					public String apply(final LabeledNode v) {
						return Double.toString((LazyGraphMLWriter.this.layout != null) ? LazyGraphMLWriter.this.layout.getY(v) : v.getY());
					}
				});
		this.addVertexData(NODE_OBSERVED_KEY, "Proposition Observed. Value specification: " + Literal.PROPOSITION_RANGE, "",
				new Function<LabeledNode, String>() {
					@Override
					public String apply(final LabeledNode v) {
						return (v.getPropositionObserved() != Constants.UNKNOWN) ? "" + v.getPropositionObserved() : null;
					}
				});
		this.addVertexData(NODE_LABEL_KEY, "Label. Format: [¬" + Literal.PROPOSITION_RANGE + "|" + Literal.PROPOSITION_RANGE + "]+|⊡", "⊡",
				new Function<LabeledNode, String>() {
					@Override
					public String apply(final LabeledNode v) {
						return v.getLabel().toString();
					}
				});

		/*
		 * Edge attributes
		 */
		this.setEdgeIDs(new Function<LabeledLazyWeightEdge, String>() {
			@Override
			public String apply(final LabeledLazyWeightEdge e) {
				return e.getName();
			}
		});
		this.addEdgeData(EDGE_TYPE_KEY, "Type: Possible values: normal|contingent|constraint.", "normal",
				new Function<LabeledLazyWeightEdge, String>() {
					@Override
					public String apply(final LabeledLazyWeightEdge e) {
						return e.getConstraintType().toString();
					}
				});

		this.addEdgeData(EDGE_LABELED_VALUE_KEY, "Labeled Values. Format: {[('integer', 'label') ]+}|{}", "",
				new Function<LabeledLazyWeightEdge, String>() {
					@Override
					public String apply(final LabeledLazyWeightEdge e) {
						String value = e.getLabeledValueMap().toString();
						if (value.length() > 1) {
							value = value.replaceAll("\\([-0-9]*\\[Piece ", "(").replaceAll(" \\* ∂ \\+", ";").replaceAll("\\],", ",");
							// remove sum
							value = value.replaceAll("\\(([-0-9]*)\\[Sum [-0-9SumPiecMax;\\[\\] ]*, ([¬a-zA-Z]*)\\) ", "($1, $2) ");
							// remove max
							value = value.replaceAll("\\(([-0-9]*)\\[Max [-0-9SumPiecMax;\\[\\] ]*, ([¬a-zA-Z]*)\\) ", "($1, $2) ");
						}
						return value;
					}
				});
		// this.addEdgeData(EDGE_LABELED_UC_VALUE_KEY, "Labeled Upper-Case Values. Format: {[('node name (no case modification)', 'integer', 'label') ]+}|{}",
		// "",
		// new Function<LabeledLazyWeightEdge, String>() {
		// @Override
		// public String apply(final LabeledLazyWeightEdge e) {
		// String s = e.getUpperCaseValueMap().toString();
		// return (s.startsWith("{}")) ? null : s;
		// }
		// });
		// this.addEdgeData(EDGE_LABELED_LC_VALUE_KEY, "Labeled Lower-Case Values. Format: {[('node name (no case modification)', 'integer', 'label') ]+}|{}",
		// "",
		// new Function<LabeledLazyWeightEdge, String>() {
		// @Override
		// public String apply(final LabeledLazyWeightEdge e) {
		// String s = e.getLowerCaseValue().toString();
		// return (s.startsWith("{}")) ? null : s;
		// }
		// });

	}
}
