package uk.gov.ons.census.casesvc.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.listener.exception.ListenerExecutionFailedException;
import org.springframework.util.ErrorHandler;
import uk.gov.ons.census.casesvc.client.ExceptionManagerClient;
import uk.gov.ons.census.casesvc.model.dto.ExceptionReportResponse;

public class MessageErrorHandler implements ErrorHandler {
  private static final Logger log = LoggerFactory.getLogger(MessageErrorHandler.class);
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

  public MessageErrorHandler(
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
  public void handleError(Throwable throwable) {
    if (throwable instanceof ListenerExecutionFailedException) {
      ListenerExecutionFailedException failedException =
          (ListenerExecutionFailedException) throwable;
      byte[] rawMessageBody = failedException.getFailedMessage().getBody();
      String messageBody = new String(rawMessageBody);
      String messageHash;

      // Digest is not thread-safe
      synchronized (digest) {
        messageHash = bytesToHexString(digest.digest(rawMessageBody));
      }

      ExceptionReportResponse reportResult = null;
      try {
        reportResult =
            exceptionManagerClient.reportException(
                messageHash, serviceName, queueName, throwable.getCause().getCause());
      } catch (Exception exceptionManagerClientException) {
        log.warn(
            "Could not report exception. There will be excessive logging until this is resolved",
            exceptionManagerClientException);
      }

      if (reportResult != null && reportResult.isSkipIt()) {
        boolean haveStored = false;

        // Make damn certain that we have a copy of the message before skipping it
        try {
          exceptionManagerClient.storeMessageBeforeSkipping(
              messageHash, rawMessageBody, serviceName, queueName);
          haveStored = true;
        } catch (Exception exceptionManagerClientException) {
          log.with("message_hash", messageHash)
              .warn(
                  "Unable to store a copy of the message. Will NOT be skipping",
                  exceptionManagerClientException);
        }

        // OK the message is stored... we can go ahead and skip it
        if (haveStored) {
          log.with("message_hash", messageHash).warn("Skipping message");

          // There's no going back after this point - better be certain about this!
          throw new AmqpRejectAndDontRequeueException("Skipping message", throwable);
        }
      }

      if (reportResult != null && reportResult.isPeek()) {
        try {
          // Send it back to the exception manager so it can be peeked
          exceptionManagerClient.respondToPeek(messageHash, rawMessageBody);
        } catch (Exception respondException) {
          // Nothing we can do about this - ignore it
        }
      }

      if (reportResult == null || reportResult.isLogIt()) {
        if (logStackTraces) {
          log.with("message_hash", messageHash)
              .with("valid_json", validateJson(messageBody))
              .error("Could not process message", failedException.getCause());
        } else {
          log.with("message_hash", messageHash)
              .with("valid_json", validateJson(messageBody))
              .with("cause", failedException.getCause().getMessage())
              .error("Could not process message");
        }
      }
    } else {
      // Very unlikely that this'd happen but let's log it anyway
      log.error("Unexpected exception has occurred", throwable);
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

  private String validateJson(String messageBody) {
    try {
      objectMapper.readValue(messageBody, expectedMessageType);
      return "Valid JSON";
    } catch (IOException e) {
      return String.format("Invalid JSON: %s", e.getMessage());
    }
  }
}
