package design_pattern_prototyping.util.CacheAsideSQL;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class ProxySQLRule {
    private final StringProperty regexPattern;
    private final StringProperty ttl;

    public ProxySQLRule(String regexPattern, String ttl) {
        this.regexPattern = new SimpleStringProperty(regexPattern);
        this.ttl = new SimpleStringProperty(ttl);
    }

    public String getRegexPattern() {
        return regexPattern.get();
    }

    public void setRegexPattern(String regexPattern) {
        this.regexPattern.set(regexPattern);
    }

    public StringProperty regexPatternProperty() {
        return regexPattern;
    }

    public String getTtl() {
        return ttl.get();
    }

    public void setTtl(String ttl) {
        this.ttl.set(ttl);
    }

    public StringProperty ttlProperty() {
        return ttl;
    }
}