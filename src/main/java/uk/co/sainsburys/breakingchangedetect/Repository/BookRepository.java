package uk.co.sainsburys.breakingchangedetect.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.co.sainsburys.breakingchangedetect.Entity.Book;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {
}
