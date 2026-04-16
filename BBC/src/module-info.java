module bigblackhomework {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires mysql.connector.j;

    exports trang_chu.du_an_lon;

    opens trang_chu.du_an_lon to javafx.fxml;
}
