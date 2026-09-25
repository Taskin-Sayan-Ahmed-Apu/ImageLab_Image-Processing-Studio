package com.imagelab.ui;

import com.imagelab.core.ImageOps;
import com.imagelab.core.PPMHandler;
import com.imagelab.core.YUVHandler;
import com.imagelab.data.*;
import com.imagelab.util.Dialogs;
import com.imagelab.util.ImageConverter;
import com.imagelab.util.Log;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

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

    private final BorderPane root = new BorderPane();
    private final ImageView imageView = new ImageView();
    private final Label statusLabel = new Label("Ready");
    private final Label resolutionLabel = new Label("Resolution: -");
    private final Label mouseLabel = new Label("Mouse: (-, -)");

    private final Deque<BufferedImage> undoStack = new ArrayDeque<>();
    private BufferedImage original;
    private BufferedImage current;
    private String currentSource = "(none)";

    private final ExecutorService executor = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
    private final DatabaseManager db = new DatabaseManager();
    private final AppConfig config = ConfigLoader.get();

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public MainWindow(Stage stage) {
        root.setTop(buildMenuBar());
        root.setCenter(buildCenter());
        root.setBottom(buildStatusBar());
        Log.info(config.getAppName() + " ready. Defaults from config.json loaded.");
    }

    public BorderPane getRoot() { return root; }

    // ---------- UI building ----------

    private MenuBar buildMenuBar() {
        MenuBar bar = new MenuBar();
        bar.getMenus().addAll(
                fileMenu(), editMenu(), intensityMenu(), colorMenu(),
                channelMenu(), filterMenu(), arithmeticMenu(),
                histogramMenu(), morphologyMenu(), dataMenu(), apiMenu());
        return bar;
    }

    private ScrollPane buildCenter() {
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        StackPane pane = new StackPane(imageView);
        pane.setPadding(new Insets(10));
        pane.setStyle("-fx-background-color: #ffffff;");

        ScrollPane sp = new ScrollPane(pane);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setPannable(true);
        sp.setOnMouseMoved(e ->
                mouseLabel.setText(String.format("Mouse: (%.0f, %.0f)", e.getX(), e.getY())));
        return sp;
    }

    private HBox buildStatusBar() {
        HBox box = new HBox(20, statusLabel, resolutionLabel, mouseLabel);
        box.getStyleClass().add("status-bar");
        return box;
    }

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
                item("Exit", () -> { executor.shutdownNow(); Platform.exit(); }));
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

    // ---------- File operations ----------

    private void openImage() {
        File f = chooseOpen("Open Image",
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"));
        if (f == null) return;
        try {
            BufferedImage img = ImageIO.read(f);
            if (img == null) throw new IOException("Unsupported image file");
            loadImage(img, f.getName());
            db.addMetadata(f.getAbsolutePath(), img.getWidth(), img.getHeight(),
                    f.getName().substring(f.getName().lastIndexOf('.') + 1));
        } catch (IOException e) {
            Dialogs.error("Could not open image: " + e.getMessage());
            Log.error(e.getMessage());
        }
    }

    private void openPPM() {
        File f = chooseOpen("Open PPM", new FileChooser.ExtensionFilter("PPM", "*.ppm"));
        if (f == null) return;
        try { loadImage(PPMHandler.read(f), f.getName()); }
        catch (IOException e) { Dialogs.error("Could not read PPM: " + e.getMessage()); }
    }

    private void openYUV() {
        File f = chooseOpen("Open YUV", new FileChooser.ExtensionFilter("YUV", "*.yuv"));
        if (f == null) return;
        try { loadImage(YUVHandler.read(f), f.getName()); }
        catch (IOException e) { Dialogs.error("Could not read YUV: " + e.getMessage()); }
    }

    // ---------- JSON / API ----------

    private void loadRandomDogFromApi() {
        Task<BufferedImage> task = new Task<>() {
            @Override protected BufferedImage call() throws Exception {
                Log.info("Calling API: " + config.getApi().getRandomImageEndpoint());
                DogApiResponse json = ApiClient.fetchRandomDog();
                Log.info("Received JSON: " + json);
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
            db.addHistory(new HistoryEntry("Load from API",
                    LocalDateTime.now().format(TS), "dog.ceo API", "JSON parsed via Gson"));
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            Dialogs.error("API call failed: " + ex.getMessage());
            statusLabel.setText("Ready");
            Log.error("API: " + ex.getMessage());
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
            Log.info("Exported history JSON: " + f.getAbsolutePath());
        } catch (IOException e) {
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
            Log.info("Imported " + entries.size() + " history entries");
        } catch (IOException e) {
            Dialogs.error("Import failed: " + e.getMessage());
        }
    }

    private void showConfig() {
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

    // ---------- Save ----------

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
            Log.info("Saved " + f.getAbsolutePath());
        } catch (IOException e) {
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
        try { PPMHandler.write(current, f); Log.info("PPM exported: " + f.getName()); }
        catch (IOException e) { Dialogs.error("PPM save failed: " + e.getMessage()); }
    }

    private void saveYUV() {
        if (current == null) { Dialogs.warn("No image"); return; }
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("YUV", "*.yuv"));
        fc.setInitialFileName("output.yuv");
        File f = fc.showSaveDialog(null);
        if (f == null) return;
        try { YUVHandler.write(current, f); Log.info("YUV exported: " + f.getName()); }
        catch (IOException e) { Dialogs.error("YUV save failed: " + e.getMessage()); }
    }

    private File chooseOpen(String title, FileChooser.ExtensionFilter filter) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(filter);
        return fc.showOpenDialog(null);
    }

    // ---------- Image pipeline ----------

    private void loadImage(BufferedImage img, String source) {
        original = ImageOps.copy(img);
        current = ImageOps.copy(img);
        currentSource = source;
        undoStack.clear();
        updateView();
        Log.info("Loaded " + source + " (" + img.getWidth() + "x" + img.getHeight() + ")");
        statusLabel.setText("Loaded: " + source);
    }

    private void updateView() {
        if (current == null) { imageView.setImage(null); resolutionLabel.setText("Resolution: -"); return; }
        Image fx = ImageConverter.toFX(current);
        imageView.setImage(fx);
        imageView.setFitWidth(current.getWidth());
        imageView.setFitHeight(current.getHeight());
        resolutionLabel.setText("Resolution: " + current.getWidth() + " x " + current.getHeight());
    }

    private void process(String name, Function<BufferedImage, BufferedImage> op) {
        if (current == null) { Dialogs.warn("Please load an image first."); return; }
        BufferedImage src = current;
        Task<BufferedImage> task = new Task<>() {
            @Override protected BufferedImage call() { return op.apply(src); }
        };
        statusLabel.setText("Processing: " + name + "...");
        task.setOnSucceeded(e -> {
            pushUndo(src);
            current = task.getValue();
            updateView();
            statusLabel.setText("Done: " + name);
            Log.info("Applied: " + name);
            db.addHistory(new HistoryEntry(name, LocalDateTime.now().format(TS), currentSource, ""));
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            Log.error(name + " failed: " + ex.getMessage());
            Dialogs.error(name + " failed: " + ex.getMessage());
            statusLabel.setText("Ready");
        });
        executor.submit(task);
    }

    private void secondImageProcess(String name,
                                    BiFunction<BufferedImage, BufferedImage, BufferedImage> op) {
        if (current == null) { Dialogs.warn("Please load an image first."); return; }
        FileChooser fc = new FileChooser();
        fc.setTitle("Choose second image");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.bmp"));
        File f = fc.showOpenDialog(null);
        if (f == null) return;
        try {
            BufferedImage second = ImageIO.read(f);
            if (second == null) throw new IOException("Unsupported second image");
            process(name, img -> op.apply(img, second));
        } catch (IOException e) {
            Dialogs.error("Could not read second image: " + e.getMessage());
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
        if (undoStack.isEmpty()) { Dialogs.info("Nothing to undo."); return; }
        current = undoStack.pop();
        updateView();
        statusLabel.setText("Undo");
    }

    private void reset() {
        if (original == null) return;
        undoStack.clear();
        current = ImageOps.copy(original);
        updateView();
        statusLabel.setText("Reset to original");
    }

    private void pushUndo(BufferedImage img) {
        undoStack.push(ImageOps.copy(img));
        if (undoStack.size() > 20) undoStack.removeLast();
    }

    private void showInfo() {
        if (current == null) { Dialogs.warn("No image loaded"); return; }
        Dialogs.info("Source: " + currentSource
                + "\nDimensions: " + current.getWidth() + " x " + current.getHeight()
                + "\nType: " + current.getType());
    }

    // ---------- Histogram ----------

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
    }

    // ---------- SQLite History Viewer ----------

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
    }
}