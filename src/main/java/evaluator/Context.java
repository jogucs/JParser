package evaluator;

import literals.FunctionDefinition;
import literals.NativeFunction;
import literals.Term;
import nodes.ExpressionNode;
import nodes.FunctionDefinitionNode;
import parser.Parser;
import tokenizer.Tokenizer;

import java.math.BigDecimal;
import java.util.*;

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
    public static Map<String, Double> variables = new HashMap<>()
    {{
        put("e", 2.718281828459045235360287471352);
        put("pi", 3.1415926535897932384626433);
    }};

    public static Map<String, String[]> trigonometricDerivatives = new HashMap<>()
    {{
        put("sin", new String[]{"cos"});
        put("cos", new String[]{"-sin"});
        put("tan", new String[]{"sec^2"});
        put("cot", new String[]{"-csc^2"});
        put("sec", new String[]{"sec", "tan"});
        put("csc", new String[]{"-csc", "cot"});
        put("arcsin", new String[]{"1/(sqrt(1-x^2))"});
        put("arccos", new String[]{"(-1)/(sqrt(1-x^2))"});
        put("arctan", new String[]{"1/(x^2 + 1)"});
        put("arccot", new String[]{"(-1)/(x^2 + 1)"});
        put("arcsec", new String[]{"1/(abs(x)*sqrt(x^2-1))"});
        put("arccsc", new String[]{"(-1)/(abs(x)*sqrt(x^2-1))"});
    }};

    /**
     * Map of user-defined function names to their {@link FunctionDefinition} representations.
     * Functions are added by parsing a function definition expression and stored here.
     */
    public Map<String, FunctionDefinition> functions = new HashMap<>();

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

    public boolean containsNativeFunction(String name) {
        return NativeFunction.contains(name);
    }

    /**
     * Call a native function by name with the provided numeric arguments.
     *
     * @param name function name
     * @param args numeric arguments
     * @return the function result as {@code Double}
     */
    public static Term callNativeFunction(String name, Term[] args) {
        if (args[0].getValue() != null) {
            return switch (name) {
                case "cos" ->
                        new Term(Math.cos((args[0].getValue().doubleValue() * ((JParser.degrees) ? 0.0174533 : 1))));
                case "sin" ->
                        new Term(Math.sin((args[0].getValue().doubleValue() * ((JParser.degrees) ? 0.0174533 : 1))));
                case "tan" ->
                        new Term(Math.tan(args[0].getValue().doubleValue() * ((JParser.degrees) ? 0.0174533 : 1)));
                case "tanh" ->
                        new Term(Math.tanh(args[0].getValue().doubleValue() * ((JParser.degrees) ? 0.0174533 : 1)));
                case "sinh" ->
                        new Term(Math.sinh(args[0].getValue().doubleValue() * ((JParser.degrees) ? 0.0174533 : 1)));
                case "cosh" ->
                        new Term(Math.cosh(args[0].getValue().doubleValue() * ((JParser.degrees) ? 0.0174533 : 1)));
                case "asin" ->
                        new Term(Math.asin(args[0].getValue().doubleValue() * ((JParser.degrees) ? 0.0174533 : 1)));
                case "acos" ->
                        new Term(Math.acos(args[0].getValue().doubleValue() * ((JParser.degrees) ? 0.0174533 : 1)));
                case "atan" ->
                        new Term(Math.atan(args[0].getValue().doubleValue() * ((JParser.degrees) ? 0.0174533 : 1)));
                case "cbrt" ->
                        new Term(Math.cbrt(args[0].getValue().doubleValue() * ((JParser.degrees) ? 0.0174533 : 1)));
                case "sqrt" ->
                        new Term(Math.sqrt(args[0].getValue().doubleValue() * ((JParser.degrees) ? 0.0174533 : 1)));
                case "abs" ->
                        new Term(Math.abs(args[0].getValue().doubleValue() * ((JParser.degrees) ? 0.0174533 : 1)));
                case "ln" -> new Term(Math.log(args[0].getValue().doubleValue() * ((JParser.degrees) ? 0.0174533 : 1)));
                case "log" ->
                        new Term(Math.log10(args[0].getValue().doubleValue() * ((JParser.degrees) ? 0.0174533 : 1)));
                case "fac" ->
                        factorial(args[0]);
                case "perm" ->
                        new Term(permutation(args[0].getValue(), args[1].getValue()));
                case "comb" ->
                        new Term(combination(args[0].getValue(), args[1].getValue()));
                case "mod" ->
                        new Term(mod(args[0].getValue(), args[1].getValue()));
                case "div" ->
                        new Term(div(args[0].getValue(), args[1].getValue()));
                case "sum" ->
                        new Term(summation(args[0].toString()).getValue());
                default -> null;
            };
        } else {
            return new Term(name + "(" + args[0] + ")");
        }
    }

    private static Term factorial(Term number) {
        if (number.getValue() == null) {
            return new Term("fac(" + number + ")");
        }
        BigDecimal decimal = new BigDecimal(1);
        for (double i = number.getValue().doubleValue(); i > 0; i--) {
            decimal = decimal.multiply(BigDecimal.valueOf(i));
        }
        return new Term(decimal);
    }

    private static BigDecimal permutation(BigDecimal n, BigDecimal k) {
        BigDecimal n_fac = factorial(new Term(n)).getValue();
        BigDecimal k_fac = factorial(new Term(n.subtract(k))).getValue();
        return n_fac.divide(k_fac);
    }

    private static BigDecimal combination(BigDecimal n, BigDecimal k) {
        BigDecimal permutation = permutation(n, k);
        return permutation.divide(factorial(new Term(k)).getValue());
    }

    private static BigDecimal mod(BigDecimal a, BigDecimal b) {
        return a.remainder(b);
    }

    private static BigDecimal div(BigDecimal a, BigDecimal b) {
        return a.divide(b).subtract(mod(a, b));
    }

    public static Term euclidianGcf(Term a, Term b) {
        if (JParser.isZero(b)) {
            throw new RuntimeException("Unable to find gcf when b = 0");
        }
        Term quotient = new Term(0);
        Term remainder = new Term(a.toString());

        return a;
    }

    public static Term summation(String expression) {
        Term object = JParser.evaluate(expression);
        FunctionDefinition function = JParser.createFunctionFromPolynomial(object.toString(), object.findVariable());
        BigDecimal sum = new BigDecimal(0);
        BigDecimal prevValue;
        BigDecimal newValue = null;
        Term functionValue;

        for (int i = 0; true; i++) {
            prevValue = newValue;
            functionValue = JParser.evaluate(function.getName() + "(" + i + ")");
            newValue = sum.add(functionValue.getValue());
            sum = newValue;
            if (prevValue != null && JParser.isZero(prevValue.subtract(newValue))) {
                break;
            }
        }
        return new Term(sum);
    }
}
