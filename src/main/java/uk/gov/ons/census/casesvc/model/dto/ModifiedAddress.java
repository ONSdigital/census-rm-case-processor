package uk.gov.ons.census.casesvc.model.dto;

import java.util.Optional;
import lombok.Data;

@Data
public class ModifiedAddress {
  private Optional<String> addressLine1;
  private Optional<String> addressLine2;
  private Optional<String> addressLine3;
  private Optional<String> townName;
  private Optional<String> estabType;
  private Optional<String> organisationName;
}
