package org.paasify.tfsb.api.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.Data;

import java.util.List;

@Type("applies")
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Apply {
    @Id
    private String id;

    private String status;

    private int resourceAdditions;

    private int resourceChanges;

    private int resourceDestructions;

    private String logReadUrl;

    @Relationship("state-versions")
    private List<StateVersion> stateVersions;
}
