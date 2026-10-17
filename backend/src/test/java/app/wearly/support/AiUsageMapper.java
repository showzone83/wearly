package app.wearly.support;

import app.wearly.common.mybatis.JsonbMapTypeHandler;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/** 테스트에서 AI 호출 기록을 읽는 매퍼. 운영 코드에서 쓰지 않으므로 테스트 쪽에 둔다 (L10) */
@Mapper
public interface AiUsageMapper {

  @Select(
      "SELECT ai_analysis_log_id, feature, provider, model, status, error_code, image_sha256,"
          + " result, created_at FROM tb_ai_analysis_log WHERE user_id = #{userId}"
          + " ORDER BY created_at DESC, ai_analysis_log_id DESC LIMIT #{limit}")
  @Results({
    @Result(property = "id", column = "ai_analysis_log_id", id = true),
    @Result(property = "errorCode", column = "error_code"),
    @Result(property = "imageSha256", column = "image_sha256"),
    @Result(property = "result", column = "result", typeHandler = JsonbMapTypeHandler.class),
    @Result(property = "createdAt", column = "created_at")
  })
  List<AiLogRowDto> findRecent(@Param("userId") long userId, @Param("limit") int limit);
}
