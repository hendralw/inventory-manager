package com.sicpa.inventory.service;

import com.sicpa.inventory.dto.item.ItemDto;
import com.sicpa.inventory.dto.item.UpsertItemDto;
import com.sicpa.inventory.dto.common.PagedItemsResponse;
import com.sicpa.inventory.repository.ItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private ItemService itemService;

    private static final ItemDto APPLE  = new ItemDto("apple",  "A1", "2024-01-01T00:00:00Z", "2024-01-01T00:00:00Z");
    private static final ItemDto BANANA = new ItemDto("banana", "A1", "2024-01-01T00:00:00Z", "2024-01-01T00:00:00Z");

    @Test
    void upsertItem_newItem_returnsCreatedMessage() {
        when(itemRepository.findLocationByItem("apple")).thenReturn(null);

        UpsertItemDto result = itemService.upsertItem("apple", "A1");

        assertThat(result.message()).contains("created");
        assertThat(result.data().item()).isEqualTo("apple");
        assertThat(result.data().location()).isEqualTo("A1");
        verify(itemRepository, never()).removeItemFromLocation(any(), any());
        verify(itemRepository).saveItemLocation("apple", "A1");
        verify(itemRepository).addItemToLocation("apple", "A1");
    }

    @Test
    void upsertItem_movedToDifferentLocation_removesFromOldLocation() {
        when(itemRepository.findLocationByItem("apple")).thenReturn("B2");

        UpsertItemDto result = itemService.upsertItem("apple", "A1");

        assertThat(result.message()).contains("updated");
        verify(itemRepository).removeItemFromLocation("apple", "B2");
        verify(itemRepository).addItemToLocation("apple", "A1");
    }

    @Test
    void upsertItem_sameLocation_doesNotRemoveFromLocation() {
        when(itemRepository.findLocationByItem("apple")).thenReturn("A1");

        UpsertItemDto result = itemService.upsertItem("apple", "A1");

        assertThat(result.message()).contains("updated");
        verify(itemRepository, never()).removeItemFromLocation(any(), any());
        verify(itemRepository).addItemToLocation("apple", "A1");
    }

    @Test
    void getItemsByLocation_returnsPaginatedResponse() {
        when(itemRepository.findByLocation("A1", 0, 10)).thenReturn(List.of(APPLE, BANANA));
        when(itemRepository.countByLocation("A1")).thenReturn(25);

        PagedItemsResponse response = itemService.getItemsByLocation("A1", 0, 10);

        assertThat(response.items()).extracting(ItemDto::item).containsExactly("apple", "banana");
        assertThat(response.total()).isEqualTo(25);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(10);
    }

    @Test
    void getItemsByLocation_totalPages_roundsUp() {
        when(itemRepository.findByLocation("A1", 0, 10)).thenReturn(List.of());
        when(itemRepository.countByLocation("A1")).thenReturn(11);

        PagedItemsResponse response = itemService.getItemsByLocation("A1", 0, 10);

        assertThat(response.totalPages()).isEqualTo(2);
    }

    @Test
    void getItemsByLocation_emptyLocation_returnsZeroPages() {
        when(itemRepository.findByLocation("A1", 0, 10)).thenReturn(List.of());
        when(itemRepository.countByLocation("A1")).thenReturn(0);

        PagedItemsResponse response = itemService.getItemsByLocation("A1", 0, 10);

        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isZero();
        assertThat(response.totalPages()).isZero();
    }
}
