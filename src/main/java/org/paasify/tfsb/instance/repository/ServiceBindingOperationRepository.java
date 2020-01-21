package org.paasify.tfsb.instance.repository;

import org.paasify.tfsb.instance.model.ServiceBindingOperation;
import org.springframework.data.repository.CrudRepository;

public interface ServiceBindingOperationRepository extends CrudRepository<ServiceBindingOperation, Long> {
    ServiceBindingOperation findByRunId(String runId);
}
