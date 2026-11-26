package cn.club.welcome;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.Arrays;

@SpringBootApplication
@EnableScheduling
public class WelcomeApplication {
    public static void main(String[] args) throws Exception {
        if (Arrays.asList(args).contains("--setup")) {
            Setup.create(Arrays.asList(args).contains("--local"));
            return;
        }
        if (args.length > 0 && java.util.Set.of("--backup", "--prune", "--reset-password").contains(args[0])) { Maintenance.run(args); return; }
        SpringApplication.run(WelcomeApplication.class, args);
    }
}
@Controller
class PageController {
    @GetMapping({"/admin", "/admin/"})
    String admin() { return "forward:/admin.html"; }
}
