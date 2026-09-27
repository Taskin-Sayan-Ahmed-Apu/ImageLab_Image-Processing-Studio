package com.imagelab.util;

import javafx.embed.swing.SwingFXUtils;

import javafx.scene.image.Image;

import java.awt.image.BufferedImage;

public final class ImageConverter {
    private ImageConverter() {}

    public static Image toFX(BufferedImage img) {
        return SwingFXUtils.toFXImage(img, null);
    }
}
