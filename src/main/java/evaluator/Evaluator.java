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
            MathObject object;
            MathObject left = evaluate(bin.getLeftChild(), context);
            MathObject right = evaluate(bin.getRightChild(), context);
            object = combineTerms(left, right, bin.getOperator());
            object.addParenthesis();
            object.removeTrailingParenthesis();
            return object;
        }
        return null;
    }

    private MathObject combineTerms(MathObject left, MathObject right, Operator operator) {
        if (operator.equals(Operator.MULT) && (left.findVariable() != null || right.findVariable() != null)) {
            return MathObject.combine(left, right);
        }
        return left.operation(right, operatorToString(operator));
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