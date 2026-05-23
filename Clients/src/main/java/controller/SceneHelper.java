package controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.stage.Stage;

import java.io.IOException;

public class SceneHelper {
    public static void changeScene(Node node, String fxmlFile) {
        try {
            FXMLLoader loader = new FXMLLoader(SceneHelper.class.getResource( fxmlFile));
            Parent root = loader.load();

            Stage stage = (Stage) node.getScene().getWindow();
            stage.getScene().setRoot(root);
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
            stage.setScene(new javafx.scene.Scene(root));
            stage.show();

            // Trả về Controller của Scene mới để bên ngoài thích truyền gì thì truyền
            return loader.getController();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}