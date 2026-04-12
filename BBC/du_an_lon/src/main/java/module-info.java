module trang_chu.du_an_lon {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires java.sql;
    requires mysql.connector.java;


    opens trang_chu.du_an_lon to javafx.fxml;
    exports trang_chu.du_an_lon;
    exports trang_chu.du_an_lon.database;
    opens trang_chu.du_an_lon.database to javafx.fxml;
    exports trang_chu.du_an_lon.model;
    opens trang_chu.du_an_lon.model to javafx.fxml;
}