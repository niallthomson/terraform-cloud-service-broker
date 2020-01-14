package org.paasify.tfsb.instance;

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
        this.api = new TerraformCloud(config.getTerraformToken());

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
        //Map<String, Object> parameters = request.getParameters();

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
        repo.setOauthTokenId(this.config.getOauthTokenId());

        Workspace workspaceReq = new Workspace();
        workspaceReq.setName("instance-"+serviceInstanceId);
        workspaceReq.setVcsRepo(repo);
        workspaceReq.setTerraformVersion(this.config.getTerraformVersion());
        workspaceReq.setWorkingDirectory(offering.getVcsDirectory());
        workspaceReq.setAutoApply(true);

        Workspace workspace;
        Run run;

        try {
            workspace = this.api.createWorkspace(this.config.getTerraformOrganization(), workspaceReq);

            NotificationConfiguration webhook = new NotificationConfiguration();
            webhook.setName("service-broker");
            webhook.setDestinationType("generic");
            webhook.setEnabled(true);
            webhook.setSubscribable(workspace);
            webhook.setUrl(this.config.getWebhookUrl());
            webhook.setTriggers(Arrays.asList(new String[]{
                    "run:completed",
                    "run:errored",
                    "run:needs_attention"
            }));
            workspace.setAutoApply(true);

            Variable instanceIdVariable = new Variable();
            instanceIdVariable.setKey("service_instance_id");
            instanceIdVariable.setValue(serviceInstanceId);
            instanceIdVariable.setCategory("terraform");
            instanceIdVariable.setWorkspace(workspace);

            this.api.createVariable(instanceIdVariable);

            for(String envName : this.config.getEnv().keySet()) {
                Variable envVariable = new Variable();
                envVariable.setKey(envName);
                envVariable.setValue(this.config.getEnv().get(envName));
                envVariable.setCategory("env");
                envVariable.setWorkspace(workspace);

                this.api.createVariable(envVariable);
            }

            for(String variableName : plan.getParameters().keySet()) {
                Variable variable = new Variable();
                variable.setKey(variableName);
                variable.setValue(plan.getParameters().get(variableName));
                variable.setCategory("terraform");
                variable.setWorkspace(workspace);

                this.api.createVariable(variable);
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

        ServiceInstanceOperation operation = new ServiceInstanceOperation();
        operation.setInstance(instance);
        operation.setState(OperationState.IN_PROGRESS);
        operation.setType(ServiceInstanceOperation.Type.CREATION);
        operation.setRunId(run.getId());

        operation = this.serviceInstanceOperationRepository.save(operation);

        return Mono.just(CreateServiceInstanceResponse.builder()
                .async(true)
                .operation(Long.toString(operation.getId()))
                .build());
    }

    @Override
    public Mono<UpdateServiceInstanceResponse> updateServiceInstance(UpdateServiceInstanceRequest request) {
        String serviceInstanceId = request.getServiceInstanceId();
        String planId = request.getPlanId();
        String previousPlan = request.getPreviousValues().getPlanId();
        Map<String, Object> parameters = request.getParameters();

        //
        // perform the steps necessary to initiate the asynchronous
        // updating of all necessary resources
        //

        return Mono.just(UpdateServiceInstanceResponse.builder()
                .async(true)
                .build());
    }

    @Override
    @Transactional
    public Mono<DeleteServiceInstanceResponse> deleteServiceInstance(DeleteServiceInstanceRequest request) {
        String serviceInstanceId = request.getServiceInstanceId();

        Optional<ServiceInstance> instance = repository.findById(serviceInstanceId);

        if(instance.isEmpty()) {
            throw new RuntimeException("No service instance");
        }

        Workspace workspace = null;
        try {
            workspace = api.workspace(instance.get().getWorkspaceId());
        } catch (TerraformCloudException e) {
            throw new RuntimeException(e);
        }

        Workspace param = new Workspace();
        param.setId(workspace.getId());

        Variable variable = new Variable();
        variable.setKey("CONFIRM_DESTROY");
        variable.setValue("1");
        variable.setCategory("env");
        variable.setWorkspace(param);

        try {
            this.api.createVariable(variable);
        }
        catch(TerraformCloudException e) {
            throw new RuntimeException(e);
        }

        Run run = workspace.getLatestRun();

        String runId = handleDestroy(run);

        ServiceInstanceOperation operation = new ServiceInstanceOperation();
        operation.setInstance(instance.get());
        operation.setState(OperationState.IN_PROGRESS);
        operation.setType(ServiceInstanceOperation.Type.DELETION);
        operation.setRunId(runId);

        operation = this.serviceInstanceOperationRepository.save(operation);

        return Mono.just(DeleteServiceInstanceResponse.builder()
                .async(true)
                .operation(Long.toString(operation.getId()))
                .build());
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
            throw new RuntimeException("No such operation");
        }

        return Mono.just(GetLastServiceOperationResponse.builder()
                .operationState(operation.get().getState())
                .build());
    }

    private String handleDestroy(Run run) {
        String id = run.getId();

        try {
            if (run.getActions().get("is-cancelable")) {
                this.api.cancelRun(run.getId());
            } else if (run.getActions().get("is-discardable")) {
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

    @Override
    public Mono<CreateServiceInstanceBindingResponse> createServiceInstanceBinding(CreateServiceInstanceBindingRequest request) {
        String id = request.getServiceInstanceId();

        Optional<ServiceInstance> instance = repository.findById(id);

        if(instance.isEmpty()) {
            throw new RuntimeException("Instance does not exist");
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
}