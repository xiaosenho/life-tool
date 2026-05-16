package com.lifetool.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lifetool.ai")
public class AiProperties {
    private boolean mockEnabled = true;
    private int maxToolRounds = 3;
    private String disclaimer = "AI 建议仅供参考，不构成医疗、营养、财务或法律结论。";

    public boolean isMockEnabled() {
        return mockEnabled;
    }

    public void setMockEnabled(boolean mockEnabled) {
        this.mockEnabled = mockEnabled;
    }

    public int getMaxToolRounds() {
        return maxToolRounds;
    }

    public void setMaxToolRounds(int maxToolRounds) {
        this.maxToolRounds = maxToolRounds;
    }

    public String getDisclaimer() {
        return disclaimer;
    }

    public void setDisclaimer(String disclaimer) {
        this.disclaimer = disclaimer;
    }
}
