package app.wearly.auth.application.port.in;

public record SignupCommand(String email, String password, String name) {}
