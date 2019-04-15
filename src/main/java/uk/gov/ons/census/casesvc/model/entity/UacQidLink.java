package uk.gov.ons.census.casesvc.model.entity;

import java.util.List;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import lombok.Data;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;

@Data
@Entity
public class UacQidLink {
  @Id private UUID id;

  @Column(columnDefinition = "serial")
  @Generated(GenerationTime.INSERT)
  private Long uniqueNumber;

  @Column private String qid;

  @Column private String uac;

  @ManyToOne private Case caze;

  @OneToMany(mappedBy = "uacQidLink")
  private List<Event> events;
}
