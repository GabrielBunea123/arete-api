package app.arete.arete_api.service;

import app.arete.arete_api.api.model.UpdateUserRequestDto;
import app.arete.arete_api.api.model.UserResponseDto;
import app.arete.arete_api.exception.UnauthorizedException;
import app.arete.arete_api.model.entity.User;

public interface UserService {

    /**
     * Resolves the caller from the security context. For other services only; controllers never call it.
     *
     * @return the signed-in user's entity, to be used as an owner or for owner-scoped queries
     * @throws UnauthorizedException if there is no authenticated caller or their account no longer exists
     */
    User getAuthenticatedUser();

    /**
     * Returns the signed-in user's own account.
     *
     * @return the caller's account, including which sign-in methods it has
     */
    UserResponseDto getMe();

    /**
     * Updates the signed-in user's editable profile fields. Email and roles can't be changed here.
     *
     * @param updateUserRequestDto the new profile values
     * @return the updated account
     */
    UserResponseDto updateMe(final UpdateUserRequestDto updateUserRequestDto);
}
