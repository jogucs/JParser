package evaluator;

import misc.Matrix;
import misc.Vector;

import java.math.BigDecimal;
import java.math.MathContext;
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
     * @param matrixToTransform the matrix to reduce
     * @return the same matrix instance in echelon form (or partially reduced)
     */
    public static Matrix reduceToEchelon(Matrix matrixToTransform) {
        Matrix matrix = matrixToTransform.clone();
        MathContext mc = new MathContext(JParser.getCurrentPrecision(), RoundingMode.HALF_UP) ;
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
            pivotPos = getPivotColumn(matrix, rowIndex);
            int idx = rowIndex + 1;
            while (isNonZeroColumn(matrix, pivotPos, rowIndex + 1)) {
                BigDecimal scalar = matrix.getValue(idx, pivotPos).divide(matrix.getValue(rowIndex, pivotPos), mc);
                rowAddScale(matrix, rowIndex, idx, scalar.multiply(BigDecimal.valueOf(-1)));
                idx++;
                if (idx >= rowSize) {
                    break;
                }
            }
            colIndex++;
        }
        prettify(matrix);
        return matrix;
    }

    /**
     * Perform full row reduction (Gaussian elimination to reduced row-echelon form).
     *
     * @param matrixToTransform the matrix to row-reduce (in-place)
     * @return the same matrix instance after row reduction
     */
    public static Matrix rowReduce(Matrix matrixToTransform) {
        Matrix matrix = matrixToTransform.clone();
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
            System.out.println(matrix);
            if (!JParser.isZero(matrix.getValue(rowIndex, columnIndex))) {
                rowScale(matrix, rowIndex, BigDecimal.valueOf((1/matrix.getValue(rowIndex, columnIndex).doubleValue())));
            }
            for (cursor = 0; cursor < rowSize; cursor++) {
                if (cursor != rowIndex) {
                    rowAddScale(matrix, rowIndex, cursor, (matrix.getValue(cursor, columnIndex).multiply(BigDecimal.valueOf(-1))));
                }
            }columnIndex++;
        }
        prettify(matrix);
        return matrix;
    }


    public static Matrix findInverse(Matrix matrixToFindInverse) {
        Matrix matrix = matrixToFindInverse.clone();

        int colSize = matrix.getColSize();
        int rowSize = matrix.getRowSize();

        if (colSize != rowSize) {
            throw new RuntimeException("Unable to invert non-square matrix");
        }

        Matrix identity = Matrix.createIdentityMatrix(colSize);
        Matrix inverse = rowReduce(addMatricesTogether(matrixToFindInverse, identity));
        for (int i = 0; i < colSize; i++) {
            inverse.removeVector(inverse.getColumns().removeFirst());
        }
        return inverse;
    }

    private static Matrix addMatricesTogether(Matrix m1, Matrix m2) {
        if (m1.getRowSize() != m2.getRowSize()) {
            throw new RuntimeException("Two matrices provided do not have same row size");
        }

        Matrix newMatrix = new Matrix();
        for (Vector v : m1.getColumns()) {
            newMatrix.addVector(v);
        }

        for (Vector v : m2.getColumns()) {
            newMatrix.addVector(v);
        }

        return newMatrix;
    }

    public static BigDecimal findDeterminant(Matrix matrixToFindDeterminant) {

        BigDecimal determinant = new BigDecimal("1.0");
        Matrix echelon = reduceToEchelon(matrixToFindDeterminant);
        for (int i = 0; i < matrixToFindDeterminant.getColSize(); i++) {
            determinant = determinant.multiply(echelon.getValue(i, i));
        }
        return BigDecimal.valueOf(Double.parseDouble(new DecimalFormat("#.#####").format(determinant.doubleValue()))).stripTrailingZeros();
    }

    public static Matrix deleteRowCol(Matrix matrixToTransform, int row, int col) {
        if (row >= matrixToTransform.getRowSize() || col >= matrixToTransform.getColSize() || row < 0 || col < 0) {
            throw new RuntimeException("Invalid row/column to remove");
        }
        Matrix matrix = Matrix.createMatrix(matrixToTransform.getColSize() - 1);
        int newRowIdx = 0;
        int newColIdx = 0;
        for (int i = 0; i < matrixToTransform.getRowSize(); i++) {
            if (i == row) continue;
            for (int j = 0; j < matrixToTransform.getColSize(); j++) {
                if (j == col) continue;
                matrix.setValue(newRowIdx, newColIdx, matrixToTransform.getValue(i, j));
                newColIdx++;
            }
            newColIdx = 0;
            newRowIdx++;
        }
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

        BigDecimal hold;

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

        BigDecimal hold;

        for (int k = 0; k < numCols; k++) {
            hold = matrix.getValue(rowIndex2, k).add(matrix.getValue(rowIndex, k));

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
    private static void rowScale(Matrix matrix, int rowIndex, BigDecimal scalar) {
        int numCols = matrix.getColSize();

        for (int k = 0; k < numCols; k++) {;
            matrix.setValue(rowIndex, k, matrix.getValue(rowIndex, k).multiply(scalar));
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
    private static void rowAddScale(Matrix matrix, int rowIndex, int rowIndex2, BigDecimal scalar) {
        int numCols = matrix.getColSize();

        BigDecimal hold;

        for (int k = 0; k < numCols; k++) {
            hold = matrix.getValue(rowIndex2, k).add(matrix.getValue(rowIndex, k).multiply(scalar));
            matrix.setValue(rowIndex2, k, hold);
        }

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
            if (!JParser.isZero(matrix.getValue(row, col))) {
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
            if (!JParser.isZero(matrix.getValue(row, col))) {
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
            if (!JParser.isZero(matrix.getValue(row, col))) {
                return col;
            }
        }
        return -1;
    }

    private static void format(Matrix matrix, int cursor, int columnIndex, int rowIndex) {
        int rowSize = matrix.getRowSize();
        int colSize = matrix.getColSize();
        while (JParser.isZero(matrix.getValue(cursor, columnIndex))) {
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
        DecimalFormat df = new DecimalFormat("#.######");
        df.setRoundingMode(RoundingMode.UP);
        int idx = 0;
        for (Vector vector : matrix.getColumns()) {
            if (isNonZeroColumn(matrix, idx, 0)) {
                for (BigDecimal d : vector.getBody()) {
                    if (JParser.isZero(d)) {
                        vector.setValue(idx, BigDecimal.valueOf(Long.parseLong(df.format(0))));
                    } else {
                        vector.setValue(idx, BigDecimal.valueOf(Double.parseDouble(df.format(d))).stripTrailingZeros());
                    }
                    idx++;
                }
            }
            idx = 0;
        }
    }
}
