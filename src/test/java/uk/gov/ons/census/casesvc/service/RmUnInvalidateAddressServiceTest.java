package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getCaseThatWillPassFieldWorkHelper;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.Metadata;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.RmUnInvalidateAddress;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;

@RunWith(MockitoJUnitRunner.class)
public class RmUnInvalidateAddressServiceTest {

  @Mock private CaseService caseService;

  @Mock private EventLogger eventLogger;

  @InjectMocks private RmUnInvalidateAddressService underTest;

  @Test
  public void testUnInvalidateAddressHappyPath() {
    // Given
    Case caseToUnInvalidate = getCaseThatWillPassFieldWorkHelper();
    UUID expectedCaseId = UUID.randomUUID();
    caseToUnInvalidate.setCaseId(expectedCaseId);
    caseToUnInvalidate.setAddressInvalid(true);
    caseToUnInvalidate.setRegion("E");
    caseToUnInvalidate.setCaseType("HH");
    caseToUnInvalidate.setEstabType("HOUSEHOLD");
    caseToUnInvalidate.setRefusalReceived(null);

    ResponseManagementEvent rme = new ResponseManagementEvent();

    EventDTO event = new EventDTO();
    rme.setEvent(event);
    event.setDateTime(OffsetDateTime.now());
    event.setChannel("RM");
    event.setType(EventTypeDTO.RM_UNINVALIDATE_ADDRESS);

    OffsetDateTime messageTimestamp = OffsetDateTime.now();
    PayloadDTO payload = new PayloadDTO();
    rme.setPayload(payload);

    RmUnInvalidateAddress rmUnInvalidateAddress = new RmUnInvalidateAddress();
    payload.setRmUnInvalidateAddress(rmUnInvalidateAddress);
    rmUnInvalidateAddress.setCaseId(expectedCaseId);

    when(caseService.getCaseByCaseId(any())).thenReturn(caseToUnInvalidate);

    // When
    underTest.processMessage(rme, messageTimestamp);

    // Then
    ArgumentCaptor<Metadata> metadataArgumentCaptor = ArgumentCaptor.forClass(Metadata.class);
    verify(caseService)
        .saveCaseAndEmitCaseUpdatedEvent(eq(caseToUnInvalidate), metadataArgumentCaptor.capture());
    Metadata eventMetadata = metadataArgumentCaptor.getValue();
    assertThat(eventMetadata.getFieldDecision()).isEqualTo(ActionInstructionType.UPDATE);
    assertThat(eventMetadata.getCauseEventType()).isEqualTo(EventTypeDTO.RM_UNINVALIDATE_ADDRESS);

    verify(eventLogger)
        .logCaseEvent(
            eq(caseToUnInvalidate),
            any(),
            eq("Case address un-invalidate"),
            eq(EventType.RM_UNINVALIDATE_ADDRESS),
            eq(rme.getEvent()),
            eq(convertObjectToJson(rmUnInvalidateAddress)),
            any());
  }
}
