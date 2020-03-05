package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementCCSAddressListedEvent;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.InvalidAddress;
import uk.gov.ons.census.casesvc.model.dto.Metadata;
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
  private static final String TEST_QID_1 = "710000000043";
  private static final String TEST_QID_2 = "610000000043";

  @Mock EventLogger eventLogger;
  @Mock CaseService caseService;
  @Mock UacService uacService;
  @Mock UacQidLinkRepository uacQidLinkRepository;

  @InjectMocks CCSPropertyListedService underTest;

  @Test
  public void testCCSPropertyListedWithoutQid() {
    // Given
    ResponseManagementEvent managementEvent = getTestResponseManagementCCSAddressListedEvent();
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    Case expectedCase =
        getExpectedCCSCase(
            managementEvent.getPayload().getCcsProperty().getCollectionCase().getId());

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
    underTest.processCCSPropertyListed(managementEvent, messageTimestamp);

    // Then
    InOrder inOrder = inOrder(caseService, uacQidLinkRepository, uacService, eventLogger);
    checkCorrectEventLogging(inOrder, expectedCase, managementEvent, messageTimestamp);

    ArgumentCaptor<Case> caseCaptor = ArgumentCaptor.forClass(Case.class);
    ArgumentCaptor<Metadata> metadataCaptor = ArgumentCaptor.forClass(Metadata.class);
    verify(caseService)
        .saveCaseAndEmitCaseCreatedEvent(caseCaptor.capture(), metadataCaptor.capture());
    Case actualCaseToFieldService = caseCaptor.getValue();
    assertThat(actualCaseToFieldService.getCaseId())
        .isEqualTo(UUID.fromString(expectedCase.getCaseId().toString()));
    assertThat(actualCaseToFieldService.getSurvey()).isEqualTo("CCS");

    Metadata actualMetadata = metadataCaptor.getValue();
    assertThat(actualMetadata.getCauseEventType()).isEqualTo(managementEvent.getEvent().getType());
    assertThat(actualMetadata.getFieldDecision()).isEqualTo(ActionInstructionType.CREATE);
  }

  @Test
  public void testCaseListedWithMultipleQidsSet() {
    // Given
    ResponseManagementEvent managementEvent = getTestResponseManagementCCSAddressListedEvent();
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    List<UacDTO> qids = new ArrayList<>();

    UacDTO firstQid = new UacDTO();
    firstQid.setQuestionnaireId(TEST_QID_1);
    qids.add(firstQid);

    UacDTO secondQid = new UacDTO();
    secondQid.setQuestionnaireId(TEST_QID_2);
    qids.add(secondQid);
    managementEvent.getPayload().getCcsProperty().setUac(qids);

    UacQidLink firstUacQidLink = new UacQidLink();
    firstUacQidLink.setId(TEST_UAC_QID_LINK_ID);
    firstUacQidLink.setQid(TEST_QID_1);
    firstUacQidLink.setCcsCase(true);
    when(uacService.findByQid(TEST_QID_1)).thenReturn(firstUacQidLink);

    UacQidLink secondUacQidLink = new UacQidLink();
    secondUacQidLink.setId(TEST_UAC_QID_LINK_ID);
    secondUacQidLink.setQid(TEST_QID_2);
    secondUacQidLink.setCcsCase(true);
    when(uacService.findByQid(TEST_QID_2)).thenReturn(secondUacQidLink);

    Case expectedCase =
        getExpectedCCSCase(
            managementEvent.getPayload().getCcsProperty().getCollectionCase().getId());

    when(caseService.createCCSCase(
            expectedCase.getCaseId().toString(),
            managementEvent.getPayload().getCcsProperty().getSampleUnit(),
            false,
            false))
        .thenReturn(expectedCase);

    // When
    underTest.processCCSPropertyListed(managementEvent, messageTimestamp);

    // Then
    InOrder inOrder = inOrder(caseService, uacService, uacQidLinkRepository, eventLogger);
    checkCorrectEventLogging(inOrder, expectedCase, managementEvent, messageTimestamp);

    ArgumentCaptor<UacQidLink> uacQidLinkArgumentCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacQidLinkRepository, times(2)).saveAndFlush(uacQidLinkArgumentCaptor.capture());

    UacQidLink firstActualUacQidLink = uacQidLinkArgumentCaptor.getAllValues().get(0);
    testUacQidLinkForCase(expectedCase, firstActualUacQidLink, TEST_QID_1);

    UacQidLink secondActualQidLink = uacQidLinkArgumentCaptor.getAllValues().get(1);
    testUacQidLinkForCase(expectedCase, secondActualQidLink, TEST_QID_2);
  }

  @Test
  public void testRefusedCaseListed() {
    // Given
    ResponseManagementEvent managementEvent = getTestResponseManagementCCSAddressListedEvent();
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    RefusalDTO refusalDto = new RefusalDTO();
    managementEvent.getPayload().getCcsProperty().setRefusal(refusalDto);

    Case expectedCase =
        getExpectedCCSCase(
            managementEvent.getPayload().getCcsProperty().getCollectionCase().getId());
    expectedCase.setRefusalReceived(true);

    when(caseService.createCCSCase(
            expectedCase.getCaseId().toString(),
            managementEvent.getPayload().getCcsProperty().getSampleUnit(),
            true,
            false))
        .thenReturn(expectedCase);

    // When
    underTest.processCCSPropertyListed(managementEvent, messageTimestamp);

    // Then
    InOrder inOrder = inOrder(caseService, eventLogger);

    inOrder
        .verify(caseService)
        .createCCSCase(
            expectedCase.getCaseId().toString(),
            managementEvent.getPayload().getCcsProperty().getSampleUnit(),
            true,
            false);

    checkCorrectEventLogging(inOrder, expectedCase, managementEvent, messageTimestamp);
  }

  @Test
  public void testInvalidAddressCaseListed() {
    // Given
    ResponseManagementEvent managementEvent = getTestResponseManagementCCSAddressListedEvent();
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    InvalidAddress invalidAddress = new InvalidAddress();
    managementEvent.getPayload().getCcsProperty().setInvalidAddress(invalidAddress);

    Case expectedCase =
        getExpectedCCSCase(
            managementEvent.getPayload().getCcsProperty().getCollectionCase().getId());
    expectedCase.setAddressInvalid(true);

    when(caseService.createCCSCase(
            expectedCase.getCaseId().toString(),
            managementEvent.getPayload().getCcsProperty().getSampleUnit(),
            false,
            true))
        .thenReturn(expectedCase);

    // When
    underTest.processCCSPropertyListed(managementEvent, messageTimestamp);

    // Then
    InOrder inOrder = inOrder(caseService, eventLogger);
    checkCorrectEventLogging(inOrder, expectedCase, managementEvent, messageTimestamp);
  }

  private Case getExpectedCCSCase(String id) {
    Case caze = new Case();
    caze.setCaseId(UUID.fromString(id));
    caze.setSurvey("CCS");
    caze.setRefusalReceived(false);
    caze.setAddressInvalid(false);

    return caze;
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

  private void testUacQidLinkForCase(Case expectedCase, UacQidLink uacQidLink, String qid) {
    assertThat(uacQidLink.getQid()).isEqualTo(qid);
    assertThat(uacQidLink.getCaze().getCaseId()).isEqualTo(expectedCase.getCaseId());
    assertThat(uacQidLink.isCcsCase()).isTrue();
    assertThat(uacQidLink.getCaze().getSurvey()).isEqualTo("CCS");
  }
}
