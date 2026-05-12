/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-5.
 */

package eu.mikus.edziennik.data.api.edziennik.template.data.web

import eu.mikus.edziennik.data.api.edziennik.template.DataTemplate
import eu.mikus.edziennik.data.api.edziennik.template.ENDPOINT_TEMPLATE_WEB_SAMPLE_2
import eu.mikus.edziennik.data.api.edziennik.template.data.TemplateWeb
import eu.mikus.edziennik.data.db.entity.SYNC_ALWAYS
import eu.mikus.edziennik.data.db.enums.FeatureType
import eu.mikus.edziennik.ext.DAY

class TemplateWebSample2(override val data: DataTemplate,
                         override val lastSync: Long?,
                         val onSuccess: (endpointId: Int) -> Unit
) : TemplateWeb(data, lastSync) {
    companion object {
        private const val TAG = "TemplateWebSample2"
    }

    init {
        webGet(TAG, "/api/v3/getData.php") {
            // here you can access and update any fields of the `data` object

            // ================
            // schedule a sync:

            // not sooner than two days later
            data.setSyncNext(ENDPOINT_TEMPLATE_WEB_SAMPLE_2, 2 * DAY)
            // in two days OR on explicit "grades" sync
            data.setSyncNext(ENDPOINT_TEMPLATE_WEB_SAMPLE_2, 2 * DAY, FeatureType.GRADES)
            // always, in every sync
            data.setSyncNext(ENDPOINT_TEMPLATE_WEB_SAMPLE_2, SYNC_ALWAYS)

            onSuccess(ENDPOINT_TEMPLATE_WEB_SAMPLE_2)
        }
    }
}
