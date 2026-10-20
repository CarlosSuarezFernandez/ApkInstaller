package com.carlos.apkinstaller

import android.app.Application
import com.carlos.apkinstaller.data.ApkRepository
import com.carlos.apkinstaller.installer.InstallEvents
import com.carlos.apkinstaller.installer.Installer
import com.carlos.apkinstaller.installer.SessionInstaller
import com.carlos.apkinstaller.ui.MainViewModel
import com.carlos.apkinstaller.ui.theme.ThemePreferences
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { ApkRepository(androidContext()) }
    single { InstallEvents() }
    single<Installer> { SessionInstaller(androidContext()) }
    single { ThemePreferences(androidContext()) }
    viewModel { MainViewModel(get(), get(), get()) }
}

class ApkInstallerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ApkInstallerApp)
            modules(appModule)
        }
    }
}
