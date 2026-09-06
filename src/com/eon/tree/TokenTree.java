package com.eon.tree;

import com.eon.Span;
import com.eon.format.FormatOptions;
import com.eon.format.Formatter;

import java.util.ArrayList;
import java.util.List;

/// A tree of tokens that represents the structure of an Eon document,
/// including its comments.
///
/// It is somewhere between a Concrete Syntax Tree and an Abstract Syntax Tree.
/// It keeps comments, but does not keep whitespace or some optional tokens
/// like trailing commas. These can still be found from {@link #span} and
/// the original source in some cases.
public final class TokenTree {
    /// The part of the source code covered by this tree, if known.
    public Span span;

    /// Comments on the lines before this value.
    /// For example, {@code // like this}.
    public List<String> prefixComments;

    /// The actual value of this tree.
    public TokenValue value;

    /// A comment after the value on the same line.
    /// For example: {@code value // like this}.
    public String suffixComment;

    public TokenTree(Span span, List<String> prefixComments, TokenValue value, String suffixComment) {
        this.span = span;
        this.prefixComments = prefixComments != null ? new ArrayList<>(prefixComments) : new ArrayList<>();
        this.value = value;
        this.suffixComment = suffixComment;
    }

    public static TokenTree of(TokenValue value) {
        return new TokenTree(null, new ArrayList<>(), value, null);
    }

    /// Formats this tree back into an Eon string.
    public String format(FormatOptions options) {
        return Formatter.format(this, options);
    }
}