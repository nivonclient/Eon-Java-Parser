package com.eon.tree;

import java.util.List;

/// An object, like <code>{ key: value, … }</code>
public record TokenMap(List<TokenKeyValue> keyValues, List<String> closingComments) {}
