package mrduck.simpleon;

import com.eon.Eon;
import com.eon.EonError;
import com.eon.Strings;
import com.eon.format.FormatOptions;
import com.eon.tree.*;

import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// This class is like a simple, friendly cover on top of the eon-syntax library.
///
/// The normal way (with `com.eon.Eon` and `TokenTree`) let you see everything inside the
/// document, like comment, position of each word, and all different kind of value. But
/// most of the time, we don't need all of that. We just want to think of the document
/// like a normal map: some key, and some value. So this class do that for you — it think
/// of an Eon document as key -> value, where value can be normal Java thing like String,
/// Long, Double, Boolean, List, another map inside, or [Variant].
///
/// Also, this class don't make you write try/catch for [EonError] or [IOException] all the
/// time. If something go wrong, it just throw [EonError], which is unchecked, so
/// your code can stay simple.
///
/// You can also use key with dot inside, like `"server.address.port"`, and it will go
/// inside the nested map for you automatically.
///
/// A small example, so you can see how it look:
/// ```
/// // make and save
/// SimpleEon config = SimpleEon.create()
///         .set("name", "Viet")
///         .set("age", 30)
///         .set("tags", List.of(1, 2, 3))
///         .set("address.city", "HCMC")   // this make the "address" map by itself
///         .set("color", Variant.of("Rgb", 255, 0, 0));
/// config.save("config.eon");
///
/// // load and read back
/// SimpleEon loaded = SimpleEon.load("config.eon");
/// String name = loaded.getString("name");
/// int age = loaded.getInt("age");
/// String city = loaded.getString("address.city");
/// ```
///
/// One thing to know: this class only work when the top of the document is a map (that
/// mean, `key: value` pairs). If your document is just a list, or a single value, or a
/// variant, at the very top, then this class cannot help you — please use
/// [com.eon.Eon#parse] by itself in that case.
///
/// And if you need something this class don't have, like reading comment or looking at
/// position of a value, you can still go down to the original tree with [#rawTree()]. It
/// give you the same [TokenTree] that the low level part of the library use.
public final class SimpleEon {

    private final TokenTree root; // root.value should always be a TokenValue.MapValue
    private FormatOptions formatOptions = new FormatOptions();

    private SimpleEon(TokenTree root) {
        this.root = root;
    }

    // ======================================================================
    // Creation
    // ======================================================================

    /// Make a new document, with nothing inside yet.
    public static SimpleEon create() {
        return new SimpleEon(TokenTree.of(new TokenValue.MapValue(newMap())));
    }

    /// Read an Eon text and turn it into a document. Remember, the top level part of the
    /// text need to be a map, not a list or a single value.
    public static SimpleEon parse(String source) {
        TokenTree tree;
        try {
            tree = Eon.parse(source);
        } catch (EonError e) {
            throw new SimpleEonException("Failed to parse Eon source: " + e.getMessage(), e);
        }
        if (!(tree.value instanceof TokenValue.MapValue)) {
            throw new SimpleEonException(
                    "SimpleEon only supports documents whose top level is a map (key: value pairs). "
                            + "For a top-level list/value/variant, use com.eon.Eon.parse(...) directly.");
        }
        return new SimpleEon(tree);
    }

    /// Open a file and read it, same as [#parse(String)] but from a file path.
    public static SimpleEon load(Path path) {
        String text;
        try {
            text = Files.readString(path);
        } catch (IOException e) {
            throw new SimpleEonException("Failed to read " + path, e);
        }
        return parse(text);
    }

    /// Same thing as [#load(Path)], just with a String path instead, so you don't need to
    /// make a Path yourself.
    public static SimpleEon load(String path) {
        return load(Path.of(path));
    }

    /// Take a normal Java map and turn it into a document. Every value inside the map need
    /// to be something [#set(String, Object)] know how to handle, or it will complain.
    public static SimpleEon fromMap(Map<String, ?> data) {
        return create().setAll(data);
    }

    // ======================================================================
    // Reading
    // ======================================================================

    /// Tell you `true` when there is something at that path (the path can have dot inside,
    /// like `"a.b.c"`, to go into nested map).
    public boolean has(String path) {
        return findLeaf(path) != null;
    }

    /// Give you back the value at `path`, as a normal Java object — could be `String`,
    /// `Long`, `Double`, `Boolean`, `null`, a `List<Object>`, a `Map<String,Object>`, or a
    /// [Variant]. If nothing is there, it just give `null` back, no error.
    public Object get(String path) {
        TokenTree leaf = findLeaf(path);
        return leaf == null ? null : toObject(leaf.value);
    }

    /// Same as [#get(String)], but if nothing is found, it give you `fallback` instead of
    /// `null`.
    public Object get(String path, Object fallback) {
        TokenTree leaf = findLeaf(path);
        return leaf == null ? fallback : toObject(leaf.value);
    }

