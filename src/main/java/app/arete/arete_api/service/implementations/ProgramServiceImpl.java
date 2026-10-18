package app.arete.arete_api.service.implementations;

import app.arete.arete_api.api.model.ProgramPageResponseDto;
import app.arete.arete_api.api.model.ProgramRequestDto;
import app.arete.arete_api.api.model.ProgramResponseDto;
import app.arete.arete_api.api.model.ProgramSortFieldDto;
import app.arete.arete_api.api.model.SortDirectionDto;
import app.arete.arete_api.exception.BadRequestException;
import app.arete.arete_api.exception.NotFoundException;
import app.arete.arete_api.mapper.PaginationMapper;
import app.arete.arete_api.mapper.ProgramMapper;
import app.arete.arete_api.model.dto.program.ProgramFilterDto;
import app.arete.arete_api.model.entity.Program;
import app.arete.arete_api.model.entity.User;
import app.arete.arete_api.repository.ProgramRepository;
import app.arete.arete_api.service.ProgramService;
import app.arete.arete_api.service.UserService;
import app.arete.arete_api.specification.ProgramSpecifications;
import app.arete.arete_api.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProgramServiceImpl implements ProgramService {

    private static final String PROGRAM_NOT_FOUND_DETAIL = "Program not found";

    private final ProgramRepository programRepository;
    private final ProgramSpecifications programSpecifications;
    private final ProgramMapper programMapper;
    private final PaginationMapper paginationMapper;
    private final UserService userService;

    @Override
    public Program getUserProgramEntity(final Long programId) {
        final User user = userService.getAuthenticatedUser();
        return programRepository.findByIdAndOwnerId(programId, user.getId())
                .orElseThrow(() -> new NotFoundException(PROGRAM_NOT_FOUND_DETAIL));
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramPageResponseDto getUserPrograms(
            final int page,
            final int size,
            final ProgramSortFieldDto sort,
            final SortDirectionDto direction,
            final ProgramFilterDto programFilterDto) {
        validateCreatedRange(programFilterDto);
        final User user = userService.getAuthenticatedUser();
        final Pageable pageable = PaginationUtils.toPageRequest(
                page,
                size,
                programMapper.toProgramSortField(sort).getValue(),
                paginationMapper.toSortDirection(direction));
        final Specification<Program> specification = programSpecifications.buildFilters(user.getId(), programFilterDto);
        return programMapper.toProgramPageResponseDto(programRepository.findAll(specification, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramResponseDto getUserProgramById(final Long programId) {
        return programMapper.toProgramResponseDto(getUserProgramEntity(programId));
    }

    @Override
    @Transactional
    public ProgramResponseDto createUserProgram(final ProgramRequestDto programRequestDto) {
        final User user = userService.getAuthenticatedUser();
        final Program program = programMapper.toProgram(programRequestDto, user);
        return programMapper.toProgramResponseDto(programRepository.save(program));
    }

    @Override
    @Transactional
    public ProgramResponseDto updateUserProgram(final Long programId, final ProgramRequestDto programRequestDto) {
        final Program program = getUserProgramEntity(programId);
        programMapper.updateProgramFromDto(programRequestDto, program);
        return programMapper.toProgramResponseDto(programRepository.save(program));
    }

    @Override
    @Transactional
    public void deleteUserProgram(final Long programId) {
        programRepository.delete(getUserProgramEntity(programId));
    }

    private static void validateCreatedRange(final ProgramFilterDto programFilterDto) {
        if (programFilterDto.createdFrom() != null
                && programFilterDto.createdTo() != null
                && programFilterDto.createdTo().isBefore(programFilterDto.createdFrom())) {
            throw new BadRequestException("'createdTo' must not be before 'createdFrom'");
        }
    }
}
