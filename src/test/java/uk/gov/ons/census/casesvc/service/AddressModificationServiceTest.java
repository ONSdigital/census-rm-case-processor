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
import uk.gov.ons.census.casesvc.utility.JsonHelper;

@RunWith(MockitoJUnitRunner.class)
public class AddressModificationServiceTest {
  private static final UUID TEST_CASE_ID = UUID.randomUUID();

  @Mock private CaseService caseService;

  @Mock private EventLogger eventLogger;

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
        .setTownName(Optional.of("modified town name"));
    rme.getPayload()
        .getAddressModification()
        .getNewAddress()
        .setOrganisationName(Optional.of("modified org name"));
    rme.getPayload().getAddressModification().getNewAddress().setEstabType(Optional.of("HOSPITAL"));
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    Case caze = new Case();
    caze.setAddressLine1("test address line 1");
    caze.setAddressLine2("test address line 2");
    caze.setAddressLine3("test address line 3");
    caze.setTownName("test town name");
    caze.setOrganisationName("test org name");
    caze.setEstabType("test estab type");
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
    assertThat(actualCase.getTownName()).isEqualTo("modified town name");
    assertThat(actualCase.getOrganisationName()).isEqualTo("modified org name");
    assertThat(actualCase.getEstabType()).isEqualTo("HOSPITAL");

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

  @Test(expected = RuntimeException.class)
  public void testProcessMessageMissingMandatoryFieldTownNameNull() {
    // Given
    ResponseManagementEvent rme = getEvent();
    rme.getPayload()
        .getAddressModification()
        .getNewAddress()
        .setAddressLine1(Optional.of("123 Fake Street"));
    rme.getPayload()
        .getAddressModification()
        .getNewAddress()
        .setEstabType(Optional.of("HOUSEHOLD"));
    rme.getPayload().getAddressModification().getNewAddress().setTownName(Optional.empty());
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    // When
    underTest.processMessage(rme, messageTimestamp);

    // Then
  }

  @Test(expected = RuntimeException.class)
  public void testProcessMessageMissingMandatoryFieldTownNameEmpty() {
    // Given
    ResponseManagementEvent rme = getEvent();
    rme.getPayload()
        .getAddressModification()
        .getNewAddress()
        .setAddressLine1(Optional.of("123 Fake Street"));
    rme.getPayload()
        .getAddressModification()
        .getNewAddress()
        .setEstabType(Optional.of("HOUSEHOLD"));
    rme.getPayload().getAddressModification().getNewAddress().setTownName(Optional.of(" "));
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    // When
    underTest.processMessage(rme, messageTimestamp);

    // Then
  }

  @Test(expected = RuntimeException.class)
  public void testProcessMessageMissingMandatoryFieldAddressLine1Null() {
    // Given
    ResponseManagementEvent rme = getEvent();
    rme.getPayload().getAddressModification().getNewAddress().setAddressLine1(Optional.empty());
    rme.getPayload()
        .getAddressModification()
        .getNewAddress()
        .setEstabType(Optional.of("HOUSEHOLD"));
    rme.getPayload().getAddressModification().getNewAddress().setTownName(Optional.of("Fake Town"));
    rme.getPayload()
        .getAddressModification()
        .getNewAddress()
        .setEstabType(Optional.of("HOUSEHOLD"));
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    // When
    underTest.processMessage(rme, messageTimestamp);

    // Then
  }

  @Test(expected = RuntimeException.class)
  public void testProcessMessageMissingMandatoryFieldAddressLine1Empty() {
    // Given
    ResponseManagementEvent rme = getEvent();
    rme.getPayload().getAddressModification().getNewAddress().setAddressLine1(Optional.of(" "));
    rme.getPayload()
        .getAddressModification()
        .getNewAddress()
        .setEstabType(Optional.of("HOUSEHOLD"));
    rme.getPayload().getAddressModification().getNewAddress().setTownName(Optional.of("Fake Town"));
    rme.getPayload()
        .getAddressModification()
        .getNewAddress()
        .setEstabType(Optional.of("HOUSEHOLD"));
    rme.getPayload()
        .getAddressModification()
        .getNewAddress()
        .setEstabType(Optional.of("HOUSEHOLD"));
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    // When
    underTest.processMessage(rme, messageTimestamp);

    // Then
  }

  @Test(expected = RuntimeException.class)
  public void testProcessMessageMissingMandatoryFieldEstabType() {
    // Given
    ResponseManagementEvent rme = getEvent();
    rme.getPayload()
        .getAddressModification()
        .getNewAddress()
        .setAddressLine1(Optional.of("123 Fake Street"));
    rme.getPayload().getAddressModification().getNewAddress().setTownName(Optional.of("Fake Town"));
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    // When
    underTest.processMessage(rme, messageTimestamp);

    // Then
  }

  @Test(expected = RuntimeException.class)
  public void testProcessMessageEstabTypeInvalid() {
    // Given
    ResponseManagementEvent rme = getEvent();
    rme.getPayload()
        .getAddressModification()
        .getNewAddress()
        .setAddressLine1(Optional.of("123 Fake Street"));
    rme.getPayload().getAddressModification().getNewAddress().setTownName(Optional.of("Fake Town"));
    rme.getPayload()
        .getAddressModification()
        .getNewAddress()
        .setEstabType(Optional.of("SPACE STATION"));
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    // When
    underTest.processMessage(rme, messageTimestamp);

    // Then
  }

  @Test(expected = RuntimeException.class)
  public void testProcessMessageEstabTypeNull() {
    // Given
    ResponseManagementEvent rme = getEvent();
    rme.getPayload()
        .getAddressModification()
        .getNewAddress()
        .setAddressLine1(Optional.of("123 Fake Street"));
    rme.getPayload().getAddressModification().getNewAddress().setTownName(Optional.of("Fake Town"));
    rme.getPayload().getAddressModification().getNewAddress().setEstabType(Optional.empty());
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    // When
    underTest.processMessage(rme, messageTimestamp);

    // Then
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
