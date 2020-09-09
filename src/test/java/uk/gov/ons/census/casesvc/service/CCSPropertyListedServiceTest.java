package uk.gov.ons.census.casesvc.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.Metadata;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementCCSAddressListedEvent;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

@RunWith(MockitoJUnitRunner.class)
public class CCSPropertyListedServiceTest {

  @Mock EventLogger eventLogger;
  @Mock CaseService caseService;
  @Mock UacService uacService;

  @InjectMocks CCSPropertyListedService underTest;

  @Test
  public void testCCSPropertyListedWitInterviewRequiredFalseNotSentToField() {
    // Given
    ResponseManagementEvent managementEvent = getTestResponseManagementCCSAddressListedEvent();
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    managementEvent.getPayload().getCcsProperty().setInterviewRequired(false);

    Case caze = new Case();
    caze.setCaseId(managementEvent.getPayload().getCollectionCase().getId());
    caze.setSurvey("CCS");

    when(caseService.createCCSCase(any(), any())).thenReturn(caze);

    // When
    underTest.processCCSPropertyListed(managementEvent, messageTimestamp);

    verify(caseService)
        .createCCSCase(
            managementEvent.getPayload().getCcsProperty().getCollectionCase().getId(),
            managementEvent.getPayload().getCcsProperty().getSampleUnit());

    verify(uacService).createUacQidLinkedToCCSCase(caze, managementEvent.getEvent());

    // Then
    checkCorrectEventLogging(caze, managementEvent, messageTimestamp);

    verifyNoMoreInteractions(caseService);
  }

  @Test
  public void testCCSPropertyListedWitInterviewRequireTrueIsSentToField() {
    // Given
    ResponseManagementEvent managementEvent = getTestResponseManagementCCSAddressListedEvent();
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    managementEvent.getPayload().getCcsProperty().setInterviewRequired(true);

    Case expectedCase = new Case();

    when(caseService.createCCSCase(any(), any())).thenReturn(expectedCase);

    // When
    underTest.processCCSPropertyListed(managementEvent, messageTimestamp);

    verify(caseService)
        .createCCSCase(
            managementEvent.getPayload().getCcsProperty().getCollectionCase().getId(),
            managementEvent.getPayload().getCcsProperty().getSampleUnit());

    verify(uacService).createUacQidLinkedToCCSCase(expectedCase, managementEvent.getEvent());

    ArgumentCaptor<Metadata> metadataCaptor = ArgumentCaptor.forClass(Metadata.class);

    verify(caseService).saveCaseAndEmitCaseCreatedEvent(eq(expectedCase), metadataCaptor.capture());

    Metadata actualMetadata = metadataCaptor.getValue();

    assertThat(actualMetadata.getCauseEventType()).isEqualTo(managementEvent.getEvent().getType());
    assertThat(actualMetadata.getFieldDecision()).isEqualTo(ActionInstructionType.CREATE);

    // Then
    checkCorrectEventLogging(expectedCase, managementEvent, messageTimestamp);

    verifyNoMoreInteractions(caseService);
  }

  //

  private void checkCorrectEventLogging(
      Case expectedCase, ResponseManagementEvent managementEvent, OffsetDateTime messageTimestamp) {
    ArgumentCaptor<String> ccsPayload = ArgumentCaptor.forClass(String.class);

    verify(eventLogger)
        .logCaseEvent(
            eq(expectedCase),
            any(OffsetDateTime.class),
            eq("CCS Address Listed"),
            eq(EventType.CCS_ADDRESS_LISTED),
            eq(managementEvent.getEvent()),
            ccsPayload.capture(),
            eq(messageTimestamp));

    String actualLoggedPayload = ccsPayload.getValue();
    assertThat(actualLoggedPayload)
        .isEqualTo(convertObjectToJson(managementEvent.getPayload().getCcsProperty()));
  }
}
