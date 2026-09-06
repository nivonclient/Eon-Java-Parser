package com.eon.parse;

import com.eon.EonError;
import com.eon.Lexer;
import com.eon.Span;
import com.eon.TokenKind;
import com.eon.tree.*;

import java.util.ArrayList;
import java.util.List;

/// Converts Eon source text into a {@link TokenTree} using a recursive-descent parser
public final class Parser {
    /// Protect against stack overflow in the recursive-descent parser
    private static final int MAX_RECURSION_DEPTH = 128;

    private Parser() {}

    /// Parses a full Eon document
    public static TokenTree parseStr(String source) throws EonError {
        return parseTopStr(source);
    }

    private static TokenTree parseTopStr(String eonSource) throws EonError {
        // Usually an Eon file contains a bunch of `key: value` pairs, without any
        // surrounding braces, so we optimize for that case:
        TokenStream tokensA = new TokenStream(eonSource);
        try {
            TokenMap map = parseMapContents(tokensA, 0);
            checkForTrailingTokens(tokensA);
            return new TokenTree(
                    new Span(0, eonSource.length()),
                    List.of(),
                    new TokenValue.MapValue(map),
                    null);
        } catch (EonError errA) {
            // Maybe the user wrapped the file in {}, or maybe it isn't a map at all.
            TokenStream tokensB = new TokenStream(eonSource);
            try {
                TokenList list = parseListContents(tokensB, 0);
                checkForTrailingTokens(tokensB);

                List<TokenTree> values = list.values();
                if (values.size() == 1) {
                    // A file containing a single value, e.g. `42` or `{…}`.-
                    return values.get(0);
                } else {
                    // A file containing many values, e.g. `1, 2, 3` or `{…}, {…}`.-
                    return new TokenTree(
                            new Span(0, eonSource.length()),
                            List.of(),
                            new TokenValue.ListValue(new TokenList(values, list.closingComments())),
                            null);
                }
            } catch (EonError errB) {
                // Return the error of the path that processed the most tokens, i.e. got further:
                if (tokensA.spanOfPrevious().end() < tokensB.spanOfPrevious().end()) {
                    throw errB;
                } else {
                    throw errA;
                }
            }
        }
    }

    private static void checkForTrailingTokens(TokenStream tokens) throws EonError {
        Lexer.Result r = tokens.next();
        if (r != null) {
            PlacedToken token = tokens.ok(r);
            throw tokens.errorAt(token.span(), "Expected end of file here");
        }
    }

    /// Parses the inside of a list, without consuming either the opening or closing bracket. */
    private static TokenList parseListContents(TokenStream tokens, int recurseDepth) throws EonError {
        List<TokenTree> values = new ArrayList<>();

        while (true) {
            List<String> prefixComments = parseComments(tokens);

            Lexer.Result peeked = tokens.peek();
            if (peeked == null || isClosing(peeked.kind())) {
                return new TokenList(values, prefixComments);
            }

            TokenTree value = parseTokenTree(tokens, recurseDepth + 1);

            List<String> merged = new ArrayList<>(prefixComments);
            merged.addAll(value.prefixComments);
            value.prefixComments = merged;

            if (tokens.peekIs(TokenKind.COMMA)) {
                tokens.next(); // Consume optional comma
                value.suffixComment = parseSuffixComment(tokens);
            }

            values.add(value);
        }
    }

    /// Parses the inside of a map, without consuming either the opening or closing bracket. */
    private static TokenMap parseMapContents(TokenStream tokens, int recurseDepth) throws EonError {
        List<TokenKeyValue> keyValues = new ArrayList<>();

        while (true) {
            List<String> prefixComments = parseComments(tokens);

            Lexer.Result peeked = tokens.peek();
            if (peeked == null || isClosing(peeked.kind())) {
                return new TokenMap(keyValues, prefixComments);
            }

            TokenTree key = parseTokenTree(tokens, recurseDepth + 1);
            key.prefixComments = prefixComments;

            consumeToken(tokens, TokenKind.COLON);

            TokenTree value = parseTokenTree(tokens, recurseDepth + 1);

            if (tokens.peekIs(TokenKind.COMMA)) {
                tokens.next(); // Consume optional comma
                value.suffixComment = parseSuffixComment(tokens);
            }

            keyValues.add(new TokenKeyValue(key, value));
        }
    }

    private static boolean isClosing(TokenKind kind) {
        return kind == TokenKind.CLOSE_BRACE || kind == TokenKind.CLOSE_LIST || kind == TokenKind.CLOSE_PAREN;
    }

