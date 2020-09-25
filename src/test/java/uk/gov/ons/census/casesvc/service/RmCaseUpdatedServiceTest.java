package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.Metadata;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.RmCaseUpdated;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;

@RunWith(MockitoJUnitRunner.class)
public class RmCaseUpdatedServiceTest {
  @Mock private CaseService caseService;

  @Mock private EventLogger eventLogger;

  @Mock private Set<String> estabTypes;

  @InjectMocks private RmCaseUpdatedService underTest;

  @Test
  public void testMinimumChanges() {
    // Given
    // Set up minimum expected data on a skeleton case
    Case caseToUpdate = setUpMinimumGoodSkeletonCase();

    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    RmCaseUpdated rmCaseUpdated = rme.getPayload().getRmCaseUpdated();

    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(caseService.getCaseByCaseId(any())).thenReturn(caseToUpdate);
    when(estabTypes.contains(eq("TEST_ESTAB_TYPE"))).thenReturn(true);

    // When
    underTest.processMessage(rme, messageTimestamp);

    // Then
    assertThat(caseToUpdate.isSkeleton()).isFalse();
    assertThat(caseToUpdate.getTreatmentCode()).isEqualTo(rmCaseUpdated.getTreatmentCode());
    assertThat(caseToUpdate.getOa()).isEqualTo(rmCaseUpdated.getOa());
    assertThat(caseToUpdate.getMsoa()).isEqualTo(rmCaseUpdated.getMsoa());
    assertThat(caseToUpdate.getLsoa()).isEqualTo(rmCaseUpdated.getLsoa());
    assertThat(caseToUpdate.getFieldCoordinatorId())
        .isEqualTo(rmCaseUpdated.getFieldCoordinatorId());
    assertThat(caseToUpdate.getFieldOfficerId()).isEqualTo(rmCaseUpdated.getFieldOfficerId());
    assertThat(caseToUpdate.getEstabType()).isEqualTo(rmCaseUpdated.getEstabType());
    assertThat(caseToUpdate.getLatitude()).isEqualTo(rmCaseUpdated.getLatitude());
    assertThat(caseToUpdate.getLongitude()).isEqualTo(rmCaseUpdated.getLongitude());

    ArgumentCaptor<Metadata> metadataArgumentCaptor = ArgumentCaptor.forClass(Metadata.class);
    verify(caseService)
        .saveCaseAndEmitCaseUpdatedEvent(eq(caseToUpdate), metadataArgumentCaptor.capture());
    Metadata eventMetadata = metadataArgumentCaptor.getValue();
    assertThat(eventMetadata.getFieldDecision()).isEqualTo(ActionInstructionType.CREATE);
    assertThat(eventMetadata.getCauseEventType()).isEqualTo(EventTypeDTO.RM_CASE_UPDATED);

    verify(eventLogger)
        .logCaseEvent(
            eq(caseToUpdate),
            any(),
            eq("Case details updated"),
            eq(EventType.RM_CASE_UPDATED),
            eq(rme.getEvent()),
            eq(convertObjectToJson(rmCaseUpdated)),
            any());
  }

  //  @Test
  //  public void testUpdateActionToField() {
  //    // Given
  //    // Set up minimum expected data on a skeleton case
  //    Case caseToUpdate = setUpMinimumGoodSkeletonCase();
  //    caseToUpdate.setOa("Test OA");
  //
  //    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
  //    RmCaseUpdated rmCaseUpdated = rme.getPayload().getRmCaseUpdated();
  //
  //    OffsetDateTime messageTimestamp = OffsetDateTime.now();
  //
  //    when(caseService.getCaseByCaseId(any())).thenReturn(caseToUpdate);
  //    when(estabTypes.contains(eq("TEST_ESTAB_TYPE"))).thenReturn(true);
  //
  //    // When
  //    underTest.processMessage(rme, messageTimestamp);
  //
  //    // Then
  //    // UPDATE should be sent if field already know about it (OA value present on original case)
  //    ArgumentCaptor<Metadata> metadataArgumentCaptor = ArgumentCaptor.forClass(Metadata.class);
  //    verify(caseService)
  //        .saveCaseAndEmitCaseUpdatedEvent(eq(caseToUpdate), metadataArgumentCaptor.capture());
  //    Metadata eventMetadata = metadataArgumentCaptor.getValue();
  //    assertThat(eventMetadata.getFieldDecision()).isEqualTo(ActionInstructionType.UPDATE);
  //  }

