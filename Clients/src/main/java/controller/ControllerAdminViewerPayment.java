package controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import model.DepositTransaction;
import network.AuctionClient;
import network.DataPacket;
import network.ServerListener;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ControllerAdminViewerPayment implements ServerListener {
    @FXML private TableView<DepositTransaction> paymentTable;
    @FXML private TableColumn<DepositTransaction, String> colUser;
    @FXML private TableColumn<DepositTransaction, Double> colAmount;
    @FXML private TableColumn<DepositTransaction, String> colTime;
    @FXML private TableColumn<DepositTransaction, String> colStatus;

    private final ObservableList<DepositTransaction> masterData = FXCollections.observableArrayList();

    public void initialize() {
        AuctionClient.getInstance().addListener(this);
        colUser.setCellValueFactory(new PropertyValueFactory<>("username"));
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colTime.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        
        loadPendingDeposits();
    }

    private void loadPendingDeposits() {
        try {
            AuctionClient.getInstance().sendCommand(network.Command.GET_PENDING_DEPOSITS, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    void handleApprove(ActionEvent event) {
        DepositTransaction selected = paymentTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            try {
                AuctionClient.getInstance().sendCommand(network.Command.APPROVE_DEPOSIT, Map.of(
                        "username", selected.getUsername(),
                        "transactionId", selected.getId()
                ));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @FXML
    void handleReject(ActionEvent event) {
        DepositTransaction selected = paymentTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            try {
                AuctionClient.getInstance().sendCommand(network.Command.REJECT_DEPOSIT, Map.of(
                        "username", selected.getUsername(),
                        "transactionId", selected.getId()
                ));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @FXML
    void handleReturn(ActionEvent event) {
        AuctionClient.getInstance().removeListener(this);
        SceneHelper.changeScene((Node) event.getSource(), "/fxml/AdminView.fxml");
    }

    @Override
    public void onServerResponse(DataPacket response) {
        switch (response.command()) {
            case GET_PENDING_DEPOSITS_RESULT:
                List<DepositTransaction> list = (List<DepositTransaction>) response.payload();
                Platform.runLater(() -> {
                    masterData.setAll(list);
                    paymentTable.setItems(masterData);
                });
                break;
            case APPROVE_DEPOSIT_RESULT:
            case REJECT_DEPOSIT_RESULT:
                boolean success = (boolean) response.payload();
                Platform.runLater(() -> {
                    if (success) {
                        showAlert("Thành công", "Thao tác đã được thực hiện.");
                        loadPendingDeposits();
                    } else {
                        showAlert("Lỗi", "Không thể thực hiện thao tác.");
                    }
                });
                break;
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
