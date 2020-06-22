package uk.gov.ons.census.casesvc.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import net.logstash.logback.encoder.org.apache.commons.lang.exception.ExceptionUtils;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import uk.gov.ons.census.casesvc.client.ExceptionManagerClient;
import uk.gov.ons.census.casesvc.model.dto.ExceptionReportResponse;
import uk.gov.ons.census.casesvc.model.dto.SkippedMessage;

public class ManagedMessageRecoverer implements MessageRecoverer {
  private static final Logger log = LoggerFactory.getLogger(ManagedMessageRecoverer.class);
  private static final ObjectMapper objectMapper;
  private static final MessageDigest digest;

  static {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      log.error("Could not initialise hashing", e);
      throw new RuntimeException("Could not initialise hashing", e);
    }
  }

  private final ExceptionManagerClient exceptionManagerClient;
  private final Class expectedMessageType;
  private final boolean logStackTraces;
  private final String serviceName;
  private final String queueName;

  public ManagedMessageRecoverer(
      ExceptionManagerClient exceptionManagerClient,
      Class expectedMessageType,
      boolean logStackTraces,
      String serviceName,
      String queueName) {
    this.exceptionManagerClient = exceptionManagerClient;
    this.expectedMessageType = expectedMessageType;
    this.logStackTraces = logStackTraces;
    this.serviceName = serviceName;
    this.queueName = queueName;
  }

  @Override
  public void recover(Message message, Throwable throwable) {
    if (throwable instanceof ListenerExecutionFailedException) {
      ListenerExecutionFailedException listenerExecutionFailedException =
          (ListenerExecutionFailedException) throwable;
      byte[] rawMessageBody = message.getBody();
      String messageHash;

      // Digest is not thread-safe
      synchronized (digest) {
        messageHash = bytesToHexString(digest.digest(rawMessageBody));
      }

      String stackTraceRootCause =
          findUsefulRootCauseInStackTrace(listenerExecutionFailedException.getCause());
      ExceptionReportResponse reportResult =
          getExceptionReportResponse(
              listenerExecutionFailedException, messageHash, stackTraceRootCause);

      if (skipMessage(
          reportResult, messageHash, rawMessageBody, listenerExecutionFailedException, message)) {
        return; // Our work here is done
      }

      peekMessage(reportResult, messageHash, rawMessageBody);
      logMessage(
          reportResult,
          listenerExecutionFailedException.getCause(),
          messageHash,
          rawMessageBody,
          stackTraceRootCause);

      // Reject the original message where it'll be retried at some future point in time
      throw new AmqpRejectAndDontRequeueException(
          String.format("Message sent to DLQ exchange, message_hash is: %s", messageHash));
    } else {
      // Very unlikely that this'd happen but let's log it anyway
      log.error("Unexpected exception has occurred", throwable);
    }
  }

  private ExceptionReportResponse getExceptionReportResponse(
      ListenerExecutionFailedException listenerExecutionFailedException,
      String messageHash,
      String stackTraceRootCause) {
    ExceptionReportResponse reportResult = null;
    try {
      reportResult =
          exceptionManagerClient.reportException(
              messageHash,
              serviceName,
              queueName,
              listenerExecutionFailedException.getCause().getCause(),
              stackTraceRootCause);
    } catch (Exception exceptionManagerClientException) {
      log.with("reason", exceptionManagerClientException.getMessage())
          .warn(
              "Could not report to Exception Manager. There will be excessive logging until resolved");
    }
    return reportResult;
  }

  private boolean skipMessage(
      ExceptionReportResponse reportResult,
      String messageHash,
      byte[] rawMessageBody,
      ListenerExecutionFailedException listenerExecutionFailedException,
      Message message) {

    if (reportResult == null || !reportResult.isSkipIt()) {
      return false;
    }

    boolean result = false;

    // Make certain that we have a copy of the message before quarantining it
    try {
      SkippedMessage skippedMessage = new SkippedMessage();
      skippedMessage.setMessageHash(messageHash);
      skippedMessage.setMessagePayload(rawMessageBody);
      skippedMessage.setService(serviceName);
      skippedMessage.setQueue(queueName);
      skippedMessage.setContentType(
          listenerExecutionFailedException
              .getFailedMessage()
              .getMessageProperties()
              .getContentType());
      skippedMessage.setHeaders(
          listenerExecutionFailedException.getFailedMessage().getMessageProperties().getHeaders());
      skippedMessage.setRoutingKey(
          listenerExecutionFailedException
              .getFailedMessage()
              .getMessageProperties()
              .getReceivedRoutingKey());
      exceptionManagerClient.storeMessageBeforeSkipping(skippedMessage);
      result = true;
    } catch (Exception exceptionManagerClientException) {
      log.with("message_hash", messageHash)
          .warn(
              "Unable to store a copy of the message. Will NOT be quarantining",
              exceptionManagerClientException);
    }

    // If the quarantined message is persisted OK then we can ACK the message
    if (result) {
      log.with("message_hash", messageHash).warn("Quarantined message");
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
      byte[] rawMessageBody,
      String stackTraceRootCause) {
    if (reportResult != null && !reportResult.isLogIt()) {
      return;
    }

    if (logStackTraces) {
      log.with("message_hash", messageHash)
          .with("valid_json", validateJson(rawMessageBody))
          .error("Could not process message", cause);
    } else {
      log.with("message_hash", messageHash)
          .with("valid_json", validateJson(rawMessageBody))
          .with("cause", cause.getMessage())
          .with("root_cause", stackTraceRootCause)
          .error("Could not process message");
    }
  }

  private String bytesToHexString(byte[] hash) {
    StringBuffer hexString = new StringBuffer();
    for (int i = 0; i < hash.length; i++) {
      String hex = Integer.toHexString(0xff & hash[i]);
      if (hex.length() == 1) {
        hexString.append('0');
      }
      hexString.append(hex);
    }
    return hexString.toString();
  }

  private String validateJson(byte[] rawMessageBody) {
    try {
      objectMapper.readValue(new String(rawMessageBody), expectedMessageType);
      return "Valid JSON";
    } catch (IOException e) {
      return String.format("Invalid JSON: %s", e.getMessage());
    }
  }

  private String findUsefulRootCauseInStackTrace(Throwable cause) {
    String[] stackTrace = ExceptionUtils.getRootCauseStackTrace(cause);

    // Iterate through the stack trace until we hit the first problem with our code
    for (String stackTraceLine : stackTrace) {
      if (stackTraceLine.contains("uk.gov.ons.census")) {
        return stackTraceLine;
      }
    }

    return stackTrace[0];
  }
}
