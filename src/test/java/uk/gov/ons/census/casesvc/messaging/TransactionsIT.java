package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNull;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementQuestionnaireLinkedEvent;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementReceiptEvent;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.util.UUID;
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
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.UacDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.testutil.QueueSpy;
import uk.gov.ons.census.casesvc.testutil.RabbitQueueHelper;

@ContextConfiguration
@ActiveProfiles("nologging")
@SpringBootTest
@RunWith(SpringJUnit4ClassRunner.class)
@Ignore // TODO: These tests seem to be causing problems with other integration tests
public class TransactionsIT {
  private static final UUID TEST_CASE_ID = UUID.randomUUID();
  private static final EasyRandom easyRandom = new EasyRandom();
  private static final String TEST_QID = "01234567890";
  private static final String TEST_UAC = easyRandom.nextObject(String.class);

  @Value("${queueconfig.receipt-response-inbound-queue}")
  private String receiptInboundQueue;

  @Value("${queueconfig.questionnaire-linked-inbound-queue}")
  private String questionnaireLinkedInboundQueue;

  @Value("${queueconfig.rh-uac-queue}")
  private String rhUacQueue;

  @Autowired private RabbitQueueHelper rabbitQueueHelper;
  @Autowired private CaseRepository caseRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private UacQidLinkRepository uacQidLinkRepository;

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(receiptInboundQueue);
    rabbitQueueHelper.purgeQueue(questionnaireLinkedInboundQueue);
    rabbitQueueHelper.purgeQueue(rhUacQueue);
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
  }

  @Test
  public void testReceiptTransactionality() throws Exception {
    try (QueueSpy rhUacQueueSpy = rabbitQueueHelper.listen(rhUacQueue)) {
      // GIVEN

      // WHEN
      ResponseManagementEvent managementEvent = getTestResponseManagementReceiptEvent();
      managementEvent.getPayload().getResponse().setQuestionnaireId(TEST_QID);
      managementEvent.getEvent().setTransactionId(UUID.randomUUID());

      String json = convertObjectToJson(managementEvent);
      Message message =
          MessageBuilder.withBody(json.getBytes())
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .build();
      rabbitQueueHelper.sendMessage(receiptInboundQueue, message);

      // Poll Queue, expected failure
      String actualMessage = rhUacQueueSpy.getQueue().poll(5, TimeUnit.SECONDS);
      assertNull(actualMessage);

      // Log events empty
      assertThat(eventRepository.findAll().size()).isEqualTo(0);

      // Save case and UacQidLink
      Case caze = easyRandom.nextObject(Case.class);
      caze.setCaseId(TEST_CASE_ID);
      caze.setUacQidLinks(null);
      caze.setEvents(null);
      caze.setCaseType("HH");
      caze.setAddressLevel("U");
      caze = caseRepository.saveAndFlush(caze);

      UacQidLink uacQidLink = new UacQidLink();
      uacQidLink.setId(UUID.randomUUID());
      uacQidLink.setCaze(caze);
      uacQidLink.setQid(TEST_QID);
      uacQidLink.setUac(TEST_UAC);
      uacQidLinkRepository.saveAndFlush(uacQidLink);

      // Poll Queue, expected message to be there
      rhUacQueueSpy.checkExpectedMessageReceived();

      // check Log Events
      assertThat(eventRepository.findAll().size()).isEqualTo(1);
    }
  }

  @Test
  public void testQuestionnaireLinkedTransactionality() throws Exception {
    try (QueueSpy rhUacQueueSpy = rabbitQueueHelper.listen(rhUacQueue)) {
      // GIVEN

      // WHEN
      ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();
      managementEvent.getEvent().setTransactionId(UUID.randomUUID());
      UacDTO uac = managementEvent.getPayload().getUac();
      uac.setCaseId(TEST_CASE_ID);
      uac.setQuestionnaireId(TEST_QID);

      String json = convertObjectToJson(managementEvent);
      Message message =
          MessageBuilder.withBody(json.getBytes())
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .build();
      rabbitQueueHelper.sendMessage(questionnaireLinkedInboundQueue, message);

      // Poll Queue, expected failure
      String actualMessage = rhUacQueueSpy.getQueue().poll(5, TimeUnit.SECONDS);
      assertNull(actualMessage);

      // Log events empty
      assertThat(eventRepository.findAll().size()).isEqualTo(0);

      // Save case
      Case caze = easyRandom.nextObject(Case.class);
      caze.setCaseId(TEST_CASE_ID);
      caze.setUacQidLinks(null);
      caze.setEvents(null);
      caze.setCaseType("HH");
      caze.setAddressLevel("U");
      caseRepository.saveAndFlush(caze);

      UacQidLink uacQidLink = new UacQidLink();
      uacQidLink.setId(UUID.randomUUID());
      uacQidLink.setQid(TEST_QID);
      uacQidLink.setUac(TEST_UAC);
      uacQidLinkRepository.saveAndFlush(uacQidLink);

      // Poll Queue, expected message to be there
      rhUacQueueSpy.checkExpectedMessageReceived();

      // check Log Events
      assertThat(eventRepository.findAll().size()).isEqualTo(1);
    }
  }
}
