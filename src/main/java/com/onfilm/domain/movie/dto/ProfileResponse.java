package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.Person;

import java.time.LocalDate;
import java.util.List;

public record ProfileResponse(
        String publicId,
        String name,
        LocalDate birthDate,
        String birthPlace,
        String oneLineIntro,
        String profileImageKey,
        String profileImageUrl,
        boolean filmographyPrivate,
        boolean galleryPrivate,
        List<PersonSnsResponse> snsList,
        List<ProfileTagResponse> rawTags
) {
    public ProfileResponse {
        snsList = List.copyOf(snsList);
        rawTags = List.copyOf(rawTags);
    }

    public static ProfileResponse from(Person person, String publicUrl) {
        return new ProfileResponse(
                person.getPublicId(),
                person.getName(),
                person.getBirthDate(),
                person.getBirthPlace(),
                person.getOneLineIntro(),
                person.getProfileImageKey(),
                publicUrl,
                person.isFilmographyPrivate(),
                person.isGalleryPrivate(),
                person.getSnsList().stream().map(PersonSnsResponse::from).toList(),
                person.getProfileTags().stream().map(ProfileTagResponse::from).toList()
        );
    }
}
