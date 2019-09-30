package uk.gov.ons.census.casesvc.model.entity;

import java.util.List;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import lombok.Data;

@Data
@Entity
public class UacQidLink {
  @Id private UUID id;

  @Column private String qid;

  @Column private String uac;

  @ManyToOne private Case caze;

  @OneToMany(mappedBy = "uacQidLink")
  private List<Event> events;

  @Column private UUID batchId;

  @Column private boolean active;

  @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
  private boolean isCCS;
}
