package com.ispf.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@org.springframework.boot.context.properties.EnableConfigurationProperties({
        com.ispf.server.config.NatsProperties.class,
        com.ispf.server.config.IspfSecurityProperties.class,
        com.ispf.server.config.VariableHistoryProperties.class,
        com.ispf.server.config.PlatformUpdateProperties.class,
        com.ispf.server.config.CommercialLicenseProperties.class,
        com.ispf.server.config.AiProperties.class
})
public class IspfServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(IspfServerApplication.class, args);
    }
}
