package org.paasify.tfsb.instance;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.text.StringSubstitutor;
import org.paasify.tfsb.api.TerraformCloud;
import org.paasify.tfsb.api.TerraformCloudException;
import org.paasify.tfsb.api.model.*;
import org.paasify.tfsb.catalog.CatalogService;
import org.paasify.tfsb.catalog.model.Offering;
import org.paasify.tfsb.catalog.model.OfferingPlan;
import org.paasify.tfsb.config.ApplicationConfig;
import org.paasify.tfsb.instance.model.ServiceInstance;
import org.paasify.tfsb.instance.model.ServiceInstanceOperation;
import org.paasify.tfsb.instance.repository.ServiceInstanceOperationRepository;
import org.paasify.tfsb.instance.repository.ServiceInstanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.servicebroker.exception.ServiceBrokerOperationInProgressException;
import org.springframework.cloud.servicebroker.exception.ServiceInstanceDoesNotExistException;
import org.springframework.cloud.servicebroker.model.binding.*;
import org.springframework.cloud.servicebroker.model.instance.*;
import org.springframework.cloud.servicebroker.service.ServiceInstanceBindingService;
import org.springframework.cloud.servicebroker.service.ServiceInstanceService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Log4j2
public class ServiceManager implements ServiceInstanceService, ServiceInstanceBindingService {

    private TerraformCloud api;

    private ApplicationConfig config;

    private ServiceInstanceRepository repository;

    private ServiceInstanceOperationRepository serviceInstanceOperationRepository;

    private CatalogService catalogService;

    public ServiceManager(@Autowired ApplicationConfig config,
                          @Autowired ServiceInstanceRepository repository,
                          @Autowired ServiceInstanceOperationRepository serviceInstanceOperationRepository,
                          @Autowired CatalogService catalogService) {
        this.api = new TerraformCloud(config.getTerraform().getToken());

        this.catalogService = catalogService;
        this.repository = repository;
        this.serviceInstanceOperationRepository = serviceInstanceOperationRepository;
        this.config = config;
    }

    @Override
    @Transactional
    public Mono<CreateServiceInstanceResponse> createServiceInstance(CreateServiceInstanceRequest request) {
        String serviceInstanceId = request.getServiceInstanceId();
        String serviceDefinitionId = request.getServiceDefinitionId();
        String planId = request.getPlanId();

        Offering offering = this.catalogService.getOffering(serviceDefinitionId);

        if(offering == null) {
            throw new RuntimeException("Service definition does not exist");
        }

        OfferingPlan plan = offering.getPlans().get(planId);

        if(plan == null) {
            throw new RuntimeException("Plan does not exist");
        }

        VcsRepo repo = new VcsRepo();
        repo.setIdentifier(this.config.getVcsRepo());
        repo.setOauthTokenId(this.config.getTerraform().getOauthTokenId());

        Workspace workspaceReq = new Workspace();
        workspaceReq.setName("instance-"+serviceInstanceId);
        workspaceReq.setVcsRepo(repo);
        workspaceReq.setTerraformVersion(this.config.getTerraform().getVersion());
        workspaceReq.setWorkingDirectory(offering.getVcsDirectory());
        workspaceReq.setAutoApply(true);

        Workspace workspace;
        Run run;

        try {
            workspace = this.api.createWorkspace(this.config.getTerraform().getOrganization(), workspaceReq);

            NotificationConfiguration webhook = new NotificationConfiguration();
            webhook.setName("service-broker");
            webhook.setDestinationType(NotificationConfiguration.DESTINATION_TYPE_GENERIC);
            webhook.setEnabled(true);
            webhook.setSubscribable(workspace);
            webhook.setUrl(this.config.getWebhookUrl());
            webhook.setTriggers(Arrays.asList(new String[]{
                    "run:completed",
                    "run:errored",
                    "run:needs_attention"
            }));

            this.api.createNotificationConfiguration(workspace.getId(), webhook);

            this.api.createVariable(Variable.builder().key("service_instance_id")
                    .value(serviceInstanceId)
                    .category(Variable.CATEGORY_TERRAFORM)
                    .workspace(workspace)
                    .build());

            StringSubstitutor substitutor = new StringSubstitutor(this.config.getContext());

            for(String envName : offering.getEnv().keySet()) {
                this.api.createVariable(Variable.builder()
                        .key(envName)
                        .value(substitutor.replace(offering.getEnv().get(envName)))
                        .category(Variable.CATEGORY_ENV)
                        .workspace(workspace)
                        .sensitive(true)
                        .build());
            }

            for(String variableName : plan.getParameters().keySet()) {
                this.api.createVariable(Variable.builder()
                        .key(variableName)
                        .value(plan.getParameters().get(variableName))
                        .category(Variable.CATEGORY_TERRAFORM)
                        .workspace(workspace)
                        .build());
            }

            Thread.sleep(2000);

            Run runParam = new Run();
            runParam.setWorkspace(workspace);
            runParam.setMessage("Initial run");

            run = api.createRun(runParam);
        }
        catch(Exception e) {
            throw new RuntimeException(e);
        }

        ServiceInstance instance = new ServiceInstance();
        instance.setId(serviceInstanceId);
        instance.setPlanId(planId);
        instance.setWorkspaceId(workspace.getId());

        instance = this.repository.save(instance);

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

        Workspace workspace = null;
        try {
            workspace = api.workspace(instance.get().getWorkspaceId());
        } catch (TerraformCloudException e) {
            throw new RuntimeException(e);
        }

        Workspace param = new Workspace();
        param.setId(workspace.getId());

        try {
            this.api.createVariable(Variable.builder()
                    .key("CONFIRM_DESTROY")
                    .value("1")
                    .category(Variable.CATEGORY_ENV)
                    .workspace(param)
                    .build());
        }
        catch(TerraformCloudException e) {
            throw new RuntimeException(e);
        }

        Run run = workspace.getLatestRun();

        String runId = handleDestroy(run);

        ServiceInstanceOperation operation = createOperation(instance.get(), ServiceInstanceOperation.Type.DELETION, runId);

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

        return this.serviceInstanceOperationRepository.save(operation);
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

        Optional<ServiceInstanceOperation> operation = this.serviceInstanceOperationRepository.findById(Long.parseLong(operationId));

        if(operation.isEmpty()) {
            throw new ServiceBrokerOperationInProgressException("No such operation");
        }

        return Mono.just(GetLastServiceOperationResponse.builder()
                .operationState(operation.get().getState())
                .build());
    }

