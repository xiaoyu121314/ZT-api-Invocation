package cn.tengyuan;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@EnableScheduling
@SpringBootApplication
public class TengYuanApplication {
    public static void main(String[] args) {

        // 启动Spring上下文
        ConfigurableApplicationContext context = SpringApplication.run(TengYuanApplication.class, args);
        // 获取环境对象
        Environment environment = context.getEnvironment();
        // 获取yml里面的端口号 (server.port)
        String port = environment.getProperty("server.port");

        log.info("    \n\n  (♥◠‿◠)ﾉﾞ  系统启动成功,端口号：" + port + "   ლ(´ڡ`ლ)ﾞ  \n\n      (♥◠‿◠)ﾉﾞ  小飞棍来咯！！！！   ლ(´ڡ`ლ)ﾞ\n");

    }
}