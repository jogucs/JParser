package evaluator;

import literals.FunctionDefinition;
import literals.MathObject;
import nodes.ExpressionNode;
import nodes.FunctionDefinitionNode;
import parser.Parser;
import tokenizer.Tokenizer;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

/**
 * Evaluation context that holds variable values, user-defined functions, and native functions.
 *
 * <p>
 * An instance of {@code EvalContext} is used during expression evaluation to resolve variable
 * names, look up function definitions, and call built-in ("native") functions. Contexts can
 * form a parent/child relationship so that functions and native functions are inherited while
 * allowing local variables to be shadowed in child contexts (used when evaluating user-defined
 * function bodies).
 * </p>
 */
public class Context {
    /**
     * Map of variable names to numeric values available in this context.
     * Pre-populated with mathematical constants.
     */
    public Map<String, Double> variables = new HashMap<>()
    {{
        put("e", 2.718281828459045235360287471352);
        put("pi", 3.1415926535897932384626433);
    }};

    /**
     * Map of user-defined function names to their {@link FunctionDefinition} representations.
     * Functions are added by parsing a function definition expression and stored here.
     */
    public Map<String, FunctionDefinition> functions = new HashMap<>();

    /**
     * Map of native (built-in) function names to their implementations.
     * Each implementation is a function that accepts a double\[\] of arguments and returns a Double.
     *
     * <p>
     * Example native functions: cos, sin, tan, sqrt, ln, log, etc.
     * The {@code int} function here is a placeholder that calls {@link #integral(double, double)}.
     * </p>
     */
    public Map<String, Function<BigDecimal[], BigDecimal>> nativeFunctions = new HashMap<>() {{
        put("cos", args -> BigDecimal.valueOf(Math.cos(args[0].doubleValue())));
        put("sin", args -> BigDecimal.valueOf(Math.sin((args[0].doubleValue()))));
        put("tan", args -> BigDecimal.valueOf(Math.tan(args[0].doubleValue())));
        put("tanh", args -> BigDecimal.valueOf(Math.tanh(args[0].doubleValue())));
        put("sinh", args -> BigDecimal.valueOf(Math.sinh(args[0].doubleValue())));
        put("cosh", args -> BigDecimal.valueOf(Math.cosh(args[0].doubleValue())));
        put("asin", args -> BigDecimal.valueOf(Math.asin(args[0].doubleValue())));
        put("acos", args -> BigDecimal.valueOf(Math.acos(args[0].doubleValue())));
        put("atan", args -> BigDecimal.valueOf(Math.atan(args[0].doubleValue())));
        put("cbrt", args -> BigDecimal.valueOf(Math.cbrt(args[0].doubleValue())));
        put("sqrt", args -> BigDecimal.valueOf(Math.sqrt(args[0].doubleValue())));
        put("abs", args -> BigDecimal.valueOf(Math.abs(args[0].doubleValue())));
        put("ln", args -> BigDecimal.valueOf(Math.log(args[0].doubleValue())));
        put("log", args -> BigDecimal.valueOf(Math.log10(args[0].doubleValue())));
        put("fac", args -> factorial(args[0]));
        put("perm", args -> permutation(args[0], args[1]));
        put("comb", args -> combination(args[0], args[1]));
        put("mod", args -> mod(args[0], args[1]));
        put("div", args -> div(args[0], args[1]));
    }};

    /** Optional parent context. When present, functions are inherited from the parent. */
    public Context parent;

    /** Create an empty root evaluation context. */
    public Context() {}

    /**
     * Create a child context that inherits functions and native functions from {@code parent}.
     *
     * <p>
     * The constructor copies user-defined functions from the parent into the new context so that
     * functions are visible while allowing the child to bind its own local variables (e.g. when
     * evaluating a function body with parameter names bound to argument values).
     * </p>
     *
     * @param parent the parent context to inherit functions from
     */
    public Context(Context parent) {
        this.parent = parent;
        // Copy user-defined functions so child can modify its own function map independently.
        for (FunctionDefinition functionDefinition : parent.functions.values()) {
            this.functions.put(functionDefinition.getName(), functionDefinition);
        }
        // Inherit native functions.
        this.nativeFunctions.putAll(parent.nativeFunctions);
    }

