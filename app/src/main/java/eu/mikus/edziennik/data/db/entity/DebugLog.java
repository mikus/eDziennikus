/*
 * Copyright (c) Kacper Ziubryniewicz 2020-1-6
 */

package eu.mikus.edziennik.data.db.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import static eu.mikus.edziennik.utils.Utils.d;

@Entity(tableName = "debugLogs")
public class DebugLog {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String text;

    public DebugLog(String text) {
        d("DebugLog", text);
        this.text = text;
    }
}
