package mqtt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import config.Config;
import dao.DBConnection;

public class Sender implements Runnable {

    private final String BASE_URL = Config.getBaseUrl() + "/OrderStartServlet?tt=";
    private MqttClient client;

    /**
     * MQTTクライアントの初期化・接続
     */
    private void setUpMqtt() throws MqttException {
        client = new MqttClient(Config.getMqttHost(), "Sender_Master", new MemoryPersistence());
        client.connect();
        System.out.println("📡 [MQTT] ブローカーに接続しました: " + Config.getMqttHost());
    }

    /**
     * topic: epaper/<tableId>/status/URL_SENT にURLをpublishする
     */
    private void sendUrl(int tableId, String fullUrl) {
        try {
            String topic = "epaper/" + tableId + "/status/URL_SENT";
            MqttMessage message = new MqttMessage(fullUrl.getBytes());
            client.publish(topic, message);
            System.out.println("📤 [Publish] topic=" + topic + " / url=" + fullUrl);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            setUpMqtt();
        } catch (MqttException e) {
            e.printStackTrace();
            return; // MQTT接続失敗なら起動中止
        }

        System.out.println("🚀 [Sender] 卓状態の監視を開始しました... (" + Config.getBaseUrl() + ")");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                checkAndUpdateQRCodes();
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                System.out.println("Senderを停止します。");
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void checkAndUpdateQRCodes() {
        String selectSql =
            "SELECT tm.table_id, ts.url_token " +
            "FROM table_master tm " +
            "JOIN table_sessions ts ON tm.table_id = ts.table_id " +
            "WHERE tm.update_flag = 1 " +
            "AND ts.session_status = 'inactive' " +
            "AND ts.session_id = ( " +
            "    SELECT MAX(session_id) " +
            "    FROM table_sessions " +
            "    WHERE table_id = tm.table_id " +
            ")";
        String resetFlagSql = "UPDATE table_master SET update_flag = 0 WHERE table_id = ?";

        try (Connection conn = DBConnection.getConnection()) {
            try (PreparedStatement psSelect = conn.prepareStatement(selectSql);
                 ResultSet rs = psSelect.executeQuery()) {

                int foundCount = 0;

                while (rs.next()) {
                    foundCount++;
                    int tableId = rs.getInt("table_id");
                    String newToken = rs.getString("url_token");
                    String fullUrl = BASE_URL + newToken;

                    System.out.println("🔍 [検知] 卓番=" + tableId + " / 新トークン=" + newToken);

                    sendUrl(tableId, fullUrl); // ★ここが変更点：直接呼び出し → MQTT publish

                    try (PreparedStatement psReset = conn.prepareStatement(resetFlagSql)) {
                        psReset.setInt(1, tableId);
                        int updated = psReset.executeUpdate();
                        System.out.println("🔄 [フラグリセット] 卓番=" + tableId + " / 更新件数=" + updated);
                    }
                }

                if (foundCount == 0) {
                    System.out.println("💤 [検知なし] 対象の卓なし");
                } else {
                    System.out.println("✅ [完了] 今回のポーリングで " + foundCount + " 件のQRを送信しました");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Sender sender = new Sender();
        new Thread(sender).start();
    }
}