package uk.gov.ons.census.casesvc.messaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.Test;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.InvalidAddressService;

public class InvalidAddressReceiverTest {

  @Test
  public void testInvalidAddress() {
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();
    InvalidAddressService invalidAddressService = mock(InvalidAddressService.class);

    InvalidAddressReceiver invalidAddressReceiver =
        new InvalidAddressReceiver(invalidAddressService);
    invalidAddressReceiver.receiveMessage(managementEvent);

    verify(invalidAddressService).processMessage(managementEvent);
  }
}
