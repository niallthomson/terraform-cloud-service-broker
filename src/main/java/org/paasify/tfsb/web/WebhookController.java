package org.paasify.tfsb.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.paasify.tfsb.instance.ServiceInstanceBindingServiceImpl;
import org.paasify.tfsb.instance.ServiceInstanceServiceImpl;
import org.paasify.tfsb.instance.WebhookHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Log4j2
public class WebhookController {

    @Autowired
    private ServiceInstanceServiceImpl serviceInstanceService;

    @Autowired
    private ServiceInstanceBindingServiceImpl serviceBindingService;

    @PostMapping("/webhook")
    public String webhook(@RequestBody WebhookPayload payload, @RequestParam("binding") boolean binding) {
        log.error("Processing webhook payload {}", payload);

        WebhookHandler handler = this.serviceInstanceService;

        if(binding) {
            handler = this.serviceBindingService;
        }

        for(WebhookPayloadNotification notification : payload.getNotifications()) {
            switch(notification.getTrigger()) {
                case "run:completed":
                    log.error("Processing completion event for {} ", payload.getRunId());
                    handler.onCompleted(payload.getRunId());
                    break;
                case "run:needs_attention":
                    log.error("Processing needs attention event for {} ", payload.getRunId());
                    handler.onNeedsAttention(payload.getRunId());
                    break;
                case "run:errored":
                    log.error("Processing error event for {} ", payload.getRunId());
                    handler.onError(payload.getRunId());
                    break;
            }
        }

        return "";
    }
}

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
class WebhookPayload {
    private long payloadVersion;

    private String runUrl;

    private String runId;

    private String runMessage;

    private String runCreatedAt;

    private String runCreatedBy;

    private String workspaceId;

    private String workspaceName;

    private String organizationName;

    private List<WebhookPayloadNotification> notifications;
}

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
class WebhookPayloadNotification {
    private String message;

    private String trigger;

    private String runStatus;

    private String runUpdatedAt;

    private String runUpdatedBy;
}