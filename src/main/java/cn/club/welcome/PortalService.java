package cn.club.welcome;

import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import java.util.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;

@Service
public class PortalService {
    final Database db; final AuthService auth; final ObjectMapper json;
    static final Map<String,String> INTERESTS=ordered("code","编程与开发","ai","AI 与新技术","design","设计与创意","game","游戏与交互","hardware","硬件与折腾","explore","还在探索中");
    static final Map<String,String> PROPOSALS=ordered("project","一起做一个小作品","workshop","新技术体验课","share","社团团建与趣味活动","challenge","组队参加挑战");
    static final Set<String> YEARS=Set.of("大一","大二","大三","大四","研究生","其他");
    static final String QQ_URL="https://qm.qq.com/example-community";
    public PortalService(Database db,AuthService auth,ObjectMapper json) { this.db=db;this.auth=auth;this.json=json; }
    static Map<String,String> ordered(String... pairs) {
        Map<String,String> map=new LinkedHashMap<>();
        for(int i=0;i<pairs.length;i+=2)map.put(pairs[i],pairs[i+1]);
        return Collections.unmodifiableMap(map);
    }
    static Map<String,Object> map(Object... pairs) {
        Map<String,Object> map=new LinkedHashMap<>();
        for(int i=0;i<pairs.length;i+=2)map.put((String)pairs[i],pairs[i+1]);
        return map;
    }
    String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch(Exception e) { throw new IllegalStateException("JSON 编码失败"); }
    }
    List<String> interests(Object value) {
        try { return json.readValue((String)value,json.getTypeFactory().constructCollectionType(List.class,String.class)); }
        catch(Exception e) { throw new PortalError(503,"记录格式异常，请联系管理员。"); }
    }
    static String text(JsonNode body,String key,int max,boolean required) {
        JsonNode node=body.get(key);
        if(node==null&&!required)return "";
        if(node==null||!node.isTextual())throw new PortalError(400,"资料格式不正确。");
        String value=node.asText().strip();
        if(value.codePointCount(0,value.length())>max||value.chars().anyMatch(c->c<32)||required&&value.isEmpty())
            throw new PortalError(400,"资料长度或格式不正确。");
        return value;
    }
    public Map<String,Object> signup(JsonNode body) {
        if(!body.isObject())throw new PortalError(400,"请求必须是 JSON 对象。");
        String name=text(body,"name",20,true),major=text(body,"major",40,true),year="大一",qq=text(body,"qq",12,false);
        if(!qq.isEmpty()&&!qq.matches("[1-9][0-9]{4,11}"))throw new PortalError(400,"QQ 格式不正确。");
        if(!body.path("consent").isBoolean()||!body.path("consent").booleanValue())throw new PortalError(400,"请先阅读并同意隐私说明。");
        JsonNode nodes=body.path("interests");
        TreeSet<String> selected=new TreeSet<>();
        if(!nodes.isArray()||nodes.isEmpty()||nodes.size()>6)throw new PortalError(400,"请选择兴趣方向。");
        for(JsonNode node:nodes) {
            if(!node.isTextual()||!INTERESTS.containsKey(node.asText())||!selected.add(node.asText()))throw new PortalError(400,"兴趣方向无效。");
        }
        String token=text(body,"token",64,true);
        if(!token.matches("[0-9a-f]{64}"))throw new PortalError(400,"报名凭证格式错误。");
        String payload=auth.digest(encode(map("name",name,"major",major,"year",year,"qq",qq,"interests",selected)));
        String tokenHash=auth.digest(token);
        return db.transaction(con->{
            var old=Database.query(con,"SELECT * FROM signups WHERE token_hash=?",tokenHash);
            if(!old.isEmpty()) {
                var row=old.get(0);
                if(!row.get("payload_hash").equals(payload))throw new PortalError(409,"此凭证已提交其他资料，请刷新状态。");
                return map("id",row.get("id"),"name",row.get("name"),"duplicate",true);
            }
            String id=Crypto.random(6).toUpperCase(Locale.ROOT);
            Database.execute(con,"INSERT INTO signups VALUES (?,?,?,?,?,?,?,?,?,?)",id,tokenHash,payload,name,major,year,qq,encode(selected),"2026-10-18-v4",System.currentTimeMillis()/1000);
            return map("id",id,"name",name,"duplicate",false);
        });
    }
    public Map<String,Object> me(Map<String,Object> row) {
        var vote=db.query("SELECT choice FROM votes WHERE signup_id=?",row.get("id"));
        return map("id",row.get("id"),"name",row.get("name"),"major",row.get("major"),"year",row.get("year"),"interests",interests(row.get("interests")),"vote",vote.isEmpty()?null:vote.get(0).get("choice"));
    }
    public Map<String,Object> poll() {
        Map<String,Object> counts=new LinkedHashMap<>();PROPOSALS.keySet().forEach(k->counts.put(k,0));
        for(var row:db.query("SELECT choice,count(*) n FROM votes GROUP BY choice"))counts.put((String)row.get("choice"),row.get("n"));
        return counts;
    }
    static String esc(Object value) {
        return Objects.toString(value,"").replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
    }
    public byte[] card(Map<String,Object> member) {
        String name=Objects.toString(member.get("name")),major=Objects.toString(member.get("major"));
        StringBuilder tags=new StringBuilder();int i=0;
        for(String key:interests(member.get("interests"))) {
            tags.append("<text x=\"").append(65+i%3*305).append("\" y=\"").append(393+i/3*42).append("\" font-size=\"22\">").append(esc(INTERESTS.get(key))).append("</text>");i++;
        }
        String svg="""
            <svg xmlns="http://www.w3.org/2000/svg" width="1080" height="680" viewBox="0 0 1080 680">
            <defs><linearGradient id="bg"><stop stop-color="#edf0ff"/><stop offset="1" stop-color="#f9ddd3"/></linearGradient></defs>
            <rect width="1080" height="680" rx="32" fill="url(#bg)"/><circle cx="980" cy="80" r="210" fill="#c2cae8"/>
            <path d="M65 520H1015" stroke="#8399be" stroke-dasharray="8 6"/>
            <g font-family="sans-serif" fill="#304f7c"><text x="65" y="85" font-size="26">计算机社团 / EXPLORER PASS</text>
            <text x="65" y="145" font-size="20">热爱不设限，未来由你定义。</text>
            <text x="65" y="260" font-size="%s">%s</text><text x="65" y="320" font-size="%s">%s</text>%s
            <text x="65" y="580" font-size="23">NO. %s</text><text x="65" y="630" font-size="17">报名纪念 · 不代表审核通过或正式录取</text></g></svg>
            """.formatted(name.length()>12?42:58,esc(name),major.length()>30?19:25,esc(major),tags,esc(member.get("id")));
        return svg.getBytes(StandardCharsets.UTF_8);
    }
    public Map<String,Object> dashboard(String query,int page,boolean export) {
        query=query.length()>80?query.substring(0,80):query;
        String where=query.isEmpty()?"":" WHERE instr(s.name,?)>0 OR instr(s.major,?)>0 OR instr(s.id,?)>0";
        List<Object> args=new ArrayList<>();if(!query.isEmpty())Collections.addAll(args,query,query,query);
        long total=((Number)db.query("SELECT count(*) n FROM signups s"+where,args.toArray()).get(0).get("n")).longValue();
        long all=((Number)db.query("SELECT count(*) n FROM signups").get(0).get("n")).longValue();
        Map<String,Object> interestCounts=new LinkedHashMap<>();INTERESTS.keySet().forEach(k->interestCounts.put(k,0));
        for(var row:db.query("SELECT interests FROM signups"))for(String key:interests(row.get("interests")))interestCounts.put(key,((Number)interestCounts.get(key)).intValue()+1);
        String sql="SELECT s.id,s.name,s.major,s.year,s.qq,s.interests,s.created_at,s.consent_version,v.choice FROM signups s LEFT JOIN votes v ON s.id=v.signup_id"+where+" ORDER BY s.created_at DESC,s.id";
        if(!export){sql+=" LIMIT 30 OFFSET ?";args.add((long)(page-1)*30);}
        var rows=db.query(sql,args.toArray());
        for(var row:rows)row.put("interests",interests(row.get("interests")));
        return map("items",rows,"total",total,"allTotal",all,"page",page,"pageSize",30,"interests",interestCounts,"poll",poll());
    }
    static String csvCell(Object raw) {
        String value=Objects.toString(raw,"");
        if(value.stripLeading().matches("(?s)^[=+@-].*"))value="'"+value;
        return "\""+value.replace("\"","\"\"")+"\"";
    }
    @SuppressWarnings("unchecked")
    public byte[] csv(String query) {
        var data=dashboard(query,1,true);
        StringBuilder out=new StringBuilder("\ufeff编号,称呼,专业班级,QQ,兴趣方向,活动提案,报名时间UTC,隐私版本\r\n");
        for(var row:(List<Map<String,Object>>)data.get("items")) {
            List<String> labels=new ArrayList<>();
            for(String key:(List<String>)row.get("interests"))labels.add(INTERESTS.get(key));
            Object[] cells={row.get("id"),row.get("name"),row.get("major"),row.get("qq"),String.join(" / ",labels),PROPOSALS.get(Objects.toString(row.get("choice"),"")),Instant.ofEpochSecond(((Number)row.get("created_at")).longValue()),row.get("consent_version")};
            out.append(String.join(",",Arrays.stream(cells).map(PortalService::csvCell).toList())).append("\r\n");
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
}
