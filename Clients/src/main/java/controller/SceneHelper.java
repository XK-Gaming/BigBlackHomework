package controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.stage.Stage;

import java.io.IOException;

public class SceneHelper {
    public static void changeScene(Node node, String fxmlFile) {
        try {
            FXMLLoader loader = new FXMLLoader(SceneHelper.class.getResource("/controller/" + fxmlFile));
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
}