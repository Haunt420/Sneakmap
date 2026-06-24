package com.example.analysis

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream

data class HostResult(
    val ipAddress: String,
    val ports: List<PortResult>
)

data class PortResult(
    val portNumber: Int,
    val protocol: String,
    val state: String,
    val service: String?
)

class XmlAnalysisParser {

    @Throws(XmlPullParserException::class, IOException::class)
    fun parse(inputStream: InputStream): List<HostResult> {
        inputStream.use {
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(it, null)
            parser.nextTag()
            return readRoot(parser)
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readRoot(parser: XmlPullParser): List<HostResult> {
        val hosts = mutableListOf<HostResult>()
        // The root tag is typically "nmaprun" according to the provided structure.
        // We will read the root tag but strictly focus on extracting <host> blocks.
        parser.require(XmlPullParser.START_TAG, null, "nmaprun")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            if (parser.name == "host") {
                hosts.add(readHost(parser))
            } else {
                skip(parser)
            }
        }
        return hosts
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readHost(parser: XmlPullParser): HostResult {
        parser.require(XmlPullParser.START_TAG, null, "host")
        var ipAddress = ""
        val ports = mutableListOf<PortResult>()

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            when (parser.name) {
                "address" -> {
                    val addr = parser.getAttributeValue(null, "addr")
                    if (addr != null) {
                        ipAddress = addr
                    }
                    skip(parser)
                }
                "ports" -> {
                    ports.addAll(readPorts(parser))
                }
                else -> skip(parser)
            }
        }
        return HostResult(ipAddress, ports)
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readPorts(parser: XmlPullParser): List<PortResult> {
        val ports = mutableListOf<PortResult>()
        parser.require(XmlPullParser.START_TAG, null, "ports")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            if (parser.name == "port") {
                ports.add(readPort(parser))
            } else {
                skip(parser)
            }
        }
        return ports
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readPort(parser: XmlPullParser): PortResult {
        parser.require(XmlPullParser.START_TAG, null, "port")
        val protocol = parser.getAttributeValue(null, "protocol") ?: ""
        val portidStr = parser.getAttributeValue(null, "portid")
        val portNumber = portidStr?.toIntOrNull() ?: 0

        var state = ""
        var service: String? = null

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            when (parser.name) {
                "state" -> {
                    val s = parser.getAttributeValue(null, "state")
                    if (s != null) {
                        state = s
                    }
                    skip(parser)
                }
                "service" -> {
                    val s = parser.getAttributeValue(null, "name")
                    if (s != null) {
                        service = s
                    }
                    skip(parser)
                }
                else -> skip(parser)
            }
        }
        return PortResult(portNumber, protocol, state, service)
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }
}
