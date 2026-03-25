package io.tonyblack10.aiplayground.chat.service.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class CalculatorTool {

  @Tool(description = "Evaluates a mathematical expression. Supports +, -, *, /, parentheses, sqrt(), abs(), and decimal numbers. Example: '(2 + 3) * 4.5' or 'sqrt(16)'")
  public String calculate(
      @ToolParam(description = "The math expression to evaluate, e.g. '(2 + 3) * 4'") String expression) {
    try {
      double result = eval(expression);
      if (result == Math.floor(result) && !Double.isInfinite(result)) {
        return String.valueOf((long) result);
      }
      return String.valueOf(result);
    } catch (Exception e) {
      return "Error evaluating expression: " + e.getMessage();
    }
  }

  private double eval(String expr) {
    return new Object() {
      final String input = expr.replaceAll("\\s+", "");
      int pos = -1, ch;

      void nextChar() {
        ch = (++pos < input.length()) ? input.charAt(pos) : -1;
      }

      boolean eat(int charToEat) {
        while (ch == ' ') nextChar();
        if (ch == charToEat) {
          nextChar();
          return true;
        }
        return false;
      }

      double parse() {
        nextChar();
        double x = parseExpression();
        if (pos < input.length()) throw new RuntimeException("Unexpected character: " + (char) ch);
        return x;
      }

      double parseExpression() {
        double x = parseTerm();
        while (true) {
          if (eat('+')) x += parseTerm();
          else if (eat('-')) x -= parseTerm();
          else return x;
        }
      }

      double parseTerm() {
        double x = parseFactor();
        while (true) {
          if (eat('*')) x *= parseFactor();
          else if (eat('/')) x /= parseFactor();
          else return x;
        }
      }

      double parseFactor() {
        if (eat('+')) return parseFactor();
        if (eat('-')) return -parseFactor();

        double x;
        int startPos = this.pos;

        if (eat('(')) {
          x = parseExpression();
          eat(')');
        } else if ((ch >= '0' && ch <= '9') || ch == '.') {
          while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
          x = Double.parseDouble(input.substring(startPos + 1, this.pos));
        } else if (ch >= 'a' && ch <= 'z') {
          while (ch >= 'a' && ch <= 'z') nextChar();
          String func = input.substring(startPos + 1, this.pos);
          x = parseFactor();
          x = switch (func) {
            case "sqrt" -> Math.sqrt(x);
            case "abs" -> Math.abs(x);
            case "ceil" -> Math.ceil(x);
            case "floor" -> Math.floor(x);
            default -> throw new RuntimeException("Unknown function: " + func);
          };
        } else {
          throw new RuntimeException("Unexpected: " + (char) ch);
        }
        return x;
      }
    }.parse();
  }
}
