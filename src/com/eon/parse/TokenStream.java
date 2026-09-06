package com.eon.parse;

import com.eon.EonError;
import com.eon.Lexer;
import com.eon.Span;
import com.eon.TokenKind;

/// A peekable stream of tokens, with helpers for building spanned parse errors
final class TokenStream {
    final String source;
    private final Lexer lexer;
    private Lexer.Result peeked;
    private boolean hasPeeked;
    private Span lastSpan = Span.EMPTY;

    TokenStream(String source) {
        this.source = source;
        this.lexer = new Lexer(source);
    }

    EonError errorAt(Span span, String message) {
        return EonError.at(source, span, message);
    }

    /// Peeks the next token without consuming it. Returns {@code null} at end of input
    Lexer.Result peek() {
        if (!hasPeeked) {
            peeked = lexer.next();
            hasPeeked = true;
        }
        return peeked;
    }

    boolean peekIs(TokenKind kind) {
        Lexer.Result p = peek();
        return p != null && p.kind() == kind;
    }

    /// Span of the token {@link #peek()} would return, or {@link #spanOfPrevious()} if none
    Span spanOfNext() {
        Lexer.Result p = peek();
        return p != null ? p.span() : lastSpan;
    }

    /// Span of the most recent token returned by {@link #next()}
    Span spanOfPrevious() { return lastSpan; }

    Span endSpan() { return new Span(source.length(), source.length()); }

    /// Consumes and returns the next raw lex result, or {@code null} at end of input
    Lexer.Result next() {
        Lexer.Result result;
        if (hasPeeked) {
            result = peeked;
            hasPeeked = false;
            peeked = null;
        } else {
            result = lexer.next();
        }
        if (result != null) {
            lastSpan = result.span();
        }
        return result;
    }

    /// Converts a raw lex result into a validated token, or throws if it was invalid
    PlacedToken ok(Lexer.Result r) throws EonError {
        if (r.kind() == null) {
            throw errorAt(r.span(), "Invalid token: '" + r.slice() + "'");
        }
        return new PlacedToken(r.span(), r.slice(), r.kind());
    }
}
