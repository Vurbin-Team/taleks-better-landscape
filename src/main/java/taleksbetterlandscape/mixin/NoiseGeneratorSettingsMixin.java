package taleksbetterlandscape.mixin;

import taleksbetterlandscape.mc.TerrainDensity;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The single hook into world generation: wrap the final density of every noise router that is handed out. */
@Mixin(NoiseGeneratorSettings.class)
abstract class NoiseGeneratorSettingsMixin {

    @Inject(method = "noiseRouter", at = @At("RETURN"), cancellable = true)
    private void fantasyterrain$shape(CallbackInfoReturnable<NoiseRouter> cir) {
        NoiseGeneratorSettings self = (NoiseGeneratorSettings) (Object) this;
        cir.setReturnValue(TerrainDensity.wrap(self.seaLevel(), cir.getReturnValue()));
    }
}
