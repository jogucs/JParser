package literals;

import evaluator.JParser;
import nodes.ExpressionNode;

import java.util.List;

/**
 * Represents a user-defined function within the evaluator.
 *
 * <p>Holds the function's name, parameter list, parsed body (AST), and the
 * original expression string when available. The constructor registers the
 * new function in the global parser context {@link JParser#CONTEXT}.</p>
 */
public class FunctionDefinition {
    /**
     * The original textual expression used to define the function, if available.
     * May be set after construction via {@link #setExpression(String)}.
     */
    private String expression;

    /**
     * The function name (identifier).
     */
    private String name;

    /**
     * Ordered list of parameter names for the function.
     */
    private List<String> params;

    /**
     * Parsed function body represented as an {@link ExpressionNode} AST.
     */
    private ExpressionNode body;

    /**
     * Construct a FunctionDefinition and register it in the parser context.
     *
     * @param name   the function name
     * @param params the ordered parameter names
     * @param body   the parsed function body as an {@link ExpressionNode}
     *
     * Side effect: registers this instance in {@code JParser.CONTEXT.functions}
     * using the provided name as the key.
     */
    public FunctionDefinition(String name, List<String> params, ExpressionNode body) {
        this.name = name;
        this.params = params;
        this.body = body;
        this.expression = JParser.evaluate(body).toString();
        JParser.CONTEXT.functions.put(name, this);
    }

    /**
     * Set the original textual expression that defined this function.
     *
     * @param expression the source expression string (may be null)
     */
    public void setExpression(String expression) {
        this.expression = expression;
    }

    /**
     * Return the original expression string associated with this function definition.
     *
     * @return the source expression, or {@code null} if not set
     */
    public String getExpression() {
        return expression;
    }

    /**
     * Get the parsed AST body of the function.
     *
     * @return the function body as an {@link ExpressionNode}
     */
    public ExpressionNode getBody() {
        return body;
    }

    /**
     * Get the function's name.
     *
     * @return the identifier name of the function
     */
    public String getName() {
        return name;
    }

    /**
     * Get the parameter names in declaration order.
     *
     * @return list of parameter names
     */
    public List<String> getParameters() {
        return params;
    }
}
