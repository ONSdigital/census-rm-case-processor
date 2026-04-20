package uk.gov.ons.census.caseprocessor.messaging;

import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import com.google.cloud.spring.pubsub.support.GcpPubSubHeaders;
import com.google.protobuf.ByteString;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.messaging.MessagingException;
import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.caseprocessor.client.ExceptionManagerClient;
import uk.gov.ons.census.caseprocessor.model.dto.ExceptionReportResponse;
import uk.gov.ons.census.caseprocessor.model.dto.SkippedMessage;
import uk.gov.ons.census.caseprocessor.utils.HashHelper;

@Component
public class ManagedMessageRecoverer implements RecoveryCallback<Object> {
  private static final Logger log = LoggerFactory.getLogger(ManagedMessageRecoverer.class);
  private static final String SERVICE_NAME = "Case Processor";

  @Value("${messagelogging.logstacktraces}")
  private boolean logStackTraces;

  private final ExceptionManagerClient exceptionManagerClient;

  public ManagedMessageRecoverer(ExceptionManagerClient exceptionManagerClient) {
    this.exceptionManagerClient = exceptionManagerClient;
  }

  @Override
  public Object recover(RetryContext retryContext) {
    if (!(retryContext.getLastThrowable() instanceof MessagingException)) {
      log.error(
          "Super duper unexpected kind of error, so going to fail very noisily",
          retryContext.getLastThrowable());
      throw new RuntimeException(retryContext.getLastThrowable());
    }

    MessagingException messagingException = (MessagingException) retryContext.getLastThrowable();
    Message<?> message = messagingException.getFailedMessage();
    BasicAcknowledgeablePubsubMessage originalMessage =
        (BasicAcknowledgeablePubsubMessage)
            message.getHeaders().get(GcpPubSubHeaders.ORIGINAL_MESSAGE);
    String subscriptionName = originalMessage.getProjectSubscriptionName().getSubscription();
    ByteString originalMessageByteString = originalMessage.getPubsubMessage().getData();
    byte[] rawMessageBody = new byte[originalMessageByteString.size()];
    originalMessageByteString.copyTo(rawMessageBody, 0);

    String messageHash = HashHelper.hash(rawMessageBody);

    String stackTraceRootCause = findUsefulRootCauseInStackTrace(retryContext.getLastThrowable());

    Throwable cause = retryContext.getLastThrowable();
    if (retryContext.getLastThrowable() != null
        && retryContext.getLastThrowable().getCause() != null
        && retryContext.getLastThrowable().getCause().getCause() != null) {
      cause = retryContext.getLastThrowable().getCause().getCause();
    }

    ExceptionReportResponse reportResult =
        getExceptionReportResponse(cause, messageHash, stackTraceRootCause, subscriptionName);

    if (skipMessage(reportResult, messageHash, rawMessageBody, subscriptionName)) {
      return null; // Our work here is done
    }

    peekMessage(reportResult, messageHash, rawMessageBody);

    logMessage(
        reportResult, retryContext.getLastThrowable().getCause(), messageHash, stackTraceRootCause);

    // Reject the original message (auto nack'ed). It will be retried at some future point in time
    throw new MessageHandlingException(
        message, "Cannot process this message at this time, but it will be retried");
  }

  private ExceptionReportResponse getExceptionReportResponse(
      Throwable cause, String messageHash, String stackTraceRootCause, String subscriptionName) {
    ExceptionReportResponse reportResult = null;
    try {
      reportResult =
          exceptionManagerClient.reportException(
              messageHash, SERVICE_NAME, subscriptionName, cause, stackTraceRootCause);
    } catch (Exception exceptionManagerClientException) {
      log.atWarn()
          .setMessage(
              "Could not report to Exception Manager. There will be excessive logging until resolved")
          .addKeyValue("reason", exceptionManagerClientException.getMessage())
          .log();
    }
    return reportResult;
  }

  private boolean skipMessage(
      ExceptionReportResponse reportResult,
      String messageHash,
      byte[] rawMessageBody,
      String subscriptionName) {

    if (reportResult == null || !reportResult.isSkipIt()) {
      return false;
    }

    boolean result = false;

    // Make certain that we have a copy of the message before quarantining it
    try {
      SkippedMessage skippedMessage = new SkippedMessage();
      skippedMessage.setMessageHash(messageHash);
      skippedMessage.setMessagePayload(rawMessageBody);
      skippedMessage.setService(SERVICE_NAME);
      skippedMessage.setSubscription(subscriptionName);
      skippedMessage.setContentType("application/json");
      skippedMessage.setHeaders(null);
      skippedMessage.setRoutingKey(null);
      exceptionManagerClient.storeMessageBeforeSkipping(skippedMessage);
      result = true;
    } catch (Exception exceptionManagerClientException) {

      log.atWarn()
          .setMessage("Unable to store a copy of the message. Will NOT be quarantining")
          .setCause(exceptionManagerClientException)
          .addKeyValue("message_hash", messageHash)
          .log();
    }

    // If the quarantined message is persisted OK then we can ACK the message
    if (result) {
      log.atWarn().setMessage("Quarantined message").addKeyValue("message_hash", messageHash).log();
    }

    return result;
  }

  private void peekMessage(
      ExceptionReportResponse reportResult, String messageHash, byte[] rawMessageBody) {
    if (reportResult == null || !reportResult.isPeek()) {
      return;
    }

    try {
      // Send it back to the exception manager so it can be peeked
      exceptionManagerClient.respondToPeek(messageHash, rawMessageBody);
    } catch (Exception respondException) {
      // Nothing we can do about this - ignore it
    }
  }

  private void logMessage(
      ExceptionReportResponse reportResult,
      Throwable cause,
      String messageHash,
      String stackTraceRootCause) {
    if (reportResult != null && !reportResult.isLogIt()) {
      return;
    }

    if (logStackTraces) {
      log.atError()
          .setMessage("Could not process message")
          .setCause(cause)
          .addKeyValue("message_hash", messageHash)
          .log();
    } else {

      log.atError()
          .setMessage("Could not process message")
          .addKeyValue("message_hash", messageHash)
          .addKeyValue("cause", cause.getMessage())
          .addKeyValue("root_cause", stackTraceRootCause)
          .log();
    }
  }

  private String findUsefulRootCauseInStackTrace(Throwable cause) {
    String[] stackTrace = ExceptionUtils.getRootCauseStackTrace(cause);

    // Iterate through the stack trace until we hit the first problem with our code
    for (String stackTraceLine : stackTrace) {
      if (stackTraceLine.contains("uk.gov.ons")) {
        return stackTraceLine;
      }
    }

    return stackTrace[0];
  }
}
