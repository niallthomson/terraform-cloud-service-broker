package org.paasify.tfsb.instance.repository;

import org.paasify.tfsb.instance.model.ServiceInstanceOperation;
import org.springframework.data.repository.CrudRepository;

public interface ServiceInstanceOperationRepository extends CrudRepository<ServiceInstanceOperation, Long> {
    ServiceInstanceOperation findByRunId(String runId);
}
