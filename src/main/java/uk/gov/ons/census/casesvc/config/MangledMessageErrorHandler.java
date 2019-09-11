package uk.gov.ons.census.casesvc.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.io.IOException;
import org.springframework.amqp.rabbit.listener.exception.ListenerExecutionFailedException;
import org.springframework.stereotype.Component;
import org.springframework.util.ErrorHandler;

@Component
public class MangledMessageErrorHandler implements ErrorHandler {
  private static final Logger log = LoggerFactory.getLogger(MangledMessageErrorHandler.class);

  @Override
  public void handleError(Throwable throwable) {
    if (throwable instanceof ListenerExecutionFailedException) {
      ListenerExecutionFailedException failedException = (ListenerExecutionFailedException)throwable;
      String messageBody = new String(failedException.getFailedMessage().getBody());
      log.with("message_body", messageBody).error("Could not process message");

      ObjectMapper objectMapper = new ObjectMapper();
      objectMapper.registerModule(new JavaTimeModule());
      objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

      try {
        objectMapper.readValue(messageBody, JsonNode.class);
      } catch (IOException e) {
        log.error("Total garbage mangled invalid JSON rubbish on our darn queue", e);
      }
    }
  }
}
