package it.univr.di.cstnu.util;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;

/**
 * Represent a minimal interface to an optimization engine that can solve non-linear optimization problems.
 * <p>
 * Such interface is primarily used by {@link it.univr.di.cstnu.algorithms.PSTN} class.
 * <p>
 * One available implementation based on MatLab tool is at <a
 * href="https://profs.scienze.univr.it/~posenato/software/cstnu/matlabplugin">MatLabPlugin4CSTNUTool</a>.
 */
public interface OptimizationEngine {

	/**
	 * Records the result of a call to the {@link #nonLinearOptimization(double[], double[][], double[], double[], double[])} method.
	 *
	 * @param solution     the new solution
	 * @param optimumValue the new optimum value
	 * @param exitFlag     1 = OK! First-order optimality measure was less than options.OptimalityTolerance, and maximum constraint violation was less than
	 *                     options.ConstraintTolerance.<br> 0 = NO OK! Number of iterations exceeded options.MaxIterations or number of function evaluations
	 *                     exceeded options.MaxFunctionEvaluations.<br> -1 = NO OK! Stopped by an output function or plot function.<br> -2 = NO OK! No feasible
	 *                     point was found.
	 *                     <br>OK! 2 = Change in x was less than options.StepTolerance and maximum constraint violation was less than
	 *                     options.ConstraintTolerance (All algorithms except active-set).<br> -3 = Objective function at current iteration went below
	 *                     options.ObjectiveLimit and maximum constraint violation was less than options.ConstraintTolerance.
	 */
	@SuppressFBWarnings(value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"}, justification = "I prefer to suppress these FindBugs warnings")
	record OptimizationResult(double[] solution, double optimumValue, int exitFlag) {
		@Override
		public String toString() {
			return "solution: " + Arrays.toString(solution) + "\noptimumValue: " + optimumValue + "\nexit flag: " + exitFlag;
		}
	}

	/**
	 * Closes the connection to the optimization engine.
	 */
	void close();

	/**
	 * Finds a minimum value of the non-linear problem specified as
	 * <pre>
	 * min_x f(x) such that
	 *                   A⋅x ≤ b,
	 *                   lb ≤ x ≤ ub.
	 * </pre>
	 * where b is an array of doubles, A is a bi-dimensional array of doubles, and f(x) is a function that returns a scalar. f(x) is a nonlinear function.
	 * Parameters x, lb, and ub are arrays of double.
	 * <p>
	 * This method is tailored to solve the optimization problem associated to the STNU approximation task for a PSTN. So, it requires also to specify the means
	 * of log-normal distributions of contingent links and std. dev. of log-normal distributions of contingent links of the considered PSTN.
	 *
	 * @param x     initial solution. Must be double.
	 * @param A     constraint coefficients. Each row represents the constraint coefficient relative a negative cycle. Must be double
	 * @param b     negative cycle values. Must be double.
	 * @param mu    means of log-normal distributions of contingent links
	 * @param sigma std. dev. of log-normal distributions of contingent link
	 *
	 * @return a #OptimizationResult object representing an optimal solution.
	 * @throws InterruptedException possible exception
	 * @throws ExecutionException possible exception
	 */
	OptimizationResult nonLinearOptimization(double[] x, double[][] A, double[] b, double[] mu, double[] sigma) throws ExecutionException, InterruptedException;
}
