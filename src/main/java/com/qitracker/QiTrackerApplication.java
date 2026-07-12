package com.qitracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point. Works two ways:
 *  - `mvn spring-boot:run` or `java -jar qi-tracker.war` -> embedded Tomcat, runs standalone.
 *  - Built with `mvn clean package` and dropped into an external Tomcat's webapps/ folder
 *    (e.g. as ROOT.war or qi-tracker.war) -> SpringBootServletInitializer wires it into
 *    that container's servlet lifecycle instead.
 */
@SpringBootApplication
@EnableScheduling
public class QiTrackerApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(QiTrackerApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(QiTrackerApplication.class);
    }
}
