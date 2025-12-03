/**
 * Base abstract class for all expression AST nodes.
 *
 * <p>Concrete subclasses represent specific expression kinds (numbers, variables,
 * function definitions/calls, vectors, matrices, binary and unary operations).
 * Implementations must provide the node type and a value representation used by
 * evaluators or visitors.</p>
 */
package nodes;

public abstract class ExpressionNode {

    /**
     * Enumeration of possible expression node kinds.
     *
     * - NUMBER: numeric literal nodes
     * - VARIABLE: identifier/variable reference nodes
     * - FUNCTION_DEF: user-defined function declaration nodes
     * - FUNCTION_CALL: function invocation nodes
     * - VECTOR: one-dimensional collection nodes
     * - MATRIX: two-dimensional collection nodes
     * - BINARY: binary operator nodes (e.g. +, -, *, /, ^)
     * - UNARY: unary operator nodes (e.g. negation)
     */
    public enum NodeType {
        NUMBER,
        VARIABLE,
        FUNCTION_DEF,
        FUNCTION_CALL,
        VECTOR,
        MATRIX,
        BINARY,
        UNARY,
        SPACE
    }

    /**
     * Return the specific NodeType of this node instance.
     *
     * @return node type enum value indicating the concrete node kind
     */
    public abstract NodeType getNodeType();

    /**
     * Return a value associated with this node.
     *
     * <p>Interpretation of the returned Object depends on the node type:
     * for a NUMBER node this might be a Double, for VARIABLE a String name,
     * for operators an Operator enum, etc.</p>
     *
     * @return node-specific value used by evaluators or consumers
     */
    public abstract Object getValue();
}
