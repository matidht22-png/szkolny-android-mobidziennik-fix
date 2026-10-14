/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-11.
 */

package pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.data.web

import org.jsoup.Jsoup
import pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.DataMobidziennik
import pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.ENDPOINT_MOBIDZIENNIK_WEB_NOTICES
import pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.data.MobidziennikWeb
import pl.szczodrzynski.edziennik.data.db.entity.Event
import pl.szczodrzynski.edziennik.data.db.entity.Metadata
import pl.szczodrzynski.edziennik.data.db.entity.SYNC_ALWAYS
import pl.szczodrzynski.edziennik.data.db.enums.MetadataType
import pl.szczodrzynski.edziennik.ext.fixWhiteSpaces
import pl.szczodrzynski.edziennik.utils.Utils.crc16
import pl.szczodrzynski.edziennik.utils.models.Date

class MobidziennikWebNotices(override val data: DataMobidziennik,
                             override val lastSync: Long?,
                             val onSuccess: (endpointId: Int) -> Unit
) : MobidziennikWeb(data, lastSync) {
    companion object {
        private const val TAG = "MobidziennikWebNotices"

        // /mobile/zachowanie renders each note as a block of <p><b>Label:</b> value</p>.
        // One regex captures a whole note (type + content + category + written-date).
        private val NOTE = Regex(
                "<b>Uwaga:</b>\\s*(.+?)\\s*</p>" +
                        "\\s*<p><b>Treść:</b>\\s*(.*?)</p>" +
                        "\\s*<p><b>Kategoria:</b>\\s*(.*?)</p>" +
                        "\\s*<p><b>Wpisano:</b>\\s*(?:\\w+,\\s*)?([0-9]+)\\s+(\\w+)\\s+([0-9]{4})",
                RegexOption.DOT_MATCHES_ALL
        )

        private val MONTHS = mapOf(
                "stycznia" to 1, "lutego" to 2, "marca" to 3, "kwietnia" to 4,
                "maja" to 5, "czerwca" to 6, "lipca" to 7, "sierpnia" to 8,
                "września" to 9, "października" to 10, "listopada" to 11, "grudnia" to 12
        )
    }

    init {
        webGet(TAG, "/mobile/zachowanie") { text ->
            MobidziennikLuckyNumberExtractor(data, text)

            for (m in NOTE.findAll(text)) {
                val type = m.groupValues[1].trim().fixWhiteSpaces()
                val content = Jsoup.parse(m.groupValues[2]).text().fixWhiteSpaces()
                val category = m.groupValues[3].trim().fixWhiteSpaces()
                val day = m.groupValues[4].toIntOrNull() ?: continue
                val month = MONTHS[m.groupValues[5]] ?: continue
                val year = m.groupValues[6].toIntOrNull() ?: continue

                val topic = buildString {
                    append("Uwaga: $type")
                    if (category.isNotEmpty()) append("\nKategoria: $category")
                    if (content.isNotEmpty()) append("\n$content")
                }

                val eventObject = Event(
                        profileId = profileId,
                        id = crc16(topic.toByteArray()).toLong(),
                        date = Date(year, month, day), time = null,
                        topic = topic,
                        color = null,
                        type = Event.TYPE_INFORMATION,
                        teacherId = -1,
                        subjectId = -1,
                        teamId = data.teamClass?.id ?: -1
                )
                eventObject.isDownloaded = false

                data.eventList.add(eventObject)
                data.metadataList.add(
                        Metadata(
                                profileId,
                                MetadataType.EVENT,
                                eventObject.id,
                                profile?.empty ?: false,
                                profile?.empty ?: false
                        ))
            }

            data.setSyncNext(ENDPOINT_MOBIDZIENNIK_WEB_NOTICES, SYNC_ALWAYS)
            onSuccess(ENDPOINT_MOBIDZIENNIK_WEB_NOTICES)
        }
    }
}
