package com.sicpa.inventory.repository;

import com.sicpa.inventory.dto.item.ItemDto;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store using three maps for O(1) bidirectional lookup.
 *
 * <p>locationItemsMap values are {@link LinkedHashSet}:
 * O(1) add/contains (vs List's O(n) contains) with stable insertion order
 * for consistent pagination (vs HashSet's undefined order).
 *
 * <p>{@link ConcurrentHashMap} makes individual map operations thread-safe.
 * Multistep sequences are protected by {@code synchronized} in ItemService.
 *
 * <p>itemCreatedAtMap tracks when each item was first added to the system.
 * {@code putIfAbsent} ensures the timestamp is preserved across location moves.
 */
@Repository
public class ItemRepository {

    private static final DateTimeFormatter ISO_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final Map<String, String>      itemLocationMap  = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> locationItemsMap = new ConcurrentHashMap<>();
    private final Map<String, Instant>     itemCreatedAtMap = new ConcurrentHashMap<>();
    private final Map<String, Instant>     itemUpdatedAtMap = new ConcurrentHashMap<>();

    /** @return current location of the item, or {@code null} if it doesn't exist */
    public String findLocationByItem(String item) {
        return itemLocationMap.get(item);
    }

    public void saveItemLocation(String item, String location) {
        itemLocationMap.put(item, location);
    }

    /** Called before re-assigning an item to remove it from its previous location. */
    public void removeItemFromLocation(String item, String location) {
        Set<String> items = locationItemsMap.get(location);
        if (items != null) {
            items.remove(item);
        }
    }

    /**
     * Adds item to the location's set and records creation time if this is the
     * first time the item has been seen. {@code putIfAbsent} is atomic and
     * preserves the original timestamp across subsequent location moves.
     */
    public void addItemToLocation(String item, String location) {
        locationItemsMap.computeIfAbsent(location, k -> new LinkedHashSet<>()).add(item);
        itemCreatedAtMap.putIfAbsent(item, Instant.now());
        itemUpdatedAtMap.put(item, Instant.now());
    }

    /**
     * Returns one page of {@link ItemDto} objects in insertion order.
     * skip/limit are the stream equivalents of SQL OFFSET/FETCH NEXT.
     */
    public List<ItemDto> findByLocation(String location, int page, int size) {
        Set<String> items = locationItemsMap.getOrDefault(location, new LinkedHashSet<>());
        return items.stream()
                .sorted(Comparator.comparingInt(this::extractNumber))
                .skip((long) page * size)
                .limit(size)
                .map(item -> new ItemDto(
                        item, location,
                        ISO_UTC.format(itemCreatedAtMap.get(item)),
                        ISO_UTC.format(itemUpdatedAtMap.get(item))))
                .toList();
    }

    public int countByLocation(String location) {
        Set<String> items = locationItemsMap.get(location);
        return items == null ? 0 : items.size();
    }

    private int extractNumber(String item) {
        try {
            return Integer.parseInt(item.replaceAll("\\D+", ""));
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }
}
