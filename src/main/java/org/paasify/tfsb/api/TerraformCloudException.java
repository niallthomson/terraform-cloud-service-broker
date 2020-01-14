package org.paasify.tfsb.api;

public class TerraformCloudException extends Exception {
    public TerraformCloudException(String message, Throwable cause) {
        super(message, cause);
    }

    public TerraformCloudException(String message) {
        super(message);
    }
}