    public String getString(String path) {
        return (String) require(path, String.class);
    }

    public String getString(String path, String fallback) {
        Object v = get(path);
        return v instanceof String s ? s : fallback;
    }

    public long getLong(String path) {
        return ((Number) require(path, Number.class)).longValue();
    }

    public long getLong(String path, long fallback) {
        Object v = get(path);
        return v instanceof Number n ? n.longValue() : fallback;
    }

    public int getInt(String path) {
        return (int) getLong(path);
    }

    public int getInt(String path, int fallback) {
        return (int) getLong(path, fallback);
    }

    public double getDouble(String path) {
        return ((Number) require(path, Number.class)).doubleValue();
    }

    public double getDouble(String path, double fallback) {
        Object v = get(path);
        return v instanceof Number n ? n.doubleValue() : fallback;
    }

    public boolean getBoolean(String path) {
        return (Boolean) require(path, Boolean.class);
    }

    public boolean getBoolean(String path, boolean fallback) {
        Object v = get(path);
        return v instanceof Boolean b ? b : fallback;
    }

    public Variant getVariant(String path) {
        return (Variant) require(path, Variant.class);
    }

    @SuppressWarnings("unchecked")
    public List<Object> getList(String path) {
        return (List<Object>) require(path, List.class);
    }

    public List<String> getStringList(String path) {
        return castEach(getList(path), String.class);
    }

    public List<Long> getLongList(String path) {
        List<Long> out = new ArrayList<>();
        for (Object o : getList(path)) out.add(((Number) o).longValue());
        return out;
    }

    public List<Double> getDoubleList(String path) {
        List<Double> out = new ArrayList<>();
        for (Object o : getList(path)) out.add(((Number) o).doubleValue());
        return out;
    }

    /// Give you back the nested map at `path`, as another `SimpleEon`, so you can keep
    /// working with it the same way. If the map (or the maps on the way to it) don't
    /// exist yet, this method will just make them for you — you don't have to check first.
    ///
    /// One more thing: the `SimpleEon` you get back is not a copy. It point to the same
    /// place inside the document. So if you change something through it, the original
    /// document change too, and the other way around also true.
    public SimpleEon getEon(String path) {
        TokenTree current = root;
        for (String part : path.split("\\.")) {
            TokenMap currentMap = ((TokenValue.MapValue) current.value).map();
            TokenTree next = findInMap(currentMap, part);
            if (next != null && next.value instanceof TokenValue.MapValue) {
                current = next;
            } else if (next == null) {
                TokenTree child = TokenTree.of(new TokenValue.MapValue(newMap()));
                putInMap(currentMap, part, child);
                current = child;
            } else {
                throw new SimpleEonException("Key '" + part + "' in path '" + path + "' is not a map");
            }
        }
        return new SimpleEon(current);
    }

    /// Give you the keys that are at the top of this document (or, if this is a nested
    /// view from [#getEon(String)], the top of that nested part). The order is kept the
    /// same as how they appear.
    public Set<String> keys() {
        Set<String> result = new LinkedHashSet<>();
        for (TokenKeyValue kv : map().keyValues()) result.add(keyToString(kv.key()));
        return result;
    }

    public int size() {
        return map().keyValues().size();
    }

    public boolean isEmpty() {
        return map().keyValues().isEmpty();
    }

    /// Turn the whole document into a normal `Map<String, Object>`, going down into every
    /// nested map and list too.
    ///
    /// There is one small thing to be careful about: an identifier value written without
    /// quote, like `status: Active`, will become just a plain Java `String` — same as if
    /// it was written `status: "Active"` with quote. So once you turn it into a map, you
    /// cannot tell anymore which one it was. If you turn this map back with [#fromMap],
    /// that value will now come out quoted, like `"Active"`. If you don't want that, use
    /// [Ident#of] when you write the value, instead of a plain `String`.
    @SuppressWarnings("unchecked")
    public Map<String, Object> toMap() {
        return (Map<String, Object>) toObject(root.value);
    }

    // Writing

    /// Put a value at `path`, making any map that is missing on the way there. This method
    /// can take a `String`, any kind of boxed number, `Boolean`, `null`, a `List<?>`, an
    /// array, a `Map<String,?>`, another [SimpleEon], a [Variant], or an [Ident]. It give
    /// back `this`, so you can chain many `set` call one after another.
    public SimpleEon set(String path, Object value) {
        String[] parts = path.split("\\.");
        TokenMap current = map();
        for (int i = 0; i < parts.length - 1; i++) {
            TokenTree next = findInMap(current, parts[i]);
            if (next != null && next.value instanceof TokenValue.MapValue mv) {
                current = mv.map();
            } else {
                TokenMap child = newMap();
                putInMap(current, parts[i], TokenTree.of(new TokenValue.MapValue(child)));
                current = child;
            }
        }
        putInMap(current, parts[parts.length - 1], TokenTree.of(toTokenValue(value)));
        return this;
    }

