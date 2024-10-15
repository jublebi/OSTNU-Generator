// SPDX-FileCopyrightText: 2020 Roberto Posenato <roberto.posenato@univr.it>
//
// SPDX-License-Identifier: LGPL-3.0-or-later
package it.univr.di;

/**
 * Contains the static final boolean for enable/disable debug logging in all it.univr.di subpackages.
 *
 * @author posenato
 */
public enum Debug {
	//a class containing just a static constant can be represented as an enum.
	;//this ';' is necessary for a proper enum.
	/**
	 * set to false to allow compiler to identify and eliminate debug code.
	 */
	public static final boolean ON = false;
}
