package uk.gov.ons.census.casesvc.model.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import java.util.List;
import java.util.UUID;

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
}
