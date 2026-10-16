package taleksbetterlandscape.mc;

import taleksbetterlandscape.core.TerrainConfig;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FantasyTerrainMod implements ModInitializer {
    public static final String ID = "fantasy_terrain";
    public static final Logger LOG = LoggerFactory.getLogger(ID);
    private static final String DEFAULTS = "/taleks-better-landscape.default.properties";

    private static volatile TerrainConfig config;

    /** Null until the mod has initialised; terrain stays vanilla in that case. */
    public static TerrainConfig config() {
        return config;
    }

    @Override
    public void onInitialize() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(ID + ".properties");
        try {
            if (Files.notExists(file)) {
                try (InputStream in = defaults()) {
                    Files.copy(in, file);
                }
            }
            try (InputStream in = defaults()) {
                config = TerrainConfig.load(in, file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
        LOG.info("Fantasy Terrain config loaded from {}", file);
    }

    private static InputStream defaults() {
        return Objects.requireNonNull(FantasyTerrainMod.class.getResourceAsStream(DEFAULTS), DEFAULTS);
    }
}
