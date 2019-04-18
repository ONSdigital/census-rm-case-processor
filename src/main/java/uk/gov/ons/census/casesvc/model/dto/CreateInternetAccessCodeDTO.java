package uk.gov.ons.census.casesvc.model.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class CreateInternetAccessCodeDTO {

  private Integer count;

  private String createdBy;
}
