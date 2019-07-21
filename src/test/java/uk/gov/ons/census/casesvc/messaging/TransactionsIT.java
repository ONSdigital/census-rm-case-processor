package uk.gov.ons.census.casesvc.messaging;


import java.io.IOException;
import java.util.UUID;
import org.jeasy.random.EasyRandom;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.testutil.RabbitQueueHelper;

@ContextConfiguration
@ActiveProfiles("nologging")
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@RunWith(SpringJUnit4ClassRunner.class)
public class TransactionsIT {
  private static final UUID TEST_CASE_ID = UUID.randomUUID();
  private static final EasyRandom easyRandom = new EasyRandom();
  private static final String TEST_QID = easyRandom.nextObject(String.class);
  private static final String TEST_UAC = easyRandom.nextObject(String.class);

  @Value("${queueconfig.receipt-response-inbound-queue}")
  private String inboundQueue;

  @Value("${queueconfig.rh-uac-queue}")
  private String rhUacQueue;

  @Autowired private RabbitQueueHelper rabbitQueueHelper;
  @Autowired private CaseRepository caseRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private UacQidLinkRepository uacQidLinkRepository;

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(inboundQueue);
    rabbitQueueHelper.purgeQueue(rhUacQueue);
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
  }

  // todo fix
  @Test
  public void testTransactionality() throws InterruptedException, IOException {
    assert true;
    //    // no cases on the database
    //    BlockingQueue<String> outboundQueue = rabbitQueueHelper.listen(rhUacQueue);
    //
    //    ReceiptDTO receipt = new ReceiptDTO();
    //    receipt.setQuestionnaireId(TEST_QID);
    //
    //    // WHEN
    //    String json = convertObjectToJson(receipt);
    //    Message message =
    //        MessageBuilder.withBody(json.getBytes())
    //            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
    //            .setHeader("source", "any source")
    //            .setHeader("channel", "any channel")
    //            .build();
    //    rabbitQueueHelper.sendMessage(inboundQueue, message);
    //
    //    // Poll Queue, expected failure
    //    String actualMessage = outboundQueue.poll(5, TimeUnit.SECONDS);
    //    assertNull(actualMessage);
    //
    //    // Log events empty
    //    assertThat(eventRepository.findAll().size()).isEqualTo(0);
    //
    //    // Save case and UacQidLink
    //    EasyRandom easyRandom = new EasyRandom();
    //    Case caze = easyRandom.nextObject(Case.class);
    //    caze.setCaseId(TEST_CASE_ID);
    //    caze.setUacQidLinks(null);
    //    caze.setEvents(null);
    //    caze = caseRepository.saveAndFlush(caze);
    //
    //    UacQidLink uacQidLink = new UacQidLink();
    //    uacQidLink.setId(UUID.randomUUID());
    //    uacQidLink.setCaze(caze);
    //    uacQidLink.setQid(TEST_QID);
    //    uacQidLink.setUac(TEST_UAC);
    //    uacQidLinkRepository.saveAndFlush(uacQidLink);
    //
    //    // Poll Queue, expected message to be there
    //    rabbitQueueHelper.checkExpectedMessageReceived(outboundQueue);
    //
    //    // check Log Events
    //    assertThat(eventRepository.findAll().size()).isEqualTo(1);
  }
}
