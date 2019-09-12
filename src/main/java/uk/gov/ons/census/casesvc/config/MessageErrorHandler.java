package uk.gov.ons.census.casesvc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import lombok.Data;
import org.springframework.amqp.rabbit.listener.exception.ListenerExecutionFailedException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.ErrorHandler;

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

    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      log.error("Could not initialise hashing", e);
      throw new RuntimeException(e);
    }
  }

  private Class expectedType;

  @Override
  public void handleError(Throwable throwable) {
    if (throwable instanceof ListenerExecutionFailedException) {
      ListenerExecutionFailedException failedException =
          (ListenerExecutionFailedException) throwable;
      byte[] rawMessageBody = failedException.getFailedMessage().getBody();
      String messageBody = new String(rawMessageBody);

      log.with("message_hash", bytesToHexString(digest.digest(rawMessageBody)))
          .with("cause", failedException.getCause().getMessage())
          .error("Could not process message");

      try {
        objectMapper.readValue(messageBody, expectedType);
      } catch (IOException e) {
        log.with("cause", e.getMessage())
            .error("Could not deserialise. JSON not in expected format or invalid");
      }
    } else {
      log.error("Unexpected exception has occurred", throwable);
    }
  }

  private static String bytesToHexString(byte[] hash) {
    StringBuffer hexString = new StringBuffer();
    for (int i = 0; i < hash.length; i++) {
      String hex = Integer.toHexString(0xff & hash[i]);
      if(hex.length() == 1) hexString.append('0');
      hexString.append(hex);
    }
    return hexString.toString();
  }
}
