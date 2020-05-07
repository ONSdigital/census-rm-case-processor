package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.domain.Sort.Direction.ASC;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementQuestionnaireLinkedEvent;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;
import org.jeasy.random.EasyRandom;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.CreateUacQid;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.UacDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.testutil.RabbitQueueHelper;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@RunWith(SpringJUnit4ClassRunner.class)
public class QuestionnaireLinkedReceiverIT {

  private static final UUID TEST_CASE_ID = UUID.randomUUID();
  private static final UUID TEST_INDIVIDUAL_CASE_ID = UUID.randomUUID();
  private static final EasyRandom easyRandom = new EasyRandom();
  private static final String QUESTIONNAIRE_LINKED_CHANNEL = "FIELD";
  private static final String QUESTIONNAIRE_LINKED_SOURCE = "FIELDWORK_GATEWAY";
  private static final String ENGLAND_HOUSEHOLD_CONTINUATION = "11000121332321";

  @Value("${queueconfig.questionnaire-linked-inbound-queue}")
  private String questionnaireLinkedQueue;

  @Value("${queueconfig.action-scheduler-queue}")
  private String actionQueue;

  @Value("${queueconfig.rh-uac-queue}")
  private String rhUacQueue;

  @Value("${queueconfig.rh-case-queue}")
  private String rhCaseQueue;

  @Value("${queueconfig.unaddressed-inbound-queue}")
  private String unaddressedQueue;

