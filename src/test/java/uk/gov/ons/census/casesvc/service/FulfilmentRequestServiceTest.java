package uk.gov.ons.census.casesvc.service;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.model.entity.EventType.FULFILMENT_REQUESTED;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementEvent;

import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.FulfilmentRequestDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;

@RunWith(MockitoJUnitRunner.class)
public class FulfilmentRequestServiceTest {
  @Mock private EventLogger eventLogger;

  @Mock private CaseService caseService;

  @InjectMocks FulfilmentRequestService underTest;

  @Test
  public void testGoodFulfilmentRequest() {
    // Given
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    FulfilmentRequestDTO expectedFulfilmentRequest =
        managementEvent.getPayload().getFulfilmentRequest();

    Case expectedCase = getRandomCase();
    expectedFulfilmentRequest.setCaseId(expectedCase.getCaseId().toString());

    when(caseService.getCaseByCaseId(UUID.fromString(expectedFulfilmentRequest.getCaseId())))
        .thenReturn(expectedCase);

    // when
    underTest.processFulfilmentRequest(managementEvent);

    // then
    verify(eventLogger, times(1))
        .logCaseEvent(
            eq(expectedCase),
            eq(managementEvent.getEvent().getDateTime()),
            eq("Fulfilment Request Received"),
            eq(FULFILMENT_REQUESTED),
            eq(managementEvent.getEvent()),
            anyString());
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequest() {
    // Given
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    FulfilmentRequestDTO expectedFulfilmentRequest =
        managementEvent.getPayload().getFulfilmentRequest();
    expectedFulfilmentRequest.setFulfilmentCode("UACIT1");

    Case expectedCase = getRandomCase();
    expectedFulfilmentRequest.setCaseId(expectedCase.getCaseId().toString());
    Case expectedChildCase = getRandomCase();

    when(caseService.getCaseByCaseId(UUID.fromString(expectedFulfilmentRequest.getCaseId())))
        .thenReturn(expectedCase);
    when(caseService.prepareIndividualResponseCaseFromParentCase(expectedCase))
        .thenReturn(expectedChildCase);

    // when
    underTest.processFulfilmentRequest(managementEvent);

    // then
    verify(eventLogger, times(1))
        .logCaseEvent(
            eq(expectedCase),
            eq(managementEvent.getEvent().getDateTime()),
            eq("Fulfilment Request Received"),
            eq(FULFILMENT_REQUESTED),
            eq(managementEvent.getEvent()),
            anyString());

    verify(caseService).saveAndEmitCaseCreatedEvent(expectedChildCase);
  }
}
