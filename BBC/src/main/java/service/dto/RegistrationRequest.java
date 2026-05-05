package service.dto;

public record RegistrationRequest(
        String fullName,
        String username,
        String password,
        String confirmPassword,
        String roleLabel,
        String email
) {
}
