package dev.wallpaperhub.account.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code PATCH /users/me} (UpdateProfileRequest of account.yaml): only the given fields change, and {@code bio}
 * may be set to {@code null} to clear it. A record cannot tell "bio is null" from "bio is absent", so this is a class whose
 * {@code bio} setter remembers that it was called (Jackson calls a setter for every property that is in the JSON, also for null).
 */
public class UpdateProfileRequest {

    @Size(min = 1, max = 60)
    @Pattern(regexp = ".*\\S.*")
    private String displayName;

    @Size(max = 500)
    private String bio;

    @Pattern(regexp = "en|de|es")
    private String locale;

    private boolean bioGiven;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
        this.bioGiven = true;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    /** Whether the JSON had a {@code bio} property, with a text or with null. (Not named like a getter: it is not a JSON property.) */
    boolean bioGiven() {
        return bioGiven;
    }

    /** The contract wants at least one field ({@code minProperties: 1}). */
    boolean isEmptyUpdate() {
        return displayName == null && !bioGiven && locale == null;
    }
}
