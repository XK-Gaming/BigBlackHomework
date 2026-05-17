package controller;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import network.AuctionClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        String serverIp = "localhost";
        int serverPort = 8080;

        Properties props = new Properties();
        try (InputStream input = Main.class.getClassLoader().getResourceAsStream("client.properties")) {
            if (input != null) {
                props.load(input);
                serverIp = props.getProperty("server.ip", "localhost");
                serverPort = Integer.parseInt(props.getProperty("server.port", "8080"));
            }
        } catch (Exception e) {
            System.err.println("Không load được client.properties, dùng cấu hình mặc định");
        }

        try {
            AuctionClient client = AuctionClient.getInstance();
            client.connect(serverIp, serverPort);
        } catch (Exception e) {
            System.err.println("Không thể kết nối server: " + e.getMessage());
        }

        FXMLLoader loader = new FXMLLoader(getClass().getResource("LoginView.fxml"));
        Parent root = loader.load();
        
        stage.setTitle("Hệ thống đấu giá trực tuyến");
        stage.setScene(new Scene(root));
        stage.show();
    }
}