package mqtt;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import config.Config;
import qrcode.QrDisplay;

public class Receiver implements MqttCallback {

    private final int tableId;
    private MqttClient client;
    private final String subscribeTopic;

    public Receiver(int tableId) {
        this.tableId = tableId;
        this.subscribeTopic = "epaper/" + tableId + "/status/URL_SENT";
    }

    public void start() {
        try {
            client = new MqttClient(Config.getMqttHost(), "Receiver_Table_" + tableId, new MemoryPersistence());
            client.setCallback(this);
            client.connect();

            publishStatus("CONNECTED");
            client.subscribe(subscribeTopic);

            System.out.println("📡 卓番 " + tableId + " Receiver 起動完了 (待受: " + subscribeTopic + ")");

            while (true) {
                Thread.sleep(1000);
            }

        } catch (Exception e) {
            e.printStackTrace();
            publishStatus("ERROR");
        }
    }

    private void publishStatus(String event) {
        try {
            String topic = "epaper/" + tableId + "/status/" + event;
            MqttMessage message = new MqttMessage(new byte[0]);
            if (client != null && client.isConnected()) {
                client.publish(topic, message);
                System.out.println("[卓番 " + tableId + " → Sender] Publish: " + topic);
            }
        } catch (Exception e) {
            System.err.println("ステータス送信失敗: " + event);
            e.printStackTrace();
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String orderStartUrl = new String(message.getPayload());
        System.out.println("[卓番 " + tableId + "] 注文開始URL受信: " + orderStartUrl);

        publishStatus("URL_RECEIVED");

        generateQrCodeImage(orderStartUrl);
    }

    public void generateQrCodeImage(String orderStartUrl) {
        try {
            // ★ 開発中はエミュレータ(PNG保存)、実機は EpdQrDisplay に差し替え
            QrDisplay qrDisplay = new QrDisplay();
            String outputPath = "qr_table" + tableId + ".png";
            qrDisplay.runForTable(tableId, orderStartUrl);

            System.out.println("[卓番 " + tableId + "] QRコード画像生成完了: " + outputPath);

            publishStatus("QR_DISPLAYED");

        } catch (Exception e) {
            System.err.println("[卓番 " + tableId + "] エラーが発生しました: " + e.getMessage());
            publishStatus("ERROR");
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        System.out.println("卓番 " + tableId + " 接続切断");
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 使用しない予定
    }

    public static void main(String[] args) {
        int tableId = 1;

        if (args.length > 0) {
            try {
                tableId = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("引数は数字(卓番)で指定してください。デフォルトで1卓として起動します。");
            }
        }

        Receiver receiver = new Receiver(tableId);
        receiver.start();
    }
}