  @Test
  public void testMaximumChanges() {
    // Given
    // Set up with absolute minimum data
    Case caseToUpdate = new Case();
    caseToUpdate.setSkeleton(true);
    caseToUpdate.setCaseId(UUID.randomUUID());
    caseToUpdate.setAddressType("CE");
    caseToUpdate.setCaseType("CE");
    caseToUpdate.setAddressLevel("E");
    caseToUpdate.setCaseRef(123456789L);
    caseToUpdate.setRegion("Enonsensenumbers");

    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    RmCaseUpdated rmCaseUpdated = rme.getPayload().getRmCaseUpdated();

    // Set up all possible data on rm case updated event
    rmCaseUpdated.setAddressLine1(Optional.of("TEST existing address line 1"));
    rmCaseUpdated.setTownName(Optional.of("Springfield"));
    rmCaseUpdated.setPostcode(Optional.of("AB12CD"));
    rmCaseUpdated.setHtcWillingness(Optional.of("1"));
    rmCaseUpdated.setHtcDigital(Optional.of("1"));
    rmCaseUpdated.setAbpCode(Optional.of("7"));
    rmCaseUpdated.setUprn(Optional.of("Dummy"));
    rmCaseUpdated.setEstabUprn(Optional.of("Dummy"));
    rmCaseUpdated.setSecureEstablishment(Optional.of(true));
    rmCaseUpdated.setPrintBatch(Optional.of("99"));
    rmCaseUpdated.setOrganisationName(Optional.of("Null incorporated"));
    rmCaseUpdated.setCeExpectedCapacity(Optional.of(9001));

    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(caseService.getCaseByCaseId(any())).thenReturn(caseToUpdate);
    when(estabTypes.contains(eq("TEST_ESTAB_TYPE"))).thenReturn(true);

    // When
    underTest.processMessage(rme, messageTimestamp);

    // Then
    assertThat(caseToUpdate.isSkeleton()).isFalse();
    assertThat(caseToUpdate.getTreatmentCode()).isEqualTo(rmCaseUpdated.getTreatmentCode());
    assertThat(caseToUpdate.getOa()).isEqualTo(rmCaseUpdated.getOa());
    assertThat(caseToUpdate.getMsoa()).isEqualTo(rmCaseUpdated.getMsoa());
    assertThat(caseToUpdate.getLsoa()).isEqualTo(rmCaseUpdated.getLsoa());
    assertThat(caseToUpdate.getFieldCoordinatorId())
        .isEqualTo(rmCaseUpdated.getFieldCoordinatorId());
    assertThat(caseToUpdate.getFieldOfficerId()).isEqualTo(rmCaseUpdated.getFieldOfficerId());
    assertThat(caseToUpdate.getEstabType()).isEqualTo(rmCaseUpdated.getEstabType());
    assertThat(caseToUpdate.getLatitude()).isEqualTo(rmCaseUpdated.getLatitude());
    assertThat(caseToUpdate.getLongitude()).isEqualTo(rmCaseUpdated.getLongitude());
    assertThat(caseToUpdate.getAddressLine1()).isEqualTo(rmCaseUpdated.getAddressLine1().get());
    assertThat(caseToUpdate.getTownName()).isEqualTo(rmCaseUpdated.getTownName().get());
    assertThat(caseToUpdate.getPostcode()).isEqualTo(rmCaseUpdated.getPostcode().get());
    assertThat(caseToUpdate.getHtcDigital()).isEqualTo(rmCaseUpdated.getHtcDigital().get());
    assertThat(caseToUpdate.getHtcWillingness()).isEqualTo(rmCaseUpdated.getHtcWillingness().get());
    assertThat(caseToUpdate.getAbpCode()).isEqualTo(rmCaseUpdated.getAbpCode().get());
    assertThat(caseToUpdate.getEstabUprn()).isEqualTo(rmCaseUpdated.getEstabUprn().get());
    assertThat(caseToUpdate.getUprn()).isEqualTo(rmCaseUpdated.getUprn().get());
    assertThat(caseToUpdate.getOrganisationName())
        .isEqualTo(rmCaseUpdated.getOrganisationName().get());
    assertThat(caseToUpdate.getPrintBatch()).isEqualTo(rmCaseUpdated.getPrintBatch().get());
    assertThat(caseToUpdate.getCeExpectedCapacity())
        .isEqualTo(rmCaseUpdated.getCeExpectedCapacity().get());
    assertThat(caseToUpdate.getMetadata().getSecureEstablishment())
        .isEqualTo(rmCaseUpdated.getSecureEstablishment().get());

    ArgumentCaptor<Metadata> metadataArgumentCaptor = ArgumentCaptor.forClass(Metadata.class);
    verify(caseService)
        .saveCaseAndEmitCaseUpdatedEvent(eq(caseToUpdate), metadataArgumentCaptor.capture());
    Metadata eventMetadata = metadataArgumentCaptor.getValue();
    assertThat(eventMetadata.getFieldDecision()).isEqualTo(ActionInstructionType.CREATE);
    assertThat(eventMetadata.getCauseEventType()).isEqualTo(EventTypeDTO.RM_CASE_UPDATED);

    verify(eventLogger)
        .logCaseEvent(
            eq(caseToUpdate),
            any(),
            eq("Case details updated"),
            eq(EventType.RM_CASE_UPDATED),
            eq(rme.getEvent()),
            eq(convertObjectToJson(rmCaseUpdated)),
            any());
  }

