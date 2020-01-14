package org.paasify.tfsb.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.Data;

@Type("state-version-outputs")
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StateVersionOutput {
    @Id
    private String id;

    private String name;

    private String type;

    private String value;
}
