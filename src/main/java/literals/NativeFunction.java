package literals;

import evaluator.Context;

public enum NativeFunction {
    COS(true),
    SIN(true),
    TAN(true),
    COT(true),
    SEC(true),
    CSC(true),
    COSH(true),
    SINH(true),
    TANH(true),
    ACOS(true),
    ASIN(true),
    ATAN(true),
    ACOT(true),
    ASEC(true),
    ACSC(true),
    CBRT(false),
    SQRT(false),
    ABS(false),
    LN(false),
    LOG(false),
    FAC(false),
    PERM(false),
    COMB(false),
    MOD(false),
    DIV(false),
    SUM(false);

    private final boolean isTrigonometric;

    NativeFunction(boolean isTrig) {
        this.isTrigonometric = isTrig;
    }

    private static NativeFunction getFromString(String name) {
        return valueOf(name.toUpperCase());
    }

    public static boolean isTrigonometric(String name) {
        return getFromString(name).isTrigonometric;
    }

    public static boolean contains(String name) {
        try {
            valueOf(name.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
