package com.sicpa.inventory.controller;

import com.sicpa.inventory.dto.common.ApiResponse;
import com.sicpa.inventory.dto.common.PagedItemsResponse;
import com.sicpa.inventory.dto.request.UpsertItemRequest;
import com.sicpa.inventory.dto.response.UpsertItemResponse;
import com.sicpa.inventory.dto.item.UpsertItemDto;
import com.sicpa.inventory.service.ItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST entry point for inventory operations.
 * Handles HTTP concerns only — business logic lives in {@link ItemService}.
 * All responses use the {@link ApiResponse} envelope: { status, message, data }.
 */
@RestController
@RequestMapping("/api")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    /**
     * POST /api/item — creates or moves an item (upsert).
     *
     * @return 400 if item or location is blank; 200 with saved data otherwise
     */
    @PostMapping("/item")
    public ResponseEntity<ApiResponse<UpsertItemResponse>> upsertItem(@RequestBody UpsertItemRequest request) {
        if (request.item() == null || request.item().isBlank()
                || request.location() == null || request.location().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("item and location are required"));
        }
        UpsertItemDto result = itemService.upsertItem(request.item().trim(), request.location().trim());
        return ResponseEntity.ok(ApiResponse.success(result.message(), result.data()));
    }

    /**
     * GET /api/locations/{location}/items?page=0&size=20
     * Location is a path variable, not a query param — it is the resource being accessed.
     *
     * @return 400 if page or size are out of range; 200 with paginated items otherwise
     */
    @GetMapping("/locations/{location}/items")
    public ResponseEntity<ApiResponse<PagedItemsResponse>> getItemsByLocation(
            @PathVariable String location,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (page < 0 || size <= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.error("page must be >= 0 and size must be > 0"));
        }
        PagedItemsResponse data = itemService.getItemsByLocation(location, page, size);
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
