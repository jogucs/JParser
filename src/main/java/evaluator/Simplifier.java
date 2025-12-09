package evaluator;

import literals.MathObject;
import nodes.*;
import tokenizer.Operator;
import tokenizer.OperatorToken;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Simplifier {

    public static ExpressionNode simplify(ExpressionNode node) {
        if (node instanceof BinaryNode binary) {
            // simplify children first
            ExpressionNode left = simplify(binary.getLeftChild());
            ExpressionNode right = simplify(binary.getRightChild());
            Operator op = binary.getOperator();

            // only handle associative + and -
            if (op.equals(Operator.PLUS) || op.equals(Operator.MINUS)) {
                List<ExpressionNode> terms = new ArrayList<>();
                List<Integer> signs = new ArrayList<>();
                collectTerms(left, 1, terms, signs);
                collectTerms(right, op.equals(Operator.PLUS) ? 1 : -1, terms, signs);

                // sum numeric constants and collect non-numeric terms
                BigDecimal numericSum = BigDecimal.ZERO;
                List<ExpressionNode> nonNumericTerms = new ArrayList<>();
                List<Integer> nonNumericSigns = new ArrayList<>();
                for (int i = 0; i < terms.size(); i++) {
                    ExpressionNode t = terms.get(i);
                    int s = signs.get(i);
                    if (t instanceof LiteralNode lit && lit.getValue() != null) {
                        BigDecimal val = (BigDecimal) lit.getValue();
                        numericSum = numericSum.add(val.multiply(BigDecimal.valueOf(s)));
                    } else {
                        nonNumericTerms.add(t);
                        nonNumericSigns.add(s);
                    }
                }

                // build result
                ExpressionNode result = null;

                if (numericSum.compareTo(BigDecimal.ZERO) != 0) {
                    // create numeric literal (can be negative)
                    result = new LiteralNode(numericSum.doubleValue());
                }

                // append non-numeric terms
                for (int i = 0; i < nonNumericTerms.size(); i++) {
                    ExpressionNode term = nonNumericTerms.get(i);
                    int s = nonNumericSigns.get(i);
                    if (result == null) {
                        // first term becomes the base; if negative, wrap in unary negation
                        if (s < 0) {
                            result = new UnaryNode(UnaryNode.UnarySymbol.NEGATIVE, term);
                        } else {
                            result = term;
                        }
                    } else {
                        // append with + or -
                        OperatorToken opTok = s >= 0
                                ? new OperatorToken("+", Operator.PLUS)
                                : new OperatorToken("-", Operator.MINUS);
                        result = new BinaryNode(opTok, result, term);
                    }
                }

                // if everything canceled out, return 0 literal
                if (result == null) {
                    return new LiteralNode(0.0);
                }
                return result;
            }

            // non-add/sub: return binary with simplified children
            OperatorToken tok = new OperatorToken(op == null ? "" : String.valueOf(Operator.getFromOperator(op)), op);
            return new BinaryNode(tok, left, right);
        } else if (node instanceof UnaryNode unary) {
            return new UnaryNode(unary.getSymbol(), simplify(unary.getChild()));
        } else if (node instanceof MatrixNode || node instanceof FunctionCallNode || node instanceof FunctionDefinitionNode
                || node instanceof VariableNode || node instanceof LiteralNode || node instanceof VectorNode || node instanceof SpaceNode) {
            // leaf or complex nodes: nothing to fold at this simple pass
            return node;
        } else {
            return node;
        }
    }

    private static void collectTerms(ExpressionNode node, int sign, List<ExpressionNode> terms, List<Integer> signs) {
        switch (node) {
            case BinaryNode bin when (bin.getOperator().equals(Operator.PLUS) || bin.getOperator().equals(Operator.MINUS)) -> {
                collectTerms(bin.getLeftChild(), sign, terms, signs);
                int rightSign = bin.getOperator().equals(Operator.PLUS) ? sign : -sign;
                collectTerms(bin.getRightChild(), rightSign, terms, signs);
            }
            case UnaryNode unaryNode ->
                    collectTerms(unaryNode.getChild(), unaryNode.getSymbol().equals(UnaryNode.UnarySymbol.NEGATIVE) ? -sign : sign, terms, signs);
            case null, default -> {
                terms.add(node);
                signs.add(sign);
            }
        }
    }

    public static ExpressionNode factor(ExpressionNode node) {
        if (node instanceof BinaryNode bin) {
            Operator op = bin.getOperator();
            ExpressionNode left = JParser.parse(JParser.evaluate(bin.getLeftChild()));
            ExpressionNode right = JParser.parse(JParser.evaluate(bin.getRightChild()));
            if (op.equals(Operator.MULT)) {
                List<ExpressionNode> terms = new ArrayList<>();
                List<Integer> signs = new ArrayList<>();
                if (left instanceof BinaryNode subBin && !(right instanceof BinaryNode)) {
                    if (subBin.getOperator().equals(Operator.MULT)) {
                        collectTerms(left, 1, terms, signs);
                        left = factor(left);
                    }
                } else if (!(left instanceof BinaryNode) && right instanceof BinaryNode subBin) {
                    if (subBin.getOperator().equals(Operator.MULT)) {
                        collectTerms(right, 1, terms, signs);
                        right = factor(right);
                    }
                } else {
                    left = factor(left);
                    right = factor(right);
                }
                collectTerms(left, 1, terms, signs);
                int leftSize = terms.size();
                collectTerms(right, 1, terms, signs);
                List<MathObject> combinedValues = new ArrayList<>();
                MathObject val;
                MathObject combined = new MathObject("");
                for (int i = 0; i < leftSize; i++) {
                    for (int j = leftSize; j < terms.size(); j++) {
                            val = combineTerms(terms.get(i), terms.get(j), "*");
                        if (signs.get(i).equals(-1)) {
                            val = new MathObject("-" + val.toString());
                        }
                        combinedValues.add(val);
                    }
                }
                for (MathObject value : combinedValues) {
                    combined = MathObject.combine(combined, value, (value.toString().contains("-") ? "" : "+"));
                }
                if (!combined.toString().isEmpty() && combined.getName().charAt(0) == '+') {
                     combined.setName(combined.getName().substring(1));
                }
                System.out.println(combined);
            } else {
                return JParser.parse(JParser.evaluate(node));
            }
        } else if (node instanceof MatrixNode || node instanceof FunctionCallNode || node instanceof FunctionDefinitionNode
                || node instanceof VariableNode || node instanceof LiteralNode || node instanceof VectorNode || node instanceof SpaceNode) {
            // leaf or complex nodes: nothing to fold at this simple pass
            return node;
        }
        return node;
    }

    private static MathObject combineTerms(ExpressionNode left, ExpressionNode right, String operator) {
        MathObject leftObject = JParser.evaluate(left);
        MathObject rightObject = JParser.evaluate(left);
        System.out.println(leftObject + operator + rightObject);
        return leftObject.operation(rightObject, operator);
    }
}

