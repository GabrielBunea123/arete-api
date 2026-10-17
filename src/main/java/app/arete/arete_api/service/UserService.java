package app.arete.arete_api.service;

import app.arete.arete_api.api.model.RegisterUserRequestDto;
import app.arete.arete_api.api.model.UserResponseDto;

public interface UserService {
    UserResponseDto register(final RegisterUserRequestDto registerUserRequestDto);
}
