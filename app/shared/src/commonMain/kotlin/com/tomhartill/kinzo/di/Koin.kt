package com.tomhartill.kinzo.di

import com.tomhartill.kinzo.KinzoConfig
import com.tomhartill.kinzo.data.AccountRepository
import com.tomhartill.kinzo.data.CatalogueImporter
import com.tomhartill.kinzo.data.CatalogueRepository
import com.tomhartill.kinzo.data.DriverFactory
import com.tomhartill.kinzo.data.KinzoWorkoutsImporter
import com.tomhartill.kinzo.data.MesocycleRepository
import com.tomhartill.kinzo.data.ProgressPhotoRepository
import com.tomhartill.kinzo.data.RoutineRepository
import com.tomhartill.kinzo.data.SessionStore
import com.tomhartill.kinzo.data.SettingsRepository
import com.tomhartill.kinzo.data.SplitRepository
import com.tomhartill.kinzo.data.TrendRepository
import com.tomhartill.kinzo.data.WorkoutRepository
import com.tomhartill.kinzo.data.api.KinzoApi
import com.tomhartill.kinzo.data.createDatabase
import com.tomhartill.kinzo.domain.RestTimer
import com.tomhartill.kinzo.resources.Res
import com.tomhartill.kinzo.ui.AppViewModel
import com.tomhartill.kinzo.ui.screens.account.AccountViewModel
import com.tomhartill.kinzo.ui.screens.account.CodeSignInViewModel
import com.tomhartill.kinzo.ui.screens.account.SignInViewModel
import com.tomhartill.kinzo.ui.screens.account.UsernameViewModel
import com.tomhartill.kinzo.ui.screens.exercises.ExerciseBrowserViewModel
import com.tomhartill.kinzo.ui.screens.exercises.ExerciseDetailViewModel
import com.tomhartill.kinzo.ui.screens.exercises.MachineScanViewModel
import com.tomhartill.kinzo.ui.screens.friends.FriendsViewModel
import com.tomhartill.kinzo.ui.screens.friends.InviteViewModel
import com.tomhartill.kinzo.ui.screens.history.HistoryViewModel
import com.tomhartill.kinzo.ui.screens.history.WorkoutDetailViewModel
import com.tomhartill.kinzo.ui.screens.library.LibraryViewModel
import com.tomhartill.kinzo.ui.screens.library.MesocycleViewModel
import com.tomhartill.kinzo.ui.screens.library.RoutineDetailViewModel
import com.tomhartill.kinzo.ui.screens.library.RoutineEditorViewModel
import com.tomhartill.kinzo.ui.screens.profile.ProfileViewModel
import com.tomhartill.kinzo.ui.screens.progress.ProgressPhotosViewModel
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
    single { TrendRepository(get(), get()) }
    single { RoutineRepository(get(), get()) }
    single { SplitRepository(get(), get()) }
    single { MesocycleRepository(get(), get()) }
    single { ProgressPhotoRepository(get(), get(), get(), get()) }
    // One rest timer for the whole app, so it keeps counting when you leave the workout.
    single { RestTimer(get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    single {
        CatalogueImporter(get(), get()) { Res.readBytes(CatalogueImporter.RESOURCE_PATH).decodeToString() }
    }
    single {
        KinzoWorkoutsImporter(get(), get()) { Res.readBytes(KinzoWorkoutsImporter.RESOURCE_PATH).decodeToString() }
    }

    // Accounts. The server address and Google client ID are baked in at build time.
    single { SessionStore(get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    single {
        val session = get<SessionStore>()
        KinzoApi(KinzoConfig.API_URL, token = { session.token() }, onSignedOut = { session.clear() })
    }
    single { AccountRepository(get(), get(), get(), get(), KinzoConfig.GOOGLE_WEB_CLIENT_ID) }
}

internal val viewModelModule: Module = module {
    viewModelOf(::AppViewModel)
    viewModelOf(::TodayViewModel)
    viewModelOf(::HistoryViewModel)
    viewModelOf(::ExerciseBrowserViewModel)
    viewModelOf(::MachineScanViewModel)
    viewModelOf(::ProfileViewModel)
    viewModelOf(::ProgressPhotosViewModel)
    // No parameter for the workout in progress; a workout ID to edit a finished one.
    viewModel { params -> ActiveWorkoutViewModel(params.getOrNull(), get(), get(), get()) }
    viewModelOf(::LibraryViewModel)
    viewModelOf(::SignInViewModel)
    viewModelOf(::UsernameViewModel)
    viewModelOf(::AccountViewModel)
    viewModelOf(::FriendsViewModel)
    viewModel { params -> InviteViewModel(params.get(), get(), get()) }
    viewModel { params -> CodeSignInViewModel(params.get(), get()) }
    viewModel { params -> ExerciseDetailViewModel(params.get(), get(), get(), get()) }
    viewModel { params -> WorkoutDetailViewModel(params.get(), get(), get()) }
    viewModel { params -> RoutineDetailViewModel(params.get(), get(), get(), get(), get()) }
    viewModel { params -> MesocycleViewModel(params.get(), get(), get()) }
    viewModel { params -> RoutineEditorViewModel(params.get(), get(), get(), get(), get(), get()) }
}

/**
 * Called once at startup by each platform, which supplies its own SQLite driver and a
 * [platformModule] with its sign-in and share sheet ([com.tomhartill.kinzo.account.BrowserSignIn],
 * [com.tomhartill.kinzo.account.NativeGoogleSignIn], [com.tomhartill.kinzo.account.Sharer]) and
 * its camera and photo storage ([com.tomhartill.kinzo.photos.Camera], [com.tomhartill.kinzo.photos.PhotoFiles]).
 */
fun initKoin(driverFactory: DriverFactory, platformModule: Module) {
    startKoin {
        modules(dataModule(driverFactory), viewModelModule, platformModule)
    }
}
