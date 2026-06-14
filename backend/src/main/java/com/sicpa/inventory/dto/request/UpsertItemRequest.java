package com.sicpa.inventory.dto.request;

/**
 * Request body for POST /api/item.
 * The same request handles both insert and update — the backend determines which applies.
 *
 * @param item
 * @param location
 */
public record UpsertItemRequest(String item, String location) {}
