package com.docusphere.backend.document.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true)
	private String fileId;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String type;

	@Column(nullable = false)
	private Long sizeBytes;

	@Column(nullable = false)
	private Long ownerId;

	private UUID teamId;

	@Column(nullable = false)
	private String storageKey;

	@Column(nullable = false)
	private String fileUrl;

	@Column(nullable = false)
	private boolean deleted = false;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UploadStatus status = UploadStatus.UPLOADING;

	@Column(nullable = false)
	private boolean secured = false;

	@Column(name = "password_hash")
	private String passwordHash;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	/** {@code secured} column — document requires a password to open/download. */
	public boolean isPasswordProtected() {
		return secured;
	}

	public void setPasswordProtected(boolean passwordProtected) {
		this.secured = passwordProtected;
	}

	@PrePersist
	public void onCreate() {
		createdAt = LocalDateTime.now();
		updatedAt = LocalDateTime.now();
	}

	@PreUpdate
	public void onUpdate() {
		updatedAt = LocalDateTime.now();
	}

	public enum UploadStatus {
		UPLOADING,
		COMPLETED,
		FAILED
	}
}