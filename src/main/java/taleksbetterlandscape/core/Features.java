package taleksbetterlandscape.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Feature registry. Other mods (or new classes in this one) call register() once; the config's
 * "features" list decides what runs. Built each time a world loads, so late registration works.
 */
public final class Features {
    private static final Map<String, Function<TerrainConfig, TerrainFeature>> REGISTRY = new LinkedHashMap<>();

    static {
        register("sea_stacks", SeaStacks::fromConfig);
    }

    private Features() {
    }

    public static synchronized void register(String id, Function<TerrainConfig, TerrainFeature> factory) {
        if (REGISTRY.putIfAbsent(id, factory) != null) {
            throw new IllegalArgumentException("feature '" + id + "' already registered");
        }
    }

    public static synchronized List<TerrainFeature> build(TerrainConfig config) {
        List<TerrainFeature> out = new ArrayList<>();
        for (String id : config.list("features")) {
            Function<TerrainConfig, TerrainFeature> factory = REGISTRY.get(id);
            if (factory == null) {
                throw new IllegalArgumentException(
                        "unknown feature '" + id + "'; known: " + String.join(", ", REGISTRY.keySet()));
            }
            out.add(factory.apply(config));
        }
        return out;
    }
}
