package controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class SceneHelper {
    private static final String GLOBAL_STYLESHEET = "/css/style.css";

    public static void applyGlobalStyles(Scene scene) {
        if (scene == null) {
            return;
        }

        var stylesheet = SceneHelper.class.getResource(GLOBAL_STYLESHEET);
        if (stylesheet == null) {
            return;
        }

        String stylesheetUrl = stylesheet.toExternalForm();
        if (!scene.getStylesheets().contains(stylesheetUrl)) {
            scene.getStylesheets().add(stylesheetUrl);
        }
    }

    public static void changeScene(Node node, String fxmlFile) {
        try {
            FXMLLoader loader = new FXMLLoader(SceneHelper.class.getResource( fxmlFile));
            Parent root = loader.load();

            Stage stage = (Stage) node.getScene().getWindow();
            stage.getScene().setRoot(root);
            applyGlobalStyles(stage.getScene());
            stage.sizeToScene();
            stage.centerOnScreen();

        } catch (IOException e) {
            System.err.println("Lỗi: " + fxmlFile);
            e.printStackTrace();
        }
    }
    public static <T> T changeSceneAndGetController(Node node, String fxmlPath) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(SceneHelper.class.getResource(fxmlPath));
            javafx.scene.Parent root = loader.load();

            javafx.stage.Stage stage = (javafx.stage.Stage) node.getScene().getWindow();
            javafx.scene.Scene scene = new javafx.scene.Scene(root);
            applyGlobalStyles(scene);
            stage.setScene(scene);
            stage.show();

            // Trả về Controller của Scene mới để bên ngoài thích truyền gì thì truyền
            return loader.getController();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    public static void changeScene(javafx.stage.Stage stage, String fxmlPath) {
        try {
            javafx.scene.Parent root = javafx.fxml.FXMLLoader.load(SceneHelper.class.getResource(fxmlPath));
            stage.getScene().setRoot(root);
            applyGlobalStyles(stage.getScene());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
