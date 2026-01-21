package literals;

import evaluator.JParser;
import nodes.ExpressionNode;
import nodes.UnaryNode;
import nodes.VariableNode;
import tokenizer.Operator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents either a named variable or a numeric constant used in mathematical
 * expressions.
 *
 * <p>A MathObject can hold a variable name (for example "x") or a numeric
 * value. If a value is queried when no numeric value is set, {@link #getValue()}
 * returns 0.0. The {@link #toString()} method prefers the variable name if set,
 * otherwise returns the numeric value as a string, and falls back to "0.0".</p>
 */
public class MathObject {
    /**
     * The variable name represented by this object, e.g. "x".
     * When non-null this object represents a variable rather than a concrete value.
     */
    private String name;

    /**
     * The numeric value represented by this object.
     * When non-null this object represents a concrete numeric constant.
     */
    private BigDecimal value;

    private MathObject coefficient;
    private MathObject exponent;

    /**
     * Constructs a MathObject that represents a variable with the given name.
     *
     * @param name the variable name (may be null, but then object is effectively empty)
     */
    public MathObject(String name) {
        this.name = name;
        if (JParser.isNumeric(name) && !name.isEmpty()) {
            this.setValue(BigDecimal.valueOf(Double.parseDouble(name)));
        }
    }

    public MathObject(Double value) {
        this.value = BigDecimal.valueOf(value);
    }

    public MathObject(int value) {
        this.value = BigDecimal.valueOf(value);
    }

    /**
     * Constructs a MathObject that represents a numeric constant.
     *
     * @param value the numeric value to store
     */
    public MathObject(BigDecimal value) {
        this.value = value;
        JParser.normalize(this);
    }

    public String findVariable() {
        for (String s : this.name.split("")) {
            if (!JParser.isNumeric(s) && !Operator.getAsStringList().contains(s) && !s.equals("(") && !s.equals(")")) {
                return s;
            }
        }
        return null;
    }

    public String findExponent() {
        for (String s : this.name.split("")) {
            if (s.equals("^")) {
                return String.valueOf(this.name.charAt(this.name.indexOf("^") + 1));
            }
        }
        return "1";
    }

    public boolean isCharacter() {
        return this.name != null;
    }

    public MathObject getCoefficient() {
        return coefficient;
    }

    public void setCoefficient(MathObject coefficient) {
        this.coefficient = coefficient;
    }

    public MathObject getExponent() {
        return exponent;
    }

    public void setExponent(MathObject exponent) {
        this.exponent = exponent;
    }

    public void setCharAt(String s, int index) {
        if (index >= this.name.length() || index < 0) return;
        char[] chars = this.name.toCharArray();
        chars[index] = s.charAt(0);
        this.setName(String.copyValueOf(chars));
    }

    /**
     * Returns the variable name stored in this object.
     *
     * @return the variable name, or {@code null} if this object holds a numeric value
     */
    public String getName() {
        return name;
    }

    public UnaryNode.UnarySymbol getSign() {
        for (int i = 0; i < this.toString().length(); i++) {
            if (this.toString().charAt(i) == '-') {
                return UnaryNode.UnarySymbol.NEGATIVE;
            }
            if (JParser.isNumeric(String.valueOf(this.toString().charAt(i)))) {
                return UnaryNode.UnarySymbol.POSITIVE;
            }
        }
        return UnaryNode.UnarySymbol.POSITIVE;
    }

    /**
     * Returns the numeric value stored in this object.
     *
     * <p>If no numeric value is set (i.e. {@link #value} is {@code null}), this
     * method returns {@code 0.0} as a safe default.</p>
     *
     * @return the stored numeric value, or {@code 0.0} when none is set
     */
    public BigDecimal getValue() {
        return (this.value != null ? this.value : null);
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public void setValue(MathObject object) {
        this.name = object.getName();
    }

    public void setName(String name) {
        this.name = name;
    }

    public MathObject combine(MathObject object) {
        this.setName(this + object.toString());
        return this;
    }

    public MathObject combine(MathObject object, String string) {
        this.setName(this.toString() + string + object.toString());
        return this;
    }

    public static MathObject combine(MathObject object1, MathObject object2) {
        return new MathObject(object1+ "" + object2);
    }

    public static MathObject combine(MathObject object1, MathObject object2, String string) {
        return new MathObject(object1 + "" + string + "" + object2);
    }

    public MathObject operation(MathObject object, String operator) {
        Operator op = Operator.getAsString(operator);
        if (this.getValue() != null && object.getValue() != null) {
            return switch (op) {
                case MULT -> new MathObject(this.getValue().multiply(object.getValue()));
                case DIV -> new MathObject(this.getValue().divide(object.getValue(), new MathContext(JParser.getCurrentPrecision(), RoundingMode.HALF_UP)));
                case PLUS -> new MathObject(this.getValue().add(object.getValue()));
                case MINUS -> new MathObject(this.getValue().subtract(object.getValue()));
                case null, default -> null;
            };
        } else {
            return combine(this, object, operator);
        }
    }

    public MathObject operation(String operator) {
        MathObject object = operation(this, operator);
        return object;
    }

    public void addParenthesis() {
        if (this.name == null) {
            this.name = this.value.toString();
        }
        if (this.name.isEmpty()) return;

        String s = this.name;
        if (isSurroundedBySinglePair(s)) {
            return;
        }

        this.name = "(" + this.name + ")";
    }

    public void forceParenthesis() {
        if (this.name == null) {
            this.name = this.value.toPlainString();
        }
        this.name = "(" + this.name + ")";
    }

    private boolean isSurroundedBySinglePair(String s) {
        if (s.length() < 2) {
            return false;
        }
        if (s.charAt(0) != '(') {
            return false;
        }
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0 && i < s.length() - 1) return false;
            }
        }

        return depth == 0;
    }

    public MathObject removeTrailingParenthesis() {
        if (this.name == null || this.name.length() < 2 || !this.name.contains("(")) return this;
        String s = this.name;
        while (s.length() >= 2 && s.charAt(0) == '(' && s.charAt(s.length() - 1) == ')' && isSurroundedBySinglePair(s)) {
            s = s.substring(1, s.length() - 1);
        }
        this.name = s;
        return this;
    }


    /**
     * Returns a string representation of this MathObject.
     *
     * <p>If a variable name is present it is returned. Otherwise the numeric value
     * is returned as a string. If neither is present the string {@code "0.0"} is returned.</p>
     *
     * @return a human-readable representation of the object
     */
    @Override
    public String toString() {
        if (this.name != null) {
            return this.name;
        } else if (this.value != null){
            return String.valueOf(this.value);
        } else {
            return "0.0";
        }
    }
}
