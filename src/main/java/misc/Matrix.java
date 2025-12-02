package misc;

import nodes.ExpressionNode;
import nodes.LiteralNode;
import nodes.MatrixNode;
import nodes.VectorNode;
import parser.Parser;
import tokenizer.Tokenizer;

import java.util.ArrayList;
import java.util.List;

/**
 * Matrix provides a simple column-oriented matrix representation and common
 * matrix operations such as conversion from a parsed matrix literal,
 * row-reduction and reduction to echelon form.
 *
 * <p>
 * Internally the matrix stores a list of column vectors. Many operations
 * are implemented as static helpers that operate on a {@code Matrix} instance.
 * This class is intentionally lightweight and assumes numeric (double) entries.
 * </p>
 */
public class Matrix {
    /**
     * Columns of the matrix; each column is represented as a {@link Vector}.
     */
    private List<Vector> columns = new ArrayList<>();

    /**
     * Construct a matrix from an existing list of column vectors.
     *
     * @param columns the column vectors that form the matrix
     * @throws RuntimeException if vectors have inconsistent sizes
     */
    public Matrix(List<Vector> columns) {
        int colSize = columns.getFirst().getSize(); // expects first column size as row count
        for (Vector vector : columns) {
            if (vector.getSize() > colSize) {
                throw new RuntimeException("Invalid matrix size");
            }
        }
        this.columns = columns;
    }

    /**
     * Construct a matrix by parsing a textual matrix literal.
     *
     * <p>
     * The input should be a matrix representation compatible with the parser/tokenizer,
     * e.g. "[[1,2],[3,4]]" depending on the language grammar.
     * </p>
     *
     * @param matrix textual representation of a matrix
     */
    public Matrix(String matrix) {
        Parser parser = new Parser(new Tokenizer(matrix).tokenize());
        this.columns = createMatrix((MatrixNode) parser.parseExpression());
    }

    /** Default empty matrix constructor. */
    public Matrix() {}

    /** Return the list of column vectors. */
    public List<Vector> getColumns() {
        return columns;
    }

    /**
     * Get the value stored at a specific row and column.
     *
     * @param row zero-based row index
     * @param col zero-based column index
     * @return the value at the specified position
     */
    public Double getValue(int row, int col) {
        return columns.get(col).getBody().get(row);
    }

    /**
     * Set the value at a specific row and column.
     *
     * <p>
     * Out-of-bounds attempts are caught and a message is printed to stderr.
     * </p>
     *
     * @param row   zero-based row index
     * @param col   zero-based column index
     * @param value new value to set
     */
    public void setValue(int row, int col, double value) {
        try {
            columns.get(col).setValue(row, value);
        } catch (IndexOutOfBoundsException e) {
            System.err.println("Position out of bounds");
        }
    }

    /** Append a column vector to the matrix. */
    public void addVector(Vector vector) {
        columns.add(vector);
    }

    /** Remove a column vector from the matrix. */
    public void removeVector(Vector vector) {
        columns.remove(vector);
    }

    /**
     * Get the number of rows in the matrix.
     *
     * @return number of rows (based on the first column)
     */
    public int getRowSize() {
        return columns.getFirst().getSize();
    }

    /**
     * Get the number of columns in the matrix.
     *
     * @return number of columns
     */
    public int getColSize() {
        return columns.size();
    }

    /**
     * Return the left-most non-zero column vector or null if all-zero.
     *
     * @return first non-zero column or {@code null}
     */
    public Vector getLeftNonZero() {
        for (Vector vector : columns) {
            if (!vector.isZero()) {
                return vector;
            }
        }
        return null;
    }

    /**
     * Human-readable representation of the matrix in row-major textual form.
     *
     * @return string with each row on a new line
     */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < this.getRowSize(); i++) {
            str.append("[");
            for (int j = 0; j < this.getColSize(); j++) {
                str.append(getValue(i, j));
                str.append(" ");
            }
            str.append("]");
            str.append("\n");
        }
        return str.toString();
    }

    /**
     * Build a Matrix from a parsed {@link MatrixNode} produced by the parser.
     *
     * @param matrixNode parsed matrix AST node
     * @return list of column {@link Vector}s
     * @throws RuntimeException if the parsed vectors have inconsistent sizes
     */
    private static List<Vector> createMatrix(MatrixNode matrixNode) {
        List<Vector> cols = new ArrayList<>();
        for (VectorNode vec : matrixNode.getVectorNodeList()) {
            List<Double> body = new ArrayList<>();
            for (ExpressionNode node : vec.getBody()) {
                if (node instanceof LiteralNode lit) {
                    body.add((Double) lit.getValue());
                }
            }
            cols.add(new Vector(body));
        }
        int colSize = cols.getFirst().getSize();
        for (Vector vector : cols) {
            if (vector.getSize() != colSize) {
                throw new RuntimeException("Invalid matrix size");
            }
        }
        return cols;
    }
}
