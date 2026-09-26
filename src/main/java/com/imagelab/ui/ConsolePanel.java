package com.imagelab.ui;

import com.imagelab.util.Log;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * In-app, fully colourful console panel — pure BLACK background,
 * animated spinner in the header, and a live loading progress bar
 * that can be shown/hidden while the app is processing.
 */
public class ConsolePanel extends BorderPane {

    private static final int MAX_ROWS = 500;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final VBox messages = new VBox(2);
    private final ScrollPane scroll = new ScrollPane(messages);
    private final Label spinner = new Label("●");
    private final ProgressBar loadingBar = new ProgressBar(0);
    private final Label loadingLabel = new Label("Ready");

    private Timeline spinnerTimeline;

    public ConsolePanel() {
        getStyleClass().add("console-panel");

        // ---------- Header ----------
        HBox header = new HBox(10);
        header.getStyleClass().add("console-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(6, 12, 6, 12));

        Label title = new Label("  Console");
        title.getStyleClass().add("console-title");

        spinner.getStyleClass().add("console-spinner");

        loadingBar.setPrefWidth(160);
        loadingBar.getStyleClass().add("console-loading-bar");
        loadingBar.setVisible(false);
        loadingBar.setManaged(false);

        loadingLabel.getStyleClass().add("console-loading-label");
        loadingLabel.setText("");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button clear = new Button("Clear");
        clear.getStyleClass().add("console-clear-btn");
        clear.setOnAction(e -> clear());

        header.getChildren().addAll(title, spinner, loadingLabel, loadingBar, spacer, clear);

        // ---------- Body ----------
        messages.setPadding(new Insets(10, 14, 10, 14));
        messages.getStyleClass().add("console-body");

        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("console-scroll");

        setTop(header);
        setCenter(scroll);

        messages.heightProperty().addListener((o, ov, nv) ->
                Platform.runLater(() -> scroll.setVvalue(1.0)));

        // ---------- Spinner animation ----------
        spinnerTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,        new KeyValue(spinner.rotateProperty(), 0)),
                new KeyFrame(Duration.seconds(1.4), new KeyValue(spinner.rotateProperty(), 360))
        );
        spinnerTimeline.setCycleCount(Animation.INDEFINITE);
        spinnerTimeline.play();
    }

    // ============================================================
    //                     LOADING STATE
    // ============================================================
    /** Show the loading bar + label with an indeterminate animation. */
    public void showLoading(String label) {
        Platform.runLater(() -> {
            loadingLabel.setText(label);
            loadingBar.setVisible(true);
            loadingBar.setManaged(true);
            loadingBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        });
    }

    /** Hide the loading bar. */
    public void hideLoading() {
        Platform.runLater(() -> {
            loadingBar.setVisible(false);
            loadingBar.setManaged(false);
            loadingLabel.setText("");
        });
    }

    // ============================================================
    //                     MESSAGE APPEND
    // ============================================================
    public void append(Log.Level level, String msg) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> append(level, msg));
            return;
        }

        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("console-row");

        Label icon = new Label(iconFor(level));
        icon.getStyleClass().addAll("console-icon", "console-icon-" + level.name().toLowerCase());

        Label time = new Label(LocalTime.now().format(TS));
        time.getStyleClass().add("console-time");

        Label tag = new Label(level.name());
        tag.getStyleClass().addAll("console-tag", "console-tag-" + level.name().toLowerCase());

        Label text = new Label(msg);
        text.getStyleClass().addAll("console-msg", "console-msg-" + level.name().toLowerCase());
        text.setWrapText(true);

        row.getChildren().addAll(icon, time, tag, text);

        if (messages.getChildren().size() > MAX_ROWS) {
            messages.getChildren().remove(0, messages.getChildren().size() - MAX_ROWS);
        }
        messages.getChildren().add(row);

        // Fade + slide-in
        row.setOpacity(0);
        row.setTranslateX(-18);
        FadeTransition ft = new FadeTransition(Duration.millis(220), row);
        ft.setToValue(1);
        TranslateTransition tt = new TranslateTransition(Duration.millis(220), row);
        tt.setToX(0);
        new ParallelTransition(ft, tt).play();
    }

    // ============================================================
    //                     ANIMATED BANNER
    // ============================================================
    public void banner(String appName, String version) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> banner(appName, version));
            return;
        }

        VBox card = new VBox(2);
        card.getStyleClass().add("console-banner");
        card.setPadding(new Insets(10, 4, 12, 4));

        Label bigTitle = new Label("  ◆  " + appName + "  ◆");
        bigTitle.getStyleClass().add("console-banner-title");

        Label sub = new Label("  Image Processing Studio");
        sub.getStyleClass().add("console-banner-sub");

        Label meta = new Label("  Version " + version
                + "   ·   JavaFX + SQLite + Gson + REST API");
        meta.getStyleClass().add("console-banner-meta");

        card.getChildren().addAll(bigTitle, sub, meta);
        messages.getChildren().add(card);

        card.setOpacity(0);
        card.setTranslateY(-12);
        FadeTransition ft = new FadeTransition(Duration.millis(700), card);
        ft.setToValue(1);
        TranslateTransition tt = new TranslateTransition(Duration.millis(700), card);
        tt.setToY(0);
        tt.setInterpolator(Interpolator.EASE_OUT);
        new ParallelTransition(ft, tt).play();

        // Simulated "loading" progress for the startup greeting
        showLoading("Booting…");
        Timeline boot = new Timeline(
                new KeyFrame(Duration.ZERO,             new KeyValue(loadingBar.progressProperty(), 0.0)),
                new KeyFrame(Duration.millis(600),      new KeyValue(loadingBar.progressProperty(), 0.4)),
                new KeyFrame(Duration.millis(1200),     new KeyValue(loadingBar.progressProperty(), 0.75)),
                new KeyFrame(Duration.millis(1700),     new KeyValue(loadingBar.progressProperty(), 1.0))
        );
        boot.setOnFinished(e -> {
            hideLoading();
            append(Log.Level.SUCCESS, "Welcome to " + appName + "! Ready to process images.");
            append(Log.Level.INFO,    "Tip: use View → Zoom In/Out, or ⌘/Ctrl + scroll to zoom.");
        });
        boot.play();
    }

    public void clear() {
        messages.getChildren().clear();
    }

    private static String iconFor(Log.Level level) {
        return switch (level) {
            case INFO    -> "ℹ";
            case SUCCESS -> "✔";
            case WARN    -> "⚠";
            case ERROR   -> "✖";
            case DEBUG   -> "·";
            case ITEM    -> "▸";
            case ZOOM    -> "🔍";
            case OP      -> "⟳";
        };
    }
}