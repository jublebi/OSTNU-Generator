// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.graph;

/**
 * Root class for representing nodes in it.univr.di.cstnu package.
 *
 * @author posenato
 * @version $Rev: 804 $
 */
@SuppressWarnings("InterfaceWithOnlyOneDirectInheritor")
public interface Node extends Component {

	/**
	 * @return true if the node is a contingent one.
	 */
	default boolean isContingent() {
		return false;
	}

	/**
	 * @return true if the node is a parameter one.
	 */
	default boolean isParameter() {
		return false;
	}

//	/**
//	 * This method is inappropriate here, but it helps to speed up the code.
//	 *
//	 * @return true if the node is CSTN node
//	 */
//	public default boolean isCSTNNode() {
//		return false;
//	}
//
//	/**
//	 * This method is inappropriate here, but it helps to speed up the code.
//	 *
//	 * @return true if the node is CSTNU node
//	 */
//	public default boolean isCSTNUNode() {
//		return false;
//	}
//
//	/**
//	 * This method is inappropriate here, but it helps to speed up the code.
//	 *
//	 * @return true if the node is PCSTNU node
//	 */
//	public default boolean isPCSTNUNode() {
//		return false;
//	}
//
//	/**
//	 * This method is inappropriate here, but it helps to speed up the code.
//	 *
//	 * @return true if the node is STN node
//	 */
//	public default boolean isSTNNode() {
//		return false;
//	}
//
//	/**
//	 * This method is inappropriate here, but it helps to speed up the code.
//	 *
//	 * @return true if the node is STN node
//	 */
//	public default boolean isSTNUNode() {
//		return false;
//	}

	/**
	 * Factory
	 *
	 * @return an object of type Edge.
	 */
	Node newInstance();

	/**
	 * Any super-interfaces/implementing classes should assure that such method has Edge node as argument!
	 *
	 * @param node an object to clone.
	 * @return an object of type Edge.
	 */
	Node newInstance(Node node);

	/**
	 * Factory
	 *
	 * @param name of the node
	 * @return an object of type Edge.
	 */
	Node newInstance(String name);

	/**
	 * A copy by reference of internal structure of node e.
	 *
	 * @param e node to clone. If null, it does nothing.
	 */
	void takeIn(Node e);
}
