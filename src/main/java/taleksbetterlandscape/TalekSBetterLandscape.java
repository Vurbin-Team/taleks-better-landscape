package taleksbetterlandscape;

import net.fabricmc.api.ModInitializer;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TalekSBetterLandscape implements ModInitializer {
	public static final String MOD_ID = "taleks-better-landscape";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// surface rule condition for mountain snow, ice and the rock gradient (see mountain_blocks.json)
		Registry.register(BuiltInRegistries.MATERIAL_CONDITION, id("peak"), PeakCondition.CODEC);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
