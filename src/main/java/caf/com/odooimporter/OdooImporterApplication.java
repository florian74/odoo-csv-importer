package caf.com.odooimporter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@Slf4j
@EnableScheduling
@EnableConfigurationProperties
public class OdooImporterApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(OdooImporterApplication.class, args);
    }
    
}


