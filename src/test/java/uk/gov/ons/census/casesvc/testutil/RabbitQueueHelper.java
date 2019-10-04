package uk.gov.ons.census.casesvc.testutil;

import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
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
    BlockingQueue<String> transfer = new ArrayBlockingQueue(50);

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
}
