/*
 * Copyright (c) Kuba Szczodrzyński 2020-1-6.
 */

package eu.mikus.edziennik.ui.widgets.timetable;

import android.content.Intent;
import android.widget.RemoteViewsService;

public class WidgetTimetableService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return (new WidgetTimetableFactory(this.getApplicationContext(), intent));
    }
}
