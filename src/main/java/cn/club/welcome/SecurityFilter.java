package cn.club.welcome;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.nio.charset.StandardCharsets;

@Component
public class SecurityFilter extends OncePerRequestFilter {
    private final Settings settings;
    public SecurityFilter(Settings settings) { this.settings=settings; }
    private void reject(HttpServletResponse response,int status,String message) throws IOException {
        response.setStatus(status);response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\""+message+"\"}");
    }
    @Override
    protected void doFilterInternal(HttpServletRequest req,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        response.setHeader("Cache-Control","no-store");
        response.setHeader("X-Content-Type-Options","nosniff");
        response.setHeader("X-Frame-Options","DENY");
        response.setHeader("Referrer-Policy","no-referrer");
        response.setHeader("Permissions-Policy","camera=(), microphone=(), geolocation=()");
        response.setHeader("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' blob: data:; connect-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
        if(!java.util.Set.of("GET","HEAD","POST","DELETE").contains(req.getMethod())) {
            reject(response,405,"不支持此方法。");return;
        }
        if(req.getMethod().equals("POST")||req.getMethod().equals("DELETE")) {
            if(!settings.baseUrl.equals(req.getHeader("Origin"))) { reject(response,403,"请求来源不正确。");return; }
        }
        if(req.getMethod().equals("POST")) {
            if(req.getContentType()==null||!req.getContentType().split(";")[0].trim().equals("application/json")) {
                reject(response,415,"请使用 JSON 请求。");return;
            }
            if(req.getContentLengthLong()>16384) { reject(response,413,"请求体过大。");return; }
            byte[] body=req.getInputStream().readNBytes(16385);
            if(body.length==0||body.length>16384) { reject(response,413,"请求体为空或过大。");return; }
            HttpServletRequestWrapper wrapper=new HttpServletRequestWrapper(req) {
                @Override public ServletInputStream getInputStream() {
                    ByteArrayInputStream input=new ByteArrayInputStream(body);
                    return new ServletInputStream() {
                        public int read() { return input.read(); }
                        public boolean isFinished() { return input.available()==0; }
                        public boolean isReady() { return true; }
                        public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                    };
                }
                @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8)); }
            };
            chain.doFilter(wrapper,response);
        } else chain.doFilter(req,response);
    }
}
