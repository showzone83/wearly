package app.wearly.code;

import app.wearly.code.CodeDtos.CodesResponse;
import java.util.Arrays;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 공개 코드 목록. 로그인 전 화면에서도 쓴다. ETag(version)가 같으면 304. */
@RestController
public class CodeController {

    private final CodeCatalog catalog;

    public CodeController(CodeCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/api/v1/codes")
    public ResponseEntity<CodesResponse> codes(
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        CodeSnapshot s = catalog.snapshot();
        String etag = "\"" + s.version() + "\"";
        if (matches(ifNoneMatch, etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(CacheControl.noCache()).build();
        }
        return ResponseEntity.ok().eTag(etag).cacheControl(CacheControl.noCache()).body(CodeDtos.publicView(s));
    }

    /** If-None-Match: "*", 여러 개(쉼표), 약한 비교(W/) 모두 받는다 */
    static boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        return Arrays.stream(ifNoneMatch.split(","))
                .map(String::strip)
                .map(t -> t.startsWith("W/") ? t.substring(2) : t)
                .anyMatch(t -> t.equals("*") || t.equals(etag));
    }
}
