package com.pureblue.woad;

import com.pureblue.woad.blackjack.BlackjackManager;
import com.pureblue.woad.blackjack.gui.BlackjackScreen;
import com.pureblue.woad.config.ConfigStore;
import com.pureblue.woad.core.Woad;
import com.pureblue.woad.customitem.CustomItemApplier;
import com.pureblue.woad.gui.CustomItemScreen;
import com.pureblue.woad.core.FeatureManager;
import com.pureblue.woad.core.SkyBlockArea;
import com.pureblue.woad.gui.WoadScreen;
import com.pureblue.woad.net.NetworkChoice;
import com.pureblue.woad.ui.DevCapture;
import com.pureblue.woad.ui.UiGalleryScreen;
import com.pureblue.woad.ui.UiDropdown;
import com.pureblue.woad.ui.UiVanillaButton;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import com.pureblue.woad.gui.HudEditScreen;
import com.pureblue.woad.lava.LavaColorManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class WoadClient implements ClientModInitializer {

    private KeyMapping openMenuKey;
    private boolean pendingOpen = false;
    private boolean pendingHudOpen = false;
    private boolean pendingBlackjackOpen = false;
    private boolean pendingCustomItem = false;
    private boolean pendingGallery = false;
    /** The design-system gallery opens itself once when WOAD_GALLERY is set (development only). */
    private boolean galleryAutoOpened = false;

    @Override
    public void onInitializeClient() {
        // Build the feature registry. Load the lava palette before textures so lava is coloured from
        // the first frame, then restore feature state (which gates the lava master switch).
        FeatureManager.getFeatures();
        LavaColorManager.INSTANCE.load();
        ConfigStore.load();

        // NOTE: 26.x rebuilt fluid rendering around FluidModel.Unbaked (sprites come from a
        // model resource, not from code), so the "show lava as water" mode of Custom Lava is
        // not available here. Recolouring still works: it patches the sprite image itself.

        // Keybind to open the config menu (defaults to Right Shift, rebindable in Controls).
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Woad.MOD_ID, "main"));
        openMenuKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("woad.key.openMenu", GLFW.GLFW_KEY_RIGHT_SHIFT, category));

        // "/woad" opens the menu; "/woad hud" opens the HUD editor.
        // (Queued so the screen opens after the chat screen closes.)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
                dispatcher.register(ClientCommands.literal(Woad.MOD_ID)
                        .executes(ctx -> {
                            pendingOpen = true;
                            return 1;
                        })
                        .then(ClientCommands.literal("customitem")
                                .executes(ctx -> {
                                    pendingCustomItem = true;
                                    return 1;
                                }))
                        .then(ClientCommands.literal("hud")
                                .executes(ctx -> {
                                    pendingHudOpen = true;
                                    return 1;
                                }))
                        // What the mod reads to know where you are, for when a dungeon-only
                        // option does not kick in.
                        .then(ClientCommands.literal("area")
                                .executes(ctx -> {
                                    reportArea(Minecraft.getInstance());
                                    return 1;
                                }))));

        // "/woad ui": every component of the design system on one screen. Development builds
        // only — the command does not exist in a released jar.
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
                    dispatcher.register(ClientCommands.literal(Woad.MOD_ID)
                            .then(ClientCommands.literal("ui").executes(ctx -> {
                                pendingGallery = true;
                                return 1;
                            }))));
        }

        // Draw feature HUDs (e.g. the Jerry timer) over the in-game overlay.
        // 26.x replaced HudRenderCallback with named HUD elements drawn after the vanilla ones.
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(Woad.MOD_ID, "hud"),
                (context, tickCounter) -> {
                    if (FeatureManager.JERRY_TIMER.isEnabled()) {
                        FeatureManager.JERRY_TIMER.renderHud(context);
                    }
                });

        // Inventory command buttons: route clicks from the player inventory. (Rendering is done by
        // HandledScreenMixin, before the item tooltip, so the tooltip stays on top.)
        ScreenEvents.AFTER_INIT.register((client, screen, sw, sh) -> {
            // Network adapter picker, top-right of the server list. Deliberately not in the
            // config menu: it belongs where you are about to connect.
            if (screen instanceof JoinMultiplayerScreen) {
                addAdapterPicker(screen, sw);
            }
            if (screen instanceof InventoryScreen) {
                ScreenMouseEvents.allowMouseClick(screen).register((scr, click) ->
                        !FeatureManager.INV_BUTTONS.onInventoryClick((AbstractContainerScreen<?>) scr, click));
            }
            // Translator: hold the bound key and click a line in the open chat to translate it.
            if (screen instanceof ChatScreen) {
                ScreenMouseEvents.allowMouseClick(screen).register((scr, click) ->
                        !(FeatureManager.TRANSLATOR.isEnabled()
                                && FeatureManager.TRANSLATOR.onChatClick(click.x(), click.y(), click.button())));
            }
        });

        // Hypixel's chat is *system* chat, read straight off the network in ChatInterceptor
        // (Fabric's GAME event misses lines other mods cancel, e.g. Mort's dialogue).
        // Player chat — singleplayer and vanilla servers — is a different, signed packet, so it
        // needs this event; it hands us the sender and the text already separated.
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTime) -> {
            if (sender == null || signedMessage == null) return;
            FeatureManager.onPlayerChatMessage(sender.name(), signedMessage.signedContent());
        });

        // "/bj" opens the blackjack table (queued like the other screens).
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
                dispatcher.register(ClientCommands.literal("bj")
                        .executes(ctx -> {
                            pendingBlackjackOpen = true;
                            return 1;
                        })));

        // Forget any table and queued lines when leaving a server.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BlackjackManager.reset());

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    /**
     * The adapter button: shows the current choice, and a click unfolds the list of adapters right
     * under it. The vanilla screen knows nothing of the list, so it is drawn after the screen and
     * gets the clicks, wheel and Escape before the screen does while it is open.
     */
    private static void addAdapterPicker(Screen screen, int screenWidth) {
        int width = 110;
        UiVanillaButton[] button = new UiVanillaButton[1];
        UiDropdown picker = new UiDropdown(
                () -> NetworkChoice.options().stream().map(NetworkChoice.Option::id).toList(),
                () -> NetworkChoice.current().id(),
                id -> {
                    NetworkChoice.select(id);
                    button[0].setMessage(adapterLabel());
                })
                .label(WoadClient::adapterName);
        button[0] = new UiVanillaButton(screenWidth - width - 6, 6, width, 20, adapterLabel(), b -> picker.toggle())
                .tooltip(() -> picker.isOpen() ? List.of() : adapterTooltip());
        picker.bounds(button[0].getX(), button[0].getY(), width, 20);
        Screens.getWidgets(screen).add(button[0]);

        ScreenEvents.afterExtract(screen).register((scr, ctx, mx, my, delta) ->
                picker.renderList(ctx, mx, my, scr.width, scr.height));
        ScreenMouseEvents.allowMouseClick(screen).register((scr, click) ->
                !picker.listMouseClicked(click.x(), click.y(), click.button()));
        ScreenMouseEvents.allowMouseScroll(screen).register((scr, mx, my, horizontal, vertical) ->
                !picker.listMouseScrolled(mx, my, vertical));
        ScreenKeyboardEvents.allowKeyPress(screen).register((scr, key) -> {
            if (picker.isOpen() && key.key() == GLFW.GLFW_KEY_ESCAPE) {
                picker.close();
                return false;
            }
            return true;
        });
    }

    /** An adapter's short, address-free name, for the list. */
    private static String adapterName(String id) {
        for (NetworkChoice.Option option : NetworkChoice.options()) {
            if (option.id().equals(id)) return option.label();
        }
        return id.isEmpty() ? "Automatic" : id;
    }

    /** Never the address: this button is visible on stream and in screen shares. */
    private static Component adapterLabel() {
        return Component.literal("Net: " + NetworkChoice.current().label());
    }

    /** The address belongs here, where it only shows while the mouse rests on the button. */
    private static List<String> adapterTooltip() {
        NetworkChoice.Option current = NetworkChoice.current();
        List<String> lines = new ArrayList<>();
        if (current.address() == null) {
            lines.add("Automatic: whichever connection the system picks.");
        } else {
            lines.add(current.fullName());
            lines.add(current.address().getHostAddress());
        }
        lines.add("Click to pick a connection.");
        lines.add("Applies to joining servers and pings, not to login.");
        return lines;
    }

    /** Prints whether the mod thinks you are in the Catacombs, and the lines it decided that from. */
    private static void reportArea(Minecraft mc) {
        if (mc.player == null) return;
        mc.gui.getChat().addClientSystemMessage(Component.literal("[Woad] In Catacombs: " + SkyBlockArea.inCatacombs())
                .withStyle(ChatFormatting.AQUA));
        for (String line : SkyBlockArea.sidebarLines(mc)) {
            mc.gui.getChat().addClientSystemMessage(Component.literal("  sidebar: " + line).withStyle(ChatFormatting.GRAY));
        }
        for (String line : SkyBlockArea.tabLines(mc)) {
            if (line.contains("Dungeon") || line.contains("Area")) {
                mc.gui.getChat().addClientSystemMessage(Component.literal("  tab: " + line).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private void onClientTick(Minecraft client) {
        SkyBlockArea.tick(client);
        while (openMenuKey.consumeClick()) {
            pendingOpen = true;
        }
        if (client.screen == null && client.level != null) {
            if (pendingOpen) {
                pendingOpen = false;
                client.setScreen(new WoadScreen());
            } else if (pendingHudOpen) {
                pendingHudOpen = false;
                client.setScreen(new HudEditScreen());
            } else if (pendingBlackjackOpen) {
                pendingBlackjackOpen = false;
                if (FeatureManager.BLACKJACK.isEnabled()) client.setScreen(new BlackjackScreen());
            } else if (pendingCustomItem) {
                pendingCustomItem = false;
                openCustomItemEditor(client);
            } else if (pendingGallery) {
                pendingGallery = false;
                client.setScreen(new UiGalleryScreen());
            } else if (!galleryAutoOpened && client.player != null
                    && FabricLoader.getInstance().isDevelopmentEnvironment()
                    && System.getenv("WOAD_GALLERY") != null) {
                galleryAutoOpened = true;
                client.setScreen(new UiGalleryScreen());
            }
        }

        // Repaint customised items: the server keeps resending stacks, which would undo it.
        CustomItemApplier.tick();

        FeatureManager.onClientTick();

        // Development only: automated screenshots of the interface (WOAD_SHOT).
        DevCapture.tick(client);
    }

    /** Opens the look editor for whatever is in the main hand. */
    private static void openCustomItemEditor(Minecraft client) {
        if (client.player == null) return;
        ItemStack held = client.player.getMainHandItem();
        if (held.isEmpty()) {
            Woad.sendPrefixedMessage(Component.literal("Hold the item you want to customise.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        client.setScreen(new CustomItemScreen(held));
    }
}
