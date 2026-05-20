package controller;

import dao.DAOAuction_Items;
import dao.DAOItems;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import model.Items.Item;
import model.User.User;
import model.User.UserSession;
import model.auction.AuctionStatus;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ControllerProductList {

    @FXML private TableView<Item> tableProducts;
    @FXML private TableColumn<Item, Integer> colId;
    @FXML private TableColumn<Item, String> colName;
    @FXML private TableColumn<Item, String> colCategory;
    @FXML private TableColumn<Item, Double> colPrice;
    @FXML private TableColumn<Item, String> colTimeStart;
    @FXML private TableColumn<Item, String> colTimeEnd;
    @FXML private TableColumn<Item, AuctionStatus> colSessionStatus;

    @FXML private TextField txtSearch;
    @FXML private Label j_LabelName;
    @FXML private Label j_textSoDu;

    private final ObservableList<Item> productList = FXCollections.observableArrayList();
    private FilteredList<Item> filteredData;

    // Sử dụng HashMap để cache (lưu tạm) trạng thái, tránh việc gọi DB liên tục trong CellFactory
    private final Map<Integer, AuctionStatus> statusCache = new HashMap<>();

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    @FXML
    public void initialize() {
        // 1. Thông tin user
        User currentUser = UserSession.getLoggedInUser();
        if (currentUser != null) {
            j_LabelName.setText(currentUser.getName());
            j_textSoDu.setText("0 VNĐ");
        }

        // 2. Cấu hình các cột cơ bản
        colId.setCellValueFactory(new PropertyValueFactory<>("databaseId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("startingPrice"));

        colCategory.setCellValueFactory(cellData -> {
            String type = cellData.getValue().getItemType();
            return new SimpleStringProperty(type != null ? type : "");
        });

        colPrice.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                if (empty || price == null) {
                    setText(null);
                } else {
                    setText(String.format("%,.0f VNĐ", price));
                }
            }
        });

        colTimeStart.setCellValueFactory(cellData -> formatInstant(cellData.getValue().getAuctionStartTime()));
        colTimeEnd.setCellValueFactory(cellData -> formatInstant(cellData.getValue().getAuctionEndTime()));

        // Tối ưu hóa cột Trạng Thái: Lấy từ Cache đã nạp sẵn, KHÔNG GỌI DAO Ở ĐÂY
        colSessionStatus.setCellValueFactory(cellData -> {
            Item item = cellData.getValue();
            AuctionStatus status = statusCache.get(item.getDatabaseId());
            return new SimpleObjectProperty<>(status);
        });

        colSessionStatus.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(AuctionStatus status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    // Tận dụng switch-case tối ưu hiệu năng hiển thị text & màu sắc
                    switch (status) {
                        case OPEN:
                            setText("Sắp diễn ra");
                            setStyle("-fx-text-fill: #17a2b8; -fx-font-weight: bold;");
                            break;
                        case RUNNING:
                            setText("Đang diễn ra");
                            setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
                            break;
                        case FINISHED:
                            setText("Đã kết thúc");
                            setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
                            break;
                        case PAID:
                            setText("Đã thanh toán");
                            setStyle("-fx-text-fill: #007bff; -fx-font-weight: bold;");
                            break;
                        case CANCELLED:
                            setText("Đã hủy");
                            setStyle("-fx-text-fill: #6c757d; -fx-font-style: italic;");
                            break;
                        default:
                            setText(status.toString());
                            setStyle("");
                            break;
                    }
                }
            }
        });

        // 3. Tích hợp bộ lọc tìm kiếm Real-time
        filteredData = new FilteredList<>(productList, p -> true);
        txtSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(item -> {
                if (newValue == null || newValue.trim().isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newValue.toLowerCase().trim();
                if (item.getName() != null && item.getName().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                }
                return String.valueOf(item.getDatabaseId()).contains(lowerCaseFilter);
            });
        });
        tableProducts.setItems(filteredData);

        // 4. Chạy luồng ngầm để nạp dữ liệu từ DB lên mà không lo đơ màn hình
        loadProductsFromDatabaseAsync();
    }

    /**
     * TỐI ƯU: Tải danh sách bằng luồng ngầm (Background Thread) và nạp trước trạng thái vào Cache
     */
    private void loadProductsFromDatabaseAsync() {
        User currentUser = UserSession.getLoggedInUser();
        if (currentUser == null) return;

        String currentSellerUsername = currentUser.getUsername();

        // Hiển thị trạng thái đang tải (Tùy chọn: Có thể set placeholder cho table)
        tableProducts.setPlaceholder(new ProgressIndicator());

        Task<List<Item>> loadTask = new Task<>() {
            @Override
            protected List<Item> call() throws Exception {
                // Gợi ý tốt nhất: Viết hàm selectBySellerId(currentSellerUsername) trong DAO để tránh selectAll()
                ArrayList<Item> sellerItems = DAOItems.getInstance().selectBySellerId(currentSellerUsername);
                Map<Integer, AuctionStatus> localCache = new HashMap<>();

                if (sellerItems != null) {
                    for (Item item : sellerItems) {
                            // Nạp trạng thái đấu giá của từng item vào cache
                            var auctionItem = DAOAuction_Items.getInstance().selectByItemId(item);
                            if (auctionItem != null) {
                                localCache.put(item.getDatabaseId(), auctionItem.getStatus());
                            }
                        }
                    }

                // Đẩy cache tạm thời về vùng nhớ tạm của class
                Platform.runLater(() -> {
                    statusCache.clear();
                    statusCache.putAll(localCache);
                });

                return sellerItems != null ? sellerItems : new ArrayList<>();
            }
        };

        // Khi Task hoàn thành thành công, cập nhật giao diện trên UI Thread
        loadTask.setOnSucceeded(e -> {
            productList.setAll(loadTask.getValue());
            tableProducts.setPlaceholder(new Label("Không có sản phẩm nào."));
        });

        // Khi Task thất bại
        loadTask.setOnFailed(e -> {
            tableProducts.setPlaceholder(new Label("Lỗi khi tải dữ liệu từ cơ sở dữ liệu."));
            loadTask.getException().printStackTrace();
        });

        // Kích hoạt chạy luồng ngầm
        Thread thread = new Thread(loadTask);
        thread.setDaemon(true); // Đảm bảo thread tự tắt khi tắt ứng dụng
        thread.start();
    }

    private SimpleStringProperty formatInstant(Instant instant) {
        if (instant == null) return new SimpleStringProperty("");
        return new SimpleStringProperty(formatter.format(instant));
    }

    @FXML
    void On_AddProduct(ActionEvent event) {
        SceneHelper.changeScene((Node) event.getSource(), "/fxml/SellerView.fxml");
    }
    @FXML
    void On_EditProduct(ActionEvent event) {
        Item selectedItem = tableProducts.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            showAlert(Alert.AlertType.WARNING, "Thông báo", "Vui lòng chọn một sản phẩm trong danh sách để sửa!");
            return;
        }

        AuctionStatus status = statusCache.get(selectedItem.getDatabaseId());
        if (status == null) status = AuctionStatus.OPEN;

        boolean hasBids = false;
        var auctionItem = DAOAuction_Items.getInstance().selectByItemId(selectedItem);
        if (auctionItem != null && auctionItem.getBidHistory() != null && !auctionItem.getBidHistory().isEmpty()) {
            hasBids = true;
        }

        // Kiểm tra luật trạng thái
        if (status == AuctionStatus.FINISHED || status == AuctionStatus.PAID) {
            showAlert(Alert.AlertType.ERROR, "Bị từ chối", "Phiên đấu giá đã kết thúc/thanh toán. Không thể sửa!");
            return;
        }

        // Dùng SceneHelper nâng cấp: Vừa chuyển scene vừa lấy chuẩn Controller ra để nạp data
        ControllerEditProduct editController = SceneHelper.changeSceneAndGetController(
                (Node) event.getSource(), "/fxml/EditProductView.fxml"
        );

        if (editController != null) {
            // Gọi hàm truyền dữ liệu và kích hoạt luật disable trường nhập liệu
            editController.initData(selectedItem, status, hasBids);
        }
    }

    @FXML
    void On_DeleteProduct(ActionEvent event) {
        Item selectedItem = tableProducts.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            showAlert(Alert.AlertType.WARNING, "Thông báo", "Vui lòng chọn một sản phẩm để xóa!");
            return;
        }

        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Xác nhận xóa");
        confirmAlert.setHeaderText(null);
        confirmAlert.setContentText("Bạn có chắc chắn muốn xóa sản phẩm '" + selectedItem.getName() + "' không?");

        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {

            // Xử lý logic xóa chạy mượt hơn bằng cách dùng luồng phụ nếu cần,
            // hoặc giữ nguyên vì hành động xóa đơn lẻ diễn ra rất nhanh.
            var auctionItem = DAOAuction_Items.getInstance().selectByItemId(selectedItem);
            boolean hasBids = (auctionItem != null && auctionItem.getBidHistory() != null && !auctionItem.getBidHistory().isEmpty());

            if (hasBids) {
                showAlert(Alert.AlertType.WARNING, "Thông báo", "Chỉ có thể xóa Item chưa được đặt giá!");
            } else {
                if (auctionItem != null) {
                    DAOAuction_Items.getInstance().Delete(selectedItem);
                }
                DAOItems.getInstance().Delete(selectedItem);

                // Xóa khỏi cache và danh sách hiển thị
                statusCache.remove(selectedItem.getDatabaseId());
                productList.remove(selectedItem);
                showAlert(Alert.AlertType.INFORMATION, "Thành công", "Xóa sản phẩm thành công!");
            }
        }
    }

    @FXML
    void On_LogOut(ActionEvent event) {
        UserSession.cleanUserSession();
        SceneHelper.changeScene((Node) event.getSource(), "/fxml/LoginView.fxml");
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}