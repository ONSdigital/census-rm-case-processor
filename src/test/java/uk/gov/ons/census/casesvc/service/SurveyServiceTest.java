package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jeasy.random.EasyRandom;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@RunWith(MockitoJUnitRunner.class)
public class SurveyServiceTest {

  private final UUID TEST_CASE_ID = UUID.randomUUID();
  private final String TEST_QID_ID = "1234567890123456";
  private final String TEST_AGENT_ID = "any agent";

  @Mock private UacService uacService;

  @Mock private EventLogger eventLogger;

  @Mock private CaseService caseService;

  @InjectMocks SurveyService underTest;

  private final EasyRandom easyRandom = new EasyRandom();

  @Test
  public void testSurveryLaunchedEventFromRH() {
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();
    managementEvent.setEvent(new EventDTO());
    managementEvent.getEvent().setDateTime(OffsetDateTime.now());
    managementEvent.getEvent().setType(EventTypeDTO.SURVEY_LAUNCHED);
    managementEvent.getEvent().setChannel("RH");
    managementEvent.setPayload(new PayloadDTO());

    ResponseDTO response = new ResponseDTO();
    response.setCaseId(TEST_CASE_ID);
    response.setQuestionnaireId(TEST_QID_ID);
    response.setAgentId(TEST_AGENT_ID);
    managementEvent.getPayload().setResponse(response);

    UacQidLink expectedUacQidLink = easyRandom.nextObject(UacQidLink.class);
    expectedUacQidLink.setUac(TEST_QID_ID);
    expectedUacQidLink.getCaze().setCaseId(TEST_CASE_ID);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    // Given
    when(uacService.findByQid(TEST_QID_ID)).thenReturn(expectedUacQidLink);

    // when
    underTest.processMessage(managementEvent, messageTimestamp);

    // then

    verify(uacService).findByQid(TEST_QID_ID);

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveCaseAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture(), eq(null));
    assertThat(caseArgumentCaptor.getValue().getCaseId()).isEqualTo(TEST_CASE_ID);
    assertThat(caseArgumentCaptor.getValue().isSurveyLaunched()).isTrue();

    verify(eventLogger)
        .logUacQidEvent(
            eq(expectedUacQidLink),
            any(OffsetDateTime.class),
            eq("Survey launched"),
            eq(EventType.SURVEY_LAUNCHED),
            eq(managementEvent.getEvent()),
            any(),
            eq(messageTimestamp));

    verifyNoMoreInteractions(uacService);
    verifyNoMoreInteractions(caseService);
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void testSurveryLaunchedEventNotFromRH() {
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();
    managementEvent.setEvent(new EventDTO());
    managementEvent.getEvent().setDateTime(OffsetDateTime.now());
    managementEvent.getEvent().setType(EventTypeDTO.SURVEY_LAUNCHED);
    managementEvent.getEvent().setChannel("CC");
    managementEvent.setPayload(new PayloadDTO());

    ResponseDTO response = new ResponseDTO();
    response.setCaseId(TEST_CASE_ID);
    response.setQuestionnaireId(TEST_QID_ID);
    response.setAgentId(TEST_AGENT_ID);
    managementEvent.getPayload().setResponse(response);

    UacQidLink expectedUacQidLink = easyRandom.nextObject(UacQidLink.class);
    expectedUacQidLink.setUac(TEST_QID_ID);
    expectedUacQidLink.getCaze().setCaseId(TEST_CASE_ID);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    // Given
    when(uacService.findByQid(TEST_QID_ID)).thenReturn(expectedUacQidLink);

    // when
    underTest.processMessage(managementEvent, messageTimestamp);

    // then

    verify(uacService).findByQid(TEST_QID_ID);

    verify(eventLogger)
        .logUacQidEvent(
            eq(expectedUacQidLink),
            any(OffsetDateTime.class),
            eq("Survey launched"),
            eq(EventType.SURVEY_LAUNCHED),
            eq(managementEvent.getEvent()),
            any(),
            eq(messageTimestamp));

    verifyNoMoreInteractions(uacService);
    verifyNoInteractions(caseService);
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void testRespondentAuthenticatedEventTypeLoggedAndRejected() throws JSONException {
    // Given
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();
    managementEvent.setEvent(new EventDTO());
    managementEvent.getEvent().setDateTime(OffsetDateTime.now());
    managementEvent.getEvent().setType(EventTypeDTO.RESPONDENT_AUTHENTICATED);
    managementEvent.setPayload(new PayloadDTO());

    ResponseDTO response = new ResponseDTO();
    response.setCaseId(TEST_CASE_ID);
    response.setQuestionnaireId(TEST_QID_ID);
    response.setAgentId(TEST_AGENT_ID);
    managementEvent.getPayload().setResponse(response);

    UacQidLink expectedUacQidLink = easyRandom.nextObject(UacQidLink.class);
    expectedUacQidLink.setId(TEST_CASE_ID);
    expectedUacQidLink.setUac(TEST_QID_ID);
    expectedUacQidLink.setBatchId(UUID.randomUUID());
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    // Given
    when(uacService.findByQid(TEST_QID_ID)).thenReturn(expectedUacQidLink);

    // when
    underTest.processMessage(managementEvent, messageTimestamp);

    // then
    InOrder inOrder = inOrder(uacService, eventLogger);

    inOrder.verify(uacService).findByQid(TEST_QID_ID);

    ArgumentCaptor<String> respondentAuthenticatedCaptor = ArgumentCaptor.forClass(String.class);
    inOrder
        .verify(eventLogger)
        .logUacQidEvent(
            eq(expectedUacQidLink),
            any(OffsetDateTime.class),
            eq("Respondent authenticated"),
            eq(EventType.RESPONDENT_AUTHENTICATED),
            eq(managementEvent.getEvent()),
            eq(response),
            eq(messageTimestamp));

    verifyNoMoreInteractions(uacService);
    verifyNoMoreInteractions(eventLogger);
    verifyNoInteractions(caseService);
  }

  @Test(expected = RuntimeException.class)
  public void testInvalidSurveyEventTypeException() {
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();
    managementEvent.setEvent(new EventDTO());
    managementEvent.getEvent().setType(EventTypeDTO.CASE_CREATED);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    String expectedErrorMessage =
        String.format("Event Type '%s' is invalid on this topic", EventTypeDTO.CASE_CREATED);

    try {
      // WHEN
      underTest.processMessage(managementEvent, messageTimestamp);
    } catch (RuntimeException re) {
      // THEN
      assertThat(re.getMessage()).isEqualTo(expectedErrorMessage);
      throw re;
    }
  }
}
