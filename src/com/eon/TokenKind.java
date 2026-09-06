package com.eon;

public enum TokenKind {

    /// `// Some comment`
    COMMENT("// comment"),

    /// `[`
    OPEN_LIST("open bracket '['"),

    /// `]`
    CLOSE_LIST("close bracket ']'"),

    /// `{`
    OPEN_BRACE("open brace '{'"),

    /// `}`
    CLOSE_BRACE("close brace '}'"),

    /// `(`
    OPEN_PAREN("open parenthesis '('"),

    /// `)`
    CLOSE_PAREN("close parenthesis ')'"),

    /// `:`
    COLON("colon ':'"),

    /// `,`
    COMMA("comma ','"),

    /// Can be a map key, or "false", "true", "null"
    IDENTIFIER("identifier"),

    /// Anything that starts with a sign (+/-), a digit (0-9), or a period (decimal separator).
    NUMBER("number"),

    /// `"this"`
    ///
    /// Processes escaped characters like `\"`, `\\`, `\n`, etc.
    DOUBLE_QUOTED_STRING("\"basic string\""),

    /// Raw string `'like "this"'`
    ///
    /// Can contain any character except for single quotes.
    /// Does not process escape sequences.
    SINGLE_QUOTED_STRING("'literal string'"),

    /// Multiline basic string (triple double quotes) - processes escape sequences
    MULTILINE_BASIC_STRING("\"\"\"multiline basic string\"\"\""),

    /// Multiline literal string (triple single quotes) - no escape processing
    MULTILINE_LITERAL_STRING("'''multiline literal string'''");

    private final String display;

    TokenKind(String display) { this.display = display; }

    @Override
    public String toString() { return display; }
}
