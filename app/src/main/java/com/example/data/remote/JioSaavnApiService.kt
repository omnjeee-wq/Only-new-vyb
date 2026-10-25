package com.example.data.remote

import com.example.data.model.ApiAlbum
import com.example.data.model.ApiArtistDetails
import com.example.data.model.ApiPlaylist
import com.example.data.model.ApiResponse
import com.example.data.model.ApiSearchResult
import com.example.data.model.ApiSong
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface JioSaavnApiService {

    @GET("api/search/songs")
    suspend fun searchSongs(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 15
    ): ApiResponse<ApiSearchResult<ApiSong>>

    @GET("api/search/albums")
    suspend fun searchAlbums(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 15
    ): ApiResponse<ApiSearchResult<ApiAlbum>>

    @GET("api/search/playlists")
    suspend fun searchPlaylists(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 15
    ): ApiResponse<ApiSearchResult<ApiPlaylist>>

    @GET("api/songs/{id}")
    suspend fun getSongById(
        @Path("id") id: String
    ): ApiResponse<List<ApiSong>>

    @GET("api/songs")
    suspend fun getSongsByIds(
        @Query("ids") ids: String
    ): ApiResponse<List<ApiSong>>

    @GET("api/albums")
    suspend fun getAlbumById(
        @Query("id") id: String
    ): ApiResponse<ApiAlbum>

    @GET("api/playlists")
    suspend fun getPlaylistById(
        @Query("id") id: String,
        @Query("limit") limit: Int = 30
    ): ApiResponse<ApiPlaylist>

    @GET("api/artists/{id}")
    suspend fun getArtistById(
        @Path("id") id: String,
        @Query("songCount") songCount: Int = 20,
        @Query("albumCount") albumCount: Int = 10
    ): ApiResponse<ApiArtistDetails>

    @GET("api/songs/{id}/suggestions")
    suspend fun getSongSuggestions(
        @Path("id") id: String,
        @Query("limit") limit: Int = 10
    ): ApiResponse<List<ApiSong>>

    @GET
    suspend fun getQueue(
        @Url url: String,
        @Query("seeds") seeds: String,
        @Query("exclude") exclude: String,
        @Query("limit") limit: Int = 10,
        @Query("skipped") skipped: String = "",
        @Query("liked") liked: String = ""
    ): ApiResponse<List<ApiSong>>

    @GET
    suspend fun getDiscover(
        @Url url: String,
        @Query("seeds") seeds: String,
        @Query("langs") langs: String,
        @Query("exclude") exclude: String,
        @Query("limit") limit: Int = 16,
        @Query("skipped") skipped: String = "",
        @Query("liked") liked: String = ""
    ): ApiResponse<List<ApiSong>>
}
