package org.paasify.tfsb.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.Data;

import java.util.List;

@Type("state-versions")
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StateVersion {
    @Id
    private String id;

    private String vcsCommitSha;

    private String vcsCommitUrl;

    private int serial;

    private String hostedStateDownloadUrl;

    @Relationship("outputs")
    private List<StateVersionOutput> outputs;
}
