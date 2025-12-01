# JParser
Math expression parser and evaluator, written from scratch in Java.\
\
This algorithm goes through 3 stages:
1. Tokenizing: transforms the expression into a token list to feed into the parser.
2. Parsing: (recursive descent)[https://en.wikipedia.org/wiki/Recursive_descent_parser] goes through token list and forms an AST.
3. Evaluating: the actual math to calculate an expression.
# Features
**Math Functions**:
  - sin, cos, tan, sinh, cosh, tanh, asin, acos, atan
  - pi, e
  - ln (natural log), log (base 10)
  - abs
  - sqrt, cbrt
  - Radians/degrees
  - Any user defined functions\

**Matrices**:
- Row reduction
- Echelon form

# Examples
**Matrix Math**
  ```
  Matrix matrix = new Matrix("[1 3 5][8 30 2][1 89 2]");
  JParser.echelonForm(matrix); ->
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
