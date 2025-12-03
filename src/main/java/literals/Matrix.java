package literals;

import evaluator.JParser;
import evaluator.MatrixMath;
import nodes.*;
import parser.Parser;
import tokenizer.Tokenizer;

import java.math.BigDecimal;
import java.text.DecimalFormat;
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
public class Matrix implements Cloneable{
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
        Tokenizer tokenizer = new Tokenizer(matrix);
        Parser parser = new Parser(tokenizer.tokenize());
        if (parser.parseExpression() instanceof MatrixNode matrixNode) {
            this.columns = matrixFromMatrixNode(matrixNode);

        }
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
    public BigDecimal getValue(int row, int col) {
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
    public void setValue(int row, int col, BigDecimal value) {
        try {
            columns.get(col).setValue(row, value);
        } catch (IndexOutOfBoundsException e) {
            System.err.println("Position out of bounds");
        }
    }

    public void setColumns(List<Vector> columns) {
        this.columns = columns;
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
     * Build a Matrix from a parsed {@link MatrixNode} produced by the parser.
     *
     * @param matrixNode parsed matrix AST node
     */
    private static List<Vector> matrixFromMatrixNode(MatrixNode matrixNode) {
        DecimalFormat df = new DecimalFormat("#." +  "#".repeat(JParser.getCurrentPrecision()));
        List<Vector> vectors = new ArrayList<>();
        for (VectorNode vectorNode : matrixNode.getVectorNodeList()) {
            List<BigDecimal> body = new ArrayList<>();
            for (ExpressionNode expressionNode : vectorNode.getBody()) {
                if (expressionNode instanceof BinaryNode binaryNode) {
                    BigDecimal decimal = JParser.evaluate(binaryNode).getValue();
                    body.add(BigDecimal.valueOf(Double.parseDouble(df.format(decimal))).stripTrailingZeros());
                } else if (expressionNode instanceof LiteralNode literalNode) {
                    BigDecimal decimal = new BigDecimal(literalNode.getValue().toString());
                    body.add(decimal.stripTrailingZeros());
                } else if (expressionNode instanceof UnaryNode unaryNode) {
                    BigDecimal decimal = JParser.evaluate(unaryNode).getValue();
                    body.add(decimal.stripTrailingZeros());
                }
            }
            vectors.add(new Vector(body));
        }
        return vectors;
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

    public static Matrix createMatrix(int size) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            sb.append("[");
            sb.append("0 ".repeat(size - 1));
            sb.append("0");
            sb.append("]");
        }
        return new Matrix(sb.toString());
    }

    /**
     * Makes a clone of this matrix.
     *
     * @return a cloned matrix.
     */
    @Override
    public Matrix clone() {
        try {
            Matrix clone = (Matrix) super.clone();
            List<Vector> cols = new ArrayList<>();
            for (Vector vector : this.columns) {
                List<BigDecimal> body = new ArrayList<>();
                for (BigDecimal decimal : vector.getBody()) {
                    body.add(BigDecimal.valueOf(decimal.doubleValue()));
                }
                cols.add(new Vector(body));
            }
            clone.setColumns(cols);
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    public static Matrix createIdentityMatrix(int size) {
        Matrix matrix = createMatrix(size);
        for (int i = 0; i < size; i++) {
            matrix.setValue(i, i, new BigDecimal("1"));
        }
        return matrix;
    }
}
