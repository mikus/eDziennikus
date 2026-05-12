/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-27.
 */

package eu.mikus.edziennik.ui.notes

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import eu.mikus.edziennik.App
import eu.mikus.edziennik.Binding
import eu.mikus.edziennik.data.db.entity.Note
import eu.mikus.edziennik.databinding.NoteListCategoryItemBinding
import eu.mikus.edziennik.ext.resolveDrawable
import eu.mikus.edziennik.ui.grades.viewholder.BindableViewHolder

class NoteCategoryViewHolder(
    inflater: LayoutInflater,
    parent: ViewGroup,
    val b: NoteListCategoryItemBinding = NoteListCategoryItemBinding.inflate(
        inflater,
        parent,
        false,
    ),
) : RecyclerView.ViewHolder(b.root), BindableViewHolder<Note, NoteListAdapter> {
    companion object {
        private const val TAG = "NoteCategoryViewHolder"
    }

    override fun onBind(
        activity: AppCompatActivity,
        app: App,
        item: Note,
        position: Int,
        adapter: NoteListAdapter,
    ) {
        val manager = app.noteManager
        val title = b.root as? TextView ?: return
        val ownerType = item.ownerType ?: return

        title.setText(manager.getOwnerTypeText(ownerType))
        title.setCompoundDrawables(
            manager.getOwnerTypeImage(ownerType).resolveDrawable(activity),
            null,
            null,
            null,
        )
        Binding.drawableLeftAutoSize(title, enable = true)
    }
}
