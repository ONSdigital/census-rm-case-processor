package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.service.ReceiptService.QID_RECEIPTED;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.*;

import java.time.OffsetDateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ResponseDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@RunWith(MockitoJUnitRunner.class)
public class ReceiptServiceTest {

  private final String TEST_NON_CCS_QID_ID = "0134567890123456";
  private final String TEST_CCS_QID_ID = "7134567890123456";
  private final String TEST_CONTINUATION_QID = "113456789023";

  @Mock private CaseService caseService;

  @Mock private UacService uacService;

  @Mock private EventLogger eventLogger;

  @InjectMocks ReceiptService underTest;

  @Test
  public void testReceiptForNonCCSCase() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    ResponseDTO expectedReceipt = managementEvent.getPayload().getResponse();

    // Given
    Case expectedCase = getRandomCase();
    expectedCase.setReceiptReceived(false);
    expectedCase.setCcsCase(false);
    UacQidLink expectedUacQidLink = generateRandomUacQidLinkedToCase(expectedCase);
    expectedUacQidLink.setQid(TEST_NON_CCS_QID_ID);

    managementEvent.getPayload().getResponse().setResponseDateTime(OffsetDateTime.now());

    when(uacService.findByQid(expectedReceipt.getQuestionnaireId())).thenReturn(expectedUacQidLink);

    // when
    underTest.processReceipt(managementEvent);

    // then
    InOrder inOrder = inOrder(uacService, caseService, eventLogger);

    inOrder.verify(uacService).findByQid(anyString());

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    inOrder.verify(caseService).saveAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase.isReceiptReceived()).isTrue();
    assertThat(actualCase.isCcsCase()).isFalse();
    verifyNoMoreInteractions(caseService);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    inOrder.verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getQid()).isEqualTo(expectedUacQidLink.getQid());
    assertThat(actualUacQidLink.getUac()).isEqualTo(expectedUacQidLink.getUac());

    verify(eventLogger)
        .logUacQidEvent(
            eq(expectedUacQidLink),
            any(OffsetDateTime.class),
            eq(QID_RECEIPTED),
            eq(EventType.RESPONSE_RECEIVED),
            eq(managementEvent.getEvent()),
            anyString());
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void testReceiptForCCSCase() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    ResponseDTO expectedReceipt = managementEvent.getPayload().getResponse();

    // Given
    Case expectedCase = getRandomCase();
    expectedCase.setReceiptReceived(false);
    expectedCase.setCcsCase(true);
    UacQidLink expectedUacQidLink = generateRandomUacQidLinkedToCase(expectedCase);
    expectedUacQidLink.setQid(TEST_CCS_QID_ID);

    managementEvent.getPayload().getResponse().setResponseDateTime(OffsetDateTime.now());

    when(uacService.findByQid(expectedReceipt.getQuestionnaireId())).thenReturn(expectedUacQidLink);

    // when
    underTest.processReceipt(managementEvent);

    // then
    InOrder inOrder = inOrder(uacService, caseService, eventLogger);

    inOrder.verify(uacService).findByQid(anyString());

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    inOrder.verify(caseService).saveCase(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase.isReceiptReceived()).isTrue();
    assertThat(actualCase.isCcsCase()).isTrue();
    verifyNoMoreInteractions(caseService);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    inOrder.verify(uacService).saveUacQidLink(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getQid()).isEqualTo(expectedUacQidLink.getQid());
    assertThat(actualUacQidLink.getUac()).isEqualTo(expectedUacQidLink.getUac());

    verify(eventLogger)
        .logUacQidEvent(
            eq(expectedUacQidLink),
            any(OffsetDateTime.class),
            eq(QID_RECEIPTED),
            eq(EventType.RESPONSE_RECEIVED),
            eq(managementEvent.getEvent()),
            anyString());
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void testReceiptForContinuationQID() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    ResponseDTO expectedReceipt = managementEvent.getPayload().getResponse();

    // Given
    Case expectedCase = getRandomCase();
    expectedCase.setReceiptReceived(false);
    expectedCase.setCcsCase(false);
    UacQidLink expectedUacQidLink = generateRandomUacQidLinkedToCase(expectedCase);
    expectedUacQidLink.setQid(TEST_CONTINUATION_QID);

    managementEvent.getPayload().getResponse().setResponseDateTime(OffsetDateTime.now());

    when(uacService.findByQid(expectedReceipt.getQuestionnaireId())).thenReturn(expectedUacQidLink);

    // when
    underTest.processReceipt(managementEvent);

    // then
    InOrder inOrder = inOrder(uacService, eventLogger);

    inOrder.verify(uacService).findByQid(anyString());

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    inOrder.verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getQid()).isEqualTo(expectedUacQidLink.getQid());
    assertThat(actualUacQidLink.getUac()).isEqualTo(expectedUacQidLink.getUac());

    verify(eventLogger)
        .logUacQidEvent(
            eq(expectedUacQidLink),
            any(OffsetDateTime.class),
            eq(QID_RECEIPTED),
            eq(EventType.RESPONSE_RECEIVED),
            eq(managementEvent.getEvent()),
            anyString());
    verifyNoMoreInteractions(eventLogger);

    verifyZeroInteractions(caseService);
  }
}
