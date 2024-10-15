package it.univr.di.cstnu.util;

import org.apache.commons.math3.distribution.LogNormalDistribution;

import java.io.Serializable;

/**
 * Log-normal distribution. Initially created inside PSTN class. Then, extracted to allow the compilation of the package without class PSTN.
 */
public class LogNormalDistributionParameter implements Serializable {
	/**
	 * @param input a {@link #toString()} representation of a LogNormalDistributionParameter object. <br> Format is "LogNormalDistributionParameter[location=%f,
	 *              scale=%f, shift=%d]"
	 *
	 * @return the LogNormalDistributionParameter object represented in the input, null otherwise.
	 */
	public static LogNormalDistributionParameter parse(String input) {
		if (input == null) {
			return null;
		}
		final double location;
		final double scale;
		int shift = 0;
		//The toString representation is like "LogNormalDistributionParameter[location=1.5, scale=0.5]"
		int locStart = input.indexOf("location=");
		if (locStart == -1) {
			return null;
		}
		locStart += "location=".length();
		int scaleStart = input.indexOf("scale=", locStart);
		if (scaleStart == -1) {
			return null;
		}
		final int comaIndex = scaleStart - 2;//there is a space
		scaleStart += "scale=".length();
		location = Double.parseDouble(input.substring(locStart, comaIndex));

		final int end;
		int shiftStart = input.indexOf("shift=", scaleStart);
		if (shiftStart != -1) {
			end = shiftStart - 2;
			shiftStart += "shift=".length();
			shift = Integer.parseInt(input.substring(shiftStart, input.length() - 1));
		} else {
			end = input.length() - 1;
		}
		scale = Double.parseDouble(input.substring(scaleStart, end));

		return new LogNormalDistributionParameter(location, scale, shift);
	}

//		/**
//		 * Shifts the location of this distribution by a quantity.
//		 *
//		 * <p>
//		 * This method is useful when the activation node of a contingent link is rerouted. In this case, the contingent link bounds are adjusted
//		 * adding/subtracting a quantity. Therefore, the parameter describing the log-normal distribution must be adjusted to represent the new possible domain
//		 * of the distribution. This requires to shift the location parameter and adjust the scale.
//		 * </p>
//		 * <p>Such lcation and scale adjustment can be determined using two relations: <code>median = e<sup>location</sup>,
//		 * mode= e<sup>location-scale^2</sup></code> Adding the shift to the current median and mode, it is then possible to determine the new location and the
//		 * new scale.
//		 * </p>
//		 *
//		 * @param source the log-normal parameters that must be shifted.
//		 * @param shift  the shift of the location parameter. This value has to be considered in the scale of random variable. The method determines the right
//		 *               ln() value for the location shift.
//		 *
//		 * @return the new log-normal parameters. If the shift determines an impossible distribution (e.g., a negative location), it returns null.
//		 */
//		public static LogNormalDistributionParameter shiftMean(@Nonnull LogNormalDistributionParameter source, int shift) {
//			//new median and mode
//			if (shift == 0) {
//				return source;
//			}
//			final double newMedian = Math.exp(source.location()) + shift;
//			final double newMode = Math.exp(source.location() - Math.pow(source.scale(), 2)) + shift;
//			if (newMedian <= 0 || newMode <= 0) {
//				return null;
//			}
//			final double newLocation = Math.log(newMedian);
//			final double newScale = Math.sqrt(newLocation - Math.log(newMode));
//			if (Debug.ON) {
//				if (LOG.isLoggable(Level.FINEST)) {
//					LOG.finest("Request of a shift " + shift + " of log-normal parameters " + source.toString()
//					           + ". New parameters: location=" + newLocation + ", scale=" + newScale);
//				}
//			}
//			return new LogNormalDistributionParameter(newLocation, newScale);
//		}


	/**
	 * Considering the associated normal distribution, this is the mean μ of the normal distribution.
	 */
	private final double location;
	/**
	 * Considering the associated normal distribution, this is the std.dev. σ of the normal distribution.
	 */
	private final double scale;
	/**
	 * The pdf
	 */
	private final LogNormalDistribution logNormalDistribution;
	/**
	 * It is possible that the activation time point is shifted by a rigid distance. In such a case, the log-normal distribution of the contingent link is the
	 * same, but the sample values must be simply added to the shift to have a right value. It would be possible to adjust the location and scale of the
	 * log-normal distribution to incorporate such a shift, but we verified that such an adjustment introduce some approximation errors.
	 */
	private int shift;

	/**
	 * @param location Considering the associated normal distribution, this is the mean μ of the normal distribution.
	 * @param scale    Considering the associated normal distribution, this is the std.dev. σ of the normal distribution.
	 * @param shift    It is possible that the activation time point is shifted by a rigid distance. In such a case, the log-normal distribution of the
	 *                 contingent link is the same, but the sample values must be simply added to the shift to have a right value. It would be possible to
	 *                 adjust the location and scale of the log-normal distribution to incorporate such a shift, but we verified that such an adjustment
	 *                 introduce some approximation errors. Be careful: if the original pdf was for the contingent link  X --v--> A===>C and A was moved to X,
	 *                 the range of contingent link is increased by v. The shift is 'v', not (-v). Shift can be only positive.
	 */
	public LogNormalDistributionParameter(double location, double scale, int shift) {
		if (location < 0 || scale < 0 || shift < 0) {
			throw new IllegalArgumentException("Parameters must be non-negative: location=" + location + ", scale=" + scale + ", shift=" + shift);
		}
		this.location = location;
		this.scale = scale;
		this.shift = shift;
		this.logNormalDistribution = new LogNormalDistribution(this.location, this.scale);
	}

	/**
	 * @param location Considering the associated normal distribution, this is the mean μ of the normal distribution.
	 * @param scale    Considering the associated normal distribution, this is the std.dev. σ of the normal distribution.
	 */
	public LogNormalDistributionParameter(double location, double scale) {
		this(location, scale, 0);

	}

	/**
	 * @param x value in the domain (it will compensate by -shift)
	 *
	 * @return the cumulative probability at x.
	 */
	public double cumulativeProbability(double x) {
		return this.logNormalDistribution.cumulativeProbability(x - shift);
	}

	/**
	 * @return location
	 */
	public double getLocation() {
		return location;
	}

	/**
	 * @return the associated pdf
	 */
	public LogNormalDistribution getLogNormalDistribution() {
		return logNormalDistribution;
	}

	/**
	 * @return the scale
	 */
	public double getScale() {
		return scale;
	}

	/**
	 * @return the shift
	 */
	public int getShift() {
		return shift;
	}

	/**
	 * Registers shift.
	 *
	 * @param shift the shift of the location parameter.
	 */
	public void setShift(int shift) {
		this.shift = shift;
	}

	/**
	 * @return a random value sampled by the current log-normal distribution adjusted by shift value.
	 */
	public double sample() {
		return this.logNormalDistribution.sample() + shift;
	}

	public String toString() {
		return String.format("LogNormalDistributionParameter[location=%f, scale=%f, shift=%d]", this.location, this.scale, this.shift);
	}

}
