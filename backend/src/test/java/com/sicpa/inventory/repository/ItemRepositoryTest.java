package com.sicpa.inventory.repository;

import com.sicpa.inventory.dto.item.ItemDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ItemRepositoryTest {

    private ItemRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ItemRepository();
    }

    @Test
    void findLocationByItem_returnsNull_whenItemNotFound() {
        assertThat(repository.findLocationByItem("unknown")).isNull();
    }

    @Test
    void saveItemLocation_andFindLocationByItem_roundTrip() {
        repository.saveItemLocation("apple", "A1");
        assertThat(repository.findLocationByItem("apple")).isEqualTo("A1");
    }

    @Test
    void addItemToLocation_andFindByLocation_returnsItem() {
        repository.addItemToLocation("apple", "A1");
        List<ItemDto> result = repository.findByLocation("A1", 0, 10);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().item()).isEqualTo("apple");
        assertThat(result.getFirst().location()).isEqualTo("A1");
        assertThat(result.getFirst().createdAt()).isNotNull();
        assertThat(result.getFirst().updatedAt()).isNotNull();
    }

    @Test
    void removeItemFromLocation_removesItem() {
        repository.addItemToLocation("apple", "A1");
        repository.removeItemFromLocation("apple", "A1");
        assertThat(repository.findByLocation("A1", 0, 10)).isEmpty();
    }

    @Test
    void removeItemFromLocation_doesNothing_whenLocationUnknown() {
        // should not throw
        repository.removeItemFromLocation("apple", "NONEXISTENT");
    }

    @Test
    void countByLocation_returnsZero_whenLocationUnknown() {
        assertThat(repository.countByLocation("UNKNOWN")).isZero();
    }

    @Test
    void countByLocation_returnsCorrectCount() {
        repository.addItemToLocation("apple", "A1");
        repository.addItemToLocation("banana", "A1");
        assertThat(repository.countByLocation("A1")).isEqualTo(2);
    }

    @Test
    void findByLocation_returnsEmptyList_whenLocationUnknown() {
        assertThat(repository.findByLocation("EMPTY", 0, 10)).isEmpty();
    }

    @Test
    void findByLocation_paginatesCorrectly() {
        for (int i = 1; i <= 15; i++) {
            repository.addItemToLocation("item" + i, "A1");
        }
        List<ItemDto> page0 = repository.findByLocation("A1", 0, 10);
        List<ItemDto> page1 = repository.findByLocation("A1", 1, 10);
        assertThat(page0).hasSize(10);
        assertThat(page1).hasSize(5);
    }

    @Test
    void findByLocation_maintainsInsertionOrder() {
        repository.addItemToLocation("first", "A1");
        repository.addItemToLocation("second", "A1");
        repository.addItemToLocation("third", "A1");
        assertThat(repository.findByLocation("A1", 0, 10))
                .extracting(ItemDto::item)
                .containsExactly("first", "second", "third");
    }

    @Test
    void addItemToLocation_deduplicates_whenSameItemAddedTwice() {
        repository.addItemToLocation("apple", "A1");
        repository.addItemToLocation("apple", "A1");
        assertThat(repository.countByLocation("A1")).isEqualTo(1);
    }
}
