package org.immregistries.clear.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import org.hibernate.annotations.OptimisticLockType;

@Entity
@org.hibernate.annotations.Entity(optimisticLock = OptimisticLockType.ALL, dynamicUpdate = true)
@Table(name = "ValidationCode")
public class ValidationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "validationCodeId", unique = true, nullable = false)
    private int validationCodeId;

    @Column(name = "accessCode", unique = true, nullable = false)
    private int accessCode;

    @Column(name = "contactId", nullable = false)
    private int contactId;

    @Column(name = "issueDate", nullable = false)
    private Date issueDate = new Date();

    public int getValidationCodeId() {
        return validationCodeId;
    }

    public int getAccessCode() {
        return accessCode;
    }

    public int getcantactId() {
        return contactId;
    }

    public void setContactId(int contactId) {
        this.contactId = contactId;
    }

    public Date getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(Date issueDate) {
        this.issueDate = issueDate;
    }
}