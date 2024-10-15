package it.univr.di.labeledvalue;

/**
 * Extends LabeledIntTreeMap setting optimize field to false. In this way, labeled values where labels not shorten. This class is useful for OSTNUPluggableEdge
 * class.
 */
public class LabeledIntTreeSimpleMap extends LabeledIntTreeMap {
	/**
	 * Constructor to clone the structure. For optimization issue, this method clone only LabeledIntTreeMap object.
	 *
	 * @param lvm      the LabeledValueTreeMap to clone. If lvm is null, this will be an empty map.
	 * @param optimize ignored.
	 *
	 * @see LabeledIntTreeSimpleMap()
	 */
	LabeledIntTreeSimpleMap(final LabeledIntMap lvm, final boolean optimize) {
		super(lvm, false);
	}

	/**
	 * Constructor to clone the structure. For optimization issue, this method clone only LabeledIntTreeMap object.
	 *
	 * @param lvm the LabeledValueTreeMap to clone. If lvm is null, this will be an empty map.
	 */
	LabeledIntTreeSimpleMap(final LabeledIntMap lvm) {
		super(lvm, false);
	}


	/**
	 * Necessary constructor for the factory. The internal structure is built and empty.
	 *
	 * @param optimize ignored
	 *
	 * @see LabeledIntTreeSimpleMap()
	 */
	LabeledIntTreeSimpleMap(final boolean optimize) {
		super(false);
	}

	/**
	 * Necessary constructor for the factory. The internal structure is built and empty.
	 */
	LabeledIntTreeSimpleMap() {
		super(false);
	}

	@Override
	public LabeledIntTreeSimpleMap newInstance() {
		return new LabeledIntTreeSimpleMap(false);
	}

	/**
	 * @param optimize ignored
	 *
	 * @return a new instance
	 */
	@Override
	public LabeledIntTreeSimpleMap newInstance(boolean optimize) {
		return new LabeledIntTreeSimpleMap(false);
	}

	@Override
	public LabeledIntTreeSimpleMap newInstance(LabeledIntMap lim) {
		return new LabeledIntTreeSimpleMap(lim, false);
	}

	/**
	 * @param lim      an object to clone.
	 * @param optimize ignored
	 *
	 * @return a new instance
	 */
	@Override
	public LabeledIntTreeSimpleMap newInstance(LabeledIntMap lim, boolean optimize) {
		return new LabeledIntTreeSimpleMap(lim, false);
	}

}
