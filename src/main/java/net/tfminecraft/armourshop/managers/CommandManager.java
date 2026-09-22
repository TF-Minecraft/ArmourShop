package net.tfminecraft.armourshop.managers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.subapi.ArmorMerger;
import net.tfminecraft.armourshop.ArmourShop;
import net.tfminecraft.armourshop.api.ProvinceSystemClient;
import net.tfminecraft.armourshop.loaders.CategoryLoader;
import net.tfminecraft.armourshop.objects.SkinCategory;
import net.tfminecraft.armourshop.objects.SkinSet;
import net.tfminecraft.armourshop.pack.apply.PackPullRunner;
import net.tfminecraft.armourshop.pack.catalog.CatalogSyncService;
import net.tfminecraft.armourshop.pack.reload.DeferredIaReloadService;
import net.tfminecraft.armourshop.utils.ChatMessages;
import net.tfminecraft.armourshop.utils.Permissions;


public class CommandManager implements Listener, CommandExecutor, TabCompleter {
	public String cmd1 = "armourshop";

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (!cmd.getName().equalsIgnoreCase(cmd1)) {
			return false;
		}

		if (args.length == 0) {
			if (sender instanceof Player) {
				Player player = (Player) sender;
				InventoryManager i = new InventoryManager();
				i.typeView(player);
				return true;
			}
			return false;
		}

