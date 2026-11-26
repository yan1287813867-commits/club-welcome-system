package cn.club.welcome;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    final AuthService auth;final Database db;final Settings settings;final PortalService portal;
    public AdminController(AuthService auth,Database db,Settings settings,PortalService portal) {
        this.auth=auth;this.db=db;this.settings=settings;this.portal=portal;
    }
    @PostMapping("/login") public ResponseEntity<?> login(HttpServletRequest req,@RequestBody JsonNode body) {
        auth.rate(req,"login",8,900);
        if (!body.path("password").isTextual() || body.path("password").asText().length()>256) throw new PortalError(400,"密码格式不正确。");
        String password=body.path("password").asText(),username=PortalService.text(body,"username",80,true);
        boolean valid=Crypto.verify(password,settings.adminHash);
        if(!valid||!Crypto.same(username,settings.adminUser))throw new PortalError(401,"账号或密码不正确。");
        String token=Crypto.random(32),csrf=Crypto.random(32);
        db.execute("DELETE FROM sessions WHERE expires<?",System.currentTimeMillis()/1000);
        db.execute("INSERT INTO sessions VALUES (?,?,?)",auth.digest(token),csrf,System.currentTimeMillis()/1000+28800);
        return ResponseEntity.ok().header("Set-Cookie",auth.cookie(token,28800)).body(Map.of("csrf",csrf));
    }
    @GetMapping("/session") public Map<String,Object> session(HttpServletRequest req) { return Map.of("csrf",auth.admin(req,false).get("csrf")); }
    @PostMapping("/logout") public ResponseEntity<?> logout(HttpServletRequest req) {
        var row=auth.admin(req,true);db.execute("DELETE FROM sessions WHERE token_hash=?",row.get("token_hash"));
        return ResponseEntity.ok().header("Set-Cookie",auth.cookie("",0)).body(Map.of("ok",true));
    }
    @GetMapping("/signups") public Map<String,Object> signups(HttpServletRequest req,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="1")String page) {
        auth.admin(req,false);
        int number;
        try { number=Math.max(1,Math.min(100000,Integer.parseInt(page))); }
        catch(NumberFormatException e) { throw new PortalError(400,"页码不正确。"); }
        return portal.dashboard(q,number,false);
    }
    @GetMapping("/export") public ResponseEntity<byte[]> export(HttpServletRequest req,@RequestParam(defaultValue="")String q) {
        auth.admin(req,false);
        return ResponseEntity.ok().header("Content-Type","text/csv; charset=utf-8").header("Content-Disposition","attachment; filename=\"club-signups.csv\"").body(portal.csv(q));
    }
    @DeleteMapping("/signups/{id}") public Map<String,Object> delete(HttpServletRequest req,@PathVariable String id) {
        auth.admin(req,true);
        if(db.execute("DELETE FROM signups WHERE id=?",id)==0)throw new PortalError(404,"报名记录不存在。");
        return Map.of("deleted",true);
    }

    @PostMapping("/signups/bulk-delete")
    public Map<String,Object> bulkDelete(HttpServletRequest req,@RequestBody JsonNode body) {
        auth.admin(req,true);
        JsonNode values=body.path("ids");
        if(!values.isArray() || values.isEmpty() || values.size()>300)
            throw new PortalError(400,"请选择 1–300 条报名记录。");
        LinkedHashSet<String> ids=new LinkedHashSet<>();
        for(JsonNode value:values) {
            if(!value.isTextual() || !value.asText().matches("[0-9A-F]{12}") || !ids.add(value.asText()))
                throw new PortalError(400,"报名编号无效或重复。");
        }
        String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
        return db.transaction(con->{
            long found=((Number)Database.query(con,"SELECT count(*) n FROM signups WHERE id IN ("+placeholders+")",ids.toArray()).get(0).get("n")).longValue();
            if(found!=ids.size())throw new PortalError(409,"部分记录已不存在，请刷新后重新选择；本次未删除任何记录。");
            int removed=Database.execute(con,"DELETE FROM signups WHERE id IN ("+placeholders+")",ids.toArray());
            return PortalService.map("deleted",removed,"ids",new ArrayList<>(ids));
        });
    }
}
