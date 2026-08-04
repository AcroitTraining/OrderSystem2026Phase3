package config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {
    private static final Properties properties = new Properties();

    static {
        try (InputStream input = Config.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                throw new RuntimeException("application.properties が見つかりません。");
            }
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String get(String key) {
        return properties.getProperty(key);
    }

    // ★ここから追加：よく使う値専用のgetter
    public static String getBaseUrl() {
        return get("base.url");
    }

    public static String getMqttHost() {
        return get("mqtt.host");
    }

    public static String getDbUrl() {
        return "jdbc:mysql://" + get("db.host") + ":" + get("db.port") + "/" + get("db.name")
                + "?useSSL=false&serverTimezone=Asia/Tokyo";
    }

    public static String getDbUser() {
        return get("db.user");
    }

    public static String getDbPassword() {
        return get("db.password");
    }
}