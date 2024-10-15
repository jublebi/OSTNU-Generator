// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.graph;

/**
 * Root class for representing edges in it.univr.di.cstnu package.
 *
 * @author posenato
 * @version $Rev: 851 $
 */
public interface Edge extends Component {

	/**
	 * Possible types of an edge.
	 *
	 * @author posenato
	 */
	enum ConstraintType {
		/**
		 * The edge represents a user requirement
		 */
		requirement,

		/**
		 * The edge represent a contingent constraint.
		 */
		contingent,

		/**
		 * The edge represents a constraint derived by the controllability check algorithm.
		 */
		derived,

		/**
		 * The edge represents an internal one used to represent high level construct as a WORKFLOW OR JOIN.
		 */
		internal,

		/**
		 * The edge represents an internal one used by qloop finder
		 */
		qloopFinder
	}

	/**
	 * @param e the other edge
	 *
	 * @return true if it has the same values.
	 */
	boolean hasSameValues(Edge e);

	/**
	 * This method is inappropriate here, but it helps to speed up the code.
	 *
	 * @return true if the edge is CSTN edge
	 */
	boolean isCSTNEdge();

	/**
	 * This method is inappropriate here, but it helps to speed up the code.
	 *
	 * @return true if the edge is CSTNPSU edge
	 */
	boolean isCSTNPSUEdge();

	/**
	 * This method is inappropriate here, but it helps to speed up the code.
	 *
	 * @return true if the edge is CSTNU edge
	 */
	boolean isCSTNUEdge();

	/**
	 * @return true if it does not contain any values
	 */
	boolean isEmpty();

	/**
	 * @return true if the edge is a normal edge or similar (it is not contingent).
	 */
	default boolean isRequirementEdge() {
		return !isContingentEdge();
	}

	/**
	 * @return true if the constraint is a contingent one.
	 */
	default boolean isContingentEdge() {
		return getConstraintType() == ConstraintType.contingent;
	}

	/**
	 * @return the type of the edge with respect to the classification used inside the CSTNU Tool.
	 *
	 * @see ConstraintType
	 */
	ConstraintType getConstraintType();

	/**
	 * @param type the type to set
	 */
	void setConstraintType(ConstraintType type);

	/**
	 * This method is inappropriate here, but it helps to speed up the code.
	 *
	 * @return true if the edge is STN edge
	 */
	boolean isSTNEdge();

	/**
	 * This method is inappropriate here, but it helps to speed up the code.
	 *
	 * @return true if the edge is STNU edge
	 */
	boolean isSTNUEdge();

	/**
	 * This method is inappropriate here, but it helps to speed up the code.
	 *
	 * @return true if the edge is OSTNU edge
	 */
	boolean isOSTNUEdge();

	/**
	 * Factory
	 *
	 * @return an object of type Edge.
	 */
	Edge newInstance();

	/**
	 * Any super-interfaces/implementing classes should assure that such method has edge as argument!
	 *
	 * @param edge an object to clone.
	 *
	 * @return an object of type Edge.
	 */
	Edge newInstance(Edge edge);

	/**
	 * Factory
	 *
	 * @param name of the edge
	 *
	 * @return an object of type Edge.
	 */
	Edge newInstance(String name);

	/**
	 * A copy by reference of internal structure of edge e.
	 *
	 * @param e edge to clone. If null, it does nothing.
	 */
	void takeIn(Edge e);
}
