package app.wearly.user;

import app.wearly.code.CodeCatalog;
import app.wearly.code.CodeGroups;
import app.wearly.code.CodeSnapshot;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import app.wearly.common.error.ErrorResponse.FieldError;
import app.wearly.domain.DressCode;
import app.wearly.domain.EnumCodes;
import app.wearly.domain.WorkType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final CodeCatalog codes;

    public ProfileService(UserRepository users, UserProfileRepository profiles, CodeCatalog codes) {
        this.users = users;
        this.profiles = profiles;
        this.codes = codes;
    }

    @Transactional(readOnly = true)
    public ProfileDto get(long userId) {
        User user = users.findById(userId).orElseThrow(ApiException::notFound);
        return profiles.findByUserId(userId)
                .map(p -> toDto(user, p))
                .orElseGet(() -> ProfileDto.defaults(user.getName()));
    }

    /** PUT: 프로필 전체를 바꾸고 tb_user.name도 함께 바꾼다. */
    @Transactional
    public ProfileDto put(long userId, ProfileDto dto) {
        User user = users.findById(userId).orElseThrow(ApiException::notFound);
        UserProfile profile = profiles.findByUserId(userId).orElseGet(() -> new UserProfile(userId));
        validateCodes(dto, profile);
        user.rename(dto.getName().strip());
        Map<String, Boolean> notify = new LinkedHashMap<>();
        notify.put("morning", dto.getNotify().morning());
        notify.put("evening", dto.getNotify().evening());
        notify.put("dMinus1", dto.getNotify().dMinus1());
        profile.update(
                EnumCodes.names(dto.getDressCodes()),
                dto.getMoods().stream().distinct().toList(),
                dto.getColors().stream().distinct().toList(),
                dto.getWorkType().name(),
                dto.getWorkDays().stream().distinct().sorted().map(Integer::shortValue).toList(),
                dto.getWorkRule(),
                dto.getOffRule(),
                notify,
                dto.getOnboarded() == null || dto.getOnboarded());
        profiles.saveAndFlush(profile);
        return toDto(user, profile);
    }

    private static ProfileDto toDto(User user, UserProfile p) {
        Map<String, Boolean> n = p.getNotify();
        return new ProfileDto(
                user.getName(),
                EnumCodes.parse(p.getDressCodes(), DressCode::valueOf),
                List.copyOf(p.getMoods()),
                List.copyOf(p.getColors()),
                WorkType.valueOf(p.getWorkType()),
                p.getWorkDays().stream().map(Short::intValue).toList(),
                p.isWorkRule(),
                p.isOffRule(),
                new ProfileDto.Notify(flag(n, "morning"), flag(n, "evening"), flag(n, "dMinus1")),
                p.isOnboarded());
    }

    /**
     * moods는 STYLE, colors는 COLOR 코드여야 한다. 이미 저장돼 있던 값은 비활성이어도 그대로 두고,
     * 새로 고른 값은 활성 코드만 받는다 (doc/06_코드_관리.md 4장).
     */
    private void validateCodes(ProfileDto dto, UserProfile stored) {
        CodeSnapshot snapshot = codes.snapshot();
        List<FieldError> errors = new ArrayList<>();
        check(snapshot, CodeGroups.STYLE, dto.getMoods(), stored.getMoods(), "moods", errors);
        check(snapshot, CodeGroups.COLOR, dto.getColors(), stored.getColors(), "colors", errors);
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), errors);
        }
    }

    private static void check(CodeSnapshot snapshot, String group, List<String> values, List<String> stored, String field,
            List<FieldError> errors) {
        for (int i = 0; i < values.size(); i++) {
            String v = values.get(i);
            if (!snapshot.exists(group, v)) {
                errors.add(new FieldError(field + "[" + i + "]", "허용되지 않는 값이에요."));
            } else if ((stored == null || !stored.contains(v)) && !snapshot.selectable(group, v)) {
                errors.add(new FieldError(field + "[" + i + "]", "더 이상 고를 수 없는 값이에요."));
            }
        }
    }

    private static boolean flag(Map<String, Boolean> notify, String key) {
        return notify != null && Boolean.TRUE.equals(notify.get(key));
    }

}
