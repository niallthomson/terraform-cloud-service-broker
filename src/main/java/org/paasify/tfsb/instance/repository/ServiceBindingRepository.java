package org.paasify.tfsb.instance.repository;

import org.paasify.tfsb.instance.model.ServiceBinding;
import org.springframework.data.repository.CrudRepository;

public interface ServiceBindingRepository extends CrudRepository<ServiceBinding, String> {
    ServiceBinding findByWorkspaceId(String workspaceId);
}
