package uk.gov.ons.census.casesvc.service;

import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.service.ReceiptService.QID_RECEIPTED;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.*;

import java.time.OffsetDateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ReceiptDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@RunWith(MockitoJUnitRunner.class)
public class ReceiptServiceTest {

  @Mock private CaseService caseService;

  @Mock private UacService uacService;

  @Mock private EventLogger eventLogger;

  @InjectMocks ReceiptService underTest;

  @Test
  public void testGoodReceipt() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    ReceiptDTO expectedReceipt = managementEvent.getPayload().getReceipt();

    // Given
    Case expectedCase = getRandomCase();
    UacQidLink expectedUacQidLink = generateRandomUacQidLink(expectedCase);

    managementEvent.getPayload().getReceipt().setResponseDateTime(OffsetDateTime.now());

    when(uacService.findByQid(expectedReceipt.getQuestionnaireId())).thenReturn(expectedUacQidLink);

    // when
    underTest.processReceipt(managementEvent);

    // then
    verify(uacService).saveAndEmitUacUpdatedEvent(expectedUacQidLink);
    verify(caseService).saveAndEmitCaseUpdatedEvent(expectedCase);
    verify(eventLogger)
        .logUacQidEvent(
            eq(expectedUacQidLink),
            any(OffsetDateTime.class),
            eq(QID_RECEIPTED),
            eq(EventType.RESPONSE_RECEIVED),
            eq(managementEvent.getEvent()),
            anyString());
  }
}
