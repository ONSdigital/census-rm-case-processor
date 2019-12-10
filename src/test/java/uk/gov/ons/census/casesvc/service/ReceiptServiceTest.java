package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.service.ReceiptService.QID_RECEIPTED;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.*;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@RunWith(MockitoJUnitRunner.class)
public class ReceiptServiceTest {
  private final static String TEST_CCS_QID_ID = "7134567890123456";
  private final static String TEST_QID = "213456787654567";
  private final static String TEST_QID_2 = "213456787654568";
  private final static String TEST_UAC = "123456789012233";
  private final static String TEST_UAC_2 = "123456789012237";


  @Mock
  private CaseService caseService;

  @Mock
  private UacService uacService;

  @Mock
  private EventLogger eventLogger;

  @Mock
  private FieldworkFollowupService fieldworkFollowupService;

  @InjectMocks
  ReceiptService underTest;

  @Test
  public void testReceiptForNonCCSCase() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    ResponseDTO expectedReceipt = managementEvent.getPayload().getResponse();

    // Given
    Case expectedCase = getRandomCase();
    expectedCase.setReceiptReceived(false);
    expectedCase.setCcsCase(false);
    UacQidLink expectedUacQidLink = generateUacQidLinkedToCase(expectedCase);

    managementEvent.getPayload().getResponse().setResponseDateTime(OffsetDateTime.now());
    managementEvent.getPayload().getResponse().setUnreceipt(false);

    when(uacService.findByQid(expectedReceipt.getQuestionnaireId())).thenReturn(expectedUacQidLink);

    // when
    underTest.processReceipt(managementEvent);

