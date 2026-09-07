package mrduck.example;

import com.eon.Eon;
import com.eon.EonError;
import com.eon.Strings;
import com.eon.format.FormatOptions;
import com.eon.tree.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException, EonError {
        Path outputPath = Path.of("config_out.eon");

        /// Identifier & Number
        TokenKeyValue nameKv = kv("name", new TokenValue.Identifier("You"));
        TokenKeyValue ageKv = kv("age", new TokenValue.Number("30"));
        TokenKeyValue activeKv = kv("active", new TokenValue.Identifier("true"));

        /// ListValue: [1, 2, 3]
        List<TokenTree> numbers = new ArrayList<>();
        numbers.add(TokenTree.of(new TokenValue.Number("1")));
        numbers.add(TokenTree.of(new TokenValue.Number("2")));
        numbers.add(TokenTree.of(new TokenValue.Number("3")));
        TokenKeyValue tagsKv = kv("tags", new TokenValue.ListValue(new TokenList(numbers, List.of())));

        /// Nested MapValue: address: { city: "HCMC", zip: 70000 }
        TokenKeyValue cityKv = kv("city", new TokenValue.QuotedString(Strings.escapeAndQuote("Where")));
        TokenKeyValue zipKv = kv("zip", new TokenValue.Number("00000"));
        TokenMap addressMap = new TokenMap(List.of(cityKv, zipKv), List.of());
        TokenKeyValue addressKv = kv("address", new TokenValue.MapValue(addressMap));

        /// VariantValue: color: "Rgb"(255, 0, 0)
        List<TokenTree> rgbValues = List.of(
                TokenTree.of(new TokenValue.Number("255")),
                TokenTree.of(new TokenValue.Number("0")),
                TokenTree.of(new TokenValue.Number("0")));
        TokenVariant rgbVariant = new TokenVariant(null, "Rgb", rgbValues, List.of());
        TokenKeyValue colorKv = kv("color", new TokenValue.VariantValue(rgbVariant));

        TokenMap rootMap = new TokenMap(
                List.of(nameKv, ageKv, activeKv, tagsKv, addressKv, colorKv), List.of());
        TokenTree root = TokenTree.of(new TokenValue.MapValue(rootMap));

        /// Format & write to disk
        String eonText = root.format(new FormatOptions());
        Files.writeString(outputPath, eonText);

        /// Read the file and print it
        String written = Files.readString(outputPath);
        System.out.println("\nconfig_out.eon:\n");
        System.out.println(written);

        /// Parse it back and walk the tree
        TokenTree parsed = Eon.parse(written);
        System.out.println("\nParsed values:\n");
        TokenMap parsedMap = ((TokenValue.MapValue) parsed.value).map();
        for (TokenKeyValue entry : parsedMap.keyValues()) {
            String key = ((TokenValue.Identifier) entry.key().value).slice();
            System.out.println(key + " -> " + describe(entry.value().value));
        }

        /// Strings utilities
        System.out.println("\nStrings utilities:\n");
        System.out.println("escapeAndQuote(\"a \\\"b\\\"\") = " + Strings.escapeAndQuote("a \"b\""));
        System.out.println("unescapeAndUnquote(\"\\\"hi\\\\nthere\\\"\") = "
                + Strings.unescapeAndUnquote("\"hi\\nthere\"").replace("\n", "\\n"));
        System.out.println("isValidIdentifier(\"my_key\") = " + Strings.isValidIdentifier("my_key"));
        System.out.println("isValidIdentifier(\"true\") = " + Strings.isValidIdentifier("true"));
    }

    private static TokenKeyValue kv(String key, TokenValue value) {
        return new TokenKeyValue(TokenTree.of(new TokenValue.Identifier(key)), TokenTree.of(value));
    }

    private static String describe(TokenValue value) {
        if (value instanceof TokenValue.Identifier v) return "Identifier(" + v.slice() + ")";
        if (value instanceof TokenValue.Number v) return "Number(" + v.slice() + ")";
        if (value instanceof TokenValue.QuotedString v) return "QuotedString(" + v.slice() + ")";
        if (value instanceof TokenValue.ListValue v) return "List of " + v.list().values().size() + " items";
        if (value instanceof TokenValue.MapValue v) return "Map with " + v.map().keyValues().size() + " keys";
        if (value instanceof TokenValue.VariantValue v)
            return "Variant " + v.variant().quotedName() + " with " + v.variant().values().size() + " args";
        return "?";
    }
}