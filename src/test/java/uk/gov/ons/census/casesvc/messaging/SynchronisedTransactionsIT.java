package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jeasy.random.EasyRandom;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.testutil.QueueSpy;
import uk.gov.ons.census.casesvc.testutil.RabbitQueueHelper;
import uk.gov.ons.census.casesvc.utility.ObjectMapperFactory;

@ContextConfiguration
@ActiveProfiles("synctxns")
@SpringBootTest
@RunWith(SpringJUnit4ClassRunner.class)
@Ignore // TODO: These tests don't cause the problem with the transactions, so pretty useless as is
public class SynchronisedTransactionsIT {
  private static final int SIZE_OF_SAMPLE = 50;
  private static final ObjectMapper objectMapper = ObjectMapperFactory.objectMapper();
  private static final EasyRandom easyRandom = new EasyRandom();

  @Value("${queueconfig.inbound-queue}")
  private String inboundQueue;

  @Value("${queueconfig.rh-case-queue}")
  private String rhCaseQueue;

  @Autowired private RabbitQueueHelper rabbitQueueHelper;
  @Autowired private CaseRepository caseRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private UacQidLinkRepository uacQidLinkRepository;

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(inboundQueue);
    rabbitQueueHelper.purgeQueue(rhCaseQueue);
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
  }

  @Test
  public void testTransactionSynchronisation() throws Exception {
    try (QueueSpy rhCaseQueueSpy = rabbitQueueHelper.listen(rhCaseQueue)) {
      // GIVEN

      // WHEN
      List<CreateCaseSample> samples = new LinkedList<>();
      for (int i = 0; i < SIZE_OF_SAMPLE; i++) {
        samples.add(sendSample());
      }

      List<String> messages = new LinkedList<>();

      String actualMessage = null;
      do {
        actualMessage = rhCaseQueueSpy.getQueue().poll(20, TimeUnit.SECONDS);
        if (actualMessage != null) {
          messages.add(actualMessage);
        }
      } while (actualMessage != null);

      assertThat(messages.size()).isEqualTo(SIZE_OF_SAMPLE);
      assertThat(caseRepository.findAll().size()).isEqualTo(SIZE_OF_SAMPLE);

      for (String message : messages) {
        ResponseManagementEvent responseManagementEvent =
            objectMapper.readValue(message, ResponseManagementEvent.class);

        CreateCaseSample foundSample = null;
        for (CreateCaseSample sample : samples) {
          if (sample
              .getAddressLine1()
              .equals(
                  responseManagementEvent
                      .getPayload()
                      .getCollectionCase()
                      .getAddress()
                      .getAddressLine1())) {
            foundSample = sample;
            break;
          }
        }

        if (foundSample != null) {
          samples.remove(foundSample);
        } else {
          assertTrue("Couldn't find sample - might have been a dupe", false);
        }
      }
    }
  }

  private CreateCaseSample sendSample() {
    CreateCaseSample createCaseSample = easyRandom.nextObject(CreateCaseSample.class);
    createCaseSample.setAddressType("HH");
    createCaseSample.setAddressLevel("U");
    createCaseSample.setRegion("E");
    createCaseSample.setBulkProcessed(false);

    String json = convertObjectToJson(createCaseSample);
    Message message =
        MessageBuilder.withBody(json.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();
    rabbitQueueHelper.sendMessage(inboundQueue, message);

    return createCaseSample;
  }
}
