package evaluator;

import literals.Term;
import nodes.*;
import tokenizer.Operator;
import tokenizer.OperatorToken;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class that attempts to simplify and factor expression trees.
 *
 * <p>The primary entry point is {@link #factor(ExpressionNode)} which will
 * examine a binary expression and attempt to factor or combine its terms.
 * The class uses {@code JParser} and {@code MathObject} utilities to evaluate
 * and combine subexpressions during factoring.</p>
 *
 * <p>Note: This class operates directly on {@link ExpressionNode} trees and
 * constructs new {@link BinaryNode} instances when combining terms.</p>
 */
public class Simplifier {

    /**
     * Attempt to factor the provided expression node.
     *
     * <p>If the node is a {@link BinaryNode}, this method collects additive
     * terms from both left and right sides, tries to pairwise combine them
     * (including creating explicit exponent and multiplication nodes where
     * necessary), and builds a combined {@link Term} string which is
     * then parsed back into an {@link ExpressionNode} by {@code JParser}.</p>
     *
     * @param node the expression node to factor
     * @return a simplified / factored expression node, or the original node if
     *         no factoring is performed
     */
    public static ExpressionNode factor(ExpressionNode node) {
        if (node instanceof BinaryNode binaryNode && binaryNode.getOperator().equals(Operator.MULT)) {
            ExpressionNode left = binaryNode.getLeftChild();
            ExpressionNode right = binaryNode.getRightChild();
            List<ExpressionNode> terms = new ArrayList<>();
            List<Integer> signs = new ArrayList<>();

            // Collect additive terms from the left subtree.
            collectTerms(left, 1, terms, signs);
            int leftSize = terms.size();

            // Collect additive terms from the right subtree.
            collectTerms(right, 1, terms, signs);

            // Accumulate the resulting expression as a MathObject (string-backed).
            Term result = new Term("");
            int sign;

            // Try pairing each term from left with each term from right.
            for (int i = 0; i < leftSize; i++) {
                for (int j = leftSize; j < terms.size(); j++) {
                    // If a term's parent represents exponentiation, rebuild it as a BinaryNode '^'
                    if (terms.get(j).getParent() instanceof BinaryNode bin && bin.getOperator().equals(Operator.EXP)) {
                        terms.set(j, new BinaryNode(new OperatorToken("^", Operator.EXP), terms.get(j), terms.get(j+1)));
                        terms.remove(j+1);
                        signs.remove(j+1);
                    }
                    if (terms.get(i).getParent() instanceof BinaryNode bin && bin.getOperator().equals(Operator.EXP)) {
                        terms.set(i, new BinaryNode(new OperatorToken("^", Operator.EXP), terms.get(i), terms.get(i+1)));
                        terms.remove(i+1);
                        signs.remove(i+1);
                    }

                    // If adjacent left terms are actually multiplied, merge into a BinaryNode '*'
                    if (terms.get(i).getParent() instanceof BinaryNode bin && bin.getOperator().equals(Operator.MULT) && i + 1 < leftSize) {
                        terms.set(i, new BinaryNode(new OperatorToken("*", Operator.MULT), terms.get(i), terms.get(i+1)));
                        terms.remove(i+1);
                        leftSize--;
                        j--;
                    }

                    // If adjacent right terms are multiplied, merge them as well.
                    if (terms.get(j).getParent() instanceof BinaryNode bin && bin.getOperator().equals(Operator.MULT) && j + 1 < terms.size()) {
                        terms.set(j, new BinaryNode(new OperatorToken("*", Operator.MULT), terms.get(j), terms.get(j+1)));
                        terms.remove(j+1);
                        leftSize--;
                        j--;
                    }

                    // Determine combined sign from the collected signs for each term.
                    sign = (signs.get(i) * signs.get(j) > 0 ? 1 : -1);

                    // Build sign string: first combined pair may omit leading '+'.
                    String signStr = "";
                    if (i == 0 && j == leftSize) {
                        if (sign < 0) {
                            signStr = "-";
                        }
                    } else {
                        signStr = (sign > 0) ? "+" : "-";
                    }

                    // Combine the two terms (as strings) using "*" operator and merge into result.
                    // removeTrailingParenthesis attempts to clean up unnecessary parentheses.
                    result = result.operation(combineTerms(terms.get(i), terms.get(j), "*").removeTrailingParenthesis(), signStr);
                }
            }
            // Parse the combined string back into an ExpressionNode tree.
            return JParser.parse(result);
        }
        // Non-binary nodes are returned unchanged.
        return node;
    }

    /**
     * Combine two expression nodes into a single {@link Term} according to
     * common algebraic heuristics.
     *
     * <p>This method evaluates both subexpressions to {@link Term} forms,
     * inspects for matching variables and exponents, and attempts to combine like
     * terms (e.g. x^a * x^b => x^(a+b)). If only one side contains a variable,
     * it uses the other as a multiplier. Otherwise it performs a generic
     * operation via {@link Term#operation}.</p>
     *
     * @param left the left expression node
     * @param right the right expression node
     * @param operator the operator string to use when combining (e.g. "*")
     * @return a new {@link Term} representing the combined expression
     */
    private static Term combineTerms(ExpressionNode left, ExpressionNode right, String operator) {
        Term leftObject = JParser.EVALUATOR.evaluate(left, JParser.CONTEXT).removeTrailingParenthesis();
        Term rightObject = JParser.EVALUATOR.evaluate(right, JParser.CONTEXT).removeTrailingParenthesis();

        // Find variable names and exponents (if any) inside the evaluated MathObject values.
        String varInLeft = leftObject.findVariable();
        String varInRight = rightObject.findVariable();
        String leftExp = leftObject.findExponent();
        String rightExp = rightObject.findExponent();
        Term combined;

        // If both sides have the same variable, add exponents: x^a * x^b -> x^(a+b)
        if (varInLeft != null && varInLeft.equals(varInRight)) {
            Term newExp = JParser.evaluate(leftExp + "+" + rightExp);
            combined = new Term(leftObject + "^" + newExp);
            return combined;
        } else if (varInLeft != null) {
            // Left contains a variable, right is treated as multiplicative factor.
            leftObject.removeTrailingParenthesis();
            combined = rightObject.operation(leftObject, operator);
            return combined;
        } else if (varInRight != null) {
            // Right contains a variable, left is treated as multiplicative factor.
            rightObject.removeTrailingParenthesis();
            combined = leftObject.operation(rightObject, operator);

            return combined;
        } else {
            // No variable detected on either side; perform generic operation.
            return leftObject.operation(rightObject, operator);
        }
    }

    /**
     * Collect additive terms from an expression tree into flat lists.
     *
     * <p>This method performs a traversal that flattens additions and subtractions
     * into individual terms along with their associated signs. It also handles
     * unary negation by flipping the sign for its child term(s).</p>
     *
     * @param node the node to collect from
     * @param sign the sign to apply to terms found under this node (1 or -1)
     * @param terms output list to which term nodes are appended
     * @param operators output list to which corresponding signs are appended
     */
    private static void collectTerms(ExpressionNode node, int sign, List<ExpressionNode> terms, List<Integer> operators) {
        switch (node) {
            case BinaryNode bin -> {
                // Recurse left with the same sign.
                collectTerms(bin.getLeftChild(), sign, terms, operators);
                // If operator is subtraction, the right subtree terms should flip sign.
                int rightSign = bin.getOperator().equals(Operator.MINUS) ? -1 : 1;
                collectTerms(bin.getRightChild(), rightSign, terms, operators);
            }
            case UnaryNode unaryNode -> {
                // If unary negative, flip the sign for the child; otherwise propagate.
                collectTerms(unaryNode.getChild(), unaryNode.getSymbol().equals(UnaryNode.UnarySymbol.NEGATIVE) ? -sign : sign, terms, operators);
            }
            case null, default -> {
                // Leaf or other node: add to terms with current sign.
                terms.add(node);
                operators.add(sign);
            }
        }
    }
}