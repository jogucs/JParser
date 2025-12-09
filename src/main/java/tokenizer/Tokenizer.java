package tokenizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Converts an input mathematical expression string into a list of {@link Token}
 * instances for subsequent parsing.
 *
 * <p>The tokenizer recognizes numbers (with optional decimal point), operators
 * (including two-character operators), identifiers (function/variable names),
 * parentheses, commas, vector delimiters ('[' and ']'), and an end-of-file token.
 * It uses {@link Operator#getAsStringList()} to determine valid operator strings.</p>
 */
public class Tokenizer {

    /**
     * Input expression to be tokenized.
     */
    private final String expression;

    /**
     * Current character index in {@link #expression}.
     */
    private int position = 0;

    private boolean inVector = false;

    /**
     * Cached list of operator string representations to match against input.
     */
    private final List<String> operators = Operator.getAsStringList();

    /**
     * Create a tokenizer for the given expression.
     *
     * @param expression input expression
     */
    public Tokenizer(String expression) {
        this.expression = expression;
    }

    /**
     * Tokenize the expression.
     *
     * <p>This method iterates over {@link #expression} and builds a {@link List}
     * of {@link Token} objects. Token types produced include:
     * - {@link NumberToken} for numeric literals (supports one decimal dot),
     * - {@link OperatorToken} for recognized operators (one- or two-character),
     * - {@link IdentifierToken} for contiguous alphabetic names,
     * - {@link ParenthesisToken} for '(' and ')',
     * - {@link CommaToken} for ',' separators,
     * - {@link VectorToken} for '[' and ']' vector delimiters,
     * - {@link EOFToken} appended at the end of the stream.</p>
     *
     * <p>Notes:
     * - Numeric parsing stops when a second '.' would be encountered.
     * - Two-character operators are matched by checking current+peek.</p>
     *
     * @return list of tokens representing the input expression, terminated by EOFToken
     */
    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        Character currentChar;
        boolean dotSeen;
        StringBuilder sb = new StringBuilder();

        // Iterate until end of expression
        while (current() != null && position < expression.length()) {
            currentChar = current();
            if (currentChar != null) {
                // Number literal: sequence of digits optionally containing one '.'
                if (Character.isDigit(currentChar)) {
                    dotSeen = false;
                    while (currentChar != null && (Character.isDigit(currentChar) || currentChar.equals('.'))) {
                        if (currentChar.equals('.')) {
                            if (dotSeen) {
                                // Second dot encountered: stop numeric accumulation
                                break;
                            } else {
                                dotSeen = true;
                            }
                        }
                        sb.append(currentChar);
                        // Advance while next char is digit or dot
                        if (peek() != null && (Character.isDigit(peek()) || match('.'))) {
                            currentChar = advance();
                        } else {
                            break;
                        }
                    }
                    // Emit a NumberToken with parsed double value
                    tokens.add(new NumberToken(sb.toString(), Double.parseDouble(sb.toString())));
                }
                else if (currentChar.equals(')') || currentChar.equals('(')) {
                    sb.append(currentChar);
                    tokens.add(new ParenthesisToken(sb.toString()));
                }
                // Operator token: check for two-char operator first
                else if (operators.contains(currentChar.toString())) {
                    if (peek() != null && operators.contains(currentChar.toString() + peek())) {
                        sb.append(currentChar).append(peek());
                        tokens.add(new OperatorToken(sb.toString(), Operator.getAsString(sb.toString())));
                        // Advance once to consume peek() as part of the operator
                        advance();
                    } else {
                        sb.append(currentChar);
                        tokens.add(new OperatorToken(sb.toString(), Operator.getAsString(sb.toString())));
                    }
                }
                // Identifier token: contiguous letters (stops at parentheses or non-letter)
                else if (Character.isLetter(currentChar)) {
                    while (currentChar != null && Character.isLetter(currentChar) && currentChar != '(' && currentChar != ')') {
                        sb.append(currentChar);
                        // Stop if next char is '(' or ')' or not a letter; otherwise advance
                        if (peek() != null && (match('(') || match(')') || !Character.isLetter(peek()))) {
                            break;
                        } else {
                            currentChar = advance();
                        }
                    }
                    tokens.add(new IdentifierToken(sb.toString()));
                }
                // Parentheses
                // Comma separator
                else if (currentChar.equals(',')) {
                    tokens.add(new CommaToken());
                }
                // Vector delimiters '[' or ']'
                else if (currentChar.equals('[') || currentChar.equals(']')) {
                    if (currentChar.equals('[')) {
                        inVector = true;
                    } else if (currentChar.equals(']')) {
                        inVector = false;
                    }
                    sb.append(currentChar);
                    tokens.add(new VectorToken(sb.toString()));
                } else if (currentChar.equals(' ') && inVector) {
                    tokens.add(new SpaceToken());
                }
                // Advance to next character and reset the buffer
                advance();
                sb.setLength(0);
            }
        }
        // Always terminate token stream with EOF
        tokens.add(new EOFToken());
        return tokens;
    }

    /**
     * Return the current character under {@link #position}, or {@code null} if at end.
     *
     * @return current character or {@code null}
     */
    private Character current() {
        return (position < expression.length() ? expression.charAt(position) : null);
    }

    /**
     * Peek the next character without advancing the position.
     *
     * @return next character or {@code null} if none
     */
    private Character peek() {
        if (position + 1 < expression.length()) {
            return expression.charAt(position + 1);
        }
        return null;
    }

    /**
     * Advance the position by one and return the new current character.
     *
     * @return character at the new position or {@code null} when past end
     */
    private Character advance() {
        position++;
        if (position < expression.length()) {
            return expression.charAt(position);
        }
        return null;
    }

    /**
     * Check whether the next character (peek) equals the provided character.
     *
     * @param c character to compare with peek()
     * @return true if peek() equals c
     */
    private boolean match(Character c) {
        return Objects.equals(peek(), c);
    }

    /**
     * Return a substring starting at current position of the requested length
     * when available. This helper is used for lookahead operations.
     *
     * @param amount number of characters to include
     * @return substring from current position of length 'amount' when available,
     *         otherwise the remainder of the expression
     */
    private String lookAhead(int amount) {
        if (position + amount < expression.length()) {
            return expression.substring(position, position + amount);
        }
        return expression.substring(position + 1);
    }
}
