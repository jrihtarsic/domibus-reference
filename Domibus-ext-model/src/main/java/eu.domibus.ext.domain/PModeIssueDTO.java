package eu.domibus.ext.domain;

/**
 * @author Ion Perpegel
 * @since 4.2
 */
public class PModeIssueDTO {

    private IssueLevelExt level;
    private String code;
    private String message;

    public PModeIssueDTO() {}

    public PModeIssueDTO(String message) {
        this.message = message;
        this.level = IssueLevelExt.NOTE;
    }

    public PModeIssueDTO(String message, IssueLevelExt level) {
        this.message = message;
        this.level = level;
    }

    public IssueLevelExt getLevel() {
        return level;
    }

    public void setLevel(IssueLevelExt level) {
        this.level = level;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return message;
    }
}