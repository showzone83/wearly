package app.wearly.ai.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/** tb_ai_provider_secret JPA 엔티티. 원문 키는 없고 AES-GCM 암호문·nonce·끝 4자만 있다 */
@Entity
@Table(name = "tb_ai_provider_secret")
public class ProviderSecretEntity {

    @Id
    @Column(name = "provider_id", length = 30)
    private String providerId;

    @Column(name = "ciphertext", nullable = false)
    private byte[] ciphertext;

    @Column(name = "nonce", nullable = false)
    private byte[] nonce;

    @Column(name = "key_version", nullable = false)
    private short keyVersion;

    @Column(name = "last4", nullable = false, length = 4)
    private String last4;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProviderSecretEntity() {
    }

    public ProviderSecretEntity(String providerId) {
        this.providerId = providerId;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    void apply(byte[] ciphertext, byte[] nonce, short keyVersion, String last4, Long updatedBy) {
        this.ciphertext = ciphertext;
        this.nonce = nonce;
        this.keyVersion = keyVersion;
        this.last4 = last4;
        this.updatedBy = updatedBy;
    }

    public String getProviderId() {
        return providerId;
    }

    public byte[] getCiphertext() {
        return ciphertext;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public short getKeyVersion() {
        return keyVersion;
    }

    public String getLast4() {
        return last4;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
