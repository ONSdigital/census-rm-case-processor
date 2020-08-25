package uk.gov.ons.census.casesvc.model.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class AddressTypeChangedDetails {
  private UUID id;
  private String ceExpectedCapacity;
  private Address address;
}
