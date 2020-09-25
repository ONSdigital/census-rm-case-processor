package uk.gov.ons.census.casesvc.model.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class AddressTypeChangeDetails {
  private UUID id;
  private String ceExpectedCapacity;
  private ModifiedAddress address;
}
