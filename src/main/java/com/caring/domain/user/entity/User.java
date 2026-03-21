package com.caring.domain.user.entity;

import com.caring.domain.common.entity.BaseTimeEntity;
import com.caring.domain.fcm.entity.FcmToken;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(of = "id", callSuper = false)
@Table(name = "users")
public class User extends BaseTimeEntity implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(unique = true)
    private String userUuid;

    //TODO auth logic
    @Column(unique = true)
    private String username;

    @Enumerated(EnumType.STRING)
    private Role role;

    private String password;

    private String name;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "province")
    private String province;

    @Column(name = "city_district")
    private String cityDistrict;

    @Column(name = "road_address") // 도로명 주소
    private String roadAddress;

    @Column(name = "detail_address") // 상세 주소
    private String detailAddress;

    @Column(name = "postal_code", length = 10) // 우편번호
    private String postalCode;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private FcmToken fcmToken;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singleton(new SimpleGrantedAuthority(this.role.getKey()));
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    public boolean isSamePassword(String encodedPassword) {
        return Objects.equals(this.password, encodedPassword);
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void changePhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public void changeAddress(String roadAddress, String detailAddress, String postalCode) {
        if (StringUtils.hasText(roadAddress))
            this.roadAddress = roadAddress;
        if (StringUtils.hasText(detailAddress))
            this.detailAddress = detailAddress;
        if (StringUtils.hasText(postalCode))
            this.postalCode = postalCode;
    }
}
