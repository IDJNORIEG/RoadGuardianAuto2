package com.roadguardian.auto.models

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

data class DetectionLog(
    val detections: MutableList<Detection> = mutableListOf(),
    var startTime: Long = System.currentTimeMillis(),
    var endTime: Long = 0
) {
    fun addDetection(detection: Detection) {
        detections.add(detection)
    }

    fun exportAsJson(context: Context): File? {
        return try {
            val jsonArray = JSONArray()
            detections.forEach { det ->
                val jsonObj = JSONObject().apply {
                    put("animal", det.animal.getLocalizedName())
                    put("confidence", det.confidence)
                    put("distance_m", det.distance)
                    put("timestamp", det.timestamp)
                }
                jsonArray.put(jsonObj)
            }

            val root = JSONObject().apply {
                put("detections", jsonArray)
                put("session_start", startTime)
                put("session_end", endTime)
                put("exported_at", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            }

            val file = File(context.filesDir, "detection_log_${System.currentTimeMillis()}.json")
            FileWriter(file).use { it.write(root.toString(2)) }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun exportAsCsv(context: Context): File? {
        return try {
            val file = File(context.filesDir, "detection_log_${System.currentTimeMillis()}.csv")
            FileWriter(file).use { writer ->
                writer.appendLine("animal,confidence,distance_m,timestamp")
                detections.forEach { det ->
                    writer.appendLine("${det.animal.getLocalizedName()},${det.confidence},${det.distance},${det.timestamp}")
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}