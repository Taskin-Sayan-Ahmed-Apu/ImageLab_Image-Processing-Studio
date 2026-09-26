package com.imagelab;

import com.imagelab.data.AppConfig;
import com.imagelab.data.ConfigLoader;
import com.imagelab.ui.ConsolePanel;
import com.imagelab.ui.MainWindow;
import com.imagelab.util.Log;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        // Terminal banner (safe to ignore in IntelliJ — panel shows its own)
        Log.banner("ImageLab", "1.0.0");

        AppConfig cfg = ConfigLoader.get();

        Log.section("System");
        Log.kv("Application", cfg.getAppName() + " v" + cfg.getVersion());
        Log.kv("Java", System.getProperty("java.version"));
        Log.kv("OS", System.getProperty("os.name"));
        Log.kv("CPU cores", String.valueOf(Runtime.getRuntime().availableProcessors()));
        Log.kv("Max heap", (Runtime.getRuntime().maxMemory() / 1024 / 1024) + " MB");
        Log.divider();

        MainWindow window = new MainWindow(stage);

        // Push the welcome banner into the in-app console panel
        ConsolePanel console = window.getConsole();
        console.banner(cfg.getAppName(), cfg.getVersion());

        Scene scene = new Scene(window.getRoot(), 1280, 850);
        scene.getStylesheets().add(
                App.class.getResource("/styles.css").toExternalForm());

        stage.setTitle(cfg.getAppName() + " — Image Processing Studio");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> System.exit(0));
        stage.show();

        Log.success("UI ready — waiting for user input");
    }

    public static void main(String[] args) {
        launch(args);
    }
}