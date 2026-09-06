package com.eon.parse;

import com.eon.Span;
import com.eon.TokenKind;

/// A validated token together with its span and source slice
record PlacedToken(Span span, String slice, TokenKind kind) {}
