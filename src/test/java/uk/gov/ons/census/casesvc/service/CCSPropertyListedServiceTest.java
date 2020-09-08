package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementCCSAddressListedEvent;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.*;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;

@RunWith(MockitoJUnitRunner.class)
public class CCSPropertyListedServiceTest {

  private static final UUID TEST_UAC_QID_LINK_ID = UUID.randomUUID();
  private static final String TEST_QID_1 = "710000000043";
  private static final String TEST_QID_2 = "610000000043";

  @Mock EventLogger eventLogger;
  @Mock CaseService caseService;
  @Mock UacService uacService;
  @Mock UacQidLinkRepository uacQidLinkRepository;

  @InjectMocks CCSPropertyListedService underTest;

  @Test
  public void testCCSPropertyListedWitInterviewRequiredFalseNotSentToField() {
    // Given
    ResponseManagementEvent managementEvent = getTestResponseManagementCCSAddressListedEvent();
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    CCSPropertyDTO ccsPropertyDTO = new CCSPropertyDTO();
    ccsPropertyDTO.setInterviewRequired();
    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();

    Case caze = new Case();
    caze.setCaseId(managementEvent.getPayload().getCollectionCase().getId());
    caze.setSurvey("CCS");

    UacQidLink expectedUacQidLink = new UacQidLink();
    expectedUacQidLink.setId(TEST_UAC_QID_LINK_ID);
    expectedUacQidLink.setCcsCase(true);
    expectedUacQidLink.setCaze(caze);

    when(caseService.createCCSCase(
            managementEvent.getPayload().getCcsProperty().getCollectionCase().getId(),
            managementEvent.getPayload().getCcsProperty().getSampleUnit()))
        .thenReturn(caze);

    // When
    underTest.processCCSPropertyListed(managementEvent, messageTimestamp);

    // Then
    InOrder inOrder = inOrder(caseService, uacQidLinkRepository, uacService, eventLogger);
    checkCorrectEventLogging(inOrder, caze, managementEvent, messageTimestamp);

    ArgumentCaptor<Case> caseCaptor = ArgumentCaptor.forClass(Case.class);
    ArgumentCaptor<Metadata> metadataCaptor = ArgumentCaptor.forClass(Metadata.class);
    verify(caseService)
        .saveCaseAndEmitCaseCreatedEvent(caseCaptor.capture(), metadataCaptor.capture());
    Case actualCaseToFieldService = caseCaptor.getValue();
    assertThat(actualCaseToFieldService.getCaseId())
        .isEqualTo(UUID.fromString(caze.getCaseId().toString()));
    assertThat(actualCaseToFieldService.getSurvey()).isEqualTo("CCS");

    Metadata actualMetadata = metadataCaptor.getValue();
    assertThat(actualMetadata.getCauseEventType()).isEqualTo(managementEvent.getEvent().getType());
    assertThat(actualMetadata.getFieldDecision()).isEqualTo(ActionInstructionType.CREATE);
  }

  private void checkCorrectEventLogging(
      InOrder inOrder,
      Case expectedCase,
      ResponseManagementEvent managementEvent,
      OffsetDateTime messageTimestamp) {
    ArgumentCaptor<String> ccsPayload = ArgumentCaptor.forClass(String.class);

    inOrder
        .verify(eventLogger)
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
