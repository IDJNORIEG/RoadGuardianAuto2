package com.roadguardian.auto.models

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

data class DetectionLog(
    val detections: MutableList<Detection> = mutableListOf()
) {
    fun addDetection(detection: Detection) {
        detections.add(detection)
    }

    fun exportAsJson(context: Context): File {
        val jsonArray = JSONArray()
        detections.forEach { det ->
            val jsonObj = JSONObject().apply {
                put("animal", det.animal.displayName)
                put("confidence", det.confidence)
                put("distance_m", det.distance)
                put("timestamp", System.currentTimeMillis())
            }
            jsonArray.put(jsonObj)
        }

        val root = JSONObject().apply {
            put("detections", jsonArray)
            put("exported_at", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
        }

        val file = File(context.filesDir, "detection_log.json")
        FileWriter(file).use { it.write(root.toString(2)) }
        return file
    }

    fun exportAsCsv(context: Context): File {
        val file = File(context.filesDir, "detection_log.csv")
        FileWriter(file).use { writer ->
            writer.appendLine("animal,confidence,distance_m,timestamp")
            detections.forEach { det ->
                writer.appendLine("${det.animal.displayName},${det.confidence},${det.distance},${System.currentTimeMillis()}")
            }
        }
        return file
    }
}
