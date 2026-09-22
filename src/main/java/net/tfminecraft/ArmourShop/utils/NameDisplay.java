package net.tfminecraft.ArmourShop.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.configuration.ConfigurationSection;

import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

/**
 * Loads plain name + colour(s) + styles from YAML and formats for display.
 */
public final class NameDisplay {

	private static final Pattern LEADING_HEX = Pattern.compile(
		"^(?:\u00A7)?#([0-9A-Fa-f]{6})(.*)$"
	);
	private static final Pattern LEADING_LEGACY = Pattern.compile(
		"^[\u00A7&]([0-9A-Fa-fk-or])(.*)$",
		Pattern.CASE_INSENSITIVE
	);

	private NameDisplay() {}

	public static String formatFromConfig(ConfigurationSection config, String nameKey) {
		ParsedName parsed = parseFromConfig(config, nameKey);
		return StringFormatter.formatDisplayName(parsed.plain, parsed.colours, parsed.styles);
	}

	/**
	 * Plain name + colour stops + styles from YAML (before formatting).
	 * Callers that append suffixes (e.g. " Chestplate") should format the full
	 * plain string so gradients span the entire display name.
	 */
	public static ParsedName parseFromConfig(ConfigurationSection config, String nameKey) {
		if (config == null) {
			return new ParsedName("", List.of(), List.of());
		}
		String rawName = config.getString(nameKey, "");
		if (rawName == null) {
			rawName = "";
		}
		List<String> colours = readColours(config);
		List<String> styles = readStyles(config);

		String plain = rawName;
		if (colours.isEmpty()) {
			Peeled peeled = peelEmbedded(rawName);
			plain = peeled.plain;
			if (peeled.colour != null) {
				colours.add(peeled.colour);
			}
		} else {
			// name should already be plain after migrate; strip leftover embed if present
			Peeled peeled = peelEmbedded(rawName);
			if (peeled.colour != null) {
				plain = peeled.plain;
			}
		}
		return new ParsedName(plain, List.copyOf(colours), List.copyOf(styles));
	}

	public static final class ParsedName {
		public final String plain;
		public final List<String> colours;
		public final List<String> styles;

		public ParsedName(String plain, List<String> colours, List<String> styles) {
			this.plain = plain == null ? "" : plain;
			this.colours = colours == null ? List.of() : colours;
			this.styles = styles == null ? List.of() : styles;
		}
	}

	public static List<String> readColours(ConfigurationSection config) {
		List<String> out = new ArrayList<>();
		if (config == null) {
			return out;
		}
		if (config.isList("colour")) {
			for (Object o : config.getList("colour", List.of())) {
				if (o != null) {
					out.add(o.toString().trim());
				}
			}
		} else if (config.isList("colors")) {
			for (Object o : config.getList("colors", List.of())) {
				if (o != null) {
					out.add(o.toString().trim());
				}
			}
		} else if (config.isString("colour")) {
			String c = config.getString("colour");
			if (c != null && !c.isBlank()) {
				out.add(c.trim());
			}
		} else if (config.isString("color")) {
			String c = config.getString("color");
			if (c != null && !c.isBlank()) {
				out.add(c.trim());
			}
		}
		return out;
	}

	public static List<String> readStyles(ConfigurationSection config) {
		List<String> out = new ArrayList<>();
		if (config == null || !config.isList("styles")) {
			return out;
		}
		for (Object o : config.getList("styles", List.of())) {
			if (o == null) {
				continue;
			}
			String s = o.toString().trim().toLowerCase(Locale.ROOT);
			if (s.equals("bold") || s.equals("italic") || s.equals("underline")
				|| s.equals("underlined") || s.equals("strikethrough") || s.equals("strike")) {
				out.add(s);
			}
		}
		return out;
	}

	private static Peeled peelEmbedded(String raw) {
		if (raw == null || raw.isEmpty()) {
			return new Peeled("", null);
		}
		Matcher hex = LEADING_HEX.matcher(raw);
		if (hex.matches()) {
			String rest = hex.group(2) == null ? "" : hex.group(2).trim();
			return new Peeled(rest.isEmpty() ? raw : rest, "#" + hex.group(1).toLowerCase(Locale.ROOT));
		}
		Matcher leg = LEADING_LEGACY.matcher(raw);
		if (leg.matches()) {
			String rest = leg.group(2) == null ? "" : leg.group(2).trim();
			return new Peeled(
				rest.isEmpty() ? raw : rest,
				"\u00A7" + Character.toLowerCase(leg.group(1).charAt(0))
			);
		}
		return new Peeled(raw, null);
	}

	private static final class Peeled {
		final String plain;
		final String colour;

		Peeled(String plain, String colour) {
			this.plain = plain;
			this.colour = colour;
		}
	}
}
