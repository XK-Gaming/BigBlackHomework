package utils;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import model.auction.BidTransaction;

import java.lang.reflect.Type;
import java.time.Instant;

/**
 * Utility class để cấu hình Gson với custom TypeAdapter cho Instant và BidTransaction
 */
public class GsonUtils {
    
    /**
     * Precondition: Không có.
     * Postcondition: Method trả về Gson đã đăng ký adapter cho Instant và BidTransaction.
     */
    public static Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
                .registerTypeAdapter(BidTransaction.class, new BidTransactionTypeAdapter())
                .create();
    }
    
    /**
     * Custom TypeAdapter cho java.time.Instant
     * Serialize: Instant -> epoch seconds (long)
     * Deserialize: epoch seconds -> Instant
     */
    public static class InstantTypeAdapter extends TypeAdapter<Instant> {
        /**
         * Precondition: out là JsonWriter đang mở; value có thể null.
         * Postcondition: Ghi null hoặc epochSecond của Instant ra JSON.
         * Method không trả về giá trị.
         */
        @Override
        public void write(JsonWriter out, Instant value) throws java.io.IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.getEpochSecond());
            }
        }

        /**
         * Precondition: in là JsonReader đang trỏ tới null hoặc epochSecond.
         * Postcondition: Method trả về Instant tương ứng, hoặc null nếu token là JSON null.
         */
        @Override
        public Instant read(JsonReader in) throws java.io.IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return Instant.ofEpochSecond(in.nextLong());
        }
    }
    
    /**
     * Custom TypeAdapter cho BidTransaction
     */
    public static class BidTransactionTypeAdapter extends TypeAdapter<BidTransaction> {
        /**
         * Precondition: out là JsonWriter đang mở; value có thể null.
         * Postcondition: Ghi BidTransaction thành object JSON gồm id, Usernamebidder, amount, bidTime.
         * Method không trả về giá trị.
         */
        @Override
        public void write(JsonWriter out, BidTransaction value) throws java.io.IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            
            out.beginObject();
            out.name("id").value(value.getId());
            out.name("Usernamebidder").value(value.getBidder());
            out.name("amount").value(value.getAmount());
            out.name("bidTime").value(value.getBidTime().getEpochSecond());
            out.endObject();
        }

        /**
         * Precondition: in là JsonReader đang trỏ tới object JSON của BidTransaction hoặc null.
         * Postcondition: Method trả về BidTransaction được tạo lại từ JSON, hoặc null nếu token null.
         */
        @Override
        public BidTransaction read(JsonReader in) throws java.io.IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            
            String id = null;
            String bidder = null;
            double amount = 0;
            long epochSeconds = 0;
            
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                switch (name) {
                    case "id":
                        id = in.nextString();
                        break;
                    case "Usernamebidder":
                        bidder = in.nextString();
                        break;
                    case "amount":
                        amount = in.nextDouble();
                        break;
                    case "bidTime":
                        epochSeconds = in.nextLong();
                        break;
                    default:
                        in.skipValue();
                }
            }
            in.endObject();
            
            return new BidTransaction(id, bidder, amount, Instant.ofEpochSecond(epochSeconds));
        }
    }
}
