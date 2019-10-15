package uk.gov.ons.census.casesvc.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.exception.ListenerExecutionFailedException;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
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
  private final String delayExchangeName;
  private final String quarantineExchangeName;
  private final RabbitTemplate rabbitTemplate;

  public ManagedMessageRecoverer(
      ExceptionManagerClient exceptionManagerClient,
      Class expectedMessageType,
      boolean logStackTraces,
      String serviceName,
      String queueName,
      String delayExchangeName,
      String quarantineExchangeName,
      RabbitTemplate rabbitTemplate) {
    this.exceptionManagerClient = exceptionManagerClient;
    this.expectedMessageType = expectedMessageType;
    this.logStackTraces = logStackTraces;
    this.serviceName = serviceName;
    this.queueName = queueName;
    this.delayExchangeName = delayExchangeName;
    this.quarantineExchangeName = quarantineExchangeName;
    this.rabbitTemplate = rabbitTemplate;
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

      ExceptionReportResponse reportResult =
          getExceptionReportResponse(listenerExecutionFailedException, messageHash);

      if (skipMessage(
          reportResult, messageHash, rawMessageBody, listenerExecutionFailedException, message)) {
        return; // Our work here is done
      }

      peekMessage(reportResult, messageHash, rawMessageBody);
      logMessage(
          reportResult, listenerExecutionFailedException.getCause(), messageHash, rawMessageBody);

      // Send the bad message to an exchange where it'll be retried at some future point in time
      rabbitTemplate.send(delayExchangeName, queueName, message);
    } else {
      // Very unlikely that this'd happen but let's log it anyway
      log.error("Unexpected exception has occurred", throwable);
    }
  }

  private ExceptionReportResponse getExceptionReportResponse(
      ListenerExecutionFailedException listenerExecutionFailedException, String messageHash) {
    ExceptionReportResponse reportResult = null;
    try {
      reportResult =
          exceptionManagerClient.reportException(
              messageHash,
              serviceName,
              queueName,
              listenerExecutionFailedException.getCause().getCause());
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

    // Make damn certain that we have a copy of the message before skipping it
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
              "Unable to store a copy of the message. Will NOT be skipping",
              exceptionManagerClientException);
    }

    // Check if OK and the message is stored... then we can go ahead and quarantine
    if (result) {
      result = false; // The next bit might go wrong
      log.with("message_hash", messageHash).warn("Skipping message");

      // Send the bad message to the quarantine queue
      rabbitTemplate.send(quarantineExchangeName, queueName, message);

      // Presumably the message is now safely quarantined
      result = true;
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
      byte[] rawMessageBody) {
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
          .error("Could not process message");
    }
  }

  private String bytesToHexString(byte[] hash) {
    StringBuffer hexString = new StringBuffer();
    for (int i = 0; i < hash.length; i++) {
      String hex = Integer.toHexString(0xff & hash[i]);
      if (hex.length() == 1) hexString.append('0');
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
}
