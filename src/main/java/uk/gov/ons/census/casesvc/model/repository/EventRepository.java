package uk.gov.ons.census.casesvc.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.ons.census.casesvc.model.entity.Event;

import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {}
