package app.wearly.user;

import app.wearly.common.error.ApiException;
import app.wearly.domain.ColorKey;
import app.wearly.domain.DressCode;
import app.wearly.domain.EnumCodes;
import app.wearly.domain.StyleTag;
import app.wearly.domain.WorkType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final UserRepository users;
    private final UserProfileRepository profiles;

    public ProfileService(UserRepository users, UserProfileRepository profiles) {
        this.users = users;
        this.profiles = profiles;
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
        user.rename(dto.getName().strip());
        UserProfile profile = profiles.findByUserId(userId).orElseGet(() -> new UserProfile(userId));
        Map<String, Boolean> notify = new LinkedHashMap<>();
        notify.put("morning", dto.getNotify().morning());
        notify.put("evening", dto.getNotify().evening());
        notify.put("dMinus1", dto.getNotify().dMinus1());
        profile.update(
                EnumCodes.names(dto.getDressCodes()),
                EnumCodes.names(dto.getMoods()),
                dto.getColors().stream().distinct().map(ColorKey::code).toList(),
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
                EnumCodes.parse(p.getMoods(), StyleTag::valueOf),
                EnumCodes.parse(p.getColors(), ColorKey::fromCode),
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
