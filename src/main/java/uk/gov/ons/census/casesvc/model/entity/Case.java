package uk.gov.ons.census.casesvc.model.entity;

import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "cases")
public class Case {
  @Id private UUID id;

  @Column private String stuff;

  @Column @Enumerated private CaseStatus status;
}
