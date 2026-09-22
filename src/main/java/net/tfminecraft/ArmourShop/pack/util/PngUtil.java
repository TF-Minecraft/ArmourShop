package net.tfminecraft.ArmourShop.pack.util;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

/**
 * PNG helpers for pack fixtures.
 */
public final class PngUtil {

	private PngUtil() {}

	public static byte[] solidPng(int w, int h, Color color) throws Exception {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		try {
			g.setColor(color);
			g.fillRect(0, 0, w, h);
		} finally {
			g.dispose();
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if (!ImageIO.write(img, "png", out)) {
			throw new IllegalStateException("ImageIO failed to write PNG");
		}
		return out.toByteArray();
	}
}
