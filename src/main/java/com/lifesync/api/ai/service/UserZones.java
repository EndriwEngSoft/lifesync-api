package com.lifesync.api.ai.service;

import com.lifesync.api.user.entity.User;

import java.time.DateTimeException;
import java.time.ZoneId;

/** Resolve o fuso do usuario; se o valor salvo for invalido, usa UTC. */
final class UserZones {

    private static final ZoneId FALLBACK_ZONE = ZoneId.of("UTC");

    private UserZones() {
    }

    static ZoneId of(User user) {
        try {
            return ZoneId.of(user.getTimezone());
        } catch (DateTimeException | NullPointerException e) {
            return FALLBACK_ZONE;
        }
    }
}
