package com.sicpa.inventory.dto.response;

/** Response payload echoing the saved item and its assigned location.
 *
 * @param  item
 * @param location
 */
public record UpsertItemResponse(String item, String location) {}
