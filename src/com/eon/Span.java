package com.eon;

/// The character range of something in the source code (like Rust's Span, using char offsets)
public record Span(int start, int end) {
    public static final Span EMPTY = new Span(0, 0);

    public boolean isEmpty() { return start == end; }
    public int len() { return end - start; }

    /// Union of two spans (smallest span containing both)
    public Span or(Span other) {
        return new Span(Math.min(start, other.start), Math.max(end, other.end));
    }
}