    /// Just call [#set(String, Object)] one time for every entry inside `data`. Give back
    /// `this`, so you can chain it too.
    public SimpleEon setAll(Map<String, ?> data) {
        for (Map.Entry<String, ?> e : data.entrySet()) set(e.getKey(), e.getValue());
        return this;
    }

    /// Take away the value at `path`, if there is one (if not, nothing happen, no error).
    /// Give back `this`, so you can chain it too.
    public SimpleEon remove(String path) {
        String[] parts = path.split("\\.");
        TokenMap current = map();
        for (int i = 0; i < parts.length - 1; i++) {
            TokenTree next = findInMap(current, parts[i]);
            if (next == null || !(next.value instanceof TokenValue.MapValue mv)) return this;
            current = mv.map();
        }
        String last = parts[parts.length - 1];
        current.keyValues().removeIf(kv -> keyMatches(kv.key(), last));
        return this;
    }

    // Output

    /// Change the [FormatOptions] that [#toText()] and [#save] will use, if you want the
    /// output to look a bit different (for example, different indent).
    public SimpleEon withFormatOptions(FormatOptions options) {
        this.formatOptions = options;
        return this;
    }

    /// Turn this document back into Eon text, the same kind of text you would write in a
    /// `.eon` file.
    public String toText() {
        return root.format(formatOptions);
    }

    public void save(Path path) {
        try {
            Files.writeString(path, toText());
        } catch (IOException e) {
            throw new SimpleEonException("Failed to write " + path, e);
        }
    }

    public void save(String path) {
        save(Path.of(path));
    }

    @Override
    public String toString() {
        return toText();
    }

    /// If you need more than this class can give you (like comment on a value, or the
    /// position inside the text) this method let you go down to the full, low level tree
    /// that this class is built on top of.
    public TokenTree rawTree() {
        return root;
    }

    // Internals

    private static TokenMap newMap() {
        return new TokenMap(new ArrayList<>(), new ArrayList<>());
    }

    private TokenMap map() {
        return ((TokenValue.MapValue) root.value).map();
    }

    private TokenTree findLeaf(String path) {
        String[] parts = path.split("\\.");
        TokenMap current = map();
        for (int i = 0; i < parts.length - 1; i++) {
            TokenTree next = findInMap(current, parts[i]);
            if (next == null || !(next.value instanceof TokenValue.MapValue mv)) return null;
            current = mv.map();
        }
        return findInMap(current, parts[parts.length - 1]);
    }

    private static TokenTree findInMap(TokenMap map, String key) {
        for (TokenKeyValue kv : map.keyValues()) {
            if (keyMatches(kv.key(), key)) return kv.value();
        }
        return null;
    }

    /// Put `key -> value` inside the map. If the key already there, we just change the
    /// value on the spot, so any comment that was already on that entry stay how it was.
    /// If the key is new, we add it at the end
    private static void putInMap(TokenMap map, String key, TokenTree valueTree) {
        for (TokenKeyValue kv : map.keyValues()) {
            if (keyMatches(kv.key(), key)) {
                kv.value().value = valueTree.value;
                return;
            }
        }
        map.keyValues().add(new TokenKeyValue(TokenTree.of(makeKeyValue(key)), valueTree));
    }

    private static boolean keyMatches(TokenTree keyTree, String key) {
        return keyToString(keyTree).equals(key);
    }

    private static String keyToString(TokenTree keyTree) {
        if (keyTree.value instanceof TokenValue.Identifier id) return id.slice();
        if (keyTree.value instanceof TokenValue.QuotedString qs) {
            try {
                return Strings.unescapeAndUnquote(qs.slice());
            } catch (EonError e) {
                return qs.slice();
            }
        }
        return String.valueOf(keyTree.value);
    }

    private static TokenValue makeKeyValue(String key) {
        return Strings.isValidIdentifier(key)
                ? new TokenValue.Identifier(key)
                : new TokenValue.QuotedString(Strings.escapeAndQuote(key));
    }

