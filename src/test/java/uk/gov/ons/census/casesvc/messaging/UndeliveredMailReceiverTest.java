package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.testutil.MessageConstructor.constructMessageWithValidTimeStamp;

import java.time.OffsetDateTime;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.FulfilmentInformation;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.service.CaseService;
import uk.gov.ons.census.casesvc.service.UacService;
import uk.gov.ons.census.casesvc.utility.MsgDateHelper;

public class UndeliveredMailReceiverTest {

  @Test
  public void testReceiveMessageWithCaseRef() {
    ResponseManagementEvent event = new ResponseManagementEvent();
    event.setEvent(new EventDTO());
    event.getEvent().setDateTime(OffsetDateTime.now());
    event.setPayload(new PayloadDTO());
    event.getPayload().setFulfilmentInformation(new FulfilmentInformation());
    event.getPayload().getFulfilmentInformation().setCaseRef("123");

    Case caze = new Case();

    UacService uacService = mock(UacService.class);
    CaseService caseService = mock(CaseService.class);
    EventLogger eventLogger = mock(EventLogger.class);
    UndeliveredMailReceiver underTest =
        new UndeliveredMailReceiver(uacService, caseService, eventLogger);

    Message<ResponseManagementEvent> message = constructMessageWithValidTimeStamp(event);
    OffsetDateTime expectedDate = MsgDateHelper.getMsgTimeStamp(message);

    // Given
    when(caseService.getCaseByCaseRef(eq(123))).thenReturn(caze);

    // When
    underTest.receiveMessage(message);

    // Then
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase).isEqualTo(caze);
    assertThat(caseArgumentCaptor.getValue().isUndeliveredAsAddressed()).isTrue();

    verify(eventLogger)
        .logCaseEvent(
            eq(caze),
            eq(event.getEvent().getDateTime()),
            eq("Undelivered mail reported"),
            eq(EventType.UNDELIVERED_MAIL_REPORTED),
            eq(event.getEvent()),
            eq("{\"caseRef\":\"123\"}"),
            eq(expectedDate));

    verify(eventLogger, never()).logUacQidEvent(any(), any(), any(), any(), any(), any(), any());
    verify(uacService, never()).findByQid(any());
  }

  @Test
  public void testReceiveMessageWithQid() {
    ResponseManagementEvent event = new ResponseManagementEvent();
    event.setEvent(new EventDTO());
    event.getEvent().setDateTime(OffsetDateTime.now());
    event.setPayload(new PayloadDTO());
    event.getPayload().setFulfilmentInformation(new FulfilmentInformation());
    event.getPayload().getFulfilmentInformation().setQuestionnaireId("76543");

    Case caze = new Case();
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
    verify(caseService).saveAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase).isEqualTo(caze);
    assertThat(caseArgumentCaptor.getValue().isUndeliveredAsAddressed()).isTrue();

    verify(eventLogger)
        .logUacQidEvent(
            eq(uacQidLink),
            eq(event.getEvent().getDateTime()),
            eq("Undelivered mail reported"),
            eq(EventType.UNDELIVERED_MAIL_REPORTED),
            eq(event.getEvent()),
            eq("{\"questionnaireId\":\"76543\"}"),
            eq(expectedDate));

    verify(eventLogger, never()).logCaseEvent(any(), any(), any(), any(), any(), any(), any());
    verify(caseService, never()).getCaseByCaseRef(anyInt());
  }
}
