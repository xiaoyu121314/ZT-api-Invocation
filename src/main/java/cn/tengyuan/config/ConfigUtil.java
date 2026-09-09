package cn.tengyuan.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class ConfigUtil {

    private static final String CONFIG_PATH =
            System.getProperty("config.path", "../app.properties");

    public static String get(String key) {

        Properties properties = new Properties();

        // Properties.load(InputStream) 固定按 ISO-8859-1 解析，无法正确读取
        // “中天光纤”等中文配置值，因此显式使用 UTF-8 Reader。
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(CONFIG_PATH), StandardCharsets.UTF_8)) {

            properties.load(reader);

            return properties.getProperty(key);

        } catch (IOException e) {

            throw new RuntimeException("加载配置文件失败", e);

        }
    }

    /**
     * 读取必填配置项，并在缺失时给出明确的配置名称。
     *
     * @param key 配置项名称
     * @return 去除首尾空格后的配置值
     */
    public static String getRequired(String key) {
        String value = get(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("缺少必填配置项：" + key);
        }
        return value.trim();
    }

    /**
     * 读取可选字符串配置；未配置或配置为空时返回默认值。
     */
    public static String get(String key, String defaultValue) {
        String value = get(key);
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    /**
     * 读取布尔配置，支持标准 true/false 字符串。
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key);
        return value == null || value.trim().isEmpty()
                ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    /**
     * 读取整数配置；格式不正确时直接抛出异常，避免静默使用错误参数。
     */
    public static int getInt(String key, int defaultValue) {
        String value = get(key);
        return value == null || value.trim().isEmpty()
                ? defaultValue : Integer.parseInt(value.trim());
    }

}
