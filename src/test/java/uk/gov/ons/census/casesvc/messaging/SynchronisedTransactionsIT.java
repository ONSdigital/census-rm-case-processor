package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.jeasy.random.EasyRandom;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
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
  @Autowired private ConnectionFactory connectionFactory;
  @Autowired Jackson2JsonMessageConverter messageConverter;

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(inboundQueue);
    rabbitQueueHelper.purgeQueue(rhCaseQueue);
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
  }

  // This test exists for the purpose of checking that we never commit a DB transaction without
  // also committing the Rabbit transaction, and vice-versa: the transactions need to synchronise
  // their commits AND rollbacks.
  @Test
  public void testTransactionSynchronisation() throws Exception {
    try (QueueSpy rhCaseQueueSpy = rabbitQueueHelper.listen(rhCaseQueue)) {
      RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
      rabbitTemplate.setMessageConverter(messageConverter);

      // GIVEN

      // WHEN
      Collection<CreateCaseSample> samples = Collections.synchronizedCollection(new LinkedList<>());
      IntStream.range(0, SIZE_OF_SAMPLE)
          .parallel()
          .forEach(
              i -> {
                CreateCaseSample createCaseSample = easyRandom.nextObject(CreateCaseSample.class);
                createCaseSample.setAddressType("HH");
                createCaseSample.setAddressLevel("U");
                createCaseSample.setRegion("E");
                createCaseSample.setBulkProcessed(false);
                rabbitTemplate.convertAndSend("", inboundQueue, createCaseSample);
                samples.add(createCaseSample);
              });

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
}
