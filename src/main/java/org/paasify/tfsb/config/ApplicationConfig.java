package org.paasify.tfsb.config;

import lombok.Data;
import org.paasify.tfsb.catalog.repository.HttpOfferingRepository;
import org.paasify.tfsb.catalog.repository.OfferingRepository;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@Data
@ConfigurationProperties(prefix="servicebroker")
public class ApplicationConfig {

    private String terraformToken;

    private String terraformOrganization;

    private String terraformVersion;

    private String webhookUrl;

    private String vcsRepo;

    private String oauthTokenId;

    private String offeringCatalogUrl;

    private Map<String, String> parameters;

    private Map<String, String> context;

    private Auth auth;

    @Data
    public static class Auth {
        private String username;

        private String password;
    }

    @Bean
    public OfferingRepository offeringRepository() {
        return new HttpOfferingRepository(this.offeringCatalogUrl);
    }
}
