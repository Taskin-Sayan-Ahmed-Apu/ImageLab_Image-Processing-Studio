package com.imagelab.core;

import java.awt.image.BufferedImage;
import java.io.*;

public final class PPMHandler {
    private PPMHandler() {}

    public static BufferedImage read(File file) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            String magic = token(in);
            if (!magic.equals("P3") && !magic.equals("P6"))
                throw new IOException("Unsupported PPM magic: " + magic);
            int w = Integer.parseInt(token(in));
            int h = Integer.parseInt(token(in));
            int max = Integer.parseInt(token(in));
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            if (magic.equals("P3")) {
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++) {
                        int r = Integer.parseInt(token(in)) * 255 / max;
                        int g = Integer.parseInt(token(in)) * 255 / max;
                        int b = Integer.parseInt(token(in)) * 255 / max;
                        img.setRGB(x, y, (r << 16) | (g << 8) | b);
                    }
            } else {
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++) {
                        int r = in.read() * 255 / max;
                        int g = in.read() * 255 / max;
                        int b = in.read() * 255 / max;
                        img.setRGB(x, y, (r << 16) | (g << 8) | b);
                    }
            }
            return img;
        }
    }

    public static void write(BufferedImage img, File file) throws IOException {
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            out.write(("P6\n" + img.getWidth() + " " + img.getHeight() + "\n255\n").getBytes());
            for (int y = 0; y < img.getHeight(); y++)
                for (int x = 0; x < img.getWidth(); x++) {
                    int rgb = img.getRGB(x, y);
                    out.write((rgb >> 16) & 0xFF);
                    out.write((rgb >> 8) & 0xFF);
                    out.write(rgb & 0xFF);
                }
        }
    }

    private static String token(InputStream in) throws IOException {
        int c;
        do {
            c = in.read();
            if (c == '#') while (c != '\n' && c != -1) c = in.read();
        } while (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '#');
        StringBuilder sb = new StringBuilder();
        while (c > ' ' && c != -1) {
            sb.append((char) c);
            c = in.read();
        }
        return sb.toString();
    }
}