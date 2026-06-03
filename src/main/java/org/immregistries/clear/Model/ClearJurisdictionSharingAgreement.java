package org.immregistries.clear.model;

import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.OptimisticLockType;
import org.hibernate.annotations.OptimisticLocking;

@Entity
@DynamicUpdate
@OptimisticLocking(type = OptimisticLockType.ALL)
@Table(name = "clear_jurisdiction_sharing_agreement", indexes = {
        @Index(name = "cjsa_jurisdiction_active_idx", columnList = "jurisdiction_id,active") })
public class ClearJurisdictionSharingAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "jurisdiction_sharing_agreement_id", unique = true, nullable = false)
    private int jurisdictionSharingAgreementId;

    @Column(name = "jurisdiction_id", nullable = false)
    private int jurisdictionId;

    @Column(name = "agreement_id", nullable = false)
    private int agreementId;

    @Column(name = "first_agreed_by_user_id", nullable = false)
    private int firstAgreedByUserId;

    @Column(name = "first_agreed_by_name", nullable = false, length = 200)
    private String firstAgreedByName;

    @Column(name = "first_agreed_by_email", nullable = false, length = 320)
    private String firstAgreedByEmail;

    @Column(name = "first_agreed_date", nullable = false)
    private Date firstAgreedDate = new Date();

    @Column(name = "active", nullable = false)
    private boolean active;

    public int getJurisdictionSharingAgreementId() {
        return jurisdictionSharingAgreementId;
    }

    public int getJurisdictionId() {
        return jurisdictionId;
    }

    public void setJurisdictionId(int jurisdictionId) {
        this.jurisdictionId = jurisdictionId;
    }

    public int getAgreementId() {
        return agreementId;
    }

    public void setAgreementId(int agreementId) {
        this.agreementId = agreementId;
    }

    public int getFirstAgreedByUserId() {
        return firstAgreedByUserId;
    }

    public void setFirstAgreedByUserId(int firstAgreedByUserId) {
        this.firstAgreedByUserId = firstAgreedByUserId;
    }

    public String getFirstAgreedByName() {
        return firstAgreedByName;
    }

    public void setFirstAgreedByName(String firstAgreedByName) {
        this.firstAgreedByName = firstAgreedByName;
    }

    public String getFirstAgreedByEmail() {
        return firstAgreedByEmail;
    }

    public void setFirstAgreedByEmail(String firstAgreedByEmail) {
        this.firstAgreedByEmail = firstAgreedByEmail;
    }

    public Date getFirstAgreedDate() {
        return firstAgreedDate;
    }

    public void setFirstAgreedDate(Date firstAgreedDate) {
        this.firstAgreedDate = firstAgreedDate;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
