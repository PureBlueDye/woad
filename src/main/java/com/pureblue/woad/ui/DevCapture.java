package com.pureblue.woad.ui;

import com.pureblue.woad.gui.WoadScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * Photographs Woad's screens without anyone at the keyboard, to check a redesign in game.
 *
 * <p>Development only. With {@code WOAD_SHOT=<sequence>} set, once a world is loaded it switches to
 * GUI scale {@code WOAD_SCALE} (2 by default), plays the named sequence — open a screen, prepare
 * it, capture it into {@code run/screenshots/} — then puts the scale back and closes the game. The
 * option is never saved.
 */
public final class DevCapture {

    private DevCapture() {}

    private record Step(String label, Consumer<Minecraft> action) {}

    private static final Deque<Step> steps = new ArrayDeque<>();
    private static boolean started;
    private static int originalScale;
    private static double nextAt;

    public static void tick(Minecraft mc) {
        String sequence = System.getenv("WOAD_SHOT");
        if (sequence == null || !FabricLoader.getInstance().isDevelopmentEnvironment() || mc.player == null) return;
        double now = Anim.nowMs();
        if (!started) {
            started = true;
            originalScale = mc.options.guiScale().get();
            int scale = 2;
            try {
                scale = Integer.parseInt(System.getenv().getOrDefault("WOAD_SCALE", "2"));
            } catch (NumberFormatException ignored) {
                // keep 2
            }
            mc.options.guiScale().set(scale);
            mc.resizeGui();
            build(sequence);
            nextAt = now + 2500; // let the world and the fonts settle
            return;
        }
        if (now < nextAt) return;
        Step step = steps.poll();
        if (step == null) {
            mc.options.guiScale().set(originalScale);
            mc.resizeGui();
            mc.stop();
            return;
        }
        step.action().accept(mc);
        nextAt = now + 900;
    }

    private static void open(Screen screen) {
        steps.add(new Step("open", mc -> mc.setScreen(screen)));
    }

    private static void act(Consumer<Minecraft> action) {
        steps.add(new Step("act", action));
    }

