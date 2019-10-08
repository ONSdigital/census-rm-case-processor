package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementCCSAddressListedEvent;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
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
    PayloadDTO payload = managementEvent.getPayload();
    payload.getCcsProperty().setRefusal(null);
    payload.getCcsProperty().setUac(null);

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
            expectedCaseId, managementEvent.getPayload().getCcsProperty().getSampleUnit(), false))
        .thenReturn(expectedCase);

    // When
    underTest.processCCSPropertyListed(managementEvent);

    // Then
    verify(caseService)
        .createCCSCase(
            expectedCaseId, managementEvent.getPayload().getCcsProperty().getSampleUnit(), false);

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
    ResponseManagementEvent managementEvent = getTestResponseManagementCCSAddressListedEvent();
    PayloadDTO payload = managementEvent.getPayload();

    payload.getCcsProperty().setRefusal(null);
    payload.getCcsProperty().getUac().setQuestionnaireId(TEST_QID);

    String expectedCaseId =
        managementEvent.getPayload().getCcsProperty().getCollectionCase().getId();
    managementEvent.getPayload().getCcsProperty().getCollectionCase().getId();

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(TEST_UAC_QID_LINK_ID);
    uacQidLink.setQid(TEST_QID);
    when(uacService.findByQid(TEST_QID)).thenReturn(uacQidLink);

    Case expectedCase = new Case();
    expectedCase.setCaseId(UUID.fromString(expectedCaseId));
    expectedCase.setCcsCase(true);
    expectedCase.setUacQidLinks(Collections.singletonList(uacQidLink));

    when(caseService.createCCSCase(
            expectedCaseId, managementEvent.getPayload().getCcsProperty().getSampleUnit(), false))
        .thenReturn(expectedCase);

    // When
    underTest.processCCSPropertyListed(managementEvent);

    // Then
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
    assertThat(actualCase.getCaseId().toString()).isEqualTo(expectedCaseId);

    verifyZeroInteractions(ccsToFieldService);
  }

  @Test
  public void testRefusedCaseListed() {
    // Given
    ResponseManagementEvent managementEvent = getTestResponseManagementCCSAddressListedEvent();

    managementEvent.getPayload().getCcsProperty().getUac().setQuestionnaireId(TEST_QID);

    String expectedCaseId =
        managementEvent.getPayload().getCcsProperty().getCollectionCase().getId();

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(TEST_UAC_QID_LINK_ID);
    uacQidLink.setQid(TEST_QID);
    when(uacService.findByQid(TEST_QID)).thenReturn(uacQidLink);

    Case expectedCase = new Case();
    expectedCase.setCaseId(UUID.fromString(expectedCaseId));
    expectedCase.setCcsCase(true);
    expectedCase.setRefusalReceived(true);
    expectedCase.setUacQidLinks(Collections.singletonList(uacQidLink));

    when(caseService.createCCSCase(
            expectedCaseId, managementEvent.getPayload().getCcsProperty().getSampleUnit(), true))
        .thenReturn(expectedCase);

    // When
    underTest.processCCSPropertyListed(managementEvent);

    // Then
    InOrder inOrder = inOrder(caseService, uacService, eventLogger);

    inOrder
        .verify(caseService)
        .createCCSCase(
            expectedCaseId, managementEvent.getPayload().getCcsProperty().getSampleUnit(), true);
    inOrder.verify(uacService).findByQid(TEST_QID);

    ArgumentCaptor<Case> caseCaptor = ArgumentCaptor.forClass(Case.class);
    inOrder
        .verify(eventLogger)
        .logCaseEvent(
            caseCaptor.capture(),
            any(OffsetDateTime.class),
            eq("CCS Address Listed"),
            eq(EventType.CCS_ADDRESS_LISTED),
            eq(managementEvent.getEvent()),
            anyString());

    Case actualCase = caseCaptor.getValue();
    assertThat(actualCase.getCaseId().toString()).isEqualTo(expectedCaseId);
    assertThat(actualCase.isRefusalReceived()).isTrue();

    verifyZeroInteractions(ccsToFieldService);
  }
}
