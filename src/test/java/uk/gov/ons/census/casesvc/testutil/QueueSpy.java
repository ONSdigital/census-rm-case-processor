package uk.gov.ons.census.casesvc.testutil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;

@AllArgsConstructor
public class QueueSpy implements AutoCloseable {
  private static final ObjectMapper objectMapper = new ObjectMapper();

  static {
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  @Getter private BlockingQueue<String> queue;
  private SimpleMessageListenerContainer container;

  @Override
  public void close() throws Exception {
    container.stop();
  }

  public ResponseManagementEvent checkExpectedMessageReceived()
      throws IOException, InterruptedException {
    String actualMessage = queue.poll(20, TimeUnit.SECONDS);
    assertNotNull("Did not receive message before timeout", actualMessage);
    ResponseManagementEvent responseManagementEvent =
        objectMapper.readValue(actualMessage, ResponseManagementEvent.class);
    assertNotNull(responseManagementEvent);
    assertEquals("RM", responseManagementEvent.getEvent().getChannel());
    return responseManagementEvent;
  }

  public void checkMessageIsNotReceived(int timeOut) throws InterruptedException {
    String actualMessage = queue.poll(timeOut, TimeUnit.SECONDS);
    assertNull("Message received when not expected", actualMessage);
  }

  public List<Integer> collectAllActualResponseCountsForCaseId(String caseId) throws IOException {
    List<String> jsonList = new ArrayList<>();
    queue.drainTo(jsonList);

    List<Integer> actualActualResponseCountList = new ArrayList<>();

    for (String jsonString : jsonList) {
      ResponseManagementEvent responseManagementEvent =
          objectMapper.readValue(jsonString, ResponseManagementEvent.class);

      assertThat(responseManagementEvent.getPayload().getCollectionCase().getId())
          .isEqualTo(caseId);

      actualActualResponseCountList.add(
          responseManagementEvent.getPayload().getCollectionCase().getCeActualResponses());
    }

    return actualActualResponseCountList;
  }
}
