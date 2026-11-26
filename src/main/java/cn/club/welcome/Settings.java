package cn.club.welcome;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Component
public class Settings {
    final String baseUrl, secret, adminHash, adminUser, dbUrl, dbUser, dbPassword, contact;
    final boolean secure, trustProxy;
    final int retentionDays;
    public Settings(Environment spring) throws Exception {
        Map<String,String> values = new HashMap<>();
        Path path = Path.of(".env");
        if (Files.exists(path)) {
            for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                int equal = line.indexOf('=');
                if (!line.startsWith("#") && equal > 0)
                    values.put(line.substring(0,equal).trim(), line.substring(equal+1).trim());
            }
        }
        values.putAll(System.getenv());
        java.util.function.BiFunction<String,String,String> get = (key, fallback) ->
            spring.getProperty("club." + key.toLowerCase(Locale.ROOT).replace('_','-'), values.getOrDefault(key, fallback));
        baseUrl = get.apply("BASE_URL","https://example.com").replaceAll("/+$","");
        secret = get.apply("APP_SECRET","");
        adminHash = get.apply("ADMIN_PASSWORD_HASH","");
        adminUser = get.apply("ADMIN_USERNAME","admin");
        dbUrl = get.apply("DATABASE_URL","jdbc:mysql://127.0.0.1:3306/club_welcome?characterEncoding=UTF-8&connectionTimeZone=Asia/Shanghai");
        dbUser = get.apply("DATABASE_USERNAME","club_app");
        dbPassword = get.apply("DATABASE_PASSWORD","");
        if (!dbUrl.startsWith("jdbc:mysql:")) throw new IllegalStateException("DATABASE_URL 必须是 MySQL JDBC 地址");
        contact = get.apply("PRIVACY_CONTACT","请通过招新 QQ 群联系管理员");
        secure = Boolean.parseBoolean(get.apply("COOKIE_SECURE","true"));
        trustProxy = Boolean.parseBoolean(get.apply("TRUST_PROXY","false"));
        retentionDays = Integer.parseInt(get.apply("RETENTION_DAYS","180"));
        if (secret.length()<48 || !adminHash.matches("pbkdf2_sha256\\$600000\\$[0-9a-f]{32}\\$[0-9a-f]{64}"))
            throw new IllegalStateException("请先运行 java -jar target/welcome.jar --setup 生成密钥和密码摘要");
        if (baseUrl.startsWith("https://") && !secure)
            throw new IllegalStateException("HTTPS 必须启用 COOKIE_SECURE");
        if (!baseUrl.startsWith("https://") && !baseUrl.matches("http://(127\\.0\\.0\\.1|localhost):[0-9]+"))
            throw new IllegalStateException("非本机站点必须使用 HTTPS");
        if (retentionDays<1 || retentionDays>730) throw new IllegalStateException("保存天数需在 1–730 之间");
    }
}
