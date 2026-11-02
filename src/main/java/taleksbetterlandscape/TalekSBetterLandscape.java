package taleksbetterlandscape;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biomes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TalekSBetterLandscape implements ModInitializer {
	public static final String MOD_ID = "taleks-better-landscape";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Registry.register(BuiltInRegistries.FEATURE, id("shore_shelf"), new ShoreShelfFeature());
		Registry.register(BuiltInRegistries.FEATURE, id("rocky_cove"), new RockyCoveFeature());

		// surface rule condition for mountain snow, ice and the rock gradient (see mountain_blocks.json)
		Registry.register(BuiltInRegistries.MATERIAL_CONDITION, id("peak"), PeakCondition.CODEC.codec());

		// no cave/canyon carvers on beaches
		BiomeModifications.create(id("no_beach_carvers")).add(ModificationPhase.REMOVALS,
			BiomeSelectors.includeByKey(Biomes.BEACH, Biomes.SNOWY_BEACH),
			ctx -> {
				for (String n : new String[] {"cave", "cave_extra_underground", "canyon"}) {
					ctx.getGenerationSettings().removeCarver(ResourceKey.create(Registries.CONFIGURED_CARVER, Identifier.withDefaultNamespace(n)));
				}
			});
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