    // then
    verify(uacService).findByQid(anyString());

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase.isReceiptReceived()).isTrue();
    assertThat(actualCase.isCcsCase()).isFalse();
    verifyNoMoreInteractions(caseService);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getQid()).isEqualTo(expectedUacQidLink.getQid());
    assertThat(actualUacQidLink.getUac()).isEqualTo(expectedUacQidLink.getUac());
    assertThat(actualUacQidLink.isReceipted()).isTrue();

    verify(eventLogger)
            .logUacQidEvent(
                    eq(expectedUacQidLink),
                    any(OffsetDateTime.class),
                    eq(QID_RECEIPTED),
                    eq(EventType.RESPONSE_RECEIVED),
                    eq(managementEvent.getEvent()),
                    anyString());
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void testReceiptForCCSCase() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    ResponseDTO expectedReceipt = managementEvent.getPayload().getResponse();

    // Given
    Case expectedCase = getRandomCase();
    expectedCase.setReceiptReceived(false);
    expectedCase.setCcsCase(true);
    UacQidLink expectedUacQidLink = generateRandomUacQidLinkedToCase(expectedCase);
    expectedUacQidLink.setQid(TEST_CCS_QID_ID);

    managementEvent.getPayload().getResponse().setResponseDateTime(OffsetDateTime.now());

    when(uacService.findByQid(expectedReceipt.getQuestionnaireId())).thenReturn(expectedUacQidLink);

    // when
    underTest.processReceipt(managementEvent);

    // then
    verify(uacService).findByQid(anyString());

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveCase(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase.isReceiptReceived()).isTrue();
    assertThat(actualCase.isCcsCase()).isTrue();
    verifyNoMoreInteractions(caseService);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService).saveUacQidLink(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getQid()).isEqualTo(expectedUacQidLink.getQid());
    assertThat(actualUacQidLink.getUac()).isEqualTo(expectedUacQidLink.getUac());

    verify(eventLogger)
            .logUacQidEvent(
                    eq(expectedUacQidLink),
                    any(OffsetDateTime.class),
                    eq(QID_RECEIPTED),
                    eq(EventType.RESPONSE_RECEIVED),
                    eq(managementEvent.getEvent()),
                    anyString());
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void blankQuestionnaireHappyPath() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    ResponseDTO expectedReceipt = managementEvent.getPayload().getResponse();

    // Given
    Case expectedCase = getRandomCase();
    expectedCase.setReceiptReceived(true);
    expectedCase.setCcsCase(false);
    UacQidLink expectedUacQidLink = generateUacQidLinkedToCase(expectedCase);

    managementEvent.getPayload().getResponse().setResponseDateTime(OffsetDateTime.now());
    managementEvent.getPayload().getResponse().setUnreceipt(true);

    when(uacService.findByQid(expectedReceipt.getQuestionnaireId())).thenReturn(expectedUacQidLink);

    // when
    underTest.processReceipt(managementEvent);

    // then
    verify(uacService).findByQid(anyString());

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase.isReceiptReceived()).isFalse();
    assertThat(actualCase.isCcsCase()).isFalse();
    verifyNoMoreInteractions(caseService);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getQid()).isEqualTo(expectedUacQidLink.getQid());
    assertThat(actualUacQidLink.getUac()).isEqualTo(expectedUacQidLink.getUac());
    assertThat(actualUacQidLink.isBlankQuestionnaireReceived()).isTrue();
    assertThat(actualUacQidLink.isReceipted()).isFalse();

    verify(fieldworkFollowupService).ifIUnreceiptedNeedsNewFieldWorkFolloup(caseArgumentCaptor.capture());
    assertThat(caseArgumentCaptor.getValue().getCaseId()).isEqualTo(expectedCase.getCaseId());

    verify(eventLogger)
            .logUacQidEvent(
                    eq(expectedUacQidLink),
                    any(OffsetDateTime.class),
                    eq(QID_RECEIPTED),
                    eq(EventType.RESPONSE_RECEIVED),
                    eq(managementEvent.getEvent()),
                    anyString());
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void blankQuestionnaireReceivedQMBeforePQRS() {
    ResponseManagementEvent managementEvent = createResponseReceivedEvent(true);
    ResponseDTO expectedReceipt = managementEvent.getPayload().getResponse();

    // Given
    Case expectedCase = getRandomCase();
    expectedCase.setReceiptReceived(false);
    expectedCase.setCcsCase(false);
    UacQidLink expectedUacQidLink = generateUacQidLinkedToCase(expectedCase);

    managementEvent.getPayload().getResponse().setResponseDateTime(OffsetDateTime.now());
    managementEvent.getPayload().getResponse().setUnreceipt(true);

    when(uacService.findByQid(expectedReceipt.getQuestionnaireId())).thenReturn(expectedUacQidLink);

    // when
    underTest.processReceipt(managementEvent);

    // then
    verify(uacService).findByQid(anyString());

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase.isReceiptReceived()).isFalse();
    assertThat(actualCase.isCcsCase()).isFalse();

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getQid()).isEqualTo(expectedUacQidLink.getQid());
    assertThat(actualUacQidLink.getUac()).isEqualTo(expectedUacQidLink.getUac());
    assertThat(actualUacQidLink.isBlankQuestionnaireReceived()).isTrue();

    verify(eventLogger)
            .logUacQidEvent(
                    eq(expectedUacQidLink),
                    any(OffsetDateTime.class),
                    eq(QID_RECEIPTED),
                    eq(EventType.RESPONSE_RECEIVED),
                    eq(managementEvent.getEvent()),
                    anyString());

    expectedUacQidLink.setBlankQuestionnaireReceived(true);
    ResponseManagementEvent pQRSReceiptmanagementEvent = createResponseReceivedEvent(false);

    // when, sending in another response for PQRS which is a receipt
    underTest.processReceipt(pQRSReceiptmanagementEvent);
    verify(uacService, times(2)).findByQid(TEST_QID);

    // then,
    verifyNoMoreInteractions(caseService, uacService);
    // Need to look into why event logger is throwing weird errors
    verify(eventLogger)
        .logUacQidEvent(
                eq(expectedUacQidLink),
                any(OffsetDateTime.class),
                eq(QID_RECEIPTED),
                eq(EventType.RESPONSE_RECEIVED),
                eq(managementEvent.getEvent()),
                anyString());
  }

  @Test
  public void blankQuestionnaireReceivedQMBeforePQRSMultipleQIDs() {
    // Given
    Case expectedCase = getRandomCase();
    expectedCase.setReceiptReceived(true);
    expectedCase.setCcsCase(false);
    UacQidLink existingReceiptedQidUacLink = generateUacQidLinkedToCase(expectedCase, TEST_QID_2, TEST_UAC_2);
    existingReceiptedQidUacLink.setReceipted(true);
    UacQidLink qidUacToReceiveBlankQuestionnaire = generateUacQidLinkedToCase(expectedCase);

    ResponseManagementEvent unreceiptingQuestionnaireEvent = createResponseReceivedEvent(true);
    ResponseDTO expectedReceipt = unreceiptingQuestionnaireEvent.getPayload().getResponse();
    unreceiptingQuestionnaireEvent.getPayload().getResponse().setResponseDateTime(OffsetDateTime.now());
    unreceiptingQuestionnaireEvent.getPayload().getResponse().setUnreceipt(true);

    when(uacService.findByQid(expectedReceipt.getQuestionnaireId())).thenReturn(qidUacToReceiveBlankQuestionnaire);

    // when
    underTest.processReceipt(unreceiptingQuestionnaireEvent);

    // then
    verify(uacService).findByQid(TEST_QID);

    // There is already a valid different QID receipted
    // The case should remain receipted, send nothing to field, mark this qid as blankQuestionnaire Received

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getQid()).isEqualTo(qidUacToReceiveBlankQuestionnaire.getQid());
    assertThat(actualUacQidLink.getUac()).isEqualTo(qidUacToReceiveBlankQuestionnaire.getUac());
    assertThat(actualUacQidLink.isBlankQuestionnaireReceived()).isTrue();
    assertThat(actualUacQidLink.isReceipted()).isFalse();

    //Case shouldn't be updated, nothing should be sent to fieldwork
    verifyZeroInteractions(caseService, fieldworkFollowupService);

    ResponseManagementEvent PQRSReceiptingEvent = createResponseReceivedEvent(false);
    ResponseDTO expectedPQRSReceipt = PQRSReceiptingEvent.getPayload().getResponse();
    PQRSReceiptingEvent.getPayload().getResponse().setResponseDateTime(OffsetDateTime.now());
    unreceiptingQuestionnaireEvent.getPayload().getResponse().setUnreceipt(false);

    underTest.processReceipt(PQRSReceiptingEvent);

    verify(uacService, times(2)).findByQid(TEST_QID);

    // There is already a valid different QID receipted
    // The case should remain receipted, send nothing to field, mark this qid as blankQuestionnaire Received

    verifyZeroInteractions(uacService, caseService, fieldworkFollowupService);

    verify(eventLogger)
            .logUacQidEvent(
                    eq(qidUacToReceiveBlankQuestionnaire),
                    any(OffsetDateTime.class),
                    eq(QID_RECEIPTED),
                    eq(EventType.RESPONSE_RECEIVED),
                    eq(unreceiptingQuestionnaireEvent.getEvent()),
                    anyString());
  }

  //scenario 5, not sure that PQRS B should result in a Cancel
  @Test
  public void blankQuestionnaieBwhenAalreadyReceipedSuccessfullyMeansNoMoreActionCreateEvents() {
    //Given
    Case expectedCase = getRandomCase();
    expectedCase.setReceiptReceived(true);
    expectedCase.setCcsCase(false);
    UacQidLink existingReceiptedQidUacLink = generateUacQidLinkedToCase(expectedCase, TEST_QID_2, TEST_UAC_2);
    existingReceiptedQidUacLink.setReceipted(true);
    UacQidLink qidUacToReceiveBlankQuestionnaire = generateUacQidLinkedToCase(expectedCase);

    ResponseManagementEvent receiptingQuestionnaireEventB = createResponseReceivedEvent(false);
    ResponseManagementEvent pqrsReceiptB = createResponseReceivedEvent(true);
    ResponseDTO expectedReceiptB = receiptingQuestionnaireEventB.getPayload().getResponse();
    receiptingQuestionnaireEventB.getPayload().getResponse().setResponseDateTime(OffsetDateTime.now());
    receiptingQuestionnaireEventB.getPayload().getResponse().setUnreceipt(false);

    when(uacService.findByQid(expectedReceiptB.getQuestionnaireId())).thenReturn(qidUacToReceiveBlankQuestionnaire);

    // when
    underTest.processReceipt(receiptingQuestionnaireEventB);

    // then send in PQRS receipt for qid B
    verify(uacService).findByQid(TEST_QID);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getQid()).isEqualTo(qidUacToReceiveBlankQuestionnaire.getQid());
    assertThat(actualUacQidLink.isBlankQuestionnaireReceived()).isFalse();
    assertThat(actualUacQidLink.isReceipted()).isTrue();

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase.getCaseId()).isEqualTo(expectedCase.getCaseId());
    assertThat(actualCase.isReceiptReceived()).isTrue();

    //Nothing should be sent to fieldwork
    verifyZeroInteractions(fieldworkFollowupService);

    //Now send in a QM blank questionnaire for qid B
    ResponseManagementEvent unreceiptingQuestionnaireEvent = createResponseReceivedEvent(true);
    ResponseDTO expectedReceipt = unreceiptingQuestionnaireEvent.getPayload().getResponse();
    unreceiptingQuestionnaireEvent.getPayload().getResponse().setResponseDateTime(OffsetDateTime.now());
    unreceiptingQuestionnaireEvent.getPayload().getResponse().setUnreceipt(true);

    when(uacService.findByQid(expectedReceipt.getQuestionnaireId())).thenReturn(qidUacToReceiveBlankQuestionnaire);

    // when
    underTest.processReceipt(unreceiptingQuestionnaireEvent);

    // then
    verify(uacService, times(2)).findByQid(TEST_QID);

    //Update that UACQidLink
    uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService, times(2)).saveAndEmitUacUpdatedEvent(uacQidLinkCaptor.capture());
    actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getQid()).isEqualTo(qidUacToReceiveBlankQuestionnaire.getQid());
    assertThat(actualUacQidLink.isBlankQuestionnaireReceived()).isTrue();
    assertThat(actualUacQidLink.isReceipted()).isFalse();

    //Do not update the case or send anything to field
    verifyZeroInteractions(caseService, fieldworkFollowupService);
  }

  private UacQidLink generateUacQidLinkedToCase(Case expectedCase, String testQid, String testUac) {
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(testQid);
    uacQidLink.setUac(testUac);
    uacQidLink.setCaze(expectedCase);
    uacQidLink.setEvents(null);
    uacQidLink.setBlankQuestionnaireReceived(false);
    uacQidLink.setReceipted(false);
    expectedCase.getUacQidLinks().add(uacQidLink);

    return uacQidLink;
  }
  
  @Test
  public void handleUnlinkedBlankQuestionnaireStuff() {
    assertFalse(true);
  }

  @Test
  public void blankQuestionnaireForAnotherUACQIDPair() {
    // Given
    Case expectedCase = getRandomCase();

    expectedCase.setReceiptReceived(false);
    expectedCase.setCcsCase(false);
    UacQidLink blankUacQidPairLink = generateUacQidLinkedToCase(expectedCase, TEST_QID_2, TEST_UAC_2);
    blankUacQidPairLink.setActive(false);
    blankUacQidPairLink.setBlankQuestionnaireReceived(true);
    blankUacQidPairLink.setReceipted(false);

    UacQidLink newPQRSLink = generateUacQidLinkedToCase(expectedCase);

    ResponseManagementEvent newPQRSEvent = getTestResponseManagementEvent();
    ResponseDTO expectedReceipt = newPQRSEvent.getPayload().getResponse();
    newPQRSEvent.getPayload().getResponse().setResponseDateTime(OffsetDateTime.now());
    newPQRSEvent.getPayload().getResponse().setUnreceipt(false);

    when(uacService.findByQid(expectedReceipt.getQuestionnaireId())).thenReturn(newPQRSLink);

    // when
    underTest.processReceipt(newPQRSEvent);

    // then
    verify(uacService).findByQid(anyString());

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase.isReceiptReceived()).isTrue();
    verifyNoMoreInteractions(caseService);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getQid()).isEqualTo(newPQRSLink.getQid());
    assertThat(actualUacQidLink.getUac()).isEqualTo(newPQRSLink.getUac());
    assertThat(actualUacQidLink.isReceipted()).isTrue();


    verify(eventLogger)
            .logUacQidEvent(
                    eq(newPQRSLink),
                    any(OffsetDateTime.class),
                    eq(QID_RECEIPTED),
                    eq(EventType.RESPONSE_RECEIVED),
                    eq(newPQRSEvent.getEvent()),
                    anyString());
    verifyNoMoreInteractions(eventLogger);
  }


  private UacQidLink generateUacQidLinkedToCase(Case linkedCase) {
    return generateUacQidLinkedToCase(linkedCase, TEST_QID, TEST_UAC);
  }

  private ResponseManagementEvent createResponseReceivedEvent(boolean unreceiped) {
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();

    EventDTO event = new EventDTO();
    event.setType(EventTypeDTO.RESPONSE_RECEIVED);
    event.setSource("RECEIPT_SERVICE");
    event.setChannel("DUMMY");
    event.setDateTime(OffsetDateTime.now());
    managementEvent.setEvent(event);

    ResponseDTO responseDTO = new ResponseDTO();
    responseDTO.setQuestionnaireId(TEST_QID);
    responseDTO.setUnreceipt(unreceiped);

    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setResponse(responseDTO);
    managementEvent.setPayload(payloadDTO);

    managementEvent.getEvent().setTransactionId(UUID.randomUUID());

    return managementEvent;
  }
}
