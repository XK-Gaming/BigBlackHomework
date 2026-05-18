package controller;

import dao.DAOItems;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import model.Items.Item;
import model.User.User;
import model.User.UserSession;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Optional;

public class ControllerProductList {

    @FXML private TableView<Item> tableProducts;
    @FXML private TableColumn<Item, Integer> colId;
    @FXML private TableColumn<Item, String> colName;
    @FXML private TableColumn<Item, String> colCategory;
    @FXML private TableColumn<Item, Double> colPrice;
    @FXML private TableColumn<Item, String> colTimeStart;
    @FXML private TableColumn<Item, String> colTimeEnd;

    @FXML private TextField txtSearch;
    @FXML private Label j_LabelName;
    @FXML private Label j_textSoDu;


    // Danh sách lưu trữ sản phẩm phục vụ TableView
    private final ObservableList<Item> productList = FXCollections.observableArrayList();
    private FilteredList<Item> filteredData;

    // Định dạng thời gian hiển thị: Ngày/Tháng/Năm Giờ:Phút
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    @FXML
    public void initialize() {
        // 1. Lấy thông tin user hiện tại từ static UserSession
        User currentUser = UserSession.getLoggedInUser();
        if (currentUser != null) {
            // Hiển thị tên đầy đủ (hoặc dùng currentUser.getUsername() tùy bạn muốn)
            j_LabelName.setText(currentUser.getName());

            // Vì class User của bạn hiện chưa có trường Số dư (Balance),
            // tạm thời hiển thị cố định hoặc bạn có thể bổ sung trường này vào DB/User sau.
            j_textSoDu.setText("0 VNĐ");
        }

        // 2. Cấu hình map dữ liệu vào các cột của TableView
        colId.setCellValueFactory(new PropertyValueFactory<>("databaseId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));

        // Hiển thị loại mặt hàng từ Enum ItemType thông qua hàm toString()
        colCategory.setCellValueFactory(cellData -> {
            if (cellData.getValue().getItemType() != null) {
                return new SimpleStringProperty(cellData.getValue().getItemType());
            }
            return new SimpleStringProperty("");
        });

        colPrice.setCellValueFactory(new PropertyValueFactory<>("startingPrice"));

        // Định dạng cột Giá khởi điểm hiển thị thêm chữ "VNĐ" và dấu phẩy phân cách hàng nghìn
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

        // Định dạng 2 cột thời gian từ Instant về String dễ đọc
        colTimeStart.setCellValueFactory(cellData -> formatInstant(cellData.getValue().getAuctionStartTime()));
        colTimeEnd.setCellValueFactory(cellData -> formatInstant(cellData.getValue().getAuctionEndTime()));

        // 3. Tải sản phẩm từ Database lên bảng
        loadProductsFromDatabase();

        // 4. Tích hợp thanh tìm kiếm Real-time (Gõ đến đâu lọc đến đấy)
        filteredData = new FilteredList<>(productList, p -> true);
        txtSearch.textProperty().addListener((observable, oldValue, newValue) -> filteredData.setPredicate(item -> {
            if (newValue == null || newValue.trim().isEmpty()) {
                return true;
            }
            String lowerCaseFilter = newValue.toLowerCase();

            // Hỗ trợ tìm kiếm theo cả Tên sản phẩm hoặc Mã sản phẩm
            if (item.getName() != null && item.getName().toLowerCase().contains(lowerCaseFilter)) {
                return true;
            } else return String.valueOf(item.getDatabaseId()).contains(lowerCaseFilter);
        }));

        tableProducts.setItems(filteredData);
    }

    /**
     * Tải danh sách sản phẩm và chỉ lọc ra những sản phẩm do Seller này đăng bán
     */
    private void loadProductsFromDatabase() {
        productList.clear();
        ArrayList<Item> allItems = DAOItems.getInstance().selectAll();

        User currentUser = UserSession.getLoggedInUser();
        if (allItems != null && currentUser != null) {
            String currentSellerUsername = currentUser.getUsername(); // Đối chiếu với sellerId trong DB

            for (Item item : allItems) {
                // Kiểm tra nếu sản phẩm có sellerId trùng với username người đang đăng nhập
                if (item.getSellerId() != null && item.getSellerId().equals(currentSellerUsername)) {
                    productList.add(item);
                }
            }
        }
    }

    /**
     * Hàm helper đổi Instant sang chuỗi ngày tháng trực quan
     */
    private SimpleStringProperty formatInstant(Instant instant) {
        if (instant == null) {
            return new SimpleStringProperty("");
        }
        return new SimpleStringProperty(formatter.format(instant));
    }

    @FXML
    void On_AddProduct(ActionEvent event) {
        // Chuyển hướng quay lại màn hình thêm sản phẩm
        SceneHelper.changeScene((Node) event.getSource(), "/fxml/SellerView.fxml");
    }

    @FXML
    void On_EditProduct(ActionEvent event) {
        Item selectedItem = tableProducts.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            showAlert(Alert.AlertType.WARNING, "Thông báo", "Vui lòng chọn một sản phẩm trong danh sách để sửa!");
            return;
        }

        // Bạn có thể truyền dữ liệu sản phẩm được chọn sang màn hình sửa tại đây:
        // Ví dụ: ControllerEditProduct.setTargetItem(selectedItem);
        // SceneHelper.changeScene((Node) event.getSource(), "/fxml/EditProductView.fxml");

        showAlert(Alert.AlertType.INFORMATION, "Tính năng", "Hệ thống sẽ mở giao diện chỉnh sửa cho: " + selectedItem.getName());
    }

    @FXML
    void On_DeleteProduct(ActionEvent event) {
        Item selectedItem = tableProducts.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            showAlert(Alert.AlertType.WARNING, "Thông báo", "Vui lòng chọn một sản phẩm để xóa!");
            return;
        }

        // Hộp thoại xác nhận trước khi xóa
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Xác nhận xóa");
        confirmAlert.setHeaderText(null);
        confirmAlert.setContentText("Bạn có chắc chắn muốn xóa sản phẩm '" + selectedItem.getName() + "' không?");

        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // Gọi xuống DAO để xóa sản phẩm dưới database.
            // Khi bạn đã update code hàm Delete trong DAOItems, hãy mở comment dòng bên dưới ra:
            // int check = DAOItems.getInstance().Delete(selectedItem);

            // Xóa trực tiếp trên giao diện để cập nhật ngay lập tức
            productList.remove(selectedItem);
            showAlert(Alert.AlertType.INFORMATION, "Thành công", "Xóa sản phẩm thành công!");
        }
    }

    @FXML
    void On_LogOut(ActionEvent event) {
        UserSession.cleanUserSession(); // Xóa sạch session static giải phóng bộ nhớ
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