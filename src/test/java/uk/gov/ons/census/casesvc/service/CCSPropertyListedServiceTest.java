package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementCCSAddressListedEvent;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;

@RunWith(MockitoJUnitRunner.class)
public class CCSPropertyListedServiceTest {

  private static final UUID TEST_UAC_QID_LINK_ID = UUID.randomUUID();
  private static final String TEST_QID = "710000000043";

  @Mock EventLogger eventLogger;

  @Mock CaseService caseService;

  @Mock UacService uacService;

  @Mock CcsToFieldService ccsToFieldService;

  @Mock UacQidLinkRepository uacQidLinkRepository;

  @InjectMocks CCSPropertyListedService underTest;

  @Test
  public void testGoodCCSPropertyListed() {
    // Given
    ResponseManagementEvent managementEvent = getTestResponseManagementCCSAddressListedEvent();
    managementEvent.getPayload().getCcsProperty().setUac(null);

    String expectedCaseId =
        managementEvent.getPayload().getCcsProperty().getCollectionCase().getId();

    Case expectedCase = new Case();
    expectedCase.setCaseId(UUID.fromString(expectedCaseId));
    expectedCase.setCcsCase(true);

    UacQidLink expectedUacQidLink = new UacQidLink();
    expectedUacQidLink.setId(TEST_UAC_QID_LINK_ID);
    expectedUacQidLink.setCcsCase(true);
    expectedUacQidLink.setCaze(expectedCase);

    when(caseService.createCCSCase(
            expectedCaseId, managementEvent.getPayload().getCcsProperty().getSampleUnit()))
        .thenReturn(expectedCase);

    // When
    underTest.processCCSPropertyListed(managementEvent);

    // Then
    verify(caseService)
        .createCCSCase(
            expectedCaseId, managementEvent.getPayload().getCcsProperty().getSampleUnit());

    ArgumentCaptor<Case> caseCaptor = ArgumentCaptor.forClass(Case.class);
    verify(eventLogger)
        .logCaseEvent(
            caseCaptor.capture(),
            any(OffsetDateTime.class),
            eq("CCS Address Listed"),
            eq(EventType.CCS_ADDRESS_LISTED),
            eq(managementEvent.getEvent()),
            anyString());
    Case actualCase = caseCaptor.getValue();
    assertThat(actualCase.getCaseId()).isEqualTo(UUID.fromString(expectedCaseId));
    assertThat(actualCase.isCcsCase()).isTrue();

    verify(ccsToFieldService).convertAndSendCCSToField(caseCaptor.capture());
    actualCase = caseCaptor.getValue();
    assertThat(actualCase.getCaseId()).isEqualTo(UUID.fromString(expectedCaseId));
    assertThat(actualCase.isCcsCase()).isTrue();
  }

  @Test
  public void testCaseListedWithQidSet() {
    // Given
    ResponseManagementEvent responseManagementEvent =
        getTestResponseManagementCCSAddressListedEvent();

    responseManagementEvent.getPayload().getCcsProperty().getUac().setQuestionnaireId(TEST_QID);

    String expectedCaseId =
        responseManagementEvent.getPayload().getCcsProperty().getCollectionCase().getId();

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(TEST_UAC_QID_LINK_ID);
    uacQidLink.setQid(TEST_QID);
    when(uacService.findByQid(TEST_QID)).thenReturn(uacQidLink);

    Case expectedCase = new Case();
    expectedCase.setCaseId(UUID.fromString(expectedCaseId));
    expectedCase.setCcsCase(true);
    expectedCase.setUacQidLinks(Collections.singletonList(uacQidLink));

    when(caseService.createCCSCase(
            expectedCaseId, responseManagementEvent.getPayload().getCcsProperty().getSampleUnit()))
        .thenReturn(expectedCase);

    underTest.processCCSPropertyListed(responseManagementEvent);

    ArgumentCaptor<Case> caseCaptor = ArgumentCaptor.forClass(Case.class);
    verify(eventLogger)
        .logCaseEvent(
            caseCaptor.capture(),
            any(OffsetDateTime.class),
            eq("CCS Address Listed"),
            eq(EventType.CCS_ADDRESS_LISTED),
            eq(responseManagementEvent.getEvent()),
            anyString());

    Case actualCase = caseCaptor.getValue();
    assertThat(actualCase.getCaseId()).isEqualTo(UUID.fromString(expectedCaseId));
    assertThat(actualCase.isCcsCase()).isTrue();

    ArgumentCaptor<UacQidLink> uacQidLinkArgumentCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacQidLinkRepository).saveAndFlush(uacQidLinkArgumentCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkArgumentCaptor.getValue();
    assertThat(actualUacQidLink.getQid()).isEqualTo(TEST_QID);
    assertThat(actualUacQidLink.getCaze().getCaseId()).isEqualTo(expectedCase.getCaseId());

    verifyZeroInteractions(ccsToFieldService);
  }
}
