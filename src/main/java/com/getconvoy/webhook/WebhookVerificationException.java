package com.getconvoy.webhook;

/** Thrown when a webhook signature cannot be verified. */
public class WebhookVerificationException extends Exception {
    public WebhookVerificationException(String message) {
        super(message);
    }
}
