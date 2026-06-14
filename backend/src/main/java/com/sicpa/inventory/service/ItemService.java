package com.sicpa.inventory.service;

import com.sicpa.inventory.dto.item.ItemDto;
import com.sicpa.inventory.dto.common.PagedItemsResponse;
import com.sicpa.inventory.dto.response.UpsertItemResponse;
import com.sicpa.inventory.dto.item.UpsertItemDto;
import com.sicpa.inventory.repository.ItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Business logic layer between controller and repository.
 *
 * <p>Both methods are {@code synchronized} on the service instance.
 * Upsert is a 3-step sequence: read existing location → remove from old → write to new.
 * A concurrent GET mid-upsert could observe an item in neither location.
 * {@link java.util.concurrent.ConcurrentHashMap} only protects individual map operations;
 * {@code synchronized} protects the entire multistep sequence.
 */
@Service
public class ItemService {

    private final ItemRepository itemRepository;

    public ItemService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    /**
     * Inserts a new item or moves an existing item to a different location.
     * Requirement: each item belongs to exactly one location at a time.
     */
    public synchronized UpsertItemDto upsertItem(String item, String location) {
        String previousLocation = itemRepository.findLocationByItem(item);

        boolean isNew = previousLocation == null;
        if (!isNew && !previousLocation.equals(location)) {
            itemRepository.removeItemFromLocation(item, previousLocation);
        }

        itemRepository.saveItemLocation(item, location);
        itemRepository.addItemToLocation(item, location);

        String action = isNew ? "created" : "updated";
        String message = String.format("Item %s %s in location: %s", item, action, location);

        return new UpsertItemDto(new UpsertItemResponse(item, location), message);
    }

    /**
     * Returns a paginated list of items for a location in insertion order.
     * Synchronized so a GET cannot read mid-upsert (item removed from old, not yet in new).
     * totalPages is computed here because it depends on business params (total, size).
     */
    public synchronized PagedItemsResponse getItemsByLocation(String location, int page, int size) {
        List<ItemDto> items = itemRepository.findByLocation(location, page, size);
        int total = itemRepository.countByLocation(location);
        int totalPages = (int) Math.ceil((double) total / size);
        return new PagedItemsResponse(items, page, size, total, totalPages);
    }
}
