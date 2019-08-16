package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.generateRandomUacQidLink;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementQuestionnaireLinkedEvent;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.util.Optional;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.UacDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;

@RunWith(MockitoJUnitRunner.class)
public class QuestionnaireLinkedProcessorTest {

  @Mock UacQidLinkRepository uacQidLinkRepository;

  @Mock CaseRepository caseRepository;

  @Mock UacProcessor uacProcessor;

  @Mock EventLogger eventLogger;

  @InjectMocks QuestionnaireLinkedProcessor underTest;

  @Test
  public void testGoodQuestionnaireLinked() {
    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();
    managementEvent.getPayload().getUac().setCaseId(UUID.randomUUID().toString());
    Case expectedCase = getRandomCase();
    UacQidLink uacQidLink = generateRandomUacQidLink(expectedCase);

    UacDTO uac = managementEvent.getPayload().getUac();
    String questionnaireId = uac.getQuestionnaireId();
    String caseId = uac.getCaseId();

    when(uacQidLinkRepository.findByQid(questionnaireId)).thenReturn(Optional.of(uacQidLink));
    when(caseRepository.findByCaseId(UUID.fromString(caseId)))
        .thenReturn(Optional.of(expectedCase));

    // when
    underTest.processQuestionnaireLinked(managementEvent);

    // then
    verify(uacProcessor, times(1)).emitUacUpdatedEvent(uacQidLink, expectedCase);
    verify(eventLogger, times(1))
        .logEvent(
            uacQidLink,
            "Questionnaire Linked",
            EventType.QUESTIONNAIRE_LINKED,
            convertObjectToJson(uac),
            managementEvent.getEvent());
  }

  @Test(expected = RuntimeException.class)
  public void testQuestionnaireLinkedQidNotFound() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();
    String questionnaireId = managementEvent.getPayload().getUac().getQuestionnaireId();
    String expectedErrorMessage =
        String.format("Questionnaire Id '%s' not found!", questionnaireId);

    when(uacQidLinkRepository.findByQid(questionnaireId)).thenReturn(Optional.empty());

    try {
      // WHEN
      underTest.processQuestionnaireLinked(managementEvent);
    } catch (RuntimeException re) {
      // THEN
      assertThat(re.getMessage()).isEqualTo(expectedErrorMessage);
      throw re;
    }
  }

  @Test(expected = RuntimeException.class)
  public void testQuestionnaireLinkedCaseNotFound() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();
    managementEvent.getPayload().getUac().setCaseId(UUID.randomUUID().toString());
    UacDTO uac = managementEvent.getPayload().getUac();
    String questionnaireId = uac.getQuestionnaireId();
    String caseId = uac.getCaseId();
    String expectedErrorMessage = String.format("Case Id '%s' not found!", caseId);

    when(uacQidLinkRepository.findByQid(questionnaireId)).thenReturn(Optional.of(new UacQidLink()));
    when(caseRepository.findByCaseId(UUID.fromString(caseId))).thenReturn(Optional.empty());

    try {
      // WHEN
      underTest.processQuestionnaireLinked(managementEvent);
    } catch (RuntimeException re) {
      // THEN
      assertThat(re.getMessage()).isEqualTo(expectedErrorMessage);
      throw re;
    }
  }
}
