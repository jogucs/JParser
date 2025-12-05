package evaluator;

import literals.Matrix;
import literals.MathObject;
import nodes.*;
import parser.Parser;
import tokenizer.Operator;
import tokenizer.Tokenizer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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

    private static DecimalFormat decimalFormat = new DecimalFormat("#.###########") {{
        setRoundingMode(RoundingMode.CEILING);
    }};
    private static final int currentPrecision = 10;

    public static final BigDecimal NEGATIVE_ONE = new BigDecimal("-1");
    public static final BigDecimal ZERO = new BigDecimal("0");
    public static final BigDecimal ONE = new BigDecimal("1");

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
     * by the native trig implementations in {@link evaluator.EvalContext}.
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
            return new MathObject(new BigDecimal(0));
        }
        // Tokenize and parse the expression, storing the parser for potential inspection
        PARSER = new Parser(new Tokenizer(expression).tokenize());
        // Evaluate the parsed AST using the shared CONTEXT
        MathObject object = EVALUATOR.evaluate(PARSER.parseExpression(), CONTEXT);
        if (isZero(object)) {
            return new MathObject(0.0);
        }
        return object;
    }

    public static ExpressionNode parse(String expression) {
        PARSER = new Parser(new Tokenizer(expression).tokenize());
        return PARSER.parseExpression();
    }

    public static MathObject evaluate(ExpressionNode node) {
        return EVALUATOR.evaluate(node, CONTEXT);
    }

    public static MathObject findDerivative(String expression, String withRespectTo) {
        ExpressionNode body = parse(expression);
        return findDerivative(body, withRespectTo);
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
    public static FunctionDefinitionNode createFunction(String expression) {
        return CONTEXT.addFunction(expression);
    }

    public static int getCurrentPrecision() {
        return currentPrecision;
    }

    public static void setCurrentPrecision(int decimalPlaces) {
        decimalFormat = new DecimalFormat("#." + "#".repeat(decimalPlaces));
    }

    /**
     * Toggle whether trigonometric functions and other angle-based operations should
     * use degrees instead of radians.
     *
     * <p>
     * Note: Native trig implementations must read this flag to change behavior; current
     * built-in functions in {@link evaluator.EvalContext} use {@link Math} which expects radians.
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
    public static Matrix makeTriangular(Matrix matrix) {
        return MatrixMath.makeTriangular(matrix);
    }

    public static Matrix inverseMatrix(Matrix matrix) {
        return MatrixMath.findInverse(matrix);
    }

    public static MathObject matrixDeterminant(Matrix matrix) {
        return MatrixMath.findDeterminant(matrix);
    }

    public static MathObject integrate(ExpressionNode root, String wrt) {
        MathObject integrated = new MathObject("");
        if (root instanceof BinaryNode binaryNode) {
            ExpressionNode left = binaryNode.getLeftChild();
            ExpressionNode right = binaryNode.getRightChild();
            integrated.operation(integrateExpression(left, right, binaryNode.getOperator(), wrt), "+");
        }

        return integrated;
    }

    private static MathObject integrateExpression(ExpressionNode left, ExpressionNode right, Operator operator, String wrt) {
        MathObject integrated = new MathObject("");
        if (left instanceof BinaryNode) {
            integrated.operation(integrate(left, wrt), "+");
        }
        if (right instanceof BinaryNode) {
            integrated.operation(integrate(right, wrt), "+");
        }

        return switch (operator) {
            case EXP -> integrateExp(left, right, wrt);
            case MULT ->  integrateMult(integrated, left, right, wrt);
            case null, default -> integrated;
        };
    }

    private static MathObject integrateExp(ExpressionNode left, ExpressionNode right, String wrt) {
        MathObject variable = evaluate(left);
        MathObject exponent = evaluate(right);

        if (exponent.getValue() != null) {
            exponent.setValue(exponent.getValue().stripTrailingZeros().add(ONE));
        } else if (exponent.getName() != null) {
            exponent.setName(exponent.getName() + "+1");
            exponent.addParenthesis();
        }
        exponent.setName(exponent.toString());

        MathObject integrated = new MathObject("");
        integrated.combine(variable);
        integrated.operation(exponent, "^");
        integrated.addParenthesis();
        integrated.operation(exponent, "/");

        return integrated;
    }

    private static MathObject integrateMult(MathObject accumulated, ExpressionNode left, ExpressionNode right, String wrt) {
        MathObject leftObject = integrate(left, wrt);
        MathObject rightObject = integrate(right, wrt);

        accumulated = MathObject.combine(leftObject, rightObject, "*");
        accumulated.addParenthesis();
        return accumulated;
    }

    private static MathObject findDerivative(ExpressionNode root, String wrt) {
        parseThroughTree(root);
        MathObject derivative = new MathObject("");
        if (root instanceof BinaryNode binaryNode) {
            ExpressionNode left = binaryNode.getLeftChild();
            ExpressionNode right = binaryNode.getRightChild();
            derivative.operation(differentiateExpression(left, right, binaryNode.getOperator(), wrt), "+");
        } else if (root instanceof LiteralNode literalNode) {
            if (literalNode.getParent() != null) {
                if (literalNode.getParent() instanceof BinaryNode bin) {
                    if (bin.getOperator().equals(Operator.PLUS) || bin.getOperator().equals(Operator.MINUS)) {
                        return new MathObject(0);
                    } else {
                        return new MathObject(((BigDecimal) literalNode.getValue()).stripTrailingZeros());
                    }
                }
            }
            return new MathObject(0);
        } else if (root instanceof VariableNode variableNode) {
            if (!variableNode.getName().equals(wrt)) {
                return new MathObject(0);
            } if (variableNode.getParent() != null) {
                if (variableNode.getParent() instanceof BinaryNode binaryNode) {
                    if (binaryNode.getOperator().equals(Operator.PLUS) || binaryNode.getOperator().equals(Operator.MINUS)) {
                        return new MathObject("1");
                    }
                } else if (variableNode.getParent() instanceof UnaryNode unaryNode) {
                    if (unaryNode.getSymbol().equals(UnaryNode.UnarySymbol.NEGATIVE)) {
                        return new MathObject("-1");
                    }
                    return new MathObject("1");
                }
            }
            return new MathObject(variableNode.getName());
        } else if (root instanceof UnaryNode unaryNode) {
            if (unaryNode.getSymbol().equals(UnaryNode.UnarySymbol.NEGATIVE)) {
                return new MathObject("-" + findDerivative(unaryNode.getChild(), wrt));
            } else {
                return findDerivative(unaryNode.getChild(), wrt);
            }
        }
        return derivative;
    }

    private static MathObject differentiateExpression(ExpressionNode left, ExpressionNode right, Operator operator, String wrt) {
        MathObject differentiated = new MathObject("");
        if (left instanceof BinaryNode) {
            differentiated.operation(findDerivative(left, wrt), "+");
        }
        if (right instanceof BinaryNode) {
            differentiated.operation(findDerivative(right, wrt), "+");
        }

        return switch (operator) {
            case EXP -> differentiateExp(left, right, wrt);
            case MULT -> differentiateMult(differentiated, left);
            case MINUS, PLUS -> differentiateAddSub(left, right, operator, wrt);
            case DIV -> differentiateDiv(left, right, wrt);
            default -> differentiated;
        };
    }

    private static MathObject differentiateExp(ExpressionNode left, ExpressionNode right, String wrt) {
        MathObject variable = evaluate(left);
        MathObject exp = evaluate(right);

        if (!variable.toString().equals(wrt)) {
            return new MathObject(0);
        }

        if (exp.getValue() != null) {
            exp.setValue(exp.getValue().stripTrailingZeros());
        }
        exp.setName(exp.toString());
        exp.operation(variable, "");
        MathObject differentiated = new MathObject("");
        differentiated.combine(exp);

        MathObject expReduced = new MathObject(exp.getValue());
        expReduced.operation(new MathObject(ONE), "-");
        if (expReduced.getValue() != null && expReduced.getValue().doubleValue() > 1) {
            expReduced.setValue(expReduced.getValue().stripTrailingZeros());
            differentiated.operation(expReduced, "^");
        }
        return differentiated;
    }

    private static MathObject differentiateMult(MathObject accumulated, ExpressionNode left) {
        MathObject leftObject = evaluate(left);
        if (!accumulated.getName().isEmpty()) {
            accumulated.addParenthesis();
        }
        if (leftObject.getValue() != null) {
            leftObject.setValue(leftObject.getValue().stripTrailingZeros());
        }
        return MathObject.combine(leftObject, accumulated);
    }

    private static MathObject differentiateAddSub(ExpressionNode left, ExpressionNode right, Operator operator, String wrt) {
        MathObject leftObject = findDerivative(left, wrt);
        MathObject rightObject = findDerivative(right, wrt);

        if (rightObject.toString().isEmpty()) {
            rightObject = new MathObject(0);
        } else if (leftObject.toString().isEmpty()) {
            leftObject = new MathObject(0);
        }

        MathObject combined = new MathObject("");
        if (leftObject.isCharacter()) {
            if (rightObject.isCharacter()) {
                leftObject.combine(rightObject, Operator.getFromOperator(operator));
                combined.combine(leftObject);
            } else {
                combined.combine(leftObject);
            }
        } else if (rightObject.isCharacter()) {
            if (!leftObject.isCharacter()) {
                if (rightObject.toString().equals(wrt)) {
                    combined.combine(new MathObject(1), (operator.equals(Operator.MINUS) ? "-" : ""));
                } else {
                    combined.combine(rightObject, (operator.equals(Operator.MINUS) ? "-" : ""));
                }
            }
        }
        return combined;
    }

    private static MathObject differentiateDiv(ExpressionNode left, ExpressionNode right, String wrt) {
        MathObject leftObject = evaluate(left);
        MathObject rightObject = evaluate(right);
        leftObject.addParenthesis();
        rightObject.addParenthesis();

        MathObject fx = findDerivative(left, wrt);
        fx.addParenthesis();
        MathObject gx = findDerivative(right, wrt);
        gx.addParenthesis();

        MathObject topLeft = MathObject.combine(fx, rightObject, " * ");
        MathObject topRight = MathObject.combine(leftObject, gx, " * ");

        MathObject bottom = MathObject.combine(rightObject, new MathObject("^2"));
        MathObject top = MathObject.combine(topLeft, topRight, " - ");
        top.addParenthesis();
        bottom.addParenthesis();
        return MathObject.combine(top, bottom, " / ");
    }


    public static List<MathObject> findRoots(String expression, String... variables) {
        ExpressionNode body = createFunction(createFunctionFromPolynomial(expression, variables));
        List<MathObject> objects = new ArrayList<>();
        int rootsFound = 0;
        int degree = findPolynomialDegree(body);
        BigDecimal lastValue;
        while (rootsFound < degree) {

            rootsFound++;
        }

        return objects;
    }

    public static String parseThroughTree(ExpressionNode root) {
        if (root instanceof BinaryNode binaryNode) {
            binaryNode.getLeftChild().setParent(binaryNode);
            binaryNode.getRightChild().setParent(binaryNode);
            return parseThroughTree(binaryNode.getLeftChild()) + Operator.getFromOperator(binaryNode.getOperator()) + parseThroughTree(binaryNode.getRightChild());
        } else if (root instanceof LiteralNode literalNode) {
            return literalNode.getValue().toString();
        } else if (root instanceof UnaryNode unaryNode) {
            unaryNode.getChild().setParent(unaryNode);
            return parseThroughTree(unaryNode.getChild());
        } else if (root instanceof VariableNode variableNode) {
            return variableNode.getValue().toString();
        } else {
            return "";
        }
    }

    public static String createFunctionFromPolynomial(String expression, String... variables) {
        StringBuilder func = new StringBuilder();
        String identifier = getSaltString();
        while (CONTEXT.containsFunction(identifier)) {
            identifier = getSaltString();
        }
        func.append(identifier).append("(");
        int idx = 1;
        for (String s : variables) {
            func.append(s);
            if (idx < variables.length) {
                func.append(",");
            }
        }
        func.append(") = ").append(expression);
        return func.toString();
    }

    private static int findPolynomialDegree(ExpressionNode body, int... currentHighestDegree) {
        if (currentHighestDegree.length == 0) {
            currentHighestDegree = new int[]{1};
        }
        int highestDegree = currentHighestDegree[0];
        if (body instanceof BinaryNode binaryNode && binaryNode.getOperator().equals(Operator.EXP)) {
            if (!(binaryNode.getLeftChild() instanceof LiteralNode) && binaryNode.getRightChild() instanceof LiteralNode node) {
                highestDegree = Math.max(highestDegree, ((BigDecimal) node.getValue()).intValue());
            }
            highestDegree = Math.max(findPolynomialDegree(binaryNode.getLeftChild()), Math.max(findPolynomialDegree(binaryNode.getRightChild()), highestDegree));
        } else if (body instanceof BinaryNode binaryNode) {
            highestDegree = Math.max(findPolynomialDegree(binaryNode.getLeftChild()), Math.max(findPolynomialDegree(binaryNode.getRightChild()), highestDegree));
        }
        return highestDegree;
    }

    /**
     * Determine whether a value should be considered zero (with epsilon tolerance).
     *
     * @param val numeric value to test
     * @return true if value is (approximately) zero
     */
    public static boolean isZero(MathObject val) {
        if (val.getValue() != null) {
            return Math.abs(val.getValue().doubleValue()) <= 0.0000001;
        } else {
            return val.getName() != null && val.getName().isEmpty();
        }
    }

    private static String getSaltString() {
        String SALTCHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder salt = new StringBuilder();
        Random rnd = new Random();
        while (salt.length() < 3) { // length of the random string.
            int index = (int) (rnd.nextFloat() * SALTCHARS.length());
            salt.append(SALTCHARS.charAt(index));
        }
        String saltStr = salt.toString();
        return saltStr;
    }
}
