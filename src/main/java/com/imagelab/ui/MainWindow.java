package com.imagelab.ui;

import com.imagelab.core.ImageOps;
import com.imagelab.core.PPMHandler;
import com.imagelab.core.YUVHandler;
import com.imagelab.data.*;
import com.imagelab.util.Dialogs;
import com.imagelab.util.ImageConverter;
import com.imagelab.util.Log;
import javafx.animation.FadeTransition;
import javafx.animation.RotateTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Function;

public class MainWindow {

    private static final double ZOOM_STEP = 1.2;
    private static final double MIN_ZOOM  = 0.10;
    private static final double MAX_ZOOM  = 8.0;

    private final BorderPane root = new BorderPane();
    private final ImageView imageView = new ImageView();
    private final Label statusLabel     = new Label("Ready");
    private final Label resolutionLabel = new Label("Resolution: -");
    private final Label zoomLabel       = new Label("Zoom: 100%");
    private final Label mouseLabel      = new Label("Mouse: (-, -)");

    private final ConsolePanel console = new ConsolePanel();

    // Loading overlay
    private StackPane imageHolder;
    private StackPane loadingOverlay;
    private Arc loadingArc;

    private final Deque<BufferedImage> undoStack = new ArrayDeque<>();
    private BufferedImage original;
    private BufferedImage current;
    private String currentSource = "(none)";

    private double zoomFactor = 1.0;
    private ScrollPane scrollPane;

    private final ExecutorService executor = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
    private final DatabaseManager db = new DatabaseManager();
    private final AppConfig config = ConfigLoader.get();

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public MainWindow(Stage stage) {
        Log.setUiSink(console::append);
        root.setTop(buildMenuBar());
        root.setCenter(buildMainArea());
        root.setBottom(buildStatusBar());
        Log.success(config.getAppName() + " ready — try the View menu for zoom");
    }

    public BorderPane getRoot() { return root; }
    public ConsolePanel getConsole() { return console; }

    // ============================================================
    //                    MAIN AREA
    // ============================================================
    private SplitPane buildMainArea() {
        SplitPane split = new SplitPane();
        split.setOrientation(Orientation.VERTICAL);
        split.getItems().addAll(buildCenter(), console);
        split.setDividerPositions(0.68);
        SplitPane.setResizableWithParent(console, Boolean.TRUE);
        return split;
    }

    private MenuBar buildMenuBar() {
        MenuBar bar = new MenuBar();
        bar.getMenus().addAll(
                fileMenu(), editMenu(), viewMenu(), intensityMenu(), colorMenu(),
                channelMenu(), filterMenu(), transformMenu(), arithmeticMenu(),
                histogramMenu(), morphologyMenu(), dataMenu(), apiMenu());
        return bar;
    }

    // ---------- Transform (NEW) ----------
    private Menu transformMenu() {
        Menu m = new Menu("Transform");

        MenuItem rotCW = item("Rotate 90° Clockwise", () ->
                process("Rotate 90° CW", ImageOps::rotate90Right));
        rotCW.setAccelerator(KeyCombination.keyCombination("Shortcut+R"));

        MenuItem rotCCW = item("Rotate 90° Counter-Clockwise", () ->
                process("Rotate 90° CCW", ImageOps::rotate90Left));
        rotCCW.setAccelerator(KeyCombination.keyCombination("Shift+Shortcut+R"));

        MenuItem rot180 = item("Rotate 180°", () ->
                process("Rotate 180°", ImageOps::rotate180));

        MenuItem mirH = item("Mirror Horizontal (flip left ↔ right)", () ->
                process("Mirror Horizontal", ImageOps::mirrorHorizontal));
        mirH.setAccelerator(KeyCombination.keyCombination("Shortcut+M"));

        MenuItem mirV = item("Mirror Vertical (flip top ↔ bottom)", () ->
                process("Mirror Vertical", ImageOps::mirrorVertical));
        mirV.setAccelerator(KeyCombination.keyCombination("Shift+Shortcut+M"));

        MenuItem crop = item("Crop…", this::askCrop);
        crop.setAccelerator(KeyCombination.keyCombination("Shortcut+K"));

        m.getItems().addAll(
                rotCW, rotCCW, rot180,
                new SeparatorMenuItem(),
                mirH, mirV,
                new SeparatorMenuItem(),
                crop);
        return m;
    }

