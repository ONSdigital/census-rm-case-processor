package uk.gov.ons.census.casesvc.messaging;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.testutil.MessageConstructor.constructMessageWithValidTimeStamp;

import java.time.OffsetDateTime;
import org.junit.Test;
import org.springframework.messaging.Message;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.InvalidAddressService;
import uk.gov.ons.census.casesvc.utility.MsgDateHelper;

public class InvalidAddressReceiverTest {

  @Test
  public void testInvalidAddress() {
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();
    InvalidAddressService invalidAddressService = mock(InvalidAddressService.class);

    Message<ResponseManagementEvent> message = constructMessageWithValidTimeStamp(managementEvent);
    OffsetDateTime expectedDate = MsgDateHelper.getMsgTimeStamp(message);

    InvalidAddressReceiver invalidAddressReceiver =
        new InvalidAddressReceiver(invalidAddressService);
    invalidAddressReceiver.receiveMessage(message);

    verify(invalidAddressService).processMessage(eq(managementEvent), eq(expectedDate));
  }
}
