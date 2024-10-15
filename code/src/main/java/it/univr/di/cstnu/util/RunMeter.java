package it.univr.di.cstnu.util;

import java.sql.Time;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Simple class for making a meter in the console.
 *
 * @author posenato
 */
public class RunMeter {
	/**
	 * A value between 0 and 100
	 */
	static final int maxMeterSize = 50;

	/**
	 *
	 */
	long current;
	/**
	 *
	 */
	long startTime;
	/**
	 *
	 */
	long total;

	/**
	 * @param inputStartTime in milliseconds
	 * @param inputTotal     number of time to show
	 * @param inputCurrent   number of time to show
	 */
	@SuppressWarnings("SameParameterValue")
	public RunMeter(long inputStartTime, long inputTotal, long inputCurrent) {
		current = inputCurrent;
		total = inputTotal;
		startTime = inputStartTime;
	}

	/**
	 *
	 */
	public void printProgress() {
		if (current < total) {
			current++;
		}
		printProgress(current);
	}

	/**
	 * Each call of method, advance `this.current` and print the meter.
	 *
	 * @param givenCurrent current meter
	 */
	public void printProgress(long givenCurrent) {

		final long now = System.currentTimeMillis();
		final long eta = givenCurrent == 0 ? 0 : (total - givenCurrent) * (now - startTime) / givenCurrent;

		final String etaHms = givenCurrent == 0
		                      ? "N/A"
		                      : String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(eta)
			                      , TimeUnit.MILLISECONDS.toMinutes(eta) % TimeUnit.HOURS.toMinutes(1)
			                      , TimeUnit.MILLISECONDS.toSeconds(eta) % TimeUnit.MINUTES.toSeconds(1));

		final int percent = (int) (givenCurrent * 100 / total);
		final int percentScaled = (int) (givenCurrent * maxMeterSize / total);
		final String string = '\r' + String.format("%s %3d%% [", new Time(now), percent) + String.join("",
		                                                                                               Collections.nCopies(
			                                                                                               percentScaled,
			                                                                                               "=")) + '>' +
		                      String.join("",
		                                  Collections.nCopies(maxMeterSize - percentScaled, " ")) + ']' +
		                      String.join("", Collections.nCopies(
			                      (int) (Math.log10(total)) - (int) (Math.log10((givenCurrent < 1) ? 1 : givenCurrent)),
			                      " "))
		                      + String.format(" %d/%d, ETA: %s", givenCurrent, total, etaHms);

		System.out.print(string);
	}
}
