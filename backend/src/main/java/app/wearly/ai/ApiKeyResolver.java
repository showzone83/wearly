package app.wearly.ai;

import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 공급자 행의 api_key_env(환경변수 이름)로 키 값을 찾는다. 환경변수·시스템 속성·backend/.env 순서는 Spring Environment를 따른다.
 * 키 값은 공급자 호출에만 쓰고 응답·로그에는 내보내지 않는다 (관리자 화면에는 keyConfigured만).
 * 이름은 *_API_KEY 모양만 받는다: 관리자가 DB 비밀번호·JWT 비밀 키 같은 다른 비밀을 공급자 주소로 보내지 못하게 한다.
 */
@Component
public class ApiKeyResolver {

    public static final Pattern NAME = Pattern.compile("^[A-Z][A-Z0-9_]{0,91}_API_KEY$");

    private final Environment environment;

    public ApiKeyResolver(Environment environment) {
        this.environment = environment;
    }

    public Optional<String> resolve(String envName) {
        if (envName == null || !NAME.matcher(envName).matches()) {
            return Optional.empty();
        }
        String value = environment.getProperty(envName);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.strip());
    }

    public boolean configured(String envName) {
        return resolve(envName).isPresent();
    }
}
