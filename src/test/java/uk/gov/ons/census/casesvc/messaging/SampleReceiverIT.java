package uk.gov.ons.census.casesvc.messaging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.MessageChannel;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import uk.gov.ons.census.casesvc.model.dto.CaseCreatedEvent;
import uk.gov.ons.census.casesvc.model.dto.FooBar;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest()
@RunWith(SpringJUnit4ClassRunner.class)
public class SampleReceiverIT {

  @Autowired
  MessageChannel amqpInputChannel;

  @Autowired
  ConnectionFactory connectionFactory;

  @Autowired
  RabbitQueueHelper rabbitQueueHelper;

  @Autowired
  private AmqpAdmin amqpAdmin;

  @Autowired
  private RabbitTemplate rabbitTemplate;


  @Before
  public void setUp() {
//    while (!Application.allSetUp.get()) {
//      try {
//        Thread.sleep(1);
//      } catch (InterruptedException e) {
//        e.printStackTrace();
//      }
//    }
//    try {
//      Thread.sleep(5000);
//    } catch (InterruptedException e) {
//      e.printStackTrace();
//    }
//    amqpAdmin.purgeQueue("exampleInboundQueue", false);
//    amqpAdmin.purgeQueue("myfanout.queue1", false);
//    amqpAdmin.purgeQueue("myfanout.queue2", false);
  }

  @Test
  public void testHappyPath() throws InterruptedException, IOException {
    BlockingQueue<String> queue1 = rabbitQueueHelper.listen("myfanout.queue1");
    BlockingQueue<String> queue2 = rabbitQueueHelper.listen("myfanout.queue2");

    FooBar fooBar = new FooBar();
    fooBar.setFoo("bar");
    fooBar.setTest("noodles");

    rabbitQueueHelper.sendMessage("exampleInboundQueue", fooBar);

    ObjectMapper objectMapper = new ObjectMapper();

    String actualMessage = queue1.poll(30, TimeUnit.SECONDS);
    CaseCreatedEvent caseCreatedEvent = objectMapper.readValue(actualMessage, CaseCreatedEvent.class);
    assertNotNull(caseCreatedEvent);
    assertEquals("rm", caseCreatedEvent.getEvent().getChannel());

    actualMessage = queue2.poll(30, TimeUnit.SECONDS);
    caseCreatedEvent = objectMapper.readValue(actualMessage, CaseCreatedEvent.class);
    assertNotNull(caseCreatedEvent);
    assertEquals("rm", caseCreatedEvent.getEvent().getChannel());

  }
}
