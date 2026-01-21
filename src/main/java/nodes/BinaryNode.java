package nodes;

import tokenizer.Operator;
import tokenizer.OperatorToken;
import tokenizer.Token;

/**
 * AST node representing a binary operation (e.g. addition, multiplication).
 *
 * <p>Holds the operator token and references to the left and right operand
 * subtrees.</p>
 */
public class BinaryNode extends ExpressionNode {

    /**
     * Left operand of the binary operation.
     */
    private ExpressionNode leftChild;

    /**
     * Right operand of the binary operation.
     */
    private ExpressionNode rightChild;

    /**
     * Token that contains the operator for this binary node.
     */
    private Token token;

    private boolean attachedToVar = false;

    /**
     * Create a BinaryNode with the given operator token and operand nodes.
     *
     * @param token the operator token (e.g. {@code +}, {@code *})
     * @param leftChild left operand subtree
     * @param rightChild right operand subtree
     */
    public BinaryNode(Token token, ExpressionNode leftChild, ExpressionNode rightChild) {
        this.token = token;
        this.leftChild = leftChild;
        this.rightChild = rightChild;
    }

    public BinaryNode(Token token, ExpressionNode leftChild, ExpressionNode rightChild, boolean attachedToVar) {
        this(token, leftChild, rightChild);
        this.attachedToVar = attachedToVar;
    }

    /**
     * Return the right operand node.
     *
     * @return right {@link ExpressionNode}
     */
    public ExpressionNode getRightChild() {
        return rightChild;
    }

    public void setRightChild(ExpressionNode rightChild) {
        this.rightChild = rightChild;
    }

    public void setLeftChild(ExpressionNode leftChild) {
        this.leftChild = leftChild;
    }

    /**
     * Return the left operand node.
     *
     * @return left {@link ExpressionNode}
     */
    public ExpressionNode getLeftChild() {
        return leftChild;
    }

    /**
     * Return the operator represented by this node.
     *
     * @return {@link Operator} of the underlying token
     */
    public Operator getOperator() {
        return token.getValue();
    }

    public void setOperator(Operator operator) {
        this.token = new OperatorToken(Operator.getFromOperator(operator), operator);
    }

    public boolean isAttachedToVar() {
        return attachedToVar;
    }

    /**
     * Node type identifier for visitors or evaluators.
     *
     * @return {@link NodeType#BINARY}
     */
    @Override
    public NodeType getNodeType() {
        return NodeType.BINARY;
    }

    /**
     * Return the raw value associated with this node.
     *
     * <p>For a BinaryNode this is the operator value stored in the token.</p>
     *
     * @return operator value (as an {@link Object})
     */
    @Override
    public Object getValue() {
        return this.token.getValue();
    }
}
