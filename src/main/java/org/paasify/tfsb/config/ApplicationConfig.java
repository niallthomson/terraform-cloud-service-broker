package org.paasify.tfsb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
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

    private Map<String, String> parameters;

    private Map<String, String> env;
}
