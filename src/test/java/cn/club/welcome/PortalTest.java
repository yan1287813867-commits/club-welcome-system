package cn.club.welcome;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PortalTest {
    static final String ORIGIN="http://127.0.0.1:8080";
    static final String PASSWORD=Crypto.random(24);
    static final Path TEMP;
    static final String HASH;
    static { try { Files.createDirectories(Path.of("target","test-data"));TEMP=Files.createTempDirectory(Path.of("target","test-data"),"club-java-test-");HASH=Crypto.hash(PASSWORD.toCharArray()); } catch(Exception e){throw new RuntimeException(e);} }
    @DynamicPropertySource static void settings(DynamicPropertyRegistry r) {
        r.add("club.base-url",()->ORIGIN);r.add("club.app-secret",()->"test-"+Crypto.random(48));
        r.add("club.admin-password-hash",()->HASH);r.add("club.admin-username",()->"organizer");
        String url=System.getenv("TEST_DATABASE_URL");
        if(url==null || !url.matches("jdbc:mysql://[^/]+/club_welcome_test(?:[?].*)?"))
            throw new IllegalStateException("测试必须设置 TEST_DATABASE_URL，且数据库名必须为 club_welcome_test，避免清理正式数据");
        r.add("club.database-url",()->url);
        r.add("club.database-username",()->System.getenv().getOrDefault("TEST_DATABASE_USERNAME","root"));
        r.add("club.database-password",()->System.getenv().getOrDefault("TEST_DATABASE_PASSWORD",""));
        r.add("club.cookie-secure",()->"false");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Database db;
    @Autowired PortalService portal;
    @Autowired AuthService auth;
    String token,csrf;Cookie cookie;
    @BeforeEach void clear() {
        db.execute("DELETE FROM signups");db.execute("DELETE FROM sessions");db.execute("DELETE FROM limits");
        token=Crypto.random(32);csrf="";cookie=null;
    }
    Map<String,Object> payload() {
        return PortalService.map("name","测试同学","major","计算机 1 班","qq","1234567","interests",List.of("code","ai"),"consent",true,"token",token);
    }
    JsonNode result(MvcResult r)throws Exception {return json.readTree(r.getResponse().getContentAsByteArray());}
    String signup()throws Exception {
        return result(mvc.perform(post("/api/signup").header("Origin",ORIGIN).contentType("application/json").content(json.writeValueAsBytes(payload()))).andExpect(status().isCreated()).andReturn()).path("id").asText();
    }
    void login()throws Exception {
        var r=mvc.perform(post("/api/admin/login").header("Origin",ORIGIN).contentType("application/json").content(json.writeValueAsBytes(Map.of("username","organizer","password",PASSWORD)))).andExpect(status().isOk()).andReturn();
        String raw=r.getResponse().getHeader("Set-Cookie");
        assertNotNull(raw);assertTrue(raw.contains("HttpOnly"));assertTrue(raw.contains("SameSite=Strict"));
        cookie=new Cookie("club_session",raw.split(";")[0].split("=")[1]);csrf=result(r).path("csrf").asText();
    }
    @Test void healthAndStatic()throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(header().string("X-Frame-Options","DENY"));
        mvc.perform(get("/content.js")).andExpect(status().isOk());
        mvc.perform(get("/theme.css")).andExpect(status().isOk());
        mvc.perform(get("/.env")).andExpect(status().isNotFound());
        mvc.perform(get("/app.py")).andExpect(status().isNotFound());
    }
    @Test void signupAndPrivateMe()throws Exception {
        String id=signup();
        mvc.perform(get("/api/me").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.qq").doesNotExist());
        assertEquals("1234567",db.query("SELECT qq FROM signups").get(0).get("qq"));
        assertNotEquals(token,db.query("SELECT token_hash FROM signups").get(0).get("token_hash"));
    }
    @Test void idempotentRetryAndConflict()throws Exception {
        String id=signup();
        var node=result(mvc.perform(post("/api/signup").header("Origin",ORIGIN).contentType("application/json").content(json.writeValueAsBytes(payload()))).andExpect(status().isCreated()).andReturn());
        assertEquals(id,node.path("id").asText());assertTrue(node.path("duplicate").asBoolean());
        var changed=payload();changed.put("name","其他人");
        mvc.perform(post("/api/signup").header("Origin",ORIGIN).contentType("application/json").content(json.writeValueAsBytes(changed))).andExpect(status().isConflict());
    }
    @Test void concurrentRetriesOnlyOneRow()throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(4);
        try {
            List<Callable<Map<String,Object>>> tasks=new ArrayList<>();
            JsonNode body=json.valueToTree(payload());
            for(int i=0;i<4;i++)tasks.add(()->portal.signup(body));
            Set<Object> ids=new HashSet<>();for(var future:pool.invokeAll(tasks))ids.add(future.get().get("id"));
            assertEquals(1,ids.size());
            assertEquals(1,db.query("SELECT * FROM signups").size());
        }finally{pool.shutdown();}
    }
    @Test void validationRejectsInvalidFields()throws Exception {
        List<Map.Entry<String,Object>> cases=List.of(Map.entry("name"," "),Map.entry("name","x".repeat(21)),Map.entry("qq","abc"),Map.entry("consent","true"),Map.entry("interests",List.of()),Map.entry("interests",List.of("code","code")),Map.entry("interests",List.of(Map.of("x",1))));
        for(var entry:cases){var data=payload();data.put(entry.getKey(),entry.getValue());assertThrows(PortalError.class,()->portal.signup(json.valueToTree(data)));}
    }
    @Test void originJsonAndSize()throws Exception {
        mvc.perform(post("/api/signup").header("Origin","https://evil.invalid").contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/signup").header("Origin",ORIGIN).contentType("text/plain").content("{}")).andExpect(status().isUnsupportedMediaType());
        mvc.perform(post("/api/signup").header("Origin",ORIGIN).contentType("application/json").content("{")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/signup").header("Origin",ORIGIN).contentType("application/json").content("x".repeat(16385))).andExpect(status().isPayloadTooLarge());
    }
    @Test void authorizationBoundaries()throws Exception {
        signup();
        for(String path:List.of("/api/me","/api/card","/api/admin/signups","/api/admin/export","/api/admin/session"))
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization","Bearer "+Crypto.random(32))).andExpect(status().isUnauthorized());
    }
    @Test void voteReplacesAndRejectsInvalidChoice()throws Exception {
        signup();
        for(String choice:List.of("project","share","share"))
            mvc.perform(post("/api/vote").header("Origin",ORIGIN).header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsBytes(Map.of("choice",choice)))).andExpect(status().isOk());
        mvc.perform(get("/api/poll")).andExpect(jsonPath("$.results.project").value(0)).andExpect(jsonPath("$.results.share").value(1));
        mvc.perform(post("/api/vote").header("Origin",ORIGIN).header("Authorization","Bearer "+token).contentType("application/json").content("{\"choice\":\"bad\"}")).andExpect(status().isBadRequest());
    }
    @Test void safeSvgAndLongFields()throws Exception {
        var data=payload();data.put("name","<script>&");data.put("major","计算机类".repeat(10));portal.signup(json.valueToTree(data));
        var r=mvc.perform(get("/api/card").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn();
        String svg=r.getResponse().getContentAsString();
        assertTrue(svg.contains("&lt;script&gt;&amp;"));assertFalse(svg.contains("<script>"));assertFalse(svg.contains("1234567"));assertTrue(svg.contains("font-size=\"19\""));
        javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new java.io.ByteArrayInputStream(r.getResponse().getContentAsByteArray()));
    }
    @Test void loginSessionAndExpiry()throws Exception {
        login();mvc.perform(get("/api/admin/session").cookie(cookie)).andExpect(status().isOk());
        db.execute("UPDATE sessions SET expires=0");
        mvc.perform(get("/api/admin/session").cookie(cookie)).andExpect(status().isUnauthorized());
    }
    @Test void csrfAndDeleteCascade()throws Exception {
        String id=signup();db.execute("INSERT INTO votes VALUES (?,?,?)",id,"share",System.currentTimeMillis()/1000);login();
        mvc.perform(delete("/api/admin/signups/"+id).header("Origin",ORIGIN).cookie(cookie).header("X-CSRF-Token","bad")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/signups/"+id).header("Origin",ORIGIN).cookie(cookie).header("X-CSRF-Token",csrf)).andExpect(status().isOk());
        assertTrue(db.query("SELECT * FROM votes").isEmpty());
        mvc.perform(get("/api/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }
    @Test void csvProtectsFormulaAndPreservesChinese()throws Exception {
        var data=payload();data.put("name","=HYPERLINK(1)");portal.signup(json.valueToTree(data));login();
        String csv=mvc.perform(get("/api/admin/export").cookie(cookie)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(csv.startsWith("\ufeff"));assertTrue(csv.contains("'=HYPERLINK(1)"));assertTrue(csv.contains("计算机 1 班"));assertTrue(csv.contains("1234567"));
    }
    @Test void searchAndPagination()throws Exception {
        signup();login();
        mvc.perform(get("/api/admin/signups").param("q","测试").cookie(cookie)).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/admin/signups").param("q","' OR 1=1--").cookie(cookie)).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/admin/signups").param("page","2").cookie(cookie)).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/admin/signups").param("page","oops").cookie(cookie)).andExpect(status().isBadRequest());
    }
    @Test void logoutRequiresCsrfAndInvalidates()throws Exception {
        login();
        mvc.perform(post("/api/admin/logout").header("Origin",ORIGIN).cookie(cookie).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/logout").header("Origin",ORIGIN).cookie(cookie).header("X-CSRF-Token",csrf).contentType("application/json").content("{}")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/session").cookie(cookie)).andExpect(status().isUnauthorized());
    }
    @Test void rateLimitingPersistsInDatabase()throws Exception {
        for(int i=0;i<12;i++)mvc.perform(post("/api/signup").header("Origin",ORIGIN).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/signup").header("Origin",ORIGIN).contentType("application/json").content("{}")).andExpect(status().isTooManyRequests());
        assertFalse(db.query("SELECT * FROM limits").isEmpty());
    }
    @Test void retentionPrunesVoteToo()throws Exception {
        String id=signup();db.execute("INSERT INTO votes VALUES (?,?,?)",id,"share",0);
        db.execute("UPDATE signups SET created_at=0");db.prune();
        assertTrue(db.query("SELECT * FROM signups").isEmpty());assertTrue(db.query("SELECT * FROM votes").isEmpty());
    }
    @Test void databaseCanReopenAndCheckIntegrity()throws Exception {
        signup();
        try(var con=db.open();var stmt=con.createStatement();var rows=stmt.executeQuery("SELECT 1")){assertTrue(rows.next());assertEquals(1,rows.getInt(1));}
        assertEquals(1,db.query("SELECT * FROM signups").size());
    }
    @Test void passwordHashIsSaltedAndVerified()throws Exception {
        String a=Crypto.hash(PASSWORD.toCharArray()),b=Crypto.hash(PASSWORD.toCharArray());
        assertNotEquals(a,b);assertTrue(Crypto.verify(PASSWORD,a));assertFalse(Crypto.verify("wrong",a));
    }

    @Test void gradeIsNotRequiredAndNewRowsAreFreshman()throws Exception {
        String id=signup();
        assertEquals("大一",db.query("SELECT year FROM signups WHERE id=?",id).get(0).get("year"));
        token=Crypto.random(32);
        var data=payload();data.put("year","大四");
        portal.signup(json.valueToTree(data));
        assertEquals("大一",db.query("SELECT year FROM signups WHERE token_hash=?",auth.digest(token)).get(0).get("year"));
        String card=new String(portal.card(db.query("SELECT * FROM signups WHERE id=?",id).get(0)),java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(card.contains("大一"));
    }
    @Test void passwordLengthPolicy()throws Exception {
        String valid="Aa1"+Crypto.random(3).substring(0,5);
        assertTrue(Crypto.validNewPassword(valid.toCharArray()));
        assertTrue(Crypto.verify(valid,Crypto.hash(valid.toCharArray())));
        for(String invalid:List.of("Abc1234","abcdefgh","ABCDEFGH","12345678","abcd1234","ABCD1234","Abcdefgh","Abc1234!","大aB12345")) {
            assertFalse(Crypto.validNewPassword(invalid.toCharArray()),invalid);
        }
        assertTrue(Crypto.validNewPassword("Abcd12345".toCharArray()));
        assertTrue(Crypto.validNewPassword(("Aa1"+"b".repeat(61)).toCharArray()));
        assertFalse(Crypto.validNewPassword(("Aa1"+"b".repeat(62)).toCharArray()));
        assertFalse(Crypto.validNewPassword(null));
    }
    @Test void bulkDeleteSelectedOnlyAndCascadesVotes()throws Exception {
        List<String> ids=new ArrayList<>();
        for(int i=0;i<3;i++){token=Crypto.random(32);String id=signup();ids.add(id);db.execute("INSERT INTO votes VALUES (?,?,?)",id,"share",System.currentTimeMillis()/1000);}
        login();
        mvc.perform(post("/api/admin/signups/bulk-delete").header("Origin",ORIGIN).cookie(cookie).header("X-CSRF-Token",csrf).contentType("application/json").content(json.writeValueAsBytes(Map.of("ids",ids.subList(0,2)))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(2));
        assertEquals(ids.get(2),db.query("SELECT id FROM signups").get(0).get("id"));
        assertEquals(1,db.query("SELECT * FROM votes").size());
    }
    @Test void bulkDeleteRequiresAuthOriginAndCsrf()throws Exception {
        String id=signup();byte[] body=json.writeValueAsBytes(Map.of("ids",List.of(id)));
        mvc.perform(post("/api/admin/signups/bulk-delete").header("Origin",ORIGIN).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        login();
        mvc.perform(post("/api/admin/signups/bulk-delete").header("Origin",ORIGIN).cookie(cookie).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/signups/bulk-delete").header("Origin","https://evil.invalid").cookie(cookie).header("X-CSRF-Token",csrf).contentType("application/json").content(body)).andExpect(status().isForbidden());
        assertEquals(1,db.query("SELECT * FROM signups").size());
    }
    @Test void bulkDeleteIsAtomicOnStaleSelection()throws Exception {
        String id=signup();login();
        mvc.perform(post("/api/admin/signups/bulk-delete").header("Origin",ORIGIN).cookie(cookie).header("X-CSRF-Token",csrf).contentType("application/json").content(json.writeValueAsBytes(Map.of("ids",List.of(id,"FFFFFFFFFFFF")))))
            .andExpect(status().isConflict());
        assertEquals(1,db.query("SELECT * FROM signups").size());
    }
    @Test void bulkDeleteRejectsInvalidSelection()throws Exception {
        String id=signup();login();
        List<Object> inputs=List.of(List.of(),List.of(id,id),List.of("bad"),List.of(1),Collections.nCopies(301,id),"all");
        for(Object ids:inputs)
            mvc.perform(post("/api/admin/signups/bulk-delete").header("Origin",ORIGIN).cookie(cookie).header("X-CSRF-Token",csrf).contentType("application/json").content(json.writeValueAsBytes(Map.of("ids",ids))))
                .andExpect(status().isBadRequest());
        assertEquals(1,db.query("SELECT * FROM signups").size());
    }
}
