// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di.cstnu.algorithms;

import java.io.Serial;

/**
 * Some common types of unsatisfied property for well-defined CSTN.
 *
 * @author posenato
 * @version $Rev: 840 $
 */
public class WellDefinitionException extends Exception {

	/**
	 *
	 */
	public WellDefinitionException() {
		type = null;
	}

	/**
	 *
	 */
	@Serial
	private static final long serialVersionUID = 2L;
	/**
	 * Type of exception.
	 */
	private Type type;

	/**
	 * @param message a {@link java.lang.String} object.
	 */
	public WellDefinitionException(String message) {
		super(message);
		type = null;
	}


	/**
	 * @param message a {@link java.lang.String} object.
	 * @param t       a {@link it.univr.di.cstnu.algorithms.WellDefinitionException.Type} object.
	 */
	public WellDefinitionException(String message, Type t) {
		super(message);
		type = t;
	}

	/**
	 * Kinds of possible errors in checking well-definition property.
	 *
	 * @author posenato
	 */
	public enum Type {
		/**
		 *
		 */
		LabelInconsistent,
		/**
		 *
		 */
		LabelNotSubsumes,
		/**
		 *
		 */
		ObservationNodeDoesNotExist,
		/**
		 *
		 */
		@Deprecated
		ObservationNodeDoesNotOccurBefore
	}

	/**
	 * @param cause a {@link java.lang.Throwable} object.
	 */
	public WellDefinitionException(Throwable cause) {
		super(cause);
	}

	/**
	 * @param message a {@link java.lang.String} object.
	 * @param cause   a {@link java.lang.Throwable} object.
	 */
	public WellDefinitionException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @param message            a {@link java.lang.String} object.
	 * @param cause              a {@link java.lang.Throwable} object.
	 * @param enableSuppression  a boolean.
	 * @param writableStackTrace a boolean.
	 */
	public WellDefinitionException(String message, Throwable cause, boolean enableSuppression,
	                               boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @return the type of exception.
	 */
	public Type getType() {
		return type;
	}
}
