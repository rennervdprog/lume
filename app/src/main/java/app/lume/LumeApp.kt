package app.lume

import android.app.Application
import app.lume.di.AppContainer

class LumeApp : Application() {
    val container: AppContainer by lazy { AppContainer(applicationContext) }
}
