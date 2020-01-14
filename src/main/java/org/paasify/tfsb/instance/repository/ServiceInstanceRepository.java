package org.paasify.tfsb.instance.repository;

import org.paasify.tfsb.instance.model.ServiceInstance;
import org.springframework.data.repository.CrudRepository;

public interface ServiceInstanceRepository extends CrudRepository<ServiceInstance, String> {
    ServiceInstance findByWorkspaceId(String workspaceId);
}
