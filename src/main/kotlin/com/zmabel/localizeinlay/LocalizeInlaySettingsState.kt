package com.zmabel.localizeinlay

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "NumericInlaySettings", storages = [Storage("numeric-inlay.xml")])
@Service(Service.Level.APP)
class LocalizeInlaySettingsState : PersistentStateComponent<LocalizeInlaySettingsState.State> {

    data class State(
        var jsonPath: String? = null,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var jsonPath: String?
        get() = state.jsonPath
        set(value) {
            state.jsonPath = value
        }

    companion object {
        @JvmStatic
        fun getInstance(): LocalizeInlaySettingsState {
            // application-level service
            return ApplicationManager
                .getApplication()
                .getService(LocalizeInlaySettingsState::class.java)
        }
    }
}

