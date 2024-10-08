package ai.soliman.plugins.messagereader;

import java.util.List;
import java.util.ArrayList;

public class GetMessageFilterInput {
    private List<String> ids;
    private String body;
    private String sender;
    private Long minDate;
    private Long maxDate;
    private Integer indexFrom;
    private Integer limit;

    // Getters and setters
    public List<String> getIds() {
        return ids != null ? ids : new ArrayList<>();
    }

    public void setIds(List<String> ids) { this.ids = ids; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getSender() {
        return sender != null ? sender : "";
    }

    public void setSender(String sender) { this.sender = sender; }

    public Long getMinDate() { return minDate; }
    public void setMinDate(Long minDate) { this.minDate = minDate; }

    public Long getMaxDate() { return maxDate; }
    public void setMaxDate(Long maxDate) { this.maxDate = maxDate; }

    public Integer getIndexFrom() { return indexFrom; }
    public void setIndexFrom(Integer indexFrom) { this.indexFrom = indexFrom; }

    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }
}
