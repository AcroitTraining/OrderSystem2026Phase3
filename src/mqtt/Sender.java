package mqtt;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalTime;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class Sender implements MqttCallback {

    private MqttClient client;

    // ★ 最新の Wi-Fi IP (172.19.72.36) を設定
    private final String urlBase = "http://172.19.72.36:8080/OrderSystem2026Phase1/OrderStartServlet?tt=";

    // DB接続情報
    private static final String DB_HOST = "localhost:3306";
    private static final String DB_NAME = "order_management";
    private static final String DB_USER = "root";
    private static final String DB_PASS = ""; 

    // 営業時間の設定
    private static final int OPEN_HOUR = 9;
    private static final int OPEN_MINUTE = 0;
    private static final int CLOSE_HOUR = 21;
    private static final int CLOSE_MINUTE = 0;

    private boolean[][] status = new boolean[5][5];
    private String lastState = "";

    public int timeToMinutes(int hour, int minute) {
        return 60 * hour + minute;
    }

    public Connection databaseConnector() throws Exception {
        String url = "jdbc:mysql://" + DB_HOST + "/" + DB_NAME + "?useSSL=false&serverTimezone=Asia/Tokyo";
        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection(url, DB_USER, DB_PASS);
    }

    public void setTablesOpened() {
        String sql = "UPDATE table_master SET opening_flag = 1, update_flag = 1";
        try (Connection conn = databaseConnector();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
            System.out.println("[DB] 開店処理: 全卓の opening_flag / update_flag を 1 に更新しました");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void getUpdatedTables() {
        String sql = "SELECT tm.table_id, ts.url_token " +
                     "FROM table_master tm " +
                     "JOIN table_sessions ts ON tm.table_id = ts.table_id " +
                     "WHERE tm.update_flag = 1 " +
                     "  AND ts.session_id = (" +
                     "      SELECT MAX(session_id) " +
                     "      FROM table_sessions " +
                     "      WHERE table_id = tm.table_id" +
                     "  )";

        try (Connection conn = databaseConnector();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                int tableId = rs.getInt("table_id");
                String urlToken = rs.getString("url_token");
                String orderStartUrl = urlBase + urlToken;

                System.out.println("[更新検知] 卓番: " + tableId + " → URL送信開始");
                sendUrl(tableId, orderStartUrl);
                resetUpdateFlag(tableId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void resetUpdateFlag(int tableId) {
        String sql = "UPDATE table_master SET update_flag = 0 WHERE table_id = ?";
        try (Connection conn = databaseConnector();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, tableId);
            stmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setTablesClosed() {
        String sql = "UPDATE table_master SET opening_flag = 0";
        try (Connection conn = databaseConnector();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
            System.out.println("[DB] 閉店処理: 全卓の opening_flag を 0 に更新しました");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendUrl(int tableId, String orderStartUrl) {
        try {
            String topic = "epaper/" + tableId + "/status/URL_SENT";
            MqttMessage message = new MqttMessage(orderStartUrl.getBytes());
            client.publish(topic, message);

            status[tableId][1] = true;
            System.out.println("[Sender → 卓番 " + tableId + "] URL送信完了: " + topic);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void start() {
        try {
            String broker = System.getenv("MQTT_BROKER");
            if (broker == null) {
                broker = "tcp://localhost:1883";
            }

            client = new MqttClient(broker, "Sender_Master");
            client.setCallback(this);
            client.connect();

            client.subscribe("epaper/+/status/#");
            System.out.println("Sender 起動完了（全卓のレスポンスを待受中）");

            int openMinutes = timeToMinutes(OPEN_HOUR, OPEN_MINUTE);
            int closeMinutes = timeToMinutes(CLOSE_HOUR, CLOSE_MINUTE);

            while (true) {
                LocalTime now = LocalTime.now();
                int currentMinutes = timeToMinutes(now.getHour(), now.getMinute());

                if (currentMinutes == openMinutes) {
                    if (!"OPENED".equals(lastState)) {
                        setTablesOpened();
                        getUpdatedTables();
                        lastState = "OPENED";
                    }
                } else if (currentMinutes > openMinutes && currentMinutes < closeMinutes) {
                    getUpdatedTables();
                    lastState = "OPENING";
                } else if (currentMinutes >= closeMinutes) {
                    if (!"CLOSED".equals(lastState)) {
                        setTablesClosed();
                        lastState = "CLOSED";
                    }
                }

                Thread.sleep(30000);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String[] parts = topic.split("/");
        if (parts.length >= 4) {
            int tableId = Integer.parseInt(parts[1]);
            String event = parts[3];

            System.out.println("[Sender受信] 卓番 " + tableId + " からの通知: " + event);

            switch (event) {
                case "CONNECTED":
                    status[tableId][0] = true;
                    break;
                case "URL_RECEIVED":
                    status[tableId][2] = true;
                    break;
                case "QR_DISPLAYED":
                    status[tableId][3] = true;
                    System.out.println("卓番 " + tableId + " のQR表示を確認しました。");
                    break;
                case "ERROR":
                    status[tableId][4] = true;
                    System.err.println("卓番 " + tableId + " でエラーが発生しました。再送を検討します。");
                    break;
            }
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        System.out.println("Sender 接続切断");
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {}

    public static void main(String[] args) {
        Sender sender = new Sender();
        sender.start();
    }
}