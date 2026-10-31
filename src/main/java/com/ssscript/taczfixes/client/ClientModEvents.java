package com.ssscript.taczfixes.client;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.client.handler.AimSwayHandler;
import com.ssscript.taczfixes.client.handler.ArcanaOffhandKeyHandler;
import com.ssscript.taczfixes.client.handler.ConfigKeyHandler;
import com.ssscript.taczfixes.client.handler.CrawlHitboxDebugHandler;
import com.ssscript.taczfixes.client.handler.DynamicCrosshairHandler;
import com.ssscript.taczfixes.client.handler.EditDataKeyHandler;
import com.ssscript.taczfixes.client.handler.GunDataMessageHandler;
import com.ssscript.taczfixes.client.handler.GunDurabilityTooltipHandler;
import com.ssscript.taczfixes.client.handler.GunEnchantmentHandler;
import com.ssscript.taczfixes.client.handler.GunPackKeyHandler;
import com.ssscript.taczfixes.client.handler.GunPackMenuButtonHandler;
import com.ssscript.taczfixes.client.handler.HoldBreathKeyHandler;
import com.ssscript.taczfixes.client.handler.InputSyncHandler;
import com.ssscript.taczfixes.client.handler.ScopeFovTransitionHandler;
import com.ssscript.taczfixes.client.handler.ScopeStencilHandler;
import com.ssscript.taczfixes.client.handler.ScopeSwitchHandler;
import com.ssscript.taczfixes.client.handler.StaminaClientHandler;
import com.ssscript.taczfixes.client.handler.SteplessZoomHandler;
import com.ssscript.taczfixes.client.render.LightManager;
import com.ssscript.taczfixes.client.screen.ConfigScreen;
import com.ssscript.taczfixes.client.util.ClientDisplayDataReloadListener;
import com.ssscript.taczfixes.common.data.AdapterNativeDataReloadListener;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientModEvents {
    private ClientModEvents() {
    }

    public static void init(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, parent) -> ConfigScreen.create(parent));
        LightManager.injectShaders();
        modBus.addListener(ClientModEvents::onClientSetup);
        modBus.addListener(ClientModEvents::onRegisterKeyMappings);
        modBus.addListener(ClientModEvents::onRegisterClientReloadListeners);
        modBus.addListener(ClientModEvents::onRegisterGuiLayers);
        modBus.addListener(NetworkHandler::register);
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(TaczFixesMod.MOD_ID, "aiming_stamina"),
                new com.ssscript.taczfixes.client.hud.AimingStaminaOverlay());
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(TaczFixesMod.MOD_ID, "stamina"),
                new com.ssscript.taczfixes.client.hud.StaminaOverlay());
    }

    private static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new ClientDisplayDataReloadListener());
        event.registerReloadListener(new AdapterNativeDataReloadListener());
        event.registerReloadListener((barrier, resourceManager, preparationsProfiler, reloadProfiler, backgroundExecutor, gameExecutor) ->
                java.util.concurrent.CompletableFuture.completedFuture(null).thenCompose(barrier::wait)
                        .thenRunAsync(com.ssscript.taczfixes.client.client.CrosshairPartLayout::clear, gameExecutor));
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ScopeSwitchHandler.SWITCH_SCOPE_KEY);
        event.register(ConfigKeyHandler.OPEN_CONFIG_KEY);
        event.register(EditDataKeyHandler.EDIT_DATA_KEY);
        event.register(HoldBreathKeyHandler.HOLD_BREATH_KEY);
        event.register(GunPackKeyHandler.GUN_PACK_KEY);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            NeoForge.EVENT_BUS.register(new SteplessZoomHandler());
            NeoForge.EVENT_BUS.register(new ScopeSwitchHandler());
            NeoForge.EVENT_BUS.register(new ConfigKeyHandler());
            NeoForge.EVENT_BUS.register(new GunEnchantmentHandler());
            NeoForge.EVENT_BUS.register(new EditDataKeyHandler());
            NeoForge.EVENT_BUS.register(new GunDataMessageHandler());
            NeoForge.EVENT_BUS.register(new InputSyncHandler());
            NeoForge.EVENT_BUS.register(new HoldBreathKeyHandler());
            NeoForge.EVENT_BUS.register(new AimSwayHandler());
            NeoForge.EVENT_BUS.register(new ScopeStencilHandler());
            NeoForge.EVENT_BUS.register(new StaminaClientHandler());
            NeoForge.EVENT_BUS.register(new ScopeFovTransitionHandler());
            NeoForge.EVENT_BUS.register(new ArcanaOffhandKeyHandler());
            NeoForge.EVENT_BUS.register(new CrawlHitboxDebugHandler());
            NeoForge.EVENT_BUS.register(new DynamicCrosshairHandler());
            NeoForge.EVENT_BUS.register(new GunDurabilityTooltipHandler());
            NeoForge.EVENT_BUS.register(new GunPackKeyHandler());
            NeoForge.EVENT_BUS.register(new GunPackMenuButtonHandler());
        });
    }
}
