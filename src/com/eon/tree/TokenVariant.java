package com.eon.tree;

import com.eon.Span;

import java.util.List;

/// A sum-type (enum) variant, like {@code "Rgb"(255, 0, 0)}
public record TokenVariant(Span nameSpan, String quotedName, List<TokenTree> values, List<String> closingComments) {}
