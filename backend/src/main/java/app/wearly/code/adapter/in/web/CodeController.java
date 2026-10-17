package app.wearly.code.adapter.in.web;

import app.wearly.code.adapter.in.web.CodeDtos.CodesResponse;
import app.wearly.code.application.port.in.CodeCatalog;
import app.wearly.common.domain.code.CodeSnapshot;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/** 공개 코드 조회. ETag = "version", If-None-Match가 같으면 304 */
@RestController
@RequestMapping("/api/v1/codes")
public class CodeController {

    private final CodeCatalog catalog;

    public CodeController(CodeCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public ResponseEntity<CodesResponse> codes(WebRequest request) {
        CodeSnapshot snapshot = catalog.current();
        String etag = "\"" + snapshot.version() + "\"";
        // 캐시는 해도 매번 서버에 확인하게 한다 (no-cache = 재검증 필수)
        CacheControl cache = CacheControl.noCache();
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(cache).build();
        }
        return ResponseEntity.ok().eTag(etag).cacheControl(cache).body(CodeDtos.from(snapshot));
    }
}
