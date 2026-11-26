package cn.club.welcome;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PublicController {
    final PortalService portal; final AuthService auth; final Database db; final Settings settings;
    public PublicController(PortalService portal,AuthService auth,Database db,Settings settings) {
        this.portal=portal;this.auth=auth;this.db=db;this.settings=settings;
    }
    @GetMapping("/health") public Map<String,Object> health() {
        db.query("SELECT count(*) FROM signups");return Map.of("status","ok");
    }
    @GetMapping("/config") public Map<String,Object> config() {
        return PortalService.map("clubName","计算机社团","siteUrl",settings.baseUrl+"/","qqGroupUrl",PortalService.QQ_URL,"retentionDays",settings.retentionDays,"privacyContact",settings.contact);
    }
    @PostMapping("/signup") public ResponseEntity<?> signup(HttpServletRequest req,@RequestBody JsonNode body) {
        auth.rate(req,"signup",12,3600);
        return ResponseEntity.status(201).body(portal.signup(body));
    }
    @GetMapping("/me") public Map<String,Object> me(HttpServletRequest req) { return portal.me(auth.member(req)); }
    @GetMapping("/card") public ResponseEntity<byte[]> card(HttpServletRequest req) {
        return ResponseEntity.ok().header("Content-Type","image/svg+xml; charset=utf-8").header("Content-Disposition","attachment; filename=\"explorer-card.svg\"").body(portal.card(auth.member(req)));
    }
    @GetMapping("/poll") public Map<String,Object> poll() { return Map.of("results",portal.poll()); }
    @PostMapping("/vote") public Map<String,Object> vote(HttpServletRequest req,@RequestBody JsonNode body) {
        auth.rate(req,"vote",60,3600);var member=auth.member(req);
        String choice=PortalService.text(body,"choice",20,true);
        if(!PortalService.PROPOSALS.containsKey(choice))throw new PortalError(400,"请选择有效的活动提案。");
        db.execute("INSERT INTO votes VALUES (?,?,?) ON DUPLICATE KEY UPDATE choice=?,updated_at=?",member.get("id"),choice,System.currentTimeMillis()/1000,choice,System.currentTimeMillis()/1000);
        return PortalService.map("saved",true,"choice",choice);
    }
}
