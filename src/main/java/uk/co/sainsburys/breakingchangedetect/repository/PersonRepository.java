package uk.co.sainsburys.breakingchangedetect.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.co.sainsburys.breakingchangedetect.entity.Person;

@Repository
public interface PersonRepository extends JpaRepository<Person, Long> {
}
