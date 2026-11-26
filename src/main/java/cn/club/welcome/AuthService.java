package cn.club.welcome;

import jakarta.servlet.http.*;
import org.springframework.stereotype.Service;
import java.util.*;
import java.time.Duration;
import org.springframework.http.ResponseCookie;

@Service
public class AuthService {
    final Settings settings; final Database db;
    public AuthService(Settings settings,Database db) { this.settings=settings;this.db=db; }
    String digest(String value) { return Crypto.digest(settings.secret,value); }
    public void rate(HttpServletRequest req,String scope,int max,int seconds) {
        String ip=req.getRemoteAddr();
        if(settings.trustProxy && Set.of("127.0.0.1","::1","0:0:0:0:0:0:0:1").contains(ip)) {
            String real=req.getHeader("X-Real-IP");
            if(real!=null&&!real.isBlank())ip=real;
        }
        String key=digest(scope+":"+ip);
        long now=System.currentTimeMillis()/1000;
        db.transaction(con->{
            Database.execute(con,"DELETE FROM limits WHERE expires < ?",now);
            var rows=Database.query(con,"SELECT count FROM limits WHERE `key`=?",key);
            if(!rows.isEmpty()&&((Number)rows.get(0).get("count")).intValue()>=max)
                throw new PortalError(429,"操作太频繁，请稍后再试。");
            Database.execute(con,"INSERT INTO limits VALUES (?,1,?) ON DUPLICATE KEY UPDATE count=count+1",key,now+seconds);
            return null;
        });
    }
    public Map<String,Object> member(HttpServletRequest req) {
        String bearer=req.getHeader("Authorization");
        if(bearer==null||!bearer.matches("Bearer [0-9a-f]{64}"))throw new PortalError(401,"报名凭证缺失或失效。");
        var rows=db.query("SELECT * FROM signups WHERE token_hash=?",digest(bearer.substring(7)));
        if(rows.isEmpty())throw new PortalError(401,"报名凭证已失效。");
        return rows.get(0);
    }
    public Map<String,Object> admin(HttpServletRequest req,boolean mutation) {
        String token="";
        if(req.getCookies()!=null)for(Cookie c:req.getCookies())if(c.getName().equals("club_session"))token=c.getValue();
        var rows=db.query("SELECT * FROM sessions WHERE token_hash=? AND expires>?",digest(token),System.currentTimeMillis()/1000);
        if(rows.isEmpty())throw new PortalError(401,"请先登录管理员账号。");
        var row=rows.get(0);
        if(mutation&&!Crypto.same((String)row.get("csrf"),Objects.toString(req.getHeader("X-CSRF-Token"),"")))
            throw new PortalError(403,"安全校验失败，请刷新后台。");
        return row;
    }
    String cookie(String token,int seconds) {
        return ResponseCookie.from("club_session",token).path("/api/admin").httpOnly(true).secure(settings.secure)
            .sameSite("Strict").maxAge(Duration.ofSeconds(seconds)).build().toString();
    }
}