    // ---------- File ----------
    private Menu fileMenu() {
        Menu m = new Menu("File");
        m.getItems().addAll(
                item("Open Image...", this::openImage),
                item("Open PPM...", this::openPPM),
                item("Open YUV...", this::openYUV),
                new SeparatorMenuItem(),
                item("Save As PNG...", () -> saveAs("png")),
                item("Save As JPG...", () -> saveAs("jpg")),
                item("Export as PPM...", this::savePPM),
                item("Export as YUV...", this::saveYUV),
                new SeparatorMenuItem(),
                item("Exit", () -> { Log.warn("Shutting down…"); executor.shutdownNow(); Platform.exit(); }));
        return m;
    }

    private Menu editMenu() {
        Menu m = new Menu("Edit");
        m.getItems().addAll(
                item("Undo", this::undo),
                item("Reset to Original", this::reset),
                item("Image Info...", this::showInfo));
        return m;
    }

    private Menu viewMenu() {
        Menu m = new Menu("View");
        MenuItem zoomIn = item("Zoom In", this::zoomIn);
        zoomIn.setAccelerator(KeyCombination.keyCombination("Shortcut+Equals"));
        MenuItem zoomOut = item("Zoom Out", this::zoomOut);
        zoomOut.setAccelerator(KeyCombination.keyCombination("Shortcut+Minus"));
        MenuItem resetZoom = item("Reset Zoom  (100%)", this::resetZoom);
        resetZoom.setAccelerator(KeyCombination.keyCombination("Shortcut+0"));
        MenuItem fit = item("Fit to Window", this::fitToWindow);
        fit.setAccelerator(KeyCombination.keyCombination("Shortcut+9"));
        m.getItems().addAll(zoomIn, zoomOut, new SeparatorMenuItem(), resetZoom, fit);
        return m;
    }

    private Menu intensityMenu() {
        Menu m = new Menu("Intensity");
        AppConfig.Defaults d = config.getDefaults();
        m.getItems().addAll(
                item("Negative", () -> process("Negative", ImageOps::negative)),
                item("Log Transform...", () -> {
                    Optional<String> s = Dialogs.prompt("Log Transform", "Enter c:", String.valueOf(d.getLogC()));
                    s.ifPresent(v -> process("Log Transform",
                            img -> ImageOps.logTransform(img, Double.parseDouble(v))));
                }),
                item("Power-Law...", () -> {
                    Optional<String> s = Dialogs.prompt("Power-Law", "Enter gamma:", String.valueOf(d.getGamma()));
                    s.ifPresent(v -> process("Power-Law",
                            img -> ImageOps.powerLaw(img, 1.0, Double.parseDouble(v))));
                }),
                item("Piecewise-Linear...", this::askPiecewise),
                item("Brightness...", () -> {
                    Optional<String> s = Dialogs.prompt("Brightness", "Delta (-255..255):",
                            String.valueOf(d.getBrightnessDelta()));
                    s.ifPresent(v -> process("Brightness",
                            img -> ImageOps.brightness(img, Integer.parseInt(v))));
                }),
                item("Contrast...", () -> {
                    Optional<String> s = Dialogs.prompt("Contrast", "Factor (e.g. 1.5):",
                            String.valueOf(d.getContrastFactor()));
                    s.ifPresent(v -> process("Contrast",
                            img -> ImageOps.contrast(img, Double.parseDouble(v))));
                }),
                item("Bit-Plane Slicing...", () -> {
                    Optional<String> s = Dialogs.prompt("Bit-Plane", "Bit (0-7):",
                            String.valueOf(d.getBitPlane()));
                    s.ifPresent(v -> process("Bit-Plane " + v,
                            img -> ImageOps.bitPlane(img, Integer.parseInt(v))));
                }));
        return m;
    }

    private Menu colorMenu() {
        Menu m = new Menu("Color Models");
        m.getItems().addAll(
                item("To CMY",   () -> process("CMY",   ImageOps::toCMY)),
                item("To CMYK",  () -> process("CMYK",  ImageOps::toCMYK)),
                item("To HSI",   () -> process("HSI",   ImageOps::toHSI)),
                item("To YUV",   () -> process("YUV",   ImageOps::toYUV)),
                item("To YCbCr", () -> process("YCbCr", ImageOps::toYCbCr)));
        return m;
    }

