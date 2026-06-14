package com.sicpa.inventory.controller;

import com.sicpa.inventory.dto.item.ItemDto;
import com.sicpa.inventory.dto.item.UpsertItemDto;
import com.sicpa.inventory.dto.common.PagedItemsResponse;
import com.sicpa.inventory.dto.response.UpsertItemResponse;
import com.sicpa.inventory.service.ItemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ItemController.class)
class ItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ItemService itemService;

    private static final String TS = "2024-01-01T00:00:00Z";

    // --- POST /api/item ---

    @Test
    void upsertItem_validRequest_returns200WithData() throws Exception {
        when(itemService.upsertItem("apple", "A1")).thenReturn(
                new UpsertItemDto(new UpsertItemResponse("apple", "A1"), "Item apple created in location: A1")
        );

        mockMvc.perform(post("/api/item")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"item": "apple", "location": "A1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Item apple created in location: A1"))
                .andExpect(jsonPath("$.data.item").value("apple"))
                .andExpect(jsonPath("$.data.location").value("A1"));
    }

    @Test
    void upsertItem_blankItem_returns400() throws Exception {
        mockMvc.perform(post("/api/item")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"item": "  ", "location": "A1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void upsertItem_blankLocation_returns400() throws Exception {
        mockMvc.perform(post("/api/item")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"item": "apple", "location": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void upsertItem_nullFields_returns400() throws Exception {
        mockMvc.perform(post("/api/item")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void upsertItem_missingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/item")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void upsertItem_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/item")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    // --- GET /api/locations/{location}/items ---

    @Test
    void getItemsByLocation_validRequest_returns200WithPagedData() throws Exception {
        when(itemService.getItemsByLocation(anyString(), anyInt(), anyInt())).thenReturn(
                new PagedItemsResponse(List.of(
                        new ItemDto("apple",  "A1", TS, TS),
                        new ItemDto("banana", "A1", TS, TS)
                ), 0, 20, 2, 1)
        );

        mockMvc.perform(get("/api/locations/A1/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.items[0].item").value("apple"))
                .andExpect(jsonPath("$.data.items[1].item").value("banana"))
                .andExpect(jsonPath("$.data.items[0].location").value("A1"))
                .andExpect(jsonPath("$.data.items[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(1));
    }

    @Test
    void getItemsByLocation_negativePage_returns400() throws Exception {
        mockMvc.perform(get("/api/locations/A1/items?page=-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void getItemsByLocation_zeroSize_returns400() throws Exception {
        mockMvc.perform(get("/api/locations/A1/items?size=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void getItemsByLocation_nonNumericPage_returns400() throws Exception {
        mockMvc.perform(get("/api/locations/A1/items?page=abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void unknownRoute_returns404() throws Exception {
        mockMvc.perform(get("/api/nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"));
    }
}
