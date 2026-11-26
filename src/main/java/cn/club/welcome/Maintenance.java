package cn.club.welcome;

import org.springframework.core.env.StandardEnvironment;
import java.io.Console;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class Maintenance {
    public static void run(String[] args) throws Exception {
        Settings settings=new Settings(new StandardEnvironment());
        Database db=new Database(settings);
        if(args[0].equals("--prune")) { db.prune();System.out.println("已清理到期资料。");return; }
        if(args[0].equals("--backup")) {
            throw new IllegalArgumentException("MySQL 版本请使用 mysqldump --single-transaction 备份，详见 README.md；旧 SQLite 备份命令不再适用。");
        }
        if(args[0].equals("--reset-password")) {
            Console console=System.console();if(console==null)throw new IllegalStateException("请在系统终端运行以隐藏密码输入");
            char[] pass=console.readPassword("新密码（8～64 位，包含大写、小写字母和数字）："),confirm=console.readPassword("再次输入：");
            String hash;
            try {
                if(!Crypto.validNewPassword(pass)||!Arrays.equals(pass,confirm))throw new IllegalArgumentException("密码须8～64 位，仅含大写、小写字母和数字且三类都有，两次输入须一致");
                hash=Crypto.hash(pass);
            }finally{Arrays.fill(pass,'\0');Arrays.fill(confirm,'\0');}
            Path env=Path.of(".env"),temporary=Path.of(".env.reset.tmp");
            List<String> lines=new ArrayList<>(Files.readAllLines(env));lines.removeIf(s->s.startsWith("ADMIN_PASSWORD_HASH="));lines.add("ADMIN_PASSWORD_HASH="+hash);
            Files.write(temporary,lines,StandardOpenOption.CREATE_NEW);
            if(FileSystems.getDefault().supportedFileAttributeViews().contains("posix")){
                var old=Files.readAttributes(env,PosixFileAttributes.class);
                var view=Files.getFileAttributeView(temporary,PosixFileAttributeView.class);
                view.setOwner(old.owner());view.setGroup(old.group());view.setPermissions(old.permissions());
            }
            Files.move(temporary,env,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            db.execute("DELETE FROM sessions");
            console.printf("密码已更新、管理员会话已清空。请重启网站服务。%n");return;
        }
        throw new IllegalArgumentException("未知维护命令");
    }
    private Maintenance() {}
}