    private Menu channelMenu() {
        Menu m = new Menu("Channels");
        for (ImageOps.Channel c : ImageOps.Channel.values()) {
            m.getItems().add(item(prettyName(c.name()),
                    () -> process("Channel " + prettyName(c.name()),
                            img -> ImageOps.extractChannel(img, c))));
        }
        return m;
    }

    private Menu filterMenu() {
        Menu m = new Menu("Filters");
        AppConfig.Defaults d = config.getDefaults();
        m.getItems().addAll(
                item("Mean...", () -> {
                    Optional<String> s = Dialogs.prompt("Mean Filter", "Kernel size (odd):",
                            String.valueOf(d.getMeanKernel()));
                    s.ifPresent(v -> process("Mean " + v,
                            img -> ImageOps.meanFilter(img, Integer.parseInt(v))));
                }),
                item("Gaussian", () -> process("Gaussian", ImageOps::gaussianFilter)),
                item("Median...", () -> {
                    Optional<String> s = Dialogs.prompt("Median Filter", "Kernel size (odd):",
                            String.valueOf(d.getMedianKernel()));
                    s.ifPresent(v -> process("Median " + v,
                            img -> ImageOps.medianFilter(img, Integer.parseInt(v))));
                }),
                item("Laplacian", () -> process("Laplacian", ImageOps::laplacian)),
                item("High-Boost...", () -> {
                    Optional<String> s = Dialogs.prompt("High-Boost", "A (>=1):",
                            String.valueOf(d.getHighBoostA()));
                    s.ifPresent(v -> process("High-Boost",
                            img -> ImageOps.highBoost(img, Double.parseDouble(v))));
                }));
        return m;
    }

    private Menu arithmeticMenu() {
        Menu m = new Menu("Arithmetic / Logic");
        m.getItems().addAll(
                item("Subtract (choose 2nd image)", () -> secondImageProcess("Subtract", ImageOps::subtract)),
                item("Add (choose 2nd image)",      () -> secondImageProcess("Add",      ImageOps::add)),
                new SeparatorMenuItem(),
                item("AND (choose 2nd image)", () -> secondImageProcess("AND",
                        (a, b) -> ImageOps.logical(a, b, ImageOps.LogicalOp.AND))),
                item("OR (choose 2nd image)",  () -> secondImageProcess("OR",
                        (a, b) -> ImageOps.logical(a, b, ImageOps.LogicalOp.OR))),
                item("XOR (choose 2nd image)", () -> secondImageProcess("XOR",
                        (a, b) -> ImageOps.logical(a, b, ImageOps.LogicalOp.XOR))));
        return m;
    }

    private Menu histogramMenu() {
        Menu m = new Menu("Histogram");
        m.getItems().addAll(
                item("Show Histogram", this::showHistogram),
                item("Equalize",       () -> process("Equalize", ImageOps::equalize)));
        return m;
    }

    private Menu morphologyMenu() {
        Menu m = new Menu("Morphology");
        m.getItems().addAll(
                item("Binary Erosion",  () -> process("Binary Erosion",  ImageOps::binaryErosion)),
                item("Binary Dilation", () -> process("Binary Dilation", ImageOps::binaryDilation)),
                item("Binary Opening",  () -> process("Binary Opening",  ImageOps::binaryOpening)),
                item("Binary Closing",  () -> process("Binary Closing",  ImageOps::binaryClosing)),
                new SeparatorMenuItem(),
                item("Grayscale Erosion",  () -> process("Grayscale Erosion",  ImageOps::grayscaleErosion)),
                item("Grayscale Dilation", () -> process("Grayscale Dilation", ImageOps::grayscaleDilation)),
                item("Grayscale Opening",  () -> process("Grayscale Opening",  ImageOps::grayscaleOpening)),
                item("Grayscale Closing",  () -> process("Grayscale Closing",  ImageOps::grayscaleClosing)));
        return m;
    }

