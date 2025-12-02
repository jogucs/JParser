package evaluator;

import misc.Matrix;
import misc.Vector;

import java.math.RoundingMode;
import java.text.DecimalFormat;

public abstract class MatrixMath {
    /**
     * Reduce the matrix to echelon form (not necessarily reduced row-echelon).
     *
     * <p>
     * This method performs an in-place transformation and returns the same instance for convenience.
     * The algorithm walks rows top-to-bottom and finds pivots, swapping and scaling rows as needed.
     * </p>
     *
     * @param matrix the matrix to reduce
     * @return the same matrix instance in echelon form (or partially reduced)
     */
    public static Matrix reduceToEchelon(Matrix matrix) {
        int colIndex = 0;

        int rowSize = matrix.getRowSize();
        int colSize = matrix.getColSize();

        int rowToSwap;
        int pivotPos;

        for (int rowIndex = 0; rowIndex < rowSize; rowIndex++) {

            rowToSwap = rowIndex;
            if (colIndex > colSize - 1) {
                break;
            }
            format(matrix, rowToSwap, colIndex, rowIndex);

            rowSwap(matrix, rowIndex, rowToSwap);
            pivotPos = getPivotColumn(matrix, rowIndex);
            int idx = rowIndex + 1;
            while (isNonZeroColumn(matrix, pivotPos, rowIndex + 1)) {
                double scalar = matrix.getValue(idx, pivotPos) / matrix.getValue(rowIndex, pivotPos);
                rowAddScale(matrix, rowIndex, idx, -1 * scalar);
                idx++;
            }
            colIndex++;
        }
        prettify(matrix);
        return matrix;
    }

    /**
     * Perform full row reduction (Gaussian elimination to reduced row-echelon form).
     *
     * @param matrix the matrix to row-reduce (in-place)
     * @return the same matrix instance after row reduction
     */
    public static Matrix rowReduce(Matrix matrix) {
        int columnIndex = 0;
        int cursor;

        int rowSize = matrix.getRowSize();
        int colSize = matrix.getColSize();

        for (int rowIndex = 0; rowIndex < rowSize; rowIndex++) {
            if (colSize <= columnIndex) {
                break;
            }
            cursor = rowIndex;
            format(matrix, cursor, columnIndex, rowIndex);
            rowSwap(matrix, cursor, rowIndex);
            if (!isZero(matrix.getValue(rowIndex, columnIndex))) {
                rowScale(matrix, rowIndex, (1/matrix.getValue(rowIndex, columnIndex)));
            }
            for (cursor = 0; cursor < rowSize; cursor++) {
                if (cursor != rowIndex) {
                    rowAddScale(matrix, rowIndex, cursor, ((-1) * matrix.getValue(cursor, columnIndex)));
                }
            }columnIndex++;
        }
        prettify(matrix);
        return matrix;
    }

    /**
     * Swap two rows of the matrix in-place.
     *
     * @param matrix   the matrix to modify
     * @param rowIndex first row index
     * @param rowIndex2 second row index
     */
    private static void rowSwap(Matrix matrix, int rowIndex, int rowIndex2) {
        int numCols = matrix.getColSize();

        double hold;

        for (int k = 0; k < numCols; k++) {
            hold = matrix.getValue(rowIndex2, k);
            matrix.setValue(rowIndex2, k, matrix.getValue(rowIndex, k));
            matrix.setValue(rowIndex, k, hold);
        }
    }

    /**
     * Add one row to another (rowIndex + rowIndex2) storing result in rowIndex.
     *
     * @param matrix the matrix to modify
     * @param rowIndex source row index
     * @param rowIndex2 destination row index (receives the sum)
     * @return the modified matrix
     */
    private static Matrix rowAdd(Matrix matrix, int rowIndex, int rowIndex2) {
        int numCols = matrix.getColSize();

        double hold;

        for (int k = 0; k < numCols; k++) {
            hold = matrix.getValue(rowIndex2, k) + matrix.getValue(rowIndex, k);

            matrix.setValue(rowIndex, k, hold);
        }
        return matrix;
    }

    /**
     * Scale a row by a scalar value.
     *
     * @param matrix the matrix to modify
     * @param rowIndex row to scale
     * @param scalar  scalar multiplier
     */
    private static void rowScale(Matrix matrix, int rowIndex, double scalar) {
        int numCols = matrix.getColSize();

        for (int k = 0; k < numCols; k++) {;
            matrix.setValue(rowIndex, k, matrix.getValue(rowIndex, k) * scalar);
        }
    }

