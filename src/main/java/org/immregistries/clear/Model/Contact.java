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
@Table(name = "Contact")
@SuppressWarnings("unused")
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contactId", unique = true, nullable = false)
    private int contactId;

    private int jurisdictionId;

    private String nameFirst;

    private String nameLast;

    private String emailAddress;

    private Date dateAccess;

    private Date dateCreated;

}
