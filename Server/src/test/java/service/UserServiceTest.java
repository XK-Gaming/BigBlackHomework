package service;

import dao.DAOAuction_Items;
import dao.DAOItems;
import dao.DAOUser;
import model.DepositTransaction;
import model.Items.Item;
import model.Items.ItemType;
import model.User.Bidder;
import model.User.Seller;
import model.User.User;
import model.User.UserRole;
import model.auction.Auction;
import model.auction.AuctionStatus;
import model.auction.BidHistoryDTO;
import model.auction.BidTransaction;
import model.exception.BidRejectedException;
import model.exception.NotFoundException;
import model.exception.PersistenceException;
import model.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ## JUnit: test UserService xu ly nghiep vu truoc khi ghi DB hoac tra ket qua ve handler.
 */
class UserServiceTest {

    /**
     * ## Test dang nhap hop le: DAO tra user dung password thi service tra user cho handler.
     */
    @Test
    void loginReturnsUserWhenCredentialsMatch() throws Exception {
        User user = new Bidder("bidder1", "secret", "Bidder One", "bidder@example.com");
        FakeUserDao userDao = new FakeUserDao();
        userDao.user = user;
        UserService service = serviceWith(userDao, new FakeItemDao(null), new FakeAuctionDao(null));

        assertSame(user, service.loginAndGetUser("bidder1", "secret"));
    }

    /**
     * ## Test dang nhap sai: service nem UnauthorizedException truoc khi tra ve client.
     */
    @Test
    void loginThrowsUnauthorizedWhenDaoReturnsNoUser() throws Exception {
        UserService service = serviceWith(new FakeUserDao(), new FakeItemDao(null), new FakeAuctionDao(null));

        assertThrows(UnauthorizedException.class, () -> service.loginAndGetUser("bidder1", "wrong"));
    }

    /**
     * ## Test dang ky: username da ton tai thi service tra EXSITED va khong insert lan nua.
     */
    @Test
    void registerReturnsExistingWhenUsernameAlreadyExists() throws Exception {
        FakeUserDao userDao = new FakeUserDao();
        userDao.addUser(new Bidder("bidder1", "secret", "Bidder One", "bidder@example.com"));
        UserService service = serviceWith(userDao, new FakeItemDao(null), new FakeAuctionDao(null));

        Map<String, Object> response = service.register(
                new Bidder("bidder1", "other", "Duplicate", "other@example.com"));

        assertEquals("EXSITED", response.get("success"));
        assertEquals(0, userDao.insertCalls);
    }

    /**
     * ## Test dang ky: user moi duoc insert qua DAO va service tra TRUE.
     */
    @Test
    void registerInsertsNewUserAndReturnsTrue() throws Exception {
        FakeUserDao userDao = new FakeUserDao();
        UserService service = serviceWith(userDao, new FakeItemDao(null), new FakeAuctionDao(null));
        User newUser = new Bidder("bidder1", "secret", "Bidder One", "bidder@example.com");

        Map<String, Object> response = service.register(newUser);

        assertEquals("TRUE", response.get("success"));
        assertEquals(1, userDao.insertCalls);
        assertSame(newUser, userDao.insertedUser);
        assertSame(newUser, userDao.selectByUsernameOnly("bidder1"));
    }

    /**
     * ## Test dang san pham: service luu item truoc roi tao auction tu item da co databaseId.
     */
    @Test
    void createItemPersistsItemThenAuction() throws Exception {
        FakeItemDao itemDao = new FakeItemDao(null);
        FakeAuctionDao auctionDao = new FakeAuctionDao(null);
        UserService service = serviceWith(new FakeUserDao(), itemDao, auctionDao);
        Item item = item("seller", 500);
        item.setMinBid(50);
        item.setDatabaseId(0);

        service.creater_item(item);

        assertEquals(1, itemDao.insertCalls);
        assertEquals(1, auctionDao.insertCalls);
        assertSame(item, itemDao.insertedItem);
        assertSame(item, auctionDao.insertedItem);
        assertNotNull(auctionDao.insertedAuction);
        assertEquals("seller", auctionDao.insertedAuction.getSellerID());
        assertTrue(item.getDatabaseId() > 0);
    }

    /**
     * ## Test dang san pham: MinBid bat buoc lon hon 0 khi tao auction moi.
     */
    @Test
    void createItemRejectsMissingMinBid() throws Exception {
        FakeItemDao itemDao = new FakeItemDao(null);
        UserService service = serviceWith(new FakeUserDao(), itemDao, new FakeAuctionDao(null));
        Item item = item("seller", 500);
        item.setMinBid(0);

        PersistenceException exception = assertThrows(PersistenceException.class,
                () -> service.creater_item(item));

        assertTrue(exception.getMessage().contains("MinBid"));
        assertEquals(0, itemDao.insertCalls);
    }

    /**
     * ## Test dang san pham: MinBid khong duoc vuot qua 20% gia khoi diem.
     */
    @Test
    void createItemRejectsMinBidAboveTwentyPercentOfStartingPrice() throws Exception {
        FakeItemDao itemDao = new FakeItemDao(null);
        UserService service = serviceWith(new FakeUserDao(), itemDao, new FakeAuctionDao(null));
        Item item = item("seller", 500);
        item.setMinBid(101);

        PersistenceException exception = assertThrows(PersistenceException.class,
                () -> service.creater_item(item));

        assertTrue(exception.getMessage().contains("20%"));
        assertEquals(0, itemDao.insertCalls);
    }

    /**
     * ## Test select_items: admin thay ca item chua co status, bidder chi thay item da co status dau gia.
     */
    @Test
    void selectItemsFiltersUnapprovedItemsForNonAdminRoles() throws Exception {
        Item visible = item("seller", 100);
        visible.setAuctionStatus(AuctionStatus.OPEN);
        Item hidden = item("seller", 200);
        hidden.setDatabaseId(2);
        FakeItemDao itemDao = new FakeItemDao(null);
        itemDao.addItem(visible);
        itemDao.addItem(hidden);
        UserService service = serviceWith(new FakeUserDao(), itemDao, new FakeAuctionDao(null));

        assertEquals(2, service.select_items(UserRole.ADMIN).size());
        List<Item> bidderItems = service.select_items(UserRole.BIDDER);
        assertEquals(1, bidderItems.size());
        assertSame(visible, bidderItems.getFirst());
    }

