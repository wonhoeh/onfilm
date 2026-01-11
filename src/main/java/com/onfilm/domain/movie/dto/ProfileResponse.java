package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.Person;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileResponse {
    private String publicId;
    private String name;
    private LocalDate birthDate;
    private String birthPlace;
    private String oneLineIntro;
    private String profileImageKey;
    private String profileImageUrl;
    private boolean filmographyPrivate;
    private boolean galleryPrivate;
    private List<PersonSnsResponse> snsList;
    private List<ProfileTagResponse> rawTags;

    public static ProfileResponse from(Person person, String publicUrl) {
        return ProfileResponse.builder()
                .publicId(person.getPublicId())
                .name(person.getName())
                .birthDate(person.getBirthDate())
                .birthPlace(person.getBirthPlace())
                .oneLineIntro(person.getOneLineIntro())
                .profileImageKey(person.getProfileImageUrl())
                .profileImageUrl(publicUrl)
                .filmographyPrivate(person.isFilmographyPrivate())
                .galleryPrivate(person.isGalleryPrivate())
                .snsList(person.getSnsList().stream().map(PersonSnsResponse::from).toList())
                .rawTags(person.getProfileTags().stream().map(ProfileTagResponse::from).toList())
                .build();
    }
}
