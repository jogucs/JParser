# JParser
Math expression parser/scientific calculator written from scratch in Java.
# Features
**Math Functions**:
  - sin, cos, tan, cot, sec, csc sinh, cosh, tanh, asin, acos, atan, acot, asec, acsc (in radians or degrees)
  - pi, e
  - ln (natural log), log (base 10)
  - abs
  - sqrt, cbrt
  - fac, perm, comb, mod 
  - Any user defined functions

**Algebra**:
- Factorization
- Basic root finding, uses Newton-Raphsom method (currently WIP)

**Calculus**
- Differentiation
- Summation

**Matrices**:
- Row reduction
- Echelon form
- Inverse 
- Determinant
\
*Any matrix operation will return a new matrix without modifying the original one.

# Examples
**Matrix Math**
  ```
  Matrix matrix = new Matrix("[1 3 5][8 30 2][1 89 2]");
  JParser.makeTriangular(matrix); ->
    [1.0 8.0 1.0 ]
    [0.0 6.0 86.0 ]
    [0.0 0.0 541.6666666666666 ]
  JParser.rowReduce(matrix); ->
    [1.0 0.0 0.0 ]
    [0.0 1.0 0.0 ]
    [0.0 0.0 1.0 ]
  ```
**User Defined Functions**
  ```
    String func = "f(x, y, z) = x^2 + 2z^3 - 8.2y^4";
    JParser.createFunction(func); -> can be used in any context now.
    JParser.evaluate("f(3, 5, 9)"); -> returns -4954.0
  ```
