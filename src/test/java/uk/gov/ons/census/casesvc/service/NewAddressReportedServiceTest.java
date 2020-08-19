package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jeasy.random.EasyRandom;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.concurrent.FailureCallback;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.util.concurrent.SuccessCallback;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.Address;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.Metadata;
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
  @Mock PubSubTemplate pubSubTemplate;

  private UUID EXPECTED_ACTION_PLAN_ID = UUID.randomUUID();

  private String DUMMY_UPRN_PREFIX = "999";

  @Test
  public void testCreatingSkeletonCaseWithAllEventFields() {
    // given
    EasyRandom easyRandom = new EasyRandom();
    CollectionCase collectionCase = easyRandom.nextObject(CollectionCase.class);
    collectionCase.setId(UUID.randomUUID());
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
    ReflectionTestUtils.setField(underTest, "censusActionPlanId", EXPECTED_ACTION_PLAN_ID);

    when(pubSubTemplate.publish(any(), any(ResponseManagementEvent.class)))
        .thenReturn(mockFuture());
    when(caseService.saveNewCaseAndStampCaseRef(any(Case.class))).thenReturn(casetoEmit);
    OffsetDateTime expectedDateTime = OffsetDateTime.now();

    // When
    underTest.processNewAddress(newAddressEvent, expectedDateTime);

    // Then
    Case expectedCase = getExpectedCase(collectionCase);

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveNewCaseAndStampCaseRef(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    actualCase.setCreatedDateTime(expectedCase.getCreatedDateTime());
    assertThat(actualCase).isEqualToComparingFieldByFieldRecursively(expectedCase);

    verify(caseService).emitCaseCreatedEvent(casetoEmit);

    verify(eventLogger)
        .logCaseEvent(
            eq(casetoEmit),
            eq(eventDTO.getDateTime()),
            eq("New Address reported"),
            eq(EventType.NEW_ADDRESS_REPORTED),
            eq(eventDTO),
            eq(JsonHelper.convertObjectToJson(newAddressEvent.getPayload().getNewAddress())),
            eq(expectedDateTime));
  }

  @Test
  public void testRequiredFieldsSetIfNotOnEvent() {
    UUID collectionExerciseId = UUID.randomUUID();
    ReflectionTestUtils.setField(underTest, "censusCollectionExerciseId", collectionExerciseId);

    ResponseManagementEvent responseManagementEvent = getMinimalValidNewAddress();
    Case casetoEmit = new Case();
    casetoEmit.setUprn("1234");
    when(caseService.saveNewCaseAndStampCaseRef(any(Case.class))).thenReturn(casetoEmit);
    underTest.processNewAddress(responseManagementEvent, OffsetDateTime.now());
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);

    verify(caseService).saveNewCaseAndStampCaseRef(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase.getCaseType())
        .isEqualTo(
            responseManagementEvent
                .getPayload()
                .getNewAddress()
                .getCollectionCase()
                .getAddress()
                .getAddressType());

    assertThat(actualCase.getCollectionExerciseId()).isEqualTo(collectionExerciseId);
  }

  @Test
  public void testMinimalGoodNewAddressDoesNotFailValidation() {
    ResponseManagementEvent responseManagementEvent = getMinimalValidNewAddress();
    Case casetoEmit = new Case();
    casetoEmit.setUprn("1234");
    when(caseService.saveNewCaseAndStampCaseRef(any(Case.class))).thenReturn(casetoEmit);
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

  @Test
  public void testNewAddressFromSourceCaseWithMinimalEventFields() {
    ReflectionTestUtils.setField(underTest, "dummyUprnPrefix", DUMMY_UPRN_PREFIX);

    EasyRandom easyRandom = new EasyRandom();
    Case sourceCase = easyRandom.nextObject(Case.class);
    sourceCase.setCaseId(UUID.randomUUID());
    sourceCase.getMetadata().setSecureEstablishment(true);
    ResponseManagementEvent newAddressEvent = getMinimalValidNewAddress();
    OffsetDateTime timeNow = OffsetDateTime.now();
    newAddressEvent.getEvent().setChannel("NOT FIELD");

    when(pubSubTemplate.publish(any(), any(ResponseManagementEvent.class)))
        .thenReturn(mockFuture());
    when(caseService.getCaseByCaseId(any())).thenReturn(sourceCase);
    when(caseService.saveNewCaseAndStampCaseRef(any())).then(returnsFirstArg());

    underTest.processNewAddressFromSourceId(newAddressEvent, timeNow, sourceCase.getCaseId());

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveNewCaseAndStampCaseRef(caseArgumentCaptor.capture());
    Case newCase = caseArgumentCaptor.getAllValues().get(0);

    CollectionCase newAddressCollectionCase =
        newAddressEvent.getPayload().getNewAddress().getCollectionCase();

    assertThat(newCase.isSkeleton()).isTrue();
    assertThat(newCase.getAddressLine1()).isEqualTo(sourceCase.getAddressLine1());
    assertThat(newCase.getAddressLine2()).isEqualTo(sourceCase.getAddressLine2());
    assertThat(newCase.getAddressLine3()).isEqualTo(sourceCase.getAddressLine3());

    assertThat(newCase.getCaseId()).isEqualTo(newAddressCollectionCase.getId());
    assertThat(newCase.getAddressType())
        .isEqualTo(newAddressCollectionCase.getAddress().getAddressType());

    assertThat(newCase.getEstabUprn()).isEqualTo(sourceCase.getEstabUprn());
    assertThat(newCase.getMetadata().getSecureEstablishment()).isTrue();

    assertThat(newCase.getLongitude()).isEqualTo(sourceCase.getLongitude());
    assertThat(newCase.getLatitude()).isEqualTo(sourceCase.getLatitude());
    assertThat(newCase.getUprn()).isEqualTo(DUMMY_UPRN_PREFIX + newCase.getCaseRef());
  }

  @Test
  public void testNewAddressFromSourceCaseWithProvidedEventFields() {
    EasyRandom easyRandom = new EasyRandom();
    Case sourceCase = easyRandom.nextObject(Case.class);
    sourceCase.setCaseId(UUID.randomUUID());
    sourceCase.getMetadata().setSecureEstablishment(false);
    ResponseManagementEvent newAddressEvent = getMinimalValidNewAddress();
    newAddressEvent
        .getPayload()
        .getNewAddress()
        .getCollectionCase()
        .getAddress()
        .setAddressLine1("666");
    newAddressEvent
        .getPayload()
        .getNewAddress()
        .getCollectionCase()
        .getAddress()
        .setLatitude("51.47");
    newAddressEvent
        .getPayload()
        .getNewAddress()
        .getCollectionCase()
        .getAddress()
        .setLongitude("1.34");

    OffsetDateTime timeNow = OffsetDateTime.now();

    newAddressEvent.getEvent().setChannel("NOT FIELD");

    when(pubSubTemplate.publish(any(), any(ResponseManagementEvent.class)))
        .thenReturn(mockFuture());
    when(caseService.getCaseByCaseId(any())).thenReturn(sourceCase);
    when(caseService.saveNewCaseAndStampCaseRef(any())).then(returnsFirstArg());

    underTest.processNewAddressFromSourceId(newAddressEvent, timeNow, sourceCase.getCaseId());

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveNewCaseAndStampCaseRef(caseArgumentCaptor.capture());
    Case newCase = caseArgumentCaptor.getAllValues().get(0);

    CollectionCase newAddressCollectionCase =
        newAddressEvent.getPayload().getNewAddress().getCollectionCase();

    assertThat(newCase.isSkeleton()).isTrue();
    assertThat(newCase.getAddressLine1())
        .isEqualTo(newAddressCollectionCase.getAddress().getAddressLine1());
    assertThat(newCase.getCaseId()).isEqualTo(newAddressCollectionCase.getId());
    assertThat(newCase.getAddressType())
        .isEqualTo(newAddressCollectionCase.getAddress().getAddressType());
    assertThat(newCase.getLatitude())
        .isEqualTo(newAddressCollectionCase.getAddress().getLatitude());
    assertThat(newCase.getLongitude())
        .isEqualTo(newAddressCollectionCase.getAddress().getLongitude());
    assertThat(newCase.getEstabUprn()).isEqualTo(sourceCase.getEstabUprn());
    assertThat(newCase.getMetadata().getSecureEstablishment()).isFalse();

    verify(caseService).saveCaseAndEmitCaseCreatedEvent(newCase, null);
  }

  @Test
  public void testMetaDataCreatedAndSentForNewCaseInRightConditions() {
    // Given
    EasyRandom easyRandom = new EasyRandom();
    Case sourceCase = easyRandom.nextObject(Case.class);
    sourceCase.setCaseId(UUID.randomUUID());
    ResponseManagementEvent newAddressEvent = getMinimalValidNewAddress();
    CollectionCase collectionCase =
        newAddressEvent.getPayload().getNewAddress().getCollectionCase();
    collectionCase.getAddress().setAddressLine1("666");
    collectionCase.getAddress().setLatitude("51.47");

    collectionCase.setCaseType("SPG");
    collectionCase.setFieldCoordinatorId("0123435");
    collectionCase.setFieldOfficerId("2342345");

    newAddressEvent.getEvent().setChannel("FIELD");

    OffsetDateTime timeNow = OffsetDateTime.now();

    when(pubSubTemplate.publish(any(), any(ResponseManagementEvent.class)))
        .thenReturn(mockFuture());
    when(caseService.getCaseByCaseId(any())).thenReturn(sourceCase);
    when(caseService.saveNewCaseAndStampCaseRef(any())).then(returnsFirstArg());

    // When
    underTest.processNewAddressFromSourceId(newAddressEvent, timeNow, sourceCase.getCaseId());

    // Then
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveNewCaseAndStampCaseRef(caseArgumentCaptor.capture());
    Case newCase = caseArgumentCaptor.getAllValues().get(0);

    CollectionCase newAddressCollectionCase =
        newAddressEvent.getPayload().getNewAddress().getCollectionCase();

    assertThat(newCase.getAddressLine1())
        .isEqualTo(newAddressCollectionCase.getAddress().getAddressLine1());
    assertThat(newCase.getCaseId()).isEqualTo(newAddressCollectionCase.getId());
    assertThat(newCase.getAddressType())
        .isEqualTo(newAddressCollectionCase.getAddress().getAddressType());
    assertThat(newCase.getLatitude())
        .isEqualTo(newAddressCollectionCase.getAddress().getLatitude());
    assertThat(newCase.getEstabUprn()).isEqualTo(sourceCase.getEstabUprn());

    ArgumentCaptor<Metadata> metadataArgumentCaptor = ArgumentCaptor.forClass(Metadata.class);

    verify(caseService)
        .saveCaseAndEmitCaseCreatedEvent(
            caseArgumentCaptor.capture(), metadataArgumentCaptor.capture());

    assertThat(caseArgumentCaptor.getValue().getCaseId())
        .isEqualTo(newAddressCollectionCase.getId());

    Metadata actualMetadata = metadataArgumentCaptor.getValue();
    assertThat(actualMetadata.getCauseEventType()).isEqualTo(EventTypeDTO.NEW_ADDRESS_REPORTED);
    assertThat(actualMetadata.getFieldDecision()).isEqualTo(ActionInstructionType.CREATE);
  }

  @Test
  public void testNoMetaDataWhenCaseTypeNotCEorSPG() {
    // Given
    EasyRandom easyRandom = new EasyRandom();
    Case sourceCase = easyRandom.nextObject(Case.class);
    sourceCase.setCaseId(UUID.randomUUID());
    ResponseManagementEvent newAddressEvent = getMinimalValidNewAddress();
    CollectionCase collectionCase =
        newAddressEvent.getPayload().getNewAddress().getCollectionCase();
    collectionCase.getAddress().setAddressLine1("666");
    collectionCase.setCaseType("HH");
    collectionCase.setFieldCoordinatorId("0123435");
    collectionCase.setFieldOfficerId("2342345");

    newAddressEvent.getEvent().setChannel("FIELD");

    when(pubSubTemplate.publish(any(), any(ResponseManagementEvent.class)))
        .thenReturn(mockFuture());
    when(caseService.getCaseByCaseId(any())).thenReturn(sourceCase);
    when(caseService.saveNewCaseAndStampCaseRef(any())).then(returnsFirstArg());
    OffsetDateTime timeNow = OffsetDateTime.now();

    // When
    underTest.processNewAddressFromSourceId(newAddressEvent, timeNow, sourceCase.getCaseId());

    verify(caseService).saveCaseAndEmitCaseCreatedEvent(any(), eq(null));
  }

  @Test
  public void testNoMetaDataWhenNoFieldOfficerId() {
    // Given
    EasyRandom easyRandom = new EasyRandom();
    Case sourceCase = easyRandom.nextObject(Case.class);
    sourceCase.setCaseId(UUID.randomUUID());
    sourceCase.setFieldOfficerId(null);
    ResponseManagementEvent newAddressEvent = getMinimalValidNewAddress();
    CollectionCase collectionCase =
        newAddressEvent.getPayload().getNewAddress().getCollectionCase();
    collectionCase.getAddress().setAddressLine1("666");
    collectionCase.setCaseType("SPG");
    collectionCase.setFieldCoordinatorId("0123435");
    collectionCase.setFieldOfficerId(null);

    newAddressEvent.getEvent().setChannel("FIELD");

    when(pubSubTemplate.publish(any(), any(ResponseManagementEvent.class)))
        .thenReturn(mockFuture());
    when(caseService.getCaseByCaseId(any())).thenReturn(sourceCase);
    when(caseService.saveNewCaseAndStampCaseRef(any())).then(returnsFirstArg());
    OffsetDateTime timeNow = OffsetDateTime.now();

    // When
    underTest.processNewAddressFromSourceId(newAddressEvent, timeNow, sourceCase.getCaseId());

    verify(caseService).saveCaseAndEmitCaseCreatedEvent(any(), eq(null));
  }

  @Test
  public void testNoMetaDataWhenNoFieldCordId() {
    // Given
    EasyRandom easyRandom = new EasyRandom();
    Case sourceCase = easyRandom.nextObject(Case.class);
    sourceCase.setCaseId(UUID.randomUUID());
    sourceCase.setFieldOfficerId(null);
    ResponseManagementEvent newAddressEvent = getMinimalValidNewAddress();
    CollectionCase collectionCase =
        newAddressEvent.getPayload().getNewAddress().getCollectionCase();
    collectionCase.getAddress().setAddressLine1("666");
    collectionCase.setCaseType("SPG");
    collectionCase.setFieldCoordinatorId(null);
    collectionCase.setFieldOfficerId("0123435");

    newAddressEvent.getEvent().setChannel("FIELD");

    when(pubSubTemplate.publish(any(), any(ResponseManagementEvent.class)))
        .thenReturn(mockFuture());
    when(caseService.getCaseByCaseId(any())).thenReturn(sourceCase);
    when(caseService.saveNewCaseAndStampCaseRef(any())).then(returnsFirstArg());
    OffsetDateTime timeNow = OffsetDateTime.now();

    // When
    underTest.processNewAddressFromSourceId(newAddressEvent, timeNow, sourceCase.getCaseId());

    verify(caseService).saveCaseAndEmitCaseCreatedEvent(any(), eq(null));
  }

  @Test
  public void testNewAddressGetsDummyUprn() {
    ReflectionTestUtils.setField(underTest, "dummyUprnPrefix", DUMMY_UPRN_PREFIX);
    ResponseManagementEvent responseManagementEvent = getMinimalValidNewAddress();
    Case casetoEmit = new Case();
    casetoEmit.setCaseRef(1234L);

    when(pubSubTemplate.publish(any(), any(ResponseManagementEvent.class)))
        .thenReturn(mockFuture());
    when(caseService.saveNewCaseAndStampCaseRef(any(Case.class))).thenReturn(casetoEmit);
    underTest.processNewAddress(responseManagementEvent, OffsetDateTime.now());
    verify(caseService).saveCase(any(Case.class));

    assertThat(casetoEmit.getUprn()).isEqualTo(DUMMY_UPRN_PREFIX + casetoEmit.getCaseRef());
  }

  private ResponseManagementEvent getMinimalValidNewAddress() {
    Address address = new Address();
    address.setAddressLevel("U");
    address.setRegion("E");
    address.setAddressType("SPG");

    CollectionCase collectionCase = new CollectionCase();
    collectionCase.setId(UUID.randomUUID());
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
    expectedCase.setCaseId(collectionCase.getId());
    expectedCase.setCollectionExerciseId(collectionCase.getCollectionExerciseId());
    expectedCase.setCaseType(collectionCase.getCaseType());
    expectedCase.setAddressLine1(collectionCase.getAddress().getAddressLine1());
    expectedCase.setAddressLine2(collectionCase.getAddress().getAddressLine2());
    expectedCase.setAddressLine3(collectionCase.getAddress().getAddressLine3());
    expectedCase.setTownName(collectionCase.getAddress().getTownName());
    expectedCase.setPostcode(collectionCase.getAddress().getPostcode());
    expectedCase.setLatitude(collectionCase.getAddress().getLatitude());
    expectedCase.setLongitude(collectionCase.getAddress().getLongitude());
    expectedCase.setUprn(collectionCase.getAddress().getUprn());
    expectedCase.setRegion(collectionCase.getAddress().getRegion());
    expectedCase.setAddressLevel(collectionCase.getAddress().getAddressLevel());
    expectedCase.setAddressType(collectionCase.getAddress().getAddressType());
    expectedCase.setUprn(collectionCase.getAddress().getUprn());
    expectedCase.setEstabType(collectionCase.getAddress().getEstabType());
    expectedCase.setOrganisationName(collectionCase.getAddress().getOrganisationName());
    expectedCase.setFieldCoordinatorId(collectionCase.getFieldCoordinatorId());
    expectedCase.setFieldOfficerId(collectionCase.getFieldOfficerId());
    expectedCase.setCeExpectedCapacity(collectionCase.getCeExpectedCapacity());

    expectedCase.setActionPlanId(EXPECTED_ACTION_PLAN_ID);
    expectedCase.setSurvey("CENSUS");
    expectedCase.setHandDelivery(false);
    expectedCase.setRefusalReceived(null);
    expectedCase.setReceiptReceived(false);
    expectedCase.setAddressInvalid(false);
    expectedCase.setSkeleton(true);
    expectedCase.setCeActualResponses(0);

    return expectedCase;
  }

  private ListenableFuture<String> mockFuture() {
    return new ListenableFuture<String>() {
      @Override
      public void addCallback(ListenableFutureCallback<? super String> listenableFutureCallback) {}

      @Override
      public void addCallback(
          SuccessCallback<? super String> successCallback, FailureCallback failureCallback) {}

      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
      }

      @Override
      public boolean isCancelled() {
        return false;
      }

      @Override
      public boolean isDone() {
        return false;
      }

      @Override
      public String get() throws InterruptedException, ExecutionException {
        return null;
      }

      @Override
      public String get(long timeout, TimeUnit unit)
          throws InterruptedException, ExecutionException, TimeoutException {
        return null;
      }
    };
  }
}
