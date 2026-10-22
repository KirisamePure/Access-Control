package dev.kirisame.accesscontrol.client;

import dev.kirisame.accesscontrol.AccessControl;
import dev.kirisame.accesscontrol.ReaderNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.client.render.BlockRenderLayer;

public final class AccessControlClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        BlockRenderLayerMap.putBlocks(BlockRenderLayer.CUTOUT,
                AccessControl.IRON_WINDOW_DOOR, AccessControl.TECH_WINDOW_PANEL);
        BlockEntityRendererFactories.register(AccessControl.SLIDING_ENTITY, SlidingDoorRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(ReaderNetworking.OpenConfig.ID, (payload, context) ->
                context.client().setScreen(new ReaderConfigScreen(payload)));
    }
}
