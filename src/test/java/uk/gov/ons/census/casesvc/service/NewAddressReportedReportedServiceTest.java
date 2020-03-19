package uk.gov.ons.census.casesvc.service;

import org.jeasy.random.EasyRandom;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.Address;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.NewAddressReported;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.utility.JsonHelper;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class NewAddressReportedReportedServiceTest {

  @InjectMocks
  NewAddressReportedService underTest;

  @Mock CaseService caseService;
  @Mock EventLogger eventLogger;

  @Test
  public void testCreatingSkellingtonCaseWithAllEventFields() {
    // given
    EasyRandom easyRandom = new EasyRandom();
    CollectionCase collectionCase = easyRandom.nextObject(CollectionCase.class);
    collectionCase.setId(UUID.randomUUID().toString());
    collectionCase.setCaseRef(null);
    collectionCase.setCaseType("HH");

    Address address = collectionCase.getAddress();
    address.setRegion("W");
    address.setAddressLevel("E");
    address.setAddressType("HH");
    collectionCase.setAddress(address);

    NewAddressReported newAddressReported = new NewAddressReported();
    newAddressReported.setCollectionCase(collectionCase);

    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setNewAddressReported(newAddressReported);
    ResponseManagementEvent newAddressEvent = new ResponseManagementEvent();
    newAddressEvent.setPayload(payloadDTO);
    EventDTO eventDTO = new EventDTO();
    eventDTO.setDateTime(OffsetDateTime.now().minusSeconds(10));
    newAddressEvent.setEvent(eventDTO);

    Case casetoEmit = new Case();
    when(caseService.saveNewCaseAndStampCaseRef(any(Case.class))).thenReturn(casetoEmit);
    OffsetDateTime expectedDateTime = OffsetDateTime.now();

    // When
    underTest.processNewAddress(newAddressEvent, expectedDateTime);

    // Then
    Case expectedCase = getExpectedCase(collectionCase);

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveNewCaseAndStampCaseRef(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase).isEqualToComparingFieldByFieldRecursively(expectedCase);

    verify(caseService).emitCaseCreatedEvent(casetoEmit);

    verify(eventLogger)
        .logCaseEvent(
            eq(casetoEmit),
            eq(eventDTO.getDateTime()),
            eq("New Address reported"),
            eq(EventType.NEW_ADDRESS_REPORTED),
            eq(eventDTO),
            eq(JsonHelper.convertObjectToJson(newAddressEvent.getPayload())),
            eq(expectedDateTime));
  }

  @Test
  public void testMinimalGoodNewAddressDoesNotFailValidation() {
    ResponseManagementEvent responseManagementEvent = getMinimalValidNewAddress();
    underTest.processNewAddress(responseManagementEvent, OffsetDateTime.now());
  }

  @Test(expected = RuntimeException.class)
  public void testMissingUUID() {
    ResponseManagementEvent newAddressEvent = getMinimalValidNewAddress();
    newAddressEvent.getPayload().getNewAddressReported().getCollectionCase().setId("Xdd344234");

    try {
      underTest.processNewAddress(newAddressEvent, OffsetDateTime.now());
    } catch (RuntimeException e) {
      assertThat(e.getMessage())
          .isEqualTo("Expected NewAddress CollectionCase Id to be a valid UUID, got: Xdd344234");
      throw e;
    }
  }

  @Test(expected = RuntimeException.class)
  public void testBadCasetype() {
    ResponseManagementEvent newAddressEvent = getMinimalValidNewAddress();
    newAddressEvent.getPayload().getNewAddressReported().getCollectionCase().setCaseType("BadCaseType");

    try {
      underTest.processNewAddress(newAddressEvent, OffsetDateTime.now());
    } catch (RuntimeException e) {
      assertThat(e.getMessage())
          .isEqualTo("Unexpected newAddress CollectionCase caseType: BadCaseType");
      throw e;
    }
  }

  @Test(expected = RuntimeException.class)
  public void testBadAddressLevel() {
    ResponseManagementEvent newAddressEvent = getMinimalValidNewAddress();
    newAddressEvent
        .getPayload()
        .getNewAddressReported()
        .getCollectionCase()
        .getAddress()
        .setAddressLevel("X");

    try {
      underTest.processNewAddress(newAddressEvent, OffsetDateTime.now());
    } catch (RuntimeException e) {
      assertThat(e.getMessage())
          .isEqualTo(
              "Unexpected a valid address level in newAddress CollectionCase Address, received: X");
      throw e;
    }
  }

  @Test(expected = RuntimeException.class)
  public void testBadRegion() {
    ResponseManagementEvent newAddressEvent = getMinimalValidNewAddress();
    newAddressEvent
        .getPayload()
        .getNewAddressReported()
        .getCollectionCase()
        .getAddress()
        .setRegion("UpNorth");

    try {
      underTest.processNewAddress(newAddressEvent, OffsetDateTime.now());
    } catch (RuntimeException e) {
      assertThat(e.getMessage())
          .isEqualTo("Invalid newAddress collectionCase Address Region: UpNorth");
      throw e;
    }
  }

  private ResponseManagementEvent getMinimalValidNewAddress() {
    Address address = new Address();
    address.setAddressLevel("U");
    address.setRegion("E");

    CollectionCase collectionCase = new CollectionCase();
    collectionCase.setId(UUID.randomUUID().toString());
    collectionCase.setCaseType("HH");
    collectionCase.setAddress(address);

    NewAddressReported newAddressReported = new NewAddressReported();
    newAddressReported.setCollectionCase(collectionCase);

    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setNewAddressReported(newAddressReported);
    ResponseManagementEvent newAddressEvent = new ResponseManagementEvent();
    newAddressEvent.setPayload(payloadDTO);

    EventDTO eventDTO = new EventDTO();
    eventDTO.setDateTime(OffsetDateTime.now());
    newAddressEvent.setEvent(eventDTO);

    return newAddressEvent;
  }

  private Case getExpectedCase(CollectionCase collectionCase) {
    Case expectedCase = new Case();
    expectedCase.setCaseId(UUID.fromString(collectionCase.getId()));
    expectedCase.setCaseType(collectionCase.getCaseType());
    expectedCase.setActionPlanId(collectionCase.getActionPlanId());
    expectedCase.setCollectionExerciseId(collectionCase.getCollectionExerciseId());
    expectedCase.setTreatmentCode(collectionCase.getTreatmentCode());
    expectedCase.setAddressLine1(collectionCase.getAddress().getAddressLine1());
    expectedCase.setAddressLine2(collectionCase.getAddress().getAddressLine2());
    expectedCase.setAddressLine3(collectionCase.getAddress().getAddressLine3());
    expectedCase.setTownName(collectionCase.getAddress().getTownName());
    expectedCase.setPostcode(collectionCase.getAddress().getPostcode());
    expectedCase.setArid(collectionCase.getAddress().getArid());
    expectedCase.setLatitude(collectionCase.getAddress().getLatitude());
    expectedCase.setLongitude(collectionCase.getAddress().getLongitude());
    expectedCase.setUprn(collectionCase.getAddress().getUprn());
    expectedCase.setRegion(collectionCase.getAddress().getRegion());
    expectedCase.setOa(collectionCase.getOa());
    expectedCase.setLsoa(collectionCase.getLsoa());
    expectedCase.setMsoa(collectionCase.getMsoa());
    expectedCase.setLad(collectionCase.getLad());
    expectedCase.setHtcWillingness(collectionCase.getHtcWillingness());
    expectedCase.setHtcDigital(collectionCase.getHtcDigital());
    expectedCase.setAddressLevel(collectionCase.getAddress().getAddressLevel());
    expectedCase.setAbpCode(collectionCase.getAddress().getApbCode());
    expectedCase.setAddressType(collectionCase.getAddress().getAddressType());
    expectedCase.setUprn(collectionCase.getAddress().getUprn());
    expectedCase.setEstabArid(collectionCase.getAddress().getEstabArid());
    expectedCase.setEstabType(collectionCase.getAddress().getEstabType());
    expectedCase.setOrganisationName(collectionCase.getAddress().getOrganisationName());
    expectedCase.setFieldCoordinatorId(collectionCase.getFieldCoordinatorId());
    expectedCase.setFieldOfficerId(collectionCase.getFieldOfficerId());
    expectedCase.setCeExpectedCapacity(collectionCase.getCeExpectedCapacity());
    expectedCase.setCeActualResponses(collectionCase.getCeActualResponses());
    expectedCase.setHandDelivery(collectionCase.isHandDelivery());
    expectedCase.setSurvey("CENSUS");
    expectedCase.setRefusalReceived(false);
    expectedCase.setReceiptReceived(false);
    expectedCase.setAddressInvalid(false);
    expectedCase.setUndeliveredAsAddressed(false);
    expectedCase.setSkellingtonCase(true);

    return expectedCase;
  }
}