    /**
     * Puts the pointer at a GUI position, so hover states and tooltips can be captured. The game
     * reads the pointer from its own mouse handler, which a programmatic GLFW move does not update
     * on Windows, so both are set.
     */
    private static void hover(Minecraft mc, float guiX, float guiY) {
        int scale = mc.getWindow().getGuiScale();
        double sx = guiX * scale;
        double sy = guiY * scale;
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().handle(), sx, sy);
        try {
            java.lang.reflect.Field x = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            java.lang.reflect.Field y = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            x.setAccessible(true);
            y.setAccessible(true);
            x.setDouble(mc.mouseHandler, sx);
            y.setDouble(mc.mouseHandler, sy);
        } catch (ReflectiveOperationException ignored) {
            // the GLFW move alone will have to do
        }
    }

    private static void grab(String name) {
        steps.add(new Step("grab", mc -> Screenshot.grab(mc.gameDirectory, "woad_" + name + ".png",
                mc.getMainRenderTarget(), 1, message -> {})));
    }

    private static final int GLFW_ESCAPE = 256;

    /** Clicks a settings control of the menu at a fraction of its width, as the mouse would. */
    private static void clickControl(Minecraft mc, WoadScreen menu, String setting, float across) {
        float[] b = menu.controlBounds(setting);
        if (b == null) return;
        double x = b[0] + b[2] * across;
        double y = b[1] + b[3] / 2;
        hover(mc, (float) x, (float) y);
        menu.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(x, y,
                new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
    }

    // ---- Fake dungeon chests, for the Chest Profit pictures ----

    private static void openChest(Minecraft mc, net.minecraft.world.SimpleContainer box,
                                  net.minecraft.network.chat.Component title) {
        var menu = net.minecraft.world.inventory.ChestMenu.sixRows(4242, mc.player.getInventory(), box);
        mc.setScreen(new net.minecraft.client.gui.screens.inventory.ContainerScreen(menu, mc.player.getInventory(), title));
    }

    private static net.minecraft.world.item.ItemStack named(net.minecraft.world.item.ItemStack stack, String name,
                                                            List<String> lore) {
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(name));
        if (lore != null) {
            List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
            for (String line : lore) lines.add(net.minecraft.network.chat.Component.literal(line));
            stack.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lines));
        }
        return stack;
    }

    private static net.minecraft.world.item.ItemStack chestItem(String name, net.minecraft.ChatFormatting colour,
                                                                List<String> contents, List<String> cost) {
        List<String> lore = new java.util.ArrayList<>();
        lore.add("Contents");
        lore.addAll(contents);
        lore.add("");
        lore.add("Cost");
        lore.addAll(cost);
        lore.add("");
        lore.add("Click to open this chest!");
        var stack = named(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CHEST), name, lore);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal(name).withStyle(colour));
        return stack;
    }

    private static net.minecraft.world.item.ItemStack sbItem(net.minecraft.world.item.Item item, String name, String id) {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("id", id);
        var stack = named(new net.minecraft.world.item.ItemStack(item), name, null);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        return stack;
    }

    private static net.minecraft.world.item.ItemStack book(String enchant, int level) {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("id", "ENCHANTED_BOOK");
        var enchants = new net.minecraft.nbt.CompoundTag();
        enchants.putInt(enchant, level);
        tag.put("enchantments", enchants);
        var stack = named(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK), "Enchanted Book", null);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        return stack;
    }

    /** Puts the pointer on a row of the Chest Profit panel (first row = 0), left of the chest screen. */
    private static void hoverSlotRow(Minecraft mc, int row) {
        if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)) return;
        var gui = (com.pureblue.woad.mixin.HandledScreenAccessor) screen;
        hover(mc, gui.woad$getX() - 40, gui.woad$getY() + 6 + 12 + row * 11 + 5);
    }

    private static void setSkulls(boolean hide) {
        for (var setting : com.pureblue.woad.core.FeatureManager.RENDER_OPTIMIZER.getSettings()) {
            if (setting instanceof com.pureblue.woad.core.setting.BooleanSetting toggle && toggle.enabled() != hide) {
                toggle.toggle();
            }
        }
    }

    private static void setPriceMode(String mode) {
        for (var setting : com.pureblue.woad.core.FeatureManager.CHEST_PROFIT.getSettings()) {
            if (setting instanceof com.pureblue.woad.core.setting.ModeSetting choice) choice.set(mode);
        }
    }

    /** The capture sequences, one per migrated screen. */
    private static void build(String sequence) {
        switch (sequence) {
            case "menu" -> {
                WoadScreen menu = new WoadScreen();
                open(menu);
                for (String feature : new String[]{"AI Chat", "Custom Lava", "Inventory Buttons", "Translator", "Jerry Timer"}) {
                    act(mc -> menu.selectFeature(feature));
                    grab("menu_" + feature.toLowerCase().replace(' ', '_'));
                }
            }
            case "prompts" -> {
                WoadScreen menu = new WoadScreen();
                open(new com.pureblue.woad.gui.TextPromptScreen(menu, "Trigger",
                        "Word after \"!\" that calls the AI. \"ai\" means players write !ai <question>.", "ai", v -> {}));
                grab("prompt_text");
                open(new com.pureblue.woad.gui.HexPromptScreen(menu, "#FF8000", v -> {}));
                grab("prompt_hex");
                open(new com.pureblue.woad.gui.HexPromptScreen(menu, "#FF80ZZ", v -> {}));
                grab("prompt_hex_invalid");
            }
            case "batch2" -> {
                WoadScreen menu = new WoadScreen();
                var feature = com.pureblue.woad.core.FeatureManager.INV_BUTTONS;
                open(new com.pureblue.woad.gui.TextPromptScreen(menu, "Trigger",
                        "Word after \"!\" that calls the AI. \"ai\" means players write !ai <question>.", "ai", v -> {}));
                grab("prompt_text");
                open(new com.pureblue.woad.gui.HexPromptScreen(menu, "#FF8000", v -> {}));
                grab("prompt_hex");
                open(new com.pureblue.woad.gui.HexPromptScreen(menu, "#FF80ZZ", v -> {}));
                grab("prompt_hex_invalid");

                var sample = new com.pureblue.woad.features.InventoryButtonsFeature.CmdButton(0, -1, "warp dungeon_hub");
                sample.item = "diamond_sword";
                open(new com.pureblue.woad.gui.ButtonEditScreen(menu, feature, sample));
                grab("button_edit");

                // Temporary buttons, only for the pictures; removed at the end of the sequence.
                java.util.List<com.pureblue.woad.features.InventoryButtonsFeature.CmdButton> temp = new java.util.ArrayList<>();
                act(mc -> {
                    String[][] spec = {{"-1", "0", "storage", "chest", ""}, {"-1", "1", "warp hub", "", "12"},
                            {"9", "0", "pets", "bone", ""}, {"9", "1", "bz", "", ""}, {"4", "-1", "", "", ""},
                            {"-1", "3", "ah", "gold_ingot", ""}, {"9", "3", "craft", "", "CR"}};
                    for (String[] r : spec) {
                        var t = new com.pureblue.woad.features.InventoryButtonsFeature.CmdButton(
                                Integer.parseInt(r[0]), Integer.parseInt(r[1]), r[2]);
                        t.item = r[3];
                        t.label = r[4];
                        temp.add(t);
                    }
                    feature.getButtons().addAll(temp);
                });
                open(new com.pureblue.woad.gui.InventoryButtonsScreen(menu, feature));
                grab("inventory_editor");
                act(mc -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
                grab("inventory_live");
                open(new com.pureblue.woad.gui.HudEditScreen());
                grab("hud_editor");
                act(mc -> {
                    mc.setScreen(null);
                    feature.getButtons().removeAll(temp);
                });
                grab("hud_ingame");
            }
            case "batch3" -> {
                open(new com.pureblue.woad.gui.CustomItemScreen(
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_CHESTPLATE)));
                grab("custom_item");
                // Rest the pointer on the "!" badge (same geometry as CustomItemScreen).
                act(mc -> {
                    int w = mc.getWindow().getGuiScaledWidth();
                    int h = mc.getWindow().getGuiScaledHeight();
                    float x = Math.round((w - 300) / 2f);
                    float y = Math.round((h - 161) / 2f);
                    hover(mc, x + 300 - 12 - 8, y + 12 + 9);
                });
                grab("custom_item_help");
                act(mc -> hover(mc, 4, 4));

                open(new com.pureblue.woad.blackjack.gui.BlackjackScreen());
                grab("blackjack_empty");
                act(mc -> {
                    String[] lines = {
                            "New round! Players: Pure_Blue_Dye, Bob, Alice.",
                            "Dealer shows K♥ (second card hidden).",
                            "Pure_Blue_Dye: 10♠ 7♦ (17)",
                            "Bob: A♣ K♦ (21) - BLACKJACK!",
                            "Alice: 9♥ 8♣ 7♠ (24)",
                            "Your turn, Pure_Blue_Dye!"};
                    for (String line : lines) {
                        com.pureblue.woad.blackjack.BlackjackManager.onChatLine("Party > [MVP+] Pure_Blue_Dye: " + line);
                    }
                });
                grab("blackjack_round");

                act(mc -> {
                    com.pureblue.woad.blackjack.BlackjackManager.reset();
                    mc.setScreen(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(
                            new net.minecraft.client.gui.screens.TitleScreen()));
                });
                act(mc -> hover(mc, mc.getWindow().getGuiScaledWidth() - 6 - 55, 16));
                grab("server_list");
                act(mc -> hover(mc, 4, 4));
            }
            case "final" -> {
                var feature = com.pureblue.woad.core.FeatureManager.INV_BUTTONS;
                WoadScreen menu = new WoadScreen();
                open(menu);
                for (String name : new String[]{"AI Chat", "Custom Lava", "Inventory Buttons", "Blackjack"}) {
                    act(mc -> menu.selectFeature(name));
                    grab("final_menu_" + name.toLowerCase().replace(' ', '_'));
                }
                open(new com.pureblue.woad.gui.TextPromptScreen(menu, "Ollama URL", "Address of your local Ollama server.",
                        "http://localhost:11434", v -> {}));
                grab("final_prompt_text");
                open(new com.pureblue.woad.gui.HexPromptScreen(menu, "#3B82F6", v -> {}));
                grab("final_prompt_hex");
                var sample = new com.pureblue.woad.features.InventoryButtonsFeature.CmdButton(0, -1, "warp dungeon_hub");
                sample.item = "diamond_sword";
                open(new com.pureblue.woad.gui.ButtonEditScreen(menu, feature, sample));
                grab("final_button_edit");

                open(new com.pureblue.woad.gui.CustomItemScreen(
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_CHESTPLATE)));
                act(mc -> {
                    int w = mc.getWindow().getGuiScaledWidth();
                    int h = mc.getWindow().getGuiScaledHeight();
                    hover(mc, Math.round((w - 300) / 2f) + 300 - 12 - 8, Math.round((h - 161) / 2f) + 12 + 9);
                });
                grab("final_custom_item_help");
                act(mc -> hover(mc, 4, 4));

                java.util.List<com.pureblue.woad.features.InventoryButtonsFeature.CmdButton> temp = new java.util.ArrayList<>();
                act(mc -> {
                    String[][] spec = {{"-1", "0", "storage", "chest", ""}, {"-1", "1", "warp hub", "", "12"},
                            {"9", "0", "pets", "bone", ""}, {"9", "1", "bz", "", ""}, {"-1", "3", "ah", "gold_ingot", ""}};
                    for (String[] r : spec) {
                        var t = new com.pureblue.woad.features.InventoryButtonsFeature.CmdButton(
                                Integer.parseInt(r[0]), Integer.parseInt(r[1]), r[2]);
                        t.item = r[3];
                        t.label = r[4];
                        temp.add(t);
                    }
                    feature.getButtons().addAll(temp);
                });
                open(new com.pureblue.woad.gui.InventoryButtonsScreen(menu, feature));
                grab("final_inventory_editor");
                // The real inventory only exists outside creative mode.
                act(mc -> {
                    mc.setScreen(null);
                    mc.player.connection.sendCommand("gamemode survival");
                });
                act(mc -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
                act(mc -> {
                    var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) mc.screen;
                    int gx = ((com.pureblue.woad.mixin.HandledScreenAccessor) screen).woad$getX();
                    int gy = ((com.pureblue.woad.mixin.HandledScreenAccessor) screen).woad$getY();
                    hover(mc, com.pureblue.woad.gui.InventoryGrid.cellX(gx, -1) + 8,
                            com.pureblue.woad.gui.InventoryGrid.cellY(gy, 0) + 8);
                });
                grab("final_inventory_live");
                act(mc -> {
                    hover(mc, 4, 4);
                    mc.setScreen(null);
                    feature.getButtons().removeAll(temp);
                    mc.player.connection.sendCommand("gamemode creative");
                });

                open(new com.pureblue.woad.gui.HudEditScreen());
                grab("final_hud_editor");
                open(new com.pureblue.woad.blackjack.gui.BlackjackScreen());
                act(mc -> {
                    String[] lines = {"New round! Players: Pure_Blue_Dye, Bob, Alice.", "Dealer shows K♥ (second card hidden).",
                            "Pure_Blue_Dye: 10♠ 7♦ (17)", "Bob: A♣ K♦ (21) - BLACKJACK!", "Alice: 9♥ 8♣ 7♠ (24)",
                            "Your turn, Pure_Blue_Dye!"};
                    for (String line : lines) {
                        com.pureblue.woad.blackjack.BlackjackManager.onChatLine("Party > [MVP+] Pure_Blue_Dye: " + line);
                    }
                });
                grab("final_blackjack");
                act(mc -> {
                    com.pureblue.woad.blackjack.BlackjackManager.reset();
                    mc.setScreen(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(
                            new net.minecraft.client.gui.screens.TitleScreen()));
                });
                act(mc -> hover(mc, mc.getWindow().getGuiScaledWidth() - 6 - 55, 16));
                grab("final_server_list");
                act(mc -> hover(mc, 4, 4));
            }
            case "hudcheck" -> {
                var jerry = com.pureblue.woad.core.FeatureManager.JERRY_TIMER;
                boolean[] wasEnabled = new boolean[1];
                float[] was = new float[3];
                act(mc -> {
                    wasEnabled[0] = jerry.isEnabled();
                    was[0] = jerry.getHudX();
                    was[1] = jerry.getHudY();
                    was[2] = jerry.getHudScale();
                    jerry.setEnabled(true);
                    jerry.setHudPos(20, 20);
                    jerry.setHudScale(1.5f);
                    mc.setScreen(null);
                });
                grab("hud_x1.5");
                act(mc -> jerry.setHudScale(2.5f));
                grab("hud_x2.5");
                WoadScreen menu = new WoadScreen();
                open(menu);
                act(mc -> menu.selectFeature("Custom Lava"));
                grab("menu_edge");
                act(mc -> {
                    jerry.setHudPos((int) was[0], (int) was[1]);
                    jerry.setHudScale(was[2]);
                    jerry.setEnabled(wasEnabled[0]);
                });
            }
            case "aichat" -> {
                WoadScreen menu = new WoadScreen();
                open(menu);
                act(mc -> menu.selectFeature("AI Chat"));
                grab("aichat_default");
                // A second prompt dropped into the folder must show up on the next click.
                java.nio.file.Path extra = com.pureblue.woad.ai.PromptStore.PROMPTS_DIR.resolve("Shot test.txt");
                act(mc -> {
                    try {
                        java.nio.file.Files.writeString(extra, "Answer like a pirate.");
                    } catch (java.io.IOException e) {
                        org.slf4j.LoggerFactory.getLogger("Woad").warn("[shot] could not write {}", extra, e);
                    }
                    for (var setting : com.pureblue.woad.core.FeatureManager.AI_CHAT.getSettings()) {
                        if (setting instanceof com.pureblue.woad.core.setting.ModeSetting mode
                                && mode.getName().equals("Prompt")) {
                            mode.cycle();
                            org.slf4j.LoggerFactory.getLogger("Woad").info("[shot] prompts {} -> {}",
                                    mode.getOptions(), mode.get());
                        }
                    }
                });
                grab("aichat_cycled");
                act(mc -> {
                    for (var setting : com.pureblue.woad.core.FeatureManager.AI_CHAT.getSettings()) {
                        if (setting instanceof com.pureblue.woad.core.setting.ModeSetting mode
                                && mode.getName().equals("Prompt")) mode.set("Default");
                    }
                    try {
                        java.nio.file.Files.deleteIfExists(extra);
                    } catch (java.io.IOException ignored) {
                        // left behind: harmless
                    }
                });
            }
            case "pickers" -> {
                WoadScreen menu = new WoadScreen();
                var memory = (com.pureblue.woad.core.setting.IntSetting) com.pureblue.woad.core.FeatureManager.AI_CHAT
                        .getSettings().stream().filter(s -> s.getName().equals("Memory")).findFirst().orElseThrow();
                int[] memoryWas = new int[1];
                open(menu);
                act(mc -> {
                    menu.selectFeature("AI Chat");
                    memoryWas[0] = memory.get();
                });
                grab("pick_closed");
                act(mc -> clickControl(mc, menu, "Backend", 0.5f));
                act(mc -> {
                    float[] b = menu.controlBounds("Backend");
                    if (b != null) hover(mc, b[0] + b[2] / 2, b[1] + b[3] + 6 + 14 + 7);
                });
                grab("pick_backend_open");
                act(mc -> menu.keyPressed(new net.minecraft.client.input.KeyEvent(GLFW_ESCAPE, 0, 0)));
                act(mc -> clickControl(mc, menu, "Prompt", 0.5f));
                grab("pick_prompt_open");
                act(mc -> menu.keyPressed(new net.minecraft.client.input.KeyEvent(GLFW_ESCAPE, 0, 0)));
                act(mc -> menu.mouseScrolled(mc.getWindow().getGuiScaledWidth() / 2.0 + 60,
                        mc.getWindow().getGuiScaledHeight() / 2.0, 0, -20));
                act(mc -> {
                    clickControl(mc, menu, "Memory", 0.3f);
                    float[] b = menu.controlBounds("Memory");
                    if (b != null) hover(mc, b[0] + b[2] * 0.3f, b[1] + b[3] / 2);
                    menu.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(0, 0,
                            new net.minecraft.client.input.MouseButtonInfo(0, 0)));
                });
                grab("pick_slider");
                act(mc -> {
                    memory.set(memoryWas[0]);
                    com.pureblue.woad.config.ConfigStore.save();
                    mc.setScreen(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(
                            new net.minecraft.client.gui.screens.TitleScreen()));
                });
                act(mc -> {
                    int w = mc.getWindow().getGuiScaledWidth();
                    hover(mc, w - 6 - 55, 16);
                    mc.screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(w - 6 - 55, 16,
                            new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
                });
                act(mc -> hover(mc, mc.getWindow().getGuiScaledWidth() - 6 - 55, 26 + 6 + 7));
                grab("pick_net");
            }
            case "chests" -> {
                // Croesus' list of runs: one untouched, one with the key still usable, one finished.
                act(mc -> {
                    var box = new net.minecraft.world.SimpleContainer(54);
                    box.setItem(10, named(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD),
                            "Master Mode The Catacombs", List.of("Floor VII", "", "No chests opened yet!")));
                    box.setItem(11, named(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD),
                            "The Catacombs", List.of("Floor VII", "", "Opened Chest: Bedrock Chest")));
                    box.setItem(12, named(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD),
                            "The Catacombs", List.of("Floor VI", "", "Opened Chest: Obsidian Chest", "No more chests to open!")));
                    openChest(mc, box, net.minecraft.network.chat.Component.literal("Croesus"));
                });
                act(mc -> hover(mc, 4, 4));
                grab("chests_croesus");
                // Croesus' run view, rebuilt from a real one: four chests, the Bedrock one as seen in game.
                act(mc -> {
                    var box = new net.minecraft.world.SimpleContainer(54);
                    box.setItem(26, chestItem("Obsidian Chest", net.minecraft.ChatFormatting.DARK_PURPLE,
                            List.of("Fifth Master Star", "Wither Essence x40"), List.of("1,000,000 Coins")));
                    box.setItem(20, chestItem("Wood Chest", net.minecraft.ChatFormatting.GOLD,
                            List.of("Enchanted Book (Feather Falling VI)", "Undead Essence x12"), List.of("FREE")));
                    box.setItem(22, chestItem("Gold Chest", net.minecraft.ChatFormatting.YELLOW,
                            List.of("Recombobulator 3000", "Wither Essence x20"), List.of("100,000 Coins")));
                    box.setItem(24, chestItem("Bedrock Chest", net.minecraft.ChatFormatting.DARK_GRAY,
                            List.of("Enchanted Book (Rejuvenate III)", "Enchanted Book (Last Stand II)",
                                    "Enchanted Book (Combo II)", "Wither Catalyst", "Apex Dragon Shard",
                                    "Wither Essence x103", "Undead Essence x129"),
                            List.of("2,000,000 Coins")));
                    openChest(mc, box, net.minecraft.network.chat.Component.literal("Catacombs - Floor VII"));
                });
                act(mc -> {});
                act(mc -> {});
                act(mc -> {});
                act(mc -> hoverSlotRow(mc, 0));
                act(mc -> {});
                grab("chests_run");
                // The same Bedrock chest opened: real items, price on the button.
                act(mc -> {
                    var box = new net.minecraft.world.SimpleContainer(54);
                    for (int i = 0; i < 54; i++) {
                        box.setItem(i, named(new net.minecraft.world.item.ItemStack(
                                net.minecraft.world.item.Items.BLACK_STAINED_GLASS_PANE), " ", null));
                    }
                    box.setItem(9, book("rejuvenate", 3));
                    box.setItem(10, book("ultimate_last_stand", 2));
                    box.setItem(11, book("ultimate_combo", 2));
                    box.setItem(12, sbItem(net.minecraft.world.item.Items.NETHER_STAR, "Wither Catalyst", "WITHER_CATALYST"));
                    box.setItem(13, sbItem(net.minecraft.world.item.Items.PRISMARINE_SHARD, "Apex Dragon Shard", "SHARD_APEX_DRAGON"));
                    box.setItem(14, named(new net.minecraft.world.item.ItemStack(
                            net.minecraft.world.item.Items.PLAYER_HEAD), "Wither Essence x103", null));
                    box.setItem(15, named(new net.minecraft.world.item.ItemStack(
                            net.minecraft.world.item.Items.PLAYER_HEAD), "Undead Essence x129", null));
                    box.setItem(31, named(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CHEST),
                            "Open Reward Chest", List.of("Cost", "2,000,000 Coins", "", "Click to open!")));
                    openChest(mc, box, net.minecraft.network.chat.Component.literal("Bedrock Chest")
                            .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                });
                act(mc -> hover(mc, 4, 4));
                grab("chests_single_instasell");
                act(mc -> setPriceMode("Sell offer"));
                grab("chests_single_selloffer");
                act(mc -> setPriceMode("Insta-sell"));
                // A chest opened without its price button: the price comes from the run view seen before.
                act(mc -> {
                    var box = new net.minecraft.world.SimpleContainer(54);
                    box.setItem(11, sbItem(net.minecraft.world.item.Items.TROPICAL_FISH, "Storm the Fish", "STORM_THE_FISH"));
                    box.setItem(13, sbItem(net.minecraft.world.item.Items.PRISMARINE_CRYSTALS, "Recombobulator 3000", "RECOMBOBULATOR_3000"));
                    box.setItem(15, named(new net.minecraft.world.item.ItemStack(
                            net.minecraft.world.item.Items.PLAYER_HEAD), "Wither Essence x20", null));
                    openChest(mc, box, net.minecraft.network.chat.Component.literal("Gold Chest")
                            .withStyle(net.minecraft.ChatFormatting.YELLOW));
                });
                act(mc -> {});
                act(mc -> {});
                grab("chests_single_fish");
            }
            case "skulls" -> {
                // A Catacombs-like sidebar, two skull stands and a zombie-head stand as a control.
                act(mc -> {
                    mc.setScreen(null);
                    var net = mc.player.connection;
                    net.sendCommand("scoreboard objectives add woadshot dummy \"SKYBLOCK\"");
                    net.sendCommand("scoreboard objectives setdisplay sidebar woadshot");
                    // As Hypixel writes it: the visible text lives in the team prefix of a placeholder entry.
                    net.sendCommand("team add woadshot");
                    // Split mid-word with an emoji entry name in between, as Hypixel does.
                    net.sendCommand("team modify woadshot prefix \" ⏣ The Catac\"");
                    net.sendCommand("team modify woadshot suffix \"ombs (F7)\"");
                    net.sendCommand("team join woadshot 🎂");
                    net.sendCommand("scoreboard players set 🎂 woadshot 1");
                    net.sendCommand("summon armor_stand ^-1 ^ ^3 {Tags:[\"woadshot\"],equipment:{head:{id:\"minecraft:skeleton_skull\",count:1}}}");
                    net.sendCommand("summon armor_stand ^ ^ ^3 {Tags:[\"woadshot\"],equipment:{head:{id:\"minecraft:zombie_head\",count:1}}}");
                    net.sendCommand("summon armor_stand ^1 ^ ^3 {Tags:[\"woadshot\"],NoGravity:1b,Motion:[0.0,0.0,0.02],equipment:{head:{id:\"minecraft:skeleton_skull\",count:1}}}");
                });
                act(mc -> {});
                act(mc -> {});
                act(mc -> org.slf4j.LoggerFactory.getLogger("Woad").info("[shot] catacombs={} sidebar={}",
                        com.pureblue.woad.core.SkyBlockArea.inCatacombs(), com.pureblue.woad.core.SkyBlockArea.sidebarLines(mc)));
                grab("skulls_hidden");
                act(mc -> setSkulls(false));
                act(mc -> {});
                grab("skulls_shown");
                act(mc -> {
                    setSkulls(true);
                    var net = mc.player.connection;
                    net.sendCommand("kill @e[tag=woadshot]");
                    net.sendCommand("scoreboard objectives remove woadshot");
                    net.sendCommand("team remove woadshot");
                });
                WoadScreen menu = new WoadScreen();
                open(menu);
                act(mc -> menu.selectFeature("Render Optimizer"));
                grab("skulls_menu");
            }
            case "archer" -> {
                act(mc -> {
                    mc.setScreen(null);
                    var net = mc.player.connection;
                    net.sendCommand("scoreboard objectives add woadshot dummy \"SKYBLOCK\"");
                    net.sendCommand("scoreboard objectives setdisplay sidebar woadshot");
                    net.sendCommand("team add woadshot");
                    net.sendCommand("team modify woadshot prefix \" ⏣ The Catac\"");
                    net.sendCommand("team modify woadshot suffix \"ombs (F7)\"");
                    net.sendCommand("team join woadshot 🎂");
                    net.sendCommand("scoreboard players set 🎂 woadshot 1");
                });
                act(mc -> {});
                act(mc -> {
                    var net = mc.player.connection;
                    // Two bone meals (the passive) left and right, a bone in the middle as the control.
                    net.sendCommand("summon item ^-1 ^1 ^3 {Tags:[\"woadshot\"],NoGravity:1b,PickupDelay:32767,Item:{id:\"minecraft:bone_meal\",count:1}}");
                    net.sendCommand("summon item ^ ^1 ^3 {Tags:[\"woadshot\"],NoGravity:1b,PickupDelay:32767,Item:{id:\"minecraft:bone\",count:1}}");
                    net.sendCommand("summon item ^1 ^1 ^3 {Tags:[\"woadshot\"],NoGravity:1b,PickupDelay:32767,Item:{id:\"minecraft:bone_meal\",count:1}}");
                });
                act(mc -> {});
                grab("archer_hidden");
                act(mc -> {
                    for (var setting : com.pureblue.woad.core.FeatureManager.RENDER_OPTIMIZER.getSettings()) {
                        if (setting.getName().equals("Hide archer passive")) ((com.pureblue.woad.core.setting.BooleanSetting) setting).toggle();
                    }
                    var net = mc.player.connection;
                    net.sendCommand("kill @e[tag=woadshot]");
                    net.sendCommand("summon item ^-1 ^1 ^3 {Tags:[\"woadshot\"],NoGravity:1b,PickupDelay:32767,Item:{id:\"minecraft:bone_meal\",count:1}}");
                    net.sendCommand("summon item ^ ^1 ^3 {Tags:[\"woadshot\"],NoGravity:1b,PickupDelay:32767,Item:{id:\"minecraft:bone\",count:1}}");
                    net.sendCommand("summon item ^1 ^1 ^3 {Tags:[\"woadshot\"],NoGravity:1b,PickupDelay:32767,Item:{id:\"minecraft:bone_meal\",count:1}}");
                });
                act(mc -> {});
                grab("archer_shown");
                act(mc -> {
                    for (var setting : com.pureblue.woad.core.FeatureManager.RENDER_OPTIMIZER.getSettings()) {
                        if (setting.getName().equals("Hide archer passive")) ((com.pureblue.woad.core.setting.BooleanSetting) setting).toggle();
                    }
                    com.pureblue.woad.config.ConfigStore.save();
                    var net = mc.player.connection;
                    net.sendCommand("kill @e[tag=woadshot]");
                    net.sendCommand("scoreboard objectives remove woadshot");
                    net.sendCommand("team remove woadshot");
                });
                WoadScreen menu = new WoadScreen();
                open(menu);
                act(mc -> menu.selectFeature("Render Optimizer"));
                grab("archer_menu");
            }
            default -> grab("unknown_sequence");
        }
    }
}
