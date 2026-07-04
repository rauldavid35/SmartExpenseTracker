package com.example.smartexpensetracker.repository

import com.example.smartexpensetracker.model.PhotonResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL


class LocationRepository {
    suspend fun searchLocations(query: String, userLat: Double, userLon: Double): List<PhotonResult> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = query.replace(" ", "+")
            val urlString = "https://photon.komoot.io/api/?q=$encodedQuery&lat=$userLat&lon=$userLon&limit=5"

            val connection = URL(urlString).openConnection() as HttpURLConnection
            val response = connection.inputStream.bufferedReader().readText()
            val features = JSONObject(response).getJSONArray("features")

            val results = mutableListOf<PhotonResult>()
            for (i in 0 until features.length()) {
                val feat = features.getJSONObject(i)
                val props = feat.getJSONObject("properties")
                val geom = feat.getJSONObject("geometry").getJSONArray("coordinates")

                results.add(PhotonResult(
                    name = props.optString("name", "Unknown"),
                    city = props.optString("city", null),
                    street = props.optString("street", null),
                    latitude = geom.getDouble(1),
                    longitude = geom.getDouble(0)
                ))
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
    }
}