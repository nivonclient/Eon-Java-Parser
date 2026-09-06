package com.eon;

/// String escaping/quoting/unquoting utilities for the Eon format
public final class Strings {
    private Strings() {}

    private static boolean isKeyword(String s) {
        return s.equals("true") || s.equals("false") || s.equals("null");
    }

    /// Returns `true` if the string does matches `[a-zA-Z_][a-zA-Z0-9_]*`
    public static boolean isValidIdentifier(String s) {
        if (isKeyword(s)) return false; // We need to quote these to avoid confusion with the special keyword values
        if (s.isEmpty()) return false;

        // Check first character:
        char first = s.charAt(0);

        // Check the rest:
        if (!(Character.isLetter(first) && first < 128) && first != '_') return false;
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean alnum = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
            if (!alnum && c != '_') return false;
        }
        return true;
    }

    /// Format a string into Eon, adding quotes and escaping as needed.
    ///
    /// The exact type of quoting (single, double, multiline basic, or multiline literal) will be determined automatically based on the content of the string.
    public static String escapeAndQuote(String raw) {
        // TODO(emilk): smartly choose between all four types of strings: double, single, multiline basic, and multiline literal.

        boolean mustBeDoubleQuoted = raw.chars()
                .anyMatch(c -> Character.isISOControl(c) || c == '\'' || c == '\n' || c == '\r' || c == '\t');
        if (mustBeDoubleQuoted) {
            return doubleQuote(raw);
        }

        // This would benefit from using single-quoted literal strings:
        boolean wouldBeShorterIfLiteral = raw.chars().anyMatch(c -> Character.isISOControl(c) || c == '"' || c == '\\');
        if (wouldBeShorterIfLiteral) {
            return "'" + raw + "'";
        }
        // The default
        return doubleQuote(raw);
    }

    private static String doubleQuote(String raw) {
        // Mirrors Rust's `{raw:?}` Debug formatting of a &str: a double-quoted, escaped string.
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (Character.isISOControl(c)) {
                        sb.append(String.format("\\u{%x}", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /// Remove the quotes and unescape the string.
    public static String unescapeAndUnquote(String escaped) throws EonError {
        if (escaped.indexOf('\r') != -1) {
            // Handle Windows newlines by stripping all `\r` characters,
            // turning `\r\n` into `\n`.
            return unescapeAndUnquote(escaped.replace("\r", ""));
        }

        if (escaped.startsWith("'''")) {
            // multiline literal string. No escape sequences, but strip the leading newline (if any):
            String suffix = escaped.substring(3);
            if (!suffix.endsWith("'''")) {
                throw EonError.custom("Triple-quoted multiline literal string must end with three single quotes");
            }
            String contents = suffix.substring(0, suffix.length() - 3);
            return contents.startsWith("\n") ? contents.substring(1) : contents;
        } else if (escaped.startsWith("'")) {
            // single-quoted literal string. No escape sequences.
            String suffix = escaped.substring(1);
            if (!suffix.endsWith("'")) {
                throw EonError.custom("Single-quoted literal string must end with three single quotes");
            }
            String contents = suffix.substring(0, suffix.length() - 1);
            if (contents.indexOf('\n') != -1) {
                throw EonError.custom("Single-quoted literal string may contain newlines");
            }
            return contents;
        } else if (escaped.startsWith("\"\"\"")) {
            // Multiline double-quoted string. Can contain escape sequences.
            String suffix = escaped.substring(3);
            if (!suffix.endsWith("\"\"\"")) {
                throw EonError.custom("Missing ending of multiline double-quoted string");
            }
            String contents = suffix.substring(0, suffix.length() - 3);
            return unescape(contents);
        } else if (escaped.startsWith("\"")) {
            // Simple double-quoted string. Can contain escape sequences.
            String suffix = escaped.substring(1);
            if (!suffix.endsWith("\"")) {
                throw EonError.custom("Double-quoted string must end with a double quote");
            }
            String contents = suffix.substring(0, suffix.length() - 1);
            if (contents.indexOf('\n') != -1) {
                throw EonError.custom("Double-quoted string may contain newlines");
            }
            return unescape(contents);
        } else {
            throw EonError.custom("String must start with a quote (single or double)");
        }
    }

    private static String unescape(String s) throws EonError {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        int n = s.length();
        while (i < n) {
            char chr = s.charAt(i++);
            if (chr == '\\') {
                if (i >= n) throw EonError.custom("String ended with a backslash");
                char esc = s.charAt(i++);
                switch (esc) {
                    case ' ' -> out.append(' ');
                    case '"' -> out.append('"');
                    case '/' -> out.append('/');
                    case '\'' -> out.append('\'');
                    case '\\' -> out.append('\\');
                    case '`' -> out.append('`');
                    case '$' -> out.append('$');
                    case 'a' -> out.append('\u0007');
                    case 'b' -> out.append('\u0008');
                    case 'e', 'E' -> out.append('\u001B');
                    case 'f' -> out.append('\u000C');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'v' -> out.append('\u000B');
                    case '\n' -> {
                        // Escaped newline: ignore it, and the following whitespace.
                        while (i < n && Character.isWhitespace(s.charAt(i))) i++;
                    }
                    case 'u' -> {
                        if (i >= n || s.charAt(i) != '{') {
                            throw EonError.custom("\\u must be followed by an opening brace, e.g. \\u{1F600}");
                        }
                        i++; // consume '{'
                        int numChars = 0;
                        long number = 0;
                        boolean closed = false;
                        while (i < n) {
                            char c = s.charAt(i++);
                            if (c == '}') { closed = true; break; }
                            int digit = Character.digit(c, 16);
                            if (digit >= 0) {
                                if (numChars >= 8) {
                                    throw EonError.custom(String.format(
                                            "Unicode escape sequence is too long: %X (max 8 hex digits allowed)", number));
                                }
                                number = number * 16 + digit;
                                numChars++;
                            } else if (c == '_') {
                                // ignore
                            } else {
                                throw EonError.custom("Invalid character in Unicode escape sequence: " + c);
                            }
                        }
                        if (!closed) {
                            throw EonError.custom("Unicode escape sequence must end with a closing brace '}'");
                        }
                        if (!Character.isValidCodePoint((int) number)) {
                            throw EonError.custom(String.format("Invalid Unicode code point: %X", number));
                        }
                        out.appendCodePoint((int) number);
                    }
                    default -> throw EonError.custom("Unknown escape sequence: \\" + esc);
                }
            } else {
                out.append(chr);
            }
        }
        return out.toString();
    }
}
