package org.paasify.tfsb.instance;

import org.paasify.tfsb.api.TerraformCloudException;
import org.paasify.tfsb.api.model.*;
import org.paasify.tfsb.catalog.CatalogService;
import org.paasify.tfsb.catalog.model.Offering;
import org.paasify.tfsb.config.ApplicationConfig;
import org.paasify.tfsb.instance.model.ServiceBinding;
import org.paasify.tfsb.instance.model.ServiceBindingOperation;
import org.paasify.tfsb.instance.model.ServiceInstance;
import org.paasify.tfsb.instance.model.ServiceInstanceOperation;
import org.paasify.tfsb.instance.repository.ServiceBindingOperationRepository;
import org.paasify.tfsb.instance.repository.ServiceBindingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.servicebroker.exception.ServiceBrokerOperationInProgressException;
import org.springframework.cloud.servicebroker.exception.ServiceInstanceBindingDoesNotExistException;
import org.springframework.cloud.servicebroker.exception.ServiceInstanceDoesNotExistException;
import org.springframework.cloud.servicebroker.model.binding.*;
import org.springframework.cloud.servicebroker.model.instance.DeleteServiceInstanceResponse;
import org.springframework.cloud.servicebroker.model.instance.GetLastServiceOperationResponse;
import org.springframework.cloud.servicebroker.model.instance.OperationState;
import org.springframework.cloud.servicebroker.service.ServiceInstanceBindingService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import javax.transaction.Transactional;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class ServiceInstanceBindingServiceImpl extends AbstractInstanceService implements ServiceInstanceBindingService, WebhookHandler {

    private final ServiceBindingRepository repository;

    private ServiceBindingOperationRepository operationRepository;

    private ServiceInstanceServiceImpl serviceInstanceService;

    public ServiceInstanceBindingServiceImpl(@Autowired ApplicationConfig config,
                                             @Autowired ServiceBindingRepository serviceBindingRepository,
                                             @Autowired ServiceBindingOperationRepository serviceBindingOperationRepository,
                                             @Autowired CatalogService catalogService,
                                             @Autowired ServiceInstanceServiceImpl serviceInstanceService) {
        super(config, catalogService);

        this.serviceInstanceService = serviceInstanceService;

        this.repository = serviceBindingRepository;
        this.operationRepository = serviceBindingOperationRepository;
    }

    @Transactional
    public void onCompleted(String runId) {
        Run run = null;
        try {
            run = this.api.run(runId);

            ServiceBindingOperation operation = this.operationRepository.findByRunId(runId);

            if(operation != null) {
                if(operation.getType() == ServiceBindingOperation.Type.DELETION) {
                    if(run.isDestroy()) {
                        this.api.deleteWorkspace(run.getWorkspace());

                        operation.setState(OperationState.SUCCEEDED);

                        ServiceBinding binding = operation.getBinding();
                        binding.setDeleted(true);

                        this.repository.save(binding);
                    }
                    else {
                        operation.setRunId(handleDestroy(run).getId());
                    }
                }
                else {
                    operation.setState(OperationState.SUCCEEDED);
                }

                this.operationRepository.save(operation);
            }
        } catch (TerraformCloudException e) {
            throw new RuntimeException(e);
        }
    }

    public void onNeedsAttention(String runId) {
        try {
            ServiceBindingOperation operation = this.operationRepository.findByRunId(runId);

            if(operation != null) {
                if(operation.getType() == ServiceBindingOperation.Type.DELETION) {
                    this.api.applyRun(runId);
                }
            }
        } catch (TerraformCloudException e) {
            throw new RuntimeException(e);
        }
    }

    @Transactional
    public void onError(String runId) {
        ServiceBindingOperation operation = this.operationRepository.findByRunId(runId);

        if(operation != null) {
            operation.setState(OperationState.FAILED);

            this.operationRepository.save(operation);
        }
    }

    @Override
    @Transactional
    public Mono<CreateServiceInstanceBindingResponse> createServiceInstanceBinding(CreateServiceInstanceBindingRequest request) {
        String id = request.getServiceInstanceId();
        String serviceDefinitionId = request.getServiceDefinitionId();

        Optional<ServiceInstance> instance = serviceInstanceService.getServiceInstanceRecord(id);

        if(instance.isEmpty()) {
            throw new ServiceInstanceDoesNotExistException(id);
        }

        Offering offering = this.catalogService.getOffering(serviceDefinitionId);

        if(offering == null) {
            throw new RuntimeException("Offering does note exist");
        }

        try {
            if(offering.getVcs().getBindingDirectory() == null) {
                return Mono.just(CreateServiceInstanceAppBindingResponse.builder()
                        .credentials(credentialsForWorkspace(instance.get().getWorkspaceId()))
                        .bindingExisted(true)
                        .build());
            }
            else {
                Map<String, String> additionalVars = new HashMap<>();

                if(isPlatformCloudFoundry(request.getContext())) {
                    additionalVars.put("application_guid", request.getAppGuid());
                }

                additionalVars.put("service_binding_id", request.getBindingId());
                additionalVars.put("terraform_instance_workspace", "instance-"+request.getServiceInstanceId());

                Run run;

                try {
                    run = createWorkspace("binding", serviceDefinitionId, request.getPlanId(), request.getServiceInstanceId(), request.getContext(), true, additionalVars);
                }
                catch(Exception e) {
                    throw new RuntimeException(e);
                }

                ServiceBinding binding = this.repository.save(new ServiceBinding(request.getBindingId(), instance.get(), run.getWorkspace().getId()));

                ServiceBindingOperation operation = createOperation(binding, ServiceBindingOperation.Type.CREATION, run.getId());

                return Mono.just(CreateServiceInstanceAppBindingResponse.builder()
                        .async(true)
                        .operation(Long.toString(operation.getId()))
                        .build());
            }
        }
        catch(TerraformCloudException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Mono<GetLastServiceBindingOperationResponse> getLastOperation(GetLastServiceBindingOperationRequest request) {
        String operationId = request.getOperation();

        Optional<ServiceBindingOperation> operation = this.operationRepository.findById(Long.parseLong(operationId));

        if(operation.isEmpty()) {
            throw new ServiceBrokerOperationInProgressException("No such operation");
        }

        return Mono.just(GetLastServiceBindingOperationResponse.builder()
                .operationState(operation.get().getState())
                .build());
    }

    @Override
    public Mono<GetServiceInstanceBindingResponse> getServiceInstanceBinding(GetServiceInstanceBindingRequest request) {
        String id = request.getBindingId();

        Optional<ServiceBinding> binding = repository.findById(id);

        if(binding.isEmpty()) {
            throw new ServiceInstanceBindingDoesNotExistException(id);
        }

        Map<String, Object> credentials;

        try {
            if (binding.get().getWorkspaceId() == null || "".equals(binding.get().getWorkspaceId())) {
                credentials = credentialsForWorkspace(binding.get().getInstance().getWorkspaceId());
            } else {
                credentials = credentialsForWorkspace(binding.get().getWorkspaceId());
            }

            return Mono.just(GetServiceInstanceAppBindingResponse.builder()
                    .credentials(credentials)
                    .build());
        }
        catch(TerraformCloudException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional
    public Mono<DeleteServiceInstanceBindingResponse> deleteServiceInstanceBinding(DeleteServiceInstanceBindingRequest request) {
        String id = request.getBindingId();

        Optional<ServiceBinding> binding = repository.findById(id);

        if(binding.isEmpty()) {
            throw new ServiceInstanceBindingDoesNotExistException(id);
        }

        if(binding.get().getWorkspaceId() == null || "".equals(binding.get().getWorkspaceId())) {
            return Mono.just(DeleteServiceInstanceBindingResponse.builder()
                    .build());
        }

        Run run = deleteWorkspace(binding.get().getWorkspaceId());

        ServiceBindingOperation operation = createOperation(binding.get(), ServiceBindingOperation.Type.DELETION, run.getId());

        return Mono.just(DeleteServiceInstanceBindingResponse.builder()
                .async(true)
                .operation(Long.toString(operation.getId()))
                .build());
    }

    private ServiceBindingOperation createOperation(ServiceBinding instance, ServiceBindingOperation.Type type, String runId) {
        ServiceBindingOperation operation = new ServiceBindingOperation();
        operation.setBinding(instance);
        operation.setState(OperationState.IN_PROGRESS);
        operation.setType(type);
        operation.setRunId(runId);

        return this.operationRepository.save(operation);
    }

    private Map<String, Object> credentialsForWorkspace(String workspaceId) throws TerraformCloudException {
        Workspace workspace = api.workspace(workspaceId);

        Apply apply = api.apply(workspace.getLatestRun().getApply().getId());

        StateVersion version = api.stateVersion(apply.getStateVersions().get(0).getId());

        Map<String, Object> credentials = new HashMap<>();

        for (StateVersionOutput output : version.getOutputs()) {
            credentials.put(output.getName(), output.getValue());
        }

        return credentials;
    }
}