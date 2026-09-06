package com.eon.tree;

import java.util.List;

/// A list, like {@code [ a, b, c, … ]}
public record TokenList(List<TokenTree> values, List<String> closingComments) {}
