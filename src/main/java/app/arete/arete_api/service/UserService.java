package app.arete.arete_api.service;

import app.arete.arete_api.api.model.UpdateUserRequestDto;
import app.arete.arete_api.api.model.UserResponseDto;
import app.arete.arete_api.model.entity.User;

public interface UserService {

    /**
     * Resolves the caller from the security context. For other services only; controllers never call it.
     */
    User getAuthenticatedUser();

    UserResponseDto getMe();

    UserResponseDto updateMe(final UpdateUserRequestDto updateUserRequestDto);
}
