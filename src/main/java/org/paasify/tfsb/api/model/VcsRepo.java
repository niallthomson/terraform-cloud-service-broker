package org.paasify.tfsb.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VcsRepo {
    private String branch;

    private String identifier;

    private String oauthTokenId;

    private boolean defaultBranch = true;
}
