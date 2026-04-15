package uk.gov.ons.census.caseprocessor.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.caseprocessor.testutils.MessageConstructor.constructMessage;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_CORRELATION_ID;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_ORIGINATING_USER;
import static uk.gov.ons.census.caseprocessor.utils.Constants.OUTBOUND_EVENT_SCHEMA_VERSION;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import uk.gov.ons.census.caseprocessor.logging.EventLogger;
import uk.gov.ons.census.caseprocessor.model.dto.EmailConfirmation;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.model.dto.PayloadDTO;
import uk.gov.ons.census.caseprocessor.service.CaseService;
import uk.gov.ons.census.caseprocessor.service.UacService;
import uk.gov.ons.ssdc.common.model.entity.Case;
import uk.gov.ons.ssdc.common.model.entity.EventType;
import uk.gov.ons.ssdc.common.model.entity.UacQidLink;

@ExtendWith(MockitoExtension.class)
class EmailFulfilmentReceiverTest {

  @InjectMocks EmailFulfilmentReceiver underTest;

  @Mock CaseService caseService;
  @Mock UacService uacService;
  @Mock EventLogger eventLogger;

  private static final UUID CASE_ID = UUID.randomUUID();
  private static final String TEST_QID = "TEST_QID";
  private static final String TEST_UAC = "TEST_UAC";
  private static final String PACK_CODE = "TEST_EMAIL";
  private static final Map<String, String> TEST_UAC_METADATA = Map.of("TEST_UAC_METADATA", "TEST");
  private static final Map<String, String> TEST_PERSONALISATION = Map.of("foo", "bar");

  private static final String EMAIL_FULFILMENT_DESCRIPTION = "Email fulfilment request received";

  @Test
  void testReceiveMessageHappyPathWithUacQid() {
    // Given
    Case testCase = new Case();
    testCase.setId(CASE_ID);
    EventDTO event = buildEmailFulfilmentConfirmationEventWithUacQid();
    Message<byte[]> eventMessage = constructMessage(event);

    when(caseService.getCase(CASE_ID)).thenReturn(testCase);
    when(uacService.existsByQid(TEST_QID)).thenReturn(false);

    // When
    underTest.receiveMessage(eventMessage);

    // Then
    verify(uacService)
        .createLinkAndEmitNewUacQid(
            testCase,
            TEST_UAC,
            TEST_QID,
            TEST_UAC_METADATA,
            TEST_CORRELATION_ID,
            TEST_ORIGINATING_USER);
    verify(eventLogger)
        .logCaseEvent(
            testCase,
            EMAIL_FULFILMENT_DESCRIPTION,
            EventType.EMAIL_FULFILMENT,
            event,
            eventMessage);
  }

  @Test
  void testReceiveMessageHappyPathNoUacQid() {
    // Given
    Case testCase = new Case();
    testCase.setId(CASE_ID);
    EventDTO event = buildEmailFulfilmentConfirmationEvent();
    Message<byte[]> eventMessage = constructMessage(event);

    when(caseService.getCase(CASE_ID)).thenReturn(testCase);

    // When
    underTest.receiveMessage(eventMessage);

    // Then
    verifyNoInteractions(uacService);
    verify(eventLogger)
        .logCaseEvent(
            testCase,
            EMAIL_FULFILMENT_DESCRIPTION,
            EventType.EMAIL_FULFILMENT,
            event,
            eventMessage);
  }

  @Test
  void testReceiveMessageQidAlreadyLinkedToCorrectCase() {
    // Given
    Case testCase = new Case();
    testCase.setId(CASE_ID);
    EventDTO event = buildEmailFulfilmentConfirmationEventWithUacQid();
    Message<byte[]> eventMessage = constructMessage(event);

    UacQidLink existingUacQidLink = new UacQidLink();
    existingUacQidLink.setQid(TEST_QID);
    existingUacQidLink.setCaze(testCase);

    when(caseService.getCase(CASE_ID)).thenReturn(testCase);

    when(uacService.existsByQid(TEST_QID)).thenReturn(true);
    when(uacService.findByQid(TEST_QID)).thenReturn(existingUacQidLink);

    // When
    underTest.receiveMessage(eventMessage);

    // Then
    verify(uacService, never()).saveAndEmitUacUpdateEvent(any(), any(UUID.class), anyString());
    verifyNoInteractions(eventLogger);
  }

  @Test
  void testReceiveMessageQidAlreadyLinkedToOtherCase() {
    // Given
    Case testCase = new Case();
    testCase.setId(CASE_ID);

    Case otherCase = new Case();
    otherCase.setId(UUID.randomUUID());

    EventDTO event = buildEmailFulfilmentConfirmationEventWithUacQid();
    Message<byte[]> eventMessage = constructMessage(event);

    UacQidLink existingUacQidLink = new UacQidLink();
    existingUacQidLink.setQid(TEST_QID);
    existingUacQidLink.setCaze(otherCase);

    when(caseService.getCase(CASE_ID)).thenReturn(testCase);

    when(uacService.existsByQid(TEST_QID)).thenReturn(true);
    when(uacService.findByQid(TEST_QID)).thenReturn(existingUacQidLink);

    // When, then throws
    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> underTest.receiveMessage(eventMessage));
    assertThat(thrown.getMessage())
        .isEqualTo("Email fulfilment QID TEST_QID is already linked to a different case");
    verifyNoInteractions(eventLogger);
  }

  private EventDTO buildEmailFulfilmentConfirmationEventWithUacQid() {
    EventDTO event = buildEmailFulfilmentConfirmationEvent();
    event.getPayload().getEmailConfirmation().setUac(TEST_UAC);
    event.getPayload().getEmailConfirmation().setQid(TEST_QID);
    return event;
  }

  private EventDTO buildEmailFulfilmentConfirmationEvent() {
    EmailConfirmation emailConfirmation = new EmailConfirmation();
    emailConfirmation.setCaseId(CASE_ID);
    emailConfirmation.setPackCode(PACK_CODE);
    emailConfirmation.setUacMetadata(TEST_UAC_METADATA);
    emailConfirmation.setPersonalisation(TEST_PERSONALISATION);

    EventHeaderDTO eventHeader = new EventHeaderDTO();
    eventHeader.setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    eventHeader.setCorrelationId(TEST_CORRELATION_ID);
    eventHeader.setOriginatingUser(TEST_ORIGINATING_USER);
    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setEmailConfirmation(emailConfirmation);

    EventDTO event = new EventDTO();
    event.setHeader(eventHeader);
    event.setPayload(payloadDTO);

    return event;
  }
}
