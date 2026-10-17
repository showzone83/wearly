package app.wearly.ai.adapter.out.query;

import app.wearly.ai.application.port.out.AiUsagePort;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** AiUsagePort 구현 (MyBatis) */
@Component
class AiUsageQueryAdapter implements AiUsagePort {

    private final AiUsageMapper mapper;

    AiUsageQueryAdapter(AiUsageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long countSuccess(long userId, Instant from, Instant to) {
        return mapper.countSuccess(userId, from, to);
    }
}
