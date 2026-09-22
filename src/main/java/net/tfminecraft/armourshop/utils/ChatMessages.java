package net.tfminecraft.armourshop.utils;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Prefixed chat helpers, including click-to-copy codes.
 */
public final class ChatMessages {

	public static final String PREFIX = "\u00A7a[ArmourShop] \u00A7r";

	private ChatMessages() {}

	public static void info(Player player, String message) {
		player.sendMessage(PREFIX + message);
	}

	public static void error(Player player, String message) {
		player.sendMessage(PREFIX + "\u00A7c" + message);
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

		Component label = Component.text("Code: ", NamedTextColor.GRAY);
		Component codeComp = Component.text(code, NamedTextColor.AQUA)
				.decorate(TextDecoration.BOLD)
				.clickEvent(ClickEvent.copyToClipboard(code))
				.hoverEvent(HoverEvent.showText(Component.text("Click to copy")));
		Component hint = Component.text(" (click to copy)", NamedTextColor.DARK_GRAY)
				.decorate(TextDecoration.ITALIC);

		player.sendMessage(Component.empty().append(label).append(codeComp).append(hint));
	}

	/**
	 * One active-token list line with a red [Delete] that runs token delete.
	 */
	public static void sendTokenListLine(Player player, String code, String ownerLabel) {
		if (code == null || code.isEmpty()) {
			return;
		}
		String owner = ownerLabel == null || ownerLabel.isBlank() ? "?" : ownerLabel.trim();

		Component line = Component.text(code, NamedTextColor.AQUA);
		Component sep = Component.text(" - ", NamedTextColor.GRAY);
		Component ownerComp = Component.text(owner, NamedTextColor.YELLOW);
		Component space = Component.text(" ");
		Component delete = Component.text("[Delete]", NamedTextColor.RED)
				.decorate(TextDecoration.BOLD)
				.clickEvent(ClickEvent.runCommand("/armourshop token delete " + code))
				.hoverEvent(HoverEvent.showText(Component.text("Click to delete")));

		player.sendMessage(Component.empty().append(line).append(sep).append(ownerComp).append(space).append(delete));
	}
}
