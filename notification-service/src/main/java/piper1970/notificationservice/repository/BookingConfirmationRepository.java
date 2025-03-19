package piper1970.notificationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import piper1970.notificationservice.domain.BookingConfirmation;

@Repository
public interface BookingConfirmationRepository extends JpaRepository<BookingConfirmation, Integer> {
}
