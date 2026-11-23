package com.mcbane23.parentalcontrol

import android.app.Activity
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcbane23.parentalcontrol.data.AppRule
import com.mcbane23.parentalcontrol.data.AppUsage
import com.mcbane23.parentalcontrol.data.ChildRules
import com.mcbane23.parentalcontrol.data.DeviceRepository
import com.mcbane23.parentalcontrol.data.DeviceStatus
import com.mcbane23.parentalcontrol.data.Family
import com.mcbane23.parentalcontrol.data.FamilyRepository
import com.mcbane23.parentalcontrol.data.GoogleSignIn
import com.mcbane23.parentalcontrol.data.Member
import com.mcbane23.parentalcontrol.data.PairingCode
import com.mcbane23.parentalcontrol.data.Role
import com.mcbane23.parentalcontrol.data.UserProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Loading : Screen
    /** google-services.json has not been added to the build yet. */
    data object FirebaseMissing : Screen
    data object Welcome : Screen
    /** Signed in with Google but not in a family yet. */
    data object ParentSetup : Screen
    data object ChildPairing : Screen
    data class ParentHome(val family: Family, val myUid: String) : Screen
    /** A parent looking at one child: usage, limits and blocked apps. */
    data class ChildDetail(val family: Family, val myUid: String, val child: Member) : Screen
    data class ChildHome(val family: Family, val myUid: String) : Screen
}

data class UiState(
    val screen: Screen = Screen.Loading,
    val busy: Boolean = false,
    val error: String? = null,
    val members: List<Member> = emptyList(),
    val pairingCode: PairingCode? = null,
    /** On a parent's phone: the child being looked at. On a child's phone: this phone. */
    val childStatus: DeviceStatus? = null,
    val childApps: List<AppUsage> = emptyList(),
    val childRules: ChildRules = ChildRules(),
)

