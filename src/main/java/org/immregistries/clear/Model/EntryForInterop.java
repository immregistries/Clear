package org.immregistries.clear.model;

import java.util.Date;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import org.hibernate.annotations.OptimisticLockType;

@Entity
@org.hibernate.annotations.Entity(optimisticLock = OptimisticLockType.ALL, dynamicUpdate = true)
@Table(name = "EntryForInterop")
public class EntryForInterop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entryForInteropId", unique = true, nullable = false)
    private int entryForInteropId;

    @Column(name = "countUpdate", nullable = false)
    private int countUpdate = 0;

    @Column(name = "countQuery", nullable = false)
    private int countQuery = 0;

    @Column(name = "reportingPeriod", nullable = false)
    private Date reportingPeriod = new Date();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jurisdiction")
    private Jurisdiction jurisdiction;

    @Column(name = "contactId", nullable = false)
    private int contactId;

    public int getEntryForInteropId() {
        return entryForInteropId;
    }

    public int getCountUpdate() {
        return countUpdate;
    }

    public void setCountUpdate(int countUpdate) {
        this.countUpdate = countUpdate;
    }

    public int getCountQuery() {
        return countQuery;
    }

    public void setCountQuery(int countQuery) {
        this.countQuery = countQuery;
    }

    public Date getReportingPeriod() {
        return reportingPeriod;
    }

    public void setReportingPeriod(Date reportingPeriod) {
        this.reportingPeriod = reportingPeriod;
    }

    public Jurisdiction getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(Jurisdiction jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public int getContactId() {
        return contactId;
    }

    public void setContactId(int contactId) {
        this.contactId = contactId;
    }

}
