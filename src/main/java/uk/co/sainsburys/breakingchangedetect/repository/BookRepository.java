package uk.co.sainsburys.breakingchangedetect.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.co.sainsburys.breakingchangedetect.entity.Book;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {
}
