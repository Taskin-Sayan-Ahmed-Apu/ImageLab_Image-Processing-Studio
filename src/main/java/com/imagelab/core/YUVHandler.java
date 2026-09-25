package com.imagelab.core;

import java.awt.image.BufferedImage;
import java.io.*;

public final class YUVHandler {
    private YUVHandler() {}

    public static void write(BufferedImage img, File file) throws IOException {
        int w = img.getWidth(), h = img.getHeight();
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            out.writeBytes("YUV444\n" + w + " " + h + "\n");
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) out.writeByte(luma(img.getRGB(x, y)));
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) out.writeByte(chromaU(img.getRGB(x, y)));
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) out.writeByte(chromaV(img.getRGB(x, y)));
        }
    }

    public static BufferedImage read(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            StringBuilder header = new StringBuilder();
            int c;
            while ((c = in.read()) != '\n') header.append((char) c);
            if (!header.toString().equals("YUV444")) throw new IOException("Unsupported YUV header");
            header.setLength(0);
            while ((c = in.read()) != '\n') header.append((char) c);
            String[] parts = header.toString().trim().split("\\s+");
            int w = Integer.parseInt(parts[0]);
            int h = Integer.parseInt(parts[1]);
            byte[] Y = in.readNBytes(w * h);
            byte[] U = in.readNBytes(w * h);
            byte[] V = in.readNBytes(w * h);
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int idx = y * w + x;
                    int yy = Y[idx] & 0xFF;
                    int uu = U[idx] & 0xFF;
                    int vv = V[idx] & 0xFF;
                    double u = (uu / 255.0 - 0.5) * 2 * 0.436;
                    double v = (vv / 255.0 - 0.5) * 2 * 0.615;
                    int r = ImageOps.clamp((int) (yy + 1.13983 * v));
                    int g = ImageOps.clamp((int) (yy - 0.39465 * u - 0.58060 * v));
                    int b = ImageOps.clamp((int) (yy + 2.03211 * u));
                    img.setRGB(x, y, (r << 16) | (g << 8) | b);
                }
            }
            return img;
        }
    }

    private static int luma(int rgb) {
        return ImageOps.clamp((int) (0.299 * ((rgb >> 16) & 0xFF)
                + 0.587 * ((rgb >> 8) & 0xFF)
                + 0.114 * (rgb & 0xFF)));
    }
    private static int chromaU(int rgb) {
        double r = ((rgb >> 16) & 0xFF) / 255.0;
        double g = ((rgb >> 8) & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;
        return ImageOps.clamp((int) ((-0.14713 * r - 0.28886 * g + 0.436 * b) / (2 * 0.436) * 255 + 128));
    }
    private static int chromaV(int rgb) {
        double r = ((rgb >> 16) & 0xFF) / 255.0;
        double g = ((rgb >> 8) & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;
        return ImageOps.clamp((int) ((0.615 * r - 0.51499 * g - 0.10001 * b) / (2 * 0.615) * 255 + 128));
    }
}