		if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
			if (Permissions.isAdmin(sender)) {
				if (sender instanceof Player) {
					Player p = (Player) sender;
					JavaPlugin.getPlugin(ArmourShop.class).reloadMessage(p);
				} else {
					JavaPlugin.getPlugin(ArmourShop.class).reload();
				}
				return true;
			}
			if (sender instanceof Player) {
				Player p = (Player) sender;
				p.sendMessage("\u00A7a[ArmourShop] \u00A7cYou do not have access to this command");
			}
			return true;
		}

		if (args.length == 2
			&& args[0].equalsIgnoreCase("token")
			&& args[1].equalsIgnoreCase("create")) {
			sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
				+ ChatColor.YELLOW + "Use "
				+ ChatColor.AQUA + "/token create skin"
				+ ChatColor.YELLOW + " (TFMCWeb) instead.");
			return true;
		}

		if (args.length == 2
			&& args[0].equalsIgnoreCase("token")
			&& args[1].equalsIgnoreCase("delete")) {
			sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
				+ ChatColor.RED + "Usage: /armourshop token delete <code>");
			return true;
		}

		if (args.length >= 3
			&& args[0].equalsIgnoreCase("token")
			&& args[1].equalsIgnoreCase("delete")) {
			return handleTokenDelete(sender, args[2]);
		}

		if (args.length == 1 && args[0].equalsIgnoreCase("listtokens")) {
			return handleListTokens(sender);
		}

		if (args.length == 2
			&& args[0].equalsIgnoreCase("pack")
			&& args[1].equalsIgnoreCase("pull")) {
			return handlePackPull(sender);
		}

		if (args.length == 2
			&& args[0].equalsIgnoreCase("pack")
			&& args[1].equalsIgnoreCase("sync")) {
			return handlePackSync(sender);
		}

		if (args.length == 2
			&& args[0].equalsIgnoreCase("catalog")
			&& args[1].equalsIgnoreCase("sync")) {
			return handleCatalogSync(sender);
		}

		if (args.length == 2
			&& args[0].equalsIgnoreCase("submission")
			&& args[1].equalsIgnoreCase("delete")) {
			sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
				+ ChatColor.RED + "Usage: /armourshop submission delete <id>");
			return true;
		}

		if (args.length >= 3
			&& args[0].equalsIgnoreCase("submission")
			&& args[1].equalsIgnoreCase("delete")) {
			return handleSubmissionDelete(sender, args[2]);
		}

		if (args.length == 2
			&& args[0].equalsIgnoreCase("skin")
			&& args[1].equalsIgnoreCase("delete")) {
			sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
				+ ChatColor.RED + "Usage: /armourshop skin delete <id>");
			return true;
		}

		if (args.length >= 3
			&& args[0].equalsIgnoreCase("skin")
			&& args[1].equalsIgnoreCase("delete")) {
			return handleSkinDelete(sender, args[2]);
		}

		if (args.length >= 1 && args[0].equalsIgnoreCase("model")) {
			if (args.length >= 4
				&& args[1].equalsIgnoreCase("apply")) {
				return handleModelApply(sender, args[2], args[3]);
			}
			sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
				+ ChatColor.RED + "Usage: /armourshop model apply <category_id> <skin_id>");
			return true;
		}

		return false;
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private boolean handleModelApply(CommandSender sender, String categoryId, String skinId) {
		if (!Permissions.isAdmin(sender)) {
			sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
				+ ChatColor.RED + "You do not have access to this command");
			return true;
		}
		if (!(sender instanceof Player)) {
			sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
				+ ChatColor.RED + "Only a player can apply a model to a held item");
			return true;
		}
		Player player = (Player) sender;
		ItemStack held = player.getInventory().getItemInMainHand();
		if (held == null || held.getType().isAir()) {
			player.sendMessage(ChatColor.GREEN + "[ArmourShop] "
				+ ChatColor.RED + "Hold the item you want to skin");
			return true;
		}
		SkinCategory category = CategoryLoader.getByString(categoryId);
		if (category == null) {
			player.sendMessage(ChatColor.GREEN + "[ArmourShop] "
				+ ChatColor.RED + "Unknown category: " + categoryId);
			return true;
		}
		SkinSet set = null;
		for (SkinSet candidate : category.getSets()) {
			if (candidate.getId().equalsIgnoreCase(skinId)) {
				set = candidate;
				break;
			}
		}
		if (set == null) {
			player.sendMessage(ChatColor.GREEN + "[ArmourShop] "
				+ ChatColor.RED + "Unknown skin: " + skinId);
			return true;
		}
		String path = modelPath(set, held);
		if (path == null) {
			player.sendMessage(ChatColor.GREEN + "[ArmourShop] "
				+ ChatColor.RED + "That skin has no model for this item. Pieces: "
				+ pieceList(set));
			return true;
		}
		ItemMeta previous = held.getItemMeta();
		BookMeta previousBook = null;
		if (previous instanceof BookMeta) {
			previousBook = (BookMeta) previous.clone();
		}
		Optional<String> name = set.addName()
			? Optional.of(set.getName())
			: Optional.empty();
		ItemStack merged;
		try {
			ArmorMerger merger = TLibs.getItemAPI().getArmorMerger();
			merged = merger.merge(held.clone(), name, path);
		} catch (Exception ex) {
			player.sendMessage(ChatColor.GREEN + "[ArmourShop] "
				+ ChatColor.RED + "Could not apply that model");
			ArmourShop.plugin.getLogger().warning(
				"[model-apply] failed " + categoryId + " " + skinId + ": " + ex.getMessage()
			);
			return true;
		}
		if (merged == null || merged.getType().isAir()) {
			player.sendMessage(ChatColor.GREEN + "[ArmourShop] "
				+ ChatColor.RED + "Could not apply that model");
			return true;
		}
		merged.setAmount(Math.max(1, held.getAmount()));
		if (previousBook != null) {
			restoreBook(merged, previousBook, set.addName());
		}
		player.getInventory().setItemInMainHand(merged);
		player.sendMessage(ChatColor.GREEN + "[ArmourShop] "
			+ ChatColor.YELLOW + "Applied " + set.getId() + " to the item in your hand");
		return true;
	}

	/**
	 * Item skins (books included) use the set's item path. Armor sets pick the
	 * piece that matches the held material.
	 */
	private static String modelPath(SkinSet set, ItemStack held) {
		if (set.hasItem()) {
			return set.getItem();
		}
		String material = held.getType().name();
		if (material.endsWith("_HELMET") && set.hasHelmet()) {
			return set.getHelmet();
		}
		if (material.endsWith("_CHESTPLATE") && set.hasChestplate()) {
			return set.getChestplate();
		}
		if (material.endsWith("_LEGGINGS") && set.hasLeggings()) {
			return set.getLeggings();
		}
		if (material.endsWith("_BOOTS") && set.hasBoots()) {
			return set.getBoots();
		}
		return null;
	}

	private static String pieceList(SkinSet set) {
		List<String> pieces = new ArrayList<>();
		if (set.hasItem()) {
			pieces.add("item");
		}
		if (set.hasHelmet()) {
			pieces.add("helmet");
		}
		if (set.hasChestplate()) {
			pieces.add("chestplate");
		}
		if (set.hasLeggings()) {
			pieces.add("leggings");
		}
		if (set.hasBoots()) {
			pieces.add("boots");
		}
		if (pieces.isEmpty()) {
			return "none";
		}
		return String.join(", ", pieces);
	}

	/** setType inside the merger clears book meta. Put the writing back. */
	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private static void restoreBook(ItemStack merged, BookMeta previous, boolean replaceName) {
		ItemMeta meta = merged.getItemMeta();
		if (!(meta instanceof BookMeta bookMeta)) {
			return;
		}
		if (previous.getPages() != null) {
			bookMeta.setPages(previous.getPages());
		}
		if (previous.hasTitle()) {
			bookMeta.setTitle(previous.getTitle());
		}
		if (previous.hasAuthor()) {
			bookMeta.setAuthor(previous.getAuthor());
		}
		if (previous.hasGeneration()) {
			bookMeta.setGeneration(previous.getGeneration());
		}
		if (!replaceName && previous.hasDisplayName()) {
			bookMeta.setDisplayName(previous.getDisplayName());
		}
		if (previous.hasLore() && previous.getLore() != null) {
			bookMeta.setLore(new ArrayList<>(previous.getLore()));
		}
		try {
			previous.getPersistentDataContainer().copyTo(bookMeta.getPersistentDataContainer(), true);
		} catch (NoSuchMethodError | UnsupportedOperationException ignored) {
			// Older API without copyTo. Name and lore are already copied.
		}
		merged.setItemMeta(bookMeta);
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private boolean handleSubmissionDelete(CommandSender sender, String submissionId) {
		if (!Permissions.isAdmin(sender)) {
			sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
				+ ChatColor.RED + "You do not have access to this command");
			return true;
		}
		sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
			+ ChatColor.YELLOW + "Deleting submission…");
		JavaPlugin plugin = JavaPlugin.getPlugin(ArmourShop.class);
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			String result = net.tfminecraft.armourshop.pack.delete.SubmissionDeleteRunner
				.run(submissionId);
			net.tfminecraft.armourshop.pack.delete.DeletableSubmissionCache.invalidate();
			Bukkit.getScheduler().runTask(plugin, () ->
				sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
					+ ChatColor.YELLOW + result)
			);
		});
		return true;
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private boolean handleSkinDelete(CommandSender sender, String skinId) {
		if (!Permissions.isAdmin(sender)) {
			sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
				+ ChatColor.RED + "You do not have access to this command");
			return true;
		}
		sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
			+ ChatColor.YELLOW + "Deleting staff skin…");
		JavaPlugin plugin = JavaPlugin.getPlugin(ArmourShop.class);
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			String result = net.tfminecraft.armourshop.pack.delete.SkinDeleteRunner
				.run(skinId);
			net.tfminecraft.armourshop.pack.delete.DeletableStaffSkinCache.invalidate();
			Bukkit.getScheduler().runTask(plugin, () ->
				sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
					+ ChatColor.YELLOW + result)
			);
		});
		return true;
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private boolean handlePackPull(CommandSender sender) {
		if (!Permissions.isAdmin(sender)) {
			sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
				+ ChatColor.RED + "You do not have access to this command");
			return true;
		}

		sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
			+ ChatColor.YELLOW + "Pulling approved skins…");
		boolean started = PackPullRunner.run(true, result -> {
			sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
				+ ChatColor.YELLOW + result.summaryLine());
			if (result.busy || result.failed) {
				return;
			}
			int shown = 0;
			for (String line : result.messages) {
				if (shown >= 8) {
					sender.sendMessage(ChatColor.GRAY + "… "
						+ (result.messages.size() - shown)
						+ " more (see console)");
					break;
				}
				sender.sendMessage(ChatColor.GRAY + line);
				shown++;
			}
		});
		if (!started) {
			sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
				+ ChatColor.YELLOW + "Pack pull already running.");
		}
		return true;
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private boolean handlePackSync(CommandSender sender) {
		if (!Permissions.isAdmin(sender)) {
			sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
				+ ChatColor.RED + "You do not have access to this command");
			return true;
		}

		ArmourShop plugin = JavaPlugin.getPlugin(ArmourShop.class);
		sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
			+ ChatColor.YELLOW + "Syncing pending-reload queue from ProvinceSystem…");
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			DeferredIaReloadService.SyncResult sync =
				plugin.getDeferredIaReloadService().syncQueueFromWebsite(plugin.getLogger());
			Bukkit.getScheduler().runTask(plugin, () -> {
				if (!sync.ok) {
					sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
						+ ChatColor.RED + "Pack sync failed: "
						+ (sync.error != null ? sync.error : "unknown error"));
					return;
				}
				sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
					+ ChatColor.YELLOW + "Pending-reload synced: "
					+ sync.before + " → " + sync.after
					+ " id(s) (approved, not yet applied).");
			});
		});
		return true;
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private boolean handleCatalogSync(CommandSender sender) {
		if (!Permissions.isAdmin(sender)) {
			sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
				+ ChatColor.RED + "You do not have access to this command");
			return true;
		}
		ArmourShop plugin = JavaPlugin.getPlugin(ArmourShop.class);
		sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
			+ ChatColor.YELLOW + "Syncing catalog to ProvinceSystem…");
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			ProvinceSystemClient.CatalogPushResult result = CatalogSyncService.pushNow();
			Bukkit.getScheduler().runTask(plugin, () -> {
				if (result.ok) {
					sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
						+ ChatColor.YELLOW + "Catalog synced: categories="
						+ result.categories
						+ " skin_sets=" + result.skinSets
						+ " scrolls=" + result.scrolls);
				} else {
					sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
						+ ChatColor.RED + "Catalog sync failed: " + result.error);
				}
			});
		});
		return true;
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private boolean handleListTokens(CommandSender sender) {
		if (!Permissions.isAdmin(sender)) {
			sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
				+ ChatColor.RED + "You do not have access to this command");
			return true;
		}

		ArmourShop plugin = JavaPlugin.getPlugin(ArmourShop.class);
		sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
			+ ChatColor.YELLOW + "Fetching active tokens…");
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			ProvinceSystemClient.ActiveCodesResult result =
				ProvinceSystemClient.listActiveCodes();
			Bukkit.getScheduler().runTask(plugin, () -> {
				if (!result.ok) {
					sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
						+ ChatColor.RED
						+ (result.error != null ? result.error : "Could not list tokens."));
					return;
				}
				if (result.codes.isEmpty()) {
					sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
						+ ChatColor.YELLOW + "No active unused tokens.");
					return;
				}
				sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
					+ ChatColor.YELLOW + "Active tokens (" + result.codes.size() + "):");
				for (ProvinceSystemClient.ActiveCode entry : result.codes) {
					String owner = ownerLabel(entry);
					if (sender instanceof Player) {
						ChatMessages.sendTokenListLine((Player) sender, entry.code, owner);
					} else {
						sender.sendMessage(ChatColor.AQUA + entry.code
							+ ChatColor.GRAY + " - "
							+ ChatColor.YELLOW + owner);
					}
				}
			});
		});
		return true;
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private boolean handleTokenDelete(CommandSender sender, String codeArg) {
		if (!Permissions.isAdmin(sender)) {
			sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
				+ ChatColor.RED + "You do not have access to this command");
			return true;
		}

		String code = codeArg == null ? "" : codeArg.trim();
		if (code.startsWith("\"") && code.endsWith("\"") && code.length() >= 2) {
			code = code.substring(1, code.length() - 1).trim();
		}
		if (code.isEmpty()) {
			sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
				+ ChatColor.RED + "Usage: /armourshop token delete <code>");
			return true;
		}

		ArmourShop plugin = JavaPlugin.getPlugin(ArmourShop.class);
		final String toRevoke = code;
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			ProvinceSystemClient.SimpleResult result =
				ProvinceSystemClient.revokeCode(toRevoke);
			Bukkit.getScheduler().runTask(plugin, () -> {
				if (!result.ok) {
					sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
						+ ChatColor.RED
						+ (result.error != null ? result.error : "Could not delete token."));
					return;
				}
				sender.sendMessage(ChatColor.GREEN + "[ArmourShop] "
					+ ChatColor.YELLOW + "Deleted token "
					+ ChatColor.AQUA + toRevoke);
			});
		});
		return true;
	}

	private static String ownerLabel(ProvinceSystemClient.ActiveCode entry) {
		if (entry.minecraftName != null && !entry.minecraftName.isBlank()) {
			return entry.minecraftName.trim();
		}
		String uuid = entry.playerUuid == null ? "" : entry.playerUuid.trim();
		if (uuid.isEmpty()) {
			return "?";
		}
		try {
			String name = Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName();
			if (name != null && !name.isBlank()) {
				return name;
			}
		} catch (IllegalArgumentException ignored) {
			// fall through
		}
		return uuid;
	}

	@Override
	public List<String> onTabComplete(
		CommandSender sender,
		Command command,
		String alias,
		String[] args
	) {
		if (!command.getName().equalsIgnoreCase(cmd1)) {
			return Collections.emptyList();
		}

		if (args.length == 1) {
			List<String> completions = new ArrayList<>();
			if (Permissions.isAdmin(sender)) {
				completions.add("token");
				completions.add("reload");
				completions.add("pack");
				completions.add("catalog");
				completions.add("listtokens");
				completions.add("submission");
				completions.add("skin");
				completions.add("model");
			}
			return filter(completions, args[0]);
		}

		if (args.length == 2 && args[0].equalsIgnoreCase("token")) {
			List<String> completions = new ArrayList<>();
			if (Permissions.isAdmin(sender)) {
				completions.add("delete");
			}
			return filter(completions, args[1]);
		}

		if (args.length == 2
			&& args[0].equalsIgnoreCase("pack")
			&& Permissions.isAdmin(sender)) {
			return filter(List.of("pull", "sync"), args[1]);
		}

		if (args.length == 2
			&& args[0].equalsIgnoreCase("catalog")
			&& Permissions.isAdmin(sender)) {
			return filter(List.of("sync"), args[1]);
		}

		if (args.length == 2
			&& args[0].equalsIgnoreCase("submission")
			&& Permissions.isAdmin(sender)) {
			return filter(Collections.singletonList("delete"), args[1]);
		}

		if (args.length == 2
			&& args[0].equalsIgnoreCase("skin")
			&& Permissions.isAdmin(sender)) {
			return filter(Collections.singletonList("delete"), args[1]);
		}

		if (args.length == 2
			&& args[0].equalsIgnoreCase("model")
			&& Permissions.isAdmin(sender)) {
			return filter(Collections.singletonList("apply"), args[1]);
		}

		if (args.length == 3
			&& args[0].equalsIgnoreCase("model")
			&& args[1].equalsIgnoreCase("apply")
			&& Permissions.isAdmin(sender)) {
			List<String> ids = new ArrayList<>();
			for (SkinCategory category : CategoryLoader.get()) {
				ids.add(category.getId());
			}
			return filter(ids, args[2]);
		}

		if (args.length == 4
			&& args[0].equalsIgnoreCase("model")
			&& args[1].equalsIgnoreCase("apply")
			&& Permissions.isAdmin(sender)) {
			SkinCategory category = CategoryLoader.getByString(args[2]);
			List<String> ids = new ArrayList<>();
			if (category != null) {
				for (SkinSet set : category.getSets()) {
					ids.add(set.getId());
				}
			}
			return filter(ids, args[3]);
		}

		if (args.length == 3
			&& args[0].equalsIgnoreCase("submission")
			&& args[1].equalsIgnoreCase("delete")
			&& Permissions.isAdmin(sender)) {
			List<String> ids = new ArrayList<>(
				net.tfminecraft.armourshop.pack.delete.DeletableSubmissionCache.snapshot()
			);
			return filter(ids, args[2]);
		}

		if (args.length == 3
			&& args[0].equalsIgnoreCase("skin")
			&& args[1].equalsIgnoreCase("delete")
			&& Permissions.isAdmin(sender)) {
			List<String> ids = new ArrayList<>(
				net.tfminecraft.armourshop.pack.delete.DeletableStaffSkinCache.snapshot()
			);
			return filter(ids, args[2]);
		}

		return Collections.emptyList();
	}

	private List<String> filter(List<String> list, String input) {
		String prefix = input == null ? "" : input.toLowerCase();
		return list.stream()
			.filter(s -> s.toLowerCase().startsWith(prefix))
			.collect(Collectors.toList());
	}
}
