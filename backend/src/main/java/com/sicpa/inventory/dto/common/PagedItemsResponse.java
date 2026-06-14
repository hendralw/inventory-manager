package com.sicpa.inventory.dto.common;

import com.sicpa.inventory.dto.item.ItemDto;

import java.util.List;

/**
 * Paginated response for GET /api/locations/{location}/items.
 *
 * @param items      items for the current page, each with name, location, and createdAt
 * @param page       zero-based page index
 * @param size       page size requested
 * @param total      total items across all pages
 * @param totalPages ⌈total / size⌉
 */
public record PagedItemsResponse(
    List<ItemDto> items,
    int page,
    int size,
    int total,
    int totalPages
) {}
