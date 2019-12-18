package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementCCSAddressListedEvent;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

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
import uk.gov.ons.census.casesvc.model.dto.InvalidAddress;
import uk.gov.ons.census.casesvc.model.dto.RefusalDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.UacDTO;
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
  @Mock FieldworkFollowupService fieldworkFollowupService;
  @Mock UacQidLinkRepository uacQidLinkRepository;

  @InjectMocks CCSPropertyListedService underTest;

  @Test
  public void testCCSPropertyListedWithoutQid() {
    // Given
    ResponseManagementEvent managementEvent = getTestResponseManagementCCSAddressListedEvent();
    Case expectedCase =
        getExpectedCase(managementEvent.getPayload().getCcsProperty().getCollectionCase().getId());

    UacQidLink expectedUacQidLink = new UacQidLink();
    expectedUacQidLink.setId(TEST_UAC_QID_LINK_ID);
    expectedUacQidLink.setCcsCase(true);
    expectedUacQidLink.setCaze(expectedCase);

    when(caseService.createCCSCase(
            managementEvent.getPayload().getCcsProperty().getCollectionCase().getId(),
            managementEvent.getPayload().getCcsProperty().getSampleUnit(),
            false,
            false))
        .thenReturn(expectedCase);

    // When
    underTest.processCCSPropertyListed(managementEvent);

    // Then
    InOrder inOrder = inOrder(caseService, uacQidLinkRepository, uacService, eventLogger);
    checkCorrectEventLogging(inOrder, expectedCase, managementEvent);

    ArgumentCaptor<Case> caseCaptor = ArgumentCaptor.forClass(Case.class);
    verify(fieldworkFollowupService).buildAndSendFieldWorkFollowUp(caseCaptor.capture(), eq("CCS"), eq(false));
    Case actualCaseToFieldService = caseCaptor.getValue();
    assertThat(actualCaseToFieldService.getCaseId())
        .isEqualTo(UUID.fromString(expectedCase.getCaseId().toString()));
    assertThat(actualCaseToFieldService.isCcsCase()).isTrue();
  }

  @Test
  public void testCaseListedWithQidSet() {
    // Given
    ResponseManagementEvent managementEvent = getTestResponseManagementCCSAddressListedEvent();
    UacDTO uacDTO = new UacDTO();
    uacDTO.setQuestionnaireId(TEST_QID);
    managementEvent.getPayload().getCcsProperty().setUac(uacDTO);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(TEST_UAC_QID_LINK_ID);
    uacQidLink.setQid(TEST_QID);
    when(uacService.findByQid(TEST_QID)).thenReturn(uacQidLink);

    Case expectedCase =
        getExpectedCase(managementEvent.getPayload().getCcsProperty().getCollectionCase().getId());
    expectedCase.setUacQidLinks(Collections.singletonList(uacQidLink));

    when(caseService.createCCSCase(
            expectedCase.getCaseId().toString(),
            managementEvent.getPayload().getCcsProperty().getSampleUnit(),
            false,
            false))
        .thenReturn(expectedCase);

    // When
    underTest.processCCSPropertyListed(managementEvent);

    // Then
    InOrder inOrder = inOrder(caseService, uacService, uacQidLinkRepository, eventLogger);
    checkCorrectEventLogging(inOrder, expectedCase, managementEvent);

    ArgumentCaptor<UacQidLink> uacQidLinkArgumentCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacQidLinkRepository).saveAndFlush(uacQidLinkArgumentCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkArgumentCaptor.getValue();
    assertThat(actualUacQidLink.getQid()).isEqualTo(TEST_QID);
    assertThat(actualUacQidLink.getCaze().getCaseId()).isEqualTo(expectedCase.getCaseId());

    verifyZeroInteractions(fieldworkFollowupService);
  }

  @Test
  public void testRefusedCaseListed() {
    // Given
    ResponseManagementEvent managementEvent = getTestResponseManagementCCSAddressListedEvent();
    RefusalDTO refusalDto = new RefusalDTO();
    managementEvent.getPayload().getCcsProperty().setRefusal(refusalDto);

    Case expectedCase =
        getExpectedCase(managementEvent.getPayload().getCcsProperty().getCollectionCase().getId());
    expectedCase.setRefusalReceived(true);

    when(caseService.createCCSCase(
            expectedCase.getCaseId().toString(),
            managementEvent.getPayload().getCcsProperty().getSampleUnit(),
            true,
            false))
        .thenReturn(expectedCase);

    // When
    underTest.processCCSPropertyListed(managementEvent);

    // Then
    InOrder inOrder = inOrder(caseService, eventLogger);

    inOrder
        .verify(caseService)
        .createCCSCase(
            expectedCase.getCaseId().toString(),
            managementEvent.getPayload().getCcsProperty().getSampleUnit(),
            true,
            false);

    checkCorrectEventLogging(inOrder, expectedCase, managementEvent);
    verifyZeroInteractions(fieldworkFollowupService);
  }

  @Test
  public void testInvalidAddressCaseListed() {
    // Given
    ResponseManagementEvent managementEvent = getTestResponseManagementCCSAddressListedEvent();
    InvalidAddress invalidAddress = new InvalidAddress();
    managementEvent.getPayload().getCcsProperty().setInvalidAddress(invalidAddress);

    Case expectedCase =
        getExpectedCase(managementEvent.getPayload().getCcsProperty().getCollectionCase().getId());
    expectedCase.setAddressInvalid(true);

    when(caseService.createCCSCase(
            expectedCase.getCaseId().toString(),
            managementEvent.getPayload().getCcsProperty().getSampleUnit(),
            false,
            true))
        .thenReturn(expectedCase);

    // When
    underTest.processCCSPropertyListed(managementEvent);

    // Then
    InOrder inOrder = inOrder(caseService, eventLogger);
    checkCorrectEventLogging(inOrder, expectedCase, managementEvent);
    verifyZeroInteractions(fieldworkFollowupService);
  }

  private Case getExpectedCase(String id) {
    Case caze = new Case();
    caze.setCaseId(UUID.fromString(id));
    caze.setCcsCase(true);
    caze.setRefusalReceived(false);
    caze.setAddressInvalid(false);

    return caze;
  }

  private void checkCorrectEventLogging(
      InOrder inOrder, Case expectedCase, ResponseManagementEvent managementEvent) {
    ArgumentCaptor<String> ccsPayload = ArgumentCaptor.forClass(String.class);

    inOrder
        .verify(eventLogger)
        .logCaseEvent(
            eq(expectedCase),
            any(OffsetDateTime.class),
            eq("CCS Address Listed"),
            eq(EventType.CCS_ADDRESS_LISTED),
            eq(managementEvent.getEvent()),
            ccsPayload.capture());

    String actualLoggedPayload = ccsPayload.getValue();
    assertThat(actualLoggedPayload)
        .isEqualTo(convertObjectToJson(managementEvent.getPayload().getCcsProperty()));
  }
}