  @Test
  public void testMissingTreatmentCodeOnMessage() {
    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    rme.getPayload().getRmCaseUpdated().setTreatmentCode(null);

    expectExceptionOnProcessMessage("RM_CASE_UPDATED message missing mandatory field(s)", rme);
  }

  @Test
  public void testMissingOaOnMessage() {
    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    rme.getPayload().getRmCaseUpdated().setOa(null);

    expectExceptionOnProcessMessage("RM_CASE_UPDATED message missing mandatory field(s)", rme);
  }

  @Test
  public void testMissingLsoaOnMessage() {
    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    rme.getPayload().getRmCaseUpdated().setLsoa(null);

    expectExceptionOnProcessMessage("RM_CASE_UPDATED message missing mandatory field(s)", rme);
  }

  @Test
  public void testMissingMsoaOnMessage() {
    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    rme.getPayload().getRmCaseUpdated().setMsoa(null);

    expectExceptionOnProcessMessage("RM_CASE_UPDATED message missing mandatory field(s)", rme);
  }

  @Test
  public void testMissingLadOnMessage() {
    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    rme.getPayload().getRmCaseUpdated().setLad(null);

    expectExceptionOnProcessMessage("RM_CASE_UPDATED message missing mandatory field(s)", rme);
  }

  @Test
  public void testMissingFieldCoordinatorIdOnMessage() {
    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    rme.getPayload().getRmCaseUpdated().setFieldCoordinatorId(null);

    expectExceptionOnProcessMessage("RM_CASE_UPDATED message missing mandatory field(s)", rme);
  }

