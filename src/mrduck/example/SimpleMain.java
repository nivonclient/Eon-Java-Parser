package mrduck.example;

import mrduck.simpleon.Ident;
import mrduck.simpleon.SimpleEon;
import mrduck.simpleon.Variant;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class SimpleMain {
    public static void main(String[] args) {
        // Build the config with set()
        /// Dot paths can also be used to create nested maps automatically.
        SimpleEon config = SimpleEon.create()
                .set("name", "You")
                .set("age", 30)
                .set("score", 9.5)
                .set("active", true)
                .set("status", Ident.of("Active"))            /// writes without quotes
                .set("tags", List.of(1, 2, 3))
                .set("address.city", "Whenre")                      /// creates "address" automatically
                .set("address.zip", 70000)
                .set("color", Variant.of("Rgb", 255, 0, 0));

        Path out = Path.of("config_out_simple.eon");
        config.save(out);

        System.out.println("===== config_out.eon =====");
        System.out.println(config.toText());

        // Load it back
        SimpleEon loaded = SimpleEon.load(out);

        System.out.println("===== Typed getters =====");
        System.out.println("name = " + loaded.getString("name"));
        System.out.println("age = " + loaded.getInt("age"));
        System.out.println("score = " + loaded.getDouble("score"));
        System.out.println("active = " + loaded.getBoolean("active"));
        System.out.println("status = " + loaded.getString("status"));
        System.out.println("tags = " + loaded.getLongList("tags"));
        System.out.println("address.city (dot-path) = " + loaded.getString("address.city"));
        System.out.println("missing.key default = " + loaded.getString("missing.key", "fallback"));

        // Get a nested view with getEon()
        /// The returned object still works on the same document.
        SimpleEon address = loaded.getEon("address");
        System.out.println("address.zip via nested view = " + address.getInt("zip"));
        address.set("country", "UFO"); // also changes the original document
        System.out.println("address.country after nested set = " + loaded.getString("address.country"));

        // work with a Variant
        Variant color = loaded.getVariant("color");
        System.out.println("color = " + color.name() + color.args());

        // Convert to a Map and back
        /// An unquoted identifier like status: Active becomes a plain String in toMap().
        /// Because of this, fromMap() writes it with quotes again.
        /// The other values can still be written back normally.
        Map<String, Object> asMap = loaded.toMap();
        System.out.println("toMap() = " + asMap);
        SimpleEon fromMap = SimpleEon.fromMap(asMap);
        System.out.println("fromMap().toText():");
        System.out.println(fromMap.toText());

        // keys(), size(), has(), and remove()
        System.out.println("keys() = " + loaded.keys());
        System.out.println("size() = " + loaded.size());
        System.out.println("has('name') = " + loaded.has("name"));
        loaded.remove("score");
        System.out.println("has('score') after remove = " + loaded.has("score"));
    }
}