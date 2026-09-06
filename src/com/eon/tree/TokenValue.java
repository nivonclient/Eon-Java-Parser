package com.eon.tree;

/// The different kinds of values that a {@link TokenTree} can hold.
public sealed interface TokenValue {

    /// {@code null}, {@code true}, {@code false}, or a map key without quotes.
    record Identifier(String slice) implements TokenValue {}

    /// A number, starting with a sign ({@code +}/{@code -}) or a digit.
    record Number(String slice) implements TokenValue {}

    /// A quoted string, keeping its original quotes.
    /// It can be a basic, literal, or multiline string.
    record QuotedString(String slice) implements TokenValue {}

    /// A list, like {@code [ a, b, c, … ]}.
    record ListValue(TokenList list) implements TokenValue {}

    /// A map, like {@code { key: value }}.
    record MapValue(TokenMap map) implements TokenValue {}

    /// A variant, like {@code "Rgb"(255, 0, 0)}.
    record VariantValue(TokenVariant variant) implements TokenValue {}

    default boolean isNumber() { return this instanceof Number; }

    /// Makes a {@link TokenTree} from this value, without comments or a span.
    default TokenTree toTree() { return new TokenTree(null, java.util.List.of(), this, null); }
}