package org.openraffle.image;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Real encoded pictures for tests, made on the spot. */
public final class TestImages {

    private TestImages() {
    }

    public static byte[] png(int width, int height) {
        return encode(width, height, "png", true);
    }

    public static byte[] jpeg(int width, int height) {
        return encode(width, height, "jpg", false);
    }

    public static byte[] gif(int width, int height) {
        return encode(width, height, "gif", false);
    }

    private static byte[] encode(int width, int height, String format, boolean alpha) {
        BufferedImage image = new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLUE);
        g.fillOval(width / 4, height / 4, width / 2, height / 2);
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
