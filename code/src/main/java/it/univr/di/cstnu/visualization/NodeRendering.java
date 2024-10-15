// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.visualization;

import com.google.common.base.Function;
import edu.uci.ics.jung.visualization.picking.PickedInfo;
import it.univr.di.cstnu.graph.LabeledNode;

import java.awt.*;

/**
 * Provides method for LabeledNode rendering in TNEditor GUI application.
 *
 * @author posenato
 * @version $Rev: 832 $
 */
@SuppressWarnings("ALL")
public enum NodeRendering {
	;

	/**
	 * Used to show the node name.
	 */
	@SuppressWarnings("AnonymousInnerClass")
	public final static Function<LabeledNode, String> vertexLabelFunction = new Function<>() {
		/**
		 * Returns a label for the node
		 */
		@Override
		public String apply(final LabeledNode v) {
			if (v == null) {
				return "";
			}

			return v.getName() + (v.getLabel().isEmpty() ? "" : "_[" + v.getLabel() + "]");
		}
	};

	/**
	 * Transformer object to show the tooltip of node: the label is print.
	 */
	public static final Function<LabeledNode, String> vertexToolTipFunction = v -> {
		if (v == null) {
			return "";
		}
		return "Propositional label: " + v.getLabel().toString();
	};

	/**
	 * Returns a transformer to select the color that is used to draw the node. This transformer uses the type and the
	 * state of the node to select the color.
	 *
	 * @param pi                 a {@link edu.uci.ics.jung.visualization.picking.PickedInfo} object.
	 * @param fillPaint          color for non-selected node
	 * @param pickedPaint        color when the node is selected
	 * @param negativeCyclePaint color when node is in a negative cycle.
	 * @param contingentPaint    color for contingent nodes.
	 * @param observerPaint      color when node is an observer one.
	 * @param parameterPaint     color when node is a parameter one.
	 * @param <K>                a K object.
	 * @return a transformer object to draw an edge with a different color when it is picked.
	 */
	public static <K extends LabeledNode> Function<K, Paint> nodeDrawPaintTransformer(
			final PickedInfo<K> pi,
			final Paint fillPaint, final Paint pickedPaint, final Paint negativeCyclePaint, final Paint contingentPaint,
			final Paint observerPaint, final Paint parameterPaint) {
		return node -> {
			if (node == null) {
				return fillPaint;
			}
			if (pi.isPicked(node)) {
				return pickedPaint;
			}
			if (node.inNegativeCycle()) {
				return negativeCyclePaint;
			}
			if (node.isContingent()) {
				return contingentPaint;
			}
			if (node.isParameter()) {
				return parameterPaint;
			}
			if (node.isObserver()) {
				return observerPaint;
			}
			return fillPaint;
		};
	}
}
