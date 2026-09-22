package net.tfminecraft.ArmourShop.utils;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * Prefixed chat helpers, including click-to-copy codes.
 */
public final class ChatMessages {

	public static final String PREFIX = ChatColor.GREEN + "[ArmourShop] " + ChatColor.RESET;

	private ChatMessages() {}

	public static void info(Player player, String message) {
		player.sendMessage(PREFIX + message);
	}

	public static void error(Player player, String message) {
		player.sendMessage(PREFIX + ChatColor.RED + message);
	}

	/**
	 * Sends an intro line (if non-blank), then a clickable code that copies to clipboard.
	 */
	public static void sendCopyableCode(Player player, String intro, String code) {
		if (intro != null && !intro.isEmpty()) {
			player.sendMessage(PREFIX + intro);
		}
		if (code == null || code.isEmpty()) {
			return;
		}

		TextComponent label = new TextComponent("Code: ");
		label.setColor(net.md_5.bungee.api.ChatColor.GRAY);

		TextComponent codeComp = new TextComponent(code);
		codeComp.setColor(net.md_5.bungee.api.ChatColor.AQUA);
		codeComp.setBold(true);
		codeComp.setClickEvent(
			new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, code)
		);
		@SuppressWarnings("deprecation")
		HoverEvent hover = new HoverEvent(
			HoverEvent.Action.SHOW_TEXT,
			new ComponentBuilder("Click to copy").create()
		);
		codeComp.setHoverEvent(hover);

		TextComponent hint = new TextComponent(" (click to copy)");
		hint.setColor(net.md_5.bungee.api.ChatColor.DARK_GRAY);
		hint.setItalic(true);

		player.spigot().sendMessage(label, codeComp, hint);
	}

	/**
	 * One active-token list line with a red [Delete] that runs token delete.
	 */
	public static void sendTokenListLine(Player player, String code, String ownerLabel) {
		if (code == null || code.isEmpty()) {
			return;
		}
		String owner = ownerLabel == null || ownerLabel.isBlank() ? "?" : ownerLabel.trim();

		TextComponent line = new TextComponent(code);
		line.setColor(net.md_5.bungee.api.ChatColor.AQUA);

		TextComponent sep = new TextComponent(" - ");
		sep.setColor(net.md_5.bungee.api.ChatColor.GRAY);

		TextComponent ownerComp = new TextComponent(owner);
		ownerComp.setColor(net.md_5.bungee.api.ChatColor.YELLOW);

		TextComponent space = new TextComponent(" ");

		TextComponent delete = new TextComponent("[Delete]");
		delete.setColor(net.md_5.bungee.api.ChatColor.RED);
		delete.setBold(true);
		delete.setClickEvent(
			new ClickEvent(
				ClickEvent.Action.RUN_COMMAND,
				"/armourshop token delete " + code
			)
		);
		@SuppressWarnings("deprecation")
		HoverEvent hover = new HoverEvent(
			HoverEvent.Action.SHOW_TEXT,
			new ComponentBuilder("Click to delete").create()
		);
		delete.setHoverEvent(hover);

		player.spigot().sendMessage(line, sep, ownerComp, space, delete);
	}
}
