package org.paasify.tfsb.config;

import lombok.Data;
import org.paasify.tfsb.catalog.repository.HttpOfferingRepository;
import org.paasify.tfsb.catalog.repository.OfferingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@Data
@ConfigurationProperties(prefix="servicebroker")
public class ApplicationConfig {

    private Terraform terraform;

    private String webhookUrl;

    private String vcsRepo;

    private String catalogUrl;

    private Map<String, String> parameters;

    private Map<String, String> context;

    private Auth auth;

    @Data
    public static class Auth {
        private String username;

        private String password;
    }

    @Data
    public static class Terraform {
        private String organization;

        private String token;

        private String oauthTokenId;

        private String version;
    }

    @Bean
    public OfferingRepository offeringRepository() {
        return new HttpOfferingRepository(this.catalogUrl);
    }
}
