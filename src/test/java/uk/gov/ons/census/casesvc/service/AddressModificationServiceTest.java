package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.AddressModification;
import uk.gov.ons.census.casesvc.model.dto.CollectionCaseCaseId;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.ModifiedAddress;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.utility.AddressModificationValidator;
import uk.gov.ons.census.casesvc.utility.JsonHelper;

@RunWith(MockitoJUnitRunner.class)
public class AddressModificationServiceTest {
  private static final UUID TEST_CASE_ID = UUID.randomUUID();

  @Mock private CaseService caseService;
  @Mock private EventLogger eventLogger;
  @Mock private AddressModificationValidator addressModificationValidator;

  @InjectMocks private AddressModificationService underTest;

  @Test
  public void testProcessMessageHappyPath() {
    // Given
    ResponseManagementEvent rme = getEvent();
    rme.getPayload()
        .getAddressModification()
        .getNewAddress()
        .setAddressLine1(Optional.of("modified address line 1"));
    rme.getPayload()
        .getAddressModification()
        .getNewAddress()
        .setAddressLine2(Optional.of("modified address line 2"));
    rme.getPayload()
        .getAddressModification()
        .getNewAddress()
        .setAddressLine3(Optional.of("modified address line 3"));
    rme.getPayload()
        .getAddressModification()
        .getNewAddress()
        .setOrganisationName(Optional.of("modified org name"));
    rme.getPayload()
        .getAddressModification()
        .getNewAddress()
        .setEstabType(Optional.of("HOUSEHOLD"));
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    Case caze = new Case();
    caze.setAddressLine1("test address line 1");
    caze.setAddressLine2("test address line 2");
    caze.setAddressLine3("test address line 3");
    caze.setOrganisationName("test org name");
    caze.setEstabType("test estab type");
    caze.setCaseType("HH");
    Mockito.when(caseService.getCaseByCaseId(any())).thenReturn(caze);

    // When
    underTest.processMessage(rme, messageTimestamp);

    // Then
    verify(caseService).getCaseByCaseId(TEST_CASE_ID);

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveCaseAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture(), isNull());

    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase).isEqualTo(caze);
    assertThat(actualCase.getAddressLine1()).isEqualTo("modified address line 1");
    assertThat(actualCase.getAddressLine2()).isEqualTo("modified address line 2");
    assertThat(actualCase.getAddressLine3()).isEqualTo("modified address line 3");
    assertThat(actualCase.getOrganisationName()).isEqualTo("modified org name");
    assertThat(actualCase.getEstabType()).isEqualTo("HOUSEHOLD");

    verify(eventLogger)
        .logCaseEvent(
            caze,
            rme.getEvent().getDateTime(),
            "Address modified",
            EventType.ADDRESS_MODIFIED,
            rme.getEvent(),
            JsonHelper.convertObjectToJson(rme.getPayload().getAddressModification()),
            messageTimestamp);
  }

  @Test
  public void testProcessMessageHappyPathSetBunchOfNulls() {
    // Given
    ResponseManagementEvent rme = getEvent();
    rme.getPayload().getAddressModification().getNewAddress().setAddressLine2(Optional.empty());
    rme.getPayload().getAddressModification().getNewAddress().setAddressLine3(Optional.empty());
    rme.getPayload().getAddressModification().getNewAddress().setOrganisationName(Optional.empty());
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    Case caze = new Case();
    caze.setAddressLine1("test address line 1");
    caze.setAddressLine2("test address line 2");
    caze.setAddressLine3("test address line 3");
    caze.setTownName("test town name");
    caze.setOrganisationName("test org name");
    caze.setEstabType("test estab type");
    caze.setCaseType("HH");
    Mockito.when(caseService.getCaseByCaseId(any())).thenReturn(caze);

    // When
    underTest.processMessage(rme, messageTimestamp);

    // Then
    verify(caseService).getCaseByCaseId(TEST_CASE_ID);

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveCaseAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture(), isNull());

    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase).isEqualTo(caze);
    assertThat(actualCase.getAddressLine1()).isEqualTo("test address line 1");
    assertThat(actualCase.getAddressLine2()).isNull();
    assertThat(actualCase.getAddressLine3()).isNull();
    assertThat(actualCase.getTownName()).isEqualTo("test town name");
    assertThat(actualCase.getOrganisationName()).isNull();
    assertThat(actualCase.getEstabType()).isEqualTo("test estab type");

    verify(eventLogger)
        .logCaseEvent(
            caze,
            rme.getEvent().getDateTime(),
            "Address modified",
            EventType.ADDRESS_MODIFIED,
            rme.getEvent(),
            JsonHelper.convertObjectToJson(rme.getPayload().getAddressModification()),
            messageTimestamp);
  }

  @Test
  public void testProcessMessageHappyPathDontChangeAnything() {
    // Given
    ResponseManagementEvent rme = getEvent();
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    Case caze = new Case();
    caze.setAddressLine1("test address line 1");
    caze.setAddressLine2("test address line 2");
    caze.setAddressLine3("test address line 3");
    caze.setTownName("test town name");
    caze.setOrganisationName("test org name");
    caze.setEstabType("test estab type");
    caze.setCaseType("HH");
    Mockito.when(caseService.getCaseByCaseId(any())).thenReturn(caze);

    // When
    underTest.processMessage(rme, messageTimestamp);

    // Then
    verify(caseService).getCaseByCaseId(TEST_CASE_ID);

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveCaseAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture(), isNull());

    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase).isEqualTo(caze);
    assertThat(actualCase.getAddressLine1()).isEqualTo("test address line 1");
    assertThat(actualCase.getAddressLine2()).isEqualTo("test address line 2");
    assertThat(actualCase.getAddressLine3()).isEqualTo("test address line 3");
    assertThat(actualCase.getTownName()).isEqualTo("test town name");
    assertThat(actualCase.getOrganisationName()).isEqualTo("test org name");
    assertThat(actualCase.getEstabType()).isEqualTo("test estab type");

    verify(eventLogger)
        .logCaseEvent(
            caze,
            rme.getEvent().getDateTime(),
            "Address modified",
            EventType.ADDRESS_MODIFIED,
            rme.getEvent(),
            JsonHelper.convertObjectToJson(rme.getPayload().getAddressModification()),
            messageTimestamp);
  }

  private ResponseManagementEvent getEvent() {
    CollectionCaseCaseId collectionCaseCaseId = new CollectionCaseCaseId();
    collectionCaseCaseId.setId(TEST_CASE_ID);

    ModifiedAddress newAddress = new ModifiedAddress();

    AddressModification addressModification = new AddressModification();
    addressModification.setCollectionCase(collectionCaseCaseId);
    addressModification.setNewAddress(newAddress);

    PayloadDTO payload = new PayloadDTO();
    payload.setAddressModification(addressModification);

    EventDTO event = new EventDTO();
    event.setDateTime(OffsetDateTime.now());

    ResponseManagementEvent rme = new ResponseManagementEvent();
    rme.setEvent(event);
    rme.setPayload(payload);

    return rme;
  }
}
