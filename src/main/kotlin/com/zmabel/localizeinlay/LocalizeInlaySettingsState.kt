package com.zmabel.localizeinlay

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "LocalizeInlaySettings", storages = [Storage("localize-inlay.xml")])
@Service(Service.Level.APP)
class LocalizeInlaySettingsState : PersistentStateComponent<LocalizeInlaySettingsState.State> {

    data class State(
        var jsonPath: String? = null,
        var methodNames: String = "LocalUtils.GetString",
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
            // 当设置项的值改变时，重新刷新显示
            SnJsonConfigMatcher.resetCache()
        }

    var methodNames: String
        get() = state.methodNames
        set(value) {
            state.methodNames = value
            // 当设置项的值改变时，重新刷新显示
            SnJsonConfigMatcher.resetCache()
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

