package network;


public enum Command {
    LOGIN,  // Đăng nhập
    LOGIN_RESULT,


    REGISTER, // Đăng ký
    REGISTER_RESULT,

    CREATE_ITEM,// Tạo sản phẩm mới
    CREATE_ITEM_RESULT,

    // Lấy ra tất cả sản phẩm --- > Dùng để hiển thị danh sách sản phẩm trên client
    SELECT_ITEMS,
    SELECT_ITEMS_RESULT,

    // Lấy ra phiên đấu giá --- > Dùng để hiển thị thông tin phiên đấu giá trên client
    GET_AUCTION,
    GET_AUCTION_RESULT,


    // Chức năng chính là khi có 1 client vào trang đấu giá thì sản phẩm
    // thì sẽ tự động câp nhật thông tin trang thái của phien đấu giá đó trên client
    // Ví dụ nếu không ấn vào thì phiên đấu giá sẽ ở trạng thái open
    // Trức khi mỗi lần một người và phiên đấu giá thì ngời ấy sẽ tự động yêu cầu cập nhật status
    // phiên đấu giá đó trên db với thời gian thực... từ đó mới xuất hiện trạng thái cua phiên.
    SET_AUCTION,
    SET_AUCTION_RESULT,

    // Đấu giá -- > Dùng để cập nhật thông tin phiên đấu giá trên
    // client đã đấu giá sau khi có người đặt giá mới
    BID,
    BID_RESULT,

    // Lấy tất cả các phiên đấu giá () Chưa sử dụng
    GET_ALL_AUCTIONS,
    GET_ALL_AUCTIONS_RESULT,

    // Update thông tin người dùng (tên, email) Chưa sử dụng
    UPDATE_USER,
    UPDATE_USER_RESULT,

    // Update mật khẩu  Chưa sử dụng
    CHANGE_PASSWORD,
    CHANGE_PASSWORD_RESULT,

    // Đăng xuất  Chưa sử dụng
    LOGOUT,
    LOGOUT_RESULT,

    // Đăng cập nhật giá mới nhất trên toàn
    // bộ client đang xem sản phẩm đó (dùng để đồng bộ giá mới nhất sau khi có người đặt giá mới)
    BID_UPDATE,

    ITEMS_UPDATE,


    SET_ALLOW,
    SET_ALLOW_RESULT,

    DELETE_ITEM,
    DELETE_ITEM_RESULT,

    RECHARGE_AMOUNT,
    RECHARGE_AMOUNT_RESULT
    ;

}