package com.imagelab;

import com.imagelab.ui.MainWindow;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {
    @Override
    public void start(Stage stage) {
        MainWindow window = new MainWindow(stage);
        Scene scene = new Scene(window.getRoot(), 1280, 800);
        scene.getStylesheets().add(
                App.class.getResource("/styles.css").toExternalForm());
        stage.setTitle("ImageLab - Image Processing Studio");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> System.exit(0));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}