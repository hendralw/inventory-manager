package com.sicpa.inventory.dto.item;

import com.sicpa.inventory.dto.response.UpsertItemResponse;

/**
 * Internal carrier between service and controller — not serialised to JSON.
 * Bundles the saved data and the user-facing message in one return value.
 *
 * @param data
 * @param message
 */
public record UpsertItemDto(UpsertItemResponse data, String message) {}
