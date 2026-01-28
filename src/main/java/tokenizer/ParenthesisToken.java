package tokenizer;

public class ParenthesisToken extends Token{

    public ParenthesisToken(String lexeme) {
        super((lexeme.equals(")") ? TokenType.RPAREN : TokenType.LPAREN), lexeme);
    }

    @Override
    public <T> T getValue() {
        return (T) this.type.toString();
    }
}
