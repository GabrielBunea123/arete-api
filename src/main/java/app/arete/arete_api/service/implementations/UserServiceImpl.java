package app.arete.arete_api.service.implementations;

import app.arete.arete_api.api.model.RegisterUserRequestDto;
import app.arete.arete_api.api.model.UserResponseDto;
import app.arete.arete_api.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {
    @Override
    public UserResponseDto register(final RegisterUserRequestDto registerUserRequestDto) {
        return null;
    }
}
