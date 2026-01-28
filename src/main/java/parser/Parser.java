package parser;

import evaluator.Context;
import evaluator.JParser;
import nodes.*;
import tokenizer.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Recursive-descent parser for mathematical expressions and simple function/matrix
 * constructs.
 *
 * <p>This parser operates over a pre-tokenized input ({@link Token}) and builds
 * an {@link ExpressionNode} AST. The grammar implemented (informally) is:
 *
 * <pre>
 * expression  ::= sign
 * term        ::= factor ( ('+' | '-') factor )*
 * factor      ::= exponent ( ('*' | '/') exponent )*
 * exponent    ::= unary ( '^' unary )*
 * unary       ::= '-' unary | primary
 * primary     ::= NUMBER | IDENTIFIER (function call or definition) | '(' expression ')' | vector/matrix
 * </pre>
 *
 * <p>Function definitions are detected by checking the token after the closing
 * parenthesis for an {@code '='} operator. The parser also inserts implicit
 * multiplication when a number is directly followed by an identifier (e.g.
 * {@code 2x} becomes {@code 2 * x}) by injecting an OperatorToken.</p>
 */
public class Parser {

    /**
     * Token stream to parse.
     */
    private final List<Token> tokens;

    private boolean inVector = false;
    private boolean nativeFunction = false;

    /**
     * Current position (index) within {@link #tokens}.
     */
    private int position = 0;

    /**
     * Create a parser for the given token list.
     *
     * @param tokens the pre-tokenized input
     */
    public Parser(List<Token> tokens){
        this.tokens = tokens;
    }

    /**
     * Entry point to parse a complete expression from the current token stream.
     *
     * @return root {@link ExpressionNode} of the parsed expression
     */
    public ExpressionNode parseExpression() {
        ExpressionNode body = parseSign();
        JParser.parseThroughTree(body);
        return body;
    }

    /**
     * Parse additions and subtractions (left-associative).
     *
     * @return parsed {@link ExpressionNode} for the term sequence
     */
    private ExpressionNode parseTerm()  {
        ExpressionNode left = parseFactor();

        while (match(Operator.PLUS) || match(Operator.MINUS)) {
            Token token = consume();
            ExpressionNode right = parseFactor();
            left = new BinaryNode(token, left, right);
        }

        return left;
    }

    /**
     * Parse multiplications and divisions (left-associative).
     *
     * @return parsed {@link ExpressionNode} for the factor sequence
     */
    private ExpressionNode parseFactor() {
        ExpressionNode left = parseExponent();

        while (match(Operator.DIV) || match(Operator.MULT)) {
            Token token = consume();
            ExpressionNode right = parseExponent();
            if (right instanceof VariableNode || left instanceof VariableNode) {
                left = new BinaryNode(token, left, right, true);
            } else {
                left = new BinaryNode(token, left, right);
            }
        }

        return left;
    }

    /**
     * Parse exponentiation (right-associative in many grammars; implemented here
     * as repeated binary nodes).
     *
     * @return parsed {@link ExpressionNode} for exponent expressions
     */
    private ExpressionNode parseExponent() {
        ExpressionNode left = parseUnary();

        while (match(Operator.EXP)) {
            Token token = consume();
            ExpressionNode right = parseUnary();
            left = new BinaryNode(token, left, right);
        }

        return left;
    }

    /**
     * Parse comparison and equality operators (e.g. {@code >, >=, <=, <, ==, !=, =}).
     *
     * @return parsed {@link ExpressionNode} representing comparisons
     */
    private ExpressionNode parseSign() {
        ExpressionNode left = parseTerm();

        while (match(Operator.GT) || match(Operator.GTE) || match(Operator.LTE) || match(Operator.LT) || match(Operator.PEQUAL) || match(Operator.NEQ) || match(Operator.EQUAL)) {
            Token token = consume();
            ExpressionNode right = parseTerm();
            left = new BinaryNode(token, left, right);
        }

        return left;
    }

