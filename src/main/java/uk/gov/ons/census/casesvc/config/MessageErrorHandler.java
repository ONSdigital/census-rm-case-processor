package uk.gov.ons.census.casesvc.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import lombok.Data;
import org.springframework.amqp.rabbit.listener.exception.ListenerExecutionFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.ErrorHandler;
import uk.gov.ons.census.casesvc.client.BadMessageHandlerClient;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Data
public class MessageErrorHandler implements ErrorHandler {
  private static final Logger log = LoggerFactory.getLogger(MessageErrorHandler.class);
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

  private Class expectedType;

  @Value("${messagelogging.logstacktraces}")
  private boolean logStackTraces;

  public MessageErrorHandler(BadMessageHandlerClient badMessageHandlerClient) {
    this.badMessageHandlerClient = badMessageHandlerClient;
  }

  @Override
  public void handleError(Throwable throwable) {
    if (throwable instanceof ListenerExecutionFailedException) {
      ListenerExecutionFailedException failedException =
          (ListenerExecutionFailedException) throwable;

      System.out.println("WIBBLE");
      //      byte[] rawMessageBody = failedException.getFailedMessage().getBody();
      //      String messageBody = new String(rawMessageBody);
      //      String messageHash;
      //      // Digest is not thread-safe
      //      synchronized (digest) {
      //        messageHash = bytesToHexString(digest.digest(rawMessageBody));
      //      }
      //
      //      ErrorResponse errorResponse = badMessageHandlerClient
      //          .reportError(messageHash, "CaseProcessor");
      //
      //      if (errorResponse.getResponse().equals(ResponseType.DO_NOT_LOG_IT)) {
      //        return;
      //      }
      //
      //      if (logStackTraces) {
      //        log.with("message_hash", messageHash)
      //            .with("valid_json", validateJson(messageBody))
      //            .error("Could not process message", failedException.getCause());
      //      } else {
      //        log.with("message_hash", messageHash)
      //            .with("valid_json", validateJson(messageBody))
      //            .with("cause", failedException.getCause().getMessage())
      //            .error("Could not process message");
      //      }
      //    } else {
      //      log.error("Unexpected exception has occurred", throwable);
    }
  }

  //  private String bytesToHexString(byte[] hash) {
  //    StringBuffer hexString = new StringBuffer();
  //    for (int i = 0; i < hash.length; i++) {
  //      String hex = Integer.toHexString(0xff & hash[i]);
  //      if (hex.length() == 1) hexString.append('0');
  //      hexString.append(hex);
  //    }
  //    return hexString.toString();
  //  }
  //
  //  private String validateJson(String messageBody) {
  //    try {
  //      objectMapper.readValue(messageBody, expectedType);
  //      return "Valid JSON";
  //    } catch (IOException e) {
  //      return String.format("Invalid JSON: %s", e.getMessage());
  //    }
  //  }
}
