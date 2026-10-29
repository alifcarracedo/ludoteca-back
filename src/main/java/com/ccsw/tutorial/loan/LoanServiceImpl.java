package com.ccsw.tutorial.loan;

import com.ccsw.tutorial.client.ClientService;
import com.ccsw.tutorial.common.criteria.SearchCriteria;
import com.ccsw.tutorial.game.GameService;
import com.ccsw.tutorial.loan.model.Loan;
import com.ccsw.tutorial.loan.model.LoanDto;
import com.ccsw.tutorial.loan.model.LoanSearchDto;
import jakarta.transaction.Transactional;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * @author ccsw
 *
 */
@Service
@Transactional
public class LoanServiceImpl implements LoanService {

    @Autowired
    LoanRepository loanRepository;

    @Autowired
    GameService gameService;

    @Autowired
    ClientService clientService;

    @Override
    public Loan find(Long id) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Loan get(Long id) {

        return this.loanRepository.findById(id).orElse(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Page<Loan> findPage(LoanSearchDto dto) {

        LoanSpecification game = new LoanSpecification(new SearchCriteria("game.id", ":", dto.getIdGame()));
        LoanSpecification client = new LoanSpecification(new SearchCriteria("client.id", ":", dto.getIdClient()));
        LoanSpecification start = new LoanSpecification(new SearchCriteria("startDate", "<=", dto.getDate()));
        LoanSpecification end = new LoanSpecification(new SearchCriteria("endDate", ">=", dto.getDate()));

        Specification<Loan> spec = Specification.where(game).and(client).and(start).and(end);

        return this.loanRepository.findAll(spec, dto.getPageable().getPageable());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void save(Long id, LoanDto dto) {

        validateDates(dto);
        validateGameAvailable(id, dto);
        validateClientLimit(id, dto);

        Loan loan;

        if (id == null) {
            loan = new Loan();
        } else {
            loan = this.loanRepository.findById(id).orElse(null);
        }

        BeanUtils.copyProperties(dto, loan, "id", "game", "client");

        loan.setGame(gameService.get(dto.getGame().getId()));
        loan.setClient(clientService.get(dto.getClient().getId()));

        this.loanRepository.save(loan);

    }

    public void validateDates(LoanDto dto) {
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fecha de devolución anterior a fecha de préstamo");
        }

        long days = ChronoUnit.DAYS.between(dto.getStartDate(), dto.getEndDate());
        if (days > 14) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los préstamos no pueden exceder de 14 días");
        }
    }

    private void validateGameAvailable(Long id, LoanDto dto) {
        LoanSpecification game = new LoanSpecification(new SearchCriteria("game.id", ":", dto.getGame().getId()));
        LoanSpecification start = new LoanSpecification(new SearchCriteria("startDate", "<=", dto.getEndDate()));
        LoanSpecification end = new LoanSpecification(new SearchCriteria("endDate", ">=", dto.getStartDate()));

        Specification<Loan> specification = Specification.where(game).and(start).and(end);

        List<Loan> overlaps = this.loanRepository.findAll(specification);

        boolean busy = overlaps.stream().anyMatch(loan -> !loan.getId().equals(id));

        if (busy) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Juego no disponible");
        }
    }

    private void validateClientLimit(Long id, LoanDto dto) {
        LoanSpecification client = new LoanSpecification(new SearchCriteria("client.id", ":", dto.getGame().getId()));
        LoanSpecification start = new LoanSpecification(new SearchCriteria("startDate", "<=", dto.getEndDate()));
        LoanSpecification end = new LoanSpecification(new SearchCriteria("endDate", ">=", dto.getStartDate()));

        Specification<Loan> specification = Specification.where(client).and(start).and(end);

        List<Loan> overlaps = this.loanRepository.findAll(specification).stream().filter(loan -> !loan.getId().equals(id)).toList();

        for (LocalDate day = dto.getStartDate(); !day.isAfter(dto.getEndDate()); day = day.plusDays(1)) {
            final LocalDate current = day;

            long count = overlaps.stream().filter(loan -> !loan.getStartDate().isAfter(current) && !loan.getEndDate().isBefore(current)).count();

            if (count >= 2) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cliente ya tiene 2 préstamos");
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) throws Exception {

        if (this.get(id) == null) {
            throw new Exception("Not exists");
        }

        this.loanRepository.deleteById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Loan> findAll() {

        return (List<Loan>) this.loanRepository.findAll();
    }
}
