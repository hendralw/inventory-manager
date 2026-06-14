package com.sicpa.inventory.dto.item;

/**
 * Single inventory item with its location and timestamps.
 *
 * @param item      item name
 * @param location  current location
 * @param createdAt ISO-8601 UTC string when the item was first added (preserved across moves)
 * @param updatedAt ISO-8601 UTC string when the item was last moved or re-saved
 */
public record ItemDto(String item, String location, String createdAt, String updatedAt) {}
