package org.paasify.tfsb.instance;

import lombok.extern.log4j.Log4j2;
import org.paasify.tfsb.api.TerraformCloudException;
import org.paasify.tfsb.api.model.Run;
import org.paasify.tfsb.api.model.Variable;
import org.paasify.tfsb.api.model.Workspace;
import org.paasify.tfsb.catalog.CatalogService;
import org.paasify.tfsb.config.ApplicationConfig;
import org.paasify.tfsb.instance.model.ServiceInstance;
import org.paasify.tfsb.instance.model.ServiceInstanceOperation;
import org.paasify.tfsb.instance.repository.ServiceInstanceOperationRepository;
import org.paasify.tfsb.instance.repository.ServiceInstanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.servicebroker.exception.ServiceBrokerOperationInProgressException;
import org.springframework.cloud.servicebroker.exception.ServiceInstanceDoesNotExistException;
import org.springframework.cloud.servicebroker.model.instance.*;
import org.springframework.cloud.servicebroker.service.ServiceInstanceService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import javax.transaction.Transactional;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Log4j2
public class ServiceInstanceServiceImpl extends AbstractInstanceService implements ServiceInstanceService, WebhookHandler {

    private ServiceInstanceRepository repository;

    private ServiceInstanceOperationRepository operationRepository;

    public ServiceInstanceServiceImpl(@Autowired ApplicationConfig config,
                                      @Autowired ServiceInstanceRepository serviceInstanceRepository,
                                      @Autowired ServiceInstanceOperationRepository serviceInstanceOperationRepository,
                                      @Autowired CatalogService catalogService) {
        super(config, catalogService);

        this.repository = serviceInstanceRepository;
        this.operationRepository = serviceInstanceOperationRepository;
    }

    @Override
    @Transactional
    public Mono<CreateServiceInstanceResponse> createServiceInstance(CreateServiceInstanceRequest request) {
        String serviceInstanceId = request.getServiceInstanceId();
        String serviceDefinitionId = request.getServiceDefinitionId();
        String planId = request.getPlanId();

        Run run;

        try {
            run = createWorkspace("instance", serviceDefinitionId, planId, serviceInstanceId, request.getContext(), false, null);
        }
        catch(Exception e) {
            throw new RuntimeException(e);
        }

        ServiceInstance instance = this.repository.save(new ServiceInstance(serviceInstanceId, serviceDefinitionId, planId, run.getWorkspace().getId()));

        ServiceInstanceOperation operation = createOperation(instance, ServiceInstanceOperation.Type.CREATION, run.getId());

        return Mono.just(CreateServiceInstanceResponse.builder()
                .async(true)
                .operation(Long.toString(operation.getId()))
                .build());
    }

    @Override
    public Mono<UpdateServiceInstanceResponse> updateServiceInstance(UpdateServiceInstanceRequest request) {
        String serviceInstanceId = request.getServiceInstanceId();

        Optional<ServiceInstance> instance = repository.findById(serviceInstanceId);

        if(instance.isEmpty()) {
            throw new ServiceInstanceDoesNotExistException(serviceInstanceId);
        }

        try {
            Workspace param = new Workspace();
            param.setId(instance.get().getWorkspaceId());

            Run runParam = new Run();
            runParam.setWorkspace(param);
            runParam.setMessage("Update run");

            Run run = api.createRun(runParam);

            ServiceInstanceOperation operation = createOperation(instance.get(), ServiceInstanceOperation.Type.UPDATE, run.getId());

            return Mono.just(UpdateServiceInstanceResponse.builder()
                    .async(true)
                    .operation(Long.toString(operation.getId()))
                    .build());
        } catch (TerraformCloudException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional
    public Mono<DeleteServiceInstanceResponse> deleteServiceInstance(DeleteServiceInstanceRequest request) {
        String serviceInstanceId = request.getServiceInstanceId();

        Optional<ServiceInstance> instance = repository.findById(serviceInstanceId);

        if(instance.isEmpty()) {
            throw new ServiceInstanceDoesNotExistException(serviceInstanceId);
        }

        Run run = deleteWorkspace(instance.get().getWorkspaceId());

        ServiceInstanceOperation operation = createOperation(instance.get(), ServiceInstanceOperation.Type.DELETION, run.getId());

        return Mono.just(DeleteServiceInstanceResponse.builder()
                .async(true)
                .operation(Long.toString(operation.getId()))
                .build());
    }

    private ServiceInstanceOperation createOperation(ServiceInstance instance, ServiceInstanceOperation.Type type, String runId) {
        ServiceInstanceOperation operation = new ServiceInstanceOperation();
        operation.setInstance(instance);
        operation.setState(OperationState.IN_PROGRESS);
        operation.setType(type);
        operation.setRunId(runId);

        return this.operationRepository.save(operation);
    }

    @Override
    public Mono<GetServiceInstanceResponse> getServiceInstance(GetServiceInstanceRequest request) {
        String serviceInstanceId = request.getServiceInstanceId();

        //
        // retrieve the details of the specified service instance
        //

        String dashboardUrl = ""; /* retrieve dashboard URL */

        return Mono.just(GetServiceInstanceResponse.builder()
                .build());
    }

    @Override
    public Mono<GetLastServiceOperationResponse> getLastOperation(GetLastServiceOperationRequest request) {
        String operationId = request.getOperation();

        Optional<ServiceInstanceOperation> operation = this.operationRepository.findById(Long.parseLong(operationId));

        if(operation.isEmpty()) {
            throw new ServiceBrokerOperationInProgressException("No such operation");
        }

        return Mono.just(GetLastServiceOperationResponse.builder()
                .operationState(operation.get().getState())
                .build());
    }

    @Transactional
    public void onCompleted(String runId) {
        Run run = null;
        try {
            run = this.api.run(runId);

            ServiceInstanceOperation operation = this.operationRepository.findByRunId(runId);

            if(operation != null) {
                if(operation.getType() == ServiceInstanceOperation.Type.DELETION) {
                    if(run.isDestroy()) {
                        this.api.deleteWorkspace(run.getWorkspace());

                        operation.setState(OperationState.SUCCEEDED);

                        ServiceInstance instance = operation.getInstance();
                        instance.setDeleted(true);

                        this.repository.save(instance);
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
            ServiceInstanceOperation operation = this.operationRepository.findByRunId(runId);

            if(operation != null) {
                if(operation.getType() == ServiceInstanceOperation.Type.DELETION) {
                    this.api.applyRun(runId);
                }
            }
        } catch (TerraformCloudException e) {
            throw new RuntimeException(e);
        }
    }

    @Transactional
    public void onError(String runId) {
        ServiceInstanceOperation operation = this.operationRepository.findByRunId(runId);

        if(operation != null) {
            operation.setState(OperationState.FAILED);

            this.operationRepository.save(operation);
        }
    }

    public Optional<ServiceInstance> getServiceInstanceRecord(String id) {
        return this.repository.findById(id);
    }
}