    /**
     * Parse primary expressions:
     * - NUMBER: produces {@link LiteralNode}. If the next token is an identifier,
     *   implicit multiplication is inserted by adding a {@link OperatorToken} "*".
     * - IDENTIFIER: delegates to {@link #parseIdentifierBasedExpression()} which
     *   resolves variables, function calls and function definitions.
     * - LPAREN: parses a parenthesized expression.
     * - BEGVEC: parses a matrix/vector construct via {@link #parseMatrix()}.
     *
     * @return parsed primary {@link ExpressionNode}
     */
    private ExpressionNode parsePrimary() {
        while (current().getType().equals(TokenType.RPAREN)) {
            consume();
        }
        Token tok = current();
        TokenType nextType = Objects.requireNonNull(peek()).getType();
        switch (current().getType()) {
            case NUMBER -> {
                if (nextType.equals(TokenType.IDENTIFIER) || nextType.equals(TokenType.LPAREN)) {
                    tokens.add(position + 1, new OperatorToken("*", Operator.MULT));
                }
                consume();
                return new LiteralNode(tok.getValue());
            } case IDENTIFIER -> {
                return parseIdentifierBasedExpression();
            } case LPAREN -> {
                consume();
                ExpressionNode node = parseExpression();
                if (peek() != null && peek().getType().equals(TokenType.LPAREN)) {
                    tokens.add(position + 1, new OperatorToken("*", Operator.MULT));
                }
                consume();
                return node;
            } case BEGVEC -> {
                return parseMatrix();
            } case SPACE -> {
                consume();
                if (!inVector) {
                    return new LiteralNode(0.0);
                }
                return new SpaceNode();
            }
            case null, default -> throw new RuntimeException("Invalid token type passed " + tok.getType());
        }
    }

    /**
     * Parse unary expressions. Currently only unary negation is supported.
     *
     * @return parsed {@link ExpressionNode} for unary expression
     */
    private ExpressionNode parseUnary() {
        if (current() != null && current().getValue().equals(Operator.MINUS)) {
            consume();
            return new UnaryNode(UnaryNode.UnarySymbol.NEGATIVE, parseUnary());
        }
        return parsePrimary();
    }

    /**
     * Parse a matrix construct which consists of one or more vectors.
     *
     * @return {@link MatrixNode} representing the parsed matrix
     */
    private ExpressionNode parseMatrix() {
        List<VectorNode> nodes = new ArrayList<>();

        while (current().getType().equals(TokenType.BEGVEC)) {
            nodes.add(parseVector());
        }

        return new MatrixNode(nodes);
    }

    /**
     * Parse a vector body delimited by {@code BEGVEC} and {@code ENDVEC}.
     *
     * @return {@link VectorNode} containing parsed expressions for each element
     */
    private VectorNode parseVector() {
        inVector = true;
        consume();
        List<ExpressionNode> body = new ArrayList<>();

        while (!current().getType().equals(TokenType.ENDVEC)) {
            body.add(parseExpression());
        }
        consume();
        inVector = false;
        return new VectorNode(body);
    }

    /**
     * Parse a function call given the function name. Expects the current token
     * to be {@code LPAREN} when called (the method consumes the closing parenthesis).
     *
     * @param name function name
     * @return {@link FunctionCallNode} with parsed argument list
     */
    private ExpressionNode parseFunctionCall(String name) {
        consume();
        List<ExpressionNode> args = new ArrayList<>();

        if (!current().getType().equals(TokenType.RPAREN)) {
            args.add(parseExpression());
            while (current().getType().equals(TokenType.COMMA)) {
                consume();
                args.add(parseExpression());
            }
        }
        consume();
        return new FunctionCallNode(name, args);
    }

