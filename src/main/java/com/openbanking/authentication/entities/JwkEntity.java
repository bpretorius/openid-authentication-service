package com.openbanking.authentication.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@Entity
@Table(name = "jwk_keys")
public class JwkEntity implements Serializable {

    @Id
    @Column(name = "key_id")
    private String keyId;

    @Column(name = "jwk_json", length = 8192, nullable = false)
    private String jwkJson;

    // Getters and setters
}

