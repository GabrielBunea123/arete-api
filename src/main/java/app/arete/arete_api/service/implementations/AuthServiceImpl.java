package app.arete.arete_api.service.implementations;

import app.arete.arete_api.api.model.GoogleLoginRequestDto;
import app.arete.arete_api.api.model.RegisterUserRequestDto;
import app.arete.arete_api.api.model.UserLoginRequestDto;
import app.arete.arete_api.exception.BadRequestException;
import app.arete.arete_api.exception.ConflictException;
import app.arete.arete_api.exception.UnauthorizedException;
import app.arete.arete_api.mapper.UserMapper;
import app.arete.arete_api.model.dto.auth.AuthTokens;
import app.arete.arete_api.model.dto.auth.GoogleIdentity;
import app.arete.arete_api.model.dto.auth.RegistrationResult;
import app.arete.arete_api.model.entity.User;
import app.arete.arete_api.model.entity.UserIdentity;
import app.arete.arete_api.repository.UserIdentityRepository;
import app.arete.arete_api.repository.UserRepository;
import app.arete.arete_api.security.GoogleIdTokenVerifier;
import app.arete.arete_api.security.JwtService;
import app.arete.arete_api.security.UserPrincipal;
import app.arete.arete_api.service.AuthService;
import app.arete.arete_api.service.RefreshTokenService;
import app.arete.arete_api.util.Constants;
import app.arete.arete_api.util.enums.AuthProvider;
import app.arete.arete_api.util.enums.RoleEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;

    @Override
    @Transactional
    public RegistrationResult register(final RegisterUserRequestDto registerUserRequestDto) {
        if (!registerUserRequestDto.getPassword().equals(registerUserRequestDto.getConfirmPassword())) {
            throw new BadRequestException("Passwords do not match");
        }
        final String email = normalizeEmail(registerUserRequestDto.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already in use");
        }

        final User user = userRepository.save(userMapper.toUser(
                registerUserRequestDto,
                email,
                passwordEncoder.encode(registerUserRequestDto.getPassword()),
                EnumSet.of(RoleEnum.USER)));
        return new RegistrationResult(userMapper.toUserResponseDto(user), issueTokens(user));
    }

    @Override
    @Transactional
    public AuthTokens login(final UserLoginRequestDto userLoginRequestDto) {
        final Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                    normalizeEmail(userLoginRequestDto.getEmail()),
                    userLoginRequestDto.getPassword()));
        } catch (final AuthenticationException exception) {
            throw new UnauthorizedException(Constants.INVALID_CREDENTIALS_DETAIL);
        }
        if (!(authentication.getPrincipal() instanceof UserPrincipal(User user))) {
            throw new IllegalStateException("Password login must authenticate a UserPrincipal");
        }
        return issueTokens(user);
    }

    @Override
    @Transactional
    public AuthTokens loginWithGoogle(final GoogleLoginRequestDto googleLoginRequestDto) {
        final GoogleIdentity googleIdentity = googleIdTokenVerifier.verify(googleLoginRequestDto.getIdToken());
        final User user = userIdentityRepository
                .findByProviderAndProviderSubjectWithUser(AuthProvider.GOOGLE, googleIdentity.subject())
                .map(UserIdentity::getUser)
                .orElseGet(() -> linkOrCreateGoogleUser(googleIdentity));
        return issueTokens(user);
    }

    @Override
    public AuthTokens refresh(final String refreshToken) {
        return refreshTokenService.rotateRefreshToken(refreshToken);
    }

    @Override
    public void logout(final String refreshToken) {
        refreshTokenService.revokeRefreshTokenFamily(refreshToken);
    }

    private User linkOrCreateGoogleUser(final GoogleIdentity googleIdentity) {
        if (!googleIdentity.emailVerified()) {
            throw new UnauthorizedException("Google account email is not verified");
        }
        final String email = normalizeEmail(googleIdentity.email());
        final User user = userRepository.findByEmail(email)
                .map(this::prepareExistingUserForGoogleLink)
                .orElseGet(() -> userMapper.toUser(googleIdentity, email, EnumSet.of(RoleEnum.USER)));
        user.addIdentity(userMapper.toUserIdentity(googleIdentity, AuthProvider.GOOGLE));
        return userRepository.save(user);
    }

    private User prepareExistingUserForGoogleLink(final User existingUser) {
        final boolean hasGoogleIdentity = existingUser.getIdentities().stream()
                .anyMatch(identity -> identity.getProvider() == AuthProvider.GOOGLE);
        if (hasGoogleIdentity) {
            throw new ConflictException("This account is already linked to a different Google account");
        }
        if (!existingUser.isEmailVerified()) {
            existingUser.setPasswordHash(null);
            existingUser.setEmailVerified(true);
            refreshTokenService.revokeAllUserRefreshTokens(existingUser);
        }
        return existingUser;
    }

    private AuthTokens issueTokens(final User user) {
        return new AuthTokens(jwtService.generateAccessToken(user), refreshTokenService.issueRefreshToken(user));
    }

    private static String normalizeEmail(final String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
