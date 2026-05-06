package controller;

import javafx.application.Application;

/**
 * Điểm vào (entry point) dạng plain `main()` để chạy JavaFX app.
 *
 * <p>Lý do tồn tại:
 * một số môi trường đóng gói/chạy jar cần `public static void main` riêng thay vì gọi trực tiếp {@link Main}.
 */
public class Launcher {
    /**
     * Precondition: Không có.
     * Postcondition: Khởi chạy JavaFX runtime và gọi {@link Main#start(javafx.stage.Stage)}.
     * NOTE: JavaFX sẽ quản lý vòng đời ứng dụng; method này chỉ "uỷ quyền" cho {@link Application#launch(Class, String...)}.
     * Method returns: nothing.
     */
    public static void main(String[] args) {
        Application.launch(Main.class, args);
    }
}
