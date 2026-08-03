package mqtt;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import qrcode.QrDisplay;

public class Receiver implements MqttCallback {

    private String broker;
    private int tableId;
    private MqttClient client;

    // トピック定義 (epaper/<tableId>/status/<event>)
    private String subscribeTopic; // epaper/<tableId>/status/URL_SENT

    public Receiver(int tableId) {
        this.tableId = tableId;

        this.broker = System.getenv("MQTT_BROKER");
        if (this.broker == null) {
            this.broker = "tcp://localhost:1883";
        }

        // SenderからのURL送信イベントを待機するトピック
        this.subscribeTopic = "epaper/" + tableId + "/status/URL_SENT";
    }

    public void start() {
        try {
            client = new MqttClient(broker, "Receiver_Table_" + tableId);
            client.setCallback(this);
            client.connect();

            // Senderへ接続完了を通知 (CONNECTED)
            publishStatus("CONNECTED");

            // URL送信待ち用トピックのサブスクライブ
            client.subscribe(subscribeTopic);

            System.out.println("卓番 " + tableId + " Receiver 起動完了 (待受: " + subscribeTopic + ")");

            // 常駐プログラム用の無限ループ
            while (true) {
                Thread.sleep(1000);
            }

        } catch (Exception e) {
            e.printStackTrace();
            publishStatus("ERROR");
        }
    }

    // ステータス返信用 publish メソッド
    private void publishStatus(String event) {
        try {
            String topic = "epaper/" + tableId + "/status/" + event;
            MqttMessage message = new MqttMessage(new byte[0]); // ペイロードは空でOK
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
        // メッセージを受信した際の処理
        String orderStartUrl = new String(message.getPayload());
        System.out.println("[卓番 " + tableId + "] 注文開始URL受信: " + orderStartUrl);

        // ① 送信元へ URL_RECEIVED を返信
        publishStatus("URL_RECEIVED");

        // ② 受信したURLからQRコード画像を生成
        generateQrCodeImage(orderStartUrl);
    }

    /**
     * 注文開始URLをQRコード画像化し、結果に応じてトピックを発行する
     */
    public void generateQrCodeImage(String orderStartUrl) {
        try {
            QrDisplay qrDisplay = new QrDisplay();
            String outputPath = "qr_table" + tableId + ".png";
            
            // QrDisplayによる画像生成実行（バリデーションや例外チェックはQrDisplay内で行われる）
            qrDisplay.run(orderStartUrl, outputPath);

            System.out.println("[卓番 " + tableId + "] QRコード画像生成完了: " + outputPath);

            // 成功したら QR_DISPLAYED を送信
            publishStatus("QR_DISPLAYED");

        } catch (Exception e) {
            System.err.println("[卓番 " + tableId + "] エラーが発生しました: " + e.getMessage());
            
            // 設計書[URL再送用]: 各種例外が発生した場合は ERROR を送信
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

    // 起動用 main メソッド
    public static void main(String[] args) {
        int tableId = 1; // デフォルトは 1卓

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