class AppViewModel(
    private val repo: FamilyRepository?,
    private val devices: DeviceRepository?,
) : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var membersJob: Job? = null
    private var childJob: Job? = null

    init {
        if (repo == null) {
            _state.value = UiState(screen = Screen.FirebaseMissing)
        } else {
            launchAction { routeForCurrentUser() }
        }
    }

    val suggestedName: String get() = repo?.accountName.orEmpty()

    fun signInAsParent(activity: Activity) = launchAction {
        val repo = requireRepo()
        try {
            repo.signInWithGoogle(GoogleSignIn.requestCredential(activity))
        } catch (e: GetCredentialCancellationException) {
            return@launchAction // The person closed the account picker.
        }
        routeForCurrentUser()
    }

    fun startChildPairing() {
        _state.update { it.copy(screen = Screen.ChildPairing, error = null) }
    }

    fun backToWelcome() {
        requireRepo().signOut()
        stopWatchingMembers()
        stopWatchingChild()
        _state.value = UiState(screen = Screen.Welcome)
    }

    fun openChild(child: Member) {
        val home = _state.value.screen as? Screen.ParentHome ?: return
        _state.update { it.copy(screen = Screen.ChildDetail(home.family, home.myUid, child)) }
        watchChild(home.family.id, child.uid, withReports = true)
    }

    fun closeChild() {
        val detail = _state.value.screen as? Screen.ChildDetail ?: return
        stopWatchingChild()
        _state.update { it.copy(screen = Screen.ParentHome(detail.family, detail.myUid)) }
    }

    fun setDailyLimit(minutes: Int?) = launchAction {
        val detail = _state.value.screen as? Screen.ChildDetail ?: return@launchAction
        requireDevices().setDailyLimit(detail.family.id, detail.child.uid, minutes)
    }

    fun setAppBlocked(packageName: String, blocked: Boolean) = launchAction {
        val detail = _state.value.screen as? Screen.ChildDetail ?: return@launchAction
        val rule = _state.value.childRules.apps[packageName] ?: AppRule()
        requireDevices().setAppRule(detail.family.id, detail.child.uid, packageName, rule.copy(blocked = blocked))
    }

    fun setAppLimit(packageName: String, minutes: Int?) = launchAction {
        val detail = _state.value.screen as? Screen.ChildDetail ?: return@launchAction
        val rule = _state.value.childRules.apps[packageName] ?: AppRule()
        requireDevices().setAppRule(detail.family.id, detail.child.uid, packageName, rule.copy(limitMinutes = minutes))
    }

    /** Called by the child screen with what it just read from Android. */
    fun reportOwnDevice(status: DeviceStatus, apps: List<AppUsage>) {
        val home = _state.value.screen as? Screen.ChildHome ?: return
        _state.update { it.copy(childStatus = status) }
        runCatching { requireDevices().report(home.family.id, home.myUid, status, apps) }
    }

    fun createFamily(familyName: String, parentName: String) = launchAction {
        val profile = requireRepo().createFamily(familyName.trim(), parentName.trim())
        showHome(profile)
    }

    fun joinAsParent(code: String, parentName: String) = launchAction {
        val profile = requireRepo().joinWithCode(code, parentName.trim(), Role.PARENT)
        showHome(profile)
    }

    fun pairChildDevice(code: String, childName: String) = launchAction {
        val repo = requireRepo()
        repo.signInChildDevice()
        val profile = repo.joinWithCode(code, childName.trim(), Role.CHILD)
        showHome(profile)
    }

    fun createPairingCode(role: Role) = launchAction {
        val home = _state.value.screen as? Screen.ParentHome ?: return@launchAction
        val code = requireRepo().createPairingCode(home.family, role)
        _state.update { it.copy(pairingCode = code) }
    }

    fun dismissPairingCode() {
        _state.update { it.copy(pairingCode = null) }
    }

    fun setFullControl(member: Member, fullControl: Boolean) = launchAction {
        val home = _state.value.screen as? Screen.ParentHome ?: return@launchAction
        requireRepo().setFullControl(home.family.id, member.uid, fullControl)
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private suspend fun routeForCurrentUser() {
        val repo = requireRepo()
        if (repo.currentUid == null) {
            _state.update { it.copy(screen = Screen.Welcome) }
            return
        }
        val profile = repo.loadProfile()
        when {
            profile != null -> showHome(profile)
            repo.isAnonymous -> _state.update { it.copy(screen = Screen.ChildPairing) }
            else -> _state.update { it.copy(screen = Screen.ParentSetup) }
        }
    }

    private suspend fun showHome(profile: UserProfile) {
        val repo = requireRepo()
        val family = repo.loadFamily(profile.familyId)
            ?: error("This account's family no longer exists.")
        when (profile.role) {
            Role.PARENT -> {
                _state.update { it.copy(screen = Screen.ParentHome(family, profile.uid)) }
                watchMembers(family.id)
            }
            Role.CHILD -> {
                _state.update { it.copy(screen = Screen.ChildHome(family, profile.uid)) }
                watchChild(family.id, profile.uid, withReports = false)
            }
        }
    }

    private fun watchMembers(familyId: String) {
        stopWatchingMembers()
        membersJob = viewModelScope.launch {
            requireRepo().members(familyId)
                .catch { e -> _state.update { it.copy(error = e.message) } }
                .collect { members -> _state.update { it.copy(members = members) } }
        }
    }

    private fun stopWatchingMembers() {
        membersJob?.cancel()
        membersJob = null
    }

    /** Follows one child phone's rules and, for parents, what that phone reports. */
    private fun watchChild(familyId: String, childUid: String, withReports: Boolean) {
        stopWatchingChild()
        val devices = requireDevices()
        childJob = viewModelScope.launch {
            launch {
                devices.rules(familyId, childUid)
                    .catch { e -> _state.update { it.copy(error = e.message) } }
                    .collect { rules -> _state.update { it.copy(childRules = rules) } }
            }
            if (withReports) {
                launch {
                    devices.status(familyId, childUid)
                        .catch { e -> _state.update { it.copy(error = e.message) } }
                        .collect { status -> _state.update { it.copy(childStatus = status) } }
                }
                launch {
                    devices.apps(familyId, childUid)
                        .catch { e -> _state.update { it.copy(error = e.message) } }
                        .collect { apps -> _state.update { it.copy(childApps = apps) } }
                }
            }
        }
    }

    private fun stopWatchingChild() {
        childJob?.cancel()
        childJob = null
        _state.update { it.copy(childStatus = null, childApps = emptyList(), childRules = ChildRules()) }
    }

    private fun requireDevices(): DeviceRepository = checkNotNull(devices) { "Firebase is not set up." }

    private fun requireRepo(): FamilyRepository = checkNotNull(repo) { "Firebase is not set up." }

    /** Runs [block] with the busy flag set, and turns any failure into an on-screen message. */
    private fun launchAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                block()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Something went wrong.") }
                if (_state.value.screen == Screen.Loading) {
                    _state.update { it.copy(screen = Screen.Welcome) }
                }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
