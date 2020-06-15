package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.RESPONSE_RECEIVED;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.Metadata;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@RunWith(MockitoJUnitRunner.class)
public class CaseReceiptServiceTest {
  private static final String HOUSEHOLD_INDIVIDUAL_QID_TYPE = "21";
  private static final String HOUSEHOLD_HH_ENGLAND_QID_TYPE = "01";
  private static final String ENGLAND_HOUSEHOLD_CONTINUATION = "11";
  private static final String ENGLAND_CE_QID = "31";
  private static final String EQ_EVENT_CHANNEL = "EQ";
  private static final String HOUSEHOLD_CASE_TYPE = "HH";
  private static final String UNIT_ADDRESS_LEVEL = "U";

  @Test
  public void testInactiveQidDoesNotReceiptsCaseAlreadyReceipted() {
    // Given
    CaseService caseService = mock(CaseService.class);
    CaseRepository caseRepository = mock(CaseRepository.class);
    CaseReceiptService caseReceiptService = new CaseReceiptService(caseService, caseRepository);

    Case caze = new Case();
    caze.setCaseId(UUID.randomUUID());
    caze.setReceiptReceived(true);
    caze.setCaseType(HOUSEHOLD_CASE_TYPE);
    caze.setAddressLevel(UNIT_ADDRESS_LEVEL);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(HOUSEHOLD_HH_ENGLAND_QID_TYPE);
    uacQidLink.setCaze(caze);

    // When
    caseReceiptService.receiptCase(uacQidLink, getReceiptEventDTO());

    // Then
    verifyNoInteractions(caseService);
  }

  @Test
  public void testNonEqReceiptForQidMarkedBlankQuestionnaireDoesNotReceiptCase() {
    // Given
    CaseService caseService = mock(CaseService.class);
    CaseRepository caseRepository = mock(CaseRepository.class);
    CaseReceiptService caseReceiptService = new CaseReceiptService(caseService, caseRepository);

    Case caze = new Case();
    caze.setCaseId(UUID.randomUUID());

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(HOUSEHOLD_INDIVIDUAL_QID_TYPE);
    uacQidLink.setBlankQuestionnaire(true);
    uacQidLink.setCaze(caze);
    EventDTO receiptEvent = getReceiptEventDTO();
    receiptEvent.setChannel("NOT_EQ");

    // When
    caseReceiptService.receiptCase(uacQidLink, receiptEvent);

    // Then
    verifyNoInteractions(caseService);
  }

  @Test
  public void testEqReceiptForQidMarkedBlankQuestionnaireReceiptsCase() {
    // Given
    CaseService caseService = mock(CaseService.class);
    CaseRepository caseRepository = mock(CaseRepository.class);
    CaseReceiptService caseReceiptService = new CaseReceiptService(caseService, caseRepository);

    Case caze = new Case();
    caze.setCaseId(UUID.randomUUID());
    caze.setCaseType("HH");
    caze.setAddressLevel("U");

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(HOUSEHOLD_HH_ENGLAND_QID_TYPE);
    uacQidLink.setBlankQuestionnaire(true);
    uacQidLink.setCaze(caze);

    EventDTO receiptEvent = getReceiptEventDTO();
    receiptEvent.setChannel(EQ_EVENT_CHANNEL);

    // When
    caseReceiptService.receiptCase(uacQidLink, receiptEvent);

    // Then
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    ArgumentCaptor<Metadata> metadataArgumentCaptor = ArgumentCaptor.forClass(Metadata.class);
    verify(caseService)
        .saveCaseAndEmitCaseUpdatedEvent(
            caseArgumentCaptor.capture(), metadataArgumentCaptor.capture());

    // The case is receipted
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase.isReceiptReceived()).as("Case Receipted").isTrue();

    // The follow up cancel instruction is sent
    Metadata metadata = metadataArgumentCaptor.getValue();
    assertThat(metadata.getCauseEventType()).isEqualTo(RESPONSE_RECEIVED);
    assertThat(metadata.getFieldDecision()).isEqualTo(ActionInstructionType.CANCEL);
  }

  private EventDTO getReceiptEventDTO() {
    EventDTO receiptEventDTO = new EventDTO();
    receiptEventDTO.setDateTime(OffsetDateTime.now());
    receiptEventDTO.setTransactionId(UUID.randomUUID());
    receiptEventDTO.setType(RESPONSE_RECEIVED);
    receiptEventDTO.setChannel(EQ_EVENT_CHANNEL);
    return receiptEventDTO;
  }
}
