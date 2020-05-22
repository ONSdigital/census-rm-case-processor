package uk.gov.ons.census.casesvc.service;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.FULFILMENT_CONFIRMED;

import java.time.OffsetDateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.FulfilmentInformation;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@RunWith(MockitoJUnitRunner.class)
public class FulfilmentConfirmedServiceTest {
  @Mock private CaseService caseService;
  @Mock private UacService uacService;
  @Mock private EventLogger eventLogger;

  @InjectMocks FulfilmentConfirmedService underTest;

  @Test
  public void testQMFulfilmentConfirmed() {
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();
    managementEvent.setEvent(new EventDTO());
    managementEvent.getEvent().setDateTime(OffsetDateTime.now());
    managementEvent.getEvent().setType(FULFILMENT_CONFIRMED);
    managementEvent.getEvent().setChannel("QM");
    managementEvent.setPayload(new PayloadDTO());
    managementEvent.getPayload().setFulfilmentInformation(new FulfilmentInformation());
    managementEvent.getPayload().getFulfilmentInformation().setQuestionnaireId("12345");
    managementEvent.getPayload().getFulfilmentInformation().setFulfilmentCode("ABC_XYZ_123");

    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    // Given
    UacQidLink uacQidLink = new UacQidLink();
    when(uacService.findByQid(anyString())).thenReturn(uacQidLink);

    // when
    underTest.processFulfilmentConfirmed(managementEvent, messageTimestamp);

    // then
    verify(eventLogger)
        .logUacQidEvent(
            eq(uacQidLink),
            any(OffsetDateTime.class),
            eq("Fulfilment Confirmed Received for pack code ABC_XYZ_123"),
            eq(EventType.FULFILMENT_CONFIRMED),
            eq(managementEvent.getEvent()),
            anyString(),
            eq(messageTimestamp));
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void testPPOFulfilmentConfirmed() {
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();
    managementEvent.setEvent(new EventDTO());
    managementEvent.getEvent().setDateTime(OffsetDateTime.now());
    managementEvent.getEvent().setType(FULFILMENT_CONFIRMED);
    managementEvent.getEvent().setChannel("PPO");
    managementEvent.setPayload(new PayloadDTO());
    managementEvent.getPayload().setFulfilmentInformation(new FulfilmentInformation());
    managementEvent.getPayload().getFulfilmentInformation().setCaseRef("12345");
    managementEvent.getPayload().getFulfilmentInformation().setFulfilmentCode("ABC_XYZ_123");

    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    // Given
    Case caze = new Case();
    when(caseService.getCaseByCaseRef(anyLong())).thenReturn(caze);

    // when
    underTest.processFulfilmentConfirmed(managementEvent, messageTimestamp);

    // then
    verify(eventLogger)
        .logCaseEvent(
            eq(caze),
            any(OffsetDateTime.class),
            eq("Fulfilment Confirmed Received for pack code ABC_XYZ_123"),
            eq(EventType.FULFILMENT_CONFIRMED),
            eq(managementEvent.getEvent()),
            anyString(),
            eq(messageTimestamp));
    verifyNoMoreInteractions(eventLogger);
  }
}