    /**
     * Add a scaled version of one row to another: rowIndex2 += rowIndex * scalar.
     *
     * @param matrix the matrix to modify
     * @param rowIndex source row index (scaled)
     * @param rowIndex2 destination row index (receives addition)
     * @param scalar multiplier applied to source row
     */
    private static void rowAddScale(Matrix matrix, int rowIndex, int rowIndex2, double scalar) {
        int numCols = matrix.getColSize();

        double hold;

        for (int k = 0; k < numCols; k++) {
            hold = matrix.getValue(rowIndex2, k) + matrix.getValue(rowIndex, k) * scalar;
            matrix.setValue(rowIndex2, k, hold);
        }

    }


    /**
     * Determine whether a value should be considered zero (with epsilon tolerance).
     *
     * @param val numeric value to test
     * @return true if value is (approximately) zero
     */
    private static boolean isZero(double val) {
        return Math.abs(val)<0.00001;
    }

    /**
     * Check whether the matrix is in echelon form.
     *
     * @param matrix matrix to inspect
     * @return true if matrix is in echelon form
     */
    private static boolean isEchelon(Matrix matrix) {
        boolean seenZeroRow = false;
        int currentPivot = 0;
        int previousPivot = -1;
        for (int row = 0; row < matrix.getRowSize(); row++) {
            boolean nonZero = isNonZeroRow(matrix, row);
            if (!nonZero) {
                if (!seenZeroRow) {
                    seenZeroRow = true;
                    continue;
                }
            }
            if (nonZero && seenZeroRow) {
                return false;
            }

            if (nonZero) {
                currentPivot = getPivotColumn(matrix, row);
            }
            if (currentPivot <= previousPivot) {
                return false;
            }

            previousPivot = currentPivot;

            if (isNonZeroColumn(matrix, currentPivot, row)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a row contains any non-zero entry.
     *
     * @param matrix matrix to inspect
     * @param row row index
     * @return true if any entry in the row is non-zero
     */
    private static boolean isNonZeroRow(Matrix matrix, int row) {
        for (int col = 0; col < matrix.getColSize(); col++) {
            if (!isZero(matrix.getValue(row, col))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check whether a column has a non-zero entry at or below a starting row.
     *
     * @param matrix matrix to inspect
     * @param col column index
     * @param startingRow starting row index
     * @return true if column contains non-zero entries below startingRow
     */
    private static boolean isNonZeroColumn(Matrix matrix, int col, int startingRow) {
        if (col > matrix.getColSize() || col < 0) {
            return true;
        }
        for (int row = startingRow; row < matrix.getRowSize(); row++) {
            if (!isZero(matrix.getValue(row, col))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find the pivot column index for a given row (first non-zero column).
     *
     * @param matrix matrix to inspect
     * @param row row index
     * @return pivot column index or -1 if none
     */
    private static int getPivotColumn(Matrix matrix, int row) {
        if (!isNonZeroRow(matrix, row)) {
            return -1;
        }

        for (int col = 0; col < matrix.getColSize(); col++) {
            if (!isZero(matrix.getValue(row, col))) {
                return col;
            }
        }
        return -1;
    }

    private static void format(Matrix matrix, int cursor, int columnIndex, int rowIndex) {
        int rowSize = matrix.getRowSize();
        int colSize = matrix.getColSize();
        while (isZero(matrix.getValue(cursor, columnIndex))) {
            cursor++;
            if (rowSize == cursor) {
                cursor = rowIndex;
                columnIndex++;
                if (colSize == columnIndex) {
                    break;
                }
            }
        }
        rowSwap(matrix, cursor, rowIndex);
    }

    /**
     * Format entries in a Matrix by rounding them up to 4 decimal places.
     *
     * @param matrix matrix to format.
     */
    private static void prettify(Matrix matrix) {
        DecimalFormat df = new DecimalFormat("#.#####");
        df.setRoundingMode(RoundingMode.UP);
        int idx = 0;
        for (Vector vector : matrix.getColumns()) {
            if (isNonZeroColumn(matrix, idx, 0)) {
                for (Double d : vector.getBody()) {
                    vector.setValue(idx, Double.parseDouble(df.format(d)));
                    idx++;
                }
            }
            idx = 0;
        }
    }
}
