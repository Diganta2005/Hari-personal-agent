package com.HARI.HARI;

public class MathParser {

    private final String input;
    private int position;

    public MathParser(String input) {
        this.input = input;
    }

    public double parse() {
        double value = parseExpression();
        skipSpaces();

        if (position < input.length()) {
            throw new IllegalArgumentException("unexpected value near '" + input.charAt(position) + "'");
        }

        return value;
    }

    private double parseExpression() {
        double value = parseTerm();

        while (true) {
            skipSpaces();
            if (match('+')) {
                value += parseTerm();
            } else if (match('-')) {
                value -= parseTerm();
            } else {
                return value;
            }
        }
    }

    private double parseTerm() {
        double value = parsePower();

        while (true) {
            skipSpaces();
            if (match('*')) {
                value *= parsePower();
            } else if (match('/')) {
                double divisor = parsePower();
                if (divisor == 0) {
                    throw new ArithmeticException("division by zero");
                }
                value /= divisor;
            } else {
                return value;
            }
        }
    }

    private double parsePower() {
        double value = parseUnary();
        skipSpaces();

        if (match('^')) {
            value = Math.pow(value, parsePower());
        }

        return value;
    }

    private double parseUnary() {
        skipSpaces();

        if (match('+')) {
            return parseUnary();
        }

        if (match('-')) {
            return -parseUnary();
        }

        return parsePercent();
    }

    private double parsePercent() {
        double value = parsePrimary();

        while (true) {
            skipSpaces();
            if (match('%')) {
                value /= 100;
            } else {
                return value;
            }
        }
    }

    private double parsePrimary() {
        skipSpaces();

        if (match('(')) {
            double value = parseExpression();
            if (!match(')')) {
                throw new IllegalArgumentException("missing closing parenthesis");
            }
            return value;
        }

        if (peekLetter()) {
            return parseFunction();
        }

        return parseNumber();
    }

    private double parseFunction() {
        String name = parseName();
        double value;

        if (match('(')) {
            value = parseExpression();
            if (!match(')')) {
                throw new IllegalArgumentException("missing closing parenthesis after " + name);
            }
        } else {
            value = parseUnary();
        }

        return switch (name) {
            case "sqrt" -> {
                if (value < 0) {
                    throw new ArithmeticException("square root of a negative number");
                }
                yield Math.sqrt(value);
            }
            case "sin" -> Math.sin(Math.toRadians(value));
            case "cos" -> Math.cos(Math.toRadians(value));
            case "tan" -> Math.tan(Math.toRadians(value));
            case "log" -> Math.log10(value);
            case "ln" -> Math.log(value);
            case "abs" -> Math.abs(value);
            default -> throw new IllegalArgumentException("unknown function '" + name + "'");
        };
    }

    private String parseName() {
        int start = position;
        while (position < input.length() && Character.isLetter(input.charAt(position))) {
            position++;
        }
        return input.substring(start, position);
    }

    private double parseNumber() {
        skipSpaces();
        int start = position;

        while (position < input.length()) {
            char current = input.charAt(position);
            if (Character.isDigit(current) || current == '.') {
                position++;
            } else {
                break;
            }
        }

        if (start == position) {
            throw new IllegalArgumentException("expected a number");
        }

        try {
            return Double.parseDouble(input.substring(start, position));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("invalid number");
        }
    }

    private boolean match(char expected) {
        skipSpaces();
        if (position < input.length() && input.charAt(position) == expected) {
            position++;
            return true;
        }
        return false;
    }

    private boolean peekLetter() {
        skipSpaces();
        return position < input.length() && Character.isLetter(input.charAt(position));
    }

    private void skipSpaces() {
        while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
            position++;
        }
    }
}
