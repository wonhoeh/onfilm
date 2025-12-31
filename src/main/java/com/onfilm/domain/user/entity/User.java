package com.onfilm.domain.user.entity;

import com.onfilm.domain.movie.entity.Person;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_users_username", columnNames = "username")
        })
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name ="user_id")
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 30)
    private String username;

    private String avatarUrl;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "person_id", unique = true)
    private Person person;

    public static User create(String email, String password, String username) {
        User user = new User();
        user.email = email;
        user.password = password;
        user.username = username;
        return user;
    }

    public void attachPerson(Person person) {
        this.person = person;
        if (person != null && person.getUser() != this) {
            person.attachUser(this);
        }
    }
    public void detachPerson() {
        if (this.person == null) return;
        Person old = this.person;
        this.person = null;
        if (old.getUser() == this) old.detachUser();
    }
}
