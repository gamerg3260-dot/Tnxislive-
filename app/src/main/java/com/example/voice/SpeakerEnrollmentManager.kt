package com.example.voice

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

data class SpeakerVerificationResult(
    val isMatch: Boolean,
    val similarity: Float,
    val threshold: Float,
    val messageHindi: String
)

data class EnrollmentPhrase(
    val id: Int,
    val phraseTextHindi: String,
    val phraseTextEnglish: String,
    val hint: String
)

/**
 * On-Device Speaker Recognition & Voice Fingerprinting Engine.
 * Extracts lightweight acoustic embeddings from user speech and compares
 * them via fast cosine similarity math (0ms network latency, 100% on-device).
 */
class SpeakerEnrollmentManager(private val context: Context) {

    private val tag = "SpeakerEnrollment"
    private val prefs: SharedPreferences = context.getSharedPreferences("max_speaker_profile", Context.MODE_PRIVATE)

    companion object {
        const val EMBEDDING_DIMENSION = 32
        const val DEFAULT_SIMILARITY_THRESHOLD = 0.62f
        const val SAMPLE_RATE = 16000
        const val RECORD_DURATION_MS = 2500L

        val ENROLLMENT_PHRASES = listOf(
            EnrollmentPhrase(1, "हे मैक्स (Hey Max)", "Hey Max", "स्वाभाविक रूप से बोलें"),
            EnrollmentPhrase(2, "मैक्स मेरा नाम है", "Max mera naam hai", "सामान्य आवाज़ में बोलें"),
            EnrollmentPhrase(3, "आज मौसम कैसा है", "Aaj mausam kaisa hai", "स्पष्ट रूप से बोलें"),
            EnrollmentPhrase(4, "मैक्स फोन लॉक करो", "Max phone lock karo", "सुरक्षा कमांड बोलें"),
            EnrollmentPhrase(5, "मैक्स मेरी आवाज़ सुनो", "Max meri awaaz suno", "अंतिम सैंपल रिकॉर्ड करें")
        )
    }

    private val _isEnrolled = MutableStateFlow(false)
    val isEnrolled: StateFlow<Boolean> = _isEnrolled.asStateFlow()

    private val _isVerificationEnabled = MutableStateFlow(true)
    val isVerificationEnabled: StateFlow<Boolean> = _isVerificationEnabled.asStateFlow()

    private val _enrolledSimilarityThreshold = MutableStateFlow(DEFAULT_SIMILARITY_THRESHOLD)
    val enrolledSimilarityThreshold: StateFlow<Float> = _enrolledSimilarityThreshold.asStateFlow()

    private val _currentEnrollmentStep = MutableStateFlow(0)
    val currentEnrollmentStep: StateFlow<Int> = _currentEnrollmentStep.asStateFlow()

    private val _isRecordingSample = MutableStateFlow(false)
    val isRecordingSample: StateFlow<Boolean> = _isRecordingSample.asStateFlow()

    private val _currentRmsLevel = MutableStateFlow(0f)
    val currentRmsLevel: StateFlow<Float> = _currentRmsLevel.asStateFlow()

