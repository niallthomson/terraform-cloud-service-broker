package org.paasify.tfsb.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.Data;

import java.util.Map;

@Type("runs")
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Run {
    @Id
    private String id;

    private boolean autoApply;

    private String errorText;

    @JsonProperty("is-destroy")
    private boolean isDestroy;

    private String message;

    private String source;

    private String status;

    private Map<String, String> metadata;

    @Relationship("apply")
    private Apply apply;

    @Relationship("workspace")
    private Workspace workspace;

    private Map<String, Boolean> actions;

    public static final String ACTION_CANCELABLE = "is-cancelable";
    public static final String ACTION_CONFIRMABLE = "is-confirmable";
    public static final String ACTION_DISCARDABLE = "is-discardable";
}
