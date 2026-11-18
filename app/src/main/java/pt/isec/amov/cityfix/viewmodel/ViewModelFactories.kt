package pt.isec.amov.cityfix.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import pt.isec.amov.cityfix.data.CityFixApp
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.utils.Strings

/*
 * Factories dos ViewModels (padrão ContactsViewModelFactory / EditContactViewModelFactory das aulas):
 * injetam os repositórios criados uma única vez no objeto Application.
 */

class AuthViewModelFactory(private val app: CityFixApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AuthViewModel(app.cloudRepository, app.localRepository) as T
}

class IncidentViewModelFactory(private val app: CityFixApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        IncidentViewModel(app.cloudRepository, app.localRepository, app.ticketNotifier, app.mediaRepository, Strings.from(app)) as T
}

class OperatorViewModelFactory(private val app: CityFixApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        OperatorViewModel(app.cloudRepository) as T
}

/** ViewModel com âmbito no ecrã de autenticação (login ou registo). */
class AuthFormViewModelFactory(private val app: CityFixApp, private val startInRegisterMode: Boolean) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AuthFormViewModel(app.cloudRepository, app.mediaRepository, startInRegisterMode, app.ticketNotifier, app.localRepository, Strings.from(app)) as T
}

/** ViewModel com âmbito no ecrã de nova ocorrência. */
class ReportIncidentViewModelFactory(private val app: CityFixApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ReportIncidentViewModel(app.cloudRepository, app.mediaRepository, Strings.from(app)) as T
}

/** ViewModel com âmbito no ecrã de edição de perfil. */
class EditProfileViewModelFactory(private val app: CityFixApp, private val user: CityFixUser) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        EditProfileViewModel(app.cloudRepository, app.mediaRepository, user, Strings.from(app)) as T
}
