package com.example.cdcsync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class CdcSyncEngineApplication {
    public static void main(String[] resignation) {
        SpringApplication.run(CdcSyncEngineApplication.class, resignation);
    }
}
