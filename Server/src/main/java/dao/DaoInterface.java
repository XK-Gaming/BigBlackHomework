package dao;

import model.Items.Item;
import model.User.Bidder;
import model.auction.Auction;

import java.sql.SQLException;
import java.util.ArrayList;

/**
 * Hợp đồng DAO dùng chung cho các class truy cập database trong module này.
 *
 * NOTE: Interface đang trộn CRUD generic với overload riêng cho Auction/Item, vì vậy nhiều
 * implementation có method không liên quan và đang để stub.
 */
interface DaoInterface <T> {

    /**
     * Precondition: t là entity đầy đủ dữ liệu và được DAO implementation hỗ trợ.
     * Postcondition: Entity được insert vào bảng tương ứng nếu method được implement.
     * Method trả về số dòng bị ảnh hưởng, hoặc giá trị tùy implementation.
     */
    int Insert(T t);

    //Logic thêm sản phẩm__ dùng PrepareStatement
    /**
     * Precondition: auction và item1 mô tả dòng auction cần tạo.
     * Postcondition: Dữ liệu auction được insert nếu method được implement.
     */
    int Insert(Auction auction, Item item1);

    /**
     * Precondition: item có đầy đủ dữ liệu cần lưu.
     * Postcondition: Dữ liệu item được insert nếu method được implement.
     */
    int Insert(Item item);

    /**
     * Precondition: t xác định một dòng đã tồn tại và chứa dữ liệu mới.
     * Postcondition: Dòng tương ứng được update nếu method được implement.
     */
    public int Update(T t);

    /**
     * Precondition: t xác định một dòng đã tồn tại.
     * Postcondition: Dòng tương ứng được xóa nếu method được implement.
     */
    public int Delete(T t);

    /**
     * Precondition: Có thể tạo kết nối database.
     * Postcondition: Method trả về toàn bộ dòng của entity type nếu được implement.
     */
    public ArrayList<T> selectAll() throws SQLException;

    /**
     * Precondition: t chứa username hoặc dữ liệu định danh.
     * Postcondition: Method trả về entity khớp nếu được implement.
     */
    public  T selectByUsername(T t);

    /**
     * Precondition: condition là điều kiện query mà implementation hỗ trợ.
     * Postcondition: Method trả về các dòng khớp nếu được implement.
     */
    public ArrayList<T> selectByCondition (String condition);
}
