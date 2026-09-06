package mrduck.simpleon;

/// By default, {@link SimpleEon#set(String, Object)} writes a Java {@code String}
/// with quotes, for example {@code "Active"}.
///
/// To write it as a bare identifier without quotes, use {@code Ident} instead:
/// <pre>{@code
/// eon.set("status", Ident.of("Active")); // -> status: Active
/// eon.set("status", "Active");           // -> status: "Active"
/// }</pre>
public record Ident(String name) {
    public static Ident of(String name) {
        return new Ident(name);
    }
}
