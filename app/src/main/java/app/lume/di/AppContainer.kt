package app.lume.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.room.Room
import app.lume.data.DraftRepository
import app.lume.data.LocalDraftRepository
import app.lume.data.LocalPresetRepository
import app.lume.data.PresetRepository
import app.lume.data.auth.AuthRepository
import app.lume.data.auth.LocalGuestAuthRepository
import app.lume.data.local.LumeDatabase
import app.lume.data.local.appDataStore
import app.lume.data.pro.EntitlementRepository
import app.lume.data.pro.LocalEntitlementRepository
import app.lume.ui.account.AccountViewModel
import app.lume.ui.editor.EditorViewModel
import app.lume.ui.library.LibraryViewModel
import app.lume.ui.paywall.PaywallViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Raiz de composição manual (sem framework de DI na Fase 0).
 * Criado uma vez na [app.lume.LumeApp] e acessado pela MainActivity.
 *
 * Quando (e se) a complexidade justificar, Hilt/Koin entram aqui sem
 * tocar nas telas: as interfaces já isolam cada dependência.
 */
class AppContainer(private val context: Context) {

    val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database: LumeDatabase by lazy {
        Room.databaseBuilder(context, LumeDatabase::class.java, "lume.db")
            // Fase 0, pré-lançamento: sem usuários reais, migração
            // destrutiva é aceitável. ANTES da Play Store, escrever
            // migrações explícitas e remover esta linha.
            .fallbackToDestructiveMigration()
            .build()
    }

    val authRepository: AuthRepository by lazy {
        LocalGuestAuthRepository(context.appDataStore, applicationScope)
    }

    val entitlementRepository: EntitlementRepository by lazy {
        LocalEntitlementRepository(context.appDataStore, applicationScope)
    }

    val presetRepository: PresetRepository by lazy {
        LocalPresetRepository(database.presetDao(), entitlementRepository)
    }

    val draftRepository: DraftRepository by lazy {
        LocalDraftRepository(database.draftDao())
    }

    fun editorViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
        EditorViewModel(
            appContext = context.applicationContext,
            presetRepository = presetRepository,
            draftRepository = draftRepository,
            entitlementRepository = entitlementRepository
        )
    }

    fun libraryViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
        LibraryViewModel(
            presetRepository = presetRepository,
            draftRepository = draftRepository,
            entitlementRepository = entitlementRepository
        )
    }

    fun accountViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
        AccountViewModel(
            authRepository = authRepository,
            presetRepository = presetRepository,
            draftRepository = draftRepository
        )
    }

    fun paywallViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
        PaywallViewModel(entitlementRepository = entitlementRepository)
    }

    // Fábrica genérica mínima sobre ViewModelProvider.Factory, sem
    // depender de CreationExtras (padrão simples e explícito).
    private fun <T : ViewModel> viewModelFactory(create: () -> T): ViewModelProvider.Factory {
        return object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <V : ViewModel> create(modelClass: Class<V>, extras: CreationExtras): V {
                return create() as V
            }
        }
    }
}
