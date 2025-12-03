package literals;

import evaluator.JParser;
import nodes.ExpressionNode;
import nodes.VectorNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple container for a numeric vector (ordered list of doubles) with basic
 * mutation and inspection operations.
 *
 * <p>This class stores the vector elements in a mutable {@link List} of
 * {@link BigDecimal}. It provides methods to set or append values, query the size
 * and access the underlying list, and to check whether all elements are zero.
 * Note: {@link #isZero()} uses exact equality against 0.0; consider using an
 * epsilon comparison for floating-point tolerance if needed.</p>
 */
public class Vector {
    /**
     * Underlying list of elements comprising the vector.
     */
    private List<BigDecimal> body = new ArrayList<>();

    public Vector(List<BigDecimal> body) {
        this.body = body;
    }

    /**
     * Replace the value at the given index.
     *
     * @param pos   zero-based index of the element to replace
     * @param value new value to set
     * @throws IndexOutOfBoundsException if {@code pos} is out of range for the list
     */
    public void setValue(int pos, BigDecimal value) {
        this.body.set(pos, value);
    }

    /**
     * Append a value to the end of the vector.
     *
     * @param value value to append
     */
    public void addValue(double value) {
        this.body.add(BigDecimal.valueOf(value));
    }

    /**
     * Return the number of elements in the vector.
     *
     * @return size of the vector
     */
    public int getSize() {
        return body.size();
    }

    /**
     * Return the underlying list representing the vector body.
     *
     * <p>Note: the returned list is the actual internal list (no defensive copy),
     * so callers modifying it will affect this Vector instance.</p>
     *
     * @return list of {@link Double} elements
     */
    public List<BigDecimal> getBody() {
        return body;
    }

    /**
     * Check whether all elements in the vector are exactly zero.
     *
     * @return {@code true} if every element equals {@code 0.0}, {@code false} otherwise
     */
    public boolean isZero() {
        for (BigDecimal doub : body) {
            if (JParser.isZero(doub)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < this.body.size(); i++) {
            sb.append("[");
            sb.append(this.body.get(i));
            sb.append("]\n");
        }
        return sb.toString();
    }
}
