package evaluator;

import literals.MathObject;
import nodes.*;
import literals.FunctionDefinition;
import tokenizer.Operator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The `Evaluator` class is responsible for evaluating mathematical expressions represented as an
 * abstract syntax tree (AST). It supports literals, variables, function calls, unary operations,
 * and binary operations. The evaluation can handle both numeric and symbolic computations.
 */
public class Evaluator {

    /**
     * Evaluates an expression node within a given evaluation context.
     *
     * @param node    The expression node to evaluate.
     * @param context The evaluation context containing variable and function definitions.
     * @return A `MathObject` representing the result of the evaluation, which can be numeric or symbolic.
     */
    public MathObject evaluate(ExpressionNode node, Context context) {
        if (node instanceof LiteralNode lit) {
            if (((BigDecimal) lit.getValue()).ulp().toString().length() != JParser.getCurrentPrecision()) {
                JParser.setCurrentPrecision(((BigDecimal) lit.getValue()).ulp().toString().length() + 2);
            }
            // Handle literal values by wrapping them in a MathObject.
            MathObject literal = new MathObject((BigDecimal) lit.getValue());
            literal.setName(JParser.normalize(literal.getValue()));
            return literal;
        } else if (node instanceof VariableNode var) {
            // Handle variables. If the variable is not defined in the context, return it as symbolic.
            return new MathObject(var.getName());
        } else if (node instanceof FunctionCallNode funcCall) {
            // Handle function calls, including user-defined and native functions.
            String fname = funcCall.getName();
            FunctionDefinition def = context.lookupFunction(fname);
            if (def != null) {
                ExpressionNode substituted = substituteFunctionBody(def, funcCall.getArgs());
                Context childContext = new Context(context);
                return evaluate(substituted, childContext);
            }
            double val = 0.0;

            if (context.containsFunction(funcCall.getName())) {
                // Evaluate user-defined functions.
                Context childContext = new Context(context);
                FunctionDefinition functionDefinition = context.lookupFunction(funcCall.getName());

                // Validate the number of arguments.
                if (functionDefinition.getParameters().size() != funcCall.getArgs().size()) {
                    throw new RuntimeException("Invalid number of parameters in function " + functionDefinition.getName());
                }

                // Map arguments to function parameters.
                for (int i = 0; i < funcCall.getArgs().size(); i++) {
                    MathObject object = evaluate(funcCall.getArgs().get(i), context);
                    if (object.getValue() != null) {
                        childContext.variables.put(functionDefinition.getParameters().get(i), object.getValue().doubleValue());
                    }
                }

                // Evaluate the function body.
                MathObject evaluated = evaluate(functionDefinition.getBody(), childContext);
                if (evaluated.getValue() != null) {
                    val += evaluated.getValue().doubleValue();
                } else {
                    return evaluated;
                }
            } else if (context.containsNativeFunction(fname)) {
                // Evaluate native functions.
                BigDecimal[] args = new BigDecimal[funcCall.getArgs().size()];
                for (int i = 0; i < funcCall.getArgs().size(); i++) {
                    args[i] = evaluate(funcCall.getArgs().get(i), context).getValue();
                }
                if (args.length < 1) {
                    throw new RuntimeException("Can't evaluate function with 0 arguments passed");
                }
                return new MathObject(context.callNativeFunction(fname, args));
            } else {
                // Throw an error if the function is not found.
                throw new RuntimeException("Unable to locate function " + funcCall.getName() + " in context");
            }
            return new MathObject(val);
        } else if (node instanceof UnaryNode un) {
            // Handle unary operations (e.g., positive, negative).
            MathObject value = evaluate(un.getChild(), context);
            if (value.getName() == null) {
                // Numeric unary operations.
                return switch (un.getSymbol()) {
                    case NEGATIVE -> new MathObject(value.getValue().multiply(BigDecimal.valueOf(-1)));
                    case POSITIVE -> value;
                };
            } else {
                // Symbolic unary operations.
                return switch (un.getSymbol()) {
                    case NEGATIVE -> new MathObject("-" + value.getName());
                    case POSITIVE -> value;
                };
            }
        } else if (node instanceof BinaryNode bin) {
            // Handle binary operations (e.g., addition, subtraction, multiplication, etc.).
            MathObject leftObj = evaluate(bin.getLeftChild(), context);
            MathObject rightObj = evaluate(bin.getRightChild(), context);
            if ((!JParser.isNumeric(leftObj.toString()) && leftObj.getName() != null) || (!JParser.isNumeric(rightObj.toString()) && rightObj.getName() != null)) {
                // Symbolic binary operations.
                if (leftObj.getValue() != null) {
                    leftObj.setValue(leftObj.getValue());
                }
                if (rightObj.getValue() != null) {
                    rightObj.setValue(rightObj.getValue());
                }
                String op = operatorToString(bin.getOperator());
                String sym;
                if (!op.equals("*")) {
                    sym = leftObj + "" + op + "" + rightObj;
                } else {
                    sym =  leftObj + "" + rightObj;
                }
                MathObject symObject = new MathObject(sym);
                symObject.forceParenthesis();
                return symObject;
            }

            // Numeric binary operations.
            BigDecimal left = leftObj.getValue();
            BigDecimal right = rightObj.getValue();

            return switch (bin.getOperator()) {
                case PLUS -> new MathObject(left.add(right));
                case MINUS -> new MathObject(left.subtract(right));
                case MULT -> new MathObject(left.multiply(right));
                case DIV -> new MathObject(left.divide(right, 200, RoundingMode.HALF_UP));
                case GT -> new MathObject((left.doubleValue() > right.doubleValue() ? 1 : 0));
                case LT -> new MathObject((left.doubleValue() < right.doubleValue() ? 1 : 0));
                case GTE -> new MathObject((left.doubleValue() >= right.doubleValue() ? 1 : 0));
                case LTE -> new MathObject((left.doubleValue() <= right.doubleValue() ? 1 : 0));
                case NEQ -> new MathObject((left.doubleValue() != right.doubleValue() ? 1 : 0));
                case EQUAL -> new MathObject((left == right ? 1 : 0));
                case PEQUAL -> new MathObject(left.add(left.add(right)));
                case EXP -> new MathObject(evalExponent(left.doubleValue(), right.doubleValue()));
            };
        } else if (node instanceof SpaceNode spaceNode) {
            return new MathObject(0.0);
        } else {
            // Throw an error for unknown node types.
            throw new RuntimeException("Unknown node type " + node);
        }
    }

