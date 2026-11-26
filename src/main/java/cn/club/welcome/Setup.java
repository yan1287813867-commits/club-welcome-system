package cn.club.welcome;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;

public final class Setup {
    public static void create(boolean local) throws Exception {
        Path file=Path.of(".env");
        if(Files.exists(file))throw new IllegalStateException(".env 已存在，已停止以避免覆盖，请先备份。");
        Console console=System.console();
        if(console==null)throw new IllegalStateException("请在 Windows Terminal / PowerShell 或服务器 SSH 终端执行初始化，不要使用 IDE 输出窗口。");
        String username=console.readLine("管理员账号（默认 admin）：").strip();
        if(username.isEmpty())username="admin";
        if(!username.matches("[A-Za-z0-9_-]{1,80}"))throw new IllegalArgumentException("账号格式不正确");
        char[] password=console.readPassword("管理员密码（8～64 位，包含大写、小写字母和数字）：");
        char[] confirm=console.readPassword("再次输入密码：");
        String hash;
        try {
            if(!Crypto.validNewPassword(password)||!Arrays.equals(password,confirm))
                throw new IllegalArgumentException("密码须8～64 位，仅含大写字母、小写字母和数字，且三类都有；两次输入须一致。");
            hash=Crypto.hash(password);
        } finally { Arrays.fill(password,'\0');Arrays.fill(confirm,'\0'); }
        String content=String.join("\n",
            "# 密钥由初始化工具生成，请勿公开或提交到 Git；值为原文，不加引号。",
            "BASE_URL="+(local?"http://127.0.0.1:8080":"https://example.com"),
            "ADMIN_USERNAME="+username,"ADMIN_PASSWORD_HASH="+hash,"APP_SECRET="+Crypto.random(48),
            "DATABASE_URL=jdbc:mysql://127.0.0.1:3306/club_welcome?characterEncoding=UTF-8&connectionTimeZone=Asia/Shanghai",
            "DATABASE_USERNAME=club_app","DATABASE_PASSWORD=",
            "COOKIE_SECURE="+!local,"TRUST_PROXY="+!local,"RETENTION_DAYS=180",
            "PRIVACY_CONTACT=请通过招新 QQ 群联系管理员，提供报名编号申请查询、更正或删除",""
        );
        Files.writeString(file,content,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        if(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
            Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-------"));
        console.printf("已生成 .env，没有保存明文密码。启动：java -jar target/welcome.jar%n");
    }
    private Setup() {}
}
