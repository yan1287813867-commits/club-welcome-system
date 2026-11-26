package cn.club.welcome;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(PortalError.class)
    ResponseEntity<?> portal(PortalError e) {
        var result=ResponseEntity.status(e.status).header("Cache-Control","no-store");
        if(e.status==429)result.header("Retry-After","3600");
        return result.body(Map.of("error",e.getMessage()));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<?> invalidJson() { return ResponseEntity.badRequest().body(Map.of("error","JSON 格式不正确。")); }
}
