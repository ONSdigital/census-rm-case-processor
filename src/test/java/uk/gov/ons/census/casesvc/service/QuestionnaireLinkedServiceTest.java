package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCaseWithUacQidLinks;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementQuestionnaireLinkedEvent;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jeasy.random.EasyRandom;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.UacDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@RunWith(MockitoJUnitRunner.class)
public class QuestionnaireLinkedServiceTest {

  private final UUID TEST_CASE_ID = UUID.randomUUID();
  private final String TEST_QID = new EasyRandom().nextObject(String.class);

  @Mock UacService uacService;

  @Mock CaseService caseService;

  @Mock EventLogger eventLogger;

  @InjectMocks QuestionnaireLinkedService underTest;

  @Test
  public void testGoodQuestionnaireLinkedForUnreceiptedCase() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();

    UacDTO uac = managementEvent.getPayload().getUac();
    uac.setCaseId(TEST_CASE_ID.toString());
    uac.setQuestionnaireId(TEST_QID);

    Case testCase = getRandomCaseWithUacQidLinks(1);
    testCase.setCaseId(TEST_CASE_ID);

    UacQidLink testUacQidLink = testCase.getUacQidLinks().get(0);
    testUacQidLink.setActive(true);
    testUacQidLink.setCaze(null);

    when(uacService.findByQid(TEST_QID)).thenReturn(testUacQidLink);
    when(caseService.getCaseByCaseId(TEST_CASE_ID)).thenReturn(testCase);

    // WHEN
    underTest.processQuestionnaireLinked(managementEvent);

    // THEN
    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getCaze()).isEqualTo(testCase);
    assertThat(actualUacQidLink.isActive()).isTrue();
    verify(eventLogger)
        .logUacQidEvent(
            eq(testUacQidLink),
            any(OffsetDateTime.class),
            eq("Questionnaire Linked"),
            eq(EventType.QUESTIONNAIRE_LINKED),
            eq(managementEvent.getEvent()),
            anyString());
  }

  @Test
  public void testGoodQuestionnaireLinkedForReceiptedCase() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();

    UacDTO uac = managementEvent.getPayload().getUac();
    uac.setCaseId(TEST_CASE_ID.toString());
    uac.setQuestionnaireId(TEST_QID);

    Case testCase = getRandomCaseWithUacQidLinks(1);
    testCase.setCaseId(TEST_CASE_ID);
    testCase.setReceiptReceived(false);

    UacQidLink testUacQidLink = testCase.getUacQidLinks().get(0);
    testUacQidLink.setActive(false);
    testUacQidLink.setCaze(null);

    when(uacService.findByQid(TEST_QID)).thenReturn(testUacQidLink);
    when(caseService.getCaseByCaseId(TEST_CASE_ID)).thenReturn(testCase);

    // WHEN
    underTest.processQuestionnaireLinked(managementEvent);

    // THEN
    ArgumentCaptor<Case> caseCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveAndEmitCaseUpdatedEvent(caseCaptor.capture());
    Case actualCase = caseCaptor.getValue();
    assertThat(actualCase.getCaseId()).isEqualTo(TEST_CASE_ID);
    assertThat(actualCase.isReceiptReceived()).isTrue();

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getCaze()).isEqualTo(testCase);
    assertThat(actualUacQidLink.isActive()).isFalse();
    verify(eventLogger)
        .logUacQidEvent(
            eq(testUacQidLink),
            any(OffsetDateTime.class),
            eq("Questionnaire Linked"),
            eq(EventType.QUESTIONNAIRE_LINKED),
            eq(managementEvent.getEvent()),
            anyString());
  }
}
