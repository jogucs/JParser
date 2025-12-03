package tokenizer;

/**
 * Enumeration of lexical token types produced by the tokenizer.
 *
 * <p>Each enum constant represents a distinct category of lexeme that the
 * tokenizer recognizes. Consumers (parser, AST builder, evaluator) can use
 * these values to switch on the kind of token encountered.</p>
 */
public enum TokenType {
    /**
     * Numeric literal token (e.g. integer or floating-point).
     */
    NUMBER,
    /**
     * Operator token (e.g. +, -, *, /).
     */
    OPERATOR,
    /**
     * Identifier token (variable names).
     */
    IDENTIFIER,
    /**
     * Begin vector token (e.g. '[').
     */
    BEGVEC,
    /**
     * End vector token (e.g. ']').
     */
    ENDVEC,
    /**
     * Left parenthesis token '('.
     */
    LPAREN,
    /**
     * Right parenthesis token ')'.
     */
    RPAREN,
    /**
     * Comma separator token ','.
     */
    COMMA,
    /**
     * End-of-file/input marker.
     */
    SPACE,
    EOF,
}
