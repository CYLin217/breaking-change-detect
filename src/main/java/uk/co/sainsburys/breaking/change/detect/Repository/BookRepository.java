package uk.co.sainsburys.breaking.change.detect.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.co.sainsburys.breaking.change.detect.Entity.Book;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {
}
