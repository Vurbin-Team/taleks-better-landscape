package taleksbetterlandscape.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Typed, validated view over a .properties file. Defaults live in ONE place (the bundled default
 * file); the user file only overrides. Bad values throw with the key name, so a typo fails loudly
 * instead of producing a garbage world.
 */
public final class TerrainConfig {
    private final Properties props;

    private TerrainConfig(Properties props) {
        this.props = props;
    }

    /** user may be null. */
    public static TerrainConfig parse(Reader defaults, Reader user) throws IOException {
        Properties d = new Properties();
        d.load(defaults);
        Properties p = new Properties(d);
        if (user != null) {
            p.load(user);
        }
        return new TerrainConfig(p);
    }

    public static TerrainConfig load(InputStream defaults, Path userFile) throws IOException {
        try (Reader d = new InputStreamReader(defaults, StandardCharsets.UTF_8)) {
            if (Files.notExists(userFile)) {
                return parse(d, null);
            }
            try (Reader u = Files.newBufferedReader(userFile, StandardCharsets.UTF_8)) {
                return parse(d, u);
            }
        }
    }

    public double number(String key, double min, double max) {
        String v = raw(key);
        double x;
        try {
            x = Double.parseDouble(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("config '" + key + "' must be a number, got '" + v + "'");
        }
        if (!(x >= min && x <= max)) {
            throw new IllegalArgumentException("config '" + key + "' must be in [" + min + ", " + max + "], got " + v);
        }
        return x;
    }

    public long integer(String key) {
        String v = raw(key);
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("config '" + key + "' must be a whole number, got '" + v + "'");
        }
    }

    public List<String> list(String key) {
        List<String> out = new ArrayList<>();
        for (String s : raw(key).split(",")) {
            if (!s.isBlank()) {
                out.add(s.trim());
            }
        }
        return out;
    }

    private String raw(String key) {
        String v = props.getProperty(key);
        if (v == null) {
            throw new IllegalArgumentException("missing config key '" + key + "'");
        }
        return v.trim();
    }
}
