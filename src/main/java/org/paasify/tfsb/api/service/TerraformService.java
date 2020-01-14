package org.paasify.tfsb.api.service;

import com.github.jasminb.jsonapi.JSONAPIDocument;
import okhttp3.ResponseBody;
import org.paasify.tfsb.api.model.*;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

public interface TerraformService {
    // Organizations
    @GET("organizations")
    Call<JSONAPIDocument<List<Organization>>> organizations();

    @GET("organizations/{organization}")
    Call<JSONAPIDocument<Organization>> organization(@Path("organization") String organization);

    // Workspaces
    @GET("organizations/{organization}/workspaces")
    Call<JSONAPIDocument<List<Workspace>>> workspaces(@Path("organization") String organization);

    @GET("organizations/{organization}/workspaces/{workspace}?include=organization,current_run")
    Call<JSONAPIDocument<Workspace>> workspace(@Path("organization") String organization, @Path("workspace") String workspaceName);

    @GET("workspaces/{workspace}?include=organization,current_run")
    Call<JSONAPIDocument<Workspace>> workspace(@Path("workspace") String workspaceId);

    @POST("organizations/{organization}/workspaces")
    Call<JSONAPIDocument<Workspace>> createWorkspace(@Path("organization") String organization, @Body Workspace workspace);

    @POST("workspaces/{id}")
    Call<JSONAPIDocument<Workspace>> updateWorkspace(@Path("id") String id, @Body Workspace workspace);

    @DELETE("workspaces/{id}")
    Call<ResponseBody> deleteWorkspace(@Path("id") String id);

    // Runs
    @GET("runs/{id}")
    Call<JSONAPIDocument<Run>> run(@Path("id") String id);

    @POST("runs")
    Call<JSONAPIDocument<Run>> createRun(@Body Run run);

    @POST("runs/{id}/actions/apply")
    Call<ResponseBody> applyRun(@Path("id") String id);

    @POST("runs/{id}/actions/discard")
    Call<ResponseBody> discardRun(@Path("id") String id);

    @POST("runs/{id}/actions/cancel")
    Call<ResponseBody> cancelRun(@Path("id") String id);

    // Applies
    @GET("applies/{id}")
    Call<JSONAPIDocument<Apply>> apply(@Path("id") String id);

    // State Versions
    @GET("state-versions/{id}?include=outputs")
    Call<JSONAPIDocument<StateVersion>> stateVersion(@Path("id") String id);

    @GET("state-version-outputs/{id}")
    Call<JSONAPIDocument<StateVersionOutput>> stateVersionOutput(@Path("id") String id);

    // Variables
    @GET("vars")
    Call<JSONAPIDocument<List<Variable>>> variables(@Query("filter[organization][name]") String organization, @Query("filter[workspace][name]") String workspace);

    @POST("vars")
    Call<JSONAPIDocument<Variable>> createVariable(@Body Variable variable);

    @POST("vars/{id}")
    Call<JSONAPIDocument<Variable>> updateVariable(@Path("id") String id, @Body Variable variable);

    @DELETE("vars/{id}")
    Call<ResponseBody> deleteVariable(@Path("id") String id);

    // Notification Configurations
    @POST("workspaces/{workspaceId}/notification-configurations")
    Call<JSONAPIDocument<NotificationConfiguration>> createNotificationConfiguration(@Path("workspaceId") String workspaceId, @Body NotificationConfiguration notificationConfiguration);
}