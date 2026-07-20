package cn.tengyuan.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ConfigUtil {

    private static final String CONFIG_PATH =
            System.getProperty("config.path", "../app.properties");

    public static String get(String key) {

        Properties properties = new Properties();

        try (FileInputStream fis =
                     new FileInputStream(CONFIG_PATH)) {

            properties.load(fis);

            return properties.getProperty(key);

        } catch (IOException e) {

            throw new RuntimeException("加载配置文件失败", e);

        }
    }

}