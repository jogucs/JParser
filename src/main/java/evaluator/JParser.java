package evaluator;

import misc.EvalContext;
import misc.Matrix;
import misc.MathObject;
import parser.Parser;
import tokenizer.Tokenizer;

/**
 * Utility entry-point for parsing and evaluating mathematical expressions.
 *
 * <p>
 * This class exposes static helpers to evaluate expressions, create user-defined functions,
 * and perform matrix operations. It holds a global {@code CONTEXT} and {@code EVALUATOR}
 * used across evaluations. Designed as a thin facade over the tokenizer/parser/evaluator
 * components.
 * </p>
 *
 * <p>
 * Example usage:
 * <pre>
 * evaluator.JParser.createFunction("f(x, y, z) = x^2 + z^2 + y^2");
 * MathObject result = evaluator.JParser.evaluate("-x + f(1, 1, 1) - 9/3");
 * </pre>
 * </p>
 */
public abstract class JParser {
    /**
     * Global evaluation context. Contains variables, user-defined functions and native functions.
     * Shared across evaluations; child contexts are created as needed when evaluating function bodies.
     */
    public static EvalContext CONTEXT = new EvalContext();

    /**
     * Global evaluator instance used to evaluate parsed ASTs.
     */
    public static Evaluator EVALUATOR = new Evaluator();

    /**
     * Parser instance used for the most recent parse operation.
     *
     * <p>
     * This field is updated on each call to {@link #evaluate(String)}. Keeping it public
     * can be useful for debugging or inspecting the last-parsed expression, but callers
     * should not rely on it being non-null between calls.
     * </p>
     */
    public static Parser PARSER;

    /**
     * Whether angle inputs/outputs should be interpreted in degrees.
     *
     * <p>
     * This flag is available for future use by the evaluator/native functions that
     * perform trigonometric computations. Currently the flag is stored but not read
     * by the native trig implementations in {@link misc.EvalContext}.
     * </p>
     */
    private static boolean degrees;

    /**
     * Parse and evaluate the given expression string using the shared {@link EvalContext}.
     *
     * <p>
     * If the expression is empty or only whitespace, returns a {@link MathObject} wrapping 0.0.
     * Otherwise the expression is tokenized and parsed to an AST and then evaluated.
     * </p>
     *
     * @param expression the expression string to evaluate
     * @return a {@link MathObject} containing the numeric or symbolic evaluation result
     */
    public static MathObject evaluate(String expression) {
        if (expression.trim().isEmpty()) {
            // Return zero for empty input
            return new MathObject(0.0);
        }
        // Tokenize and parse the expression, storing the parser for potential inspection
        PARSER = new Parser(new Tokenizer(expression).tokenize());
        // Evaluate the parsed AST using the shared CONTEXT
        return EVALUATOR.evaluate(PARSER.parseExpression(), CONTEXT);
    }

    /**
     * Create a new user-defined function from the given function definition expression.
     *
     * <p>
     * The provided expression should be a valid function definition, for example:
     * {@code "f(x) = x^2"} or {@code "g(a,b)=a+b"}. The function is added to the global
     * context maintained by {@link #CONTEXT}.
     * </p>
     *
     * @param expression function definition expression
     */
    public static void createFunction(String expression) {
        CONTEXT.addFunction(expression);
    }

    /**
     * Toggle whether trigonometric functions and other angle-based operations should
     * use degrees instead of radians.
     *
     * <p>
     * Note: Native trig implementations must read this flag to change behavior; current
     * built-in functions in {@link misc.EvalContext} use {@link Math} which expects radians.
     * </p>
     *
     * @param degrees true to use degrees, false to use radians
     */
    public static void changeDegrees(boolean degrees) {
        JParser.degrees = degrees;
    }

    /**
     * Perform row reduction (Gaussian elimination) on the provided matrix.
     *
     * @param matrix the matrix to row-reduce
     * @return a new {@link Matrix} in reduced row-echelon form (or partially reduced depending on implementation)
     */
    public static Matrix rowReduce(Matrix matrix) {
        return MatrixMath.rowReduce(matrix);
    }

    /**
     * Convert the provided matrix to echelon form.
     *
     * @param matrix the matrix to convert
     * @return a {@link Matrix} reduced to echelon form
     */
    public static Matrix echelonForm(Matrix matrix) {
        return MatrixMath.reduceToEchelon(matrix);
    }
}
