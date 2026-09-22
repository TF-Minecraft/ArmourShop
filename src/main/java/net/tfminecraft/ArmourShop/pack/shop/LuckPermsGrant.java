package net.tfminecraft.ArmourShop.pack.shop;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.PermissionNode;

/**
 * Grants ArmourShop submission permissions via the LuckPerms API (no console commands).
 * Prefer calling off the main thread — {@code loadUser} may block.
 */
public final class LuckPermsGrant {

	private LuckPermsGrant() {}

	/**
	 * Sets {@code armourshop.submission.<slug>} true for the player UUID.
	 *
	 * @return false if LuckPerms is missing/disabled or inputs/API call fail
	 */
	public static boolean grantSubmission(UUID playerUuid, String slug, Logger log) {
		if (playerUuid == null) {
			if (log != null) {
				log.warning("[lp] grant failed: player uuid is null");
			}
			return false;
		}
		if (slug == null || slug.isBlank()) {
			if (log != null) {
				log.warning("[lp] grant failed: slug is blank");
			}
			return false;
		}

		Plugin plugin = Bukkit.getPluginManager().getPlugin("LuckPerms");
		if (plugin == null || !plugin.isEnabled()) {
			if (log != null) {
				log.severe("[lp] LuckPerms is not enabled — cannot grant armourshop.submission."
					+ slug.trim());
			}
			return false;
		}

		String node = "armourshop.submission." + slug.trim();
		try {
			LuckPerms api = LuckPermsProvider.get();
			User user = api.getUserManager().loadUser(playerUuid).join();
			if (user == null) {
				if (log != null) {
					log.warning("[lp] loadUser returned null for " + playerUuid);
				}
				return false;
			}
			user.data().add(PermissionNode.builder(node).value(true).build());
			api.getUserManager().saveUser(user).join();
			if (log != null) {
				log.info("[lp] granted " + node + " to " + playerUuid);
			}
			return true;
		} catch (IllegalStateException e) {
			if (log != null) {
				log.severe("[lp] LuckPerms API not ready: " + e.getMessage());
			}
			return false;
		} catch (Exception e) {
			if (log != null) {
				log.log(Level.WARNING, "[lp] grant failed for " + node + " / " + playerUuid, e);
			}
			return false;
		}
	}

	/**
	 * Removes {@code armourshop.submission.<slug>} for the player UUID.
	 *
	 * @return false if LuckPerms is missing/disabled or inputs/API call fail
	 */
	public static boolean revokeSubmission(UUID playerUuid, String slug, Logger log) {
		if (playerUuid == null) {
			if (log != null) {
				log.warning("[lp] revoke failed: player uuid is null");
			}
			return false;
		}
		if (slug == null || slug.isBlank()) {
			if (log != null) {
				log.warning("[lp] revoke failed: slug is blank");
			}
			return false;
		}

		Plugin plugin = Bukkit.getPluginManager().getPlugin("LuckPerms");
		if (plugin == null || !plugin.isEnabled()) {
			if (log != null) {
				log.severe("[lp] LuckPerms is not enabled — cannot revoke armourshop.submission."
					+ slug.trim());
			}
			return false;
		}

		String node = "armourshop.submission." + slug.trim();
		try {
			LuckPerms api = LuckPermsProvider.get();
			User user = api.getUserManager().loadUser(playerUuid).join();
			if (user == null) {
				if (log != null) {
					log.warning("[lp] loadUser returned null for " + playerUuid);
				}
				return false;
			}
			user.data().remove(PermissionNode.builder(node).value(true).build());
			api.getUserManager().saveUser(user).join();
			if (log != null) {
				log.info("[lp] revoked " + node + " from " + playerUuid);
			}
			return true;
		} catch (IllegalStateException e) {
			if (log != null) {
				log.severe("[lp] LuckPerms API not ready: " + e.getMessage());
			}
			return false;
		} catch (Exception e) {
			if (log != null) {
				log.log(Level.WARNING, "[lp] revoke failed for " + node + " / " + playerUuid, e);
			}
			return false;
		}
	}
}