    /// Parses a value, including its prefix and suffix comments. */
    private static TokenTree parseTokenTree(TokenStream tokens, int recurseDepth) throws EonError {
        if (recurseDepth >= MAX_RECURSION_DEPTH) {
            throw tokens.errorAt(tokens.spanOfPrevious(), "Maximum recursion depth exceeded while parsing document");
        }

        List<String> prefixComments = parseComments(tokens);

        Lexer.Result rawToken = tokens.next();
        if (rawToken == null) {
            throw tokens.errorAt(tokens.endSpan(), "Unexpected end of input: expected a value");
        }
        PlacedToken token = tokens.ok(rawToken);

        Span startSpan = tokens.spanOfNext();

        TokenValue value;
        switch (token.kind()) {
            case OPEN_LIST -> {
                TokenList list = parseListContents(tokens, recurseDepth + 1);
                consumeToken(tokens, TokenKind.CLOSE_LIST);
                value = new TokenValue.ListValue(list);
            }
            case OPEN_BRACE -> {
                TokenMap map = parseMapContents(tokens, recurseDepth + 1);
                consumeToken(tokens, TokenKind.CLOSE_BRACE);
                value = new TokenValue.MapValue(map);
            }
            case IDENTIFIER -> value = new TokenValue.Identifier(token.slice());
            case NUMBER -> value = new TokenValue.Number(token.slice());
            case DOUBLE_QUOTED_STRING, SINGLE_QUOTED_STRING, MULTILINE_BASIC_STRING, MULTILINE_LITERAL_STRING -> {
                // This could be a free-floating string, or the opening of a variant like `"Rgb"(…)`.
                if (tokens.peekIs(TokenKind.OPEN_PAREN)) {
                    tokens.next(); // Consume the open parenthesis

                    TokenList inner = parseListContents(tokens, recurseDepth + 1);
                    consumeToken(tokens, TokenKind.CLOSE_PAREN);

                    value = new TokenValue.VariantValue(new TokenVariant(
                            token.span(), token.slice(), inner.values(), inner.closingComments()));
                } else {
                    value = new TokenValue.QuotedString(token.slice());
                }
            }
            case COMMENT -> throw new IllegalStateException("We should have already consumed comments");
            case CLOSE_LIST -> throw tokens.errorAt(token.span(), "Unbalanced brackets");
            case CLOSE_BRACE -> throw tokens.errorAt(token.span(), "Unbalanced braces");
            case CLOSE_PAREN -> throw tokens.errorAt(token.span(), "Unbalanced parentheses");
            case OPEN_PAREN -> throw tokens.errorAt(token.span(), "Parentheses must be proceeded by a string");
            case COLON, COMMA -> throw tokens.errorAt(
                    token.span(), "Expected a value, like a map, list, number, or string");
            default -> throw new IllegalStateException("Unhandled token kind: " + token.kind());
        }

        Span span = startSpan.or(tokens.spanOfPrevious());

        String suffixComment = parseSuffixComment(tokens);

        return new TokenTree(span, prefixComments, value, suffixComment);
    }

    private static void consumeToken(TokenStream tokens, TokenKind expected) throws EonError {
        Lexer.Result r = tokens.next();
        if (r == null) {
            throw tokens.errorAt(tokens.spanOfPrevious(), "Expected " + expected + " but reached end of input");
        }
        PlacedToken token = tokens.ok(r);
        if (token.kind() != expected) {
            throw tokens.errorAt(token.span(), "Expected " + expected + " but found " + token.kind());
        }
    }

    private static String parseSuffixComment(TokenStream tokens) throws EonError {
        Span previousTokenSpan = tokens.spanOfPrevious();
        Lexer.Result peeked = tokens.peek();
        if (peeked == null || peeked.kind() != TokenKind.COMMENT) {
            return null;
        }
        Span commentSpan = peeked.span();

        if (tokens.source.substring(previousTokenSpan.end(), commentSpan.start()).contains("\n")) {
            // The comment is not on the same line.
            return null;
        } else {
            // The comment is on the same line as the previous token (i.e. the value),
            // so this is a proper suffix comment.
            Lexer.Result r = tokens.next();
            PlacedToken token = tokens.ok(r);
            return token.slice();
        }
    }

    private static List<String> parseComments(TokenStream tokens) {
        List<String> comments = new ArrayList<>();
        Lexer.Result peeked;
        while ((peeked = tokens.peek()) != null && peeked.kind() == TokenKind.COMMENT) {
            comments.add(peeked.slice());
            tokens.next(); // Consume the comment token
        }
        return comments;
    }
}
