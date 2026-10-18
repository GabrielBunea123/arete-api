package app.arete.arete_api.mapper;

import app.arete.arete_api.api.model.SortDirectionDto;
import org.mapstruct.Mapper;
import org.springframework.data.domain.Sort;

@Mapper(config = CentralMapperConfig.class)
public interface PaginationMapper {

    Sort.Direction toSortDirection(final SortDirectionDto sortDirectionDto);
}
