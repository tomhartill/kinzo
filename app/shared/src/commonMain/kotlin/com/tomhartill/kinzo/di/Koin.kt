package com.tomhartill.kinzo.di

import com.tomhartill.kinzo.data.CatalogueImporter
import com.tomhartill.kinzo.data.CatalogueRepository
import com.tomhartill.kinzo.data.DriverFactory
import com.tomhartill.kinzo.data.SettingsRepository
import com.tomhartill.kinzo.data.WorkoutRepository
import com.tomhartill.kinzo.data.createDatabase
import com.tomhartill.kinzo.domain.RestTimer
import com.tomhartill.kinzo.resources.Res
import com.tomhartill.kinzo.ui.AppViewModel
import com.tomhartill.kinzo.ui.screens.exercises.ExerciseBrowserViewModel
import com.tomhartill.kinzo.ui.screens.exercises.ExerciseDetailViewModel
import com.tomhartill.kinzo.ui.screens.history.HistoryViewModel
import com.tomhartill.kinzo.ui.screens.history.WorkoutDetailViewModel
import com.tomhartill.kinzo.ui.screens.profile.ProfileViewModel
import com.tomhartill.kinzo.ui.screens.today.TodayViewModel
import com.tomhartill.kinzo.ui.screens.workout.ActiveWorkoutViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import kotlin.time.Clock

@OptIn(ExperimentalResourceApi::class)
internal fun dataModule(driverFactory: DriverFactory): Module = module {
    single { driverFactory.createDatabase() }
    single<Clock> { Clock.System }
    single { CatalogueRepository(get()) }
    single { SettingsRepository(get()) }
    single { WorkoutRepository(get(), get()) }
    // One rest timer for the whole app, so it keeps counting when you leave the workout.
    single { RestTimer(get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    single {
        CatalogueImporter(get(), get()) { Res.readBytes(CatalogueImporter.RESOURCE_PATH).decodeToString() }
    }
}

internal val viewModelModule: Module = module {
    viewModelOf(::AppViewModel)
    viewModelOf(::TodayViewModel)
    viewModelOf(::HistoryViewModel)
    viewModelOf(::ExerciseBrowserViewModel)
    viewModelOf(::ProfileViewModel)
    viewModelOf(::ActiveWorkoutViewModel)
    viewModel { params -> ExerciseDetailViewModel(params.get(), get(), get(), get()) }
    viewModel { params -> WorkoutDetailViewModel(params.get(), get(), get()) }
}

/** Called once at startup by each platform, which supplies its own SQLite driver. */
fun initKoin(driverFactory: DriverFactory) {
    startKoin {
        modules(dataModule(driverFactory), viewModelModule)
    }
}
