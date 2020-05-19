package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.service.QidReceiptService.QID_RECEIPTED;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.*;

import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ResponseDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@RunWith(MockitoJUnitRunner.class)
public class QidReceiptServiceTest {

  private final String TEST_NON_CCS_QID_ID = "0134567890123456";
  private final String TEST_CONTINUATION_QID = "113456789023";

  @Mock private CaseReceiptService caseReceiptService;

  @Mock private BlankQuestionnaireService blankQuestionnaireService;

  @Mock private UacService uacService;

  @Mock private EventLogger eventLogger;

  @InjectMocks QidReceiptService underTest;

  @Test
  public void testReceiptForCase() {
    ResponseManagementEvent managementEvent = getTestResponseManagementReceiptEvent();
    ResponseDTO expectedReceipt = managementEvent.getPayload().getResponse();

    // Given
    Case expectedCase = getRandomCase();
    expectedCase.setReceiptReceived(false);
    expectedCase.setSurvey("CENSUS");
    UacQidLink expectedUacQidLink = generateRandomUacQidLinkedToCase(expectedCase);
    expectedUacQidLink.setQid(TEST_NON_CCS_QID_ID);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    managementEvent.getPayload().getResponse().setResponseDateTime(OffsetDateTime.now());

    when(uacService.findByQid(expectedReceipt.getQuestionnaireId())).thenReturn(expectedUacQidLink);

    // when
    underTest.processReceipt(managementEvent, messageTimestamp);

    // then
    verify(uacService).findByQid(anyString());

    ArgumentCaptor<UacQidLink> uacQidLinkArgumentCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(caseReceiptService)
        .receiptCase(uacQidLinkArgumentCaptor.capture(), eq(managementEvent.getEvent()));
    Case actualCase = uacQidLinkArgumentCaptor.getValue().getCaze();
    assertThat(actualCase.isReceiptReceived()).isFalse();
    assertThat(actualCase.getSurvey()).isEqualTo("CENSUS");
    assertThat(uacQidLinkArgumentCaptor.getValue().getQid()).isEqualTo(TEST_NON_CCS_QID_ID);

    verifyNoMoreInteractions(caseReceiptService);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkCaptor.capture());
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
            anyString(),
            eq(messageTimestamp));
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void testUnreceiptForCase() {
    ResponseManagementEvent managementEvent = getTestResponseManagementReceiptEventUnreceipt();
    ResponseDTO expectedReceipt = managementEvent.getPayload().getResponse();

    // Given
    Case expectedCase = getRandomCase();
    expectedCase.setReceiptReceived(false);
    expectedCase.setSurvey("CENSUS");
    UacQidLink expectedUacQidLink = generateRandomUacQidLinkedToCase(expectedCase);
    expectedUacQidLink.setQid(TEST_NON_CCS_QID_ID);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    managementEvent.getPayload().getResponse().setResponseDateTime(OffsetDateTime.now());

    when(uacService.findByQid(expectedReceipt.getQuestionnaireId())).thenReturn(expectedUacQidLink);

    // when
    underTest.processReceipt(managementEvent, messageTimestamp);

    // then
    verify(uacService).findByQid(anyString());

    ArgumentCaptor<UacQidLink> uacQidLinkArgumentCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(blankQuestionnaireService)
        .handleBlankQuestionnaire(
            caseArgumentCaptor.capture(),
            uacQidLinkArgumentCaptor.capture(),
            eq(managementEvent.getEvent().getType()));
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase.isReceiptReceived()).isFalse();
    assertThat(actualCase.getSurvey()).isEqualTo("CENSUS");
    assertThat(uacQidLinkArgumentCaptor.getValue().getQid()).isEqualTo(TEST_NON_CCS_QID_ID);

    verifyNoMoreInteractions(blankQuestionnaireService);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getQid()).isEqualTo(expectedUacQidLink.getQid());
    assertThat(actualUacQidLink.getUac()).isEqualTo(expectedUacQidLink.getUac());
    assertThat(actualUacQidLink.isBlankQuestionnaire()).isTrue();

    verify(eventLogger)
        .logUacQidEvent(
            eq(expectedUacQidLink),
            any(OffsetDateTime.class),
            eq("Blank questionnaire received"),
            eq(EventType.RESPONSE_RECEIVED),
            eq(managementEvent.getEvent()),
            anyString(),
            eq(messageTimestamp));
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void testUnreceiptForUnlinkedQidIsLoggedAndQidUpdated() {

    ResponseManagementEvent managementEvent = getTestResponseManagementReceiptEventUnreceipt();
    managementEvent.getPayload().getResponse().setQuestionnaireId(TEST_NON_CCS_QID_ID);
    UacQidLink uacQidLink = generateRandomUacQidLink();
    uacQidLink.setQid(TEST_NON_CCS_QID_ID);
    uacQidLink.setBlankQuestionnaire(false);
    uacQidLink.setCaze(null);

    // When
    underTest.processUnreceipt(managementEvent, OffsetDateTime.now(), uacQidLink);

    // Then
    verifyZeroInteractions(caseReceiptService);
    verifyZeroInteractions(blankQuestionnaireService);
    verify(eventLogger)
        .logUacQidEvent(
            eq(uacQidLink),
            any(),
            eq("Blank questionnaire received"),
            eq(EventType.RESPONSE_RECEIVED),
            any(),
            any(),
            any());
    ArgumentCaptor<UacQidLink> uacQidLinkArgumentCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkArgumentCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkArgumentCaptor.getValue();
    assertThat(actualUacQidLink)
        .isEqualToIgnoringGivenFields(uacQidLink, "blankQuestionnaire", "active");
    assertThat(actualUacQidLink.isBlankQuestionnaire()).isTrue();
    assertThat(actualUacQidLink.isActive()).isFalse();
  }

  @Test
  public void testUnreceiptForEqReceiptedIsIgnored() {

    ResponseManagementEvent managementEvent = getTestResponseManagementReceiptEventUnreceipt();
    managementEvent.getPayload().getResponse().setQuestionnaireId(TEST_NON_CCS_QID_ID);
    UacQidLink uacQidLink = generateRandomUacQidLink();
    uacQidLink.setQid(TEST_NON_CCS_QID_ID);
    uacQidLink.setBlankQuestionnaire(true);
    uacQidLink.setCaze(null);
    uacQidLink.setActive(false);
    List<Event> events = new LinkedList<>();
    Event eqReceiptEvent = new Event();
    eqReceiptEvent.setEventChannel("EQ");
    eqReceiptEvent.setEventType(EventType.RESPONSE_RECEIVED);
    events.add(eqReceiptEvent);
    uacQidLink.setEvents(events);

    // When
    underTest.processUnreceipt(managementEvent, OffsetDateTime.now(), uacQidLink);

    // Then
    verifyZeroInteractions(uacService);
    verifyZeroInteractions(caseReceiptService);
    verifyZeroInteractions(blankQuestionnaireService);
    verifyZeroInteractions(eventLogger);
  }
}
