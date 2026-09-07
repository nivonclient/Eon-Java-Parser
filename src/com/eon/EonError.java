package com.eon;

/// An error that can occur during parsing of an Eon file
public final class EonError extends RuntimeException {

    private EonError(String source, Span span, String msg) {
        super(render(source, span, msg));
    }

    private EonError(String source, Span span, String msg, Throwable cause) {
        super(render(source, span, msg), cause);
    }

    public static EonError custom(String message) {
        return new EonError(null, null, message);
    }

    public static EonError at(String eonSource, Span span, String message) {
        return new EonError(eonSource, span, message);
    }

    public static EonError at(String eonSource, Span span, java.util.function.Supplier<String> message) {
        return new EonError(eonSource, span, message.get());
    }

    /// Optionally-spanned error: if {@code span} is null, behaves like {@link #custom}
    public static EonError of(String eonSource, Span span, String message) {
        return span != null ? at(eonSource, span, message) : custom(message);
    }

    private static String render(String source, Span span, String msg) {
        if (source == null || span == null) {
            return msg;
        }
        int lineStart = source.lastIndexOf('\n', Math.max(0, span.start() - 1)) + 1;
        int lineEndSearch = source.indexOf('\n', span.start());
        int lineEnd = lineEndSearch == -1 ? source.length() : lineEndSearch;

        // Compute 1-based line/column of the span start
        int line = 1;
        for (int i = 0; i < lineStart; i++) {
            if (source.charAt(i) == '\n') line++;
        }
        int col = span.start() - lineStart + 1;

        String lineText = source.substring(lineStart, lineEnd);
        int caretLen = Math.max(1, Math.min(span.end(), lineEnd) - span.start());

        StringBuilder sb = new StringBuilder();
        sb.append("Error: ").append(msg).append('\n');
        sb.append("   --> line ").append(line).append(", column ").append(col).append('\n');
        String lineNoStr = String.valueOf(line);
        String pad = " ".repeat(lineNoStr.length());
        sb.append(pad).append(" |\n");
        sb.append(lineNoStr).append(" | ").append(lineText).append('\n');
        sb.append(pad).append(" | ").repeat(" ", Math.max(0, col - 1)).repeat("^", caretLen).append(' ').append(msg);
        return sb.toString();
    }

    @Override
    public String toString() { return getMessage(); }
}