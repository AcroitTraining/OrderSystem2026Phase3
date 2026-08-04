package mqtt;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Sender implements Runnable {

    private final String JDBC_URL = "jdbc:mysql://localhost:3306/order_management";
    private final String DB_USER = "order";
    private final String DB_PASS = "1234";

    // ★ 最新のIPアドレスを設定
    private final String BASE_URL = "http://172.19.72.36:8080/OrderSystem2026Phase1/OrderStartServlet?tt=";

    @Override
    public void run() {
        System.out.println("🚀 [Sender] 卓状態の監視を開始しました... (IP: 172.19.72.36)");

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
            "WHERE tm.update_flag = 1 AND ts.session_status = 'active'";

        String resetFlagSql = "UPDATE table_master SET update_flag = 0 WHERE table_id = ?";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return;
        }

        try (Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS)) {

            try (PreparedStatement psSelect = conn.prepareStatement(selectSql);
                 ResultSet rs = psSelect.executeQuery()) {

                while (rs.next()) {
                    int tableId = rs.getInt("table_id");
                    String newToken = rs.getString("url_token");

                    String fullUrl = BASE_URL + newToken;

                    System.out.println("🔔 [検知] 卓番 " + tableId + " の会計を検知。新URLを自動生成します。");

                    Receiver.generateQRCode(tableId, fullUrl);

                    try (PreparedStatement psReset = conn.prepareStatement(resetFlagSql)) {
                        psReset.setInt(1, tableId);
                        psReset.executeUpdate();
                    }
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