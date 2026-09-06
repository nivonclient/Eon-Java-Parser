package com.eon;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// A small tokenizer for Eon.
///
/// It follows the same general rules as the Rust {@code logos}-based lexer:
/// whitespace is skipped, and when more than one token can start at the same
/// place, the longer one is used first.
public final class Lexer {
    private static final Pattern WHITESPACE = Pattern.compile("[ \\t\\n\\f\\r]*");
    private static final Pattern COMMENT = Pattern.compile("//[^\\n]*");
    private static final Pattern IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
    private static final Pattern NUMBER = Pattern.compile("[+\\-0-9.][0-9a-zA-Z.+\\-_]*");
    private static final Pattern DOUBLE_QUOTED_STRING = Pattern.compile("\"([^\"\\\\]|\\\\.)*\"");
    private static final Pattern SINGLE_QUOTED_STRING = Pattern.compile("'[^']*'");
    private static final Pattern MULTILINE_BASIC_STRING =
            Pattern.compile("\"\"\"([^\"]|\"[^\"]|\"\"[^\"])*\"\"\"");
    private static final Pattern MULTILINE_LITERAL_STRING =
            Pattern.compile("'''([^']|'[^']|''[^'])*'''");

    // The order here is also used when two tokens have the same length.
    // It follows the order used by the Rust lexer.
    private record Candidate(TokenKind kind, Pattern pattern) {}
    private static final Candidate[] CANDIDATES = {
            new Candidate(TokenKind.COMMENT, COMMENT),
            new Candidate(TokenKind.IDENTIFIER, IDENTIFIER),
            new Candidate(TokenKind.NUMBER, NUMBER),
            new Candidate(TokenKind.DOUBLE_QUOTED_STRING, DOUBLE_QUOTED_STRING),
            new Candidate(TokenKind.SINGLE_QUOTED_STRING, SINGLE_QUOTED_STRING),
            new Candidate(TokenKind.MULTILINE_BASIC_STRING, MULTILINE_BASIC_STRING),
            new Candidate(TokenKind.MULTILINE_LITERAL_STRING, MULTILINE_LITERAL_STRING),
    };

    private final String source;
    private int pos;

    public Lexer(String source) {
        this.source = source;
        this.pos = 0;
    }

    public String source() { return source; }

    /// A token found in the source.
    ///
    /// If {@code kind} is {@code null}, the slice could not be read as a valid token.
    public record Result(Span span, String slice, TokenKind kind) {}

    /// Returns the next token, or {@code null} when there is nothing left.
    public Result next() {
        skipWhitespace();
        if (pos >= source.length()) {
            return null;
        }

        char c = source.charAt(pos);
        TokenKind singleChar = switch (c) {
            case '[' -> TokenKind.OPEN_LIST;
            case ']' -> TokenKind.CLOSE_LIST;
            case '{' -> TokenKind.OPEN_BRACE;
            case '}' -> TokenKind.CLOSE_BRACE;
            case '(' -> TokenKind.OPEN_PAREN;
            case ')' -> TokenKind.CLOSE_PAREN;
            case ':' -> TokenKind.COLON;
            case ',' -> TokenKind.COMMA;
            default -> null;
        };
        if (singleChar != null) {
            // These characters are not used at the start of any of the patterns above,
            // so there is no need to check for a longer token here.
            Span span = new Span(pos, pos + 1);
            String slice = source.substring(pos, pos + 1);
            pos += 1;
            return new Result(span, slice, singleChar);
        }

        TokenKind bestKind = null;
        int bestLen = -1;
        for (Candidate candidate : CANDIDATES) {
            Matcher m = candidate.pattern().matcher(source);
            m.region(pos, source.length());
            m.useTransparentBounds(true);
            m.useAnchoringBounds(true);
            if (m.lookingAt()) {
                int len = m.end() - m.start();
                if (len > bestLen) {
                    bestLen = len;
                    bestKind = candidate.kind();
                }
            }
        }

        if (bestKind == null) {
            // Nothing matched, so move forward by one code point and mark it as invalid.
            int cp = source.codePointAt(pos);
            int charCount = Character.charCount(cp);
            Span span = new Span(pos, pos + charCount);
            String slice = source.substring(pos, pos + charCount);
            pos += charCount;
            return new Result(span, slice, null);
        }

        Span span = new Span(pos, pos + bestLen);
        String slice = source.substring(pos, pos + bestLen);
        pos += bestLen;
        return new Result(span, slice, bestKind);
    }

    private void skipWhitespace() {
        Matcher m = WHITESPACE.matcher(source);
        m.region(pos, source.length());
        m.useTransparentBounds(true);
        m.useAnchoringBounds(true);
        if (m.lookingAt()) {
            pos = m.end();
        }
    }
}