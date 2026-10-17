package app.arete.arete_api.controller;

import app.arete.arete_api.api.UserApi;
import app.arete.arete_api.api.model.UpdateUserRequestDto;
import app.arete.arete_api.api.model.UserResponseDto;
import app.arete.arete_api.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController implements UserApi {

    private final UserService userService;

    @Override
    public ResponseEntity<UserResponseDto> getMe() {
        return ResponseEntity.ok(userService.getMe());
    }

    @Override
    public ResponseEntity<UserResponseDto> updateMe(final UpdateUserRequestDto updateUserRequestDto) {
        return ResponseEntity.ok(userService.updateMe(updateUserRequestDto));
    }
}
