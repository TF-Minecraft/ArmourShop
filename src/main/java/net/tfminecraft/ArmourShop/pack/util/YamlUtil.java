package net.tfminecraft.ArmourShop.pack.util;

/**
 * Minimal YAML helpers for pack writers.
 */
public final class YamlUtil {

	private YamlUtil() {}

	public static String escapeDoubleQuoted(String raw) {
		if (raw == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder(raw.length() + 8);
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			switch (c) {
				case '\\':
				case '"':
					sb.append('\\').append(c);
					break;
				case '\n':
					sb.append("\\n");
					break;
				case '\r':
					sb.append("\\r");
					break;
				case '\t':
					sb.append("\\t");
					break;
				default:
					sb.append(c);
			}
		}
		return sb.toString();
	}

	public static void validateSlug(String slug) {
		for (int i = 0; i < slug.length(); i++) {
			char c = slug.charAt(i);
			if (!(c >= 'a' && c <= 'z'
				|| c >= '0' && c <= '9'
				|| c == '_')) {
				throw new IllegalArgumentException(
					"slug must be snake_case [a-z0-9_]: " + slug
				);
			}
		}
	}
}