  @Autowired private RabbitQueueHelper rabbitQueueHelper;
  @Autowired private CaseRepository caseRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private UacQidLinkRepository uacQidLinkRepository;

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(actionQueue);
    rabbitQueueHelper.purgeQueue(rhUacQueue);
    rabbitQueueHelper.purgeQueue(rhCaseQueue);
    rabbitQueueHelper.purgeQueue(unaddressedQueue);
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
  }

  @Test
  public void testGoodQuestionnaireLinkedForUnreceiptedCase() throws Exception {
    // GIVEN
    BlockingQueue<String> outboundUacQueue = rabbitQueueHelper.listen(rhUacQueue);

    Case testCase = easyRandom.nextObject(Case.class);
    testCase.setCaseId(TEST_CASE_ID);
    testCase.setReceiptReceived(false);
    testCase.setSurvey("CENSUS");
    testCase.setUacQidLinks(null);
    testCase.setEvents(null);
    caseRepository.saveAndFlush(testCase);

    // Send unaddressed uac message to create uac/qid unaddressed pair
    CreateUacQid createUacQid = new CreateUacQid();
    createUacQid.setQuestionnaireType("01");
    createUacQid.setBatchId(UUID.randomUUID());
    sendMessageAndExpectInboundMessage(unaddressedQueue, createUacQid, outboundUacQueue);

    // Get generated Questionnaire Id
    String expectedQuestionnaireId = uacQidLinkRepository.findAll().get(0).getQid();

    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();
    managementEvent.getEvent().setTransactionId(UUID.randomUUID());
    UacDTO uac = new UacDTO();
    uac.setCaseId(TEST_CASE_ID.toString());
    uac.setQuestionnaireId(expectedQuestionnaireId);
    managementEvent.getPayload().setUac(uac);

    // WHEN

    // Send questionnaire linked message and wait for uac updated message
    ResponseManagementEvent responseManagementEvent =
        sendMessageAndExpectInboundMessage(
            questionnaireLinkedQueue, managementEvent, outboundUacQueue);

    // THEN

    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.UAC_UPDATED);
    UacDTO actualUac = responseManagementEvent.getPayload().getUac();
    assertThat(actualUac.getQuestionnaireId()).isEqualTo(expectedQuestionnaireId);
    assertThat(actualUac.getCaseId()).isEqualTo(TEST_CASE_ID.toString());
    assertThat(actualUac.getActive()).isTrue();

    // Check database Case is still unreceipted and response received not set
    Case actualCase = caseRepository.findById(TEST_CASE_ID).get();
    assertThat(actualCase.isReceiptReceived()).isFalse();
    assertThat(actualCase.getSurvey()).isEqualTo("CENSUS");

    // Check database Case is now linked to questionnaire and still unreceipted
    List<UacQidLink> uacQidLinks = uacQidLinkRepository.findAll();
    assertThat(uacQidLinks.size()).isEqualTo(1);
    UacQidLink actualUacQidLink = uacQidLinks.get(0);
    assertThat(actualUacQidLink.getCaze().getCaseId()).isEqualTo(TEST_CASE_ID);
    assertThat(actualUacQidLink.isActive()).isTrue();

    validateEvents(eventRepository.findAll(), expectedQuestionnaireId, 2, true);
  }

  @Test
  public void testGoodQuestionnaireLinkedAfterCaseReceipted() throws Exception {
    // GIVEN
    BlockingQueue<String> outboundUacQueue = rabbitQueueHelper.listen(rhUacQueue);
    BlockingQueue<String> outboundCaseQueue = rabbitQueueHelper.listen(rhCaseQueue);

    Case testCase = easyRandom.nextObject(Case.class);
    testCase.setCaseId(TEST_CASE_ID);
    testCase.setReceiptReceived(true);
    testCase.setSurvey("CENSUS");
    testCase.setUacQidLinks(null);
    testCase.setEvents(null);
    caseRepository.saveAndFlush(testCase);

    // Send unaddressed uac message to create uac/qid unaddressed pair
    CreateUacQid createUacQid = new CreateUacQid();
    createUacQid.setQuestionnaireType("01");
    createUacQid.setBatchId(UUID.randomUUID());
    sendMessageAndExpectInboundMessage(unaddressedQueue, createUacQid, outboundUacQueue);

    // Get generated Questionnaire Id
    String expectedQuestionnaireId = uacQidLinkRepository.findAll().get(0).getQid();

    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();
    managementEvent.getEvent().setTransactionId(UUID.randomUUID());
    UacDTO uac = new UacDTO();
    uac.setCaseId(TEST_CASE_ID.toString());
    uac.setQuestionnaireId(expectedQuestionnaireId);
    managementEvent.getPayload().setUac(uac);

    // WHEN

    // Send questionnaire linked message and wait for uac updated message
    ResponseManagementEvent responseManagementEvent =
        sendMessageAndExpectInboundMessage(
            questionnaireLinkedQueue, managementEvent, outboundUacQueue);

    // THEN

    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.UAC_UPDATED);
    UacDTO actualUac = responseManagementEvent.getPayload().getUac();
    assertThat(actualUac.getQuestionnaireId()).isEqualTo(expectedQuestionnaireId);
    assertThat(actualUac.getCaseId()).isEqualTo(TEST_CASE_ID.toString());

    // Check Case updated message not sent
    rabbitQueueHelper.checkMessageIsNotReceived(outboundCaseQueue, 5);

    // Check database that Case is still receipted and has response received set
    Case actualCase = caseRepository.findById(TEST_CASE_ID).get();
    assertThat(actualCase.isReceiptReceived()).isTrue();
    assertThat(actualCase.getSurvey()).isEqualTo("CENSUS");

    // Check database that Case is now linked to questionnaire and still receipted
    List<UacQidLink> uacQidLinks = uacQidLinkRepository.findAll();
    assertThat(uacQidLinks.size()).isEqualTo(1);
    UacQidLink actualUacQidLink = uacQidLinks.get(0);
    assertThat(actualUacQidLink.getCaze().getCaseId()).isEqualTo(TEST_CASE_ID);

    validateEvents(eventRepository.findAll(), expectedQuestionnaireId, 2, true);
  }

  @Test
  public void testGoodQuestionnaireLinkedBeforeCaseReceipted() throws Exception {
    // GIVEN
    BlockingQueue<String> outboundUacQueue = rabbitQueueHelper.listen(rhUacQueue);
    BlockingQueue<String> outboundCaseQueue = rabbitQueueHelper.listen(rhCaseQueue);

    Case testCase = easyRandom.nextObject(Case.class);
    testCase.setCaseId(TEST_CASE_ID);
    testCase.setReceiptReceived(false);
    testCase.setSurvey("CENSUS");
    testCase.setUacQidLinks(null);
    testCase.setEvents(null);
    testCase.setCaseType("HH");
    testCase.setAddressLevel("U");
    caseRepository.saveAndFlush(testCase);

    // Send unaddressed uac message to create uac/qid unaddressed pair
    CreateUacQid createUacQid = new CreateUacQid();
    createUacQid.setQuestionnaireType("01");
    createUacQid.setBatchId(UUID.randomUUID());
    sendMessageAndExpectInboundMessage(unaddressedQueue, createUacQid, outboundUacQueue);

    // Get generated Questionnaire Id
    UacQidLink uacQidLink = uacQidLinkRepository.findAll().get(0);
    uacQidLink.setActive(false); // Simulate receipted
    uacQidLink.setCaze(testCase);
    uacQidLinkRepository.saveAndFlush(uacQidLink);
    String expectedQuestionnaireId = uacQidLink.getQid();

    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();
    managementEvent.getEvent().setTransactionId(UUID.randomUUID());
    UacDTO uac = new UacDTO();
    uac.setCaseId(TEST_CASE_ID.toString());
    uac.setQuestionnaireId(expectedQuestionnaireId);
    managementEvent.getPayload().setUac(uac);

    // WHEN

    // Send questionnaire linked message and wait for uac updated message
    ResponseManagementEvent responseManagementEvent =
        sendMessageAndExpectInboundMessage(
            questionnaireLinkedQueue, managementEvent, outboundUacQueue);

    // THEN

    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.UAC_UPDATED);
    UacDTO actualUac = responseManagementEvent.getPayload().getUac();
    assertThat(actualUac.getQuestionnaireId()).isEqualTo(expectedQuestionnaireId);
    assertThat(actualUac.getCaseId()).isEqualTo(TEST_CASE_ID.toString());

    // Check Case updated message sent and contains expected data
    responseManagementEvent = rabbitQueueHelper.checkExpectedMessageReceived(outboundCaseQueue);
    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.CASE_UPDATED);
    CollectionCase actualCollectionCase = responseManagementEvent.getPayload().getCollectionCase();
    assertThat(actualCollectionCase.getId()).isEqualTo(TEST_CASE_ID.toString());

    // Check database that Case is still receipted and has response received set
    Case actualCase = caseRepository.findById(TEST_CASE_ID).get();
    assertThat(actualCase.isReceiptReceived()).isTrue();
    assertThat(actualCase.getSurvey()).isEqualTo("CENSUS");

    // Check database that Case is now linked to questionnaire and still receipted
    List<UacQidLink> uacQidLinks = uacQidLinkRepository.findAll();
    assertThat(uacQidLinks.size()).isEqualTo(1);
    UacQidLink actualUacQidLink = uacQidLinks.get(0);
    assertThat(actualUacQidLink.getCaze().getCaseId()).isEqualTo(TEST_CASE_ID);

    validateEvents(eventRepository.findAll(), expectedQuestionnaireId, 2, true);
  }

  @Test
  public void testGoodIndividualQuestionnaireLinked() throws Exception {
    // GIVEN
    BlockingQueue<String> outboundUacQueue = rabbitQueueHelper.listen(rhUacQueue);
    BlockingQueue<String> outboundCaseQueue = rabbitQueueHelper.listen(rhCaseQueue);

    // Create HH (parent) case
    Case testHHCase = easyRandom.nextObject(Case.class);
    testHHCase.setCaseId(TEST_CASE_ID);
    testHHCase.setCaseType("HH");
    testHHCase.setReceiptReceived(false);
    testHHCase.setUacQidLinks(null);
    testHHCase.setEvents(null);
    caseRepository.saveAndFlush(testHHCase);

    // Send unaddressed uac message to create uac/qid unaddressed pair
    CreateUacQid createUacQid = new CreateUacQid();
    createUacQid.setQuestionnaireType("21");
    createUacQid.setBatchId(UUID.randomUUID());
    sendMessageAndExpectInboundMessage(unaddressedQueue, createUacQid, outboundUacQueue);

    // Get generated Questionnaire Id
    String expectedQuestionnaireId = uacQidLinkRepository.findAll().get(0).getQid();

    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();
    managementEvent.getEvent().setTransactionId(UUID.randomUUID());
    UacDTO uac = new UacDTO();
    uac.setCaseId(TEST_CASE_ID.toString());
    uac.setQuestionnaireId(expectedQuestionnaireId);
    uac.setIndividualCaseId(TEST_INDIVIDUAL_CASE_ID.toString());
    managementEvent.getPayload().setUac(uac);

    // WHEN

    // Send questionnaire linked message and wait for case (HI) created message
    ResponseManagementEvent responseManagementEvent =
        sendMessageAndExpectInboundMessage(
            questionnaireLinkedQueue, managementEvent, outboundCaseQueue);

    // THEN

    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.CASE_CREATED);
    CollectionCase actualCollectionCase = responseManagementEvent.getPayload().getCollectionCase();
    assertThat(actualCollectionCase.getCaseType()).isEqualTo("HI");

    // Check Uac updated message sent and contains expected data
    responseManagementEvent = rabbitQueueHelper.checkExpectedMessageReceived(outboundUacQueue);
    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.UAC_UPDATED);
    UacDTO actualUac = responseManagementEvent.getPayload().getUac();
    assertThat(actualUac.getQuestionnaireId()).isEqualTo(expectedQuestionnaireId);
    assertThat(actualUac.getCaseId()).isEqualTo(TEST_INDIVIDUAL_CASE_ID.toString());
    assertThat(actualUac.getActive()).isTrue();

    // Check database HI Case created as expected
    Case actualHHCase = caseRepository.findById(TEST_CASE_ID).get();
    Case actualHICase = caseRepository.findById(TEST_INDIVIDUAL_CASE_ID).get();
    assertThat(actualHICase.getCaseType()).isEqualTo("HI");
    assertThat(actualHICase.getUprn()).isEqualTo(actualHHCase.getUprn());
    assertThat(actualHICase.getEstabUprn()).isEqualTo(actualHHCase.getEstabUprn());

    // Check database that HI Case is linked to UacQidLink
    List<UacQidLink> uacQidLinks = uacQidLinkRepository.findAll();
    assertThat(uacQidLinks.size()).isEqualTo(1);
    UacQidLink actualUacQidLink = uacQidLinks.get(0);
    assertThat(actualUacQidLink.getQid()).isEqualTo(expectedQuestionnaireId);
    assertThat(actualHICase.getCaseRef()).isEqualTo(actualUacQidLink.getCaze().getCaseRef());

    List<Event> events = eventRepository.findAll(new Sort(ASC, "rmEventProcessed"));

    validateEvents(events, expectedQuestionnaireId, 2, true);
  }

  @Test
  public void testGoodIndividualQuestionnaireLinkedToCE() throws Exception {
    // GIVEN
    BlockingQueue<String> outboundUacQueue = rabbitQueueHelper.listen(rhUacQueue);
    BlockingQueue<String> outboundCaseQueue = rabbitQueueHelper.listen(rhCaseQueue);

    // Create CE (parent) case
    Case testHHCase = easyRandom.nextObject(Case.class);
    testHHCase.setCaseId(TEST_CASE_ID);
    testHHCase.setCaseType("CE");
    testHHCase.setReceiptReceived(false);
    testHHCase.setUacQidLinks(null);
    testHHCase.setEvents(null);
    caseRepository.saveAndFlush(testHHCase);

    // Send unaddressed uac message to create uac/qid unaddressed pair
    CreateUacQid createUacQid = new CreateUacQid();
    createUacQid.setQuestionnaireType("21");
    createUacQid.setBatchId(UUID.randomUUID());
    sendMessageAndExpectInboundMessage(unaddressedQueue, createUacQid, outboundUacQueue);

    // Get generated Questionnaire Id
    String expectedQuestionnaireId = uacQidLinkRepository.findAll().get(0).getQid();

    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();
    managementEvent.getEvent().setTransactionId(UUID.randomUUID());
    UacDTO uac = new UacDTO();
    uac.setCaseId(TEST_CASE_ID.toString());
    uac.setQuestionnaireId(expectedQuestionnaireId);
    managementEvent.getPayload().setUac(uac);

    // WHEN
    // Send questionnaire linked message
    sendMessageAndDoNotExpectInboundMessage(
        questionnaireLinkedQueue, managementEvent, outboundCaseQueue);

    // THEN
    Case actualHHCase = caseRepository.findById(TEST_CASE_ID).get();

    // Check database that CE Case is linked to UacQidLink
    List<UacQidLink> uacQidLinks = uacQidLinkRepository.findAll();
    assertThat(uacQidLinks.size()).isEqualTo(1);
    UacQidLink actualUacQidLink = uacQidLinks.get(0);
    assertThat(actualUacQidLink.getQid()).isEqualTo(expectedQuestionnaireId);
    assertThat(actualHHCase.getCaseRef()).isEqualTo(actualUacQidLink.getCaze().getCaseRef());

    List<Event> events = eventRepository.findAll(new Sort(ASC, "rmEventProcessed"));

    validateEvents(events, expectedQuestionnaireId, 2, false);
  }

  @Test
  public void testGoodIndividualQuestionnaireLinkedToSPG() throws Exception {
    // GIVEN
    BlockingQueue<String> outboundUacQueue = rabbitQueueHelper.listen(rhUacQueue);
    BlockingQueue<String> outboundCaseQueue = rabbitQueueHelper.listen(rhCaseQueue);

    // Create CE (parent) case
    Case testHHCase = easyRandom.nextObject(Case.class);
    testHHCase.setCaseId(TEST_CASE_ID);
    testHHCase.setCaseType("SPG");
    testHHCase.setReceiptReceived(false);
    testHHCase.setUacQidLinks(null);
    testHHCase.setEvents(null);
    caseRepository.saveAndFlush(testHHCase);

    // Send unaddressed uac message to create uac/qid unaddressed pair
    CreateUacQid createUacQid = new CreateUacQid();
    createUacQid.setQuestionnaireType("21");
    createUacQid.setBatchId(UUID.randomUUID());
    sendMessageAndExpectInboundMessage(unaddressedQueue, createUacQid, outboundUacQueue);

    // Get generated Questionnaire Id
    String expectedQuestionnaireId = uacQidLinkRepository.findAll().get(0).getQid();

    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();
    managementEvent.getEvent().setTransactionId(UUID.randomUUID());
    UacDTO uac = new UacDTO();
    uac.setCaseId(TEST_CASE_ID.toString());
    uac.setQuestionnaireId(expectedQuestionnaireId);
    managementEvent.getPayload().setUac(uac);

    // WHEN
    // Send questionnaire linked message
    sendMessageAndDoNotExpectInboundMessage(
        questionnaireLinkedQueue, managementEvent, outboundCaseQueue);

    // THEN
    Case actualHHCase = caseRepository.findById(TEST_CASE_ID).get();

    // Check database that SPG Case is linked to UacQidLink
    List<UacQidLink> uacQidLinks = uacQidLinkRepository.findAll();
    assertThat(uacQidLinks.size()).isEqualTo(1);
    UacQidLink actualUacQidLink = uacQidLinks.get(0);
    assertThat(actualUacQidLink.getQid()).isEqualTo(expectedQuestionnaireId);
    assertThat(actualHHCase.getCaseRef()).isEqualTo(actualUacQidLink.getCaze().getCaseRef());

    List<Event> events = eventRepository.findAll(new Sort(ASC, "rmEventProcessed"));

    validateEvents(events, expectedQuestionnaireId, 2, false);
  }

  @Test
  public void testContinuationQuestionnaireLinkedForUnreceiptedCaseButReceipedUacQid()
      throws Exception {
    // GIVEN
    BlockingQueue<String> outboundUacQueue = rabbitQueueHelper.listen(rhUacQueue);

    Case testCase = easyRandom.nextObject(Case.class);
    testCase.setCaseId(TEST_CASE_ID);
    testCase.setReceiptReceived(false);
    testCase.setSurvey("CENSUS");
    testCase.setUacQidLinks(null);
    testCase.setEvents(null);
    testCase.setCaseType("HH");
    testCase.setAddressLevel("U");
    caseRepository.saveAndFlush(testCase);

    UacQidLink receiptedContinuationUacQidLink = new UacQidLink();
    receiptedContinuationUacQidLink.setId(UUID.randomUUID());
    receiptedContinuationUacQidLink.setBatchId(UUID.randomUUID());
    receiptedContinuationUacQidLink.setUac("test uac");
    receiptedContinuationUacQidLink.setQid(ENGLAND_HOUSEHOLD_CONTINUATION);
    receiptedContinuationUacQidLink.setActive(false);
    UacQidLink createdUacQidLink = uacQidLinkRepository.save(receiptedContinuationUacQidLink);

    String expectedQuestionnaireId = createdUacQidLink.getQid();
    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();
    managementEvent.getEvent().setTransactionId(UUID.randomUUID());
    UacDTO uac = new UacDTO();
    uac.setCaseId(TEST_CASE_ID.toString());
    uac.setQuestionnaireId(expectedQuestionnaireId);
    managementEvent.getPayload().setUac(uac);

    // WHEN

    // Send questionnaire linked message and wait for uac updated message
    ResponseManagementEvent responseManagementEvent =
        sendMessageAndExpectInboundMessage(
            questionnaireLinkedQueue, managementEvent, outboundUacQueue);

    // THEN

    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.UAC_UPDATED);
    UacDTO actualUac = responseManagementEvent.getPayload().getUac();
    assertThat(actualUac.getQuestionnaireId()).isEqualTo(expectedQuestionnaireId);
    assertThat(actualUac.getCaseId()).isEqualTo(TEST_CASE_ID.toString());
    assertThat(actualUac.getActive()).isFalse();

    Case actualCase = caseRepository.findById(TEST_CASE_ID).get();
    assertThat(actualCase.isReceiptReceived()).isFalse();

    List<UacQidLink> uacQidLinks = uacQidLinkRepository.findAll();
    assertThat(uacQidLinks.size()).isEqualTo(1);
    UacQidLink actualUacQidLink = uacQidLinks.get(0);
    assertThat(actualUacQidLink.getCaze().getCaseId()).isEqualTo(TEST_CASE_ID);
    assertThat(actualUacQidLink.isActive()).isFalse();

    validateEvents(eventRepository.findAll(), expectedQuestionnaireId, 1, false);
  }

  @Test
  public void testQuestionnaireLinkedForUnreceiptedCaseButReceipedUacQid() throws Exception {
    // GIVEN
    BlockingQueue<String> outboundUacQueue = rabbitQueueHelper.listen(rhUacQueue);

    Case testCase = easyRandom.nextObject(Case.class);
    testCase.setCaseId(TEST_CASE_ID);
    testCase.setReceiptReceived(false);
    testCase.setSurvey("CENSUS");
    testCase.setUacQidLinks(null);
    testCase.setEvents(null);
    testCase.setCaseType("HH");
    testCase.setAddressLevel("U");
    caseRepository.saveAndFlush(testCase);

    UacQidLink receiptedContinuationUacQidLink = new UacQidLink();
    receiptedContinuationUacQidLink.setId(UUID.randomUUID());
    receiptedContinuationUacQidLink.setBatchId(UUID.randomUUID());
    receiptedContinuationUacQidLink.setUac("test uac");
    receiptedContinuationUacQidLink.setQid("01");
    receiptedContinuationUacQidLink.setActive(false);
    UacQidLink createdUacQidLink = uacQidLinkRepository.save(receiptedContinuationUacQidLink);

    String expectedQuestionnaireId = createdUacQidLink.getQid();
    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();
    managementEvent.getEvent().setTransactionId(UUID.randomUUID());
    UacDTO uac = new UacDTO();
    uac.setCaseId(TEST_CASE_ID.toString());
    uac.setQuestionnaireId(expectedQuestionnaireId);
    managementEvent.getPayload().setUac(uac);

    // WHEN

    // Send questionnaire linked message and wait for uac updated message
    ResponseManagementEvent responseManagementEvent =
        sendMessageAndExpectInboundMessage(
            questionnaireLinkedQueue, managementEvent, outboundUacQueue);

    // THEN

    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.UAC_UPDATED);
    UacDTO actualUac = responseManagementEvent.getPayload().getUac();
    assertThat(actualUac.getQuestionnaireId()).isEqualTo(expectedQuestionnaireId);
    assertThat(actualUac.getCaseId()).isEqualTo(TEST_CASE_ID.toString());
    assertThat(actualUac.getActive()).isFalse();

    Case actualCase = caseRepository.findById(TEST_CASE_ID).get();
    assertThat(actualCase.isReceiptReceived()).isTrue();

    List<UacQidLink> uacQidLinks = uacQidLinkRepository.findAll();
    assertThat(uacQidLinks.size()).isEqualTo(1);
    UacQidLink actualUacQidLink = uacQidLinks.get(0);
    assertThat(actualUacQidLink.getCaze().getCaseId()).isEqualTo(TEST_CASE_ID);
    assertThat(actualUacQidLink.isActive()).isFalse();

    validateEvents(eventRepository.findAll(), expectedQuestionnaireId, 1, false);
  }

  private void validateEvents(
      List<Event> events,
      String expectedQuestionnaireId,
      int execptedEventCount,
      boolean individualCaseIdExpected)
      throws JSONException {
    assertThat(events.size()).as("Event Count").isEqualTo(execptedEventCount);

    List<Event> linkedEventList =
        events.stream()
            .filter(e -> e.getEventType() == EventType.QUESTIONNAIRE_LINKED)
            .collect(Collectors.toList());
    assertThat(linkedEventList.size()).as("Linked Event Count").isEqualTo(1);

    Event event = linkedEventList.get(0);
    assertThat(event.getEventChannel()).isEqualTo(QUESTIONNAIRE_LINKED_CHANNEL);
    assertThat(event.getEventSource()).isEqualTo(QUESTIONNAIRE_LINKED_SOURCE);
    assertThat(event.getEventType()).isEqualTo(EventType.QUESTIONNAIRE_LINKED);
    assertThat(event.getEventDescription()).isEqualTo("Questionnaire Linked");

    JSONObject payload = new JSONObject(event.getEventPayload());
    assertThat(payload.getString("caseId")).isEqualTo(TEST_CASE_ID.toString());
    assertThat(payload.getString("questionnaireId")).isEqualTo(expectedQuestionnaireId);
    if (individualCaseIdExpected) {
      assertThat(payload.getString("individualCaseId"))
          .isEqualTo(TEST_INDIVIDUAL_CASE_ID.toString());
    }
  }

  private ResponseManagementEvent sendMessageAndExpectInboundMessage(
      String sendQueue, Object createUacQid, BlockingQueue<String> inboundQueue)
      throws IOException, InterruptedException {
    sendMessage(sendQueue, createUacQid);

    return rabbitQueueHelper.checkExpectedMessageReceived(inboundQueue);
  }

  private void sendMessageAndDoNotExpectInboundMessage(
      String sendQueue, Object createUacQid, BlockingQueue<String> inboundQueue)
      throws InterruptedException {
    sendMessage(sendQueue, createUacQid);

    rabbitQueueHelper.checkMessageIsNotReceived(inboundQueue, 5);
  }

  private void sendMessage(String sendQueue, Object createUacQid) {
    String json = convertObjectToJson(createUacQid);
    Message message =
        MessageBuilder.withBody(json.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();

    rabbitQueueHelper.sendMessage(sendQueue, message);
  }
}
