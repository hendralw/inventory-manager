import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse, PagedItemsResponse, UpsertItemRequest } from '../models/item.model';

/**
 * HTTP client for the inventory API.
 * Singleton via providedIn: 'root' — one shared instance across all components.
 */
@Injectable({ providedIn: 'root' })
export class ItemService {
  private readonly apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  upsertItem(request: UpsertItemRequest): Observable<ApiResponse<any>> {
    return this.http.post<ApiResponse<any>>(`${this.apiUrl}/item`, request);
  }

  getItemsByLocation(
    location: string,
    page: number = 0,
    size: number = 20
  ): Observable<ApiResponse<PagedItemsResponse>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    return this.http.get<ApiResponse<PagedItemsResponse>>(
      `${this.apiUrl}/locations/${encodeURIComponent(location)}/items`,
      { params }
    );
  }
}
