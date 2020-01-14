package org.paasify.tfsb.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.Data;

import java.util.Map;

@Type("organizations")
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Organization {
    @Id
    private String id;

    private String name;

    private String externalId;

    private String email;

    private Map<String, Boolean> permissions;
}