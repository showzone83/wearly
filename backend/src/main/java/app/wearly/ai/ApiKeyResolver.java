package app.wearly.ai;

import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * tb_ai_provider.api_key_env가 가리키는 환경변수에서 키를 읽는다. 환경변수와 backend/.env 둘 다 Spring Environment로 읽힌다.
 * 다른 서버 비밀(DB 비밀번호, JWT 키 등)을 공급자에게 보내지 않도록 이름은 *_API_KEY 형식만 허용한다.
 */
@Component
public class ApiKeyResolver {

    public static final Pattern ENV_NAME = Pattern.compile("^[A-Z][A-Z0-9_]{0,91}_API_KEY$");

    private final Environment environment;

    public ApiKeyResolver(Environment environment) {
        this.environment = environment;
    }

    public Optional<String> resolve(String envName) {
        if (envName == null || !ENV_NAME.matcher(envName).matches()) {
            return Optional.empty();
        }
        String value = environment.getProperty(envName);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.strip());
    }

    public boolean configured(String envName) {
        return resolve(envName).isPresent();
    }
}
