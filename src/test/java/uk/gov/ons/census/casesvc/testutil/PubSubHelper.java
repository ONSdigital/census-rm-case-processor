package uk.gov.ons.census.casesvc.testutil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;

public class PubSubHelper {
  private static final ObjectMapper objectMapper = new ObjectMapper();

  static {
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  public static BlockingQueue<ResponseManagementEvent> subscribe(
      PubSubTemplate pubSubTemplate, String subscription) {
    BlockingQueue<ResponseManagementEvent> queue = new ArrayBlockingQueue(50);
    pubSubTemplate.subscribe(
        subscription,
        message -> {
          try {
            ResponseManagementEvent responseManagementEvent =
                objectMapper.readValue(
                    message.getPubsubMessage().getData().toByteArray(),
                    ResponseManagementEvent.class);
            queue.add(responseManagementEvent);
          } catch (IOException e) {
            // Not a lot we can do but bomb out
            System.out.println("ERROR: Cannot unmarshal bad data on PubSub subscription");
            System.exit(-1);
          }
        });

    return queue;
  }
}
