package icyllis.modernui.mc.mixin;

import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Named Minecraft members are remapped by Loom, including production intermediary names. */
@Mixin(LevelRenderer.class)
public interface AccessFabricVulkanLevelRenderer {
    @Accessor("starBuffer") VertexBuffer modernui$getStarBuffer();
    @Accessor("starBuffer") void modernui$setStarBuffer(VertexBuffer value);
    @Accessor("skyBuffer") VertexBuffer modernui$getSkyBuffer();
    @Accessor("skyBuffer") void modernui$setSkyBuffer(VertexBuffer value);
    @Accessor("darkBuffer") VertexBuffer modernui$getDarkBuffer();
    @Accessor("darkBuffer") void modernui$setDarkBuffer(VertexBuffer value);
}
