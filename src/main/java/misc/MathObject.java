package misc;

import java.math.BigDecimal;

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

    /**
     * Constructs a MathObject that represents a variable with the given name.
     *
     * @param name the variable name (may be null, but then object is effectively empty)
     */
    public MathObject(String name) {
        this.name = name;
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
    }

    /**
     * Returns the variable name stored in this object.
     *
     * @return the variable name, or {@code null} if this object holds a numeric value
     */
    public String getName() {
        return name;
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
