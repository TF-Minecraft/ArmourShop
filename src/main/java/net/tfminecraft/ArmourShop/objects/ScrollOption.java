package net.tfminecraft.ArmourShop.objects;

/**
 * One scroll option from config.yml scrolls list.
 */
public final class ScrollOption {
	private final String id;
	private final String label;

	public ScrollOption(String id, String label) {
		this.id = id == null ? "" : id.trim();
		String lbl = label == null ? "" : label.trim();
		this.label = lbl.isEmpty() ? this.id : lbl;
	}

	public String getId() {
		return id;
	}

	public String getLabel() {
		return label;
	}
}
