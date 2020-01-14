package org.paasify.tfsb.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.Data;

@Type("vars")
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Variable {
    @Id
    private String id;

    private String key;

    private String value;

    private String description;

    private boolean sensitive;

    private String category;

    private boolean hcl;

    @Relationship("workspace")
    private Workspace workspace;

    public static Variable build(String key, String value, String category, String workspaceId) {
        Workspace workspace = new Workspace();
        workspace.setId(workspaceId);

        Variable variable = new Variable();
        variable.setKey(key);
        variable.setValue(value);
        variable.setCategory(category);
        variable.setWorkspace(workspace);

        return variable;
    }
}
