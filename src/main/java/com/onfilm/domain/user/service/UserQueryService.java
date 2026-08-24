package com.onfilm.domain.user.service;

import com.onfilm.domain.auth.dto.MeResponse;
import com.onfilm.domain.common.error.exception.UserNotFoundException;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.entity.UserEmail;
import com.onfilm.domain.user.entity.Username;
import com.onfilm.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {

    private final UserRepository userRepository;
    private final StorageService storageService;

    public MeResponse findMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        String avatarKey = user.getAvatarImageKey();
        String avatarUrl = avatarKey == null || avatarKey.isBlank()
                ? null
                : storageService.toPublicUrl(avatarKey);
        return new MeResponse(user.getId(), user.getEmail(), user.getUsername(), avatarUrl);
    }

    public boolean isUsernameAvailable(String rawUsername) {
        try {
            Username username = Username.from(rawUsername);
            return !userRepository.existsByUsernameNormalized(username.normalized());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public boolean isEmailAvailable(String rawEmail) {
        try {
            UserEmail email = UserEmail.from(rawEmail);
            return !userRepository.existsByEmail(email.value());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
