package com.imagelab.util;

import javafx.scene.control.Alert;
import javafx.scene.control.TextInputDialog;

import java.util.Optional;

public final class Dialogs {
    private Dialogs() {}

    public static void info(String msg)  { show(Alert.AlertType.INFORMATION, "Information", msg); }
    public static void warn(String msg)  { show(Alert.AlertType.WARNING, "Warning", msg); }
    public static void error(String msg) { show(Alert.AlertType.ERROR, "Error", msg); }

    private static void show(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    public static Optional<String> prompt(String title, String message, String defaultValue) {
        TextInputDialog d = new TextInputDialog(defaultValue);
        d.setTitle(title);
        d.setHeaderText(null);
        d.setContentText(message);
        return d.showAndWait();
    }
}