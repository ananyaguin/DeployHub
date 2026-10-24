package com.cloudflow.dto.response;

import java.time.Instant;

import com.cloudflow.entity.DeliveryStatus;
import com.cloudflow.entity.NotificationChannel;

public record NotificationDeliveryResponse(
        NotificationChannel channel, DeliveryStatus status, Instant sentAt) {}
