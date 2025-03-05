package toyproject.onfilm.director.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import toyproject.onfilm.common.Profile;
import toyproject.onfilm.director.dto.CreateDirectorRequest;
import toyproject.onfilm.director.entity.Director;
import toyproject.onfilm.director.repository.DirectorRepository;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DirectorService {

    private final DirectorRepository directorRepository;

    @Transactional
    public Long createDirector(CreateDirectorRequest request) {
        Profile profile = Profile.builder()
                .name(request.getName())
                .age(request.getAge())
                .sns(request.getSns())
                .build();

        Director director = directorRepository.save(Director.builder()
                .profile(profile)
                .build());

        return director.getId();
    }
}
