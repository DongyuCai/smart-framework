package org.axe.util;

import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 配置文件工具类
 * Created by CaiDongYu on 2016/4/8.
 */
public final class PropsUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropsUtil.class);

    private PropsUtil() {}
    
    /**
     * 加载属性文件
     */
    public static Properties loadProps(String fileName) {
        Properties props = new Properties();
        InputStream is = null;
        try {
            is = Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName);
            if (is != null) {
                props.load(is);
            }
        } catch (Exception e) {
            LOGGER.error("load properties file [" + fileName + "] failure", e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    LOGGER.error("close input stream failure ", e);
                }
            }
        }
        return props;
    }

    /**
     * 获取字符型属性（默认值为空字符串）
     */
    public static String getString(Properties props, String key) {
        return getString(props, key, "");
    }

    /**
     * 获取字符型属性（可以指定默认值）
     */
    public static String getString(Properties props, String key, String defaultValue) {
        String value = defaultValue;
        if (props.containsKey(key)) {
            value = props.getProperty(key);
        }
        return value;
    }

    /**
     * 获取属性值（默认值为 0）
     */
    public static int getInt(Properties props, String key) {
        return getInt(props, key, 0);
    }

    /**
     * 获取属性值（可以指定默认值）
     */
    public static int getInt(Properties props, String key, int defaultValue) {
        int value = defaultValue;
        if (props.containsKey(key)) {
            value = CastUtil.castInteger(props.getProperty(key));
        }
        return value;
    }
    
    public static long getLong(Properties props, String key) {
        return getLong(props, key, 0l);
    }

    /**
     * 获取属性值（可以指定默认值）
     */
    public static long getLong(Properties props, String key, long defaultValue) {
        long value = defaultValue;
        if (props.containsKey(key)) {
            value = CastUtil.castLong(props.getProperty(key));
        }
        return value;
    }


    /**
     * 获取属性值（默认值为 false）
     */
    public static boolean getBoolean(Properties props, String key) {
        return getBoolean(props, key, false);
    }

    /**
     * 获取属性值（可以指定默认值）
     */
    public static boolean getBoolean(Properties props, String key, boolean defaultValue) {
        boolean value = defaultValue;
        if (props.containsKey(key)) {
            value = CastUtil.castBoolean(props.getProperty(key));
        }
        return value;
    }
}