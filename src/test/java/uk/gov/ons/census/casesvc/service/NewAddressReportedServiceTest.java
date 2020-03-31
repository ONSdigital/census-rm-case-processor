package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import uk.gov.ons.census.casesvc.model.dto.Address;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.NewAddress;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.utility.JsonHelper;

@RunWith(MockitoJUnitRunner.class)
public class NewAddressReportedServiceTest {

  @InjectMocks NewAddressReportedService underTest;

  @Mock CaseService caseService;
  @Mock EventLogger eventLogger;

  @Test
  public void testCreatingSkeletonCaseWithAllEventFields() {
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

    NewAddress newAddress = new NewAddress();
    newAddress.setCollectionCase(collectionCase);

    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setNewAddress(newAddress);
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
    newAddressEvent.getPayload().getNewAddress().getCollectionCase().setId(null);

    try {
      underTest.processNewAddress(newAddressEvent, OffsetDateTime.now());
    } catch (RuntimeException e) {
      assertThat(e.getMessage()).isEqualTo("missing id in newAddress CollectionCase");
      throw e;
    }
  }

  @Test(expected = RuntimeException.class)
  public void testMissingAddressType() {
    ResponseManagementEvent newAddressEvent = getMinimalValidNewAddress();
    newAddressEvent
        .getPayload()
        .getNewAddress()
        .getCollectionCase()
        .getAddress()
        .setAddressType(null);

    try {
      underTest.processNewAddress(newAddressEvent, OffsetDateTime.now());
    } catch (RuntimeException e) {
      assertThat(e.getMessage())
          .isEqualTo("missing addressType in newAddress CollectionCase Address");
      throw e;
    }
  }

  @Test(expected = RuntimeException.class)
  public void testMissingAddressLevel() {
    ResponseManagementEvent newAddressEvent = getMinimalValidNewAddress();
    newAddressEvent
        .getPayload()
        .getNewAddress()
        .getCollectionCase()
        .getAddress()
        .setAddressLevel(null);

    try {
      underTest.processNewAddress(newAddressEvent, OffsetDateTime.now());
    } catch (RuntimeException e) {
      assertThat(e.getMessage())
          .isEqualTo("missing addressLevel in newAddress CollectionCase Address");
      throw e;
    }
  }

  @Test(expected = RuntimeException.class)
  public void testMssingRegion() {
    ResponseManagementEvent newAddressEvent = getMinimalValidNewAddress();
    newAddressEvent.getPayload().getNewAddress().getCollectionCase().getAddress().setRegion(null);

    try {
      underTest.processNewAddress(newAddressEvent, OffsetDateTime.now());
    } catch (RuntimeException e) {
      assertThat(e.getMessage()).isEqualTo("missing region in newAddress CollectionCase Address");
      throw e;
    }
  }

  private ResponseManagementEvent getMinimalValidNewAddress() {
    Address address = new Address();
    address.setAddressLevel("U");
    address.setRegion("E");
    address.setAddressType("U");

    CollectionCase collectionCase = new CollectionCase();
    collectionCase.setId(UUID.randomUUID().toString());
    collectionCase.setAddress(address);

    NewAddress newAddress = new NewAddress();
    newAddress.setCollectionCase(collectionCase);

    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setNewAddress(newAddress);
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
    expectedCase.setLatitude(collectionCase.getAddress().getLatitude());
    expectedCase.setLongitude(collectionCase.getAddress().getLongitude());
    expectedCase.setUprn(collectionCase.getAddress().getUprn());
    expectedCase.setEstabUprn(collectionCase.getAddress().getEstabUprn());
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
    expectedCase.setSkeleton(true);

    return expectedCase;
  }
}
