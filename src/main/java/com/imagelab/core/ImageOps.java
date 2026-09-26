package com.imagelab.core;

import java.awt.image.BufferedImage;

public final class ImageOps {

    private ImageOps() {}

    public enum Channel {
        RED, GREEN, BLUE, GRAY,
        HUE, SATURATION, INTENSITY,
        YUV_Y, YUV_U, YUV_V,
        YCBCR_Y, YCBCR_CB, YCBCR_CR
    }

    public enum LogicalOp { AND, OR, XOR }

    @FunctionalInterface private interface PixelOp { int[] apply(int r, int g, int b); }
    @FunctionalInterface private interface PairOp  { int[] apply(int[] a, int[] b); }

    public static int clamp(int v) { return v < 0 ? 0 : v > 255 ? 255 : v; }

    public static BufferedImage copy(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        out.getGraphics().drawImage(src, 0, 0, null);
        return out;
    }

    private static BufferedImage perPixel(BufferedImage src, PixelOp op) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int[] n = op.apply((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
                out.setRGB(x, y, (clamp(n[0]) << 16) | (clamp(n[1]) << 8) | clamp(n[2]));
            }
        }
        return out;
    }

    private static BufferedImage perPixel2(BufferedImage a, BufferedImage b, PairOp op) {
        int w = Math.min(a.getWidth(), b.getWidth());
        int h = Math.min(a.getHeight(), b.getHeight());
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int[] p = unpack(a.getRGB(x, y));
                int[] q = unpack(b.getRGB(x, y));
                int[] n = op.apply(p, q);
                out.setRGB(x, y, (clamp(n[0]) << 16) | (clamp(n[1]) << 8) | clamp(n[2]));
            }
        }
        return out;
    }

    private static int[] unpack(int rgb) {
        return new int[]{(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF};
    }

    // ---------- Point operations ----------

    public static BufferedImage negative(BufferedImage src) {
        return perPixel(src, (r, g, b) -> new int[]{255 - r, 255 - g, 255 - b});
    }

    public static BufferedImage logTransform(BufferedImage src, double c) {
        return perPixel(src, (r, g, b) -> new int[]{
                (int) (c * Math.log(1 + r)),
                (int) (c * Math.log(1 + g)),
                (int) (c * Math.log(1 + b))
        });
    }

    public static BufferedImage powerLaw(BufferedImage src, double c, double gamma) {
        return perPixel(src, (r, g, b) -> new int[]{
                (int) (c * Math.pow(r / 255.0, gamma) * 255),
                (int) (c * Math.pow(g / 255.0, gamma) * 255),
                (int) (c * Math.pow(b / 255.0, gamma) * 255)
        });
    }

    public static BufferedImage piecewiseLinear(BufferedImage src, int r1, int s1, int r2, int s2) {
        return perPixel(src, (r, g, b) -> new int[]{
                piecewise(r, r1, s1, r2, s2),
                piecewise(g, r1, s1, r2, s2),
                piecewise(b, r1, s1, r2, s2)
        });
    }

    private static int piecewise(int v, int r1, int s1, int r2, int s2) {
        if (v < r1) return (int) ((double) s1 / r1 * v);
        if (v <= r2) return (int) ((double) (s2 - s1) / (r2 - r1) * (v - r1) + s1);
        return (int) ((double) (255 - s2) / (255 - r2) * (v - r2) + s2);
    }

    public static BufferedImage brightness(BufferedImage src, int delta) {
        return perPixel(src, (r, g, b) -> new int[]{r + delta, g + delta, b + delta});
    }

    public static BufferedImage contrast(BufferedImage src, double factor) {
        return perPixel(src, (r, g, b) -> new int[]{
                (int) ((r - 128) * factor + 128),
                (int) ((g - 128) * factor + 128),
                (int) ((b - 128) * factor + 128)
        });
    }

    public static BufferedImage bitPlane(BufferedImage src, int bit) {
        return perPixel(src, (r, g, b) -> {
            int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);
            int v = ((gray >> bit) & 1) * 255;
            return new int[]{v, v, v};
        });
    }

    public static BufferedImage grayscale(BufferedImage src) {
        return perPixel(src, (r, g, b) -> {
            int v = (int) (0.299 * r + 0.587 * g + 0.114 * b);
            return new int[]{v, v, v};
        });
    }

    // ---------- Color models ----------

    public static BufferedImage toCMY(BufferedImage src) {
        return perPixel(src, (r, g, b) -> new int[]{255 - r, 255 - g, 255 - b});
    }

    public static BufferedImage toCMYK(BufferedImage src) {
        return perPixel(src, (r, g, b) -> {
            double rf = r / 255.0, gf = g / 255.0, bf = b / 255.0;
            double k = 1 - Math.max(rf, Math.max(gf, bf));
            double c = (1 - rf - k) / (1 - k + 1e-6);
            double m = (1 - gf - k) / (1 - k + 1e-6);
            double y = (1 - bf - k) / (1 - k + 1e-6);
            return new int[]{(int) ((1 - c) * 255), (int) ((1 - m) * 255), (int) ((1 - y) * 255)};
        });
    }

    public static BufferedImage toHSI(BufferedImage src) {
        return perPixel(src, (r, g, b) -> {
            double rf = r / 255.0, gf = g / 255.0, bf = b / 255.0;
            double num = 0.5 * ((rf - gf) + (rf - bf));
            double den = Math.sqrt((rf - gf) * (rf - gf) + (rf - bf) * (gf - bf)) + 1e-6;
            double theta = Math.acos(Math.max(-1, Math.min(1, num / den)));
            double h = bf <= gf ? theta : 2 * Math.PI - theta;
            double min = Math.min(rf, Math.min(gf, bf));
            double s = 1 - 3 * min / (rf + gf + bf + 1e-6);
            double i = (rf + gf + bf) / 3;
            return new int[]{(int) (h / (2 * Math.PI) * 255), (int) (s * 255), (int) (i * 255)};
        });
    }

    public static BufferedImage toYUV(BufferedImage src) {
        return perPixel(src, (r, g, b) -> {
            double rf = r / 255.0, gf = g / 255.0, bf = b / 255.0;
            double y = 0.299 * rf + 0.587 * gf + 0.114 * bf;
            double u = -0.14713 * rf - 0.28886 * gf + 0.436 * bf;
            double v = 0.615 * rf - 0.51499 * gf - 0.10001 * bf;
            return new int[]{
                    (int) (y * 255),
                    (int) ((u + 0.436) / (2 * 0.436) * 255),
                    (int) ((v + 0.615) / (2 * 0.615) * 255)
            };
        });
    }

    public static BufferedImage toYCbCr(BufferedImage src) {
        return perPixel(src, (r, g, b) -> {
            double rf = r / 255.0, gf = g / 255.0, bf = b / 255.0;
            int y  = (int) (65.481 * rf + 128.553 * gf + 24.966 * bf);
            int cb = (int) (-37.797 * rf - 74.203 * gf + 112.0 * bf + 128);
            int cr = (int) (112.0 * rf - 93.786 * gf - 18.214 * bf + 128);
            return new int[]{y, cb, cr};
        });
    }

    // ---------- Channel extraction ----------

    public static BufferedImage extractChannel(BufferedImage src, Channel ch) {
        return perPixel(src, (r, g, b) -> {
            int v = switch (ch) {
                case RED -> r;
                case GREEN -> g;
                case BLUE -> b;
                case GRAY -> (int) (0.299 * r + 0.587 * g + 0.114 * b);
                case HUE -> channelFromHSI(r, g, b, 0);
                case SATURATION -> channelFromHSI(r, g, b, 1);
                case INTENSITY -> channelFromHSI(r, g, b, 2);
                case YUV_Y -> (int) (0.299 * r + 0.587 * g + 0.114 * b);
                case YUV_U -> clamp((int) ((-0.14713 * r - 0.28886 * g + 0.436 * b) + 128));
                case YUV_V -> clamp((int) ((0.615 * r - 0.51499 * g - 0.10001 * b) + 128));
                case YCBCR_Y -> clamp((int) (0.257 * r + 0.504 * g + 0.098 * b + 16));
                case YCBCR_CB -> clamp((int) (-0.148 * r - 0.291 * g + 0.439 * b + 128));
                case YCBCR_CR -> clamp((int) (0.439 * r - 0.368 * g - 0.071 * b + 128));
            };
            return new int[]{v, v, v};
        });
    }

    private static int channelFromHSI(int r, int g, int b, int which) {
        double rf = r / 255.0, gf = g / 255.0, bf = b / 255.0;
        double num = 0.5 * ((rf - gf) + (rf - bf));
        double den = Math.sqrt((rf - gf) * (rf - gf) + (rf - bf) * (gf - bf)) + 1e-6;
        double theta = Math.acos(Math.max(-1, Math.min(1, num / den)));
        double h = bf <= gf ? theta : 2 * Math.PI - theta;
        double min = Math.min(rf, Math.min(gf, bf));
        double s = 1 - 3 * min / (rf + gf + bf + 1e-6);
        double i = (rf + gf + bf) / 3;
        return switch (which) {
            case 0 -> (int) (h / (2 * Math.PI) * 255);
            case 1 -> (int) (s * 255);
            default -> (int) (i * 255);
        };
    }

    // ---------- Filters ----------

    public static BufferedImage meanFilter(BufferedImage src, int k) {
        return convolve(src, uniformKernel(k));
    }

    public static BufferedImage gaussianFilter(BufferedImage src) {
        float[][] k = {
                {1/16f, 2/16f, 1/16f},
                {2/16f, 4/16f, 2/16f},
                {1/16f, 2/16f, 1/16f}
        };
        return convolve(src, k);
    }

    public static BufferedImage laplacian(BufferedImage src) {
        float[][] k = {
                {0, -1, 0},
                {-1, 4, -1},
                {0, -1, 0}
        };
        return convolve(src, k);
    }

    public static BufferedImage highBoost(BufferedImage src, double A) {
        BufferedImage blurred = gaussianFilter(src);
        return perPixel2(src, blurred, (o, bl) -> new int[]{
                (int) (A * o[0] - bl[0]),
                (int) (A * o[1] - bl[1]),
                (int) (A * o[2] - bl[2])
        });
    }

    public static BufferedImage medianFilter(BufferedImage src, int k) {
        int w = src.getWidth(), h = src.getHeight();
        int pad = k / 2;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] rw = new int[k * k], gw = new int[k * k], bw = new int[k * k];
        for (int y = pad; y < h - pad; y++) {
            for (int x = pad; x < w - pad; x++) {
                int idx = 0;
                for (int ky = -pad; ky <= pad; ky++) {
                    for (int kx = -pad; kx <= pad; kx++) {
                        int rgb = src.getRGB(x + kx, y + ky);
                        rw[idx] = (rgb >> 16) & 0xFF;
                        gw[idx] = (rgb >> 8) & 0xFF;
                        bw[idx] = rgb & 0xFF;
                        idx++;
                    }
                }
                java.util.Arrays.sort(rw);
                java.util.Arrays.sort(gw);
                java.util.Arrays.sort(bw);
                int mid = rw.length / 2;
                out.setRGB(x, y, (rw[mid] << 16) | (gw[mid] << 8) | bw[mid]);
            }
        }
        return out;
    }

    private static float[][] uniformKernel(int k) {
        float[][] kk = new float[k][k];
        float v = 1f / (k * k);
        for (int i = 0; i < k; i++)
            for (int j = 0; j < k; j++)
                kk[i][j] = v;
        return kk;
    }

    private static BufferedImage convolve(BufferedImage src, float[][] kernel) {
        int w = src.getWidth(), h = src.getHeight();
        int pad = kernel.length / 2;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = pad; y < h - pad; y++) {
            for (int x = pad; x < w - pad; x++) {
                float sr = 0, sg = 0, sb = 0;
                for (int ky = -pad; ky <= pad; ky++) {
                    for (int kx = -pad; kx <= pad; kx++) {
                        int rgb = src.getRGB(x + kx, y + ky);
                        float kv = kernel[ky + pad][kx + pad];
                        sr += ((rgb >> 16) & 0xFF) * kv;
                        sg += ((rgb >> 8) & 0xFF) * kv;
                        sb += (rgb & 0xFF) * kv;
                    }
                }
                out.setRGB(x, y,
                        (clamp(Math.round(sr)) << 16) |
                                (clamp(Math.round(sg)) << 8) |
                                clamp(Math.round(sb)));
            }
        }
        return out;
    }

    // ---------- Arithmetic / logic ----------

    public static BufferedImage subtract(BufferedImage a, BufferedImage b) {
        return perPixel2(a, b, (p, q) -> new int[]{
                Math.max(0, p[0] - q[0]),
                Math.max(0, p[1] - q[1]),
                Math.max(0, p[2] - q[2])
        });
    }

    public static BufferedImage add(BufferedImage a, BufferedImage b) {
        return perPixel2(a, b, (p, q) -> new int[]{
                Math.min(255, p[0] + q[0]),
                Math.min(255, p[1] + q[1]),
                Math.min(255, p[2] + q[2])
        });
    }

    public static BufferedImage logical(BufferedImage a, BufferedImage b, LogicalOp op) {
        return perPixel2(a, b, (p, q) -> switch (op) {
            case AND -> new int[]{p[0] & q[0], p[1] & q[1], p[2] & q[2]};
            case OR  -> new int[]{p[0] | q[0], p[1] | q[1], p[2] | q[2]};
            case XOR -> new int[]{p[0] ^ q[0], p[1] ^ q[1], p[2] ^ q[2]};
        });
    }

    // ---------- Histogram ----------

    public static int[] grayscaleHistogram(BufferedImage src) {
        int[] hist = new int[256];
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int rgb = src.getRGB(x, y);
                int gray = (int) (0.299 * ((rgb >> 16) & 0xFF)
                        + 0.587 * ((rgb >> 8) & 0xFF)
                        + 0.114 * (rgb & 0xFF));
                hist[clamp(gray)]++;
            }
        }
        return hist;
    }

    public static BufferedImage equalize(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        int[] hist = grayscaleHistogram(src);
        int[] cdf = new int[256];
        cdf[0] = hist[0];
        for (int i = 1; i < 256; i++) cdf[i] = cdf[i - 1] + hist[i];
        int total = w * h;
        int cdfMin = 0;
        for (int i = 0; i < 256; i++) if (cdf[i] > 0) { cdfMin = cdf[i]; break; }
        int[] map = new int[256];
        for (int i = 0; i < 256; i++)
            map[i] = clamp(Math.round((float) (cdf[i] - cdfMin) / (total - cdfMin) * 255));

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int gray = (int) (0.299 * ((rgb >> 16) & 0xFF)
                        + 0.587 * ((rgb >> 8) & 0xFF)
                        + 0.114 * (rgb & 0xFF));
                int v = map[clamp(gray)];
                out.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        return out;
    }

    // ---------- Morphology ----------

    public static BufferedImage binarize(BufferedImage src, int threshold) {
        return perPixel(src, (r, g, b) -> {
            int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);
            int v = gray < threshold ? 0 : 255;
            return new int[]{v, v, v};
        });
    }

    public static BufferedImage binaryErosion(BufferedImage src)     { return morph(src, true, true); }
    public static BufferedImage binaryDilation(BufferedImage src)    { return morph(src, true, false); }
    public static BufferedImage grayscaleErosion(BufferedImage src)  { return morph(src, false, true); }
    public static BufferedImage grayscaleDilation(BufferedImage src) { return morph(src, false, false); }

    public static BufferedImage binaryOpening(BufferedImage src)     { return binaryDilation(binaryErosion(src)); }
    public static BufferedImage binaryClosing(BufferedImage src)     { return binaryErosion(binaryDilation(src)); }
    public static BufferedImage grayscaleOpening(BufferedImage src)  { return grayscaleDilation(grayscaleErosion(src)); }
    public static BufferedImage grayscaleClosing(BufferedImage src)  { return grayscaleErosion(grayscaleDilation(src)); }

    private static BufferedImage morph(BufferedImage src, boolean binary, boolean erosion) {
        BufferedImage base = binary ? binarize(src, 128) : src;
        int w = base.getWidth(), h = base.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int r0 = 255, g0 = 255, b0 = 255;
                int r1 = 0, g1 = 0, b1 = 0;
                for (int j = -1; j <= 1; j++) {
                    for (int i = -1; i <= 1; i++) {
                        int rgb = base.getRGB(x + i, y + j);
                        int r = (rgb >> 16) & 0xFF;
                        int g = (rgb >> 8) & 0xFF;
                        int b = rgb & 0xFF;
                        r0 = Math.min(r0, r); g0 = Math.min(g0, g); b0 = Math.min(b0, b);
                        r1 = Math.max(r1, r); g1 = Math.max(g1, g); b1 = Math.max(b1, b);
                    }
                }
                int r = erosion ? r0 : r1;
                int g = erosion ? g0 : g1;
                int b = erosion ? b0 : b1;
                out.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    // ============================================================
    //                NEW — GEOMETRIC TRANSFORMS
    // ============================================================

    /** Rotate 90° clockwise (right). */
    public static BufferedImage rotate90Right(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(h, w, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                out.setRGB(h - 1 - y, x, src.getRGB(x, y));
        return out;
    }

    /** Rotate 90° counter-clockwise (left). */
    public static BufferedImage rotate90Left(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(h, w, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                out.setRGB(y, w - 1 - x, src.getRGB(x, y));
        return out;
    }

    /** Rotate 180° (upside down). */
    public static BufferedImage rotate180(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                out.setRGB(w - 1 - x, h - 1 - y, src.getRGB(x, y));
        return out;
    }

    /** Mirror left ↔ right (horizontal flip). */
    public static BufferedImage mirrorHorizontal(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                out.setRGB(w - 1 - x, y, src.getRGB(x, y));
        return out;
    }

    /** Mirror top ↔ bottom (vertical flip). */
    public static BufferedImage mirrorVertical(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                out.setRGB(x, h - 1 - y, src.getRGB(x, y));
        return out;
    }

    /** Crop a rectangular region; coordinates are clamped to image bounds. */
    public static BufferedImage crop(BufferedImage src, int x, int y, int cw, int ch) {
        int sw = src.getWidth(), sh = src.getHeight();
        x  = Math.max(0, Math.min(x, sw - 1));
        y  = Math.max(0, Math.min(y, sh - 1));
        cw = Math.max(1, Math.min(cw, sw - x));
        ch = Math.max(1, Math.min(ch, sh - y));
        BufferedImage out = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < ch; j++)
            for (int i = 0; i < cw; i++)
                out.setRGB(i, j, src.getRGB(x + i, y + j));
        return out;
    }
}