    private Menu dataMenu() {
        Menu m = new Menu("Data (SQLite/JSON)");
        m.getItems().addAll(
                item("Show History",   this::showHistory),
                item("Clear History",  () -> { db.clearHistory(); Dialogs.info("History cleared."); }),
                new SeparatorMenuItem(),
                item("Export History to JSON...", this::exportJson),
                item("Import History from JSON...", this::importJson),
                new SeparatorMenuItem(),
                item("Show config.json", this::showConfig));
        return m;
    }

    private Menu apiMenu() {
        Menu m = new Menu("API");
        m.getItems().add(
                item("Load Random Dog Image (dog.ceo API)...", this::loadRandomDogFromApi));
        return m;
    }

    private MenuItem item(String text, Runnable action) {
        MenuItem mi = new MenuItem(text);
        mi.setOnAction(e -> action.run());
        return mi;
    }

    private static String prettyName(String enumName) {
        String[] parts = enumName.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts)
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        return sb.toString().trim();
    }

    // ============================================================
    //           CENTER AREA + LOADING OVERLAY
    // ============================================================
    private ScrollPane buildCenter() {
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        imageHolder = new StackPane(imageView);
        imageHolder.setPadding(new Insets(10));
        imageHolder.setStyle("-fx-background-color: #ffffff;");

        // ---------- Loading overlay ----------
        loadingOverlay = new StackPane();
        loadingOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.55);");
        loadingOverlay.setVisible(false);

        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);

        loadingArc = new Arc(0, 0, 30, 30, 0, 260);
        loadingArc.setType(ArcType.OPEN);
        loadingArc.setFill(Color.TRANSPARENT);
        loadingArc.setStroke(Color.web("#00e5ff"));
        loadingArc.setStrokeWidth(5);

        Label msg = new Label("Processing…");
        msg.setStyle("-fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold;");

        box.getChildren().addAll(loadingArc, msg);
        loadingOverlay.getChildren().add(box);

        RotateTransition spin = new RotateTransition(Duration.seconds(1), loadingArc);
        spin.setByAngle(360);
        spin.setCycleCount(RotateTransition.INDEFINITE);
        spin.play();

        // Stack: imageHolder + overlay
        StackPane stack = new StackPane(imageHolder, loadingOverlay);

        scrollPane = new ScrollPane(stack);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(true);

        scrollPane.setOnMouseMoved(e ->
                mouseLabel.setText(String.format("Mouse: (%.0f, %.0f)", e.getX(), e.getY())));

        scrollPane.setOnScroll(e -> {
            if (e.isControlDown() || e.isMetaDown()) {
                if (e.getDeltaY() > 0)      zoomIn();
                else if (e.getDeltaY() < 0) zoomOut();
                e.consume();
            }
        });
        return scrollPane;
    }

    private void showLoading(String msg) {
        loadingOverlay.setVisible(true);
        loadingOverlay.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(180), loadingOverlay);
        ft.setToValue(1);
        ft.play();
        console.showLoading(msg);
    }

    private void hideLoading() {
        FadeTransition ft = new FadeTransition(Duration.millis(180), loadingOverlay);
        ft.setToValue(0);
        ft.setOnFinished(e -> loadingOverlay.setVisible(false));
        ft.play();
        console.hideLoading();
    }

    private HBox buildStatusBar() {
        HBox box = new HBox(20, statusLabel, resolutionLabel, zoomLabel, mouseLabel);
        box.getStyleClass().add("status-bar");
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        return box;
    }

    // ============================================================
    //                        ZOOM
    // ============================================================
    private void zoomIn() {
        if (current == null) return;
        zoomFactor = Math.min(MAX_ZOOM, zoomFactor * ZOOM_STEP);
        applyZoom();
        Log.zoom(zoomFactor * 100);
    }

    private void zoomOut() {
        if (current == null) return;
        zoomFactor = Math.max(MIN_ZOOM, zoomFactor / ZOOM_STEP);
        applyZoom();
        Log.zoom(zoomFactor * 100);
    }

    private void resetZoom() {
        zoomFactor = 1.0;
        applyZoom();
        Log.info("Zoom reset to 100%");
    }

    private void fitToWindow() {
        if (current == null || scrollPane == null) return;
        double vw = scrollPane.getViewportBounds().getWidth() - 40;
        double vh = scrollPane.getViewportBounds().getHeight() - 40;
        if (vw <= 0 || vh <= 0) return;
        double scale = Math.min(vw / current.getWidth(), vh / current.getHeight());
        zoomFactor = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, scale));
        applyZoom();
        Log.info("Fit to window — " + String.format("%.0f%%", zoomFactor * 100));
    }

    private void applyZoom() {
        if (current == null) return;
        imageView.setFitWidth(current.getWidth() * zoomFactor);
        imageView.setFitHeight(current.getHeight() * zoomFactor);
        zoomLabel.setText(String.format("Zoom: %.0f%%", zoomFactor * 100));
    }

    // ============================================================
    //                    FILE OPERATIONS
    // ============================================================
    private void openImage() {
        File f = chooseOpen("Open Image",
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"));
        if (f == null) return;
        try {
            long t = System.currentTimeMillis();
            BufferedImage img = ImageIO.read(f);
            if (img == null) throw new IOException("Unsupported image file");
            loadImage(img, f.getName());
            db.addMetadata(f.getAbsolutePath(), img.getWidth(), img.getHeight(),
                    f.getName().substring(f.getName().lastIndexOf('.') + 1));
            Log.opDone("Open Image", System.currentTimeMillis() - t);
        } catch (IOException e) {
            Log.error("Could not open image: " + e.getMessage());
            Dialogs.error("Could not open image: " + e.getMessage());
        }
    }

    private void openPPM() {
        File f = chooseOpen("Open PPM", new FileChooser.ExtensionFilter("PPM", "*.ppm"));
        if (f == null) return;
        try {
            long t = System.currentTimeMillis();
            loadImage(PPMHandler.read(f), f.getName());
            Log.opDone("Read PPM", System.currentTimeMillis() - t);
        } catch (IOException e) {
            Log.error("Could not read PPM: " + e.getMessage());
            Dialogs.error("Could not read PPM: " + e.getMessage());
        }
    }

    private void openYUV() {
        File f = chooseOpen("Open YUV", new FileChooser.ExtensionFilter("YUV", "*.yuv"));
        if (f == null) return;
        try {
            long t = System.currentTimeMillis();
            loadImage(YUVHandler.read(f), f.getName());
            Log.opDone("Read YUV", System.currentTimeMillis() - t);
        } catch (IOException e) {
            Log.error("Could not read YUV: " + e.getMessage());
            Dialogs.error("Could not read YUV: " + e.getMessage());
        }
    }

    private File chooseOpen(String title, FileChooser.ExtensionFilter filter) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(filter);
        return fc.showOpenDialog(null);
    }

    // ============================================================
    //                    SAVE / EXPORT
    // ============================================================
    private void saveAs(String ext) {
        if (current == null) { Dialogs.warn("No image to save"); return; }
        FileChooser fc = new FileChooser();
        fc.setTitle("Save As " + ext.toUpperCase());
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(ext.toUpperCase(), "*." + ext));
        fc.setInitialFileName("output." + ext);
        File f = fc.showSaveDialog(null);
        if (f == null) return;
        try {
            ImageIO.write(current, ext, f);
            statusLabel.setText("Saved: " + f.getName());
            Log.success("Saved: " + f.getName());
        } catch (IOException e) {
            Log.error("Save failed: " + e.getMessage());
            Dialogs.error("Save failed: " + e.getMessage());
        }
    }

    private void savePPM() {
        if (current == null) { Dialogs.warn("No image"); return; }
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PPM", "*.ppm"));
        fc.setInitialFileName("output.ppm");
        File f = fc.showSaveDialog(null);
        if (f == null) return;
        try { PPMHandler.write(current, f); Log.success("PPM exported: " + f.getName()); }
        catch (IOException e) { Log.error("PPM save failed: " + e.getMessage());
            Dialogs.error("PPM save failed: " + e.getMessage()); }
    }

    private void saveYUV() {
        if (current == null) { Dialogs.warn("No image"); return; }
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("YUV", "*.yuv"));
        fc.setInitialFileName("output.yuv");
        File f = fc.showSaveDialog(null);
        if (f == null) return;
        try { YUVHandler.write(current, f); Log.success("YUV exported: " + f.getName()); }
        catch (IOException e) { Log.error("YUV save failed: " + e.getMessage());
            Dialogs.error("YUV save failed: " + e.getMessage()); }
    }

    // ============================================================
    //                   IMAGE PIPELINE + OVERLAY
    // ============================================================
    private void loadImage(BufferedImage img, String source) {
        original = ImageOps.copy(img);
        current  = ImageOps.copy(img);
        currentSource = source;
        undoStack.clear();
        zoomFactor = 1.0;
        updateView();
        Log.success("Loaded: " + source + "  (" + img.getWidth() + "×" + img.getHeight() + ")");
        statusLabel.setText("Loaded: " + source);
    }

    private void updateView() {
        if (current == null) {
            imageView.setImage(null);
            resolutionLabel.setText("Resolution: -");
            return;
        }
        Image fx = ImageConverter.toFX(current);
        imageView.setImage(fx);
        applyZoom();
        resolutionLabel.setText("Resolution: " + current.getWidth() + " × " + current.getHeight());
    }

    private void process(String name, Function<BufferedImage, BufferedImage> op) {
        if (current == null) { Dialogs.warn("Please load an image first."); return; }
        BufferedImage src = current;
        final long start = System.currentTimeMillis();

        showLoading(name + "…");
        statusLabel.setText("Processing: " + name + "...");
        Log.opStart(name);

        Task<BufferedImage> task = new Task<>() {
            @Override protected BufferedImage call() { return op.apply(src); }
        };
        task.setOnSucceeded(e -> {
            pushUndo(src);
            current = task.getValue();
            updateView();
            long ms = System.currentTimeMillis() - start;
            statusLabel.setText("Done: " + name);
            Log.opDone(name, ms);
            db.addHistory(new HistoryEntry(name, LocalDateTime.now().format(TS), currentSource, ""));
            hideLoading();
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            Log.error(name + " failed: " + ex.getMessage());
            Dialogs.error(name + " failed: " + ex.getMessage());
            statusLabel.setText("Ready");
            hideLoading();
        });
        executor.submit(task);
    }

    private void secondImageProcess(String name,
                                    BiFunction<BufferedImage, BufferedImage, BufferedImage> op) {
        if (current == null) { Dialogs.warn("Please load an image first."); return; }
        FileChooser fc = new FileChooser();
        fc.setTitle("Choose second image");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images",
                "*.png", "*.jpg", "*.jpeg", "*.bmp"));
        File f = fc.showOpenDialog(null);
        if (f == null) return;
        try {
            BufferedImage second = ImageIO.read(f);
            if (second == null) throw new IOException("Unsupported second image");
            process(name, img -> op.apply(img, second));
        } catch (IOException e) {
            Log.error("Could not read second image: " + e.getMessage());
            Dialogs.error("Could not read second image: " + e.getMessage());
        }
    }

    // ---------- Crop ----------
    private void askCrop() {
        if (current == null) { Dialogs.warn("Please load an image first."); return; }
        int W = current.getWidth(), H = current.getHeight();
        try {
            int x = Integer.parseInt(Dialogs.prompt("Crop", "x (0 – " + (W - 1) + "):", "0").orElse("0"));
            int y = Integer.parseInt(Dialogs.prompt("Crop", "y (0 – " + (H - 1) + "):", "0").orElse("0"));
            int w = Integer.parseInt(Dialogs.prompt("Crop", "width (1 – " + (W - x) + "):",
                    String.valueOf(W / 2)).orElse(String.valueOf(W / 2)));
            int h = Integer.parseInt(Dialogs.prompt("Crop", "height (1 – " + (H - y) + "):",
                    String.valueOf(H / 2)).orElse(String.valueOf(H / 2)));
            process("Crop [" + x + "," + y + " " + w + "×" + h + "]",
                    img -> ImageOps.crop(img, x, y, w, h));
        } catch (NumberFormatException e) {
            Dialogs.error("All inputs must be integers.");
        }
    }

    private void askPiecewise() {
        if (current == null) { Dialogs.warn("Please load an image first."); return; }
        AppConfig.Piecewise p = config.getDefaults().getPiecewise();
        try {
            int r1 = Integer.parseInt(Dialogs.prompt("Piecewise", "r1:", String.valueOf(p.getR1())).orElse(String.valueOf(p.getR1())));
            int s1 = Integer.parseInt(Dialogs.prompt("Piecewise", "s1:", String.valueOf(p.getS1())).orElse(String.valueOf(p.getS1())));
            int r2 = Integer.parseInt(Dialogs.prompt("Piecewise", "r2:", String.valueOf(p.getR2())).orElse(String.valueOf(p.getR2())));
            int s2 = Integer.parseInt(Dialogs.prompt("Piecewise", "s2:", String.valueOf(p.getS2())).orElse(String.valueOf(p.getS2())));
            process("Piecewise", img -> ImageOps.piecewiseLinear(img, r1, s1, r2, s2));
        } catch (NumberFormatException e) {
            Dialogs.error("All inputs must be integers.");
        }
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            Log.warn("Undo stack empty");
            Dialogs.info("Nothing to undo.");
            return;
        }
        current = undoStack.pop();
        zoomFactor = 1.0;
        updateView();
        statusLabel.setText("Undo");
        Log.warn("Undo — remaining: " + undoStack.size());
    }

    private void reset() {
        if (original == null) return;
        undoStack.clear();
        current = ImageOps.copy(original);
        zoomFactor = 1.0;
        updateView();
        statusLabel.setText("Reset to original");
        Log.warn("Reset to original image");
    }

    private void pushUndo(BufferedImage img) {
        undoStack.push(ImageOps.copy(img));
        if (undoStack.size() > 20) undoStack.removeLast();
    }

    private void showInfo() {
        if (current == null) { Dialogs.warn("No image loaded"); return; }
        Dialogs.info("Source: " + currentSource
                + "\nDimensions: " + current.getWidth() + " × " + current.getHeight()
                + "\nType: " + current.getType()
                + "\nZoom: " + String.format("%.0f%%", zoomFactor * 100));
    }

    // ============================================================
    //                     HISTOGRAM
    // ============================================================
    private void showHistogram() {
        if (current == null) { Dialogs.warn("Please load an image."); return; }
        int[] hist = ImageOps.grayscaleHistogram(current);
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setTitle("Grayscale Histogram");
        chart.setLegendVisible(false);
        chart.setCategoryGap(0);
        chart.setBarGap(0);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (int i = 0; i < 256; i += 4)
            series.getData().add(new XYChart.Data<>(String.valueOf(i), hist[i]));
        chart.getData().add(series);

        Stage s = new Stage();
        s.setTitle("Histogram");
        Scene sc = new Scene(chart, 800, 500);
        sc.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        s.setScene(sc);
        s.show();
        Log.info("Histogram window opened");
    }

    // ============================================================
    //                SQLITE HISTORY VIEWER
    // ============================================================
    private void showHistory() {
        List<HistoryEntry> rows = db.getHistory();
        TableView<HistoryEntry> table = new TableView<>();
        TableColumn<HistoryEntry, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        TableColumn<HistoryEntry, String> opCol = new TableColumn<>("Operation");
        opCol.setCellValueFactory(new PropertyValueFactory<>("operation"));
        TableColumn<HistoryEntry, String> tsCol = new TableColumn<>("Time");
        tsCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        TableColumn<HistoryEntry, String> srcCol = new TableColumn<>("Source");
        srcCol.setCellValueFactory(new PropertyValueFactory<>("sourceImage"));
        TableColumn<HistoryEntry, String> detCol = new TableColumn<>("Details");
        detCol.setCellValueFactory(new PropertyValueFactory<>("details"));
        table.getColumns().addAll(idCol, opCol, tsCol, srcCol, detCol);
        table.getItems().addAll(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Stage s = new Stage();
        s.setTitle("Processing History (" + rows.size() + " entries)");
        Scene sc = new Scene(table, 900, 420);
        sc.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        s.setScene(sc);
        s.show();
        Log.info("History window opened — " + rows.size() + " entries");
    }

    // ============================================================
    //                     JSON / API
    // ============================================================
    private void loadRandomDogFromApi() {
        showLoading("Calling dog.ceo API…");
        Task<BufferedImage> task = new Task<>() {
            @Override protected BufferedImage call() throws Exception {
                Log.opStart("dog.ceo API request");
                DogApiResponse json = ApiClient.fetchRandomDog();
                Log.item("JSON received: " + json);
                byte[] bytes = ApiClient.downloadImage(json.getMessage());
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                if (img == null) throw new IOException("Could not decode image from API");
                return img;
            }
        };
        statusLabel.setText("Fetching from API...");
        task.setOnSucceeded(e -> {
            loadImage(task.getValue(), "dog.ceo API");
            statusLabel.setText("Loaded from API");
            Log.success("Dog image loaded from REST API (JSON parsed via Gson)");
            db.addHistory(new HistoryEntry("Load from API",
                    LocalDateTime.now().format(TS), "dog.ceo API", "JSON parsed via Gson"));
            hideLoading();
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            Dialogs.error("API call failed: " + ex.getMessage());
            statusLabel.setText("Ready");
            Log.error("API: " + ex.getMessage());
            hideLoading();
        });
        executor.submit(task);
    }

    private void exportJson() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export History to JSON");
        fc.setInitialFileName("history.json");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        File f = fc.showSaveDialog(null);
        if (f == null) return;
        try {
            JsonHandler.exportHistory(db.getHistory(), f);
            Dialogs.info("History exported to " + f.getName());
            Log.success("Exported history → " + f.getName());
        } catch (IOException e) {
            Log.error("Export failed: " + e.getMessage());
            Dialogs.error("Export failed: " + e.getMessage());
        }
    }

    private void importJson() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Import History from JSON");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        File f = fc.showOpenDialog(null);
        if (f == null) return;
        try {
            List<HistoryEntry> entries = JsonHandler.importHistory(f);
            for (HistoryEntry e : entries) db.addHistory(e);
            Dialogs.info("Imported " + entries.size() + " entries.");
            Log.success("Imported " + entries.size() + " history entries");
        } catch (IOException e) {
            Log.error("Import failed: " + e.getMessage());
            Dialogs.error("Import failed: " + e.getMessage());
        }
    }

    private void showConfig() {
        Log.section("config.json contents");
        Log.kv("appName",       config.getAppName());
        Log.kv("version",       config.getVersion());
        Log.kv("logC",          String.valueOf(config.getDefaults().getLogC()));
        Log.kv("gamma",         String.valueOf(config.getDefaults().getGamma()));
        Log.kv("brightness",    String.valueOf(config.getDefaults().getBrightnessDelta()));
        Log.kv("contrast",      String.valueOf(config.getDefaults().getContrastFactor()));
        Log.kv("bitPlane",      String.valueOf(config.getDefaults().getBitPlane()));
        Log.kv("meanKernel",    String.valueOf(config.getDefaults().getMeanKernel()));
        Log.kv("medianKernel",  String.valueOf(config.getDefaults().getMedianKernel()));
        Log.kv("highBoostA",    String.valueOf(config.getDefaults().getHighBoostA()));
        Log.kv("api.endpoint",  config.getApi().getRandomImageEndpoint());
        Log.kv("db.url",        config.getDatabase().getUrl());
        Log.divider();
        

        Dialogs.info(
                "config.json contents:\n\n" +
                        "appName: " + config.getAppName() + "\n" +
                        "version: " + config.getVersion() + "\n" +
                        "logC: " + config.getDefaults().getLogC() + "\n" +
                        "gamma: " + config.getDefaults().getGamma() + "\n" +
                        "brightnessDelta: " + config.getDefaults().getBrightnessDelta() + "\n" +
                        "contrastFactor: " + config.getDefaults().getContrastFactor() + "\n" +
                        "bitPlane: " + config.getDefaults().getBitPlane() + "\n" +
                        "meanKernel: " + config.getDefaults().getMeanKernel() + "\n" +
                        "medianKernel: " + config.getDefaults().getMedianKernel() + "\n" +
                        "highBoostA: " + config.getDefaults().getHighBoostA() + "\n" +
                        "api.endpoint: " + config.getApi().getRandomImageEndpoint() + "\n" +
                        "db.url: " + config.getDatabase().getUrl());
    }
}
