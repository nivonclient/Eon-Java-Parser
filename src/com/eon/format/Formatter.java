package com.eon.format;

import com.eon.tree.*;

import java.util.List;

/// Serializes a {@link TokenTree} to an Eon string
public final class Formatter {
    private Formatter() {}

    public static String format(TokenTree tree, FormatOptions options) {
        State f = new State(options);

        if (!f.options.alwaysIncludeOuterBraces && tree.value instanceof TokenValue.MapValue) {
            TokenMap map = ((TokenValue.MapValue) tree.value).map();
            f.indentedComments(tree.prefixComments);
            f.mapContent(map);
            f.suffixComment(tree.suffixComment);
            return f.finish();
        }

        f.indentedValue(tree);
        return f.finish();
    }

    private static final class State {
        final FormatOptions options;
        int indent = 0;
        final StringBuilder out = new StringBuilder();

        State(FormatOptions options) { this.options = options; }

        String finish() { return out.toString(); }

        void newline() { out.append(options.newline); }

        void addIndent() {
            for (int i = 0; i < indent; i++) out.append(options.indentation);
        }

        void indentedComments(List<String> comments) {
            for (String comment : comments) {
                addIndent();
                out.append(comment);
                newline();
            }
        }

        void indentedValue(TokenTree value) {
            indentedComments(value.prefixComments);
            addIndent();
            value(value.value);
            suffixComment(value.suffixComment);
        }

        void suffixComment(String suffixComment) {
            if (suffixComment != null) {
                out.append(' ');
                out.append(suffixComment);
            }
        }

        void value(TokenValue value) {
            if (value instanceof TokenValue.Identifier v) {
                out.append(v.slice());
            } else if (value instanceof TokenValue.Number v) {
                out.append(v.slice());
            } else if (value instanceof TokenValue.QuotedString v) {
                out.append(v.slice());
            } else if (value instanceof TokenValue.ListValue v) {
                list(v.list());
            } else if (value instanceof TokenValue.MapValue v) {
                map(v.map());
            } else if (value instanceof TokenValue.VariantValue v) {
                variant(v.variant());
            } else {
                throw new IllegalStateException("Unhandled TokenValue: " + value);
            }
        }

        void list(TokenList list) {
            List<TokenTree> values = list.values();
            List<String> closingComments = list.closingComments();

            if (values.isEmpty() && closingComments.isEmpty()) {
                out.append("[]");
                return;
            }

            if (shouldFormatListOnOneLine(list)) {
                out.append('[');
                for (int i = 0; i < values.size(); i++) {
                    value(values.get(i).value);
                    if (i + 1 < values.size()) {
                        out.append(", "); // Commas for single-line lists, just for extra readability.
                    }
                }
                out.append(']');
            } else {
                out.append('[');
                indent += 1;
                newline();
                listContent(list);
                indent -= 1;
                addIndent();
                out.append(']');
            }
        }

        void listContent(TokenList list) {
            List<TokenTree> values = list.values();
            List<String> closingComments = list.closingComments();

            for (int i = 0; i < values.size(); i++) {
                TokenTree value = values.get(i);
                if (i > 0 && !value.prefixComments.isEmpty()) {
                    newline();
                }
                indentedValue(value);
                newline();
            }

            if (!closingComments.isEmpty()) {
                if (!values.isEmpty()) newline();
                indentedComments(closingComments);
            }
        }

        void map(TokenMap map) {
            List<TokenKeyValue> keyValues = map.keyValues();
            List<String> closingComments = map.closingComments();

            if (keyValues.isEmpty() && closingComments.isEmpty()) {
                out.append("{}");
                return;
            }

            out.append('{');
            indent += 1;
            newline();
            mapContent(map);
            indent -= 1;
            addIndent();
            out.append('}');
        }

        void mapContent(TokenMap map) {
            List<TokenKeyValue> keyValues = map.keyValues();
            List<String> closingComments = map.closingComments();

            for (int i = 0; i < keyValues.size(); i++) {
                TokenKeyValue kv = keyValues.get(i);
                if (i > 0 && !kv.key().prefixComments.isEmpty()) {
                    newline();
                }
                indentedKeyValue(kv);
                newline();
            }

            if (!closingComments.isEmpty()) {
                if (!keyValues.isEmpty()) newline();
                indentedComments(closingComments);
            }
        }

        void indentedKeyValue(TokenKeyValue keyValue) {
            TokenTree key = keyValue.key();
            TokenTree value = keyValue.value();
            indentedComments(key.prefixComments);
            indentedComments(value.prefixComments);
            addIndent();
            value(key.value);
            out.append(options.keyValueSeparator);
            value(value.value);
            suffixComment(value.suffixComment);
        }

