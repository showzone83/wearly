package app.wearly.code.adapter.out.query;

/** 코드별 사용 수 (그룹, 코드, 개수) */
public class CodeUsageRow {

    private String groupCode;
    private String code;
    private long count;

    public String getGroupCode() {
        return groupCode;
    }

    public void setGroupCode(String groupCode) {
        this.groupCode = groupCode;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
}
