package com.docusphere.backend.authentication.entity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name="users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column (unique=true,nullable=false)
    private String email;

    @Column(nullable = false)
    private String password;

    //Account must Verified before direct to dashbord
    @Column(nullable = false)
    private boolean enabled=false;

    @ManyToOne(fetch=FetchType.EAGER)
    @JoinColumn(name="role_id",nullable=false)
    private Role role;

    private LocalDateTime createdAt=LocalDateTime.now();


    @Column(name = "profile_picture_url", length = 1000)
    private String profilePictureUrl;
}
