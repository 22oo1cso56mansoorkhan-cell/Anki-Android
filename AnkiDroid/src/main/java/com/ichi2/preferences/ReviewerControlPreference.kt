/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.preferences

import android.content.Context
import android.util.AttributeSet
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.GestureProcessor
import com.ichi2.anki.dialogs.CardSideSelectionDialog
import com.ichi2.anki.dialogs.WarningDisplay
import com.ichi2.anki.preferences.allPreferences
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.ext.usingStyledAttributes

open class ReviewerControlPreference : ControlPreference {
    protected open var side: CardSide? = null

    @Suppress("unused")
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, androidx.preference.R.attr.dialogPreferenceStyle)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
    ) : this(context, attrs, defStyleAttr, android.R.attr.dialogPreferenceStyle)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        side =
            context.usingStyledAttributes(attrs, R.styleable.ReviewerControlPreference) {
                val value = getInt(R.styleable.ReviewerControlPreference_cardSide, -1)
                when (value) {
                    0 -> CardSide.QUESTION
                    1 -> CardSide.ANSWER
                    2 -> CardSide.BOTH
                    else -> null
                }
            }
    }

    override fun getSummary(): CharSequence =
        if (side != null) {
            getMappableBindings().joinToString(", ") { it.binding.toDisplayString(context) }
        } else {
            super.getSummary()
        }

    override val areGesturesEnabled: Boolean
        get() = Prefs.isNewStudyScreenEnabled || sharedPreferences?.getBoolean(GestureProcessor.PREF_KEY, false) ?: false

    override fun getMappableBindings(): List<ReviewerBinding> = ReviewerBinding.fromPreferenceString(value).toList()

    @Suppress("UNCHECKED_CAST")
    override fun getRelatedPreferences(): List<ReviewerControlPreference> {
        val allPrefs =
            preferenceManager.preferenceScreen
                .allPreferences()
                .filter { it::class == ReviewerControlPreference::class } as List<ReviewerControlPreference>

        val answerActionKeys =
            setOf(
                context.getString(R.string.answer_again_command_key),
                context.getString(R.string.answer_hard_command_key),
                context.getString(R.string.answer_good_command_key),
                context.getString(R.string.answer_easy_command_key),
            )

        // For answer actions: check against all other answer actions (excluding itself)
        if (key in answerActionKeys) {
            return allPrefs.filter { it.key in answerActionKeys && it.key != key }
        }

        // For all other preferences (including show answer): return all preferences
        return allPrefs
    }

    override fun warnIfUsed(
        binding: Binding,
        warningDisplay: WarningDisplay,
    ): Boolean {
        val bindingPreference = getPreferenceAssignedTo(binding) ?: return false
        if (bindingPreference == this) return false

        val answerActionKeys =
            setOf(
                context.getString(R.string.answer_again_command_key),
                context.getString(R.string.answer_hard_command_key),
                context.getString(R.string.answer_good_command_key),
                context.getString(R.string.answer_easy_command_key),
            )

        val showAnswerKey = context.getString(R.string.show_answer_command_key)

        val isCurrentAnswer = key in answerActionKeys
        val isCurrentShowAnswer = key == showAnswerKey
        val isOtherAnswer = bindingPreference.key in answerActionKeys
        val isOtherShowAnswer = bindingPreference.key == showAnswerKey

        // Allow sharing if either preference is Show answer
        if (isCurrentShowAnswer || isOtherShowAnswer) {
            return false
        }

        // Only show warning when answer action conflicts with another answer action
        if (isCurrentAnswer && isOtherAnswer) {
            val actionTitle = bindingPreference.title ?: ""
            val warning = context.getString(R.string.bindings_already_bound, actionTitle)
            warningDisplay.setWarning(warning)
            return true
        }

        // No warning for all other cases
        return false
    }

    fun interface OnBindingSelectedListener {
        /**
         * Called when a binding is selected, before the side is set. This allows listeners
         * to respond before the value is set, and potentially override it.
         *
         * @param binding The selected binding.
         *
         * @return True if the listener has consumed the event, false otherwise.
         */
        fun onBindingSelected(binding: Binding): Boolean
    }

    private var onBindingSelectedListener: OnBindingSelectedListener? = null

    fun setOnBindingSelectedListener(listener: OnBindingSelectedListener?) {
        onBindingSelectedListener = listener
    }

    override fun onKeySelected(binding: Binding) = setBinding(binding)

    override fun onGestureSelected(binding: Binding) = setBinding(binding)

    override fun onAxisSelected(binding: Binding) = setBinding(binding)

    fun setBinding(binding: Binding) {
        if (onBindingSelectedListener?.onBindingSelected(binding) == true) return
        selectSide { side ->
            addBinding(binding, side)
        }
    }

    fun addBinding(
        binding: Binding,
        side: CardSide,
    ) {
        val newBinding = ReviewerBinding(binding, side)
        getPreferenceAssignedTo(binding)?.removeMappableBinding(newBinding)
        val bindings = ReviewerBinding.fromPreferenceString(value).toMutableList()
        bindings.add(newBinding)
        value = bindings.toPreferenceString()
    }

    /**
     * If this command can be executed on a single side, execute the callback on this side.
     * Otherwise, ask the user to select one or two side(s) and execute the callback on them.
     */
    private fun selectSide(callback: (c: CardSide) -> Unit) {
        val cardSide = side
        if (cardSide != null) {
            callback(cardSide)
        } else {
            CardSideSelectionDialog.displayInstance(context, callback)
        }
    }
}