        void variant(TokenVariant variant) {
            String quotedName = variant.quotedName();
            List<TokenTree> values = variant.values();
            List<String> closingComments = variant.closingComments();

            if (values.isEmpty() && closingComments.isEmpty()) {
                out.append(quotedName); // Omit parentheses if no values.
                return;
            }

            TokenValue onlyValue = values.size() == 1 ? values.get(0).value : null;

            if (shouldFormatVariantOnOneLine(variant)) {
                out.append(quotedName);
                out.append('(');
                for (int i = 0; i < values.size(); i++) {
                    value(values.get(i).value);
                    if (i + 1 < values.size()) {
                        out.append(", "); // Commas for single-line variants, just for extra readability.
                    }
                }
                out.append(')');
            } else if (closingComments.isEmpty() && onlyValue instanceof TokenValue.MapValue) {
                TokenMap map = ((TokenValue.MapValue) onlyValue).map();
                if (map.keyValues().isEmpty() && map.closingComments().isEmpty()) {
                    out.append(quotedName);
                    out.append("({ })");
                } else {
                    // A single map variant, like `"VariantName"({ key: value, … })`.
                    // Avoid double-indenting for nicer/more compact output.
                    out.append(quotedName);
                    out.append("({");
                    indent += 1;
                    newline();
                    mapContent(map);
                    indent -= 1;
                    addIndent();
                    out.append("})");
                }
            } else if (closingComments.isEmpty() && onlyValue instanceof TokenValue.ListValue) {
                TokenList list = ((TokenValue.ListValue) onlyValue).list();
                if (list.values().isEmpty() && list.closingComments().isEmpty()) {
                    out.append(quotedName);
                    out.append("([ ])");
                } else {
                    // A single list variant, like `"VariantName"([a, b, …])`.
                    // Avoid double-indenting for nicer/more compact output.
                    out.append(quotedName);
                    out.append("([");
                    indent += 1;
                    newline();
                    listContent(list);
                    indent -= 1;
                    addIndent();
                    out.append("])");
                }
            } else {
                out.append(quotedName);
                out.append('(');
                indent += 1;
                newline();
                for (int i = 0; i < values.size(); i++) {
                    if (i > 0 && !values.get(i).prefixComments.isEmpty()) {
                        newline();
                    }
                    newline();
                }

                if (!closingComments.isEmpty()) {
                    if (!values.isEmpty()) newline();
                    indentedComments(closingComments);
                }

                indent -= 1;
                addIndent();
                out.append(')');
            }
        }
    }

    private static boolean shouldFormatListOnOneLine(TokenList list) {
        return list.closingComments().isEmpty() && shouldFormatValuesOnOneLine(list.values());
    }

    private static boolean shouldFormatVariantOnOneLine(TokenVariant variant) {
        return variant.closingComments().isEmpty() && shouldFormatValuesOnOneLine(variant.values());
    }

    private static boolean shouldFormatValuesOnOneLine(List<TokenTree> values) {
        if (!values.stream().allMatch(Formatter::isSimple)) {
            return false;
        }

        if (values.size() <= 4 && values.stream().allMatch(tt -> tt.value.isNumber())) {
            return true; // e.g. [1 2 3 4]
        }

        if (values.size() > 4) {
            return false;
        }

        int estimatedWidth = 0;
        for (TokenTree value : values) {
            if (value.value instanceof TokenValue.QuotedString) {
                estimatedWidth += ((TokenValue.QuotedString) value.value).slice().length();
            } else {
                estimatedWidth += 5;
            }
            estimatedWidth += 2;
        }

        return estimatedWidth < 60;
    }

    private static boolean isSimple(TokenTree value) {
        if (!value.prefixComments.isEmpty() || value.suffixComment != null) {
            return false;
        }
        TokenValue v = value.value;
        if (v instanceof TokenValue.Identifier || v instanceof TokenValue.Number) {
            return true;
        } else if (v instanceof TokenValue.QuotedString qs) {
            return !qs.slice().contains("\n");
        } else if (v instanceof TokenValue.ListValue lv) {
            return lv.list().values().isEmpty() && lv.list().closingComments().isEmpty();
        } else if (v instanceof TokenValue.MapValue mv) {
            return mv.map().keyValues().isEmpty() && mv.map().closingComments().isEmpty();
        } else if (v instanceof TokenValue.VariantValue vv) {
            return vv.variant().values().isEmpty() && vv.variant().closingComments().isEmpty();
        } else {
            throw new IllegalStateException("Unhandled TokenValue: " + v);
        }
    }
}