    private Object require(String path, Class<?> type) {
        TokenTree leaf = findLeaf(path);
        if (leaf == null) {
            throw new SimpleEonException("Missing key: '" + path + "'");
        }
        Object value = toObject(leaf.value);
        if (!type.isInstance(value)) {
            throw new SimpleEonException("Key '" + path + "' is not a " + type.getSimpleName()
                    + " (was " + (value == null ? "null" : value.getClass().getSimpleName()) + ")");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> castEach(List<Object> list, Class<T> type) {
        List<T> out = new ArrayList<>(list.size());
        for (Object o : list) out.add((T) o);
        return out;
    }

    /// turn a TokenValue into a normal Java object

    private static Object toObject(TokenValue value) {
        if (value instanceof TokenValue.Identifier id) {
            return switch (id.slice()) {
                case "true" -> Boolean.TRUE;
                case "false" -> Boolean.FALSE;
                case "null" -> null;
                default -> id.slice(); // just a plain identifier without quote: give the text back
            };
        } else if (value instanceof TokenValue.Number num) {
            return parseNumber(num.slice());
        } else if (value instanceof TokenValue.QuotedString qs) {
            try {
                return Strings.unescapeAndUnquote(qs.slice());
            } catch (EonError e) {
                throw new SimpleEonException("Invalid string literal: " + qs.slice(), e);
            }
        } else if (value instanceof TokenValue.ListValue lv) {
            List<Object> list = new ArrayList<>();
            for (TokenTree tt : lv.list().values()) list.add(toObject(tt.value));
            return list;
        } else if (value instanceof TokenValue.MapValue mv) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (TokenKeyValue kv : mv.map().keyValues()) {
                map.put(keyToString(kv.key()), toObject(kv.value().value));
            }
            return map;
        } else if (value instanceof TokenValue.VariantValue vv) {
            List<Object> args = new ArrayList<>();
            for (TokenTree tt : vv.variant().values()) args.add(toObject(tt.value));
            String name;
            try {
                name = Strings.unescapeAndUnquote(vv.variant().quotedName());
            } catch (EonError e) {
                name = vv.variant().quotedName();
            }
            return new Variant(name, args);
        }
        throw new IllegalStateException("Unhandled TokenValue: " + value);
    }

    // try to read the number text as Long first, then Double, and if none of them work,
    // we just give the original text back — better than throwing an error for something
    // small like this
    private static Object parseNumber(String slice) {
        String s = slice.replace("_", "");
        boolean neg = s.startsWith("-");
        String unsigned = (s.startsWith("+") || s.startsWith("-")) ? s.substring(1) : s;

        if (unsigned.equals("inf")) return neg ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        if (unsigned.equals("nan")) return Double.NaN;

        try {
            String lower = unsigned.toLowerCase(java.util.Locale.ROOT);
            if (lower.startsWith("0x")) {
                long v = Long.parseLong(lower.substring(2), 16);
                return neg ? -v : v;
            }
            if (s.indexOf('.') >= 0 || lower.indexOf('e') >= 0) {
                return Double.parseDouble(s);
            }
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e2) {
                return slice; // this text is not really a number we know, so just keep it as it is
            }
        }
    }

    // ---- turn a normal Java object into a TokenValue ----

    private static TokenValue toTokenValue(Object value) {
        if (value == null) {
            return new TokenValue.Identifier("null");
        } else if (value instanceof TokenValue tv) {
            return tv; // in case someone already has a TokenValue and want to use it directly
        } else if (value instanceof Boolean b) {
            return new TokenValue.Identifier(b ? "true" : "false");
        } else if (value instanceof Ident ident) {
            return new TokenValue.Identifier(ident.name());
        } else if (value instanceof Variant variant) {
            List<TokenTree> args = new ArrayList<>();
            for (Object arg : variant.args()) args.add(TokenTree.of(toTokenValue(arg)));
            return new TokenValue.VariantValue(
                    new TokenVariant(null, Strings.escapeAndQuote(variant.name()), args, new ArrayList<>()));
        } else if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d)) return new TokenValue.Number("+nan");
            if (Double.isInfinite(d)) return new TokenValue.Number(d > 0 ? "+inf" : "-inf");
            return new TokenValue.Number(String.valueOf(d));
        } else if (value instanceof Number n) {
            return new TokenValue.Number(String.valueOf(n.longValue()));
        } else if (value instanceof String s) {
            return new TokenValue.QuotedString(Strings.escapeAndQuote(s));
        } else if (value instanceof SimpleEon nested) {
            return nested.root.value;
        } else if (value instanceof Map<?, ?> m) {
            TokenMap map = newMap();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String key = String.valueOf(e.getKey());
                map.keyValues().add(new TokenKeyValue(
                        TokenTree.of(makeKeyValue(key)), TokenTree.of(toTokenValue(e.getValue()))));
            }
            return new TokenValue.MapValue(map);
        } else if (value instanceof List<?> list) {
            List<TokenTree> items = new ArrayList<>();
            for (Object o : list) items.add(TokenTree.of(toTokenValue(o)));
            return new TokenValue.ListValue(new TokenList(items, new ArrayList<>()));
        } else if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            List<Object> boxed = new ArrayList<>(len);
            for (int i = 0; i < len; i++) boxed.add(Array.get(value, i));
            return toTokenValue(boxed);
        } else {
            throw new SimpleEonException("Unsupported value type for set(): " + value.getClass());
        }
    }
}