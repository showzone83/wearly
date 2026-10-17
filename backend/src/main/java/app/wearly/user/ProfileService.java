package app.wearly.user;

import app.wearly.code.CodeCatalog;
import app.wearly.code.CodeChecks;
import app.wearly.code.CodeGroups;
import app.wearly.code.CodeSnapshot;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import app.wearly.common.error.ErrorResponse;
import app.wearly.domain.DressCode;
import app.wearly.domain.EnumCodes;
import app.wearly.domain.WorkType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final CodeCatalog catalog;

    public ProfileService(UserRepository users, UserProfileRepository profiles, CodeCatalog catalog) {
        this.users = users;
        this.profiles = profiles;
        this.catalog = catalog;
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
        Optional<UserProfile> saved = profiles.findByUserId(userId);
        // 새로 고르는 색·취향은 활성 코드만. 이미 저장돼 있던 값은 비활성이어도 둔다
        CodeSnapshot codes = catalog.current();
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        CodeChecks.many(codes, CodeGroups.STYLE, "moods", dto.getMoods(), saved.map(UserProfile::getMoods).orElse(null), errors);
        CodeChecks.many(codes, CodeGroups.COLOR, "colors", dto.getColors(), saved.map(UserProfile::getColors).orElse(null),
                errors);
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), errors);
        }
        user.rename(dto.getName().strip());
        UserProfile profile = saved.orElseGet(() -> new UserProfile(userId));
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

    private static boolean flag(Map<String, Boolean> notify, String key) {
        return notify != null && Boolean.TRUE.equals(notify.get(key));
    }

}
