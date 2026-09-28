package mqtt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SenderTest {

    private MqttClient mockMqttClient;
    private Connection mockConnection;
    private PreparedStatement mockPsSelect;
    private PreparedStatement mockPsUpdate;
    private ResultSet mockResultSet;

    private Sender sender;

    @BeforeEach
    void setUp() throws Exception {
        // 各種モックの作成
        mockMqttClient = mock(MqttClient.class);
        mockConnection = mock(Connection.class);
        mockPsSelect = mock(PreparedStatement.class);
        mockPsUpdate = mock(PreparedStatement.class);
        mockResultSet = mock(ResultSet.class);

        // DB挙動の擬似設定
        when(mockConnection.prepareStatement(contains("SELECT"))).thenReturn(mockPsSelect);
        when(mockConnection.prepareStatement(contains("UPDATE"))).thenReturn(mockPsUpdate);
        when(mockPsSelect.executeQuery()).thenReturn(mockResultSet);

        // テスト対象のインスタンス化（モックを注入）
        sender = new Sender(
            mockMqttClient, 
            () -> mockConnection, 
            "http://localhost:8080/OrderStartServlet?tt="
        );
    }

    @Test
    void testCheckAndUpdateQRCodes_Success() throws Exception {
        // 1件データが見つかった状況を作る
        when(mockResultSet.next()).thenReturn(true, false); // 1回目true, 2回目false
        when(mockResultSet.getInt("table_id")).thenReturn(5);
        when(mockResultSet.getString("url_token")).thenReturn("token123");
        when(mockPsUpdate.executeUpdate()).thenReturn(1);

        // テスト実行
        sender.checkAndUpdateQRCodes();

        // 検証1: 正しいトピックとURLでMQTT送信されたか
        String expectedTopic = "epaper/5/status/URL_SENT";
        verify(mockMqttClient, times(1)).publish(eq(expectedTopic), any(MqttMessage.class));

        // 検証2: 対象卓のフラグ更新（UPDATE）が実行されたか
        verify(mockPsUpdate, times(1)).setInt(1, 5);
        verify(mockPsUpdate, times(1)).executeUpdate();
    }
}