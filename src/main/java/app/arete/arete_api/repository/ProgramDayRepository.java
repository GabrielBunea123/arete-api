package app.arete.arete_api.repository;

import app.arete.arete_api.model.entity.ProgramDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProgramDayRepository extends JpaRepository<ProgramDay, Long>, JpaSpecificationExecutor<ProgramDay> {

    List<ProgramDay> findAllByProgramIdAndDayDateBetween(
            final Long programId,
            final LocalDate from,
            final LocalDate to);

    Optional<ProgramDay> findByProgramIdAndDayDate(final Long programId, final LocalDate dayDate);
}
