package com.cloudflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "worker_nodes")
public class WorkerNode extends TimestampedPublicEntity {

    @Column(nullable = false, length = 120, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkerStatus status;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "cpu_millis", nullable = false)
    private int cpuMillis = 1000;

    @Column(name = "memory_mb", nullable = false)
    private int memoryMb = 512;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public WorkerStatus getStatus() {
        return status;
    }

    public void setStatus(WorkerStatus status) {
        this.status = status;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public int getCpuMillis() {
        return cpuMillis;
    }

    public void setCpuMillis(int cpuMillis) {
        this.cpuMillis = cpuMillis;
    }

    public int getMemoryMb() {
        return memoryMb;
    }

    public void setMemoryMb(int memoryMb) {
        this.memoryMb = memoryMb;
    }
}
