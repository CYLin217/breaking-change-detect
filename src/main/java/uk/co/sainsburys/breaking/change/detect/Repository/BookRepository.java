package uk.co.sainsburys.breaking.change.detect.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.sainsburys.breaking.change.detect.Entity.Book;

public interface BookRepository extends JpaRepository<Book, Long> {
}
