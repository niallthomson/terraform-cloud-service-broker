package org.paasify.tfsb.instance.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

@Entity
@Data
@NoArgsConstructor
public class ServiceBinding {
    @Id
    private String id;

    @ManyToOne(optional=false)
    @JoinColumn(name="INSTANCE_ID")
    private ServiceInstance instance;

    private String workspaceId;

    private String message;

    private boolean deleted;

    public ServiceBinding(String id, ServiceInstance instance, String workspaceId) {
        this.id = id;
        this.instance = instance;
        this.workspaceId = workspaceId;
    }
}
