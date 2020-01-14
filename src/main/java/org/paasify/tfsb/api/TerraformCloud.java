package org.paasify.tfsb.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.github.jasminb.jsonapi.ErrorUtils;
import com.github.jasminb.jsonapi.JSONAPIDocument;
import com.github.jasminb.jsonapi.ResourceConverter;
import com.github.jasminb.jsonapi.models.errors.Errors;
import com.github.jasminb.jsonapi.retrofit.JSONAPIConverterFactory;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.paasify.tfsb.api.model.*;
import org.paasify.tfsb.api.service.TerraformService;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;

import java.io.IOException;
import java.util.List;

public class TerraformCloud {

    private TerraformService terraformService;

    private ObjectMapper objectMapper;

    public TerraformCloud(String token) {
        objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.KEBAB_CASE);

        OkHttpClient.Builder httpClient = new OkHttpClient.Builder();
        httpClient.addInterceptor((Interceptor) chain -> {
            Request original = chain.request();

            Request request = original.newBuilder()
                    .header("Content-Type", "application/vnd.api+json")
                    .header("Authorization", "Bearer "+token)
                    .method(original.method(), original.body())
                    .build();

            return chain.proceed(request);
        });

        OkHttpClient client = httpClient.build();

        ResourceConverter converter = new ResourceConverter(objectMapper,  Organization.class, Workspace.class, Variable.class, Run.class, Apply.class, NotificationConfiguration.class);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://app.terraform.io/api/v2/")
                .addConverterFactory(new JSONAPIConverterFactory(converter))
                .client(client)
                .build();

        terraformService = retrofit.create(TerraformService.class);
    }

    public Workspace workspace(String organization, String name) throws TerraformCloudException {
        return call(this.terraformService.workspace(organization, name));
    }

    public Workspace workspace(String workspaceId) throws TerraformCloudException {
        return call(this.terraformService.workspace(workspaceId));
    }

    public Workspace createWorkspace(String organization, Workspace workspace) throws TerraformCloudException {
        return call(this.terraformService.createWorkspace(organization, workspace));
    }

    public void deleteWorkspace(Workspace workspace) throws TerraformCloudException {
        empty(this.terraformService.deleteWorkspace(workspace.getId()));
    }

    public Run run(String id) throws TerraformCloudException {
        return call(this.terraformService.run(id));
    }

    public Run createRun(Run run) throws TerraformCloudException {
        return call(this.terraformService.createRun(run));
    }

    public void applyRun(String id) throws TerraformCloudException {
        empty(this.terraformService.applyRun(id));
    }

    public void cancelRun(String id) throws TerraformCloudException {
        empty(this.terraformService.cancelRun(id));
    }

    public void discardRun(String id) throws TerraformCloudException {
        empty(this.terraformService.discardRun(id));
    }

    public Apply apply(String id) throws TerraformCloudException {
        return call(this.terraformService.apply(id));
    }

    public StateVersion stateVersion(String id) throws TerraformCloudException {
        return call(this.terraformService.stateVersion(id));
    }

    public List<Variable> variables(String organization, String workspace) throws TerraformCloudException {
        return call(this.terraformService.variables(organization, workspace));
    }

    public Variable createVariable(Variable variable) throws TerraformCloudException {
        return call(this.terraformService.createVariable(variable));
    }

    public NotificationConfiguration createNotificationConfiguration(String workspaceId, NotificationConfiguration notificationConfiguration) throws TerraformCloudException {
        return call(this.terraformService.createNotificationConfiguration(workspaceId, notificationConfiguration));
    }

    protected <T> T call(Call<JSONAPIDocument<T>> call) throws TerraformCloudException {
        Response<JSONAPIDocument<T>> response = null;

        try {
            response = call.execute();
        } catch (IOException ioe) {
            throw new TerraformCloudException("Failed to make Terraform Cloud API request ", ioe);
        }

        if(response.isSuccessful()) {
            return response.body().get();
        }

        try {
            Errors errors = ErrorUtils.parseErrorResponse(objectMapper, response.errorBody(), Errors.class);

            throw new TerraformCloudException("Terraform Cloud API Error: " + errors);
        }
        catch(IOException ioe) {
            throw new TerraformCloudException("Terraform Cloud API Error", ioe);
        }
    }

    protected void empty(Call<ResponseBody> call) throws TerraformCloudException {
        Response<ResponseBody> response = null;

        try {
            response = call.execute();
        } catch (IOException ioe) {
            throw new TerraformCloudException("Failed to make Terraform Cloud API request ", ioe);
        }

        if(response.isSuccessful()) {
            return;
        }

        try {
            Errors errors = ErrorUtils.parseErrorResponse(objectMapper, response.errorBody(), Errors.class);

            throw new TerraformCloudException("Terraform Cloud API Error: " + errors);
        }
        catch(IOException ioe) {
            throw new TerraformCloudException("Terraform Cloud API Error", ioe);
        }
    }
}
