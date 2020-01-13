package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.skyscreamer.jsonassert.JSONCompareMode.STRICT;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.ADDRESS_MODIFIED;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.ADDRESS_NOT_VALID;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.ADDRESS_TYPE_CHANGED;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.CASE_CREATED;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.NEW_ADDRESS_REPORTED;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.createNewAddressReportedJson;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.createTestAddressModifiedJson;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.createTestAddressTypeChangeJson;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.skyscreamer.jsonassert.JSONAssert;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.CollectionCaseCaseId;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.InvalidAddress;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.utility.JsonHelper;

@RunWith(MockitoJUnitRunner.class)
public class InvalidAddressServiceTest {

  private final UUID TEST_CASE_ID = UUID.randomUUID();

  @Mock private CaseService caseService;

  @Mock private EventLogger eventLogger;

  @InjectMocks InvalidAddressService underTest;

  @Test
  public void testInvalidAddress() {
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();
    managementEvent.setEvent(new EventDTO());
    managementEvent.getEvent().setDateTime(OffsetDateTime.now());
    managementEvent.getEvent().setType(ADDRESS_NOT_VALID);
    managementEvent.setPayload(new PayloadDTO());
    managementEvent.getPayload().setInvalidAddress(new InvalidAddress());
    managementEvent.getPayload().getInvalidAddress().setCollectionCase(new CollectionCaseCaseId());
    managementEvent
        .getPayload()
        .getInvalidAddress()
        .getCollectionCase()
        .setId(UUID.randomUUID().toString());

    // Given
    Case expectedCase = getRandomCase();
    expectedCase.setAddressInvalid(false);
    expectedCase.setSurvey("CENSUS");
    when(caseService.getCaseByCaseId(any(UUID.class))).thenReturn(expectedCase);

    // when
    underTest.processMessage(managementEvent);

    // then
    InOrder inOrder = inOrder(caseService, eventLogger);

    inOrder.verify(caseService).getCaseByCaseId(any(UUID.class));

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    inOrder.verify(caseService).saveAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase.isAddressInvalid()).isTrue();
    assertThat(actualCase.getSurvey()).isEqualTo("CENSUS");
    verifyNoMoreInteractions(caseService);

    inOrder
        .verify(eventLogger)
        .logCaseEvent(
            eq(expectedCase),
            any(OffsetDateTime.class),
            eq("Invalid address"),
            eq(EventType.ADDRESS_NOT_VALID),
            eq(managementEvent.getEvent()),
            anyString());
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void testAddressModifiedEventTypeLoggedOnly() throws JSONException {
    PayloadDTO payload = new PayloadDTO();
    payload.setAddressModification(createTestAddressModifiedJson(TEST_CASE_ID));

    testEventTypeLoggedOnly(
        payload,
        JsonHelper.convertObjectToJson(payload.getAddressModification()),
        ADDRESS_MODIFIED,
        EventType.ADDRESS_MODIFIED,
        "Address modified");
  }

  @Test
  public void testAddressTypeChangeEventTypeLoggedOnly() throws JSONException {
    PayloadDTO payload = new PayloadDTO();
    payload.setAddressTypeChange(createTestAddressTypeChangeJson(TEST_CASE_ID));

    testEventTypeLoggedOnly(
        payload,
        JsonHelper.convertObjectToJson(payload.getAddressTypeChange()),
        ADDRESS_TYPE_CHANGED,
        EventType.ADDRESS_TYPE_CHANGED,
        "Address type changed");
  }

  @Test
  public void testNewAddressReportedEventTypeLoggedOnly() throws JSONException {
    PayloadDTO payload = new PayloadDTO();
    payload.setNewAddressReported(createNewAddressReportedJson(TEST_CASE_ID));

    testEventTypeLoggedOnly(
        payload,
        JsonHelper.convertObjectToJson(payload.getNewAddressReported()),
        NEW_ADDRESS_REPORTED,
        EventType.NEW_ADDRESS_REPORTED,
        "New Address reported");
  }

  private void testEventTypeLoggedOnly(
      PayloadDTO payload,
      String expectedEventPayloadJson,
      EventTypeDTO eventTypeDTO,
      EventType eventType,
      String eventDescription)
      throws JSONException {
    // Given
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();
    managementEvent.setEvent(new EventDTO());
    managementEvent.getEvent().setDateTime(OffsetDateTime.now());
    managementEvent.getEvent().setType(eventTypeDTO);
    managementEvent.setPayload(payload);

    Case expectedCase = getRandomCase();
    expectedCase.setAddressInvalid(false);
    when(caseService.getCaseByCaseId(TEST_CASE_ID)).thenReturn(expectedCase);

    // when
    underTest.processMessage(managementEvent);

    // then
    InOrder inOrder = inOrder(caseService, eventLogger);

    inOrder.verify(caseService).getCaseByCaseId(TEST_CASE_ID);

    ArgumentCaptor<String> addressModifiedCaptor = ArgumentCaptor.forClass(String.class);
    inOrder
        .verify(eventLogger)
        .logCaseEvent(
            eq(expectedCase),
            any(OffsetDateTime.class),
            eq(eventDescription),
            eq(eventType),
            eq(managementEvent.getEvent()),
            addressModifiedCaptor.capture());

    String actualEventPayloadJson = addressModifiedCaptor.getValue();
    JSONAssert.assertEquals(actualEventPayloadJson, expectedEventPayloadJson, STRICT);

    verifyNoMoreInteractions(caseService);
    verifyNoMoreInteractions(eventLogger);
  }

  @Test(expected = RuntimeException.class)
  public void testInvalidAddressEventTypeException() {
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();
    managementEvent.setEvent(new EventDTO());
    managementEvent.getEvent().setType(CASE_CREATED);

    String expectedErrorMessage =
        String.format("Event Type '%s' is invalid on this topic", CASE_CREATED);

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
