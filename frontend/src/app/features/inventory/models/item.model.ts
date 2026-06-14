/** Response envelope for all API calls: { status, message, data } */
export interface ApiResponse<T> {
  status: string;
  message: string | null;
  data: T;
}

/** Request body for POST /api/item. Handles both insert and update. */
export interface UpsertItemRequest {
  item: string;
  location: string;
}

/** Single inventory item with location and timestamps. */
export interface ItemDto {
  item: string;
  location: string;
  createdAt: string;
  updatedAt: string;
}

/** Paginated response from GET /api/locations/{location}/items. */
export interface PagedItemsResponse {
  items: ItemDto[];
  page: number;
  size: number;
  /** Total items across all pages — used for the result count display. */
  total: number;
  /** ⌈total / size⌉ — used to render the correct number of page buttons. */
  totalPages: number;
}
