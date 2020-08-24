package uk.gov.ons.census.casesvc.model.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class AddressTypeChanged {
  private UUID newCaseId;
  private AddressTypeChangedDetails collectionCase;
}
