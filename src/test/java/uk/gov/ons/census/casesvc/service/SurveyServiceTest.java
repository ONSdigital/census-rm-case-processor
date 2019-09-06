package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

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
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@RunWith(MockitoJUnitRunner.class)
public class SurveyServiceTest {

  private final UUID TEST_CASE_ID = UUID.randomUUID();
  private final String TEST_QID_ID = "1234567890123456";
  private final String TEST_AGENT_ID = "any agent";

  @Mock private UacService uacService;

  @Mock private EventLogger eventLogger;

  @InjectMocks SurveyService underTest;

  private EasyRandom easyRandom = new EasyRandom();

  @Test
  public void testHappyPath() {
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();
    managementEvent.setEvent(new EventDTO());
    managementEvent.getEvent().setDateTime(OffsetDateTime.now());
    managementEvent.getEvent().setType(EventTypeDTO.SURVEY_LAUNCHED);
    managementEvent.setPayload(new PayloadDTO());

    ResponseDTO response = new ResponseDTO();
    response.setCaseId(TEST_CASE_ID.toString());
    response.setQuestionnaireId(TEST_QID_ID);
    response.setAgentId(TEST_AGENT_ID);
    managementEvent.getPayload().setResponse(response);

    UacQidLink expectedUacQidLink = easyRandom.nextObject(UacQidLink.class);
    expectedUacQidLink.setId(TEST_CASE_ID);
    expectedUacQidLink.setUac(TEST_QID_ID);

    // Given
    when(uacService.findByQid(TEST_QID_ID)).thenReturn(expectedUacQidLink);

    // when
    underTest.processMessage(managementEvent);

    // then
    InOrder inOrder = inOrder(uacService, eventLogger);

    inOrder.verify(uacService).findByQid(TEST_QID_ID);

    inOrder
        .verify(eventLogger)
        .logUacQidEvent(
            eq(expectedUacQidLink),
            any(OffsetDateTime.class),
            eq("Survey launched"),
            eq(EventType.SURVEY_LAUNCHED),
            eq(managementEvent.getEvent()),
            anyString());

    verifyNoMoreInteractions(uacService);
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
    response.setCaseId(TEST_CASE_ID.toString());
    response.setQuestionnaireId(TEST_QID_ID);
    response.setAgentId(TEST_AGENT_ID);
    managementEvent.getPayload().setResponse(response);

    UacQidLink expectedUacQidLink = easyRandom.nextObject(UacQidLink.class);
    expectedUacQidLink.setId(TEST_CASE_ID);
    expectedUacQidLink.setUac(TEST_QID_ID);
    expectedUacQidLink.setBatchId(UUID.randomUUID());

    // Given
    when(uacService.findByQid(TEST_QID_ID)).thenReturn(expectedUacQidLink);

    // when
    underTest.processMessage(managementEvent);

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
            respondentAuthenticatedCaptor.capture());

    String expectedEventPayloadJson = convertObjectToJson(response);
    String actualEventPayloadJson = respondentAuthenticatedCaptor.getValue();
    JSONAssert.assertEquals(
        actualEventPayloadJson, expectedEventPayloadJson, JSONCompareMode.STRICT);

    verifyNoMoreInteractions(uacService);
    verifyNoMoreInteractions(eventLogger);
  }

  @Test(expected = RuntimeException.class)
  public void testInvalidSurveyEventTypeException() {
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();
    managementEvent.setEvent(new EventDTO());
    managementEvent.getEvent().setType(EventTypeDTO.CASE_CREATED);

    String expectedErrorMessage =
        String.format("Event type '%s' not found", EventTypeDTO.CASE_CREATED);

    try {
      // WHEN
      underTest.processMessage(managementEvent);
    } catch (RuntimeException re) {
      // THEN
      assertThat(re.getMessage()).isEqualTo(expectedErrorMessage);
      throw re;
    }
  }
}
