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
@Table(name = "clear_user_agreement_acknowledgement", indexes = {
        @Index(name = "cuaa_user_current_idx", columnList = "user_id,jurisdiction_id,agreement_id") })
public class ClearUserAgreementAcknowledgement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_agreement_acknowledgement_id", unique = true, nullable = false)
    private int userAgreementAcknowledgementId;

    @Column(name = "user_id", nullable = false)
    private int userId;

    @Column(name = "jurisdiction_id", nullable = false)
    private int jurisdictionId;

    @Column(name = "agreement_id", nullable = false)
    private int agreementId;

    @Column(name = "acknowledged_date", nullable = false)
    private Date acknowledgedDate = new Date();

    @Column(name = "acknowledged_by_name", nullable = false, length = 200)
    private String acknowledgedByName;

    @Column(name = "acknowledged_by_email", nullable = false, length = 320)
    private String acknowledgedByEmail;

    @Column(name = "ip_address", length = 120)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    public int getUserAgreementAcknowledgementId() {
        return userAgreementAcknowledgementId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
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

    public Date getAcknowledgedDate() {
        return acknowledgedDate;
    }

    public void setAcknowledgedDate(Date acknowledgedDate) {
        this.acknowledgedDate = acknowledgedDate;
    }

    public String getAcknowledgedByName() {
        return acknowledgedByName;
    }

    public void setAcknowledgedByName(String acknowledgedByName) {
        this.acknowledgedByName = acknowledgedByName;
    }

    public String getAcknowledgedByEmail() {
        return acknowledgedByEmail;
    }

    public void setAcknowledgedByEmail(String acknowledgedByEmail) {
        this.acknowledgedByEmail = acknowledgedByEmail;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