    /**
     * Parse and add a user-defined function to this context.
     *
     * <p>
     * The input {@code func} should be a function definition string, for example:
     * {@code "f(x,y)=x^2+y"} or {@code "g(x)=sin(x)"}.
     * This method tokenizes and parses the string and, if it yields a {@link FunctionDefinitionNode},
     * converts it to a {@link FunctionDefinition} and stores it in the {@link #functions} map.
     * </p>
     *
     * @param func function definition expression string
     * @return the created {@link FunctionDefinition}
     * @throws RuntimeException if parsing fails or the function already exists
     */
    public ExpressionNode addFunction(String func) {
        Tokenizer tokenizer = new Tokenizer(func);
        Parser parser = new Parser(tokenizer.tokenize());
        ExpressionNode root = parser.parseExpression();
        FunctionDefinitionNode functionDefinitionNode = (FunctionDefinitionNode) root;// Ensure duplicate function names are not added.
        if (functions.containsKey((String) functionDefinitionNode.getValue())) {
            throw new RuntimeException("literals.Function " + functionDefinitionNode.getValue() + " already exists in context");
        }
        FunctionDefinition def = defineFunction(functionDefinitionNode);
        def.setExpression(func);
        functions.put(def.getName(), def);
        return functionDefinitionNode.getBody();
    }

    /**
     * Convert a parsed {@link FunctionDefinitionNode} into a {@link FunctionDefinition} model.
     *
     * @param def the parsed function definition node
     * @return the constructed {@link FunctionDefinition}
     */
    private FunctionDefinition defineFunction(FunctionDefinitionNode def) {
        return new FunctionDefinition((String) def.getValue(), def.getParams(), def.getBody());
    }

    /**
     * Look up a user-defined function by name in this context.
     *
     * @param name function name
     * @return the {@link FunctionDefinition} or {@code null} if not found
     */
    public FunctionDefinition lookupFunction(String name) {
        return functions.get(name);
    }

    /**
     * Check whether a user-defined function exists in this context.
     *
     * @param name function name
     * @return {@code true} if the function exists
     */
    public boolean containsFunction(String name) {
        return functions.containsKey(name);
    }

    /**
     * Check whether a native (built-in) function exists in this context.
     *
     * @param name function name
     * @return {@code true} if a native implementation exists
     */
    public boolean containsNativeFunction(String name) {
        return nativeFunctions.containsKey(name);
    }

    /**
     * Call a native function by name with the provided numeric arguments.
     *
     * @param name function name
     * @param args numeric arguments
     * @return the function result as {@code Double}
     */
    public BigDecimal callNativeFunction(String name, BigDecimal[] args) {
        Function<BigDecimal[], BigDecimal> f = nativeFunctions.get(name);
        return f.apply(args);
    }

    /**
     * Placeholder for numerical integration native function.
     *
     * <p>
     * Expected to compute the definite integral over the provided bounds. Current implementation
     * returns 0.0 and should be replaced with a proper numerical integration routine when needed.
     * </p>
     *
     * @param upper upper bound
     * @param lower lower bound
     * @return integral result (currently 0.0)
     */
    public static double integral(double upper, double lower) {
        return 0.0;
    }

    private static BigDecimal factorial(BigDecimal number) {
        BigDecimal decimal = new BigDecimal(1);
        for (double i = number.doubleValue(); i > 0; i--) {
            decimal = decimal.multiply(BigDecimal.valueOf(i));
        }
        return decimal;
    }

    private static BigDecimal permutation(BigDecimal n, BigDecimal k) {
        BigDecimal n_fac = factorial(n);
        BigDecimal k_fac = factorial(n.subtract(k));
        return n_fac.divide(k_fac);
    }

    private static BigDecimal combination(BigDecimal n, BigDecimal k) {
        BigDecimal permutation = permutation(n, k);
        return permutation.divide(factorial(k));
    }

    private static BigDecimal mod(BigDecimal a, BigDecimal b) {
        return a.remainder(b);
    }

    private static BigDecimal div(BigDecimal a, BigDecimal b) {
        return a.divide(b).subtract(mod(a, b));
    }

    public static MathObject euclidianGcf(MathObject a, MathObject b) {
        if (JParser.isZero(b)) {
            throw new RuntimeException("Unable to find gcf when b = 0");
        }
        MathObject quotient = new MathObject(0);
        MathObject remainder = new MathObject(a.toString());



        return a;
    }
}
