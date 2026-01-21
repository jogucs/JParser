package evaluator;

import literals.FunctionDefinition;
import literals.Matrix;
import literals.MathObject;
import nodes.*;
import parser.Parser;
import tokenizer.Operator;
import tokenizer.Token;
import tokenizer.Tokenizer;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Utility entry-point for parsing and evaluating mathematical expressions.
 *
 * <p>Provides a thin facade over tokenizer, parser and evaluator components.
 * Exposes static helpers to evaluate expressions, create user-defined functions,
 * perform calculus operations (differentiate/integrate) and matrix operations.
 * </p>
 *
 * <p>State is held in the shared {@link #CONTEXT} and {@link #EVALUATOR}. Callers
 * should treat the class as a procedural API.</p>
 */
public abstract class JParser {
    /* Public shared context and evaluator */
    public static Context CONTEXT = new Context();
    public static Evaluator EVALUATOR = new Evaluator();

    /**
     * Parser instance used for the most recent parse operation.
     * May be {@code null} between calls.
     */
    public static Parser PARSER;

    /* Formatting / precision defaults */
    private static DecimalFormat decimalFormat = new DecimalFormat("#.###########") {{
        setRoundingMode(RoundingMode.CEILING);
    }};
    private static int currentPrecision = 10;

    public static final BigDecimal NEGATIVE_ONE = new BigDecimal("-1");

    /**
     * Whether angle inputs/outputs should be interpreted in degrees.
     */
    private static boolean degrees;

    // ---------------------------------------------------------------------
    // Calculation and public API (grouped)
    // ---------------------------------------------------------------------

    /**
     * Parse and evaluate the given expression string using the shared {@link Context}.
     *
     * <p>If the expression is empty or only whitespace, returns a {@link MathObject} wrapping 0.</p>
     *
     * @param expression the expression string to evaluate
     * @return evaluated {@link MathObject}
     */
    public static MathObject evaluate(String expression) {
        if (expression.trim().isEmpty()) {
            return new MathObject(new BigDecimal(0));
        }
        ExpressionNode parsed = parse(expression);
        ExpressionNode factored = Simplifier.factor(parsed);
        MathObject object = EVALUATOR.evaluate(factored, CONTEXT);
        if (isZero(object)) {
            return new MathObject(0);
        }
        if (object.getValue() != null) {
            object.setName(normalize(object.getValue()));
        }
        object.removeTrailingParenthesis();
        return object;
    }



    /**
     * Tokenize and parse the given expression string to an {@link ExpressionNode}.
     *
     * @param expression expression to parse
     * @return root {@link ExpressionNode}
     */
    public static ExpressionNode parse(String expression) {
        Tokenizer tokenizer = new Tokenizer(expression);
        List<Token> tokens = tokenizer.tokenize();
        PARSER = new Parser(tokens);
        return PARSER.parseExpression();
    }

    /**
     * Parse a {@link MathObject} by converting to string then parsing.
     *
     * @param object object to parse
     * @return parsed {@link ExpressionNode}
     */
    public static ExpressionNode parse(MathObject object) {
        return parse(object.toString());
    }

    /**
     * Evaluate an already-parsed {@link ExpressionNode} using the shared context.
     *
     * @param node expression node to evaluate
     * @return evaluated {@link MathObject}
     */
    public static MathObject evaluate(ExpressionNode node) {
        return EVALUATOR.evaluate(node, CONTEXT);
    }

    /**
     * Differentiate the given expression string with respect to the provided variable.
     *
     * @param expression expression to differentiate
     * @param withRespectTo variable name
     * @return derivative as a {@link MathObject}
     */
    public static MathObject differentiate(String expression, String withRespectTo) {
        ExpressionNode body = parse(expression);
        return findDerivative(body, withRespectTo);
    }

    /**
     * Integrate the provided {@link ExpressionNode} with respect to {@code wrt}.
     *
     * @param root expression root to integrate
     * @param wrt variable name
     * @return integral as a {@link MathObject}
     */
    public static MathObject integrate(ExpressionNode root, String wrt) {
        MathObject integrated = new MathObject("");
        if (root instanceof BinaryNode binaryNode) {
            ExpressionNode left = binaryNode.getLeftChild();
            ExpressionNode right = binaryNode.getRightChild();
            integrated.operation(integrateExpression(left, right, binaryNode.getOperator(), wrt), "+");
        }
        return integrated;
    }

    /**
     * Create a user-defined function from a function definition expression (e.g. "f(x)=x^2").
     *
     * @param expression function definition
     * @return parsed function {@link ExpressionNode} (as stored in the context)
     */
    public static ExpressionNode createFunction(String expression) {
        return CONTEXT.addFunction(expression);
    }

    /**
     * Create an internal {@link FunctionDefinition} from a polynomial expression and variable list.
     * The generated identifier will be unique within the current context.
     *
     * @param expression polynomial expression
     * @param variables variables used in the polynomial
     * @return new {@link FunctionDefinition}
     */
    public static FunctionDefinition createFunctionFromPolynomial(String expression, String... variables) {
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
            idx++;
        }
        func.append(") = ").append(expression);
        return new FunctionDefinition(identifier, Arrays.stream(variables).toList(), JParser.parse(expression));
    }

    /**
     * Attempt to find roots for a polynomial expression. (Work in progress in original.)
     * Utilizes Newton-Raphson method of finding roots.
     *
     * @param expression polynomial expression
     * @param variables variable names
     * @return list of roots as {@link MathObject}
     */
    public static List<BigDecimal> findRoots(String expression, String... variables) {
        ExpressionNode body = JParser.parse(expression);
        FunctionDefinition definition = createFunctionFromPolynomial(expression, variables);
        String id = definition.getName();
        String expr = definition.getExpression();
        MathObject derivative = findDerivative(body, variables[0]);
        FunctionDefinition derivativeFunction = createFunctionFromPolynomial(derivative.toString(), variables);
        List<BigDecimal> objects = new ArrayList<>();
        int rootsFound = 0;
        int degree = findPolynomialDegree(body);
        BigDecimal firstGuess = new BigDecimal(1);
        BigDecimal secondGuess = new BigDecimal(-1);
        MathObject firstValue = evaluate(id + "(" + firstGuess + ")");
        MathObject secondValue = evaluate(id + "(" + secondGuess + ")");
        BigDecimal[] brackets;
        boolean signChange = firstValue.getSign() != secondValue.getSign();
        while (!signChange && firstGuess.doubleValue() < Integer.MAX_VALUE && secondGuess.doubleValue() < Integer.MAX_VALUE) {
            firstGuess =  firstGuess.multiply(BigDecimal.valueOf(2));
            secondGuess = secondGuess.multiply(BigDecimal.valueOf(2));
            firstValue = evaluate(id + "(" + firstGuess + ")");
            secondValue = evaluate(id + "(" + secondGuess + ")");
            if (firstValue.getSign() != secondValue.getSign()) {
                signChange = true;
            }
        }
        brackets = new BigDecimal[]{firstGuess, secondGuess};
        BigDecimal root;
        while (rootsFound < degree) {
            objects.add(findRootsHelper(definition, derivativeFunction, brackets));
            rootsFound++;
        }
        return objects;
    }

    private static BigDecimal findRootsHelper(FunctionDefinition expression, FunctionDefinition derivative, BigDecimal[] range) {
        if (range.length != 2) {
            throw new RuntimeException("Range should be in 2d coordinates");
        }

        return null;
    }

    /**
     * Walk the expression tree and produce a textual {@link MathObject} representation.
     *
     * @param root expression root
     * @return textual {@link MathObject}
     */
    public static MathObject parseThroughTree(ExpressionNode root) {
        if (root instanceof BinaryNode binaryNode) {
            binaryNode.getLeftChild().setParent(binaryNode);
            binaryNode.getRightChild().setParent(binaryNode);
            MathObject object = new MathObject(parseThroughTree(binaryNode.getLeftChild()) + Operator.getFromOperator(binaryNode.getOperator()) + parseThroughTree(binaryNode.getRightChild()));
            object.forceParenthesis();
            return object;
        } else if (root instanceof LiteralNode literalNode) {
            return new MathObject(literalNode.getValue().toString());
        } else if (root instanceof UnaryNode unaryNode) {
            unaryNode.getChild().setParent(unaryNode);
            MathObject object = parseThroughTree(unaryNode.getChild());
            if (unaryNode.getSymbol().equals(UnaryNode.UnarySymbol.NEGATIVE)) {
                object = new MathObject("-" + object);
                object.forceParenthesis();
            }
            return object;
        } else if (root instanceof VariableNode variableNode) {
            MathObject object = new MathObject(variableNode.getName());
            return object;
        } else if (root instanceof FunctionCallNode functionCallNode) {
            return parseThroughTree(CONTEXT.lookupFunction(functionCallNode.getName()).getBody());
        } else if (root instanceof FunctionDefinitionNode functionDefinitionNode) {
            return parseThroughTree(functionDefinitionNode.getBody());
        }
        else {
            return new MathObject("");

        }
    }

    // ---------------------------------------------------------------------
    // Matrix helpers (simple delegations)
    // ---------------------------------------------------------------------

    /**
     * Perform row reduction (Gaussian elimination) on the provided matrix.
     *
     * @param matrix matrix to reduce
     * @return reduced {@link Matrix}
     */
    public static Matrix rowReduce(Matrix matrix) {
        return MatrixMath.rowReduce(matrix);
    }

    /**
     * Convert the provided matrix to echelon/triangular form.
     *
     * @param matrix matrix to convert
     * @return triangular {@link Matrix}
     */
    public static Matrix makeTriangular(Matrix matrix) {
        return MatrixMath.makeTriangular(matrix);
    }

    /**
     * Find the inverse of the provided matrix.
     *
     * @param matrix matrix to invert
     * @return inverse {@link Matrix}
     */
    public static Matrix inverseMatrix(Matrix matrix) {
        return MatrixMath.findInverse(matrix);
    }

    /**
     * Compute the determinant of {@code matrix}.
     *
     * @param matrix matrix
     * @return determinant as a {@link MathObject}
     */
    public static MathObject matrixDeterminant(Matrix matrix) {
        return MatrixMath.findDeterminant(matrix);
    }

    // ---------------------------------------------------------------------
    // Configuration / small helpers
    // ---------------------------------------------------------------------

    /**
     * Get the configured numeric precision (number of decimal places).
     *
     * @return precision
     */
    public static int getCurrentPrecision() {
        return currentPrecision;
    }

    /**
     * Set the decimal format used when printing results.
     *
     * @param decimalPlaces number of decimal places
     */
    public static void setCurrentPrecision(int decimalPlaces) {
        decimalFormat = new DecimalFormat("#." + "#".repeat(decimalPlaces));
        currentPrecision = decimalPlaces;
    }

    /**
     * Toggle usage of degrees for angle-based functions.
     *
     * @param degrees true for degrees, false for radians
     */
    public static void changeDegrees(boolean degrees) {
        JParser.degrees = degrees;
    }

    /**
     * Determine whether a {@link MathObject} should be considered zero (with epsilon).
     *
     * @param val numeric value to test
     * @return true if value is approximately zero
     */
    public static boolean isZero(MathObject val) {
        if (val.getValue() != null) {
            return Math.abs(val.getValue().doubleValue()) <= 0.0000001;
        } else {
            return val.getName() != null && val.getName().isEmpty();
        }
    }

    public static boolean isZero(BigDecimal decimal) {
        return isZero(new MathObject(decimal));
    }

    /**
     * Normalize a {@link BigDecimal} to a string using configured precision.
     *
     * @param bd BigDecimal to normalize
     * @return normalized string
     */
    public static String normalize(BigDecimal bd) {
        return bd.round(new MathContext(getCurrentPrecision(), RoundingMode.HALF_UP)).stripTrailingZeros().toPlainString();
    }

    /**
     * Normalize a {@link MathObject} by parsing and setting its numeric {@code value} and {@code name}.
     *
     * @param object object to normalize
     */
    public static void normalize(MathObject object) {
        if (object.getValue() != null || (object.getName() != null && isNumeric(object.getName()))) {
            object.setValue(BigDecimal.valueOf(Double.parseDouble(object.toString())));
            object.setName(normalize(object.getValue()));
        }
    }

    /**
     * Check whether a string is a numeric representation.
     *
     * @param string input string
     * @return true when fully numeric
     */
    public static boolean isNumeric(String string) {
        ParsePosition pos = new ParsePosition(0);
        NumberFormat.getInstance().parse(string, pos);
        return string.length() == pos.getIndex();
    }

    // ---------------------------------------------------------------------
    // Private / utility methods (implementation details)
    // ---------------------------------------------------------------------

    /**
     * Integrate a binary expression by operator.
     *
     * @param left left child
     * @param right right child
     * @param operator operator between them
     * @param wrt variable to integrate with respect to
     * @return integrated {@link MathObject}
     */
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
            case MULT -> integrateMult(integrated, left, right, wrt);
            case null, default -> integrated;
        };
    }

    /**
     * Integrate an exponent expression (x^n -> x^(n+1)/(n+1)).
     */
    private static MathObject integrateExp(ExpressionNode left, ExpressionNode right, String wrt) {
        MathObject variable = evaluate(left);
        MathObject exponent = evaluate(right);

        if (exponent.getValue() != null) {
            exponent.setValue(exponent.getValue().add(new BigDecimal(1)));
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

    /**
     * Integrate multiplication by combining integrals (simple product rule placeholder).
     */
    private static MathObject integrateMult(MathObject accumulated, ExpressionNode left, ExpressionNode right, String wrt) {
        MathObject leftObject = integrate(left, wrt);
        MathObject rightObject = integrate(right, wrt);

        accumulated = MathObject.combine(leftObject, rightObject, "*");
        accumulated.addParenthesis();
        return accumulated;
    }


    /**
     * Compute derivative for arbitrary expression node trees.
     *
     * @param root expression root
     * @param wrt variable
     * @return derivative as {@link MathObject}
     */
    private static MathObject findDerivative(ExpressionNode root, String wrt) {
        MathObject derivative = new MathObject("");

        if (root instanceof BinaryNode binaryNode) {
            ExpressionNode left = binaryNode.getLeftChild();
            ExpressionNode right = binaryNode.getRightChild();
            derivative.combine(differentiateExpression(left, right, binaryNode.getOperator(), wrt));
        } else if (root instanceof LiteralNode literalNode) {
            if (literalNode.getParent() != null) {
                if (literalNode.getParent() instanceof BinaryNode bin) {
                    if (bin.getOperator().equals(Operator.PLUS) || bin.getOperator().equals(Operator.MINUS)) {
                        return new MathObject(0);
                    } else if (bin.getOperator().equals(Operator.MULT)){
                        return new MathObject(((BigDecimal) literalNode.getValue()));
                    }
                }
            }
            return new MathObject(0);
        } else if (root instanceof VariableNode variableNode) {
            if (!variableNode.getName().equals(wrt)) {
                return new MathObject(0);
            } else if (variableNode.getParent() != null) {
                if (variableNode.getParent() instanceof BinaryNode binaryNode) {
                    if (binaryNode.getOperator().equals(Operator.PLUS) || binaryNode.getOperator().equals(Operator.MINUS)) {
                        return new MathObject("1");
                    }
                } else if (variableNode.getParent() instanceof UnaryNode unaryNode) {
                    if (unaryNode.getSymbol().equals(UnaryNode.UnarySymbol.NEGATIVE)) {
                        return new MathObject("1");
                    }
                    return new MathObject("1");
                }
            } else if (variableNode.getName().equals(wrt)) {
                return new MathObject("1");
            }
            return new MathObject(variableNode.getName());
        } else if (root instanceof UnaryNode unaryNode) {
            if (unaryNode.getSymbol().equals(UnaryNode.UnarySymbol.NEGATIVE)) {
                return new MathObject("-" + findDerivative(unaryNode.getChild(), wrt));
            } else {
                return findDerivative(unaryNode.getChild(), wrt);
            }
        } else if (root instanceof FunctionCallNode functionCallNode) {
            FunctionDefinition def = CONTEXT.lookupFunction(functionCallNode.getName());
            MathObject object = evaluate(def.getBody());
            return findDerivative(JParser.parse(object), wrt);
        }
        return derivative;
    }

    /**
     * Differentiate a binary expression by operator.
     */
    private static MathObject differentiateExpression(ExpressionNode left, ExpressionNode right, Operator operator, String wrt) {
        MathObject differentiated = new MathObject("");
        if (left instanceof BinaryNode) {
            differentiated.combine(findDerivative(left, wrt));
        }
        if (right instanceof BinaryNode) {
            differentiated.combine(findDerivative(right, wrt));
        }

        return switch (operator) {
            case EXP -> differentiateExp(left, right, wrt);
            case MULT -> differentiateMult(differentiated, left, right, wrt);
            case MINUS, PLUS -> differentiateAddSub(left, right, operator, wrt);
            case DIV -> differentiateDiv(left, right, wrt);
            default -> differentiated;
        };
    }

    /**
     * Differentiate exponent expressions (power rule simple form).
     */
    private static MathObject differentiateExp(ExpressionNode left, ExpressionNode right, String wrt) {
        MathObject variable = evaluate(left);
        MathObject exp = evaluate(right);
        MathObject expReduced;

        if (!variable.toString().contains(wrt)) {
            return new MathObject(0);
        }
        if (exp.getValue() != null) {
            expReduced = new MathObject(exp.getValue().subtract(BigDecimal.ONE));
        } else {
            expReduced = MathObject.combine(exp, new MathObject(BigDecimal.ONE), "-");
        }
        expReduced = evaluate(expReduced.toString());
        if (expReduced.toString().length() > 1) {
            expReduced.forceParenthesis();
        }
        if (!expReduced.toString().equals("1")) {
            variable.combine(expReduced, "^");
        }
        exp = evaluate(exp.toString());
        MathObject differentiated = new MathObject("");
        differentiated.combine(exp);
        differentiated.forceParenthesis();
        differentiated.combine(variable, "*");
        return differentiated;
    }

    /**
     * Differentiate multiplication (product rule placeholder/combiner).
     */
    private static MathObject differentiateMult(MathObject accumulated, ExpressionNode left, ExpressionNode right, String wrt) {
        MathObject leftObject = findDerivative(left, wrt);
        MathObject rightObject = findDerivative(right, wrt);
        normalize(leftObject);
        normalize(rightObject);
        if (leftObject.toString().equals(wrt) || rightObject.toString().equals(wrt)) {
            if (leftObject.toString().equals(wrt)) {
                return rightObject.combine(accumulated);
            }
            if (rightObject.toString().equals(wrt)) {
                return leftObject.combine(accumulated);
            }
        } else if (!leftObject.toString().contains(wrt) && !rightObject.toString().contains(wrt)){
            MathObject zero = new MathObject("0");
            return zero.combine(accumulated);
        }
        if (accumulated.toString().isEmpty()) {
            if (!leftObject.toString().equals("1") && !rightObject.toString().equals("1")) {
                leftObject.combine(rightObject, "*");
            }
        }
        return leftObject.combine(accumulated);
    }

    /**
     * Differentiate addition/subtraction.
     */
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

    /**
     * Differentiate division using the quotient rule.
     */
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
        return MathObject.combine(top, bottom, "/");
    }

    /**
     * Determine polynomial degree by walking expression tree.
     *
     * @param body expression root
     * @param currentHighestDegree optional current highest degree
     * @return highest degree found
     */
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
     * Generate a short random alphabetic identifier.
     *
     * @return 3-character random uppercase string
     */
    private static String getSaltString() {
        String SALTCHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder salt = new StringBuilder();
        Random rnd = new Random();
        while (salt.length() < 3) {
            int index = (int) (rnd.nextFloat() * SALTCHARS.length());
            salt.append(SALTCHARS.charAt(index));
        }
        return salt.toString();
    }

    public static boolean combineSymbols(ExpressionNode node) {
        if (node instanceof BinaryNode binaryNode &&
                (binaryNode.getOperator().equals(Operator.MINUS) || binaryNode.getOperator().equals(Operator.PLUS))) {
            if (binaryNode.getOperator().equals(Operator.MINUS)) {
                if (binaryNode.getLeftChild() instanceof BinaryNode && binaryNode.getRightChild() instanceof BinaryNode) {
                    combineSymbols(binaryNode.getLeftChild());
                    combineSymbols(binaryNode.getRightChild());
                } else if (binaryNode.getLeftChild() instanceof UnaryNode leftNode && leftNode.getSymbol().equals(UnaryNode.UnarySymbol.NEGATIVE)) {
                    binaryNode.setOperator(Operator.PLUS);
                    binaryNode.setLeftChild(leftNode.getChild());
                } else if (binaryNode.getRightChild() instanceof UnaryNode rightNode && rightNode.getSymbol().equals(UnaryNode.UnarySymbol.NEGATIVE)) {
                    binaryNode.setOperator(Operator.PLUS);
                    binaryNode.setRightChild(rightNode.getChild());
                }
            } else if (binaryNode.getOperator().equals(Operator.PLUS)) {
                if (binaryNode.getLeftChild() instanceof BinaryNode && binaryNode.getRightChild() instanceof BinaryNode) {
                    combineSymbols(binaryNode.getLeftChild());
                    combineSymbols(binaryNode.getRightChild());
                } else if (binaryNode.getLeftChild() instanceof UnaryNode leftnode && leftnode.getSymbol().equals(UnaryNode.UnarySymbol.NEGATIVE)) {
                    binaryNode.setOperator(Operator.MINUS);
                    binaryNode.setLeftChild(leftnode.getChild());
                } else if (binaryNode.getRightChild() instanceof UnaryNode rightNode && rightNode.getSymbol().equals(UnaryNode.UnarySymbol.NEGATIVE)) {
                    binaryNode.setOperator(Operator.MINUS);
                    binaryNode.setRightChild(rightNode.getChild());
                }
            }
            return true;
        }
        return false;
    }

}