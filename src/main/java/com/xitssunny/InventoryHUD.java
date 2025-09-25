package com.xitssunny;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

@Environment(EnvType.CLIENT)
public class InventoryHUD implements ClientModInitializer {

	private static KeyBinding toggleKey;
	private static boolean showInventory = false;

	private static int hudX = -1;
	private static int hudY = -1;

	private static boolean isDragging = false;
	private static int dragOffsetX = 0;
	private static int dragOffsetY = 0;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_FILE = MinecraftClient.getInstance().runDirectory.toPath().resolve("config/inventoryhud.json");

	private static class Config {
		boolean enabled = false;
		int hudX = -1;
		int hudY = -1;
	}

	@Override
	public void onInitializeClient() {
		loadConfig();

		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"Toggle InventoryHUD",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_I,
				"InventoryHUD"
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.wasPressed()) {
				showInventory = !showInventory;
				saveConfig();
			}

			if (showInventory && client.currentScreen instanceof ChatScreen) {
				double mouseX = client.mouse.getX() * (double) client.getWindow().getScaledWidth() / client.getWindow().getWidth();
				double mouseY = client.mouse.getY() * (double) client.getWindow().getScaledHeight() / client.getWindow().getHeight();
				long window = client.getWindow().getHandle();

				if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
					if (!isDragging &&
							mouseX >= hudX && mouseX <= hudX + 9 * 20 &&
							mouseY >= hudY && mouseY <= hudY + 3 * 20) {
						isDragging = true;
						dragOffsetX = (int) mouseX - hudX;
						dragOffsetY = (int) mouseY - hudY;
					}
					if (isDragging) {
						hudX = (int) mouseX - dragOffsetX;
						hudY = (int) mouseY - dragOffsetY;
						saveConfig();
					}
				} else {
					isDragging = false;
				}
			} else {
				isDragging = false;
			}
		});

		HudRenderCallback.EVENT.register((drawContext, tickDelta) -> renderOverlay(drawContext));
	}

	private static void renderOverlay(DrawContext drawContext) {
		if (!showInventory) return;

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) return;

		int screenWidth = client.getWindow().getScaledWidth();
		int screenHeight = client.getWindow().getScaledHeight();

		if (hudX == -1 || hudY == -1) {
			hudX = (screenWidth / 2) - 90;
			hudY = screenHeight - 22 - (3 * 20) - 2;
		}

		int slotSize = 20;

		for (int i = 9; i < 36; i++) {
			ItemStack stack = client.player.getInventory().getStack(i);
			int row = (i - 9) / 9;
			int col = (i - 9) % 9;

			int slotX = hudX + col * slotSize;
			int slotY = hudY + row * slotSize;

			drawContext.fill(slotX, slotY, slotX + 16, slotY + 16, 0x80000000);

			drawContext.drawItem(stack, slotX, slotY);

			drawContext.drawStackOverlay(client.textRenderer, stack, slotX, slotY, null);
		}
	}

	private static void loadConfig() {
		if (!Files.exists(CONFIG_FILE)) return;
		try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
			Config cfg = GSON.fromJson(reader, Config.class);
			showInventory = cfg.enabled;
			hudX = cfg.hudX;
			hudY = cfg.hudY;
		} catch (IOException | JsonSyntaxException e) {
			System.err.println("Failed to load inventoryhud config: " + e.getMessage());
		}
	}

	private static void saveConfig() {
		Config cfg = new Config();
		cfg.enabled = showInventory;
		cfg.hudX = hudX;
		cfg.hudY = hudY;

		try {
			Files.createDirectories(CONFIG_FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
				GSON.toJson(cfg, writer);
			}
		} catch (IOException e) {
			System.err.println("Failed to save inventoryhud config: " + e.getMessage());
		}
	}
}
