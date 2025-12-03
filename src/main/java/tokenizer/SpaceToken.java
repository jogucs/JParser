package tokenizer;

public class SpaceToken extends Token{
    /**
     * Construct a token with the given type and lexeme.
     *
     */
    SpaceToken() {
        super(TokenType.SPACE, " ");
    }

    @Override
    public <T> T getValue() {
        return (T) " ";
    }
}
