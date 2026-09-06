package com.eon.format;


public final class FormatOptions {
    /// Default: "\t"
    public String indentation = "\t";

    /// Default: "\n"
    public String newline = "\n";

    /// Default: " "
    public String spaceBeforeSuffixComment = " ";

    /// Default: ": "
    public String keyValueSeparator = ": ";

    /// Surround the top-level map in { } with an extra level of indentation. */
    public boolean alwaysIncludeOuterBraces = false;

    /// Create a new [`FormatOptions`] with the default values.
    public FormatOptions() {}
    /// Set the indentation string.
    public FormatOptions withIndentation(String indentation) { this.indentation = indentation; return this; }
    /// Set the newline string.
    public FormatOptions withNewline(String newline) { this.newline = newline; return this; }
}
