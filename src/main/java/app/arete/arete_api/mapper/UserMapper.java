package app.arete.arete_api.mapper;

import app.arete.arete_api.api.model.AuthProviderDto;
import app.arete.arete_api.api.model.RegisterUserRequestDto;
import app.arete.arete_api.api.model.UpdateUserRequestDto;
import app.arete.arete_api.api.model.UserResponseDto;
import app.arete.arete_api.model.dto.auth.GoogleIdentity;
import app.arete.arete_api.model.entity.User;
import app.arete.arete_api.model.entity.UserIdentity;
import app.arete.arete_api.util.enums.AuthProvider;
import app.arete.arete_api.util.enums.RoleEnum;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.util.List;
import java.util.Set;

@Mapper(config = CentralMapperConfig.class)
public interface UserMapper {

    @Mapping(target = "passwordSet", expression = "java(user.getPasswordHash() != null)")
    @Mapping(target = "providers", source = "identities", qualifiedByName = "toProviders")
    UserResponseDto toUserResponseDto(final User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "email", source = "normalizedEmail")
    @Mapping(target = "fullName", source = "registerUserRequestDto.fullName")
    @Mapping(target = "passwordHash", source = "passwordHash")
    @Mapping(target = "emailVerified", constant = "false")
    @Mapping(target = "roles", source = "roles")
    @Mapping(target = "identities", ignore = true)
    User toUser(
            final RegisterUserRequestDto registerUserRequestDto,
            final String normalizedEmail,
            final String passwordHash,
            final Set<RoleEnum> roles);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "email", source = "normalizedEmail")
    @Mapping(target = "fullName", source = "googleIdentity.fullName")
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "emailVerified", constant = "true")
    @Mapping(target = "roles", source = "roles")
    @Mapping(target = "identities", ignore = true)
    User toUser(final GoogleIdentity googleIdentity, final String normalizedEmail, final Set<RoleEnum> roles);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "provider", source = "provider")
    @Mapping(target = "providerSubject", source = "googleIdentity.subject")
    @Mapping(target = "email", source = "googleIdentity.email")
    UserIdentity toUserIdentity(final GoogleIdentity googleIdentity, final AuthProvider provider);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "emailVerified", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "identities", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateUserFromDto(final UpdateUserRequestDto updateUserRequestDto, @MappingTarget final User user);

    AuthProviderDto toAuthProviderDto(final AuthProvider authProvider);

    @Named("toProviders")
    default List<AuthProviderDto> toProviders(final Set<UserIdentity> identities) {
        if (identities == null) {
            return List.of();
        }
        return identities.stream()
                .map(UserIdentity::getProvider)
                .distinct()
                .map(this::toAuthProviderDto)
                .toList();
    }
}
