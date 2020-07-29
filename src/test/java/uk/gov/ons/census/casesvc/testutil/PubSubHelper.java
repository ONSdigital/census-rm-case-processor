package uk.gov.ons.census.casesvc.testutil;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.utility.ObjectMapperFactory;

public class PubSubHelper {
  private static final ObjectMapper objectMapper = ObjectMapperFactory.objectMapper();

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