    /**
     * Parse a function definition of the form: name(params...) = expression.
     * The method expects that {@code LPAREN} is current when called and consumes
     * tokens up to and including the function body expression.
     *
     * @param name function name
     * @return {@link FunctionDefinitionNode} with parameter list and body
     */
    private ExpressionNode parseFunctionDefinition(String name) {
        consume();
        List<String> params = new ArrayList<>();
        if (!current().getType().equals(TokenType.RPAREN)) {
            while (true) {
                if (!current().getType().equals(TokenType.IDENTIFIER)) {
                    throw new RuntimeException("Expected parameter name in function definition");
                }
                params.add(current().getValue().toString());
                consume();
                if (current().getType().equals(TokenType.COMMA)) {
                    consume();
                } else {
                    break;
                }
            }
        }
        expect(TokenType.RPAREN);
        consume();
        if (!(current().getType().equals(TokenType.OPERATOR) && current().getValue().equals(Operator.EQUAL))) {
            throw new RuntimeException("Expected '=' after function parameters");
        }
        nativeFunction = false;
        consume();
        ExpressionNode body = parseExpression();
        return new FunctionDefinitionNode(name, params, body);
    }

    /**
     * Resolve an identifier; this may be a variable, or a function call/definition
     * when followed by {@code LPAREN}.
     *
     * @return {@link VariableNode} or a function-related node
     */
    private ExpressionNode parseIdentifierBasedExpression() {
        String name = current().getValue().toString();
        consume();

        if (current().getType().equals(TokenType.LPAREN)) {
            return parseFunctionBasedExpression(name);
        }

        return new VariableNode(name);
    }

    /**
     * Decide whether the identifier with following parenthesis is a function
     * definition or a call, then dispatch accordingly.
     *
     * @param name identifier name
     * @return parsed function node
     */
    private ExpressionNode parseFunctionBasedExpression(String name) {
        if (isFunctionDefinition(name)) {
            return parseFunctionDefinition(name);
        } else {
            return parseFunctionCall(name);
        }
    }

    /**
     * Detect whether the current identifier+parenthesis sequence is a function
     * definition by finding the matching closing parenthesis and checking the
     * subsequent token for an {@code '='} operator.
     *
     * @return true if a function definition pattern is detected
     */
    private boolean isFunctionDefinition(String name) {
        int closing = findClosingParenIndex(position);
        if (closing >= 0 && closing + 1 < tokens.size()) {
            Token next = tokens.get(closing + 1);
            if (JParser.CONTEXT.containsNativeFunction(name)) {
                return false;
            }
            return ((next.getType().equals(TokenType.OPERATOR) && next.getValue().equals(Operator.EQUAL)));
        }
        return false;
    }

    /**
     * Find the index of the matching closing parenthesis starting at {@code pos}.
     * This method counts nested parentheses and returns the index of the closing
     * parenthesis that matches the first opening parenthesis encountered.
     *
     * @param pos starting index (should point to an {@code LPAREN})
     * @return index of matching {@code RPAREN} or -1 if not found
     */
    private int findClosingParenIndex(int pos) {
        int depth = 0;
        for (int i = pos; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.getType().equals(TokenType.LPAREN)) {
                depth++;
            } else if (t.getType().equals(TokenType.RPAREN)) {
                depth --;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /**
     * Return the current token at {@link #position}.
     *
     * @return current {@link Token}
     */
    private Token current() {
        return tokens.get(position);
    }

    /**
     * Peek at the next token without advancing the parser.
     *
     * @return next {@link Token} or {@code null} if at end of stream
     */
    private Token peek() {
        if (position + 1 < tokens.size()) {
            return tokens.get(position + 1);
        }
        return null;
    }

    /**
     * Consume and return the current token, advancing the parser position.
     *
     * @return consumed {@link Token} or {@code null} if past end
     */
    private Token consume() {
        if (position < tokens.size()) {
            Token toReturn = current();
            position++;
            return toReturn;
        }
        return null;
    }

    /**
     * Check whether the current token's operator matches the provided operator.
     *
     * @param operator operator to compare with
     * @return true if current token exists and has the given operator value
     */
    private boolean match(Operator operator) {
        if (position < tokens.size()) {
            return current().getValue().equals(operator);
        }
        return false;
    }

    /**
     * Ensure the current token has the expected {@link TokenType}.
     *
     * @param type expected token type
     * @throws RuntimeException if the current token type does not match
     */
    private void expect(TokenType type) {
        if (!current().getType().equals(type)) {
            throw new RuntimeException("Error while parsing: token does not match expected type of " + type);
        }
    }
}
