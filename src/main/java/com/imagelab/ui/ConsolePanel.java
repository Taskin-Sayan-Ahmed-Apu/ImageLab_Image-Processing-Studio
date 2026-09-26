package com.imagelab.ui;

import com.imagelab.util.Log;
import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Lightweight, batched console panel.
 * Log calls enqueue messages; an AnimationTimer flushes them to the UI
 * at most once per frame so the FX thread never gets flooded.
 */
public class ConsolePanel extends BorderPane {

    private static final int MAX_ROWS = 200;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final VBox messages = new VBox(2);
    private final ScrollPane scroll = new ScrollPane(messages);
    private final Label spinner = new Label("●");
    private final ProgressBar loadingBar = new ProgressBar(0);
    private final Label loadingLabel = new Label("");

    // Batch queue + flush timer
    private final ConcurrentLinkedQueue<Runnable> pending = new ConcurrentLinkedQueue<>();
    private long lastFlush = 0;
    private static final long FLUSH_INTERVAL_NS = 33_000_000L; // ~30 fps

    // Track whether the user is glued to the bottom
    private boolean userAtBottom = true;

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

        // Smart auto-scroll: only stick to bottom if user is already there
        scroll.vvalueProperty().addListener((o, ov, nv) -> {
            userAtBottom = nv.doubleValue() >= 0.98;
        });

        // ---------- Slow, cheap spinner ----------
        // Rotating just the label's rotateProperty — no CSS animation
        Timeline spinnerTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,        new KeyValue(spinner.rotateProperty(), 0)),
                new KeyFrame(Duration.seconds(2.5), new KeyValue(spinner.rotateProperty(), 360))
        );
        spinnerTimeline.setCycleCount(Timeline.INDEFINITE);
        spinnerTimeline.play();

        // ---------- Batch flush loop ----------
        new AnimationTimer() {
            @Override public void handle(long now) {
                if (now - lastFlush < FLUSH_INTERVAL_NS) return;
                lastFlush = now;
                flushBatch();
            }
        }.start();
    }

    // ============================================================
    //                     BATCH FLUSH
    // ============================================================
    private void flushBatch() {
        if (pending.isEmpty()) return;

        int budget = 25;             // max rows added per frame
        boolean added = false;

        while (budget-- > 0) {
            Runnable r = pending.poll();
            if (r == null) break;
            r.run();                 // each runnable adds exactly 1 row
            added = true;
        }

        // Trim old rows if needed
        int size = messages.getChildren().size();
        if (size > MAX_ROWS) {
            messages.getChildren().remove(0, size - MAX_ROWS);
        }

        if (added && userAtBottom) {
            // One deferred scroll — not a listener storm
            Platform.runLater(() -> scroll.setVvalue(1.0));
        }
    }

    // ============================================================
    //                     LOADING STATE
    // ============================================================
    public void showLoading(String label) {
        Runnable r = () -> {
            loadingLabel.setText(label);
            loadingBar.setVisible(true);
            loadingBar.setManaged(true);
            loadingBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        };
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }

    public void hideLoading() {
        Runnable r = () -> {
            loadingBar.setVisible(false);
            loadingBar.setManaged(false);
            loadingLabel.setText("");
        };
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }

    // ============================================================
    //                     APPEND
    // ============================================================
    public void append(Log.Level level, String msg) {
        // Cheap: build a Runnable that will construct + add the row on FX thread
        pending.offer(() -> messages.getChildren().add(buildRow(level, msg)));
    }

    private HBox buildRow(Log.Level level, String msg) {
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
        text.setWrapText(false);          // wrap=false is much cheaper
        text.setMaxWidth(Double.MAX_VALUE);

        row.getChildren().addAll(icon, time, tag, text);
        return row;
    }

    // ============================================================
    //                     BANNER
    // ============================================================
    public void banner(String appName, String version) {
        Runnable r = () -> {
            VBox card = new VBox(2);
            card.getStyleClass().add("console-banner");
            card.setPadding(new Insets(10, 4, 12, 4));

            Label big = new Label("  ◆  " + appName + "  ◆");
            big.getStyleClass().add("console-banner-title");

            Label sub = new Label("  Image Processing Studio");
            sub.getStyleClass().add("console-banner-sub");

            Label meta = new Label("  Version " + version
                    + "   ·   JavaFX + SQLite + Gson + REST API");
            meta.getStyleClass().add("console-banner-meta");

            card.getChildren().addAll(big, sub, meta);
            messages.getChildren().add(card);

            if (userAtBottom) Platform.runLater(() -> scroll.setVvalue(1.0));
        };
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);

        // Short loading animation + greeting
        showLoading("Booting…");
        Timeline boot = new Timeline(
                new KeyFrame(Duration.ZERO,         new KeyValue(loadingBar.progressProperty(), 0.0)),
                new KeyFrame(Duration.millis(500),  new KeyValue(loadingBar.progressProperty(), 0.5)),
                new KeyFrame(Duration.millis(1000), new KeyValue(loadingBar.progressProperty(), 1.0))
        );
        boot.setOnFinished(e -> {
            hideLoading();
            append(Log.Level.SUCCESS, "Welcome to " + appName + "! Ready to process images.");
            append(Log.Level.INFO,    "Tip: use View → Zoom In/Out, or ⌘/Ctrl + scroll to zoom.");
        });
        boot.play();
    }

    public void clear() {
        Runnable r = () -> {
            pending.clear();
            messages.getChildren().clear();
        };
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
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