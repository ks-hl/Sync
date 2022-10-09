package dev.heliosares.sync.utils;

import java.util.HashMap;

//https://stackoverflow.com/questions/3422673/how-to-evaluate-a-math-expression-given-in-string-form
public class FormulaParser {
	public final String equation;

	public FormulaParser(String equation) {
		this.equation = equation;
	}

	private int pos = -1, ch;

	private void nextChar() {
		ch = (++pos < equation.length()) ? equation.charAt(pos) : -1;
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

	public double solve() throws RuntimeException {
		pos = -1;
		ch = -1;
		nextChar();
		double x = parsePM();
		if (pos < equation.length())
			throw new RuntimeException("Unexpected: " + (char) ch);
		variables.clear();
		return x;
	}

	private HashMap<String, Double> variables = new HashMap<>();

	public void setVariable(String name, double value) {
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
		for (;;) {
			boolean lessthan = false;
			boolean greaterthan = false;
			if (eat('='))
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
		for (;;) {
			if (eat('*'))
				x *= parseFactor(); // multiplication
			else if (eat('/'))
				x /= parseFactor(); // division
			else
				return x;
		}
	}

	private Object parseParameter() {
		int start = pos;
		while (ch != ')' && ch != ',' && ch != -1) {
			nextChar();
		}
		int end = pos;
		eat(',');
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
			if (variables.containsKey(func)) {
				x = variables.get(func);
			} else if (func.equalsIgnoreCase("len")) {
				if (!eat('('))
					throw new RuntimeException("Missing '(' for function " + func);
				x = parseParameter().toString().length();
				if (!eat(')'))
					throw new RuntimeException("Missing ')' after argument to " + func);
			} else if (func.equalsIgnoreCase("matches")) {
				if (!eat('('))
					throw new RuntimeException("Missing '(' for function " + func);
				String arg1 = parseParameter().toString();
				String arg2 = parseParameter().toString();
				x = arg1.matches(arg2) ? 1 : 0;
				if (!eat(')'))
					throw new RuntimeException("Missing ')' after argument to " + func);
			} else if (func.equalsIgnoreCase("equals")) {
				if (!eat('('))
					throw new RuntimeException("Missing '(' for function " + func);
				String arg1 = parseParameter().toString();
				String arg2 = parseParameter().toString();
				x = arg1.matches(arg2) ? 1 : 0;
				if (!eat(')'))
					throw new RuntimeException("Missing ')' after argument to " + func);
			} else {
				if (eat('(')) {
					x = parsePM();
					if (!eat(')'))
						throw new RuntimeException("Missing ')' after argument to " + func);
				} else {
					x = parseFactor();
				}
				if (func.equals("sqrt"))
					x = Math.sqrt(x);
				else if (func.equals("sin"))
					x = Math.sin(Math.toRadians(x));
				else if (func.equals("cos"))
					x = Math.cos(Math.toRadians(x));
				else if (func.equals("tan"))
					x = Math.tan(Math.toRadians(x));
				else
					throw new RuntimeException("Unknown function or variable: " + func);
			}
		} else {
			throw new RuntimeException(
					"Unexpected: '" + (char) ch + "' (" + ch + ") in equation '" + equation + "' index " + pos);
		}

		if (eat('^'))
			x = Math.pow(x, parseFactor()); // exponentiation

		return x;
	}

	public static void main(String args[]) {
		FormulaParser parse = new FormulaParser("matches(test,t.st)=0.5+0.5");
		long start = System.nanoTime();
		double d = parse.solve();
		start = System.nanoTime() - start;
		System.out.println(d);
		System.out.println("Took: " + Math.round(start / 10000.0) / 100.0 + "ms");
	}
}
