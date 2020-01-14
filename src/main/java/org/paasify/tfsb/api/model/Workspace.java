package org.paasify.tfsb.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.Data;

import java.util.Map;

@Type("workspaces")
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Workspace {
    @Id
    private String id;

    private String name;

    private boolean autoApply;

    private String environment;

    private boolean locked;

    private boolean queueAllRuns;

    private boolean speculativeEnabled;

    private boolean operations = true;

    private String terraformVersion;

    private String workingDirectory;

    private VcsRepo vcsRepo;

    private String description;

    private String source;

    private String sourceName;

    private String sourceUrl;

    private String createdAt;

    private Map<String, Boolean> permissions;

    @Relationship("organization")
    private Organization organization;

    @Relationship("latest-run")
    private Run latestRun;
}