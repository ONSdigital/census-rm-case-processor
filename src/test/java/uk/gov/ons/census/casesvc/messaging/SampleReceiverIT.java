package uk.gov.ons.census.casesvc.messaging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import uk.gov.ons.census.casesvc.model.dto.CaseCreatedEvent;
import uk.gov.ons.census.casesvc.model.dto.FooBar;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@RunWith(SpringJUnit4ClassRunner.class)
public class SampleReceiverIT {

  @Autowired RabbitQueueHelper rabbitQueueHelper;

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
    CaseCreatedEvent caseCreatedEvent =
        objectMapper.readValue(actualMessage, CaseCreatedEvent.class);
    assertNotNull(caseCreatedEvent);
    assertEquals("rm", caseCreatedEvent.getEvent().getChannel());

    actualMessage = queue2.poll(30, TimeUnit.SECONDS);
    caseCreatedEvent = objectMapper.readValue(actualMessage, CaseCreatedEvent.class);
    assertNotNull(caseCreatedEvent);
    assertEquals("rm", caseCreatedEvent.getEvent().getChannel());
  }
}