  @Test
  public void testMissingFieldOfficerIdOnMessage() {
    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    rme.getPayload().getRmCaseUpdated().setFieldOfficerId(null);

    expectExceptionOnProcessMessage("RM_CASE_UPDATED message missing mandatory field(s)", rme);
  }

  @Test
  public void testMissingLatitudeOnMessage() {
    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    rme.getPayload().getRmCaseUpdated().setLatitude(null);

    expectExceptionOnProcessMessage("RM_CASE_UPDATED message missing mandatory field(s)", rme);
  }

  @Test
  public void testMissingLongitudeOnMessage() {
    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    rme.getPayload().getRmCaseUpdated().setLongitude(null);

    expectExceptionOnProcessMessage("RM_CASE_UPDATED message missing mandatory field(s)", rme);
  }

  @Test
  public void testInvalidEstabTypeOnMessage() {
    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    rme.getPayload().getRmCaseUpdated().setEstabType("INVALID_TYPE");

    expectExceptionOnProcessMessage("estabType not valid on RM_CASE_UPDATED event", rme);
  }

  @Test
  public void testInvalidLatitudeOnMessage() {
    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    when(estabTypes.contains(any())).thenReturn(true);
    rme.getPayload().getRmCaseUpdated().setLatitude("n");

    expectExceptionOnProcessMessage(
        "Character n is neither a decimal digit number, decimal point, nor \"e\" notation exponential mark.",
        rme);
  }

  @Test
  public void testInvalidLongitudeOnMessage() {
    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    when(estabTypes.contains(any())).thenReturn(true);
    rme.getPayload().getRmCaseUpdated().setLongitude("x");

    expectExceptionOnProcessMessage(
        "Character x is neither a decimal digit number, decimal point, nor \"e\" notation exponential mark.",
        rme);
  }

  @Test
  public void testMissingEstabUprnOnCase() {
    Case caseToUpdate = setUpMinimumGoodSkeletonCase();
    caseToUpdate.setEstabUprn(null);

    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    when(caseService.getCaseByCaseId(any())).thenReturn(caseToUpdate);
    when(estabTypes.contains(eq("TEST_ESTAB_TYPE"))).thenReturn(true);

    // When, Then
    expectExceptionOnProcessMessage("Case missing mandatory fields after RM Case Updated", rme);
  }

  @Test
  public void testMissingAbpCodeOnCase() {
    Case caseToUpdate = setUpMinimumGoodSkeletonCase();
    caseToUpdate.setAbpCode(null);

    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    when(caseService.getCaseByCaseId(any())).thenReturn(caseToUpdate);
    when(estabTypes.contains(eq("TEST_ESTAB_TYPE"))).thenReturn(true);

    // When, Then
    expectExceptionOnProcessMessage("Case missing mandatory fields after RM Case Updated", rme);
  }

  @Test
  public void testMissingAddressLine1OnCase() {
    Case caseToUpdate = setUpMinimumGoodSkeletonCase();
    caseToUpdate.setAddressLine1(null);

    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    when(caseService.getCaseByCaseId(any())).thenReturn(caseToUpdate);
    when(estabTypes.contains(eq("TEST_ESTAB_TYPE"))).thenReturn(true);

    // When, Then
    expectExceptionOnProcessMessage("Case missing mandatory fields after RM Case Updated", rme);
  }

  @Test
  public void testMissingTownNameOnCase() {
    Case caseToUpdate = setUpMinimumGoodSkeletonCase();
    caseToUpdate.setTownName(null);

    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    when(caseService.getCaseByCaseId(any())).thenReturn(caseToUpdate);
    when(estabTypes.contains(eq("TEST_ESTAB_TYPE"))).thenReturn(true);

    // When, Then
    expectExceptionOnProcessMessage("Case missing mandatory fields after RM Case Updated", rme);
  }

