package uk.gov.ons.census.casesvc.testutil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.ons.census.casesvc.model.dto.CcsToFwmt;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;

@Component
@ActiveProfiles("test")
@EnableRetry
public class RabbitQueueHelper {
  private static final ObjectMapper objectMapper = new ObjectMapper();

  static {
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  @Autowired private ConnectionFactory connectionFactory;

  @Autowired private RabbitTemplate rabbitTemplate;

  @Autowired private AmqpAdmin amqpAdmin;

  public BlockingQueue<String> listen(String queueName) {
    return listen(queueName, 50);
  }

  public BlockingQueue<String> listen(String queueName, int capacity) {
    BlockingQueue<String> transfer = new ArrayBlockingQueue(capacity);

    org.springframework.amqp.core.MessageListener messageListener =
        message -> {
          String msgStr = new String(message.getBody());
          transfer.add(msgStr);
        };
    SimpleMessageListenerContainer container =
        new SimpleMessageListenerContainer(connectionFactory);
    container.setMessageListener(messageListener);
    container.setQueueNames(queueName);
    container.start();

    return transfer;
  }

  public void sendMessage(String queueName, Object message) {
    rabbitTemplate.convertAndSend(queueName, message);
  }

  @Retryable(
      value = {java.io.IOException.class},
      maxAttempts = 10,
      backoff = @Backoff(delay = 5000))
  public void purgeQueue(String queueName) {
    amqpAdmin.purgeQueue(queueName);
  }

  public ResponseManagementEvent checkExpectedMessageReceived(BlockingQueue<String> queue)
      throws IOException, InterruptedException {
    String actualMessage = queue.poll(20, TimeUnit.SECONDS);
    assertNotNull("Did not receive message before timeout", actualMessage);
    ResponseManagementEvent responseManagementEvent =
        objectMapper.readValue(actualMessage, ResponseManagementEvent.class);
    assertNotNull(responseManagementEvent);
    assertEquals("RM", responseManagementEvent.getEvent().getChannel());
    return responseManagementEvent;
  }

  public void checkMessageIsNotReceived(BlockingQueue<String> queue, int timeOut)
      throws InterruptedException {
    String actualMessage = queue.poll(timeOut, TimeUnit.SECONDS);
    assertNull("Message received when not expected", actualMessage);
  }

  public CcsToFwmt checkCcsFwmtEmitted(BlockingQueue<String> queue)
      throws InterruptedException, IOException {
    String actualMessage = queue.poll(20, TimeUnit.SECONDS);
    assertNotNull("Did not receive message before timeout", actualMessage);
    CcsToFwmt ccsFwmt = objectMapper.readValue(actualMessage, CcsToFwmt.class);
    assertNotNull(ccsFwmt);
    return ccsFwmt;
  }

  public List<Integer> collectAllActualResponseCountsForCaseId(
      BlockingQueue<String> rhCaseOutboundQueue, String caseId) throws IOException {
    List<String> jsonList = new ArrayList<>();
    rhCaseOutboundQueue.drainTo(jsonList);

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
