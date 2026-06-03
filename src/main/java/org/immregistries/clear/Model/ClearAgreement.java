package org.immregistries.clear.model;

import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.OptimisticLockType;
import org.hibernate.annotations.OptimisticLocking;

@Entity
@DynamicUpdate
@OptimisticLocking(type = OptimisticLockType.ALL)
@Table(name = "clear_agreement")
public class ClearAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "agreement_id", unique = true, nullable = false)
    private int agreementId;

    @Column(name = "agreement_code", nullable = false, length = 64)
    private String agreementCode;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "agreement_text", nullable = false, length = 4000)
    private String agreementText;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_date", nullable = false)
    private Date createdDate = new Date();

    @Column(name = "created_by_user_id")
    private Integer createdByUserId;

    @Column(name = "effective_date", nullable = false)
    private Date effectiveDate = new Date();

    public int getAgreementId() {
        return agreementId;
    }

    public String getAgreementCode() {
        return agreementCode;
    }

    public void setAgreementCode(String agreementCode) {
        this.agreementCode = agreementCode;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(int versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAgreementText() {
        return agreementText;
    }

    public void setAgreementText(String agreementText) {
        this.agreementText = agreementText;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public Integer getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(Integer createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public Date getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(Date effectiveDate) {
        this.effectiveDate = effectiveDate;
    }
}