  @Test
  public void testMissingPostcodeOnCase() {
    Case caseToUpdate = setUpMinimumGoodSkeletonCase();
    caseToUpdate.setPostcode(null);

    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    when(caseService.getCaseByCaseId(any())).thenReturn(caseToUpdate);
    when(estabTypes.contains(eq("TEST_ESTAB_TYPE"))).thenReturn(true);

    // When, Then
    expectExceptionOnProcessMessage("Case missing mandatory fields after RM Case Updated", rme);
  }

  @Test
  public void testMissingRegionOnCase() {
    Case caseToUpdate = setUpMinimumGoodSkeletonCase();
    caseToUpdate.setRegion(null);

    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    when(caseService.getCaseByCaseId(any())).thenReturn(caseToUpdate);
    when(estabTypes.contains(eq("TEST_ESTAB_TYPE"))).thenReturn(true);

    // When, Then
    expectExceptionOnProcessMessage("Case missing mandatory fields after RM Case Updated", rme);
  }

  @Test
  public void testMissingAddressTypeOnCase() {
    Case caseToUpdate = setUpMinimumGoodSkeletonCase();
    caseToUpdate.setAddressType(null);

    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    when(caseService.getCaseByCaseId(any())).thenReturn(caseToUpdate);
    when(estabTypes.contains(eq("TEST_ESTAB_TYPE"))).thenReturn(true);

    // When, Then
    expectExceptionOnProcessMessage("Case missing mandatory fields after RM Case Updated", rme);
  }

  @Test
  public void testMissingAddressLevelOnCase() {
    Case caseToUpdate = setUpMinimumGoodSkeletonCase();
    caseToUpdate.setAddressLevel(null);

    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    when(caseService.getCaseByCaseId(any())).thenReturn(caseToUpdate);
    when(estabTypes.contains(eq("TEST_ESTAB_TYPE"))).thenReturn(true);

    // When, Then
    expectExceptionOnProcessMessage("Case missing mandatory fields after RM Case Updated", rme);
  }

  @Test
  public void testMissingCaseTypeOnCase() {
    Case caseToUpdate = setUpMinimumGoodSkeletonCase();
    caseToUpdate.setCaseType(null);

    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    when(caseService.getCaseByCaseId(any())).thenReturn(caseToUpdate);
    when(estabTypes.contains(eq("TEST_ESTAB_TYPE"))).thenReturn(true);

    // When, Then
    expectExceptionOnProcessMessage("Case missing mandatory fields after RM Case Updated", rme);
  }

  @Test
  public void testMissingHtcWillingnessOnCase() {
    Case caseToUpdate = setUpMinimumGoodSkeletonCase();
    caseToUpdate.setHtcWillingness(null);

    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    when(caseService.getCaseByCaseId(any())).thenReturn(caseToUpdate);
    when(estabTypes.contains(eq("TEST_ESTAB_TYPE"))).thenReturn(true);

    // When, Then
    expectExceptionOnProcessMessage("Case missing mandatory fields after RM Case Updated", rme);
  }

  @Test
  public void testMissingHtcDigitalOnCase() {
    Case caseToUpdate = setUpMinimumGoodSkeletonCase();
    caseToUpdate.setHtcDigital(null);

    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    when(caseService.getCaseByCaseId(any())).thenReturn(caseToUpdate);
    when(estabTypes.contains(eq("TEST_ESTAB_TYPE"))).thenReturn(true);

    // When, Then
    expectExceptionOnProcessMessage("Case missing mandatory fields after RM Case Updated", rme);
  }

  @Test
  public void testNullAddressLine1OnMessage() {
    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    rme.getPayload().getRmCaseUpdated().setAddressLine1(Optional.empty());

    expectExceptionOnProcessMessage("addressLine1 cannot be null on an RM_CASE_UPDATED event", rme);
  }

  @Test
  public void testNullTownNameOnMessage() {
    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    rme.getPayload().getRmCaseUpdated().setTownName(Optional.empty());

    expectExceptionOnProcessMessage("townName cannot be null on an RM_CASE_UPDATED event", rme);
  }

