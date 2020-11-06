package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.model.entity.RefusalType.HARD_REFUSAL;
import static uk.gov.ons.census.casesvc.testutil.MessageConstructor.constructMessageWithValidTimeStamp;
import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;

import java.time.OffsetDateTime;
import org.jeasy.random.EasyRandom;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.*;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.service.CaseService;
import uk.gov.ons.census.casesvc.service.UacService;
import uk.gov.ons.census.casesvc.testutil.DataUtils;
import uk.gov.ons.census.casesvc.utility.MsgDateHelper;

public class UndeliveredMailReceiverTest {
  private static final long TEST_CASE_REF = 1234567890L;
  private EasyRandom easyRandom = new EasyRandom();

  @Test
  public void testReceiveMessageWithCaseRef() {
    ResponseManagementEvent event = new ResponseManagementEvent();
    event.setEvent(new EventDTO());
    event.getEvent().setDateTime(OffsetDateTime.now());
    event.getEvent().setType(EventTypeDTO.UNDELIVERED_MAIL_REPORTED);
    event.setPayload(new PayloadDTO());
    event.getPayload().setFulfilmentInformation(new FulfilmentInformation());
    event.getPayload().getFulfilmentInformation().setCaseRef(Long.toString(TEST_CASE_REF));

    Case caze = easyRandom.nextObject(Case.class);
    caze.setAddressInvalid(false);
    caze.setRefusalReceived(null);
    caze.setReceiptReceived(false);

    UacService uacService = mock(UacService.class);
    CaseService caseService = mock(CaseService.class);
    EventLogger eventLogger = mock(EventLogger.class);
    UndeliveredMailReceiver underTest =
        new UndeliveredMailReceiver(uacService, caseService, eventLogger);

    Message<ResponseManagementEvent> message = constructMessageWithValidTimeStamp(event);
    OffsetDateTime expectedDate = MsgDateHelper.getMsgTimeStamp(message);

    // Given
    when(caseService.getCaseByCaseRef(eq(TEST_CASE_REF))).thenReturn(caze);

    // When
    underTest.receiveMessage(message);

    // Then
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    Metadata expectedMetadata =
        buildMetadata(EventTypeDTO.UNDELIVERED_MAIL_REPORTED, ActionInstructionType.UPDATE);
    verify(caseService)
        .saveCaseAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture(), eq(expectedMetadata));
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase).isEqualTo(caze);

    verify(eventLogger)
        .logCaseEvent(
            eq(caze),
            eq(event.getEvent().getDateTime()),
            eq("Undelivered mail reported"),
            eq(EventType.UNDELIVERED_MAIL_REPORTED),
            eq(event.getEvent()),
            eq(event.getPayload().getFulfilmentInformation()),
            eq(expectedDate));

    verify(eventLogger, never()).logUacQidEvent(any(), any(), any(), any(), any(), any(), any());
    verify(uacService, never()).findByQid(any());
  }

  @Test
  public void testReceiveMessageWithQid() {
    ResponseManagementEvent event = new ResponseManagementEvent();
    event.setEvent(new EventDTO());
    event.getEvent().setDateTime(OffsetDateTime.now());
    event.getEvent().setType(EventTypeDTO.UNDELIVERED_MAIL_REPORTED);
    event.setPayload(new PayloadDTO());
    event.getPayload().setFulfilmentInformation(new FulfilmentInformation());
    event.getPayload().getFulfilmentInformation().setQuestionnaireId("76543");

    Case caze = easyRandom.nextObject(Case.class);
    caze.setAddressInvalid(false);
    caze.setRefusalReceived(null);
    caze.setReceiptReceived(false);
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setCaze(caze);

    UacService uacService = mock(UacService.class);
    CaseService caseService = mock(CaseService.class);
    EventLogger eventLogger = mock(EventLogger.class);
    UndeliveredMailReceiver underTest =
        new UndeliveredMailReceiver(uacService, caseService, eventLogger);
    Message<ResponseManagementEvent> message = constructMessageWithValidTimeStamp(event);
    OffsetDateTime expectedDate = MsgDateHelper.getMsgTimeStamp(message);

    // Given
    when(uacService.findByQid(eq("76543"))).thenReturn(uacQidLink);

    // When
    underTest.receiveMessage(message);

    // Then
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    Metadata expectedMetadata =
        buildMetadata(EventTypeDTO.UNDELIVERED_MAIL_REPORTED, ActionInstructionType.UPDATE);
    verify(caseService)
        .saveCaseAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture(), eq(expectedMetadata));
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase).isEqualTo(caze);

    verify(eventLogger)
        .logUacQidEvent(
            eq(uacQidLink),
            eq(event.getEvent().getDateTime()),
            eq("Undelivered mail reported"),
            eq(EventType.UNDELIVERED_MAIL_REPORTED),
            eq(event.getEvent()),
            eq(event.getPayload().getFulfilmentInformation()),
            eq(expectedDate));

    verify(eventLogger, never()).logCaseEvent(any(), any(), any(), any(), any(), any(), any());
    verify(caseService, never()).getCaseByCaseRef(anyInt());
  }

  @Test
  public void testUpdateMessageNotSentForReceiptedRefusalInvalidNiCeCases() {
    ResponseManagementEvent event = new ResponseManagementEvent();
    event.setEvent(new EventDTO());
    event.getEvent().setDateTime(OffsetDateTime.now());
    event.getEvent().setType(EventTypeDTO.UNDELIVERED_MAIL_REPORTED);
    event.setPayload(new PayloadDTO());
    event.getPayload().setFulfilmentInformation(new FulfilmentInformation());
    event.getPayload().getFulfilmentInformation().setCaseRef(Long.toString(TEST_CASE_REF));

    Case caze = new Case();
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setCaze(caze);

    UacService uacService = mock(UacService.class);
    CaseService caseService = mock(CaseService.class);
    EventLogger eventLogger = mock(EventLogger.class);
    UndeliveredMailReceiver underTest =
        new UndeliveredMailReceiver(uacService, caseService, eventLogger);
    Message<ResponseManagementEvent> message = constructMessageWithValidTimeStamp(event);

    // Given
    when(caseService.getCaseByCaseRef(eq(TEST_CASE_REF))).thenReturn(caze);

    // When

    // Scenario 1 - receipt received
    caze.setReceiptReceived(true);
    underTest.receiveMessage(message);
    caze.setReceiptReceived(false);

    // Scenario 2 - refusal received
    caze.setRefusalReceived(HARD_REFUSAL);
    underTest.receiveMessage(message);
    caze.setRefusalReceived(null);

    // Scenario 3 - invalid address
    caze.setAddressInvalid(true);
    underTest.receiveMessage(message);
    caze.setAddressInvalid(false);

    // Scenario 4 - NI CE
    caze.setRegion("N123456");
    caze.setCaseType("CE");
    underTest.receiveMessage(message);

    // Then
    verify(caseService, never()).saveCaseAndEmitCaseUpdatedEvent(any(), any());
  }

  @Test
  public void testUpdateMessageSentForNonNiCeCases() {
    ResponseManagementEvent event = new ResponseManagementEvent();
    event.setEvent(new EventDTO());
    event.getEvent().setDateTime(OffsetDateTime.now());
    event.getEvent().setType(EventTypeDTO.UNDELIVERED_MAIL_REPORTED);
    event.setPayload(new PayloadDTO());
    event.getPayload().setFulfilmentInformation(new FulfilmentInformation());
    event.getPayload().getFulfilmentInformation().setCaseRef(Long.toString(TEST_CASE_REF));

    Case caze = DataUtils.getCaseThatWillPassFieldWorkHelper();
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setCaze(caze);

    UacService uacService = mock(UacService.class);
    CaseService caseService = mock(CaseService.class);
    EventLogger eventLogger = mock(EventLogger.class);
    UndeliveredMailReceiver underTest =
        new UndeliveredMailReceiver(uacService, caseService, eventLogger);
    Message<ResponseManagementEvent> message = constructMessageWithValidTimeStamp(event);

    // Given
    caze.setReceiptReceived(false);
    caze.setRefusalReceived(null);
    caze.setAddressInvalid(false);
    caze.setRegion("E123456");
    caze.setCaseType("CE");
    caze.setFieldCoordinatorId("fieldCord123");

    when(caseService.getCaseByCaseRef(eq(TEST_CASE_REF))).thenReturn(caze);

    // When
    underTest.receiveMessage(message);

    // Then
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    Metadata expectedMetadata =
        buildMetadata(EventTypeDTO.UNDELIVERED_MAIL_REPORTED, ActionInstructionType.UPDATE);
    verify(caseService)
        .saveCaseAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture(), eq(expectedMetadata));
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase).isEqualTo(caze);
  }

  @Test
  public void testUpdateMessageNotSentForCasesMissingFieldCoordinatorId() {
    ResponseManagementEvent event = new ResponseManagementEvent();
    event.setEvent(new EventDTO());
    event.getEvent().setDateTime(OffsetDateTime.now());
    event.getEvent().setType(EventTypeDTO.UNDELIVERED_MAIL_REPORTED);
    event.setPayload(new PayloadDTO());
    event.getPayload().setFulfilmentInformation(new FulfilmentInformation());
    event.getPayload().getFulfilmentInformation().setCaseRef(Long.toString(TEST_CASE_REF));

    Case caze = new Case();
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setCaze(caze);

    UacService uacService = mock(UacService.class);
    CaseService caseService = mock(CaseService.class);
    EventLogger eventLogger = mock(EventLogger.class);
    UndeliveredMailReceiver underTest =
        new UndeliveredMailReceiver(uacService, caseService, eventLogger);
    Message<ResponseManagementEvent> message = constructMessageWithValidTimeStamp(event);

    // Given
    when(caseService.getCaseByCaseRef(eq(TEST_CASE_REF))).thenReturn(caze);

    // When
    caze.setReceiptReceived(false);
    caze.setRefusalReceived(null);
    caze.setAddressInvalid(false);
    caze.setRegion("E123456");
    caze.setCaseType("CE");
    caze.setFieldCoordinatorId(null);
    underTest.receiveMessage(message);

    // Then
    verify(caseService, never()).saveCaseAndEmitCaseUpdatedEvent(any(), any());
  }
}
