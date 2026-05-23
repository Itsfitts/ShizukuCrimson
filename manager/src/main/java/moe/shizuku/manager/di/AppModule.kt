package moe.shizuku.manager.di

import moe.shizuku.manager.utils.ActivityLogManager
import moe.shizuku.manager.utils.AppContextManager
import moe.shizuku.manager.update.UpdateManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    single { ActivityLogManager }
    single { AppContextManager }
    single { UpdateManager(androidContext()) }
}
