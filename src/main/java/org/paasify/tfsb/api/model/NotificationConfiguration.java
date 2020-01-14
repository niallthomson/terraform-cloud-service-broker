package org.paasify.tfsb.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.Data;

import java.util.List;

@Type("notification-configurations")
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationConfiguration {
    @Id
    private String id;

    private String destinationType;

    private boolean enabled;

    private String name;

    private String url;

    private List<String> triggers;

    @Relationship("subscribable")
    private Workspace subscribable;
}
