package org.paasify.tfsb.instance;

public interface WebhookHandler {
    public void onCompleted(String runId);

    public void onNeedsAttention(String runId);

    public void onError(String runId);
}
