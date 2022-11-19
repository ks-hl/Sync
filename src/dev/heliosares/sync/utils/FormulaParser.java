package dev.heliosares.sync.utils;

import java.util.HashMap;
import java.util.Random;
import java.util.function.Supplier;

//https://stackoverflow.com/questions/3422673/how-to-evaluate-a-math-expression-given-in-string-form
public class FormulaParser {
    public final String originalEquation;
    private final Random random = new Random();
    public String equation;
    private int pos = -1, ch;
    private HashMap<String, Supplier<Object>> variables = new HashMap<>();

    public FormulaParser(String equation) {
        this.originalEquation = equation.toLowerCase();
    }

    private void nextChar() {
        ch = (++pos < equation.length()) ? equation.charAt(pos) : -1;
    }

    private char peek() {
        return equation.charAt(pos + 1);
    }

    private boolean eat(int charToEat) {
        while (ch == ' ')
            nextChar();
        if (ch == charToEat) {
            nextChar();
            return true;
        }
        return false;
    }

    public String replaceVariables(String str) {
        String out = "";
        String var = "";
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '$') {
                var = "$";
            } else if (var.length() > 0) {
                if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '-') {
                    var += c;
                    if (i < str.length() - 1) {
                        continue;
                    }
                }
                if (variables.containsKey(var)) {
                    out += variables.get(var).get();
                } else {
                    throw new RuntimeException("Unknown variable: " + var);
                }
                var = "";
            } else {
                out += c;
            }
        }
        return out;
    }

    public double solve() throws RuntimeException {
        pos = -1;
        ch = -1;
        equation = replaceVariables(originalEquation);
        nextChar();
        double x = parsePM();
        if (pos < equation.length())
            throw new RuntimeException("Unexpected: " + (char) ch);
        return x;
    }

    public void reset() {
        pos = -1;
        ch = -1;
        variables.clear();
    }

    public void setVariable(String name, Supplier<Object> value) {
        variables.put(name, value);
    }

    // Grammar:
    // expression = term | expression `+` term | expression `-` term
    // term = factor | term `*` factor | term `/` factor
    // factor = `+` factor | `-` factor | `(` expression `)` | number
    // | functionName `(` expression `)` | functionName factor
    // | factor `^` factor

    private double parsePM() {
        double x = parseMD();
        int startPos = pos;
        for (; ; ) {
            boolean and = eat('&');
            boolean lessthan = false;
            boolean greaterthan;
            if (and || eat('|')) {
                double other = parsePM();
                if ((x != 1 && x != 0) || (other != 1 && other != 0)) {
                    throw new RuntimeException("Can't and/or non-boolean value: " + equation.substring(startPos, pos));
                }
                if (and) {
                    x = other == 1 && x == 1 ? 1 : 0;
                } else {
                    x = other == 1 || x == 1 ? 1 : 0;
                }
            } else if (eat('='))
                x = (x == parsePM()) ? 1 : 0;
            else if ((greaterthan = eat('>')) || (lessthan = eat('<'))) {
                boolean orequal = eat('=');
                double other = parsePM();
                if (orequal && x == other) {
                    x = 1;
                } else if (greaterthan) {
                    x = (x > other) ? 1 : 0;
                } else if (lessthan) {
                    x = (x < other) ? 1 : 0;
                }
            } else if (eat('+'))
                x += parseMD(); // addition
            else if (eat('-'))
                x -= parseMD(); // subtraction
            else
                return x;
        }
    }

    private double parseMD() {
        double x = parseFactor();
        for (; ; ) {
            if (eat('*'))
                x *= parseFactor(); // multiplication
            else if (eat('/'))
                x /= parseFactor(); // division
            else
                return x;
        }
    }

    private String parseParameters() {
        int start = pos;
        while (ch != ')' && ch != -1) {
            nextChar();
        }
        int end = pos;
        return equation.substring(start, end);
    }

    private double parseFactor() {
        if (eat('+'))
            return +parseFactor(); // unary plus
        if (eat('-'))
            return -parseFactor(); // unary minus

        double x = 0;
        int startPos = this.pos;
        if (eat('!')) {
            if (eat('=')) {
                pos = startPos;
                return x;
            }
            x = parsePM();
            if (x == 1) {
                x = 0;
            } else if (x == 0) {
                x = 1;
            } else {
                throw new RuntimeException("Cannot invert " + equation.substring(startPos, pos));
            }
        } else if (eat('(')) { // parentheses
            x = parsePM();
            if (!eat(')'))
                throw new RuntimeException("Missing ')'");
        } else if ((ch >= '0' && ch <= '9') || ch == '.') { // numbers
            while ((ch >= '0' && ch <= '9') || ch == '.')
                nextChar();
            x = Double.parseDouble(equation.substring(startPos, this.pos));
        } else if (ch >= 'a' && ch <= 'z') { // functions
            while (ch >= 'a' && ch <= 'z')
                nextChar();
            String func = equation.substring(startPos, this.pos);
            if (!eat('('))
                throw new RuntimeException("Missing '(' for function " + func);

            if (func.equals("sqrt"))
                x = Math.sqrt(parsePM());
            else if (func.equals("sin"))
                x = Math.sin(Math.toRadians(parsePM()));
            else if (func.equals("cos"))
                x = Math.cos(Math.toRadians(parsePM()));
            else if (func.equals("tan"))
                x = Math.tan(Math.toRadians(parsePM()));
            else {
                final String paramStr = parseParameters();
                final String[] params = paramStr.isEmpty() ? new String[0] : paramStr.split(",");
                if (func.equalsIgnoreCase("len")) {
                    if (params.length != 1)
                        throw new RuntimeException("Invalid number of arguments for function: " + func);
                    x = params[0].length();
                } else if (func.equalsIgnoreCase("matches")) {
                    if (params.length != 2)
                        throw new RuntimeException("Invalid number of arguments for function: " + func);
                    x = params[0].matches(params[1]) ? 1 : 0;
                } else if (func.equalsIgnoreCase("equals")) {
                    if (params.length != 2)
                        throw new RuntimeException("Invalid number of arguments for function: " + func);
                    x = params[0].matches(params[1]) ? 1 : 0;
                } else if (func.equalsIgnoreCase("rand")) {
                    if (params.length == 0) x = random.nextDouble();
                    else if (params.length == 1) {
                        x = random.nextInt(Integer.parseInt(params[0]));
                    } else if (params.length == 2) {
                        x = random.nextInt(Integer.parseInt(params[0]), Integer.parseInt(params[1]));
                    } else throw new RuntimeException("Invalid number of arguments for function: " + func);
                } else {
                    throw new RuntimeException("Unknown function: " + func);
                }
            }
            if (!eat(')'))
                throw new RuntimeException("Missing ')' after argument to " + func);
        } else {
            throw new RuntimeException(
                    "Unexpected: '" + (char) ch + "' (" + ch + ") in equation '" + equation + "' index " + pos);
        }

        if (eat('^'))
            x = Math.pow(x, parseFactor()); // exponentiation

        return x;
    }
}
