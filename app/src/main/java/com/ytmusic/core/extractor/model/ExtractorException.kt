package com.ytmusic.core.extractor.model

sealed class ExtractorException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class NetworkException(message: String, cause: Throwable? = null) :
        ExtractorException(message, cause)

    class ContentUnavailableException(val videoId: String, message: String) :
        ExtractorException("Content unavailable for video: $videoId. $message")

    class AgeRestrictedException(val videoId: String) :
        ExtractorException("Video $videoId is age restricted and requires authentication.")

    class GeoRestrictedException(val videoId: String, val region: String? = null) :
        ExtractorException("Video $videoId is not available in the current region.")

    class ParsingException(message: String, cause: Throwable? = null) :
        ExtractorException(message, cause)

    class ProcessExecutionException(val exitCode: Int, val stderr: String) :
        ExtractorException("Extractor process exited with code $exitCode: $stderr")

    class AllExtractorsFailedException(val videoId: String, val errors: List<Throwable>) :
        ExtractorException(
            "All extractor strategies failed for video $videoId:\n" +
                errors.joinToString(separator = "\n") { "- ${it.message}" }
        )
}
