package org.paasify.tfsb.instance.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
@Data
@NoArgsConstructor
public class ServiceInstance {
    @Id
    private String id;

    private String serviceDefinitionId;

    private String planId;

    private String workspaceId;

    private String message;

    private boolean deleted;

    public ServiceInstance(String id, String serviceDefinitionId, String planId, String workspaceId) {
        this.id = id;
        this.serviceDefinitionId = serviceDefinitionId;
        this.planId = planId;
        this.workspaceId = workspaceId;
    }
}