    private String handleDestroy(Run run) {
        String id = run.getId();

        try {
            if (run.getActions().get(Run.ACTION_CANCELABLE)) {
                this.api.cancelRun(run.getId());
            } else if (run.getActions().get(Run.ACTION_DISCARDABLE)) {
                this.api.discardRun(run.getId());
            } else {
                Workspace workspace = new Workspace();
                workspace.setId(run.getWorkspace().getId());

                Run destroyRun = new Run();
                destroyRun.setWorkspace(workspace);
                destroyRun.setMessage("Destroy run");
                destroyRun.setDestroy(true);

                destroyRun = api.createRun(destroyRun);

                id = destroyRun.getId();
            }

            return id;
        }
        catch(TerraformCloudException e) {
            throw new RuntimeException(e);
        }
    }

    @Transactional
    public void onCompleted(String runId) {
        Run run = null;
        try {
            run = this.api.run(runId);

            ServiceInstanceOperation operation = this.serviceInstanceOperationRepository.findByRunId(runId);

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
                        operation.setRunId(handleDestroy(run));
                    }
                }
                else {
                    operation.setState(OperationState.SUCCEEDED);
                }

                this.serviceInstanceOperationRepository.save(operation);
            }
        } catch (TerraformCloudException e) {
            throw new RuntimeException(e);
        }
    }

    public void onNeedsAttention(String runId) {
        try {
            ServiceInstanceOperation operation = this.serviceInstanceOperationRepository.findByRunId(runId);

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
        ServiceInstanceOperation operation = this.serviceInstanceOperationRepository.findByRunId(runId);

        if(operation != null) {
            operation.setState(OperationState.FAILED);

            this.serviceInstanceOperationRepository.save(operation);
        }
    }

    @Override
    public Mono<CreateServiceInstanceBindingResponse> createServiceInstanceBinding(CreateServiceInstanceBindingRequest request) {
        String id = request.getServiceInstanceId();

        Optional<ServiceInstance> instance = repository.findById(id);

        if(instance.isEmpty()) {
            throw new ServiceInstanceDoesNotExistException(id);
        }

        try {
            Workspace workspace = api.workspace(instance.get().getWorkspaceId());

            Apply apply = api.apply(workspace.getLatestRun().getApply().getId());

            StateVersion version = api.stateVersion(apply.getStateVersions().get(0).getId());

            Map<String, Object> credentials = new HashMap<>();

            for (StateVersionOutput output : version.getOutputs()) {
                credentials.put(output.getName(), output.getValue());
            }

            return Mono.just(CreateServiceInstanceAppBindingResponse.builder()
                    .credentials(credentials)
                    .bindingExisted(true)
                    .build());
        }
        catch(TerraformCloudException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Mono<DeleteServiceInstanceBindingResponse> deleteServiceInstanceBinding(DeleteServiceInstanceBindingRequest request) {
        return Mono.just(DeleteServiceInstanceBindingResponse.builder()
                .build());
    }
}