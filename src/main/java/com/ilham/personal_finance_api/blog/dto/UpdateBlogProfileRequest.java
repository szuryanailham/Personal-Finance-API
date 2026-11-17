package com.ilham.personal_finance_api.blog.dto;

import jakarta.validation.constraints.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Omitted fields are preserved; nullable profile fields can be cleared. */
public class UpdateBlogProfileRequest {
    private final Set<String> supplied = new HashSet<>();
    @Size(max = 150) @Pattern(regexp = "(?s).*\\S.*")
    private String displayName;
    @Size(max = 2048) @org.hibernate.validator.constraints.URL(protocol = "https")
    private String photoProfile;
    @Size(max = 5000)
    private String description;
    @Size(max = 20)
    private Map<@NotBlank @Size(max = 50) @Pattern(regexp = "[a-z0-9_-]+") String,
            @NotBlank @Size(max = 2048) @org.hibernate.validator.constraints.URL(protocol = "https") String> socialMedia;

    public boolean has(String field) { return supplied.contains(field); }
    public boolean empty() { return supplied.isEmpty(); }
    public String displayName() { return displayName; }
    public String photoProfile() { return photoProfile; }
    public String description() { return description; }
    public Map<String, String> socialMedia() { return socialMedia; }
    public void setDisplayName(String value) {
        if (value == null) throw new IllegalArgumentException("displayName must not be null");
        supplied.add("displayName"); displayName = value.strip();
    }
    public void setPhotoProfile(String value) { supplied.add("photoProfile"); photoProfile = value; }
    public void setDescription(String value) { supplied.add("description"); description = value; }
    public void setSocialMedia(Map<String, String> value) {
        if (value == null) throw new IllegalArgumentException("socialMedia must not be null");
        supplied.add("socialMedia"); socialMedia = value;
    }
}