    /**
     * Evaluates the exponentiation operation.
     *
     * @param left  The base value.
     * @param right The exponent value.
     * @return The result of raising `left` to the power of `right`.
     */
    private double evalExponent(double left, double right) {
        double val = left;
        for (int i = 1; i < right; i++) {
            val *= left;
        }
        return val;
    }

    private MathObject combineTerms(MathObject left, MathObject right, Operator operator) {
        switch (operator) {
            case MULT -> {
                // Case for left being variable, right being literal
                if (left.getName() != null && right.getValue() != null) {
                    return MathObject.combine(right, left, "*");
                } else if (left.getName() == null && right.getValue() == null) { // Case for left being number, right being variable
                    return MathObject.combine(left, right, "*");
                } else {
                    return new MathObject(left.getValue().multiply(right.getValue()));
                }
            } case PLUS -> {

            }
        }
        return left;
    }

    /**
     * Converts an operator enum to its string representation.
     *
     * @param op The operator enum.
     * @return The string representation of the operator.
     */
    private String operatorToString(Enum<?> op) {
        String name = op.name();
        return switch (name) {
            case "PLUS" -> "+";
            case "MINUS" -> "-";
            case "MULT" -> "*";
            case "DIV" -> "/";
            case "EXP" -> "^";
            case "GT" -> ">";
            case "LT" -> "<";
            case "GTE" -> ">=";
            case "LTE" -> "<=";
            case "NEQ" -> "!=";
            case "EQUAL" -> "==";
            case "PEQUAL" -> "+=";
            default -> name;
        };
    }

    private ExpressionNode substituteFunctionBody(FunctionDefinition definition, List<ExpressionNode> callArgs) {
        MathObject body = JParser.evaluate(definition.getBody());
        List<String> params = definition.getParameters();
        for (int i = 0; i < params.size() && i < callArgs.size(); i++) {
            String param = params.get(i);
            MathObject argExpr = JParser.evaluate(callArgs.get(i));
            body.setName(body.toString().replaceAll(Pattern.quote(param), "(" + argExpr + ")"));
        }
        return JParser.parse(body.toString());
    }
}