    /**
     * ## Test item khong ton tai: processBid dung o buoc select item va nem NotFoundException.
     */
    @Test
    void processBidThrowsNotFoundWhenItemDoesNotExist() throws Exception {
        UserService service = serviceWith(new FakeUserDao(), new FakeItemDao(null), new FakeAuctionDao(null));

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> service.processBid("99", "bidder1", 120));

        assertEquals("item", exception.getResource());
    }

    /**
     * ## Test seller tu dat gia: service tu choi voi reason SELLER_BID truoc khi load auction.
     */
    @Test
    void processBidRejectsSellerBiddingOwnItem() throws Exception {
        Item item = item("seller", 100);
        FakeAuctionDao auctionDao = new FakeAuctionDao(null);
        UserService service = serviceWith(new FakeUserDao(), new FakeItemDao(item), auctionDao);

        BidRejectedException exception = assertThrows(BidRejectedException.class,
                () -> service.processBid("1", "seller", 120));

        assertEquals(BidRejectedException.Reason.SELLER_BID, exception.getReason());
        assertFalse(auctionDao.selectCalled);
    }

    /**
     * ## Test dat bid thap hon gia hien tai: service tra reason PRICE_TOO_LOW truoc khi luu DB.
     */
    @Test
    void processBidRejectsPriceLowerThanCurrentPrice() throws Exception {
        Item item = item("seller", 100);
        UserService service = serviceWith(new FakeUserDao(), new FakeItemDao(item), new FakeAuctionDao(null));

        BidRejectedException exception = assertThrows(BidRejectedException.class,
                () -> service.processBid("1", "bidder1", 100));

        assertEquals(BidRejectedException.Reason.PRICE_TOO_LOW, exception.getReason());
    }

    /**
     * ## Test MinBid: bid dau tien chi can cao hon gia hien tai, chua bi ep current + MinBid.
     */
    @Test
    void processBidAllowsFirstBidBelowMinBidIncrement() throws Exception {
        Item item = item("seller", 100);
        item.setMinBid(20);
        FakeItemDao itemDao = new FakeItemDao(item);
        itemDao.updateResult = 1;
        FakeAuctionDao auctionDao = new FakeAuctionDao(runningAuction(item));
        auctionDao.updateResult = 1;
        FakeUserDao userDao = new FakeUserDao();
        userDao.addUser(new Bidder("bidder1", "secret", "Bidder One", "bidder1@example.com", 1_000));
        UserService service = serviceWith(userDao, itemDao, auctionDao);

        service.processBid("1", "bidder1", 110);

        assertEquals(110, item.getCurrentHighestPrice(), 0.001);
        assertEquals(1, itemDao.updateCalls);
        assertEquals(1, auctionDao.updateCalls);
    }

    /**
     * ## Test MinBid: tu bid thu hai tro di, gia phai dat toi thieu current price + MinBid.
     */
    @Test
    void processBidRejectsSecondBidBelowCurrentPlusMinBid() throws Exception {
        Item item = item("seller", 100);
        item.setMinBid(20);
        Auction auction = runningAuction(item);
        auction.setLeadingBidder("bidder0");
        auction.setBidHistory(List.of(new BidTransaction("bid-1", "bidder0", 100, Instant.now())));
        FakeUserDao userDao = new FakeUserDao();
        userDao.addUser(new Bidder("bidder1", "secret", "Bidder One", "bidder1@example.com", 1_000));
        UserService service = serviceWith(userDao, new FakeItemDao(item), new FakeAuctionDao(auction));

        BidRejectedException exception = assertThrows(BidRejectedException.class,
                () -> service.processBid("1", "bidder1", 119));

        assertEquals(BidRejectedException.Reason.PRICE_TOO_LOW, exception.getReason());
        assertTrue(exception.getMessage().contains("MinBid"));
    }

    /**
     * ## Test auction khong ton tai: item co nhung auction null thi service nem NotFoundException resource auction.
     */
    @Test
    void processBidThrowsNotFoundWhenAuctionDoesNotExist() throws Exception {
        Item item = item("seller", 100);
        UserService service = serviceWith(new FakeUserDao(), new FakeItemDao(item), new FakeAuctionDao(null));

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> service.processBid("1", "bidder1", 120));

        assertEquals("auction", exception.getResource());
    }

    /**
     * ## Test auction da dong/chua chay: service tu choi reason NOT_RUNNING truoc khi tao transaction DB.
     */
    @Test
    void processBidRejectsAuctionThatIsNotRunning() throws Exception {
        Instant now = Instant.parse("2026-05-18T10:00:00Z");
        Item item = item("seller", 100, now.minusSeconds(3600), now.minusSeconds(60));
        Auction auction = new Auction("auction-1", item, "seller", Instant.now());
        auction.setStatus(AuctionStatus.FINISHED);
        UserService service = serviceWith(
                new FakeUserDao(),
                new FakeItemDao(item),
                new FakeAuctionDao(auction),
                1,
                Clock.fixed(now, ZoneOffset.UTC));

        BidRejectedException exception = assertThrows(BidRejectedException.class,
                () -> service.processBid("1", "bidder1", 120));

        assertEquals(BidRejectedException.Reason.NOT_RUNNING, exception.getReason());
    }

    /**
     * ## Test anti-sniping: bid hop le trong 60 giay cuoi thi keo dai gio ket thuc them 90 giay.
     */
    @Test
    void processBidExtendsAuctionEndTimeInsideLastMinute() throws Exception {
        Instant now = Instant.parse("2026-05-18T10:00:00Z");
        Instant originalEnd = now.plusSeconds(30);
        Item item = item("seller", 100, now.minusSeconds(3600), originalEnd);
        item.setMinBid(20);
        FakeItemDao itemDao = new FakeItemDao(item);
        itemDao.updateResult = 1;
        FakeAuctionDao auctionDao = new FakeAuctionDao(runningAuction(item));
        auctionDao.updateResult = 1;
        FakeUserDao userDao = new FakeUserDao();
        userDao.addUser(new Bidder("bidder1", "secret", "Bidder One", "bidder1@example.com", 1_000));
        UserService service = serviceWith(
                userDao,
                itemDao,
                auctionDao,
                1,
                Clock.fixed(now, ZoneOffset.UTC));

        Map<String, Object> result = service.processBid("1", "bidder1", 150);

        assertEquals(originalEnd.plusSeconds(UserService.ANTI_SNIPING_EXTENSION_SECONDS), item.getAuctionEndTime());
        assertSame(item, result.get("item"));
        assertEquals(1, itemDao.updateCalls);
        assertEquals(1, auctionDao.updateCalls);
    }

    /**
     * ## Test anti-sniping: bid ngoai 60 giay cuoi thi khong doi thoi gian ket thuc.
     */
    @Test
    void processBidDoesNotExtendAuctionEndTimeOutsideLastMinute() throws Exception {
        Instant now = Instant.parse("2026-05-18T10:00:00Z");
        Instant originalEnd = now.plusSeconds(61);
        Item item = item("seller", 100, now.minusSeconds(3600), originalEnd);
        FakeItemDao itemDao = new FakeItemDao(item);
        itemDao.updateResult = 1;
        FakeAuctionDao auctionDao = new FakeAuctionDao(runningAuction(item));
        auctionDao.updateResult = 1;
        FakeUserDao userDao = new FakeUserDao();
        userDao.addUser(new Bidder("bidder1", "secret", "Bidder One", "bidder1@example.com", 1_000));
        UserService service = serviceWith(
                userDao,
                itemDao,
                auctionDao,
                1,
                Clock.fixed(now, ZoneOffset.UTC));

        service.processBid("1", "bidder1", 150);

        assertEquals(originalEnd, item.getAuctionEndTime());
        assertEquals(1, itemDao.updateCalls);
        assertEquals(1, auctionDao.updateCalls);
    }

    /**
     * ## Test anti-sniping: hai bid lien tiep cung luc khong lam cong don 2 lan neu bid thu hai khong con nam trong 60 giay cuoi.
     */
    @Test
    void processBidDoesNotDoubleExtendForImmediateConsecutiveBids() throws Exception {
        Instant now = Instant.parse("2026-05-18T10:00:00Z");
        Instant originalEnd = now.plusSeconds(30);
        Instant firstExtendedEnd = originalEnd.plusSeconds(UserService.ANTI_SNIPING_EXTENSION_SECONDS);
        Item item = item("seller", 100, now.minusSeconds(3600), originalEnd);
        FakeItemDao itemDao = new FakeItemDao(item);
        itemDao.updateResult = 1;
        FakeAuctionDao auctionDao = new FakeAuctionDao(runningAuction(item));
        auctionDao.updateResult = 1;
        FakeUserDao userDao = new FakeUserDao();
        userDao.addUser(new Bidder("bidder1", "secret", "Bidder One", "bidder1@example.com", 1_000));
        userDao.addUser(new Bidder("bidder2", "secret", "Bidder Two", "bidder2@example.com", 1_000));
        UserService service = serviceWith(
                userDao,
                itemDao,
                auctionDao,
                1,
                Clock.fixed(now, ZoneOffset.UTC));

        service.processBid("1", "bidder1", 150);
        service.processBid("1", "bidder2", 200);

        assertEquals(firstExtendedEnd, item.getAuctionEndTime());
        assertEquals(200, item.getCurrentHighestPrice(), 0.001);
        assertEquals(2, itemDao.updateCalls);
        assertEquals(2, auctionDao.updateCalls);
    }

    /**
     * ## Test bid co leader cu: bidder cu duoc hoan tien, bidder moi bi tru tien va auction doi leader.
     */
    @Test
    void processBidRefundsPreviousLeaderAndChargesNewLeader() throws Exception {
        Instant now = Instant.parse("2026-05-18T10:00:00Z");
        Item item = item("seller", 150, now.minusSeconds(3600), now.plusSeconds(3600));
        item.setMinBid(20);
        Auction auction = runningAuction(item);
        auction.setLeadingBidder("bidder0");
        auction.setBidHistory(List.of(new BidTransaction("bid-1", "bidder0", 150, now.minusSeconds(60))));
        FakeItemDao itemDao = new FakeItemDao(item);
        itemDao.updateResult = 1;
        FakeAuctionDao auctionDao = new FakeAuctionDao(auction);
        auctionDao.updateResult = 1;
        FakeUserDao userDao = new FakeUserDao();
        userDao.addUser(new Bidder("bidder0", "secret", "Old Leader", "old@example.com", 300));
        userDao.addUser(new Bidder("bidder1", "secret", "New Leader", "new@example.com", 1_000));
        UserService service = serviceWith(
                userDao,
                itemDao,
                auctionDao,
                1,
                Clock.fixed(now, ZoneOffset.UTC));

        Map<String, Object> result = service.processBid("1", "bidder1", 200);

        assertEquals(450, userDao.selectByUsernameOnly("bidder0").getBalance(), 0.001);
        assertEquals(800, userDao.selectByUsernameOnly("bidder1").getBalance(), 0.001);
        assertEquals("bidder0", result.get("refundedBidderId"));
        assertEquals(450.0, result.get("refundedBalance"));
        assertSame(userDao.selectByUsernameOnly("bidder0"), result.get("refundedUser"));
        assertEquals(200, item.getCurrentHighestPrice(), 0.001);
        Auction latestAuction = (Auction) result.get("latestAuction");
        assertEquals("bidder1", latestAuction.getLeadingBidder());
        assertEquals(2, latestAuction.getBidHistory().size());
    }

    /**
     * ## Test self-outbid: bidder dang dan dau duoc tinh lai balance tu gia cu roi tru gia moi, khong gui refund notification.
     */
    @Test
    void processBidRechargesAndChargesSameLeaderWithoutRefundNotification() throws Exception {
        Instant now = Instant.parse("2026-05-18T10:00:00Z");
        Item item = item("seller", 150, now.minusSeconds(3600), now.plusSeconds(3600));
        item.setMinBid(20);
        Auction auction = runningAuction(item);
        auction.setLeadingBidder("bidder1");
        auction.setBidHistory(List.of(new BidTransaction("bid-1", "bidder1", 150, now.minusSeconds(60))));
        FakeItemDao itemDao = new FakeItemDao(item);
        itemDao.updateResult = 1;
        FakeAuctionDao auctionDao = new FakeAuctionDao(auction);
        auctionDao.updateResult = 1;
        FakeUserDao userDao = new FakeUserDao();
        userDao.addUser(new Bidder("bidder1", "secret", "Bidder One", "bidder1@example.com", 850));
        UserService service = serviceWith(
                userDao,
                itemDao,
                auctionDao,
                1,
                Clock.fixed(now, ZoneOffset.UTC));

        Map<String, Object> result = service.processBid("1", "bidder1", 200);

        assertEquals(800, userDao.selectByUsernameOnly("bidder1").getBalance(), 0.001);
        assertFalse(result.containsKey("refundedBidderId"));
        assertFalse(result.containsKey("refundedBalance"));
        assertEquals(200, item.getCurrentHighestPrice(), 0.001);
        Auction latestAuction = (Auction) result.get("latestAuction");
        assertEquals("bidder1", latestAuction.getLeadingBidder());
        assertEquals(2, latestAuction.getBidHistory().size());
    }

    /**
     * ## Test bid: user khong ton tai thi rollback va tra NotFoundException resource user.
     */
    @Test
    void processBidThrowsNotFoundWhenBidderDoesNotExist() throws Exception {
        FakeConnectionState connection = new FakeConnectionState(1);
        Item item = item("seller", 100);
        FakeAuctionDao auctionDao = new FakeAuctionDao(runningAuction(item));
        UserService service = serviceWith(new FakeUserDao(), new FakeItemDao(item), auctionDao, connection);

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> service.processBid("1", "missing", 150));

        assertEquals("user", exception.getResource());
        assertEquals(1, connection.rollbackCalls);
        assertEquals(0, connection.commitCalls);
    }

    /**
     * ## Test bid: so du khong du thi service tu choi va rollback transaction.
     */
    @Test
    void processBidRejectsInsufficientBalanceAndRollsBack() throws Exception {
        FakeConnectionState connection = new FakeConnectionState(1);
        Item item = item("seller", 100);
        FakeUserDao userDao = new FakeUserDao();
        userDao.addUser(new Bidder("bidder1", "secret", "Bidder One", "bidder@example.com", 120));
        UserService service = serviceWith(userDao, new FakeItemDao(item), new FakeAuctionDao(runningAuction(item)), connection);

        BidRejectedException exception = assertThrows(BidRejectedException.class,
                () -> service.processBid("1", "bidder1", 150));

        assertEquals(BidRejectedException.Reason.PRICE_TOO_LOW, exception.getReason());
        assertEquals(1, connection.rollbackCalls);
        assertEquals(0, connection.commitCalls);
    }

    /**
     * ## Test transaction: update auction fail thi rollback va khong update item.
     */
    @Test
    void processBidRollsBackWhenAuctionUpdateFails() throws Exception {
        FakeConnectionState connection = new FakeConnectionState(1);
        Item item = item("seller", 100);
        FakeItemDao itemDao = new FakeItemDao(item);
        itemDao.updateResult = 1;
        FakeAuctionDao auctionDao = new FakeAuctionDao(runningAuction(item));
        auctionDao.updateResult = 0;
        FakeUserDao userDao = new FakeUserDao();
        userDao.addUser(new Bidder("bidder1", "secret", "Bidder One", "bidder@example.com", 1_000));
        UserService service = serviceWith(userDao, itemDao, auctionDao, connection);

        BidRejectedException exception = assertThrows(BidRejectedException.class,
                () -> service.processBid("1", "bidder1", 150));

        assertEquals(BidRejectedException.Reason.PERSIST, exception.getReason());
        assertEquals(1, auctionDao.updateCalls);
        assertEquals(0, itemDao.updateCalls);
        assertEquals(1, connection.rollbackCalls);
        assertEquals(0, connection.commitCalls);
    }

    /**
     * ## Test transaction: update item fail sau update auction thi van rollback.
     */
    @Test
    void processBidRollsBackWhenItemUpdateFails() throws Exception {
        FakeConnectionState connection = new FakeConnectionState(1);
        Item item = item("seller", 100);
        FakeItemDao itemDao = new FakeItemDao(item);
        itemDao.updateResult = 0;
        FakeAuctionDao auctionDao = new FakeAuctionDao(runningAuction(item));
        auctionDao.updateResult = 1;
        FakeUserDao userDao = new FakeUserDao();
        userDao.addUser(new Bidder("bidder1", "secret", "Bidder One", "bidder@example.com", 1_000));
        UserService service = serviceWith(userDao, itemDao, auctionDao, connection);

        BidRejectedException exception = assertThrows(BidRejectedException.class,
                () -> service.processBid("1", "bidder1", 150));

        assertEquals(BidRejectedException.Reason.PERSIST, exception.getReason());
        assertEquals(1, auctionDao.updateCalls);
        assertEquals(1, itemDao.updateCalls);
        assertEquals(1, connection.rollbackCalls);
        assertEquals(0, connection.commitCalls);
    }

    /**
     * ## Test lay auction theo itemId: service nap item truoc roi moi lay auction tu DAO.
     */
    @Test
    void getAuctionByItemIdLoadsItemThenAuction() throws Exception {
        Item item = item("seller", 100);
        Auction auction = new Auction("auction-1", item, "seller", Instant.now());
        FakeAuctionDao auctionDao = new FakeAuctionDao(auction);
        UserService service = serviceWith(new FakeUserDao(), new FakeItemDao(item), auctionDao);

        assertSame(auction, service.getAuctionByItemId("1"));
        assertSame(item, auctionDao.selectedItem);
    }

    /**
     * ## Test getAllAuctions: auction thieu item se duoc hydrate bang itemDAO theo itemId.
     */
    @Test
    void getAllAuctionsHydratesAuctionItemsWhenMissing() throws Exception {
        Item item = item("seller", 100);
        Auction auction = new StaticStatusAuction("auction-1", item, "seller", Instant.now(), AuctionStatus.RUNNING);
        auction.setItemId(1);
        auction.setItem(null);
        FakeAuctionDao auctionDao = new FakeAuctionDao(null);
        auctionDao.addAuction(auction);
        UserService service = serviceWith(new FakeUserDao(), new FakeItemDao(item), auctionDao);

        List<Auction> auctions = service.getAllAuctions();

        assertEquals(1, auctions.size());
        assertSame(item, auctions.getFirst().getItem());
    }

    /**
     * ## Test bid history: service tra ban copy cua lich su bid theo itemId.
     */
    @Test
    void getBidHistoryReturnsCopyOfAuctionHistory() throws Exception {
        Item item = item("seller", 100);
        Auction auction = runningAuction(item);
        auction.setBidHistory(List.of(new BidTransaction("bid-1", "bidder1", 150, Instant.now())));
        UserService service = serviceWith(new FakeUserDao(), new FakeItemDao(item), new FakeAuctionDao(auction));

        ArrayList<BidTransaction> history = service.getBidHistory("1");

        assertEquals(1, history.size());
        history.clear();
        assertEquals(1, auction.getBidHistory().size());
    }

    /**
     * ## Test update status: service load item/auction roi ghi status moi qua DAO.
     */
    @Test
    void updateAuctionStatusPersistsStatusWhenItemAndAuctionExist() throws Exception {
        Item item = item("seller", 100);
        Auction auction = runningAuction(item);
        FakeAuctionDao auctionDao = new FakeAuctionDao(auction);
        UserService service = serviceWith(new FakeUserDao(), new FakeItemDao(item), auctionDao);

        service.updateAuctionStatus("auction-1", "1", "FINISHED");

        assertEquals(1, auctionDao.statusUpdateCalls);
        assertEquals(AuctionStatus.FINISHED, auctionDao.updatedStatus);
        assertEquals(AuctionStatus.FINISHED, auction.getStatus());
    }

    /**
     * ## Test duyet auction: choose=true mo phien, choose=false xoa status cho phien.
     */
    @Test
    void setAllowAppliesOpenOrNullStatus() throws Exception {
        Item item = item("seller", 100);
        Auction auction = runningAuction(item);
        FakeAuctionDao auctionDao = new FakeAuctionDao(auction);
        UserService service = serviceWith(new FakeUserDao(), new FakeItemDao(item), auctionDao);

        Auction opened = service.setAllow("1", "true");
        assertSame(auction, opened);
        assertEquals(AuctionStatus.OPEN, auctionDao.updatedStatus);

        Auction cleared = service.setAllow("1", "false");
        assertSame(auction, cleared);
        assertEquals(null, auctionDao.updatedStatus);
    }

    /**
     * ## Test xoa san pham: service lay item theo id roi goi DAO delete.
     */
    @Test
    void deleteItemDelegatesToItemDaoWhenItemExists() throws Exception {
        Item item = item("seller", 100);
        FakeItemDao itemDao = new FakeItemDao(item);
        itemDao.deleteResult = 1;
        UserService service = serviceWith(new FakeUserDao(), itemDao, new FakeAuctionDao(null));

        assertEquals(1, service.DeleteItem(1));
        assertEquals(1, itemDao.deleteCalls);
        assertSame(item, itemDao.deletedItem);
    }

    /**
     * ## Test thanh toan: seller nhan gia thang va auction duoc chuyen sang PAID.
     */
    @Test
    void payHandlerTransfersAmountToSellerAndMarksAuctionPaid() throws Exception {
        Item item = item("seller", 500);
        Auction auction = runningAuction(item);
        FakeAuctionDao auctionDao = new FakeAuctionDao(auction);
        FakeUserDao userDao = new FakeUserDao();
        userDao.addUser(new Seller("seller", "secret", "Seller", "seller@example.com", 0));
        UserService service = serviceWith(userDao, new FakeItemDao(item), auctionDao);

        assertTrue(service.PayHandler(item));
        assertEquals(500, userDao.selectByUsernameOnly("seller").getBalance(), 0.001);
        assertEquals(AuctionStatus.PAID, auctionDao.updatedStatus);
    }

    /**
     * ## Test nap tien: service tao giao dich PENDING va luu lich su deposit.
     */
    @Test
    void rechargeAmountAddsPendingDepositAndPersistsHistory() throws Exception {
        FakeUserDao userDao = new FakeUserDao();
        User bidder = new Bidder("bidder1", "secret", "Bidder One", "bidder@example.com", 100);
        userDao.addUser(bidder);
        UserService service = serviceWith(userDao, new FakeItemDao(null), new FakeAuctionDao(null));

        assertTrue(service.rechargeAmount("bidder1", 250));

        assertEquals(1, bidder.getDepositHistory().size());
        DepositTransaction deposit = bidder.getDepositHistory().getFirst();
        assertEquals("bidder1", deposit.getUsername());
        assertEquals(250, deposit.getAmount(), 0.001);
        assertEquals("PENDING", deposit.getStatus());
        assertEquals(1, userDao.depositHistoryUpdateCalls);
    }

    /**
     * ## Test duyet nap tien: transaction PENDING thanh APPROVED va balance duoc cong.
     */
    @Test
    void approveDepositApprovesPendingTransactionAndAddsBalance() throws Exception {
        FakeUserDao userDao = new FakeUserDao();
        User bidder = new Bidder("bidder1", "secret", "Bidder One", "bidder@example.com", 100);
        DepositTransaction deposit = deposit("tx-1", "bidder1", 250);
        bidder.setDepositHistory(new ArrayList<>(List.of(deposit)));
        userDao.addUser(bidder);
        UserService service = serviceWith(userDao, new FakeItemDao(null), new FakeAuctionDao(null));

        assertTrue(service.approveDeposit("bidder1", "tx-1"));

        assertEquals("APPROVED", deposit.getStatus());
        assertEquals(350, bidder.getBalance(), 0.001);
        assertEquals(1, userDao.depositHistoryUpdateCalls);
    }

    /**
     * ## Test tu choi nap tien: transaction PENDING thanh REJECTED va khong cong balance.
     */
    @Test
    void rejectDepositRejectsPendingTransactionWithoutAddingBalance() throws Exception {
        FakeUserDao userDao = new FakeUserDao();
        User bidder = new Bidder("bidder1", "secret", "Bidder One", "bidder@example.com", 100);
        DepositTransaction deposit = deposit("tx-1", "bidder1", 250);
        bidder.setDepositHistory(new ArrayList<>(List.of(deposit)));
        userDao.addUser(bidder);
        UserService service = serviceWith(userDao, new FakeItemDao(null), new FakeAuctionDao(null));

        assertTrue(service.rejectDeposit("bidder1", "tx-1"));

        assertEquals("REJECTED", deposit.getStatus());
        assertEquals(100, bidder.getBalance(), 0.001);
        assertEquals(1, userDao.depositHistoryUpdateCalls);
    }

    /**
     * ## Test xoa lich su nap tien: transaction dung id bi remove va lich su duoc luu lai.
     */
    @Test
    void deleteDepositHistoryRemovesMatchingTransaction() throws Exception {
        FakeUserDao userDao = new FakeUserDao();
        User bidder = new Bidder("bidder1", "secret", "Bidder One", "bidder@example.com", 100);
        bidder.setDepositHistory(new ArrayList<>(List.of(deposit("tx-1", "bidder1", 250))));
        userDao.addUser(bidder);
        UserService service = serviceWith(userDao, new FakeItemDao(null), new FakeAuctionDao(null));

        assertTrue(service.deleteDepositHistory("bidder1", "tx-1"));

        assertTrue(bidder.getDepositHistory().isEmpty());
        assertEquals(1, userDao.depositHistoryUpdateCalls);
    }

    /**
     * ## Test pending deposits: service tra danh sach pending tu DAO.
     */
    @Test
    void getPendingDepositsDelegatesToUserDao() throws Exception {
        FakeUserDao userDao = new FakeUserDao();
        DepositTransaction pending = deposit("tx-1", "bidder1", 250);
        userDao.pendingDeposits = List.of(pending);
        UserService service = serviceWith(userDao, new FakeItemDao(null), new FakeAuctionDao(null));

        assertSame(pending, service.getPendingDeposits().getFirst());
    }

    /**
     * ## Test lich su bidder: service map WINNING/OUTBID/WON/LOST theo status va leader.
     */
    @Test
    void getBidderHistoryMapsAuctionOutcomeStatuses() throws Exception {
        Instant now = Instant.parse("2026-05-18T10:00:00Z");
        FakeAuctionDao auctionDao = new FakeAuctionDao(null);
        auctionDao.addAuction(historyAuction(1, "Running Win", AuctionStatus.RUNNING, "bidder1",
                List.of(new BidTransaction("bid-1", "bidder1", 150, now))));
        auctionDao.addAuction(historyAuction(2, "Running Lost", AuctionStatus.RUNNING, "bidder2",
                List.of(
                        new BidTransaction("bid-2", "bidder1", 120, now),
                        new BidTransaction("bid-3", "bidder2", 160, now.plusSeconds(10)))));
        auctionDao.addAuction(historyAuction(3, "Finished Win", AuctionStatus.FINISHED, "bidder1",
                List.of(new BidTransaction("bid-4", "bidder1", 180, now))));
        auctionDao.addAuction(historyAuction(4, "Finished Lost", AuctionStatus.FINISHED, "bidder2",
                List.of(
                        new BidTransaction("bid-5", "bidder1", 150, now),
                        new BidTransaction("bid-6", "bidder2", 200, now.plusSeconds(10)))));
        UserService service = serviceWith(new FakeUserDao(), new FakeItemDao(null), auctionDao);

        List<BidHistoryDTO> history = service.getBidderHistory("bidder1");

        assertEquals(4, history.size());
        assertEquals("WINNING", history.get(0).getStatus());
        assertEquals("OUTBID", history.get(1).getStatus());
        assertEquals("WON", history.get(2).getStatus());
        assertEquals("LOST", history.get(3).getStatus());
        assertEquals(120, history.get(1).getMyHighestBid(), 0.001);
        assertEquals(160, history.get(1).getCurrentHighestPrice(), 0.001);
    }

    /**
     * ## Test lich su bidder: getStatus tu tinh lai RUNNING khi endTime con han, nhung service khong persist status qua DAO.
     */
    @Test
    void getBidderHistoryTreatsExtendedAuctionAsRunningWhenStoredStatusIsFinished() throws Exception {
        Instant now = Instant.now();
        Item item = item("seller", 100, now.minusSeconds(3600), now.plusSeconds(60));
        item.setDatabaseId(5);
        item.setName("Extended Auction");
        Auction auction = new Auction("auction-5", item, "seller", now.minusSeconds(3600));
        auction.setItemId(5);
        auction.setStatus(AuctionStatus.FINISHED);
        auction.setLeadingBidder("bidder1");
        auction.setBidHistory(List.of(new BidTransaction("bid-extended", "bidder1", 150, now.minusSeconds(10))));

        FakeAuctionDao auctionDao = new FakeAuctionDao(null);
        auctionDao.addAuction(auction);
        UserService service = serviceWith(
                new FakeUserDao(),
                new FakeItemDao(null),
                auctionDao,
                1,
                Clock.fixed(now, ZoneOffset.UTC));

        List<BidHistoryDTO> history = service.getBidderHistory("bidder1");

        assertEquals(1, history.size());
        assertEquals("WINNING", history.get(0).getStatus());
        assertEquals(AuctionStatus.RUNNING, auction.getRawStatus());
        assertEquals(0, auctionDao.statusUpdateCalls);
    }

    /**
     * ## Test update user: field khong hop le thi service tra false va khong goi DAO update.
     */
    @Test
    void updateUserRejectsUnsupportedField() throws Exception {
        FakeUserDao userDao = new FakeUserDao();
        userDao.user = new Bidder("bidder1", "old", "Old Name", "old@example.com");
        UserService service = serviceWith(userDao, new FakeItemDao(null), new FakeAuctionDao(null));

        assertFalse(service.updateUser("bidder1", "unknown", "value"));
        assertFalse(userDao.updated);
    }

    /**
     * ## Test doi mat khau: sai mat khau cu thi service tra false va khong luu DAO.
     */
    @Test
    void changePasswordRejectsWrongOldPassword() throws Exception {
        FakeUserDao userDao = new FakeUserDao();
        userDao.user = new Bidder("bidder1", "old", "Bidder", "bidder@example.com");
        UserService service = serviceWith(userDao, new FakeItemDao(null), new FakeAuctionDao(null), 0);

        assertFalse(service.changePassword("bidder1", "wrong", "new"));
        assertFalse(userDao.updated);
    }

    /**
     * ## Test doi mat khau: dung mat khau cu thi SQL update thanh cong va service tra true.
     */
    @Test
    void changePasswordReturnsTrueWhenSqlUpdateAffectsRow() throws Exception {
        FakeUserDao userDao = new FakeUserDao();
        userDao.user = new Bidder("bidder1", "old", "Bidder", "bidder@example.com");
        UserService service = serviceWith(userDao, new FakeItemDao(null), new FakeAuctionDao(null), 1);

        assertTrue(service.changePassword("bidder1", "old", "new"));
        assertFalse(userDao.updated);
    }

    private UserService serviceWith(FakeUserDao userDao, FakeItemDao itemDao, FakeAuctionDao auctionDao) throws Exception {
        return serviceWith(userDao, itemDao, auctionDao, 1);
    }

    private UserService serviceWith(FakeUserDao userDao, FakeItemDao itemDao, FakeAuctionDao auctionDao,
                                    int updateCount) {
        return serviceWith(userDao, itemDao, auctionDao, updateCount, Clock.systemUTC());
    }

    private UserService serviceWith(FakeUserDao userDao, FakeItemDao itemDao, FakeAuctionDao auctionDao,
                                    int updateCount, Clock clock) {
        return new UserService(userDao, itemDao, auctionDao, () -> connectionWithUpdateCount(updateCount), clock);
    }

    private UserService serviceWith(FakeUserDao userDao, FakeItemDao itemDao, FakeAuctionDao auctionDao,
                                    FakeConnectionState connectionState) {
        return new UserService(userDao, itemDao, auctionDao, connectionState::connection);
    }

    private Item item(String sellerId, double currentPrice) {
        Instant now = Instant.now();
        return item(sellerId, currentPrice, now.minusSeconds(60), now.plusSeconds(60));
    }

    private Item item(String sellerId, double currentPrice, Instant start, Instant end) {
        Item item = new Item(
                "Item",
                "Description",
                currentPrice,
                start,
                end,
                sellerId,
                ItemType.ART,
                "image.png");
        item.setDatabaseId(1);
        item.setCurrentHighestPrice(currentPrice);
        return item;
    }

    private Auction runningAuction(Item item) {
        return new StaticStatusAuction("auction-1", item, item.getSellerId(), Instant.now(), AuctionStatus.RUNNING);
    }

    private Auction historyAuction(long itemId, String itemName, AuctionStatus status, String leadingBidder,
                                   List<BidTransaction> history) {
        Instant now = Instant.now();
        Instant end = status == AuctionStatus.FINISHED ? now.minusSeconds(60) : now.plusSeconds(3600);
        Item item = item("seller", 100, now.minusSeconds(3600), end);
        item.setDatabaseId((int) itemId);
        item.setName(itemName);
        Auction auction = new StaticStatusAuction("auction-" + itemId, item, "seller", Instant.now(), status);
        auction.setItemId(itemId);
        auction.setLeadingBidder(leadingBidder);
        auction.setBidHistory(history);
        return auction;
    }

    private DepositTransaction deposit(String id, String username, double amount) {
        DepositTransaction deposit = new DepositTransaction(username, amount);
        deposit.setId(id);
        return deposit;
    }

    private Connection connectionWithUpdateCount(int updateCount) {
        return new FakeConnectionState(updateCount).connection();
    }

    private PreparedStatement preparedStatementWithUpdateCount(int updateCount) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("executeUpdate".equals(method.getName())) {
                return updateCount;
            }
            return defaultValue(method);
        };
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                handler);
    }

    private Object defaultValue(Method method) {
        Class<?> returnType = method.getReturnType();
        if (returnType == Void.TYPE) {
            return null;
        }
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Integer.TYPE) {
            return 0;
        }
        if (returnType == Long.TYPE) {
            return 0L;
        }
        if (returnType == Double.TYPE) {
            return 0d;
        }
        if (returnType == Float.TYPE) {
            return 0f;
        }
        if (returnType == Short.TYPE) {
            return (short) 0;
        }
        if (returnType == Byte.TYPE) {
            return (byte) 0;
        }
        if (returnType == Character.TYPE) {
            return (char) 0;
        }
        return null;
    }

    private static final class StaticStatusAuction extends Auction {
        private StaticStatusAuction(String id, Item item, String sellerId, Instant createdAt, AuctionStatus status) {
            super(id, item, sellerId, createdAt);
            setStatus(status);
        }

        @Override
        public AuctionStatus getStatus() {
            return getRawStatus();
        }
    }

    /**
     * ## Test fake DAO user: mo phong login, update profile va change password.
     */
    private static final class FakeUserDao extends DAOUser {
        private final Map<String, User> users = new HashMap<>();
        private User user;
        private boolean updated;
        private int insertCalls;
        private User insertedUser;
        private int depositHistoryUpdateCalls;
        private List<DepositTransaction> pendingDeposits = List.of();

        private void addUser(User user) {
            users.put(user.getUsername(), user);
            if (this.user == null) {
                this.user = user;
            }
        }

        @Override
        public int Insert(User user) {
            insertCalls++;
            insertedUser = user;
            users.put(user.getUsername(), user);
            if (this.user == null) {
                this.user = user;
            }
            return 1;
        }

        @Override
        public User selectByUsername(String username, String password) {
            User knownUser = users.get(username);
            if (knownUser != null && knownUser.getPassword().equals(password)) {
                return knownUser;
            }
            if (user != null && user.getUsername().equals(username) && user.getPassword().equals(password)) {
                return user;
            }
            return null;
        }

        @Override
        public User selectByUsernameOnly(String username) {
            User knownUser = users.get(username);
            if (knownUser != null) {
                return knownUser;
            }
            return user != null && user.getUsername().equals(username) ? user : null;
        }

        // =========================================================================
        // 🌟 1. FIX CHÍ MẠNG: Override hàm nhận Connection dùng trong Transaction
        // =========================================================================
        @Override
        public User selectByUsernameOnly(Connection con, String username) throws java.sql.SQLException {
            // Gọi lại chính hàm giả lập nội bộ bên trên để lấy từ Memory Map, bỏ qua Connection gốc
            return selectByUsernameOnly(username);
        }

        @Override
        public int UpdateBalance(String username, double newBalance) {
            User knownUser = selectByUsernameOnly(username);
            if (knownUser != null) {
                knownUser.setBalance(newBalance);
                return 1;
            }
            return 0;
        }

        // =========================================================================
        // 🌟 2. FIX ĐỒNG BỘ: Override luôn bản UpdateBalance có Connection
        // =========================================================================
        @Override
        public int UpdateBalance(Connection con, String username, double newBalance) throws java.sql.SQLException {
            return UpdateBalance(username, newBalance);
        }

        @Override
        public void Update(User user) {
            this.updated = true;
            this.user = user;
            this.users.put(user.getUsername(), user); // Cập nhật luôn vào bản đồ giả lập
        }

        // =========================================================================
        // 🌟 3. FIX ĐỒNG BỘ: Override hàm Update chạy trong Transaction
        // =========================================================================
        @Override
        public int Update(Connection con, User user) throws java.sql.SQLException {
            Update(user);
            return 1;
        }

        @Override
        public int UpdateDepositHistory(String username, List<DepositTransaction> history) {
            depositHistoryUpdateCalls++;
            User knownUser = selectByUsernameOnly(username);
            if (knownUser != null) {
                knownUser.setDepositHistory(history);
                return 1;
            }
            return 0;
        }

        @Override
        public List<DepositTransaction> getAllPendingDeposits() {
            return pendingDeposits;
        }
    }

    /**
     * ## Test fake DAO item: tra item cho processBid/getAuctionByItemId va chan ghi DB that.
     */
    private static final class FakeItemDao extends DAOItems {
        private final Map<String, Item> itemsById = new HashMap<>();
        private final ArrayList<Item> allItems = new ArrayList<>();
        private Item item;
        private int insertResult = 1;
        private int insertCalls;
        private Item insertedItem;
        private int updateResult = -1;
        private int updateCalls;
        private Item updatedItem;
        private int deleteResult = 0;
        private int deleteCalls;
        private Item deletedItem;

        private FakeItemDao(Item item) {
            this.item = item;
            addItem(item);
        }

        private void addItem(Item item) {
            if (item == null) {
                return;
            }
            this.item = this.item == null ? item : this.item;
            itemsById.put(String.valueOf(item.getDatabaseId()), item);
            if (!allItems.contains(item)) {
                allItems.add(item);
            }
        }

        @Override
        public int Insert(Item item) {
            insertCalls++;
            insertedItem = item;
            if (item.getDatabaseId() == 0) {
                item.setDatabaseId(100 + insertCalls);
            }
            addItem(item);
            return insertResult;
        }

        @Override
        public Item selectById(String itemId) {
            Item found = itemsById.get(itemId);
            return found != null ? found : item;
        }

        @Override
        public Item selectById(Connection con, String itemId) {
            return selectById(itemId);
        }

        @Override
        public ArrayList<Item> selectAll() {
            return new ArrayList<>(allItems);
        }

        @Override
        public int Delete(Item item) {
            deleteCalls++;
            deletedItem = item;
            return deleteResult;
        }

        @Override
        public int Update(Connection con, Item item) throws SQLException {
            if (updateResult < 0) {
                throw new AssertionError("Unit test khong duoc ghi DB that.");
            }
            this.updatedItem = item;
            this.updateCalls++;
            return updateResult;
        }
    }

    /**
     * ## Test fake DAO auction: tra auction cho processBid va ghi nhan item duoc service truyen vao.
     */
    private static final class FakeAuctionDao extends DAOAuction_Items {
        private final Map<Long, Auction> auctionsByItemId = new HashMap<>();
        private final ArrayList<Auction> allAuctions = new ArrayList<>();
        private Auction auction;
        private boolean selectCalled;
        private Item selectedItem;
        private int insertResult = 1;
        private int insertCalls;
        private Auction insertedAuction;
        private Item insertedItem;
        private int updateResult = -1;
        private int updateCalls;
        private Auction updatedAuction;
        private AuctionStatus updatedStatus;
        private int statusUpdateCalls;

        private FakeAuctionDao(Auction auction) {
            this.auction = auction;
            addAuction(auction);
        }

        private void addAuction(Auction auction) {
            if (auction == null) {
                return;
            }
            this.auction = this.auction == null ? auction : this.auction;
            if (auction.getItem() != null) {
                auctionsByItemId.put((long) auction.getItem().getDatabaseId(), auction);
            }
            if (auction.getItemId() != 0) {
                auctionsByItemId.put(auction.getItemId(), auction);
            }
            if (!allAuctions.contains(auction)) {
                allAuctions.add(auction);
            }
        }

        @Override
        public int Insert(Auction auction, Item item) {
            insertCalls++;
            insertedAuction = auction;
            insertedItem = item;
            addAuction(auction);
            return insertResult;
        }

        @Override
        public Auction selectByItemId(Item item) {
            this.selectCalled = true;
            this.selectedItem = item;
            Auction found = item == null ? null : auctionsByItemId.get((long) item.getDatabaseId());
            return found != null ? found : auction;
        }

        @Override
        public Auction selectByItemId(Connection con, Item item) {
            return selectByItemId(item);
        }

        @Override
        public List<Auction> selectAll() {
            return new ArrayList<>(allAuctions);
        }

        @Override
        public int Update(Connection con, Auction auction, int itemId, String bidderId, Double price) {
            if (updateResult < 0) {
                throw new AssertionError("Unit test khong duoc ghi DB that.");
            }
            this.updatedAuction = auction;
            this.updateCalls++;
            return updateResult;
        }

        @Override
        public void Update_Status(Auction auction, Item item1, AuctionStatus status) {
            statusUpdateCalls++;
            updatedAuction = auction;
            insertedItem = item1;
            updatedStatus = status;
            if (auction != null) {
                auction.setStatus(status);
            }
        }

    }

    /**
     * ## Test fake connection: ghi nhan commit/rollback/close ma khong can database.
     */
    private final class FakeConnectionState {
        private final int updateCount;
        private int commitCalls;
        private int rollbackCalls;
        private int closeCalls;
        private Boolean autoCommit;

        private FakeConnectionState(int updateCount) {
            this.updateCount = updateCount;
        }

        private Connection connection() {
            InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                case "prepareStatement" -> preparedStatementWithUpdateCount(updateCount);
                case "commit" -> {
                    commitCalls++;
                    yield null;
                }
                case "rollback" -> {
                    rollbackCalls++;
                    yield null;
                }
                case "close" -> {
                    closeCalls++;
                    yield null;
                }
                case "setAutoCommit" -> {
                    autoCommit = (Boolean) args[0];
                    yield null;
                }
                case "getAutoCommit" -> autoCommit == null || autoCommit;
                case "isClosed" -> closeCalls > 0;
                default -> defaultValue(method);
            };
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    handler);
        }
    }
}
