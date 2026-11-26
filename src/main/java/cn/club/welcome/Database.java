package cn.club.welcome;

import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.*;
import java.util.*;

@Component
public class Database {
    final Settings settings;
    final Object lock=new Object();
    public Database(Settings settings) throws Exception {
        this.settings=settings;
        Class.forName("com.mysql.cj.jdbc.Driver");
        try(Connection con=open(); Statement stmt=con.createStatement()) {
            String schema=new String(Database.class.getResourceAsStream("/schema-mysql.sql").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            for(String sql:schema.split(";")) if(!sql.isBlank()) stmt.execute(sql);
        }
        prune();
    }
    Connection open() throws SQLException {
        return DriverManager.getConnection(settings.dbUrl,settings.dbUser,settings.dbPassword);
    }
    public List<Map<String,Object>> query(String sql,Object... args) {
        try(Connection con=open()) { return query(con,sql,args); }
        catch(SQLException e) { throw new PortalError(503,"数据库暂时不可用，请稍后重试。"); }
    }
    static List<Map<String,Object>> query(Connection con,String sql,Object... args) {
        try(PreparedStatement stmt=con.prepareStatement(sql)) {
            for(int i=0;i<args.length;i++)stmt.setObject(i+1,args[i]);
            try(ResultSet rs=stmt.executeQuery()) {
                List<Map<String,Object>> rows=new ArrayList<>();
                while(rs.next()) {
                    Map<String,Object> row=new LinkedHashMap<>();
                    for(int i=1;i<=rs.getMetaData().getColumnCount();i++)row.put(rs.getMetaData().getColumnLabel(i),rs.getObject(i));
                    rows.add(row);
                }
                return rows;
            }
        } catch(SQLException e) { throw new PortalError(503,"数据库暂时不可用，请稍后重试。"); }
    }
    public int execute(String sql,Object... args) {
        try(Connection con=open()) { return execute(con,sql,args); }
        catch(SQLException e) { throw new PortalError(503,"数据库暂时不可用，请稍后重试。"); }
    }
    static int execute(Connection con,String sql,Object... args) {
        try(PreparedStatement stmt=con.prepareStatement(sql)) {
            for(int i=0;i<args.length;i++)stmt.setObject(i+1,args[i]);
            return stmt.executeUpdate();
        } catch(SQLException e) { throw new PortalError(503,"数据库暂时不可用，请稍后重试。"); }
    }
    interface Work<T> { T run(Connection con); }
    <T> T transaction(Work<T> work) {
        synchronized(lock) {
            try(Connection con=open()) {
                con.setAutoCommit(false);
                try { T result=work.run(con);con.commit();return result; }
                catch(RuntimeException e) { con.rollback();throw e; }
            } catch(SQLException e) { throw new PortalError(503,"数据库暂时不可用，请稍后重试。"); }
        }
    }
    @Scheduled(fixedDelay=3600000)
    public void prune() {
        long now=System.currentTimeMillis()/1000;
        execute("DELETE FROM signups WHERE created_at < ?",now-settings.retentionDays*86400L);
        execute("DELETE FROM sessions WHERE expires < ?",now);
        execute("DELETE FROM limits WHERE expires < ?",now);
    }
}
