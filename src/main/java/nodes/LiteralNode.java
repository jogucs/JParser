package nodes;

import java.math.BigDecimal;

/**
 * AST node representing a numeric literal (constant) in an expression.
 *
 * <p>This node stores a single numeric value and reports its node type as
 * {@link NodeType#NUMBER}. Evaluators should use {@link #getValue()} to obtain
 * the numeric value (as a boxed {@link Double}).</p>
 */
public class LiteralNode extends ExpressionNode{
    /**
     * The numeric value of this literal.
     */
    private BigDecimal value;

    /**
     * Create a LiteralNode with the provided numeric value.
     *
     * @param value the numeric literal value
     */
    public LiteralNode(double value) {
        this.value = BigDecimal.valueOf(value);
    }

    /**
     * Return the value represented by this node.
     *
     * @return the numeric value (as a {@link Double})
     */
    @Override
    public Object getValue() {
        return value;
    }

    /**
     * Return the node kind for this expression node.
     *
     * @return {@link NodeType#NUMBER}
     */
    @Override
    public NodeType getNodeType() {
        return NodeType.NUMBER;
    }
}