  @Test
  public void testNullPostcodeOnMessage() {
    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    rme.getPayload().getRmCaseUpdated().setPostcode(Optional.empty());

    expectExceptionOnProcessMessage("postcode cannot be null on an RM_CASE_UPDATED event", rme);
  }

  @Test
  public void testNullUprnOnMessage() {
    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    rme.getPayload().getRmCaseUpdated().setUprn(Optional.empty());

    expectExceptionOnProcessMessage("uprn cannot be null on an RM_CASE_UPDATED event", rme);
  }

  @Test
  public void testNullEstabUprnOnMessage() {
    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    rme.getPayload().getRmCaseUpdated().setEstabUprn(Optional.empty());

    expectExceptionOnProcessMessage("estabUprn cannot be null on an RM_CASE_UPDATED event", rme);
  }

  @Test
  public void testNullHtcWillingnessOnMessage() {
    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    rme.getPayload().getRmCaseUpdated().setHtcWillingness(Optional.empty());

    expectExceptionOnProcessMessage(
        "htcWillingness cannot be null on an RM_CASE_UPDATED event", rme);
  }

  @Test
  public void testNullHtcDigitalOnMessage() {
    ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
    rme.getPayload().getRmCaseUpdated().setHtcDigital(Optional.empty());

    expectExceptionOnProcessMessage("htcDigital cannot be null on an RM_CASE_UPDATED event", rme);
  }

  private void expectExceptionOnProcessMessage(String message, ResponseManagementEvent rme) {
    try {
      underTest.processMessage(rme, null);
      fail("Expected exception not thrown");
    } catch (RuntimeException e) {
      assertThat(e.getMessage()).isEqualTo(message);
    }
  }

  private ResponseManagementEvent setUpMinimumGoodRmCaseUpdatedEvent() {
    ResponseManagementEvent rme = new ResponseManagementEvent();

    EventDTO event = new EventDTO();
    event.setChannel("TEST");
    event.setType(EventTypeDTO.RM_CASE_UPDATED);
    rme.setEvent(event);

    PayloadDTO payload = new PayloadDTO();
    rme.setPayload(payload);

    RmCaseUpdated rmCaseUpdated = new RmCaseUpdated();
    payload.setRmCaseUpdated(rmCaseUpdated);

    // Set up mandatory data on rm case updated event
    rmCaseUpdated.setTreatmentCode("TEST TreatmentCode CODE");
    rmCaseUpdated.setOa("TEST Oa CODE");
    rmCaseUpdated.setLsoa("TEST Lsoa CODE");
    rmCaseUpdated.setMsoa("TEST Msoa CODE");
    rmCaseUpdated.setLad("TEST Lad CODE");
    rmCaseUpdated.setFieldCoordinatorId("TEST FieldCoordinatorId CODE");
    rmCaseUpdated.setFieldOfficerId("TEST FieldOfficerId CODE");
    rmCaseUpdated.setLatitude("123.456");
    rmCaseUpdated.setLongitude("000.000");
    rmCaseUpdated.setEstabType("TEST_ESTAB_TYPE");
    return rme;
  }

  private Case setUpMinimumGoodSkeletonCase() {
    Case caze = new Case();
    caze.setSkeleton(true);
    caze.setCaseId(UUID.randomUUID());
    caze.setAddressLine1("TEST existing address line 1");
    caze.setAddressType("HH");
    caze.setCaseType("HH");
    caze.setAddressLevel("U");
    caze.setTownName("Springfield");
    caze.setPostcode("AB12CD");
    caze.setRegion("Enonsensenumbers");
    caze.setCaseRef(123456789L);
    caze.setHtcWillingness("1");
    caze.setHtcDigital("1");
    caze.setAbpCode("7");
    caze.setUprn("Dummy");
    caze.setEstabUprn("Dummy");
    caze.setRefusalReceived(null);
    return caze;
  }
}
