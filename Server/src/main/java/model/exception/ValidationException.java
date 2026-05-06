package model.exception;

public class ValidationException extends RuntimeException {
    /**
     * Precondition: message mô tả lỗi validate dữ liệu.
     * Postcondition: Tạo RuntimeException mang message được truyền vào.
     */
    public ValidationException(String message) {
        super(message);
    }
}
