package org.paasify.tfsb.instance.model;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
@Data
public class ServiceInstance {
    @Id
    private String id;

    private String serviceDefinitionId;

    private String planId;

    private String workspaceId;

    private String message;

    private boolean deleted;
}
