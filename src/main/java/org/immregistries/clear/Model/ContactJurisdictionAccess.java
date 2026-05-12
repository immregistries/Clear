package org.immregistries.clear.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.OptimisticLockType;
import org.hibernate.annotations.OptimisticLocking;

@Entity
@DynamicUpdate
@OptimisticLocking(type = OptimisticLockType.ALL)
@Table(name = "ContactJurisdictionAccess", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "contactId", "jurisdictionId" }) }, indexes = {
                @Index(name = "contactJurisdictionAccessJurisdictionIdx", columnList = "jurisdictionId") })
public class ContactJurisdictionAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contactJurisdictionAccessId", unique = true, nullable = false)
    private int contactJurisdictionAccessId;

    @Column(name = "contactId", nullable = false)
    private int contactId;

    @Column(name = "jurisdictionId", nullable = false)
    private int jurisdictionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "accessRole", nullable = false, length = 32)
    private JurisdictionAccessRole accessRole = JurisdictionAccessRole.VIEWER;

    @Column(name = "dateCreated", nullable = false)
    private Date dateCreated = new Date();

    @Column(name = "dateUpdated", nullable = false)
    private Date dateUpdated = new Date();

    @Column(name = "updatedByContactId")
    private Integer updatedByContactId;

    public int getContactJurisdictionAccessId() {
        return contactJurisdictionAccessId;
    }

    public int getContactId() {
        return contactId;
    }

    public void setContactId(int contactId) {
        this.contactId = contactId;
    }

    public int getJurisdictionId() {
        return jurisdictionId;
    }

    public void setJurisdictionId(int jurisdictionId) {
        this.jurisdictionId = jurisdictionId;
    }

    public JurisdictionAccessRole getAccessRole() {
        return accessRole;
    }

    public void setAccessRole(JurisdictionAccessRole accessRole) {
        this.accessRole = accessRole;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    public Date getDateUpdated() {
        return dateUpdated;
    }

    public void setDateUpdated(Date dateUpdated) {
        this.dateUpdated = dateUpdated;
    }

    public Integer getUpdatedByContactId() {
        return updatedByContactId;
    }

    public void setUpdatedByContactId(Integer updatedByContactId) {
        this.updatedByContactId = updatedByContactId;
    }
}