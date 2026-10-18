package app.arete.arete_api.mapper;

import org.mapstruct.Mapper;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Entity timestamps are stored as UTC LocalDateTimes (see JpaConfig); API models use OffsetDateTime.
 */
@Mapper(config = CentralMapperConfig.class)
public interface DateTimeMapper {

    default OffsetDateTime toOffsetDateTime(final LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.atOffset(ZoneOffset.UTC);
    }
}
