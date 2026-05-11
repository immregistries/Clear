package org.immregistries.clear.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "SystemSetting")
public class SystemSetting {

    @Id
    @Column(name = "settingKey", nullable = false, unique = true, length = 100)
    private String settingKey;

    @Column(name = "settingValue", length = 500)
    private String settingValue;

    public String getSettingKey() {
        return settingKey;
    }

    public void setSettingKey(String settingKey) {
        this.settingKey = settingKey;
    }

    public String getSettingValue() {
        return settingValue;
    }

    public void setSettingValue(String settingValue) {
        this.settingValue = settingValue;
    }
}
