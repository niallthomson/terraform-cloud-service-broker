package org.paasify.tfsb.instance.model;

import lombok.Data;
import org.springframework.cloud.servicebroker.model.instance.OperationState;

import javax.persistence.*;

@Entity
@Data
public class ServiceBindingOperation {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @ManyToOne(optional=false)
    @JoinColumn(name="BINDING_ID")
    private ServiceBinding binding;

    private boolean complete;

    @Enumerated(EnumType.STRING)
    private OperationState state;

    private Type type;

    private String runId;

    public enum Type {
        CREATION,
        DELETION,
        UPDATE
    }
}
