package uk.gov.ons.census.casesvc.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.handler.advice.AbstractRequestHandlerAdvice;
import org.springframework.messaging.Message;
import uk.gov.ons.census.casesvc.client.BadMessageHandlerClient;
import uk.gov.ons.census.casesvc.model.dto.ErrorResponse;
import uk.gov.ons.census.casesvc.model.dto.ErrorResponse.ResponseType;

public class MessageHandlingAdvice extends AbstractRequestHandlerAdvice {
  private static final Logger log = LoggerFactory.getLogger(MessageHandlingAdvice.class);
  private static final ObjectMapper objectMapper;
  private static final MessageDigest digest;

  static {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      log.error("Could not initialise hashing", e);
      throw new RuntimeException("Could not initialise hashing", e);
    }
  }

  private final BadMessageHandlerClient badMessageHandlerClient;
  private final Class expectedMessageType;

  @Value("${messagelogging.logstacktraces}")
  private boolean logStackTraces;

  public MessageHandlingAdvice(
      BadMessageHandlerClient badMessageHandlerClient, Class expectedMessageType) {
    this.badMessageHandlerClient = badMessageHandlerClient;
    this.expectedMessageType = expectedMessageType;
  }

  @Override
  protected Object doInvoke(ExecutionCallback executionCallback, Object o, Message<?> message)
      throws Exception {

    try {
      // add code before the invocation
      Object result = executionCallback.execute();

      // add code after the invocation
      return result;
    } catch (Exception e) {
      String messageHash;

      byte[] rawMessageBody;
      try {
        rawMessageBody = (byte[]) message.getPayload();
      } catch (ClassCastException cce) {
        log.with("payload", message.getPayload()).error("Error with unexpected payload", e);
        throw e;
      }

      // Digest is not thread-safe
      synchronized (digest) {
        messageHash = bytesToHexString(digest.digest(rawMessageBody));
      }

      ResponseType response = ResponseType.LOG_IT; // Default to log
      try {
        ErrorResponse errorResponse =
            badMessageHandlerClient.reportError(messageHash, "CaseProcessor");
        response = errorResponse.getResponse();
      } catch (Exception badMessageCheckException) {
        log.warn("Unable to check to see if we should not log or skip the message", e);
      }

      if (response == ResponseType.SKIP_IT) {
        return null; // This will cause the message to be ACK'ed
      } else if (response == ResponseType.LOG_IT) {
        if (logStackTraces) {
          log.with("message_hash", messageHash)
              .with("valid_json", validateJson(new String(rawMessageBody)))
              .error("Could not process message", e.getCause());
        } else {
          log.with("message_hash", messageHash)
              .with("valid_json", validateJson(new String(rawMessageBody)))
              .with("cause", e.getCause().getMessage())
              .error("Could not process message");
        }
      }

      throw e; // Rethrow so that message is NACK'ed
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
