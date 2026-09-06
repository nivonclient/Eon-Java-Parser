package com.eon;

import com.eon.format.FormatOptions;
import com.eon.parse.Parser;
import com.eon.tree.TokenTree;

/// Eon is a configuration format made to be easy to read and write.
///
/// <pre>
/// // Comment
/// string: "Hello Eon!"
/// list: [1, 2, 3]
/// object: {
///     boolean: true
///     regex: '\d{3}-\d{3}-\d{4}'
/// }
/// map: {
///     1: "map keys don't need to be strings"
///     2: "they can be any Eon value"
/// }
/// special_floats: [+inf, -inf, +nan]
/// </pre>
///
/// This is a Java port of the {@code eon_syntax} Rust crate.
/// It can be used to read and write Eon documents, while keeping
/// their comments and some formatting information.
public final class Eon {
    private Eon() {}

    /// Parses an Eon document into a {@link TokenTree}.
    public static TokenTree parse(String eonSource) throws EonError {
        return Parser.parseStr(eonSource);
    }

    /// Parses an Eon document and formats it again with the given options.
    ///
    /// An {@link EonError} is thrown if the source is not valid Eon.
    public static String reformat(String eonSource, FormatOptions options) throws EonError {
        return Parser.parseStr(eonSource).format(options);
    }
}