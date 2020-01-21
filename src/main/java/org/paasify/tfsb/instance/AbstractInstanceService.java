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
import org.paasify.tfsb.instance.repository.ServiceBindingOperationRepository;
import org.paasify.tfsb.instance.repository.ServiceBindingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.servicebroker.model.Context;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Log4j2
public abstract class AbstractInstanceService {

    protected CatalogService catalogService;
    protected ApplicationConfig config;
    protected TerraformCloud api;

    public AbstractInstanceService(ApplicationConfig config,
                                             CatalogService catalogService) {
        this.api = new TerraformCloud(config.getTerraform().getToken());

        this.catalogService = catalogService;
        this.config = config;
    }

    protected Run createWorkspace(String prefix, String serviceDefinitionId, String planId, String serviceInstanceId, Context context, boolean isBinding, Map<String, String> additionalVars) throws TerraformCloudException {
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
        workspaceReq.setName(prefix+"-"+serviceInstanceId);
        workspaceReq.setVcsRepo(repo);
        workspaceReq.setTerraformVersion(this.config.getTerraform().getVersion());

        if(isBinding) {
            workspaceReq.setWorkingDirectory(offering.getVcs().getBindingDirectory());
        }
        else {
            workspaceReq.setWorkingDirectory(offering.getVcs().getInstanceDirectory());
        }
        workspaceReq.setAutoApply(true);

        Workspace workspace = this.api.createWorkspace(this.config.getTerraform().getOrganization(), workspaceReq);

        NotificationConfiguration webhook = new NotificationConfiguration();
        webhook.setName("service-broker");
        webhook.setDestinationType(NotificationConfiguration.DESTINATION_TYPE_GENERIC);
        webhook.setEnabled(true);
        webhook.setSubscribable(workspace);
        webhook.setUrl(this.config.getWebhookUrl()+"?binding="+isBinding);
        webhook.setTriggers(Arrays.asList(new String[]{
                "run:completed",
                "run:errored",
                "run:needs_attention"
        }));

        this.api.createNotificationConfiguration(workspace.getId(), webhook);

        if(additionalVars == null) {
            additionalVars = new HashMap<>();
        }

        if(isPlatformCloudFoundry(context)) {
            additionalVars.put("organization_guid", (String)context.getProperty("organizationGuid"));
            additionalVars.put("space_guid", (String)context.getProperty("spaceGuid"));
        }

        createTerraformVariable("service_instance_id", serviceInstanceId, workspace);
        createTerraformVariable("terraform_organization", this.config.getTerraform().getOrganization(), workspace);

        StringSubstitutor substitutor = new StringSubstitutor(this.config.getContext());

        for(String envName : offering.getEnv().keySet()) {
            createEnvVariable(envName, substitutor.replace(offering.getEnv().get(envName)), workspace, true);
        }

        for(String variableName : plan.getParameters().keySet()) {
            createTerraformVariable(variableName, plan.getParameters().get(variableName), workspace);
        }

        for(String varName : additionalVars.keySet()) {
            createTerraformVariable(varName, additionalVars.get(varName), workspace);
        }

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            log.error("Sleep interrupted", e);
        }

        Run runParam = new Run();
        runParam.setWorkspace(workspace);
        runParam.setMessage("Initial run");

        return api.createRun(runParam);
    }

    protected boolean isPlatformCloudFoundry(Context context) {
        return "cloudfoundry".equals(context.getPlatform());
    }

    protected Variable createTerraformVariable(String name, String value, Workspace workspace) throws TerraformCloudException {
        return this.api.createVariable(Variable.builder()
                .key(name)
                .value(value)
                .category(Variable.CATEGORY_TERRAFORM)
                .workspace(workspace)
                .build());
    }

    protected Variable createEnvVariable(String name, String value, Workspace workspace, boolean sensitive) throws TerraformCloudException {
        return this.api.createVariable(Variable.builder()
                .key(name)
                .value(value)
                .category(Variable.CATEGORY_ENV)
                .workspace(workspace)
                .sensitive(sensitive)
                .build());
    }

    protected Run deleteWorkspace(String workspaceId) {
        Workspace workspace = null;
        try {
            workspace = api.workspace(workspaceId);
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

        return handleDestroy(run);
    }

    protected Run handleDestroy(Run run) {
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

                return destroyRun;
            }

            return run;
        }
        catch(TerraformCloudException e) {
            throw new RuntimeException(e);
        }
    }
}
