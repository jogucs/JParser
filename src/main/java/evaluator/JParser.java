package evaluator;

import literals.FunctionDefinition;
import literals.Matrix;
import literals.NativeFunction;
import literals.Term;
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
    public static boolean degrees = false;

    // ---------------------------------------------------------------------
    // Calculation and public API (grouped)
    // ---------------------------------------------------------------------

    /**
     * Parse and evaluate the given expression string using the shared {@link Context}.
     *
     * <p>If the expression is empty or only whitespace, returns a {@link Term} wrapping 0.</p>
     *
     * @param expression the expression string to evaluate
     * @return evaluated {@link Term}
     */
    public static Term evaluate(String expression) {
        if (expression.trim().isEmpty()) {
            return new Term(new BigDecimal(0));
        }
        ExpressionNode parsed = parse(expression);
        Term object = EVALUATOR.evaluate(parsed, CONTEXT);
        if (isZero(object)) {
            return new Term(0);
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
     * Parse a {@link Term} by converting to string then parsing.
     *
     * @param object object to parse
     * @return parsed {@link ExpressionNode}
     */
    public static ExpressionNode parse(Term object) {
        return parse(object.toString());
    }

    public static Term factor(String expression) {
        ExpressionNode node = Simplifier.factor(parse(expression));
        return evaluate(node);
    }

    /**
     * Evaluate an already-parsed {@link ExpressionNode} using the shared context.
     *
     * @param node expression node to evaluate
     * @return evaluated {@link Term}
     */
    public static Term evaluate(ExpressionNode node, boolean... remove) {
        return EVALUATOR.evaluate(node, CONTEXT);
    }

    /**
     * Differentiate the given expression string with respect to the provided variable.
     *
     * @param expression expression to differentiate
     * @param withRespectTo variable name
     * @return derivative as a {@link Term}
     */
    public static Term differentiate(String expression, String withRespectTo) {
        ExpressionNode body = parse(expression);
        return findDerivative(body, withRespectTo);
    }

    /**
     * Integrate the provided {@link ExpressionNode} with respect to {@code wrt}.
     *
     * @param root expression root to integrate
     * @param wrt variable name
     * @return integral as a {@link Term}
     */
    public static Term integrate(ExpressionNode root, String wrt) {
        Term integrated = new Term("");
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
     * @return list of roots as {@link Term}
     */
    public static List<Float> findRoots(String expression, String... variables) {
        List<Float> roots = new ArrayList<>();
        ExpressionNode parsed = parse(expression);

        Term derivative = findDerivative(parsed, variables[0]);
        Term evaluated = evaluate(expression);

        String functionId = createFunctionFromPolynomial(evaluated.toString(), variables).getName();
        String derivativeFunctionId = createFunctionFromPolynomial(derivative.toString(), variables).getName();

        float x = 1;

        Term guessFunctionValue;
        Term guessDerivativeValue;

        Term funcDiv;
        int guesss = 0;

        boolean checkingPlusX = true;
        boolean checkingNegX = true;

        while (checkingPlusX) {
            guessFunctionValue = evaluate(functionId + "(" + x + ")");
            guessDerivativeValue = evaluate(derivativeFunctionId + "(" + x + ")");
            funcDiv = evaluate(guessFunctionValue + "/" + guessDerivativeValue);
            x = evaluate(x + "-" + funcDiv).getValue().floatValue();
            if (guessFunctionValue.getValue().abs().compareTo(BigDecimal.valueOf(0.000001)) < 0 || guesss > 100) {
                roots.add(Float.valueOf(normalize(BigDecimal.valueOf(x))));
                checkingPlusX = false;
            }
            guesss++;
        }
        guesss = 0;
        while (checkingNegX) {
            guessFunctionValue = evaluate(functionId + "(" + x + ")");
            guessDerivativeValue = evaluate(derivativeFunctionId + "(" + x + ")");
            funcDiv = evaluate(guessFunctionValue + "/" + guessDerivativeValue);
            x = evaluate(x + "-" + funcDiv).getValue().floatValue() * -1;
            if (guessFunctionValue.getValue().abs().compareTo(BigDecimal.valueOf(0.000001)) < 0 || guesss > 100) {
                roots.add(Float.valueOf(normalize(BigDecimal.valueOf(x))));
                checkingNegX = false;
            }
            guesss++;
        }

        return roots;
    }



    /**
     * Walk the expression tree and produce a textual {@link Term} representation.
     *
     * @param root expression root
     * @return textual {@link Term}
     */
    public static Term parseThroughTree(ExpressionNode root) {
        if (root instanceof BinaryNode binaryNode) {
            binaryNode.getLeftChild().setParent(binaryNode);
            binaryNode.getRightChild().setParent(binaryNode);
            Term object = new Term(parseThroughTree(binaryNode.getLeftChild()) + Operator.getFromOperator(binaryNode.getOperator()) + parseThroughTree(binaryNode.getRightChild()));
            object.forceParenthesis();
            return object;
        } else if (root instanceof LiteralNode literalNode) {
            return new Term(literalNode.getValue().toString());
        } else if (root instanceof UnaryNode unaryNode) {
            unaryNode.getChild().setParent(unaryNode);
            Term object = parseThroughTree(unaryNode.getChild());
            if (unaryNode.getSymbol().equals(UnaryNode.UnarySymbol.NEGATIVE)) {
                object = new Term("-" + object);
                object.forceParenthesis();
            }
            return object;
        } else if (root instanceof VariableNode variableNode) {
            Term object = new Term(variableNode.getName());
            return object;
        } else if (root instanceof FunctionCallNode functionCallNode) {
            if (!CONTEXT.containsNativeFunction(functionCallNode.getName())) {
                return parseThroughTree(CONTEXT.lookupFunction(functionCallNode.getName()).getBody());
            }
            return new Term(root.getValue().toString());
        } else if (root instanceof FunctionDefinitionNode functionDefinitionNode) {
            return parseThroughTree(functionDefinitionNode.getBody());
        } else {
            return new Term(root.getValue().toString());

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
     * @return determinant as a {@link Term}
     */
    public static Term matrixDeterminant(Matrix matrix) {
        return MatrixMath.findDeterminant(matrix);
    }

    public static Term characteristicPolynomial(Matrix matrix) {
        return MatrixMath.findCharacteristicPolynomial(matrix);
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
     */
    public static void changeDegrees() {
        JParser.degrees = !JParser.degrees;
    }

    /**
     * Determine whether a {@link Term} should be considered zero (with epsilon).
     *
     * @param val numeric value to test
     * @return true if value is approximately zero
     */
    public static boolean isZero(Term val) {
        if (val.getValue() != null) {
            return Math.abs(val.getValue().doubleValue()) <= 1e-99;
        } else {
            return val.getName() != null && val.getName().isEmpty();
        }
    }

    public static boolean isZero(BigDecimal decimal) {
        return isZero(new Term(decimal));
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
     * Normalize a {@link Term} by parsing and setting its numeric {@code value} and {@code name}.
     *
     * @param object object to normalize
     */
    public static void normalize(Term object) {
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
     * @return integrated {@link Term}
     */
    private static Term integrateExpression(ExpressionNode left, ExpressionNode right, Operator operator, String wrt) {
        Term integrated = new Term("");
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
    private static Term integrateExp(ExpressionNode left, ExpressionNode right, String wrt) {
        Term variable = evaluate(left);
        Term exponent = evaluate(right);

        if (exponent.getValue() != null) {
            exponent.setValue(exponent.getValue().add(new BigDecimal(1)));
        } else if (exponent.getName() != null) {
            exponent.setName(exponent.getName() + "+1");
            exponent.addParenthesis();
        }
        exponent.setName(exponent.toString());

        Term integrated = new Term("");
        integrated.combine(variable);
        integrated.operation(exponent, "^");
        integrated.addParenthesis();
        integrated.operation(exponent, "/");

        return integrated;
    }

    /**
     * Integrate multiplication by combining integrals (simple product rule placeholder).
     */
    private static Term integrateMult(Term accumulated, ExpressionNode left, ExpressionNode right, String wrt) {
        Term leftObject = integrate(left, wrt);
        Term rightObject = integrate(right, wrt);

        accumulated = Term.combine(leftObject, rightObject, "*");
        accumulated.addParenthesis();
        return accumulated;
    }


    /**
     * Compute derivative for arbitrary expression node trees.
     *
     * @param root expression root
     * @param wrt variable
     * @return derivative as {@link Term}
     */
    private static Term findDerivative(ExpressionNode root, String wrt) {
        Term derivative = new Term("");

        if (root instanceof BinaryNode binaryNode) {
            ExpressionNode left = binaryNode.getLeftChild();
            ExpressionNode right = binaryNode.getRightChild();
            derivative.combine(differentiateExpression(left, right, binaryNode.getOperator(), wrt));
        } else if (root instanceof LiteralNode literalNode) {
            if (literalNode.getParent() != null) {
                if (literalNode.getParent() instanceof BinaryNode bin) {
                    if (bin.getOperator().equals(Operator.PLUS) || bin.getOperator().equals(Operator.MINUS)) {
                        return new Term(0);
                    } else if (bin.getOperator().equals(Operator.MULT)){
                        return new Term(((BigDecimal) literalNode.getValue()));
                    }
                }
            }
            return new Term(0);
        } else if (root instanceof VariableNode variableNode) {
            if (!variableNode.getName().equals(wrt)) {
                return new Term(0);
            } else if (variableNode.getParent() != null) {
                if (variableNode.getParent() instanceof BinaryNode binaryNode) {
                    if (binaryNode.getOperator().equals(Operator.PLUS) || binaryNode.getOperator().equals(Operator.MINUS)) {
                        return new Term("1");
                    }
                } else if (variableNode.getParent() instanceof UnaryNode unaryNode) {
                    if (unaryNode.getSymbol().equals(UnaryNode.UnarySymbol.NEGATIVE)) {
                        return new Term("1");
                    }
                    return new Term("1");
                }
            } else if (variableNode.getName().equals(wrt)) {
                return new Term("1");
            }
            return new Term(variableNode.getName());
        } else if (root instanceof UnaryNode unaryNode) {
            if (unaryNode.getSymbol().equals(UnaryNode.UnarySymbol.NEGATIVE)) {
                return new Term("-" + findDerivative(unaryNode.getChild(), wrt));
            } else {
                return findDerivative(unaryNode.getChild(), wrt);
            }
        } else if (root instanceof FunctionCallNode functionCallNode) {
            String name = functionCallNode.getName();
            Term outsideDerivative;
            Term insideDerivative;
            if (CONTEXT.containsNativeFunction(name)) {
                if (NativeFunction.isTrigonometric(name)) {
                    insideDerivative = findDerivative(functionCallNode.getArgs().getFirst(), wrt);
                    outsideDerivative = new Term(Context.trigonometricDerivatives.get(name)[0]);
                    for (String s : Context.trigonometricDerivatives.get(name)) {
                        if (s.equalsIgnoreCase(outsideDerivative.getName())) continue;
                        outsideDerivative.operation(new Term(s), "*");
                    }
                    outsideDerivative.addParenthesis();
                    insideDerivative.addParenthesis();
                    derivative = insideDerivative.combine(outsideDerivative.combine(new Term("(" + JParser.evaluate(functionCallNode.getArgs().getFirst()) + ")")), "*");
                    return derivative;
                }
            } else {
                FunctionDefinition def = CONTEXT.lookupFunction(name);
                outsideDerivative = evaluate(def.getBody());
                return findDerivative(parse(outsideDerivative), wrt);
            }
        }
        return derivative;
    }

    /**
     * Differentiate a binary expression by operator.
     */
    private static Term differentiateExpression(ExpressionNode left, ExpressionNode right, Operator operator, String wrt) {
        Term differentiated = new Term("");
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
    private static Term differentiateExp(ExpressionNode left, ExpressionNode right, String wrt) {
        Term variable = evaluate(left);
        Term exp = evaluate(right);
        Term expReduced;

        if (!variable.toString().contains(wrt)) {
            return new Term(0);
        }
        if (exp.getValue() != null) {
            expReduced = new Term(exp.getValue().subtract(BigDecimal.ONE));
        } else {
            expReduced = Term.combine(exp, new Term(BigDecimal.ONE), "-");
        }
        expReduced = evaluate(expReduced.toString());
        if (expReduced.toString().length() > 1) {
            expReduced.forceParenthesis();
        }
        if (!expReduced.toString().equals("1")) {
            variable.combine(expReduced, "^");
        }
        exp = evaluate(exp.toString());
        Term differentiated = new Term("");
        differentiated.combine(exp);
        differentiated.combine(variable);
        differentiated.forceParenthesis();
        return differentiated;
    }

    /**
     * Differentiate multiplication (product rule placeholder/combiner).
     */
    private static Term differentiateMult(Term accumulated, ExpressionNode left, ExpressionNode right, String wrt) {
        Term leftObject = findDerivative(left, wrt);
        Term rightObject = findDerivative(right, wrt);
        System.out.println(leftObject);
        System.out.println(rightObject);
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
            Term zero = new Term("0");
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
    private static Term differentiateAddSub(ExpressionNode left, ExpressionNode right, Operator operator, String wrt) {
        Term leftObject = findDerivative(left, wrt);
        Term rightObject = findDerivative(right, wrt);

        if (rightObject.toString().isEmpty()) {
            rightObject = new Term(0);
        } else if (leftObject.toString().isEmpty()) {
            leftObject = new Term(0);
        }

        Term combined = new Term("");
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
                    combined.combine(new Term(1), (operator.equals(Operator.MINUS) ? "-" : ""));
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
    private static Term differentiateDiv(ExpressionNode left, ExpressionNode right, String wrt) {
        Term leftObject = evaluate(left);
        Term rightObject = evaluate(right);
        leftObject.addParenthesis();
        rightObject.addParenthesis();

        Term fx = findDerivative(left, wrt);
        fx.addParenthesis();
        Term gx = findDerivative(right, wrt);
        gx.addParenthesis();

        Term topLeft = Term.combine(fx, rightObject, " * ");
        Term topRight = Term.combine(leftObject, gx, " * ");

        Term bottom = Term.combine(rightObject, new Term("^2"));
        Term top = Term.combine(topLeft, topRight, " - ");
        top.addParenthesis();
        bottom.addParenthesis();
        return Term.combine(top, bottom, "/");
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