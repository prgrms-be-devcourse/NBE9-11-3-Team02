package com.back.together02be.global.entity

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
open class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long=0L
        protected set

    @CreatedDate
    @Column(updatable = false)
    open var createdAt: LocalDateTime? = null
        protected set

    @LastModifiedDate
    open var modifiedAt: LocalDateTime? = null
        protected set
}