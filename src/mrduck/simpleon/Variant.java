package mrduck.simpleon;

import java.util.Arrays;
import java.util.List;

/// A simple Java class for representing an Eon enum variant,
/// for example "Rgb"(255, 0, 0).
///
/// You can read it with {@link SimpleEon#get} or
/// {@link SimpleEon#getVariant}, and use it with
/// {@link SimpleEon#set(String, Object)}.
///
/// For example:
/// <pre>{@code
/// eon.set("color", Variant.of("Rgb", 255, 0, 0));
/// // -> color: "Rgb"(255, 0, 0)
/// }</pre>
public record Variant(String name, List<Object> args) {
    public static Variant of(String name, Object... args) {
        return new Variant(name, Arrays.asList(args));
    }
}