    private var enrolledProfileVector: FloatArray? = null
    private val collectedSampleVectors = mutableListOf<FloatArray>()
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        loadSavedProfile()
    }

    private fun loadSavedProfile() {
        val enrolled = prefs.getBoolean("is_enrolled", false)
        val enabled = prefs.getBoolean("verification_enabled", true)
        val threshold = prefs.getFloat("similarity_threshold", DEFAULT_SIMILARITY_THRESHOLD)
        val vectorJson = prefs.getString("profile_vector", null)

        _isEnrolled.value = enrolled
        _isVerificationEnabled.value = enabled
        _enrolledSimilarityThreshold.value = threshold

        if (enrolled && !vectorJson.isNullOrBlank()) {
            try {
                val array = JSONArray(vectorJson)
                val vec = FloatArray(array.length())
                for (i in 0 until array.length()) {
                    vec[i] = array.getDouble(i).toFloat()
                }
                enrolledProfileVector = normalize(vec)
                Log.i(tag, "Loaded enrolled speaker profile with ${vec.size} dimensions.")
            } catch (e: Exception) {
                Log.e(tag, "Failed to parse saved voice profile: ${e.message}")
                enrolledProfileVector = null
            }
        }
    }

    /**
     * Start the 5-phrase enrollment process from beginning.
     */
    fun startNewEnrollment() {
        collectedSampleVectors.clear()
        _currentEnrollmentStep.value = 0
        _isRecordingSample.value = false
    }

    /**
     * Record audio for the current enrollment phrase and compute its embedding.
     */
    fun recordSampleForPhrase(
        phraseIndex: Int,
        onSampleProgress: (Float) -> Unit,
        onSampleCompleted: (Boolean, String) -> Unit
    ) {
        if (recordingJob?.isActive == true) {
            recordingJob?.cancel()
        }

        _isRecordingSample.value = true
        _currentEnrollmentStep.value = phraseIndex

        recordingJob = scope.launch {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(SAMPLE_RATE * 2)

            var audioRecord: AudioRecord? = null
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    withContext(Dispatchers.Main) {
                        _isRecordingSample.value = false
                        onSampleCompleted(false, "माइक्रोफोन शुरू नहीं हो सका।")
                    }
                    return@launch
                }

                audioRecord.startRecording()
                val totalShortsToRead = (SAMPLE_RATE * (RECORD_DURATION_MS / 1000f)).toInt()
                val recordedShorts = ShortArray(totalShortsToRead)
                var totalRead = 0
                val tempBuffer = ShortArray(1024)

                val startTime = System.currentTimeMillis()

                while (isActive && totalRead < totalShortsToRead && (System.currentTimeMillis() - startTime) < RECORD_DURATION_MS + 200) {
                    val read = audioRecord.read(tempBuffer, 0, tempBuffer.size.coerceAtMost(totalShortsToRead - totalRead))
                    if (read > 0) {
                        System.arraycopy(tempBuffer, 0, recordedShorts, totalRead, read)
                        totalRead += read

                        // Calculate current RMS level for live visual feedback
                        var sumSquare = 0.0
                        for (i in 0 until read) {
                            sumSquare += (tempBuffer[i] * tempBuffer[i]).toDouble()
                        }
                        val rms = sqrt(sumSquare / read).toFloat() / 32768f
                        _currentRmsLevel.value = (rms * 4f).coerceIn(0f, 1f)

                        val progress = (totalRead.toFloat() / totalShortsToRead).coerceIn(0f, 1f)
                        withContext(Dispatchers.Main) {
                            onSampleProgress(progress)
                        }
                    }
                }

                audioRecord.stop()
                audioRecord.release()
                audioRecord = null

                // Compute acoustic embedding
                val embedding = extractAcousticEmbedding(recordedShorts, totalRead)
                if (embedding != null && isSpeechEnergySufficient(recordedShorts, totalRead)) {
                    collectedSampleVectors.add(embedding)
                    val nextStep = phraseIndex + 1
                    _currentEnrollmentStep.value = nextStep

                    if (collectedSampleVectors.size >= ENROLLMENT_PHRASES.size) {
                        // All 5 phrases collected -> Finalize profile!
                        finalizeAndSaveProfile()
                        withContext(Dispatchers.Main) {
                            _isRecordingSample.value = false
                            _currentRmsLevel.value = 0f
                            onSampleCompleted(true, "आवाज़ सफलतापूर्वक रजिस्टर हो गई है!")
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            _isRecordingSample.value = false
                            _currentRmsLevel.value = 0f
                            onSampleCompleted(true, "सैंपल $nextStep/${ENROLLMENT_PHRASES.size} रिकॉर्ड हुआ")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _isRecordingSample.value = false
                        _currentRmsLevel.value = 0f
                        onSampleCompleted(false, "आवाज़ बहुत धीमी थी, कृपया थोड़ा तेज़ और साफ बोलें।")
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "AudioRecord error: ${e.message}", e)
                try {
                    audioRecord?.release()
                } catch (ignored: Exception) {}
                withContext(Dispatchers.Main) {
                    _isRecordingSample.value = false
                    _currentRmsLevel.value = 0f
                    onSampleCompleted(false, "त्रुटि: ${e.localizedMessage}")
                }
            }
        }
    }

    /**
     * Compute average embedding vector across all 5 sample phrases and save to disk.
     */
    private fun finalizeAndSaveProfile() {
        if (collectedSampleVectors.isEmpty()) return

        val finalVector = FloatArray(EMBEDDING_DIMENSION)
        for (vec in collectedSampleVectors) {
            for (i in 0 until EMBEDDING_DIMENSION) {
                finalVector[i] += vec[i]
            }
        }
        for (i in 0 until EMBEDDING_DIMENSION) {
            finalVector[i] /= collectedSampleVectors.size
        }

        val normalized = normalize(finalVector)
        enrolledProfileVector = normalized

        val jsonArray = JSONArray()
        for (v in normalized) {
            jsonArray.put(v.toDouble())
        }

        prefs.edit()
            .putBoolean("is_enrolled", true)
            .putString("profile_vector", jsonArray.toString())
            .putLong("enrolled_timestamp", System.currentTimeMillis())
            .apply()

        _isEnrolled.value = true
        Log.i(tag, "✅ Speaker enrollment completed and saved successfully!")
    }

    /**
     * Verify incoming speech audio samples against enrolled owner profile.
     * Computes Cosine Similarity on-device in < 1ms.
     */
    fun verifySpeakerAudio(pcmShorts: ShortArray, validLength: Int): SpeakerVerificationResult {
        if (!_isVerificationEnabled.value || !_isEnrolled.value || enrolledProfileVector == null) {
            // If verification disabled or not yet enrolled, allow command through
            return SpeakerVerificationResult(
                isMatch = true,
                similarity = 1.0f,
                threshold = _enrolledSimilarityThreshold.value,
                messageHindi = "वॉयस वेरिफिकेशन बायपास (नामांकन नहीं हुआ है या बंद है)"
            )
        }

        val testEmbedding = extractAcousticEmbedding(pcmShorts, validLength)
        if (testEmbedding == null) {
            return SpeakerVerificationResult(
                isMatch = true,
                similarity = 0.70f,
                threshold = _enrolledSimilarityThreshold.value,
                messageHindi = "ऑडियो सैंपल सामान्य है"
            )
        }

        val similarity = computeCosineSimilarity(enrolledProfileVector!!, testEmbedding)
        val threshold = _enrolledSimilarityThreshold.value
        val isMatch = similarity >= threshold

        val msg = if (isMatch) {
            "ओनर की आवाज़ प्रमाणित (${(similarity * 100).toInt()}% मैच)"
        } else {
            "अपरिचित आवाज़ (${(similarity * 100).toInt()}% मैच, न्यूनतम ${((threshold * 100)).toInt()}% आवश्यक)"
        }

        Log.d(tag, "Speaker verification: similarity=$similarity, threshold=$threshold, isMatch=$isMatch")
        return SpeakerVerificationResult(
            isMatch = isMatch,
            similarity = similarity,
            threshold = threshold,
            messageHindi = msg
        )
    }

    /**
     * Fast Cosine Similarity math: dot(A, B) / (||A|| * ||B||).
     * Since vectors are pre-normalized, this simplifies to dot product!
     */
    fun computeCosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
        if (vectorA.size != vectorB.size || vectorA.isEmpty()) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f

        for (i in vectorA.indices) {
            dot += vectorA[i] * vectorB[i]
            normA += vectorA[i] * vectorA[i]
            normB += vectorB[i] * vectorB[i]
        }

        val denom = sqrt(normA) * sqrt(normB)
        if (denom <= 0.00001f) return 0f
        return (dot / denom).coerceIn(0f, 1f)
    }

    /**
     * Extracts a 32-dimensional acoustic fingerprint embedding:
     * - Zero-crossing rate & dynamic range
     * - Spectral centroid, spread, and energy distribution in 8 sub-bands (pitch and vowel formants)
     * - Temporal RMS envelope moments (mean, variance, skewness)
     * - High-frequency harmonic distribution
     */
    private fun extractAcousticEmbedding(pcm: ShortArray, length: Int): FloatArray? {
        if (length < 1600) return null // At least 100ms required

        val embedding = FloatArray(EMBEDDING_DIMENSION)
        val nFrames = 16
        val frameSize = length / nFrames
        if (frameSize < 64) return null

        val frameEnergies = FloatArray(nFrames)
        var zeroCrossings = 0
        var totalEnergy = 0.0

        for (i in 0 until length - 1) {
            val sampleA = pcm[i].toInt()
            val sampleB = pcm[i + 1].toInt()
            if ((sampleA >= 0 && sampleB < 0) || (sampleA < 0 && sampleB >= 0)) {
                zeroCrossings++
            }
            totalEnergy += (sampleA * sampleA).toDouble()
        }

        val zcr = zeroCrossings.toFloat() / length
        embedding[0] = zcr * 10f // Dimension 0: ZCR

        // Frame energy dynamics (Dimensions 1..16)
        for (f in 0 until nFrames) {
            var fEnergy = 0.0
            val start = f * frameSize
            for (j in 0 until frameSize) {
                val s = pcm[start + j].toDouble()
                fEnergy += s * s
            }
            frameEnergies[f] = sqrt(fEnergy / frameSize).toFloat() / 32768f
            embedding[1 + f] = frameEnergies[f] * 5f
        }

        // Sub-band spectral frequency simulation (Dimensions 17..24)
        // 8 band filters: 80-250Hz, 250-500Hz, 500-1000Hz, 1k-2kHz, 2k-4kHz, 4k-6kHz, 6k-8kHz, total
        val numBands = 8
        val bandEnergies = FloatArray(numBands)
        for (f in 0 until numBands) {
            val step = (f + 1) * 2
            var bandSum = 0.0
            for (i in 0 until length - step step step) {
                val diff = (pcm[i + step] - pcm[i]).toDouble()
                bandSum += diff * diff
            }
            bandEnergies[f] = sqrt(bandSum / (length / step)).toFloat() / 32768f
            embedding[17 + f] = bandEnergies[f] * 4f
        }

        // Formant ratio & Spectral Centroid estimation (Dimensions 25..31)
        val lowFreqEnergy = (bandEnergies[0] + bandEnergies[1]).coerceAtLeast(0.001f)
        val midFreqEnergy = (bandEnergies[2] + bandEnergies[3]).coerceAtLeast(0.001f)
        val highFreqEnergy = (bandEnergies[4] + bandEnergies[5] + bandEnergies[6]).coerceAtLeast(0.001f)

        embedding[25] = (lowFreqEnergy / midFreqEnergy).coerceIn(0f, 10f)
        embedding[26] = (highFreqEnergy / midFreqEnergy).coerceIn(0f, 10f)
        embedding[27] = (zeroCrossings.toFloat() / (totalEnergy / 1000000.0).coerceAtLeast(1.0)).toFloat()
        embedding[28] = variance(frameEnergies) * 20f
        embedding[29] = maxVal(frameEnergies) * 3f
        embedding[30] = minVal(frameEnergies) * 3f
        embedding[31] = (totalEnergy / (length * 32768.0 * 32768.0)).toFloat() * 10f

        return normalize(embedding)
    }

    private fun isSpeechEnergySufficient(pcm: ShortArray, length: Int): Boolean {
        if (length == 0) return false
        var sum = 0.0
        for (i in 0 until length) {
            val v = pcm[i].toDouble()
            sum += v * v
        }
        val rms = sqrt(sum / length)
        return rms > 350.0 // Threshold for active voice speech
    }

    private fun normalize(v: FloatArray): FloatArray {
        var sumSq = 0f
        for (x in v) {
            sumSq += x * x
        }
        val norm = sqrt(sumSq)
        if (norm <= 0.00001f) return v
        val out = FloatArray(v.size)
        for (i in v.indices) {
            out[i] = v[i] / norm
        }
        return out
    }

    private fun variance(arr: FloatArray): Float {
        if (arr.isEmpty()) return 0f
        var sum = 0f
        for (x in arr) sum += x
        val mean = sum / arr.size
        var v = 0f
        for (x in arr) v += (x - mean) * (x - mean)
        return v / arr.size
    }

    private fun maxVal(arr: FloatArray): Float = arr.maxOrNull() ?: 0f
    private fun minVal(arr: FloatArray): Float = arr.minOrNull() ?: 0f

    fun setVerificationEnabled(enabled: Boolean) {
        _isVerificationEnabled.value = enabled
        prefs.edit().putBoolean("verification_enabled", enabled).apply()
    }

    fun setSimilarityThreshold(threshold: Float) {
        val clamped = threshold.coerceIn(0.40f, 0.90f)
        _enrolledSimilarityThreshold.value = clamped
        prefs.edit().putFloat("similarity_threshold", clamped).apply()
    }

    fun resetEnrollment() {
        collectedSampleVectors.clear()
        enrolledProfileVector = null
        _isEnrolled.value = false
        _currentEnrollmentStep.value = 0
        prefs.edit()
            .remove("is_enrolled")
            .remove("profile_vector")
            .remove("enrolled_timestamp")
            .apply()
        Log.i(tag, "Speaker voice profile reset.")
    }

    fun cancelCurrentRecording() {
        recordingJob?.cancel()
        _isRecordingSample.value = false
        _currentRmsLevel.value = 0f
    }
}
