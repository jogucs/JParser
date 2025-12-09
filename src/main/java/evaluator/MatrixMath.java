package evaluator;

import literals.MathObject;
import literals.Matrix;
import literals.Vector;
import nodes.ExpressionNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.List;

public abstract class MatrixMath {
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

        format(matrix);
        for (int rowIndex = 0; rowIndex < rowSize; rowIndex++) {
            if (colSize <= columnIndex) {
                break;
            }
            if (!JParser.isZero(matrix.getValue(rowIndex, columnIndex))) {
                rowScale(matrix, rowIndex, BigDecimal.valueOf((1/matrix.getValue(rowIndex, columnIndex).getValue().doubleValue())));
            }
            for (cursor = 0; cursor < rowSize; cursor++) {
                if (cursor != rowIndex) {
                    rowAddScale(matrix, rowIndex, cursor, (matrix.getValue(cursor, columnIndex).getValue().multiply(BigDecimal.valueOf(-1))));
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

        if (JParser.isZero(findDeterminant(matrix))) {
            throw new RuntimeException("Cannot find inverse of matrix with determinant = 0");
        }

        Matrix identity = Matrix.createIdentityMatrix(colSize);
        Matrix inverse = rowReduce(addMatricesTogether(matrixToFindInverse, identity));
        for (int i = 0; i < colSize; i++) {
            inverse.removeVector(inverse.getColumns().removeFirst());
        }
        return inverse;
    }

    public static Matrix makeTriangular(Matrix matrixToTransform) {
        Matrix matrix = matrixToTransform.clone();
        int rowSize = matrix.getRowSize();
        int colSize = matrix.getColSize();
        format(matrix);
        for (int colIdx = 0; colIdx < colSize; colIdx++) {
            for (int rowIdx = rowSize - 1; rowIdx > colIdx; rowIdx--) {
                if (JParser.isZero(matrix.getValue(rowIdx, colIdx))) {
                    continue;
                }
                MathObject x = matrix.getValue(rowIdx, colIdx);
                MathObject y = matrix.getValue(rowIdx - 1, colIdx);
                if (x.getValue() != null && y.getValue() != null) {
                    BigDecimal xVal = x.getValue();
                    BigDecimal yVal = y.getValue();
                    rowScale(matrix, rowIdx, yVal.multiply(JParser.NEGATIVE_ONE).divide(xVal, 10, RoundingMode.HALF_UP));
                    rowAdd(matrix, rowIdx, rowIdx - 1);
                    rowScale(matrix, rowIdx, xVal.multiply(JParser.NEGATIVE_ONE).divide(yVal, 10, RoundingMode.HALF_UP));
                }
            }
        }
        prettify(matrix);
        return matrix;
    }

    private static boolean isUpperTriangular(Matrix matrix) {
        if (matrix.getRowSize() < 2) {
            return false;
        }

        for (int i = 0; i < matrix.getRowSize(); i++) {
            for (int j = 0; j < i; j++) {
                if (!JParser.isZero(matrix.getValue(i, j))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isLowerTriangle(Matrix matrix) {
        if (matrix.getRowSize() < 2) {
            return false;
        }

        for (int i = 0; i < matrix.getRowSize(); i++) {
            for (int j = 0; i > j; j++) {
                if (JParser.isZero(matrix.getValue(j, i))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void format(Matrix matrix) {
        int rowSize = matrix.getRowSize();
        int cursor;
        int pivotPos;
        for (int rowIdx = 0; rowIdx < rowSize; rowIdx++) {
            cursor = rowIdx;
            pivotPos = getPivotColumn(matrix, rowIdx);
            while (pivotPos > 0) {
                cursor++;
                if (cursor < rowSize) {
                    pivotPos = getPivotColumn(matrix, cursor);
                } else {
                    cursor--;
                    break;
                }
            }
            rowSwap(matrix, rowIdx, cursor);
        }
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

    public static MathObject findCharacteristicPolynomial(Matrix matrixToFind) {
        Matrix matrix = matrixToFind.clone();
        int colSize = matrix.getColSize();
        int rowSize = matrix.getRowSize();

        if (colSize != rowSize) {
            throw new RuntimeException("Unable to find characteristic polynomial for non-square matrix");
        }
        for (int i = 0; i < colSize; i++) {
            matrix.setValue(i, i, new MathObject(matrix.getValue(i, i) + "-λ"));
        }
        MathObject object = findDeterminantLaplace(matrix);
        object.setName(object.getName().substring(0, object.getName().length() - 1));
        return JParser.evaluate(object.toString());
    }

    public static Vector findAxb(Matrix matrix, Vector b) {
        Matrix bAsMatrix = new Matrix(List.of(b));
        Matrix added = addMatricesTogether(matrix, bAsMatrix);
        return rowReduce(added).getColumns().getLast();
    }

    public static MathObject findDeterminant(Matrix matrixToFindDeterminant) {

        MathObject determinant = new MathObject("1");
        MathObject scalar;
        Matrix echelon = makeTriangular(matrixToFindDeterminant);
        for (int i = 0; i < matrixToFindDeterminant.getColSize(); i++) {
            scalar = echelon.getValue(i, i);
            scalar.forceParenthesis();
            determinant = determinant.operation(scalar, "*");
        }
        return determinant;
    }

    public static MathObject findDeterminantLaplace(Matrix matrixToFindDeterminate, int... colIdx) {
        int colSize = matrixToFindDeterminate.getColSize();
        int rowSize = matrixToFindDeterminate.getRowSize();
        if (colIdx.length == 0) {
            colIdx = new int[]{0};
        }
        if (colIdx[0] == colSize) {
            return new MathObject("");
        }
        if (colSize != rowSize) {
            throw new RuntimeException("Unable to find determinate for non-square matrix");
        }

        MathObject determinant;
        MathObject currentScalar = matrixToFindDeterminate.getValue(0, colIdx[0]);
        MathObject ad;
        MathObject bc;
        MathObject ad_bc;
        Matrix matrix = deleteRowCol(matrixToFindDeterminate, 0, colIdx[0]);
        if (matrix.getColSize() == 2) {
            MathObject a = matrix.getValue(0, 0);
            MathObject d = matrix.getValue(1, 1);
            ad = new MathObject("(" + a + ") * (" + d + ")");
            MathObject b = matrix.getValue(0, 1);
            MathObject c = matrix.getValue(1, 0);
            bc = new MathObject("(" + b + ") * (" + c + ")");
            ad_bc = ad;
            ad_bc = ad_bc.operation(bc, "-");
            ad_bc.addParenthesis();
            currentScalar.addParenthesis();
            currentScalar = currentScalar.operation(ad_bc, "*");
            if (colIdx[0] - 1 % 2 == 0) {
                currentScalar = currentScalar.operation(findDeterminantLaplace(matrixToFindDeterminate, colIdx[0] + 1), "+");
            } else {
                currentScalar = currentScalar.operation(findDeterminantLaplace(matrixToFindDeterminate, colIdx[0] + 1), "-");
            }
        } else {
            currentScalar = currentScalar.operation(findDeterminantLaplace(deleteRowCol(matrixToFindDeterminate, 0, colIdx[0]), colIdx[0] + 1), "*");
        }

        determinant = currentScalar;
        return determinant;
    }

    private static MathObject cofactor(Matrix matrix, int row, int col) {
        return findDeterminant(deleteRowCol(matrix, row, col));
    }

    private static Matrix multiplyMatrix(Matrix matrix, BigDecimal scalar) {
        for (Vector vector : matrix.getColumns()) {
            for (MathObject object : vector.getBody()) {
                object.setValue(object.getValue().multiply(scalar));
            }
        }
        return matrix;
    }

    private static Matrix deleteRowCol(Matrix matrixToTransform, int row, int col) {
        if (row >= matrixToTransform.getRowSize() || col >= matrixToTransform.getColSize() || row < 0 || col < 0) {
            throw new RuntimeException("Invalid row/column to remove");
        }
        if (matrixToTransform.getColSize()-1 < 1) {
            return matrixToTransform;
        }
        Matrix matrix = Matrix.createMatrix(matrixToTransform.getColSize() - 1);
        int newRowIdx = 0;
        int newColIdx = 0;
        for (int i = 0; i < matrixToTransform.getRowSize(); i++) {
            if (i == row) continue;
            for (int j = 0; j < matrixToTransform.getColSize(); j++) {
                if (j == col) continue;
                if (matrixToTransform.getValue(i, j).getValue() == null) {
                    matrix.setValue(newRowIdx, newColIdx, matrixToTransform.getValue(i, j, 0));
                } else {
                    matrix.setValue(newRowIdx, newColIdx, matrixToTransform.getValue(i, j));
                }
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

        MathObject hold;

        for (int k = 0; k < numCols; k++) {
            hold = matrix.getValue(rowIndex2, k, 0);
            matrix.setValue(rowIndex2, k, matrix.getValue(rowIndex, k, 0));
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
            hold = matrix.getValue(rowIndex2, k).getValue().add(matrix.getValue(rowIndex, k).getValue());

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

            matrix.setValue(rowIndex, k, matrix.getValue(rowIndex, k).getValue().multiply(scalar));
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
            hold = matrix.getValue(rowIndex2, k).getValue().add(matrix.getValue(rowIndex, k).getValue().multiply(scalar));
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
            if (matrix.getValue(row, col) != null || matrix.getValue(row, col, 0).getName() != null) {
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

    /**
     * Format entries in a Matrix by rounding them up to 4 decimal places.
     *
     * @param matrix matrix to format.
     */
    private static void prettify(Matrix matrix) {
        DecimalFormat df = new DecimalFormat("#.######");
        df.setRoundingMode(RoundingMode.HALF_UP);
        int idx = 0;
        for (Vector vector : matrix.getColumns()) {
            if (isNonZeroColumn(matrix, idx, 0)) {
                for (MathObject d : vector.getBody()) {
                    if (JParser.isZero(d)) {
                        vector.setValue(idx, BigDecimal.valueOf(Long.parseLong(df.format(0))));
                    } else if (d.getValue() != null) {
                        vector.setValue(idx, BigDecimal.valueOf(Double.parseDouble(df.format(d.getValue()))).stripTrailingZeros());
                    } else {
                        vector.setValue(idx, d);
                    }
                    idx++;
                }
            }
            idx = 0;
        }
    }
}
